<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getOverview, getStrategies } from '@/api'

const steps = [
  { no: '01', title: '录入成绩', desc: '省份、年份、选科、总分与单科分，按一分一段表换算为可比对的省排名' },
  { no: '02', title: '按省规则匹配', desc: '批次线与选科资格过滤后，按 min_rank 索引召回近三年同位次院校，划分冲 / 稳 / 保' },
  { no: '03', title: '生成志愿表', desc: '按本省志愿结构生成可拖拽方案，逐条校验并导出含数据版本的 Excel' }
]

const pillars = [
  { span: 'lg:col-span-5', tag: '数据口径', title: '跨年只比位次', desc: '每年难度与满分不同，绝对分数不可比。所有历史投档线统一落到省排名后再比较。' },
  { span: 'lg:col-span-4', tag: '规则引擎', title: '省 × 年 × 模式', desc: '志愿单位、志愿数、批次与选科要求全部读取 rule_policy，策略工厂负责校验模式。' },
  { span: 'lg:col-span-3', tag: '数据治理', title: '版本与留证', desc: '每条录取数据带 data_version、来源链接与审核状态，一次匹配只读同一版本。' }
]

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 11) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const typeTarget = '从分数 → 位次 → 冲 / 稳 / 保 → 志愿表'
const typed = ref('')
let timer = null

const modes = ref([])
const overview = ref(null)
const backendStatus = ref('正在检测后端…')
const probeState = ref('loading')

const facts = computed(() => [
  { value: modes.value.length || 0, label: '考试模式策略', hint: '传统文理 · 院校专业组 · 专业+院校' },
  { value: overview.value?.schoolCount ?? 0, label: '院校档案', hint: '含层次标签、属地、主管部门' },
  { value: overview.value?.admissionRowCount ?? 0, label: '投档记录', hint: '近三年最低位次，带来源留证' }
])

const metrics = computed(() => [
  { cls: 'is-mint', glyph: '✓', value: '已就绪', label: `${modes.value.length || 0} 种考试模式` },
  { cls: 'is-sky', glyph: '▶', value: 'P1 试点', label: '湖北 · 物理类' },
  { cls: 'is-slate', glyph: '◷', value: `${overview.value?.schoolCount ?? 0} 所`, label: '已入库院校' },
  { cls: 'is-violet', glyph: '▥', value: '5 / 5', label: '链路环节打通' }
])

onMounted(async () => {
  if (!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
    let i = 0
    timer = window.setInterval(() => {
      i += 1
      typed.value = typeTarget.slice(0, i)
      if (i >= typeTarget.length) window.clearInterval(timer)
    }, 95)
  } else {
    typed.value = typeTarget
  }

  try {
    const [o, s] = await Promise.all([getOverview(), getStrategies()])
    overview.value = o
    modes.value = s?.supportedModes ?? []
    backendStatus.value = `后端已连通 · ${o?.stage ?? 'P1-pilot'} · ${modes.value.length} 种考试模式`
    probeState.value = 'ok'
  } catch (e) {
    backendStatus.value = `后端未连通 · ${e.message || '请启动 launcher 后刷新'}`
    probeState.value = 'error'
  }
})

onUnmounted(() => timer && window.clearInterval(timer))
</script>

