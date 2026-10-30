<template>
  <div class="page">
    <el-row :gutter="14">
      <el-col v-for="c in cards" :key="c.label" :xs="12" :sm="8" :md="6" :lg="4">
        <el-card shadow="hover" class="stat-card" :body-style="{ padding: '16px' }">
          <div class="stat-value" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="stat-label">{{ c.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="page-card">
          <div class="section-title">波次状态分布</div>
          <div v-for="item in waveStatusDist" :key="item.name" class="bar-row">
            <span class="bar-name">{{ item.name }}</span>
            <div class="bar-track"><div class="bar-fill" :style="{ width: pct(item.value, maxOf(waveStatusDist)) + '%' }"></div></div>
            <span class="bar-val">{{ item.value }}</span>
          </div>
          <el-empty v-if="!waveStatusDist.length" description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="page-card">
          <div class="section-title">平台订单分布</div>
          <div v-for="item in platformDist" :key="item.name" class="bar-row">
            <span class="bar-name">{{ item.name || '未知' }}</span>
            <div class="bar-track"><div class="bar-fill green" :style="{ width: pct(item.value, maxOf(platformDist)) + '%' }"></div></div>
            <span class="bar-val">{{ item.value }}</span>
          </div>
          <el-empty v-if="!platformDist.length" description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="page-card">
          <div class="section-title">近 7 天订单量</div>
          <div v-for="item in orderRecent" :key="item.name" class="bar-row">
            <span class="bar-name">{{ item.name }}</span>
            <div class="bar-track"><div class="bar-fill orange" :style="{ width: pct(item.value, maxOf(orderRecent)) + '%' }"></div></div>
            <span class="bar-val">{{ item.value }}</span>
          </div>
          <el-empty v-if="!orderRecent.length" description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="page-card">
          <div class="section-title">缺货原因分布</div>
          <div v-for="item in shortageReason" :key="item.name" class="bar-row">
            <span class="bar-name">{{ item.name }}</span>
            <div class="bar-track"><div class="bar-fill red" :style="{ width: pct(item.value, maxOf(shortageReason)) + '%' }"></div></div>
            <span class="bar-val">{{ item.value }} 单 / {{ item.qty }} 件</span>
          </div>
          <el-empty v-if="!shortageReason.length" description="暂无缺货" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="page-card" style="margin-top: 14px">
      <div class="section-title">作业闭环概览</div>
      <el-steps :active="activeStep" align-center finish-status="success">
        <el-step title="订单汇聚" :description="`待分波 ${d.orderPending} 单`" />
        <el-step title="波次生成" :description="`待拣货 ${d.waveTodo} 波`" />
        <el-step title="拣货执行" :description="`拣货中 ${d.wavePicking} 波`" />
        <el-step title="复核打包" :description="`待复核 ${d.waveToReview} 波`" />
        <el-step title="出库交接" :description="`待出库 ${d.waveToOutbound} 波`" />
        <el-step title="效率复盘" :description="`差错率 ${d.errorRate}%`" />
      </el-steps>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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
  { label: '待分波订单', value: d.orderPending, color: '#409eff' },
  { label: '待拣货波次', value: d.waveTodo, color: '#e6a23c' },
  { label: '拣货中波次', value: d.wavePicking, color: '#9254de' },
  { label: '待复核波次', value: d.waveToReview, color: '#13c2c2' },
  { label: '待出库波次', value: d.waveToOutbound, color: '#2f9e44' },
  { label: '今日新增订单', value: d.orderToday, color: '#303133' },
  { label: '累计差错率', value: `${d.errorRate}%`, color: '#f56c6c' },
  { label: '待处理缺货', value: d.shortagePending, color: '#fa8c16' },
  { label: '低库存 SKU', value: d.lowStock, color: '#eb2f96' },
  { label: '累计出库件数', value: d.outboundQty, color: '#2c5282' },
  { label: '已出库波次', value: d.waveDone, color: '#52c41a' },
  { label: '订单总数', value: d.orderTotal, color: '#606266' },
])

const activeStep = computed(() => {
  if (d.waveToOutbound > 0) return 4
  if (d.waveToReview > 0) return 3
  if (d.wavePicking > 0) return 2
  if (d.waveTodo > 0) return 1
  return 5
})

function maxOf(list: DistItem[]) {
  return Math.max(1, ...list.map((i) => Number(i.value) || 0))
}

function pct(v: number, max: number) {
  return Math.max(4, Math.round((Number(v) || 0) * 100 / max))
}

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
.stat-card {
  margin-bottom: 14px;
  border: none;
}

.bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.bar-name {
  width: 78px;
  font-size: 13px;
  color: #606266;
  text-align: right;
  flex-shrink: 0;
}

.bar-track {
  flex: 1;
  height: 14px;
  background: #f0f2f5;
  border-radius: 7px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: #409eff;
  border-radius: 7px;
  transition: width 0.4s;
}

.bar-fill.green { background: #52c41a; }
.bar-fill.orange { background: #faad14; }
.bar-fill.red { background: #f56c6c; }

.bar-val {
  width: 96px;
  font-size: 13px;
  color: #303133;
  flex-shrink: 0;
}
</style>
