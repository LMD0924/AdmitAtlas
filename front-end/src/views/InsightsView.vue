<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getInsights } from '@/api'
import { useTheme } from '@/composables/useTheme'

echarts.use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const { theme } = useTheme()

// ---------------------------------------------------------------- 工具：SQL 聚合值一律安全转数字
function toNum(v) {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}
function n(v, fallback = 0) { const x = toNum(v); return x === null ? fallback : x }
function fmtNum(v) { const x = toNum(v); return x === null ? '—' : x.toLocaleString() }
function arr(v) { return Array.isArray(v) ? v : [] }
/** 从 :root 读取当前主题色，保证图表文字/轴线在明暗两侧都可读 */
function palette() {
  const css = getComputedStyle(document.documentElement)
  const get = (name, fb) => css.getPropertyValue(name).trim() || fb
  return {
    text: get('--text', '#1d2534'),
    text2: get('--text-2', '#626d81'),
    text3: get('--text-3', '#9aa4b6'),
    line: get('--line', '#e7ebf4'),
    surface: get('--surface', '#ffffff'),
    accent: get('--accent', '#4a6cf7'),
    sky: get('--sky', '#3b82f6'),
    violet: get('--violet', '#8b5cf6'),
    teal: get('--teal', '#14a89a'),
    amber: get('--amber', '#e39620'),
    rose: get('--rose', '#e26363'),
    mint: get('--mint', '#18a56d'),
    cyan: get('--hud-cyan', '#3aaee0')
  }
}
/** 给 6 位十六进制色追加透明度，供 canvas 渐变使用 */
function alpha(hex, a = '44') {
  const h = String(hex || '').trim()
  return /^#[0-9a-fA-F]{6}$/.test(h) ? `${h}${a}` : h
}

// ---------------------------------------------------------------- 筛选条件
const PROVINCES = [
  { value: '11', label: '北京' }, { value: '31', label: '上海' },
  { value: '32', label: '江苏' }, { value: '33', label: '浙江' },
  { value: '37', label: '山东' }, { value: '41', label: '河南' },
  { value: '42', label: '湖北' }, { value: '43', label: '湖南' },
  { value: '44', label: '广东' }, { value: '51', label: '四川' }
]
const YEARS = [2026, 2025, 2024, 2023]
const CATEGORIES = ['物理类', '历史类', '综合', '理科', '文科']

const provinceCode = ref('42')
const examYear = ref(2026)
const category = ref('物理类')

const data = ref(null)
const loading = ref(true)
const error = ref('')
let loadSeq = 0 // 快速切换筛选时丢弃过期响应

// ---------------------------------------------------------------- 归一化后的各图表数据
const overview = computed(() => data.value?.overview ?? {})
const kpis = computed(() => [
  { label: '院校数', value: overview.value.schoolCount, color: 'var(--sky)', tag: 'DIM.SCHOOL' },
  { label: '专业数', value: overview.value.majorCount, color: 'var(--violet)', tag: 'DIM.MAJOR' },
  { label: '投档记录数', value: overview.value.admissionRowCount, color: 'var(--teal)', tag: 'FACT.ADMISSION' },
  { label: '一分一段行数', value: overview.value.segmentRowCount, color: 'var(--amber)', tag: 'FACT.SEGMENT' },
  { label: '规则数', value: overview.value.policyCount, color: 'var(--rose)', tag: 'RULE.POLICY' }
].map((k) => ({ ...k, display: fmtNum(k.value) })))

const scoreDist = computed(() => arr(data.value?.scoreDistribution).map((r) => ({
  bucket: String(r.bucket ?? '—'), people: n(r.people)
})))
const tierDist = computed(() => arr(data.value?.tierDistribution).map((r) => ({
  label: String(r.label ?? '未知'), count: n(r.count)
})))
const typeDist = computed(() => arr(data.value?.typeDistribution).map((r) => ({
  label: String(r.label ?? '未知'), count: n(r.count)
})))
const trend = computed(() => arr(data.value?.scoreTrend).map((r) => ({
  examYear: r.examYear ?? '—',
  rowCount: n(r.rowCount),
  avgScore: n(r.avgScore, null),
  avgRank: n(r.avgRank, null)
})).sort((a, b) => (Number(a.examYear) || 0) - (Number(b.examYear) || 0)))
const topSchools = computed(() => arr(data.value?.topSchools).map((r) => ({
  schoolName: String(r.schoolName ?? r.schoolCode ?? '—'),
  bestRank: toNum(r.bestRank),
  bestScore: toNum(r.bestScore)
})).sort((a, b) =>
  (a.bestRank ?? Number.POSITIVE_INFINITY) - (b.bestRank ?? Number.POSITIVE_INFINITY)
).slice(0, 10))

