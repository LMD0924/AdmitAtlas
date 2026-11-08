<script setup>
import { onMounted, ref } from 'vue'
import { activateVersion, getAdminVersions } from '@/api'

const items = ref([])
const current = ref('')
const demoData = ref(true)
const error = ref('')
const msg = ref('')

async function load() {
  error.value = ''
  try {
    const data = await getAdminVersions()
    items.value = data.items || []
    current.value = data.current || ''
    demoData.value = !!data.demoData
  } catch (e) {
    error.value = e.message || '加载数据版本失败'
  }
}

async function activate(v) {
  error.value = ''
  msg.value = ''
  try {
    await activateVersion(v)
    msg.value = `已切换在线公示版本为 ${v}，操作已写入审计日志`
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
        <p class="eyebrow">F-B-17 / F-B-20 · 数据版本</p>
        <h2 class="section-title mt-1">版本登记与公示</h2>
      </div>
      <div class="flex flex-wrap gap-2">
        <span class="pill pill-sky">当前 {{ current || '—' }}</span>
        <span class="pill" :class="demoData ? 'pill-amber' : 'pill-mint'">
          {{ demoData ? '演示数据（合成值）' : '已发布数据' }}
        </span>
      </div>
    </div>
    <p class="muted mt-3 text-[12px] leading-6">
      一次匹配请求内所有事实表固定读取同一 data_version，避免跨版本混算。演示阶段切换仅改变登记与公示状态，
      不改写事实表；真实数据的采集、审核、灰度与回滚为 P2 能力。
    </p>

    <p v-if="error" class="mt-3 rounded-lg border border-hairline bg-[var(--rose-bg)] px-3 py-2 text-[12px] text-[var(--rose)]">{{ error }}</p>
    <p v-if="msg" class="mt-3 text-[12px] text-[var(--mint)]">{{ msg }}</p>

    <div class="mt-4 overflow-x-auto rounded-xl border border-hairline">
      <table class="data-table">
        <thead>
          <tr><th>版本号</th><th>状态</th><th>覆盖范围 / 说明</th><th>操作人</th><th>激活时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="v in items" :key="v.dataVersion">
            <td class="font-mono text-[11px]">{{ v.dataVersion }}</td>
            <td><span class="pill" :class="v.status === 'ACTIVE' ? 'pill-mint' : 'pill-neutral'">{{ v.status }}</span></td>
            <td class="max-w-[280px]">{{ v.coverageNote || '—' }}</td>
            <td>{{ v.activatedBy || '—' }}</td>
            <td class="text-[11px]">{{ v.activatedAt || '—' }}</td>
            <td>
              <button
                v-if="v.status !== 'ACTIVE'"
                class="btn btn-teal"
                type="button"
                @click="activate(v.dataVersion)"
              ><span class="btn-glyph">◉</span> 设为在线</button>
              <span v-else class="faint text-[11px]">在线中</span>
            </td>
          </tr>
          <tr v-if="!items.length"><td colspan="6" class="faint">暂无版本登记</td></tr>
        </tbody>
      </table>
    </div>

    <div class="mt-4 rounded-xl border border-hairline p-4">
      <p class="field-label">合规提示</p>
      <p class="muted text-[12px] leading-6">
        演示集全部数值为合成值，非真实录取分数；最低分不可跨年直接比较，本平台只按最低位次计算匹配度与风险等级，
        不输出录取率或录取承诺。最终以各省教育考试院公布数据为准。
      </p>
    </div>
  </div>
</template>
