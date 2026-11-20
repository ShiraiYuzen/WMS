<template>
  <div class="page-container dashboard-page" v-loading="loading">
    <PageHeader
      title="作业看板"
      description="覆盖「订单汇聚 → 波次生成 → 拣货执行 → 复核打包 → 出库交接 → 效率复盘」全链路实时概览"
    >
      <template #actions>
        <el-radio-group v-model="days" size="small" @change="loadAll">
          <el-radio-button :value="1">今日</el-radio-button>
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
        </el-radio-group>
        <el-button size="small" :icon="Refresh" @click="loadAll">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon>
      <template #title>看板数据读取失败</template>
      {{ loadError }} <el-button link type="primary" @click="loadAll">重新加载</el-button>
    </el-alert>
    <template v-else-if="ready">
      <!-- ======================= 指标预警 ======================= -->
      <el-alert
        v-if="alerts.length"
        type="warning"
        :closable="false"
        show-icon
        class="alert-bar mb-16"
      >
        <template #title>
          <span style="font-weight: 600">指标预警（{{ alerts.length }} 项超出目标阈值）</span>
        </template>
        <div class="alert-list">
          <span v-for="a in alerts" :key="a.metric" class="alert-item" :class="a.level.toLowerCase()">
            <el-icon><WarningFilled /></el-icon>
            {{ a.message }}
          </span>
        </div>
      </el-alert>

      <!-- ======================= 核心 KPI ======================= -->
      <el-row :gutter="14" class="mb-16">
        <el-col :xs="12" :sm="12" :md="6" :lg="6">
          <StatCard
            label="在制波次"
            :value="ov.workingWaves"
            unit="个"
            tone="primary"
            icon="Grid"
            :sub="`累计波次 ${ov.totalWaves} 个`"
            to="/wave"
          />
        </el-col>
        <el-col :xs="12" :sm="12" :md="6" :lg="6">
          <StatCard
            label="拣货效率"
            :value="ov.pickEfficiency"
            unit="件/小时"
            :tone="metricTone('pickEfficiency', 'success')"
            icon="Van"
            :sub="`平均波次耗时 ${formatDuration(ov.avgPickDurationSec)}`"
            to="/pick"
          />
        </el-col>
        <el-col :xs="12" :sm="12" :md="6" :lg="6">
          <StatCard
            label="差错率"
            :value="formatPercent(ov.errorRate)"
            :tone="metricTone('errorRate')"
            icon="CircleClose"
            :sub="`复核通过率 ${formatPercent(ov.reviewPassRate)}`"
            to="/analytics"
          />
        </el-col>
        <el-col :xs="12" :sm="12" :md="6" :lg="6">
          <StatCard
            label="缺货率"
            :value="formatPercent(ov.shortageRate)"
            :tone="metricTone('shortageRate')"
            icon="Warning"
            :sub="`待处理缺货 ${ov.pendingShortageCount} 条`"
            to="/shortage"
          />
        </el-col>
      </el-row>

      <!-- ======================= 全链路水位 + 待办 ======================= -->
      <div class="workflow-row mb-16">
        <div class="card card-pad full-h pipeline-panel">
          <div class="panel-head">
            <span class="panel-title">全链路作业水位</span>
            <span class="panel-sub">各环节当前数量</span>
          </div>
          <div class="pipeline">
            <div v-for="(p, i) in pipeline" :key="p.stage" class="pipe-node">
              <div class="pipe-card" :class="{ hot: p.pending > 0 }">
                <router-link v-if="stageTarget(p.stage)" class="pipe-stage stage-link" :to="stageTarget(p.stage)">{{ p.stage }} ›</router-link>
                <span v-else class="pipe-stage">{{ p.stage }}</span>
                <div class="pipe-nums">
                  <div class="pipe-num">
                    <span class="num pending">{{ p.pending }}</span>
                    <span class="pipe-lbl">{{ stages[p.stage]?.pending || '待处理' }}</span>
                  </div>
                  <div class="pipe-divider"></div>
                  <div class="pipe-num">
                    <span class="num working">{{ p.working }}</span>
                    <span class="pipe-lbl">{{ stages[p.stage]?.working || '进行中' }}</span>
                  </div>
                </div>
              </div>
              <div v-if="i < pipeline.length - 1" class="pipe-arrow">
                <el-icon><Right /></el-icon>
              </div>
            </div>
          </div>
        </div>

        <div class="card card-pad full-h">
          <div class="panel-head">
            <span class="panel-title">我的待办</span>
            <span class="panel-sub">点击直接跳转处理</span>
          </div>
          <div class="todo-list">
            <button
              v-for="t in activeTodos"
              :key="t.path"
              type="button"
              class="todo-row"
              :class="{ shortage: t.path === '/shortage' }"
              @click="router.push(t.path)"
            >
              <span class="todo-name">{{ t.name }}</span>
              <span class="todo-count num">{{ t.count }}</span>
              <el-icon class="todo-arrow"><ArrowRight /></el-icon>
            </button>
          </div>
          <details v-if="emptyTodos.length" class="todo-empty">
            <summary>{{ emptyTodos.length }} 项待办为零</summary>
            <div>{{ emptyTodos.map(t => t.name).join('、') }}</div>
          </details>
        </div>

      </div>

      <!-- ======================= 趋势图 ======================= -->
      <el-row :gutter="14">
        <el-col :xs="24" :md="14">
          <div class="card card-pad">
            <div class="panel-head">
              <span class="panel-title">作业量趋势</span>
              <span class="panel-sub">波次数 / 订单数 / 拣货件数</span>
            </div>
            <ChartBox :option="volumeOption" height="288px" />
          </div>
        </el-col>
        <el-col :xs="24" :md="10">
          <div class="card card-pad">
            <div class="panel-head">
              <span class="panel-title">效率与质量趋势</span>
              <span class="panel-sub">拣货效率（件/小时）/ 差错率</span>
            </div>
            <ChartBox :option="qualityOption" height="288px" />
          </div>
        </el-col>
      </el-row>

      <!-- ======================= 环节耗时 ======================= -->
      <el-row :gutter="14" class="mt-16">
        <el-col :span="24">
          <div class="card card-pad">
            <div class="panel-head">
              <span class="panel-title">平均波次耗时</span>
              <span class="panel-sub">从波次生成到交接完成的平均时长（分钟）</span>
            </div>
            <ChartBox :option="durationOption" height="240px" />
          </div>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import ChartBox from '@/components/ChartBox.vue'
