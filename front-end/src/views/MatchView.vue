<script setup>
import { computed, ref } from 'vue'
import { useMatch } from '@/composables/useMatch'
import { useAppStore } from '@/stores/app'
import RiskTag from '@/components/RiskTag.vue'

const store = useAppStore()
const { loading, error, errorCode, result, run } = useMatch()

const tiers = [
  { key: 'CHONG', label: '冲', desc: '历史门槛位次明显靠前，波动较大', pill: 'pill-rose' },
  { key: 'WEN', label: '稳', desc: '历史门槛位次与你相当', pill: 'pill-amber' },
  { key: 'BAO', label: '保', desc: '历史门槛位次明显靠后，可作兜底', pill: 'pill-mint' }
]

const tierLabels = { CHONG: '冲', WEN: '稳', BAO: '保' }
const tierFilter = ref('ALL')
const keyword = ref('')
const notice = ref('')
const keywordText = ref((store.preference.majorKeywords || []).join(','))

const regionOptions = [
  { code: '42', label: '湖北' }, { code: '11', label: '北京' },
  { code: '44', label: '广东' }, { code: '33', label: '浙江' }, { code: '51', label: '四川' }
]
const tierOptions = ['985', '211', '双一流', '省重点', '普通']

const summary = computed(() => [
  { label: '省份', value: store.provinceCode || '未填写' },
  { label: '年份', value: store.examYear },
  { label: '总分', value: store.totalScore ?? '未填写' },
  { label: '考试模式', value: store.examMode },
  { label: '位次', value: store.rank ? store.rank.toLocaleString() : '待换算' }
])

const items = computed(() => result.value?.items ?? [])
const shown = computed(() => items.value.filter((it) => {
  if (tierFilter.value !== 'ALL' && it.tier !== tierFilter.value) return false
  if (keyword.value.trim()) {
    const q = keyword.value.trim()
    return (it.schoolName || '').includes(q) || (it.unitName || '').includes(q)
  }
  return true
}))

function toggleRegion(code) {
  const next = [...store.preference.regions]
  const i = next.indexOf(code)
  if (i >= 0) next.splice(i, 1)
  else next.push(code)
  store.setPreference({ ...store.preference, regions: next })
}

function toggleTier(t) {
  const next = [...store.preference.schoolTiers]
  const i = next.indexOf(t)
  if (i >= 0) next.splice(i, 1)
  else next.push(t)
  store.setPreference({ ...store.preference, schoolTiers: next })
}

function applyKeywords() {
  const list = keywordText.value.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
  store.setPreference({ ...store.preference, majorKeywords: list })
}

function isAdded(item) {
  return store.planItems.some(
    (x) => x.schoolCode === item.schoolCode && x.groupOrMajorCode === item.groupOrMajorCode)
}

