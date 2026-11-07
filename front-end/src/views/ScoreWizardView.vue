<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getStrategies } from '@/api'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const saved = ref(false)
const modes = ref([])
const probeState = ref('loading')

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
  { value: 'GROUP_3_1_2', label: '3+1+2 · 院校专业组' },
  { value: 'GROUP_3_3', label: '3+3 · 专业+院校' }
]
const subjectOptions = ['物理', '化学', '生物', '历史', '政治', '地理']
const timeline = [
  { no: '01', title: '录入成绩', desc: '当前步骤' },
  { no: '02', title: '换算位次', desc: 'P1 接入一分一段表' },
  { no: '03', title: '开始匹配', desc: 'P1 接入录取数据' }
]

onMounted(async () => {
  try {
    const res = await getStrategies()
    modes.value = res?.data?.supportedModes ?? []
    probeState.value = modes.value.length ? 'ok' : 'error'
  } catch {
    modes.value = []
    probeState.value = 'error'
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
  <div class="grid gap-6 lg:grid-cols-12">
    <aside class="lg:col-span-4">
      <p v-reveal="0" class="eyebrow">Step 01 / 成绩档案</p>
      <h1 v-reveal="1" class="mt-5 text-[clamp(2rem,3.4vw,3rem)] font-medium leading-tight tracking-[-0.04em]">
        先让分数<br /><span class="text-accent">变得可比</span>
      </h1>
      <p v-reveal="2" class="mt-5 max-w-[38ch] text-[14px] leading-7 muted">
        分数本身不参与跨年比较。录入后系统会换算为省排名（位次），再用位次去对照历年投档线。
      </p>

      <div v-reveal="3" class="glass mt-10 p-6">
        <p class="eyebrow">Progress</p>
        <div class="mt-6 space-y-0">
          <div v-for="(item, i) in timeline" :key="item.no" class="timeline-row" :class="{ 'is-current': i === 0 }">
            <span class="timeline-no">{{ item.no }}</span>
            <div>
              <p class="text-[14px] font-medium">{{ item.title }}</p>
              <p class="mt-0.5 text-[12px] faint">{{ item.desc }}</p>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <div class="lg:col-span-8">
      <form v-reveal="1" v-spotlight class="glass spotlight p-6 md:p-8" @submit.prevent="submit">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 class="text-[17px] font-medium">成绩信息</h2>
          <span class="badge">分数 → 位次</span>
        </div>
        <hr class="hairline my-6" />

        <div class="grid gap-5 md:grid-cols-2">
          <div v-reveal="1">
            <label class="field-label" for="province">省份代码</label>
            <input id="province" v-model="form.provinceCode" class="field" placeholder="如 42 表示湖北" maxlength="6" />
          </div>
          <div v-reveal="2">
            <label class="field-label" for="year">高考年份</label>
            <input id="year" v-model.number="form.examYear" type="number" class="field" min="2000" max="2100" />
          </div>
          <div v-reveal="3">
            <label class="field-label" for="mode">考试模式</label>
            <select id="mode" v-model="form.examMode" class="field">
              <option v-for="m in examModes" :key="m.value" :value="m.value">{{ m.label }}</option>
            </select>
          </div>
          <div v-reveal="4">
            <label class="field-label" for="category">科类</label>
            <input id="category" v-model="form.category" class="field" placeholder="物理类 / 历史类 / 理科 / 文科" />
          </div>
          <div v-reveal="5" class="md:col-span-2">
            <label class="field-label" for="total">总分</label>
            <input id="total" v-model.number="form.totalScore" type="number" class="field md:max-w-[260px]" min="0" max="750" />
          </div>
        </div>

        <div v-reveal="6" class="mt-7">
          <p class="field-label">选科组合</p>
          <div class="flex flex-wrap gap-2">
            <label
              v-for="s in subjectOptions"
              :key="s"
              class="chip"
              :class="{ 'is-active': form.subjectCombo.includes(s) }"
            >
              <input v-model="form.subjectCombo" type="checkbox" :value="s" class="sr-only" />
              {{ s }}
            </label>
          </div>
        </div>

        <hr class="hairline my-7" />
        <div class="flex flex-wrap items-center gap-4">
          <button type="submit" class="btn btn-primary">保存成绩档案 <span class="link-arrow">→</span></button>
          <Transition name="page">
            <span v-if="saved" class="tick flex items-center gap-2 text-[13px] muted">
              <span class="inline-block h-1.5 w-1.5 rounded-full bg-accent"></span>
              已保存到本地状态
            </span>
          </Transition>
          <RouterLink v-if="saved" to="/match" class="btn btn-quiet ml-auto text-[13px]">
            前往智能匹配 <span class="link-arrow">→</span>
          </RouterLink>
        </div>
      </form>

      <div v-reveal="2" class="glass mt-5 p-6">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <p class="text-[14px] font-medium">后端策略探测</p>
          <span class="badge" :style="probeState === 'error' ? { color: 'var(--chong-text)' } : null">
            {{ probeState === 'loading' ? '检测中' : probeState === 'ok' ? '已连通' : '未连通' }}
          </span>
        </div>
        <div v-if="probeState === 'loading'" class="shimmer placeholder-bar mt-4 h-3 w-2/3 rounded-full"></div>
        <p v-else-if="modes.length" class="mt-3 text-[13px] muted">
          已注册模式：<span class="text-accent">{{ modes.join(' · ') }}</span>
        </p>
        <p v-else class="mt-3 text-[13px] muted">未检测到后端，请先启动 launcher 应用（8080）。</p>
        <p class="mt-3 text-[12px] faint">位次换算接口 /api/v1/scores/convert-rank 将在 P1 实现。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.timeline-row { position: relative; display: grid; grid-template-columns: 42px 1fr; gap: 12px; padding: 14px 0; opacity: .55; transition: opacity .4s var(--ease), transform .4s var(--ease); }
.timeline-row.is-current { opacity: 1; }
.timeline-row:hover { transform: translateX(5px); opacity: 1; }
.timeline-row:not(:last-child)::after { content: ''; position: absolute; left: 15px; top: 42px; bottom: -4px; width: 1px; background: var(--line); }
.timeline-no { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%; border: 1px solid var(--line); font-size: 11px; color: var(--text-2); font-variant-numeric: tabular-nums; }
.is-current .timeline-no { background: var(--accent); border-color: var(--accent); color: var(--on-accent); }
</style>
