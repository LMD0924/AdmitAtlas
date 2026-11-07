import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { reveal, spotlight, count } from '@/directives'
import { useTheme } from '@/composables/useTheme'
import './style.css'

useTheme().initTheme()

const app = createApp(App)
app.directive('reveal', reveal)
app.directive('spotlight', spotlight)
app.directive('count', count)
app.use(createPinia())
app.use(router)
app.mount('#app')
