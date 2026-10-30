<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-select v-model="query.role" placeholder="角色" clearable style="width: 150px" @change="reload">
          <el-option v-for="(label, key) in roleLabels" :key="key" :label="label" :value="key" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="用户名 / 昵称" clearable style="width: 200px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
        <div class="spacer"></div>
        <el-button type="success" @click="openEdit()">新增用户</el-button>
      </div>

      <el-table :data="rows" border stripe size="small" v-loading="loading">
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column label="角色" width="130">
          <template #default="{ row }">
            <el-tag size="small">{{ roleLabels[row.role] || row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="onReset(row)">重置密码</el-button>
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

    <el-dialog v-model="editVisible" :title="form.id ? '编辑用户' : '新增用户'" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名"><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="(label, key) in roleLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button label="启用" value="启用" />
            <el-radio-button label="停用" value="停用" />
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始密码">
          <el-input v-model="form.password" placeholder="默认 123456" />
        </el-form-item>
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
import { addUser, deleteUser, listUsers, resetPassword, updateUser } from '@/api/user'
import { ROLE_LABELS } from '@/store/user'

const roleLabels = ROLE_LABELS

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ keyword: '', role: '', page: 1, size: 10 })

const editVisible = ref(false)
const form = reactive<any>({ id: null, username: '', nickname: '', role: 'PICKER', status: '启用', password: '' })

async function load() {
  loading.value = true
  try {
    const res = await listUsers({ ...query })
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

function openEdit(row?: any) {
  form.id = row ? row.id : null
  form.username = row ? row.username : ''
  form.nickname = row ? row.nickname : ''
  form.role = row ? row.role : 'PICKER'
  form.status = row ? row.status : '启用'
  form.password = ''
  editVisible.value = true
}

async function submit() {
  if (!form.username) {
    ElMessage.warning('请填写用户名')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateUser({ ...form })
    } else {
      await addUser({ ...form })
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onReset(row: any) {
  try {
    await ElMessageBox.confirm(`确定将 ${row.username} 的密码重置为 123456 吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await resetPassword(row.id, '123456')
  ElMessage.success('密码已重置为 123456')
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除用户 ${row.username} 吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await deleteUser(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
