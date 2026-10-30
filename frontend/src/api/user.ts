import { http } from './request'

export function listUsers(params: Record<string, any>) {
  return http.get('/user/list', params)
}

export function allUsers() {
  return http.get('/user/all')
}

export function usersByRole(role: string) {
  return http.get('/user/role', { role })
}

export function addUser(data: any) {
  return http.post('/user/add', data)
}

export function updateUser(data: any) {
  return http.post('/user/update', data)
}

export function resetPassword(id: number, password: string) {
  return http.post('/user/resetPassword', { id, password })
}

export function deleteUser(id: number) {
  return http.post('/user/delete', { id })
}
