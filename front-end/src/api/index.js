import request from '@/utils/request'

const V1 = '/v1'

// ---------------------------------------------------------------- 系统 / 元数据
export const getStrategies = () => request.get(`${V1}/match/strategies`)
export const getOverview = () => request.get(`${V1}/system/overview`)
export const getDataVersion = () => request.get(`${V1}/system/data-version`)
export const getDictionaries = () => request.get(`${V1}/dictionaries`)
export const getMe = () => request.get(`${V1}/users/me`)

// ---------------------------------------------------------------- 认证 / 双身份
export const login = (payload) => request.post(`${V1}/auth/login`, payload)
export const register = (payload) => request.post(`${V1}/auth/register`, payload)
export const logoutApi = () => request.post(`${V1}/auth/logout`)
export const getAuthMe = () => request.get(`${V1}/auth/me`)

// ---------------------------------------------------------------- 个人中心（需登录）
export const getProfile = () => request.get(`${V1}/users/profile`)
export const updateProfile = (payload) => request.put(`${V1}/users/profile`, payload)
export const changePassword = (payload) => request.post(`${V1}/users/password`, payload)

// ---------------------------------------------------------------- 管理端（需 ADMIN）
export const getAdminOverview = () => request.get(`${V1}/admin/overview`)
export const getAdminUsers = (params) => request.get(`${V1}/admin/users`, { params })
export const createAdminUser = (payload) => request.post(`${V1}/admin/users`, payload)
export const setUserStatus = (id, status) =>
  request.post(`${V1}/admin/users/${id}/status`, { status })
export const resetUserPassword = (id, password) =>
  request.post(`${V1}/admin/users/${id}/reset-password`, { password })
export const getAdminAudit = (params) => request.get(`${V1}/admin/audit-logs`, { params })
export const getAdminVersions = () => request.get(`${V1}/admin/data-versions`)
export const activateVersion = (version) =>
  request.post(`${V1}/admin/data-versions/${version}/activate`)
export const getAdminPolicies = () => request.get(`${V1}/admin/policies`)
export const updateAdminPolicy = (province, year, payload) =>
  request.put(`${V1}/admin/policies/${province}/${year}`, payload)
export const getAdminSchools = (params) => request.get(`${V1}/admin/schools`, { params })
export const updateAdminSchool = (code, payload) =>
  request.put(`${V1}/admin/schools/${code}`, payload)
export const getAdminPlans = (params) => request.get(`${V1}/admin/plans`, { params })
export const getAdminTasks = (params) => request.get(`${V1}/admin/match-tasks`, { params })

// ---------------------------------------------------------------- 成绩 / 分数
export const saveStudentScore = (payload) => request.post(`${V1}/student/scores`, payload)
export const getStudentScore = (params) => request.get(`${V1}/student/scores`, { params })
export const convertRank = (payload) => request.post(`${V1}/scores/convert-rank`, payload)
export const getSegments = (params) => request.get(`${V1}/segments`, { params })
export const getBatchLines = (params) => request.get(`${V1}/batch-lines`, { params })
export const getAdmissionScores = (params) => request.get(`${V1}/admission/scores`, { params })

// ---------------------------------------------------------------- 院校 / 专业
export const getSchools = (params) => request.get(`${V1}/schools`, { params })
export const getSchool = (code, params) => request.get(`${V1}/schools/${code}`, { params })
export const getMajors = (params) => request.get(`${V1}/majors`, { params })

// ---------------------------------------------------------------- 院校资料（Markdown + 图片 / 头像上传）
export const getSchoolProfile = (code) => request.get(`${V1}/schools/${code}/profile`)
export const saveSchoolProfile = (code, markdown) =>
  request.post(`${V1}/admin/schools/${code}/profile`, { markdown })
export const uploadSchoolImage = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post(`${V1}/admin/upload/image`, form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export const uploadAvatar = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post(`${V1}/users/avatar`, form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ---------------------------------------------------------------- 规则 / 校验
export const getPolicy = (params) => request.get(`${V1}/rules/policy`, { params })
export const getPolicyBook = (province, params) =>
  request.get(`${V1}/policies/${province}`, { params })
export const validateCombo = (payload) =>
  request.post(`${V1}/rules/subject-combo/validate`, payload)
export const validatePlanItems = (payload) =>
  request.post(`${V1}/rules/plan-items/validate`, payload)

// ---------------------------------------------------------------- 匹配
export const analyzeMatch = (payload) => request.post(`${V1}/match/analyze`, payload)
export const getMatchTask = (taskId) => request.get(`${V1}/match/tasks/${taskId}`)

// ---------------------------------------------------------------- 志愿方案
export const validatePlan = (payload) => request.post(`${V1}/plans/validate`, payload)
export const savePlan = (payload) => request.post(`${V1}/plans`, payload)
export const getPlans = () => request.get(`${V1}/plans`)
export const getPlan = (id) => request.get(`${V1}/plans/${id}`)

/** 导出走原生下载（响应是二进制 CSV，不走统一响应解包）。 */
export async function downloadPlan(id, filename) {
  const token = localStorage.getItem('admitatlas_token')
  const res = await fetch(`${import.meta.env.VITE_API_BASE_URL || '/api'}${V1}/plans/${id}/export?format=csv`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!res.ok) throw new Error('导出失败，请先保存方案')
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename || `AdmitAtlas-志愿方案-${id}.csv`
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
