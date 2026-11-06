import request from '@/utils/request'

const V1 = '/v1'

/** 匹配引擎：已实现的策略列表（P0 可用于联调） */
export const getStrategies = () => request.get(`${V1}/match/strategies`)

/** 智能匹配：P0 返回 501 占位，P1 实现算法 */
export const analyzeMatch = (payload) => request.post(`${V1}/match/analyze`, payload)

/** 分数换算位次：P1 实现 */
export const convertRank = (payload) => request.post(`${V1}/scores/convert-rank`, payload)

export const getSegments = (params) => request.get(`${V1}/segments`, { params })
export const getBatchLines = (params) => request.get(`${V1}/batch-lines`, { params })
export const getAdmissionScores = (params) => request.get(`${V1}/admission/scores`, { params })
export const getSchools = (params) => request.get(`${V1}/schools`, { params })
export const getSchool = (code) => request.get(`${V1}/schools/${code}`)
export const getMajors = (params) => request.get(`${V1}/majors`, { params })
export const getPolicy = (params) => request.get(`${V1}/rules/policy`, { params })
export const savePlan = (payload) => request.post(`${V1}/plans`, payload)
