<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getSharedPlan } from '@/api'

const route = useRoute()

function toNum(v) {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}
function fmtNum(v) {
  const n = toNum(v)
  return n === null ? '—' : n.toLocaleString()
}
function txt(v, fb = '—') {
  return v === null || v === undefined || v === '' ? fb : String(v)
}
function arr(v) { return Array.isArray(v) ? v : [] }
function fmtDate(v) {
  if (v === null || v === undefined || v === '') return '—'
  const s = String(v)
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  return d.toLocaleString('zh-CN', { hour12: false })
}
/** 档位用 pill-* 语义色：CHONG 冲 / WEN 稳 / BAO 保。 */
function tierPill(tier) {
  const t = String(tier || '').toUpperCase()
  if (t === 'CHONG') return { cls: 'pill-rose', label: '冲' }
  if (t === 'WEN') return { cls: 'pill-amber', label: '稳' }
  if (t === 'BAO') return { cls: 'pill-mint', label: '保' }
  return { cls: 'pill-neutral', label: t || '未分档' }
}

const data = ref(null)
const loading = ref(true)
const error = ref('')
const failed = ref(false)

const items = computed(() => arr(data.value?.items))
const ownerName = computed(() => {
  const v = data.value?.ownerName
  return v === null || v === undefined || v === '' ? '未公开' : String(v)
})

async function load() {
  loading.value = true
  error.value = ''
  failed.value = false
  data.value = null
  const token = String(route.params.token || '').trim()
  if (!token) {
    failed.value = true
    error.value = '缺少分享令牌'
    loading.value = false
    return
  }
  try {
    const res = await getSharedPlan(token)
    data.value = res ?? null
    if (!data.value) {
      failed.value = true
      error.value = '未返回方案内容'
    }
  } catch (e) {
    // 404 / 过期 / 撤销：统一给出可读卡片，不白屏。
    failed.value = true
    error.value = e?.message || '分享链接加载失败'
  } finally {
    loading.value = false
  }
}
function doPrint() { window.print() }

