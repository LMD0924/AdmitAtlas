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

/** 业务错误：保留 code 与 data，方便页面区分 400 / 423 / 501 等口径并读取剩余次数、冻结截止时间。 */
export function apiError(message, code, status, data) {
  const err = new Error(message)
  err.code = code
  err.status = status
  err.data = data ?? null
  return err
}

/** 登录接口本身的 401 是「账号或密码错误」，不能当作会话失效。 */
function isAuthEndpoint(url = '') {
  return url.includes('/auth/login') || url.includes('/auth/register')
}

function revokeSession() {
  localStorage.removeItem('admitatlas_token')
  localStorage.removeItem('admitatlas_user_v1')
  window.dispatchEvent(new CustomEvent('aa:unauthorized'))
}

const FALLBACK = {
  400: '请求参数不合法，请检查输入',
  401: '登录状态已失效',
  404: '未找到对应资源',
  409: '数据状态冲突',
  423: '账号已被临时冻结',
  501: '该能力尚未实现',
  503: '该省 / 年份的数据尚未发布'
}

request.interceptors.response.use(
  (response) => {
    const body = response.data
    // 统一响应包装：成功直接解包 data，页面不必再取一层 res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        if (body.code === 401 && !isAuthEndpoint(response.config.url)) revokeSession()
        return Promise.reject(apiError(
          body.message || FALLBACK[body.code] || '请求失败', body.code, response.status, body.data))
      }
      return body.data
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const body = error.response?.data
    const message = body?.message || error.message || '网络异常'
    if ((status === 401 || body?.code === 401) && !isAuthEndpoint(error.config?.url)) {
      revokeSession()
    }
    return Promise.reject(apiError(message, body?.code ?? status, status, body?.data))
  }
)

export default request
