<template>
  <div class="page-container" v-loading="loading">
    <PageHeader :title="`拣货作业 · ${taskNo}`" :description="task ? `波次 ${task.waveNo}` : ''">
      <template #actions>
        <el-button :icon="Back" @click="router.back()">返回</el-button>
        <el-button
          v-if="task && task.status !== 'FINISHED'"
          type="success"
          :icon="CircleCheck"
          :disabled="pendingCount > 0"
          @click="onFinish"
        >
          完成拣货
        </el-button>
      </template>
    </PageHeader>

    <template v-if="task">
      <!-- ======================= 任务概览 ======================= -->
      <div class="card card-pad mb-12">
        <div class="exec-head">
          <div class="eh-left">
            <StatusTag :code="task.status" group="pickTaskStatus" size="default" effect="dark" />
            <span class="eh-picker">{{ task.pickerName || '未分配' }}</span>
            <span class="eh-time" v-if="task.startTime">开始于 {{ formatTimeShort(task.startTime) }}</span>
          </div>
          <div class="eh-right">
            <el-progress
              :percentage="task.progress"
              :stroke-width="10"
              :color="task.progress >= 100 ? '#16a34a' : '#2563eb'"
              style="width: 240px"
            />
          </div>
        </div>

        <el-row :gutter="12" class="mt-16">
          <el-col :span="6">
            <div class="mini-stat">
              <span class="ms-num num">{{ task.itemCount }}</span>
              <span class="ms-lbl">明细行数</span>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="mini-stat">
              <span class="ms-num num">{{ task.locationCount }}</span>
              <span class="ms-lbl">涉及库位</span>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="mini-stat">
              <span class="ms-num num success">{{ task.actualQty }}</span>
              <span class="ms-lbl">已拣件数 / 应拣 {{ task.shouldQty }}</span>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="mini-stat">
              <span class="ms-num num" :class="{ danger: task.shortageQty > 0 }">{{ task.shortageQty }}</span>
              <span class="ms-lbl">缺货件数</span>
            </div>
          </el-col>
        </el-row>

        <div v-if="task.pathText" class="path-text mt-12">
          <el-icon><Position /></el-icon>
          拣货路线：{{ task.pathText }}
        </div>
      </div>

      <!-- ======================= 明细行 ======================= -->
      <div class="card">
        <div class="card-pad" style="padding-bottom: 0">
          <div class="panel-head">
            <span class="panel-title">拣货明细（按库位路径排序）</span>
            <span class="panel-sub">
              剩余 {{ pendingCount }} 行待处理
              <el-tag v-if="pendingCount === 0" type="success" size="small" effect="plain" style="margin-left: 8px">
                已全部处理，可结单
              </el-tag>
            </span>
          </div>
        </div>

        <el-table :data="task.items" stripe height="calc(100vh - 508px)" :row-class-name="rowClass">
          <el-table-column label="序" width="56" align="center">
            <template #default="{ row }">
              <div class="seq-badge" :class="{ done: row.status !== 'PENDING', current: isCurrent(row) }">
                {{ row.seqNo }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="库位" width="120">
            <template #default="{ row }"><span class="mono loc">{{ row.locationCode }}</span></template>
          </el-table-column>
          <el-table-column label="SKU" width="115">
            <template #default="{ row }"><span class="mono">{{ row.skuCode }}</span></template>
          </el-table-column>
          <el-table-column prop="skuName" label="商品名称" min-width="150" />
          <el-table-column prop="spec" label="规格" width="95" />
          <el-table-column label="应拣数量" width="100" align="center">
            <template #default="{ row }">
              <span class="num should-qty">{{ row.shouldQty }}</span>
              <span class="unit">{{ row.unit }}</span>
            </template>
          </el-table-column>
          <el-table-column label="实拣数量" width="100" align="center">
            <template #default="{ row }">
              <span v-if="row.status === 'PENDING'" class="text-secondary">—</span>
              <span v-else class="num" :class="row.actualQty === row.shouldQty ? 'success' : 'warning'">
                {{ row.actualQty }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="缺货" width="80" align="center">
            <template #default="{ row }">
              <span v-if="row.shortageQty > 0" class="num danger">{{ row.shortageQty }}</span>
              <span v-else class="text-secondary">—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="105">
            <template #default="{ row }"><StatusTag :code="row.status" group="pickItemStatus" /></template>
          </el-table-column>
          <el-table-column label="关联订单" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="mono small">{{ row.relatedOrderNos }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <template v-if="task.status !== 'FINISHED'">
                <el-button link type="primary" size="small" @click="openConfirm(row)">
                  {{ row.status === 'PENDING' ? '确认拣货' : '修改' }}
                </el-button>
                <el-button link type="danger" size="small" @click="openShortage(row)">上报缺货</el-button>
              </template>
              <span v-else class="text-secondary">已结单</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- ======================= 确认拣货 ======================= -->
    <el-dialog v-model="confirmVisible" title="确认拣货" width="460px">
      <div v-if="currentItem" class="confirm-body">
        <div class="cb-loc">
          <el-icon><Location /></el-icon>
          库位 <b class="mono">{{ currentItem.locationCode }}</b>
        </div>
        <div class="cb-sku">
          <div class="cb-name">{{ currentItem.skuName }}</div>
          <div class="cb-meta">
            <span class="mono">{{ currentItem.skuCode }}</span>
            <span>·</span>
            <span>{{ currentItem.spec }}</span>
          </div>
        </div>

        <div class="cb-qty">
          <span class="cb-qty-label">应拣数量</span>
          <span class="cb-qty-value num">{{ currentItem.shouldQty }} {{ currentItem.unit }}</span>
        </div>

        <div class="cb-input">
          <span class="cb-qty-label">实拣数量</span>
          <el-input-number
            v-model="confirmQty"
            :min="0"
            :max="currentItem.shouldQty"
            controls-position="right"
            style="width: 160px"
          />
          <span class="unit">{{ currentItem.unit }}</span>
        </div>

        <el-alert v-if="confirmQty < currentItem.shouldQty" type="warning" :closable="false" show-icon class="mt-12">
          <template #title>
            实拣少于应拣，差额 <b>{{ currentItem.shouldQty - confirmQty }}</b> 件将自动生成缺货记录，待波次员处理。
          </template>
        </el-alert>

        <el-input v-model="confirmRemark" placeholder="备注（可选）" class="mt-12" />
      </div>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" @click="doConfirm">确认</el-button>
      </template>
    </el-dialog>

    <!-- ======================= 上报缺货 ======================= -->
    <el-dialog v-model="shortageVisible" title="上报缺货" width="480px">
      <div v-if="currentItem" class="confirm-body">
        <div class="cb-loc">
          <el-icon><Location /></el-icon>
          库位 <b class="mono">{{ currentItem.locationCode }}</b>
        </div>
        <div class="cb-sku">
          <div class="cb-name">{{ currentItem.skuName }}</div>
          <div class="cb-meta">
            <span class="mono">{{ currentItem.skuCode }}</span>
            <span>·</span>
            <span>应拣 {{ currentItem.shouldQty }} {{ currentItem.unit }}</span>
          </div>
        </div>

        <el-form label-width="90px" class="mt-16">
          <el-form-item label="缺货数量">
            <el-input-number v-model="shortageQty" :min="1" :max="currentItem.shouldQty" controls-position="right" style="width: 160px" />
            <span class="unit" style="margin-left: 6px">{{ currentItem.unit }}</span>
          </el-form-item>
          <el-form-item label="缺货原因">
            <el-select v-model="shortageReason" style="width: 100%">
              <el-option
                v-for="o in dict.options('shortageReason')"
                :key="o.code"
                :label="o.desc"
                :value="o.code"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="补充说明">
            <el-input v-model="shortageRemark" type="textarea" :rows="2" placeholder="例如：库位实物仅剩 2 件，账实不符" />
          </el-form-item>
        </el-form>

        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            上报后该行会标记为缺货，波次员将在「缺货处理」页决定拆单 / 取消 / 替代 / 等补货。你可以继续拣下一行。
          </template>
        </el-alert>
      </div>
      <template #footer>
        <el-button @click="shortageVisible = false">取消</el-button>
        <el-button type="danger" @click="doReportShortage">确认上报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back, CircleCheck, Position, Location } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { pickApi } from '@/api'
import { useDictStore } from '@/stores/dict'
import { formatTimeShort } from '@/utils'

const route = useRoute()
const router = useRouter()
const dict = useDictStore()

const loading = ref(false)
const task = ref(null)
const taskNo = computed(() => route.params.taskNo)

const pendingCount = computed(() => (task.value?.items || []).filter((i) => i.status === 'PENDING').length)

/** 当前应拣的第一行（用于高亮） */
const currentSeq = computed(() => {
  const first = (task.value?.items || []).find((i) => i.status === 'PENDING')
  return first ? first.seqNo : -1
})

const isCurrent = (row) => row.seqNo === currentSeq.value

function rowClass({ row }) {
  if (row.status === 'PENDING' && isCurrent(row)) return 'row-current'
  if (row.status === 'SHORTAGE' || row.status === 'PARTIAL') return 'row-warn'
  return ''
}

/* ------------------------------ 加载 ------------------------------ */

async function load() {
  loading.value = true
  try {
    task.value = await pickApi.detail(taskNo.value)
  } finally {
    loading.value = false
  }
}

/* ------------------------------ 确认拣货 ------------------------------ */

const confirmVisible = ref(false)
const currentItem = ref(null)
const confirmQty = ref(0)
const confirmRemark = ref('')

function openConfirm(row) {
  currentItem.value = row
  confirmQty.value = row.status === 'PENDING' ? row.shouldQty : row.actualQty
  confirmRemark.value = row.remark || ''
  confirmVisible.value = true
}

async function doConfirm() {
  await pickApi.confirmItem({
    itemId: currentItem.value.id,
    actualQty: confirmQty.value,
    pickerNo: task.value.pickerNo,
    remark: confirmRemark.value
  })
  ElMessage.success(confirmQty.value < currentItem.value.shouldQty ? '已确认，差额已生成缺货记录' : '已确认拣货')
  confirmVisible.value = false
  await load()
  window.__wmsRefreshBadge?.()
}

/* ------------------------------ 上报缺货 ------------------------------ */

const shortageVisible = ref(false)
const shortageQty = ref(1)
const shortageReason = ref('STOCK_ZERO')
const shortageRemark = ref('')

function openShortage(row) {
  currentItem.value = row
  shortageQty.value = Math.max(1, row.shouldQty - (row.actualQty || 0))
  shortageReason.value = 'STOCK_ZERO'
  shortageRemark.value = ''
  shortageVisible.value = true
}

async function doReportShortage() {
  await pickApi.reportShortage({
    itemId: currentItem.value.id,
    shortageQty: shortageQty.value,
    reason: shortageReason.value,
    pickerNo: task.value.pickerNo,
    pickerName: task.value.pickerName,
    remark: shortageRemark.value
  })
  ElMessage.warning('缺货已上报，等待波次员处理')
  shortageVisible.value = false
  await load()
  window.__wmsRefreshBadge?.()
}

/* ------------------------------ 结单 ------------------------------ */

async function onFinish() {
  await ElMessageBox.confirm(
    `确认结单？本任务共 ${task.value.itemCount} 行，实拣 ${task.value.actualQty} 件，缺货 ${task.value.shortageQty} 件。结单后波次将进入复核环节。`,
    '完成拣货',
    { type: 'warning', confirmButtonText: '确认结单' }
  )
  await pickApi.finish(task.value.taskNo)
  ElMessage.success('拣货任务已结单，等待复核')
  await load()
  window.__wmsRefreshBadge?.()
}

watch(taskNo, load)
onMounted(load)
</script>

<style scoped lang="scss">
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

/* 概览 */
.exec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.eh-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.eh-picker {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
}

.eh-time {
  font-size: 12px;
  color: $text-placeholder;
}

.mini-stat {
  padding: 11px 14px;
  border-radius: 9px;
  background: #f8fafc;
  border: 1px solid $border-base;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.ms-num {
  font-size: 20px;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.1;

  &.success {
    color: $color-success;
  }

  &.danger {
    color: $color-danger;
  }
}

.ms-lbl {
  font-size: 11.5px;
  color: $text-secondary;
}

.path-text {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 12px;
  background: #eff6ff;
  border-radius: 7px;
  font-size: 12px;
  color: #1d4ed8;
  word-break: break-all;
}

/* 序号徽标 */
.seq-badge {
  width: 24px;
  height: 24px;
  margin: 0 auto;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  background: #f1f5f9;
  color: $text-secondary;
  border: 1px solid $border-base;

  &.done {
    background: #f0fdf4;
    color: $color-success;
    border-color: #bbf7d0;
  }

  &.current {
    background: $color-primary;
    color: #fff;
    border-color: $color-primary;
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.16);
  }
}

.loc {
  font-weight: 600;
  color: #1d4ed8;
}

.should-qty {
  font-size: 15px;
  font-weight: 700;
  color: $text-primary;
}

.unit {
  font-size: 11px;
  color: $text-placeholder;
  margin-left: 2px;
}

.small {
  font-size: 11.5px;
}

:deep(.row-current) {
  background: #eff6ff !important;

  td {
    background: #eff6ff !important;
  }
}

:deep(.row-warn) {
  background: #fffbf5 !important;

  td {
    background: #fffbf5 !important;
  }
}

/* 对话框内容 */
.cb-loc {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: $text-secondary;
  padding: 9px 12px;
  background: #eff6ff;
  border-radius: 7px;
}

.cb-sku {
  margin: 12px 0;
}

.cb-name {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
}

.cb-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: $text-placeholder;
  margin-top: 3px;
}

.cb-qty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 7px;
  margin-bottom: 12px;
}

.cb-qty-label {
  font-size: 12.5px;
  color: $text-secondary;
}

.cb-qty-value {
  font-size: 17px;
  font-weight: 700;
  color: $text-primary;
}

.cb-input {
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>
