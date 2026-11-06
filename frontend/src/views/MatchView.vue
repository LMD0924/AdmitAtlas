<script setup>
import { computed } from 'vue'
import { useMatch } from '@/composables/useMatch'
import { useAppStore } from '@/stores/app'
import RiskTag from '@/components/RiskTag.vue'

const store = useAppStore()
const { loading, error, result, run } = useMatch()

const tiers = [
  { key: 'CHONG', label: '冲', desc: '位次略高于历史录取位次', cls: 'border-red-100 bg-risk-chong' },
  { key: 'WEN', label: '稳', desc: '位次与历史录取位次相当', cls: 'border-amber-100 bg-risk-wen' },
  { key: 'BAO', label: '保', desc: '位次明显优于历史录取位次', cls: 'border-teal-100 bg-risk-bao' }
]

const summary = computed(() => ({
  province: store.provinceCode || '未填写',
  year: store.examYear,
  score: store.totalScore ?? '未填写',
  mode: store.examMode
}))
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-medium text-slate-800">智能匹配</h1>
        <p class="mt-2 text-sm text-slate-500">位次换算 → 规则过滤 → 候选检索 → 风险打分 → 冲稳保分档</p>
      </div>
      <button
        class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-800 disabled:opacity-50"
        :disabled="loading || !store.hasScore"
        @click="run()"
      >
        {{ loading ? '匹配中…' : '开始匹配' }}
      </button>
    </div>

    <div class="aa-card flex flex-wrap gap-6 text-sm text-slate-600">
      <span>省份：<span class="font-medium text-slate-800">{{ summary.province }}</span></span>
      <span>年份：<span class="font-medium text-slate-800">{{ summary.year }}</span></span>
      <span>总分：<span class="font-medium text-slate-800">{{ summary.score }}</span></span>
      <span>模式：<span class="font-medium text-slate-800">{{ summary.mode }}</span></span>
      <span>位次：<span class="font-medium text-slate-800">{{ store.rank ?? '待换算' }}</span></span>
    </div>

    <div v-if="error" class="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
      {{ error }}
    </div>

    <div v-if="result" class="aa-card">
      <p class="text-sm text-slate-600">匹配结果已返回，共 {{ result.items?.length ?? 0 }} 条建议。</p>
    </div>

    <div class="grid gap-4 md:grid-cols-3">
      <div v-for="t in tiers" :key="t.key" :class="['rounded-xl border p-5', t.cls]">
        <div class="flex items-center gap-2">
          <RiskTag :tier="t.key" />
          <h2 class="text-base font-medium text-slate-800">{{ t.label }} · 候选院校</h2>
        </div>
        <p class="mt-2 text-sm leading-6 text-slate-600">{{ t.desc }}</p>
        <p class="mt-6 text-xs text-slate-400">P1 接入录取数据后展示候选列表</p>
      </div>
    </div>

    <p class="text-xs leading-6 text-slate-400">
      输出为「匹配度 / 风险等级」，不构成录取承诺；最终以各省教育考试院公布数据为准。
    </p>
  </div>
</template>
