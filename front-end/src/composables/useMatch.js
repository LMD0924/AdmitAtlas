import { ref } from 'vue'
import { analyzeMatch, convertRank } from '@/api'
import { useAppStore } from '@/stores/app'

/**
 * P1：先向服务端换算位次（前端不传 rank），再发起匹配。
 * 结果与错误码都写回 store，方案页可以直接复用匹配结果。
 */
export function useMatch() {
  const store = useAppStore()
  const loading = ref(false)
  const error = ref('')
  const errorCode = ref(null)
  const result = ref(store.matchResult)

  async function run(options = {}) {
    if (!store.hasScore) {
      error.value = '请先完成成绩录入'
      errorCode.value = 400
      return null
    }
    loading.value = true
    error.value = ''
    errorCode.value = null
    try {
      const rank = await convertRank({
        provinceCode: store.provinceCode,
        examYear: Number(store.examYear),
        category: store.category,
        totalScore: Number(store.totalScore)
      })
      store.setRank(rank)

      const data = await analyzeMatch({
        score: store.scorePayload,
        preference: store.preference,
        options
      })
      result.value = data
      store.setMatchResult(data)
      return data
    } catch (e) {
      error.value = e.message || '匹配请求失败'
      errorCode.value = e.code ?? null
      return null
    } finally {
      loading.value = false
    }
  }

  return { loading, error, errorCode, result, run }
}
