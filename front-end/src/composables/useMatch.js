import { ref } from 'vue'
import { analyzeMatch } from '@/api'
import { useAppStore } from '@/stores/app'

/**
 * 匹配流程组合式函数。P0 阶段后端返回 501，此处保留完整错误透传，
 * P1 只需替换数据源即可，页面无需改动。
 */
export function useMatch() {
  const store = useAppStore()
  const loading = ref(false)
  const error = ref('')
  const result = ref(null)

  async function run(options = {}) {
    if (!store.hasScore) {
      error.value = '请先完成成绩录入'
      return null
    }
    loading.value = true
    error.value = ''
    result.value = null
    try {
      const body = await analyzeMatch({
        score: store.scorePayload,
        preference: store.preference,
        rank: store.rank,
        options
      })
      result.value = body?.data ?? body
      return result.value
    } catch (e) {
      error.value = e.message || '匹配请求失败'
      return null
    } finally {
      loading.value = false
    }
  }

  return { loading, error, result, run }
}
