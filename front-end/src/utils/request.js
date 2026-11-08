import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' }
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('admitatlas_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

/** 业务错误：保留 code，方便页面区分 400 / 501 / 503 等口径。 */
export function apiError(message, code, status) {
  const err = new Error(message)
  err.code = code
  err.status = status
  return err
}

const FALLBACK = {
  400: '请求参数不合法，请检查输入',
  401: '登录状态已失效',
  404: '未找到对应资源',
  409: '数据状态冲突',
  501: '该能力尚未实现',
  503: '该省 / 年份的数据尚未发布'
}

request.interceptors.response.use(
  (response) => {
    const body = response.data
    // 统一响应包装：成功直接解包 data，页面不必再取一层 res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        return Promise.reject(apiError(
          body.message || FALLBACK[body.code] || '请求失败', body.code, response.status))
      }
      return body.data
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const body = error.response?.data
    const message = body?.message || error.message || '网络异常'
    return Promise.reject(apiError(message, body?.code ?? status, status))
  }
)

export default request
