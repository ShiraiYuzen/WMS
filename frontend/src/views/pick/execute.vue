<template>
  <div class="page-container work-table-page" v-loading="loading">
    <PageHeader :title="`拣货作业 · ${taskNo}`" :description="task ? `波次 ${task.waveNo}` : ''">
      <template #actions>
        <el-button :icon="Back" @click="router.back()">返回</el-button>
        <el-button v-if="task?.status === 'WAITING'" type="primary" :loading="saving" @click="startTask">领取开工</el-button>
        <el-button
          v-if="task && !task.finishTime && ['PICKING', 'EXCEPTION'].includes(task.status)"
          type="success"
          :icon="CircleCheck"
          :disabled="pendingCount > 0" :loading="saving"
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
              :percentage="processedPercent" aria-label="明细处理进度"
              :stroke-width="10"
              :color="processedPercent >= 100 ? '#16a34a' : '#2563eb'"
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
      <div class="card work-table-panel pick-panel">
        <div class="card-pad" style="padding-bottom: 0">
          <div class="panel-head">
            <span class="panel-title">拣货明细（按库位路径排序）</span>
            <span class="panel-sub">
              剩余 {{ pendingCount }} 行待处理
              <el-tag v-if="pendingCount === 0 && !task.finishTime" type="success" size="small" effect="plain" style="margin-left: 8px">
                已全部处理，可结单
              </el-tag>
            </span>
          </div>
        </div>

        <div ref="pickList" class="pick-list">
          <div class="pick-columns" aria-hidden="true">
            <span>库位</span><span>商品 / 明细</span><span>应拣</span><span>实拣</span><span>状态</span><span>操作</span>
          </div>
          <ol>
            <li v-for="row in task.items" :key="row.id" class="pick-line"
                :class="{ current: isCurrent(row), shortage: row.shortageQty > 0 }"
                :aria-current="isCurrent(row) ? 'step' : undefined">
              <span class="location"><small>{{ row.seqNo }}</small><b class="mono">{{ row.locationCode }}</b></span>
              <details class="product">
                <summary>{{ row.skuName }}</summary>
                <div>{{ row.skuCode }} · {{ row.spec }}</div>
                <div>关联订单 {{ row.relatedOrderNos || '—' }}</div>
              </details>
              <span class="quantity"><small>应拣 </small><b class="num">{{ row.shouldQty }}</b> {{ row.unit }}</span>
              <span class="quantity"><small>实拣 </small>{{ row.status === 'PENDING' ? '—' : row.actualQty }}</span>
              <div class="item-status">
                <StatusTag :code="row.status" group="pickItemStatus" />
                <span v-if="row.shortageQty > 0" class="text-danger">缺 {{ row.shortageQty }} {{ row.unit }}</span>
              </div>
              <div class="item-actions" v-if="!task.finishTime && ['PICKING', 'EXCEPTION'].includes(task.status) && !row.shortageQty">
                <el-button :link="!isCurrent(row)" type="primary" @click="openConfirm(row)">
                  {{ needsPick(row) ? '确认拣货' : '修改' }}
                </el-button>
                <el-button v-if="row.shouldQty > (row.actualQty || 0) && !row.shortageQty"
                           link @click="openShortage(row)">上报缺货</el-button>
              </div>
              <span v-else class="text-secondary">{{ task.finishTime || task.status === 'FINISHED' ? '已结单' : row.shortageQty ? '缺货已上报' : '待开工' }}</span>
            </li>
          </ol>
        </div>
      </div>
    </template>

    <el-alert v-if="loadError" type="error" :closable="false">
      {{ loadError }} <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>

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
            实拣少于应拣，本次先保存实拣数量。剩余 <b>{{ currentItem.shouldQty - confirmQty }}</b> 件可继续拣货，也可通过「上报缺货」登记。
          </template>
        </el-alert>

        <el-input v-model="confirmRemark" placeholder="备注（可选）" class="mt-12" />
      </div>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doConfirm">确认</el-button>
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
            <el-input-number v-model="shortageQty" :min="1" :max="currentItem.shouldQty - (currentItem.actualQty || 0)" controls-position="right" style="width: 160px" />
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
        <el-button type="danger" :loading="saving" @click="doReportShortage">确认上报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
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

const pickList = ref(null)
const saving = ref(false)
const loadError = ref('')

function needsPick(row) {
  return row.status === 'PENDING' || (row.actualQty || 0) + (row.shortageQty || 0) < row.shouldQty
}

const pending = computed(() => (task.value?.items || []).filter(needsPick))
const pendingCount = computed(() => pending.value.length)
const processedPercent = computed(() => {
  const count = task.value?.items?.length || 0
  return count ? Math.round((count - pendingCount.value) * 100 / count) : 0
})
const isCurrent = row => !task.value?.finishTime && task.value?.status !== 'WAITING' && row.id === pending.value[0]?.id

