import { defineStore } from 'pinia'
import { getFavorites, addFavorite, removeFavorite } from '@/api'
import { useAuthStore } from './auth'

const COMPARE_KEY = 'admitatlas_compare_v1'
export const MAX_COMPARE = 4

/** 对比篮在会话内保留；刷新页面不丢，关闭标签页即清空。 */
function loadCompare() {
  try {
    const raw = JSON.parse(sessionStorage.getItem(COMPARE_KEY) || '[]')
    if (!Array.isArray(raw)) return []
    return raw.filter((x) => x && typeof x.code === 'string').slice(0, MAX_COMPARE)
  } catch {
    return []
  }
}

function persist(items) {
  try { sessionStorage.setItem(COMPARE_KEY, JSON.stringify(items)) } catch { /* 隐私模式下忽略 */ }
}

/** 院校收藏与对比篮：跨页面共享状态，避免每页各拉一次接口。 */
export const useEngagementStore = defineStore('engagement', {
  state: () => ({
    compare: loadCompare(),
    favoriteCodes: [],
    favoritesLoaded: false,
    favoriteBusy: false
  }),

  getters: {
    compareCount: (s) => s.compare.length,
    compareCodes: (s) => s.compare.map((x) => x.code),
    isCompared: (s) => (code) => s.compare.some((x) => x.code === code),
    isFavorite: (s) => (code) => s.favoriteCodes.includes(code)
  },

  actions: {
    /** 返回 { ok, reason }：reason = max（超过 4 所）/ removed / added。 */
    toggleCompare(code, name = code) {
      const index = this.compare.findIndex((x) => x.code === code)
      if (index >= 0) {
        this.compare.splice(index, 1)
        persist(this.compare)
        return { ok: true, reason: 'removed' }
      }
      if (this.compare.length >= MAX_COMPARE) {
        return { ok: false, reason: 'max' }
      }
      this.compare.push({ code, name: String(name || code) })
      persist(this.compare)
      return { ok: true, reason: 'added' }
    },

    clearCompare() {
      this.compare = []
      persist(this.compare)
    },

    /** 游客不请求收藏接口；退出登录后清掉缓存，防止下一账号看到别人的收藏态。 */
    async loadFavorites(force = false) {
      const auth = useAuthStore()
      if (!auth.isLoggedIn) {
        this.favoriteCodes = []
        this.favoritesLoaded = false
        return this.favoriteCodes
      }
      if (this.favoritesLoaded && !force) return this.favoriteCodes
      try {
        const data = await getFavorites()
        const codes = data?.codes ?? (data?.items ?? []).map((x) => x.schoolCode)
        this.favoriteCodes = Array.isArray(codes) ? codes : []
        this.favoritesLoaded = true
      } catch {
        this.favoriteCodes = []
      }
      return this.favoriteCodes
    },

    /** 返回 { ok, reason }：reason = login（需登录）/ added / removed / failed。 */
    async toggleFavorite(code) {
      const auth = useAuthStore()
      if (!auth.isLoggedIn) return { ok: false, reason: 'login' }
      if (this.favoriteBusy) return { ok: false, reason: 'busy' }
      this.favoriteBusy = true
      const wasFavorite = this.favoriteCodes.includes(code)
      try {
        if (wasFavorite) await removeFavorite(code)
        else await addFavorite(code)
        this.favoriteCodes = wasFavorite
          ? this.favoriteCodes.filter((c) => c !== code)
          : [...this.favoriteCodes, code]
        this.favoritesLoaded = true
        return { ok: true, reason: wasFavorite ? 'removed' : 'added' }
      } catch (e) {
        return { ok: false, reason: 'failed', message: e.message }
      } finally {
        this.favoriteBusy = false
      }
    }
  }
})
