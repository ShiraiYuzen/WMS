<template>
  <div class="page">
    <el-row :gutter="14">
      <el-col v-for="c in cards" :key="c.label" :xs="12" :sm="8" :md="6" :lg="4">
        <StatCard class="grid-card" :label="c.label" :value="c.value" :tone="c.tone" :icon="c.icon" :to="c.to" />
      </el-col>
    </el-row>

    <el-row :gutter="14" class="mt-row">
      <el-col :xs="24" :md="12">
        <div class="page-card">
          <div class="section-title">波次状态分布</div>
          <ChartBox :option="waveOption" height="268px" />
        </div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div class="page-card">
          <div class="section-title">平台订单分布</div>
          <ChartBox :option="platformOption" height="268px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14" class="mt-row">
      <el-col :xs="24" :md="12">
        <div class="page-card">
          <div class="section-title">近 7 天订单量</div>
          <ChartBox :option="orderOption" height="268px" />
        </div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div class="page-card">
          <div class="section-title">缺货原因分布</div>
          <ChartBox :option="shortageOption" height="268px" />
        </div>
      </el-col>
    </el-row>

    <div class="page-card mt-row">
      <div class="section-title">作业闭环概览</div>
      <el-steps :active="activeStep" align-center finish-status="success">
        <el-step title="订单汇聚" :description="`待分波 ${d.orderPending} 单`" />
        <el-step title="波次生成" :description="`待拣货 ${d.waveTodo} 波`" />
        <el-step title="拣货执行" :description="`拣货中 ${d.wavePicking} 波`" />
        <el-step title="复核打包" :description="`待复核 ${d.waveToReview} 波`" />
        <el-step title="出库交接" :description="`待出库 ${d.waveToOutbound} 波`" />
        <el-step title="效率复盘" :description="`差错率 ${d.errorRate}%`" />
      </el-steps>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import StatCard from '@/components/StatCard.vue'
import ChartBox from '@/components/ChartBox.vue'
import { dashboard } from '@/api/stats'

interface DistItem { name: string; value: number; qty?: number }

const d = reactive<Record<string, any>>({
  orderPending: 0, orderTotal: 0, orderDone: 0, orderToday: 0,
  waveTodo: 0, wavePicking: 0, waveToReview: 0, waveToOutbound: 0, waveDone: 0, waveTotal: 0, waveToday: 0,
  reviewTotal: 0, errorTotal: 0, errorRate: 0, shortageTotal: 0, shortagePending: 0,
  outboundTotal: 0, outboundQty: 0, lowStock: 0,
})

const platformDist = ref<DistItem[]>([])
const orderRecent = ref<DistItem[]>([])
const waveStatusDist = ref<DistItem[]>([])
const shortageReason = ref<DistItem[]>([])

const cards = computed(() => [
  { label: '待分波订单', value: d.orderPending, tone: 'primary', icon: 'Tickets', to: '/order' },
  { label: '待拣货波次', value: d.waveTodo, tone: 'warning', icon: 'Files', to: '/wave' },
  { label: '拣货中波次', value: d.wavePicking, tone: 'primary', icon: 'ShoppingCart', to: '/pick' },
  { label: '待复核波次', value: d.waveToReview, tone: 'info', icon: 'Finished', to: '/review' },
  { label: '待出库波次', value: d.waveToOutbound, tone: 'success', icon: 'Van', to: '/outbound' },
  { label: '今日新增订单', value: d.orderToday, tone: 'default', icon: 'Calendar', to: '/order' },
  { label: '累计差错率', value: `${d.errorRate}%`, tone: 'danger', icon: 'WarningFilled', to: '/error' },
  { label: '待处理缺货', value: d.shortagePending, tone: 'warning', icon: 'Warning', to: '/shortage' },
  { label: '低库存 SKU', value: d.lowStock, tone: 'danger', icon: 'Box', to: '/inventory' },
  { label: '累计出库件数', value: d.outboundQty, tone: 'primary', icon: 'SoldOut', to: '/outbound' },
  { label: '已出库波次', value: d.waveDone, tone: 'success', icon: 'CircleCheck', to: '/wave' },
  { label: '订单总数', value: d.orderTotal, tone: 'info', icon: 'DataLine', to: '/order' },
])

