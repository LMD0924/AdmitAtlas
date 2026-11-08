import { defineStore } from 'pinia'
import { getAuthMe, login, logoutApi, register } from '@/api'

const TOKEN_KEY = 'admitatlas_token'
const USER_KEY = 'admitatlas_user_v1'

function loadUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

/** 双身份会话：游客 / 学生 STUDENT / 管理员 ADMIN。 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: loadUser(),
    ready: false,
    pending: false
  }),

  getters: {
    isLoggedIn: (s) => !!s.token && !!s.user && !s.user.guest,
    isAdmin: (s) => !!s.user && s.user.role === 'ADMIN',
    isStudent: (s) => !!s.user && s.user.role === 'STUDENT',
    role: (s) => s.user?.role || 'GUEST',
    displayName: (s) => s.user?.displayName || s.user?.username || '游客'
  },

  actions: {
    /** 应用启动时校验本地 token；无效则静默降级为游客。 */
    async bootstrap() {
      if (this.ready) return
      this.pending = true
      if (!this.token) {
        this.ready = true
        this.pending = false
        return
      }
      try {
        const data = await getAuthMe()
        if (data?.guest || !data?.user) this.clear()
        else this.user = data.user
      } catch {
        this.clear()
      } finally {
        this.ready = true
        this.pending = false
      }
    },

    async login(username, password) {
      const data = await login({ username, password })
      this.setSession(data)
      return data.user
    },

    async register(payload) {
      await register(payload)
      return this.login(payload.username, payload.password)
    },

    async logout() {
      if (this.token) {
        try { await logoutApi() } catch { /* 服务端不可达时也要清本地会话 */ }
      }
      this.clear()
    },

    setSession(data) {
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
    },

    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
