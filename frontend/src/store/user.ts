import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export const ROLE_LABELS: Record<string, string> = {
  ADMIN: '系统管理员',
  WAVE_PLANNER: '波次员',
  PICKER: '拣货员',
  CHECKER: '复核员',
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('wms_token') || '')
  const username = ref<string>(localStorage.getItem('wms_username') || '')
  const nickname = ref<string>(localStorage.getItem('wms_nickname') || '')
  const role = ref<string>(localStorage.getItem('wms_role') || 'PICKER')

  const isAdmin = computed(() => role.value === 'ADMIN')
  const roleLabel = computed(() => ROLE_LABELS[role.value] || role.value)

  function setLogin(t: string, name: string, nick: string, r: string) {
    token.value = t
    username.value = name
    nickname.value = nick || name
    role.value = r || 'PICKER'
    localStorage.setItem('wms_token', t)
    localStorage.setItem('wms_username', name)
    localStorage.setItem('wms_nickname', nickname.value)
    localStorage.setItem('wms_role', role.value)
  }

  function logout() {
    token.value = ''
    username.value = ''
    nickname.value = ''
    role.value = 'PICKER'
    localStorage.removeItem('wms_token')
    localStorage.removeItem('wms_username')
    localStorage.removeItem('wms_nickname')
    localStorage.removeItem('wms_role')
  }

  function hasRole(roles?: string[]) {
    if (!roles || roles.length === 0) return true
    return roles.includes(role.value)
  }

  return { token, username, nickname, role, isAdmin, roleLabel, setLogin, logout, hasRole }
})
