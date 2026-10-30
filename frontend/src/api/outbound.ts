import { http } from './request'

export function listOutbounds(params: Record<string, any>) {
  return http.get('/outbound/list', params)
}

export function submitOutbound(data: {
  waveId: number
  carrier: string
  handoverTo?: string
  remark?: string
}) {
  return http.post('/outbound/submit', data)
}
