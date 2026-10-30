<template>
  <div class="nf-wrap">
    <div class="nf-card">
      <div class="nf-code">404</div>
      <h2 class="nf-title">页面走丢了</h2>
      <p class="nf-desc">
        你访问的地址不在系统路由表中。可能是链接过期，或该功能尚未开放。
      </p>
      <div class="nf-path">
        <span class="nf-label">当前地址</span>
        <span class="mono">{{ path }}</span>
      </div>
      <div class="nf-actions">
        <el-button type="primary" :icon="HomeFilled" @click="goHome">回到作业看板</el-button>
        <el-button :icon="Back" @click="goBack">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HomeFilled, Back } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const path = computed(() => route.fullPath)

function goHome() {
  router.push('/dashboard')
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/dashboard')
}
</script>

<style scoped lang="scss">
.nf-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #f8fafc 0%, #eff6ff 100%);
  padding: 24px;
}

.nf-card {
  max-width: 460px;
  width: 100%;
  text-align: center;
  background: #fff;
  border: 1px solid $border-base;
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.08);
  padding: 40px 36px 34px;
}

.nf-code {
  font-size: 62px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -2px;
  background: linear-gradient(135deg, #2563eb, #60a5fa);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin-bottom: 14px;
}

.nf-title {
  margin: 0 0 10px;
  font-size: 18px;
  font-weight: 600;
  color: $text-primary;
}

.nf-desc {
  margin: 0 0 20px;
  font-size: 12.5px;
  line-height: 1.7;
  color: $text-secondary;
}

.nf-path {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 14px;
  background: #f8fafc;
  border: 1px solid $border-base;
  border-radius: 8px;
  font-size: 12px;
  color: $text-regular;
  margin-bottom: 22px;
  word-break: break-all;
}

.nf-label {
  color: $text-placeholder;
  flex-shrink: 0;
}

.nf-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}
</style>
