<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getMajors, getMajor } from '@/api'

const items = ref([])            // getMajors 全量目录，前端分组
const keyword = ref('')
const activeCategory = ref('')   // '' 表示全部门类
const demoData = ref(false)
const loading = ref(true)
const error = ref('')

const selectedCode = ref('')
const selectedMajor = ref(null)  // 列表项，先于详情接口渲染基础信息
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')

// 快速切换专业时，只有最后一次请求的结果可以落地
let detailSeq = 0

function code(v) { return String(v ?? '').trim() }
function text(v, fallback = '—') { const s = String(v ?? '').trim(); return s || fallback }
function toNum(v) {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}
function fmt(v, suffix = '') {
  const n = toNum(v)
  return n === null ? '—' : `${n.toLocaleString()}${suffix}`
}

// 左侧门类列表与顶部 chip 共用全量数据的计数
const categories = computed(() => {
  const m = new Map()
  for (const it of items.value) {
    const c = text(it.category, '未分类')
    m.set(c, (m.get(c) || 0) + 1)
  }
  return [...m.entries()].map(([name, count]) => ({ name, count }))
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return items.value.filter((it) => {
    if (activeCategory.value && text(it.category, '未分类') !== activeCategory.value) return false
    if (!kw) return true
    return [it.majorName, it.majorCode, it.category, it.majorClass]
      .some((v) => String(v ?? '').toLowerCase().includes(kw))
  })
})

// 学科门类 → 专业类 → 专业
const tree = computed(() => {
  const byCat = new Map()
  for (const it of filtered.value) {
    const cat = text(it.category, '未分类')
    const cls = text(it.majorClass, '未分类专业类')
    if (!byCat.has(cat)) byCat.set(cat, new Map())
    const byCls = byCat.get(cat)
    if (!byCls.has(cls)) byCls.set(cls, [])
    byCls.get(cls).push(it)
  }
  return [...byCat.entries()].map(([name, classes]) => ({
    name,
    majorCount: [...classes.values()].reduce((s, arr) => s + arr.length, 0),
    classes: [...classes.entries()].map(([cls, majors]) => ({ name: cls, majors }))
  }))
})

const majorInfo = computed(() => detail.value?.major ?? selectedMajor.value ?? {})

const schools = computed(() => {
  const list = detail.value?.schools
  return Array.isArray(list) ? list : []
})

// getMajor 未单独返回选科要求，按开设院校计划中的 reqName / reqExpr 去重汇总
const requirements = computed(() => {
  const seen = new Set()
  const out = []
  for (const s of schools.value) {
    const name = String(s.reqName ?? '').trim()
    const expr = String(s.reqExpr ?? '').trim()
    const label = name ? (expr && expr !== name ? `${name} · ${expr}` : name) : (expr || '不限')
    const key = `${name}|${expr}`
    if (seen.has(key)) continue
    seen.add(key)
    out.push(label)
  }
  return out
})

function setCategory(name) {
  activeCategory.value = activeCategory.value === name ? '' : name
}

function selectMajor(m) {
  const c = code(m?.majorCode)
  if (!c || c === selectedCode.value) return
  selectedCode.value = c
  selectedMajor.value = m
  loadDetail(c)
}

