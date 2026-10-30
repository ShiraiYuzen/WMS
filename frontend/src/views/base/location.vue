<template>
  <div class="page-container">
    <PageHeader
      title="库位管理"
      description="库位按「区-巷-架-层」编码，pathSeq 决定拣货行走顺序。波次生成时会把同一库位的商品合并，并按 pathSeq 排序形成单向拣货路线。"
      :count="total"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="card card-pad mb-12">
      <el-form :model="query" inline class="filter-form">
        <el-form-item label="库区">
          <el-select v-model="query.zone" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="z in zones" :key="z" :label="zoneLabel(z)" :value="z" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 库区概览 -->
    <div class="zone-row mb-12">
      <div v-for="z in zoneStats" :key="z.zone" class="zone-card">
        <div class="zc-code">{{ z.zone }}</div>
        <div class="zc-body">
          <div class="zc-name">{{ z.zoneName }}</div>
          <div class="zc-meta">
            <span>{{ z.temp }}</span>
            <span class="dot">·</span>
            <span><b class="num">{{ z.count }}</b> 个库位</span>
          </div>
        </div>
      </div>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="rows" stripe height="calc(100vh - 452px)">
        <el-table-column label="库位编码" width="150" fixed>
          <template #default="{ row }"><span class="mono loc-code">{{ row.locationCode }}</span></template>
        </el-table-column>
        <el-table-column label="库区" width="130">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info">{{ row.zone }} · {{ row.zoneName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="巷 / 架 / 层" width="140" align="center">
          <template #default="{ row }">
            <span class="mono">{{ row.aisle }} / {{ row.rack }} / {{ row.level }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库位类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.locationType === 'BULK' ? 'warning' : 'primary'" effect="plain">
              {{ row.locationType === 'BULK' ? '周转位' : '拣货位' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :code="row.status" group="locationStatus" />
          </template>
        </el-table-column>
        <el-table-column label="偏好承运商" width="130">
          <template #default="{ row }">
            <span v-if="row.preferredCarrierCode" class="mono">{{ carrierName(row.preferredCarrierCode) }}</span>
            <span v-else class="text-secondary">—</span>
          </template>
        </el-table-column>
        <el-table-column label="拣货路径序号" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ row.pathSeq }}</span></template>
        </el-table-column>
        <el-table-column label="关联 SKU" min-width="220">
          <template #default="{ row }">
            <div v-if="skuMap[row.locationCode]" class="sku-tags">
              <el-tag
                v-for="s in skuMap[row.locationCode].slice(0, 4)"
                :key="s.skuCode"
                size="small"
                effect="plain"
                class="sku-tag"
              >
                {{ s.skuName }}
              </el-tag>
              <span v-if="skuMap[row.locationCode].length > 4" class="text-secondary more">
                +{{ skuMap[row.locationCode].length - 4 }}
              </span>
            </div>
            <span v-else class="text-secondary">—</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="load"
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh, Search, RefreshLeft } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { baseApi } from '@/api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const zones = ref([])
const allLocations = ref([])
const skus = ref([])
const carriers = ref([])

const query = ref({ pageNum: 1, pageSize: 50, zone: '' })

const ZONE_META = {
  A: { name: '冷藏区', temp: '0-4℃' },
  B: { name: '常温区', temp: '15-25℃' },
  C: { name: '干货区', temp: '常温干燥' },
  D: { name: '周转区', temp: '临时周转' }
}

const zoneStats = computed(() => {
  const map = new Map()
  allLocations.value.forEach((l) => {
    if (!map.has(l.zone)) {
      map.set(l.zone, { zone: l.zone, zoneName: l.zoneName || ZONE_META[l.zone]?.name || '', temp: ZONE_META[l.zone]?.temp || '', count: 0 })
    }
    map.get(l.zone).count++
  })
  return [...map.values()].sort((a, b) => a.zone.localeCompare(b.zone))
})

/** 库位 → 该库位上的 SKU 列表 */
const skuMap = computed(() => {
  const map = {}
  skus.value.forEach((s) => {
    if (!map[s.defaultLocationCode]) map[s.defaultLocationCode] = []
    map[s.defaultLocationCode].push(s)
  })
  return map
})

function zoneLabel(z) {
  const meta = ZONE_META[z]
  return meta ? `${z} · ${meta.name}` : z
}

function carrierName(code) {
  return carriers.value.find((c) => c.carrierCode === code)?.carrierName || code
}

async function load() {
  loading.value = true
  try {
    const res = await baseApi.locationPage(query.value)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.value.pageNum = 1
  load()
}

function reset() {
  query.value = { pageNum: 1, pageSize: query.value.pageSize, zone: '' }
  load()
}

onMounted(async () => {
  const [zoneList, all, skuRes, carrierList] = await Promise.all([
    baseApi.zones(),
    baseApi.locationPage({ pageNum: 1, pageSize: 500 }),
    baseApi.skuPage({ pageNum: 1, pageSize: 500 }),
    baseApi.carriers()
  ])
  zones.value = zoneList
  allLocations.value = all.records
  skus.value = skuRes.records
  carriers.value = carrierList
  await load()
})
</script>

<style scoped lang="scss">
.filter-form {
  :deep(.el-form-item) {
    margin-bottom: 6px;
    margin-right: 16px;
  }
}

.loc-code {
  font-weight: 600;
  color: $color-primary;
}

.zone-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.zone-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 16px;
  background: #fff;
  border: 1px solid $border-base;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
}

.zc-code {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 9px;
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  color: $color-primary-dark;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  font-family: 'DIN Alternate', Menlo, monospace;
}

.zc-name {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 3px;
}

.zc-meta {
  font-size: 11.5px;
  color: $text-placeholder;

  b {
    color: $text-regular;
  }
}

.dot {
  margin: 0 3px;
  color: #cbd5e1;
}

.sku-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.sku-tag {
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.more {
  font-size: 11px;
}
</style>
