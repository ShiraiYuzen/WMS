<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="处理状态" clearable style="width: 140px" @change="reload">
          <el-option label="待处理" value="待处理" />
          <el-option label="已补货" value="已补货" />
          <el-option label="已核减" value="已核减" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="波次号 / SKU / 商品" clearable style="width: 220px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table :data="rows" border stripe size="small" v-loading="loading">
        <el-table-column prop="waveNo" label="波次号" width="150" />
        <el-table-column prop="skuCode" label="SKU" width="110" />
        <el-table-column prop="skuName" label="商品" min-width="130" />
        <el-table-column prop="location" label="库位" width="90" />
        <el-table-column prop="requiredQty" label="需求" width="70" align="center" />
        <el-table-column prop="pickedQty" label="实拣" width="70" align="center" />
        <el-table-column prop="shortQty" label="缺货数" width="80" align="center">
          <template #default="{ row }"><span class="danger-text">{{ row.shortQty }}</span></template>
        </el-table-column>
        <el-table-column prop="reason" label="缺货原因" width="110" />
        <el-table-column prop="reporterName" label="上报人" width="130" />
        <el-table-column label="处理状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === '待处理' ? 'warning' : 'success'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="上报时间" width="160" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status !== '待处理'" @click="openHandle(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
                       v-model:current-page="query.page" v-model:page-size="query.size"
                       :page-sizes="[10, 20, 50]" @current-change="load" @size-change="reload" />
      </div>
    </el-card>

    <el-dialog v-model="handleVisible" title="处理缺货" width="460px">
      <el-form label-width="90px">
        <el-form-item label="波次号">{{ handleRow.waveNo }}</el-form-item>
        <el-form-item label="商品">{{ handleRow.skuCode }} {{ handleRow.skuName }}</el-form-item>
        <el-form-item label="缺货数量">{{ handleRow.shortQty }}</el-form-item>
        <el-form-item label="处理方式">
          <el-radio-group v-model="handleForm.status">
            <el-radio-button label="已补货" value="已补货" />
            <el-radio-button label="已核减" value="已核减" />
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理说明"><el-input v-model="handleForm.remark" placeholder="如：已从备用库位补货" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitHandle">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { handleShortage, listShortages } from '@/api/shortage'

const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ status: '', keyword: '', page: 1, size: 10 })

const handleVisible = ref(false)
const handleRow = ref<any>({})
const handleForm = reactive({ status: '已补货', remark: '' })

async function load() {
  loading.value = true
  try {
    const res = await listShortages({ ...query })
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
  query.status = ''
  query.keyword = ''
  reload()
}

function openHandle(row: any) {
  handleRow.value = row
  handleForm.status = '已补货'
  handleForm.remark = ''
  handleVisible.value = true
}

async function submitHandle() {
  await handleShortage({ id: handleRow.value.id, status: handleForm.status, remark: handleForm.remark })
  ElMessage.success('已处理')
  handleVisible.value = false
  load()
}

onMounted(load)
</script>

<style scoped>
.danger-text {
  color: #f56c6c;
  font-weight: 600;
}
</style>
