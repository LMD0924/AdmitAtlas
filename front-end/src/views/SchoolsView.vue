<script setup>
import { computed, ref } from 'vue'

const keyword = ref('')
const searched = ref(false)
const activeFilter = ref('全部')

const filters = ['全部', '985 / 211', '双一流', '省重点']
const capabilities = [
  { span: 'lg:col-span-5', tag: '档案', title: '院校层次与属地', desc: '985 / 211 / 双一流、主管部门、所在地与办学性质，支持多条件组合检索。', pill: 'pill-sky' },
  { span: 'lg:col-span-4', tag: '目录', title: '学科门类与专业', desc: '门类 → 专业类 → 专业三级目录，联动选科要求与开设院校。', pill: 'pill-violet' },
  { span: 'lg:col-span-3', tag: '趋势', title: '历年录取轨迹', desc: '近 3–5 年最低位次与计划数变化。', pill: 'pill-mint' }
]

const emptyText = computed(() =>
  searched.value
    ? `暂未找到「${keyword.value}」相关数据`
    : '院校档案数据尚未入库'
)

function search() {
  searched.value = keyword.value.trim().length > 0
}

function clear() {
  keyword.value = ''
  searched.value = false
  activeFilter.value = '全部'
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p v-reveal="0" class="eyebrow">School archive / 资源中枢</p>
        <h1 v-reveal="1" class="mt-2 text-[22px] font-semibold tracking-[-0.02em]">全国高校，一张可检索的地图</h1>
        <p v-reveal="2" class="mt-2 max-w-[76ch] text-[12px] leading-6 muted">
          按层次、地域、类型检索院校档案与历年录取趋势。数据版本、来源与更新时间随结果一同公示。
        </p>
      </div>
      <div v-reveal="2" class="btn-group">
        <RouterLink to="/" class="btn btn-slate">← 返回概览</RouterLink>
        <RouterLink to="/match" class="btn btn-teal">
          <span class="btn-glyph">◎</span> 智能匹配
        </RouterLink>
      </div>
    </div>

    <div v-reveal="0" class="glass p-6 md:p-7">
      <div class="flex flex-wrap gap-3">
        <input
          v-model="keyword"
          class="field max-w-[420px] flex-1"
          placeholder="输入院校名称或代码"
          aria-label="院校检索"
          @keyup.enter="search"
        />
        <button class="btn btn-blue" type="button" @click="search">
          <span class="btn-glyph">⌕</span> 检索 <span class="link-arrow">→</span>
        </button>
        <button class="btn btn-rose" type="button" @click="clear">
          <span class="btn-glyph">✕</span> 清空
        </button>
      </div>
      <div class="mt-4 flex flex-wrap gap-2">
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

      <div class="empty-state mt-6">
        <svg class="mx-auto h-8 w-8 text-accent" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round">
          <circle cx="11" cy="11" r="6.5" />
          <path d="m16 16 4.5 4.5M8.5 11h5M11 8.5v5" />
        </svg>
        <p class="mt-4 text-[13px] font-semibold">{{ emptyText }}</p>
        <p class="mt-2 text-[11px] faint">P1 接入 Elasticsearch 后开放检索、档案详情与多校对比</p>
      </div>
    </div>

    <div class="grid gap-4 lg:grid-cols-12">
      <div
        v-for="(c, i) in capabilities"
        :key="c.title"
        v-reveal="i + 1"
        :class="['glass lift p-5', c.span]"
      >
        <span class="pill" :class="c.pill">{{ c.tag }}</span>
        <h3 class="mt-4 text-[14px] font-semibold">{{ c.title }}</h3>
        <p class="mt-2 text-[12px] leading-6 muted">{{ c.desc }}</p>
      </div>
    </div>

    <p v-reveal="0" class="text-center text-[11px] faint">
      所有录取数据均来自公开公示渠道并保留来源链接，最终以各省教育考试院公布为准
    </p>
  </div>
</template>
