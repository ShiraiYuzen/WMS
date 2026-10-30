<template>
  <div class="layout">
    <!-- ======================= 侧边栏 ======================= -->
    <aside class="sidebar" :class="{ collapsed }">
      <div class="brand">
        <div class="brand-logo">
          <el-icon><Box /></el-icon>
        </div>
        <div v-show="!collapsed" class="brand-text">
          <div class="brand-title">拣货与波次管理</div>
          <div class="brand-sub">Warehouse Picking Platform</div>
        </div>
      </div>

      <el-scrollbar class="menu-wrap">
        <el-menu :default-active="activePath" :collapse="collapsed" :collapse-transition="false"
                 background-color="transparent" text-color="#cbd5e1" active-text-color="#ffffff" router>
          <template v-for="g in menuGroups" :key="g.name">
            <el-menu-item-group>
              <template #title>
                <span v-show="!collapsed" class="group-title">{{ g.name }}</span>
              </template>
              <el-menu-item v-for="m in g.items" :key="m.path" :index="m.path">
                <el-icon><component :is="m.icon" /></el-icon>
                <template #title>{{ m.title }}</template>
              </el-menu-item>
            </el-menu-item-group>
          </template>
        </el-menu>
      </el-scrollbar>

      <div class="sidebar-foot">
        <div class="collapse-btn" @click="toggleCollapse">
          <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
          <span v-show="!collapsed">收起菜单</span>
        </div>
      </div>
    </aside>

    <!-- ======================= 右侧主区 ======================= -->
    <div class="main">
      <header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>仓储作业</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentGroup">{{ currentGroup }}</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <!-- 待办概览 -->
          <el-popover placement="bottom-end" :width="250" trigger="click">
            <template #reference>
              <div class="header-icon">
                <el-badge :value="totalTodo" :max="99" :hidden="totalTodo === 0">
                  <el-icon><Bell /></el-icon>
                </el-badge>
              </div>
            </template>
            <div class="todo-panel">
              <div class="todo-title">待办事项</div>
              <div v-for="t in todoList" :key="t.path" class="todo-item" @click="router.push(t.path)">
                <span>{{ t.name }}</span>
                <el-tag :type="t.count > 0 ? 'danger' : 'info'" size="small" effect="plain">{{ t.count }}</el-tag>
              </div>
            </div>
          </el-popover>

          <!-- 用户 -->
          <el-dropdown @command="onCommand">
            <div class="user-box">
              <el-avatar :size="30" class="user-avatar">{{ avatarChar }}</el-avatar>
              <div class="user-meta">
                <div class="user-name">{{ store.nickname || store.username }}</div>
                <div class="user-role">{{ store.roleLabel }}</div>
              </div>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <span style="font-size: 12px; color: #94a3b8">当前角色：{{ store.roleLabel }}</span>
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  <span>退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <div class="page-hero">
          <h2 class="hero-title">{{ currentTitle }}</h2>
          <p v-if="currentDesc" class="hero-desc">{{ currentDesc }}</p>
        </div>
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <keep-alive :max="6">
              <component :is="Component" />
            </keep-alive>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { logout } from '@/api/auth'
import { dashboard } from '@/api/stats'
import { useUserStore } from '@/store/user'

interface MenuItem {
  path: string
  title: string
  icon: string
  roles: string[]
  group: string
}

const GROUP_ORDER = ['总览', '作业闭环', '复盘与配置', '系统']

