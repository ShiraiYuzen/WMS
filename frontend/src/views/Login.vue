<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-head">
        <div class="login-logo">WMS</div>
        <h1>仓库拣货与波次管理系统</h1>
        <p class="muted">订单汇聚 · 波次生成 · 拣货执行 · 复核打包 · 出库交接 · 效率复盘</p>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @keyup.enter="onSubmit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" size="large" clearable>
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="onSubmit">
          登 录
        </el-button>
      </el-form>

      <div class="quick">
        <div class="muted" style="margin-bottom: 8px">演示账号（密码均为 123456）</div>
        <div class="quick-btns">
          <el-tag v-for="a in accounts" :key="a.username" class="quick-tag" @click="fill(a)">
            {{ a.label }}
          </el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login } from '@/api/auth'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '123456' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const accounts = [
  { label: '管理员 admin', username: 'admin', password: '123456' },
  { label: '波次员 planner', username: 'planner', password: '123456' },
  { label: '拣货员 picker1', username: 'picker1', password: '123456' },
  { label: '拣货员 picker2', username: 'picker2', password: '123456' },
  { label: '复核员 checker1', username: 'checker1', password: '123456' },
  { label: '复核员 checker2', username: 'checker2', password: '123456' },
]

function fill(a: { username: string; password: string }) {
  form.username = a.username
  form.password = a.password
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await login(form.username.trim(), form.password)
      const d = res.data
      store.setLogin(d.token, d.username, d.nickname, d.role)
      ElMessage.success('登录成功')
      const redirect = (route.query.redirect as string) || '/dashboard'
      router.push(redirect)
    } catch {
      // 错误已由拦截器提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f3a5f 0%, #2c5282 45%, #4a7ba7 100%);
}

.login-box {
  width: 420px;
  background: #fff;
  border-radius: 12px;
  padding: 32px 36px 26px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}

.login-head {
  text-align: center;
  margin-bottom: 18px;
}

.login-logo {
  display: inline-block;
  background: #409eff;
  color: #fff;
  font-weight: 800;
  letter-spacing: 2px;
  padding: 6px 14px;
  border-radius: 8px;
  margin-bottom: 12px;
}

.login-head h1 {
  font-size: 19px;
  margin: 0 0 8px;
}

.login-head p {
  font-size: 12px;
  margin: 0;
}

.quick {
  margin-top: 18px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 14px;
}

.quick-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.quick-tag {
  cursor: pointer;
}
</style>
