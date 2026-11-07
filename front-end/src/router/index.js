import { createRouter, createWebHistory } from 'vue-router'
import { startRouteLoading, stopRouteLoading } from '@/composables/useRouteLoading'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/score',
    name: 'score-wizard',
    component: () => import('@/views/ScoreWizardView.vue'),
    meta: { title: '成绩录入' }
  },
  {
    path: '/match',
    name: 'match',
    component: () => import('@/views/MatchView.vue'),
    meta: { title: '智能匹配' }
  },
  {
    path: '/plan',
    name: 'plan',
    component: () => import('@/views/PlanView.vue'),
    meta: { title: '志愿方案' }
  },
  {
    path: '/schools',
    name: 'schools',
    component: () => import('@/views/SchoolsView.vue'),
    meta: { title: '院校库' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

let firstNavigation = true

router.beforeEach((to, from) => {
  if (!firstNavigation && to.fullPath !== from.fullPath) startRouteLoading()
  firstNavigation = false
})

router.afterEach((to) => {
  document.title = `${to.meta.title ?? 'AdmitAtlas'} · AdmitAtlas`
  stopRouteLoading()
})

router.onError(() => stopRouteLoading())

export default router
