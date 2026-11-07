<script setup>
import { computed } from 'vue'
import { useMatch } from '@/composables/useMatch'
import { useAppStore } from '@/stores/app'
import RiskTag from '@/components/RiskTag.vue'

const store = useAppStore()
const { loading, error, result, run } = useMatch()

const tiers = [
  { key: 'CHONG', label: '冲', span: 'lg:col-span-5', desc: '位次略高于历史录取位次，波动较大', tone: 'chong' },
  { key: 'WEN', label: '稳', span: 'lg:col-span-4', desc: '位次与历史录取位次相当', tone: 'wen' },
  { key: 'BAO', label: '保', span: 'lg:col-span-3', desc: '位次明显优于历史录取位次', tone: 'bao' }
]

const summary = computed(() => [
  { label: '省份', value: store.provinceCode || '未填写' },
  { label: '年份', value: store.examYear },
  { label: '总分', value: store.totalScore ?? '未填写' },
  { label: '考试模式', value: store.examMode },
  { label: '位次', value: store.rank ?? '待换算' }
])
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p v-reveal="0" class="eyebrow">Matching engine</p>
        <h1 v-reveal="1" class="mt-2 text-[22px] font-semibold tracking-[-0.02em]">在同一把尺子上找到你的位置</h1>
        <p v-reveal="2" class="mt-2 max-w-[76ch] text-[12px] leading-6 muted">
          位次换算 → 规则过滤 → 候选检索 → 风险打分 → 冲稳保分档
        </p>
      </div>
      <button
        v-reveal="2"
        class="btn btn-primary"
        :disabled="loading || !store.hasScore"
        @click="run()"
      >
        {{ loading ? '匹配中…' : '开始匹配' }}
        <span class="link-arrow">→</span>
      </button>
    </div>

    <div v-reveal="0" class="glass grid grid-cols-2 gap-x-6 gap-y-4 p-5 md:grid-cols-5">
      <div v-for="item in summary" :key="item.label">
        <p class="text-[11px] faint">{{ item.label }}</p>
        <p class="mt-1 truncate text-[13px] font-semibold">{{ item.value }}</p>
      </div>
    </div>

    <p v-if="!store.hasScore" v-reveal="0" class="glass-flat px-4 py-3 text-[12px] muted">
      还没有成绩档案，请先到 <RouterLink to="/score" class="link">成绩录入</RouterLink> 保存后再匹配。
    </p>

    <Transition name="page">
      <div v-if="error" class="glass px-4 py-3 text-[12px]" :style="{ color: 'var(--rose)' }">{{ error }}</div>
    </Transition>

    <Transition name="page">
      <div v-if="result" class="glass px-4 py-3 text-[12px] muted">
        匹配结果已返回，共 {{ result.items?.length ?? 0 }} 条建议。
      </div>
    </Transition>

    <div v-if="loading" class="grid gap-4 lg:grid-cols-12">
      <div v-for="t in tiers" :key="t.key" :class="['glass shimmer h-44 p-5', t.span]">
        <div class="h-2.5 w-20 rounded-full placeholder-bar"></div>
        <div class="mt-6 h-6 w-16 rounded-lg placeholder-bar"></div>
        <div class="mt-7 h-2 w-3/4 rounded-full placeholder-bar"></div>
      </div>
    </div>

    <div v-else class="grid gap-4 lg:grid-cols-12">
      <div
        v-for="(t, i) in tiers"
        :key="t.key"
        v-reveal="i + 1"
        :class="['glass lift tier-card p-5', t.span, `tier-${t.tone}`]"
      >
        <div class="flex items-center justify-between gap-3">
          <RiskTag :tier="t.key" />
          <span class="text-[11px] faint">Tier 0{{ i + 1 }}</span>
        </div>
        <div class="mt-6 flex items-baseline gap-3">
          <span class="text-[26px] font-semibold leading-none">{{ t.label }}</span>
          <span class="text-[12px] muted">{{ t.desc }}</span>
        </div>
        <div class="progress-track mt-6">
          <div class="progress-fill" :style="{ width: [28, 46, 26][i] + '%' }"></div>
        </div>
        <div class="mt-5 flex items-center justify-between border-t border-hairline pt-3">
          <span class="text-[11px] faint">P1 接入录取数据后展示候选列表</span>
          <RouterLink to="/plan" class="link text-[11px]">查看方案 →</RouterLink>
        </div>
      </div>
    </div>

    <p v-reveal="0" class="text-center text-[11px] leading-6 faint">
      输出为「匹配度 / 风险等级」，不构成录取承诺；最终以各省教育考试院公布数据为准
    </p>
  </div>
</template>

<style scoped>
.tier-card { min-height: 196px; border-top: 3px solid var(--line); }
.tier-chong { border-top-color: var(--chong-text); }
.tier-wen { border-top-color: var(--wen-text); }
.tier-bao { border-top-color: var(--bao-text); }
</style>
