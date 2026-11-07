<template>
  <div class="flex min-h-[58vh] items-center justify-center">
    <div class="glass spotlight mx-auto max-w-[520px] p-10 text-center">
      <p class="stat-num text-[clamp(4rem,9vw,7rem)] text-accent">404</p>
      <h1 class="mt-4 text-[20px] font-medium">这条路径不在地图上</h1>
      <p class="mt-3 text-[13px] leading-7 muted">页面不存在或已被移动，回到概览重新出发。</p>
      <RouterLink to="/" class="btn btn-primary mt-8">
        返回首页 <span class="link-arrow">→</span>
      </RouterLink>
    </div>
  </div>
</template>
