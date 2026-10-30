<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <div class="section-title">待出库波次</div>
      <el-table :data="pendingWaves" border size="small" v-loading="loading">
        <el-table-column prop="waveNo" label="波次号" width="160" />
        <el-table-column prop="orderCount" label="订单数" width="90" align="center" />
        <el-table-column prop="totalQty" label="件数" width="80" align="center" />
        <el-table-column prop="pickerName" label="拣货员" width="140" />
        <el-table-column prop="checkerName" label="复核员" width="140" />
        <el-table-column prop="reviewFinishTime" label="复核完成时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openHandover(row)">出库交接</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !pendingWaves.length" description="暂无待出库波次" :image-size="70" />
    </el-card>

    <el-card shadow="never" class="page-card" style="margin-top: 14px">
      <div class="toolbar">
        <div class="section-title" style="margin: 0">出库交接记录</div>
        <div class="spacer"></div>
        <el-input v-model="query.keyword" placeholder="波次号 / 承运商 / 交接人" clearable style="width: 220px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
      </div>
      <el-table :data="records" border stripe size="small" v-loading="recordLoading">
        <el-table-column prop="waveNo" label="波次号" width="160" />
        <el-table-column prop="orderCount" label="订单数" width="90" align="center" />
        <el-table-column prop="totalQty" label="出库件数" width="100" align="center" />
        <el-table-column prop="carrier" label="承运商" width="120" />
        <el-table-column prop="handoverTo" label="交接人" width="110" />
        <el-table-column prop="operatorName" label="操作人" width="140" />
        <el-table-column prop="handoverTime" label="交接时间" width="170" />
        <el-table-column prop="remark" label="备注" />
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       v-model:current-page="query.page" v-model:page-size="query.size" @current-change="loadRecords" />
      </div>
    </el-card>

    <el-dialog v-model="handoverVisible" title="出库交接" width="460px">
      <el-form label-width="90px">
        <el-form-item label="波次号">{{ handoverRow.waveNo }}</el-form-item>
        <el-form-item label="承运商">
          <el-select v-model="handoverForm.carrier" placeholder="选择承运商" style="width: 100%" filterable allow-create>
            <el-option v-for="c in carriers" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="交接人"><el-input v-model="handoverForm.handoverTo" placeholder="快递/物流交接人" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="handoverForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handoverVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitHandover">确认交接</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listOutbounds, submitOutbound } from '@/api/outbound'
import { outboundableWaves } from '@/api/wave'

const carriers = ['顺丰速运', '京东物流', '中通快递', '圆通速递', '韵达快递', '德邦物流']

const loading = ref(false)
const recordLoading = ref(false)
const saving = ref(false)
const pendingWaves = ref<any[]>([])
const records = ref<any[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 10 })

const handoverVisible = ref(false)
const handoverRow = ref<any>({})
const handoverForm = reactive({ carrier: '顺丰速运', handoverTo: '', remark: '' })

async function loadPending() {
  loading.value = true
  try {
    const res = await outboundableWaves()
    pendingWaves.value = res.data
  } finally {
    loading.value = false
  }
}

async function loadRecords() {
  recordLoading.value = true
  try {
    const res = await listOutbounds({ ...query })
    records.value = res.data.list
    total.value = res.data.total
  } finally {
    recordLoading.value = false
  }
}

function reload() {
  query.page = 1
  loadRecords()
}

function openHandover(row: any) {
  handoverRow.value = row
  handoverForm.carrier = '顺丰速运'
  handoverForm.handoverTo = ''
  handoverForm.remark = ''
  handoverVisible.value = true
}

async function submitHandover() {
  try {
    await ElMessageBox.confirm(`确认波次 ${handoverRow.value.waveNo} 出库交接？出库后将扣减库存。`, '提示', { type: 'warning' })
  } catch {
    return
  }
  saving.value = true
  try {
    await submitOutbound({
      waveId: handoverRow.value.id,
      carrier: handoverForm.carrier,
      handoverTo: handoverForm.handoverTo,
      remark: handoverForm.remark,
    })
    ElMessage.success('出库交接完成')
    handoverVisible.value = false
    await loadPending()
    await loadRecords()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadPending()
  loadRecords()
})
</script>
