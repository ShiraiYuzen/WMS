import { http } from './request'

// 操作日志（第三版新增）
export function logPage(params: Record<string, any>) {
  return http.get('/log/page', params)
}
