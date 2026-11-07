import { ref } from 'vue'

/** Global route transition state, shared between router guards and RouteLoader. */
const loading = ref(false)
const MIN_VISIBLE_MS = 560
let startedAt = 0
let timer = null

export const routeLoading = loading

export function startRouteLoading() {
  if (timer) clearTimeout(timer)
  startedAt = performance.now()
  loading.value = true
}

export function stopRouteLoading() {
  const elapsed = performance.now() - startedAt
  const wait = Math.max(0, MIN_VISIBLE_MS - elapsed)
  timer = setTimeout(() => {
    loading.value = false
    timer = null
  }, wait)
}
