<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-select v-model="query.zone" placeholder="库区" clearable style="width: 120px" @change="reload">
          <el-option v-for="z in zones" :key="z" :label="z + ' 区'" :value="z" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="库位编码 / 备注" clearable style="width: 220px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer"></div>
        <el-button type="success" @click="openEdit()">新增库位</el-button>
      </div>

      <el-table :data="rows" border stripe size="small" v-loading="loading">
        <el-table-column prop="locationCode" label="库位编码" width="130" />
        <el-table-column prop="zone" label="库区" width="90" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="capacity" label="容量" width="90" align="center" />
        <el-table-column prop="remark" label="备注" min-width="160" />
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

    <el-dialog v-model="editVisible" :title="form.id ? '编辑库位' : '新增库位'" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="库位编码"><el-input v-model="form.locationCode" placeholder="如 A-01-01" /></el-form-item>
        <el-form-item label="库区">
          <el-select v-model="form.zone" placeholder="选择库区" style="width: 100%">
            <el-option v-for="z in ['A', 'B', 'C', 'D', 'S', 'T']" :key="z" :label="z + ' 区'" :value="z" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in ['拣货位', '存储位', '暂存位']" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="启用">启用</el-radio>
            <el-radio value="停用">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="容量"><el-input-number v-model="form.capacity" :min="0" :step="50" /></el-form-item>
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
import { deleteLocation, locationPage, locationZones, saveLocation } from '@/api/base'

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const zones = ref<string[]>([])
const query = reactive({ zone: '', keyword: '', page: 1, size: 10 })

const editVisible = ref(false)
const form = reactive<any>({ id: null, locationCode: '', zone: 'A', type: '拣货位', status: '启用', capacity: 0, remark: '' })

async function load() {
  loading.value = true
  try {
    const res = await locationPage({ ...query })
    rows.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadZones() {
  const res = await locationZones()
  zones.value = res.data || []
}

function reload() {
  query.page = 1
  load()
}

function reset() {
  query.zone = ''
  query.keyword = ''
  reload()
}

function openEdit(row?: any) {
  form.id = row ? row.id : null
  form.locationCode = row ? row.locationCode : ''
  form.zone = row ? row.zone : 'A'
  form.type = row ? row.type : '拣货位'
  form.status = row ? row.status : '启用'
  form.capacity = row ? row.capacity : 0
  form.remark = row ? row.remark : ''
  editVisible.value = true
}

async function submit() {
  if (!form.locationCode) {
    ElMessage.warning('请填写库位编码')
    return
  }
  saving.value = true
  try {
    await saveLocation({ ...form })
    ElMessage.success('保存成功')
    editVisible.value = false
    loadZones()
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除库位 ${row.locationCode} 吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await deleteLocation(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(() => {
  loadZones()
  load()
})
</script>
