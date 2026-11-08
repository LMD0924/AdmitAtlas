<script setup>
import { onMounted, ref } from 'vue'
import { getAdminAudit } from '@/api'

const items = ref([])
const limit = ref(30)
const error = ref('')

async function load() {
  error.value = ''
  try {
    const data = await getAdminAudit({ limit: limit.value })
    items.value = data.items || []
  } catch (e) {
    error.value = e.message || '加载审计日志失败'
  }
}

onMounted(load)
</script>

<template>
  <div class="glass rounded-2xl p-5" v-reveal="0">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <p class="eyebrow">F-S-09 · 操作审计</p>
        <h2 class="section-title mt-1">管理端操作留痕</h2>
      </div>
      <div class="btn-group">
        <select v-model.number="limit" class="field !w-auto" @change="load">
          <option :value="20">最近 20 条</option>
          <option :value="50">最近 50 条</option>
          <option :value="100">最近 100 条</option>
        </select>
        <button class="btn btn-slate" type="button" @click="load"><span class="btn-glyph">↻</span> 刷新</button>
      </div>
    </div>
    <p class="muted mt-3 text-[12px] leading-6">
      新建账号、启停账号、重置密码、修改志愿规则、维护院校档案、切换数据版本都会记录操作人、时间与前后值摘要。
    </p>

    <p v-if="error" class="mt-3 rounded-lg border border-hairline bg-[var(--rose-bg)] px-3 py-2 text-[12px] text-[var(--rose)]">{{ error }}</p>

    <div class="mt-4 overflow-x-auto rounded-xl border border-hairline">
      <table class="data-table">
        <thead>
          <tr><th>时间</th><th>操作人</th><th>动作</th><th>对象</th><th>说明</th></tr>
        </thead>
        <tbody>
          <tr v-for="a in items" :key="a.id">
            <td class="whitespace-nowrap text-[11px]">{{ a.createdAt || '—' }}</td>
            <td>{{ a.operatorName || '—' }}</td>
            <td><span class="pill pill-neutral">{{ a.action }}</span></td>
            <td class="text-[11px]">{{ a.targetType }} / {{ a.targetId }}</td>
            <td class="max-w-[300px]">{{ a.detail || '—' }}</td>
          </tr>
          <tr v-if="!items.length"><td colspan="5" class="faint">暂无审计记录，可在其他后台页面操作后返回查看</td></tr>
        </tbody>
      </table>
    </div>
    <p class="faint mt-3 text-[11px]">当前显示 {{ items.length }} 条</p>
  </div>
</template>
