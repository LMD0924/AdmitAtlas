<script setup>
import { computed } from 'vue'

const props = defineProps({
  tier: { type: String, default: 'WEN' }
})

const styleMap = {
  CHONG: { text: '冲', cls: 'bg-risk-chong text-red-800' },
  WEN: { text: '稳', cls: 'bg-risk-wen text-amber-800' },
  BAO: { text: '保', cls: 'bg-risk-bao text-teal-800' }
}

const current = computed(() => styleMap[props.tier] || styleMap.WEN)
</script>

<template>
  <span :class="['inline-flex rounded-md px-2 py-0.5 text-xs font-medium', current.cls]">
    {{ current.text }}
  </span>
</template>
