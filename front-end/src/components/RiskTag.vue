<script setup>
import { computed } from 'vue'

const props = defineProps({
  tier: { type: String, default: 'WEN' }
})

const styleMap = {
  CHONG: { text: '冲', cls: 'tag-chong' },
  WEN: { text: '稳', cls: 'tag-wen' },
  BAO: { text: '保', cls: 'tag-bao' }
}

const current = computed(() => styleMap[props.tier] || styleMap.WEN)
</script>

<template>
  <span class="tag" :class="current.cls">{{ current.text }}</span>
</template>
