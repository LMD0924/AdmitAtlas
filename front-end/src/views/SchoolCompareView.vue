<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { LineChart, RadarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, RadarComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { compareSchools } from '@/api'
import { useTheme } from '@/composables/useTheme'
import { schoolTheme, tierPill, splitTiers, schoolInitial } from '@/utils/schoolTheme'

echarts.use([
  LineChart, RadarChart, GridComponent, TooltipComponent,
  LegendComponent, RadarComponent, CanvasRenderer
])

const route = useRoute()
const router = useRouter()
const { theme } = useTheme()

// ---------------------------------------------------------------- 数值兜底：SQL 聚合值可能是字符串 / BigDecimal / null
function toNum(v) {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}
function fmtNum(v) {
  const n = toNum(v)
  return n === null ? '—' : n.toLocaleString()
}
function txt(v, fb = '—') {
  return v === null || v === undefined || v === '' ? fb : String(v)
}
function arr(v) { return Array.isArray(v) ? v : [] }

/** 图表色统一从 :root 读 CSS 变量，明暗主题切换后重新取值重绘。 */
function palette() {
  const css = getComputedStyle(document.documentElement)
  const get = (name, fb) => css.getPropertyValue(name).trim() || fb
  return {
    text: get('--text', '#1d2534'), text2: get('--text-2', '#626d81'),
    text3: get('--text-3', '#9aa4b6'), line: get('--line', '#e7ebf4'),
    surface: get('--surface', '#f8f9fd'), surface2: get('--surface-2', '#eef2ff'),
    accent: get('--accent', '#4a6cf7'), accentSoft: get('--accent-soft', '#eef2ff'),
    sky: get('--sky', '#3b82f6'), violet: get('--violet', '#8b5cf6'),
    teal: get('--teal', '#14a89a'), amber: get('--amber', '#e39620'),
    rose: get('--rose', '#e26363'), mint: get('--mint', '#18a56d')
  }
}
/** 每校折线 / 雷达颜色跟随顶部主题色卡，保持「还是这一所」的连续感。 */
function themeColor(key, p) {
  return ({
    sky: p.sky, violet: p.violet, teal: p.teal,
    amber: p.amber, rose: p.rose, mint: p.mint
  })[key] || p.accent
}

// ---------------------------------------------------------------- URL 中的院校代码（最多 4 个，去重）
const codes = computed(() => {
  const raw = route.query.codes
  const str = Array.isArray(raw) ? raw.join(',') : (typeof raw === 'string' ? raw : '')
  return [...new Set(str.split(',').map((s) => s.trim()).filter(Boolean))].slice(0, 4)
})
/** 返回院校库时保留其它筛选条件，只剔除对比专用的 codes。 */
const schoolsQuery = computed(() => {
  const q = { ...route.query }
  delete q.codes
  return q
})

// ---------------------------------------------------------------- 状态
const items = ref([])
const meta = ref({})
const loading = ref(false)
const error = ref('')
const colLabels = computed(() => items.value.map((_, i) => `院校 ${String.fromCharCode(65 + i)}`))

const lineEl = ref(null)
const radarEl = ref(null)
let lineChart = null
let radarChart = null

// ---------------------------------------------------------------- 单所院校字段级容错
function normalizeItem(raw) {
  try {
    const s = raw?.school ?? {}
    const sm = raw?.summary ?? {}
    const tierTags = txt(s.tierTags, '') || txt(sm.tierTags, '')
    return {
      code: txt(s.schoolCode, ''),
      name: txt(s.schoolName, '未知院校'),
      supervisor: txt(s.supervisor, ''),
      city: txt(s.city, ''),
      schoolType: txt(s.schoolType, ''),
      schoolLevel: txt(s.schoolLevel, ''),
      tierTags,
      tiers: splitTiers(tierTags),
      theme: schoolTheme({ schoolCode: s.schoolCode, schoolName: s.schoolName }),
      initial: schoolInitial({ schoolCode: s.schoolCode, schoolName: s.schoolName }),
      history: arr(raw?.history).map((x) => ({
        examYear: x?.examYear ?? null,
        minRank: toNum(x?.minRank),
        minScore: toNum(x?.minScore),
        planCount: toNum(x?.planCount)
      })).filter((x) => x.examYear !== null),
      summary: {
        latestYear: sm.latestYear ?? null,
        latestMinScore: toNum(sm.latestMinScore),
        latestMinRank: toNum(sm.latestMinRank),
        latestPlanCount: toNum(sm.latestPlanCount),
        bestRank: toNum(sm.bestRank),
        avgRank: toNum(sm.avgRank),
        historyCount: toNum(sm.historyCount)
      }
    }
  } catch {
    return null
  }
}

// ---------------------------------------------------------------- 并排对比表（行 = 维度，列 = 院校）
const compareRows = computed(() => {
  const its = items.value
  // 位次越小越好：仅在 >= 2 所院校时，标出该维度有效值的最小者。
  const bestOf = (get) => {
    if (its.length < 2) return () => false
    const vals = its.map(get).filter((v) => v !== null && Number.isFinite(v))
    if (!vals.length) return () => false
    const min = Math.min(...vals)
    return (it) => {
      const v = get(it)
      return v !== null && Number.isFinite(v) && v === min
    }
  }
  const mk = (label, get, opts = {}) => ({
    label, get, kind: opts.kind || 'text',
    best: !!opts.best, isBest: opts.best ? bestOf(get) : () => false
  })
  return [
    mk('校名', (it) => txt(it.name)),
    mk('层次标签', (it) => it.tiers, { kind: 'tiers' }),
    mk('属地', (it) => txt(it.city)),
    mk('类型', (it) => txt(it.schoolType)),
    mk('主管部门', (it) => txt(it.supervisor)),
    { label: '最新年份最低分', kind: 'score', get: (it) => it.summary, best: false, isBest: () => false },
    mk('最低位次', (it) => it.summary.latestMinRank, { kind: 'num', best: true }),
    mk('近三年平均位次', (it) => it.summary.avgRank, { kind: 'num', best: true }),
    mk('历史最佳位次', (it) => it.summary.bestRank, { kind: 'num', best: true }),
    mk('招生计划数', (it) => it.summary.latestPlanCount, { kind: 'num' }),
    mk('投档记录数', (it) => it.summary.historyCount, { kind: 'num' })
  ]
})

// ---------------------------------------------------------------- 图 1：历年最低位次多线折线（位次越小越好）
const allYears = computed(() => {
  const set = new Set()
  for (const it of items.value) for (const h of it.history) set.add(String(h.examYear))
  return [...set].sort()
})
function byYearMinRank(it) {
  const byYear = new Map()
  for (const h of it.history) {
    if (h.minRank === null) continue
    const y = String(h.examYear)
    const prev = byYear.get(y)
    if (prev === undefined || h.minRank < prev) byYear.set(y, h.minRank)
  }
  return byYear
}
function buildLine(p) {
  const years = allYears.value
  const series = items.value.map((it) => {
    const byYear = byYearMinRank(it)
    return {
      name: it.name, type: 'line', smooth: true, symbolSize: 6, connectNulls: true,
      lineStyle: { width: 2 }, itemStyle: { color: themeColor(it.theme, p) },
      data: years.map((y) => byYear.get(y) ?? null)
    }
  })
  return {
    backgroundColor: 'transparent',
    color: items.value.map((it) => themeColor(it.theme, p)),
    tooltip: {
      trigger: 'axis', backgroundColor: p.surface, borderColor: p.line, borderWidth: 1,
      textStyle: { color: p.text, fontSize: 11 },
      axisPointer: { type: 'line', lineStyle: { color: p.line } }
    },
    legend: { type: 'scroll', top: 0, textStyle: { color: p.text2, fontSize: 11 } },
    grid: { left: 64, right: 18, top: 40, bottom: 30 },
    xAxis: {
      type: 'category', data: years, boundaryGap: false,
      axisLine: { lineStyle: { color: p.line } }, axisTick: { show: false },
      axisLabel: { color: p.text2, fontSize: 10 }
    },
    yAxis: {
      type: 'value', inverse: true, name: '最低位次（越小越好）',
      nameTextStyle: { color: p.text3, fontSize: 10 },
      axisLine: { show: false }, axisLabel: { color: p.text2, fontSize: 10 },
      splitLine: { lineStyle: { color: p.line, type: 'dashed' } }
    },
    series
  }
}

// ---------------------------------------------------------------- 图 2：综合能力雷达（归一化到 0~100，位次反向）
const RADAR_DIMS = ['最低位次', '平均位次', '招生计划', '层次分', '投档记录']
function normalize(values, reverse) {
  const valid = values.filter((v) => v !== null && Number.isFinite(v))
  if (!valid.length) return values.map(() => 0)
  const min = Math.min(...valid)
  const max = Math.max(...valid)
  const range = max - min
  return values.map((v) => {
    if (v === null || !Number.isFinite(v)) return 0
    if (range <= 0) return 60
    const t = (v - min) / range
    return Math.round((reverse ? 1 - t : t) * 100)
  })
}
function tierScore(tierTags) {
  const t = String(tierTags || '')
  if (t.includes('985')) return 100
  if (t.includes('211')) return 82
  if (t.includes('双一流')) return 72
  if (t.includes('省重点')) return 52
  if (t.includes('普通')) return 32
  return 20
}
function buildRadar(p) {
  const its = items.value
  const scores = [
    normalize(its.map((it) => it.summary.latestMinRank), true),
    normalize(its.map((it) => it.summary.avgRank), true),
    normalize(its.map((it) => it.summary.latestPlanCount), false),
    its.map((it) => tierScore(it.tierTags)),
    normalize(its.map((it) => it.summary.historyCount), false)
  ]
  return {
    backgroundColor: 'transparent',
    color: its.map((it) => themeColor(it.theme, p)),
    tooltip: {
      trigger: 'item', backgroundColor: p.surface, borderColor: p.line,
      borderWidth: 1, textStyle: { color: p.text, fontSize: 11 }
    },
    legend: { type: 'scroll', bottom: 0, textStyle: { color: p.text2, fontSize: 11 } },
    radar: {
      center: ['50%', '48%'], radius: '62%',
      indicator: RADAR_DIMS.map((name) => ({ name, max: 100 })),
      axisName: { color: p.text2, fontSize: 10 },
      axisLine: { lineStyle: { color: p.line } },
      splitLine: { lineStyle: { color: p.line } },
      splitArea: { areaStyle: { color: [p.surface2, p.surface] } }
    },
    series: [{
      type: 'radar', symbolSize: 4,
      data: its.map((it, i) => ({
        name: it.name,
        value: scores.map((d) => d[i]),
        lineStyle: { width: 2, color: themeColor(it.theme, p) },
        itemStyle: { color: themeColor(it.theme, p) },
        areaStyle: { color: themeColor(it.theme, p), opacity: 0.08 }
      }))
    }]
  }
}

// ---------------------------------------------------------------- 图表生命周期
function renderCharts() {
  if (!items.value.length) return
  const p = palette()
  if (lineEl.value) {
    if (!lineChart) lineChart = echarts.init(lineEl.value)
    lineChart.setOption(buildLine(p), true)
    lineChart.resize()
  }
  if (radarEl.value) {
    if (!radarChart) radarChart = echarts.init(radarEl.value)
    radarChart.setOption(buildRadar(p), true)
    radarChart.resize()
  }
}
function disposeCharts() {
  if (lineChart) { lineChart.dispose(); lineChart = null }
  if (radarChart) { radarChart.dispose(); radarChart = null }
}
function onResize() { lineChart?.resize(); radarChart?.resize() }

// ---------------------------------------------------------------- 数据加载（Promise.allSettled 容错）
async function load() {
  error.value = ''
  disposeCharts()
  if (!codes.value.length) {
    items.value = []
    meta.value = {}
    loading.value = false
    return
  }
  loading.value = true
  // 整体调用容错：接口失败不会白屏，保留可读错误。
  const [res] = await Promise.allSettled([
    compareSchools(codes.value, { provinceCode: '42', category: '物理类' })
  ])
  if (res.status === 'rejected') {
    error.value = res.reason?.message || '院校对比数据加载失败'
    items.value = []
    meta.value = {}
  } else {
    const raw = res.value ?? {}
    meta.value = {
      dataVersion: txt(raw.dataVersion, ''),
      demoData: !!raw.demoData,
      sourceNote: txt(raw.sourceNote, ''),
      provinceCode: txt(raw.provinceCode, '42'),
      category: txt(raw.category, '物理类'),
      count: toNum(raw.count)
    }
    // 逐项容错：某所院校字段缺失 / 异常不影响其余院校参与对比。
    const settled = await Promise.allSettled(
      arr(raw.items).map((it) => Promise.resolve().then(() => normalizeItem(it)))
    )
    items.value = settled
      .filter((s) => s.status === 'fulfilled' && s.value)
      .map((s) => s.value)
    if (!items.value.length) error.value = '未找到这些院校代码的对比数据，请确认代码是否正确'
  }
  loading.value = false
  await nextTick()
  renderCharts()
}

function removeSchool(code) {
  const next = codes.value.filter((c) => c !== code)
  const q = { ...route.query }
  if (next.length) q.codes = next.join(',')
  else delete q.codes
  router.replace({ name: 'school-compare', query: q })
}

watch(() => route.query.codes, () => { load() })
watch(theme, async () => { await nextTick(); renderCharts() })
onMounted(() => {
  window.addEventListener('resize', onResize)
  load()
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  disposeCharts()
})
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <nav class="flex flex-wrap items-center gap-2 text-[11px] faint" aria-label="面包屑">
        <RouterLink :to="{ name: 'schools', query: schoolsQuery }" class="link">院校库</RouterLink>
        <span>/</span>
        <span class="muted">院校对比</span>
      </nav>
      <div class="btn-group">
        <RouterLink :to="{ name: 'schools', query: schoolsQuery }" class="btn btn-slate">← 返回院校库</RouterLink>
      </div>
    </div>

    <div>
      <p v-reveal="0" class="eyebrow">School compare / 院校对比</p>
      <h1 v-reveal="1" class="mt-2 text-[22px] font-semibold tracking-[-0.02em]">把几所院校放在同一把尺子上</h1>
      <p v-reveal="2" class="mt-2 max-w-[76ch] text-[12px] leading-6 muted">
        并排比较层次、属地与历年位次；位次越小越靠前，最优项自动高亮。最多同时对比 4 所院校。
      </p>
    </div>

    <!-- 加载骨架 -->
    <div v-if="loading" class="grid gap-4 sm:grid-cols-2">
      <div v-for="n in 2" :key="n" class="glass shimmer h-40 p-6">
        <div class="h-3 w-40 rounded-full placeholder-bar"></div>
        <div class="mt-4 h-2.5 w-60 rounded-full placeholder-bar"></div>
        <div class="mt-3 h-2.5 w-44 rounded-full placeholder-bar"></div>
      </div>
    </div>

    <!-- 无 codes 引导空态 -->
    <div v-else-if="!codes.length" class="glass empty-state">
      <p class="text-[14px] font-semibold">还没有选择要对比的院校</p>
      <p class="mt-2 text-[12px] muted">
        前往院校库浏览院校，把要对比的 2–4 所院校代码加入链接的 codes 参数即可开始对比。
      </p>
      <RouterLink :to="{ name: 'schools', query: schoolsQuery }" class="btn btn-blue mt-5">
        前往院校库 <span class="link-arrow">→</span>
      </RouterLink>
    </div>

    <!-- 接口失败 / 数据缺失 -->
    <div v-else-if="error" class="glass p-8 text-center">
      <p class="text-[14px] font-semibold">{{ error }}</p>
      <p class="mt-2 text-[12px] muted">请稍后重试，或返回院校库重新选择院校代码。</p>
      <div class="btn-group mt-5 justify-center">
        <button type="button" class="btn btn-blue" @click="load">重新加载</button>
        <RouterLink :to="{ name: 'schools', query: schoolsQuery }" class="btn btn-slate">返回院校库</RouterLink>
      </div>
    </div>

    <template v-else-if="items.length">
      <!-- 每校一张主题色卡 -->
      <div class="cmp-cards">
        <div v-for="(it, i) in items" :key="it.code + i" v-reveal="i"
          class="cmp-card" :data-school-theme="it.theme">
          <span class="cmp-badge" aria-hidden="true">{{ it.initial }}</span>
          <div class="cmp-card-main">
            <p class="cmp-code">{{ it.code || '—' }}</p>
            <p class="cmp-name">{{ it.name }}</p>
            <div class="cmp-card-tags">
              <span v-for="t in it.tiers" :key="t" class="pill" :class="tierPill(t)">{{ t }}</span>
              <span v-if="!it.tiers.length" class="pill pill-neutral">暂无层次标签</span>
            </div>
          </div>
          <button type="button" class="btn btn-slate cmp-remove" @click="removeSchool(it.code)">
            <span class="btn-glyph">✕</span> 移除
          </button>
        </div>
      </div>

      <!-- 数据口径状态条 -->
      <div class="glass cmp-status">
        <span class="pill" :class="meta.demoData ? 'pill-amber' : 'pill-mint'">
          {{ meta.demoData ? '演示数据（合成值）' : '已发布数据' }}
        </span>
        <span class="text-[11px] faint">数据版本 {{ meta.dataVersion || '—' }}</span>
        <span class="text-[11px] faint">{{ meta.provinceCode }} · {{ meta.category }}</span>
        <span class="text-[11px] faint">返回 {{ fmtNum(meta.count) }} 所院校</span>
        <span class="cmp-source text-[11px] faint">{{ meta.sourceNote || '统计口径以当前数据版本为准' }}</span>
      </div>

      <!-- 并排对比表 -->
      <div v-reveal="0" class="glass cmp-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>对比维度</th>
              <th v-for="(it, i) in items" :key="'h' + i">
                <span class="cmp-head" :data-school-theme="it.theme">
                  <span class="cmp-dot" aria-hidden="true"></span>{{ colLabels[i] }}
                </span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in compareRows" :key="row.label">
              <th class="cmp-label">{{ row.label }}</th>
              <td v-for="(it, ci) in items" :key="row.label + ci" class="cmp-cell"
                :class="{ 'is-best': row.best && row.isBest(it) }">
                <template v-if="row.kind === 'tiers'">
                  <span v-if="row.get(it).length" class="cmp-tiers">
                    <span v-for="t in row.get(it)" :key="t" class="pill" :class="tierPill(t)">{{ t }}</span>
                  </span>
                  <span v-else class="faint">—</span>
                </template>
                <template v-else-if="row.kind === 'score'">
                  {{ fmtNum(row.get(it).latestMinScore) }}
                  <span v-if="row.get(it).latestYear != null" class="faint text-[10px]">
                    {{ row.get(it).latestYear }} 年
                  </span>
                </template>
                <template v-else-if="row.kind === 'num'">{{ fmtNum(row.get(it)) }}</template>
                <template v-else>{{ row.get(it) }}</template>
                <span v-if="row.best && row.isBest(it)" class="best-flag">最优</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 两个 ECharts 图 -->
      <div class="grid gap-4 lg:grid-cols-2">
        <section v-reveal="0" class="glass p-5">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <p class="text-[13px] font-semibold">历年最低位次趋势</p>
            <span class="pill pill-sky">位次越小越好</span>
          </div>
          <p class="mt-1 text-[11px] faint">纵轴已反转，越靠上代表位次越靠前</p>
          <div class="cmp-chart-wrap">
            <div ref="lineEl" class="cmp-chart"></div>
            <div v-if="!allYears.length" class="cmp-chart-empty">暂无历年投档位次数据</div>
          </div>
        </section>

        <section v-reveal="1" class="glass p-5">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <p class="text-[13px] font-semibold">综合能力雷达</p>
            <span class="pill pill-violet">归一化 0–100</span>
          </div>
          <p class="mt-1 text-[11px] faint">
            最低位次、平均位次反向归一化，计划数、层次分、投档记录数正向归一化
          </p>
          <div class="cmp-chart-wrap">
            <div ref="radarEl" class="cmp-chart"></div>
          </div>
        </section>
      </div>
    </template>

    <p class="text-center text-[11px] leading-5 faint">
      当前为演示数据集（合成值），非真实录取分数；最终以各省教育考试院公布为准
    </p>
  </div>
</template>

<style scoped>
.cmp-cards {
  display: grid; gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}
.cmp-card {
  position: relative; display: flex; align-items: flex-start; gap: 12px;
  padding: 16px; overflow: hidden; border-radius: 14px;
  border: 1px solid color-mix(in srgb, var(--card-c) 30%, var(--line));
  background:
    radial-gradient(120% 120% at 0% 0%, color-mix(in srgb, var(--card-soft) 82%, transparent) 0%, transparent 70%),
    var(--surface);
  -webkit-backdrop-filter: blur(10px) saturate(140%);
  backdrop-filter: blur(10px) saturate(140%);
  transition: transform .3s var(--ease), box-shadow .3s var(--ease), border-color .3s;
}
.cmp-card::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px;
  background: linear-gradient(90deg, var(--card-c), color-mix(in srgb, var(--card-c) 16%, transparent));
}
.cmp-card > * { position: relative; z-index: 1; }
.cmp-card:hover {
  transform: translateY(-3px); border-color: var(--card-c);
  box-shadow: 0 12px 28px color-mix(in srgb, var(--card-c) 20%, transparent);
}
.cmp-badge {
  display: grid; place-items: center; width: 40px; height: 40px; flex: none;
  border-radius: 12px; font-size: 16px; font-weight: 700;
  color: var(--card-c); background: var(--card-soft);
  border: 1px solid color-mix(in srgb, var(--card-c) 28%, transparent);
}
.cmp-card-main { min-width: 0; flex: 1; }
.cmp-code { font-size: 10px; letter-spacing: .06em; color: var(--text-3); font-variant-numeric: tabular-nums; }
.cmp-name { margin-top: 2px; font-size: 15px; font-weight: 600; color: var(--text); line-height: 1.45; }
.cmp-card-tags { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 8px; }
.cmp-remove { flex: none; }

