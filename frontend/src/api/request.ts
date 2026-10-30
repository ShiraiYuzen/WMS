import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * axios 请求封装。
 * baseURL 走 vite 代理：/api -> http://localhost:8080/api
 * 请求拦截器自动携带 Bearer token；响应拦截器统一处理业务码与 401。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

export interface ApiRes<T = any> {
  code: number
  message: string
  data: T
}

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('wms_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res.code !== 'undefined' && res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('wms_token')
      localStorage.removeItem('wms_username')
      localStorage.removeItem('wms_role')
      localStorage.removeItem('wms_nickname')
      ElMessage.warning('登录已失效，请重新登录')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    } else {
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  },
)

export const http = {
  get: <T = any>(url: string, params?: Record<string, any>) =>
    request.get(url, { params }) as unknown as Promise<ApiRes<T>>,
  post: <T = any>(url: string, data?: any) =>
    request.post(url, data) as unknown as Promise<ApiRes<T>>,
}

export default request
