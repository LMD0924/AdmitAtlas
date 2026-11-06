import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    provinceCode: '',
    examYear: new Date().getFullYear(),
    examMode: 'GROUP_3_1_2',
    category: '物理类',
    subjectCombo: ['物理', '化学', '生物'],
    totalScore: null,
    subjectScores: {},
    rank: null,
    rankEstimated: false,
    preference: {
      regions: [],
      majorKeywords: [],
      schoolTiers: [],
      priority: 'MAJOR_FIRST'
    }
  }),
  getters: {
    hasScore: (state) => state.totalScore !== null && state.provinceCode !== '',
    scorePayload: (state) => ({
      provinceCode: state.provinceCode,
      examYear: Number(state.examYear),
      examMode: state.examMode,
      category: state.category,
      subjectCombo: state.subjectCombo,
      totalScore: Number(state.totalScore),
      subjectScores: state.subjectScores
    })
  },
  actions: {
    setScore(payload) {
      Object.assign(this, payload)
    },
    reset() {
      this.$reset()
    }
  }
})
