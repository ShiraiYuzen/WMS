import { get, post } from './request'

/**
 * 各业务模块接口定义。
 *
 * 这里的方法签名与后端 Controller 一一对应，
 * 便于接库后逐条比对（后端接口清单见项目根目录 README.md）。
 */

/* ============================== 作业看板 ============================== */

export const dashboardApi = {
  summary: () => get('/dashboard/summary'),
  trend: () => get('/dashboard/trend')
}

/* ============================== 订单汇聚 ============================== */

export const orderApi = {
  page: (params) => get('/order/page', params),
  detail: (orderNo) => get(`/order/${orderNo}`),
  countByStatus: () => get('/order/count-by-status'),
  pool: (orderNos) => post('/order/pool', { orderNos }),
  poolAll: () => post('/order/pool-all'),
  cancel: (orderNo) => post(`/order/${orderNo}/cancel`)
}

/* ============================== 波次生成 ============================== */

export const waveApi = {
  page: (params) => get('/wave/page', params),
  detail: (waveNo) => get(`/wave/${waveNo}`),
  pickerWorkload: () => get('/wave/picker-workload'),
  preview: (data) => post('/wave/preview', data),
  generate: (data) => post('/wave/generate', data),
  assign: (data) => post('/wave/assign', data),
  close: (waveNo) => post(`/wave/${waveNo}/close`),
  cancel: (waveNo) => post(`/wave/${waveNo}/cancel`)
}

/* ============================== 拣货执行 ============================== */

export const pickApi = {
  page: (params) => get('/pick/page', params),
  detail: (taskNo) => get(`/pick/${taskNo}`),
  myTasks: (pickerNo) => get('/pick/my-tasks', { pickerNo }),
  start: (taskNo, pickerNo) => post(`/pick/${taskNo}/start`, null, { pickerNo }),
  confirmItem: (data) => post('/pick/confirm-item', data),
  reportShortage: (data) => post('/pick/report-shortage', data),
  finish: (taskNo) => post(`/pick/${taskNo}/finish`)
}

/* ============================== 缺货处理 ============================== */

export const shortageApi = {
  page: (params) => get('/shortage/page', params),
  detail: (id) => get(`/shortage/${id}`),
  pendingCount: () => get('/shortage/pending-count'),
  reasonDistribution: () => get('/shortage/reason-distribution'),
  handle: (data) => post('/shortage/handle', data),
  close: (id) => post(`/shortage/${id}/close`)
}

/* ============================== 复核打包 ============================== */

export const reviewApi = {
  page: (params) => get('/review/page', params),
  detail: (reviewNo) => get(`/review/${reviewNo}`),
  pendingWaves: () => get('/review/pending-waves'),
  claim: (waveNo, reviewerNo, reviewerName) =>
    post('/review/claim', null, { waveNo, reviewerNo, reviewerName }),
  submit: (data) => post('/review/submit', data)
}

/* ============================== 出库交接 ============================== */

export const handoverApi = {
  page: (params) => get('/handover/page', params),
  detail: (handoverNo) => get(`/handover/${handoverNo}`),
  reviewableWaves: (carrierCode) => get('/handover/reviewable-waves', { carrierCode }),
  create: (data) => post('/handover/create', data),
  confirm: (data) => post('/handover/confirm', data)
}

/* ============================== 效率复盘 ============================== */

export const analyticsApi = {
  overview: (params) => get('/analytics/overview', params),
  employeeEfficiency: (params) => get('/analytics/employee-efficiency', params),
  errorRate: (params) => get('/analytics/error-rate', params),
  trend: (params) => get('/analytics/trend', params),
  shortageDistribution: (params) => get('/analytics/shortage-distribution', params)
}

/* ============================== 基础数据 ============================== */

export const baseApi = {
  enums: () => get('/base/enums'),
  skuPage: (params) => get('/base/sku/page', params),
  skuCategories: () => get('/base/sku/categories'),
  locationPage: (params) => get('/base/location/page', params),
  zones: () => get('/base/location/zones'),
  employeePage: (params) => get('/base/employee/page', params),
  employeesByRole: (role) => get('/base/employee/by-role', { role }),
  teams: () => get('/base/employee/teams'),
  carriers: () => get('/base/carrier/list')
}

/* ============================== 作业追溯 ============================== */

export const logApi = {
  page: (params) => get('/log/page', params),
  trace: (bizNo) => get('/log/trace', { bizNo })
}

export default {
  dashboardApi,
  orderApi,
  waveApi,
  pickApi,
  shortageApi,
  reviewApi,
  handoverApi,
  analyticsApi,
  baseApi,
  logApi
}
