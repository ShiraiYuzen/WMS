import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 应用级状态（WMS-v4）。
 *
 * 已接入真实登录：用户信息来自 POST /api/auth/login 的返回，
 * 持久化在 localStorage.wms_user，token 存在 localStorage.wms_token。
 */

const ROLE_DESC = {
  WAVE_OPERATOR: '波次员',
  PICKER: '拣货员',
  REVIEWER: '复核员',
  ADMIN: '仓库主管'
}

export const useAppStore = defineStore('app', () => {
  /* ------------------------------ 侧边栏 ------------------------------ */

  const sidebarCollapsed = ref(false)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  /* ------------------------------ 当前用户 ------------------------------ */

  /** 演示账号（与后端 sys_user 种子数据一致，密码均为 123456） */
  const ROLE_OPTIONS = [
    { username: 'wave', employeeNo: 'W001', employeeName: '王敏', role: 'WAVE_OPERATOR', roleDesc: '波次员', team: '波次调度组' },
    { username: 'picker', employeeNo: 'P001', employeeName: '张伟', role: 'PICKER', roleDesc: '拣货员', team: '拣货一组' },
    { username: 'reviewer', employeeNo: 'R001', employeeName: '吴倩', role: 'REVIEWER', roleDesc: '复核员', team: '复核组' },
    { username: 'admin', employeeNo: 'A001', employeeName: '何军', role: 'ADMIN', roleDesc: '仓库主管', team: '仓库管理' }
  ]

  function normalize(user) {
    if (!user) return null
    return { ...user, roleDesc: user.roleDesc || ROLE_DESC[user.role] || user.role }
  }

  function readSaved() {
    try {
      const raw = localStorage.getItem('wms_user')
      return raw ? normalize(JSON.parse(raw)) : null
    } catch {
      return null
    }
  }

  const currentUser = ref(readSaved() || normalize({ ...ROLE_OPTIONS[0] }))

  /** 登录成功后写入用户与 token */
  function setUser(loginResult) {
    const user = normalize({
      employeeNo: loginResult.employeeNo,
      employeeName: loginResult.employeeName,
      role: loginResult.role,
      team: loginResult.team
    })
    currentUser.value = user
    localStorage.setItem('wms_user', JSON.stringify(user))
    if (loginResult.token) localStorage.setItem('wms_token', loginResult.token)
  }

  function logout() {
    currentUser.value = normalize({ ...ROLE_OPTIONS[0] })
    localStorage.removeItem('wms_token')
    localStorage.removeItem('wms_user')
    localStorage.removeItem('wms_role')
  }

  const isAdmin = computed(() => currentUser.value.role === 'ADMIN')
  const isWaveOperator = computed(() => ['WAVE_OPERATOR', 'ADMIN'].includes(currentUser.value.role))
  const isPicker = computed(() => ['PICKER', 'ADMIN'].includes(currentUser.value.role))
  const isReviewer = computed(() => ['REVIEWER', 'ADMIN'].includes(currentUser.value.role))

  /** 判断某个角色编码是否可见 */
  function canAccess(roles) {
    if (!roles || !roles.length) return true
    return roles.includes(currentUser.value.role)
  }

  /* ------------------------------ 待办红点 ------------------------------ */

  const badge = ref({
    order: 0,
    wave: 0,
    pick: 0,
    shortage: 0,
    review: 0,
    handover: 0
  })

  function setBadge(data) {
    badge.value = { ...badge.value, ...data }
  }

  return {
    sidebarCollapsed,
    toggleSidebar,
    ROLE_OPTIONS,
    currentUser,
    setUser,
    logout,
    isAdmin,
    isWaveOperator,
    isPicker,
    isReviewer,
    canAccess,
    badge,
    setBadge
  }
})
