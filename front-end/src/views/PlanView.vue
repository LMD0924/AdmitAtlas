<script setup>
import RiskTag from '@/components/RiskTag.vue'

const rows = [
  { no: 1, school: '示例院校 A', unit: '院校专业组 01', tier: 'CHONG' },
  { no: 2, school: '示例院校 B', unit: '院校专业组 02', tier: 'WEN' },
  { no: 3, school: '示例院校 C', unit: '院校专业组 03', tier: 'BAO' }
]

const distribution = [
  { label: '冲', cls: 'tag-chong', ratio: 34 },
  { label: '稳', cls: 'tag-wen', ratio: 40 },
  { label: '保', cls: 'tag-bao', ratio: 26 }
]
</script>

<template>
  <div class="space-y-8">
    <div>
      <p v-reveal="0" class="eyebrow">Volunteer plan</p>
      <h1 v-reveal="1" class="mt-5 text-[clamp(2rem,3.4vw,3rem)] font-medium leading-tight tracking-[-0.04em]">
        把选择排成<br /><span class="text-accent">有梯度的顺序</span>
      </h1>
      <p v-reveal="2" class="mt-4 max-w-[60ch] text-[14px] leading-7 muted">
        按本省志愿结构生成，可拖拽调序、校验数量并导出。志愿单位与数量均由规则配置驱动。
      </p>
    </div>

    <div class="grid gap-4 lg:grid-cols-12">
      <div v-reveal="0" v-spotlight class="glass spotlight p-6 lg:col-span-5">
        <p class="eyebrow">Structure</p>
        <div class="mt-6 space-y-5">
          <div class="flex items-baseline justify-between">
            <span class="text-[13px] muted">志愿单位</span>
            <span class="text-[15px] font-medium">院校专业组</span>
          </div>
          <hr class="hairline" />
          <div class="flex items-baseline justify-between">
            <span class="text-[13px] muted">志愿数</span>
            <span class="stat-num text-accent" v-count="45">0</span>
          </div>
          <hr class="hairline" />
          <div class="flex items-center justify-between">
            <span class="text-[13px] muted">数据状态</span>
            <span class="badge">P0 占位数据</span>
          </div>
        </div>
      </div>

      <div v-reveal="1" class="glass p-6 lg:col-span-7">
        <div class="flex items-center justify-between gap-3">
          <p class="eyebrow">Grade distribution</p>
          <span class="text-[12px] faint">建议比例 · 示意</span>
        </div>
        <div class="mt-7 flex h-3 w-full overflow-hidden rounded-full placeholder-bar">
          <div
            v-for="(d, i) in distribution"
            :key="d.label"
            class="bar-fill h-full"
            :class="d.cls"
            :style="{ width: d.ratio + '%', animationDelay: i * 0.12 + 's' }"
          ></div>
        </div>
        <div class="mt-6 flex flex-wrap gap-6">
          <div v-for="d in distribution" :key="d.label" class="flex items-center gap-2">
            <span class="tag" :class="d.cls">{{ d.label }}</span>
            <span class="text-[13px] muted">{{ d.ratio }}%</span>
          </div>
        </div>
        <p class="mt-7 text-[12px] leading-6 faint">
          冲稳保比例检查、逐条资格校验与方案对比将在 P1 接入；当前仅展示版式。
        </p>
      </div>
    </div>

    <div v-reveal="0" class="glass overflow-hidden">
      <div class="flex flex-wrap items-center justify-between gap-3 px-6 py-5">
        <h2 class="text-[16px] font-medium">志愿表</h2>
        <RouterLink to="/match" class="btn btn-quiet text-[13px]">
          从匹配结果添加 <span class="link-arrow">→</span>
        </RouterLink>
      </div>
      <div class="overflow-x-auto">
        <table class="data-table min-w-[620px]">
          <thead>
            <tr>
              <th>序号</th>
              <th>院校 / 志愿单位</th>
              <th>档位</th>
              <th>匹配度</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in rows" :key="row.no" v-reveal="i">
              <td class="font-medium tabular-nums">{{ String(row.no).padStart(2, '0') }}</td>
              <td style="color: var(--text)">{{ row.school }} · {{ row.unit }}</td>
              <td><RiskTag :tier="row.tier" /></td>
              <td class="faint">—</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="px-6 py-5 text-[12px] faint">
        拖拽排序（vuedraggable）、志愿数量校验与 PDF / Excel 导出将在 P1 接入。
      </p>
    </div>
  </div>
</template>

<style scoped>
.bar-fill { transform-origin: left; animation: bar-grow .9s var(--ease) both; }
.tag-chong.bar-fill { background: var(--chong-text); }
.tag-wen.bar-fill { background: var(--wen-text); }
.tag-bao.bar-fill { background: var(--bao-text); }
@keyframes bar-grow { from { transform: scaleX(0); } to { transform: scaleX(1); } }
</style>
