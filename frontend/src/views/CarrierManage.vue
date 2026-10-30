<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="编码 / 名称 / 联系人" clearable style="width: 240px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer"></div>
        <el-button type="success" @click="openEdit()">新增承运商</el-button>
      </div>

      <el-table :data="rows" border stripe size="small" v-loading="loading">
        <el-table-column prop="carrierCode" label="编码" width="100" />
        <el-table-column prop="carrierName" label="承运商名称" min-width="140" />
        <el-table-column prop="contact" label="联系人" width="110" />
        <el-table-column prop="phone" label="联系电话" width="150" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" />
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

    <el-dialog v-model="editVisible" :title="form.id ? '编辑承运商' : '新增承运商'" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="编码"><el-input v-model="form.carrierCode" placeholder="如 SF" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.carrierName" placeholder="如 顺丰速运" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="启用">启用</el-radio>
            <el-radio value="停用">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
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
import { carrierPage, deleteCarrier, saveCarrier } from '@/api/base'

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 10 })

const editVisible = ref(false)
const form = reactive<any>({ id: null, carrierCode: '', carrierName: '', contact: '', phone: '', status: '启用', remark: '' })

async function load() {
  loading.value = true
  try {
    const res = await carrierPage({ ...query })
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
  form.carrierCode = row ? row.carrierCode : ''
  form.carrierName = row ? row.carrierName : ''
  form.contact = row ? row.contact : ''
  form.phone = row ? row.phone : ''
  form.status = row ? row.status : '启用'
  form.remark = row ? row.remark : ''
  editVisible.value = true
}

async function submit() {
  if (!form.carrierName) {
    ElMessage.warning('请填写承运商名称')
    return
  }
  saving.value = true
  try {
    await saveCarrier({ ...form })
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除承运商 ${row.carrierName} 吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await deleteCarrier(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
