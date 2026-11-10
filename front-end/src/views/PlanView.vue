<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  createPlanShare, downloadPlan, getPlan, getPlans, getPolicy,
  revokePlanShare, savePlan, validatePlan
} from '@/api'
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

// F-C-44：只读分享链接，默认隐藏账号、72 小时有效；只能分享已保存的方案。
const shareOpen = ref(false)
const shareBusy = ref(false)
const shareHours = ref(72)
const shareResult = ref(null)
const shareError = ref('')

const shareUrl = computed(() =>
  shareResult.value ? window.location.origin + shareResult.value.sharePath : '')

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

function openShare() {
  shareError.value = ''
  if (!savedPlanId.value) {
    notify('请先保存方案，再生成只读分享链接', 'rose')
    return
  }
  shareOpen.value = true
}

async function doShare() {
  if (!savedPlanId.value) return
  shareBusy.value = true
  shareError.value = ''
  try {
    shareResult.value = await createPlanShare(savedPlanId.value, {
      expiresInHours: Number(shareHours.value), masked: true
    })
    notify('只读分享链接已生成')
  } catch (e) {
    shareError.value = e.message || '生成失败，请稍后重试'
  } finally {
    shareBusy.value = false
  }
}

async function doRevoke() {
  if (!savedPlanId.value) return
  shareBusy.value = true
  try {
    await revokePlanShare(savedPlanId.value)
    shareResult.value = null
    notify('已撤销该方案的全部分享链接')
  } catch (e) {
    shareError.value = e.message || '撤销失败'
  } finally {
    shareBusy.value = false
  }
}

async function copyShare() {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    notify('链接已复制到剪贴板')
  } catch {
    notify('请手动复制下方链接', 'rose')
  }
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
        <RouterLink to="/plan/compare" class="btn btn-slate"><span class="btn-glyph">⇄</span> 方案对比</RouterLink>
        <button type="button" class="btn btn-blue" @click="openShare"><span class="btn-glyph">↗</span> 生成分享</button>
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

    <Teleport to="body">
      <div v-if="shareOpen" class="share-mask no-print" @click.self="shareOpen = false">
        <div class="share-panel glass" role="dialog" aria-modal="true" aria-label="生成只读分享链接">
          <div class="flex items-start justify-between gap-3">
            <div>
              <p class="eyebrow">Read-only share</p>
              <h2 class="mt-2 text-[16px] font-semibold">分享这套志愿方案</h2>
            </div>
            <button class="btn btn-slate" type="button" aria-label="关闭" @click="shareOpen = false">✕</button>
          </div>
          <p class="mt-3 text-[12px] leading-6 muted">
            生成一条游客可打开的只读链接，不暴露账号与方案内部标识；到期或撤销后立即失效。
          </p>

          <label class="mt-4 block text-[11px] muted" for="share-hours">有效期（小时，0 表示长期有效）</label>
          <input id="share-hours" v-model.number="shareHours" class="field mt-1" type="number" min="0" max="720" />

          <div v-if="shareError" class="mt-3 rounded-lg px-3 py-2 text-[12px]"
            style="background: var(--rose-bg); color: var(--rose)">{{ shareError }}</div>

          <div v-if="shareResult" class="mt-4 rounded-xl border border-hairline p-3">
            <p class="text-[11px] faint">只读链接</p>
            <p class="mt-1 break-all font-mono text-[11px]">{{ shareUrl }}</p>
            <p class="mt-2 text-[11px] faint">
              到期时间：{{ shareResult.expiresAt ? shareResult.expiresAt.replace('T', ' ').slice(0, 16) : '长期有效' }} · 已隐藏账号信息
            </p>
            <div class="mt-3 btn-group">
              <button class="btn btn-blue" type="button" @click="copyShare">复制链接</button>
              <RouterLink :to="shareResult.sharePath" class="btn btn-slate" target="_blank">预览</RouterLink>
            </div>
          </div>

          <div class="mt-5 flex flex-wrap justify-end gap-2">
            <button v-if="shareResult" class="btn btn-rose" type="button" :disabled="shareBusy" @click="doRevoke">撤销链接</button>
            <button class="btn btn-teal" type="button" :disabled="shareBusy" @click="doShare">
              {{ shareBusy ? '处理中…' : (shareResult ? '重新生成' : '生成链接') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
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
.share-mask {
  position: fixed; inset: 0; z-index: 95; display: grid; place-items: center; padding: 20px;
  background: color-mix(in srgb, var(--text) 32%, transparent);
  -webkit-backdrop-filter: blur(6px); backdrop-filter: blur(6px);
}
.share-panel { width: min(480px, 100%); max-height: 88vh; overflow: auto; padding: 22px; border-radius: 18px; box-shadow: var(--shadow); }
</style>
