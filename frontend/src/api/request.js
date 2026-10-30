import axios from 'axios'
import { ElMessage } from 'element-plus'
import { mockAdapter } from '@/mock/index.js'

/**
 * Axios 实例。
 *
 * 【关键设计】Mock 切换
 * 当 VITE_USE_MOCK=true 时，把 axios 的 adapter 换成前端内置的 mock 适配器，
 * 请求不会发出网络，而是直接在前端内存里完成业务运算并返回。
 * 这样做的意义是：**业务代码完全无感**——
 * 后端接好数据库后，只需把 .env 里的 VITE_USE_MOCK 改成 false，
 * 所有页面自动切换到真实接口，不需要改动任何 api/views 代码。
 */
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: Number(import.meta.env.VITE_REQUEST_TIMEOUT || 15000),
  headers: { 'Content-Type': 'application/json;charset=UTF-8' }
})

export const USE_MOCK = String(import.meta.env.VITE_USE_MOCK) === 'true'

if (USE_MOCK) {
  service.defaults.adapter = mockAdapter
  console.info(
    '%c[WMS] 当前运行在 Mock 演示模式%c\n数据来自前端内存，刷新页面即复位。\n后端就绪后把 .env.development 的 VITE_USE_MOCK 改为 false 即可切换。',
    'color:#2563eb;font-weight:600',
    'color:#64748b'
  )
}

/* -------------------------------------------------------------------------- */
/* 请求拦截                                                                    */
/* -------------------------------------------------------------------------- */

service.interceptors.request.use(
  (config) => {
    // 预留：接入登录后在这里带上 token
    const token = localStorage.getItem('wms_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // GET 请求加时间戳，避免浏览器缓存
    if (config.method === 'get') {
      config.params = { ...(config.params || {}), _t: Date.now() }
    }
    return config
  },
  (error) => Promise.reject(error)
)

/* -------------------------------------------------------------------------- */
/* 响应拦截                                                                    */
/* -------------------------------------------------------------------------- */

service.interceptors.response.use(
  (response) => {
    const res = response.data

    // 后端统一响应体：{ code, message, data, timestamp }
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 0) {
        return res
      }
      ElMessage.error(res.message || '操作失败')
      return Promise.reject(Object.assign(new Error(res.message || '操作失败'), { code: res.code }))
    }
    return res
  },
  (error) => {
    let message = '网络异常，请稍后重试'

    if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message)) {
      message = '请求超时，请检查网络或后端服务'
    } else if (error.response) {
      const { status } = error.response
      if (status === 401) message = '登录已失效，请重新登录'
      else if (status === 403) message = '没有该操作权限'
      else if (status === 404) message = '接口不存在'
      else if (status >= 500) message = '服务端异常，请联系管理员'
    }

    ElMessage.error(message)
    return Promise.reject(error)
  }
)

/* -------------------------------------------------------------------------- */
/* 便捷方法：直接返回业务数据（已剥离 R 外壳）                                    */
/* -------------------------------------------------------------------------- */

export function get(url, params) {
  return service.get(url, { params }).then((r) => r.data)
}

export function post(url, data, params) {
  return service.post(url, data, { params }).then((r) => r.data)
}

export function put(url, data, params) {
  return service.put(url, data, { params }).then((r) => r.data)
}

export function del(url, params) {
  return service.delete(url, { params }).then((r) => r.data)
}

export default service
