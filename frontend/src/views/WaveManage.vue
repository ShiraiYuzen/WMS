<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="波次状态" clearable style="width: 140px" @change="reload">
          <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="波次号 / 拣货员 / 复核员" clearable style="width: 220px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer"></div>
        <el-button type="success" @click="openCreate">生成波次</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="waveNo" label="波次号" width="150" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderCount" label="订单数" width="80" align="center" />
        <el-table-column prop="itemCount" label="品项数" width="80" align="center" />
        <el-table-column prop="totalQty" label="件数" width="70" align="center" />
        <el-table-column prop="pickerName" label="拣货员" width="130">
          <template #default="{ row }">{{ row.pickerName || '未指派' }}</template>
        </el-table-column>
        <el-table-column prop="checkerName" label="复核员" width="130">
          <template #default="{ row }">{{ row.checkerName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === '待拣货'" link type="warning" @click="openAssign(row)">指派</el-button>
            <el-button v-if="row.status === '待拣货'" link type="danger" @click="onCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
                       v-model:current-page="query.page" v-model:page-size="query.size"
                       :page-sizes="[10, 20, 50]" @current-change="load" @size-change="reload" />
      </div>
    </el-card>

    <!-- 生成波次 -->
    <el-dialog v-model="createVisible" title="生成波次" width="880px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px"
                title="选择待分波订单，系统将按 SKU 聚合拣货清单、校验并锁定库存" />
      <el-table :data="pendingRows" v-loading="pendingLoading" border size="small" height="300"
                @selection-change="onSelect" ref="pendingTableRef">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="orderNo" label="订单号" width="150" />
        <el-table-column prop="platform" label="平台" width="80" />
        <el-table-column prop="customerName" label="客户" width="90" />
        <el-table-column prop="itemCount" label="品项" width="70" align="center" />
        <el-table-column prop="totalQty" label="件数" width="70" align="center" />
        <el-table-column prop="priority" label="优先级" width="80" align="center" />
        <el-table-column prop="createTime" label="下单时间" width="150" />
      </el-table>
      <el-form label-width="80px" style="margin-top: 14px">
        <el-form-item label="拣货员">
          <el-select v-model="createForm.pickerId" placeholder="指派拣货员（可选）" clearable style="width: 240px">
            <el-option v-for="u in pickers" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
          <span class="muted" style="margin-left: 10px">已选 {{ selected.length }} 单</span>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="createForm.remark" style="width: 420px" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">生成波次</el-button>
      </template>
    </el-dialog>

    <!-- 指派拣货员 -->
    <el-dialog v-model="assignVisible" title="指派拣货员" width="420px">
      <el-form label-width="80px">
        <el-form-item label="波次">{{ assignRow.waveNo }}</el-form-item>
        <el-form-item label="拣货员">
          <el-select v-model="assignPickerId" placeholder="选择拣货员" style="width: 100%">
            <el-option v-for="u in pickers" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">确定</el-button>
      </template>
    </el-dialog>

    <!-- 波次详情 -->
    <el-dialog v-model="detailVisible" title="波次详情" width="900px">
      <el-descriptions :column="3" border size="small" v-if="detail.wave">
        <el-descriptions-item label="波次号">{{ detail.wave.waveNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.wave.status }}</el-descriptions-item>
        <el-descriptions-item label="策略">{{ detail.wave.strategy }}</el-descriptions-item>
        <el-descriptions-item label="订单数">{{ detail.wave.orderCount }}</el-descriptions-item>
        <el-descriptions-item label="件数">{{ detail.wave.totalQty }}</el-descriptions-item>
        <el-descriptions-item label="拣货员">{{ detail.wave.pickerName || '未指派' }}</el-descriptions-item>
        <el-descriptions-item label="创建">{{ detail.wave.createTime }}</el-descriptions-item>
        <el-descriptions-item label="开始拣货">{{ detail.wave.startTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="拣货完成">{{ detail.wave.pickFinishTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="复核完成">{{ detail.wave.reviewFinishTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="出库时间">{{ detail.wave.outboundTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.wave.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs style="margin-top: 14px">
        <el-tab-pane label="拣货清单">
          <el-table :data="detail.items" border size="small">
            <el-table-column prop="location" label="库位" width="90" />
            <el-table-column prop="skuCode" label="SKU" width="110" />
            <el-table-column prop="skuName" label="商品" />
            <el-table-column prop="requiredQty" label="需求" width="70" align="center" />
            <el-table-column prop="pickedQty" label="实拣" width="70" align="center" />
            <el-table-column prop="shortageQty" label="缺货" width="70" align="center" />
            <el-table-column prop="status" label="状态" width="80" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="关联订单">
          <el-table :data="detail.orders" border size="small">
            <el-table-column prop="orderNo" label="订单号" width="150" />
            <el-table-column prop="platform" label="平台" width="90" />
            <el-table-column prop="customerName" label="客户" width="100" />
            <el-table-column prop="totalQty" label="件数" width="70" align="center" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="缺货记录">
          <el-table :data="detail.shortages" border size="small">
            <el-table-column prop="skuCode" label="SKU" width="110" />
            <el-table-column prop="skuName" label="商品" />
            <el-table-column prop="shortQty" label="缺货数" width="80" align="center" />
            <el-table-column prop="reason" label="原因" width="110" />
            <el-table-column prop="status" label="处理状态" width="100" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="复核 / 出库">
          <el-table :data="detail.reviews" border size="small" style="margin-bottom: 10px">
            <el-table-column prop="orderNo" label="订单号" width="150" />
            <el-table-column prop="checkerName" label="复核员" width="130" />
            <el-table-column prop="result" label="结果" width="80" />
            <el-table-column prop="errorCount" label="差错数" width="80" align="center" />
            <el-table-column prop="remark" label="备注" />
          </el-table>
          <el-table :data="detail.outbound" border size="small">
            <el-table-column prop="carrier" label="承运商" width="120" />
            <el-table-column prop="handoverTo" label="交接人" width="110" />
            <el-table-column prop="totalQty" label="件数" width="80" align="center" />
            <el-table-column prop="operatorName" label="操作人" width="130" />
            <el-table-column prop="handoverTime" label="交接时间" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assignPicker, cancelWave, createWave, listWaves, waveDetail } from '@/api/wave'
import { pendingOrders } from '@/api/order'
import { usersByRole } from '@/api/user'

const statusOptions = ['待拣货', '拣货中', '待复核', '待出库', '已出库', '已取消']

const loading = ref(false)
const saving = ref(false)
const pendingLoading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const pickers = ref<any[]>([])
const pendingRows = ref<any[]>([])
const selected = ref<any[]>([])
const pendingTableRef = ref()

const query = reactive({ status: '', keyword: '', page: 1, size: 10 })

const createVisible = ref(false)
const createForm = reactive<any>({ pickerId: null, remark: '' })

const assignVisible = ref(false)
const assignRow = ref<any>({})
const assignPickerId = ref<number | null>(null)

const detailVisible = ref(false)
const detail = reactive<any>({ wave: null, items: [], orders: [], shortages: [], reviews: [], outbound: [] })

function statusType(s: string) {
  switch (s) {
    case '待拣货': return 'warning'
    case '拣货中': return 'primary'
    case '待复核': return 'info'
    case '待出库': return 'success'
    case '已出库': return 'success'
    case '已取消': return 'danger'
    default: return 'info'
  }
}

async function load() {
  loading.value = true
  try {
    const res = await listWaves({ ...query })
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

function reset() {
  query.status = ''
  query.keyword = ''
  reload()
}

async function openCreate() {
  createForm.pickerId = null
  createForm.remark = ''
  selected.value = []
  createVisible.value = true
  pendingLoading.value = true
  try {
    const res = await pendingOrders()
    pendingRows.value = res.data
  } finally {
    pendingLoading.value = false
  }
}

function onSelect(rowsSel: any[]) {
  selected.value = rowsSel
}

async function submitCreate() {
  if (!selected.value.length) {
    ElMessage.warning('请至少选择一个待分波订单')
    return
  }
  saving.value = true
  try {
    const res = await createWave({
      orderIds: selected.value.map((r) => r.id),
      pickerId: createForm.pickerId,
      remark: createForm.remark,
    })
    ElMessage.success(`波次 ${res.data.waveNo} 生成成功`)
    createVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

function openAssign(row: any) {
  assignRow.value = row
  assignPickerId.value = row.pickerId || null
  assignVisible.value = true
}

async function submitAssign() {
  if (!assignPickerId.value) {
    ElMessage.warning('请选择拣货员')
    return
  }
  await assignPicker(assignRow.value.id, assignPickerId.value)
  ElMessage.success('已指派')
  assignVisible.value = false
  load()
}

async function onCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确定取消波次 ${row.waveNo} 吗？库存锁定将被释放，订单退回待分波。`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await cancelWave(row.id)
  ElMessage.success('已取消')
  load()
}

async function openDetail(row: any) {
  const res = await waveDetail(row.id)
  Object.assign(detail, res.data)
  detailVisible.value = true
}

onMounted(async () => {
  load()
  const res = await usersByRole('PICKER')
  pickers.value = res.data
})
</script>
