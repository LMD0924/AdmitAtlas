<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import {
  getAdminSchools, updateAdminSchool, saveSchoolProfile,
  getSchoolProfile, uploadSchoolImage
} from '@/api'

const items = ref([])
const total = ref(0)
const keyword = ref('')
const page = ref(1)
const size = 8
const error = ref('')
const msg = ref('')
const editing = ref('')
const isNew = ref(false)
const profileDraft = ref('')
const showPreview = ref(false)
const imageInput = ref(null)
const uploading = ref(false)

const draft = reactive({
  schoolCode: '', schoolName: '', supervisor: '', provinceCode: '', city: '',
  tierTags: '', schoolType: '', schoolLevel: '', intro: ''
})

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const renderedProfile = computed(() => md.render(profileDraft.value || ''))

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
  isNew.value = false
  editing.value = s.schoolCode
  Object.assign(draft, {
    schoolCode: s.schoolCode || '', schoolName: s.schoolName || '', supervisor: s.supervisor || '',
    provinceCode: s.provinceCode || '', city: s.city || '',
    tierTags: s.tierTags || '', schoolType: s.schoolType || '',
    schoolLevel: s.schoolLevel || '', intro: s.intro || ''
  })
  profileDraft.value = ''
  showPreview.value = false
  msg.value = ''
  loadProfile(s.schoolCode)
}

async function loadProfile(code) {
  try {
    const p = await getSchoolProfile(code)
    profileDraft.value = p?.profileMarkdown || ''
  } catch { profileDraft.value = '' }
}

function createNew() {
  isNew.value = true
  editing.value = 'new'
  Object.assign(draft, {
    schoolCode: '', schoolName: '', supervisor: '', provinceCode: '', city: '',
    tierTags: '', schoolType: '', schoolLevel: '', intro: ''
  })
  profileDraft.value = ''
  showPreview.value = false
  msg.value = ''
}

async function save(code) {
  error.value = ''
  try {
    if (isNew.value) {
      if (!draft.schoolCode.trim()) throw new Error('院校代码不能为空')
      if (!draft.schoolName.trim()) throw new Error('院校名称不能为空')
    }
    const target = isNew.value ? draft.schoolCode.trim() : code
    await updateAdminSchool(target, {
      schoolName: draft.schoolName, supervisor: draft.supervisor, provinceCode: draft.provinceCode,
      city: draft.city, tierTags: draft.tierTags, schoolType: draft.schoolType,
      schoolLevel: draft.schoolLevel, intro: draft.intro
    })
    await saveSchoolProfile(target, profileDraft.value || '')
    msg.value = isNew.value
      ? `院校 ${target} 已创建并写入审计日志`
      : `院校 ${target} 已保存并写入审计日志`
    editing.value = ''
    isNew.value = false
    await load()
  } catch (e) {
    error.value = e.message
  }
}

function triggerImage() {
  imageInput.value?.click()
}

