<script setup>
import { routeLoading } from '@/composables/useRouteLoading'
</script>

<template>
  <Transition name="loader">
    <div v-if="routeLoading" class="route-loader" role="status" aria-live="polite">
      <div class="loader-inner">
        <div class="loader-mark">
          <span class="loader-ring"></span>
          A
        </div>
        <div class="loader-bar"><i></i></div>
        <p class="loader-text">正在准备页面…</p>
      </div>
    </div>
  </Transition>
</template>
