<script setup>
import AppHeader from '@/components/AppHeader.vue'
import RouteLoader from '@/components/RouteLoader.vue'
</script>

<template>
  <div class="bg-layer" aria-hidden="true">
    <div class="bg-grid"></div>
    <div class="orb orb-a"></div>
    <div class="orb orb-b"></div>
    <div class="orb orb-c"></div>
  </div>

  <div class="relative z-10 flex min-h-[100dvh] flex-col">
    <AppHeader />

    <main class="mx-auto w-full max-w-[1320px] flex-1 px-5 pb-20 pt-[128px] md:px-8 md:pt-[148px]">
      <RouterView v-slot="{ Component, route }">
        <Transition name="page" mode="out-in">
          <component :is="Component" :key="route.path" />
        </Transition>
      </RouterView>
    </main>

    <footer class="glass-flat mx-auto mb-5 w-[calc(100%-2.5rem)] max-w-[1320px] rounded-[22px] px-6 py-5 md:w-[calc(100%-4rem)]">
      <div class="flex flex-wrap items-center justify-between gap-3 text-xs leading-6 muted">
        <span class="flex items-center gap-2">
          <span class="pulse-dot inline-block h-1.5 w-1.5 rounded-full bg-accent"></span>
          AdmitAtlas · P0 骨架阶段，数据与匹配算法尚未上线
        </span>
        <span class="faint">所有建议仅供参考，以各省教育考试院公布数据为准 · MIT © 2026 LMD0924</span>
      </div>
    </footer>
  </div>

  <RouteLoader />
</template>
