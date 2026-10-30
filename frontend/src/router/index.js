import { createRouter, createWebHashHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

/**
 * 路由表。
 *
 * 采用 hash 模式：内部系统部署简单，不需要服务端配 history fallback。
 * 若后续需要 SEO 或更漂亮的 URL，换成 createWebHistory 即可（需服务端配合）。
 *
 * meta 字段约定：
 *   title    —— 面包屑与浏览器标题
 *   icon     —— 侧边栏图标名（Element Plus 图标组件名）
 *   group    —— 侧边栏分组标题
 *   roles    —— 可见角色（用于按角色过滤菜单），不写表示全部可见
 *   hidden   —— 不在侧边栏展示（详情页等）
 */
const routes = [
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '作业看板', icon: 'Odometer', group: '总览' }
      },

      /* ---------------- 订单汇聚 ---------------- */
      {
        path: 'order',
        name: 'OrderList',
        component: () => import('@/views/order/index.vue'),
        meta: { title: '订单池', icon: 'Tickets', group: '作业闭环', roles: ['WAVE_OPERATOR', 'ADMIN'] }
      },

      /* ---------------- 波次生成 ---------------- */
      {
        path: 'wave',
        name: 'WaveList',
        component: () => import('@/views/wave/index.vue'),
        meta: { title: '波次管理', icon: 'Grid', group: '作业闭环', roles: ['WAVE_OPERATOR', 'ADMIN'] }
      },
      {
        path: 'wave/detail/:waveNo',
        name: 'WaveDetail',
        component: () => import('@/views/wave/detail.vue'),
        meta: { title: '波次详情', hidden: true, activeMenu: '/wave' }
      },

      /* ---------------- 拣货执行 ---------------- */
      {
        path: 'pick',
        name: 'PickList',
        component: () => import('@/views/pick/index.vue'),
        meta: { title: '拣货任务', icon: 'Van', group: '作业闭环', roles: ['PICKER', 'WAVE_OPERATOR', 'ADMIN'] }
      },
      {
        path: 'pick/execute/:taskNo',
        name: 'PickExecute',
        component: () => import('@/views/pick/execute.vue'),
        meta: { title: '拣货作业', hidden: true, activeMenu: '/pick' }
      },

      /* ---------------- 缺货处理 ---------------- */
      {
        path: 'shortage',
        name: 'ShortageList',
        component: () => import('@/views/shortage/index.vue'),
        meta: { title: '缺货处理', icon: 'Warning', group: '作业闭环', roles: ['WAVE_OPERATOR', 'ADMIN'] }
      },

      /* ---------------- 复核打包 ---------------- */
      {
        path: 'review',
        name: 'ReviewList',
        component: () => import('@/views/review/index.vue'),
        meta: { title: '复核打包', icon: 'DocumentChecked', group: '作业闭环', roles: ['REVIEWER', 'ADMIN'] }
      },
      {
        path: 'review/execute/:reviewNo',
        name: 'ReviewExecute',
        component: () => import('@/views/review/execute.vue'),
        meta: { title: '复核作业', hidden: true, activeMenu: '/review' }
      },

      /* ---------------- 出库交接 ---------------- */
      {
        path: 'handover',
        name: 'HandoverList',
        component: () => import('@/views/handover/index.vue'),
        meta: { title: '出库交接', icon: 'Box', group: '作业闭环', roles: ['REVIEWER', 'ADMIN'] }
      },

      /* ---------------- 效率复盘 ---------------- */
      {
        path: 'analytics',
        name: 'Analytics',
        component: () => import('@/views/analytics/index.vue'),
        meta: { title: '效率复盘', icon: 'TrendCharts', group: '复盘与配置', roles: ['ADMIN', 'WAVE_OPERATOR'] }
      },

      /* ---------------- 基础数据 ---------------- */
      {
        path: 'base/sku',
        name: 'BaseSku',
        component: () => import('@/views/base/sku.vue'),
        meta: { title: '商品档案', icon: 'Goods', group: '复盘与配置' }
      },
      {
        path: 'base/location',
        name: 'BaseLocation',
        component: () => import('@/views/base/location.vue'),
        meta: { title: '库位管理', icon: 'Coordinate', group: '复盘与配置' }
      },
      {
        path: 'base/employee',
        name: 'BaseEmployee',
        component: () => import('@/views/base/employee.vue'),
        meta: { title: '人员管理', icon: 'User', group: '复盘与配置' }
      },
      {
        path: 'base/carrier',
        name: 'BaseCarrier',
        component: () => import('@/views/base/carrier.vue'),
        meta: { title: '承运商', icon: 'Truck', group: '复盘与配置' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { hidden: true }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.afterEach((to) => {
  const base = import.meta.env.VITE_APP_TITLE || '仓库拣货与波次管理系统'
  document.title = to.meta?.title ? `${to.meta.title} · ${base}` : base
})

export default router
