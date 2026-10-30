import dayjs from 'dayjs'

/**
 * 通用工具函数。
 */

/** 日期格式化 */
export function formatTime(v, pattern = 'YYYY-MM-DD HH:mm:ss') {
  if (!v) return '—'
  const d = dayjs(v)
  return d.isValid() ? d.format(pattern) : String(v)
}

export function formatTimeShort(v) {
  return formatTime(v, 'MM-DD HH:mm')
}

/** 秒 → 人类可读时长 */
export function formatDuration(sec) {
  if (sec === null || sec === undefined || sec === '') return '—'
  const s = Number(sec)
  if (Number.isNaN(s)) return '—'
  // 先四舍五入到整秒再拆分，否则 419.6 秒会拆成「6 分 60 秒」
  const total = Math.round(s)
  if (total < 60) return `${total} 秒`
  const m = Math.floor(total / 60)
  const rest = total % 60
  if (m < 60) return rest ? `${m} 分 ${rest} 秒` : `${m} 分`
  const h = Math.floor(m / 60)
  const mm = m % 60
  return mm ? `${h} 小时 ${mm} 分` : `${h} 小时`
}

/** 比率 → 百分比字符串 */
export function formatPercent(v, digits = 2) {
  if (v === null || v === undefined) return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return '—'
  return `${(n * 100).toFixed(digits)}%`
}

/** 数字千分位 */
export function formatNumber(v) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toLocaleString('zh-CN')
}

/** 金额 */
export function formatMoney(v) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return '—'
  return `¥${n.toFixed(2)}`
}

/** 最近 N 天的日期范围，返回 [start, end] 的 YYYY-MM-DD */
export function recentRange(days = 7) {
  const end = dayjs()
  return [end.subtract(days - 1, 'day').format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
}

/**
 * 状态 → Element Plus tag 类型
 * 集中在这里，避免每个页面各写一套 switch
 */
const TAG_TYPE_MAP = {
  // 订单
  CREATED: 'info',
  POOLED: 'warning',
  WAVED: 'primary',
  PICKED: 'primary',
  REVIEWED: 'success',
  OUTBOUND: 'success',
  CANCELLED: 'info',
  // 波次
  ASSIGNED: 'warning',
  PICKING: 'primary',
  REVIEWING: 'warning',
  HANDOVERED: 'success',
  CLOSED: 'info',
  // 拣货任务
  WAITING: 'info',
  FINISHED: 'success',
  EXCEPTION: 'danger',
  // 拣货明细
  PENDING: 'info',
  SHORTAGE: 'danger',
  PARTIAL: 'warning',
  SKIPPED: 'info',
  // 复核
  PASS: 'success',
  DIFF: 'danger',
  REPICKING: 'warning',
  // 缺货
  HANDLED: 'success',
  // 交接
  CONFIRMED: 'success',
  REJECTED: 'danger',
  // 差异类型
  NONE: 'info',
  SHORT: 'danger',
  OVER: 'warning',
  WRONG_SKU: 'danger',
  DAMAGED: 'warning',
  WRONG_ORDER: 'danger'
}

export function tagType(code) {
  return TAG_TYPE_MAP[code] || 'info'
}