const activeStep = computed(() => {
  if (d.waveToOutbound > 0) return 4
  if (d.waveToReview > 0) return 3
  if (d.wavePicking > 0) return 2
  if (d.waveTodo > 0) return 1
  return 5
})

const PIE_COLORS = ['#2563eb', '#f59e0b', '#8b5cf6', '#06b6d4', '#16a34a', '#94a3b8']

const waveOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0, icon: 'circle', textStyle: { color: '#64748b' } },
  color: PIE_COLORS,
  series: [{
    type: 'pie', radius: ['45%', '70%'], center: ['50%', '44%'],
    itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
    label: { formatter: '{b}\n{c}', color: '#334155', fontSize: 12 },
    data: waveStatusDist.value.map((i) => ({ name: i.name || '未知', value: Number(i.value) || 0 })),
  }],
}))

const platformOption = computed(() => ({
  grid: { left: 40, right: 20, top: 20, bottom: 28 },
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    data: platformDist.value.map((i) => i.name || '未知'),
    axisLabel: { color: '#64748b' },
    axisLine: { lineStyle: { color: '#e2e8f0' } },
  },
  yAxis: {
    type: 'value', minInterval: 1,
    axisLabel: { color: '#64748b' },
    splitLine: { lineStyle: { color: '#f1f5f9' } },
  },
  series: [{
    type: 'bar', barWidth: '46%',
    itemStyle: {
      borderRadius: [6, 6, 0, 0],
      color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
        { offset: 0, color: '#60a5fa' }, { offset: 1, color: '#2563eb' },
      ] },
    },
    data: platformDist.value.map((i) => Number(i.value) || 0),
  }],
}))

const orderOption = computed(() => ({
  grid: { left: 40, right: 20, top: 20, bottom: 28 },
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category', boundaryGap: false,
    data: orderRecent.value.map((i) => i.name),
    axisLabel: { color: '#64748b' },
    axisLine: { lineStyle: { color: '#e2e8f0' } },
  },
  yAxis: {
    type: 'value', minInterval: 1,
    axisLabel: { color: '#64748b' },
    splitLine: { lineStyle: { color: '#f1f5f9' } },
  },
  series: [{
    type: 'line', smooth: true, symbolSize: 7,
    lineStyle: { width: 3, color: '#f59e0b' },
    itemStyle: { color: '#f59e0b' },
    areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
      { offset: 0, color: 'rgba(245,158,11,0.32)' }, { offset: 1, color: 'rgba(245,158,11,0.02)' },
    ] } },
    data: orderRecent.value.map((i) => Number(i.value) || 0),
  }],
}))

const shortageOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0, icon: 'circle', textStyle: { color: '#64748b' } },
  color: ['#dc2626', '#f59e0b', '#facc15', '#94a3b8'],
  series: [{
    type: 'pie', radius: ['0%', '62%'], center: ['50%', '44%'], roseType: 'radius',
    itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
    label: { formatter: '{b} {c}', color: '#334155', fontSize: 12 },
    data: shortageReason.value.map((i) => ({ name: i.name || '未知', value: Number(i.value) || 0 })),
  }],
}))

async function load() {
  const res = await dashboard()
  Object.assign(d, res.data)
  platformDist.value = res.data.platformDist || []
  orderRecent.value = res.data.orderRecent || []
  waveStatusDist.value = res.data.waveStatusDist || []
  shortageReason.value = res.data.shortageReason || []
}

onMounted(load)
</script>

<style scoped>
.grid-card {
  margin-bottom: 14px;
}

.mt-row {
  margin-top: 0;
}
</style>