// ---------------------------------------------------------------- 图表实例与容器
const scoreEl = ref(null)
const tierEl = ref(null)
const trendEl = ref(null)
const topEl = ref(null)
const typeEl = ref(null)
const chartEls = { score: scoreEl, tier: tierEl, trend: trendEl, top: topEl, type: typeEl }
const instances = {}

function ensureChart(name) {
  const el = chartEls[name]?.value
  if (!el) return null
  if (!instances[name]) instances[name] = echarts.init(el)
  return instances[name]
}
function disposeCharts() {
  for (const k of Object.keys(instances)) { instances[k].dispose(); delete instances[k] }
}

// ---------------------------------------------------------------- 各图配置（主题色由 palette() 注入）
function tooltipBase(p, trigger = 'axis') {
  const base = {
    trigger,
    backgroundColor: p.surface,
    borderColor: p.line,
    borderWidth: 1,
    textStyle: { color: p.text, fontSize: 11 }
  }
  // item 触发不需要 axisPointer，避免传入非法 type
  if (trigger === 'axis') base.axisPointer = { type: 'shadow', lineStyle: { color: p.line } }
  return base
}
const seriesColors = (p) => [p.accent, p.violet, p.teal, p.amber, p.rose, p.mint, p.sky, p.cyan]

const builders = {
  // 01 分数段人数分布柱状图
  score: (p) => ({
    color: [p.accent],
    backgroundColor: 'transparent',
    tooltip: tooltipBase(p, 'axis'),
    grid: { left: 58, right: 16, top: 26, bottom: 46 },
    xAxis: {
      type: 'category', data: scoreDist.value.map((r) => r.bucket),
      name: '分数段', nameLocation: 'end', nameTextStyle: { color: p.text3, fontSize: 10 },
      axisLine: { lineStyle: { color: p.line } }, axisTick: { show: false },
      axisLabel: { color: p.text2, fontSize: 10, rotate: scoreDist.value.length > 12 ? 40 : 0 }
    },
    yAxis: {
      type: 'value', name: '人数', nameTextStyle: { color: p.text3, fontSize: 10 },
      axisLine: { show: false }, axisLabel: { color: p.text2, fontSize: 10 },
      splitLine: { lineStyle: { color: p.line, type: 'dashed' } }
    },
    series: [{
      name: '人数', type: 'bar', barMaxWidth: 30,
      data: scoreDist.value.map((r) => r.people),
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
          { offset: 0, color: p.accent }, { offset: 1, color: p.cyan }
        ] }
      }
    }]
  }),

  // 02 院校层次占比环形图
  tier: (p) => ({
    color: seriesColors(p),
    backgroundColor: 'transparent',
    tooltip: { ...tooltipBase(p, 'item'), formatter: '{b} · {c} ({d}%)' },
    legend: { type: 'scroll', bottom: 0, textStyle: { color: p.text2, fontSize: 10 }, itemWidth: 10, itemHeight: 8 },
    series: [{
      name: '院校层次', type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: p.surface, borderWidth: 2, borderRadius: 4 },
      label: { color: p.text2, fontSize: 10, formatter: '{b}\n{d}%' },
      labelLine: { lineStyle: { color: p.line } },
      data: tierDist.value.map((r) => ({ name: r.label, value: r.count }))
    }]
  }),

  // 03 历年分数 / 位次双轴折线图（scoreTrend 提供 avgScore / avgRank）
  trend: (p) => ({
    color: [p.accent, p.amber],
    backgroundColor: 'transparent',
    tooltip: { ...tooltipBase(p, 'axis'), axisPointer: { type: 'line', lineStyle: { color: p.line } } },
    legend: { top: 0, data: ['平均分', '平均位次'], textStyle: { color: p.text2, fontSize: 10 } },
    grid: { left: 58, right: 66, top: 36, bottom: 32 },
    xAxis: {
      type: 'category', data: trend.value.map((r) => String(r.examYear)),
      axisLine: { lineStyle: { color: p.line } }, axisTick: { show: false },
      axisLabel: { color: p.text2, fontSize: 10 }
    },
    yAxis: [
      {
        type: 'value', name: '平均分', nameTextStyle: { color: p.text3, fontSize: 10 },
        axisLine: { show: false }, axisLabel: { color: p.text2, fontSize: 10 },
        splitLine: { lineStyle: { color: p.line, type: 'dashed' } }
      },
      {
        type: 'value', name: '平均位次', nameTextStyle: { color: p.text3, fontSize: 10 },
        axisLine: { show: false }, axisLabel: { color: p.text2, fontSize: 10 }, splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '平均分', type: 'line', yAxisIndex: 0, smooth: true, symbolSize: 6,
        data: trend.value.map((r) => r.avgScore), connectNulls: true,
        lineStyle: { width: 2 },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
          { offset: 0, color: alpha(p.accent, '55') }, { offset: 1, color: alpha(p.accent, '05') }
        ] } }
      },
      {
        name: '平均位次', type: 'line', yAxisIndex: 1, smooth: true, symbolSize: 6,
        data: trend.value.map((r) => r.avgRank), connectNulls: true,
        lineStyle: { width: 2, type: 'dashed' }
      }
    ]
  }),

  // 04 位次榜 TOP10 横向条形图（按 bestRank 升序，最优位次在最上方）
  top: (p) => ({
    color: [p.teal],
    backgroundColor: 'transparent',
    tooltip: {
      ...tooltipBase(p, 'axis'),
      formatter: (params) => {
        const d = params[0]?.data
        return `${params[0]?.name ?? ''}<br/>最优位次：${fmtNum(d?.rank)}<br/>最高分：${fmtNum(d?.score)}`
      }
    },
    grid: { left: 8, right: 92, top: 16, bottom: 26, containLabel: true },
    xAxis: {
      type: 'value', name: '最高分', nameTextStyle: { color: p.text3, fontSize: 10 },
      axisLine: { show: false }, axisLabel: { color: p.text2, fontSize: 10 },
      splitLine: { lineStyle: { color: p.line, type: 'dashed' } }
    },
    yAxis: {
      type: 'category', inverse: true, data: topSchools.value.map((r) => r.schoolName),
      axisLine: { lineStyle: { color: p.line } }, axisTick: { show: false },
      axisLabel: { color: p.text2, fontSize: 10, width: 110, overflow: 'truncate' }
    },
    series: [{
      name: '最高分', type: 'bar', barMaxWidth: 16,
      data: topSchools.value.map((r) => ({ value: r.bestScore ?? 0, rank: r.bestRank, score: r.bestScore })),
      label: { show: true, position: 'right', color: p.text2, fontSize: 10, formatter: (v) => `位次 ${fmtNum(v.data?.rank)}` },
      itemStyle: {
        borderRadius: [0, 4, 4, 0],
        color: { type: 'linear', x: 0, y: 0, x2: 1, y2: 0, colorStops: [
          { offset: 0, color: p.accent }, { offset: 1, color: p.cyan }
        ] }
      }
    }]
  }),

  // 05 院校类型占比饼图
  type: (p) => ({
    color: seriesColors(p),
    backgroundColor: 'transparent',
    tooltip: { ...tooltipBase(p, 'item'), formatter: '{b} · {c} ({d}%)' },
    legend: { type: 'scroll', bottom: 0, textStyle: { color: p.text2, fontSize: 10 }, itemWidth: 10, itemHeight: 8 },
    series: [{
      name: '院校类型', type: 'pie', radius: '62%', center: ['50%', '44%'],
      itemStyle: { borderColor: p.surface, borderWidth: 2, borderRadius: 4 },
      label: { color: p.text2, fontSize: 10, formatter: '{b} {d}%' },
      labelLine: { lineStyle: { color: p.line } },
      data: typeDist.value.map((r) => ({ name: r.label, value: r.count }))
    }]
  })
}

