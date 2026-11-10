<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getPlans, getPlan } from '@/api'
import { useTheme } from '@/composables/useTheme'

echarts.use([BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])
const { theme } = useTheme()

// ---------------------------------------------------------------- 数值兜底
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
/** tier 分档口径：CHONG / WEN / BAO → 语义色 pill。 */
function tierPill(tier) {
  const t = String(tier || '').toUpperCase()
  if (t === 'CHONG') return { cls: 'pill-rose', label: '冲' }
  if (t === 'WEN') return { cls: 'pill-amber', label: '稳' }
  if (t === 'BAO') return { cls: 'pill-mint', label: '保' }
  return { cls: 'pill-neutral', label: t || '未分档' }
}

// ---------------------------------------------------------------- 状态
const plans = ref([])
const idA = ref(null)
const idB = ref(null)
const planA = ref(null)
const planB = ref(null)
const errA = ref('')
const errB = ref('')
const loading = ref(true)
const error = ref('')

const tierEl = ref(null)
const avgEl = ref(null)
let tierChart = null
let avgChart = null

function nameOf(id) {
  const p = plans.value.find((x) => String(x?.planId) === String(id))
  return p?.planName ? String(p.planName) : ''
}
const labelA = computed(() => planA.value?.plan?.planName || nameOf(idA.value) || '方案 A')
const labelB = computed(() => planB.value?.plan?.planName || nameOf(idB.value) || '方案 B')

const itemsA = computed(() => arr(planA.value?.items))
const itemsB = computed(() => arr(planB.value?.items))

function stats(items) {
  const total = items.length
  const counts = { CHONG: 0, WEN: 0, BAO: 0 }
  let scoreSum = 0, scoreCount = 0, rankSum = 0, rankCount = 0
  for (const it of items) {
    const t = String(it?.tier || '').toUpperCase()
    if (t === 'CHONG' || t === 'WEN' || t === 'BAO') counts[t] += 1
    const s = toNum(it?.matchScore)
    if (s !== null) { scoreSum += s; scoreCount += 1 }
    const r = toNum(it?.historyMinRank)
    if (r !== null) { rankSum += r; rankCount += 1 }
  }
  const ratio = (c) => total ? Math.round((c * 1000) / total) / 10 : 0
  return {
    total, counts,
    ratios: { CHONG: ratio(counts.CHONG), WEN: ratio(counts.WEN), BAO: ratio(counts.BAO) },
    avgMatch: scoreCount ? scoreSum / scoreCount : null,
    avgRank: rankCount ? rankSum / rankCount : null
  }
}
const statsA = computed(() => stats(itemsA.value))
const statsB = computed(() => stats(itemsB.value))

const columns = computed(() => [
  { key: 'A', label: labelA.value, version: txt(planA.value?.dataVersion, ''),
    items: itemsA.value, stats: statsA.value, err: errA.value },
  { key: 'B', label: labelB.value, version: txt(planB.value?.dataVersion, ''),
    items: itemsB.value, stats: statsB.value, err: errB.value }
])

function schoolMap(items) {
  const m = new Map()
  for (const it of items) {
    const code = String(it?.schoolCode ?? it?.schoolName ?? '').trim()
    if (!code || m.has(code)) continue
    m.set(code, txt(it?.schoolName, code))
  }
  return m
}
const onlyInA = computed(() => {
  const b = schoolMap(itemsB.value)
  return [...schoolMap(itemsA.value)].filter(([c]) => !b.has(c)).map(([, n]) => n)
})
const onlyInB = computed(() => {
  const a = schoolMap(itemsA.value)
  return [...schoolMap(itemsB.value)].filter(([c]) => !a.has(c)).map(([, n]) => n)
})

