<template>
  <el-tag :type="tagType(code)" :size="size" :effect="effect" disable-transitions>
    {{ text }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { useDictStore } from '@/stores/dict'
import { tagType } from '@/utils'

const props = defineProps({
  /** 状态编码 */
  code: { type: String, default: '' },
  /** 字典分组，例如 waveStatus */
  group: { type: String, required: true },
  size: { type: String, default: 'small' },
  effect: { type: String, default: 'light' }
})

const dict = useDictStore()

const text = computed(() => dict.text(props.group, props.code) || '—')
</script>
