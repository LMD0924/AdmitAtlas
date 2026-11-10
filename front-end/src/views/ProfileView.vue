<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  changePassword, getDictionaries, getFavorites, getProfile, updateProfile, uploadAvatar
} from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useEngagementStore } from '@/stores/engagement'
import { tierPill, splitTiers } from '@/utils/schoolTheme'

const auth = useAuthStore()
const engagement = useEngagementStore()
const favoriteItems = ref([])
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const saving = ref(false)
const changing = ref(false)
const profile = ref(null)
const stats = ref({})
const latestScore = ref(null)
const plans = ref([])
const tasks = ref([])
const provinces = ref([])
const dataVersion = ref('')
const error = ref('')
const msg = ref('')
const pwdError = ref('')

const avatarInput = ref(null)
const uploadingAvatar = ref(false)

const form = reactive({ displayName: '', provinceCode: '' })
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const initial = computed(() => (profile.value?.displayName || auth.displayName || 'A').slice(0, 1).toUpperCase())
const isAdmin = computed(() => profile.value?.role === 'ADMIN')

const metrics = computed(() => [
  { label: '成绩档案', value: stats.value.scoreCount || 0, glyph: '✎', cls: 'is-mint' },
  { label: '匹配任务', value: stats.value.taskCount || 0, glyph: '◎', cls: 'is-sky' },
  { label: '志愿方案', value: stats.value.planCount || 0, glyph: '▤', cls: 'is-violet' },
  { label: '志愿条目', value: stats.value.itemCount || 0, glyph: '≡', cls: 'is-slate' }
])

