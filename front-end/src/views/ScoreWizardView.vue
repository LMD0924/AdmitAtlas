<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  getBatchLines, getDictionaries, getPolicy, getStrategies,
  saveStudentScore, validateCombo
} from '@/api'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const saved = ref(false)
const saving = ref(false)
const error = ref('')
const comboErrors = ref([])
const modes = ref([])
const provinces = ref([])
const policy = ref(null)
const batchLines = ref([])
const rankInfo = ref(null)
const probeState = ref('loading')

const modeLabels = {
  TRADITIONAL: '传统文理分科',
  GROUP_3_1_2: '3+1+2 · 院校专业组',
  GROUP_3_3: '3+3 · 专业+院校'
}

const defaultForm = () => ({
  provinceCode: store.provinceCode || '42',
  examYear: Number(store.examYear) || new Date().getFullYear(),
  examMode: store.examMode || 'GROUP_3_1_2',
  category: store.category || '物理类',
  totalScore: store.totalScore ?? 612,
  subjectCombo: [...store.subjectCombo],
  subjectScores: { ...store.subjectScores }
})
const form = reactive(defaultForm())

const subjectOptions = computed(() => {
  if (form.examMode === 'TRADITIONAL') return []
  if (form.examMode === 'GROUP_3_3')
    return ['物理', '化学', '生物', '历史', '政治', '地理', '技术']
  return ['物理', '历史', '化学', '生物', '政治', '地理']
})

const scoreKeys = computed(() => ['语文', '数学', '外语', ...form.subjectCombo])
const modeOptions = computed(() => modes.value.map((m) => ({ value: m, label: modeLabels[m] || m })))

const timeline = computed(() => [
  { no: '01', title: '录入成绩', desc: saved.value ? '已保存到数据库' : '当前步骤' },
  { no: '02', title: '换算位次', desc: rankInfo.value
    ? `位次 ${rankInfo.value.rank}${rankInfo.value.estimated ? '（估算）' : ''}` : '保存成绩后自动换算' },
  { no: '03', title: '开始匹配', desc: saved.value ? '可前往智能匹配' : '等待成绩档案' }
])

async function loadMetadata() {
  try {
    const [d, s] = await Promise.all([getDictionaries(), getStrategies()])
    provinces.value = d?.provinces ?? []
    modes.value = s?.supportedModes ?? []
    if (s?.dataVersion) store.dataVersion = s.dataVersion
    probeState.value = modes.value.length ? 'ok' : 'error'
  } catch {
    probeState.value = 'error'
  }
}

async function loadPolicy() {
  try {
    const [p, b] = await Promise.all([
      getPolicy({ provinceCode: form.provinceCode, examYear: Number(form.examYear) }),
      getBatchLines({ provinceCode: form.provinceCode, examYear: Number(form.examYear), category: form.category })
    ])
    policy.value = p
    batchLines.value = b?.items ?? []
  } catch {
    policy.value = null
    batchLines.value = []
  }
}

onMounted(async () => {
  await loadMetadata()
  await loadPolicy()
  if (store.rank) {
    rankInfo.value = { rank: store.rank, estimated: store.rankEstimated, message: store.rankMessage }
    saved.value = !!store.scoreId
  }
})

watch(() => [form.provinceCode, form.examYear, form.category], () => loadPolicy())

function toggleSubject(s) {
  const i = form.subjectCombo.indexOf(s)
  if (i >= 0) form.subjectCombo.splice(i, 1)
  else form.subjectCombo.push(s)
}

function reset() {
  Object.assign(form, defaultForm())
  saved.value = false
  rankInfo.value = null
  comboErrors.value = []
  error.value = ''
  store.clearPlanItems()
}

