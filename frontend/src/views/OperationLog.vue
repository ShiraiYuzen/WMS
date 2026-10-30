<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-select v-model="query.module" placeholder="业务模块" clearable style="width: 140px" @change="reload">
          <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="操作人 / 接口 / 详情" clearable style="width: 240px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table :data="rows" border stripe size="small" v-loading="loading">
        <el-table-column prop="module" label="模块" width="110" />
        <el-table-column prop="action" label="操作" width="110" />
        <el-table-column prop="method" label="方法" width="80" />
        <el-table-column prop="uri" label="接口" min-width="190" />
        <el-table-column prop="operator" label="操作人" width="110" />
        <el-table-column prop="role" label="角色" width="120" />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.result === '成功' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
                       v-model:current-page="query.page" v-model:page-size="query.size"
                       :page-sizes="[15, 30, 50]" @current-change="load" @size-change="reload" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { logPage } from '@/api/log'

const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const modules = ['波次管理', '订单汇聚', '拣货执行', '复核打包', '出库交接', '缺货处理', 'SKU管理', '用户管理', '基础数据']
const query = reactive({ module: '', keyword: '', page: 1, size: 15 })

async function load() {
  loading.value = true
  try {
    const res = await logPage({ ...query })
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
  query.module = ''
  query.keyword = ''
  reload()
}

onMounted(load)
</script>
