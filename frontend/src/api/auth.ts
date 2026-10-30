import { http } from './request'

export interface LoginResult {
  token: string
  username: string
  nickname: string
  loginTime: number
  expireTime: number
  role: string
}

/** 明文密码 Base64 编码后传输（与后端约定一致） */
export function encodePassword(pwd: string): string {
  try {
    return btoa(unescape(encodeURIComponent(pwd)))
  } catch {
    return pwd
  }
}

export function login(username: string, password: string) {
  return http.post<LoginResult>('/auth/login', { username, password: encodePassword(password) })
}

export function logout() {
  return http.post('/auth/logout')
}

export function me() {
  return http.get('/auth/me')
}
