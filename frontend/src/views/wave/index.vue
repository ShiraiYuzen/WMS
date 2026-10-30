<template>
  <div class="page-container">
    <PageHeader
      title="波次管理"
      description="按策略把订单池中的订单聚类成波次，分配拣货员后下发拣货。波次内同库位同 SKU 会自动合并，拣货员只跑一趟。"
      :count="total"
    >
      <template #actions>
        <el-button type="primary" :icon="MagicStick" @click="openGenerate">生成波次</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <!-- ======================= 拣货员负载 ======================= -->
    <div class="card card-pad mb-12">
      <div class="panel-head">
        <span class="panel-title">拣货员负载</span>
        <span class="panel-sub">按当前未完成波次数升序排列，便于均衡派工</span>
      </div>
      <div class="workload-row">
        <div
          v-for="w in workload"
          :key="w.pickerNo"
          class="wl-card"
          :class="{ busy: w.load >= 3, idle: w.load === 0 }"
        >
          <div class="wl-top">
            <el-avatar :size="26" class="wl-avatar">{{ w.pickerName.slice(0, 1) }}</el-avatar>
            <div class="wl-meta">
              <div class="wl-name">{{ w.pickerName }}</div>
              <div class="wl-team">{{ w.team }}</div>
            </div>
          </div>
          <div class="wl-nums">
            <div class="wl-num">
              <span class="num">{{ w.load }}</span>
              <span class="wl-lbl">在手</span>
            </div>
            <div class="wl-num">
              <span class="num done">{{ w.finishedWaves }}</span>
              <span class="wl-lbl">已完成</span>
            </div>
          </div>
        </div>
        <el-empty v-if="!workload.length" description="暂无在岗拣货员" :image-size="60" />
      </div>
    </div>

    <!-- ======================= 筛选 ======================= -->
    <div class="card card-pad mb-12">
      <el-form :model="query" inline class="filter-form">
        <el-form-item label="波次号 / 名称">
          <el-input v-model="query.waveNo" placeholder="模糊匹配" clearable style="width: 190px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="o in dict.options('waveStatus')" :key="o.code" :label="o.desc" :value="o.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略">
          <el-select v-model="query.strategy" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="o in dict.options('waveStrategy')" :key="o.code" :label="o.desc" :value="o.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="拣货员">
          <el-select v-model="query.pickerNo" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="p in pickers" :key="p.employeeNo" :label="p.employeeName" :value="p.employeeNo" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.onlyWorking" label="仅看在制" border size="small" />
          <el-checkbox v-model="query.onlyShortage" label="仅看含缺货" border size="small" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- ======================= 表格 ======================= -->
    <div class="card">
      <el-table v-loading="loading" :data="rows" stripe height="calc(100vh - 486px)">
        <el-table-column label="波次号" width="165" fixed>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="gotoDetail(row)">
              <span class="mono">{{ row.waveNo }}</span>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="waveName" label="波次名称" min-width="190" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :code="row.status" group="waveStatus" />
            <el-icon v-if="row.hasShortage" class="shortage-flag" title="含缺货">
              <WarningFilled />
            </el-icon>
          </template>
        </el-table-column>
        <el-table-column label="流程进度" width="150">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress"
              :stroke-width="7"
              :color="progressColor(row.progress)"
              :show-text="false"
            />
            <div class="progress-lbl">{{ row.currentNode }} · {{ row.progress }}%</div>
          </template>
        </el-table-column>
        <el-table-column label="策略" width="140">
          <template #default="{ row }">
            <span class="text-secondary">{{ row.strategyDesc }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单 / SKU / 件数" width="150" align="center">
          <template #default="{ row }">
            <span class="num">{{ row.orderCount }}</span>
            <span class="text-secondary"> / </span>
            <span class="num">{{ row.skuCount }}</span>
            <span class="text-secondary"> / </span>
            <span class="num">{{ row.totalQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="拣货员" width="100">
          <template #default="{ row }">
            <span v-if="row.pickerName">{{ row.pickerName }}</span>
            <el-tag v-else type="warning" size="small" effect="plain">待分配</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="拣货耗时" width="105" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatDuration(row.pickDurationSec) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="生成时间" width="150">
          <template #default="{ row }">
            <span class="text-secondary">{{ formatTimeShort(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="gotoDetail(row)">详情</el-button>
            <el-button
              v-if="['CREATED', 'ASSIGNED'].includes(row.status)"
              link
              type="primary"
              size="small"
              @click="openAssign(row)"
            >
              {{ row.pickerName ? '改派' : '派工' }}
            </el-button>
            <el-button
              v-if="['CREATED', 'ASSIGNED'].includes(row.status)"
              link
              type="danger"
              size="small"
              @click="onCancel(row)"
            >
              取消
            </el-button>
            <el-button
              v-if="row.status === 'HANDOVERED'"
              link
              type="success"
              size="small"
              @click="onClose(row)"
            >
              归档
            </el-button>
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

    <!-- ======================= 生成波次向导 ======================= -->
    <el-dialog v-model="genVisible" title="生成波次" width="880px" top="6vh" destroy-on-close>
      <el-steps :active="genStep" simple class="mb-16">
        <el-step title="设置策略与容量" :icon="Setting" />
        <el-step title="确认分组预览" :icon="View" />
        <el-step title="完成生成" :icon="CircleCheck" />
      </el-steps>

      <!-- 第一步 -->
      <div v-if="genStep === 0">
        <el-form :model="genForm" label-width="130px">
          <el-form-item label="生成策略">
            <el-radio-group v-model="genForm.strategy" class="strategy-group">
              <el-radio v-for="o in dict.options('waveStrategy')" :key="o.code" :value="o.code" border>
                {{ o.desc }}
              </el-radio>
            </el-radio-group>
            <div class="strategy-tip">
              <el-icon><InfoFilled /></el-icon>
              {{ dict.detail('waveStrategy', genForm.strategy) }}
            </div>
          </el-form-item>

          <el-form-item label="容量上限">
            <el-input-number v-model="genForm.maxOrdersPerWave" :min="1" :max="200" controls-position="right" style="width: 130px" />
            <span class="unit-lbl">单/波次</span>
            <el-input-number v-model="genForm.maxSkusPerWave" :min="1" :max="200" controls-position="right" style="width: 130px; margin-left: 12px" />
            <span class="unit-lbl">SKU/波次</span>
            <el-input-number v-model="genForm.maxQtyPerWave" :min="1" :max="999" controls-position="right" style="width: 130px; margin-left: 12px" />
            <span class="unit-lbl">件/波次</span>
          </el-form-item>

          <el-form-item label="波次名称前缀">
            <el-input v-model="genForm.namePrefix" placeholder="例如：2026-10-16 华南区" style="width: 320px" />
          </el-form-item>

          <el-form-item label="生成后立即派工">
            <el-switch v-model="genForm.autoAssign" />
            <span class="unit-lbl" style="margin-left: 10px">按当前负载自动挑最闲的拣货员</span>
          </el-form-item>

          <el-form-item label="参与订单">
            <el-alert type="info" :closable="false" show-icon>
              <template #title>
                将从订单池中取
                <b class="num">{{ pooledCount }}</b>
                笔「已汇聚入池」的订单参与生成（加急订单优先）。也可在「订单池」页勾选后指定生成。
              </template>
            </el-alert>
          </el-form-item>
        </el-form>
      </div>

      <!-- 第二步：预览 -->
      <div v-else-if="genStep === 1" v-loading="previewLoading">
        <el-row :gutter="12" class="mb-12">
          <el-col :span="6">
            <div class="pv-stat"><span class="pv-num num">{{ preview.waveCount || 0 }}</span><span class="pv-lbl">将生成波次</span></div>
          </el-col>
          <el-col :span="6">
            <div class="pv-stat"><span class="pv-num num">{{ preview.totalOrders || 0 }}</span><span class="pv-lbl">参与订单</span></div>
          </el-col>
          <el-col :span="6">
            <div class="pv-stat"><span class="pv-num num">{{ preview.totalQty || 0 }}</span><span class="pv-lbl">商品件数</span></div>
          </el-col>
          <el-col :span="6">
            <div class="pv-stat"><span class="pv-num num">{{ preview.strategyDesc || '—' }}</span><span class="pv-lbl">使用策略</span></div>
          </el-col>
        </el-row>

        <el-table :data="preview.groups || []" size="small" border max-height="380">
          <el-table-column type="index" label="#" width="44" align="center" />
          <el-table-column prop="groupKey" label="分组" min-width="130" />
          <el-table-column prop="suggestWaveName" label="建议波次名称" min-width="200" show-overflow-tooltip />
          <el-table-column prop="orderCount" label="订单数" width="80" align="right" />
          <el-table-column prop="skuCount" label="SKU 数" width="80" align="right" />
          <el-table-column prop="totalQty" label="件数" width="80" align="right" />
          <el-table-column prop="locationCount" label="库位数" width="80" align="right" />
        </el-table>
      </div>

      <!-- 第三步：结果 -->
      <div v-else>
        <el-result icon="success" :title="`成功生成 ${createdWaves.length} 个波次`" sub-title="已同步生成拣货任务与库位合并后的拣货明细">
          <template #extra>
            <div class="created-list">
              <div v-for="w in createdWaves" :key="w.waveNo" class="created-item">
                <span class="mono created-no">{{ w.waveNo }}</span>
                <span class="created-name ellipsis">{{ w.waveName }}</span>
                <span class="created-meta">
                  {{ w.orderCount }} 单 / {{ w.totalQty }} 件
                  <el-tag v-if="w.pickerName" size="small" type="success" effect="plain" style="margin-left: 8px">
                    {{ w.pickerName }}
                  </el-tag>
                </span>
                <el-button link type="primary" size="small" @click="gotoDetail(w)">查看</el-button>
              </div>
            </div>
          </template>
        </el-result>
      </div>

      <template #footer>
        <el-button v-if="genStep === 0" @click="genVisible = false">取消</el-button>
        <el-button v-if="genStep === 0" type="primary" :loading="previewLoading" @click="doPreview">
          下一步：预览分组
        </el-button>
        <el-button v-if="genStep === 1" @click="genStep = 0">上一步</el-button>
        <el-button v-if="genStep === 1" type="primary" :loading="generating" @click="doGenerate">
          确认生成
        </el-button>
        <el-button v-if="genStep === 2" type="primary" @click="finishGenerate">完成</el-button>
      </template>
    </el-dialog>

    <!-- ======================= 派工对话框 ======================= -->
    <el-dialog v-model="assignVisible" title="分配拣货员" width="520px">
      <div class="assign-info">
        <span>波次</span>
        <b class="mono">{{ assignTarget?.waveNo }}</b>
        <span class="text-secondary">{{ assignTarget?.waveName }}</span>
      </div>
      <el-radio-group v-model="assignPickerNo" class="assign-list">
        <el-radio v-for="w in workload" :key="w.pickerNo" :value="w.pickerNo" border class="assign-item">
          <div class="assign-row">
            <span class="assign-name">{{ w.pickerName }}</span>
            <span class="assign-team">{{ w.team }}</span>
            <el-tag size="small" :type="w.load >= 3 ? 'danger' : w.load === 0 ? 'success' : 'warning'" effect="plain">
              在手 {{ w.load }}
            </el-tag>
          </div>
        </el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!assignPickerNo" @click="doAssign">确认派工</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  MagicStick, Refresh, Search, RefreshLeft, Setting, View, CircleCheck, InfoFilled
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { waveApi, baseApi, orderApi } from '@/api'
import { useDictStore } from '@/stores/dict'
import { useAppStore } from '@/stores/app'
import { formatDuration, formatTimeShort } from '@/utils'

const router = useRouter()
const dict = useDictStore()
const app = useAppStore()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const workload = ref([])
const pickers = ref([])
const pooledCount = ref(0)

const query = ref({
  pageNum: 1,
  pageSize: 20,
  waveNo: '',
  status: '',
  strategy: '',
  pickerNo: '',
  onlyWorking: false,
  onlyShortage: false
})

/* ------------------------------ 生成向导 ------------------------------ */

const genVisible = ref(false)
const genStep = ref(0)
const previewLoading = ref(false)
const generating = ref(false)
const preview = ref({})
const createdWaves = ref([])

const genForm = ref({
  strategy: 'BY_AREA',
  maxOrdersPerWave: 12,
  maxSkusPerWave: 30,
  maxQtyPerWave: 120,
  namePrefix: '',
  autoAssign: false
})

function openGenerate() {
  genStep.value = 0
  preview.value = {}
  createdWaves.value = []
  genForm.value.namePrefix = `${new Date().toISOString().slice(0, 10)} 波次`
  genVisible.value = true
}

async function doPreview() {
  previewLoading.value = true
  try {
    preview.value = await waveApi.preview({ ...genForm.value, creatorNo: app.currentUser.employeeNo })
    genStep.value = 1
  } finally {
    previewLoading.value = false
  }
}

async function doGenerate() {
  generating.value = true
  try {
    createdWaves.value = await waveApi.generate({
      ...genForm.value,
      creatorNo: app.currentUser.employeeNo,
      creatorName: app.currentUser.employeeName
    })
    genStep.value = 2
    await refreshAll()
  } finally {
    generating.value = false
  }
}

function finishGenerate() {
  genVisible.value = false
  router.push('/wave')
}

/* ------------------------------ 派工 ------------------------------ */

const assignVisible = ref(false)
const assignTarget = ref(null)
const assignPickerNo = ref('')

function openAssign(row) {
  assignTarget.value = row
  assignPickerNo.value = row.pickerNo || ''
  assignVisible.value = true
}

async function doAssign() {
  await waveApi.assign({
    waveNo: assignTarget.value.waveNo,
    pickerNo: assignPickerNo.value,
    operatorNo: app.currentUser.employeeNo,
    operatorName: app.currentUser.employeeName
  })
  ElMessage.success('已分配拣货员')
  assignVisible.value = false
  await refreshAll()
  window.__wmsRefreshBadge?.()
}

/* ------------------------------ 列表 ------------------------------ */

async function load() {
  loading.value = true
  try {
    const res = await waveApi.page(query.value)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([load(), loadWorkload(), loadPooledCount()])
}

async function loadWorkload() {
  workload.value = await waveApi.pickerWorkload()
}

async function loadPooledCount() {
  const c = await orderApi.countByStatus()
  pooledCount.value = c.POOLED || 0
}

function search() {
  query.value.pageNum = 1
  load()
}

function reset() {
  query.value = {
    pageNum: 1,
    pageSize: query.value.pageSize,
    waveNo: '',
    status: '',
    strategy: '',
    pickerNo: '',
    onlyWorking: false,
    onlyShortage: false
  }
  load()
}

async function onCancel(row) {
  await ElMessageBox.confirm(
    `取消波次 ${row.waveNo} 后，其下 ${row.orderCount} 笔订单将退回订单池，拣货任务一并清理。是否继续？`,
    '取消波次',
    { type: 'warning', confirmButtonText: '确认取消' }
  )
  await waveApi.cancel(row.waveNo)
  ElMessage.success('波次已取消，订单已退回订单池')
  await refreshAll()
}

async function onClose(row) {
  await ElMessageBox.confirm(`确认归档波次 ${row.waveNo}？`, '归档波次', { type: 'info' })
  await waveApi.close(row.waveNo)
  ElMessage.success('波次已归档')
  await load()
}

function gotoDetail(row) {
  router.push(`/wave/detail/${row.waveNo}`)
}

function progressColor(p) {
  if (p >= 100) return '#16a34a'
  if (p >= 85) return '#0ea5e9'
  if (p >= 40) return '#2563eb'
  return '#f59e0b'
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

/* 负载卡片 */
.workload-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.wl-card {
  width: 152px;
  padding: 11px 13px;
  border-radius: 9px;
  background: #f8fafc;
  border: 1px solid $border-base;
  transition: all 0.16s;

  &:hover {
    box-shadow: $shadow-card;
  }

  &.busy {
    background: #fff7ed;
    border-color: #fed7aa;
  }

  &.idle {
    background: #f0fdf4;
    border-color: #bbf7d0;
  }
}

.wl-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 9px;
}

.wl-avatar {
  background: linear-gradient(135deg, #60a5fa, #2563eb);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.wl-name {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
  line-height: 1.2;
}

.wl-team {
  font-size: 10.5px;
  color: $text-placeholder;
}

.wl-nums {
  display: flex;
  gap: 18px;
}

.wl-num {
  display: flex;
  flex-direction: column;
}

.wl-num .num {
  font-size: 17px;
  font-weight: 700;
  color: #f59e0b;
  line-height: 1.1;

  &.done {
    color: #16a34a;
  }
}

.wl-lbl {
  font-size: 10.5px;
  color: $text-placeholder;
}

.shortage-flag {
  color: $color-danger;
  margin-left: 5px;
  font-size: 13px;
  vertical-align: middle;
}

.progress-lbl {
  font-size: 10.5px;
  color: $text-placeholder;
  margin-top: 3px;
}

/* 生成向导 */
.strategy-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  :deep(.el-radio) {
    margin-right: 0;
    height: 38px;
  }
}

.strategy-tip {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 8px;
  font-size: 12px;
  color: $text-secondary;
  line-height: 1.6;
}

.unit-lbl {
  font-size: 12px;
  color: $text-placeholder;
  margin-left: 6px;
}

.pv-stat {
  padding: 12px 14px;
  border-radius: 9px;
  background: #f8fafc;
  border: 1px solid $border-base;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.pv-num {
  font-size: 19px;
  font-weight: 700;
  color: $color-primary;
  line-height: 1.15;
}

.pv-lbl {
  font-size: 11.5px;
  color: $text-secondary;
}

.created-list {
  max-height: 320px;
  overflow-y: auto;
  text-align: left;
}

.created-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 12px;
  border-radius: 7px;
  background: #f8fafc;
  margin-bottom: 6px;
}

.created-no {
  font-size: 12.5px;
  color: $color-primary;
  font-weight: 600;
  flex-shrink: 0;
}

.created-name {
  flex: 1;
  font-size: 12.5px;
  color: $text-regular;
  min-width: 0;
}

.created-meta {
  font-size: 11.5px;
  color: $text-placeholder;
  flex-shrink: 0;
}

/* 派工 */
.assign-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 7px;
  font-size: 13px;
  margin-bottom: 14px;
}

.assign-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.assign-item {
  width: 100%;
  height: 46px;
  margin-right: 0 !important;
}

.assign-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.assign-name {
  font-weight: 600;
  font-size: 13px;
}

.assign-team {
  font-size: 11.5px;
  color: $text-placeholder;
  flex: 1;
}
</style>
