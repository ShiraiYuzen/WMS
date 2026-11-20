import { inject, nextTick, ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'

export const listPositions = Symbol('listPositions')

// 详情返回时恢复同一个历史条目。菜单和看板的新入口使用自己的筛选条件。
export function useWorkList(initialQuery, fetchPage) {
  const route = useRoute()
  const app = useAppStore()
  const positions = inject(listPositions)
  const key = [app.currentUser.employeeNo, route.fullPath, history.state?.position].join('|')
  let saved = positions.get(key)
  positions.delete(key)
  const defaults = { ...initialQuery }
  if (typeof route.query.status === 'string') defaults.status = route.query.status
  const query = ref(saved?.query || defaults)
  const rows = ref([])
  const total = ref(saved?.total || 0)
  const loading = ref(true)
  const loadError = ref('')
  const table = ref(null)
  let sequence = 0
  let applied = { ...query.value }
  let scroll = { scrollTop: 0, scrollLeft: 0 }

  function rememberScroll(position) {
    scroll = position
  }

  onBeforeRouteLeave(to => {
    if (to.meta.hidden && to.meta.activeMenu) {
      positions.set(key, {
        query: applied,
        total: total.value,
        ...scroll,
        pageTop: table.value?.$el.closest('main')?.scrollTop || 0
      })
    }
  })

  async function load() {
    const request = ++sequence
    const params = { ...query.value }
    loading.value = true
    loadError.value = ''
    try {
      const result = await fetchPage(params)
      if (request !== sequence) return
      rows.value = result.records
      total.value = result.total
      applied = params
      await nextTick()
      table.value?.setScrollTop(saved?.scrollTop || 0)
      table.value?.setScrollLeft(saved?.scrollLeft || 0)
      const main = table.value?.$el.closest('main')
      if (main && saved) main.scrollTop = saved.pageTop
      saved = null
    } catch (error) {
      if (request !== sequence) return
      loadError.value = error.message || '读取失败'
      rows.value = []
    } finally {
      if (request === sequence) loading.value = false
    }
  }
  return { query, rows, total, loading, loadError, table, rememberScroll, load }
}
