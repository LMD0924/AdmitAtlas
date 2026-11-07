<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useTheme } from '@/composables/useTheme'

const { theme, toggleTheme } = useTheme()
const route = useRoute()
const scrolled = ref(false)

const links = [
  { to: '/', label: '概览' },
  { to: '/score', label: '成绩录入' },
  { to: '/match', label: '智能匹配' },
  { to: '/plan', label: '志愿方案' },
  { to: '/schools', label: '院校库' }
]

function onScroll() {
  scrolled.value = window.scrollY > 28
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <header class="site-header" :class="{ 'is-scrolled': scrolled }">
    <div class="nav-shell">
      <RouterLink to="/" class="flex flex-none items-center gap-2.5" aria-label="AdmitAtlas 首页">
        <span class="brand-mark">A</span>
        <span class="brand-name">AdmitAtlas</span>
      </RouterLink>

      <nav class="nav-links" aria-label="主导航">
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          class="nav-link"
          :class="{ 'is-active': route.path === link.to }"
        >
          {{ link.label }}
        </RouterLink>
      </nav>

      <div class="nav-actions">
        <button
          class="btn-icon"
          type="button"
          :aria-label="theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'"
          :title="theme === 'dark' ? '浅色主题' : '深色主题'"
          @click="toggleTheme"
        >
          <svg v-if="theme === 'dark'" viewBox="0 0 24 24" fill="none" class="icon-swap" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" class="icon-swap" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20.5 14.2A8.5 8.5 0 0 1 9.8 3.5a8.5 8.5 0 1 0 10.7 10.7Z" />
          </svg>
        </button>
      </div>
    </div>
  </header>
</template>
