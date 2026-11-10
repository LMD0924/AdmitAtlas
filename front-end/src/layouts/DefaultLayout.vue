<script setup>
import { onMounted, ref } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import RouteLoader from '@/components/RouteLoader.vue'
import CompareBar from '@/components/CompareBar.vue'
import { getDataVersion } from '@/api'
import { useEngagementStore } from '@/stores/engagement'

const engagement = useEngagementStore()
const version = ref(null)
// 每次页面进入递增 key 重建扫描光带，保证 CSS 动画能重复播放。
const scanKey = ref(0)
function startScan() { scanKey.value += 1 }
onMounted(async () => {
  try { version.value = await getDataVersion() } catch { /* 后端未启动时页脚降级显示 */ }
})
</script>

<template>
  <div class="bg-layer" aria-hidden="true">
    <div class="bg-grid"></div>
    <div class="halo halo-a"></div>
    <div class="halo halo-b"></div>
    <div class="halo halo-c"></div>
  </div>

  <div class="relative z-10 flex min-h-[100dvh] flex-col">
    <AppHeader />

    <main class="mx-auto w-full max-w-[1200px] flex-1 px-5 pb-16 pt-[110px] md:px-8 md:pt-[124px]"
      :class="{ 'has-compare-bar': engagement.compareCount }">
      <RouterView v-slot="{ Component, route }">
        <Transition name="page" mode="out-in" @before-enter="startScan">
          <component :is="Component" :key="route.path" />
        </Transition>
      </RouterView>
    </main>

    <footer class="mx-auto mb-5 w-[calc(100%-2.5rem)] max-w-[1200px] border-t border-hairline px-2 py-5 md:w-[calc(100%-4rem)]">
      <div class="flex flex-wrap items-center justify-between gap-3 text-[12px] leading-6 muted">
        <span class="flex flex-wrap items-center gap-2">
          <span class="pulse-dot inline-block h-1.5 w-1.5 rounded-full bg-accent"></span>
          AdmitAtlas · P1 单省试点
          <span v-if="version?.demoData" class="pill pill-amber">演示数据（合成值）</span>
          <span v-if="version" class="faint">版本 {{ version.dataVersion }} · {{ version.admissionRowCount }} 条投档记录</span>
        </span>
        <span class="faint">仅输出匹配度与风险等级，不构成录取承诺 · 以各省教育考试院公布为准 · MIT © 2026 LMD0924</span>
      </div>
    </footer>
  </div>

  <div v-if="scanKey" :key="scanKey" class="route-scanline" aria-hidden="true"></div>
  <CompareBar />
  <RouteLoader />
</template>