import { useAppStore } from '@/stores/app'
import { dashboardApi } from '@/api'
import { axisStyle, splitLine, legend, trendGrid, trendXAxis } from '@/utils/chart'
import { formatDuration, formatPercent, formatNumber } from '@/utils'

const router = useRouter()
const app = useAppStore()
const stages = {
  '订单汇聚': { path: '/order', status: 'CREATED', pending: '待汇聚', working: '已入池' },
  '波次生成': { path: '/wave', status: 'CREATED', pending: '待分配', working: '已分配' },
  '拣货执行': { path: '/pick', status: 'WAITING', pending: '待拣货', working: '拣货中' },
  '复核打包': { path: '/review', status: 'WAITING', pending: '待复核', working: '有差异' },
  '出库交接': { path: '/handover', pending: '待交接', working: '已交接' }
}

function stageTarget(stage) {
  const entry = stages[stage]
  if (!entry || !app.canAccess(router.resolve(entry.path).meta.roles)) return null
  return { path: entry.path, query: entry.status ? { status: entry.status } : {} }
}

const loading = ref(false)
const ready = ref(false)
const loadError = ref('')
let requestId = 0
const days = ref(7)
const ov = ref({})
const pipeline = ref([])
const todos = ref([])
const trend = ref([])

const activeTodos = computed(() => todos.value.filter(t => t.count > 0))
const emptyTodos = computed(() => todos.value.filter(t => !t.count))
const alerts = computed(() => ov.value.alerts || [])

