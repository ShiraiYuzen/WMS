import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 应用级状态。
 *
 * 【关于「当前用户」】当前阶段未接入登录，为了能演示「波次员 / 拣货员 / 复核员」
 * 三种角色各自的菜单与待办，这里用角色切换器模拟登录身份。
 * 接入登录后，把 currentUser 换成登录接口返回的用户信息即可，
 * 权限判断逻辑（菜单过滤、按钮显隐）无需改动。
 */
export const useAppStore = defineStore('app', () => {
  /* ------------------------------ 侧边栏 ------------------------------ */

  const sidebarCollapsed = ref(false)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  /* ------------------------------ 当前用户 ------------------------------ */

  /** 可选角色（演示用） */
  const ROLE_OPTIONS = [
    { employeeNo: 'W001', employeeName: '王敏', role: 'WAVE_OPERATOR', roleDesc: '波次员', team: '波次调度组' },
    { employeeNo: 'P001', employeeName: '张伟', role: 'PICKER', roleDesc: '拣货员', team: '拣货一组' },
    { employeeNo: 'R001', employeeName: '吴倩', role: 'REVIEWER', roleDesc: '复核员', team: '复核组' },
    { employeeNo: 'A001', employeeName: '何军', role: 'ADMIN', roleDesc: '仓库主管', team: '仓库管理' }
  ]

  const currentUser = ref({ ...ROLE_OPTIONS[0] })

  function switchRole(employeeNo) {
    const target = ROLE_OPTIONS.find((r) => r.employeeNo === employeeNo)
    if (target) {
      currentUser.value = { ...target }
      localStorage.setItem('wms_role', employeeNo)
    }
  }

  // 恢复上次选择的角色
  const saved = localStorage.getItem('wms_role')
  if (saved) {
    const target = ROLE_OPTIONS.find((r) => r.employeeNo === saved)
    if (target) currentUser.value = { ...target }
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
    switchRole,
    isAdmin,
    isWaveOperator,
    isPicker,
    isReviewer,
    canAccess,
    badge,
    setBadge
  }
})
