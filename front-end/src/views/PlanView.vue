<script setup>
import { computed, onMounted, ref } from 'vue'
import { downloadPlan, getPlan, getPlans, getPolicy, savePlan, validatePlan } from '@/api'
import { useAppStore } from '@/stores/app'
import RiskTag from '@/components/RiskTag.vue'

const store = useAppStore()
const policy = ref(null)
const check = ref(null)
const savedPlanId = ref(store.planId)
const plans = ref([])
const planName = ref('我的志愿方案')
const busy = ref(false)
const message = ref('')
const messageTone = ref('mint')
const dragIndex = ref(null)

const distribution = computed(() => [
  { key: 'CHONG', label: '冲', cls: 'chong', ...store.tierDistribution.CHONG },
  { key: 'WEN', label: '稳', cls: 'wen', ...store.tierDistribution.WEN },
  { key: 'BAO', label: '保', cls: 'bao', ...store.tierDistribution.BAO }
])

const rows = computed(() => store.planItems)
const volunteerCount = computed(() => policy.value?.volunteerCount ?? 0)
const volunteerUnit = computed(() => policy.value?.volunteerUnit ?? '待加载')

function notify(text, tone = 'mint') {
  message.value = text
  messageTone.value = tone
  window.setTimeout(() => { message.value = '' }, 3200)
}

async function loadPolicy() {
  try {
    policy.value = await getPolicy({
      provinceCode: store.provinceCode, examYear: Number(store.examYear)
    })
  } catch { policy.value = null }
}

async function loadPlans() {
  try { plans.value = (await getPlans())?.items ?? [] } catch { plans.value = [] }
}

function payload() {
  return { ...store.planPayload, planName: planName.value || '我的志愿方案' }
}

async function doValidate() {
  if (!rows.value.length) return notify('志愿表为空，请先从匹配结果添加', 'rose')
  busy.value = true
  try {
    check.value = await validatePlan(payload())
    notify(check.value.valid ? `校验通过 · ${check.value.itemCount} 条志愿` : '校验未通过，请查看下方错误',
      check.value.valid ? 'mint' : 'rose')
  } catch (e) { notify(e.message, 'rose') }
  finally { busy.value = false }
}

async function doSave() {
  if (!rows.value.length) return notify('志愿表为空，无法保存', 'rose')
  busy.value = true
  try {
    const res = await savePlan(payload())
    check.value = res
    if (!res.valid) return notify('方案未保存：存在校验错误', 'rose')
    store.setPlanId(res.planId)
    savedPlanId.value = res.planId
    notify(`方案已保存 · ID ${res.planId}`)
    await loadPlans()
  } catch (e) { notify(e.message, 'rose') }
  finally { busy.value = false }
}

async function doExport() {
  if (!savedPlanId.value) return notify('请先保存方案再导出', 'rose')
  try {
    await downloadPlan(savedPlanId.value)
    notify('已导出 CSV（可用 Excel 打开，含数据版本与免责声明）')
  } catch (e) { notify(e.message, 'rose') }
}

async function loadSavedPlan(id) {
  try {
    const res = await getPlan(id)
    store.setPlanItems((res.items || []).map((x) => ({
      schoolCode: x.schoolCode, schoolName: x.schoolName,
      groupOrMajorCode: x.groupOrMajorCode, unitName: x.unitName,
      tier: x.tier, matchScore: x.matchScore,
      historyMinRank: x.historyMinRank, reason: x.reason
    })))
    savedPlanId.value = id
    store.setPlanId(id)
    planName.value = res.plan?.planName || '我的志愿方案'
    check.value = null
    notify(`已载入方案 ${id}`)
  } catch (e) { notify(e.message, 'rose') }
}

function onDrop(to) {
  if (dragIndex.value !== null && dragIndex.value !== to) {
    store.movePlanItem(dragIndex.value, to)
    check.value = null
    savedPlanId.value = store.planId
  }
  dragIndex.value = null
}

function move(index, delta) {
  store.movePlanItem(index, index + delta)
  check.value = null
  savedPlanId.value = store.planId
}

function remove(index) {
  store.removePlanItem(index)
  check.value = null
  savedPlanId.value = store.planId
}

function doPrint() {
  window.print()
}

function clearAll() {
  store.clearPlanItems()
  check.value = null
  savedPlanId.value = null
}

