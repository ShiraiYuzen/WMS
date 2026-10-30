import { http } from './request'

export function listOrders(params: Record<string, any>) {
  return http.get('/order/list', params)
}

export function orderDetail(id: number) {
  return http.get(`/order/detail/${id}`)
}

export function pendingOrders() {
  return http.get('/order/pending')
}

export function addOrder(data: any) {
  return http.post('/order/add', data)
}

export function cancelOrder(id: number) {
  return http.post('/order/cancel', { id })
}
