import { http } from './request'

export function listInventory(params: Record<string, any>) {
  return http.get('/inventory/list', params)
}
