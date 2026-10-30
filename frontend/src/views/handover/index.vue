<template>
  <div class="page-container">
    <PageHeader
      title="出库交接"
      description="复核完成的波次进入待交接区。按承运商归集波次生成交接单，承运商到场后核对件数并签字确认，波次与订单随即置为「已出库」。"
      :count="total"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openCreate">创建交接单</el-button>
        <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
      </template>
    </PageHeader>

    <!-- ======================= 待交接波次 ======================= -->
    <div class="card card-pad mb-12">
      <div class="panel-head">
        <span class="panel-title">待交接波次</span>
        <span class="panel-sub">
          共 {{ reviewableWaves.length }} 个波次已复核完成，等待生成交接单
        </span>
      </div>
      <div v-if="reviewableWaves.length" class="wave-row">
        <div v-for="w in reviewableWaves" :key="w.waveNo" class="wave-chip" @click="gotoWave(w.waveNo)">
          <div class="wc-no mono">{{ w.waveNo }}</div>
          <div class="wc-meta">
            <span>{{ w.orderCount }} 单</span>
            <span class="dot">·</span>
            <span>{{ w.totalQty }} 件</span>
          </div>
          <div class="wc-time">{{ formatTimeShort(w.reviewFinishTime) }} 复核完成</div>
        </div>
      </div>
      <el-empty v-else description="暂无待交接波次" :image-size="70" />
    </div>

    <!-- ======================= 筛选 ======================= -->
    <div class="card card-pad mb-12">
      <el-form :model="query" inline class="filter-form">
        <el-form-item label="交接单号">
          <el-input v-model="query.handoverNo" placeholder="模糊匹配" clearable style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="承运商">
          <el-select v-model="query.carrierCode" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="c in carriers" :key="c.carrierCode" :label="c.carrierName" :value="c.carrierCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="交接状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 145px">
            <el-option v-for="o in dict.options('handoverStatus')" :key="o.code" :label="o.desc" :value="o.code" />
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
        <el-table-column label="交接单号" width="170" fixed>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">
              <span class="mono">{{ row.handoverNo }}</span>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :code="row.status" group="handoverStatus" />
          </template>
        </el-table-column>
        <el-table-column label="承运商" width="120">
          <template #default="{ row }">{{ row.carrierName }}</template>
        </el-table-column>
        <el-table-column label="波次号" min-width="160">
          <template #default="{ row }">
            <span class="mono wave-nos">{{ row.waveNos }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单数" width="80" align="right">
          <template #default="{ row }"><span class="num">{{ row.orderCount }}</span></template>
        </el-table-column>
        <el-table-column label="包裹数" width="80" align="right">
          <template #default="{ row }"><span class="num">{{ row.packageCount }}</span></template>
        </el-table-column>
        <el-table-column label="总重量" width="95" align="right">
          <template #default="{ row }">
            <span v-if="row.totalWeight" class="num">{{ row.totalWeight }} kg</span>
            <span v-else class="text-secondary">—</span>
          </template>
        </el-table-column>
        <el-table-column label="交接人" width="85">
          <template #default="{ row }">{{ row.handoverUserName || '—' }}</template>
        </el-table-column>
        <el-table-column label="接收人 / 车牌" width="130">
          <template #default="{ row }">
            <div class="two-line">
              <span>{{ row.receiveName || '—' }}</span>
              <span class="text-secondary mono">{{ row.vehicleNo || '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="创建 / 交接时间" width="150">
          <template #default="{ row }">
            <div class="two-line">
              <span>{{ formatTimeShort(row.createTime) }}</span>
              <span class="text-secondary">{{ formatTimeShort(row.handoverTime) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'CREATED'"
              link
              type="primary"
              size="small"
              @click="openConfirm(row)"
            >
              确认交接
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

    <!-- ======================= 创建交接单 ======================= -->
    <el-dialog v-model="createVisible" title="创建交接单" width="700px" :close-on-click-modal="false">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="承运商" required>
          <el-select
            v-model="createForm.carrierCode"
            placeholder="先选择承运商，再挑选该承运商的可交接波次"
            style="width: 280px"
            @change="onCarrierChange"
          >
            <el-option v-for="c in carriers" :key="c.carrierCode" :label="c.carrierName" :value="c.carrierCode" />
          </el-select>
        </el-form-item>

        <el-form-item label="可交接波次">
          <div class="pick-waves">
            <el-table
              v-if="carrierWaves.length"
              ref="waveTableRef"
              :data="carrierWaves"
              size="small"
              height="220"
              @selection-change="onWaveSelect"
            >
              <el-table-column type="selection" width="42" />
              <el-table-column label="波次号" width="170">
                <template #default="{ row }"><span class="mono">{{ row.waveNo }}</span></template>
              </el-table-column>
              <el-table-column label="订单数" width="80" align="right">
                <template #default="{ row }"><span class="num">{{ row.orderCount }}</span></template>
              </el-table-column>
              <el-table-column label="件数" width="80" align="right">
                <template #default="{ row }"><span class="num">{{ row.totalQty }}</span></template>
              </el-table-column>
              <el-table-column label="复核完成" min-width="140">
                <template #default="{ row }">
                  <span class="text-secondary">{{ formatTimeShort(row.reviewFinishTime) }}</span>
                </template>
              </el-table-column>
            </el-table>
            <el-empty
              v-else
              :description="createForm.carrierCode ? '该承运商暂无已复核待交接的波次' : '请先选择承运商'"
              :image-size="60"
            />
          </div>
        </el-form-item>

        <el-form-item label="车牌号">
          <el-input v-model="createForm.vehicleNo" placeholder="如：粤B12345" style="width: 220px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="如：临时加车，分两趟装载" />
        </el-form-item>
      </el-form>

      <div v-if="selectedWaves.length" class="create-summary">
        已选 <b class="num">{{ selectedWaves.length }}</b> 个波次，
        合计 <b class="num">{{ selectedSummary.orders }}</b> 单 /
        <b class="num">{{ selectedSummary.qty }}</b> 件
      </div>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="creating"
          :disabled="!createForm.carrierCode || !selectedWaves.length"
          @click="submitCreate"
        >
          生成交接单
        </el-button>
      </template>
    </el-dialog>

    <!-- ======================= 确认交接 ======================= -->
    <el-dialog v-model="confirmVisible" title="确认交接" width="560px" :close-on-click-modal="false">
      <div v-if="current" class="confirm-info">
        <div class="ci-row">
          <span class="ci-label">交接单号</span><span class="mono">{{ current.handoverNo }}</span>
        </div>
        <div class="ci-row">
          <span class="ci-label">承运商</span><span>{{ current.carrierName }}</span>
        </div>
        <div class="ci-row">
          <span class="ci-label">波次</span><span class="mono">{{ current.waveNos }}</span>
        </div>
        <div class="ci-row">
          <span class="ci-label">系统件数</span>
          <span><b class="num">{{ current.packageCount }}</b> 件 · {{ current.orderCount }} 单</span>
        </div>
      </div>

      <el-form :model="confirmForm" label-width="100px" class="mt-16">
        <el-form-item label="接收人" required>
          <el-input v-model="confirmForm.receiveName" placeholder="承运商司机 / 交接员姓名" style="width: 260px" />
        </el-form-item>
        <el-form-item label="实际件数">
          <el-input-number v-model="confirmForm.actualPackageCount" :min="0" :max="9999" style="width: 180px" />
          <span
            v-if="current && confirmForm.actualPackageCount !== current.packageCount"
            class="diff-hint"
          >
            与系统件数不一致，将记入交接单备注
          </span>
        </el-form-item>
        <el-form-item label="拒收">
          <el-switch v-model="confirmForm.rejected" active-text="承运商拒收" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="confirmForm.remark"
            type="textarea"
            :rows="2"
            :placeholder="confirmForm.rejected ? '请填写拒收原因' : '如：外箱破损 1 件，已现场确认'"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button
          :type="confirmForm.rejected ? 'danger' : 'primary'"
          :loading="confirming"
          @click="submitConfirm"
        >
          {{ confirmForm.rejected ? '记录拒收' : '确认交接完成' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ======================= 详情 ======================= -->
    <el-dialog v-model="detailVisible" title="交接单详情" width="600px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="交接单号">
          <span class="mono">{{ current.handoverNo }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag :code="current.status" group="handoverStatus" />
        </el-descriptions-item>
        <el-descriptions-item label="承运商">{{ current.carrierName }}</el-descriptions-item>
        <el-descriptions-item label="车牌号">
          <span class="mono">{{ current.vehicleNo || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="波次号" :span="2">
          <span class="mono">{{ current.waveNos }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="订单数">
          <span class="num">{{ current.orderCount }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="包裹数">
          <span class="num">{{ current.packageCount }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="总重量">
          <span class="num">{{ current.totalWeight || 0 }}</span> kg
        </el-descriptions-item>
        <el-descriptions-item label="交接人">{{ current.handoverUserName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="接收人">{{ current.receiveName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(current.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="交接时间">{{ formatTime(current.handoverTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ current.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search, RefreshLeft } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { handoverApi, baseApi } from '@/api'
import { useDictStore } from '@/stores/dict'
import { useAppStore } from '@/stores/app'
import { formatTime, formatTimeShort } from '@/utils'

const router = useRouter()
const dict = useDictStore()
const app = useAppStore()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const carriers = ref([])
const reviewableWaves = ref([])

const query = ref({ pageNum: 1, pageSize: 20, handoverNo: '', carrierCode: '', status: '' })

/* ------------------------------ 创建 ------------------------------ */

const createVisible = ref(false)
const creating = ref(false)
const carrierWaves = ref([])
const selectedWaves = ref([])
const waveTableRef = ref(null)

const createForm = reactive({ carrierCode: '', vehicleNo: '', remark: '' })

const selectedSummary = computed(() => ({
  orders: selectedWaves.value.reduce((s, w) => s + (w.orderCount || 0), 0),
  qty: selectedWaves.value.reduce((s, w) => s + (w.totalQty || 0), 0)
}))

/* ------------------------------ 确认 ------------------------------ */

const confirmVisible = ref(false)
const confirming = ref(false)
const detailVisible = ref(false)
const current = ref(null)

const confirmForm = reactive({ receiveName: '', actualPackageCount: 0, rejected: false, remark: '' })

/* ------------------------------ 加载 ------------------------------ */

async function load() {
  loading.value = true
  try {
    const res = await handoverApi.page(query.value)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadReviewable() {
  reviewableWaves.value = await handoverApi.reviewableWaves()
}

async function refreshAll() {
  await Promise.all([load(), loadReviewable()])
}

function search() {
  query.value.pageNum = 1
  load()
}

function reset() {
  query.value = { pageNum: 1, pageSize: query.value.pageSize, handoverNo: '', carrierCode: '', status: '' }
  load()
}

function rowClass({ row }) {
  if (row.status === 'CREATED') return 'row-todo'
  if (row.status === 'REJECTED') return 'row-bad'
  return ''
}

/* ------------------------------ 创建流程 ------------------------------ */

function openCreate() {
  createForm.carrierCode = ''
  createForm.vehicleNo = ''
  createForm.remark = ''
  carrierWaves.value = []
  selectedWaves.value = []
  createVisible.value = true
}

async function onCarrierChange(code) {
  selectedWaves.value = []
  carrierWaves.value = code ? await handoverApi.reviewableWaves(code) : []
}

function onWaveSelect(val) {
  selectedWaves.value = val
}

async function submitCreate() {
  creating.value = true
  try {
    await handoverApi.create({
      carrierCode: createForm.carrierCode,
      waveNos: selectedWaves.value.map((w) => w.waveNo),
      vehicleNo: createForm.vehicleNo,
      remark: createForm.remark,
      handoverUserNo: app.currentUser.employeeNo,
      handoverUserName: app.currentUser.employeeName
    })
    ElMessage.success('交接单已创建')
    createVisible.value = false
    await refreshAll()
    window.__wmsRefreshBadge?.()
  } finally {
    creating.value = false
  }
}

/* ------------------------------ 确认流程 ------------------------------ */

function openConfirm(row) {
  current.value = row
  confirmForm.receiveName = row.receiveName || ''
  confirmForm.actualPackageCount = row.packageCount
  confirmForm.rejected = false
  confirmForm.remark = ''
  confirmVisible.value = true
}

async function submitConfirm() {
  if (!confirmForm.rejected && !confirmForm.receiveName) {
    ElMessage.warning('请填写接收人')
    return
  }
  confirming.value = true
  try {
    await handoverApi.confirm({
      handoverNo: current.value.handoverNo,
      receiveName: confirmForm.receiveName,
      actualPackageCount: confirmForm.actualPackageCount,
      rejected: confirmForm.rejected,
      remark: confirmForm.remark
    })
    ElMessage.success(confirmForm.rejected ? '已记录拒收' : '交接已完成')
    confirmVisible.value = false
    await refreshAll()
    window.__wmsRefreshBadge?.()
  } finally {
    confirming.value = false
  }
}

function openDetail(row) {
  current.value = row
  detailVisible.value = true
}

function gotoWave(waveNo) {
  router.push(`/wave/detail/${waveNo}`)
}

onMounted(async () => {
  carriers.value = await baseApi.carriers()
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

.panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
}

.panel-sub {
  font-size: 11.5px;
  color: $text-placeholder;
}

/* 待交接波次 */
.wave-row {
  display: flex;
  gap: 9px;
  flex-wrap: wrap;
}

.wave-chip {
  padding: 9px 13px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f0fdf4, #f8fafc);
  border: 1px solid #bbf7d0;
  cursor: pointer;
  transition: all 0.16s;
  min-width: 168px;

  &:hover {
    box-shadow: $shadow-hover;
    transform: translateY(-2px);
  }
}

.wc-no {
  font-size: 12.5px;
  font-weight: 600;
  color: $color-success;
}

.wc-meta {
  font-size: 11.5px;
  color: $text-secondary;
  margin-top: 4px;
}

.dot {
  margin: 0 3px;
  color: #cbd5e1;
}

.wc-time {
  font-size: 11px;
  color: $text-placeholder;
  margin-top: 3px;
}

.wave-nos {
  font-size: 11.5px;
  color: $text-regular;
  word-break: break-all;
}

.two-line {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12.5px;
}

:deep(.row-todo) {
  background: #f8fbff !important;
}

:deep(.row-bad) {
  background: #fef7f7 !important;
}

/* 创建对话框 */
.pick-waves {
  width: 100%;
  border: 1px solid $border-base;
  border-radius: 8px;
  overflow: hidden;
}

.create-summary {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  font-size: 12.5px;
  color: $text-secondary;

  b {
    color: $color-primary;
    font-size: 14px;
  }
}

/* 确认对话框 */
.confirm-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 18px;
  padding: 14px 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid $border-base;
}

.ci-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12.5px;
  min-width: 0;
}

.ci-label {
  color: $text-placeholder;
  flex-shrink: 0;
  min-width: 62px;
}

.diff-hint {
  margin-left: 10px;
  font-size: 11.5px;
  color: $color-warning;
}
</style>