function renderCharts() {
  const p = palette()
  for (const [name, build] of Object.entries(builders)) {
    const chart = ensureChart(name)
    if (!chart) continue
    chart.setOption(build(p), true)
    chart.resize()
  }
}
function onResize() { for (const k of Object.keys(instances)) instances[k].resize() }

// ---------------------------------------------------------------- 数据加载
async function load() {
  const token = ++loadSeq
  loading.value = true
  error.value = ''
  try {
    const res = await getInsights({
      provinceCode: provinceCode.value,
      examYear: Number(examYear.value),
      category: category.value
    })
    if (token !== loadSeq) return
    data.value = res ?? {}
  } catch (e) {
    if (token !== loadSeq) return
    error.value = e?.message || '洞察数据加载失败'
    data.value = null
  }
  if (token !== loadSeq) return
  loading.value = false
  await nextTick()
  renderCharts()
}

watch([provinceCode, examYear, category], () => { load() })
// 暗色/浅色切换后 CSS 变量已更新，重新取色并重绘
watch(theme, async () => { await nextTick(); renderCharts() })

onMounted(() => {
  window.addEventListener('resize', onResize)
  load()
})
onBeforeUnmount(() => {
  loadSeq += 1
  window.removeEventListener('resize', onResize)
  disposeCharts()
})
</script>

