<template>
  <div class="page-container" v-loading="loading">
    <PageHeader
      :title="`复核作业 · ${reviewNo}`"
      description="逐行核对实物与订单应发数量。核对数量与应发不一致的行会自动进入差异登记，需选择差异类型并判定责任环节。"
    >
      <template #actions>
        <el-button :icon="Back" @click="goBack">返回列表</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <template v-if="review">
      <!-- ======================= 概览 ======================= -->
      <div class="card card-pad mb-12">
        <div class="ov-head">
          <div class="ov-title">
            <StatusTag :code="review.status" group="reviewStatus" size="default" />
            <span class="mono ov-no">{{ review.reviewNo }}</span>
          </div>
          <div class="ov-actions">
            <el-button
              v-if="['WAITING', 'DIFF'].includes(review.status)"
              type="primary"
              :icon="Select"
              :loading="submitting"
              @click="openSubmit"
            >
              提交复核结果
            </el-button>
            <el-tag v-else type="info" effect="plain">该复核单已提交，仅供查看</el-tag>
          </div>
        </div>

        <div class="ov-grid">
          <div class="ov-item">
            <span class="ov-label">波次号</span>
            <el-link type="primary" :underline="false" @click="gotoWave">
              <span class="mono">{{ review.waveNo }}</span>
            </el-link>
          </div>
          <div class="ov-item">
            <span class="ov-label">订单号</span>
            <span class="mono">{{ review.orderNo }}</span>
          </div>
          <div class="ov-item">
            <span class="ov-label">复核员</span>
            <span>{{ review.reviewerName || '—' }}</span>
          </div>
          <div class="ov-item">
            <span class="ov-label">开始时间</span>
            <span>{{ formatTime(review.startTime) }}</span>
          </div>
          <div class="ov-item">
            <span class="ov-label">完成时间</span>
            <span>{{ formatTime(review.finishTime) }}</span>
          </div>
          <div class="ov-item">
            <span class="ov-label">复核耗时</span>
            <span class="num">{{ formatDuration(review.durationSec) }}</span>
          </div>
        </div>

        <div class="ov-stats">
          <div class="ov-stat">
            <div class="os-label">应复核 SKU</div>
            <div class="os-value"><span class="num">{{ review.shouldSkuCount }}</span> 种</div>
          </div>
          <div class="ov-stat">
            <div class="os-label">应复核数量</div>
            <div class="os-value"><span class="num">{{ review.shouldQty }}</span> 件</div>
          </div>
          <div class="ov-stat">
            <div class="os-label">本次核对实收</div>
            <div class="os-value"><span class="num">{{ checkedTotal }}</span> 件</div>
          </div>
          <div class="ov-stat" :class="{ 'is-bad': diffCount > 0 }">
            <div class="os-label">差异行数</div>
            <div class="os-value">
              <span class="num">{{ diffCount }}</span> 行
              <span v-if="diffCount" class="os-warn">需登记</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ======================= 核对明细 ======================= -->
      <div class="card mb-12">
        <div class="card-head">
          <span class="panel-title">核对明细</span>
          <span class="panel-sub">应发数量取自订单，实拣数量取自拣货回写。修改「本次核对」后不一致的行自动列为差异。</span>
        </div>
        <el-table :data="lines" stripe size="small" :row-class-name="lineRowClass">
          <el-table-column label="行号" width="60" align="center">
            <template #default="{ row }"><span class="num">{{ row.lineNo }}</span></template>
          </el-table-column>
          <el-table-column label="库位" width="120">
            <template #default="{ row }"><span class="mono">{{ row.locationCode }}</span></template>
          </el-table-column>
          <el-table-column label="商品" min-width="200">
            <template #default="{ row }">
              <div class="sku-cell">
                <span>{{ row.skuName }}</span>
                <span class="sku-code mono">{{ row.skuCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="规格" width="110">
            <template #default="{ row }"><span class="text-secondary">{{ row.spec }}</span></template>
          </el-table-column>
          <el-table-column label="单位" width="65" align="center">
            <template #default="{ row }">{{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="应发" width="75" align="right">
            <template #default="{ row }"><span class="num">{{ row.qty }}</span></template>
          </el-table-column>
          <el-table-column label="拣货回写" width="90" align="right">
            <template #default="{ row }">
              <span class="num" :class="{ danger: (row.pickedQty || 0) < row.qty }">{{ row.pickedQty || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="本次核对" width="140" align="center">
            <template #default="{ row }">
              <el-input-number
                v-model="row.checkQty"
                :min="0"
                :max="999"
                :disabled="!editable"
                size="small"
                controls-position="right"
                style="width: 110px"
              />
            </template>
          </el-table-column>
          <el-table-column label="差异" width="80" align="right">
            <template #default="{ row }">
              <span v-if="rowDiff(row) === 0" class="text-success">—</span>
              <span v-else class="num text-danger">{{ rowDiff(row) > 0 ? '+' : '' }}{{ rowDiff(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="差异类型" width="140">
            <template #default="{ row }">
              <el-select
                v-if="rowDiff(row) !== 0"
                v-model="row.diffType"
                size="small"
                :disabled="!editable"
                style="width: 120px"
              >
                <el-option
                  v-for="o in diffTypeOptions"
                  :key="o.code"
                  :label="o.desc"
                  :value="o.code"
                />
              </el-select>
              <span v-else class="text-secondary">—</span>
            </template>
          </el-table-column>
          <el-table-column label="责任环节" width="130">
            <template #default="{ row }">
              <el-select
                v-if="rowDiff(row) !== 0"
                v-model="row.responsibleStage"
                size="small"
                :disabled="!editable"
                style="width: 110px"
              >
                <el-option v-for="s in STAGES" :key="s.code" :label="s.desc" :value="s.code" />
              </el-select>
              <span v-else class="text-secondary">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- ======================= 其他差异 ======================= -->
      <div v-if="editable" class="card mb-12">
        <div class="card-head">
          <span class="panel-title">其他差异登记</span>
          <span class="panel-sub">错货、串单、破损等无法用数量差表达的问题，在此单独登记。</span>
          <el-button size="small" :icon="Plus" class="head-btn" @click="addExtraDiff">追加差异</el-button>
        </div>
        <el-table v-if="extraDiffs.length" :data="extraDiffs" stripe size="small">
          <el-table-column label="商品" min-width="200">
            <template #default="{ row }">
              <el-select
                v-model="row.skuCode"
                filterable
                size="small"
                placeholder="选择商品"
                style="width: 100%"
                @change="(v) => onExtraSkuChange(row, v)"
              >
                <el-option
                  v-for="s in review.shouldItems"
                  :key="s.skuCode"
                  :label="`${s.skuName}（${s.spec}）`"
                  :value="s.skuCode"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="应发" width="110" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.shouldQty" :min="0" size="small" controls-position="right" style="width: 90px" />
            </template>
          </el-table-column>
          <el-table-column label="实收" width="110" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.actualQty" :min="0" size="small" controls-position="right" style="width: 90px" />
            </template>
          </el-table-column>
          <el-table-column label="差异类型" width="140">
            <template #default="{ row }">
              <el-select v-model="row.diffType" size="small" style="width: 120px">
                <el-option v-for="o in diffTypeOptions" :key="o.code" :label="o.desc" :value="o.code" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="责任环节" width="130">
            <template #default="{ row }">
              <el-select v-model="row.responsibleStage" size="small" style="width: 110px">
                <el-option v-for="s in STAGES" :key="s.code" :label="s.desc" :value="s.code" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.remark" size="small" placeholder="问题描述" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="extraDiffs.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无其他差异登记" :image-size="60" />
      </div>

      <!-- ======================= 打包信息 ======================= -->
      <div class="card card-pad">
        <div class="panel-head">
          <span class="panel-title">打包信息</span>
          <span class="panel-sub">提交后该订单进入「已复核」，波次内全部订单复核完毕即推进至出库交接。</span>
        </div>
        <el-form :model="pack" inline class="pack-form" :disabled="!editable">
          <el-form-item label="包装材料">
            <el-select v-model="pack.packMaterial" style="width: 140px">
              <el-option v-for="o in dict.options('packMaterial')" :key="o.code" :label="o.desc" :value="o.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="包裹数">
            <el-input-number v-model="pack.packageCount" :min="1" :max="50" style="width: 130px" />
          </el-form-item>
          <el-form-item label="重量(kg)">
            <el-input-number v-model="pack.weight" :min="0" :max="999" :precision="2" :step="0.5" style="width: 140px" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="pack.remark" placeholder="如：外箱轻微压痕，已加护角" style="width: 320px" />
          </el-form-item>
        </el-form>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="复核单不存在" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back, Refresh, Select, Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { reviewApi } from '@/api'
import { useDictStore } from '@/stores/dict'
import { useAppStore } from '@/stores/app'
import { formatDuration, formatTime } from '@/utils'

const route = useRoute()
const router = useRouter()
const dict = useDictStore()
const app = useAppStore()

const reviewNo = route.params.reviewNo
const loading = ref(false)
const submitting = ref(false)
const review = ref(null)
const lines = ref([])
const extraDiffs = ref([])

const pack = reactive({
  packMaterial: '纸箱',
  packageCount: 1,
  weight: null,
  remark: ''
})

const STAGES = [
  { code: 'PICK', desc: '拣货环节' },
  { code: 'WAVE', desc: '波次环节' },
  { code: 'DATA', desc: '基础数据' }
]

/** 差异类型下拉：排除「无差异」 */
const diffTypeOptions = computed(() => dict.options('diffType').filter((o) => o.code !== 'NONE'))

/** 已提交的复核单只读 */
const editable = computed(() => ['WAITING', 'DIFF'].includes(review.value?.status))

/* ------------------------------ 计算 ------------------------------ */

const checkedTotal = computed(() => lines.value.reduce((s, l) => s + (Number(l.checkQty) || 0), 0))

const diffCount = computed(
  () => lines.value.filter((l) => rowDiff(l) !== 0).length + extraDiffs.value.length
)

function rowDiff(row) {
  return (Number(row.checkQty) || 0) - Number(row.qty || 0)
}

function lineRowClass({ row }) {
  return rowDiff(row) !== 0 ? 'row-diff' : ''
}

/* ------------------------------ 加载 ------------------------------ */

async function load() {
  loading.value = true
  try {
    const data = await reviewApi.detail(reviewNo)
    review.value = data
    pack.packMaterial = data.packMaterial || '纸箱'
    pack.packageCount = data.packageCount || 1
    pack.weight = data.weight ?? null
    pack.remark = data.remark || ''

    lines.value = (data.shouldItems || []).map((i) => ({
      ...i,
      // 默认核对数量 = 拣货回写数量；没有回写则按应发
      checkQty: Number(i.pickedQty ?? i.qty) || 0,
      diffType: 'SHORT',
      responsibleStage: 'PICK'
    }))

    extraDiffs.value = (data.diffItems || []).map((d) => ({
      skuCode: d.skuCode,
      shouldQty: d.shouldQty,
      actualQty: d.actualQty,
      diffType: d.diffType || 'SHORT',
      responsibleStage: d.responsibleStage || 'PICK',
      remark: d.remark || ''
    }))
  } finally {
    loading.value = false
  }
}

/* ------------------------------ 交互 ------------------------------ */

function addExtraDiff() {
  extraDiffs.value.push({
    skuCode: '',
    shouldQty: 0,
    actualQty: 0,
    diffType: 'WRONG_SKU',
    responsibleStage: 'PICK',
    remark: ''
  })
}

function onExtraSkuChange(row, skuCode) {
  const hit = (review.value?.shouldItems || []).find((s) => s.skuCode === skuCode)
  if (hit && !row.shouldQty) row.shouldQty = hit.qty
}

function buildDiffItems() {
  const items = []
  lines.value.forEach((l) => {
    const diff = rowDiff(l)
    if (diff === 0) return
    items.push({
      skuCode: l.skuCode,
      skuName: l.skuName,
      shouldQty: Number(l.qty) || 0,
      actualQty: Number(l.checkQty) || 0,
      diffType: l.diffType,
      responsibleStage: l.responsibleStage,
      responsibleNo: app.currentUser.employeeNo,
      remark: `核对差异 ${diff > 0 ? '+' : ''}${diff}`
    })
  })
  extraDiffs.value.forEach((d) => {
    if (!d.skuCode) return
    const hit = (review.value?.shouldItems || []).find((s) => s.skuCode === d.skuCode)
    items.push({
      skuCode: d.skuCode,
      skuName: hit?.skuName || d.skuCode,
      shouldQty: Number(d.shouldQty) || 0,
      actualQty: Number(d.actualQty) || 0,
      diffType: d.diffType,
      responsibleStage: d.responsibleStage,
      responsibleNo: app.currentUser.employeeNo,
      remark: d.remark || null
    })
  })
  return items
}

async function openSubmit() {
  const diffs = buildDiffItems()
  const text = diffs.length
    ? `本次复核登记 ${diffs.length} 项差异，将计入差错率统计。确认提交？`
    : '本次复核无差异，确认提交为「复核通过」？'
  await ElMessageBox.confirm(text, '提交确认', {
    type: diffs.length ? 'warning' : 'success',
    confirmButtonText: '确认提交',
    cancelButtonText: '再核对一下'
  })

  submitting.value = true
  try {
    await reviewApi.submit({
      reviewNo,
      reviewerNo: app.currentUser.employeeNo,
      reviewerName: app.currentUser.employeeName,
      packMaterial: pack.packMaterial,
      packageCount: pack.packageCount,
      weight: pack.weight,
      remark: pack.remark,
      actualQty: checkedTotal.value,
      diffItems: diffs
    })
    ElMessage.success(diffs.length ? '已提交，差异已登记' : '复核通过')
    await load()
    window.__wmsRefreshBadge?.()
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push('/review')
}

function gotoWave() {
  if (review.value?.waveNo) router.push(`/wave/detail/${review.value.waveNo}`)
}

onMounted(load)
</script>

<style scoped lang="scss">
/* 概览 */
.ov-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.ov-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ov-no {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
}

.ov-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 20px;
  padding: 14px 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid $border-base;
}

.ov-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12.5px;
  min-width: 0;
}

.ov-label {
  color: $text-placeholder;
  flex-shrink: 0;
  min-width: 62px;
}

.ov-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 14px;
}

.ov-stat {
  padding: 12px 14px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f8fbff, #f1f5f9);
  border: 1px solid #e2e8f0;

  &.is-bad {
    background: linear-gradient(135deg, #fef2f2, #fff7f7);
    border-color: #fecaca;
  }
}

.os-label {
  font-size: 11.5px;
  color: $text-secondary;
  margin-bottom: 6px;
}

.os-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 11.5px;
  color: $text-placeholder;

  .num {
    font-size: 21px;
    font-weight: 700;
    color: $text-primary;
  }
}

.ov-stat.is-bad .os-value .num {
  color: $color-danger;
}

.os-warn {
  color: $color-danger;
  font-weight: 600;
}

/* 卡片头 */
.card-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 14px 18px 10px;
  border-bottom: 1px solid $border-base;
  position: relative;
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

.head-btn {
  margin-left: auto;
}

.panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

/* 表格 */
.sku-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sku-code {
  font-size: 11px;
  color: $text-placeholder;
}

:deep(.row-diff) {
  background: #fef7f7 !important;
}

.pack-form {
  :deep(.el-form-item) {
    margin-bottom: 4px;
    margin-right: 20px;
  }
}
</style>