function metricTone(metric, normal = 'default') {
  const alert = alerts.value.find(a => a.metric === metric)
  if (!alert) return normal
  return alert.level === 'DANGER' ? 'danger' : 'warning'
}

/* ------------------------------ 数据加载 ------------------------------ */

async function loadAll() {
  const id = ++requestId
  loadError.value = ''
  loading.value = true
  try {
    const [summary, trendData] = await Promise.all([
      dashboardApi.summary(days.value),
      dashboardApi.trend(days.value)
    ])
    if (id !== requestId) return
    ready.value = true
    ov.value = summary.overview || {}
    pipeline.value = summary.pipeline || []
    todos.value = summary.todos || []
    trend.value = trendData || []
  } catch (error) {
    if (id === requestId) loadError.value = error.message || '请检查后端连接'
  } finally {
    if (id === requestId) loading.value = false
  }
}

/* ------------------------------ 图表配置 ------------------------------ */

const volumeOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  legend,
  grid: trendGrid(),
  xAxis: trendXAxis(trend.value.map((t) => t.label)),
  yAxis: [
    { type: 'value', name: '数量', nameTextStyle: { color: '#94a3b8', fontSize: 11 }, ...axisStyle, splitLine }
  ],
  series: [
    {
      name: '订单数',
      type: 'bar',
      stack: 'qty',
      barMaxWidth: 22,
      itemStyle: { color: '#93c5fd', borderRadius: [0, 0, 0, 0] },
      data: trend.value.map((t) => t.orderCount)
    },
    {
      name: '拣货件数',
      type: 'bar',
      stack: 'qty',
      barMaxWidth: 22,
      itemStyle: { color: '#2563eb', borderRadius: [4, 4, 0, 0] },
      data: trend.value.map((t) => t.pickedQty)
    },
    {
      name: '波次数',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      yAxisIndex: 0,
      lineStyle: { width: 2.5, color: '#f59e0b' },
      itemStyle: { color: '#f59e0b' },
      data: trend.value.map((t) => t.waveCount)
    }
  ]
}))

const qualityOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend,
  grid: trendGrid(),
  xAxis: trendXAxis(trend.value.map((t) => t.label)),
  yAxis: [
    { type: 'value', name: '件/小时', nameTextStyle: { color: '#94a3b8', fontSize: 11 }, ...axisStyle, splitLine },
    {
      type: 'value',
      // 右轴不写 name：ECharts 会把轴名画在轴顶端，正好压在右上角的图例上。
      // 单位改由刻度后缀表达，图例保持干净。
      ...axisStyle,
      splitLine: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 11, formatter: (v) => `${(v * 100).toFixed(1)}%` }
    }
  ],
  series: [
    {
      name: '拣货效率',
      type: 'line',
      smooth: true,
      // 没记录耗时的日子是 null（无数据），用 connectNulls 把已知点连起来，不画成 0 的假低谷
      connectNulls: true,
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

const durationOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    formatter: (params) => {
      const p = params[0]
      return `${p.name}<br/>平均拣货耗时：${(p.value / 60).toFixed(1)} 分钟`
    }
  },
  // top 留 30px：轴名「分钟」要画在绘图区上方，18px 会把它顶出容器
  grid: { ...trendGrid(30), right: 16 },
  xAxis: trendXAxis(trend.value.map((t) => t.label)),
  yAxis: {
    type: 'value',
    name: '分钟',
    nameTextStyle: { color: '#94a3b8', fontSize: 11 },
    ...axisStyle,
    splitLine,
    axisLabel: { color: '#94a3b8', fontSize: 11, formatter: (v) => (v / 60).toFixed(0) }
  },
  series: [
    {
      type: 'bar',
      barMaxWidth: 30,
      itemStyle: {
        borderRadius: [5, 5, 0, 0],
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: '#60a5fa' },
            { offset: 1, color: '#2563eb' }
          ]
        }
      },
      label: {
        show: true,
        position: 'top',
        fontSize: 11,
        color: '#64748b',
        formatter: (p) => (p.value ? `${(p.value / 60).toFixed(1)}分` : '')
      },
      data: trend.value.map((t) => t.avgDurationSec)
    }
  ]
}))

