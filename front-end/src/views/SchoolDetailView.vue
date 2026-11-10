<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getSchool, getSchoolProfile } from '@/api'
import MarkdownIt from 'markdown-it'
import { useTheme } from '@/composables/useTheme'
import { schoolTheme, tierPill, splitTiers, schoolInitial } from '@/utils/schoolTheme'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const route = useRoute()
const { theme } = useTheme()

const rootEl = ref(null)
const chartEl = ref(null)
const detail = ref(null)
const profileMarkdown = ref('')
const loading = ref(true)
const error = ref('')
let chart = null

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const renderedProfile = computed(() => md.render(profileMarkdown.value || ''))
const school = computed(() => detail.value?.school ?? null)
const themeKey = computed(() => schoolTheme(school.value || { schoolCode: route.params.code }))
const tiers = computed(() => splitTiers(school.value?.tierTags))

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

function disposeChart() {
  if (chart) { chart.dispose(); chart = null }
}

function renderChart() {
  if (!chartEl.value || !detail.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  const css = getComputedStyle(document.documentElement)
  const text = css.getPropertyValue('--text-2').trim() || '#626d81'
  const line = css.getPropertyValue('--line').trim() || '#e7ebf4'
  // 曲线主色跟随该院校的主题色，与列表卡片、页面 hero 保持一致。
  const accent = rootEl.value
    ? getComputedStyle(rootEl.value).getPropertyValue('--card-c').trim() || '#3767e8'
    : '#3767e8'
  chart.setOption({
    color: [accent, '#12996a', '#c98713', '#7c4de0', '#d9464e', '#0f9b90'],
    tooltip: { trigger: 'axis' },
    legend: { type: 'scroll', top: 0, textStyle: { color: text, fontSize: 11 } },
    grid: { left: 52, right: 18, top: 42, bottom: 32 },
    xAxis: {
      type: 'category', data: chartSeries.value.years.map(String),
      axisLine: { lineStyle: { color: line } }, axisLabel: { color: text }
    },
    yAxis: {
      type: 'value', name: '最低位次', nameTextStyle: { color: text, fontSize: 10 },
      splitLine: { lineStyle: { color: line } }, axisLabel: { color: text }
    },
    series: chartSeries.value.series
  }, true)
  chart.resize()
}

async function load() {
  const code = String(route.params.code || '').trim()
  if (!code) { error.value = '缺少院校代码'; loading.value = false; return }
  disposeChart()
  loading.value = true
  error.value = ''
  detail.value = null
  profileMarkdown.value = ''
  // 省份 / 科类沿用后端默认（湖北 · 物理类）；档案与 Markdown 资料解耦加载，
  // 资料缺失或接口失败都不会让整个详情页打不开。
  const [schoolRes, profileRes] = await Promise.allSettled([
    getSchool(code),
    getSchoolProfile(code)
  ])
  if (schoolRes.status === 'fulfilled') detail.value = schoolRes.value
  else error.value = schoolRes.reason?.message || '院校档案加载失败'
  profileMarkdown.value = profileRes.status === 'fulfilled'
    ? (profileRes.value?.profileMarkdown || '') : ''
  loading.value = false
  await nextTick()
  renderChart()
  document.title = `${school.value?.schoolName || code} · 院校详情 · AdmitAtlas`
}

function onResize() { chart && chart.resize() }

watch([detail, theme], async () => { await nextTick(); renderChart() })
watch(() => route.params.code, () => { if (route.name === 'school-detail') load() })
onMounted(() => {
  window.addEventListener('resize', onResize)
  load()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  disposeChart()
})
</script>

<template>
  <div ref="rootEl" :data-school-theme="themeKey" class="school-detail space-y-5">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <nav class="flex flex-wrap items-center gap-2 text-[11px] faint" aria-label="面包屑">
        <RouterLink to="/" class="link">首页</RouterLink>
        <span>/</span>
        <RouterLink :to="{ name: 'schools', query: route.query }" class="link">院校库</RouterLink>
        <span>/</span>
        <span class="muted">{{ school?.schoolName || route.params.code }}</span>
      </nav>
      <div class="btn-group">
        <RouterLink :to="{ name: 'schools', query: route.query }" class="btn btn-slate">← 返回院校库</RouterLink>
        <RouterLink to="/match" class="btn btn-teal"><span class="btn-glyph">◎</span> 智能匹配</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="glass shimmer h-44 p-6">
      <div class="h-3 w-40 rounded-full placeholder-bar"></div>
      <div class="mt-4 h-2.5 w-64 rounded-full placeholder-bar"></div>
    </div>

    <div v-else-if="error" class="glass p-8 text-center">
      <p class="text-[14px] font-semibold">{{ error }}</p>
      <p class="mt-2 text-[12px] muted">该院校可能尚未录入，或链接中的院校代码有误。</p>
      <RouterLink to="/schools" class="btn btn-blue mt-5">返回院校库</RouterLink>
    </div>

    <template v-if="!loading && school">
      <!-- 院校主题 hero：与列表卡片同色，形成「点进来还是这一所」的连续感 -->
      <section class="school-hero glass" aria-label="院校概览">
        <div class="flex flex-wrap items-start gap-4">
          <span class="school-hero-badge" aria-hidden="true">{{ schoolInitial(school) }}</span>
          <div class="min-w-[220px] flex-1">
            <p class="eyebrow">School profile / 院校档案</p>
            <h1 class="mt-2 text-[24px] font-semibold tracking-[-0.02em]">{{ school.schoolName }}</h1>
            <p class="mt-1 text-[11px] faint">
              {{ school.schoolCode }} · {{ school.supervisor || '主管部门未公示' }} · 数据版本 {{ detail.dataVersion || '—' }}
            </p>
            <div class="mt-3 flex flex-wrap gap-2">
              <span class="school-hero-level">{{ school.schoolLevel || '本科' }}</span>
              <span v-for="t in tiers" :key="t" class="pill" :class="tierPill(t)">{{ t }}</span>
              <span v-if="!tiers.length" class="pill pill-neutral">暂无层次标签</span>
            </div>
          </div>
          <div class="school-hero-facts">
            <div><span>所在地</span><strong>{{ school.city || '—' }}</strong></div>
            <div><span>办学类型</span><strong>{{ school.schoolType || '—' }}</strong></div>
            <div><span>投档记录</span><strong>{{ detail.total ?? 0 }} 条</strong></div>
          </div>
        </div>
      </section>

      <div class="grid gap-4 lg:grid-cols-12">
        <div class="glass p-5 lg:col-span-5">
          <p class="text-[13px] font-semibold">档案信息</p>
          <hr class="hairline my-4" />
          <div class="space-y-3 text-[12px]">
            <div class="flex justify-between gap-4"><span class="muted">院校代码</span><span class="font-mono">{{ school.schoolCode }}</span></div>
            <div class="flex justify-between gap-4"><span class="muted">主管部门</span><span>{{ school.supervisor || '—' }}</span></div>
            <div class="flex justify-between gap-4"><span class="muted">所在地</span><span>{{ school.city || '—' }}</span></div>
            <div class="flex justify-between gap-4"><span class="muted">省份代码</span><span class="font-mono">{{ school.provinceCode || '—' }}</span></div>
            <div class="flex justify-between gap-4"><span class="muted">办学类型</span><span>{{ school.schoolType || '—' }}</span></div>
            <div class="flex justify-between gap-4"><span class="muted">办学层次</span><span>{{ school.schoolLevel || '—' }}</span></div>
            <div class="flex justify-between gap-4"><span class="muted">层次标签</span><span>{{ school.tierTags || '—' }}</span></div>
          </div>
          <hr class="hairline my-4" />
          <p class="text-[12px] font-semibold">院校简介</p>
          <p class="mt-2 text-[12px] leading-6 muted">{{ school.intro || '该校暂未发布简介。' }}</p>
          <p class="mt-4 text-[11px] leading-5 faint">{{ detail.sourceNote }}</p>
        </div>

        <div class="glass p-5 lg:col-span-7">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <p class="text-[13px] font-semibold">历年最低录取位次趋势</p>
            <span class="pill pill-amber">演示数据</span>
          </div>
          <div ref="chartEl" class="mt-3 h-[240px] w-full"></div>
          <div v-if="chartSeries.series.length" class="mt-3 max-h-[240px] overflow-auto">
            <table class="data-table">
              <thead><tr><th>年份</th><th>专业组</th><th>最低分</th><th>最低位次</th><th>计划数</th></tr></thead>
              <tbody>
                <tr v-for="r in detail.admissionScores" :key="r.id ?? `${r.examYear}-${r.groupCode}`">
                  <td>{{ r.examYear }}</td>
                  <td>{{ r.groupCode }} · {{ r.unitName || '—' }}</td>
                  <td>{{ r.minScore ?? '—' }}</td>
                  <td class="font-semibold">{{ r.minRank != null ? r.minRank.toLocaleString() : '—' }}</td>
                  <td>{{ r.planCount ?? '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="mt-4 rounded-xl border border-hairline py-8 text-center text-[12px] faint">
            暂无该省份 / 科类的投档记录
          </div>
        </div>
      </div>

      <div class="glass p-5">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <p class="text-[13px] font-semibold">院校资料</p>
          <span class="pill pill-neutral">Markdown · 管理员维护</span>
        </div>
        <div v-if="!renderedProfile" class="mt-3 text-[12px] faint">该校暂未发布院校资料。</div>
        <div v-else class="markdown-body mt-3 text-[13px] leading-7" v-html="renderedProfile"></div>
      </div>
    </template>

    <p class="text-center text-[11px] faint">
      当前为演示数据集（合成值），非真实录取分数；最终以各省教育考试院公布为准
    </p>
  </div>
</template>
