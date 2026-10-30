import { http } from './request'

export function pickDetail(waveId: number) {
  return http.get(`/pick/detail/${waveId}`)
}

export function startPick(waveId: number) {
  return http.post('/pick/start', { waveId })
}

export function submitPick(data: {
  waveId: number
  items: { skuCode: string; pickedQty: number; shortageQty: number; reason?: string }[]
}) {
  return http.post('/pick/submit', data)
}

export function finishPick(waveId: number) {
  return http.post('/pick/finish', { waveId })
}
