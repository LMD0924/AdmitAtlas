<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDictionaries, getSchools } from '@/api'
import { schoolTheme, tierPill, splitTiers, schoolInitial } from '@/utils/schoolTheme'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const provinceCode = ref('')
const tier = ref('')
const page = ref(1)
const size = 10
const total = ref(0)
const rows = ref([])
const provinces = ref([])
const searched = ref(false)
const error = ref('')

const filters = [
  { label: '全部', value: '' }, { label: '985', value: '985' },
  { label: '211', value: '211' }, { label: '双一流', value: '双一流' },
  { label: '省重点', value: '省重点' }, { label: '普通', value: '普通' }
]

const capabilities = [
  { span: 'lg:col-span-5', tag: '档案', title: '院校层次与属地', desc: '985 / 211 / 双一流、主管部门、所在地与办学性质，支持多条件组合检索。', pill: 'pill-sky' },
  { span: 'lg:col-span-4', tag: '目录', title: '专业与选科要求', desc: '专业组、专业目录与选科要求联动，资格过滤在匹配阶段自动执行。', pill: 'pill-violet' },
  { span: 'lg:col-span-3', tag: '趋势', title: '历年录取轨迹', desc: '近 3 年最低位次与计划数变化，位次是唯一跨年可比口径。', pill: 'pill-mint' }
]

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
// 每张卡片带上一套稳定主题色与语义化的层次标签，避免整页卡片「都是同一个白」。
const cards = computed(() => rows.value.map((s) => ({
  ...s,
  theme: schoolTheme(s),
  tiers: splitTiers(s.tierTags),
  initial: schoolInitial(s)
})))

function readQuery() {
  keyword.value = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  provinceCode.value = typeof route.query.provinceCode === 'string' ? route.query.provinceCode : ''
  tier.value = typeof route.query.tier === 'string' ? route.query.tier : ''
  const p = Number(route.query.page)
  page.value = Number.isInteger(p) && p > 0 ? p : 1
}

/** 把筛选条件写进 URL：从独立详情页返回时可无缝还原列表，链接也可直接分享。 */
function syncQuery(p = page.value) {
  const q = {}
  if (keyword.value.trim()) q.keyword = keyword.value.trim()
  if (provinceCode.value) q.provinceCode = provinceCode.value
  if (tier.value) q.tier = tier.value
  if (p > 1) q.page = String(p)
  router.replace({ name: 'schools', query: q })
}

async function search(nextPage = 1) {
  error.value = ''
  page.value = nextPage
  searched.value = true
  syncQuery(nextPage)
  try {
    const res = await getSchools({
      keyword: keyword.value.trim() || undefined,
      provinceCode: provinceCode.value || undefined,
      tier: tier.value || undefined,
      page: page.value, size
    })
    rows.value = res?.items ?? []
    total.value = res?.total ?? 0
  } catch (e) {
    error.value = e.message || '检索失败'
    rows.value = []
    total.value = 0
  }
}

function clear() {
  keyword.value = ''
  provinceCode.value = ''
  tier.value = ''
  page.value = 1
  error.value = ''
  search(1)
}

