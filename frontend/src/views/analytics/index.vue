<template>
  <div class="page-container" v-loading="loading">
    <PageHeader
      title="效率复盘"
      description="按时间区间汇总拣货效率、差错率与缺货率，定位效率洼地与差错来源。指标超出阈值时自动预警，为班组排班与培训提供依据。"
    >
      <template #actions>
        <el-radio-group v-model="quick" size="default" @change="onQuickChange">
          <el-radio-button value="7">近 7 天</el-radio-button>
          <el-radio-button value="30">近 30 天</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :clearable="false"
          style="width: 250px"
          @change="onDateChange"
        />
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
      </template>
    </PageHeader>

    <!-- ======================= 指标预警 ======================= -->
    <div v-if="ov.alerts && ov.alerts.length" class="alert-row mb-12">
      <div v-for="a in ov.alerts" :key="a.metric" class="alert-item" :class="`lv-${a.level.toLowerCase()}`">
        <el-icon class="ai-icon"><WarningFilled /></el-icon>
        <div class="ai-body">
          <div class="ai-name">{{ a.metricName }}</div>
          <div class="ai-msg">{{ a.message }}</div>
        </div>
      </div>
    </div>
    <div v-else class="card card-pad mb-12 ok-banner">
      <el-icon class="ok-icon"><CircleCheckFilled /></el-icon>
      <span>所选区间内拣货效率、差错率、缺货率均达标，作业质量稳定。</span>
    </div>

    <!-- ======================= KPI ======================= -->
    <div class="kpi-grid mb-12">
      <StatCard
        label="拣货效率"
        :value="ov.pickEfficiency || 0"
        unit="件/小时"
        tone="primary"
        icon="TrendCharts"
        tip="实拣件数 ÷ 拣货总工时，衡量人效"
        :sub="`目标 ≥ 120 件/小时`"
      />
      <StatCard
        label="差错率"
        :value="formatPercent(ov.errorRate)"
        :thousand="false"
        tone="danger"
        icon="WarnTriangleFilled"
        tip="复核差异单数 ÷ 复核总单数"
        sub="目标 ≤ 0.50%"
      />
      <StatCard
        label="缺货率"
        :value="formatPercent(ov.shortageRate)"
        :thousand="false"
        tone="warning"
        icon="Warning"
        tip="缺货件数 ÷ 应拣件数"
        sub="目标 ≤ 2.00%"
      />
      <StatCard
        label="复核通过率"
        :value="formatPercent(ov.reviewPassRate)"
        :thousand="false"
        tone="success"
        icon="DocumentChecked"
        :sub="`复核 ${errorRate.reviewTotal || 0} 单`"
      />
      <StatCard
        label="平均拣货时长"
        :value="formatDuration(ov.avgPickDurationSec)"
        :thousand="false"
        icon="Timer"
        :sub="`完成波次 ${ov.finishedWaves || 0} 个`"
      />
      <StatCard
        label="已出库订单"
        :value="ov.outboundOrders || 0"
        unit="单"
        tone="success"
        icon="Box"
        :sub="`交接包裹 ${ov.handedPackages || 0} 件`"
      />
    </div>

    <!-- ======================= 趋势 ======================= -->
    <div class="grid-2 mb-12">
      <div class="card card-pad">
        <div class="panel-head">
          <span class="panel-title">作业量趋势</span>
          <span class="panel-sub">波次数 / 订单数 / 拣货件数</span>
        </div>
        <ChartBox :option="volumeOption" height="268px" />
      </div>
      <div class="card card-pad">
        <div class="panel-head">
          <span class="panel-title">效率与质量趋势</span>
          <span class="panel-sub">拣货效率（左轴）与差错率（右轴）</span>
        </div>
        <ChartBox :option="qualityOption" height="268px" />
      </div>
    </div>

    <!-- ======================= 差错率分析 ======================= -->
    <div class="grid-3 mb-12">
      <div class="card card-pad">
        <div class="panel-head">
          <span class="panel-title">差错类型分布</span>
          <span class="panel-sub">{{ errorRate.diffCount || 0 }} 张差异单</span>
        </div>
        <ChartBox v-if="(errorRate.typeDistribution || []).length" :option="diffTypeOption" height="236px" />
        <el-empty v-else description="区间内无差异记录" :image-size="60" />
      </div>
      <div class="card card-pad">
        <div class="panel-head">
          <span class="panel-title">责任环节分布</span>
          <span class="panel-sub">差异件数合计 {{ errorRate.diffQtyTotal || 0 }}</span>
        </div>
        <ChartBox v-if="(errorRate.stageDistribution || []).length" :option="stageOption" height="236px" />
        <el-empty v-else description="区间内无差异记录" :image-size="60" />
      </div>
      <div class="card card-pad">
        <div class="panel-head">
          <span class="panel-title">缺货原因分布</span>
          <span class="panel-sub">按记录数排序</span>
        </div>
        <ChartBox v-if="shortageDist.length" :option="shortageOption" height="236px" />
        <el-empty v-else description="区间内无缺货记录" :image-size="60" />
      </div>
    </div>

    <!-- ======================= 人员效率 ======================= -->
    <div class="card">
      <div class="panel-head card-pad-head">
        <span class="panel-title">人员效率榜</span>
        <span class="panel-sub">拣货员按件/小时排名，复核员按差错率与复核量综合评分</span>
        <el-radio-group v-model="empTab" size="small" class="head-btn">
          <el-radio-button value="PICKER">拣货员</el-radio-button>
          <el-radio-button value="REVIEWER">复核员</el-radio-button>
        </el-radio-group>
      </div>

      <el-table v-if="empTab === 'PICKER'" :data="pickerRank" stripe>
        <el-table-column label="排名" width="70" align="center">
          <template #default="{ $index }">
            <span class="rank" :class="`rank-${$index < 3 ? $index + 1 : 0}`">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="拣货员" width="110">
          <template #default="{ row }">{{ row.employeeName }}</template>
        </el-table-column>
        <el-table-column label="班组" width="120">
          <template #default="{ row }"><span class="text-secondary">{{ row.team }}</span></template>
        </el-table-column>
        <el-table-column label="任务数" width="90" align="right">
          <template #default="{ row }"><span class="num">{{ row.taskCount }}</span></template>
        </el-table-column>
        <el-table-column label="实拣件数" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ row.pickedQty }}</span></template>
        </el-table-column>
        <el-table-column label="件/小时" width="110" align="right">
          <template #default="{ row }">
            <span class="num" :class="{ success: row.qtyPerHour >= 120, danger: row.qtyPerHour < 120 && row.taskCount > 0 }">
              {{ row.qtyPerHour }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="库位数" width="90" align="right">
          <template #default="{ row }"><span class="num">{{ row.locationCount }}</span></template>
        </el-table-column>
        <el-table-column label="平均耗时" width="110" align="right">
          <template #default="{ row }"><span class="num">{{ formatDuration(row.avgDurationSec) }}</span></template>
        </el-table-column>
        <el-table-column label="综合评分" min-width="180">
          <template #default="{ row }">
            <div class="score-cell">
              <el-progress :percentage="Math.min(100, row.score)" :stroke-width="8" :show-text="false" class="score-bar" />
              <span class="num score-num">{{ row.score }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else :data="reviewerRank" stripe>
        <el-table-column label="排名" width="70" align="center">
          <template #default="{ $index }">
            <span class="rank" :class="`rank-${$index < 3 ? $index + 1 : 0}`">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="复核员" width="110">
          <template #default="{ row }">{{ row.employeeName }}</template>
        </el-table-column>
        <el-table-column label="班组" width="120">
          <template #default="{ row }"><span class="text-secondary">{{ row.team }}</span></template>
        </el-table-column>
        <el-table-column label="复核单数" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ row.reviewCount }}</span></template>
        </el-table-column>
        <el-table-column label="差异单数" width="100" align="right">
          <template #default="{ row }">
            <span class="num" :class="{ danger: row.errorCount > 0 }">{{ row.errorCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="差错率" width="110" align="right">
          <template #default="{ row }">
            <span class="num" :class="{ danger: row.errorRate > 0.005 }">{{ formatPercent(row.errorRate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单/小时" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ row.orderPerHour }}</span></template>
        </el-table-column>
        <el-table-column label="综合评分" min-width="180">
          <template #default="{ row }">
            <div class="score-cell">
              <el-progress :percentage="Math.min(100, row.score)" :stroke-width="8" :show-text="false" class="score-bar" />
              <span class="num score-num">{{ row.score }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh, WarningFilled, CircleCheckFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import ChartBox from '@/components/ChartBox.vue'
import { analyticsApi } from '@/api'
import { formatDuration, formatPercent, recentRange } from '@/utils'

const loading = ref(false)
const quick = ref('7')
const dateRange = ref(recentRange(7))

const ov = ref({})
const trend = ref([])
const employees = ref([])
const errorRate = ref({})
const shortageDist = ref([])
const empTab = ref('PICKER')

const pickerRank = computed(() =>
  employees.value.filter((e) => e.roleDesc === '拣货员').sort((a, b) => b.score - a.score)
)
const reviewerRank = computed(() =>
  employees.value.filter((e) => e.roleDesc === '复核员').sort((a, b) => b.score - a.score)
)

/* ------------------------------ 数据 ------------------------------ */

function rangeParams() {
  return { startDate: dateRange.value[0], endDate: dateRange.value[1] }
}

async function loadAll() {
  loading.value = true
  try {
    const params = rangeParams()
    const [o, t, e, er, sd] = await Promise.all([
      analyticsApi.overview(params),
      analyticsApi.trend(params),
      analyticsApi.employeeEfficiency(params),
      analyticsApi.errorRate(params),
      analyticsApi.shortageDistribution(params)
    ])
    ov.value = o || {}
    trend.value = t || []
    employees.value = e || []
    errorRate.value = er || {}
    shortageDist.value = sd || []
  } finally {
    loading.value = false
  }
}

function onQuickChange(v) {
  dateRange.value = recentRange(Number(v))
  loadAll()
}

function onDateChange() {
  loadAll()
}

/* ------------------------------ 图表 ------------------------------ */

const AXIS_STYLE = {
  axisLine: { lineStyle: { color: '#e2e8f0' } },
  axisTick: { show: false },
  axisLabel: { color: '#94a3b8', fontSize: 11 }
}
const SPLIT_LINE = { lineStyle: { color: '#f1f5f9', type: 'dashed' } }
const LEGEND = {
  right: 0,
  top: 0,
  icon: 'roundRect',
  itemWidth: 9,
  itemHeight: 9,
  textStyle: { color: '#64748b', fontSize: 11 }
}

const volumeOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  legend: LEGEND,
  grid: { left: 8, right: 8, top: 38, bottom: 4, containLabel: true },
  xAxis: { type: 'category', data: trend.value.map((t) => t.label.slice(5)), ...AXIS_STYLE },
  yAxis: [{ type: 'value', ...AXIS_STYLE, splitLine: SPLIT_LINE }],
  series: [
    {
      name: '订单数',
      type: 'bar',
      stack: 'q',
      barMaxWidth: 20,
      itemStyle: { color: '#bfdbfe' },
      data: trend.value.map((t) => t.orderCount)
    },
    {
      name: '拣货件数',
      type: 'bar',
      stack: 'q',
      barMaxWidth: 20,
      itemStyle: { color: '#2563eb', borderRadius: [4, 4, 0, 0] },
      data: trend.value.map((t) => t.pickedQty)
    },
    {
      name: '波次数',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      lineStyle: { width: 2.5, color: '#f59e0b' },
      itemStyle: { color: '#f59e0b' },
      data: trend.value.map((t) => t.waveCount)
    }
  ]
}))

const qualityOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: LEGEND,
  grid: { left: 8, right: 8, top: 38, bottom: 4, containLabel: true },
  xAxis: { type: 'category', data: trend.value.map((t) => t.label.slice(5)), ...AXIS_STYLE },
  yAxis: [
    { type: 'value', name: '件/小时', nameTextStyle: { color: '#94a3b8', fontSize: 11 }, ...AXIS_STYLE, splitLine: SPLIT_LINE },
    {
      type: 'value',
      name: '差错率%',
      nameTextStyle: { color: '#94a3b8', fontSize: 11 },
      ...AXIS_STYLE,
      splitLine: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 11, formatter: (v) => (v * 100).toFixed(1) }
    }
  ],
  series: [
    {
      name: '拣货效率',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(22,163,74,0.22)' },
            { offset: 1, color: 'rgba(22,163,74,0.01)' }
          ]
        }
      },
      lineStyle: { width: 2.5, color: '#16a34a' },
      itemStyle: { color: '#16a34a' },
      data: trend.value.map((t) => t.pickEfficiency)
    },
    {
      name: '差错率',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      yAxisIndex: 1,
      lineStyle: { width: 2.5, color: '#dc2626', type: 'dashed' },
      itemStyle: { color: '#dc2626' },
      data: trend.value.map((t) => t.errorRate)
    }
  ]
}))

