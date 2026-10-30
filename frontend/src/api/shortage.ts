import { http } from './request'

export function listShortages(params: Record<string, any>) {
  return http.get('/shortage/list', params)
}

export function handleShortage(data: { id: number; status: string; remark?: string }) {
  return http.post('/shortage/handle', data)
}
