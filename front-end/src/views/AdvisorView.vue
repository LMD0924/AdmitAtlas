<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import {
  getSegments, getBatchLines, getAdmissionScores, getSchools,
  convertRank, getStudentScore
} from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

/* ============================================================================
 * 本地规则引擎口径（所有阈值集中为常量，便于审阅与未来替换）
 * 位次是唯一跨年可比口径：所有比较都用「院校历史最低位次 minRank」与
 * 「考生位次 myRank」的差值，绝不拿最低分跨年直接比较。
 *   rel = (minRank - myRank) / myRank
 *     |rel| < TIER_STABLE(5%)    → 稳：历史门槛位次与你相当
 *     rel < -TIER_STABLE         → 冲：院校历史位次更靠前，考生落后 5%~20%
 *     rel >  TIER_STABLE         → 保：院校历史位次靠后，考生优于 5%~20%
 *     |rel| > TIER_WIDE(20%)     → 仍归同档，但标注「风险放大」
 * ========================================================================== */
const TIER_STABLE = 0.05
const TIER_WIDE = 0.20
const MIN_CARDS = 6          // 候选清单最少条数
const MAX_SCHOOLS = 24       // 限制并发拉取的院校数，避免逐校请求过多
const FALLBACK = {           // 游客默认演示值
  provinceCode: '42', examYear: 2026, category: '物理类',
  totalScore: 612, rank: 12900
}

// 策略只调整三档展示配比，不改变任何录取概率判断
const STRATEGIES = {
  CONSERVATIVE: { label: '保守', mix: { CHONG: 2, WEN: 4, BAO: 4 } },
  BALANCED: { label: '均衡', mix: { CHONG: 3, WEN: 4, BAO: 3 } },
  AGGRESSIVE: { label: '冲刺', mix: { CHONG: 4, WEN: 3, BAO: 2 } }
}
const TIER_META = {
  CHONG: { label: '冲', pill: 'pill-rose', desc: '历史门槛位次明显靠前，波动较大' },
  WEN: { label: '稳', pill: 'pill-amber', desc: '历史门槛位次与你相当' },
  BAO: { label: '保', pill: 'pill-mint', desc: '历史门槛位次明显靠后，可作兜底' }
}

const CATEGORY_OPTIONS = ['物理类', '历史类', '理科', '文科']
const REGION_OPTIONS = [
  { code: '42', label: '湖北' }, { code: '11', label: '北京' },
  { code: '44', label: '广东' }, { code: '33', label: '浙江' }, { code: '51', label: '四川' }
]
const KEYWORD_OPTIONS = ['计算机', '软件', '电子信息', '临床', '医学', '经济', '金融', '法学', '机械', '数学', '师范', '材料']
const TIER_OPTIONS = ['985', '211', '双一流', '省重点', '普通']
const PRESET_QUESTIONS = [
  '我这个分数能冲哪些 985',
  '帮我看看冲稳保怎么配',
  '省内还是出省更划算',
  '位次比分数重要吗'
]
const DISCLAIMER = '本顾问为本地规则推演，演示数据为合成值，不构成录取承诺；最终以各省教育考试院公布为准'
const stableLabel = `${(TIER_STABLE * 100).toFixed(0)}%`
const wideLabel = `${(TIER_WIDE * 100).toFixed(0)}%`

const form = reactive({
  provinceCode: FALLBACK.provinceCode,
  examYear: FALLBACK.examYear,
  category: FALLBACK.category,
  totalScore: FALLBACK.totalScore,
  rank: FALLBACK.rank,
  strategy: 'BALANCED',
  regions: [],     // 意向地域：省份代码
  keywords: [],    // 专业关键词：命中 unitName / schoolName
  tiers: []        // 层次偏好：命中 tierTags
})

const messages = ref([])        // 本次页面内的会话历史
const draft = ref('')
const thinking = ref(false)
const dataStatus = ref('loading') // loading | ok | degraded | error
const snapshot = ref(null)
const msgBox = ref(null)

