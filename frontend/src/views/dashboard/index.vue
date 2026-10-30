<template>
  <div class="page-container" v-loading="loading">
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
          tone="success"
          icon="Van"
          :sub="`平均波次耗时 ${formatDuration(ov.avgPickDurationSec)}`"
          to="/pick"
        />
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <StatCard
          label="差错率"
          :value="formatPercent(ov.errorRate)"
          tone="danger"
          icon="CircleClose"
          :sub="`复核通过率 ${formatPercent(ov.reviewPassRate)}`"
          to="/analytics"
        />
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <StatCard
          label="缺货率"
          :value="formatPercent(ov.shortageRate)"
          tone="warning"
          icon="Warning"
          :sub="`待处理缺货 ${ov.pendingShortageCount} 条`"
          to="/shortage"
        />
      </el-col>
    </el-row>

    <!-- ======================= 全链路水位 + 待办 ======================= -->
    <el-row :gutter="14" class="mb-16">
      <el-col :xs="24" :md="16">
        <div class="card card-pad full-h">
          <div class="panel-head">
            <span class="panel-title">全链路作业水位</span>
            <span class="panel-sub">按环节展示「待处理 / 进行中」数量，快速定位积压点</span>
          </div>
          <div class="pipeline">
            <div v-for="(p, i) in pipeline" :key="p.stage" class="pipe-node">
              <div class="pipe-card" :class="{ hot: p.pending > 0 }">
                <div class="pipe-stage">{{ p.stage }}</div>
                <div class="pipe-nums">
                  <div class="pipe-num">
                    <span class="num pending">{{ p.pending }}</span>
                    <span class="pipe-lbl">待处理</span>
                  </div>
                  <div class="pipe-divider"></div>
                  <div class="pipe-num">
                    <span class="num working">{{ p.working }}</span>
                    <span class="pipe-lbl">进行中</span>
                  </div>
                </div>
              </div>
              <div v-if="i < pipeline.length - 1" class="pipe-arrow">
                <el-icon><Right /></el-icon>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :md="8">
        <div class="card card-pad full-h">
          <div class="panel-head">
            <span class="panel-title">我的待办</span>
            <span class="panel-sub">点击直接跳转处理</span>
          </div>
          <div class="todo-list">
            <div
              v-for="t in todos"
              :key="t.name"
              class="todo-row"
              :class="{ empty: !t.count }"
              @click="t.count > 0 && router.push(t.path)"
            >
              <div class="todo-left">
                <span class="todo-dot" :class="{ on: t.count > 0 }"></span>
                <span class="todo-name">{{ t.name }}</span>
              </div>
              <div class="todo-right">
                <span class="todo-count num">{{ t.count }}</span>
                <el-icon class="todo-arrow"><ArrowRight /></el-icon>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

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
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import ChartBox from '@/components/ChartBox.vue'
import { dashboardApi } from '@/api'
import { formatDuration, formatPercent, formatNumber } from '@/utils'

const router = useRouter()

const loading = ref(false)
const days = ref(7)
const ov = ref({})
const pipeline = ref([])
const todos = ref([])
const trend = ref([])

const alerts = computed(() => ov.value.alerts || [])

/* ------------------------------ 数据加载 ------------------------------ */

async function loadAll() {
  loading.value = true
  try {
    const [summary, trendData] = await Promise.all([
      dashboardApi.summary(),
      dashboardApi.trend()
    ])
    ov.value = summary.overview || {}
    pipeline.value = summary.pipeline || []
    todos.value = summary.todos || []
    trend.value = trendData || []
  } finally {
    loading.value = false
  }
}

/* ------------------------------ 图表配置 ------------------------------ */

const AXIS_STYLE = {
  axisLine: { lineStyle: { color: '#e2e8f0' } },
  axisTick: { show: false },
  axisLabel: { color: '#94a3b8', fontSize: 11 }
}

const SPLIT_LINE = { lineStyle: { color: '#f1f5f9', type: 'dashed' } }

const volumeOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  legend: { right: 0, top: 0, icon: 'roundRect', itemWidth: 9, itemHeight: 9, textStyle: { color: '#64748b', fontSize: 11 } },
  grid: { left: 8, right: 8, top: 38, bottom: 4, containLabel: true },
  xAxis: { type: 'category', data: trend.value.map((t) => t.label.slice(5)), ...AXIS_STYLE },
  yAxis: [
    { type: 'value', name: '数量', nameTextStyle: { color: '#94a3b8', fontSize: 11 }, ...AXIS_STYLE, splitLine: SPLIT_LINE }
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
  legend: { right: 0, top: 0, icon: 'roundRect', itemWidth: 9, itemHeight: 9, textStyle: { color: '#64748b', fontSize: 11 } },
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
      axisLabel: { color: '#94a3b8', fontSize: 11, formatter: (v) => `${(v * 100).toFixed(1)}` }
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

const durationOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    formatter: (params) => {
      const p = params[0]
      return `${p.name}<br/>平均拣货耗时：${(p.value / 60).toFixed(1)} 分钟`
    }
  },
  grid: { left: 8, right: 16, top: 18, bottom: 4, containLabel: true },
  xAxis: { type: 'category', data: trend.value.map((t) => t.label.slice(5)), ...AXIS_STYLE },
  yAxis: {
    type: 'value',
    name: '分钟',
    nameTextStyle: { color: '#94a3b8', fontSize: 11 },
    ...AXIS_STYLE,
    splitLine: SPLIT_LINE,
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
  font-size: 11.5px;
  color: $text-placeholder;
}

/* ------------------------------ 全链路水位 ------------------------------ */

.pipeline {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.pipe-node {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 128px;
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
    color: #f59e0b;
  }

  &.working {
    color: #2563eb;
  }
}

.pipe-lbl {
  font-size: 10.5px;
  color: $text-placeholder;
}

.pipe-divider {
  width: 1px;
  height: 22px;
  background: $border-base;
}

.pipe-arrow {
  flex-shrink: 0;
  width: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #cbd5e1;
}

/* ------------------------------ 待办 ------------------------------ */

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.todo-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 10px;
  border-radius: 7px;
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: #f1f5f9;
  }

  &.empty {
    cursor: default;
    opacity: 0.55;

    &:hover {
      background: transparent;
    }
  }
}

.todo-left {
  display: flex;
  align-items: center;
  gap: 9px;
}

.todo-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #cbd5e1;

  &.on {
    background: #ef4444;
    box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.15);
  }
}

.todo-name {
  font-size: 13px;
  color: $text-regular;
}

.todo-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.todo-count {
  font-size: 15px;
  font-weight: 700;
  color: $text-primary;
}

.todo-arrow {
  font-size: 12px;
  color: #cbd5e1;
}
</style>
