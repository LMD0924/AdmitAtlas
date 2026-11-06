<script setup>
const links = [
  { to: '/', label: '首页' },
  { to: '/score', label: '成绩录入' },
  { to: '/match', label: '智能匹配' },
  { to: '/plan', label: '志愿方案' },
  { to: '/schools', label: '院校库' }
]
</script>

<template>
  <header class="sticky top-0 z-10 border-b border-slate-200 bg-white/95 backdrop-blur">
    <div class="mx-auto flex h-16 max-w-6xl items-center justify-between px-4">
      <RouterLink to="/" class="flex items-center gap-2">
        <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 text-sm font-medium text-white">A</span>
        <span class="text-base font-medium text-brand-800">AdmitAtlas</span>
      </RouterLink>
      <nav class="flex items-center gap-1">
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          class="rounded-lg px-3 py-2 text-sm text-slate-600 hover:bg-brand-50 hover:text-brand-800"
          active-class="bg-brand-50 text-brand-800"
        >
          {{ link.label }}
        </RouterLink>
      </nav>
    </div>
  </header>
</template>
