<template>
  <div class="flex min-h-[58vh] items-center justify-center">
    <div class="glass mx-auto max-w-[480px] p-9 text-center">
      <p class="hand-title">404</p>
      <h1 class="mt-4 text-[17px] font-semibold">这条路径不在地图上</h1>
      <p class="mt-2 text-[12px] leading-6 muted">页面不存在或已被移动，回到概览重新出发。</p>
      <RouterLink to="/" class="btn btn-primary mt-7">
        返回首页 <span class="link-arrow">→</span>
      </RouterLink>
    </div>
  </div>
</template>
