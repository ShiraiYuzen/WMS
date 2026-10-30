import { http } from './request'

export function listErrors(params: Record<string, any>) {
  return http.get('/error/list', params)
}