const allMenus: MenuItem[] = [
  { path: '/dashboard', title: '工作台', icon: 'DataAnalysis', roles: [], group: '总览' },
  { path: '/order', title: '订单汇聚', icon: 'Tickets', roles: ['ADMIN', 'WAVE_PLANNER'], group: '作业闭环' },
  { path: '/wave', title: '波次管理', icon: 'Files', roles: ['ADMIN', 'WAVE_PLANNER'], group: '作业闭环' },
  { path: '/pick', title: '拣货执行', icon: 'ShoppingCart', roles: ['ADMIN', 'PICKER'], group: '作业闭环' },
  { path: '/review', title: '复核打包', icon: 'Finished', roles: ['ADMIN', 'CHECKER'], group: '作业闭环' },
  { path: '/outbound', title: '出库交接', icon: 'Van', roles: ['ADMIN', 'CHECKER', 'WAVE_PLANNER'], group: '作业闭环' },
  { path: '/shortage', title: '缺货处理', icon: 'Warning', roles: ['ADMIN', 'WAVE_PLANNER', 'PICKER'], group: '作业闭环' },
  { path: '/error', title: '差错统计', icon: 'WarningFilled', roles: ['ADMIN', 'WAVE_PLANNER', 'CHECKER'], group: '复盘与配置' },
  { path: '/efficiency', title: '效率复盘', icon: 'TrendCharts', roles: ['ADMIN', 'WAVE_PLANNER'], group: '复盘与配置' },
  { path: '/sku', title: 'SKU 管理', icon: 'Goods', roles: ['ADMIN', 'WAVE_PLANNER'], group: '复盘与配置' },
  { path: '/location', title: '库位管理', icon: 'Coordinate', roles: ['ADMIN', 'WAVE_PLANNER'], group: '复盘与配置' },
  { path: '/inventory', title: '库存查询', icon: 'Box', roles: ['ADMIN', 'WAVE_PLANNER'], group: '复盘与配置' },
  { path: '/carrier', title: '承运商管理', icon: 'Ship', roles: ['ADMIN', 'WAVE_PLANNER'], group: '复盘与配置' },
  { path: '/user', title: '用户管理', icon: 'UserFilled', roles: ['ADMIN'], group: '系统' },
  { path: '/log', title: '操作日志', icon: 'Document', roles: ['ADMIN'], group: '系统' },
]

const DESCRIPTIONS: Record<string, string> = {
  '/dashboard': '全局作业概览：各环节待办、平台分布与近 7 天趋势',
  '/order': '订单汇聚池：查询、新建与取消待分波订单',
  '/wave': '按策略聚合订单生成波次，校验并锁定库存',
  '/pick': '按波次执行拣货，实拣不足自动登记缺货',
  '/review': '逐单复核，登记差错并放行至出库',
  '/outbound': '确认承运商与交接人，扣减库存完成出库',
  '/shortage': '处理拣货环节产生的缺货记录',
  '/error': '差错率、差错类型分布与按人归因',
  '/efficiency': '拣货员人效、复核异常率与整体作业指标',
  '/sku': '商品主数据与默认拣货库位维护',
  '/location': '仓库库位档案：库区、类型、容量与启停用',
  '/inventory': '按 SKU 与库位查看总库存 / 锁定 / 可用',
  '/carrier': '承运商档案与启停用管理',
  '/user': '账号、角色与密码管理',
  '/log': '所有写操作自动留痕，可按模块追溯',
}

const store = useUserStore()
const route = useRoute()
const router = useRouter()

const collapsed = ref(localStorage.getItem('wms_sidebar_collapsed') === '1')

const menus = computed(() => allMenus.filter((m) => store.hasRole(m.roles)))

const menuGroups = computed(() => {
  const groups: { name: string; items: MenuItem[] }[] = []
  for (const name of GROUP_ORDER) {
    const items = menus.value.filter((m) => m.group === name)
    if (items.length) groups.push({ name, items })
  }
  return groups
})

const activePath = computed(() => route.path)
const currentTitle = computed(() => allMenus.find((m) => m.path === route.path)?.title || '工作台')
const currentGroup = computed(() => allMenus.find((m) => m.path === route.path)?.group || '')
const currentDesc = computed(() => DESCRIPTIONS[route.path] || '')
const avatarChar = computed(() => (store.nickname || store.username || 'W').slice(0, 1))

const badge = ref<Record<string, number>>({})
const todoList = computed(() => [
  { name: '待分波订单', count: badge.value.orderPending || 0, path: '/order' },
  { name: '待拣货波次', count: badge.value.waveTodo || 0, path: '/wave' },
  { name: '拣货中波次', count: badge.value.wavePicking || 0, path: '/pick' },
  { name: '待复核波次', count: badge.value.waveToReview || 0, path: '/review' },
  { name: '待出库波次', count: badge.value.waveToOutbound || 0, path: '/outbound' },
  { name: '待处理缺货', count: badge.value.shortagePending || 0, path: '/shortage' },
])
const totalTodo = computed(() => todoList.value.reduce((s, t) => s + t.count, 0))