async function revealNext() {
  await nextTick()
  const list = pickList.value
  const row = list?.querySelector('[aria-current="step"]')
  if (!row) return
  const frame = list.getBoundingClientRect()
  const box = row.getBoundingClientRect()
  if (box.top < frame.top + 36 || box.bottom > frame.bottom) list.scrollTop += box.top - frame.top - 36
}

/* ------------------------------ 加载 ------------------------------ */

async function load() {
  loading.value = true
  try {
    loadError.value = ''
    task.value = await pickApi.detail(taskNo.value)
    await revealNext()
  } catch (error) {
    loadError.value = error.message || '任务读取失败'
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
  if (saving.value) return
  saving.value = true
  try {
    task.value = await pickApi.confirmItem({
      itemId: currentItem.value.id,
      actualQty: confirmQty.value,
      pickerNo: task.value.pickerNo,
      remark: confirmRemark.value
    })
    ElMessage.success(confirmQty.value < currentItem.value.shouldQty ? '已保存实拣数量，剩余件数待处理' : '已确认拣货')
    confirmVisible.value = false
    await revealNext()
    window.__wmsRefreshBadge?.()
  } finally {
    saving.value = false
  }
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
  if (saving.value) return
  saving.value = true
  try {
    task.value = await pickApi.reportShortage({
      itemId: currentItem.value.id,
      shortageQty: shortageQty.value,
      reason: shortageReason.value,
      pickerNo: task.value.pickerNo,
      pickerName: task.value.pickerName,
      remark: shortageRemark.value
    })
    ElMessage.warning('缺货已上报，等待波次员处理')
    shortageVisible.value = false
    await revealNext()
    window.__wmsRefreshBadge?.()
  } finally {
    saving.value = false
  }
}

/* ------------------------------ 结单 ------------------------------ */

async function startTask() {
  if (saving.value) return
  saving.value = true
  try {
    task.value = await pickApi.start(task.value.taskNo)
    await revealNext()
    window.__wmsRefreshBadge?.()
  } finally {
    saving.value = false
  }
}

async function onFinish() {
  if (saving.value) return
  saving.value = true
  try {
    await ElMessageBox.confirm(
      `确认结单？本任务共 ${task.value.itemCount} 行，实拣 ${task.value.actualQty} 件，缺货 ${task.value.shortageQty} 件。结单后波次将进入复核环节。`,
      '完成拣货',
      { type: 'warning', confirmButtonText: '确认结单' }
    )
    task.value = await pickApi.finish(task.value.taskNo)
    ElMessage.success('拣货任务已结单，等待复核')
    await revealNext()
    window.__wmsRefreshBadge?.()
  } finally {
    saving.value = false
  }
}

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
  font-size: 12px;
  color: $text-secondary;
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

.pick-panel { flex: 1 0 300px; min-height: 0; }
.pick-list {
  container-type: inline-size;
  flex: 1 1 0;
  min-height: 0;
  overflow: auto;
  border-top: 1px solid $border-base;

  ol { list-style: none; margin: 0; padding: 0; }
}
.pick-columns, .pick-line {
  display: grid;
  grid-template-columns: 128px minmax(130px, 1fr) 78px 65px 100px 175px;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
}
.pick-columns {
  position: sticky;
  top: 0;
  z-index: 1;
  background: #f8fafc;
  color: $text-secondary;
  font-size: 12px;
  font-weight: 600;
}
.pick-line {
  border-bottom: 1px solid $border-base;
  font-size: 13px;

  &.shortage { background: var(--wms-warning-soft); }
  &.current { background: var(--wms-primary-soft); box-shadow: inset 3px 0 $color-primary; }
  &.current .location, &.current .quantity b { color: $color-primary-dark; }
}
.location { display: flex; gap: 8px; align-items: center; }
.location small { color: $text-secondary; }
.product { min-width: 0; overflow-wrap: anywhere; }
.product summary { cursor: pointer; font-weight: 600; }
.product div { font-size: 12px; color: $text-secondary; margin-top: 4px; }
.quantity b { font-size: 18px; }
.quantity small { display: none; }
.item-status { display: flex; gap: 4px; align-items: flex-start; flex-direction: column; }
.item-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.item-actions .el-button { margin: 0; }
@container (max-width: 800px) {
  .pick-columns { display: none; }
  .pick-line { grid-template-columns: 1fr 1fr; padding: 12px; gap: 8px; }
  .quantity small { display: inline; color: $text-secondary; }
  .item-actions { justify-content: flex-end; }
}
@media (max-width: 700px) {
  .path-text { display: block; }
  .mini-stat { padding: 8px; }
  .panel-head { flex-wrap: wrap; }
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
