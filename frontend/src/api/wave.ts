import { http } from './request'

export function listWaves(params: Record<string, any>) {
  return http.get('/wave/list', params)
}

export function waveDetail(id: number) {
  return http.get(`/wave/detail/${id}`)
}

export function pickableWaves() {
  return http.get('/wave/pickable')
}

export function reviewableWaves() {
  return http.get('/wave/reviewable')
}

export function outboundableWaves() {
  return http.get('/wave/outboundable')
}

export function createWave(data: { orderIds: number[]; pickerId?: number | null; remark?: string; strategy?: string }) {
  return http.post('/wave/create', data)
}

/** 波次预览（第三版新增） */
export function previewWave(data: { orderIds: number[]; strategy?: string }) {
  return http.post('/wave/preview', data)
}

/** 拣货员工作量（第三版新增） */
export function pickerWorkload() {
  return http.get('/wave/picker-workload')
}

export function assignPicker(waveId: number, pickerId: number) {
  return http.post('/wave/assign', { waveId, pickerId })
}

export function cancelWave(id: number) {
  return http.post('/wave/cancel', { id })
}