// ---------------------------------------------------------------- 图 1：冲 / 稳 / 保 分组柱状
const TIER_KEYS = ['CHONG', 'WEN', 'BAO']
function tooltipBase(p) {
  return {
    trigger: 'axis', backgroundColor: p.surface, borderColor: p.line, borderWidth: 1,
    textStyle: { color: p.text, fontSize: 11 },
    axisPointer: { type: 'shadow', lineStyle: { color: p.line } }
  }
}
function buildTier(p) {
  const A = statsA.value, B = statsB.value
  return {
    backgroundColor: 'transparent',
    color: [p.sky, p.violet],
    tooltip: tooltipBase(p),
    legend: { top: 0, data: [labelA.value, labelB.value], textStyle: { color: p.text2, fontSize: 11 } },
    grid: { left: 44, right: 16, top: 40, bottom: 30 },
    xAxis: {
      type: 'category', data: ['冲', '稳', '保'],
      axisLine: { lineStyle: { color: p.line } }, axisTick: { show: false },
      axisLabel: { color: p.text2, fontSize: 11 }
    },
    yAxis: {
      type: 'value', minInterval: 1, name: '志愿数',
      nameTextStyle: { color: p.text3, fontSize: 10 },
      axisLine: { show: false }, axisLabel: { color: p.text2, fontSize: 10 },
      splitLine: { lineStyle: { color: p.line, type: 'dashed' } }
    },
    series: [
      { name: labelA.value, type: 'bar', barMaxWidth: 34,
        data: TIER_KEYS.map((k) => A.counts[k]),
        itemStyle: { borderRadius: [4, 4, 0, 0], color: p.sky } },
      { name: labelB.value, type: 'bar', barMaxWidth: 34,
        data: TIER_KEYS.map((k) => B.counts[k]),
        itemStyle: { borderRadius: [4, 4, 0, 0], color: p.violet } }
    ]
  }
}

// ---------------------------------------------------------------- 图 2：平均匹配度 / 平均历史位次（双轴）
function buildAvg(p) {
  const A = statsA.value, B = statsB.value
  return {
    backgroundColor: 'transparent',
    color: [p.accent, p.amber],
    tooltip: tooltipBase(p),
    legend: { top: 0, data: ['平均匹配度', '平均历史位次'], textStyle: { color: p.text2, fontSize: 11 } },
    grid: { left: 52, right: 66, top: 40, bottom: 34 },
    xAxis: {
      type: 'category', data: [labelA.value, labelB.value],
      axisLine: { lineStyle: { color: p.line } }, axisTick: { show: false },
      axisLabel: { color: p.text2, fontSize: 10, interval: 0 }
    },
    yAxis: [
      { type: 'value', min: 0, name: '匹配度', nameTextStyle: { color: p.text3, fontSize: 10 },
        axisLine: { show: false }, axisLabel: { color: p.text2, fontSize: 10 },
        splitLine: { lineStyle: { color: p.line, type: 'dashed' } } },
      { type: 'value', name: '平均位次', nameTextStyle: { color: p.text3, fontSize: 10 },
        axisLine: { show: false }, axisLabel: { color: p.text2, fontSize: 10 }, splitLine: { show: false } }
    ],
    series: [
      { name: '平均匹配度', type: 'bar', yAxisIndex: 0, barMaxWidth: 40,
        data: [A.avgMatch, B.avgMatch],
        itemStyle: { borderRadius: [4, 4, 0, 0], color: p.accent },
        label: { show: true, position: 'top', color: p.text2, fontSize: 10,
          formatter: (v) => (v.value == null ? '—' : Number(v.value).toFixed(1)) } },
      { name: '平均历史位次', type: 'bar', yAxisIndex: 1, barMaxWidth: 40,
        data: [A.avgRank, B.avgRank],
        itemStyle: { borderRadius: [4, 4, 0, 0], color: p.amber },
        label: { show: true, position: 'top', color: p.text2, fontSize: 10,
          formatter: (v) => (v.value == null ? '—' : Math.round(Number(v.value)).toLocaleString()) } }
    ]
  }
}

// ---------------------------------------------------------------- 图表生命周期
function renderCharts() {
  const p = palette()
  if (tierEl.value) {
    if (!tierChart) tierChart = echarts.init(tierEl.value)
    tierChart.setOption(buildTier(p), true)
    tierChart.resize()
  }
  if (avgEl.value) {
    if (!avgChart) avgChart = echarts.init(avgEl.value)
    avgChart.setOption(buildAvg(p), true)
    avgChart.resize()
  }
}
function disposeCharts() {
  if (tierChart) { tierChart.dispose(); tierChart = null }
  if (avgChart) { avgChart.dispose(); avgChart = null }
}
function onResize() { tierChart?.resize(); avgChart?.resize() }

