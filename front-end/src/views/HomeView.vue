<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getStrategies } from '@/api'

const steps = [
  { no: '01', title: '录入成绩', desc: '省份、年份、选科、总分与单科分，换算为可比对的省排名' },
  { no: '02', title: '按省规则匹配', desc: '过滤选科与批次要求，检索近三年同位次院校，划分冲 / 稳 / 保' },
  { no: '03', title: '生成志愿表', desc: '按本省志愿结构生成可编辑方案，导出 PDF 或 Excel' }
]

const facts = [
  { value: 3, label: '考试模式策略', hint: '传统文理 · 院校专业组 · 专业+院校' },
  { value: 11, label: '后端模块', hint: '模块化单体，单一启动入口' },
  { value: 121, label: '规划功能点', hint: '按域编号，含验收口径' }
]

const pillars = [
  { span: 'lg:col-span-5', tag: '数据口径', title: '跨年只比位次', desc: '每年难度与满分不同，绝对分数不可比。所有历史投档线统一换算为省排名后进入比较。' },
  { span: 'lg:col-span-4', tag: '规则引擎', title: '省 × 年 × 模式', desc: '志愿单位、志愿数、同分排序与选科要求全部配置化，策略工厂负责分发。' },
  { span: 'lg:col-span-3', tag: '数据治理', title: '先审核，后发布', desc: '原始留证、质量校验、人工复核与版本灰度，未通过的数据不进在线库。' }
]

const metrics = [
  { cls: 'is-mint', glyph: '✓', value: '已就绪', label: '3 种考试模式' },
  { cls: 'is-sky', glyph: '▶', value: '进行中', label: 'P0 骨架' },
  { cls: 'is-slate', glyph: '◷', value: '待开始', label: '121 个功能点' },
  { cls: 'is-violet', glyph: '▥', value: '17%', label: '总进度' }
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
const backendStatus = ref('正在检测后端…')
const probeState = ref('loading')

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
    const res = await getStrategies()
    modes.value = res?.data?.supportedModes ?? []
    backendStatus.value = `后端已连通 · 已注册 ${modes.value.length} 种考试模式`
    probeState.value = 'ok'
  } catch {
    backendStatus.value = '后端未启动 · 启动 launcher 后刷新'
    probeState.value = 'error'
  }
})

onUnmounted(() => timer && window.clearInterval(timer))
</script>

<template>
  <div class="space-y-14">
    <!-- hero: handwriting greeting, just like the reference -->
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
          <span class="pill pill-mint">进度 17%</span>
          <RouterLink to="/score" class="link text-[12px]">开始录入 →</RouterLink>
        </span>
      </div>

      <div class="progress-track mt-4">
        <div class="progress-fill" style="width: 17%"></div>
      </div>
      <div class="mt-3 flex flex-wrap items-center justify-between gap-2 text-left text-[11px] faint">
        <span>已完成 1 / 4 阶段 · P0 骨架</span>
        <span :style="probeState === 'error' ? { color: 'var(--rose)' } : null">{{ backendStatus }}</span>
      </div>
    </section>

    <!-- light paper cards -->
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
        <h2 class="mt-2 text-[17px] font-semibold">工程规模</h2>
        <div class="mt-5 space-y-5">
          <div v-for="(fact, i) in facts" :key="fact.label" v-reveal="i + 1" class="fact-row">
            <span class="stat-num text-accent" v-count="fact.value">0</span>
            <div>
              <p class="text-[13px] font-semibold">{{ fact.label }}</p>
              <p class="mt-1 text-[11px] faint">{{ fact.hint }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section>
      <p v-reveal="0" class="eyebrow">Design baselines</p>
      <h2 v-reveal="1" class="mt-2 text-[17px] font-semibold">三条不可妥协的基线</h2>
      <div class="mt-4 grid gap-4 lg:grid-cols-12">
        <div
          v-for="(p, i) in pillars"
          :key="p.title"
          v-reveal="i + 1"
          :class="['glass lift p-5', p.span]"
        >
          <span class="pill pill-sky">{{ p.tag }}</span>
          <h3 class="mt-4 text-[14px] font-semibold">{{ p.title }}</h3>
          <p class="mt-2 text-[12px] leading-6 muted">{{ p.desc }}</p>
        </div>
      </div>
    </section>

    <section v-reveal="0" class="glass-flat flex flex-wrap items-center justify-between gap-4 p-6">
      <div>
        <p class="eyebrow">Current stage</p>
        <h2 class="mt-2 text-[15px] font-semibold">P0 骨架已就绪</h2>
        <p class="mt-2 max-w-[70ch] text-[12px] leading-6 muted">
          工程结构、统一响应、DTO 契约与三种考试模式的策略工厂已可运行；数据采集、位次换算与匹配算法仍是占位接口。
          完整功能与验收口径见 <span class="link">docs/功能清单.md</span>。
        </p>
      </div>
      <RouterLink to="/match" class="btn btn-ghost">
        查看匹配引擎 <span class="link-arrow">→</span>
      </RouterLink>
    </section>

    <p class="pb-2 text-center text-[11px] faint">所有建议仅供参考，最终以各省教育考试院公布数据为准</p>
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