<template>
  <div class="ins-root space-y-5">
    <!-- 顶部 HUD 标题框 -->
    <div v-reveal="0" class="glass ins-frame">
      <div class="ins-grid-bg" aria-hidden="true"></div>
      <div class="ins-scan" aria-hidden="true"></div>
      <span class="ins-corner tl" aria-hidden="true"></span>
      <span class="ins-corner tr" aria-hidden="true"></span>
      <span class="ins-corner bl" aria-hidden="true"></span>
      <span class="ins-corner br" aria-hidden="true"></span>

      <div class="ins-frame-inner">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p class="ins-mono ins-head">SYS / INSIGHT.MATRIX <span class="ins-sep">//</span> AA·2026</p>
            <h1 class="mt-2 text-[22px] font-semibold tracking-[-0.02em]">录取数据洞察大屏</h1>
            <p class="mt-2 max-w-[78ch] text-[12px] leading-6 muted">
              聚合院校层次、分数段、历年投档趋势与位次榜；所有指标随省份 / 年份 / 科类联动刷新。
            </p>
          </div>
          <div class="btn-group">
            <RouterLink to="/schools" class="btn btn-slate">← 院校库</RouterLink>
            <RouterLink to="/majors" class="btn btn-teal"><span class="btn-glyph">◎</span> 专业库</RouterLink>
          </div>
        </div>

        <!-- 筛选行 -->
        <div class="ins-filter mt-5">
          <label class="ins-field">
            <span class="ins-mono">PROVINCE</span>
            <select v-model="provinceCode" class="field" aria-label="省份">
              <option v-for="p in PROVINCES" :key="p.value" :value="p.value">{{ p.label }} · {{ p.value }}</option>
            </select>
          </label>
          <label class="ins-field">
            <span class="ins-mono">EXAM.YEAR</span>
            <select v-model.number="examYear" class="field" aria-label="年份">
              <option v-for="y in YEARS" :key="y" :value="y">{{ y }}</option>
            </select>
          </label>
          <label class="ins-field">
            <span class="ins-mono">CATEGORY</span>
            <select v-model="category" class="field" aria-label="科类">
              <option v-for="c in CATEGORIES" :key="c" :value="c">{{ c }}</option>
            </select>
          </label>
          <button type="button" class="btn btn-blue" @click="load"><span class="btn-glyph">↻</span> 刷新</button>
        </div>
      </div>
    </div>

    <!-- 数据版本 + demoData 状态条 -->
    <div v-reveal="1" class="glass ins-statusbar">
      <span class="ins-mono"><i class="ins-dot"></i> SYS / INSIGHT.MATRIX</span>
      <span class="ins-mono">DATA.VER <b>{{ data?.dataVersion || '—' }}</b></span>
      <span class="pill" :class="data?.demoData ? 'pill-amber' : 'pill-mint'">
        {{ data?.demoData ? 'DEMO · 演示数据（合成值）' : 'DEMO · 已发布数据' }}
      </span>
      <span class="ins-mono">SCOPE <b>{{ data?.provinceCode || provinceCode }} / {{ data?.examYear || examYear }} / {{ data?.category || category }}</b></span>
      <span class="ins-mono ins-source">{{ data?.sourceNote || '统计口径以当前数据版本为准' }}</span>
    </div>

    <div v-if="error" class="glass p-5 text-[12px]" style="color: var(--rose)">
      {{ error }}
      <button type="button" class="link ml-2" @click="load">重新加载</button>
    </div>

    <!-- KPI 卡片区 -->
    <div class="ins-kpi-grid">
      <div v-for="(k, i) in kpis" :key="k.label" v-reveal="i" class="glass ins-kpi">
        <span class="ins-corner tl" aria-hidden="true"></span>
        <span class="ins-corner br" aria-hidden="true"></span>
        <p class="ins-mono ins-kpi-tag">{{ k.tag }}</p>
        <p class="ins-kpi-value" :style="{ color: k.color }">{{ loading ? '—' : k.display }}</p>
        <p class="ins-kpi-label">{{ k.label }}</p>
      </div>
    </div>

    <!-- 图表矩阵 -->
    <div class="grid gap-4 lg:grid-cols-2">
      <section class="glass ins-panel">
        <div class="ins-panel-head">
          <span class="ins-mono ins-idx">01</span>
          <span class="ins-title">分数段人数分布</span>
          <span class="ins-mono ins-live"><i class="ins-dot"></i> LIVE</span>
        </div>
        <p class="ins-caption">x = bucket（分数段），y = people（人数）</p>
        <div class="ins-chart">
          <div ref="scoreEl" class="ins-canvas"></div>
          <div v-if="loading" class="ins-overlay">数据校准中…</div>
          <div v-else-if="!scoreDist.length" class="ins-overlay">暂无该筛选条件下的分数段数据</div>
        </div>
      </section>

      <section class="glass ins-panel">
        <div class="ins-panel-head">
          <span class="ins-mono ins-idx">02</span>
          <span class="ins-title">院校层次占比</span>
          <span class="ins-mono ins-live"><i class="ins-dot"></i> LIVE</span>
        </div>
        <p class="ins-caption">tierDistribution · 按层次标签拆分</p>
        <div class="ins-chart">
          <div ref="tierEl" class="ins-canvas"></div>
          <div v-if="loading" class="ins-overlay">数据校准中…</div>
          <div v-else-if="!tierDist.length" class="ins-overlay">暂无院校层次分布数据</div>
        </div>
      </section>

      <section class="glass ins-panel lg:col-span-2">
        <div class="ins-panel-head">
          <span class="ins-mono ins-idx">03</span>
          <span class="ins-title">历年分数 / 位次双轴趋势</span>
          <span class="ins-mono ins-live"><i class="ins-dot"></i> LIVE</span>
        </div>
        <p class="ins-caption">scoreTrend · 左轴 avgScore（平均分） / 右轴 avgRank（平均位次）</p>
        <div class="ins-chart">
          <div ref="trendEl" class="ins-canvas"></div>
          <div v-if="loading" class="ins-overlay">数据校准中…</div>
          <div v-else-if="!trend.length" class="ins-overlay">暂无历年趋势数据</div>
        </div>
      </section>

      <section class="glass ins-panel">
        <div class="ins-panel-head">
          <span class="ins-mono ins-idx">04</span>
          <span class="ins-title">位次榜 TOP10</span>
          <span class="ins-mono ins-live"><i class="ins-dot"></i> LIVE</span>
        </div>
        <p class="ins-caption">topSchools · 按 bestRank 升序（越小越靠前），条形为 bestScore，标签为最优位次</p>
        <div class="ins-chart">
          <div ref="topEl" class="ins-canvas ins-canvas-tall"></div>
          <div v-if="loading" class="ins-overlay">数据校准中…</div>
          <div v-else-if="!topSchools.length" class="ins-overlay">暂无位次榜数据</div>
        </div>
      </section>

      <section class="glass ins-panel">
        <div class="ins-panel-head">
          <span class="ins-mono ins-idx">05</span>
          <span class="ins-title">院校类型占比</span>
          <span class="ins-mono ins-live"><i class="ins-dot"></i> LIVE</span>
        </div>
        <p class="ins-caption">typeDistribution · 按办学类型拆分</p>
        <div class="ins-chart">
          <div ref="typeEl" class="ins-canvas"></div>
          <div v-if="loading" class="ins-overlay">数据校准中…</div>
          <div v-else-if="!typeDist.length" class="ins-overlay">暂无院校类型分布数据</div>
        </div>
      </section>
    </div>

    <p v-reveal="0" class="text-center text-[11px] faint">
      当前为演示数据集（合成值），非真实录取数据；最终以各省教育考试院公布为准
    </p>
  </div>
