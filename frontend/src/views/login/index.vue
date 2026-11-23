<template>
  <main ref="entry" class="public-entry" :class="{ 'public-entry--home': showHome }">
    <section v-if="showHome" ref="hero" class="hero" aria-labelledby="hero-title" tabindex="-1">
      <canvas class="particles" aria-hidden="true"></canvas>
      <header class="topbar">
        <div class="wordmark">
          <span class="mark" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
          <span>仓库拣货与波次管理系统</span>
        </div>
        <button type="button" class="screen-link top-link" @click="goToScreen(login)">登录 <span aria-hidden="true">↘</span></button>
      </header>

      <div class="hero-content">
        <div class="particle-stage" aria-hidden="true">
          <span class="particle-fallback">WMS</span>
        </div>
        <h1 id="hero-title">仓库拣货与波次管理</h1>
      </div>

      <button type="button" class="screen-link scroll-link" @click="goToScreen(login)">
        向下进入登录
        <span class="down-arrow" aria-hidden="true">↓</span>
      </button>
    </section>

    <section ref="login" class="login" aria-labelledby="login-title" tabindex="-1">
      <canvas ref="warehouse" class="warehouse-plan" aria-hidden="true"></canvas>
      <button v-if="showHome" type="button" class="screen-link back-link" @click="goToScreen(hero)">
        <span aria-hidden="true">↑</span> 返回首页
      </button>
      <RouterLink v-else class="back-link" to="/">
        <span aria-hidden="true">↑</span> 返回首页
      </RouterLink>

      <div class="card">
        <h2 id="login-title">账号登录</h2>
        <p class="card-description">使用仓储系统账号进入工作台</p>

        <form @submit.prevent="onSubmit">
          <div class="field">
            <label for="username">用户名</label>
            <input id="username" v-model="form.username" autocomplete="username" placeholder="请输入用户名" required>
          </div>
          <div class="field">
            <label for="password">密码</label>
            <div class="input-wrap password-wrap">
              <input
                id="password"
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
                required
              >
              <button
                class="password-toggle"
                type="button"
                aria-controls="password"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >{{ showPassword ? '隐藏' : '显示' }}</button>
            </div>
          </div>
          <button class="submit" type="submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
        </form>

        <div class="demo">
          <h3>演示账号 <span>· 点击填入</span></h3>
          <div class="demo-grid">
            <button v-for="account in accounts" :key="account.username" type="button" @click="fill(account.username)">
              {{ account.roleDesc }} <small>{{ account.username }}</small>
            </button>
          </div>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { mountLoginReveal, mountParticleTitle, mountScreenScrolling, mountWarehouseBackground } from './public-entry-effects'

const props = defineProps({ showHome: Boolean })
const router = useRouter()
const route = useRoute()
const app = useAppStore()

const entry = ref(null)
const hero = ref(null)
const login = ref(null)
const warehouse = ref(null)
const loading = ref(false)
const showPassword = ref(false)
const form = reactive({ username: props.showHome ? '' : 'admin', password: props.showHome ? '' : '123456' })
const accounts = computed(() => {
  const admin = app.ROLE_OPTIONS.find(account => account.username === 'admin')
  return admin ? [admin, ...app.ROLE_OPTIONS.filter(account => account !== admin)] : app.ROLE_OPTIONS
})
const cleanup = []

onMounted(() => {
  cleanup.push(mountWarehouseBackground(warehouse.value))
  if (props.showHome) {
    cleanup.push(
      mountLoginReveal(login.value, entry.value),
      mountScreenScrolling(entry.value, login.value),
      mountParticleTitle(hero.value, entry.value)
    )
  }
})

onBeforeUnmount(() => cleanup.forEach(dispose => dispose?.()))

function goToScreen(screen) {
  screen.focus({ preventScroll: true })
  const offset = screen.getBoundingClientRect().top - entry.value.getBoundingClientRect().top
  entry.value.scrollTo({ top: entry.value.scrollTop + offset })
}

function fill(username) {
  form.username = username
  form.password = '123456'
}

async function onSubmit() {
  if (loading.value) return
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }

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

<style scoped src="./public-entry.css"></style>