.cmp-status { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 16px; padding: 10px 16px; }
.cmp-source { flex: 1 1 100%; }

.cmp-scroll { overflow-x: auto; }
.cmp-scroll .data-table { min-width: 560px; }
.cmp-scroll .data-table th, .cmp-scroll .data-table td { padding: 11px 14px; vertical-align: middle; }
.cmp-label { white-space: nowrap; text-align: left; font-weight: 500; color: var(--text-2); }
.cmp-head { display: inline-flex; align-items: center; gap: 7px; font-weight: 600; color: var(--text); white-space: nowrap; }
.cmp-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: var(--card-c, var(--accent)); }
.cmp-tiers { display: inline-flex; flex-wrap: wrap; gap: 4px; }
.cmp-cell { font-variant-numeric: tabular-nums; }
.cmp-cell.is-best { background: var(--accent-soft); }
.best-flag {
  display: inline-block; margin-left: 8px; padding: 2px 8px; border-radius: 999px;
  font-size: 10px; font-weight: 600; color: var(--mint); background: var(--mint-bg);
}

.cmp-chart-wrap { position: relative; margin-top: 10px; }
.cmp-chart { width: 100%; height: 260px; }
.cmp-chart-empty {
  position: absolute; inset: 0; display: grid; place-items: center;
  border-radius: 10px; background: color-mix(in srgb, var(--surface) 55%, transparent);
  font-size: 12px; color: var(--text-3); text-align: center; padding: 0 16px;
}
</style>
