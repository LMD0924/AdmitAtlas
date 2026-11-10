<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mode = ref('login')
const username = ref('')
const password = ref('')
const displayName = ref('')
const provinceCode = ref('42')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const showPassword = ref(false)

// F-S-01：登录冻结倒计时。后端按用户名维度计数，前端同样按账号隔离，
// 刷新页面后由下一次请求重新校准，避免只靠内存状态被绕过。
const lockMap = ref({})
const now = ref(Date.now())
const currentLock = computed(() => lockMap.value[username.value.trim()] || 0)
const remainingSeconds = computed(() =>
  Math.max(0, Math.ceil((currentLock.value - now.value) / 1000)))
const locked = computed(() => remainingSeconds.value > 0)
const lockText = computed(() => {
  const s = remainingSeconds.value
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
})
const ticker = setInterval(() => {
  now.value = Date.now()
  const next = { ...lockMap.value }
  let changed = false
  for (const [u, until] of Object.entries(next)) {
    if (until && now.value >= until) { delete next[u]; changed = true }
  }
  if (changed) {
    lockMap.value = next
    if (!locked.value) {
      error.value = ''
      notice.value = '冻结已解除，可以重新登录了'
    }
  }
}, 1000)
onBeforeUnmount(() => clearInterval(ticker))

const demos = [
  { label: '学生账号', username: 'student', password: 'Student@123456', cls: 'btn-mint' },
  { label: '管理员账号', username: 'admin', password: 'Admin@123456', cls: 'btn-violet' },
  { label: 'P1 演示账号', username: 'demo', password: 'Demo@123456', cls: 'btn-slate' }
]

const adminHint = computed(() => route.query.reason === 'admin')
const passwordHint = computed(() => route.query.reason === 'password')

function fill(demo) {
  mode.value = 'login'
  username.value = demo.username
  password.value = demo.password
  error.value = ''
  notice.value = `已填入${demo.label}，点击「登录」即可`
}

