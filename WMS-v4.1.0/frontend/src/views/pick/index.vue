<template>
  <div class="page-container">
    <PageHeader
      title="拣货任务"
      description="波次下发后自动生成拣货任务。明细已按库位路径排序，拣货员按顺序逐行扫码确认即可，同库位同 SKU 已合并。"
      :count="total"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
      </template>
    </PageHeader>

    <!-- ======================= 我的待办 ======================= -->
    <div class="card card-pad mb-12">
      <div class="panel-head">
        <span class="panel-title">我的待办任务</span>
        <span class="panel-sub">
          当前身份：{{ app.currentUser.employeeName }} · {{ app.currentUser.roleDesc }}
        </span>
      </div>
      <div v-if="myTasks.length" class="my-task-row">
        <div v-for="t in myTasks" :key="t.taskNo" class="my-task" @click="gotoExecute(t)">
          <div class="mt-head">
            <span class="mono mt-no">{{ t.taskNo }}</span>
            <StatusTag :code="t.status" group="pickTaskStatus" />
          </div>
          <div class="mt-wave">波次 {{ t.waveNo }}</div>
          <el-progress :percentage="t.progress" :stroke-width="6" :show-text="false" class="mt-progress" />
          <div class="mt-foot">
            <span>{{ t.finishedCount }}/{{ t.itemCount }} 行</span>
            <span class="num">{{ t.shouldQty }} 件</span>
          </div>
        </div>
      </div>
      <el-empty v-else description="当前没有分配给你的待办任务" :image-size="70" />
    </div>

    <!-- ======================= 筛选 ======================= -->
    <div class="card card-pad mb-12">
      <el-form :model="query" inline class="filter-form">
        <el-form-item label="任务号">
          <el-input v-model="query.taskNo" placeholder="模糊匹配" clearable style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="波次号">
          <el-input v-model="query.waveNo" placeholder="精确匹配" clearable style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="拣货员">
          <el-select v-model="query.pickerNo" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="p in pickers" :key="p.employeeNo" :label="p.employeeName" :value="p.employeeNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="o in dict.options('pickTaskStatus')" :key="o.code" :label="o.desc" :value="o.code" />
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
      <el-table v-loading="loading" :data="rows" stripe height="calc(100vh - 528px)">
        <el-table-column label="任务号" width="165" fixed>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="gotoExecute(row)">
              <span class="mono">{{ row.taskNo }}</span>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="波次号" width="165">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="gotoWave(row.waveNo)">
              <span class="mono">{{ row.waveNo }}</span>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <StatusTag :code="row.status" group="pickTaskStatus" />
          </template>
        </el-table-column>
        <el-table-column label="拣货进度" width="170">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :stroke-width="7" :show-text="false" />
            <div class="progress-lbl">{{ row.finishedCount }} / {{ row.itemCount }} 行</div>
          </template>
        </el-table-column>
        <el-table-column label="拣货员" width="100">
          <template #default="{ row }">
            <span v-if="row.pickerName">{{ row.pickerName }}</span>
            <el-tag v-else type="warning" size="small" effect="plain">待分配</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="订单数" width="80" align="right">
          <template #default="{ row }"><span class="num">{{ row.orderCount }}</span></template>
        </el-table-column>
        <el-table-column label="库位数" width="80" align="right">
          <template #default="{ row }"><span class="num">{{ row.locationCount }}</span></template>
        </el-table-column>
        <el-table-column label="应拣/实拣/缺货" width="150" align="center">
          <template #default="{ row }">
            <span class="num">{{ row.shouldQty }}</span>
            <span class="text-secondary"> / </span>
            <span class="num success">{{ row.actualQty }}</span>
            <span class="text-secondary"> / </span>
            <span class="num" :class="{ danger: row.shortageQty > 0 }">{{ row.shortageQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="105" align="right">
          <template #default="{ row }"><span class="num">{{ formatDuration(row.durationSec) }}</span></template>
        </el-table-column>
        <el-table-column label="开始时间" width="150">
          <template #default="{ row }"><span class="text-secondary">{{ formatTimeShort(row.startTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'WAITING'"
              link
              type="primary"
              size="small"
              @click="onStart(row)"
            >
              领取开工
            </el-button>
            <el-button
              v-if="['PICKING', 'EXCEPTION'].includes(row.status)"
              link
              type="primary"
              size="small"
              @click="gotoExecute(row)"
            >
              继续拣货
            </el-button>
            <el-button link size="small" @click="showPath(row)">路线</el-button>
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

    <!-- 拣货路线 -->
    <el-dialog v-model="pathVisible" title="拣货路线" width="640px">
      <div class="path-head">
        <span>任务号 <b class="mono">{{ pathTask?.taskNo }}</b></span>
        <span>共 <b class="num">{{ pathTask?.locationCount }}</b> 个库位</span>
        <span>应拣 <b class="num">{{ pathTask?.shouldQty }}</b> 件</span>
      </div>
      <div class="path-chain">
        <template v-for="(loc, i) in pathLocations" :key="loc">
          <span class="path-node">{{ loc }}</span>
          <el-icon v-if="i < pathLocations.length - 1" class="path-arrow"><Right /></el-icon>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, RefreshLeft, Right } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { pickApi, baseApi } from '@/api'
import { useDictStore } from '@/stores/dict'
import { useAppStore } from '@/stores/app'
import { formatDuration, formatTimeShort } from '@/utils'

const router = useRouter()
const dict = useDictStore()
const app = useAppStore()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const pickers = ref([])
const myTasks = ref([])

const query = ref({
  pageNum: 1,
  pageSize: 20,
  taskNo: '',
  waveNo: '',
  pickerNo: '',
  status: ''
})

const pathVisible = ref(false)
const pathTask = ref(null)

const pathLocations = computed(() => {
  const t = pathTask.value
  if (!t) return []
  return [...new Set(t.items.map((i) => i.locationCode))]
})

async function load() {
  loading.value = true
  try {
    const res = await pickApi.page(query.value)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadMyTasks() {
  try {
    myTasks.value = await pickApi.myTasks(app.currentUser.employeeNo)
  } catch {
    myTasks.value = []
  }
}

async function refreshAll() {
  await Promise.all([load(), loadMyTasks()])
}

function search() {
  query.value.pageNum = 1
  load()
}

function reset() {
  query.value = { pageNum: 1, pageSize: query.value.pageSize, taskNo: '', waveNo: '', pickerNo: '', status: '' }
  load()
}

async function onStart(row) {
  await pickApi.start(row.taskNo, row.pickerNo)
  ElMessage.success('已开始拣货')
  await refreshAll()
  window.__wmsRefreshBadge?.()
  gotoExecute(row)
}

function gotoExecute(row) {
  router.push(`/pick/execute/${row.taskNo}`)
}

function gotoWave(waveNo) {
  router.push(`/wave/detail/${waveNo}`)
}

async function showPath(row) {
  pathTask.value = await pickApi.detail(row.taskNo)
  pathVisible.value = true
}

onMounted(async () => {
  pickers.value = await baseApi.employeesByRole('PICKER')
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

/* 我的待办卡片 */
.my-task-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.my-task {
  width: 216px;
  padding: 12px 14px;
  border-radius: 9px;
  background: linear-gradient(135deg, #f8fbff, #f1f5f9);
  border: 1px solid #dbeafe;
  cursor: pointer;
  transition: all 0.16s;

  &:hover {
    box-shadow: $shadow-hover;
    transform: translateY(-2px);
    border-color: $color-primary;
  }
}

.mt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 5px;
}

.mt-no {
  font-size: 12.5px;
  font-weight: 600;
  color: $color-primary;
}

.mt-wave {
  font-size: 11.5px;
  color: $text-placeholder;
  margin-bottom: 8px;
}

.mt-progress {
  margin-bottom: 7px;
}

.mt-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11.5px;
  color: $text-secondary;
}

.progress-lbl {
  font-size: 10.5px;
  color: $text-placeholder;
  margin-top: 3px;
}

/* 路线对话框 */
.path-head {
  display: flex;
  gap: 24px;
  padding: 10px 14px;
  background: #f8fafc;
  border-radius: 8px;
  font-size: 12.5px;
  color: $text-secondary;
  margin-bottom: 14px;

  b {
    color: $text-primary;
  }
}

.path-chain {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.path-node {
  padding: 5px 10px;
  border-radius: 6px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  border: 1px solid #dbeafe;
}

.path-arrow {
  color: #cbd5e1;
  font-size: 12px;
}
</style>