async function onImagePicked(e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const res = await uploadSchoolImage(file)
    const url = res?.url || ''
    if (url) profileDraft.value += `\n![${file.name}](${url})\n`
  } catch (err) {
    error.value = err.message || '图片上传失败'
  } finally {
    uploading.value = false
    e.target.value = ''
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
      <div class="flex items-center gap-2">
        <span class="pill pill-neutral">共 {{ total }} 所</span>
        <button class="btn btn-mint" type="button" @click="createNew()"><span class="btn-glyph">＋</span> 新建院校</button>
      </div>
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

    <!-- 编辑 / 新建面板 -->
    <div v-if="editing" class="mt-4 rounded-xl border border-hairline p-4">
      <div class="mb-3 flex items-center justify-between">
        <h3 class="text-[14px] font-semibold">{{ isNew ? '新建院校' : '编辑院校' }} · {{ draft.schoolCode || '（未填代码）' }}</h3>
        <button class="btn btn-slate btn-sm" type="button" @click="editing = ''; isNew = false">关闭</button>
      </div>

      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div><label class="field-label">院校代码</label><input v-model="draft.schoolCode" class="field" :disabled="!isNew" placeholder="如 10001" /></div>
        <div><label class="field-label">院校名称</label><input v-model="draft.schoolName" class="field" /></div>
        <div><label class="field-label">主管部门</label><input v-model="draft.supervisor" class="field" /></div>
        <div><label class="field-label">属地省份</label><input v-model="draft.provinceCode" class="field" /></div>
        <div><label class="field-label">城市</label><input v-model="draft.city" class="field" /></div>
        <div><label class="field-label">层次标签</label><input v-model="draft.tierTags" class="field" placeholder="985,211,双一流" /></div>
        <div><label class="field-label">办学类型</label><input v-model="draft.schoolType" class="field" /></div>
        <div><label class="field-label">办学层次</label><input v-model="draft.schoolLevel" class="field" /></div>
        <div class="sm:col-span-2 lg:col-span-4"><label class="field-label">简介</label><input v-model="draft.intro" class="field" /></div>
      </div>

      <!-- 院校资料 Markdown 编辑器 -->
      <div class="mt-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <label class="field-label !mb-0">院校资料（Markdown）</label>
          <div class="btn-group">
            <button class="btn btn-slate btn-sm" type="button" @click="triggerImage()" :disabled="uploading">
              <span class="btn-glyph">🖼</span> {{ uploading ? '上传中…' : '插入图片' }}
            </button>
            <button class="btn btn-slate btn-sm" type="button" @click="showPreview = !showPreview">
              {{ showPreview ? '编辑' : '预览' }}
            </button>
          </div>
          <input ref="imageInput" type="file" accept="image/*" class="hidden" @change="onImagePicked" />
        </div>

        <textarea v-if="!showPreview" v-model="profileDraft" rows="10"
          class="field mt-2 font-mono text-[12px] leading-6"
          placeholder="支持 Markdown：# 标题、**加粗**、列表、表格，以及 ![图片说明](/files/school/xxx.png)"></textarea>
        <div v-else class="markdown-body mt-2 rounded-xl border border-hairline p-4 min-h-[160px]"
          v-html="renderedProfile || '<p class=\'faint\'>暂无内容</p>'"></div>
        <p class="mt-1 text-[11px] faint">点击「插入图片」上传后自动插入图片链接；资料对游客公开，可在院校库查看。</p>
      </div>

      <div class="btn-group mt-4">
        <button class="btn btn-mint" type="button" @click="save(isNew ? '' : editing)"><span class="btn-glyph">✓</span> 保存</button>
        <button class="btn btn-slate" type="button" @click="editing = ''; isNew = false">取消</button>
      </div>
    </div>

    <div class="mt-4 overflow-x-auto rounded-xl border border-hairline">
      <table class="data-table">
        <thead>
          <tr><th>代码</th><th>院校名称</th><th>属地</th><th>城市</th><th>层次标签</th><th>类型</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="s in items" :key="s.schoolCode">
            <td class="font-mono text-[11px]">{{ s.schoolCode }}</td>
            <td>{{ s.schoolName }}</td>
            <td>{{ s.provinceCode || '—' }}</td>
            <td>{{ s.city || '—' }}</td>
            <td><span class="pill pill-sky">{{ s.tierTags || '—' }}</span></td>
            <td class="text-[11px]">{{ s.schoolType || '—' }}</td>
            <td>
              <button class="btn btn-amber btn-sm" type="button" @click="edit(s)"><span class="btn-glyph">✎</span> 编辑</button>
            </td>
          </tr>
          <tr v-if="!items.length"><td colspan="7" class="faint">没有匹配的院校</td></tr>
        </tbody>
      </table>
    </div>
    <p class="faint mt-3 text-[11px]">修改会即时影响院校库与匹配结果展示；演示阶段所有院校均为虚构合成数据。</p>
  </div>
</template>
