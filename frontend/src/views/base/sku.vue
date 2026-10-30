<template>
  <div class="page-container">
    <PageHeader
      title="商品档案"
      description="SKU 主数据。默认库位决定拣货路径顺序，安全库存与当前库存是缺货预警的判定依据。接入数据库后此页将开放新增与编辑。"
      :count="total"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="card card-pad mb-12">
      <el-form :model="query" inline class="filter-form">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="SKU 编码或名称" clearable style="width: 220px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="品类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="rows" stripe height="calc(100vh - 348px)">
        <el-table-column label="SKU 编码" width="135" fixed>
          <template #default="{ row }"><span class="mono">{{ row.skuCode }}</span></template>
        </el-table-column>
        <el-table-column label="商品名称" min-width="200">
          <template #default="{ row }">{{ row.skuName }}</template>
        </el-table-column>
        <el-table-column label="规格" width="110">
          <template #default="{ row }"><span class="text-secondary">{{ row.spec }}</span></template>
        </el-table-column>
        <el-table-column label="单位" width="70" align="center">
          <template #default="{ row }">{{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="品类" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="条码" width="160">
          <template #default="{ row }"><span class="mono text-secondary">{{ row.barcode }}</span></template>
        </el-table-column>
        <el-table-column label="默认库位" width="135">
          <template #default="{ row }"><span class="mono">{{ row.defaultLocationCode }}</span></template>
        </el-table-column>
        <el-table-column label="重量" width="90" align="right">
          <template #default="{ row }"><span class="num">{{ row.weight }} kg</span></template>
        </el-table-column>
        <el-table-column label="安全库存" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ row.safetyStock }}</span></template>
        </el-table-column>
        <el-table-column label="当前库存" width="105" align="right">
          <template #default="{ row }">
            <span class="num" :class="{ danger: row.stockQty < row.safetyStock }">{{ row.stockQty }}</span>
            <el-tooltip v-if="row.stockQty < row.safetyStock" content="低于安全库存，存在缺货风险" placement="top">
              <el-icon class="warn-icon"><WarningFilled /></el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === 1 ? '在售' : '停用' }}
            </el-tag>
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
import { onMounted, ref } from 'vue'
import { Refresh, Search, RefreshLeft, WarningFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { baseApi } from '@/api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const categories = ref([])

const query = ref({ pageNum: 1, pageSize: 50, keyword: '', category: '' })

async function load() {
  loading.value = true
  try {
    const res = await baseApi.skuPage(query.value)
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
  query.value = { pageNum: 1, pageSize: query.value.pageSize, keyword: '', category: '' }
  load()
}

onMounted(async () => {
  categories.value = await baseApi.skuCategories()
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

.warn-icon {
  margin-left: 4px;
  font-size: 12px;
  color: $color-warning;
  vertical-align: middle;
}
</style>
