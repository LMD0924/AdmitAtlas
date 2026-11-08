<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getDictionaries, getSchool, getSchools } from '@/api'
import { useTheme } from '@/composables/useTheme'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const { theme } = useTheme()
const keyword = ref('')
const provinceCode = ref('')
const tier = ref('')
const page = ref(1)
const size = 10
const total = ref(0)
const rows = ref([])
const provinces = ref([])
const detail = ref(null)
const detailLoading = ref(false)
const searched = ref(false)
const error = ref('')
const chartEl = ref(null)
let chart = null

const filters = [
  { label: '全部', value: '' }, { label: '985', value: '985' },
  { label: '211', value: '211' }, { label: '双一流', value: '双一流' },
  { label: '省重点', value: '省重点' }, { label: '普通', value: '普通' }
]

const capabilities = [
  { span: 'lg:col-span-5', tag: '档案', title: '院校层次与属地', desc: '985 / 211 / 双一流、主管部门、所在地与办学性质，支持多条件组合检索。', pill: 'pill-sky' },
  { span: 'lg:col-span-4', tag: '目录', title: '专业与选科要求', desc: '专业组、专业目录与选科要求联动，资格过滤在匹配阶段自动执行。', pill: 'pill-violet' },
  { span: 'lg:col-span-3', tag: '趋势', title: '历年录取轨迹', desc: '近 3 年最低位次与计划数变化，位次是唯一跨年可比口径。', pill: 'pill-mint' }
]

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function search(nextPage = 1) {
  error.value = ''
  page.value = nextPage
  searched.value = true
  try {
    const res = await getSchools({
      keyword: keyword.value.trim() || undefined,
      provinceCode: provinceCode.value || undefined,
      tier: tier.value || undefined,
      page: page.value, size
    })
    rows.value = res?.items ?? []
    total.value = res?.total ?? 0
  } catch (e) {
    error.value = e.message || '检索失败'
    rows.value = []
    total.value = 0
  }
}

function clear() {
  keyword.value = ''
  provinceCode.value = ''
  tier.value = ''
  page.value = 1
  searched.value = false
  rows.value = []
  total.value = 0
  detail.value = null
  error.value = ''
  search()
}