async function submit() {
  error.value = ''
  comboErrors.value = []
  saving.value = true
  try {
    const combo = await validateCombo({ examMode: form.examMode, subjectCombo: [...form.subjectCombo] })
    if (!combo.valid) {
      comboErrors.value = combo.errors || []
      error.value = '选科组合不合法，请按提示修改'
      return
    }
    const payload = {
      provinceCode: form.provinceCode,
      examYear: Number(form.examYear),
      examMode: form.examMode,
      category: form.category,
      subjectCombo: [...form.subjectCombo],
      totalScore: Number(form.totalScore),
      subjectScores: { ...form.subjectScores }
    }
    const res = await saveStudentScore(payload)
    store.setScore(payload)
    store.setRank(res)
    store.setScoreId(res.id)
    rankInfo.value = res
    saved.value = true
  } catch (e) {
    error.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="space-y-7">
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p v-reveal="0" class="eyebrow">Step 01 / 成绩档案</p>
        <h1 v-reveal="1" class="mt-2 text-[22px] font-semibold tracking-[-0.02em]">先让分数变得可比</h1>
        <p v-reveal="2" class="mt-2 max-w-[76ch] text-[12px] leading-6 muted">
          分数本身不参与跨年比较。保存后系统按本省一分一段表换算为省排名（位次），再用位次对照历年投档线。
        </p>
      </div>
      <div v-reveal="2" class="btn-group">
        <RouterLink to="/match" class="btn btn-blue"><span class="btn-glyph">◎</span> 智能匹配</RouterLink>
        <RouterLink to="/plan" class="btn btn-amber"><span class="btn-glyph">▤</span> 志愿方案</RouterLink>
      </div>
    </div>

    <div class="grid gap-4 lg:grid-cols-12">
      <aside class="lg:col-span-4">
        <div v-reveal="0" class="glass p-5">
          <p class="eyebrow">Progress</p>
          <div class="mt-4 space-y-0">
            <div v-for="(item, i) in timeline" :key="item.no" class="timeline-row" :class="{ 'is-current': i === 0 }">
              <span class="timeline-no">{{ item.no }}</span>
              <div>
                <p class="text-[13px] font-semibold">{{ item.title }}</p>
                <p class="mt-0.5 text-[11px] faint">{{ item.desc }}</p>
              </div>
            </div>
          </div>
        </div>

        <div v-reveal="1" class="glass mt-4 p-5">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <p class="text-[13px] font-semibold">规则与后端</p>
            <span class="pill" :class="probeState === 'ok' ? 'pill-mint' : probeState === 'error' ? 'pill-rose' : 'pill-neutral'">
              {{ probeState === 'loading' ? '检测中' : probeState === 'ok' ? '已连通' : '未连通' }}
            </span>
          </div>
          <p v-if="modes.length" class="mt-3 text-[12px] leading-6 muted">
            已注册模式：<span class="link">{{ modes.join(' · ') }}</span>
          </p>
          <p v-else class="mt-3 text-[12px] leading-6 muted">未检测到后端，请先启动 launcher 应用（8080）。</p>
          <hr class="hairline my-4" />
          <template v-if="policy">
            <div class="flex items-baseline justify-between text-[12px]">
              <span class="muted">志愿单位</span><span class="font-semibold">{{ policy.volunteerUnit }}</span>
            </div>
            <div class="mt-2 flex items-baseline justify-between text-[12px]">
              <span class="muted">可填志愿数</span><span class="font-semibold">{{ policy.volunteerCount }}</span>
            </div>
            <div class="mt-2 flex items-baseline justify-between text-[12px]">
              <span class="muted">批次</span><span class="font-semibold">{{ (policy.batches || []).join(' / ') || '—' }}</span>
            </div>
          </template>
          <p v-else class="text-[11px] faint">该省 / 年份的志愿规则尚未发布</p>
          <div v-if="batchLines.length" class="mt-4">
            <p class="text-[11px] faint">省控线 / 批次线</p>
            <div class="mt-2 flex flex-wrap gap-2">
              <span v-for="b in batchLines" :key="b.batch" class="pill pill-sky">{{ b.batch }} {{ b.lineScore }} 分</span>
            </div>
          </div>
          <p class="mt-4 text-[11px] leading-5 faint">
            数据版本 {{ store.dataVersion || '—' }} · 演示数据（合成值），最终以各省教育考试院公布为准
          </p>
        </div>
      </aside>

      <div class="lg:col-span-8">
        <form v-reveal="1" class="glass p-6 md:p-7" @submit.prevent="submit">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <h2 class="text-[15px] font-semibold">成绩信息</h2>
            <span class="pill pill-sky">分数 → 位次</span>
          </div>
          <hr class="hairline my-5" />

          <div class="grid gap-4 md:grid-cols-2">
            <div v-reveal="1">
              <label class="field-label" for="province">省份</label>
              <select id="province" v-model="form.provinceCode" class="field">
                <option v-for="p in provinces" :key="p.provinceCode" :value="p.provinceCode">
                  {{ p.provinceName }}（{{ p.provinceCode }}）
                </option>
              </select>
            </div>
            <div v-reveal="2">
              <label class="field-label" for="year">高考年份</label>
              <input id="year" v-model.number="form.examYear" type="number" class="field" min="2000" max="2100" />
            </div>
            <div v-reveal="3">
              <label class="field-label" for="mode">考试模式</label>
              <select id="mode" v-model="form.examMode" class="field">
                <option v-for="m in modeOptions" :key="m.value" :value="m.value">{{ m.label }}</option>
              </select>
            </div>
            <div v-reveal="4">
              <label class="field-label" for="category">科类</label>
              <select id="category" v-model="form.category" class="field">
                <option>物理类</option><option>历史类</option><option>理科</option><option>文科</option>
              </select>
            </div>
            <div v-reveal="5" class="md:col-span-2">
              <label class="field-label" for="total">总分</label>
              <input id="total" v-model.number="form.totalScore" type="number" class="field md:max-w-[240px]" min="0" max="750" />
            </div>
          </div>

          <div v-if="subjectOptions.length" v-reveal="6" class="mt-6">
            <p class="field-label">选科组合（{{ form.examMode === 'GROUP_3_1_2' ? '1 门首选 + 2 门再选' : '任选 3 门' }}）</p>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="s in subjectOptions" :key="s" type="button" class="chip"
                :class="{ 'is-active': form.subjectCombo.includes(s) }"
                @click="toggleSubject(s)"
              >{{ s }}</button>
            </div>
          </div>

          <div v-reveal="6" class="mt-6">
            <p class="field-label">单科分（用于同分排序与专业门槛，不参与跨年比较）</p>
            <div class="grid grid-cols-3 gap-3 md:grid-cols-4">
              <div v-for="k in scoreKeys" :key="k">
                <label class="mb-1 block text-[11px] faint" :for="'s-' + k">{{ k }}</label>
                <input :id="'s-' + k" v-model.number="form.subjectScores[k]" type="number" class="field" min="0" max="150" />
              </div>
            </div>
          </div>

          <div v-if="comboErrors.length" class="mt-5 rounded-lg px-3 py-2 text-[12px]" style="background: var(--rose-bg); color: var(--rose)">
            <p v-for="e in comboErrors" :key="e">· {{ e }}</p>
          </div>
          <div v-if="error" class="mt-5 rounded-lg px-3 py-2 text-[12px]" style="background: var(--rose-bg); color: var(--rose)">{{ error }}</div>

          <Transition name="page">
            <div v-if="rankInfo" class="mt-6 rounded-xl border p-4" style="border-color: var(--line); background: var(--surface-2)">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <p class="text-[12px] font-semibold">换算结果 · 省排名（位次）</p>
                <span class="pill" :class="rankInfo.estimated || rankInfo.rankEstimated ? 'pill-amber' : 'pill-mint'">
                  {{ rankInfo.estimated || rankInfo.rankEstimated ? '估算' : '精确' }}
                </span>
              </div>
              <p class="mt-2 text-[26px] font-semibold leading-none">{{ rankInfo.rank.toLocaleString() }}</p>
              <p class="mt-2 text-[11px] faint">
                位次区间 {{ rankInfo.rankFrom?.toLocaleString() }} ~ {{ rankInfo.rankTo?.toLocaleString() }} ·
                {{ rankInfo.message }}
              </p>
            </div>
          </Transition>

          <hr class="hairline my-6" />
          <div class="flex flex-wrap items-center gap-3">
            <button type="submit" class="btn btn-mint" :disabled="saving">
              <span class="btn-glyph">✓</span> {{ saving ? '保存中…' : '保存成绩档案' }} <span class="link-arrow">→</span>
            </button>
            <button type="button" class="btn btn-rose" @click="reset"><span class="btn-glyph">✕</span> 重置</button>
            <RouterLink v-if="saved" to="/match" class="btn btn-blue ml-auto">
              <span class="btn-glyph">◎</span> 前往智能匹配 <span class="link-arrow">→</span>
            </RouterLink>
          </div>
          <p class="mt-4 text-[11px] faint">成绩档案已写入数据库（student_score），刷新页面不会丢失。</p>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.timeline-row { position: relative; display: grid; grid-template-columns: 38px 1fr; gap: 10px; padding: 12px 0; opacity: .58; transition: opacity .35s var(--ease), transform .35s var(--ease); }
.timeline-row.is-current { opacity: 1; }
.timeline-row:hover { transform: translateX(4px); opacity: 1; }
.timeline-row:not(:last-child)::after { content: ''; position: absolute; left: 14px; top: 38px; bottom: -4px; width: 1px; background: var(--line); }
.timeline-no { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 50%; border: 1px solid var(--line); font-size: 11px; color: var(--text-2); font-variant-numeric: tabular-nums; }
.is-current .timeline-no { background: var(--accent); border-color: var(--accent); color: var(--on-accent); }
</style>
