<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="SKU编码 / 名称 / 库位" clearable style="width: 220px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer"></div>
        <el-button type="success" @click="openEdit()">新增 SKU</el-button>
      </div>

      <el-table :data="rows" border stripe size="small" v-loading="loading">
        <el-table-column prop="skuCode" label="SKU编码" width="130" />
        <el-table-column prop="name" label="商品名称" min-width="150" />
        <el-table-column prop="spec" label="规格" min-width="130" />
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column prop="location" label="拣货库位" width="110" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
                       v-model:current-page="query.page" v-model:page-size="query.size"
                       :page-sizes="[10, 20, 50]" @current-change="load" @size-change="reload" />
      </div>
    </el-card>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑 SKU' : '新增 SKU'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="SKU编码">
          <el-input v-model="form.skuCode" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="商品名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="规格"><el-input v-model="form.spec" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="form.category" /></el-form-item>
        <el-form-item label="拣货库位"><el-input v-model="form.location" placeholder="如 A-01-01" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addSku, deleteSku, listSkus, updateSku } from '@/api/sku'

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 10 })

const editVisible = ref(false)
const form = reactive<any>({ id: null, skuCode: '', name: '', spec: '', category: '', location: '' })

async function load() {
  loading.value = true
  try {
    const res = await listSkus({ ...query })
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

function openEdit(row?: any) {
  form.id = row ? row.id : null
  form.skuCode = row ? row.skuCode : ''
  form.name = row ? row.name : ''
  form.spec = row ? row.spec : ''
  form.category = row ? row.category : ''
  form.location = row ? row.location : ''
  editVisible.value = true
}

async function submit() {
  if (!form.skuCode || !form.name) {
    ElMessage.warning('请填写 SKU 编码与名称')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateSku({ ...form })
    } else {
      await addSku({ ...form })
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除 ${row.skuCode} 吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await deleteSku(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
