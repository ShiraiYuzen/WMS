import { http } from './request'

export function dashboard() {
  return http.get('/stats/dashboard')
}

export function efficiency() {
  return http.get('/stats/efficiency')
}

export function errorStats() {
  return http.get('/stats/errors')
}
