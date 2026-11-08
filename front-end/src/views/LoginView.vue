<script setup>
import { computed, ref } from 'vue'
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
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    router.push(redirect || (user.role === 'ADMIN' ? '/admin' : '/score'))
  } catch (e) {
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
            <input id="aa-password" v-model="password" class="field" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="8~64 位" />
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

          <p v-if="error" class="rounded-lg border border-hairline bg-[var(--rose-bg)] px-3 py-2 text-[12px] text-[var(--rose)]">{{ error }}</p>
          <p v-if="notice" class="text-[12px] text-[var(--mint)]">{{ notice }}</p>

          <div class="btn-group pt-2">
            <button class="btn" :class="mode === 'login' ? 'btn-blue' : 'btn-mint'" type="submit" :disabled="busy">
              <span class="btn-glyph">→</span> {{ busy ? '处理中…' : (mode === 'login' ? '登录' : '注册并登录') }}
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
