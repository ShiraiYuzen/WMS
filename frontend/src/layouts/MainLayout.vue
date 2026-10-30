<template>
  <div class="layout">
    <!-- ======================= 侧边栏 ======================= -->
    <aside class="sidebar" :class="{ collapsed: app.sidebarCollapsed }">
      <div class="brand">
        <div class="brand-logo">
          <el-icon><Box /></el-icon>
        </div>
        <transition name="fade">
          <div v-show="!app.sidebarCollapsed" class="brand-text">
            <div class="brand-title">拣货与波次管理</div>
            <div class="brand-sub">Warehouse Picking Platform</div>
          </div>
        </transition>
      </div>

      <el-scrollbar class="menu-wrap">
        <template v-for="group in menuGroups" :key="group.name">
          <div v-if="group.items.length" class="menu-group">
            <div v-show="!app.sidebarCollapsed" class="menu-group-title">{{ group.name }}</div>
            <el-menu
              :default-active="activeMenu"
              :collapse="app.sidebarCollapsed"
              :collapse-transition="false"
              background-color="transparent"
              text-color="#cbd5e1"
              active-text-color="#ffffff"
              router
            >
              <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
                <el-icon><component :is="item.icon" /></el-icon>
                <template #title>
                  <span>{{ item.title }}</span>
                  <el-badge
                    v-if="badgeOf(item.path)"
                    :value="badgeOf(item.path)"
                    :max="99"
                    class="menu-badge"
                  />
                </template>
              </el-menu-item>
            </el-menu>
          </div>
        </template>
      </el-scrollbar>

      <div class="sidebar-foot">
        <el-tooltip content="收起 / 展开菜单" placement="right">
          <div class="collapse-btn" @click="app.toggleSidebar()">
            <el-icon>
              <component :is="app.sidebarCollapsed ? 'Expand' : 'Fold'" />
            </el-icon>
            <span v-show="!app.sidebarCollapsed">收起菜单</span>
          </div>
        </el-tooltip>
      </div>
    </aside>

    <!-- ======================= 右侧主区 ======================= -->
    <div class="main">
      <!-- 顶栏 -->
      <header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>仓储作业</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentGroup">{{ currentGroup }}</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <!-- Mock 提示 -->
          <el-tag v-if="useMock" type="warning" effect="light" size="small" class="mock-tag">
            <el-icon><InfoFilled /></el-icon>
            <span>演示数据</span>
          </el-tag>

          <!-- 待办概览 -->
          <el-popover placement="bottom-end" :width="260" trigger="click">
            <template #reference>
              <div class="header-icon">
                <el-badge :value="totalTodo" :max="99" :hidden="totalTodo === 0">
                  <el-icon><Bell /></el-icon>
                </el-badge>
              </div>
            </template>
            <div class="todo-panel">
              <div class="todo-title">待办事项</div>
              <div
                v-for="t in todoList"
                :key="t.path"
                class="todo-item"
                @click="router.push(t.path)"
              >
                <span>{{ t.name }}</span>
                <el-tag :type="t.count > 0 ? 'danger' : 'info'" size="small" effect="plain">
                  {{ t.count }}
                </el-tag>
              </div>
            </div>
          </el-popover>

          <!-- 角色切换（演示用，接入登录后替换为真实用户信息） -->
          <el-dropdown @command="onSwitchRole">
            <div class="user-box">
              <el-avatar :size="30" class="user-avatar">
                {{ app.currentUser.employeeName.slice(0, 1) }}
              </el-avatar>
              <div class="user-meta">
                <div class="user-name">{{ app.currentUser.employeeName }}</div>
                <div class="user-role">{{ app.currentUser.roleDesc }}</div>
              </div>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <span style="font-size: 12px; color: #94a3b8">切换角色以体验不同视角</span>
                </el-dropdown-item>
                <el-dropdown-item
                  v-for="r in app.ROLE_OPTIONS"
                  :key="r.employeeNo"
                  :command="r.employeeNo"
                  :class="{ 'is-active-role': r.employeeNo === app.currentUser.employeeNo }"
                >
                  <el-icon><component :is="roleIcon(r.role)" /></el-icon>
                  <span>{{ r.employeeName }} · {{ r.roleDesc }}</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="content">
        <router-view v-slot="{ Component, route: r }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" :key="r.fullPath" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useDictStore } from '@/stores/dict'
import { dashboardApi } from '@/api'
import { USE_MOCK } from '@/api/request'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const dict = useDictStore()

const useMock = USE_MOCK

/* ------------------------------ 菜单 ------------------------------ */

const GROUP_ORDER = ['总览', '作业闭环', '复盘与配置']

/** 从路由表推导菜单，按角色过滤 */
const menuGroups = computed(() => {
  const layout = router.options.routes.find((r) => r.path === '/')
  const children = (layout?.children || []).filter((c) => !c.meta?.hidden)

  const groups = GROUP_ORDER.map((name) => ({
    name,
    items: children
      .filter((c) => (c.meta?.group || '其他') === name)
      .filter((c) => app.canAccess(c.meta?.roles))
      .map((c) => ({
        path: `/${c.path}`,
        title: c.meta.title,
        icon: c.meta.icon || 'Menu'
      }))
  })).filter((g) => g.items.length)

  return groups
})