async function loadDetail(c) {
  const token = ++detailSeq
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    const res = await getMajor(c)
    if (token !== detailSeq) return
    detail.value = res ?? {}
  } catch (e) {
    if (token !== detailSeq) return
    detailError.value = e?.message || '专业详情加载失败'
  }
  if (token === detailSeq) detailLoading.value = false
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getMajors({})
    items.value = Array.isArray(res?.items) ? res.items : []
    demoData.value = Boolean(res?.demoData)
    // 默认选中首个专业，避免右侧详情面板留白
    if (items.value.length && !selectedCode.value) selectMajor(items.value[0])
  } catch (e) {
    error.value = e?.message || '专业目录加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
// 卸载后仍在进行中的详情请求一律作废
onBeforeUnmount(() => { detailSeq += 1 })
</script>

<template>
  <div class="majors-page space-y-5">
    <!-- 标题区 -->
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p v-reveal="0" class="eyebrow">Major catalog / 专业目录</p>
        <h1 v-reveal="1" class="mt-2 text-[22px] font-semibold tracking-[-0.02em]">专业库 · 三级目录</h1>
        <p v-reveal="2" class="mt-2 max-w-[76ch] text-[12px] leading-6 muted">
          按学科门类 → 专业类 → 专业逐级浏览；点击任一专业即可查看修业年限、选科要求与开设院校。
        </p>
      </div>
      <div v-reveal="2" class="btn-group">
        <RouterLink to="/schools" class="btn btn-slate">← 院校库</RouterLink>
        <RouterLink to="/insights" class="btn btn-teal"><span class="btn-glyph">◎</span> 数据洞察</RouterLink>
      </div>
    </div>

    <!-- 搜索 + 门类 chip 过滤 -->
    <div v-reveal="0" class="glass p-5">
      <div class="major-filter">
        <input v-model="keyword" class="field" placeholder="搜索专业名称 / 代码 / 专业类"
          aria-label="专业检索" />
        <span class="pill" :class="demoData ? 'pill-amber' : 'pill-mint'">
          {{ demoData ? '演示数据（合成值）' : '已发布数据' }}
        </span>
        <span class="text-[11px] faint">共 {{ filtered.length }} / {{ items.length }} 个专业</span>
      </div>
      <div class="mt-3 flex flex-wrap gap-2">
        <button type="button" class="chip" :class="{ 'is-active': activeCategory === '' }"
          @click="setCategory('')">全部门类</button>
        <button v-for="c in categories" :key="c.name" type="button" class="chip"
          :class="{ 'is-active': activeCategory === c.name }" @click="setCategory(c.name)">
          {{ c.name }} <span class="chip-count">{{ c.count }}</span>
        </button>
      </div>
      <div v-if="error" class="mt-4 rounded-lg px-3 py-2 text-[12px]"
        style="background: var(--rose-bg); color: var(--rose)">
        {{ error }}
        <button type="button" class="link ml-2 text-[12px]" @click="load">重新加载</button>
      </div>
    </div>

    <!-- 主体三栏：门类 / 专业类 / 详情 -->
    <div class="grid gap-4 lg:grid-cols-12">
      <!-- 左：门类列表 -->
      <aside v-reveal="1" class="glass p-4 lg:col-span-3">
        <p class="text-[13px] font-semibold">学科门类</p>
        <hr class="hairline my-3" />
        <div v-if="loading" class="space-y-2">
          <div v-for="i in 5" :key="i" class="h-8 rounded-lg placeholder-bar shimmer"></div>
        </div>
        <div v-else class="cat-list">
          <button type="button" class="cat-item" :class="{ 'is-active': activeCategory === '' }"
            @click="setCategory('')">
            <span>全部门类</span><span class="cat-count">{{ items.length }}</span>
          </button>
          <button v-for="c in categories" :key="c.name" type="button" class="cat-item"
            :class="{ 'is-active': activeCategory === c.name }" @click="setCategory(c.name)">
            <span>{{ c.name }}</span><span class="cat-count">{{ c.count }}</span>
          </button>
          <p v-if="!categories.length" class="py-6 text-center text-[12px] faint">暂无门类数据</p>
        </div>
      </aside>

      <!-- 中：专业类分组卡片 -->
      <section class="space-y-4 lg:col-span-5">
        <div v-if="loading" class="glass shimmer h-44 p-5">
          <div class="h-3 w-32 rounded-full placeholder-bar"></div>
          <div class="mt-4 h-2.5 w-56 rounded-full placeholder-bar"></div>
          <div class="mt-4 flex flex-wrap gap-2">
            <div v-for="i in 6" :key="i" class="h-7 w-24 rounded-lg placeholder-bar"></div>
          </div>
        </div>
        <template v-else-if="tree.length">
          <div v-for="(cat, ci) in tree" :key="cat.name" class="glass p-4">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <p class="text-[13px] font-semibold">{{ cat.name }}</p>
              <span class="pill pill-sky">{{ cat.majorCount }} 个专业 · {{ cat.classes.length }} 个专业类</span>
            </div>
            <hr class="hairline my-3" />
            <div class="space-y-3">
              <div v-for="cls in cat.classes" :key="cls.name" class="major-class">
                <p class="major-class-name">{{ cls.name }}</p>
                <div class="mt-2 flex flex-wrap gap-2">
                  <button v-for="m in cls.majors" :key="code(m.majorCode)" type="button"
                    class="major-item" :class="{ 'is-active': selectedCode === code(m.majorCode) }"
                    @click="selectMajor(m)">
                    <span class="major-code">{{ text(m.majorCode) }}</span>
                    <span class="major-name">{{ text(m.majorName) }}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </template>
        <div v-else class="empty-state text-[12px]">
          {{ items.length ? '没有匹配的专业，试试放宽关键词或切换门类' : '专业目录暂无数据' }}
        </div>
      </section>

      <!-- 右：详情面板 -->
      <aside class="lg:col-span-4">
        <div class="glass p-5 major-detail lg:sticky lg:top-24">
          <template v-if="!selectedMajor">
            <p class="eyebrow">Major detail / 专业详情</p>
            <p class="mt-6 text-[12px] leading-6 faint">
              请从中间的目录卡片中选择一个专业，查看修业年限、选科要求与开设院校。
            </p>
          </template>

          <template v-else>
            <div class="flex flex-wrap items-start justify-between gap-2">
              <div class="min-w-[160px]">
                <p class="eyebrow">Major detail / 专业详情</p>
                <h2 class="mt-2 text-[17px] font-semibold">{{ text(majorInfo.majorName) }}</h2>
                <p class="mt-1 font-mono text-[11px] faint">{{ text(majorInfo.majorCode) }}</p>
              </div>
              <span class="pill pill-teal">已选中</span>
            </div>

            <div class="mt-4 flex flex-wrap gap-2">
              <span class="pill pill-sky">{{ text(majorInfo.category) }}</span>
              <span class="pill pill-violet">{{ text(majorInfo.majorClass) }}</span>
            </div>

            <hr class="hairline my-4" />
            <div class="space-y-3 text-[12px]">
              <div class="flex justify-between gap-4"><span class="muted">专业代码</span><span class="font-mono">{{ text(majorInfo.majorCode) }}</span></div>
              <div class="flex justify-between gap-4"><span class="muted">专业名称</span><span>{{ text(majorInfo.majorName) }}</span></div>
              <div class="flex justify-between gap-4"><span class="muted">学科门类</span><span>{{ text(majorInfo.category) }}</span></div>
              <div class="flex justify-between gap-4"><span class="muted">专业类</span><span>{{ text(majorInfo.majorClass) }}</span></div>
              <div class="flex justify-between gap-4"><span class="muted">修业年限</span><span>{{ fmt(majorInfo.degreeYears, ' 年') }}</span></div>
            </div>

            <hr class="hairline my-4" />
            <p class="text-[12px] font-semibold">选科要求</p>
            <div v-if="detailLoading" class="mt-3 space-y-2">
              <div class="h-6 w-3/4 rounded-full placeholder-bar shimmer"></div>
              <div class="h-6 w-1/2 rounded-full placeholder-bar shimmer"></div>
            </div>
            <div v-else-if="detailError" class="mt-3 rounded-lg px-3 py-2 text-[12px]"
              style="background: var(--rose-bg); color: var(--rose)">{{ detailError }}</div>
            <div v-else-if="requirements.length" class="mt-3 flex flex-wrap gap-2">
              <span v-for="(r, i) in requirements" :key="`${r}-${i}`" class="pill pill-violet">{{ r }}</span>
            </div>
            <p v-else class="mt-3 text-[12px] faint">暂无选科要求数据</p>

            <hr class="hairline my-4" />
            <div class="flex items-center justify-between gap-2">
              <p class="text-[12px] font-semibold">开设院校</p>
              <span class="pill pill-neutral">{{ fmt(detail?.schoolCount ?? schools.length) }} 所</span>
            </div>
            <div v-if="detailLoading" class="mt-3 space-y-2">
              <div v-for="i in 3" :key="i" class="h-9 rounded-lg placeholder-bar shimmer"></div>
            </div>
            <div v-else-if="detailError" class="mt-3 text-[12px] faint">开设院校暂不可用</div>
            <div v-else-if="schools.length" class="school-list mt-3">
              <div v-for="(s, i) in schools" :key="`${code(s.schoolCode)}-${i}`" class="school-row">
                <div class="flex flex-wrap items-center justify-between gap-2">
                  <RouterLink v-if="code(s.schoolCode)" class="link text-[12px] font-semibold"
                    :to="`/schools/${code(s.schoolCode)}`">{{ text(s.schoolName, code(s.schoolCode)) }}</RouterLink>
                  <span v-else class="text-[12px] font-semibold muted">{{ text(s.schoolName) }}</span>
                  <span class="pill pill-neutral">计划 {{ fmt(s.planCount) }}</span>
                </div>
                <p class="mt-1 text-[11px] faint">
                  {{ [text(s.reqName, '不限'), String(s.reqExpr ?? '').trim()].filter(Boolean).join(' · ') || '不限' }}
                </p>
              </div>
            </div>
            <p v-else class="mt-3 text-[12px] faint">暂无开设院校数据</p>

            <p class="mt-4 text-[11px] faint">数据版本 {{ text(detail?.dataVersion) }}</p>
          </template>
        </div>
      </aside>
    </div>

    <p v-reveal="0" class="text-center text-[11px] faint">
      当前为演示数据集（合成值），非真实录取数据；最终以各省教育考试院公布为准
    </p>
  </div>
</template>

<style scoped>
/* 顶部检索行：覆盖全局 .field 的 width:100%，让输入与计数同排 */
.major-filter { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.major-filter > .field { width: auto; flex: 1 1 260px; max-width: 360px; min-width: 0; }

.chip-count {
  min-width: 18px; padding: 0 5px; border-radius: 999px;
  font-size: 10px; line-height: 16px; text-align: center;
  background: var(--surface-2); color: var(--text-3);
}

/* 左侧门类列表 */
.cat-list { display: flex; flex-direction: column; gap: 4px; }
.cat-item {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  width: 100%; padding: 9px 11px; border-radius: 10px; border: 1px solid transparent;
  background: transparent; color: var(--text-2); font-size: 12px; text-align: left; cursor: pointer;
  transition: background-color .3s var(--ease), color .3s, border-color .3s, transform .3s var(--ease);
}
.cat-item:hover { background: var(--surface-2); color: var(--text); transform: translateX(2px); }
.cat-item.is-active {
  background: var(--accent-soft); color: var(--accent-text);
  border-color: color-mix(in srgb, var(--accent) 30%, var(--line)); font-weight: 600;
}
.cat-count {
  flex: none; min-width: 22px; padding: 1px 6px; border-radius: 999px;
  background: var(--surface-2); color: var(--text-3); font-size: 10px; text-align: center;
}
.cat-item.is-active .cat-count { background: var(--accent-soft); color: var(--accent-text); }

/* 中间专业类分组 */
.major-class { padding-left: 10px; border-left: 2px solid var(--line); }
.major-class-name { font-size: 11px; font-weight: 600; color: var(--text-2); letter-spacing: .02em; }
.major-item {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 6px 10px; border-radius: 9px; border: 1px solid var(--line);
  background: var(--surface-2); color: var(--text); font-size: 12px; cursor: pointer;
  transition: transform .3s var(--ease), border-color .3s, background-color .3s, box-shadow .3s;
}
.major-item:hover { transform: translateY(-2px); border-color: var(--accent); }
.major-item.is-active {
  border-color: var(--accent); background: var(--accent-soft); color: var(--accent-text);
  box-shadow: 0 4px 14px color-mix(in srgb, var(--accent) 16%, transparent);
}
.major-code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 10px; color: var(--text-3); }
.major-item.is-active .major-code { color: var(--accent-text); }
.major-name { font-weight: 500; white-space: nowrap; }

/* 右侧详情 */
.major-detail { max-height: calc(100dvh - 110px); overflow-y: auto; }
.school-list { max-height: 280px; overflow-y: auto; padding-right: 4px; }
.school-row { padding: 9px 0; border-bottom: 1px solid var(--line-soft); }
.school-row:last-child { border-bottom: 0; }

@media (max-width: 560px) {
  .major-filter > .field { flex: 1 1 100%; max-width: none; }
}
</style>
