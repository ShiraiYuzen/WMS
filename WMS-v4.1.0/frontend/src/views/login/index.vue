<template>
  <div class="login-page">
    <!-- 左侧品牌区 -->
    <section class="brand-pane">
      <div class="brand-inner">
        <div class="logo-row">
          <div class="logo"><el-icon><Box /></el-icon></div>
          <div>
            <div class="logo-title">拣货与波次管理</div>
            <div class="logo-sub">Warehouse Picking Platform</div>
          </div>
        </div>

        <h1 class="slogan">
          从订单汇聚到出库交接<br />
          <span>一条链路跑完全程</span>
        </h1>

        <ul class="features">
          <li><el-icon><Tickets /></el-icon> 订单池汇聚 · 按承运商/时效自动分波</li>
          <li><el-icon><Grid /></el-icon> 波次生成 · 负载均衡指派拣货员</li>
          <li><el-icon><Van /></el-icon> 拣货执行 · 缺货实时上报与替代</li>
          <li><el-icon><DocumentChecked /></el-icon> 复核打包 · 差异记录与出库交接</li>
        </ul>

        <div class="brand-foot">WMS v4 · Spring Boot + Vue3</div>
      </div>
    </section>

    <!-- 右侧登录表单 -->
    <section class="form-pane">
      <div class="form-box">
        <h2 class="form-title">账号登录</h2>
        <p class="form-desc">请使用仓储系统账号进入工作台</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="onSubmit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" clearable @keyup.enter="onSubmit">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              show-password
              @keyup.enter="onSubmit"
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">
            登 录
          </el-button>
        </el-form>

        <div class="demo-block">
          <div class="demo-title">演示账号（点击填入，密码均为 123456）</div>
          <div class="demo-list">
            <div v-for="a in accounts" :key="a.username" class="demo-item" @click="fill(a.username)">
              <el-tag size="small" effect="plain">{{ a.roleDesc }}</el-tag>
              <span class="demo-user">{{ a.username }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const route = useRoute()
const app = useAppStore()

const formRef = ref()
const loading = ref(false)

const form = reactive({ username: 'admin', password: '123456' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const accounts = app.ROLE_OPTIONS

function fill(username) {
  form.username = username
  form.password = '123456'
}

async function onSubmit() {
  if (loading.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await authApi.login(form.username.trim(), form.password)
    app.setUser(data)
    ElMessage.success(`欢迎回来，${data.employeeName}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch {
    /* 错误提示已由响应拦截器统一处理 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  display: flex;
  height: 100vh;
  background: #f1f5f9;
}

/* ============================ 左侧品牌区 ============================ */

.brand-pane {
  position: relative;
  flex: 1.15;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: linear-gradient(145deg, #0f172a 0%, #16233c 55%, #1e3a8a 100%);
  color: #e2e8f0;

  &::before {
    content: '';
    position: absolute;
    width: 620px;
    height: 620px;
    right: -180px;
    top: -200px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(59, 130, 246, 0.35), transparent 65%);
  }

  &::after {
    content: '';
    position: absolute;
    width: 460px;
    height: 460px;
    left: -160px;
    bottom: -180px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(99, 102, 241, 0.28), transparent 65%);
  }
}

.brand-inner {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 460px;
  padding: 0 24px;
}

.logo-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 44px;
}

.logo {
  width: 44px;
  height: 44px;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #fff;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  box-shadow: 0 6px 18px rgba(37, 99, 235, 0.45);
}

.logo-title {
  font-size: 16px;
  font-weight: 600;
  color: #f8fafc;
  letter-spacing: 0.4px;
}

.logo-sub {
  font-size: 10.5px;
  color: #64748b;
  letter-spacing: 0.4px;
}

.slogan {
  font-size: 30px;
  line-height: 1.5;
  font-weight: 600;
  margin: 0 0 34px;
  letter-spacing: 0.5px;

  span {
    background: linear-gradient(90deg, #60a5fa, #a5b4fc);
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
  }
}

.features {
  list-style: none;
  margin: 0;
  padding: 0;

  li {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 13.5px;
    color: #94a3b8;
    padding: 9px 0;

    .el-icon {
      color: #60a5fa;
      font-size: 16px;
    }
  }
}

.brand-foot {
  margin-top: 46px;
  font-size: 11.5px;
  color: #475569;
  letter-spacing: 0.6px;
}

/* ============================ 右侧表单区 ============================ */

.form-pane {
  flex: 0 0 500px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  box-shadow: -6px 0 28px rgba(15, 23, 42, 0.06);
}

.form-box {
  width: 340px;
}

.form-title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 600;
  color: #0f172a;
}

.form-desc {
  margin: 0 0 26px;
  font-size: 13px;
  color: #94a3b8;
}

.submit {
  width: 100%;
  height: 42px;
  font-size: 15px;
  letter-spacing: 2px;
  margin-top: 4px;
}

.demo-block {
  margin-top: 30px;
  padding-top: 18px;
  border-top: 1px dashed #e2e8f0;
}

.demo-title {
  font-size: 11.5px;
  color: #94a3b8;
  margin-bottom: 10px;
}

.demo-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.demo-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.16s;

  &:hover {
    border-color: #93c5fd;
    background: #f8fbff;
  }
}

.demo-user {
  font-size: 12.5px;
  color: #475569;
  font-family: Consolas, Monaco, monospace;
}

@media (max-width: 1024px) {
  .brand-pane {
    display: none;
  }

  .form-pane {
    flex: 1;
  }
}
</style>