function toggleCollapse() {
  collapsed.value = !collapsed.value
  localStorage.setItem('wms_sidebar_collapsed', collapsed.value ? '1' : '0')
}

async function refreshBadge() {
  try {
    const res = await dashboard()
    badge.value = res.data || {}
  } catch {
    /* 忽略 */
  }
}

async function onCommand(cmd: string) {
  if (cmd !== 'logout') return
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await logout()
  } catch {
    /* 忽略 */
  }
  store.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

onMounted(refreshBadge)
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ============================ 侧边栏 ============================ */
.sidebar {
  width: 226px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #0f172a 0%, #16233c 100%);
  transition: width 0.22s ease;
}

.sidebar.collapsed {
  width: 64px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 58px;
  padding: 0 16px;
  flex-shrink: 0;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  overflow: hidden;
}

.brand-logo {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #fff;
  font-size: 17px;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.4);
}

.brand-text {
  white-space: nowrap;
}

.brand-title {
  font-size: 14px;
  font-weight: 600;
  color: #f1f5f9;
  letter-spacing: 0.3px;
}

.brand-sub {
  font-size: 10px;
  color: #64748b;
  letter-spacing: 0.2px;
  transform: scale(0.92);
  transform-origin: left;
}

.menu-wrap {
  flex: 1;
  min-height: 0;
}

.group-title {
  font-size: 11px;
  color: #64748b;
  letter-spacing: 1px;
}

.sidebar :deep(.el-menu) {
  border-right: none;
}

.sidebar :deep(.el-menu-item-group__title) {
  padding: 10px 20px 4px;
  line-height: 1.4;
}

.sidebar :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  margin: 2px 8px;
  border-radius: 7px;
  font-size: 13.5px;
}

.sidebar :deep(.el-menu-item:hover) {
  background: rgba(59, 130, 246, 0.14) !important;
  color: #e2e8f0 !important;
}

.sidebar :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, #2563eb, #3b82f6) !important;
  color: #fff !important;
  box-shadow: 0 2px 10px rgba(37, 99, 235, 0.36);
}

.sidebar :deep(.el-menu-item .el-icon) {
  font-size: 16px;
  color: #94a3b8;
}

.sidebar :deep(.el-menu-item.is-active .el-icon) {
  color: #fff;
}

.sidebar-foot {
  flex-shrink: 0;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  padding: 8px;
}

.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 34px;
  border-radius: 7px;
  color: #94a3b8;
  font-size: 12.5px;
  cursor: pointer;
  transition: all 0.16s;
}

.collapse-btn:hover {
  background: rgba(148, 163, 184, 0.12);
  color: #e2e8f0;
}

/* ============================ 主区 ============================ */
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--wms-bg-page);
}

.header {
  height: 58px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid var(--wms-border);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  z-index: 10;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-icon {
  cursor: pointer;
  color: var(--wms-text-secondary);
  font-size: 18px;
  display: flex;
  align-items: center;
  padding: 4px;
}

.header-icon:hover {
  color: var(--wms-primary);
}

.todo-panel {
  padding: 2px 0;
}

.todo-title {
  font-size: 12px;
  color: var(--wms-text-placeholder);
  padding: 2px 4px 8px;
  border-bottom: 1px solid var(--wms-border);
  margin-bottom: 6px;
}

.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 6px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.todo-item:hover {
  background: #f1f5f9;
}

.user-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border-radius: 20px;
  cursor: pointer;
  transition: background 0.16s;
  outline: none;
}

.user-box:hover {
  background: #f1f5f9;
}

.user-avatar {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.user-meta {
  line-height: 1.25;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--wms-text-primary);
}

.user-role {
  font-size: 11px;
  color: var(--wms-text-placeholder);
}

.user-arrow {
  font-size: 12px;
  color: var(--wms-text-placeholder);
}

/* ============================ 内容区 ============================ */
.content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.page-hero {
  padding: 18px 18px 0;
}

.hero-title {
  margin: 0;
  font-size: 19px;
  font-weight: 600;
  color: var(--wms-text-primary);
  letter-spacing: 0.2px;
}

.hero-desc {
  margin: 5px 0 0;
  font-size: 12.5px;
  color: var(--wms-text-secondary);
  line-height: 1.6;
}
</style>