// ---------------------------------------------------------------- 数据加载
async function loadPlans() {
  error.value = ''
  try {
    plans.value = arr((await getPlans())?.items)
  } catch (e) {
    error.value = e?.message || '方案列表加载失败'
    plans.value = []
  }
  if (plans.value.length >= 2) {
    idA.value = plans.value[0].planId
    idB.value = plans.value[1].planId
  } else {
    idA.value = null
    idB.value = null
  }
}
async function loadDetails() {
  errA.value = ''
  errB.value = ''
  // 两套详情各自结算：任一套失败只影响本栏，另一套照常展示。
  const [ra, rb] = await Promise.allSettled([getPlan(idA.value), getPlan(idB.value)])
  if (ra.status === 'fulfilled') planA.value = ra.value ?? null
  else { planA.value = null; errA.value = ra.reason?.message || '方案 A 详情加载失败' }
  if (rb.status === 'fulfilled') planB.value = rb.value ?? null
  else { planB.value = null; errB.value = rb.reason?.message || '方案 B 详情加载失败' }
  await nextTick()
  renderCharts()
}
async function onSelect() {
  await nextTick()
  if (idA.value == null || idB.value == null || idA.value === idB.value) return
  await loadDetails()
}
watch(theme, async () => { await nextTick(); renderCharts() })
onMounted(async () => {
  window.addEventListener('resize', onResize)
  await loadPlans()
  if (plans.value.length >= 2) await loadDetails()
  loading.value = false
  await nextTick()
  renderCharts()
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  disposeCharts()
})
</script>

