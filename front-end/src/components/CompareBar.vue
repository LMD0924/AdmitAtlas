<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { MAX_COMPARE, useEngagementStore } from '@/stores/engagement'
import { schoolTheme } from '@/utils/schoolTheme'

const store = useEngagementStore()
const router = useRouter()

const canCompare = computed(() => store.compareCount >= 2)

function goCompare() {
  if (!canCompare.value) return
  router.push({
    name: 'school-compare',
    query: { codes: store.compareCodes.join(',') }
  })
}
</script>

<template>
  <Transition name="compare-bar">
    <aside v-if="store.compareCount" class="compare-bar" aria-label="院校对比篮">
      <div class="compare-bar-info">
        <span class="compare-bar-count">{{ store.compareCount }}<i>/{{ MAX_COMPARE }}</i></span>
        <span class="compare-bar-label">已加入对比</span>
      </div>

      <div class="compare-bar-chips">
        <button
          v-for="item in store.compare"
          :key="item.code"
          type="button"
          class="compare-chip"
          :data-school-theme="schoolTheme({ schoolCode: item.code })"
          :title="`移除 ${item.name}`"
          @click="store.toggleCompare(item.code)"
        >
          {{ item.name }} <span aria-hidden="true">×</span>
        </button>
      </div>

      <div class="compare-bar-actions">
        <button type="button" class="btn btn-slate" @click="store.clearCompare()">清空</button>
        <button
          type="button"
          class="btn btn-blue"
          :disabled="!canCompare"
          :title="canCompare ? '并排比较所选院校' : '至少选择 2 所院校'"
          @click="goCompare"
        >
          <span class="btn-glyph">⇄</span> 开始对比 <span class="link-arrow">→</span>
        </button>
      </div>
    </aside>
  </Transition>
</template>

<style scoped>
.compare-bar {
  position: fixed;
  left: 50%;
  bottom: 22px;
  z-index: 60;
  display: flex;
  align-items: center;
  gap: 14px;
  width: min(940px, calc(100vw - 32px));
  padding: 11px 14px;
  transform: translateX(-50%);
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--glass);
  -webkit-backdrop-filter: blur(18px) saturate(150%);
  backdrop-filter: blur(18px) saturate(150%);
  box-shadow: var(--shadow);
}
.compare-bar-info { display: flex; flex: none; align-items: baseline; gap: 7px; }
.compare-bar-count { font-size: 17px; font-weight: 700; color: var(--accent-text); font-variant-numeric: tabular-nums; }
.compare-bar-count i { font-style: normal; font-size: 11px; font-weight: 500; color: var(--text-3); }
.compare-bar-label { font-size: 11px; color: var(--text-2); white-space: nowrap; }
.compare-bar-chips { display: flex; flex: 1; flex-wrap: wrap; gap: 6px; min-width: 0; }
.compare-chip {
  max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  padding: 4px 9px; border-radius: 999px; border: 1px solid var(--card-line, var(--line));
  background: var(--card-soft); color: var(--card-c, var(--text));
  font-size: 11px; cursor: pointer; transition: opacity .2s, transform .2s;
}
.compare-chip:hover { opacity: .75; transform: translateY(-1px); }
.compare-chip span { margin-left: 3px; font-weight: 700; }
.compare-bar-actions { display: flex; flex: none; gap: 6px; }
.compare-bar-enter-active, .compare-bar-leave-active { transition: opacity .28s ease, transform .28s ease; }
.compare-bar-enter-from, .compare-bar-leave-to { opacity: 0; transform: translate(-50%, 16px); }
@media (max-width: 720px) {
  .compare-bar { flex-wrap: wrap; gap: 8px; bottom: 12px; padding: 10px; }
  .compare-bar-chips { order: 3; flex-basis: 100%; }
  .compare-bar-actions { margin-left: auto; }
}
@media (prefers-reduced-motion: reduce) {
  .compare-bar-enter-active, .compare-bar-leave-active { transition: none; }
  .compare-bar-enter-from, .compare-bar-leave-to { transform: translateX(-50%); }
  .compare-chip { transition: none; }
}
</style>
