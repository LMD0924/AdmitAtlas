<script setup>
import RiskTag from '@/components/RiskTag.vue'

const rows = [
  { no: 1, school: '示例院校 A', unit: '院校专业组 01', tier: 'CHONG' },
  { no: 2, school: '示例院校 B', unit: '院校专业组 02', tier: 'WEN' },
  { no: 3, school: '示例院校 C', unit: '院校专业组 03', tier: 'BAO' }
]
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-2xl font-medium text-slate-800">志愿方案</h1>
      <p class="mt-2 text-sm text-slate-500">按本省志愿结构生成，可拖拽调序、校验数量并导出。</p>
    </div>

    <div class="aa-card">
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div class="text-sm text-slate-600">
          当前结构：<span class="font-medium text-slate-800">院校专业组</span>
          <span class="ml-3">志愿数：<span class="font-medium text-slate-800">45</span></span>
        </div>
        <span class="rounded-md bg-slate-100 px-2 py-1 text-xs text-slate-500">P0 占位数据</span>
      </div>
      <div class="overflow-hidden rounded-lg border border-slate-200">
        <table class="w-full text-left text-sm">
          <thead class="bg-slate-50 text-slate-600">
            <tr>
              <th class="px-4 py-2.5 font-medium">序号</th>
              <th class="px-4 py-2.5 font-medium">院校 / 志愿单位</th>
              <th class="px-4 py-2.5 font-medium">档位</th>
              <th class="px-4 py-2.5 font-medium">匹配度</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.no" class="border-t border-slate-100">
              <td class="px-4 py-3 text-slate-500">{{ row.no }}</td>
              <td class="px-4 py-3 text-slate-800">{{ row.school }} · {{ row.unit }}</td>
              <td class="px-4 py-3"><RiskTag :tier="row.tier" /></td>
              <td class="px-4 py-3 text-slate-500">—</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="mt-3 text-xs text-slate-400">
        拖拽排序（vuedraggable）、志愿数量校验、PDF / Excel 导出将在 P1 接入。
      </p>
    </div>
  </div>
</template>