<template>
  <div class="space-y-5">
    <div>
      <p v-reveal="0" class="eyebrow">Plan compare / 方案对比</p>
      <h1 v-reveal="1" class="mt-2 text-[22px] font-semibold tracking-[-0.02em]">两套方案，逐条摆在一起看</h1>
      <p v-reveal="2" class="mt-2 max-w-[76ch] text-[12px] leading-6 muted">
        对比冲 / 稳 / 保梯度、平均匹配度与院校差异；切换下拉即时刷新，任一套方案加载失败都不影响另一套。
      </p>
    </div>

    <div v-if="loading" class="grid gap-4 sm:grid-cols-2">
      <div v-for="n in 2" :key="n" class="glass shimmer h-44 p-6">
        <div class="h-3 w-36 rounded-full placeholder-bar"></div>
        <div class="mt-4 h-2.5 w-56 rounded-full placeholder-bar"></div>
        <div class="mt-3 h-2.5 w-40 rounded-full placeholder-bar"></div>
      </div>
    </div>

    <template v-else>
      <div v-if="error" class="glass p-4 text-[12px]" style="color: var(--rose)">{{ error }}</div>

      <div v-if="plans.length < 2" class="glass empty-state">
        <p class="text-[14px] font-semibold">至少需要两套方案才能对比</p>
        <p class="mt-2 text-[12px] muted">先在志愿方案页保存两套方案，再回到这里选择 A / B 进行对比。</p>
        <RouterLink to="/plan" class="btn btn-blue mt-5">前往志愿方案 <span class="link-arrow">→</span></RouterLink>
      </div>

      <template v-else>
        <div class="glass p-5">
          <div class="pc-selects">
            <label class="pc-select">
              <span class="field-label">方案 A</span>
              <select v-model="idA" class="field" aria-label="方案 A" @change="onSelect">
                <option v-for="p in plans" :key="p.planId" :value="p.planId" :disabled="p.planId === idB">
                  {{ p.planName || '未命名方案' }} · #{{ p.planId }}
                </option>
              </select>
            </label>
            <label class="pc-select">
              <span class="field-label">方案 B</span>
              <select v-model="idB" class="field" aria-label="方案 B" @change="onSelect">
                <option v-for="p in plans" :key="p.planId" :value="p.planId" :disabled="p.planId === idA">
                  {{ p.planName || '未命名方案' }} · #{{ p.planId }}
                </option>
              </select>
            </label>
          </div>
          <p class="mt-3 text-[11px] faint">两个下拉不能选同一套方案；切换后自动重新对比。</p>
        </div>

        <div class="grid gap-4 lg:grid-cols-2">
          <section class="glass p-5">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <p class="text-[13px] font-semibold">冲 / 稳 / 保 分档对比</p>
              <span class="pill pill-neutral">分组柱状</span>
            </div>
            <div ref="tierEl" class="pc-chart"></div>
          </section>
          <section class="glass p-5">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <p class="text-[13px] font-semibold">平均匹配度与平均历史位次</p>
              <span class="pill pill-neutral">双轴</span>
            </div>
            <p class="mt-1 text-[11px] faint">历史位次越小越好；两个指标量纲不同，分别使用左右纵轴</p>
            <div ref="avgEl" class="pc-chart"></div>
          </section>
        </div>

        <div class="grid gap-4 lg:grid-cols-2">
          <section v-for="col in columns" :key="col.key" class="glass overflow-hidden">
            <div class="pc-col-head">
              <div class="flex flex-wrap items-center gap-2">
                <span class="pill" :class="col.key === 'A' ? 'pill-sky' : 'pill-violet'">方案 {{ col.key }}</span>
                <span class="text-[11px] faint">数据版本 {{ col.version || '—' }}</span>
              </div>
              <h2 class="mt-2 text-[15px] font-semibold">{{ col.label }}</h2>
              <div class="pc-stats">
                <div class="pc-stat"><span>志愿条数</span><strong>{{ col.stats.total }}</strong></div>
                <div class="pc-stat"><span>冲 / 稳 / 保</span><strong>{{ col.stats.counts.CHONG }} / {{ col.stats.counts.WEN }} / {{ col.stats.counts.BAO }}</strong></div>
                <div class="pc-stat"><span>占比</span><strong>{{ col.stats.ratios.CHONG }}% / {{ col.stats.ratios.WEN }}% / {{ col.stats.ratios.BAO }}%</strong></div>
                <div class="pc-stat"><span>平均匹配度</span><strong>{{ col.stats.avgMatch === null ? '—' : col.stats.avgMatch.toFixed(1) }}</strong></div>
              </div>
            </div>

            <div v-if="col.err" class="px-5 py-3 text-[12px]" style="color: var(--rose)">{{ col.err }}</div>
            <div v-else class="pc-scroll">
              <table class="data-table">
                <thead>
                  <tr><th>序号</th><th>院校</th><th>专业组 / 专业</th><th>档位</th><th>匹配度</th><th>历史位次</th></tr>
                </thead>
                <tbody>
                  <tr v-for="(row, i) in col.items" :key="String(row.schoolCode) + String(row.groupOrMajorCode) + i">
                    <td class="tabular-nums font-semibold">{{ String(row.positionNo ?? i + 1).padStart(2, '0') }}</td>
                    <td>{{ txt(row.schoolName) }}</td>
                    <td>{{ txt(row.groupOrMajorCode) }}<span v-if="row.unitName" class="faint"> · {{ row.unitName }}</span></td>
                    <td><span class="pill" :class="tierPill(row.tier).cls">{{ tierPill(row.tier).label }}</span></td>
                    <td>{{ fmtNum(row.matchScore) }}</td>
                    <td>{{ fmtNum(row.historyMinRank) }}</td>
                  </tr>
                  <tr v-if="!col.items.length">
                    <td colspan="6" class="py-8 text-center faint">该方案暂无志愿条目</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>

        <div class="glass p-5">
          <p class="text-[13px] font-semibold">院校差异</p>
          <p class="mt-1 text-[11px] faint">按 schoolCode 比较，列出只出现在其中一套方案的院校</p>
          <div class="mt-4 grid gap-4 sm:grid-cols-2">
            <div>
              <p class="text-[12px] muted">仅方案 A 包含（{{ onlyInA.length }}）</p>
              <div v-if="onlyInA.length" class="mt-2 flex flex-wrap gap-2">
                <span v-for="n in onlyInA" :key="n" class="chip">{{ n }}</span>
              </div>
              <p v-else class="mt-2 text-[12px] faint">无</p>
            </div>
            <div>
              <p class="text-[12px] muted">仅方案 B 包含（{{ onlyInB.length }}）</p>
              <div v-if="onlyInB.length" class="mt-2 flex flex-wrap gap-2">
                <span v-for="n in onlyInB" :key="n" class="chip">{{ n }}</span>
              </div>
              <p v-else class="mt-2 text-[12px] faint">无</p>
            </div>
          </div>
        </div>
      </template>
    </template>

    <p class="text-center text-[11px] leading-5 faint">
      当前为演示数据集（合成值），非真实录取数据；最终以各省教育考试院公布为准
    </p>
  </div>
</template>

<style scoped>
.pc-selects { display: grid; gap: 14px; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); }
.pc-select { display: flex; flex-direction: column; }
.pc-select > .field { width: 100%; }
.pc-chart { width: 100%; height: 250px; margin-top: 10px; }
.pc-col-head { padding: 18px 20px 14px; }
.pc-stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 16px; margin-top: 16px; }
.pc-stat { display: flex; flex-direction: column; gap: 4px; }
.pc-stat span { font-size: 11px; color: var(--text-3); }
.pc-stat strong { font-size: 13px; font-weight: 600; color: var(--text); font-variant-numeric: tabular-nums; }
.pc-scroll { overflow-x: auto; }
.pc-scroll .data-table { min-width: 540px; }
.pc-scroll .data-table td { vertical-align: middle; }
</style>