/* ------------------------------------ 通用数值兜底 ------------------------------------ */
function num(x) {
  if (x === null || x === undefined || x === '') return null
  const n = Number(x)
  return Number.isFinite(n) ? n : null
}
function pct(x, digits = 1) { return `${(x * 100).toFixed(digits)}%` }
function signed(n) { return `${n >= 0 ? '+' : ''}${n.toLocaleString()}` }
function toggleIn(arr, value) {
  const i = arr.indexOf(value)
  if (i >= 0) arr.splice(i, 1)
  else arr.push(value)
}
function toggleRegion(v) { toggleIn(form.regions, v) }
function toggleKeyword(v) { toggleIn(form.keywords, v) }
function toggleTier(v) { toggleIn(form.tiers, v) }
function scrollToBottom() {
  nextTick(() => {
    const el = msgBox.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/* ------------------------------------ 上下文 ------------------------------------ */
function ctx() {
  const provinceCode = String(form.provinceCode || FALLBACK.provinceCode).trim() || FALLBACK.provinceCode
  const examYear = num(form.examYear) ?? FALLBACK.examYear
  const category = CATEGORY_OPTIONS.includes(form.category) ? form.category : FALLBACK.category
  const totalScore = num(form.totalScore) ?? FALLBACK.totalScore
  const rank = num(form.rank)
  return { provinceCode, examYear, category, totalScore, rank }
}
function ctxKey(c) { return `${c.provinceCode}|${c.examYear}|${c.category}` }

const statusText = computed(() => ({
  loading: 'DATA LINK · 正在拉取公开接口…',
  ok: 'DATA LINK · 全部数据源就绪',
  degraded: 'DATA LINK · 部分数据源降级（简报已标注）',
  error: 'DATA LINK · 数据源不可用（简报将标注缺失项）'
}[dataStatus.value]))
const statusPill = computed(() => (
  dataStatus.value === 'ok' ? 'pill-mint'
    : dataStatus.value === 'loading' ? 'pill-sky' : 'pill-amber'
))
const ctxSummary = computed(() => {
  const c = ctx()
  const r = c.rank && c.rank > 0 ? c.rank.toLocaleString() : 'AUTO / convertRank'
  return `BASE ${c.provinceCode} · ${c.examYear} · ${c.category} · ${c.totalScore}分 · 位次 ${r}`
})

/* ============================================================================
 * 数据快照：分段表 / 批次线 / 院校档案 / 逐校历年投档记录
 * 全部走 Promise.allSettled，单个接口失败不会打断其它数据源，也不会白屏。
 * 快照只依赖「省 / 年 / 科类」，与考生分数无关，因此按 key 缓存复用。
 * ========================================================================== */
async function loadSnapshot(c) {
  const base = { provinceCode: c.provinceCode, examYear: c.examYear, category: c.category }
  const [segRes, lineRes, schoolRes] = await Promise.allSettled([
    getSegments(base),
    getBatchLines(base),
    // 不按 provinceCode 过滤院校：保留出省院校，供「省内 / 出省」对比使用
    getSchools({ page: 1, size: 60 })
  ])
  const val = (r) => (r.status === 'fulfilled' && r.value && typeof r.value === 'object' ? r.value : null)
  const segments = val(segRes)
  const lines = val(lineRes)
  const schools = val(schoolRes)
  const schoolItems = Array.isArray(schools?.items) ? schools.items.filter(Boolean) : []

  const codes = schoolItems
    .map((s) => String(s?.schoolCode ?? '').trim())
    .filter(Boolean)
    .slice(0, MAX_SCHOOLS)

  // ScoreController: /admission/scores 的 schoolCode 为必填，必须逐校拉取
  const admResults = await Promise.allSettled(codes.map((schoolCode) => getAdmissionScores({
    provinceCode: c.provinceCode, schoolCode, category: c.category,
    fromYear: c.examYear - 3, toYear: c.examYear
  })))
  const admissionRows = []
  let admissionSchools = 0
  admResults.forEach((r) => {
    if (r.status !== 'fulfilled') return
    const items = Array.isArray(r.value?.items) ? r.value.items.filter(Boolean) : []
    if (items.length) admissionSchools += 1
    admissionRows.push(...items)
  })

  const availability = {
    segments: Array.isArray(segments?.items) && segments.items.length > 0,
    lines: Array.isArray(lines?.items) && lines.items.length > 0,
    schools: schoolItems.length > 0,
    admission: admissionRows.length > 0
  }
  return {
    key: ctxKey(c),
    segments, lines, schools, schoolItems, admissionRows,
    admissionAttempted: codes.length, admissionSchools,
    availability,
    dataVersion: segments?.dataVersion || lines?.dataVersion || schools?.dataVersion || ''
  }
}

let snapPromise = null
let snapKey = ''
function emptySnapshot(key) {
  return {
    key, segments: null, lines: null, schools: null, schoolItems: [],
    admissionRows: [], admissionAttempted: 0, admissionSchools: 0,
    availability: { segments: false, lines: false, schools: false, admission: false },
    dataVersion: ''
  }
}
async function ensureSnapshot(c) {
  const key = ctxKey(c)
  if (snapshot.value?.key === key) return snapshot.value
  if (!snapPromise || snapKey !== key) {
    snapKey = key
    dataStatus.value = 'loading'
    snapPromise = loadSnapshot(c)
      .then((s) => {
        snapshot.value = s
        dataStatus.value = Object.values(s.availability).every(Boolean) ? 'ok' : 'degraded'
        return s
      })
      .catch(() => {
        const empty = emptySnapshot(key)
        snapshot.value = empty
        dataStatus.value = 'error'
        return empty
      })
  }
  return snapPromise
}

/* ------------------------------------ 位次来源 ------------------------------------ */
// ScoreController.RankRequest 已核对：provinceCode / examYear / category / totalScore
async function resolveRank(c) {
  if (c.rank !== null && c.rank > 0) {
    return { rank: c.rank, source: '手动填写 / 成绩档案', note: '按你填写的位次推演', ok: true }
  }
  try {
    const res = await convertRank({
      provinceCode: c.provinceCode, examYear: c.examYear,
      category: c.category, totalScore: c.totalScore
    })
    const rank = num(res?.rank)
    if (rank && rank > 0) {
      return {
        rank, source: 'convertRank 换算', ok: true,
        note: `${res?.estimated ? '估算' : '精确'} · ${res?.message || '按一分一段表换算'}`
      }
    }
    return { rank: null, source: 'convertRank 换算', ok: false, note: '换算结果为空，请手动填写位次' }
  } catch (e) {
    return {
      rank: null, source: 'convertRank 换算', ok: false,
      note: `该项数据不可用（${e?.message || '接口失败'}），请手动填写位次`
    }
  }
}

/* ------------------------------------ 候选构建 ------------------------------------ */
function classifyTier(rel) {
  const abs = Math.abs(rel)
  if (abs < TIER_STABLE) return 'WEN'
  return rel < 0 ? 'CHONG' : 'BAO'
}
function buildCandidates(snap, myRank, c) {
  if (!myRank || myRank <= 0 || !snap?.availability?.admission) return []
  const schoolMap = new Map()
  for (const s of snap.schoolItems) {
    const code = String(s?.schoolCode ?? '').trim()
    if (code && !schoolMap.has(code)) schoolMap.set(code, s)
  }
  // 归并「院校 + 专业组」为一个可比志愿单元，取该单元最近一年记录
  const units = new Map()
  for (const row of snap.admissionRows) {
    const code = String(row?.schoolCode ?? '').trim()
    if (!code) continue
    const key = `${code}|${String(row?.groupCode ?? '')}|${String(row?.majorCode ?? '')}`
    if (!units.has(key)) units.set(key, [])
    units.get(key).push(row)
  }
  const list = []
  for (const [key, rows] of units) {
    const valid = rows
      .map((r) => ({ r, y: num(r?.examYear) ?? 0, mr: num(r?.minRank) }))
      .filter((x) => x.mr !== null && x.mr > 0)
      .sort((a, b) => a.y - b.y)
    if (!valid.length) continue
    const latest = valid[valid.length - 1]
    const r = latest.r || {}
    const minRank = latest.mr
    const school = schoolMap.get(String(r.schoolCode ?? '').trim()) || {}
    const delta = minRank - myRank
    const rel = delta / Math.max(1, myRank)
    const ranks = valid.map((x) => x.mr)
    list.push({
      key,
      schoolCode: String(r.schoolCode ?? ''),
      schoolName: String(r.schoolName ?? school.schoolName ?? '未命名院校'),
      unitName: String(r.unitName ?? ''),
      groupCode: String(r.groupCode ?? ''),
      batch: String(r.batch ?? ''),
      year: latest.y,
      minScore: num(r.minScore),
      minRank,
      rankDelta: delta,
      rel,
      abs: Math.abs(rel),
      tier: classifyTier(rel),
      extreme: Math.abs(rel) > TIER_WIDE,
      volatility: (Math.max(...ranks) - Math.min(...ranks)) / Math.max(1, minRank),
      years: ranks.length,
      provinceCode: String(school.provinceCode ?? ''),
      city: String(school.city ?? ''),
      tierTags: String(school.tierTags ?? ''),
      schoolType: String(school.schoolType ?? ''),
      homeLabel: String(school.provinceCode ?? '') === c.provinceCode ? '省内' : '出省',
      hits: 0,
      pass: true
    })
  }
  return list
}

/* ------------------------------------ 偏好：前端过滤条件 ------------------------------------ */
function prefState() {
  const regions = form.regions.filter(Boolean)
  const keywords = form.keywords.filter(Boolean)
  const tiers = form.tiers.filter(Boolean)
  const active = [regions.length, keywords.length, tiers.length].filter((n) => n > 0).length
  return { regions, keywords, tiers, active }
}
function prefHits(card, p) {
  let hits = 0
  let pass = true
  if (p.regions.length) {
    if (p.regions.includes(card.provinceCode)) hits += 1
    else pass = false
  }
  if (p.keywords.length) {
    const text = `${card.unitName}${card.schoolName}`
    if (p.keywords.some((k) => text.includes(k))) hits += 1
    else pass = false
  }
  if (p.tiers.length) {
    if (p.tiers.some((t) => card.tierTags.includes(t))) hits += 1
    else pass = false
  }
  return { hits, pass }
}
function applyPreferences(list, p) {
  const scored = list.map((c) => ({ ...c, ...prefHits(c, p) }))
  if (!p.active) return { list: scored, relaxed: false }
  const kept = scored.filter((c) => c.pass)
  if (kept.length >= MIN_CARDS) return { list: kept, relaxed: false }
  // 严格过滤后不足：放宽为按命中偏好数排序的全量候选，并在简报里显式说明
  return { list: [...scored].sort((a, b) => b.hits - a.hits || a.abs - b.abs), relaxed: true }
}

/* ------------------------------------ 策略配比选卡 ------------------------------------ */
function pickByStrategy(list, strategyKey) {
  const mix = (STRATEGIES[strategyKey] || STRATEGIES.BALANCED).mix
  const buckets = { CHONG: [], WEN: [], BAO: [] }
  for (const c of list) (buckets[c.tier] || buckets.WEN).push(c)
  for (const k of Object.keys(buckets)) buckets[k].sort((a, b) => a.abs - b.abs)
  const selected = []
  for (const k of ['CHONG', 'WEN', 'BAO']) selected.push(...buckets[k].slice(0, mix[k]))
  if (selected.length < MIN_CARDS) {
    const chosen = new Set(selected.map((c) => c.key))
    const rest = [...list].filter((c) => !chosen.has(c.key)).sort((a, b) => a.abs - b.abs)
    for (const c of rest) {
      if (selected.length >= MIN_CARDS) break
      selected.push(c)
    }
  }
  const order = { CHONG: 0, WEN: 1, BAO: 2 }
  return selected.sort((a, b) => order[a.tier] - order[b.tier] || a.abs - b.abs)
}

/* ------------------------------------ 数据依据计算 ------------------------------------ */
function computeSegmentStats(snap, totalScore) {
  const bad = { ok: false, text: '该项数据不可用' }
  if (!snap?.availability?.segments) return bad
  const items = (snap.segments.items || [])
    .filter((s) => s && num(s.score) !== null && num(s.cumulativeCount) !== null)
  if (!items.length) return bad
  const sorted = [...items].sort((a, b) => num(a.score) - num(b.score))
  const total = Math.max(...sorted.map((s) => num(s.cumulativeCount)))
  if (!(total > 0)) return bad
  // cumulativeCount = 分数不低于该分的人数；取考生分数对应行，缺精确行则线性插值
  // （与后端 RankCalculator 一致），总人数 = 全表最大累计人数
  let cum = null
  const exact = sorted.find((s) => num(s.score) === totalScore)
  if (exact) {
    cum = num(exact.cumulativeCount)
  } else {
    let below = null
    let above = null
    for (const s of sorted) {
      const sc = num(s.score)
      if (sc < totalScore && (!below || sc > num(below.score))) below = s
      if (sc > totalScore && (!above || sc < num(above.score))) above = s
    }
    if (below && above) {
      const ratio = (totalScore - num(below.score)) / (num(above.score) - num(below.score))
      cum = num(below.cumulativeCount) + ratio * (num(above.cumulativeCount) - num(below.cumulativeCount))
    } else if (below) cum = num(below.cumulativeCount)
    else if (above) cum = num(above.cumulativeCount)
  }
  if (cum === null || !Number.isFinite(cum)) return bad
  const exceedPct = Math.max(0, Math.min(100, (total - cum) / total * 100))
  return {
    ok: true, total, cum, exceedPct, rows: sorted.length,
    text: `一分一段 ${sorted.length} 行 · 全表最大累计人数 ${total.toLocaleString()} · ${totalScore} 分对应累计人数约 ${Math.round(cum).toLocaleString()} → 超过全省约 ${exceedPct.toFixed(1)}% 考生（约前 ${(100 - exceedPct).toFixed(1)}%）`
  }
}
function computeBatchLines(snap, totalScore) {
  const bad = { ok: false, text: '该项数据不可用', details: [] }
  if (!snap?.availability?.lines) return bad
  const details = (snap.lines.items || [])
    .filter((l) => l && num(l.lineScore) !== null)
    .map((l) => {
      const line = num(l.lineScore)
      return { batch: String(l.batch ?? '批次'), line, delta: totalScore - line, passed: totalScore >= line }
    })
    .sort((a, b) => b.line - a.line)
  if (!details.length) return bad
  const top = details[0]
  const passed = details.filter((d) => d.passed)
  const text = passed.length
    ? `已过 ${passed.map((d) => d.batch).join(' / ')}：最高 ${top.batch} ${top.line} 分，超线 ${Math.max(...passed.map((d) => d.delta))} 分`
    : `未达到任何批次线（最高 ${top.batch} ${top.line} 分，还差 ${-top.delta} 分）`
  return { ok: true, text, details }
}
function detectFocus(q) {
  const s = String(q || '')
  if (/985|211|冲哪些|能冲/.test(s)) return 'chong'
  if (/冲稳保|怎么配|配比|梯度/.test(s)) return 'mix'
  if (/省内|出省|地域|外省/.test(s)) return 'region'
  if (/位次|排名|分数/.test(s)) return 'rank'
  return 'general'
}
function decorateCard(x) {
  const meta = TIER_META[x.tier]
  const diff = Math.abs(x.rankDelta).toLocaleString()
  let risk
  if (x.tier === 'CHONG') risk = `历史最低位次比你靠前 ${diff} 名（约 ${(x.abs * 100).toFixed(1)}%），属冲刺档，年度波动风险较高`
  else if (x.tier === 'BAO') risk = `历史最低位次比你靠后 ${diff} 名（约 ${(x.abs * 100).toFixed(1)}%），可作兜底`
  else risk = `历史最低位次与你相差 ${diff} 名（约 ${(x.abs * 100).toFixed(1)}%），落在稳妥区间`
  if (x.extreme) risk += `；位次差超过 ${wideLabel}，风险放大`
  if (x.years > 1) risk += `；近 ${x.years} 年位次波动 ${(x.volatility * 100).toFixed(1)}%`
  return {
    ...x,
    tierLabel: meta.label,
    tierPill: meta.pill,
    tierDesc: meta.desc,
    minScoreText: x.minScore === null ? '—' : String(x.minScore),
    yearText: x.year || '—',
    risk
  }
}

/* ============================================================================
 * [预留 LLM 接入位 / FUTURE LLM HOOK]
 * 未来可把 generateBrief() 替换为云端 LLM 调用，输入输出契约保持不变：
 *   输入：{ question, c, snap, rankInfo, pool, selected, pref, relaxed }
 *         —— 现在这份结构化上下文（全部来自公开接口与本地规则计算）
 *   输出：{ headline, summary, basis, candidateIntro, candidates, risks, meta }
 *         —— 仍是「结论摘要 / 数据依据 / 候选清单 / 风险提示」四段对象
 * 替换时只需在函数体内把本地规则计算换成一次云端请求，并保留数据可用性标注；
 * 对话层、消息渲染与候选卡片均无需改动。
 * 当前版本刻意零外部依赖：不引入任何 LLM SDK，也不发起任何 LLM 网络请求。
 * ========================================================================== */
function generateBrief({ question, c, snap, rankInfo, pool, selected, pref, relaxed }) {
  const strategy = STRATEGIES[form.strategy] || STRATEGIES.BALANCED
  const focus = detectFocus(question)
  const seg = computeSegmentStats(snap, c.totalScore)
  const batch = computeBatchLines(snap, c.totalScore)
  const rank = rankInfo.rank
  const tierCounts = { CHONG: 0, WEN: 0, BAO: 0 }
  for (const x of selected) tierCounts[x.tier] += 1
  const hitCount = selected.filter((x) => x.hits > 0).length

  // ---- 01 结论摘要：由问题导向，但所有数字均来自接口数据 ----
  const baseLine = rank
    ? `基准：${c.provinceCode} · ${c.examYear} · ${c.category} · ${c.totalScore} 分 / 位次 ${rank.toLocaleString()}`
    : `基准：${c.provinceCode} · ${c.examYear} · ${c.category} · ${c.totalScore} 分（位次缺失）`
  let headline
  if (focus === 'chong') {
    const elite = pool.filter((x) => x.tierTags.includes('985'))
    const chong = elite.filter((x) => x.tier === 'CHONG')
    headline = elite.length
      ? `候选池中共 ${elite.length} 个 985 层次志愿单元，其中「冲」档 ${chong.length} 个；「${strategy.label}」策略建议配置 ${strategy.mix.CHONG} 个冲档名额。`
      : '当前候选池未检到 985 层次标签，985 冲刺清单该项数据不可用。'
  } else if (focus === 'mix') {
    headline = `按「${strategy.label}」策略，建议冲 ${strategy.mix.CHONG} / 稳 ${strategy.mix.WEN} / 保 ${strategy.mix.BAO}；实际从 ${pool.length} 个志愿单元中选配 ${selected.length} 个（冲 ${tierCounts.CHONG} / 稳 ${tierCounts.WEN} / 保 ${tierCounts.BAO}）。`
  } else if (focus === 'region') {
    const inside = pool.filter((x) => x.provinceCode && x.provinceCode === c.provinceCode)
    const outside = pool.filter((x) => x.provinceCode && x.provinceCode !== c.provinceCode)
    const avg = (arr) => (arr.length ? arr.reduce((s, x) => s + x.rankDelta, 0) / arr.length : null)
    const ai = avg(inside)
    const ao = avg(outside)
    headline = `省内候选 ${inside.length} 条、出省候选 ${outside.length} 条；平均位次差 省内 ${ai === null ? '该项数据不可用' : signed(Math.round(ai))} / 出省 ${ao === null ? '该项数据不可用' : signed(Math.round(ao))}（正 = 院校门槛位次靠后，相对更易）。`
  } else if (focus === 'rank') {
    headline = `分数与最低分都不可跨年直接比较；本简报全部按「最低位次 minRank」与你位次 ${rank ? rank.toLocaleString() : '（缺失）'} 的差值推演，共归并 ${pool.length} 个可比志愿单元。`
  } else {
    headline = rank
      ? `以 ${c.totalScore} 分 / 位次 ${rank.toLocaleString()} 为基准，${seg.ok ? `超过全省约 ${seg.exceedPct.toFixed(1)}% 考生，` : ''}${batch.ok ? `${batch.text}，` : ''}「${strategy.label}」策略建议冲 ${strategy.mix.CHONG} / 稳 ${strategy.mix.WEN} / 保 ${strategy.mix.BAO}。`
      : '位次缺失，无法完成冲稳保推演；请填写位次或确认 convertRank 换算可用。'
  }

  const summary = [baseLine, headline]
  if (seg.ok) summary.push(`一分一段：${seg.text}`)
  if (batch.ok) summary.push(`批次线：${batch.text}`)
  summary.push(pref.active
    ? `偏好：启用 ${pref.active} 类条件（地域 ${pref.regions.length} / 关键词 ${pref.keywords.length} / 层次 ${pref.tiers.length}），命中 ${hitCount} 条偏好${relaxed ? '；按偏好过滤后候选不足，已放宽为按命中数排序' : ''}`
    : '偏好：未设置，候选清单未做偏好过滤，命中 0 条偏好')
  if (rankInfo.rank) summary.push(`位次来源：${rankInfo.source} · ${rankInfo.note}`)
  else summary.push(`位次来源：该项数据不可用 · ${rankInfo.note}`)

  // ---- 02 数据依据：每项显式标注可用 / 不可用 ----
  let rankBasis
  if (rankInfo.source.startsWith('convertRank')) {
    rankBasis = rankInfo.rank
      ? { label: '位次换算 convertRank', value: `convertRank → 位次 ${rankInfo.rank.toLocaleString()}（${rankInfo.note}）`, ok: true }
      : { label: '位次换算 convertRank', value: '该项数据不可用', ok: false }
  } else {
    rankBasis = rankInfo.rank
      ? { label: '位次换算 convertRank', value: `未调用：使用${rankInfo.source}提供的位次 ${rankInfo.rank.toLocaleString()}`, ok: true }
      : { label: '位次换算 convertRank', value: '该项数据不可用', ok: false }
  }
  const basis = [
    { label: '一分一段表', value: seg.ok ? seg.text : '该项数据不可用', ok: seg.ok },
    { label: '批次线', value: batch.ok ? batch.text : '该项数据不可用', ok: batch.ok },
    {
      label: '院校档案',
      value: snap?.availability?.schools
        ? `补充 ${snap.schoolItems.length} 所院校的名称 / 层次标签 / 属地 / 办学类型`
        : '该项数据不可用',
      ok: !!snap?.availability?.schools
    },
    {
      label: '历年投档记录',
      value: snap?.availability?.admission
        ? `拉取 ${snap.admissionAttempted} 所院校、成功 ${snap.admissionSchools} 所，共 ${snap.admissionRows.length} 条记录，归并 ${pool.length} 个志愿单元（跨年只比 minRank）`
        : '该项数据不可用',
      ok: !!snap?.availability?.admission
    },
    rankBasis,
    { label: '数据版本', value: snap?.dataVersion || '该项数据不可用', ok: !!snap?.dataVersion }
  ]

  // ---- 03 候选清单 ----
  const candidateIntro = selected.length
    ? `按「${strategy.label}」配比输出 ${selected.length} 条候选（冲 ${tierCounts.CHONG} / 稳 ${tierCounts.WEN} / 保 ${tierCounts.BAO}）· 命中 ${hitCount} 条偏好`
    : '候选清单不可用（位次缺失或投档记录不可用）'
  const cards = selected.map(decorateCard)

  // ---- 04 风险提示 ----
  const risks = [
    '位次是唯一跨年可比口径：每年难度与满分不同，最低分仅作展示，不可跨年直接比较。',
    `分档阈值（常量）：|Δ| / 自身位次 < ${stableLabel} 为稳；考生位次落后 ${stableLabel}~${wideLabel} 为冲、优于 ${stableLabel}~${wideLabel} 为保；差值超过 ${wideLabel} 归入同档但风险放大。`,
    `策略「${strategy.label}」把三档配比建议调整为 冲 ${strategy.mix.CHONG} / 稳 ${strategy.mix.WEN} / 保 ${strategy.mix.BAO}，仅改变展示配比，不改变录取概率。`,
    DISCLAIMER
  ]
  const labels = { segments: '一分一段表', lines: '批次线', schools: '院校档案', admission: '历年投档记录' }
  if (snap) {
    for (const k of Object.keys(labels)) {
      if (!snap.availability[k]) risks.push(`${labels[k]}该项数据不可用，相关结论已跳过或降级。`)
    }
  } else {
    risks.push('全部数据源该项数据不可用，简报已降级输出。')
  }
  if (!rankInfo.rank) risks.push('位次数据不可用，冲稳保分档与候选清单未生成。')
  if (relaxed) risks.push('按偏好过滤后可用候选不足，已放宽偏好过滤，候选按命中偏好数排序。')

  return {
    headline, summary, basis, candidateIntro, candidates: cards, risks,
    meta: { strategyLabel: strategy.label, hitCount, poolSize: pool.length, tierCounts }
  }
}

function fallbackBrief(q, e) {
  return {
    headline: '本地规则推演未能完成',
    summary: [
      `问题：${q}`,
      '数据链路出现异常，已按容错策略返回降级简报，页面未中断。',
      `原因：${e?.message || '未知错误'}`
    ],
    basis: [
      { label: '一分一段表', value: '该项数据不可用', ok: false },
      { label: '批次线', value: '该项数据不可用', ok: false },
      { label: '院校档案', value: '该项数据不可用', ok: false },
      { label: '历年投档记录', value: '该项数据不可用', ok: false },
      { label: '位次换算 convertRank', value: '该项数据不可用', ok: false },
      { label: '数据版本', value: '该项数据不可用', ok: false }
    ],
    candidateIntro: '候选清单不可用',
    candidates: [],
    risks: ['请稍后重试，或手动填写位次后再次提问。', DISCLAIMER],
    meta: { strategyLabel: '—', hitCount: 0, poolSize: 0, tierCounts: { CHONG: 0, WEN: 0, BAO: 0 } }
  }
}

/* ------------------------------------ 会话 ------------------------------------ */
async function ask(question) {
  const q = String(question ?? '').trim()
  if (!q || thinking.value) return
  draft.value = ''
  messages.value.push({ role: 'user', text: q })
  thinking.value = true
  scrollToBottom()
  let brief
  try {
    const c = ctx()
    const snap = await ensureSnapshot(c)
    const rankInfo = await resolveRank(c)
    const pool = buildCandidates(snap, rankInfo.rank, c)
    const pref = prefState()
    const filtered = applyPreferences(pool, pref)
    const selected = pickByStrategy(filtered.list, form.strategy)
    brief = generateBrief({
      question: q, c, snap, rankInfo,
      pool: filtered.list, selected, pref, relaxed: filtered.relaxed
    })
  } catch (e) {
    brief = fallbackBrief(q, e)
  } finally {
    thinking.value = false
  }
  messages.value.push({ role: 'advisor', question: q, brief })
  scrollToBottom()
}
function submitDraft() { ask(draft.value) }
function clearHistory() { messages.value = [] }

onMounted(async () => {
  // 登录后尝试读取自己的成绩档案作默认值；失败（含游客 401）静默回退演示值
  if (auth.isLoggedIn) {
    try {
      const res = await getStudentScore({ examYear: Number(form.examYear) || FALLBACK.examYear })
      const p = res?.profile
      if (p) {
        const ts = num(p.totalScore)
        const rv = num(p.rankValue)
        const y = num(p.examYear)
        if (ts !== null) form.totalScore = ts
        if (rv !== null && rv > 0) form.rank = rv
        if (y !== null) form.examYear = y
        if (p.category && CATEGORY_OPTIONS.includes(p.category)) form.category = p.category
        if (p.provinceCode) form.provinceCode = String(p.provinceCode)
      }
    } catch { /* 回退演示值 */ }
  }
  ensureSnapshot(ctx()).catch(() => {})
})
</script>

<template>
  <div class="advisor-root">
    <!-- HUD 装饰：网格底由 ::before 绘制，扫描光带与四角准星均为 scoped CSS -->
    <span class="adv-corner tl" aria-hidden="true"></span>
    <span class="adv-corner tr" aria-hidden="true"></span>
    <span class="adv-corner bl" aria-hidden="true"></span>
    <span class="adv-corner br" aria-hidden="true"></span>
    <span class="adv-scan" aria-hidden="true"></span>

    <div class="adv-content space-y-5">
      <!-- 顶部 HUD 状态条 -->
      <div v-reveal="0" class="glass adv-status-bar">
        <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
          <span class="adv-sys">SYS / ADVISOR.CORE</span>
          <span class="adv-dot" aria-hidden="true"></span>
          <span class="adv-tag">LOCAL RULE ENGINE · NO LLM</span>
          <span class="pill pill-neutral">本地规则引擎</span>
          <span class="adv-spacer"></span>
          <span class="pill" :class="statusPill">{{ statusText }}</span>
        </div>
        <div class="adv-meta">
          <span>{{ ctxSummary }}</span>
          <span>数据版本 {{ snapshot?.dataVersion || '—' }}</span>
          <span>会话 {{ messages.length }} 轮</span>
        </div>
      </div>

      <header v-reveal="1">
        <p class="eyebrow">Volunteer advisor / 本地规则推演</p>
        <h1 class="mt-2 text-[22px] font-semibold tracking-[-0.02em]">志愿顾问 · 把分数翻译成位次语言</h1>
        <p class="mt-2 max-w-[86ch] text-[12px] leading-6 muted">
          不接大模型。用一分一段表、批次线与历年投档最低位次，在本地按公开规则推演一份结构化简报。
          {{ DISCLAIMER }}
        </p>
      </header>

      <div class="grid gap-4 lg:grid-cols-12">
        <!-- 左：输入控制台 -->
        <aside class="space-y-4 lg:col-span-5">
          <section v-reveal="1" class="glass p-5">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <p class="text-[13px] font-semibold">推演参数</p>
              <span class="pill pill-sky">INPUT / 游客可用</span>
            </div>

            <div class="mt-4 grid grid-cols-2 gap-3">
              <div>
                <label class="field-label" for="adv-score">总分</label>
                <input id="adv-score" v-model="form.totalScore" type="number" min="0" max="750" class="field" />
              </div>
              <div>
                <label class="field-label" for="adv-rank">位次（留空自动换算）</label>
                <input id="adv-rank" v-model="form.rank" type="number" min="1" class="field" placeholder="留空由 convertRank 换算" />
              </div>
              <div>
                <label class="field-label" for="adv-prov">省份代码</label>
                <input id="adv-prov" v-model="form.provinceCode" class="field" />
              </div>
              <div>
                <label class="field-label" for="adv-year">年份</label>
                <input id="adv-year" v-model="form.examYear" type="number" class="field" />
              </div>
              <div class="col-span-2">
                <label class="field-label" for="adv-cat">科类</label>
                <select id="adv-cat" v-model="form.category" class="field">
                  <option v-for="o in CATEGORY_OPTIONS" :key="o" :value="o">{{ o }}</option>
                </select>
              </div>
            </div>

            <p class="field-label mt-5">策略（调整冲 / 稳 / 保配比建议）</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="(s, k) in STRATEGIES" :key="k" type="button" class="chip"
                :class="{ 'is-active': form.strategy === k }" @click="form.strategy = k">
                {{ s.label }} · 冲{{ s.mix.CHONG }}/稳{{ s.mix.WEN }}/保{{ s.mix.BAO }}
              </button>
            </div>

            <p class="field-label mt-5">意向地域</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="r in REGION_OPTIONS" :key="r.code" type="button" class="chip"
                :class="{ 'is-active': form.regions.includes(r.code) }" @click="toggleRegion(r.code)">{{ r.label }}</button>
            </div>

            <p class="field-label mt-5">专业关键词</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="k in KEYWORD_OPTIONS" :key="k" type="button" class="chip"
                :class="{ 'is-active': form.keywords.includes(k) }" @click="toggleKeyword(k)">{{ k }}</button>
            </div>

            <p class="field-label mt-5">层次偏好</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="t in TIER_OPTIONS" :key="t" type="button" class="chip"
                :class="{ 'is-active': form.tiers.includes(t) }" @click="toggleTier(t)">{{ t }}</button>
            </div>

            <p class="mt-4 text-[11px] leading-5 faint">
              位次留空时调用 convertRank 按一分一段表换算；偏好仅作前端过滤，命中条数会写进简报。
            </p>
          </section>

          <section v-reveal="2" class="glass p-5">
            <p class="text-[13px] font-semibold">预设问题</p>
            <div class="mt-3 flex flex-wrap gap-2">
              <button v-for="q in PRESET_QUESTIONS" :key="q" type="button" class="chip"
                :disabled="thinking" @click="ask(q)">{{ q }}</button>
            </div>
          </section>

          <section v-reveal="2" class="glass p-5">
            <p class="text-[13px] font-semibold">规则口径（常量阈值）</p>
            <div class="mt-3 space-y-2 text-[11px] leading-5 muted">
              <p>· 位次是唯一跨年可比口径；最低分仅作展示。</p>
              <p>· |Δ| / 自身位次 &lt; {{ stableLabel }} 为稳。</p>
              <p>· 落后 {{ stableLabel }}~{{ wideLabel }} 为冲，优于 {{ stableLabel }}~{{ wideLabel }} 为保。</p>
              <p>· 差值超过 {{ wideLabel }} 归入同档但风险放大。</p>
            </div>
          </section>
        </aside>

        <!-- 右：对话式消息区 -->
        <section class="lg:col-span-7">
          <div class="glass flex h-full flex-col overflow-hidden">
            <div class="flex flex-wrap items-center justify-between gap-2 px-5 py-4" style="border-bottom: 1px solid var(--line-soft)">
              <div>
                <p class="text-[13px] font-semibold">顾问会话</p>
                <p class="mt-1 text-[11px] faint">保留本次页面内的历史 · 每次提问重新按接口数据推演</p>
              </div>
              <button type="button" class="btn btn-slate" :disabled="!messages.length" @click="clearHistory">
                <span class="btn-glyph">✕</span> 清空会话
              </button>
            </div>

            <div ref="msgBox" class="adv-messages flex-1 space-y-4 overflow-y-auto px-5 py-5">
              <div v-if="!messages.length && !thinking" class="empty-state">
                <p class="text-[13px] font-semibold">终端就绪</p>
                <p class="mt-2 text-[11px] faint">点击左侧预设问题，或在下方输入问题开始推演</p>
              </div>

              <template v-for="(m, i) in messages" :key="i">
                <!-- 用户问题气泡 -->
                <div v-if="m.role === 'user'" class="flex justify-end">
                  <div class="bubble-user">{{ m.text }}</div>
                </div>

                <!-- 顾问回复卡：结论摘要 / 数据依据 / 候选清单 / 风险提示 -->
                <div v-else class="glass-flat brief">
                  <div class="brief-head">
                    <span class="adv-sys">ADVISOR.CORE / BRIEF</span>
                    <span class="pill pill-teal">{{ m.brief.meta.strategyLabel }} · {{ m.brief.candidates.length }} 条候选</span>
                  </div>

                  <div class="brief-section">
                    <p class="brief-label">01 / 结论摘要</p>
                    <p class="brief-headline">{{ m.brief.headline }}</p>
                    <p v-for="(line, j) in m.brief.summary" :key="j" class="brief-line">· {{ line }}</p>
                  </div>

                  <div class="brief-section">
                    <p class="brief-label">02 / 数据依据</p>
                    <div class="grid gap-2 sm:grid-cols-2">
                      <div v-for="(b, j) in m.brief.basis" :key="j" class="basis-row">
                        <span class="text-[11px] faint">{{ b.label }}</span>
                        <span class="text-[11px] leading-5" :style="{ color: b.ok ? 'var(--text-2)' : 'var(--rose)' }">{{ b.value }}</span>
                      </div>
                    </div>
                  </div>

                  <div class="brief-section">
                    <p class="brief-label">03 / 候选清单</p>
                    <p class="mb-3 text-[11px] faint">{{ m.brief.candidateIntro }}</p>
                    <div v-if="m.brief.candidates.length" class="grid gap-3 xl:grid-cols-2">
                      <div v-for="card in m.brief.candidates" :key="card.key" class="glass candidate p-3">
                        <div class="flex flex-wrap items-start justify-between gap-2">
                          <div class="min-w-0">
                            <p class="truncate text-[13px] font-semibold">{{ card.schoolName }}</p>
                            <p class="mt-1 text-[11px] faint">{{ card.unitName || '—' }} · {{ card.groupCode || '—' }} · {{ card.yearText }} 年 · {{ card.batch || '—' }}</p>
                          </div>
                          <span class="pill" :class="card.tierPill">{{ card.tierLabel }}档</span>
                        </div>
                        <div class="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] muted">
                          <span>最低分 <strong class="text-[13px]" style="color: var(--text)">{{ card.minScoreText }}</strong></span>
                          <span>最低位次 {{ card.minRank.toLocaleString() }}</span>
                          <span>位次差 <strong :style="{ color: card.rankDelta >= 0 ? 'var(--mint)' : 'var(--rose)' }">{{ signed(card.rankDelta) }}</strong></span>
                          <span>约 {{ (card.abs * 100).toFixed(1) }}%</span>
                        </div>
                        <div class="mt-2 flex flex-wrap items-center gap-1.5">
                          <span class="pill pill-neutral">{{ card.homeLabel }} · {{ card.city || '属地未公示' }}</span>
                          <span v-if="card.tierTags" class="pill pill-violet">{{ card.tierTags }}</span>
                          <span v-if="card.schoolType" class="pill pill-sky">{{ card.schoolType }}</span>
                          <span v-if="card.hits" class="pill pill-teal">命中 {{ card.hits }} 项偏好</span>
                        </div>
                        <p class="mt-2 text-[11px] leading-5 faint">{{ card.risk }}</p>
                      </div>
                    </div>
                    <div v-else class="rounded-xl border py-6 text-center text-[11px] faint" style="border-color: var(--line)">
                      候选清单不可用（位次或投档记录缺失）
                    </div>
                  </div>

                  <div class="brief-section">
                    <p class="brief-label">04 / 风险提示</p>
                    <p v-for="(w, j) in m.brief.risks" :key="j" class="brief-line">· {{ w }}</p>
                  </div>
                </div>
              </template>

              <div v-if="thinking" class="flex items-center gap-2 text-[11px] faint">
                <span class="adv-dot" aria-hidden="true"></span> 顾问正在按本地规则推演…
              </div>
            </div>

            <div class="flex flex-wrap items-center gap-2 px-5 py-4" style="border-top: 1px solid var(--line-soft)">
              <input v-model="draft" class="field flex-1" placeholder="输入你的问题，例如：我这个分数能冲哪些 985"
                @keydown.enter="submitDraft" />
              <button type="button" class="btn btn-blue" :disabled="thinking || !draft.trim()" @click="submitDraft">
                <span class="btn-glyph">▶</span> 生成简报 <span class="link-arrow">→</span>
              </button>
            </div>
          </div>
        </section>
      </div>

      <footer v-reveal="3" class="glass-flat flex flex-wrap items-center justify-between gap-2 px-5 py-3 text-[11px] leading-5 faint">
        <span>{{ DISCLAIMER }}</span>
        <span class="adv-sys">LOCAL RULE ENGINE · NO LLM</span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
/* HUD 外框：网格底 + 扫描光带 + 四角准星，颜色只取 CSS 变量，双主题自适应 */
.advisor-root {
  position: relative;
  padding: 16px;
  border: 1px solid color-mix(in srgb, var(--accent) 24%, var(--line));
  border-radius: 16px;
  background: color-mix(in srgb, var(--surface) 50%, transparent);
  overflow: hidden;
}
.advisor-root::before {
  content: '';
  position: absolute; inset: 0; border-radius: inherit; pointer-events: none; z-index: 0;
  background-image:
    linear-gradient(var(--hud-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--hud-line) 1px, transparent 1px);
  background-size: 44px 44px;
  -webkit-mask-image: radial-gradient(90% 72% at 50% 40%, var(--text) 26%, transparent 100%);
  mask-image: radial-gradient(90% 72% at 50% 40%, var(--text) 26%, transparent 100%);
}
.adv-content { position: relative; z-index: 1; }
.adv-scan {
  position: absolute; left: 0; right: 0; top: -22%; height: 22%; z-index: 0; pointer-events: none;
  background: linear-gradient(180deg, transparent, var(--hud-glow) 55%, color-mix(in srgb, var(--hud-cyan) 24%, transparent));
  opacity: .5;
  animation: adv-scan 7s linear infinite;
}
@keyframes adv-scan { from { top: -22%; } to { top: 118%; } }
.adv-corner {
  position: absolute; width: 16px; height: 16px; z-index: 2; pointer-events: none;
  border: 2px solid var(--hud-cyan); opacity: .8;
}
.adv-corner.tl { top: 6px; left: 6px; border-right: 0; border-bottom: 0; border-radius: 5px 0 0 0; }
.adv-corner.tr { top: 6px; right: 6px; border-left: 0; border-bottom: 0; border-radius: 0 5px 0 0; }
.adv-corner.bl { bottom: 6px; left: 6px; border-right: 0; border-top: 0; border-radius: 0 0 0 5px; }
.adv-corner.br { bottom: 6px; right: 6px; border-left: 0; border-top: 0; border-radius: 0 0 5px 0; }

.adv-status-bar { padding: 12px 14px; }
.adv-sys, .adv-tag, .adv-meta, .brief-label { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.adv-sys { font-size: 10px; font-weight: 600; letter-spacing: .16em; color: var(--accent-text); }
.adv-tag { font-size: 10px; letter-spacing: .12em; color: var(--teal); }
.adv-dot {
  display: inline-block; width: 6px; height: 6px; flex: none; border-radius: 50%;
  background: var(--hud-cyan); box-shadow: 0 0 10px var(--hud-cyan);
  animation: adv-pulse 1.4s ease-in-out infinite;
}
@keyframes adv-pulse { 0%, 100% { opacity: 1; } 50% { opacity: .3; } }
.adv-spacer { flex: 1; }
.adv-meta {
  display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px;
  margin-top: 10px; font-size: 10px; letter-spacing: .06em; color: var(--text-3);
}

.adv-messages { min-height: 360px; max-height: 620px; }
.bubble-user {
  max-width: 86%; padding: 9px 13px; border-radius: 12px 12px 2px 12px;
  font-size: 12px; line-height: 1.6;
  color: var(--accent-text); background: var(--accent-soft);
  border: 1px solid color-mix(in srgb, var(--accent) 26%, transparent);
}
.brief { padding: 14px; }
.brief-head {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px;
  padding-bottom: 10px; border-bottom: 1px solid var(--line);
}
.brief-section { padding-top: 12px; }
.brief-section + .brief-section { margin-top: 12px; border-top: 1px dashed var(--line-soft); }
.brief-label { margin-bottom: 8px; font-size: 10px; font-weight: 600; letter-spacing: .12em; color: var(--sky); }
.brief-headline { margin-bottom: 8px; font-size: 13px; font-weight: 600; line-height: 1.7; color: var(--text); }
.brief-line { font-size: 11.5px; line-height: 1.8; color: var(--text-2); }
.basis-row {
  display: flex; flex-direction: column; gap: 3px; padding: 8px 10px;
  border: 1px solid var(--line-soft); border-radius: 9px; background: var(--surface);
}
.candidate { transition: transform .35s var(--ease), border-color .35s var(--ease); }
.candidate:hover { transform: translateY(-3px); border-color: color-mix(in srgb, var(--accent) 35%, var(--line)); }

@media (max-width: 640px) {
  .advisor-root { padding: 10px; }
  .adv-messages { min-height: 260px; max-height: none; }
}
@media (prefers-reduced-motion: reduce) {
  .adv-scan, .advisor-root::before { display: none !important; }
  .adv-dot { animation: none !important; }
}
</style>
