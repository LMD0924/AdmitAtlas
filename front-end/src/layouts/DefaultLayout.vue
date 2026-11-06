<script setup>
import AppHeader from '@/components/AppHeader.vue'
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <AppHeader />
    <main class="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
      <RouterView />
    </main>
    <footer class="border-t border-slate-200 bg-white py-5">
      <div class="mx-auto max-w-6xl px-4 text-xs leading-6 text-slate-500">
        AdmitAtlas · P0 骨架阶段，匹配算法与数据尚未上线。所有建议仅供参考，最终以各省教育考试院公布数据为准。
        <span class="ml-2 text-slate-400">MIT License © 2026 LMD0924</span>
      </div>
    </footer>
  </div>
</template>