async function submit() {
  error.value = ''
  notice.value = ''
  if (locked.value) {
    error.value = `账号仍处于冻结期，请在 ${lockText.value} 后重试`
    return
  }
  if (!username.value.trim() || !password.value) {
    error.value = '请填写用户名和密码'
    return
  }
  busy.value = true
  try {
    const payload = {
      username: username.value.trim(),
      password: password.value
    }
    const user = mode.value === 'login'
      ? await auth.login(payload.username, payload.password)
      : await auth.register({ ...payload, displayName: displayName.value, provinceCode: provinceCode.value })
    const next = { ...lockMap.value }
    delete next[username.value.trim()]
    lockMap.value = next
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    router.push(redirect || (user.role === 'ADMIN' ? '/admin' : '/score'))
  } catch (e) {
    if (e.code === 423 && e.data?.lockedUntil) {
      lockMap.value = { ...lockMap.value, [username.value.trim()]: Number(e.data.lockedUntil) }
      now.value = Date.now()
    }
    error.value = e.message || '操作失败，请重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="mx-auto max-w-[1000px]">
    <div class="mb-9 text-center" v-reveal="0">
      <p class="eyebrow">ACCOUNT · 身份入口</p>
      <h1 class="hand-title mt-3">{{ mode === 'login' ? '欢迎回来' : '创建学生账号' }}</h1>
      <p class="muted mx-auto mt-3 max-w-[560px] text-[13px] leading-7">
        管理员维护数据与规则，学生录入成绩并获得志愿建议。游客无需登录即可浏览首页与院校库。
      </p>
    </div>

    <div class="grid gap-5 lg:grid-cols-[1fr_1.15fr]">
      <!-- 左：身份说明与演示账号 -->
      <div class="glass rounded-2xl p-6 md:p-7" v-reveal="1">
        <h2 class="section-title">两种身份</h2>
        <div class="mt-5 space-y-4">
          <div class="rounded-xl border border-hairline p-4">
            <span class="pill pill-mint">学生 STUDENT</span>
            <p class="muted mt-3 text-[12px] leading-6">
              录入成绩、换算位次、智能匹配、编辑并导出志愿方案。数据按账号隔离，只能看到自己的档案。
            </p>
          </div>
          <div class="rounded-xl border border-hairline p-4">
            <span class="pill pill-violet">管理员 ADMIN</span>
            <p class="muted mt-3 text-[12px] leading-6">
              管理账号与角色、维护志愿规则与院校档案、切换公示数据版本、查看全量操作审计。也可使用学生端功能。
            </p>
          </div>
        </div>

        <div class="mt-6 border-t border-hairline pt-5">
          <p class="field-label mb-2">演示账号（一键填入）</p>
          <div class="btn-group">
            <button
              v-for="d in demos"
              :key="d.username"
              type="button"
              class="btn"
              :class="d.cls"
              @click="fill(d)"
            >
              {{ d.label }}
            </button>
          </div>
          <p class="faint mt-3 text-[11px] leading-5">
            密码仅用于本地演示，生产环境必须通过环境变量重置并删除内置账号。
          </p>
        </div>
      </div>

      <!-- 右：表单 -->
      <div class="glass-strong rounded-2xl p-6 md:p-7" v-reveal="2">
        <div class="mb-6 flex items-center gap-2">
          <button
            type="button"
            class="btn"
            :class="mode === 'login' ? 'btn-blue' : 'btn-slate'"
            :aria-pressed="mode === 'login'"
            @click="mode = 'login'; error = ''"
          >登录</button>
          <button
            type="button"
            class="btn"
            :class="mode === 'register' ? 'btn-mint' : 'btn-slate'"
            :aria-pressed="mode === 'register'"
            @click="mode = 'register'; error = ''"
          >注册学生</button>
          <span class="muted ml-auto text-[12px]">{{ mode === 'login' ? '已有账号' : '仅可注册学生' }}</span>
        </div>

        <div v-if="adminHint" class="mb-4 rounded-xl border border-hairline bg-[var(--amber-bg)] px-4 py-3 text-[12px] leading-6 text-[var(--amber)]">
          该页面仅管理员可用，请用管理员账号登录后返回。
        </div>
        <div v-if="passwordHint" class="mb-4 rounded-xl border border-hairline bg-[var(--mint-bg)] px-4 py-3 text-[12px] leading-6 text-[var(--mint)]">
          密码修改成功，旧会话已失效。请用新密码登录，登录后会自动返回个人中心。
        </div>

        <form class="space-y-4" @submit.prevent="submit">
          <div>
            <label class="field-label" for="aa-username">用户名</label>
            <input id="aa-username" v-model="username" class="field" type="text" autocomplete="username" placeholder="3~32 位字母、数字、_ . -" />
          </div>
          <div>
            <label class="field-label" for="aa-password">密码</label>
            <div class="password-wrap">
              <input id="aa-password" v-model="password" class="field password-input"
                :type="showPassword ? 'text' : 'password'"
                :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
                placeholder="8~64 位" />
              <button class="password-toggle" type="button"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                :aria-pressed="showPassword" @click="showPassword = !showPassword">
                <svg v-if="!showPassword" viewBox="0 0 24 24" width="17" height="17" fill="none"
                  stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M2 12s3.6-6.5 10-6.5S22 12 22 12s-3.6 6.5-10 6.5S2 12 2 12Z" />
                  <circle cx="12" cy="12" r="2.6" />
                </svg>
                <svg v-else viewBox="0 0 24 24" width="17" height="17" fill="none"
                  stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M4 4l16 16" />
                  <path d="M9.9 5.7A10.6 10.6 0 0 1 12 5.5c6.4 0 10 6.5 10 6.5a17 17 0 0 1-2.5 3.2M6.3 6.8A17.4 17.4 0 0 0 2 12s3.6 6.5 10 6.5a10.5 10.5 0 0 0 3.2-.5" />
                  <path d="M10.1 10.1a2.7 2.7 0 0 0 3.8 3.8" />
                </svg>
              </button>
            </div>
          </div>
          <template v-if="mode === 'register'">
            <div>
              <label class="field-label" for="aa-display">昵称（可选）</label>
              <input id="aa-display" v-model="displayName" class="field" type="text" placeholder="展示在导航栏" />
            </div>
            <div>
              <label class="field-label" for="aa-province">生源地省份代码</label>
              <input id="aa-province" v-model="provinceCode" class="field" type="text" placeholder="42" />
            </div>
          </template>

          <div v-if="locked" class="rounded-xl border border-hairline px-4 py-3 text-[12px] leading-6"
            style="background: var(--amber-bg); color: var(--amber)">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <span class="font-semibold">账号已临时冻结</span>
              <span class="tabular-nums font-semibold">{{ lockText }}</span>
            </div>
            <p class="mt-1">连续输错 5 次，30 分钟内无法登录。倒计时结束后自动恢复，也可联系管理员重置密码立即解锁。</p>
          </div>
          <p v-else-if="error" class="rounded-lg border border-hairline bg-[var(--rose-bg)] px-3 py-2 text-[12px] text-[var(--rose)]">{{ error }}</p>
          <p v-if="notice" class="text-[12px] text-[var(--mint)]">{{ notice }}</p>

          <div class="btn-group pt-2">
            <button class="btn" :class="mode === 'login' ? 'btn-blue' : 'btn-mint'" type="submit" :disabled="busy || locked">
              <span class="btn-glyph">→</span> {{ locked ? `冻结中 ${lockText}` : (busy ? '处理中…' : (mode === 'login' ? '登录' : '注册并登录')) }}
            </button>
            <RouterLink to="/" class="btn btn-slate"><span class="btn-glyph">⌂</span> 先以游客身份浏览</RouterLink>
          </div>
        </form>

        <p class="faint mt-6 border-t border-hairline pt-4 text-[11px] leading-5">
          登录态使用 HMAC-SHA256 签名的 JWT，退出、停用或重置密码会使已签发令牌立即失效。
          当前站点使用合成演示数据，最终以各省教育考试院公布为准。
        </p>
      </div>
    </div>
  </section>
</template>