function addToPlan(item) {
  const ok = store.addPlanItem({
    schoolCode: item.schoolCode, schoolName: item.schoolName,
    groupOrMajorCode: item.groupOrMajorCode, unitName: item.unitName,
    tier: item.tier, matchScore: item.matchScore,
    historyMinRank: item.historyMinRank, reason: item.reason
  })
  notice.value = ok ? `已加入志愿表：${item.schoolName} · ${item.unitName}（共 ${store.planItems.length} 条）`
    : '该志愿已在志愿表中'
  window.setTimeout(() => { notice.value = '' }, 2600)
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p v-reveal="0" class="eyebrow">Matching engine</p>
        <h1 v-reveal="1" class="mt-2 text-[22px] font-semibold tracking-[-0.02em]">在同一把尺子上找到你的位置</h1>
        <p v-reveal="2" class="mt-2 max-w-[76ch] text-[12px] leading-6 muted">
          位次换算 → 批次线与选科过滤 → 按 min_rank 索引召回 → 近三年加权位次差 → 冲稳保分档
        </p>
      </div>
      <div v-reveal="2" class="btn-group">
        <RouterLink to="/score" class="btn btn-mint"><span class="btn-glyph">✎</span> 录入成绩</RouterLink>
        <RouterLink to="/plan" class="btn btn-amber"><span class="btn-glyph">▤</span> 志愿方案（{{ store.planItems.length }}）</RouterLink>
        <button class="btn btn-violet" :disabled="loading || !store.hasScore" @click="run({ maxResults: 60 })">
          <span class="btn-glyph">◎</span> {{ loading ? '匹配中…' : '开始匹配' }} <span class="link-arrow">→</span>
        </button>
      </div>
    </div>

    <div v-reveal="0" class="glass grid grid-cols-2 gap-x-6 gap-y-4 p-5 md:grid-cols-5">
      <div v-for="item in summary" :key="item.label">
        <p class="text-[11px] faint">{{ item.label }}</p>
        <p class="mt-1 truncate text-[13px] font-semibold">{{ item.value }}</p>
      </div>
    </div>

    <div v-reveal="0" class="glass p-5">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <p class="text-[13px] font-semibold">偏好筛选（可选）</p>
        <span class="text-[11px] faint">筛选在服务端执行，命中后再打分</span>
      </div>
      <div class="mt-4 grid gap-4 lg:grid-cols-2">
        <div>
          <p class="field-label">院校属地</p>
          <div class="flex flex-wrap gap-2">
            <button v-for="r in regionOptions" :key="r.code" type="button" class="chip"
              :class="{ 'is-active': store.preference.regions.includes(r.code) }"
              @click="toggleRegion(r.code)">{{ r.label }}</button>
          </div>
        </div>
        <div>
          <p class="field-label">院校层次</p>
          <div class="flex flex-wrap gap-2">
            <button v-for="t in tierOptions" :key="t" type="button" class="chip"
              :class="{ 'is-active': store.preference.schoolTiers.includes(t) }"
              @click="toggleTier(t)">{{ t }}</button>
          </div>
        </div>
        <div>
          <label class="field-label" for="kw">专业关键词</label>
          <input id="kw" v-model="keywordText" class="field"
            placeholder="留空则不限；多个关键词用逗号分隔，如 计算机,软件"
            @change="applyKeywords" />
        </div>
        <div>
          <label class="field-label" for="prio">排序偏好</label>
          <select id="prio" v-model="store.preference.priority" class="field"
            @change="store.setPreference(store.preference)">
            <option value="MAJOR_FIRST">专业优先：按匹配度排序</option>
            <option value="SCHOOL_FIRST">院校优先：高层次院校加权</option>
          </select>
        </div>
      </div>
      <p class="mt-3 text-[11px] faint">
        专业关键词按逗号拆成多个词，任一命中院校专业组名称即保留；留空表示不限制。
      </p>
    </div>

    <p v-if="!store.hasScore" v-reveal="0" class="glass-flat px-4 py-3 text-[12px] muted">
      还没有成绩档案，请先到 <RouterLink to="/score" class="link">成绩录入</RouterLink> 保存后再匹配。
    </p>

    <div v-if="error" class="glass px-4 py-3 text-[12px]" style="color: var(--rose)">
      {{ error }}<span v-if="errorCode" class="ml-2 faint">（code {{ errorCode }}）</span>
    </div>
    <div v-if="notice" class="glass px-4 py-3 text-[12px]" style="color: var(--mint)">{{ notice }}</div>

    <template v-if="result">
      <div v-reveal="0" class="grid gap-4 md:grid-cols-3">
        <div v-for="t in tiers" :key="t.key" class="glass p-4">
          <div class="flex items-center justify-between">
            <span class="pill" :class="t.pill">{{ t.label }}档</span>
            <RiskTag :tier="t.key" />
          </div>
          <p class="mt-3 text-[24px] font-semibold leading-none">{{ result.tierCounts?.[t.key] ?? 0 }}</p>
          <p class="mt-2 text-[11px] faint">{{ t.desc }}</p>
        </div>
      </div>

      <div v-reveal="0" class="glass overflow-hidden">
        <div class="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
          <div>
            <h2 class="text-[15px] font-semibold">候选志愿（{{ shown.length }} / {{ items.length }}）</h2>
            <p class="mt-1 text-[11px] faint">
              召回 {{ result.recalledCount }} 条历史记录 · 过滤 {{ result.filteredCount }} 条 ·
              志愿结构 {{ result.volunteerUnit }} / {{ result.volunteerCount }} 个 ·
              数据版本 {{ result.dataVersion }}
            </p>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <button v-for="f in ['ALL', 'CHONG', 'WEN', 'BAO']" :key="f" type="button" class="chip"
              :class="{ 'is-active': tierFilter === f }" @click="tierFilter = f">
              {{ f === 'ALL' ? '全部' : tierLabels[f] }}
            </button>
            <input v-model="keyword" class="field w-[150px]" placeholder="筛选院校 / 专业" />
          </div>
        </div>

        <div v-if="loading" class="grid gap-3 p-5 md:grid-cols-2">
          <div v-for="n in 4" :key="n" class="glass shimmer h-28 p-4">
            <div class="h-2.5 w-24 rounded-full placeholder-bar"></div>
            <div class="mt-5 h-2 w-3/4 rounded-full placeholder-bar"></div>
          </div>
        </div>

        <div v-else-if="!shown.length" class="empty-state m-5">
          <p class="text-[13px] font-semibold">没有符合条件的候选</p>
          <p class="mt-2 text-[11px] faint">可放宽偏好筛选，或返回成绩录入调整分数</p>
        </div>

        <div v-else class="grid gap-3 p-5 lg:grid-cols-2">
          <div v-for="it in shown" :key="it.schoolCode + it.groupOrMajorCode" class="candidate glass p-4">
            <div class="flex flex-wrap items-start justify-between gap-2">
              <div>
                <p class="text-[13px] font-semibold">{{ it.schoolName }}</p>
                <p class="mt-1 text-[11px] faint">{{ it.schoolCode }} · {{ it.groupOrMajorCode }} · {{ it.unitName }}</p>
              </div>
              <RiskTag :tier="it.tier" />
            </div>
            <div class="mt-3 flex flex-wrap items-center gap-x-5 gap-y-1 text-[11px] muted">
              <span>匹配度 <strong class="text-[13px]" style="color: var(--text)">{{ it.matchScore }}</strong></span>
              <span>加权位次 {{ it.historyMinRank?.toLocaleString() }}</span>
              <span>位次差 <strong :style="{ color: it.rankDelta >= 0 ? 'var(--mint)' : 'var(--rose)' }">
                {{ it.rankDelta >= 0 ? '+' : '' }}{{ it.rankDelta }}</strong></span>
              <span>计划 {{ it.planCount }}</span>
            </div>
            <p class="mt-3 text-[11px] leading-5 faint">{{ it.reason }}</p>
            <div class="mt-3 flex items-center justify-between gap-2 border-t pt-3" style="border-color: var(--line-soft)">
              <span class="text-[11px] faint">选科：{{ it.subjectRequirement }}</span>
              <button class="btn" :class="isAdded(it) ? 'btn-slate' : 'btn-mint'"
                :disabled="isAdded(it)" @click="addToPlan(it)">
                <span class="btn-glyph">{{ isAdded(it) ? '✓' : '＋' }}</span>
                {{ isAdded(it) ? '已加入' : '加入志愿表' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="result.warnings?.length" class="glass-flat px-4 py-3 text-[11px] leading-5 muted">
        <p v-for="(w, i) in result.warnings" :key="i">· {{ w }}</p>
      </div>
    </template>

    <div v-else-if="!loading" class="empty-state">
      <p class="text-[13px] font-semibold">还没有匹配结果</p>
      <p class="mt-2 text-[11px] faint">确认成绩档案已保存后，点击右上角「开始匹配」</p>
    </div>

    <div class="flex flex-wrap items-center justify-between gap-2 text-[11px] leading-5 faint">
      <span>{{ result?.disclaimer || '输出为「匹配度 / 风险等级」，不构成录取承诺；最终以各省教育考试院公布数据为准' }}</span>
      <span v-if="result" class="pill pill-amber">{{ result.demoData ? '演示数据' : result.dataVersion }}</span>
    </div>
  </div>
</template>

<style scoped>
.candidate { transition: transform .35s var(--ease), border-color .35s var(--ease), box-shadow .35s var(--ease); }
.candidate:hover { transform: translateY(-3px); border-color: color-mix(in srgb, var(--accent) 35%, var(--line)); box-shadow: var(--shadow); }
</style>
