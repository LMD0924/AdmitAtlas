import { defineStore } from 'pinia'

const KEY = 'admitatlas-state-v1'

function load() {
  try {
    return JSON.parse(localStorage.getItem(KEY) || '{}')
  } catch {
    return {}
  }
}

const saved = typeof localStorage === 'undefined' ? {} : load()

const defaultPreference = () => ({
  regions: [], majorKeywords: [], schoolTiers: [], priority: 'MAJOR_FIRST'
})

export const useAppStore = defineStore('app', {
  state: () => ({
    provinceCode: saved.provinceCode || '42',
    examYear: saved.examYear || new Date().getFullYear(),
    examMode: saved.examMode || 'GROUP_3_1_2',
    category: saved.category || '物理类',
    subjectCombo: saved.subjectCombo || ['物理', '化学', '生物'],
    totalScore: saved.totalScore ?? null,
    subjectScores: saved.subjectScores || {},
    rank: saved.rank ?? null,
    rankFrom: saved.rankFrom ?? null,
    rankTo: saved.rankTo ?? null,
    rankEstimated: saved.rankEstimated ?? false,
    rankMessage: saved.rankMessage || '',
    scoreId: saved.scoreId ?? null,
    preference: saved.preference || defaultPreference(),
    matchResult: saved.matchResult || null,
    planItems: saved.planItems || [],
    planId: saved.planId ?? null,
    dataVersion: saved.dataVersion || '',
    demoData: saved.demoData ?? true
  }),

  getters: {
    hasScore: (s) => s.totalScore !== null && s.provinceCode !== '',
    scorePayload: (s) => ({
      provinceCode: s.provinceCode,
      examYear: Number(s.examYear),
      examMode: s.examMode,
      category: s.category,
      subjectCombo: [...s.subjectCombo],
      totalScore: Number(s.totalScore),
      subjectScores: { ...s.subjectScores }
    }),
    planPayload: (s) => ({
      planName: '我的志愿方案',
      taskId: s.matchResult?.taskId || null,
      provinceCode: s.provinceCode,
      examYear: Number(s.examYear),
      category: s.category,
      subjectCombo: [...s.subjectCombo],
      items: s.planItems.map((it, i) => ({
        positionNo: i + 1,
        schoolCode: it.schoolCode,
        schoolName: it.schoolName,
        groupOrMajorCode: it.groupOrMajorCode,
        unitName: it.unitName,
        tier: it.tier,
        matchScore: it.matchScore,
        historyMinRank: it.historyMinRank,
        reason: it.reason
      }))
    }),
    tierDistribution: (s) => {
      const counts = { CHONG: 0, WEN: 0, BAO: 0 }
      for (const it of s.planItems) counts[it.tier] = (counts[it.tier] || 0) + 1
      const total = s.planItems.length
      return Object.fromEntries(Object.entries(counts).map(([k, v]) => [
        k, { count: v, ratio: total ? Math.round((v * 1000) / total) / 10 : 0 }
      ]))
    }
  },

  actions: {
    persist() {
      try {
        localStorage.setItem(KEY, JSON.stringify({
          provinceCode: this.provinceCode, examYear: this.examYear,
          examMode: this.examMode, category: this.category,
          subjectCombo: this.subjectCombo, totalScore: this.totalScore,
          subjectScores: this.subjectScores, rank: this.rank,
          rankFrom: this.rankFrom, rankTo: this.rankTo,
          rankEstimated: this.rankEstimated, rankMessage: this.rankMessage,
          scoreId: this.scoreId, preference: this.preference,
          matchResult: this.matchResult, planItems: this.planItems,
          planId: this.planId, dataVersion: this.dataVersion,
          demoData: this.demoData
        }))
      } catch { /* 隐私模式 / 配额不足时降级为内存态 */ }
    },
    setScore(payload) {
      Object.assign(this, payload)
      this.rank = null
      this.rankFrom = null
      this.rankTo = null
      this.rankEstimated = false
      this.rankMessage = ''
      this.matchResult = null
      this.persist()
    },
    setRank(payload) {
      this.rank = payload.rank ?? null
      this.rankFrom = payload.rankFrom ?? null
      this.rankTo = payload.rankTo ?? null
      this.rankEstimated = !!payload.estimated || !!payload.rankEstimated
      this.rankMessage = payload.message || payload.rankMessage || ''
      if (payload.dataVersion) this.dataVersion = payload.dataVersion
      if (payload.demoData !== undefined) this.demoData = payload.demoData
      this.persist()
    },
    setPreference(payload) {
      this.preference = { ...defaultPreference(), ...(payload || {}) }
      this.persist()
    },
    setScoreId(id) { this.scoreId = id; this.persist() },
    setMatchResult(result) {
      this.matchResult = result
      if (result?.dataVersion) this.dataVersion = result.dataVersion
      if (result?.demoData !== undefined) this.demoData = result.demoData
      this.persist()
    },
    addPlanItem(item) {
      const exists = this.planItems.some(
        (x) => x.schoolCode === item.schoolCode
          && x.groupOrMajorCode === item.groupOrMajorCode)
      if (exists) return false
      this.planItems.push({ ...item })
      this.planId = null
      this.persist()
      return true
    },
    removePlanItem(index) {
      this.planItems.splice(index, 1)
      this.planId = null
      this.persist()
    },
    movePlanItem(from, to) {
      if (from === to || from < 0 || to < 0
        || from >= this.planItems.length || to >= this.planItems.length) return
      const [row] = this.planItems.splice(from, 1)
      this.planItems.splice(to, 0, row)
      this.planId = null
      this.persist()
    },
    setPlanItems(items) {
      this.planItems = (items || []).map((x) => ({ ...x }))
      this.planId = null
      this.persist()
    },
    clearPlanItems() {
      this.planItems = []
      this.planId = null
      this.persist()
    },
    setPlanId(id) { this.planId = id; this.persist() },
    reset() {
      this.$reset()
      this.persist()
    }
  }
})