onMounted(async () => {
  await Promise.all([loadPolicy(), loadPlans()])
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p v-reveal="0" class="eyebrow">Volunteer plan</p>
        <h1 v-reveal="1" class="mt-2 text-[22px] font-semibold tracking-[-0.02em]">把选择排成有梯度的顺序</h1>
        <p v-reveal="2" class="mt-2 max-w-[76ch] text-[12px] leading-6 muted">
          志愿单位与数量由本省规则动态决定；可拖拽调序、逐条校验、保存并导出含数据版本的方案。
        </p>
      </div>
      <div v-reveal="2" class="btn-group no-print">
        <RouterLink to="/score" class="btn btn-mint"><span class="btn-glyph">✎</span> 成绩录入</RouterLink>
        <RouterLink to="/match" class="btn btn-violet"><span class="btn-glyph">◎</span> 智能匹配</RouterLink>
      </div>
    </div>

    <div v-if="message" class="glass px-4 py-3 text-[12px]"
      :style="{ color: messageTone === 'rose' ? 'var(--rose)' : 'var(--mint)' }">{{ message }}</div>

    <div class="grid gap-4 lg:grid-cols-12">
      <div v-reveal="0" class="glass p-5 lg:col-span-5">
        <p class="eyebrow">Structure · 规则驱动</p>
        <div class="mt-4 space-y-4">
          <div class="flex items-baseline justify-between">
            <span class="text-[12px] muted">志愿单位</span>
            <span class="text-[13px] font-semibold">{{ volunteerUnit }}</span>
          </div>
          <hr class="hairline" />
          <div class="flex items-baseline justify-between">
            <span class="text-[12px] muted">可填志愿数</span>
            <span class="stat-num text-accent" v-count="volunteerCount">0</span>
          </div>
          <hr class="hairline" />
          <div class="flex items-baseline justify-between">
            <span class="text-[12px] muted">已填 / 上限</span>
            <span class="text-[13px] font-semibold">{{ rows.length }} / {{ volunteerCount || '—' }}</span>
          </div>
          <hr class="hairline" />
          <div class="flex items-center justify-between">
            <span class="text-[12px] muted">数据版本</span>
            <span class="pill pill-amber">{{ store.dataVersion || '演示数据' }}</span>
          </div>
        </div>
        <p class="mt-4 text-[11px] leading-5 faint">
          切换省份 / 年份后本卡自动读取对应 rule_policy；规则改为 96 个志愿时表格上限同步变化。
        </p>
      </div>

      <div v-reveal="1" class="glass p-5 lg:col-span-7">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <p class="eyebrow">Grade distribution</p>
          <span class="text-[11px] faint">按当前志愿表实时计算</span>
        </div>
        <div class="mt-6 flex h-2.5 w-full overflow-hidden rounded-full placeholder-bar">
          <div v-for="(d, i) in distribution" :key="d.key" class="bar-fill h-full" :class="'seg-' + d.cls"
            :style="{ width: d.ratio + '%', animationDelay: i * 0.12 + 's' }"></div>
        </div>
        <div class="mt-5 flex flex-wrap gap-6">
          <div v-for="d in distribution" :key="d.key" class="flex items-center gap-2">
            <RiskTag :tier="d.key" />
            <span class="text-[12px] muted">{{ d.count }} 条 · {{ d.ratio }}%</span>
          </div>
        </div>
        <p class="mt-6 text-[11px] leading-6 faint">
          建议保持「冲 / 稳 / 保」梯度，避免全部集中在「冲」档；保存时会检查数量、序号、重复与选科资格。
        </p>
      </div>
    </div>

    <div v-reveal="0" class="glass overflow-hidden">
      <div class="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
        <div>
          <h2 class="text-[15px] font-semibold">志愿表（{{ rows.length }} 条）</h2>
          <input v-model="planName" class="field mt-2 w-[220px] no-print" aria-label="方案名称" placeholder="方案名称" />
        </div>
        <div class="btn-group no-print">
          <RouterLink to="/match" class="btn btn-blue"><span class="btn-glyph">＋</span> 从匹配结果添加</RouterLink>
          <button class="btn btn-violet" :disabled="busy" @click="doValidate"><span class="btn-glyph">✓</span> 校验</button>
          <button class="btn btn-mint" :disabled="busy" @click="doSave"><span class="btn-glyph">▤</span> 保存方案</button>
          <button class="btn btn-amber" @click="doExport"><span class="btn-glyph">↓</span> 导出 Excel</button>
          <button class="btn btn-teal" @click="doPrint"><span class="btn-glyph">⎙</span> 打印 / PDF</button>
          <button class="btn btn-rose" @click="clearAll"><span class="btn-glyph">✕</span> 清空</button>
        </div>
      </div>

      <div class="overflow-x-auto">
        <table class="data-table min-w-[760px]">
          <thead>
            <tr><th>序号</th><th>院校 / 志愿单位</th><th>档位</th><th>匹配度</th><th>历史位次</th><th class="no-print">调序</th></tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in rows" :key="row.schoolCode + row.groupOrMajorCode"
              draggable="true" class="plan-row"
              @dragstart="dragIndex = i" @dragover.prevent @drop="onDrop(i)">
              <td class="font-semibold tabular-nums">{{ String(i + 1).padStart(2, '0') }}</td>
              <td style="color: var(--text)">
                {{ row.schoolName }}<span class="faint"> · {{ row.groupOrMajorCode }} {{ row.unitName }}</span>
              </td>
              <td><RiskTag :tier="row.tier" /></td>
              <td class="font-semibold">{{ row.matchScore }}</td>
              <td>{{ row.historyMinRank?.toLocaleString() ?? '—' }}</td>
              <td class="no-print">
                <div class="btn-group">
                  <button class="btn btn-slate" :disabled="i === 0" @click="move(i, -1)">↑</button>
                  <button class="btn btn-slate" :disabled="i === rows.length - 1" @click="move(i, 1)">↓</button>
                  <button class="btn btn-rose" @click="remove(i)">✕</button>
                </div>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="6" class="py-10 text-center faint">
                志愿表为空。到「智能匹配」把候选加入，或从下方载入已保存的方案。
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="px-5 py-4 text-[11px] faint">
        可直接拖拽行调序；导出文件与打印内容逐行一致，并包含数据版本、生成时间与免责声明。
      </p>
    </div>

    <div v-if="check" class="grid gap-4 lg:grid-cols-12">
      <div class="glass p-5 lg:col-span-6">
        <div class="flex items-center justify-between">
          <p class="text-[13px] font-semibold">校验结果</p>
          <span class="pill" :class="check.valid ? 'pill-mint' : 'pill-rose'">{{ check.valid ? '通过' : '未通过' }}</span>
        </div>
        <div v-if="check.errors?.length" class="mt-3 space-y-1 text-[12px]" style="color: var(--rose)">
          <p v-for="(e, i) in check.errors" :key="i">· {{ e }}</p>
        </div>
        <div v-if="check.warnings?.length" class="mt-3 space-y-1 text-[12px] muted">
          <p v-for="(w, i) in check.warnings" :key="i">· {{ w }}</p>
        </div>
        <p v-if="!check.errors?.length && !check.warnings?.length" class="mt-3 text-[12px] muted">
          {{ check.itemCount }} 条志愿全部符合 {{ check.volunteerUnit }} / {{ check.volunteerCount }} 的结构与选科要求。
        </p>
      </div>
      <div class="glass p-5 lg:col-span-6">
        <p class="text-[13px] font-semibold">选科资格明细</p>
        <div v-if="check.eligibility?.length" class="mt-3 max-h-[180px] space-y-1 overflow-auto text-[11px] muted">
          <p v-for="e in check.eligibility" :key="e.index">
            第 {{ e.index }} 条 · {{ e.eligible ? '符合' : '不符' }} · {{ e.requirement }}
            <span v-if="!e.eligible" style="color: var(--rose)">缺少 {{ (e.missing || []).join('、') }}</span>
          </p>
        </div>
        <p v-else class="mt-3 text-[11px] faint">暂无逐条资格信息</p>
      </div>
    </div>

    <div v-if="plans.length" class="glass p-5 no-print">
      <p class="text-[13px] font-semibold">最近保存的方案</p>
      <div class="mt-3 flex flex-wrap gap-2">
        <button v-for="p in plans" :key="p.planId" class="btn btn-slate" @click="loadSavedPlan(p.planId)">
          <span class="btn-glyph">▤</span> {{ p.planName }} · #{{ p.planId }}
        </button>
      </div>
    </div>

    <p class="text-center text-[11px] leading-5 faint">
      输出为「匹配度 / 风险等级」，不构成录取承诺；当前为演示数据（合成值），最终以各省教育考试院公布为准
    </p>
  </div>
</template>

<style scoped>
.plan-row { cursor: grab; transition: background-color .3s; }
.plan-row:hover { background: var(--surface-2); }
.bar-fill { transform-origin: left; animation: bar-grow .9s var(--ease) both; }
.seg-chong { background: var(--chong-text); }
.seg-wen { background: var(--wen-text); }
.seg-bao { background: var(--bao-text); }
@keyframes bar-grow { from { transform: scaleX(0); } to { transform: scaleX(1); } }
</style>
