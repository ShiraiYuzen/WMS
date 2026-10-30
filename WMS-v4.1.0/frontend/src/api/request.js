import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Axios 实例（WMS-v4）。
 *
 * v4 已彻底移除前端 mock：所有请求都真实发往后端 /api，
 * 开发环境由 Vite 代理转发到 http://127.0.0.1:8080。
 */
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: Number(import.meta.env.VITE_REQUEST_TIMEOUT || 15000),
  headers: { 'Content-Type': 'application/json;charset=UTF-8' }
})

/* -------------------------------------------------------------------------- */
/* 请求拦截                                                                    */
/* -------------------------------------------------------------------------- */

service.interceptors.request.use(
  (config) => {
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

/** 登录失效时清空本地态并跳登录页（避免在登录页自身反复跳转） */
function toLogin() {
  localStorage.removeItem('wms_token')
  localStorage.removeItem('wms_user')
  if (!location.hash.startsWith('#/login')) {
    location.hash = '#/login'
  }
}

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
      const { status, data } = error.response
      if (status === 401) {
        message = data?.message || '登录已失效，请重新登录'
        toLogin()
      } else if (status === 403) message = '没有该操作权限'
      else if (status === 404) message = '接口不存在'
      else if (status >= 500) message = '服务端异常，请联系管理员'
      else if (data?.message) message = data.message
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
