<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  createAdminUser, getAdminUsers, resetUserPassword, setUserStatus
} from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const items = ref([])
const role = ref('')
const keyword = ref('')
const busy = ref(false)
const error = ref('')
const msg = ref('')
const showCreate = ref(false)
const resetFor = ref(null)
const resetPw = ref('')
const form = reactive({
  username: '', password: '', displayName: '', provinceCode: '42', role: 'STUDENT'
})

async function load() {
  busy.value = true
  error.value = ''
  try {
    const data = await getAdminUsers({ role: role.value, keyword: keyword.value })
    items.value = data.items || []
  } catch (e) {
    error.value = e.message || '加载用户失败'
  } finally {
    busy.value = false
  }
}

async function toggle(u) {
  msg.value = ''
  error.value = ''
  try {
    await setUserStatus(u.id, u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE')
    msg.value = `${u.username} 状态已更新，该账号已签发的登录令牌同时失效`
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function doReset(u) {
  if (resetPw.value.length < 8) {
    error.value = '新密码至少 8 位'
    return
  }
  error.value = ''
  try {
    await resetUserPassword(u.id, resetPw.value)
    msg.value = `${u.username} 的密码已重置，旧令牌全部失效`
    resetFor.value = null
    resetPw.value = ''
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function doCreate() {
  if (!form.username || !form.password) {
    error.value = '请填写用户名和密码'
    return
  }
  error.value = ''
  try {
    await createAdminUser({ ...form })
    msg.value = `账号 ${form.username} 创建成功`
    showCreate.value = false
    Object.assign(form, { username: '', password: '', displayName: '', provinceCode: '42', role: 'STUDENT' })
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>

<template>
  <div class="glass rounded-2xl p-5" v-reveal="0">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <p class="eyebrow">F-B-22</p>
        <h2 class="section-title mt-1">用户与角色</h2>
      </div>
      <div class="btn-group">
        <button class="btn btn-blue" type="button" @click="showCreate = !showCreate">
          <span class="btn-glyph">＋</span> {{ showCreate ? '收起' : '新建账号' }}
        </button>
        <button class="btn btn-slate" type="button" :disabled="busy" @click="load">
          <span class="btn-glyph">↻</span> 刷新
        </button>
      </div>
    </div>

    <div v-if="showCreate" class="mt-4 rounded-xl border border-hairline p-4">
      <p class="field-label mb-3">新建账号（可指定 STUDENT / ADMIN）</p>
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <div><label class="field-label">用户名</label><input v-model="form.username" class="field" placeholder="3~32 位" /></div>
        <div><label class="field-label">初始密码</label><input v-model="form.password" class="field" type="password" placeholder="8~64 位" /></div>
        <div><label class="field-label">昵称</label><input v-model="form.displayName" class="field" placeholder="可选" /></div>
        <div><label class="field-label">省份代码</label><input v-model="form.provinceCode" class="field" placeholder="42" /></div>
        <div>
          <label class="field-label">角色</label>
          <select v-model="form.role" class="field">
            <option value="STUDENT">STUDENT 学生</option>
            <option value="ADMIN">ADMIN 管理员</option>
          </select>
        </div>
      </div>
      <button class="btn btn-mint mt-3" type="button" @click="doCreate"><span class="btn-glyph">✓</span> 确认创建</button>
    </div>

    <div class="mt-4 flex flex-wrap items-end gap-2">
      <div class="min-w-[180px] flex-1">
        <label class="field-label">搜索用户名 / 昵称</label>
        <input v-model="keyword" class="field" placeholder="输入关键词后回车" @keyup.enter="load" />
      </div>
      <div>
        <label class="field-label">角色</label>
        <select v-model="role" class="field" @change="load">
          <option value="">全部</option>
          <option value="STUDENT">学生</option>
          <option value="ADMIN">管理员</option>
        </select>
      </div>
      <button class="btn btn-violet" type="button" @click="load"><span class="btn-glyph">⌕</span> 查询</button>
    </div>

    <p v-if="error" class="mt-3 rounded-lg border border-hairline bg-[var(--rose-bg)] px-3 py-2 text-[12px] text-[var(--rose)]">{{ error }}</p>
    <p v-if="msg" class="mt-3 text-[12px] text-[var(--mint)]">{{ msg }}</p>

    <div class="mt-4 overflow-x-auto rounded-xl border border-hairline">
      <table class="data-table">
        <thead>
          <tr><th>ID</th><th>用户名</th><th>昵称</th><th>角色</th><th>状态</th><th>最近登录</th><th>操作</th></tr>
        </thead>
        <tbody>
          <template v-for="u in items" :key="u.id">
            <tr>
              <td class="font-mono text-[11px]">{{ u.id }}</td>
              <td>{{ u.username }}</td>
              <td>{{ u.displayName || '—' }}</td>
              <td><span class="pill" :class="u.role === 'ADMIN' ? 'pill-violet' : 'pill-mint'">{{ u.role }}</span></td>
              <td><span class="pill" :class="u.status === 'ACTIVE' ? 'pill-teal' : 'pill-rose'">{{ u.status }}</span></td>
              <td class="text-[11px]">{{ u.lastLoginAt || '—' }}</td>
              <td>
                <div class="btn-group">
                  <button
                    class="btn btn-slate"
                    type="button"
                    :disabled="u.id === auth.user?.id"
                    :title="u.id === auth.user?.id ? '不能停用当前登录账号' : ''"
                    @click="toggle(u)"
                  >{{ u.status === 'ACTIVE' ? '停用' : '启用' }}</button>
                  <button class="btn btn-amber" type="button" @click="resetFor = resetFor === u.id ? null : u.id; resetPw = ''">重置密码</button>
                </div>
              </td>
            </tr>
            <tr v-if="resetFor === u.id">
              <td colspan="7">
                <div class="flex flex-wrap items-center gap-2 py-1">
                  <span class="muted text-[12px]">为 {{ u.username }} 设置新密码：</span>
                  <input v-model="resetPw" class="field max-w-[220px]" type="password" placeholder="至少 8 位" />
                  <button class="btn btn-rose" type="button" @click="doReset(u)"><span class="btn-glyph">✓</span> 确认重置</button>
                </div>
              </td>
            </tr>
          </template>
          <tr v-if="!items.length"><td colspan="7" class="faint">没有匹配的账号</td></tr>
        </tbody>
      </table>
    </div>
    <p class="faint mt-3 text-[11px]">共 {{ items.length }} 个账号 · 列表不返回密码哈希 · 停用 / 重置密码会使该账号令牌立即失效</p>
  </div>
</template>
