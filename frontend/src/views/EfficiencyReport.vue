<template>
  <div class="page">
    <el-row :gutter="14">
      <el-col v-for="c in cards" :key="c.label" :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card" :body-style="{ padding: '16px' }">
          <div class="stat-value" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="stat-label">{{ c.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="page-card">
      <div class="section-title">拣货员效率复盘</div>
      <el-table :data="pickers" border stripe size="small" v-loading="loading">
        <el-table-column prop="name" label="拣货员" width="150" />
        <el-table-column prop="waveCount" label="完成波次" width="100" align="center" />
        <el-table-column prop="orderCount" label="处理订单" width="100" align="center" />
        <el-table-column prop="totalQty" label="拣货件数" width="100" align="center" />
        <el-table-column prop="totalMinutes" label="累计工时(分)" width="120" align="center" />
        <el-table-column prop="avgMinutes" label="平均单波耗时(分)" width="150" align="center" />
        <el-table-column label="拣货效率(件/小时)" width="160" align="center">
          <template #default="{ row }">
            <span class="rate">{{ efficiencyOf(row) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !pickers.length" description="暂无拣货数据" :image-size="70" />
    </el-card>

    <el-card shadow="never" class="page-card" style="margin-top: 14px">
      <div class="section-title">复核员效率复盘</div>
      <el-table :data="checkers" border stripe size="small" v-loading="loading">
        <el-table-column prop="name" label="复核员" width="150" />
        <el-table-column prop="reviewCount" label="复核订单" width="110" align="center" />
        <el-table-column prop="passCount" label="通过" width="100" align="center" />
        <el-table-column prop="errorCount" label="异常" width="100" align="center" />
        <el-table-column label="异常率" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="Number(row.errorCount) > 0 ? 'warning' : 'success'">
              {{ rate(row.errorCount, row.reviewCount) }}%
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !checkers.length" description="暂无复核数据" :image-size="70" />
    </el-card>

    <el-card shadow="never" class="page-card" style="margin-top: 14px">
      <div class="section-title">整体作业指标</div>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="波次总数">{{ overall.waveTotal }}</el-descriptions-item>
        <el-descriptions-item label="已出库波次">{{ overall.waveDone }}</el-descriptions-item>
        <el-descriptions-item label="订单总数">{{ overall.orderTotal }}</el-descriptions-item>
        <el-descriptions-item label="已出库订单">{{ overall.orderDone }}</el-descriptions-item>
        <el-descriptions-item label="出库件数">{{ overall.outboundQty }}</el-descriptions-item>
        <el-descriptions-item label="复核订单">{{ overall.reviewTotal }}</el-descriptions-item>
        <el-descriptions-item label="差错数">{{ overall.errorTotal }}</el-descriptions-item>
        <el-descriptions-item label="缺货数(件)">{{ overall.shortageQty }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { efficiency } from '@/api/stats'

const loading = ref(false)
const pickers = ref<any[]>([])
const checkers = ref<any[]>([])
const overall = reactive<any>({
  waveTotal: 0, waveDone: 0, orderTotal: 0, orderDone: 0,
  outboundQty: 0, reviewTotal: 0, errorTotal: 0, shortageQty: 0,
})

const cards = computed(() => [
  { label: '波次总数', value: overall.waveTotal, color: '#409eff' },
  { label: '已出库波次', value: overall.waveDone, color: '#52c41a' },
  { label: '出库件数', value: overall.outboundQty, color: '#2c5282' },
  { label: '复核订单', value: overall.reviewTotal, color: '#13c2c2' },
  { label: '差错数', value: overall.errorTotal, color: '#f56c6c' },
  { label: '缺货件数', value: overall.shortageQty, color: '#fa8c16' },
])

function efficiencyOf(row: any) {
  const qty = Number(row.totalQty) || 0
  const minutes = Number(row.totalMinutes) || 0
  if (minutes <= 0) return '-'
  return (qty * 60 / minutes).toFixed(1)
}

function rate(part: number, whole: number) {
  const w = Number(whole) || 0
  if (w <= 0) return 0
  return Math.round((Number(part) || 0) * 10000 / w) / 100
}

async function load() {
  loading.value = true
  try {
    const res = await efficiency()
    pickers.value = res.data.pickers || []
    checkers.value = res.data.checkers || []
    Object.assign(overall, res.data.overall || {})
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.stat-card {
  margin-bottom: 14px;
  border: none;
}

.rate {
  color: #2f9e44;
  font-weight: 600;
}
</style>
