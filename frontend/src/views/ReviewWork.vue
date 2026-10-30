<template>
  <div class="page">
    <el-row :gutter="14">
      <el-col :xs="24" :md="7">
        <el-card shadow="never" class="page-card">
          <div class="section-title">待复核波次</div>
          <el-empty v-if="!waves.length" description="暂无待复核波次" :image-size="70" />
          <div v-for="w in waves" :key="w.id" class="wave-item" :class="{ active: current && current.id === w.id }"
               @click="select(w)">
            <div class="wave-no">{{ w.waveNo }}</div>
            <div class="wave-meta">
              <el-tag size="small" type="info">{{ w.status }}</el-tag>
              <span class="muted">{{ w.orderCount }} 单 / {{ w.totalQty }} 件</span>
            </div>
            <div class="wave-meta muted">拣货员：{{ w.pickerName || '-' }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="17">
        <el-card shadow="never" class="page-card" v-if="current">
          <div class="toolbar">
            <div class="section-title" style="margin: 0">复核打包 · {{ current.waveNo }}</div>
            <div class="spacer"></div>
            <el-button type="success" @click="onSubmit" :loading="saving">提交复核</el-button>
          </div>

          <div v-for="(o, idx) in orderList" :key="o.order.id" class="review-block">
            <div class="review-head">
              <span class="order-no">{{ o.order.orderNo }}</span>
              <span class="muted">{{ o.order.platform }} · {{ o.order.customerName }}</span>
              <div class="spacer"></div>
              <el-radio-group v-model="forms[idx].result" size="small">
                <el-radio-button label="通过" value="通过" />
                <el-radio-button label="异常" value="异常" />
              </el-radio-group>
            </div>

            <el-table :data="o.items" border size="small" style="margin-top: 8px">
              <el-table-column prop="skuCode" label="SKU" width="110" />
              <el-table-column prop="skuName" label="商品" min-width="140" />
              <el-table-column prop="location" label="库位" width="80" />
              <el-table-column prop="qty" label="订单量" width="80" align="center" />
              <el-table-column prop="pickedQty" label="实拣" width="80" align="center" />
              <el-table-column prop="shortageQty" label="缺货" width="80" align="center" />
            </el-table>

            <div class="error-area">
              <span class="muted">包装件数：</span>
              <el-input-number v-model="forms[idx].packCount" :min="1" :max="99" size="small" />
              <el-button link type="danger" size="small" @click="addError(idx)">+ 登记差错</el-button>
            </div>

            <div v-for="(e, ei) in forms[idx].errors" :key="ei" class="error-line">
              <el-select v-model="e.skuCode" placeholder="SKU" size="small" style="width: 150px" filterable>
                <el-option v-for="s in o.items" :key="s.skuCode" :label="s.skuCode" :value="s.skuCode" />
              </el-select>
              <el-select v-model="e.errorType" placeholder="差错类型" size="small" style="width: 110px">
                <el-option v-for="t in errorTypes" :key="t" :label="t" :value="t" />
              </el-select>
              <el-input-number v-model="e.qty" :min="1" :max="999" size="small" style="width: 110px" />
              <el-input v-model="e.description" placeholder="说明" size="small" style="width: 200px" />
              <el-button link type="danger" size="small" @click="forms[idx].errors.splice(ei, 1)">移除</el-button>
            </div>

            <el-input v-model="forms[idx].remark" placeholder="复核备注（可选）" size="small" style="margin-top: 8px" />
          </div>
        </el-card>
        <el-card shadow="never" class="page-card" v-else>
          <el-empty description="请从左侧选择一个波次开始复核" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { reviewDetail, submitReview } from '@/api/review'
import { reviewableWaves } from '@/api/wave'

const errorTypes = ['错拣', '漏拣', '多拣', '破损', '其他']

const waves = ref<any[]>([])
const current = ref<any>(null)
const orderList = ref<any[]>([])
const forms = ref<any[]>([])
const saving = ref(false)

async function loadWaves() {
  const res = await reviewableWaves()
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
  const res = await reviewDetail(w.id)
  current.value = res.data.wave
  orderList.value = res.data.orders || []
  forms.value = orderList.value.map((o: any) => ({
    orderId: o.order.id,
    result: '通过',
    packCount: o.order.itemCount || 1,
    remark: '',
    errors: [],
  }))
}

function addError(idx: number) {
  forms.value[idx].errors.push({ skuCode: '', errorType: '错拣', qty: 1, description: '' })
  forms.value[idx].result = '异常'
}

async function onSubmit() {
  for (const f of forms.value) {
    for (const e of f.errors) {
      if (!e.skuCode) {
        ElMessage.warning('请为每条差错选择 SKU')
        return
      }
    }
  }
  try {
    await ElMessageBox.confirm('确认提交复核结果吗？提交后波次进入待出库。', '提示', { type: 'warning' })
  } catch {
    return
  }
  saving.value = true
  try {
    await submitReview({
      waveId: current.value.id,
      reviews: forms.value.map((f) => ({
        orderId: f.orderId,
        result: f.errors.length > 0 ? '异常' : f.result,
        packCount: f.packCount,
        remark: f.remark,
        errors: f.errors,
      })),
    })
    ElMessage.success('复核已提交')
    current.value = null
    orderList.value = []
    forms.value = []
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

.review-block {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 14px;
}

.review-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.order-no {
  font-weight: 600;
}

.error-area {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.error-line {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 8px;
}
</style>
