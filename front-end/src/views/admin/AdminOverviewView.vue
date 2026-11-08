<script setup>
import { onMounted, ref } from 'vue'
import { getAdminOverview, getAdminPlans, getAdminTasks } from '@/api'

const data = ref(null)
const plans = ref([])
const tasks = ref([])
const error = ref('')

function n(key) {
  return data.value?.counts?.[key] ?? 0
}

const metrics = [
  { label: '账号总数', key: 'student_profile', color: 'var(--sky)' },
  { label: '学生 / 管理员', key: 'students', color: 'var(--mint)' },
  { label: '成绩档案', key: 'student_score', color: 'var(--violet)' },
  { label: '志愿方案', key: 'suggestion_plan', color: 'var(--amber)' },
  { label: '匹配任务', key: 'match_task', color: 'var(--teal)' },
  { label: '投档记录', key: 'fact_admission_score', color: 'var(--rose)' }
]

onMounted(async () => {
  try {
    const [ov, pl, tk] = await Promise.all([
      getAdminOverview(), getAdminPlans({ limit: 5 }), getAdminTasks({ limit: 5 })
    ])
    data.value = ov
    plans.value = pl.items || []
    tasks.value = tk.items || []
  } catch (e) {
    error.value = e.message || '后台数据加载失败'
  }
})
</script>

<template>
  <div v-if="error" class="glass rounded-2xl p-6 text-[13px] text-[var(--rose)]">{{ error }}</div>

  <template v-else>
    <div class="glass rounded-2xl p-6" v-reveal="0">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p class="eyebrow">SYSTEM STATUS</p>
          <h2 class="section-title mt-1">平台运行概览</h2>
        </div>
        <div class="flex flex-wrap gap-2">
          <span class="pill pill-violet">{{ data?.stage || '—' }}</span>
          <span class="pill" :class="data?.demoData ? 'pill-amber' : 'pill-mint'">
            {{ data?.demoData ? '演示数据（合成值）' : '已发布数据' }}
          </span>
        </div>
      </div>
      <p class="muted mt-3 text-[12px] leading-6">
        当前公示版本 <strong>{{ data?.dataVersion || '—' }}</strong>。
        全部结论仅按历史最低位次计算匹配度与风险等级，不构成录取承诺；最终以各省教育考试院公布数据为准。
      </p>
    </div>

    <div class="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3" v-reveal="1">
      <div v-for="m in metrics" :key="m.key" class="glass-flat rounded-xl p-4">
        <p class="metric-label">{{ m.label }}</p>
        <p class="metric-value mt-1" :style="{ color: m.color }">{{ n(m.key) }}</p>
        <p v-if="m.key === 'students'" class="faint mt-1 text-[11px]">其中管理员 {{ n('admins') }} 个</p>
      </div>
    </div>

    <div class="mt-4 grid gap-4 lg:grid-cols-2">
      <div class="glass rounded-2xl p-5" v-reveal="2">
        <h2 class="section-title">最近操作审计</h2>
        <div v-if="!data?.recentAudit?.length" class="empty-state mt-3">暂无审计记录</div>
        <table v-else class="data-table mt-3">
          <thead><tr><th>动作</th><th>操作人</th><th>说明</th></tr></thead>
          <tbody>
            <tr v-for="a in data.recentAudit" :key="a.id">
              <td><span class="pill pill-neutral">{{ a.action }}</span></td>
              <td>{{ a.operatorName || '—' }}</td>
              <td class="max-w-[220px] truncate">{{ a.detail || '—' }}</td>
            </tr>
          </tbody>
        </table>
        <RouterLink to="/admin/audit" class="link mt-3 inline-block text-[12px]">查看全部 →</RouterLink>
      </div>

      <div class="glass rounded-2xl p-5" v-reveal="3">
        <h2 class="section-title">跨账号业务核查（只读）</h2>
        <p class="faint mt-1 text-[11px]">学生只能看到自己的数据，此处仅管理员可核查。</p>
        <p class="field-label mt-4">最近志愿方案 {{ plans.length }} 条</p>
        <table class="data-table">
          <tbody>
            <tr v-for="p in plans" :key="p.planId">
              <td class="font-mono text-[11px]">#{{ p.planId }}</td>
              <td>用户 {{ p.userId }}</td>
              <td>{{ p.planName || '—' }}</td>
            </tr>
            <tr v-if="!plans.length"><td colspan="3" class="faint">暂无方案</td></tr>
          </tbody>
        </table>
        <p class="field-label mt-4">最近匹配任务 {{ tasks.length }} 条</p>
        <table class="data-table">
          <tbody>
            <tr v-for="t in tasks" :key="t.taskId">
              <td class="font-mono text-[11px]">{{ t.taskId }}</td>
              <td>用户 {{ t.userId }}</td>
              <td>{{ t.status }}</td>
            </tr>
            <tr v-if="!tasks.length"><td colspan="3" class="faint">暂无任务</td></tr>
          </tbody>
        </table>
      </div>
    </div>
  </template>
</template>
