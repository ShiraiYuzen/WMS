import { http } from './request'

export function listSkus(params: Record<string, any>) {
  return http.get('/sku/list', params)
}

export function allSkus() {
  return http.get('/sku/all')
}

export function addSku(data: any) {
  return http.post('/sku/add', data)
}

export function updateSku(data: any) {
  return http.post('/sku/update', data)
}

export function deleteSku(id: number) {
  return http.post('/sku/delete', { id })
}
