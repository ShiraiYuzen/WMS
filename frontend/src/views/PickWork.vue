<template>
  <div class="page">
    <el-row :gutter="14">
      <el-col :xs="24" :md="7">
        <el-card shadow="never" class="page-card">
          <div class="section-title">待拣波次</div>
          <el-empty v-if="!waves.length" description="暂无待拣波次" :image-size="70" />
          <div v-for="w in waves" :key="w.id" class="wave-item" :class="{ active: current && current.id === w.id }"
               @click="select(w)">
            <div class="wave-no">{{ w.waveNo }}</div>
            <div class="wave-meta">
              <el-tag size="small" :type="w.status === '拣货中' ? 'primary' : 'warning'">{{ w.status }}</el-tag>
              <span class="muted">{{ w.orderCount }} 单 / {{ w.totalQty }} 件</span>
            </div>
            <div class="wave-meta muted">{{ w.pickerName || '未指派' }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="17">
        <el-card shadow="never" class="page-card" v-if="current">
          <div class="toolbar">
            <div class="section-title" style="margin: 0">拣货作业 · {{ current.waveNo }}</div>
            <div class="spacer"></div>
            <el-button v-if="current.status === '待拣货'" type="primary" @click="onStart">开始拣货</el-button>
            <el-button v-if="current.status === '拣货中'" type="success" @click="onSubmit" :loading="saving">提交拣货</el-button>
            <el-button v-if="current.status === '拣货中'" type="warning" @click="onFinish" :loading="saving">完成拣货</el-button>
          </div>

          <el-alert v-if="current.status === '待拣货'" type="info" :closable="false" style="margin-bottom: 12px"
                    title="点击「开始拣货」后进入拣货状态，按库位顺序依次拣货并填写实拣数量" />

          <el-table :data="items" border size="small" v-loading="loading">
            <el-table-column prop="location" label="库位" width="90" />
            <el-table-column prop="skuCode" label="SKU" width="110" />
            <el-table-column prop="skuName" label="商品" min-width="140" />
            <el-table-column prop="requiredQty" label="需求数量" width="90" align="center" />
            <el-table-column label="实拣数量" width="140" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.pickedQty" :min="0" :max="row.requiredQty" size="small"
                                 :disabled="current.status !== '拣货中'" />
              </template>
            </el-table-column>
            <el-table-column label="缺货" width="80" align="center">
              <template #default="{ row }">
                <span :class="{ 'danger-text': row.requiredQty - row.pickedQty > 0 }">{{ row.requiredQty - row.pickedQty }}</span>
              </template>
            </el-table-column>
            <el-table-column label="缺货原因" width="150">
              <template #default="{ row }">
                <el-select v-if="row.requiredQty - row.pickedQty > 0" v-model="row.reason" size="small"
                           placeholder="选择原因" :disabled="current.status !== '拣货中'">
                  <el-option v-for="r in reasons" :key="r" :label="r" :value="r" />
                </el-select>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" />
          </el-table>

          <div class="section-title" style="margin-top: 16px">关联订单</div>
          <el-table :data="orders" border size="small">
            <el-table-column prop="orderNo" label="订单号" width="160" />
            <el-table-column prop="platform" label="平台" width="90" />
            <el-table-column prop="customerName" label="客户" width="100" />
            <el-table-column prop="totalQty" label="件数" width="70" align="center" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </el-card>
        <el-card shadow="never" class="page-card" v-else>
          <el-empty description="请从左侧选择一个波次开始拣货" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { finishPick, pickDetail, startPick, submitPick } from '@/api/pick'
import { pickableWaves } from '@/api/wave'

const reasons = ['库存不足', '库位缺货', '商品破损', '其他']

const waves = ref<any[]>([])
const current = ref<any>(null)
const items = ref<any[]>([])
const orders = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)

async function loadWaves() {
  const res = await pickableWaves()
  waves.value = res.data
  if (current.value) {
    const hit = waves.value.find((w) => w.id === current.value.id)
    current.value = hit || null
  }
  if (!current.value && waves.value.length) {
    await select(waves.value[0])
  }
}

async function select(w: any) {
  current.value = w
  loading.value = true
  try {
    const res = await pickDetail(w.id)
    current.value = res.data.wave
    items.value = (res.data.items || []).map((i: any) => ({
      ...i,
      pickedQty: i.pickedQty || i.requiredQty,
      reason: i.shortageQty > 0 ? '库存不足' : '',
    }))
    orders.value = res.data.orders || []
  } finally {
    loading.value = false
  }
}

async function onStart() {
  await startPick(current.value.id)
  ElMessage.success('已开始拣货')
  await select({ ...current.value, status: '拣货中' })
  await loadWaves()
}

async function onSubmit() {
  saving.value = true
  try {
    const payload = items.value.map((i: any) => {
      const picked = Math.min(i.pickedQty ?? 0, i.requiredQty)
      return {
        skuCode: i.skuCode,
        pickedQty: picked,
        shortageQty: Math.max(i.requiredQty - picked, 0),
        reason: i.reason,
      }
    })
    await submitPick({ waveId: current.value.id, items: payload })
    ElMessage.success('拣货结果已提交')
    await select(current.value)
  } finally {
    saving.value = false
  }
}

async function onFinish() {
  try {
    await ElMessageBox.confirm('确认拣货完成并提交复核吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  saving.value = true
  try {
    await finishPick(current.value.id)
    ElMessage.success('已提交复核')
    current.value = null
    items.value = []
    orders.value = []
    await loadWaves()
  } finally {
    saving.value = false
  }
}

onMounted(loadWaves)
</script>

<style scoped>
.wave-item {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.wave-item:hover {
  border-color: #c6e2ff;
  background: #f5f9ff;
}

.wave-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.wave-no {
  font-weight: 600;
  margin-bottom: 6px;
}

.wave-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}

.danger-text {
  color: #f56c6c;
  font-weight: 600;
}
</style>
