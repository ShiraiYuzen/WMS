<template>
  <div class="page-container">
    <PageHeader
      title="复核打包"
      description="拣货完成后波次进入复核。认领波次后逐单核对商品与数量，登记包装材料、包裹数与重量；发现差异时登记差异类型并判定责任环节，差异数据将汇入差错率统计。"
      :count="total"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
      </template>
    </PageHeader>

    <!-- ======================= 待认领波次 ======================= -->
    <div class="card card-pad mb-12">
      <div class="panel-head">
        <span class="panel-title">待复核波次</span>
        <span class="panel-sub">
          当前身份：{{ app.currentUser.employeeName }} · {{ app.currentUser.roleDesc }}
        </span>
      </div>
      <div v-if="pendingWaves.length" class="wave-row">
        <div v-for="w in pendingWaves" :key="w.waveNo" class="wave-card">
          <div class="wc-head">
            <span class="mono wc-no">{{ w.waveNo }}</span>
            <StatusTag :code="w.status" group="waveStatus" />
          </div>
          <div class="wc-meta">
            <span>订单 <b class="num">{{ w.orderCount }}</b> 单</span>
            <span>商品 <b class="num">{{ w.skuCount }}</b> 种</span>
            <span>件数 <b class="num">{{ w.totalQty }}</b></span>
          </div>
          <div class="wc-meta wc-sub">
            <span>拣货员 {{ w.pickerName || '—' }}</span>
            <span>{{ formatTimeShort(w.pickFinishTime) }} 拣完</span>
          </div>
          <el-button
            type="primary"
            size="small"
            class="wc-btn"
            :loading="claiming === w.waveNo"
            @click="onClaim(w)"
          >
            {{ w.status === 'REVIEWING' ? '继续复核' : '认领并复核' }}
          </el-button>
        </div>
      </div>
      <el-empty v-else description="当前没有等待复核的波次" :image-size="70" />
    </div>

    <!-- ======================= 筛选 ======================= -->
    <div class="card card-pad mb-12">
      <el-form :model="query" inline class="filter-form">
        <el-form-item label="复核单号">
          <el-input v-model="query.reviewNo" placeholder="模糊匹配" clearable style="width: 175px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="波次号">
          <el-input v-model="query.waveNo" placeholder="精确匹配" clearable style="width: 170px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="订单号">
          <el-input v-model="query.orderNo" placeholder="精确匹配" clearable style="width: 170px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="复核状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 145px">
            <el-option v-for="o in dict.options('reviewStatus')" :key="o.code" :label="o.desc" :value="o.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="差异类型">
          <el-select v-model="query.diffType" placeholder="全部" clearable style="width: 135px">
            <el-option v-for="o in dict.options('diffType')" :key="o.code" :label="o.desc" :value="o.code" />
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
        height="calc(100vh - 640px)"
        :row-class-name="rowClass"
      >
        <el-table-column label="复核单号" width="170" fixed>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="gotoExecute(row)">
              <span class="mono">{{ row.reviewNo }}</span>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :code="row.status" group="reviewStatus" />
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
          <template #default="{ row }">
            <span class="mono">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="差异类型" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.diffType && row.diffType !== 'NONE'" type="danger" size="small" effect="light">
              {{ row.diffTypeDesc }}
            </el-tag>
            <span v-else class="text-secondary">—</span>
          </template>
        </el-table-column>
        <el-table-column label="应复核 / 实复核" width="150" align="center">
          <template #default="{ row }">
            <span class="num">{{ row.shouldQty }}</span>
            <span class="text-secondary"> / </span>
            <span class="num" :class="{ danger: row.diffQty > 0 }">{{ row.actualQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="差异件数" width="95" align="right">
          <template #default="{ row }">
            <span class="num" :class="{ danger: row.diffQty > 0 }">{{ row.diffQty || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="包装 / 包裹" width="140">
          <template #default="{ row }">
            <template v-if="row.status !== 'WAITING'">
              <span>{{ row.packMaterial }}</span>
              <span class="text-secondary"> · </span>
              <span class="num">{{ row.packageCount }}</span> 件
            </template>
            <span v-else class="text-secondary">待打包</span>
          </template>
        </el-table-column>
        <el-table-column label="重量" width="90" align="right">
          <template #default="{ row }">
            <span v-if="row.weight" class="num">{{ row.weight }} kg</span>
            <span v-else class="text-secondary">—</span>
          </template>
        </el-table-column>
        <el-table-column label="复核员" width="90">
          <template #default="{ row }">
            <span v-if="row.reviewerName">{{ row.reviewerName }}</span>
            <el-tag v-else type="warning" size="small" effect="plain">待认领</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ formatDuration(row.durationSec) }}</span></template>
        </el-table-column>
        <el-table-column label="完成时间" width="150">
          <template #default="{ row }"><span class="text-secondary">{{ formatTimeShort(row.finishTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'WAITING'" link type="primary" size="small" @click="gotoExecute(row)">
              去复核
            </el-button>
            <el-button v-else link size="small" @click="gotoExecute(row)">查看</el-button>
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
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search, RefreshLeft } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { reviewApi } from '@/api'
import { useDictStore } from '@/stores/dict'
import { useAppStore } from '@/stores/app'
import { formatDuration, formatTimeShort } from '@/utils'

const router = useRouter()
const dict = useDictStore()
const app = useAppStore()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const pendingWaves = ref([])
const claiming = ref('')

const query = ref({
  pageNum: 1,
  pageSize: 20,
  reviewNo: '',
  waveNo: '',
  orderNo: '',
  status: '',
  diffType: ''
})

async function load() {
  loading.value = true
  try {
    const res = await reviewApi.page(query.value)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadPending() {
  pendingWaves.value = await reviewApi.pendingWaves()
}

async function refreshAll() {
  await Promise.all([load(), loadPending()])
}

function search() {
  query.value.pageNum = 1
  load()
}

function reset() {
  query.value = {
    pageNum: 1,
    pageSize: query.value.pageSize,
    reviewNo: '',
    waveNo: '',
    orderNo: '',
    status: '',
    diffType: ''
  }
  load()
}

function rowClass({ row }) {
  return row.status === 'DIFF' ? 'row-diff' : ''
}

async function onClaim(w) {
  claiming.value = w.waveNo
  try {
    await reviewApi.claim(w.waveNo, app.currentUser.employeeNo, app.currentUser.employeeName)
    ElMessage.success(`已认领波次 ${w.waveNo}`)
    await refreshAll()
    router.push('/review')
  } finally {
    claiming.value = ''
  }
}

function gotoExecute(row) {
  router.push(`/review/execute/${row.reviewNo}`)
}

function gotoWave(waveNo) {
  router.push(`/wave/detail/${waveNo}`)
}

onMounted(refreshAll)
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

/* 待认领波次卡片 */
.wave-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.wave-card {
  width: 230px;
  padding: 12px 14px;
  border-radius: 9px;
  background: linear-gradient(135deg, #f8fbff, #f1f5f9);
  border: 1px solid #dbeafe;
  transition: all 0.16s;

  &:hover {
    box-shadow: $shadow-hover;
    transform: translateY(-2px);
  }
}

.wc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 9px;
}

.wc-no {
  font-size: 12.5px;
  font-weight: 600;
  color: $color-primary;
}

.wc-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11.5px;
  color: $text-secondary;
  margin-bottom: 5px;

  b {
    color: $text-primary;
  }
}

.wc-sub {
  color: $text-placeholder;
}

.wc-btn {
  width: 100%;
  margin-top: 8px;
}

:deep(.row-diff) {
  background: #fef7f7 !important;
}
</style>
