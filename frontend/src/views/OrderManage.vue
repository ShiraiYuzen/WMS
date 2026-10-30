<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="订单状态" clearable style="width: 140px" @change="reload">
          <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="query.platform" placeholder="平台" clearable style="width: 130px" @change="reload">
          <el-option v-for="p in platformOptions" :key="p" :label="p" :value="p" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="订单号 / 客户" clearable style="width: 200px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer"></div>
        <el-button type="success" @click="openCreate">新建订单</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="orderNo" label="订单号" width="160" />
        <el-table-column prop="platform" label="平台" width="90" />
        <el-table-column prop="customerName" label="客户" width="90" />
        <el-table-column prop="customerPhone" label="联系电话" width="120" />
        <el-table-column prop="itemCount" label="品项" width="70" align="center" />
        <el-table-column prop="totalQty" label="件数" width="70" align="center" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="waveNo" label="所属波次" width="130">
          <template #default="{ row }">{{ row.waveNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.priority === '加急'" size="small" type="danger">加急</el-tag>
            <span v-else>普通</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" width="160" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === '待分波'" link type="danger" @click="onCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
                       v-model:current-page="query.page" v-model:page-size="query.size"
                       :page-sizes="[10, 20, 50]" @current-change="load" @size-change="reload" />
      </div>
    </el-card>

    <!-- 新建订单 -->
    <el-dialog v-model="createVisible" title="新建订单" width="720px">
      <el-form :model="createForm" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="平台"><el-select v-model="createForm.platform" style="width: 100%">
            <el-option v-for="p in platformOptions" :key="p" :label="p" :value="p" />
          </el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="优先级"><el-select v-model="createForm.priority" style="width: 100%">
            <el-option label="普通" value="普通" /><el-option label="加急" value="加急" />
          </el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户姓名"><el-input v-model="createForm.customerName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="联系电话"><el-input v-model="createForm.customerPhone" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="收货地址"><el-input v-model="createForm.address" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="商品明细">
          <div style="width: 100%">
            <div v-for="(it, idx) in createForm.items" :key="idx" class="item-line">
              <el-select v-model="it.skuCode" filterable placeholder="选择 SKU" style="width: 340px">
                <el-option v-for="s in skus" :key="s.skuCode" :label="`${s.skuCode} ${s.name}（${s.location}）`" :value="s.skuCode" />
              </el-select>
              <el-input-number v-model="it.qty" :min="1" :max="999" />
              <el-button link type="danger" @click="createForm.items.splice(idx, 1)">删除</el-button>
            </div>
            <el-button link type="primary" @click="createForm.items.push({ skuCode: '', qty: 1 })">+ 添加明细</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 订单详情 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="680px">
      <el-descriptions :column="2" border size="small" v-if="detail.order">
        <el-descriptions-item label="订单号">{{ detail.order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="平台">{{ detail.order.platform }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.order.customerName }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ detail.order.customerPhone }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.order.status }}</el-descriptions-item>
        <el-descriptions-item label="所属波次">{{ detail.order.waveNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收货地址" :span="2">{{ detail.order.address }}</el-descriptions-item>
      </el-descriptions>
      <div class="section-title" style="margin-top: 14px">商品明细</div>
      <el-table :data="detail.items" border size="small">
        <el-table-column prop="skuCode" label="SKU" width="110" />
        <el-table-column prop="skuName" label="商品" />
        <el-table-column prop="location" label="库位" width="90" />
        <el-table-column prop="qty" label="数量" width="70" align="center" />
        <el-table-column prop="pickedQty" label="实拣" width="70" align="center" />
        <el-table-column prop="shortageQty" label="缺货" width="70" align="center" />
        <el-table-column prop="status" label="状态" width="80" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addOrder, cancelOrder, listOrders, orderDetail } from '@/api/order'
import { allSkus } from '@/api/sku'

const statusOptions = ['待分波', '已分波', '拣货中', '已拣货', '已复核', '已出库', '已取消']
const platformOptions = ['天猫', '京东', '抖音', '拼多多', '手工录入']

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const skus = ref<any[]>([])

const query = reactive({ status: '', platform: '', keyword: '', page: 1, size: 10 })

const createVisible = ref(false)
const createForm = reactive<any>({
  platform: '天猫', priority: '普通', customerName: '', customerPhone: '', address: '',
  items: [{ skuCode: '', qty: 1 }],
})

const detailVisible = ref(false)
const detail = reactive<any>({ order: null, items: [] })

function statusType(s: string) {
  switch (s) {
    case '待分波': return 'warning'
    case '已分波': return 'primary'
    case '拣货中': return 'info'
    case '已拣货': return ''
    case '已复核': return 'success'
    case '已出库': return 'success'
    case '已取消': return 'danger'
    default: return 'info'
  }
}

async function load() {
  loading.value = true
  try {
    const res = await listOrders({ ...query })
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
  query.platform = ''
  query.keyword = ''
  reload()
}

function openCreate() {
  createForm.platform = '天猫'
  createForm.priority = '普通'
  createForm.customerName = ''
  createForm.customerPhone = ''
  createForm.address = ''
  createForm.items = [{ skuCode: '', qty: 1 }]
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.customerName) {
    ElMessage.warning('请填写客户姓名')
    return
  }
  const items = createForm.items.filter((i: any) => i.skuCode)
  if (!items.length) {
    ElMessage.warning('请至少添加一条商品明细')
    return
  }
  saving.value = true
  try {
    await addOrder({ ...createForm, items })
    ElMessage.success('订单已创建')
    createVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function openDetail(row: any) {
  const res = await orderDetail(row.id)
  detail.order = res.data.order
  detail.items = res.data.items
  detailVisible.value = true
}

async function onCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确定取消订单 ${row.orderNo} 吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await cancelOrder(row.id)
  ElMessage.success('已取消')
  load()
}

onMounted(async () => {
  load()
  const res = await allSkus()
  skus.value = res.data
})
</script>

<style scoped>
.item-line {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}
</style>
