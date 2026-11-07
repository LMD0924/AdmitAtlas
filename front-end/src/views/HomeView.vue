<script setup>
import { onMounted, ref } from 'vue'
import { NButton, NCard } from 'naive-ui'
import { getStrategies } from '@/api'

const steps = [
  { no: '1', title: '录入成绩', desc: '省份、年份、选科、总分与单科分，自动换算省排名（位次）' },
  { no: '2', title: '按省规则匹配', desc: '过滤选科与批次要求，检索近三年同位次院校并划分冲 / 稳 / 保' },
  { no: '3', title: '生成志愿表', desc: '按本省志愿结构生成可编辑方案，导出 PDF 或 Excel' }
]

const modes = ref([])
const backendStatus = ref('检测中…')

onMounted(async () => {
  try {
    const res = await getStrategies()
    modes.value = res?.data?.supportedModes ?? []
    backendStatus.value = `后端已连通 · 已注册 ${modes.value.length} 种考试模式`
  } catch (e) {
    backendStatus.value = '后端未启动（启动网关与匹配引擎后刷新）'
  }
})
</script>

<template>
  <div class="space-y-8">
    <section class="rounded-2xl border border-brand-100 bg-brand-50 px-8 py-10">
      <p class="text-xs font-medium tracking-wide text-brand-600">ADMIT · ATLAS</p>
      <h1 class="mt-2 text-3xl font-medium text-brand-800">把分数变成位次，把位次变成选择</h1>
      <p class="mt-3 max-w-2xl text-sm leading-7 text-slate-600">
        基于全国高校历年录取投档线、一分一段表与招生计划，按你所在省份当年的志愿填报规则，
        输出「冲 / 稳 / 保」分档的可编辑志愿方案。
      </p>
      <div class="mt-6 flex flex-wrap items-center gap-3">
        <RouterLink to="/score">
          <NButton type="primary" size="medium">开始录入成绩</NButton>
        </RouterLink>
        <RouterLink to="/schools">
          <NButton size="medium" secondary>浏览院校库</NButton>
        </RouterLink>
        <span class="text-xs text-slate-500">{{ backendStatus }}</span>
      </div>
    </section>

    <section>
      <h2 class="mb-4 text-lg font-medium text-slate-800">三步完成志愿方案</h2>
      <div class="grid gap-4 md:grid-cols-3">
        <NCard v-for="step in steps" :key="step.no" class="aa-card" :bordered="true">
          <div class="mb-3 flex h-8 w-8 items-center justify-center rounded-lg bg-brand-100 text-sm font-medium text-brand-800">
            {{ step.no }}
          </div>
          <h3 class="text-base font-medium text-slate-800">{{ step.title }}</h3>
          <p class="mt-2 text-sm leading-6 text-slate-500">{{ step.desc }}</p>
        </NCard>
      </div>
    </section>

    <section class="aa-card">
      <h2 class="text-base font-medium text-slate-800">当前阶段</h2>
      <p class="mt-2 text-sm leading-7 text-slate-600">
        仓库处于 <span class="font-medium text-brand-800">P0 骨架阶段</span>：工程结构、统一 Web 入口、统一响应、
        DTO 契约与三种考试模式的策略工厂已就绪；数据采集与匹配算法为占位接口。
        设计依据见 <span class="font-medium">docs/架构设计.md</span>。
      </p>
      <p class="mt-2 text-xs text-slate-400">所有建议仅供参考，最终以各省教育考试院公布数据为准。</p>
    </section>
  </div>
</template>