onMounted(async () => {
  readQuery()
  try {
    const d = await getDictionaries()
    provinces.value = d?.provinces ?? []
  } catch { /* 字典不可用时下拉为空，检索仍可用 */ }
  await search(page.value)
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p v-reveal="0" class="eyebrow">School archive / 资源中枢</p>
        <h1 v-reveal="1" class="mt-2 text-[22px] font-semibold tracking-[-0.02em]">全国高校，一张可检索的地图</h1>
        <p v-reveal="2" class="mt-2 max-w-[76ch] text-[12px] leading-6 muted">
          按层次、地域检索院校档案与历年录取趋势；点击任意院校卡片进入独立详情页，数据版本、来源与更新时间随结果一同公示。
        </p>
      </div>
      <div v-reveal="2" class="btn-group">
        <RouterLink to="/" class="btn btn-slate">← 返回概览</RouterLink>
        <RouterLink to="/match" class="btn btn-teal"><span class="btn-glyph">◎</span> 智能匹配</RouterLink>
      </div>
    </div>

    <div v-reveal="0" class="glass p-5 md:p-6">
      <div class="school-filter">
        <input v-model="keyword" class="field" placeholder="输入院校名称 / 代码 / 城市"
          aria-label="院校检索" @keyup.enter="search(1)" />
        <select v-model="provinceCode" class="field" aria-label="院校属地">
          <option value="">全部属地</option>
          <option v-for="p in provinces" :key="p.provinceCode" :value="p.provinceCode">{{ p.provinceName }}</option>
        </select>
        <button class="btn btn-blue" type="button" @click="search(1)">
          <span class="btn-glyph">⌕</span> 检索 <span class="link-arrow">→</span>
        </button>
        <button class="btn btn-rose" type="button" @click="clear"><span class="btn-glyph">✕</span> 清空</button>
      </div>
      <div class="mt-4 flex flex-wrap gap-2">
        <button v-for="f in filters" :key="f.label" type="button" class="chip"
          :class="{ 'is-active': tier === f.value }" @click="tier = f.value; search(1)">{{ f.label }}</button>
      </div>

      <div v-if="error" class="mt-4 rounded-lg px-3 py-2 text-[12px]" style="background: var(--rose-bg); color: var(--rose)">{{ error }}</div>

      <div v-if="cards.length" class="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <RouterLink v-for="c in cards" :key="c.schoolCode" class="school-card"
          :data-school-theme="c.theme"
          :to="{ name: 'school-detail', params: { code: c.schoolCode }, query: route.query }">
          <span class="school-card-head">
            <span class="school-card-badge" aria-hidden="true">{{ c.initial }}</span>
            <span class="school-card-title">
              <span class="school-card-code">{{ c.schoolCode }}</span>
              <span class="school-card-name">{{ c.schoolName }}</span>
            </span>
            <span class="school-card-level">{{ c.schoolLevel || '本科' }}</span>
          </span>
          <span class="school-card-meta">
            <span>所在地 · {{ c.city || '—' }}</span>
            <span>类型 · {{ c.schoolType || '—' }}</span>
            <span v-if="c.supervisor">主管 · {{ c.supervisor }}</span>
          </span>
          <span class="school-card-tags">
            <span v-for="t in c.tiers" :key="t" class="pill" :class="tierPill(t)">{{ t }}</span>
            <span v-if="!c.tiers.length" class="faint text-[10px]">暂无层次标签</span>
          </span>
          <span class="school-card-foot">
            <span>{{ c.intro ? c.intro.slice(0, 28) + (c.intro.length > 28 ? '…' : '') : '点击查看完整档案' }}</span>
            <span class="school-card-cta">查看详情 <span class="link-arrow">→</span></span>
          </span>
        </RouterLink>
      </div>
      <div v-else class="mt-5 rounded-xl border border-hairline py-10 text-center text-[12px] faint">
        {{ searched ? '没有匹配的院校，试试放宽条件' : '正在加载院校档案…' }}
      </div>

      <div class="mt-4 flex flex-wrap items-center justify-between gap-3 text-[11px] faint">
        <span>共 {{ total }} 所院校 · 第 {{ page }} / {{ totalPages }} 页 · 每张卡片按院校固定配色</span>
        <div class="btn-group">
          <button class="btn btn-slate" :disabled="page <= 1" @click="search(page - 1)">← 上一页</button>
          <button class="btn btn-blue" :disabled="page >= totalPages" @click="search(page + 1)">下一页 →</button>
        </div>
      </div>
    </div>

    <div class="grid gap-4 lg:grid-cols-12">
      <div v-for="(c, i) in capabilities" :key="c.title" v-reveal="i + 1" :class="['glass lift p-5', c.span]">
        <span class="pill" :class="c.pill">{{ c.tag }}</span>
        <h3 class="mt-4 text-[14px] font-semibold">{{ c.title }}</h3>
        <p class="mt-2 text-[12px] leading-6 muted">{{ c.desc }}</p>
      </div>
    </div>

    <p v-reveal="0" class="text-center text-[11px] faint">
      当前为演示数据集（合成值），非真实录取分数；最终以各省教育考试院公布为准
    </p>
  </div>
</template>