onMounted(loadAll)
</script>

<style scoped lang="scss">
.alert-bar {
  border-radius: $radius-base;
}

.alert-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  margin-top: 4px;
}

.alert-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;

  &.danger {
    color: $color-danger;
    font-weight: 600;
  }
}

.dashboard-page { container-type: inline-size; }
.workflow-row {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 12px;
}
.pipe-stage { display: block; }
.stage-link { color: var(--wms-primary-dark); }
.stage-link:hover { text-decoration: underline; }
@container (max-width: 1320px) {
  .workflow-row { grid-template-columns: minmax(0, 1fr); }
  .workflow-row .todo-list { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@container (max-width: 720px) {
  .workflow-row .todo-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .panel-head { flex-wrap: wrap; }
}
@container (max-width: 400px) {
  .workflow-row .todo-list { grid-template-columns: 1fr; }
}


.full-h {
  height: 100%;
}

.panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 14px;
}

.panel-title {
  font-size: 14.5px;
  font-weight: 600;
  color: $text-primary;
}

.panel-sub {
  font-size: 12px;
  color: $text-secondary;
}

/* ------------------------------ 全链路水位 ------------------------------ */

.pipeline-panel {
  container: stages / inline-size;
}

.pipeline {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(142px, 100%), 1fr));
  gap: 8px 22px;
}

.pipe-node {
  position: relative;
  display: flex;
  min-width: 0;
}

.pipe-card {
  flex: 1;
  padding: 12px 14px;
  border-radius: 9px;
  background: #f8fafc;
  border: 1px solid $border-base;
  transition: all 0.18s;

  &.hot {
    background: linear-gradient(135deg, #eff6ff, #f8fafc);
    border-color: #bfdbfe;
  }
}

.pipe-stage {
  font-size: 12.5px;
  font-weight: 600;
  color: $text-regular;
  margin-bottom: 9px;
}

.pipe-nums {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pipe-num {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pipe-num .num {
  font-size: 19px;
  font-weight: 700;
  line-height: 1;

  &.pending {
    color: $text-primary;
  }

  &.working {
    color: #2563eb;
  }
}

.pipe-lbl {
  white-space: nowrap;
  font-size: 12px;
  color: $text-secondary;
}

.pipe-divider {
  width: 1px;
  height: 22px;
  background: $border-base;
}

.pipe-arrow {
  position: absolute;
  inset: 0 -22px 0 auto;
  width: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #cbd5e1;
}

/* Five 142px cards and four 22px connectors fit on one line. */
@container stages (width < 798px) {
  .pipeline { column-gap: 8px; }
  .pipe-arrow { display: none; }
}

/* ------------------------------ 待办 ------------------------------ */

.todo-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(150px, 100%), 1fr));
  gap: 6px;
}

.todo-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: $bg-page;
  border: 1px solid transparent;
  border-radius: 6px;
  font: inherit;
  color: $text-regular;
  text-align: left;
  cursor: pointer;

  &:hover {
    border-color: #bfdbfe;
    background: var(--wms-primary-soft);
  }

  &.shortage .todo-count {
    color: $color-danger;
  }
}

.todo-name {
  flex: 1;
  font-size: 13px;
}

.todo-count {
  font-size: 15px;
  font-weight: 700;
}

.todo-arrow {
  color: $text-secondary;
  font-size: 12px;
}

.todo-empty {
  color: $text-secondary;
  font-size: 12px;
  line-height: 1.6;

  summary {
    width: fit-content;
    cursor: pointer;
  }
}

.todo-list:not(:empty) + .todo-empty {
  margin-top: 8px;
}
</style>
