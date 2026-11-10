<script setup>
import { routeLoading } from '@/composables/useRouteLoading'
</script>

<template>
  <Transition name="loader">
    <div v-if="routeLoading" class="route-loader" role="status" aria-live="polite">
      <div class="hud-grid" aria-hidden="true"></div>
      <div class="hud-scan" aria-hidden="true"></div>

      <div class="hud-panel">
        <span class="hud-corner tl"></span><span class="hud-corner tr"></span>
        <span class="hud-corner bl"></span><span class="hud-corner br"></span>

        <div class="hud-head">
          <span class="hud-dot"></span>
          <span>SYS / ROUTE.SYNC</span>
          <span class="hud-spacer"></span>
          <span>AA·2026</span>
        </div>

        <div class="hud-main">
          <div class="hud-mark"><span class="hud-ring"></span>A</div>
          <div>
            <p class="hud-title">正在校准路径</p>
            <p class="hud-sub">LOADING VIEW · 数据链路校验中</p>
          </div>
        </div>

        <div class="hud-bar"><i></i></div>
        <div class="hud-foot">
          <span>ADMITATLAS / NAVIGATION</span>
          <span>◈ ONLINE</span>
        </div>
      </div>
    </div>
  </Transition>
</template>
