<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()

const menus = [
  { to: '/admin', label: '后台概览', glyph: '◈' },
  { to: '/admin/users', label: '用户与角色', glyph: '☰' },
  { to: '/admin/rules', label: '志愿规则', glyph: '⚖' },
  { to: '/admin/schools', label: '院校档案', glyph: '⌕' },
  { to: '/admin/data', label: '数据版本', glyph: '◉' },
  { to: '/admin/audit', label: '操作审计', glyph: '≡' }
]

const current = computed(() => route.meta.title || '管理后台')
</script>

<template>
  <section>
    <div class="mb-6 flex flex-wrap items-end justify-between gap-3" v-reveal="0">
      <div>
        <p class="eyebrow">ADMIN CONSOLE · 管理端</p>
        <h1 class="hand-title mt-2">管理后台</h1>
        <p class="muted mt-2 text-[12px]">当前位置：{{ current }} · 仅管理员可见</p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <span class="pill pill-violet">ADMIN</span>
        <span class="muted text-[12px]">{{ auth.displayName }}</span>
        <RouterLink to="/" class="btn btn-slate"><span class="btn-glyph">←</span> 返回学生端</RouterLink>
      </div>
    </div>

    <div class="grid gap-5 lg:grid-cols-[190px_1fr]">
      <aside class="glass h-fit rounded-2xl p-3 lg:sticky lg:top-[86px]">
        <p class="field-label px-2 pb-2">功能导航</p>
        <nav class="flex flex-col gap-1.5">
          <RouterLink
            v-for="m in menus"
            :key="m.to"
            :to="m.to"
            class="flex items-center gap-2 rounded-lg px-3 py-2 text-[12px] transition-colors"
            :class="route.path === m.to
              ? 'bg-[var(--accent-soft)] text-[var(--accent-text)]'
              : 'muted hover:bg-[var(--surface-2)]'"
          >
            <span class="text-[13px]">{{ m.glyph }}</span> {{ m.label }}
          </RouterLink>
        </nav>
        <p class="faint mt-4 border-t border-hairline px-2 pt-3 text-[11px] leading-5">
          所有写操作都会记录到操作审计，包含操作人与前后值摘要。
        </p>
      </aside>

      <div class="min-w-0">
        <RouterView v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </div>
    </div>
  </section>
</template>