</template>

<style scoped>
/* ---------------------------------------------------------------- 半透明磨砂 HUD */
.ins-root { position: relative; }

.ins-frame { position: relative; overflow: hidden; }
.ins-grid-bg {
  position: absolute; inset: 0; z-index: 0; pointer-events: none;
  background-image:
    linear-gradient(var(--hud-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--hud-line) 1px, transparent 1px);
  background-size: 38px 38px;
  -webkit-mask-image: radial-gradient(90% 80% at 50% 38%, #000 18%, transparent 100%);
  mask-image: radial-gradient(90% 80% at 50% 38%, #000 18%, transparent 100%);
}
.ins-scan {
  position: absolute; left: 0; right: 0; top: 0; height: 20vh; z-index: 0; pointer-events: none;
  background: linear-gradient(180deg, transparent, var(--hud-glow) 58%, color-mix(in srgb, var(--hud-cyan) 26%, transparent));
  opacity: .5; animation: ins-scan 6.5s linear infinite;
}
@keyframes ins-scan { from { transform: translate3d(0, -22vh, 0); } to { transform: translate3d(0, 118vh, 0); } }
.ins-frame-inner { position: relative; z-index: 1; padding: 22px 24px; }

.ins-corner {
  position: absolute; width: 15px; height: 15px; z-index: 2;
  border: 2px solid var(--hud-cyan); opacity: .75; pointer-events: none;
}
.ins-corner.tl { top: -1px; left: -1px; border-right: 0; border-bottom: 0; border-radius: 14px 0 0 0; }
.ins-corner.tr { top: -1px; right: -1px; border-left: 0; border-bottom: 0; border-radius: 0 14px 0 0; }
.ins-corner.bl { bottom: -1px; left: -1px; border-right: 0; border-top: 0; border-radius: 0 0 0 14px; }
.ins-corner.br { bottom: -1px; right: -1px; border-left: 0; border-top: 0; border-radius: 0 0 14px 0; }

.ins-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 10px; letter-spacing: .14em; color: var(--text-3);
}
.ins-head { color: var(--hud-cyan); font-weight: 600; }
.ins-sep { color: var(--text-3); margin: 0 4px; }
.ins-dot {
  display: inline-block; width: 6px; height: 6px; margin-right: 6px; border-radius: 50%;
  background: var(--hud-cyan); box-shadow: 0 0 8px var(--hud-cyan);
  animation: ins-pulse 1.4s ease-in-out infinite;
}
@keyframes ins-pulse { 0%, 100% { opacity: 1; } 50% { opacity: .3; } }

/* 筛选行 */
.ins-filter { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 12px; }
.ins-field { display: flex; flex-direction: column; gap: 6px; flex: 0 1 170px; min-width: 130px; }
.ins-field > .field { width: 100%; }

/* 状态条 */
.ins-statusbar {
  display: flex; flex-wrap: wrap; align-items: center; gap: 10px 18px;
  padding: 12px 18px; border-color: color-mix(in srgb, var(--hud-cyan) 28%, var(--glass-border));
}
.ins-statusbar b { color: var(--text); font-weight: 600; }
.ins-source { flex: 1 1 100%; letter-spacing: .04em; }

/* KPI */
.ins-kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; }
.ins-kpi {
  position: relative; overflow: hidden; padding: 16px 16px 15px;
  border-color: color-mix(in srgb, var(--hud-cyan) 18%, var(--glass-border));
}
.ins-kpi-tag { color: var(--text-3); }
.ins-kpi-value {
  margin-top: 10px; font-size: clamp(1.5rem, 2.4vw, 2rem); font-weight: 600;
  line-height: 1; letter-spacing: -.03em; font-variant-numeric: tabular-nums;
}
.ins-kpi-label { margin-top: 6px; font-size: 11px; color: var(--text-2); }

/* 图表面板 */
.ins-panel { padding: 16px 18px 14px; }
.ins-panel-head { display: flex; align-items: center; gap: 10px; }
.ins-idx { color: var(--hud-cyan); font-weight: 600; }
.ins-title { font-size: 13px; font-weight: 600; color: var(--text); }
.ins-live { margin-left: auto; color: var(--text-3); }
.ins-caption { margin-top: 4px; font-size: 10px; color: var(--text-3); }
.ins-chart { position: relative; margin-top: 8px; }
.ins-canvas { width: 100%; height: 250px; }
.ins-canvas-tall { height: 320px; }
.ins-overlay {
  position: absolute; inset: 0; display: grid; place-items: center;
  border-radius: 10px; background: color-mix(in srgb, var(--surface) 55%, transparent);
  font-size: 12px; color: var(--text-3); text-align: center; padding: 0 16px;
}

@media (max-width: 560px) {
  .ins-frame-inner { padding: 18px 16px; }
  .ins-field { flex: 1 1 100%; }
}

@media (prefers-reduced-motion: reduce) {
  .ins-scan, .ins-dot { animation: none !important; }
  .ins-grid-bg { display: none; }
}
</style>