const DIFF_COLORS = ['#dc2626', '#f97316', '#f59e0b', '#8b5cf6', '#0ea5e9', '#64748b']

const diffTypeOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} 单（{d}%）' },
  legend: {
    bottom: 0,
    icon: 'roundRect',
    itemWidth: 9,
    itemHeight: 9,
    textStyle: { color: '#64748b', fontSize: 11 }
  },
  series: [
    {
      type: 'pie',
      radius: ['46%', '68%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data: (errorRate.value.typeDistribution || []).map((d, i) => ({
        name: d.name,
        value: d.count,
        itemStyle: { color: DIFF_COLORS[i % DIFF_COLORS.length] }
      }))
    }
  ]
}))

const stageOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: {c} 单' },
  grid: { left: 8, right: 24, top: 10, bottom: 4, containLabel: true },
  xAxis: { type: 'value', show: false },
  yAxis: {
    type: 'category',
    inverse: true,
    data: (errorRate.value.stageDistribution || []).map((d) => d.name),
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisTick: { show: false },
    axisLabel: { color: '#64748b', fontSize: 11 }
  },
  series: [
    {
      type: 'bar',
      barMaxWidth: 18,
      itemStyle: { color: '#8b5cf6', borderRadius: [0, 9, 9, 0] },
      label: { show: true, position: 'right', color: '#94a3b8', fontSize: 11 },
      data: (errorRate.value.stageDistribution || []).map((d) => d.count)
    }
  ]
}))

const shortageOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: {c} 条' },
  grid: { left: 8, right: 28, top: 10, bottom: 4, containLabel: true },
  xAxis: { type: 'value', show: false },
  yAxis: {
    type: 'category',
    inverse: true,
    data: shortageDist.value.slice(0, 6).map((d) => d.name),
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisTick: { show: false },
    axisLabel: { color: '#64748b', fontSize: 11 }
  },
  series: [
    {
      type: 'bar',
      barMaxWidth: 18,
      itemStyle: { color: '#f59e0b', borderRadius: [0, 9, 9, 0] },
      label: { show: true, position: 'right', color: '#94a3b8', fontSize: 11 },
      data: shortageDist.value.slice(0, 6).map((d) => d.count)
    }
  ]
}))

onMounted(loadAll)
</script>

<style scoped lang="scss">
/* 预警 */
.alert-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}

.alert-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 13px 16px;
  border-radius: $radius-card;
  border: 1px solid;
  box-shadow: $shadow-card;

  &.lv-warn {
    background: linear-gradient(135deg, #fffbeb, #fff7ed);
    border-color: #fde68a;

    .ai-icon {
      color: $color-warning;
    }
  }

  &.lv-danger {
    background: linear-gradient(135deg, #fef2f2, #fff7f7);
    border-color: #fecaca;

    .ai-icon {
      color: $color-danger;
    }
  }
}

.ai-icon {
  font-size: 18px;
  margin-top: 1px;
}

.ai-name {
  font-size: 12.5px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 3px;
}

.ai-msg {
  font-size: 11.5px;
  color: $text-secondary;
  line-height: 1.5;
}

.ok-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  background: linear-gradient(135deg, #f0fdf4, #f8fafc);
  border-color: #bbf7d0;
  font-size: 12.5px;
  color: $text-secondary;
}

.ok-icon {
  font-size: 18px;
  color: $color-success;
}

/* KPI 网格 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.grid-2 {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: 12px;
}

.grid-3 {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 12px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}

.card-pad-head {
  padding: 14px 18px 12px;
  margin-bottom: 0;
  border-bottom: 1px solid $border-base;
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

/* 排名 */
.rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 6px;
  font-size: 11.5px;
  font-weight: 600;
  background: #f1f5f9;
  color: $text-secondary;
}

.rank-1 {
  background: #fef3c7;
  color: #b45309;
}

.rank-2 {
  background: #e2e8f0;
  color: #475569;
}

.rank-3 {
  background: #ffedd5;
  color: #c2410c;
}

.score-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.score-bar {
  flex: 1;
  min-width: 80px;
}

.score-num {
  width: 34px;
  text-align: right;
  font-weight: 600;
  color: $text-primary;
}
</style>
