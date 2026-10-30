<template>
  <div class="page-container">
    <PageHeader
      title="缺货处理"
      description="拣货员在库位上报缺货后进入本页。波次员按「待处理优先」排序逐条决策：拆单出库 / 取消明细 / 替代商品 / 等待补货 / 整单取消，处理后归档留痕。"
      :count="total"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
      </template>
    </PageHeader>

    <!-- ======================= 待处理 + 原因分布 ======================= -->
    <div class="overview-row mb-12">
      <div class="card card-pad pending-card" :class="{ 'is-clear': pendingCount === 0 }">
        <div class="pc-left">
          <div class="pc-label">待处理缺货</div>
          <div class="pc-value">
            <span class="num">{{ pendingCount }}</span>
            <span class="pc-unit">条</span>
          </div>
          <div class="pc-sub">
            {{ pendingCount === 0 ? '暂无待处理缺货，作业通畅' : '请及时决策，避免影响波次出库时效' }}
          </div>
        </div>
        <el-icon class="pc-icon"><Warning /></el-icon>
      </div>

      <div class="card card-pad dist-card">
        <div class="panel-head">
          <span class="panel-title">缺货原因分布</span>
          <span class="panel-sub">全部记录</span>
        </div>
        <ChartBox v-if="distList.length" :option="distOption" height="132px" />
        <el-empty v-else description="暂无缺货记录" :image-size="54" />
      </div>
    </div>

    <!-- ======================= 筛选 ======================= -->
    <div class="card card-pad mb-12">
      <el-form :model="query" inline class="filter-form">
        <el-form-item label="缺货单号">
          <el-input v-model="query.shortageNo" placeholder="模糊匹配" clearable style="width: 170px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="波次号">
          <el-input v-model="query.waveNo" placeholder="精确匹配" clearable style="width: 170px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="SKU">
          <el-input v-model="query.skuCode" placeholder="精确匹配" clearable style="width: 150px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="缺货原因">
          <el-select v-model="query.reason" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="o in dict.options('shortageReason')" :key="o.code" :label="o.desc" :value="o.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="o in dict.options('shortageStatus')" :key="o.code" :label="o.desc" :value="o.code" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- ======================= 表格 ======================= -->
    <div class="card">
      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        height="calc(100vh - 596px)"
        :row-class-name="rowClass"
      >
        <el-table-column label="缺货单号" width="170" fixed>
          <template #default="{ row }"><span class="mono">{{ row.shortageNo }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <StatusTag :code="row.status" group="shortageStatus" />
          </template>
        </el-table-column>
        <el-table-column label="波次号" width="165">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="gotoWave(row.waveNo)">
              <span class="mono">{{ row.waveNo }}</span>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="订单号" width="165">
          <template #default="{ row }"><span class="mono">{{ row.orderNo || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="库位" width="120">
          <template #default="{ row }"><span class="mono">{{ row.locationCode }}</span></template>
        </el-table-column>
        <el-table-column label="商品" min-width="200">
          <template #default="{ row }">
            <div class="sku-cell">
              <span class="sku-name">{{ row.skuName }}</span>
              <span class="sku-code mono">{{ row.skuCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="应拣" width="80" align="right">
          <template #default="{ row }"><span class="num">{{ row.shouldQty }}</span></template>
        </el-table-column>
        <el-table-column label="缺货" width="80" align="right">
          <template #default="{ row }"><span class="num text-danger">{{ row.shortageQty }}</span></template>
        </el-table-column>
        <el-table-column label="缺货原因" width="140">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="warning">{{ dict.text('shortageReason', row.reason) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理方式" width="130">
          <template #default="{ row }">
            <span v-if="row.handleType" class="handle-type">
              {{ dict.text('shortageHandleType', row.handleType) }}
            </span>
            <span v-else class="text-secondary">—</span>
          </template>
        </el-table-column>
        <el-table-column label="上报人 / 时间" width="170">
          <template #default="{ row }">
            <div class="two-line">
              <span>{{ row.reporterName || '—' }}</span>
              <span class="text-secondary">{{ formatTimeShort(row.reportTime) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="primary"
              size="small"
              @click="openHandle(row)"
            >
              处理
            </el-button>
            <el-button
              v-if="row.status === 'HANDLED'"
              link
              type="success"
              size="small"
              @click="onClose(row)"
            >
              归档
            </el-button>
            <el-button link size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="load"
        @current-change="load"
      />
    </div>

    <!-- ======================= 处理对话框 ======================= -->
    <el-dialog v-model="handleVisible" title="缺货处理" width="620px" :close-on-click-modal="false">
      <div v-if="current" class="handle-info">
        <div class="hi-row">
          <span class="hi-label">缺货单号</span><span class="mono">{{ current.shortageNo }}</span>
        </div>
        <div class="hi-row">
          <span class="hi-label">商品</span>
          <span>{{ current.skuName }} <span class="mono text-secondary">{{ current.skuCode }}</span></span>
        </div>
        <div class="hi-row">
          <span class="hi-label">库位</span><span class="mono">{{ current.locationCode }}</span>
        </div>
        <div class="hi-row">
          <span class="hi-label">缺货数量</span><span class="num text-danger">{{ current.shortageQty }}</span>
        </div>
        <div class="hi-row">
          <span class="hi-label">上报原因</span>
          <span>{{ dict.text('shortageReason', current.reason) }}</span>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="mt-16">
        <el-form-item label="处理方式" prop="handleType">
          <el-radio-group v-model="form.handleType" class="handle-group">
            <el-radio v-for="o in dict.options('shortageHandleType')" :key="o.code" :value="o.code" class="handle-radio">
              <span class="hr-title">{{ o.desc }}</span>
              <span v-if="o.detail" class="hr-detail">{{ o.detail }}</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.handleType === 'SUBSTITUTE'" label="替代 SKU" prop="substituteSkuCode">
          <el-select
            v-model="form.substituteSkuCode"
            filterable
            placeholder="选择同品类可替代商品"
            style="width: 100%"
          >
            <el-option
              v-for="s in substituteOptions"
              :key="s.skuCode"
              :label="`${s.skuName}（${s.spec}）`"
              :value="s.skuCode"
            >
              <span>{{ s.skuName }}</span>
              <span class="opt-sub mono">{{ s.skuCode }} · {{ s.category }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="处理备注">
          <el-input
            v-model="form.handleRemark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="例如：已通知采购补货，预计明日到仓；或已与客户沟通改发替代品"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">确认处理</el-button>
      </template>
    </el-dialog>

    <!-- ======================= 详情对话框 ======================= -->
    <el-dialog v-model="detailVisible" title="缺货记录详情" width="600px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="缺货单号">
          <span class="mono">{{ current.shortageNo }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag :code="current.status" group="shortageStatus" />
        </el-descriptions-item>
        <el-descriptions-item label="波次号">
          <span class="mono">{{ current.waveNo }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="拣货任务">
          <span class="mono">{{ current.taskNo }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="订单号">
          <span class="mono">{{ current.orderNo || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="库位">
          <span class="mono">{{ current.locationCode }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="商品" :span="2">
          {{ current.skuName }} <span class="mono text-secondary">{{ current.skuCode }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="应拣数量">
          <span class="num">{{ current.shouldQty }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="缺货数量">
          <span class="num text-danger">{{ current.shortageQty }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="上报人">
          {{ current.reporterName || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="上报时间">
          {{ formatTime(current.reportTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="处理方式">
          {{ current.handleType ? dict.text('shortageHandleType', current.handleType) : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="替代 SKU">
          <span class="mono">{{ current.substituteSkuCode || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="处理人">
          {{ current.handlerName || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="处理时间">
          {{ formatTime(current.handleTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="处理备注" :span="2">
          {{ current.handleRemark || '—' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, RefreshLeft, Warning } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import ChartBox from '@/components/ChartBox.vue'
import { shortageApi, baseApi } from '@/api'
import { useDictStore } from '@/stores/dict'
import { useAppStore } from '@/stores/app'
import { formatTime, formatTimeShort } from '@/utils'

const router = useRouter()
const dict = useDictStore()
const app = useAppStore()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const pendingCount = ref(0)
const distList = ref([])
const skus = ref([])

const query = ref({
  pageNum: 1,
  pageSize: 20,
  shortageNo: '',
  waveNo: '',
  skuCode: '',
  reason: '',
  status: ''
})

/* ------------------------------ 处理表单 ------------------------------ */

const handleVisible = ref(false)
const detailVisible = ref(false)
const submitting = ref(false)
const current = ref(null)
const formRef = ref(null)

const form = reactive({
  handleType: 'SPLIT',
  substituteSkuCode: '',
  handleRemark: ''
})

const rules = {
  handleType: [{ required: true, message: '请选择处理方式', trigger: 'change' }],
  substituteSkuCode: [{ required: true, message: '请选择替代 SKU', trigger: 'change' }]
}

/** 替代候选：同品类、且不是缺货商品本身 */
const substituteOptions = computed(() => {
  if (!current.value) return []
  const self = skus.value.find((s) => s.skuCode === current.value.skuCode)
  if (!self) return skus.value
  return skus.value.filter((s) => s.category === self.category && s.skuCode !== self.skuCode)
})

/* ------------------------------ 原因分布图 ------------------------------ */

const distOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 8, right: 24, top: 6, bottom: 0, containLabel: true },
  xAxis: { type: 'value', show: false },
  yAxis: {
    type: 'category',
    inverse: true,
    data: distList.value.map((d) => d.name),
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisTick: { show: false },
    axisLabel: { color: '#64748b', fontSize: 11 }
  },
  series: [
    {
      type: 'bar',
      barMaxWidth: 14,
      itemStyle: { color: '#f59e0b', borderRadius: [0, 7, 7, 0] },
      label: { show: true, position: 'right', color: '#94a3b8', fontSize: 11 },
      data: distList.value.map((d) => d.count)
    }
  ]
}))

/* ------------------------------ 数据加载 ------------------------------ */

async function load() {
  loading.value = true
  try {
    const res = await shortageApi.page(query.value)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  const [pending, dist] = await Promise.all([
    shortageApi.pendingCount(),
    shortageApi.reasonDistribution()
  ])
  pendingCount.value = pending || 0
  distList.value = Object.entries(dist || {})
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
}

async function refreshAll() {
  await Promise.all([load(), loadStats()])
}

function search() {
  query.value.pageNum = 1
  load()
}

function reset() {
  query.value = {
    pageNum: 1,
    pageSize: query.value.pageSize,
    shortageNo: '',
    waveNo: '',
    skuCode: '',
    reason: '',
    status: ''
  }
  load()
}

function rowClass({ row }) {
  return row.status === 'PENDING' ? 'row-pending' : ''
}

/* ------------------------------ 交互 ------------------------------ */

function openHandle(row) {
  current.value = row
  form.handleType = 'SPLIT'
  form.substituteSkuCode = ''
  form.handleRemark = ''
  handleVisible.value = true
}

function openDetail(row) {
  current.value = row
  detailVisible.value = true
}

async function submitHandle() {
  await formRef.value.validate()
  submitting.value = true
  try {
    await shortageApi.handle({
      id: current.value.id,
      handleType: form.handleType,
      substituteSkuCode: form.handleType === 'SUBSTITUTE' ? form.substituteSkuCode : null,
      handleRemark: form.handleRemark,
      handlerNo: app.currentUser.employeeNo,
      handlerName: app.currentUser.employeeName
    })
    ElMessage.success('缺货已处理')
    handleVisible.value = false
    await refreshAll()
    window.__wmsRefreshBadge?.()
  } finally {
    submitting.value = false
  }
}

async function onClose(row) {
  await ElMessageBox.confirm(
    `确认将缺货单 ${row.shortageNo} 归档？归档后不再出现在待处理列表。`,
    '归档确认',
    { type: 'warning', confirmButtonText: '确认归档', cancelButtonText: '取消' }
  )
  await shortageApi.close(row.id)
  ElMessage.success('已归档')
  await refreshAll()
  window.__wmsRefreshBadge?.()
}

function gotoWave(waveNo) {
  router.push(`/wave/detail/${waveNo}`)
}

onMounted(async () => {
  skus.value = (await baseApi.skuPage({ pageNum: 1, pageSize: 500 })).records
  await refreshAll()
})
</script>

<style scoped lang="scss">
.filter-form {
  :deep(.el-form-item) {
    margin-bottom: 6px;
    margin-right: 16px;
  }
}

/* 概览区 */
.overview-row {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 12px;
}

.pending-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #fff7ed, #fffbeb);
  border-color: #fed7aa;

  &.is-clear {
    background: linear-gradient(135deg, #f0fdf4, #f8fafc);
    border-color: #bbf7d0;

    .pc-icon {
      color: $color-success;
      background: rgba(22, 163, 74, 0.1);
    }
  }
}

.pc-label {
  font-size: 12.5px;
  color: $text-secondary;
  margin-bottom: 6px;
}

.pc-value {
  display: flex;
  align-items: baseline;
  gap: 4px;

  .num {
    font-size: 28px;
    font-weight: 700;
    color: $color-warning;
    line-height: 1.1;
  }
}

.pending-card.is-clear .pc-value .num {
  color: $color-success;
}

.pc-unit {
  font-size: 12px;
  color: $text-placeholder;
}

.pc-sub {
  margin-top: 6px;
  font-size: 11.5px;
  color: $text-placeholder;
}

.pc-icon {
  // 注意：全局 box-sizing 为 border-box，el-icon 默认 width/height 为 1em，
  // 若只给 padding 会把内容盒压成 2px，图标几乎不可见 —— 必须显式给宽高。
  width: 50px;
  height: 50px;
  flex-shrink: 0;
  font-size: 24px;
  color: $color-warning;
  background: rgba(245, 158, 11, 0.12);
  border-radius: 12px;
}

.dist-card {
  min-width: 0;
}

.panel-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

.panel-title {
  font-size: 13.5px;
  font-weight: 600;
  color: $text-primary;
}

.panel-sub {
  font-size: 11.5px;
  color: $text-placeholder;
}

/* 表格内 */
.sku-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sku-name {
  color: $text-primary;
  font-size: 13px;
}

.sku-code {
  font-size: 11px;
  color: $text-placeholder;
}

.handle-type {
  font-size: 12.5px;
  color: $color-primary;
  font-weight: 500;
}

.two-line {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12.5px;
}

:deep(.row-pending) {
  background: #fffdf7 !important;
}

/* 处理对话框 */
.handle-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 18px;
  padding: 14px 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid $border-base;
}

.hi-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12.5px;
}

.hi-label {
  color: $text-placeholder;
  flex-shrink: 0;
  min-width: 60px;
}

.handle-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.handle-radio {
  display: flex;
  align-items: flex-start;
  height: auto;
  margin-right: 0;
  padding: 9px 12px;
  border: 1px solid $border-base;
  border-radius: 8px;
  width: 100%;
  transition: all 0.16s;

  &:hover {
    border-color: $color-primary-light;
    background: #f8fbff;
  }

  :deep(.el-radio__label) {
    display: flex;
    flex-direction: column;
    gap: 2px;
    white-space: normal;
    line-height: 1.5;
  }
}

.hr-title {
  font-size: 13px;
  color: $text-primary;
  font-weight: 500;
}

.hr-detail {
  font-size: 11.5px;
  color: $text-placeholder;
}

.opt-sub {
  float: right;
  font-size: 11px;
  color: $text-placeholder;
}
</style>
