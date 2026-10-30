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

    <el-row :gutter="14" style="margin-top: 4px">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="page-card">
          <div class="section-title">差错类型分布</div>
          <div v-for="item in byType" :key="item.name" class="bar-row">
            <span class="bar-name">{{ item.name }}</span>
            <div class="bar-track"><div class="bar-fill red" :style="{ width: pct(item.value, maxOf(byType)) + '%' }"></div></div>
            <span class="bar-val">{{ item.value }}</span>
          </div>
          <el-empty v-if="!byType.length" description="暂无差错" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="page-card">
          <div class="section-title">按拣货员归因</div>
          <div v-for="item in byPicker" :key="item.name" class="bar-row">
            <span class="bar-name">{{ item.name }}</span>
            <div class="bar-track"><div class="bar-fill orange" :style="{ width: pct(item.errorCount, maxOf(byPicker, 'errorCount')) + '%' }"></div></div>
            <span class="bar-val">{{ item.errorCount }} 次 / {{ item.errorQty }} 件</span>
          </div>
          <el-empty v-if="!byPicker.length" description="暂无差错" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="page-card" style="margin-top: 14px">
      <div class="toolbar">
        <div class="section-title" style="margin: 0">差错明细</div>
        <div class="spacer"></div>
        <el-select v-model="query.errorType" placeholder="差错类型" clearable style="width: 130px" @change="reload">
          <el-option v-for="t in errorTypes" :key="t" :label="t" :value="t" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="波次号 / 订单号 / SKU" clearable style="width: 220px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
      </div>
      <el-table :data="rows" border stripe size="small" v-loading="loading">
        <el-table-column prop="waveNo" label="波次号" width="150" />
        <el-table-column prop="orderNo" label="订单号" width="150" />
        <el-table-column prop="skuCode" label="SKU" width="110" />
        <el-table-column prop="skuName" label="商品" min-width="120" />
        <el-table-column prop="errorType" label="差错类型" width="100">
          <template #default="{ row }"><el-tag size="small" type="danger">{{ row.errorType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="70" align="center" />
        <el-table-column prop="checkerName" label="复核员" width="130" />
        <el-table-column prop="description" label="说明" min-width="160" />
        <el-table-column prop="createTime" label="登记时间" width="160" />
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       v-model:current-page="query.page" v-model:page-size="query.size" @current-change="load" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { errorStats } from '@/api/stats'
import { listErrors } from '@/api/error'

const errorTypes = ['错拣', '漏拣', '多拣', '破损', '其他']

const stat = reactive<any>({ reviewTotal: 0, errorTotal: 0, passTotal: 0, abnormalTotal: 0, errorRate: 0 })
const byType = ref<any[]>([])
const byPicker = ref<any[]>([])

const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ errorType: '', keyword: '', page: 1, size: 10 })

const cards = computed(() => [
  { label: '复核订单数', value: stat.reviewTotal, color: '#409eff' },
  { label: '复核通过', value: stat.passTotal, color: '#52c41a' },
  { label: '复核异常', value: stat.abnormalTotal, color: '#fa8c16' },
  { label: '差错总数', value: stat.errorTotal, color: '#f56c6c' },
  { label: '差错率', value: `${stat.errorRate}%`, color: '#eb2f96' },
])

function maxOf(list: any[], field = 'value') {
  return Math.max(1, ...list.map((i) => Number(i[field]) || 0))
}

function pct(v: number, max: number) {
  return Math.max(4, Math.round((Number(v) || 0) * 100 / max))
}

async function loadStats() {
  const res = await errorStats()
  Object.assign(stat, res.data)
  byType.value = res.data.byType || []
  byPicker.value = res.data.byPicker || []
}

async function load() {
  loading.value = true
  try {
    const res = await listErrors({ ...query })
    rows.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function reload() {
  query.page = 1
  load()
}

onMounted(() => {
  loadStats()
  load()
})
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
  width: 92px;
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
  border-radius: 7px;
  transition: width 0.4s;
}

.bar-fill.red { background: #f56c6c; }
.bar-fill.orange { background: #faad14; }

.bar-val {
  width: 110px;
  font-size: 13px;
  color: #303133;
  flex-shrink: 0;
}
</style>
