<template>
  <div class="page-container">
    <PageHeader
      title="订单池"
      description="承接电商平台推送的订单。勾选订单汇聚入池后，即可在「波次管理」中批量生成拣货波次。"
      :count="total"
    >
      <template #actions>
        <el-button type="primary" :icon="Promotion" :disabled="!selection.length" @click="onPoolSelected">
          汇聚选中（{{ selection.length }}）
        </el-button>
        <el-button :icon="Download" @click="onPoolAll">一键汇聚全部</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <!-- ======================= 筛选区 ======================= -->
    <div class="card card-pad mb-12">
      <el-form :model="query" inline class="filter-form">
        <el-form-item label="订单号 / 收货人">
          <el-input v-model="query.orderNo" placeholder="支持订单号或收货人" clearable style="width: 190px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="o in dict.options('orderStatus')" :key="o.code" :label="o.desc" :value="o.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="query.platform" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="o in dict.options('platform')" :key="o.code" :label="o.desc" :value="o.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="承运商">
          <el-select v-model="query.carrierCode" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="c in carriers" :key="c.carrierCode" :label="c.carrierName" :value="c.carrierCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="query.priority" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="o in dict.options('priority')" :key="o.code" :label="o.desc" :value="o.code" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- ======================= 状态速览 ======================= -->
    <el-row :gutter="12" class="mb-12">
      <el-col v-for="s in statusTiles" :key="s.code" :xs="12" :sm="8" :md="4" :lg="3">
        <div class="tile" :class="{ active: query.status === s.code }" @click="quickFilter(s.code)">
          <div class="tile-num num">{{ s.count }}</div>
          <div class="tile-lbl">{{ s.desc }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- ======================= 表格 ======================= -->
    <div class="card">
      <el-table
        v-loading="loading"
        :data="rows"
        row-key="orderNo"
        stripe
        height="calc(100vh - 452px)"
        @selection-change="(v) => (selection = v)"
      >
        <el-table-column type="selection" width="46" :selectable="(r) => r.poolable" />
        <el-table-column label="订单号" width="170" fixed>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">
              <span class="mono">{{ row.orderNo }}</span>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="platform" label="平台" width="90" />
        <el-table-column label="状态" width="112">
          <template #default="{ row }">
            <StatusTag :code="row.status" group="orderStatus" />
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="78">
          <template #default="{ row }">
            <el-tag v-if="row.priority === 'URGENT'" type="danger" size="small" effect="dark">加急</el-tag>
            <span v-else class="text-secondary">普通</span>
          </template>
        </el-table-column>
        <el-table-column label="收货信息" min-width="200">
          <template #default="{ row }">
            <div class="receiver">
              <span class="rcv-name">{{ row.receiverName }}</span>
              <span class="rcv-phone">{{ row.receiverPhone }}</span>
            </div>
            <div class="rcv-addr ellipsis">{{ row.province }} {{ row.city }} {{ row.receiverAddress }}</div>
          </template>
        </el-table-column>
        <el-table-column label="承运商" width="105">
          <template #default="{ row }">{{ row.carrierName }}</template>
        </el-table-column>
        <el-table-column label="SKU / 件数" width="105" align="center">
          <template #default="{ row }">
            <span class="num">{{ row.skuCount }}</span>
            <span class="text-secondary"> / </span>
            <span class="num">{{ row.totalQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatMoney(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="归属波次" width="150">
          <template #default="{ row }">
            <el-link v-if="row.waveNo" type="primary" :underline="false" @click="gotoWave(row.waveNo)">
              <span class="mono">{{ row.waveNo }}</span>
            </el-link>
            <span v-else class="text-secondary">—</span>
          </template>
        </el-table-column>
        <el-table-column label="支付时间" width="150">
          <template #default="{ row }">
            <span class="text-secondary">{{ formatTimeShort(row.payTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.poolable"
              link
              type="danger"
              size="small"
              @click="onCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="load"
        @current-change="load"
      />
    </div>

    <!-- ======================= 订单详情抽屉 ======================= -->
    <el-drawer v-model="detailVisible" title="订单详情" size="620px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="订单号">
            <span class="mono">{{ detail.orderNo }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <StatusTag :code="detail.status" group="orderStatus" />
          </el-descriptions-item>
          <el-descriptions-item label="来源平台">{{ detail.platform }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ detail.priorityDesc }}</el-descriptions-item>
          <el-descriptions-item label="承运商">{{ detail.carrierName }}</el-descriptions-item>
          <el-descriptions-item label="归属波次">
            <span class="mono">{{ detail.waveNo || '—' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="收货人">{{ detail.receiverName }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detail.receiverPhone }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">{{ detail.province }}{{ detail.city }}{{ detail.receiverAddress }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatTime(detail.payTime) }}</el-descriptions-item>
          <el-descriptions-item label="要求送达">{{ formatTime(detail.expectedDeliveryTime) }}</el-descriptions-item>
          <el-descriptions-item label="买家备注" :span="2">
            {{ detail.buyerRemark || '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="drawer-section-title">
          商品明细
          <span class="text-secondary" style="font-weight: 400; font-size: 12px">
            共 {{ detail.skuCount }} 种 / {{ detail.totalQty }} 件
          </span>
        </div>

        <el-table :data="detail.items" size="small" border>
          <el-table-column type="index" label="#" width="44" align="center" />
          <el-table-column prop="skuCode" label="SKU" width="105">
            <template #default="{ row }"><span class="mono">{{ row.skuCode }}</span></template>
          </el-table-column>
          <el-table-column prop="skuName" label="商品名称" min-width="140" />
          <el-table-column prop="spec" label="规格" width="90" />
          <el-table-column prop="locationCode" label="拣货库位" width="110">
            <template #default="{ row }"><span class="mono">{{ row.locationCode }}</span></template>
          </el-table-column>
          <el-table-column label="数量" width="80" align="right">
            <template #default="{ row }"><span class="num">{{ row.qty }}</span> {{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="单价" width="85" align="right">
            <template #default="{ row }"><span class="num">{{ row.price.toFixed(2) }}</span></template>
          </el-table-column>
          <el-table-column label="小计" width="95" align="right">
            <template #default="{ row }"><span class="num">{{ row.amount.toFixed(2) }}</span></template>
          </el-table-column>
        </el-table>

        <div class="drawer-total">
          订单金额：<span class="num total-amount">{{ formatMoney(detail.amount) }}</span>
        </div>

        <div v-if="detail.waveNo" class="mt-16">
          <div class="drawer-section-title">作业流转轨迹</div>
          <el-timeline>
            <el-timeline-item
              v-for="l in trace"
              :key="l.id"
              :timestamp="formatTime(l.operateTime)"
              placement="top"
              :type="traceType(l.bizType)"
            >
              <div class="trace-title">{{ l.actionDesc }}</div>
              <div class="trace-detail">
                {{ l.operatorName || '系统' }}{{ l.operatorRole ? ` · ${l.operatorRole}` : '' }}
                <span v-if="l.detail"> — {{ l.detail }}</span>
              </div>
            </el-timeline-item>
            <el-timeline-item v-if="!trace.length" timestamp="" placement="top">
              <span class="text-secondary">暂无流转记录</span>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Promotion, Download, Refresh, Search, RefreshLeft } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { orderApi, baseApi, logApi } from '@/api'
import { useDictStore } from '@/stores/dict'
import { formatTime, formatTimeShort, formatMoney } from '@/utils'

const router = useRouter()
const dict = useDictStore()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const selection = ref([])
const carriers = ref([])
const counts = ref({})

const query = ref({
  pageNum: 1,
  pageSize: 20,
  orderNo: '',
  status: '',
  platform: '',
  carrierCode: '',
  priority: ''
})

const detailVisible = ref(false)
const detail = ref(null)
const trace = ref([])

/* ------------------------------ 状态速览 ------------------------------ */

const statusTiles = computed(() =>
  dict.options('orderStatus').map((o) => ({
    code: o.code,
    desc: o.desc,
    count: counts.value[o.code] || 0
  }))
)

/* ------------------------------ 加载 ------------------------------ */

async function load() {
  loading.value = true
  try {
    const res = await orderApi.page(query.value)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadCounts() {
  counts.value = await orderApi.countByStatus()
}

function search() {
  query.value.pageNum = 1
  load()
}

function reset() {
  query.value = {
    pageNum: 1,
    pageSize: query.value.pageSize,
    orderNo: '',
    status: '',
    platform: '',
    carrierCode: '',
    priority: ''
  }
  load()
}

function quickFilter(code) {
  query.value.status = query.value.status === code ? '' : code
  query.value.pageNum = 1
  load()
}

/* ------------------------------ 操作 ------------------------------ */

async function onPoolSelected() {
  const nos = selection.value.map((r) => r.orderNo)
  const res = await orderApi.pool(nos)
  ElMessage.success(`已汇聚 ${res} 笔订单，可在「波次管理」生成波次`)
  selection.value = []
  await Promise.all([load(), loadCounts()])
  window.__wmsRefreshBadge?.()
}

async function onPoolAll() {
  await ElMessageBox.confirm(
    '将把所有「新建待汇聚」的订单一次性汇聚入池，是否继续？',
    '一键汇聚',
    { type: 'warning', confirmButtonText: '确定汇聚' }
  )
  const res = await orderApi.poolAll()
  ElMessage.success(`已汇聚 ${res} 笔订单`)
  await Promise.all([load(), loadCounts()])
  window.__wmsRefreshBadge?.()
}

async function onCancel(row) {
  await ElMessageBox.confirm(`确认取消订单 ${row.orderNo}？`, '取消订单', {
    type: 'warning',
    confirmButtonText: '确认取消'
  })
  await orderApi.cancel(row.orderNo)
  ElMessage.success('订单已取消')
  await Promise.all([load(), loadCounts()])
}

async function openDetail(row) {
  detail.value = await orderApi.detail(row.orderNo)
  detailVisible.value = true
  trace.value = []
  if (detail.value.waveNo) {
    try {
      trace.value = await logApi.trace(detail.value.waveNo)
    } catch {
      trace.value = []
    }
  }
}

function gotoWave(waveNo) {
  router.push(`/wave/detail/${waveNo}`)
}

function traceType(bizType) {
  return {
    WAVE: 'primary',
    PICK: 'warning',
    REVIEW: 'success',
    HANDOVER: 'info',
    SHORTAGE: 'danger'
  }[bizType] || 'primary'
}

onMounted(async () => {
  carriers.value = await baseApi.carriers()
  await Promise.all([load(), loadCounts()])
})
</script>

<style scoped lang="scss">
.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;

  :deep(.el-form-item) {
    margin-bottom: 6px;
    margin-right: 16px;
  }
}

/* 状态速览块 */
.tile {
  padding: 11px 14px;
  border-radius: 9px;
  background: #fff;
  border: 1px solid $border-base;
  cursor: pointer;
  transition: all 0.16s;

  &:hover {
    border-color: #bfdbfe;
    background: #f8fbff;
  }

  &.active {
    border-color: $color-primary;
    background: #eff6ff;
    box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);

    .tile-num {
      color: $color-primary;
    }
  }
}

.tile-num {
  font-size: 20px;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.15;
}

.tile-lbl {
  font-size: 11.5px;
  color: $text-secondary;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 收货信息两行 */
.receiver {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rcv-name {
  font-weight: 500;
  color: $text-primary;
}

.rcv-phone {
  font-size: 12px;
  color: $text-placeholder;
}

.rcv-addr {
  font-size: 12px;
  color: $text-secondary;
  margin-top: 1px;
}

/* 抽屉 */
.drawer-section-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 600;
  color: $text-primary;
  margin: 20px 0 10px;
}

.drawer-total {
  margin-top: 12px;
  text-align: right;
  font-size: 13px;
  color: $text-secondary;
}

.total-amount {
  font-size: 17px;
  font-weight: 700;
  color: $color-danger;
}

.trace-title {
  font-size: 13px;
  font-weight: 500;
  color: $text-primary;
}

.trace-detail {
  font-size: 12px;
  color: $text-secondary;
  margin-top: 2px;
}
</style>
