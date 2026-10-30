import { http } from './request'

// ============ 库位（第三版新增） ============
export function locationPage(params: Record<string, any>) {
  return http.get('/base/location/page', params)
}

export function locationAll() {
  return http.get('/base/location/all')
}

export function locationZones() {
  return http.get('/base/location/zones')
}

export function saveLocation(data: any) {
  return http.post('/base/location/save', data)
}

export function deleteLocation(id: number) {
  return http.post('/base/location/delete', { id })
}

// ============ 承运商（第三版新增） ============
export function carrierPage(params: Record<string, any>) {
  return http.get('/base/carrier/page', params)
}

export function carrierList() {
  return http.get('/base/carrier/list')
}

export function saveCarrier(data: any) {
  return http.post('/base/carrier/save', data)
}

export function deleteCarrier(id: number) {
  return http.post('/base/carrier/delete', { id })
}
