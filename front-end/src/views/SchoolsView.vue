<script setup>
import { computed, ref } from 'vue'

const keyword = ref('')
const searched = ref(false)
const activeFilter = ref('全部')

const filters = ['全部', '985 / 211', '双一流', '省重点']
const capabilities = [
  { span: 'lg:col-span-5', tag: '档案', title: '院校层次与属地', desc: '985 / 211 / 双一流、主管部门、所在地与办学性质，支持多条件组合检索。' },
  { span: 'lg:col-span-4', tag: '目录', title: '学科门类与专业', desc: '门类 → 专业类 → 专业三级目录，联动选科要求与开设院校。' },
  { span: 'lg:col-span-3', tag: '趋势', title: '历年录取轨迹', desc: '近 3–5 年最低位次与计划数变化。' }
]

const emptyText = computed(() =>
  searched.value
    ? `暂未找到「${keyword.value}」相关数据`
    : '院校档案数据尚未入库'
)

function search() {
  searched.value = keyword.value.trim().length > 0
}
</script>

<template>
  <div class="space-y-8">
    <div>
      <p v-reveal="0" class="eyebrow">School archive</p>
      <h1 v-reveal="1" class="mt-5 text-[clamp(2rem,3.4vw,3rem)] font-medium leading-tight tracking-[-0.04em]">
        全国高校，<br /><span class="text-accent">一张可检索的地图</span>
      </h1>
      <p v-reveal="2" class="mt-4 max-w-[60ch] text-[14px] leading-7 muted">
        按层次、地域、类型检索院校档案与历年录取趋势。数据版本、来源与更新时间随结果一同公示。
      </p>
    </div>

    <div v-reveal="0" v-spotlight class="glass spotlight p-6 md:p-8">
      <div class="flex flex-wrap gap-3">
        <input
          v-model="keyword"
          class="field max-w-[420px] flex-1"
          placeholder="输入院校名称或代码"
          aria-label="院校检索"
          @keyup.enter="search"
        />
        <button class="btn btn-primary" @click="search">检索 <span class="link-arrow">→</span></button>
      </div>
      <div class="mt-5 flex flex-wrap gap-2">
        <button
          v-for="f in filters"
          :key="f"
          class="chip"
          :class="{ 'is-active': activeFilter === f }"
          type="button"
          @click="activeFilter = f"
        >
          {{ f }}
        </button>
      </div>

      <div class="empty-state mt-7">
        <svg class="mx-auto h-9 w-9 text-accent" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round">
          <circle cx="11" cy="11" r="6.5" />
          <path d="m16 16 4.5 4.5M8.5 11h5M11 8.5v5" />
        </svg>
        <p class="mt-5 text-[14px] font-medium">{{ emptyText }}</p>
        <p class="mt-2 text-[12px] faint">P1 接入 Elasticsearch 后开放检索、档案详情与多校对比</p>
      </div>
    </div>

    <div class="grid gap-4 lg:grid-cols-12">
      <div
        v-for="(c, i) in capabilities"
        :key="c.title"
        v-reveal="i + 1"
        v-spotlight
        :class="['glass spotlight lift p-6', c.span]"
      >
        <span class="badge">{{ c.tag }}</span>
        <h3 class="mt-5 text-[16px] font-medium">{{ c.title }}</h3>
        <p class="mt-2 text-[13px] leading-7 muted">{{ c.desc }}</p>
      </div>
    </div>

    <p v-reveal="0" class="text-center text-[12px] faint">
      所有录取数据均来自公开公示渠道并保留来源链接，最终以各省教育考试院公布为准
    </p>
  </div>
</template>
