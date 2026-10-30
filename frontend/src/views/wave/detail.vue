<template>
  <div class="page-container" v-loading="loading">
    <PageHeader :title="`波次详情 · ${waveNo}`" :description="wave?.waveName">
      <template #actions>
        <el-button :icon="Back" @click="router.back()">返回</el-button>
        <el-button v-if="wave?.status === 'PICKED' || wave?.status === 'REVIEWING'" type="primary" :icon="DocumentChecked" @click="gotoReview">
          进入复核
        </el-button>
      </template>
    </PageHeader>

    <template v-if="wave">
      <!-- ======================= 概览 ======================= -->
      <div class="card card-pad mb-12">
        <div class="ov-head">
          <div class="ov-status">
            <StatusTag :code="wave.status" group="waveStatus" size="default" effect="dark" />
            <el-tag v-if="wave.hasShortage" type="danger" effect="plain" size="small">
              <el-icon><WarningFilled /></el-icon> 含缺货
            </el-tag>
            <span class="ov-node">{{ wave.currentNode }}</span>
          </div>
          <el-progress
            :percentage="wave.progress"
            :stroke-width="9"
            :color="progressColor(wave.progress)"
            style="width: 260px"
          />
        </div>

        <el-steps :active="activeStep" align-center finish-status="success" class="mt-16">
          <el-step title="波次生成" :description="formatTimeShort(wave.createTime)" />
          <el-step title="分配拣货员" :description="formatTimeShort(wave.assignTime)" />
          <el-step title="拣货执行" :description="pickStepDesc" />
          <el-step title="复核打包" :description="formatTimeShort(wave.reviewFinishTime)" />
          <el-step title="出库交接" :description="formatTimeShort(wave.handoverTime)" />
        </el-steps>
      </div>

      <!-- ======================= 关键信息 ======================= -->
      <el-row :gutter="14" class="mb-12">
        <el-col :xs="24" :md="16">
          <div class="card card-pad full-h">
            <div class="panel-title mb-12">波次信息</div>
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="波次号"><span class="mono">{{ wave.waveNo }}</span></el-descriptions-item>
              <el-descriptions-item label="生成策略">{{ wave.strategyDesc }}</el-descriptions-item>
              <el-descriptions-item label="覆盖区域">{{ wave.provinceScope || '—' }}</el-descriptions-item>
              <el-descriptions-item label="订单数"><span class="num">{{ wave.orderCount }}</span> 单</el-descriptions-item>
              <el-descriptions-item label="SKU 种类"><span class="num">{{ wave.skuCount }}</span> 种</el-descriptions-item>
              <el-descriptions-item label="商品件数"><span class="num">{{ wave.totalQty }}</span> 件</el-descriptions-item>
              <el-descriptions-item label="涉及库位"><span class="num">{{ wave.locationCount }}</span> 个</el-descriptions-item>
              <el-descriptions-item label="创建人">{{ wave.creatorName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="拣货员">{{ wave.pickerName || '未分配' }}</el-descriptions-item>
              <el-descriptions-item label="拣货耗时">
                <span class="num">{{ formatDuration(wave.pickDurationSec) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="全流程耗时">
                <span class="num">{{ formatDuration(wave.totalDurationSec) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="复核员">{{ wave.reviewerName || '—' }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </el-col>

        <el-col :xs="24" :md="8">
          <div class="card card-pad full-h">
            <div class="panel-title mb-12">作业质量</div>
            <div class="qa-list">
              <div class="qa-item">
                <span class="qa-lbl">应拣件数</span>
                <span class="qa-val num">{{ pickTask?.shouldQty ?? wave.totalQty }}</span>
              </div>
              <div class="qa-item">
                <span class="qa-lbl">实拣件数</span>
                <span class="qa-val num success">{{ pickTask?.actualQty ?? '—' }}</span>
              </div>
              <div class="qa-item">
                <span class="qa-lbl">缺货件数</span>
                <span class="qa-val num" :class="{ danger: (pickTask?.shortageQty || 0) > 0 }">
                  {{ pickTask?.shortageQty ?? 0 }}
                </span>
              </div>
              <div class="qa-item">
                <span class="qa-lbl">复核单数</span>
                <span class="qa-val num">{{ reviews.length }}</span>
              </div>
              <div class="qa-item">
                <span class="qa-lbl">差异单数</span>
                <span class="qa-val num" :class="{ danger: diffCount > 0 }">{{ diffCount }}</span>
              </div>
              <div class="qa-item">
                <span class="qa-lbl">拣货准确率</span>
                <span class="qa-val num" :class="accuracyTone">{{ accuracyText }}</span>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- ======================= 明细页签 ======================= -->
      <div class="card card-pad">
        <el-tabs v-model="tab">
          <!-- 订单 -->
          <el-tab-pane :label="`订单明细 (${wave.orders?.length || 0})`" name="orders">
            <el-table :data="wave.orders" size="small" border max-height="440">
              <el-table-column type="index" label="#" width="44" align="center" />
              <el-table-column prop="orderNo" label="订单号" width="170">
                <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
              </el-table-column>
              <el-table-column prop="platform" label="平台" width="90" />
              <el-table-column prop="receiverName" label="收货人" width="90" />
              <el-table-column label="收货地址" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">{{ row.province }}{{ row.city }}{{ row.receiverAddress }}</template>
              </el-table-column>
              <el-table-column prop="carrierName" label="承运商" width="100" />
              <el-table-column label="件数" width="70" align="right">
                <template #default="{ row }"><span class="num">{{ row.totalQty }}</span></template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }"><StatusTag :code="row.status" group="orderStatus" /></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- 拣货明细 -->
          <el-tab-pane :label="`拣货明细 (${pickTask?.items?.length || 0})`" name="pick">
            <div v-if="pickTask" class="pick-summary">
              <span>任务号 <b class="mono">{{ pickTask.taskNo }}</b></span>
              <span>拣货员 <b>{{ pickTask.pickerName || '未分配' }}</b></span>
              <span>进度 <b class="num">{{ pickTask.finishedCount }}/{{ pickTask.itemCount }}</b> 行</span>
              <span>状态 <StatusTag :code="pickTask.status" group="pickTaskStatus" /></span>
            </div>
            <div v-if="pickTask?.pathText" class="path-text">
              <el-icon><Position /></el-icon>
              拣货路线：{{ pickTask.pathText }}
            </div>
            <el-table :data="pickTask?.items || []" size="small" border max-height="380" class="mt-12">
              <el-table-column prop="seqNo" label="序" width="50" align="center" />
              <el-table-column prop="locationCode" label="库位" width="110">
                <template #default="{ row }"><span class="mono">{{ row.locationCode }}</span></template>
              </el-table-column>
              <el-table-column prop="skuCode" label="SKU" width="105">
                <template #default="{ row }"><span class="mono">{{ row.skuCode }}</span></template>
              </el-table-column>
              <el-table-column prop="skuName" label="商品名称" min-width="140" />
              <el-table-column prop="spec" label="规格" width="90" />
              <el-table-column label="应拣" width="70" align="right">
                <template #default="{ row }"><span class="num">{{ row.shouldQty }}</span></template>
              </el-table-column>
              <el-table-column label="实拣" width="70" align="right">
                <template #default="{ row }">
                  <span class="num" :class="{ success: row.actualQty === row.shouldQty && row.status !== 'PENDING' }">
                    {{ row.actualQty }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="缺货" width="70" align="right">
                <template #default="{ row }">
                  <span class="num" :class="{ danger: row.shortageQty > 0 }">{{ row.shortageQty || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }"><StatusTag :code="row.status" group="pickItemStatus" /></template>
              </el-table-column>
              <el-table-column label="关联订单" min-width="180" show-overflow-tooltip>
                <template #default="{ row }"><span class="mono small">{{ row.relatedOrderNos }}</span></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- 复核记录 -->
          <el-tab-pane :label="`复核记录 (${reviews.length})`" name="review">
            <el-table :data="reviews" size="small" border max-height="440">
              <el-table-column prop="reviewNo" label="复核单号" width="160">
                <template #default="{ row }"><span class="mono">{{ row.reviewNo }}</span></template>
              </el-table-column>
              <el-table-column prop="orderNo" label="订单号" width="170">
                <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
              </el-table-column>
              <el-table-column prop="reviewerName" label="复核员" width="90" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }"><StatusTag :code="row.status" group="reviewStatus" /></template>
              </el-table-column>
              <el-table-column label="应拣/实拣" width="110" align="center">
                <template #default="{ row }">
                  <span class="num">{{ row.shouldQty }}</span>
                  <span class="text-secondary"> / </span>
                  <span class="num">{{ row.actualQty }}</span>
                </template>
              </el-table-column>
              <el-table-column label="差异" width="90" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.diffQty > 0" type="danger" size="small" effect="plain">{{ row.diffQty }} 件</el-tag>
                  <span v-else class="text-secondary">—</span>
                </template>
              </el-table-column>
              <el-table-column prop="packMaterial" label="包材" width="85" />
              <el-table-column label="包裹" width="75" align="right">
                <template #default="{ row }"><span class="num">{{ row.packageCount }}</span></template>
              </el-table-column>
              <el-table-column label="重量" width="85" align="right">
                <template #default="{ row }"><span class="num">{{ row.weight ?? '—' }}</span> kg</template>
              </el-table-column>
              <el-table-column prop="diffDetail" label="差异说明" min-width="180" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <!-- 缺货 -->
          <el-tab-pane :label="`缺货记录 (${shortages.length})`" name="shortage">
            <el-table :data="shortages" size="small" border max-height="440">
              <el-table-column prop="shortageNo" label="缺货单号" width="165">
                <template #default="{ row }"><span class="mono">{{ row.shortageNo }}</span></template>
              </el-table-column>
              <el-table-column prop="locationCode" label="库位" width="110">
                <template #default="{ row }"><span class="mono">{{ row.locationCode }}</span></template>
              </el-table-column>
              <el-table-column prop="skuName" label="商品" min-width="130" />
              <el-table-column label="应拣/缺货" width="110" align="center">
                <template #default="{ row }">
                  <span class="num">{{ row.shouldQty }}</span>
                  <span class="text-secondary"> / </span>
                  <span class="num danger">{{ row.shortageQty }}</span>
                </template>
              </el-table-column>
              <el-table-column label="缺货原因" width="130">
                <template #default="{ row }">{{ dict.text('shortageReason', row.reason) }}</template>
              </el-table-column>
              <el-table-column prop="reporterName" label="上报人" width="90" />
              <el-table-column label="处理状态" width="120">
                <template #default="{ row }"><StatusTag :code="row.status" group="shortageStatus" /></template>
              </el-table-column>
              <el-table-column label="处理方式" width="120">
                <template #default="{ row }">
                  {{ row.handleType ? dict.text('shortageHandleType', row.handleType) : '—' }}
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!shortages.length" description="该波次无缺货记录" :image-size="70" />
          </el-tab-pane>

          <!-- 流转轨迹 -->
          <el-tab-pane label="流转轨迹" name="trace">
            <el-timeline class="mt-8">
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
            </el-timeline>
            <el-empty v-if="!trace.length" description="暂无流转记录" :image-size="70" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, DocumentChecked, WarningFilled, Position } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { waveApi, pickApi, reviewApi, shortageApi, logApi } from '@/api'
import { useDictStore } from '@/stores/dict'
import { formatTime, formatTimeShort, formatDuration } from '@/utils'

const route = useRoute()
const router = useRouter()
const dict = useDictStore()

const loading = ref(false)
const waveNo = computed(() => route.params.waveNo)
const wave = ref(null)
const pickTask = ref(null)
const reviews = ref([])
const shortages = ref([])
const trace = ref([])
const tab = ref('orders')

/* ------------------------------ 派生 ------------------------------ */

const STEP_INDEX = {
  CREATED: 0,
  ASSIGNED: 1,
  PICKING: 2,
  PICKED: 3,
  REVIEWING: 3,
  REVIEWED: 4,
  HANDOVERED: 5,
  CLOSED: 5
}

const activeStep = computed(() => STEP_INDEX[wave.value?.status] ?? 0)

const pickStepDesc = computed(() => {
  const w = wave.value
  if (!w) return ''
  if (w.pickFinishTime) return `${formatTimeShort(w.pickStartTime)} → ${formatTimeShort(w.pickFinishTime)}`
  if (w.pickStartTime) return `进行中 · ${formatTimeShort(w.pickStartTime)} 开始`
  return '待开始'
})

const diffCount = computed(() => reviews.value.filter((r) => r.status === 'DIFF').length)

const accuracyText = computed(() => {
  const should = pickTask.value?.shouldQty
  const actual = pickTask.value?.actualQty
  if (!should || actual === null || actual === undefined) return '—'
  return `${((actual / should) * 100).toFixed(1)}%`
})

const accuracyTone = computed(() => {
  const t = accuracyText.value
  if (t === '—') return {}
  const v = parseFloat(t)
  return { success: v >= 99, warning: v >= 95 && v < 99, danger: v < 95 }
})

/* ------------------------------ 加载 ------------------------------ */

async function load() {
  loading.value = true
  try {
    wave.value = await waveApi.detail(waveNo.value)

    const taskNo = `PK${waveNo.value.slice(2)}`
    try {
      pickTask.value = await pickApi.detail(taskNo)
    } catch {
      pickTask.value = null
    }

    const reviewRes = await reviewApi.page({ waveNo: waveNo.value, pageSize: 200 })
    reviews.value = reviewRes.records

    const shortRes = await shortageApi.page({ waveNo: waveNo.value, pageSize: 200 })
    shortages.value = shortRes.records

    trace.value = await logApi.trace(waveNo.value)
  } finally {
    loading.value = false
  }
}

function gotoReview() {
  router.push('/review')
}

function progressColor(p) {
  if (p >= 100) return '#16a34a'
  if (p >= 85) return '#0ea5e9'
  if (p >= 40) return '#2563eb'
  return '#f59e0b'
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

watch(waveNo, load)
onMounted(load)
</script>

<style scoped lang="scss">
.full-h {
  height: 100%;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
}

.ov-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.ov-status {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ov-node {
  font-size: 12.5px;
  color: $text-secondary;
}

/* 质量面板 */
.qa-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.qa-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 2px;
  border-bottom: 1px dashed $border-base;

  &:last-child {
    border-bottom: none;
  }
}

.qa-lbl {
  font-size: 12.5px;
  color: $text-secondary;
}

.qa-val {
  font-size: 16px;
  font-weight: 700;
  color: $text-primary;

  &.success {
    color: $color-success;
  }

  &.danger {
    color: $color-danger;
  }
}

/* 拣货明细头部 */
.pick-summary {
  display: flex;
  align-items: center;
  gap: 22px;
  flex-wrap: wrap;
  padding: 10px 14px;
  background: #f8fafc;
  border-radius: 8px;
  font-size: 12.5px;
  color: $text-secondary;

  b {
    color: $text-primary;
  }
}

.path-text {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 9px;
  padding: 8px 12px;
  background: #eff6ff;
  border-radius: 7px;
  font-size: 12px;
  color: #1d4ed8;
  word-break: break-all;
}

.small {
  font-size: 11.5px;
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