const activeMenu = computed(() => route.meta?.activeMenu || route.path)

const currentGroup = computed(() => route.meta?.group || '')

/* ------------------------------ 待办与红点 ------------------------------ */

const badge = computed(() => app.badge)

function badgeOf(path) {
  const map = {
    '/order': badge.value.order,
    '/wave': badge.value.wave,
    '/pick': badge.value.pick,
    '/shortage': badge.value.shortage,
    '/review': badge.value.review,
    '/handover': badge.value.handover
  }
  return map[path] || 0
}

const todoList = computed(() => [
  { name: '待汇聚订单', count: badge.value.order || 0, path: '/order' },
  { name: '待生成波次', count: badge.value.wave || 0, path: '/wave' },
  { name: '待处理缺货', count: badge.value.shortage || 0, path: '/shortage' },
  { name: '待拣货任务', count: badge.value.pick || 0, path: '/pick' },
  { name: '待复核订单', count: badge.value.review || 0, path: '/review' },
  { name: '待交接波次', count: badge.value.handover || 0, path: '/handover' }
])

const totalTodo = computed(() => todoList.value.reduce((s, t) => s + t.count, 0))

async function refreshBadge() {
  try {
    const data = await dashboardApi.summary()
    const todos = data.todos || []
    const find = (name) => todos.find((t) => t.name === name)?.count || 0
    app.setBadge({
      order: find('待汇聚订单'),
      wave: find('待生成波次订单'),
      pick: find('待拣货任务'),
      shortage: find('待处理缺货'),
      review: find('待复核订单'),
      handover: find('待交接波次')
    })
  } catch {
    /* 忽略：不影响主流程 */
  }
}

/* ------------------------------ 角色 ------------------------------ */

function roleIcon(role) {
  return {
    WAVE_OPERATOR: 'Grid',
    PICKER: 'Van',
    REVIEWER: 'DocumentChecked',
    ADMIN: 'UserFilled'
  }[role] || 'User'
}

function onSwitchRole(employeeNo) {
  app.switchRole(employeeNo)
  // 角色变化后菜单可见性会变，若当前页无权访问则回首页
  const target = router.resolve(route.fullPath)
  if (!app.canAccess(target.meta?.roles)) {
    router.push('/dashboard')
  }
  refreshBadge()
}

/* ------------------------------ 初始化 ------------------------------ */

onMounted(() => {
  dict.load()
  refreshBadge()
  // 全局挂一个刷新函数，页面操作后可以调用以更新红点
  window.__wmsRefreshBadge = refreshBadge
})
</script>

<style scoped lang="scss">
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ============================ 侧边栏 ============================ */

.sidebar {
  width: $sidebar-width;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #0f172a 0%, #16233c 100%);
  transition: width 0.22s ease;

  &.collapsed {
    width: $sidebar-width-collapsed;
  }
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: $header-height;
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

.menu-group {
  padding: 6px 0;
}

.menu-group-title {
  padding: 8px 20px 4px;
  font-size: 11px;
  color: #64748b;
  letter-spacing: 1px;
}

:deep(.el-menu) {
  border-right: none;
}

:deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  margin: 2px 8px;
  border-radius: 7px;
  font-size: 13.5px;

  &:hover {
    background: rgba(59, 130, 246, 0.14) !important;
    color: #e2e8f0 !important;
  }

  &.is-active {
    background: linear-gradient(90deg, #2563eb, #3b82f6) !important;
    color: #fff !important;
    box-shadow: 0 2px 10px rgba(37, 99, 235, 0.36);

    .el-icon {
      color: #fff;
    }
  }

  .el-icon {
    font-size: 16px;
    color: #94a3b8;
  }
}

.menu-badge {
  margin-left: 8px;

  :deep(.el-badge__content) {
    border: none;
    background: #ef4444;
    transform: translateY(-1px);
  }
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

  &:hover {
    background: rgba(148, 163, 184, 0.12);
    color: #e2e8f0;
  }
}

/* ============================ 主区 ============================ */

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: $bg-page;
}

.header {
  height: $header-height;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid $border-base;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  z-index: 10;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.mock-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.header-icon {
  cursor: pointer;
  color: $text-secondary;
  font-size: 18px;
  display: flex;
  align-items: center;
  padding: 4px;

  &:hover {
    color: $color-primary;
  }
}

.todo-panel {
  padding: 2px 0;
}

.todo-title {
  font-size: 12px;
  color: $text-placeholder;
  padding: 2px 4px 8px;
  border-bottom: 1px solid $border-base;
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

  &:hover {
    background: #f1f5f9;
  }
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

  &:hover {
    background: #f1f5f9;
  }
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
  color: $text-primary;
}

.user-role {
  font-size: 11px;
  color: $text-placeholder;
}

.user-arrow {
  font-size: 12px;
  color: $text-placeholder;
}

:deep(.is-active-role) {
  color: $color-primary;
  font-weight: 600;
  background: #eff6ff;
}

/* ============================ 内容区 ============================ */

.content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
