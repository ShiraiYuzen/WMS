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

export function formatDate(v) {
  return formatTime(v, 'YYYY-MM-DD')
}

export function formatTimeShort(v) {
  return formatTime(v, 'MM-DD HH:mm')
}

/** 秒 → 人类可读时长 */
export function formatDuration(sec) {
  if (sec === null || sec === undefined || sec === '') return '—'
  const s = Number(sec)
  if (Number.isNaN(s)) return '—'
  if (s < 60) return `${Math.round(s)} 秒`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m} 分 ${Math.round(s % 60)} 秒`
  const h = Math.floor(m / 60)
  return `${h} 小时 ${m % 60} 分`
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

/** 相对时间：3 分钟前 */
export function fromNow(v) {
  if (!v) return '—'
  const d = dayjs(v)
  if (!d.isValid()) return String(v)
  const diff = dayjs().diff(d, 'minute')
  if (diff < 1) return '刚刚'
  if (diff < 60) return `${diff} 分钟前`
  const h = Math.floor(diff / 60)
  if (h < 24) return `${h} 小时前`
  const day = Math.floor(h / 24)
  if (day < 30) return `${day} 天前`
  return d.format('YYYY-MM-DD')
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

/** 简单深拷贝（数据均为纯 JSON 结构） */
export function clone(obj) {
  return JSON.parse(JSON.stringify(obj))
}

/** 下载文本文件（导出用） */
export function downloadText(filename, content, mime = 'text/csv;charset=utf-8') {
  const blob = new Blob(['\ufeff' + content], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

/** 数组转 CSV */
export function toCsv(rows, headers) {
  const head = headers.map((h) => `"${h.label}"`).join(',')
  const body = rows
    .map((r) => headers.map((h) => `"${r[h.key] ?? ''}"`).join(','))
    .join('\n')
  return `${head}\n${body}`
}
