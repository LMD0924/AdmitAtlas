import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { reveal, spotlight, count } from '@/directives'
import { useTheme } from '@/composables/useTheme'
import { useAuthStore } from '@/stores/auth'
import './style.css'

useTheme().initTheme()

const app = createApp(App)
app.directive('reveal', reveal)
app.directive('spotlight', spotlight)
app.directive('count', count)

const pinia = createPinia()
app.use(pinia)

const auth = useAuthStore(pinia)
// 任意接口返回 401（登录接口除外）时，请求层已清掉本地存储，这里同步 Pinia 状态。
window.addEventListener('aa:unauthorized', () => auth.clear())

// 刷新页面时先校验 token 再挂载，避免路由守卫把已登录用户误判为游客。
auth.bootstrap().finally(() => {
  app.use(router)
  app.mount('#app')
})