onMounted(load)
watch(() => route.params.token, () => { if (route.name === 'shared-plan') load() })
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-wrap items-center justify-between gap-3 no-print">
      <nav class="flex flex-wrap items-center gap-2 text-[11px] faint" aria-label="面包屑">
        <RouterLink to="/" class="link">首页</RouterLink>
        <span>/</span>
        <span class="muted">志愿方案分享</span>
      </nav>
      <button type="button" class="btn btn-teal" @click="doPrint">
        <span class="btn-glyph">⎙</span> 打印 / 另存 PDF
      </button>
    </div>

    <!-- 加载骨架 -->
    <div v-if="loading" class="space-y-4">
      <div class="glass shimmer h-40 p-6">
        <div class="h-4 w-52 rounded-full placeholder-bar"></div>
        <div class="mt-5 h-2.5 w-72 rounded-full placeholder-bar"></div>
        <div class="mt-3 h-2.5 w-56 rounded-full placeholder-bar"></div>
      </div>
      <div class="glass shimmer h-56 p-6">
        <div class="h-3 w-32 rounded-full placeholder-bar"></div>
        <div class="mt-5 h-2.5 w-full rounded-full placeholder-bar"></div>
        <div class="mt-3 h-2.5 w-[88%] rounded-full placeholder-bar"></div>
        <div class="mt-3 h-2.5 w-[94%] rounded-full placeholder-bar"></div>
      </div>
    </div>

    <!-- 404 / 过期 / 撤销 -->
    <div v-else-if="failed" class="glass p-8 text-center">
      <p class="text-[15px] font-semibold">链接不存在、已撤销或已过期</p>
      <p class="mt-2 text-[12px] muted">{{ error }}</p>
      <p class="mt-1 text-[11px] faint">请向分享者确认链接是否仍然有效，或重新获取分享链接。</p>
      <RouterLink to="/" class="btn btn-blue mt-5">返回首页</RouterLink>
    </div>

    <!-- 只读方案 -->
    <template v-else-if="data">
      <section class="glass p-6">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p class="eyebrow">Shared plan / 只读分享</p>
            <h1 class="mt-2 text-[22px] font-semibold tracking-[-0.02em]">{{ txt(data.planName, '未命名志愿方案') }}</h1>
            <p class="mt-1 text-[11px] faint">本页为只读快照，不含任何编辑或删除操作</p>
          </div>
          <div class="flex flex-wrap gap-2 no-print">
            <span class="pill pill-sky">只读</span>
            <span v-if="data.masked" class="pill pill-amber">已脱敏</span>
            <span v-if="data.demoData" class="pill pill-amber">演示数据</span>
          </div>
        </div>
        <hr class="hairline my-5" />
        <div class="sv-meta">
          <div><span>分享者</span><strong>{{ ownerName }}</strong></div>
          <div><span>创建时间</span><strong>{{ fmtDate(data.createdAt) }}</strong></div>
          <div><span>过期时间</span><strong>{{ fmtDate(data.expiresAt) }}</strong></div>
          <div><span>志愿单位</span><strong>{{ txt(data.volunteerUnit) }}</strong></div>
          <div><span>志愿数量</span><strong>{{ fmtNum(data.volunteerCount) }}</strong></div>
          <div><span>数据版本</span><strong>{{ txt(data.dataVersion) }}</strong></div>
          <div><span>条目数</span><strong>{{ fmtNum(data.total ?? items.length) }}</strong></div>
        </div>
      </section>

      <div v-if="data.disclaimer" class="glass sv-disclaimer">
        <p class="text-[12px] font-semibold">免责声明</p>
        <p class="mt-2 text-[12px] leading-6 muted">{{ data.disclaimer }}</p>
      </div>

      <section class="glass overflow-hidden">
        <div class="flex flex-wrap items-center justify-between gap-2 px-5 py-4">
          <h2 class="text-[15px] font-semibold">志愿表（{{ items.length }} 条）</h2>
          <span class="pill pill-neutral">按序号排列</span>
        </div>
        <div class="sv-scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th>序号</th><th>院校</th><th>组别 / 专业</th><th>档位</th>
                <th>匹配度</th><th>历史最低位次</th><th>理由</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, i) in items" :key="i">
                <td class="tabular-nums font-semibold">{{ String(row.positionNo ?? i + 1).padStart(2, '0') }}</td>
                <td>{{ txt(row.schoolName) }}</td>
                <td>{{ txt(row.groupOrMajorCode) }}<span v-if="row.unitName" class="faint"> · {{ row.unitName }}</span></td>
                <td><span class="pill" :class="tierPill(row.tier).cls">{{ tierPill(row.tier).label }}</span></td>
                <td>{{ fmtNum(row.matchScore) }}</td>
                <td>{{ fmtNum(row.historyMinRank) }}</td>
                <td class="sv-reason">{{ txt(row.reason, '—') }}</td>
              </tr>
              <tr v-if="!items.length">
                <td colspan="7" class="py-10 text-center faint">该分享方案暂无志愿条目</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <p class="text-center text-[11px] leading-5 faint">
        当前为演示数据集（合成值），非真实录取数据；最终以各省教育考试院公布为准
      </p>
    </template>
  </div>
</template>

<style scoped>
.sv-meta { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 14px 18px; }
.sv-meta > div { display: flex; flex-direction: column; gap: 4px; }
.sv-meta span { font-size: 11px; color: var(--text-3); }
.sv-meta strong { font-size: 13px; font-weight: 600; color: var(--text); font-variant-numeric: tabular-nums; }

.sv-disclaimer {
  padding: 16px 20px;
  border-color: color-mix(in srgb, var(--amber) 30%, var(--line));
  background: color-mix(in srgb, var(--amber-bg) 40%, var(--surface));
}

.sv-scroll { overflow-x: auto; }
.sv-scroll .data-table { min-width: 720px; }
.sv-scroll .data-table td { vertical-align: top; }
.sv-reason { min-width: 200px; max-width: 300px; white-space: normal; line-height: 1.6; }

/* 打印 / 另存 PDF：隐藏导航与按钮，表格整页铺开，不出现横向截断。 */
@media print {
  .no-print, .btn { display: none !important; }
  .glass { box-shadow: none !important; }
  .sv-disclaimer, .sv-meta, tbody tr { break-inside: avoid; }
  .sv-scroll { overflow: visible; }
  .sv-scroll .data-table { min-width: 0; }
  .sv-reason { max-width: none; }
}
</style>
