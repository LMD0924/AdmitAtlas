<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTheme } from '@/composables/useTheme'
import { useAuthStore } from '@/stores/auth'

const { theme, toggleTheme } = useTheme()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const scrolled = ref(false)
const initial = computed(() => (auth.displayName || 'A').slice(0, 1).toUpperCase())

async function doLogout() {
  await auth.logout()
  router.push('/')
}

// outline icon path data (24x24)
const icons = {
  home: 'M3 10.5 12 3l9 7.5M5.5 9.5V20h13V9.5M9.5 20v-6h5v6',
  edit: 'M4 20h4l10.5-10.5a2 2 0 0 0-4-4L4 16v4ZM14 6l4 4',
  target: 'M12 3a9 9 0 1 0 9 9M12 7a5 5 0 1 0 5 5M12 11a1 1 0 1 0 1 1',
  list: 'M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01',
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14ZM16.5 16.5 21 21',
  grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  book: 'M4 5.5A2.5 2.5 0 0 1 6.5 3H20v17H6.5A2.5 2.5 0 0 1 4 17.5v-12ZM4 17.5A2.5 2.5 0 0 1 6.5 15H20',
  chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  spark: 'M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3Z'
}

// 标签统一缩写，避免新增的院校库 / 专业库 / 洞察 / 顾问把导航挤窄；
// .nav-links 本身支持横向滚动，小屏仍可访问全部入口。
const baseLinks = [
  { to: '/', label: '概览', icon: icons.home },
  { to: '/score', label: '成绩', icon: icons.edit },
  { to: '/match', label: '匹配', icon: icons.target },
  { to: '/plan', label: '方案', icon: icons.list },
  { to: '/schools', label: '院校库', icon: icons.search },
  { to: '/majors', label: '专业库', icon: icons.book },
  { to: '/insights', label: '洞察', icon: icons.chart },
  { to: '/advisor', label: '顾问', icon: icons.spark }
]

// 管理后台入口放进主导航，避免 nav-actions 里叠太多按钮把导航挤窄。
const links = computed(() => auth.isAdmin
  ? [...baseLinks, { to: '/admin', label: '管理后台', icon: icons.grid }]
  : baseLinks)

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
        <span>
          <span class="brand-name">AdmitAtlas</span>
          <span class="brand-sub">Forge your path, rank by rank</span>
        </span>
      </RouterLink>

      <nav class="nav-links" aria-label="主导航">
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          class="nav-link"
          :class="{
            'is-active': link.to === '/' ? route.path === '/' : route.path.startsWith(link.to),
            'is-admin-link': link.to === '/admin'
          }"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path :d="link.icon" />
          </svg>
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
          <svg v-if="theme === 'dark'" viewBox="0 0 24 24" fill="none" class="icon-swap" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" class="icon-swap" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20.5 14.2A8.5 8.5 0 0 1 9.8 3.5a8.5 8.5 0 1 0 10.7 10.7Z" />
          </svg>
        </button>
        <span class="nav-divider"></span>

        <template v-if="auth.isLoggedIn">
          <span class="pill" :class="auth.isAdmin ? 'pill-violet' : 'pill-mint'">
            {{ auth.isAdmin ? '管理员' : '学生' }}
          </span>
          <span class="avatar">{{ initial }}</span>
          <RouterLink to="/profile" class="nav-user nav-user-link" title="个人中心">
            {{ auth.displayName }}
          </RouterLink>
          <button type="button" class="btn btn-slate" @click="doLogout">
            <span class="btn-glyph">↪</span> 退出
          </button>
        </template>
        <template v-else>
          <span class="pill pill-neutral">游客</span>
          <RouterLink to="/login" class="btn btn-blue">
            <span class="btn-glyph">→</span> 登录
          </RouterLink>
        </template>
      </div>
    </div>
  </header>
</template>