<template>
  <div class="space-y-14">
    <section class="mx-auto max-w-[860px] pt-4 text-center">
      <h1 class="hand-title">{{ greeting }}，同学</h1>
      <p v-reveal="0" class="mt-3 text-[13px] muted">愿你的每一分，都落在想去的地方</p>
      <p class="mt-9 min-h-[24px] text-[15px] muted">
        {{ typed }}<span class="type-caret"></span>
      </p>

      <div v-reveal="1" class="mt-9 flex flex-wrap items-center justify-center gap-x-9 gap-y-5">
        <div v-for="m in metrics" :key="m.label" class="metric-item">
          <span class="metric-circle" :class="m.cls">{{ m.glyph }}</span>
          <span class="text-left">
            <span class="metric-value block">{{ m.value }}</span>
            <span class="metric-label block">{{ m.label }}</span>
          </span>
        </div>
      </div>

      <div v-reveal="2" class="mt-12 flex flex-wrap items-center justify-between gap-3 text-left text-[12px] muted">
        <span class="flex items-center gap-2">
          <svg class="h-3.5 w-3.5 text-accent" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <circle cx="12" cy="12" r="9" /><path d="M12 8v4l3 2" stroke-linecap="round" />
          </svg>
          依据本省当年志愿填报规则，按位次输出「冲 / 稳 / 保」分档建议
        </span>
        <span class="flex items-center gap-3">
          <span class="pill" :class="overview?.demoData ? 'pill-amber' : 'pill-mint'">
            {{ overview?.demoData ? '演示数据' : `版本 ${overview?.dataVersion || '—'}` }}
          </span>
          <RouterLink to="/score" class="link text-[12px]">开始录入 →</RouterLink>
        </span>
      </div>

      <div class="progress-track mt-4"><div class="progress-fill" style="width: 100%"></div></div>
      <div class="mt-3 flex flex-wrap items-center justify-between gap-2 text-left text-[11px] faint">
        <span>P1 链路 5 / 5：录入 → 换算位次 → 匹配 → 志愿表 → 导出</span>
        <span :style="probeState === 'error' ? { color: 'var(--rose)' } : null">{{ backendStatus }}</span>
      </div>

      <div v-reveal="3" class="btn-group mt-8 justify-center">
        <RouterLink to="/score" class="btn btn-mint"><span class="btn-glyph">✎</span> 录入成绩</RouterLink>
        <RouterLink to="/match" class="btn btn-blue"><span class="btn-glyph">◎</span> 智能匹配</RouterLink>
        <RouterLink to="/plan" class="btn btn-amber"><span class="btn-glyph">▤</span> 志愿方案</RouterLink>
        <RouterLink to="/schools" class="btn btn-violet"><span class="btn-glyph">⌕</span> 院校库</RouterLink>
      </div>
    </section>

    <section class="grid gap-4 lg:grid-cols-12">
      <div v-reveal="0" class="glass p-6 lg:col-span-7">
        <p class="eyebrow">How it works</p>
        <h2 class="mt-2 text-[17px] font-semibold">三步完成志愿方案</h2>
        <div class="mt-5 space-y-0">
          <div v-for="(step, i) in steps" :key="step.no" v-reveal="i + 1" class="step-row">
            <span class="step-no">{{ step.no }}</span>
            <div>
              <h3 class="text-[13px] font-semibold">{{ step.title }}</h3>
              <p class="mt-1 text-[12px] leading-6 muted">{{ step.desc }}</p>
            </div>
          </div>
        </div>
      </div>

      <div v-reveal="1" class="glass p-6 lg:col-span-5">
        <p class="eyebrow">By the numbers</p>
        <h2 class="mt-2 text-[17px] font-semibold">当前数据规模</h2>
        <div class="mt-5 space-y-5">
          <div v-for="(fact, i) in facts" :key="fact.label" v-reveal="i + 1" class="fact-row">
            <span class="stat-num text-accent" v-count="fact.value">0</span>
            <div>
              <p class="text-[13px] font-semibold">{{ fact.label }}</p>
              <p class="mt-1 text-[11px] faint">{{ fact.hint }}</p>
            </div>
          </div>
        </div>
        <p class="mt-5 text-[11px] leading-5 faint">
          数据版本 {{ overview?.dataVersion || '检测中' }} · {{ overview?.moduleCount ?? '—' }} 个后端模块 ·
          {{ overview?.policyCount ?? '—' }} 套省年规则
        </p>
      </div>
    </section>

    <section>
      <p v-reveal="0" class="eyebrow">Design baselines</p>
      <h2 v-reveal="1" class="mt-2 text-[17px] font-semibold">三条不可妥协的基线</h2>
      <div class="mt-4 grid gap-4 lg:grid-cols-12">
        <div v-for="(p, i) in pillars" :key="p.title" v-reveal="i + 1" :class="['glass lift p-5', p.span]">
          <span class="pill pill-sky">{{ p.tag }}</span>
          <h3 class="mt-4 text-[14px] font-semibold">{{ p.title }}</h3>
          <p class="mt-2 text-[12px] leading-6 muted">{{ p.desc }}</p>
        </div>
      </div>
    </section>

    <section v-reveal="0" class="glass-flat flex flex-wrap items-center justify-between gap-4 p-6">
      <div>
        <p class="eyebrow">Current stage · P1 单省试点</p>
        <h2 class="mt-2 text-[15px] font-semibold">整条链路已可运行</h2>
        <p class="mt-2 max-w-[70ch] text-[12px] leading-6 muted">
          成绩档案落库、位次换算、候选召回、冲稳保分档、志愿校验与导出均已接通；
          当前数据集为<strong>演示数据（合成值）</strong>，仅用于验证工程链路，不是真实录取分数。
          接入各省考试院公示数据后只需导入并发布新的 data_version。完整功能与验收口径见
          <span class="link">docs/功能清单.md</span>。
        </p>
      </div>
      <RouterLink to="/match" class="btn btn-teal">
        <span class="btn-glyph">◎</span> 查看匹配引擎 <span class="link-arrow">→</span>
      </RouterLink>
    </section>

    <p class="pb-2 text-center text-[11px] faint">
      {{ overview?.disclaimer || '所有建议仅供参考，最终以各省教育考试院公布数据为准' }}
    </p>
  </div>
</template>

<style scoped>
.step-row { display: grid; grid-template-columns: 42px 1fr; gap: 10px; align-items: start; padding: 13px 0; border-bottom: 1px solid var(--line-soft); transition: transform .35s var(--ease); }
.step-row:last-child { border-bottom: 0; }
.step-row:hover { transform: translateX(5px); }
.step-no { font-size: 12px; font-weight: 600; color: var(--accent-text); font-variant-numeric: tabular-nums; }
.fact-row { display: flex; align-items: baseline; gap: 16px; padding-bottom: 16px; border-bottom: 1px solid var(--line-soft); }
.fact-row:last-child { border-bottom: 0; padding-bottom: 0; }
</style>
