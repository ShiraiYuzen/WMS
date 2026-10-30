<template>
  <div class="page-container">
    <PageHeader
      title="承运商"
      description="承运商是波次聚类与出库交接的关键维度：按承运商聚类可让交接环节只面对一家物流商，每日揽收时间决定波次的出库截止时点。"
      :count="carriers.length"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="carrier-row">
      <div v-for="c in carriers" :key="c.carrierCode" class="carrier-card">
        <div class="cc-head">
          <div class="cc-logo" :style="{ background: logoBg(c.carrierCode) }">
            {{ c.carrierName.slice(0, 2) }}
          </div>
          <div class="cc-title">
            <div class="cc-name">{{ c.carrierName }}</div>
            <div class="cc-code mono">{{ c.carrierCode }}</div>
          </div>
          <el-tag :type="c.status === 1 ? 'success' : 'info'" size="small" effect="plain">
            {{ c.status === 1 ? '合作中' : '已停用' }}
          </el-tag>
        </div>

        <div class="cc-body">
          <div class="cc-row">
            <span class="cc-label">联系人</span>
            <span>{{ c.contactName }}</span>
          </div>
          <div class="cc-row">
            <span class="cc-label">联系电话</span>
            <span class="mono">{{ c.contactPhone }}</span>
          </div>
          <div class="cc-row">
            <span class="cc-label">每日揽收</span>
            <span class="pickup">{{ c.dailyPickupTime }}</span>
          </div>
        </div>

        <div class="cc-foot">
          <div class="cc-stat">
            <span class="num">{{ stats[c.carrierCode]?.orderCount || 0 }}</span>
            <span class="cs-label">关联订单</span>
          </div>
          <div class="cc-stat">
            <span class="num">{{ stats[c.carrierCode]?.waveCount || 0 }}</span>
            <span class="cs-label">涉及波次</span>
          </div>
          <div class="cc-stat">
            <span class="num">{{ stats[c.carrierCode]?.handoverCount || 0 }}</span>
            <span class="cs-label">交接单</span>
          </div>
        </div>
      </div>
    </div>

    <div class="card card-pad mt-16">
      <div class="panel-head">
        <span class="panel-title">揽收时间轴</span>
        <span class="panel-sub">按每日揽收时间排序，越早的承运商越需优先完成拣货复核</span>
      </div>
      <div class="timeline">
        <div v-for="c in sortedCarriers" :key="c.carrierCode" class="tl-item">
          <div class="tl-time mono">{{ c.dailyPickupTime }}</div>
          <div class="tl-bar" :style="{ background: logoBg(c.carrierCode) }" />
          <div class="tl-name">{{ c.carrierName }}</div>
          <div class="tl-meta">{{ c.contactName }} · {{ c.contactPhone }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { baseApi, orderApi, handoverApi } from '@/api'

const carriers = ref([])
const orders = ref([])
const handovers = ref([])

const COLORS = {
  SF: 'linear-gradient(135deg, #1f2937, #4b5563)',
  JD: 'linear-gradient(135deg, #b91c1c, #ef4444)',
  ZTO: 'linear-gradient(135deg, #1d4ed8, #3b82f6)',
  YTO: 'linear-gradient(135deg, #0f766e, #14b8a6)',
  STO: 'linear-gradient(135deg, #c2410c, #f97316)'
}

const sortedCarriers = computed(() =>
  carriers.value.slice().sort((a, b) => String(a.dailyPickupTime).localeCompare(String(b.dailyPickupTime)))
)

/** 承运商 → 关联订单数 / 波次数 / 交接单数 */
const stats = computed(() => {
  const map = {}
  const ensure = (code) => {
    if (!map[code]) map[code] = { orderCount: 0, waveCount: 0, handoverCount: 0 }
    return map[code]
  }
  const waveSet = {}
  orders.value.forEach((o) => {
    const s = ensure(o.carrierCode)
    s.orderCount++
    if (o.waveNo) {
      if (!waveSet[o.carrierCode]) waveSet[o.carrierCode] = new Set()
      waveSet[o.carrierCode].add(o.waveNo)
    }
  })
  Object.entries(waveSet).forEach(([code, set]) => {
    ensure(code).waveCount = set.size
  })
  handovers.value.forEach((h) => {
    ensure(h.carrierCode).handoverCount++
  })
  return map
})

function logoBg(code) {
  return COLORS[code] || 'linear-gradient(135deg, #475569, #94a3b8)'
}

async function load() {
  const [carrierList, orderRes, handoverRes] = await Promise.all([
    baseApi.carriers(),
    orderApi.page({ pageNum: 1, pageSize: 500 }),
    handoverApi.page({ pageNum: 1, pageSize: 500 })
  ])
  carriers.value = carrierList
  orders.value = orderRes.records
  handovers.value = handoverRes.records
}

onMounted(load)
</script>

<style scoped lang="scss">
.carrier-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(290px, 1fr));
  gap: 12px;
}

.carrier-card {
  background: #fff;
  border: 1px solid $border-base;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
  padding: 16px 18px;
  transition: transform 0.16s, box-shadow 0.16s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: $shadow-hover;
  }
}

.cc-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.cc-logo {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  border-radius: 11px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 0.5px;
}

.cc-title {
  flex: 1;
  min-width: 0;
}

.cc-name {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 2px;
}

.cc-code {
  font-size: 11px;
  color: $text-placeholder;
}

.cc-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  background: #f8fafc;
  border-radius: 8px;
  margin-bottom: 14px;
}

.cc-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 12.5px;
}

.cc-label {
  color: $text-placeholder;
  min-width: 58px;
  flex-shrink: 0;
}

.pickup {
  font-weight: 600;
  color: $color-warning;
}

.cc-foot {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.cc-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;

  .num {
    font-size: 17px;
    font-weight: 700;
    color: $text-primary;
  }
}

.cs-label {
  font-size: 11px;
  color: $text-placeholder;
}

/* 时间轴 */
.panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 14px;
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

.timeline {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tl-item {
  display: grid;
  grid-template-columns: 70px 5px 110px 1fr;
  align-items: center;
  gap: 12px;
}

.tl-time {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
  text-align: right;
}

.tl-bar {
  height: 26px;
  border-radius: 4px;
}

.tl-name {
  font-size: 12.5px;
  font-weight: 500;
  color: $text-regular;
}

.tl-meta {
  font-size: 11.5px;
  color: $text-placeholder;
}
</style>
