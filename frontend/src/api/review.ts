import { http } from './request'

export function reviewDetail(waveId: number) {
  return http.get(`/review/detail/${waveId}`)
}

export function submitReview(data: {
  waveId: number
  reviews: {
    orderId: number
    result: string
    packCount?: number
    remark?: string
    errors: { skuCode: string; errorType: string; qty: number; description?: string }[]
  }[]
}) {
  return http.post('/review/submit', data)
}