async function openDetail(code) {
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getSchool(code, { provinceCode: '42', category: '物理类' })
  } catch (e) {
    error.value = e.message || '详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

const chartSeries = computed(() => {
  const groups = new Map()
  for (const row of detail.value?.admissionScores ?? []) {
    const key = `${row.groupCode} ${row.unitName || ''}`.trim()
    if (!groups.has(key)) groups.set(key, new Map())
    groups.get(key).set(row.examYear, row.minRank)
  }
  const years = [...new Set((detail.value?.admissionScores ?? []).map((r) => r.examYear))].sort()
  return {
    years,
    series: [...groups.entries()].map(([name, byYear]) => ({
      name, type: 'line', smooth: true, symbolSize: 6,
      data: years.map((y) => byYear.get(y) ?? null), connectNulls: true
    }))
  }
})

function renderChart() {
  if (!chartEl.value || !detail.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  const css = getComputedStyle(document.documentElement)
  const text = css.getPropertyValue('--text-2').trim() || '#626d81'
  const line = css.getPropertyValue('--line').trim() || '#e7ebf4'
  chart.setOption({
    color: ['#3767e8', '#12996a', '#c98713', '#7c4de0', '#d9464e', '#0f9b90'],
    tooltip: { trigger: 'axis' },
    legend: { type: 'scroll', top: 0, textStyle: { color: text, fontSize: 11 } },
    grid: { left: 52, right: 18, top: 42, bottom: 32 },
    xAxis: { type: 'category', data: chartSeries.value.years.map(String),
      axisLine: { lineStyle: { color: line } }, axisLabel: { color: text } },
    yAxis: { type: 'value', name: '最低位次', nameTextStyle: { color: text, fontSize: 10 },
      splitLine: { lineStyle: { color: line } }, axisLabel: { color: text } },
    series: chartSeries.value.series
  }, true)
  chart.resize()
}

function onResize() { chart && chart.resize() }

watch([detail, theme], async () => { await nextTick(); renderChart() })
onMounted(async () => {
  window.addEventListener('resize', onResize)
  try {
    const d = await getDictionaries()
    provinces.value = d?.provinces ?? []
  } catch { /* 字典不可用时下拉为空，检索仍可用 */ }
  await search()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart && chart.dispose()
  chart = null
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p v-reveal="0" class="eyebrow">School archive / 资源中枢</p>
        <h1 v-reveal="1" class="mt-2 text-[22px] font-semibold tracking-[-0.02em]">全国高校，一张可检索的地图</h1>
        <p v-reveal="2" class="mt-2 max-w-[76ch] text-[12px] leading-6 muted">
          按层次、地域检索院校档案与历年录取趋势；数据版本、来源与更新时间随结果一同公示。
        </p>
      </div>
      <div v-reveal="2" class="btn-group">
        <RouterLink to="/" class="btn btn-slate">← 返回概览</RouterLink>
        <RouterLink to="/match" class="btn btn-teal"><span class="btn-glyph">◎</span> 智能匹配</RouterLink>
      </div>
    </div>

    <div v-reveal="0" class="glass p-5 md:p-6">
      <div class="flex flex-wrap gap-3">
        <input v-model="keyword" class="field max-w-[320px] flex-1" placeholder="输入院校名称 / 代码 / 城市"
          aria-label="院校检索" @keyup.enter="search(1)" />
        <select v-model="provinceCode" class="field w-[150px]" aria-label="院校属地">
          <option value="">全部属地</option>
          <option v-for="p in provinces" :key="p.provinceCode" :value="p.provinceCode">{{ p.provinceName }}</option>
        </select>
        <button class="btn btn-blue" type="button" @click="search(1)">
          <span class="btn-glyph">⌕</span> 检索 <span class="link-arrow">→</span>
        </button>
        <button class="btn btn-rose" type="button" @click="clear"><span class="btn-glyph">✕</span> 清空</button>
      </div>
      <div class="mt-4 flex flex-wrap gap-2">
        <button v-for="f in filters" :key="f.label" type="button" class="chip"
          :class="{ 'is-active': tier === f.value }" @click="tier = f.value; search(1)">{{ f.label }}</button>
      </div>

      <div v-if="error" class="mt-4 rounded-lg px-3 py-2 text-[12px]" style="background: var(--rose-bg); color: var(--rose)">{{ error }}</div>

      <div class="mt-5 overflow-x-auto">
        <table class="data-table min-w-[640px]">
          <thead><tr><th>代码</th><th>院校</th><th>属地</th><th>层次标签</th><th>类型</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="s in rows" :key="s.schoolCode">
              <td class="font-semibold tabular-nums">{{ s.schoolCode }}</td>
              <td style="color: var(--text)">{{ s.schoolName }}</td>
              <td>{{ s.city || '—' }}</td>
              <td>
                <span v-for="t in (s.tierTags || '').split(',')" :key="t" class="mr-1 inline-block rounded-full px-2 py-0.5 text-[10px]" style="background: var(--sky-bg); color: var(--sky)">{{ t }}</span>
              </td>
              <td>{{ s.schoolType || '—' }}</td>
              <td><button class="btn btn-teal" @click="openDetail(s.schoolCode)"><span class="btn-glyph">⌕</span> 详情</button></td>
            </tr>
            <tr v-if="!rows.length"><td colspan="6" class="py-8 text-center faint">
              {{ searched ? '没有匹配的院校，试试放宽条件' : '正在加载院校档案…' }}
            </td></tr>
          </tbody>
        </table>
      </div>

      <div class="mt-4 flex flex-wrap items-center justify-between gap-3 text-[11px] faint">
        <span>共 {{ total }} 所院校 · 第 {{ page }} / {{ totalPages }} 页 · 数据版本 {{ detail?.dataVersion || 'demo-2026-hb-v1' }}</span>
        <div class="btn-group">
          <button class="btn btn-slate" :disabled="page <= 1" @click="search(page - 1)">← 上一页</button>
          <button class="btn btn-blue" :disabled="page >= totalPages" @click="search(page + 1)">下一页 →</button>
        </div>
      </div>
    </div>

    <div v-if="detailLoading" class="glass shimmer h-40 p-5"><div class="h-2.5 w-32 rounded-full placeholder-bar"></div></div>

    <div v-if="detail" class="grid gap-4 lg:grid-cols-12">
      <div class="glass p-5 lg:col-span-5">
        <p class="eyebrow">School profile</p>
        <h2 class="mt-2 text-[17px] font-semibold">{{ detail.school.schoolName }}</h2>
        <p class="mt-1 text-[11px] faint">{{ detail.school.schoolCode }} · {{ detail.school.supervisor }}</p>
        <hr class="hairline my-4" />
        <div class="space-y-3 text-[12px]">
          <div class="flex justify-between"><span class="muted">所在地</span><span>{{ detail.school.city }}</span></div>
          <div class="flex justify-between"><span class="muted">层次标签</span><span>{{ detail.school.tierTags }}</span></div>
          <div class="flex justify-between"><span class="muted">办学类型</span><span>{{ detail.school.schoolType }}</span></div>
          <div class="flex justify-between"><span class="muted">层次</span><span>{{ detail.school.schoolLevel }}</span></div>
          <div class="flex justify-between"><span class="muted">投档记录</span><span>{{ detail.total }} 条</span></div>
        </div>
        <p class="mt-4 text-[11px] leading-5 faint">{{ detail.school.intro }}</p>
        <p class="mt-4 text-[11px] leading-5 faint">{{ detail.sourceNote }}</p>
      </div>

      <div class="glass p-5 lg:col-span-7">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <p class="text-[13px] font-semibold">历年最低录取位次趋势</p>
          <span class="pill pill-amber">演示数据</span>
        </div>
        <div ref="chartEl" class="mt-3 h-[240px] w-full"></div>
        <div class="mt-3 max-h-[220px] overflow-auto">
          <table class="data-table">
            <thead><tr><th>年份</th><th>专业组</th><th>最低分</th><th>最低位次</th><th>计划数</th></tr></thead>
            <tbody>
              <tr v-for="r in detail.admissionScores" :key="r.id">
                <td>{{ r.examYear }}</td><td>{{ r.groupCode }} · {{ r.unitName }}</td>
                <td>{{ r.minScore }}</td><td class="font-semibold">{{ r.minRank.toLocaleString() }}</td>
                <td>{{ r.planCount ?? '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="grid gap-4 lg:grid-cols-12">
      <div v-for="(c, i) in capabilities" :key="c.title" v-reveal="i + 1" :class="['glass lift p-5', c.span]">
        <span class="pill" :class="c.pill">{{ c.tag }}</span>
        <h3 class="mt-4 text-[14px] font-semibold">{{ c.title }}</h3>
        <p class="mt-2 text-[12px] leading-6 muted">{{ c.desc }}</p>
      </div>
    </div>

    <p v-reveal="0" class="text-center text-[11px] faint">
      当前为演示数据集（合成值），非真实录取分数；最终以各省教育考试院公布为准
    </p>
  </div>
</template>
