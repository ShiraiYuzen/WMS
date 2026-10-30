<template>
  <div ref="el" class="chart-box" :style="{ height }"></div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

/**
 * ECharts 容器组件。
 *
 * 只做三件事：初始化实例、跟随 option 变化重绘、窗口变化时自适应。
 * 业务图表配置全部由父组件通过 option 传入。
 */
const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '300px' }
})

const el = ref(null)
let chart = null
let resizeObserver = null

function render() {
  if (!chart) return
  chart.setOption(props.option, true)
}

onMounted(async () => {
  await nextTick()
  chart = echarts.init(el.value, null, { renderer: 'canvas' })
  render()

  resizeObserver = new ResizeObserver(() => chart && chart.resize())
  resizeObserver.observe(el.value)
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.chart-box {
  width: 100%;
}
</style>
