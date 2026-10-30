<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="SKU / 商品 / 库位" clearable style="width: 220px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table :data="rows" border stripe size="small" v-loading="loading">
        <el-table-column prop="location" label="库位" width="110" />
        <el-table-column prop="skuCode" label="SKU编码" width="130" />
        <el-table-column prop="skuName" label="商品名称" min-width="160" />
        <el-table-column prop="totalQty" label="总库存" width="100" align="center" />
        <el-table-column prop="lockedQty" label="波次锁定" width="100" align="center" />
        <el-table-column label="可用库存" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.availableQty <= 10 ? 'danger' : 'success'">{{ row.availableQty }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170" />
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
                       v-model:current-page="query.page" v-model:page-size="query.size"
                       :page-sizes="[10, 20, 50]" @current-change="load" @size-change="reload" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listInventory } from '@/api/inventory'

const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 10 })

async function load() {
  loading.value = true
  try {
    const res = await listInventory({ ...query })
    rows.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function reload() {
  query.page = 1
  load()
}

function reset() {
  query.keyword = ''
  reload()
}

onMounted(load)
</script>
