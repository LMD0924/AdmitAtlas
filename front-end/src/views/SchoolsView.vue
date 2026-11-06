<script setup>
import { ref } from 'vue'

const keyword = ref('')
const searched = ref(false)

function search() {
  searched.value = keyword.value.trim().length > 0
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-2xl font-medium text-slate-800">院校库</h1>
      <p class="mt-2 text-sm text-slate-500">按层次、地域、类型检索全国高校档案与历年录取趋势。</p>
    </div>

    <div class="aa-card">
      <div class="flex flex-wrap gap-3">
        <input
          v-model="keyword"
          class="aa-input max-w-md"
          placeholder="输入院校名称或代码"
          @keyup.enter="search"
        />
        <button class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-800" @click="search">
          检索
        </button>
      </div>
      <div class="mt-6 rounded-lg border border-dashed border-slate-200 py-12 text-center">
        <p class="text-sm text-slate-500">
          {{ searched ? `暂未找到「${keyword}」相关数据` : '院校档案数据尚未入库' }}
        </p>
        <p class="mt-2 text-xs text-slate-400">P1 接入 Elasticsearch 后开放检索、详情与多校对比</p>
      </div>
    </div>

    <div class="grid gap-4 md:grid-cols-3">
      <div v-for="f in ['985 / 211 / 双一流', '主管部门与所在地', '学科门类与专业目录']" :key="f" class="aa-card text-sm text-slate-600">
        {{ f }}
      </div>
    </div>
  </div>
</template>
