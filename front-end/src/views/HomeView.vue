<script setup>
import { onMounted, ref } from 'vue'
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

const modes = ref([])
const backendStatus = ref('正在检测后端…')
const probeState = ref('loading')

onMounted(async () => {
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
</script>

<template>
  <div class="space-y-20">
    <section class="grid items-center gap-10 lg:grid-cols-12">
      <div class="lg:col-span-7">
        <p v-reveal="0" class="eyebrow">Admit · Atlas / 录取数据导航</p>
        <h1 v-reveal="1" class="display mt-6">
          把分数变成位次，<br />
          <span class="text-accent">把位次变成选择</span>
        </h1>
        <p v-reveal="2" class="mt-6 max-w-[54ch] text-[15px] leading-8 muted">
          基于全国高校历年投档线、一分一段表与招生计划，按你所在省份当年的志愿填报规则，
          输出「冲 / 稳 / 保」分档的可编辑志愿方案。
        </p>
        <div v-reveal="3" class="mt-9 flex flex-wrap items-center gap-3">
          <RouterLink to="/score" class="btn btn-primary">
            开始录入成绩 <span class="link-arrow">→</span>
          </RouterLink>
          <RouterLink to="/schools" class="btn btn-ghost">浏览院校库</RouterLink>
        </div>
        <div v-reveal="4" class="mt-8 flex items-center gap-3 text-xs muted">
          <span class="pulse-dot inline-block h-1.5 w-1.5 rounded-full bg-accent"></span>
          <span :style="probeState === 'error' ? { color: 'var(--chong-text)' } : null">{{ backendStatus }}</span>
        </div>
      </div>

      <div v-reveal="2" class="relative lg:col-span-5">
        <div class="atlas-view float">
          <div class="atlas-ring atlas-ring-1"></div>
          <div class="atlas-ring atlas-ring-2"></div>
          <div class="atlas-ring atlas-ring-3"></div>
          <div class="atlas-core">A</div>
          <span class="atlas-dot atlas-dot-1"></span>
          <span class="atlas-dot atlas-dot-2"></span>
          <span class="atlas-dot atlas-dot-3"></span>
        </div>
        <div class="atlas-chip atlas-chip-1 glass-strong glass-flat">
          <span class="tag tag-chong">冲</span>
          <span class="text-xs muted">位次差 +</span>
        </div>
        <div class="atlas-chip atlas-chip-2 glass-strong glass-flat">
          <span class="tag tag-wen">稳</span>
          <span class="text-xs muted">位次相当</span>
        </div>
        <div class="atlas-chip atlas-chip-3 glass-strong glass-flat">
          <span class="tag tag-bao">保</span>
          <span class="text-xs muted">位次占优</span>
        </div>
      </div>
    </section>

    <section class="grid gap-4 lg:grid-cols-12">
      <div v-reveal="0" class="glass spotlight lift p-7 lg:col-span-7">
        <p class="eyebrow">How it works</p>
        <h2 class="section-title mt-4">三步完成志愿方案</h2>
        <div class="mt-8 space-y-1">
          <div v-for="(step, i) in steps" :key="step.no" v-reveal="i + 1" class="step-row">
            <span class="step-no">{{ step.no }}</span>
            <div>
              <h3 class="text-[15px] font-medium">{{ step.title }}</h3>
              <p class="mt-1 text-[13px] leading-6 muted">{{ step.desc }}</p>
            </div>
          </div>
        </div>
      </div>

      <div v-reveal="1" class="glass spotlight lift p-7 lg:col-span-5">
        <p class="eyebrow">By the numbers</p>
        <h2 class="section-title mt-4">工程规模</h2>
        <div class="mt-8 space-y-6">
          <div v-for="(fact, i) in facts" :key="fact.label" v-reveal="i + 1" class="fact-row">
            <span class="stat-num text-accent" v-count="fact.value">0</span>
            <div>
              <p class="text-[14px] font-medium">{{ fact.label }}</p>
              <p class="mt-1 text-[12px] faint">{{ fact.hint }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section>
      <p v-reveal="0" class="eyebrow">Design baselines</p>
      <h2 v-reveal="1" class="section-title mt-4">三条不可妥协的基线</h2>
      <div class="mt-6 grid gap-4 lg:grid-cols-12">
        <div
          v-for="(p, i) in pillars"
          :key="p.title"
          v-reveal="i + 1"
          v-spotlight
          :class="['glass spotlight lift p-6', p.span]"
        >
          <span class="badge">{{ p.tag }}</span>
          <h3 class="mt-5 text-[16px] font-medium">{{ p.title }}</h3>
          <p class="mt-2 text-[13px] leading-7 muted">{{ p.desc }}</p>
        </div>
      </div>
    </section>

    <section v-reveal="0" class="glass shimmer flex flex-wrap items-center justify-between gap-5 p-7">
      <div>
        <p class="eyebrow">Current stage</p>
        <h2 class="mt-3 text-[18px] font-medium">P0 骨架已就绪</h2>
        <p class="mt-2 max-w-[62ch] text-[13px] leading-7 muted">
          工程结构、统一响应、DTO 契约与三种考试模式的策略工厂已可运行；数据采集、位次换算与匹配算法仍是占位接口。
          完整功能与验收口径见 <span class="text-accent">docs/功能清单.md</span>。
        </p>
      </div>
      <RouterLink to="/match" class="btn btn-ghost">
        查看匹配引擎 <span class="link-arrow">→</span>
      </RouterLink>
    </section>

    <p class="pb-4 text-center text-[12px] faint">所有建议仅供参考，最终以各省教育考试院公布数据为准</p>
  </div>
</template>

<style scoped>
.atlas-view { position: relative; width: min(100%, 380px); aspect-ratio: 1; margin: 0 auto; display: grid; place-items: center; }
.atlas-ring { position: absolute; border-radius: 50%; border: 1px solid var(--line); }
.atlas-ring-1 { inset: 6%; border-style: dashed; animation: orbit 40s linear infinite; }
.atlas-ring-2 { inset: 20%; border-color: var(--accent-soft); animation: orbit 28s linear infinite reverse; }
.atlas-ring-3 { inset: 34%; background: var(--accent-soft); border-color: var(--accent-soft); }
.atlas-core { position: relative; z-index: 2; display: grid; place-items: center; width: 62px; height: 62px; border-radius: 20px; background: var(--accent); color: var(--on-accent); font-size: 24px; font-weight: 500; box-shadow: 0 16px 40px var(--accent-soft); }
.atlas-dot { position: absolute; width: 9px; height: 9px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 0 5px var(--accent-soft); }
.atlas-dot-1 { top: 6%; left: 50%; }
.atlas-dot-2 { bottom: 18%; right: 12%; background: var(--wen-text); box-shadow: 0 0 0 5px var(--wen-bg); }
.atlas-dot-3 { bottom: 24%; left: 10%; background: var(--bao-text); box-shadow: 0 0 0 5px var(--bao-bg); }
.atlas-chip { position: absolute; display: flex; align-items: center; gap: 8px; padding: 9px 13px; border-radius: 14px; box-shadow: var(--shadow-soft); }
.atlas-chip-1 { top: 12%; right: -2%; animation: float 6s ease-in-out infinite; }
.atlas-chip-2 { bottom: 20%; left: -6%; animation: float 7.5s ease-in-out infinite .6s; }
.atlas-chip-3 { bottom: 2%; right: 8%; animation: float 8.5s ease-in-out infinite 1.2s; }
@keyframes orbit { to { transform: rotate(360deg); } }
.step-row { display: grid; grid-template-columns: 52px 1fr; gap: 14px; align-items: start; padding: 16px 0; border-bottom: 1px solid var(--line-soft); transition: transform .4s var(--ease); }
.step-row:last-child { border-bottom: 0; }
.step-row:hover { transform: translateX(6px); }
.step-no { font-size: 13px; font-weight: 500; letter-spacing: .04em; color: var(--accent-text); font-variant-numeric: tabular-nums; }
.fact-row { display: flex; align-items: baseline; gap: 18px; padding-bottom: 20px; border-bottom: 1px solid var(--line-soft); }
.fact-row:last-child { border-bottom: 0; padding-bottom: 0; }
@media (max-width: 1024px) { .atlas-view { width: min(100%, 300px); } .atlas-chip-2 { left: 0; } .atlas-chip-1 { right: 0; } }
</style>