function fmt(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

function comboText(list) {
  return Array.isArray(list) && list.length ? list.join(' · ') : '—'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [data, dict] = await Promise.all([getProfile(), getDictionaries()])
    profile.value = data.profile || null
    stats.value = data.stats || {}
    latestScore.value = data.latestScore || null
    plans.value = data.plans || []
    tasks.value = data.tasks || []
    provinces.value = dict.provinces || []
    dataVersion.value = data.dataVersion || ''
    form.displayName = profile.value?.displayName || ''
    form.provinceCode = profile.value?.provinceCode || '42'
  } catch (e) {
    error.value = e.message || '加载个人资料失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.displayName.trim()) {
    msg.value = ''
    error.value = '昵称不能为空'
    return
  }
  saving.value = true
  error.value = ''
  msg.value = ''
  try {
    const user = await updateProfile({
      displayName: form.displayName.trim(),
      provinceCode: form.provinceCode
    })
    auth.applyUser(user)
    msg.value = '资料已保存，导航栏身份信息已同步'
    await load()
  } catch (e) {
    error.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function doChangePassword() {
  pwdError.value = ''
  if (!pwd.oldPassword || !pwd.newPassword) {
    pwdError.value = '请填写原密码与新密码'
    return
  }
  if (pwd.newPassword.length < 8) {
    pwdError.value = '新密码至少 8 位'
    return
  }
  if (pwd.newPassword !== pwd.confirm) {
    pwdError.value = '两次输入的新密码不一致'
    return
  }
  changing.value = true
  try {
    await changePassword({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    // token_version 已递增，旧令牌立即失效；清本地会话后回到登录页。
    auth.clear()
    router.push({ path: '/login', query: { redirect: '/profile', reason: 'password' } })
  } catch (e) {
    pwdError.value = e.message || '修改失败'
  } finally {
    changing.value = false
  }
}

function triggerAvatar() {
  avatarInput.value?.click()
}

async function onAvatarPicked(e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploadingAvatar.value = true
  error.value = ''
  try {
    const res = await uploadAvatar(file)
    const url = res?.url || res?.avatarUrl || ''
    if (url && profile.value) {
      profile.value.avatarUrl = url
      auth.setAvatar?.(url)
    }
  } catch (err) {
    error.value = err.message || '头像上传失败'
  } finally {
    uploadingAvatar.value = false
    e.target.value = ''
  }
}

/** F-C-19：我的收藏。列表接口按账号隔离，取消收藏后同步对比篮以外的状态。 */
async function loadFavorites() {
  try {
    const data = await getFavorites()
    favoriteItems.value = data?.items ?? []
    await engagement.loadFavorites(true)
  } catch { favoriteItems.value = [] }
}

async function unfavorite(item) {
  const res = await engagement.toggleFavorite(item.schoolCode)
  if (res.ok) await loadFavorites()
}

onMounted(async () => {
  await load()
  await loadFavorites()
})
</script>

<template>
  <section class="mx-auto max-w-[1120px]">
    <div class="mb-8 text-center" v-reveal="0">
      <p class="eyebrow">ACCOUNT · 个人中心</p>
      <h1 class="hand-title mt-3">我的档案</h1>
      <p class="muted mx-auto mt-3 max-w-[620px] text-[13px] leading-7">
        成绩、匹配任务与志愿方案都按账号隔离，只有你自己能查看。这里可以维护昵称与生源地，或更新登录密码。
      </p>
    </div>

    <p v-if="route.query.reason === 'password'" class="mb-5 rounded-xl border border-hairline bg-[var(--mint-bg)] px-4 py-3 text-[12px] text-[var(--mint)]">
      密码已更新，请使用新密码重新登录。
    </p>

    <div v-if="loading" class="glass rounded-2xl p-10 text-center muted text-[13px]">正在读取个人资料…</div>
    <p v-else-if="error && !profile" class="rounded-xl border border-hairline bg-[var(--rose-bg)] px-4 py-3 text-[12px] text-[var(--rose)]">{{ error }}</p>

    <template v-if="!loading && profile">
      <!-- 身份卡 -->
      <div class="glass-strong rounded-2xl p-6 md:p-7" v-reveal="1">
        <div class="flex flex-wrap items-center gap-5">
          <div class="relative inline-flex">
            <img v-if="profile.avatarUrl" :src="profile.avatarUrl" class="profile-avatar-img" alt="头像" />
            <span v-else class="profile-avatar">{{ initial }}</span>
            <button class="avatar-upload-btn" type="button" @click="triggerAvatar()"
              :disabled="uploadingAvatar" :title="uploadingAvatar ? '上传中…' : '上传头像'">
              {{ uploadingAvatar ? '…' : '🖼' }}
            </button>
            <input ref="avatarInput" type="file" accept="image/*" class="hidden" @change="onAvatarPicked" />
          </div>
          <div class="min-w-[180px] flex-1">
            <div class="flex flex-wrap items-center gap-2">
              <h2 class="text-[20px] font-semibold tracking-tight">{{ profile.displayName }}</h2>
              <span class="pill" :class="isAdmin ? 'pill-violet' : 'pill-mint'">
                {{ profile.roleLabel }} {{ profile.role }}
              </span>
              <span class="pill" :class="profile.accountStatus === 'ACTIVE' ? 'pill-teal' : 'pill-rose'">
                {{ profile.statusLabel }}
              </span>
            </div>
            <p class="muted mt-2 text-[12px]">
              账号 <span class="font-mono">{{ profile.username }}</span> · ID <span class="font-mono">{{ profile.id }}</span>
              · 生源地 {{ profile.provinceName }}（{{ profile.provinceCode }}）
            </p>
            <p class="faint mt-1 text-[11px]">
              注册于 {{ fmt(profile.createdAt) }} · 最近登录 {{ fmt(profile.lastLoginAt) }}
              · 当前数据版本 <span class="font-mono">{{ dataVersion }}</span>
            </p>
          </div>
          <div class="btn-group">
            <RouterLink to="/score" class="btn btn-mint"><span class="btn-glyph">✎</span> 录入成绩</RouterLink>
            <RouterLink to="/match" class="btn btn-blue"><span class="btn-glyph">◎</span> 智能匹配</RouterLink>
            <RouterLink to="/plan" class="btn btn-amber"><span class="btn-glyph">▤</span> 志愿方案</RouterLink>
            <RouterLink v-if="isAdmin" to="/admin" class="btn btn-rose"><span class="btn-glyph">▦</span> 管理后台</RouterLink>
          </div>
        </div>
      </div>

      <!-- 我的数据 -->
      <div class="mt-6 glass rounded-2xl p-6" v-reveal="2">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p class="eyebrow">MY DATA</p>
            <h2 class="section-title mt-1">我的数据概览</h2>
          </div>
          <button class="btn btn-slate" type="button" :disabled="loading" @click="load">
            <span class="btn-glyph">↻</span> 刷新
          </button>
        </div>
        <div class="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
          <div v-for="m in metrics" :key="m.label" class="rounded-xl border border-hairline p-4">
            <span class="metric-circle" :class="m.cls">{{ m.glyph }}</span>
            <span class="stat-num mt-3 block" v-count="m.value">0</span>
            <span class="muted mt-1 block text-[11px]">{{ m.label }}</span>
          </div>
        </div>
        <p class="faint mt-4 text-[11px]">
          以上计数仅统计当前账号产生的数据；管理员也无法在个人中心看到他人档案。
        </p>
      </div>

      <!-- 资料设置 + 安全设置 -->
      <div class="mt-6 grid gap-5 lg:grid-cols-2">
        <div class="glass rounded-2xl p-6" v-reveal="3">
          <p class="eyebrow">PROFILE</p>
          <h2 class="section-title mt-1">资料设置</h2>
          <p class="muted mt-2 text-[12px] leading-6">
            用户名、角色与账号状态由管理员维护，此处仅可修改展示名与生源地省份。
          </p>
          <div class="mt-5 space-y-4">
            <div>
              <label class="field-label" for="pf-name">昵称</label>
              <input id="pf-name" v-model="form.displayName" class="field" type="text" maxlength="32" placeholder="展示在导航栏" />
            </div>
            <div>
              <label class="field-label" for="pf-province">生源地省份</label>
              <select id="pf-province" v-model="form.provinceCode" class="field">
                <option v-for="p in provinces" :key="p.provinceCode" :value="p.provinceCode">
                  {{ p.provinceName }}（{{ p.provinceCode }} · {{ p.examMode }}）
                </option>
              </select>
            </div>
            <div>
              <label class="field-label">用户名（不可修改）</label>
              <input class="field opacity-70" type="text" :value="profile.username" disabled />
            </div>
          </div>
          <p v-if="error" class="mt-3 rounded-lg border border-hairline bg-[var(--rose-bg)] px-3 py-2 text-[12px] text-[var(--rose)]">{{ error }}</p>
          <p v-if="msg" class="mt-3 text-[12px] text-[var(--mint)]">{{ msg }}</p>
          <div class="btn-group mt-5">
            <button class="btn btn-blue" type="button" :disabled="saving" @click="save">
              <span class="btn-glyph">✓</span> {{ saving ? '保存中…' : '保存资料' }}
            </button>
          </div>
        </div>

        <div class="glass rounded-2xl p-6" v-reveal="4">
          <p class="eyebrow">SECURITY</p>
          <h2 class="section-title mt-1">安全设置</h2>
          <p class="muted mt-2 text-[12px] leading-6">
            密码以 PBKDF2-HMAC-SHA256 加随机盐存储，平台不保存明文。修改成功后所有已登录会话立即失效，需要重新登录。
          </p>
          <div class="mt-5 space-y-4">
            <div>
              <label class="field-label" for="pf-old">原密码</label>
              <input id="pf-old" v-model="pwd.oldPassword" class="field" type="password" autocomplete="current-password" placeholder="当前登录密码" />
            </div>
            <div>
              <label class="field-label" for="pf-new">新密码</label>
              <input id="pf-new" v-model="pwd.newPassword" class="field" type="password" autocomplete="new-password" placeholder="8~64 位，且不能与原密码相同" />
            </div>
            <div>
              <label class="field-label" for="pf-confirm">确认新密码</label>
              <input id="pf-confirm" v-model="pwd.confirm" class="field" type="password" autocomplete="new-password" placeholder="再次输入新密码" />
            </div>
          </div>
          <p v-if="pwdError" class="mt-3 rounded-lg border border-hairline bg-[var(--rose-bg)] px-3 py-2 text-[12px] text-[var(--rose)]">{{ pwdError }}</p>
          <div class="btn-group mt-5">
            <button class="btn btn-rose" type="button" :disabled="changing" @click="doChangePassword">
              <span class="btn-glyph">↻</span> {{ changing ? '提交中…' : '修改密码并重新登录' }}
            </button>
          </div>
          <p class="faint mt-5 border-t border-hairline pt-4 text-[11px] leading-5">
            若忘记密码，请联系管理员在「用户与角色」中重置；演示环境请勿使用真实密码。
          </p>
        </div>
      </div>

      <!-- 最近成绩 -->
      <div class="mt-6 glass rounded-2xl p-6" v-reveal="5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p class="eyebrow">LATEST SCORE</p>
            <h2 class="section-title mt-1">最近成绩档案</h2>
          </div>
          <RouterLink to="/score" class="btn btn-mint"><span class="btn-glyph">＋</span> 新增成绩</RouterLink>
        </div>
        <div v-if="latestScore" class="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div class="rounded-xl border border-hairline p-4">
            <p class="field-label mb-1">总分</p><p class="stat-num">{{ latestScore.totalScore }}</p>
          </div>
          <div class="rounded-xl border border-hairline p-4">
            <p class="field-label mb-1">对应位次</p>
            <p class="stat-num">{{ latestScore.rankValue ?? '—' }}</p>
            <span v-if="latestScore.rankEstimated" class="pill pill-amber">估算值</span>
          </div>
          <div class="rounded-xl border border-hairline p-4">
            <p class="field-label mb-1">科类 / 考试模式</p>
            <p class="text-[13px]">{{ latestScore.category }} · {{ latestScore.examMode }}</p>
            <p class="faint mt-1 text-[11px]">{{ comboText(latestScore.subjectCombo) }}</p>
          </div>
          <div class="rounded-xl border border-hairline p-4">
            <p class="field-label mb-1">录入时间</p>
            <p class="text-[13px]">{{ fmt(latestScore.createdAt) }}</p>
            <p class="faint mt-1 text-[11px]">{{ latestScore.examYear }} 年 · {{ latestScore.provinceCode }}</p>
          </div>
        </div>
        <p v-else class="muted mt-5 rounded-xl border border-hairline p-5 text-[12px]">
          还没有成绩档案，先去「录入成绩」保存一份，系统会自动换算位次。
        </p>
      </div>

      <!-- 最近方案 / 任务 -->
      <div class="mt-6 grid gap-5 lg:grid-cols-2">
        <div class="glass rounded-2xl p-6" v-reveal="6">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <h2 class="section-title">最近志愿方案</h2>
            <RouterLink to="/plan" class="btn btn-amber"><span class="btn-glyph">▤</span> 打开志愿表</RouterLink>
          </div>
          <div class="mt-4 overflow-x-auto rounded-xl border border-hairline">
            <table class="data-table">
              <thead><tr><th>方案名称</th><th>志愿数</th><th>单位</th><th>创建时间</th></tr></thead>
              <tbody>
                <tr v-for="p in plans" :key="p.planId">
                  <td>{{ p.planName || '未命名方案' }}</td>
                  <td>{{ p.volunteerCount ?? '—' }}</td>
                  <td class="text-[11px]">{{ p.volunteerUnit || '—' }}</td>
                  <td class="text-[11px]">{{ fmt(p.createdAt) }}</td>
                </tr>
                <tr v-if="!plans.length"><td colspan="4" class="faint">还没有保存过方案</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="glass rounded-2xl p-6" v-reveal="7">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <h2 class="section-title">最近匹配任务</h2>
            <RouterLink to="/match" class="btn btn-blue"><span class="btn-glyph">◎</span> 开始匹配</RouterLink>
          </div>
          <div class="mt-4 overflow-x-auto rounded-xl border border-hairline">
            <table class="data-table">
              <thead><tr><th>任务 ID</th><th>省份 / 年份</th><th>状态</th><th>创建时间</th></tr></thead>
              <tbody>
                <tr v-for="t in tasks" :key="t.taskId">
                  <td class="font-mono text-[11px]">{{ t.taskId }}</td>
                  <td>{{ t.provinceCode }} / {{ t.examYear }}</td>
                  <td><span class="pill pill-teal">{{ t.status || t.taskStatus || '—' }}</span></td>
                  <td class="text-[11px]">{{ fmt(t.createdAt) }}</td>
                </tr>
                <tr v-if="!tasks.length"><td colspan="4" class="faint">还没有匹配记录</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="glass rounded-2xl p-6" v-reveal="8">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 class="section-title">我的收藏院校 <span class="pill pill-rose">{{ favoriteItems.length }}</span></h2>
          <RouterLink to="/schools" class="btn btn-blue"><span class="btn-glyph">⌕</span> 去院校库</RouterLink>
        </div>
        <div v-if="favoriteItems.length" class="mt-4 grid gap-3 sm:grid-cols-2">
          <div v-for="f in favoriteItems" :key="f.schoolCode"
            class="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-hairline px-3 py-2.5">
            <div class="min-w-0">
              <RouterLink :to="'/schools/' + f.schoolCode" class="text-[12px] font-semibold hover:underline">
                {{ f.schoolName || f.schoolCode }}
              </RouterLink>
              <div class="mt-1 flex flex-wrap gap-1">
                <span v-for="t in splitTiers(f.tierTags)" :key="t" class="pill" :class="tierPill(t)">{{ t }}</span>
                <span class="text-[10px] faint">{{ f.city || '—' }} · {{ f.schoolType || '—' }}</span>
              </div>
            </div>
            <div class="btn-group">
              <button class="btn btn-slate" type="button"
                @click="engagement.toggleCompare(f.schoolCode, f.schoolName)">加入对比</button>
              <button class="btn btn-rose" type="button" @click="unfavorite(f)">取消收藏</button>
            </div>
          </div>
        </div>
        <p v-else class="mt-4 text-[12px] faint">还没有收藏院校。在院校库卡片上点「收藏」即可加入这里。</p>
      </div>

      <p class="faint mt-6 text-center text-[11px] leading-5">
        当前站点使用合成演示数据（{{ dataVersion }}），所有结论仅供参考，最终以各省教育考试院公布数据为准。
      </p>
    </template>
  </section>
</template>
