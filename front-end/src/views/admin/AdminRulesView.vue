<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getAdminPolicies, updateAdminPolicy } from '@/api'

const items = ref([])
const error = ref('')
const msg = ref('')
const editing = ref('')
const draft = reactive({ volunteerUnit: '', volunteerCount: 45, batches: '' })

async function load() {
  error.value = ''
  try {
    const data = await getAdminPolicies()
    items.value = data.items || []
  } catch (e) {
    error.value = e.message || '加载规则失败'
  }
}

function edit(p) {
  editing.value = `${p.provinceCode}/${p.examYear}`
  draft.volunteerUnit = p.volunteerUnit
  draft.volunteerCount = p.volunteerCount
  draft.batches = p.batches || ''
  msg.value = ''
}

async function save(p) {
  error.value = ''
  try {
    await updateAdminPolicy(p.provinceCode, p.examYear, {
      volunteerUnit: draft.volunteerUnit,
      volunteerCount: Number(draft.volunteerCount),
      batches: draft.batches
    })
    msg.value = `${p.provinceCode}/${p.examYear} 规则已更新并写入审计日志`
    editing.value = ''
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>

<template>
  <div class="glass rounded-2xl p-5" v-reveal="0">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <p class="eyebrow">F-B-12 · 省 × 年规则</p>
        <h2 class="section-title mt-1">志愿结构配置</h2>
      </div>
      <button class="btn btn-slate" type="button" @click="load"><span class="btn-glyph">↻</span> 刷新</button>
    </div>
    <p class="muted mt-3 text-[12px] leading-6">
      志愿单位、志愿数量与批次顺序来自 rule_policy，匹配引擎与方案校验都从这里读取，代码中不硬编码 45 / 96 / 112。
      修改后即时生效，并留前后值审计。
    </p>

    <p v-if="error" class="mt-3 rounded-lg border border-hairline bg-[var(--rose-bg)] px-3 py-2 text-[12px] text-[var(--rose)]">{{ error }}</p>
    <p v-if="msg" class="mt-3 text-[12px] text-[var(--mint)]">{{ msg }}</p>

    <div class="mt-4 overflow-x-auto rounded-xl border border-hairline">
      <table class="data-table">
        <thead>
          <tr><th>省份</th><th>年份</th><th>考试模式</th><th>志愿单位</th><th>志愿数</th><th>批次</th><th>版本</th><th>操作</th></tr>
        </thead>
        <tbody>
          <template v-for="p in items" :key="`${p.provinceCode}/${p.examYear}`">
            <tr>
              <td>{{ p.provinceCode }}</td>
              <td>{{ p.examYear }}</td>
              <td class="text-[11px]">{{ p.examMode }}</td>
              <td><span class="pill pill-sky">{{ p.volunteerUnit }}</span></td>
              <td><strong>{{ p.volunteerCount }}</strong></td>
              <td class="max-w-[200px] truncate">{{ p.batches || '—' }}</td>
              <td class="text-[11px]">{{ p.dataVersion }}</td>
              <td>
                <button class="btn btn-blue" type="button" @click="edit(p)"><span class="btn-glyph">✎</span> 编辑</button>
              </td>
            </tr>
            <tr v-if="editing === `${p.provinceCode}/${p.examYear}`">
              <td colspan="8">
                <div class="grid gap-3 py-2 sm:grid-cols-2 lg:grid-cols-4">
                  <div><label class="field-label">志愿单位</label><input v-model="draft.volunteerUnit" class="field" /></div>
                  <div><label class="field-label">志愿数量</label><input v-model.number="draft.volunteerCount" class="field" type="number" min="1" max="200" /></div>
                  <div class="lg:col-span-2"><label class="field-label">批次（逗号分隔）</label><input v-model="draft.batches" class="field" /></div>
                </div>
                <div class="btn-group">
                  <button class="btn btn-mint" type="button" @click="save(p)"><span class="btn-glyph">✓</span> 保存</button>
                  <button class="btn btn-slate" type="button" @click="editing = ''">取消</button>
                </div>
              </td>
            </tr>
          </template>
          <tr v-if="!items.length"><td colspan="8" class="faint">暂无规则配置</td></tr>
        </tbody>
      </table>
    </div>
    <p class="faint mt-3 text-[11px]">共 {{ items.length }} 套省 × 年规则 · 演示数据仅覆盖 5 省</p>
  </div>
</template>
