<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getAdminSchools, updateAdminSchool } from '@/api'

const items = ref([])
const total = ref(0)
const keyword = ref('')
const page = ref(1)
const size = 8
const error = ref('')
const msg = ref('')
const editing = ref('')
const draft = reactive({
  schoolName: '', supervisor: '', provinceCode: '', city: '',
  tierTags: '', schoolType: '', schoolLevel: '', intro: ''
})

async function load() {
  error.value = ''
  try {
    const data = await getAdminSchools({ keyword: keyword.value, page: page.value, size })
    items.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    error.value = e.message || '加载院校失败'
  }
}

function edit(s) {
  editing.value = s.schoolCode
  Object.assign(draft, {
    schoolName: s.schoolName || '', supervisor: s.supervisor || '',
    provinceCode: s.provinceCode || '', city: s.city || '',
    tierTags: s.tierTags || '', schoolType: s.schoolType || '',
    schoolLevel: s.schoolLevel || '', intro: s.intro || ''
  })
  msg.value = ''
}

async function save(code) {
  error.value = ''
  try {
    await updateAdminSchool(code, { ...draft })
    msg.value = `院校 ${code} 已保存并写入审计日志`
    editing.value = ''
    await load()
  } catch (e) {
    error.value = e.message
  }
}

function go(delta) {
  page.value = Math.max(1, page.value + delta)
  load()
}

onMounted(load)
</script>

<template>
  <div class="glass rounded-2xl p-5" v-reveal="0">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <p class="eyebrow">F-B-08 · 院校档案</p>
        <h2 class="section-title mt-1">院校档案维护</h2>
      </div>
      <span class="pill pill-neutral">共 {{ total }} 所</span>
    </div>

    <div class="mt-4 flex flex-wrap items-end gap-2">
      <div class="min-w-[200px] flex-1">
        <label class="field-label">搜索院校 / 城市 / 代码</label>
        <input v-model="keyword" class="field" placeholder="回车搜索" @keyup.enter="page = 1; load()" />
      </div>
      <button class="btn btn-violet" type="button" @click="page = 1; load()"><span class="btn-glyph">⌕</span> 查询</button>
      <button class="btn btn-slate" type="button" :disabled="page <= 1" @click="go(-1)">上一页</button>
      <button class="btn btn-slate" type="button" :disabled="page * size >= total" @click="go(1)">下一页</button>
      <span class="muted text-[12px]">第 {{ page }} 页</span>
    </div>

    <p v-if="error" class="mt-3 rounded-lg border border-hairline bg-[var(--rose-bg)] px-3 py-2 text-[12px] text-[var(--rose)]">{{ error }}</p>
    <p v-if="msg" class="mt-3 text-[12px] text-[var(--mint)]">{{ msg }}</p>

    <div class="mt-4 overflow-x-auto rounded-xl border border-hairline">
      <table class="data-table">
        <thead>
          <tr><th>代码</th><th>院校名称</th><th>属地</th><th>城市</th><th>层次标签</th><th>类型</th><th>操作</th></tr>
        </thead>
        <tbody>
          <template v-for="s in items" :key="s.schoolCode">
            <tr>
              <td class="font-mono text-[11px]">{{ s.schoolCode }}</td>
              <td>{{ s.schoolName }}</td>
              <td>{{ s.provinceCode || '—' }}</td>
              <td>{{ s.city || '—' }}</td>
              <td><span class="pill pill-sky">{{ s.tierTags || '—' }}</span></td>
              <td class="text-[11px]">{{ s.schoolType || '—' }}</td>
              <td>
                <button class="btn btn-amber" type="button" @click="edit(s)"><span class="btn-glyph">✎</span> 编辑</button>
              </td>
            </tr>
            <tr v-if="editing === s.schoolCode">
              <td colspan="7">
                <div class="grid gap-3 py-2 sm:grid-cols-2 lg:grid-cols-4">
                  <div><label class="field-label">院校名称</label><input v-model="draft.schoolName" class="field" /></div>
                  <div><label class="field-label">主管部门</label><input v-model="draft.supervisor" class="field" /></div>
                  <div><label class="field-label">属地省份</label><input v-model="draft.provinceCode" class="field" /></div>
                  <div><label class="field-label">城市</label><input v-model="draft.city" class="field" /></div>
                  <div><label class="field-label">层次标签</label><input v-model="draft.tierTags" class="field" /></div>
                  <div><label class="field-label">办学类型</label><input v-model="draft.schoolType" class="field" /></div>
                  <div><label class="field-label">办学层次</label><input v-model="draft.schoolLevel" class="field" /></div>
                  <div><label class="field-label">简介</label><input v-model="draft.intro" class="field" /></div>
                </div>
                <div class="btn-group">
                  <button class="btn btn-mint" type="button" @click="save(s.schoolCode)"><span class="btn-glyph">✓</span> 保存</button>
                  <button class="btn btn-slate" type="button" @click="editing = ''">取消</button>
                </div>
              </td>
            </tr>
          </template>
          <tr v-if="!items.length"><td colspan="7" class="faint">没有匹配的院校</td></tr>
        </tbody>
      </table>
    </div>
    <p class="faint mt-3 text-[11px]">修改会即时影响院校库与匹配结果展示；演示阶段所有院校均为虚构合成数据。</p>
  </div>
</template>
