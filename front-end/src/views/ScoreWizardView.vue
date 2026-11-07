<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getStrategies } from '@/api'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const saved = ref(false)
const modes = ref([])

const form = reactive({
  provinceCode: store.provinceCode || '42',
  examYear: store.examYear,
  examMode: store.examMode,
  category: store.category,
  totalScore: store.totalScore ?? 612,
  subjectCombo: [...store.subjectCombo]
})

const examModes = [
  { value: 'TRADITIONAL', label: '传统文理分科' },
  { value: 'GROUP_3_1_2', label: '3+1+2（院校专业组）' },
  { value: 'GROUP_3_3', label: '3+3（专业+院校 / 专业组）' }
]
const subjectOptions = ['物理', '化学', '生物', '历史', '政治', '地理']

onMounted(async () => {
  try {
    const res = await getStrategies()
    modes.value = res?.data?.supportedModes ?? []
  } catch {
    modes.value = []
  }
})

function submit() {
  store.setScore({
    provinceCode: form.provinceCode,
    examYear: Number(form.examYear),
    examMode: form.examMode,
    category: form.category,
    totalScore: Number(form.totalScore),
    subjectCombo: [...form.subjectCombo],
    rank: null,
    rankEstimated: false
  })
  saved.value = true
}
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-6">
    <div>
      <h1 class="text-2xl font-medium text-slate-800">成绩录入</h1>
      <p class="mt-2 text-sm text-slate-500">成绩将换算为省排名（位次）后用于跨年比较，分数本身不直接比较。</p>
    </div>

    <form class="aa-card space-y-5" @submit.prevent="submit">
      <div class="grid gap-4 md:grid-cols-2">
        <div>
          <label class="aa-label">省份代码</label>
          <input v-model="form.provinceCode" class="aa-input" placeholder="如 42 表示湖北" maxlength="6" />
        </div>
        <div>
          <label class="aa-label">高考年份</label>
          <input v-model.number="form.examYear" type="number" class="aa-input" min="2000" max="2100" />
        </div>
        <div>
          <label class="aa-label">考试模式</label>
          <select v-model="form.examMode" class="aa-input">
            <option v-for="m in examModes" :key="m.value" :value="m.value">{{ m.label }}</option>
          </select>
        </div>
        <div>
          <label class="aa-label">科类</label>
          <input v-model="form.category" class="aa-input" placeholder="物理类 / 历史类 / 理科 / 文科" />
        </div>
        <div>
          <label class="aa-label">总分</label>
          <input v-model.number="form.totalScore" type="number" class="aa-input" min="0" max="750" />
        </div>
      </div>

      <div>
        <label class="aa-label">选科组合</label>
        <div class="flex flex-wrap gap-2">
          <label
            v-for="s in subjectOptions"
            :key="s"
            class="cursor-pointer rounded-lg border px-3 py-1.5 text-sm"
            :class="form.subjectCombo.includes(s) ? 'border-brand-400 bg-brand-50 text-brand-800' : 'border-slate-200 text-slate-600'"
          >
            <input v-model="form.subjectCombo" type="checkbox" :value="s" class="sr-only" />
            {{ s }}
          </label>
        </div>
      </div>

      <div class="flex items-center gap-3 border-t border-slate-100 pt-4">
        <button type="submit" class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-800">
          保存成绩档案
        </button>
        <RouterLink v-if="saved" to="/match" class="text-sm text-brand-600 hover:underline">前往智能匹配 →</RouterLink>
        <span v-if="saved" class="text-xs text-slate-500">已保存到本地状态</span>
      </div>
    </form>

    <div class="aa-card text-sm text-slate-600">
      <p class="font-medium text-slate-800">后端策略探测</p>
      <p v-if="modes.length" class="mt-2">已注册考试模式：<span class="text-brand-800">{{ modes.join(' · ') }}</span></p>
      <p v-else class="mt-2 text-slate-400">未检测到后端，请先启动 launcher 应用（8080）。</p>
      <p class="mt-2 text-xs text-slate-400">位次换算接口（/api/v1/scores/convert-rank）将在 P1 实现。</p>
    </div>
  </div>
</template>
