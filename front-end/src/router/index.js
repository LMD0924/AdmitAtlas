import { createRouter, createWebHistory } from 'vue-router'
import { startRouteLoading, stopRouteLoading } from '@/composables/useRouteLoading'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录 / 注册' }
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { title: '个人中心', requiresAuth: true }
  },
  {
    path: '/score',
    name: 'score-wizard',
    component: () => import('@/views/ScoreWizardView.vue'),
    meta: { title: '成绩录入', requiresAuth: true }
  },
  {
    path: '/match',
    name: 'match',
    component: () => import('@/views/MatchView.vue'),
    meta: { title: '智能匹配', requiresAuth: true }
  },
  {
    path: '/plan',
    name: 'plan',
    component: () => import('@/views/PlanView.vue'),
    meta: { title: '志愿方案', requiresAuth: true }
  },
  {
    path: '/schools',
    name: 'schools',
    component: () => import('@/views/SchoolsView.vue'),
    meta: { title: '院校库' }
  },
  {
    // 独立详情页：游客可直接访问 / 分享；query 原样带回院校库以还原筛选条件。
    path: '/schools/:code',
    name: 'school-detail',
    component: () => import('@/views/SchoolDetailView.vue'),
    meta: { title: '院校详情' }
  },
  {
    // 静态段优先于 /schools/:code，vue-router 4 的路径评分保证不会误判为院校代码。
    path: '/schools/compare',
    name: 'school-compare',
    component: () => import('@/views/SchoolCompareView.vue'),
    meta: { title: '院校对比' }
  },
  {
    path: '/majors',
    name: 'majors',
    component: () => import('@/views/MajorsView.vue'),
    meta: { title: '专业库' }
  },
  {
    path: '/insights',
    name: 'insights',
    component: () => import('@/views/InsightsView.vue'),
    meta: { title: '数据洞察' }
  },
  {
    path: '/advisor',
    name: 'advisor',
    component: () => import('@/views/AdvisorView.vue'),
    meta: { title: '志愿顾问' }
  },
  {
    path: '/plan/compare',
    name: 'plan-compare',
    component: () => import('@/views/PlanCompareView.vue'),
    meta: { title: '方案对比', requiresAuth: true }
  },
  {
    // 只读分享：游客可直接打开，令牌有效期与撤销由服务端控制。
    path: '/share/:token',
    name: 'shared-plan',
    component: () => import('@/views/ShareView.vue'),
    meta: { title: '志愿方案分享' }
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { title: '管理后台', requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'admin-overview',
        component: () => import('@/views/admin/AdminOverviewView.vue'),
        meta: { title: '后台概览', requiresAdmin: true }
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/views/admin/AdminUsersView.vue'),
        meta: { title: '用户与角色', requiresAdmin: true }
      },
      {
        path: 'rules',
        name: 'admin-rules',
        component: () => import('@/views/admin/AdminRulesView.vue'),
        meta: { title: '志愿规则', requiresAdmin: true }
      },
      {
        path: 'schools',
        name: 'admin-schools',
        component: () => import('@/views/admin/AdminSchoolsView.vue'),
        meta: { title: '院校档案', requiresAdmin: true }
      },
      {
        path: 'data',
        name: 'admin-data',
        component: () => import('@/views/admin/AdminDataView.vue'),
        meta: { title: '数据版本', requiresAdmin: true }
      },
      {
        path: 'audit',
        name: 'admin-audit',
        component: () => import('@/views/admin/AdminAuditView.vue'),
        meta: { title: '操作审计', requiresAdmin: true }
      }
    ]
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
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    // 仅筛选条件写入 query 时不滚动；切换页面才回到顶部。
    if (to.path === from.path) return false
    return { top: 0 }
  }
})

let firstNavigation = true

/**
 * 游客可浏览首页与院校库；成绩 / 匹配 / 方案需登录；/admin/** 仅 ADMIN。
 * 权限不足统一带 redirect 跳登录页，登录后原路返回。
 */
router.beforeEach(async (to, from) => {
  // query 变化（院校库筛选条件同步到 URL）属于同一页面内的状态更新，不闪加载遮罩。
  if (!firstNavigation && to.path !== from.path) startRouteLoading()
  firstNavigation = false

  const auth = useAuthStore()
  if (!auth.ready) await auth.bootstrap()

  if (to.meta.requiresAdmin && !auth.isAdmin) {
    stopRouteLoading()
    return { path: '/login', query: { redirect: to.fullPath, reason: 'admin' } }
  }
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    stopRouteLoading()
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title ?? 'AdmitAtlas'} · AdmitAtlas`
  stopRouteLoading()
})

router.onError(() => stopRouteLoading())

export default router
