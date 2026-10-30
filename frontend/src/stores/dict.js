import { defineStore } from 'pinia'
import { ref } from 'vue'
import { baseApi } from '@/api'

/**
 * 字典状态。
 *
 * 启动时从 /api/base/enums 拉一次全部枚举并缓存在内存，
 * 页面里用 dict.text('waveStatus', 'PICKING') 取中文。
 * 好处：新增枚举项只需改后端，前端不用跟着发版。
 */
export const useDictStore = defineStore('dict', () => {
  const enums = ref({})
  const loaded = ref(false)
  let loadingPromise = null

  /** 加载字典（并发调用只发一次请求） */
  async function load() {
    if (loaded.value) return enums.value
    if (loadingPromise) return loadingPromise

    loadingPromise = baseApi
      .enums()
      .then((data) => {
        enums.value = data || {}
        loaded.value = true
        return enums.value
      })
      .catch(() => {
        enums.value = {}
        return {}
      })
      .finally(() => {
        loadingPromise = null
      })

    return loadingPromise
  }

  /** 取某组的选项列表 */
  function options(group) {
    return enums.value[group] || []
  }

  /** 取某组某编码的中文 */
  function text(group, code) {
    const item = options(group).find((i) => i.code === code)
    return item ? item.desc : code || ''
  }

  /** 取某组某编码的补充说明 */
  function detail(group, code) {
    const item = options(group).find((i) => i.code === code)
    return item ? item.detail : ''
  }

  return { enums, loaded, load, options, text, detail }
})
