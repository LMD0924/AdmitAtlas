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
  <div class="space-y-8">
    <div class="flex flex-wrap items-end justify-between gap-5">
      <div>
        <p v-reveal="0" class="eyebrow">Matching engine</p>
        <h1 v-reveal="1" class="mt-5 text-[clamp(2rem,3.4vw,3rem)] font-medium leading-tight tracking-[-0.04em]">
          在同一把尺子上<br /><span class="text-accent">找到你的位置</span>
        </h1>
        <p v-reveal="2" class="mt-4 max-w-[60ch] text-[14px] leading-7 muted">
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

    <div v-reveal="0" class="glass grid grid-cols-2 gap-x-6 gap-y-5 p-6 md:grid-cols-5">
      <div v-for="item in summary" :key="item.label">
        <p class="text-[11px] uppercase tracking-[0.12em] faint">{{ item.label }}</p>
        <p class="mt-1.5 truncate text-[14px] font-medium">{{ item.value }}</p>
      </div>
    </div>

    <p v-if="!store.hasScore" v-reveal="0" class="glass-flat px-5 py-3 text-[13px] muted">
      还没有成绩档案，请先到 <RouterLink to="/score" class="text-accent underline underline-offset-4">成绩录入</RouterLink> 保存后再匹配。
    </p>

    <Transition name="page">
      <div v-if="error" class="glass px-5 py-4 text-[13px]" :style="{ color: 'var(--chong-text)' }">{{ error }}</div>
    </Transition>

    <Transition name="page">
      <div v-if="result" class="glass px-5 py-4 text-[13px] muted">
        匹配结果已返回，共 {{ result.items?.length ?? 0 }} 条建议。
      </div>
    </Transition>

    <div v-if="loading" class="grid gap-4 lg:grid-cols-12">
      <div v-for="(t, i) in tiers" :key="t.key" :class="['glass shimmer h-52 p-6', t.span]">
        <div class="h-3 w-20 rounded-full placeholder-bar"></div>
        <div class="mt-6 h-8 w-16 rounded-lg placeholder-bar"></div>
        <div class="mt-8 h-2 w-3/4 rounded-full placeholder-bar"></div>
      </div>
    </div>

    <div v-else class="grid gap-4 lg:grid-cols-12">
      <div
        v-for="(t, i) in tiers"
        :key="t.key"
        v-reveal="i + 1"
        v-spotlight
        :class="['glass spotlight lift tier-card p-6', t.span, `tier-${t.tone}`]"
      >
        <div class="flex items-center gap-3">
          <RiskTag :tier="t.key" />
          <span class="text-[11px] uppercase tracking-[0.12em] faint">Tier 0{{ i + 1 }}</span>
        </div>
        <p class="mt-7 text-[clamp(2.4rem,4vw,3.4rem)] font-medium leading-none tracking-[-0.05em]">{{ t.label }}</p>
        <p class="mt-3 text-[13px] leading-6 muted">{{ t.desc }}</p>
        <div class="mt-8 flex items-center justify-between border-t border-hairline pt-4">
          <span class="text-[12px] faint">P1 接入录取数据后展示候选列表</span>
          <span class="link-arrow text-accent">→</span>
        </div>
      </div>
    </div>

    <p v-reveal="0" class="text-center text-[12px] leading-6 faint">
      输出为「匹配度 / 风险等级」，不构成录取承诺；最终以各省教育考试院公布数据为准
    </p>
  </div>
</template>

<style scoped>
.tier-card { min-height: 234px; }
.tier-chong { background: linear-gradient(180deg, var(--chong-bg), transparent 62%), var(--glass); }
.tier-wen { background: linear-gradient(180deg, var(--wen-bg), transparent 62%), var(--glass); }
.tier-bao { background: linear-gradient(180deg, var(--bao-bg), transparent 62%), var(--glass); }
</style>
