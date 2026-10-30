<template>
  <el-container class="layout">
    <el-aside width="218px" class="aside">
      <div class="brand">
        <span class="brand-logo">WMS</span>
        <span class="brand-text">拣货波次管理</span>
      </div>
      <el-menu :default-active="activePath" router class="menu" background-color="#1f2d3d"
               text-color="#c0c4cc" active-text-color="#ffffff">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ currentTitle }}</div>
        <div class="header-right">
          <el-tag size="small" type="info">{{ store.roleLabel }}</el-tag>
          <span class="nickname">{{ store.nickname || store.username }}</span>
          <el-button link type="primary" @click="onLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <keep-alive :max="6">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { logout } from '@/api/auth'
import { useUserStore } from '@/store/user'

interface MenuItem {
  path: string
  title: string
  icon: string
  roles: string[]
}

const allMenus: MenuItem[] = [
  { path: '/dashboard', title: '工作台', icon: 'DataAnalysis', roles: [] },
  { path: '/order', title: '订单汇聚', icon: 'Tickets', roles: ['ADMIN', 'WAVE_PLANNER'] },
  { path: '/wave', title: '波次管理', icon: 'Files', roles: ['ADMIN', 'WAVE_PLANNER'] },
  { path: '/pick', title: '拣货执行', icon: 'ShoppingCart', roles: ['ADMIN', 'PICKER'] },
  { path: '/review', title: '复核打包', icon: 'Finished', roles: ['ADMIN', 'CHECKER'] },
  { path: '/outbound', title: '出库交接', icon: 'Van', roles: ['ADMIN', 'CHECKER', 'WAVE_PLANNER'] },
  { path: '/shortage', title: '缺货处理', icon: 'Warning', roles: ['ADMIN', 'WAVE_PLANNER', 'PICKER'] },
  { path: '/error', title: '差错统计', icon: 'WarningFilled', roles: ['ADMIN', 'WAVE_PLANNER', 'CHECKER'] },
  { path: '/efficiency', title: '效率复盘', icon: 'TrendCharts', roles: ['ADMIN', 'WAVE_PLANNER'] },
  { path: '/sku', title: 'SKU 管理', icon: 'Goods', roles: ['ADMIN', 'WAVE_PLANNER'] },
  { path: '/inventory', title: '库存查询', icon: 'Box', roles: ['ADMIN', 'WAVE_PLANNER'] },
  { path: '/user', title: '用户管理', icon: 'UserFilled', roles: ['ADMIN'] },
]

const store = useUserStore()
const route = useRoute()
const router = useRouter()

const menus = computed(() => allMenus.filter((m) => store.hasRole(m.roles)))
const activePath = computed(() => route.path)
const currentTitle = computed(() => {
  const hit = allMenus.find((m) => m.path === route.path)
  return hit ? hit.title : '工作台'
})

async function onLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await logout()
  } catch {
    // 忽略
  }
  store.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background: #1f2d3d;
  overflow-x: hidden;
}

.brand {
  height: 58px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-logo {
  background: #409eff;
  padding: 3px 8px;
  border-radius: 6px;
  font-weight: 800;
  letter-spacing: 1px;
  font-size: 13px;
}

.brand-text {
  font-size: 14px;
  font-weight: 600;
}

.menu {
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nickname {
  color: #606266;
}

.main {
  background: #f5f7fa;
  padding: 0;
}
</style>
