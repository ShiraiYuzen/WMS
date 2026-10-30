import { createRouter, createWebHistory } from 'vue-router'

export interface MenuRoute {
  path: string
  name: string
  title: string
  icon: string
  roles: string[]
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '工作台', icon: 'DataAnalysis', roles: [] },
      },
      {
        path: 'order',
        name: 'OrderManage',
        component: () => import('@/views/OrderManage.vue'),
        meta: { title: '订单汇聚', icon: 'Tickets', roles: ['ADMIN', 'WAVE_PLANNER'] },
      },
      {
        path: 'wave',
        name: 'WaveManage',
        component: () => import('@/views/WaveManage.vue'),
        meta: { title: '波次管理', icon: 'Files', roles: ['ADMIN', 'WAVE_PLANNER'] },
      },
      {
        path: 'pick',
        name: 'PickWork',
        component: () => import('@/views/PickWork.vue'),
        meta: { title: '拣货执行', icon: 'ShoppingCart', roles: ['ADMIN', 'PICKER'] },
      },
      {
        path: 'review',
        name: 'ReviewWork',
        component: () => import('@/views/ReviewWork.vue'),
        meta: { title: '复核打包', icon: 'Finished', roles: ['ADMIN', 'CHECKER'] },
      },
      {
        path: 'outbound',
        name: 'OutboundManage',
        component: () => import('@/views/OutboundManage.vue'),
        meta: { title: '出库交接', icon: 'Van', roles: ['ADMIN', 'CHECKER', 'WAVE_PLANNER'] },
      },
      {
        path: 'shortage',
        name: 'ShortageManage',
        component: () => import('@/views/ShortageManage.vue'),
        meta: { title: '缺货处理', icon: 'Warning', roles: ['ADMIN', 'WAVE_PLANNER', 'PICKER'] },
      },
      {
        path: 'error',
        name: 'ErrorStats',
        component: () => import('@/views/ErrorStats.vue'),
        meta: { title: '差错统计', icon: 'WarningFilled', roles: ['ADMIN', 'WAVE_PLANNER', 'CHECKER'] },
      },
      {
        path: 'efficiency',
        name: 'EfficiencyReport',
        component: () => import('@/views/EfficiencyReport.vue'),
        meta: { title: '效率复盘', icon: 'TrendCharts', roles: ['ADMIN', 'WAVE_PLANNER'] },
      },
      {
        path: 'sku',
        name: 'SkuManage',
        component: () => import('@/views/SkuManage.vue'),
        meta: { title: 'SKU 管理', icon: 'Goods', roles: ['ADMIN', 'WAVE_PLANNER'] },
      },
      {
        path: 'inventory',
        name: 'InventoryManage',
        component: () => import('@/views/InventoryManage.vue'),
        meta: { title: '库存查询', icon: 'Box', roles: ['ADMIN', 'WAVE_PLANNER'] },
      },
      {
        path: 'user',
        name: 'UserManage',
        component: () => import('@/views/UserManage.vue'),
        meta: { title: '用户管理', icon: 'UserFilled', roles: ['ADMIN'] },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = localStorage.getItem('wms_token')
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token) {
    return '/dashboard'
  }
  const roles = to.meta.roles as string[] | undefined
  const role = localStorage.getItem('wms_role') || 'PICKER'
  if (roles && roles.length > 0 && !roles.includes(role)) {
    return { path: '/dashboard' }
  }
  document.title = to.meta.title ? `${String(to.meta.title)} - 仓库拣货与波次管理系统` : '仓库拣货与波次管理系统'
  return true
})

export default router
