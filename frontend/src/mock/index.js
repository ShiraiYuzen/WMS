/**
 * Mock 适配器。
 *
 * 作为 axios 的自定义 adapter 挂载：请求不发出网络，直接在前端内存里完成业务运算，
 * 返回与后端完全同构的响应体 { code, message, data }。
 *
 * 【路由顺序很重要】具体路径必须排在参数路径前面，
 * 例如 `/order/count-by-status` 要排在 `/order/:orderNo` 之前。
 */
import {
  db,
  initDb,
  nextId,
  nextNo,
  fmt,
  paginate,
  div,
  addLog,
  dictText,
  ENUMS,
  WAVE_RANK_MAP
} from './db.js'

/* ========================================================================== */
/* 工具                                                                        */
/* ========================================================================== */

const ok = (data, message = 'success') => ({ code: 0, message, data, timestamp: Date.now() })
const fail = (message, code = 10004) => ({ code, message, data: null, timestamp: Date.now() })

const nowStr = () => fmt(new Date())

const eq = (a, b) => String(a ?? '') === String(b ?? '')
const has = (v) => v !== undefined && v !== null && v !== ''
const includes = (v, kw) => String(v ?? '').toLowerCase().includes(String(kw).toLowerCase())

const empByNo = (no) => db.employees.find((e) => e.employeeNo === no)
const waveByNo = (no) => db.waves.find((w) => w.waveNo === no)
const taskByNo = (no) => db.pickTasks.find((t) => t.taskNo === no)

function sortedBy(list, field, desc = true) {
  return list.slice().sort((a, b) => {
    const va = a[field] || ''
    const vb = b[field] || ''
    return desc ? String(vb).localeCompare(String(va)) : String(va).localeCompare(String(vb))
  })
}

/* ========================================================================== */
/* VO 转换                                                                     */
/* ========================================================================== */

function toOrderVO(o) {
  return {
    ...o,
    statusDesc: dictText('orderStatus', o.status),
    priorityDesc: o.priority === 'URGENT' ? '加急' : '普通',
    poolable: o.status === 'CREATED' || o.status === 'POOLED',
    items: db.orderItems
      .filter((i) => i.orderNo === o.orderNo)
      .sort((a, b) => a.lineNo - b.lineNo)
  }
}

const WAVE_PROGRESS = {
  CREATED: 5,
  ASSIGNED: 15,
  PICKING: 40,
  PICKED: 60,
  REVIEWING: 75,
  REVIEWED: 85,
  HANDOVERED: 95,
  CLOSED: 100,
  CANCELLED: 0
}

const WAVE_NODE = {
  CREATED: '波次生成',
  ASSIGNED: '波次生成',
  PICKING: '拣货执行',
  PICKED: '复核打包',
  REVIEWING: '复核打包',
  REVIEWED: '出库交接',
  HANDOVERED: '已完成',
  CLOSED: '已完成',
  CANCELLED: '已取消'
}

const WORKING_WAVE = ['ASSIGNED', 'PICKING', 'REVIEWING']

function toWaveVO(w) {
  return {
    ...w,
    statusDesc: dictText('waveStatus', w.status),
    strategyDesc: dictText('waveStrategy', w.strategy),
    progress: WAVE_PROGRESS[w.status] ?? 0,
    currentNode: WAVE_NODE[w.status] || '',
    orders: db.waveOrders
      .filter((r) => r.waveNo === w.waveNo)
      .sort((a, b) => a.seqNo - b.seqNo)
      .map((r) => db.orders.find((o) => o.orderNo === r.orderNo))
      .filter(Boolean)
  }
}

function toPickTaskVO(t) {
  const items = db.pickItems
    .filter((i) => i.taskNo === t.taskNo)
    .sort((a, b) => a.seqNo - b.seqNo)
  return {
    ...t,
    statusDesc: dictText('pickTaskStatus', t.status),
    progress: t.itemCount ? Math.min(100, Math.round((t.finishedCount / t.itemCount) * 100)) : 0,
    items
  }
}

function toReviewVO(r) {
  return {
    ...r,
    statusDesc: dictText('reviewStatus', r.status),
    diffTypeDesc: dictText('diffType', r.diffType),
    shouldItems: db.orderItems
      .filter((i) => i.orderNo === r.orderNo)
      .sort((a, b) => a.lineNo - b.lineNo),
    diffItems: db.reviewDiffs.filter((d) => d.reviewNo === r.reviewNo)
  }
}

/* ========================================================================== */
/* 业务运算：波次生成                                                           */
/* ========================================================================== */

function resolveCandidates(req) {
  let list
  if (req.orderNos && req.orderNos.length) {
    list = db.orders.filter((o) => req.orderNos.includes(o.orderNo) && ['CREATED', 'POOLED'].includes(o.status))
  } else {
    list = db.orders.filter((o) => o.status === 'POOLED')
  }
  // 加急优先
  return list.sort((a, b) => {
    if (a.priority !== b.priority) return a.priority === 'URGENT' ? -1 : 1
    return String(a.payTime).localeCompare(String(b.payTime))
  })
}

function groupKeyOf(o, strategy) {
  if (strategy === 'BY_AREA') return o.province || '未知区域'
  if (strategy === 'BY_CARRIER') return o.carrierName || '未知承运商'
  if (strategy === 'BY_TIMELINESS') return o.priority === 'URGENT' ? '加急订单' : '普通订单'
  return '均衡批次'
}

function buildGroups(candidates, req) {
  const strategy = req.strategy || 'BY_AREA'
  const maxOrders = req.maxOrdersPerWave || 12
  const maxSkus = req.maxSkusPerWave || 30
  const maxQty = req.maxQtyPerWave || 120

  const grouped = new Map()
  candidates.forEach((o) => {
    const k = groupKeyOf(o, strategy)
    if (!grouped.has(k)) grouped.set(k, [])
    grouped.get(k).push(o)
  })

  const result = []
  grouped.forEach((orders, key) => {
    let cur = []
    let skuSet = new Set()
    let locSet = new Set()
    let qty = 0
    let batch = 1

    const flush = () => {
      if (!cur.length) return
      const k = batch > 1 ? `${key} #${batch}` : key
      result.push({
        groupKey: k,
        suggestWaveName: `${new Date().toISOString().slice(0, 10)} ${k}`,
        orderCount: cur.length,
        skuCount: skuSet.size,
        totalQty: qty,
        locationCount: locSet.size,
        overCapacity: false,
        orders: cur.slice()
      })
      cur = []
      skuSet = new Set()
      locSet = new Set()
      qty = 0
      batch++
    }

    orders.forEach((o) => {
      const items = db.orderItems.filter((i) => i.orderNo === o.orderNo)
      const orderSkus = new Set(items.map((i) => i.skuCode))
      const orderLocs = items.map((i) => i.locationCode)
      const orderQty = items.reduce((s, i) => s + i.qty, 0)

      const merged = new Set([...skuSet, ...orderSkus])
      const overflow =
        cur.length > 0 && (cur.length + 1 > maxOrders || merged.size > maxSkus || qty + orderQty > maxQty)

      if (overflow) flush()

      cur.push(o)
      orderSkus.forEach((s) => skuSet.add(s))
      orderLocs.forEach((l) => locSet.add(l))
      qty += orderQty
    })
    flush()
  })
  return result
}

/** 为一个波次生成拣货任务与明细（库位合并 + 路径排序） */
function createPickTaskForWave(wave, orders) {
  const pathOrder = db.locations
    .slice()
    .sort((a, b) => a.pathSeq - b.pathSeq)
    .map((l) => l.locationCode)

  const merged = new Map()
  orders.forEach((o) => {
    db.orderItems
      .filter((i) => i.orderNo === o.orderNo)
      .forEach((it) => {
        const key = `${it.locationCode}|${it.skuCode}`
        if (!merged.has(key)) {
          merged.set(key, {
            locationCode: it.locationCode,
            skuCode: it.skuCode,
            skuName: it.skuName,
            spec: it.spec,
            unit: it.unit,
            shouldQty: 0,
            orderNos: new Set()
          })
        }
        const m = merged.get(key)
        m.shouldQty += it.qty
        m.orderNos.add(o.orderNo)
      })
  })

  const list = [...merged.values()].sort(
    (a, b) => pathOrder.indexOf(a.locationCode) - pathOrder.indexOf(b.locationCode)
  )

  const taskNo = `PK${wave.waveNo.slice(2)}`
  let shouldTotal = 0
  const locs = new Set()
  list.forEach((m, i) => {
    db.pickItems.push({
      id: nextId(),
      taskNo,
      waveNo: wave.waveNo,
      seqNo: i + 1,
      locationCode: m.locationCode,
      skuCode: m.skuCode,
      skuName: m.skuName,
      spec: m.spec,
      unit: m.unit,
      shouldQty: m.shouldQty,
      actualQty: 0,
      shortageQty: 0,
      status: 'PENDING',
      relatedOrderNos: [...m.orderNos].join(','),
      scanTime: null,
      remark: null
    })
    shouldTotal += m.shouldQty
    locs.add(m.locationCode)
  })

  const task = {
    id: nextId(),
    taskNo,
    waveNo: wave.waveNo,
    pickerNo: wave.pickerNo,
    pickerName: wave.pickerName,
    status: 'WAITING',
    itemCount: list.length,
    finishedCount: 0,
    locationCount: locs.size,
    shouldQty: shouldTotal,
    actualQty: 0,
    shortageQty: 0,
    orderCount: orders.length,
    startTime: null,
    finishTime: null,
    durationSec: null,
    pathText: [...locs].join(' → '),
    remark: null,
    createTime: nowStr()
  }
  db.pickTasks.push(task)
  return task
}

/* ========================================================================== */
/* 业务运算：拣货                                                              */
/* ========================================================================== */

function recalcTask(task) {
  const items = db.pickItems.filter((i) => i.taskNo === task.taskNo)
  task.itemCount = items.length
  task.finishedCount = items.filter((i) => i.status !== 'PENDING').length
  task.actualQty = items.reduce((s, i) => s + (i.actualQty || 0), 0)
  task.shortageQty = items.reduce((s, i) => s + (i.shortageQty || 0), 0)

  const allProcessed = items.every((i) => i.status !== 'PENDING')
  if (task.status !== 'FINISHED' && allProcessed) {
    finishTaskInternal(task, items)
  } else {
    if (task.status === 'WAITING') task.status = 'PICKING'
    if (!task.startTime) task.startTime = nowStr()
    if (task.shortageQty > 0) task.status = 'EXCEPTION'
    const w = waveByNo(task.waveNo)
    if (w && !w.pickStartTime) {
      w.pickStartTime = nowStr()
      w.status = 'PICKING'
    }
  }
}

function finishTaskInternal(task, items) {
  const now = new Date()
  task.status = task.shortageQty > 0 ? 'EXCEPTION' : 'FINISHED'
  task.finishTime = fmt(now)
  if (task.startTime) {
    task.durationSec = Math.round((now - new Date(task.startTime)) / 1000)
  }

  // 回写订单明细实拣数
  const wave = waveByNo(task.waveNo)
  db.waveOrders
    .filter((r) => r.waveNo === task.waveNo)
    .forEach((r) => {
      db.orderItems
        .filter((i) => i.orderNo === r.orderNo)
        .forEach((oi) => {
          const picked = items
            .filter((pi) => pi.skuCode === oi.skuCode && (pi.relatedOrderNos || '').includes(r.orderNo))
            .reduce((s, pi) => {
              const nos = pi.relatedOrderNos.split(',')
              if (nos.length === 1) return s + (pi.actualQty || 0)
              const shouldSum = db.orderItems
                .filter((x) => x.skuCode === oi.skuCode && nos.includes(x.orderNo))
                .reduce((a, x) => a + x.qty, 0)
              return s + (shouldSum > 0 ? Math.round((pi.actualQty || 0) * (oi.qty / shouldSum)) : 0)
            }, 0)
          oi.pickedQty = Math.min(picked, oi.qty)
        })
      const o = db.orders.find((x) => x.orderNo === r.orderNo)
      if (o) o.status = 'PICKED'
    })

  if (wave) {
    wave.status = 'PICKED'
    wave.pickFinishTime = fmt(now)
    if (wave.pickStartTime) {
      wave.pickDurationSec = Math.round((now - new Date(wave.pickStartTime)) / 1000)
    }
  }

  const picker = empByNo(task.pickerNo)
  addLog('PICK', task.taskNo, 'PICK_FINISH', '拣货完成', picker,
    `实拣 ${task.actualQty} / 应拣 ${task.shouldQty} 件${task.shortageQty > 0 ? `，缺货 ${task.shortageQty} 件` : ''}`)
}

/* ========================================================================== */
/* 业务运算：效率复盘                                                           */
/* ========================================================================== */

function inRange(timeStr, q) {
  if (!timeStr) return false
  const d = String(timeStr).slice(0, 10)
  if (q.startDate && d < q.startDate) return false
  if (q.endDate && d > q.endDate) return false
  return true
}

function rangeTasks(q) {
  return db.pickTasks.filter(
    (t) => inRange(t.finishTime || t.createTime, q) && ['FINISHED', 'EXCEPTION'].includes(t.status)
  )
}

function rangeReviews(q) {
  return db.reviews.filter((r) => inRange(r.finishTime || r.createTime, q))
}

function computeOverview(q) {
  const tasks = rangeTasks(q)
  const reviews = rangeReviews(q)

  const pickedQty = tasks.reduce((s, t) => s + (t.actualQty || 0), 0)
  const pickSeconds = tasks.reduce((s, t) => s + (t.durationSec || 0), 0)
  const shouldQty = tasks.reduce((s, t) => s + (t.shouldQty || 0), 0)
  const shortageQty = tasks.reduce((s, t) => s + (t.shortageQty || 0), 0)

  const reviewTotal = reviews.length
  const passCount = reviews.filter((r) => r.status === 'PASS').length
  const diffCount = reviews.filter((r) => r.status === 'DIFF').length

  const overview = {
    totalOrders: db.orders.length,
    pendingOrders: db.orders.filter((o) => o.status === 'CREATED').length,
    pooledOrders: db.orders.filter((o) => o.status === 'POOLED').length,
    totalWaves: db.waves.length,
    workingWaves: db.waves.filter((w) => WORKING_WAVE.includes(w.status)).length,
    finishedWaves: db.waves.filter((w) => ['HANDOVERED', 'CLOSED'].includes(w.status)).length,
    pickedQty,
    outboundOrders: db.orders.filter((o) => o.status === 'OUTBOUND').length,
    handedPackages: db.handovers
      .filter((h) => h.status === 'CONFIRMED')
      .reduce((s, h) => s + (h.packageCount || 0), 0),
    pickEfficiency: pickSeconds > 0 ? div(pickedQty * 3600, pickSeconds, 2) : 0,
    avgPickDurationSec: tasks.length ? div(pickSeconds, tasks.length, 2) : 0,
    errorRate: reviewTotal ? div(diffCount, reviewTotal, 4) : 0,
    shortageRate: shouldQty ? div(shortageQty, shouldQty, 4) : 0,
    reviewPassRate: reviewTotal ? div(passCount, reviewTotal, 4) : 0,
    pendingShortageCount: db.shortages.filter((s) => s.status === 'PENDING').length,
    alerts: []
  }

  // 指标预警（阈值与后端 application.yml 一致）
  const alerts = []
  if (overview.pickEfficiency > 0 && overview.pickEfficiency < 120) {
    alerts.push({
      metric: 'pickEfficiency', metricName: '拣货效率',
      currentValue: overview.pickEfficiency, threshold: 120, level: 'WARN',
      message: `拣货效率 ${overview.pickEfficiency} 件/小时，低于目标 120 件/小时`
    })
  }
  if (overview.errorRate > 0.005) {
    alerts.push({
      metric: 'errorRate', metricName: '差错率',
      currentValue: overview.errorRate, threshold: 0.005,
      level: overview.errorRate > 0.015 ? 'DANGER' : 'WARN',
      message: `差错率 ${(overview.errorRate * 100).toFixed(2)}%，高于目标 0.50%`
    })
  }
  if (overview.shortageRate > 0.02) {
    alerts.push({
      metric: 'shortageRate', metricName: '缺货率',
      currentValue: overview.shortageRate, threshold: 0.02,
      level: overview.shortageRate > 0.06 ? 'DANGER' : 'WARN',
      message: `缺货率 ${(overview.shortageRate * 100).toFixed(2)}%，高于目标 2.00%`
    })
  }
  if (overview.avgPickDurationSec > 3600) {
    alerts.push({
      metric: 'avgPickDuration', metricName: '平均拣货时长',
      currentValue: overview.avgPickDurationSec, threshold: 3600, level: 'WARN',
      message: `平均拣货时长 ${Math.round(overview.avgPickDurationSec / 60)} 分钟，高于目标 60 分钟`
    })
  }
  overview.alerts = alerts
  return overview
}

function computeEmployeeEfficiency(q) {
  const tasks = rangeTasks(q)
  const reviews = rangeReviews(q)
  const list = []

  db.employees
    .filter((e) => ['PICKER', 'REVIEWER'].includes(e.role))
    .filter((e) => !q.employeeNo || eq(q.employeeNo, e.employeeNo))
    .forEach((e) => {
      const vo = {
        employeeNo: e.employeeNo,
        employeeName: e.employeeName,
        roleDesc: dictText('employeeRole', e.role),
        team: e.team,
        taskCount: 0, pickedQty: 0, qtyPerHour: 0, avgDurationSec: 0, locationCount: 0,
        reviewCount: 0, errorCount: 0, errorRate: 0, orderPerHour: 0, score: 0
      }

      if (e.role === 'PICKER') {
        const mine = tasks.filter((t) => eq(t.pickerNo, e.employeeNo))
        const qty = mine.reduce((s, t) => s + (t.actualQty || 0), 0)
        const sec = mine.reduce((s, t) => s + (t.durationSec || 0), 0)
        const locs = mine.reduce((s, t) => s + (t.locationCount || 0), 0)
        vo.taskCount = mine.length
        vo.pickedQty = qty
        vo.qtyPerHour = sec > 0 ? div(qty * 3600, sec, 2) : 0
        vo.avgDurationSec = mine.length ? div(sec, mine.length, 2) : 0
        vo.locationCount = locs
        vo.score = div(Math.min(100, (vo.qtyPerHour / 120) * 70) + 30, 1, 2)
      } else {
        const mine = reviews.filter((r) => eq(r.reviewerNo, e.employeeNo))
        const errs = mine.filter((r) => r.status === 'DIFF').length
        const sec = mine.reduce((s, r) => s + (r.durationSec || 0), 0)
        vo.reviewCount = mine.length
        vo.errorCount = errs
        vo.errorRate = mine.length ? div(errs, mine.length, 4) : 0
        vo.orderPerHour = sec > 0 ? div(mine.length * 3600, sec, 2) : 0
        const acc = 1 - Math.min(1, vo.errorRate)
        vo.score = div(Math.min(50, mine.length * 2.5) + acc * 50, 1, 2)
      }
      list.push(vo)
    })

  return list.sort((a, b) => b.score - a.score)
}

function computeTrend(q) {
  const end = q.endDate || new Date().toISOString().slice(0, 10)
  const startDate = new Date()
  startDate.setDate(startDate.getDate() - 6)
  const start = q.startDate || startDate.toISOString().slice(0, 10)

  const buckets = new Map()
  const cur = new Date(start)
  const endD = new Date(end)
  while (cur <= endD) {
    const label = cur.toISOString().slice(0, 10)
    buckets.set(label, {
      label, waveCount: 0, orderCount: 0, pickedQty: 0,
      pickEfficiency: 0, errorRate: 0, shortageQty: 0, avgDurationSec: 0,
      _reviewTotal: 0, _diffTotal: 0, _durSum: 0
    })
    cur.setDate(cur.getDate() + 1)
  }

  db.waves.forEach((w) => {
    if (!w.createTime) return
    const b = buckets.get(w.createTime.slice(0, 10))
    if (!b) return
    b.waveCount++
    b.orderCount += w.orderCount || 0
    b.pickedQty += w.totalQty || 0
    b._durSum += w.pickDurationSec || 0
  })

  rangeTasks(q).forEach((t) => {
    const time = (t.finishTime || t.createTime || '').slice(0, 10)
    const b = buckets.get(time)
    if (b) b.shortageQty += t.shortageQty || 0
  })

  rangeReviews(q).forEach((r) => {
    if (r.status === 'WAITING') return
    const time = (r.finishTime || r.createTime || '').slice(0, 10)
    const b = buckets.get(time)
    if (!b) return
    b._reviewTotal++
    if (r.status === 'DIFF') b._diffTotal++
  })

  return [...buckets.values()].map((b) => ({
    label: b.label,
    waveCount: b.waveCount,
    orderCount: b.orderCount,
    pickedQty: b.pickedQty,
    shortageQty: b.shortageQty,
    errorRate: b._reviewTotal ? div(b._diffTotal, b._reviewTotal, 4) : 0,
    avgDurationSec: b.waveCount ? div(b._durSum, b.waveCount, 2) : 0,
    pickEfficiency: b._durSum > 0 ? div(b.pickedQty * 3600, b._durSum, 2) : 0
  }))
}

function computeErrorRate(q) {
  const reviews = rangeReviews(q)
  const total = reviews.length
  const pass = reviews.filter((r) => r.status === 'PASS').length
  const diff = reviews.filter((r) => r.status === 'DIFF').length

  const diffs = db.reviewDiffs.filter((d) => inRange(d.createTime, q))

  const dist = (codes, namer) => {
    const map = new Map()
    codes.forEach((c) => map.set(c, (map.get(c) || 0) + 1))
    const totalN = codes.length || 1
    return [...map.entries()]
      .map(([code, count]) => ({ code, name: namer(code), count, ratio: div(count, totalN, 4) }))
      .sort((a, b) => b.count - a.count)
  }

  const employees = computeEmployeeEfficiency(q)

  return {
    reviewTotal: total,
    passCount: pass,
    diffCount: diff,
    errorRate: total ? div(diff, total, 4) : 0,
    diffQtyTotal: diffs.reduce((s, d) => s + Math.abs(d.diffQty || 0), 0),
    typeDistribution: dist(diffs.map((d) => d.diffType || 'UNKNOWN'), (c) => dictText('diffType', c)),
    stageDistribution: dist(
      diffs.map((d) => d.responsibleStage || 'UNKNOWN'),
      (c) => ({ PICK: '拣货环节', WAVE: '波次环节', DATA: '基础数据' })[c] || '待定'
    ),
    topErrorEmployees: employees
      .filter((e) => e.roleDesc === '复核员' && e.errorCount > 0)
      .sort((a, b) => b.errorRate - a.errorRate)
      .slice(0, 5)
  }
}

/* ========================================================================== */
/* 路由表                                                                      */
/* ========================================================================== */

const routes = [
  /* ---------------------------- 作业看板 ---------------------------- */
  ['GET', '/dashboard/summary', () => {
    const q = { startDate: daysAgoStr(6), endDate: todayStr() }
    const pipeline = [
      { stage: '订单汇聚', pending: db.orders.filter((o) => o.status === 'CREATED').length, working: db.orders.filter((o) => o.status === 'POOLED').length },
      { stage: '波次生成', pending: db.waves.filter((w) => w.status === 'CREATED').length, working: db.waves.filter((w) => w.status === 'ASSIGNED').length },
      { stage: '拣货执行', pending: db.pickTasks.filter((t) => t.status === 'WAITING').length, working: db.pickTasks.filter((t) => t.status === 'PICKING').length },
      { stage: '复核打包', pending: db.reviews.filter((r) => r.status === 'WAITING').length, working: db.reviews.filter((r) => r.status === 'DIFF').length },
      { stage: '出库交接', pending: db.waves.filter((w) => w.status === 'REVIEWED').length, working: db.waves.filter((w) => w.status === 'HANDOVERED').length }
    ]
    const todos = [
      { name: '待汇聚订单', count: db.orders.filter((o) => o.status === 'CREATED').length, path: '/order', role: 'WAVE_OPERATOR' },
      { name: '待生成波次订单', count: db.orders.filter((o) => o.status === 'POOLED').length, path: '/wave', role: 'WAVE_OPERATOR' },
      { name: '待处理缺货', count: db.shortages.filter((s) => s.status === 'PENDING').length, path: '/shortage', role: 'WAVE_OPERATOR' },
      { name: '待拣货任务', count: db.pickTasks.filter((t) => t.status === 'WAITING').length, path: '/pick', role: 'PICKER' },
      { name: '待复核订单', count: db.reviews.filter((r) => r.status === 'WAITING').length, path: '/review', role: 'REVIEWER' },
      { name: '待交接波次', count: db.waves.filter((w) => w.status === 'REVIEWED').length, path: '/handover', role: 'REVIEWER' }
    ]
    return ok({ overview: computeOverview(q), pipeline, todos })
  }],
  ['GET', '/dashboard/trend', () => ok(computeTrend({ startDate: daysAgoStr(6), endDate: todayStr(), granularity: 'DAY' }))],

  /* ---------------------------- 订单 ---------------------------- */
  ['GET', '/order/count-by-status', () => {
    const map = {}
    Object.keys(ENUMS.orderStatus.reduce((a, i) => ({ ...a, [i.code]: 1 }), {})).forEach((c) => {
      map[c] = db.orders.filter((o) => o.status === c).length
    })
    return ok(map)
  }],
  ['GET', '/order/page', ({ query }) => {
    let list = db.orders.filter((o) => {
      if (has(query.orderNo) && !includes(o.orderNo, query.orderNo) && !includes(o.receiverName, query.orderNo)) return false
      if (has(query.platform) && !eq(o.platform, query.platform)) return false
      if (has(query.carrierCode) && !eq(o.carrierCode, query.carrierCode)) return false
      if (has(query.status) && !eq(o.status, query.status)) return false
      if (has(query.province) && !eq(o.province, query.province)) return false
      if (has(query.priority) && !eq(o.priority, query.priority)) return false
      if (has(query.waveNo) && !eq(o.waveNo, query.waveNo)) return false
      if (has(query.startDate) && (o.payTime || '').slice(0, 10) < query.startDate) return false
      if (has(query.endDate) && (o.payTime || '').slice(0, 10) > query.endDate) return false
      return true
    })
    list.sort((a, b) => {
      if (a.priority !== b.priority) return a.priority === 'URGENT' ? -1 : 1
      return String(b.payTime).localeCompare(String(a.payTime))
    })
    const vos = list.map(toOrderVO)
    return ok(paginate(vos, query))
  }],
  ['POST', '/order/pool-all', () => {
    const targets = db.orders.filter((o) => o.status === 'CREATED')
    targets.forEach((o) => (o.status = 'POOLED'))
    return ok(targets.length, `已汇聚 ${targets.length} 笔订单`)
  }],
  ['POST', '/order/pool', ({ body }) => {
    const targets = db.orders.filter((o) => (body.orderNos || []).includes(o.orderNo) && ['CREATED', 'POOLED'].includes(o.status))
    if (!targets.length) return fail('所选订单中无可汇聚的订单', 20004)
    targets.forEach((o) => (o.status = 'POOLED'))
    return ok(targets.length, `已汇聚 ${targets.length} 笔订单`)
  }],
  ['POST', '/order/:orderNo/cancel', ({ params }) => {
    const o = db.orders.find((x) => x.orderNo === params.orderNo)
    if (!o) return fail('订单不存在', 20001)
    if (!['CREATED', 'POOLED'].includes(o.status)) return fail('订单已进入波次，不能取消', 20002)
    o.status = 'CANCELLED'
    return ok(toOrderVO(o), '订单已取消')
  }],
  ['GET', '/order/:orderNo', ({ params }) => {
    const o = db.orders.find((x) => x.orderNo === params.orderNo)
    return o ? ok(toOrderVO(o)) : fail('订单不存在', 20001)
  }],

  /* ---------------------------- 波次 ---------------------------- */
  ['GET', '/wave/picker-workload', () => {
    const list = db.employees
      .filter((e) => e.role === 'PICKER' && e.status === 1)
      .map((p) => {
        const waiting = db.waves.filter((w) => eq(w.pickerNo, p.employeeNo) && w.status === 'ASSIGNED').length
        const working = db.waves.filter((w) => eq(w.pickerNo, p.employeeNo) && WORKING_WAVE.includes(w.status)).length
        const done = db.waves.filter((w) => eq(w.pickerNo, p.employeeNo) && ['HANDOVERED', 'CLOSED'].includes(w.status)).length
        return { pickerNo: p.employeeNo, pickerName: p.employeeName, team: p.team, waitingWaves: waiting, workingWaves: working, finishedWaves: done, load: waiting + working }
      })
    return ok(list.sort((a, b) => a.load - b.load))
  }],
  ['POST', '/wave/preview', ({ body }) => {
    const candidates = resolveCandidates(body)
    if (!candidates.length) return fail('没有符合条件的订单，无法生成波次', 30003)
    const groups = buildGroups(candidates, body)
    return ok({
      strategy: body.strategy,
      strategyDesc: dictText('waveStrategy', body.strategy),
      waveCount: groups.length,
      totalOrders: candidates.length,
      totalQty: groups.reduce((s, g) => s + g.totalQty, 0),
      groups
    })
  }],
  ['POST', '/wave/generate', ({ body }) => {
    const candidates = resolveCandidates(body)
    if (!candidates.length) return fail('没有符合条件的订单，无法生成波次', 30003)
    const groups = buildGroups(candidates, body)
    const strategy = body.strategy || 'BY_AREA'
    const prefix = has(body.namePrefix) ? body.namePrefix : `${todayStr()} 波次`
    const created = []

    groups.forEach((g, gi) => {
      const waveNo = nextNo('WV')
      const skuSet = new Set()
      const locSet = new Set()
      let totalQty = 0
      g.orders.forEach((o) => {
        db.orderItems
          .filter((i) => i.orderNo === o.orderNo)
          .forEach((it) => {
            skuSet.add(it.skuCode)
            locSet.add(it.locationCode)
            totalQty += it.qty
          })
      })

      const wave = {
        id: nextId(),
        waveNo,
        waveName: `${prefix} ${String(gi + 1).padStart(2, '0')}（${g.groupKey}）`,
        strategy,
        status: 'CREATED',
        orderCount: g.orders.length,
        skuCount: skuSet.size,
        totalQty,
        locationCount: locSet.size,
        provinceScope: g.groupKey,
        creatorNo: body.creatorNo || null,
        creatorName: body.creatorName || '当前用户',
        pickerNo: null,
        pickerName: null,
        reviewerNo: null,
        reviewerName: null,
        createTime: nowStr(),
        assignTime: null,
        pickStartTime: null,
        pickFinishTime: null,
        reviewFinishTime: null,
        handoverTime: null,
        pickDurationSec: null,
        totalDurationSec: null,
        hasShortage: false,
        remark: null
      }
      db.waves.push(wave)

      let seqNo = 1
      g.orders.forEach((o) => {
        db.waveOrders.push({ id: nextId(), waveNo, orderNo: o.orderNo, seqNo: seqNo++, held: false, holdReason: null })
        o.waveNo = waveNo
        o.status = 'WAVED'
      })

      createPickTaskForWave(wave, g.orders)

      const creator = empByNo(body.creatorNo)
      addLog('WAVE', waveNo, 'WAVE_CREATE', '生成波次', creator,
        `策略=${dictText('waveStrategy', strategy)}，订单 ${g.orders.length} 单，${skuSet.size} 个 SKU，共 ${totalQty} 件`)

      if (body.autoAssign) {
        const lightest = db.employees
          .filter((e) => e.role === 'PICKER' && e.status === 1)
          .map((p) => ({ p, load: db.waves.filter((w) => eq(w.pickerNo, p.employeeNo) && (WORKING_WAVE.includes(w.status) || w.status === 'ASSIGNED')).length }))
          .sort((a, b) => a.load - b.load)[0]
        if (lightest) {
          wave.pickerNo = lightest.p.employeeNo
          wave.pickerName = lightest.p.employeeName
          wave.status = 'ASSIGNED'
          wave.assignTime = nowStr()
          db.pickTasks.filter((t) => t.waveNo === waveNo).forEach((t) => {
            t.pickerNo = lightest.p.employeeNo
            t.pickerName = lightest.p.employeeName
          })
        }
      }
      created.push(toWaveVO(wave))
    })

    return ok(created, `已生成 ${created.length} 个波次`)
  }],
  ['POST', '/wave/assign', ({ body }) => {
    const w = waveByNo(body.waveNo)
    if (!w) return fail('波次不存在', 30001)
    if (!['CREATED', 'ASSIGNED'].includes(w.status)) return fail('只有「待分配 / 已分配」的波次可以改派拣货员', 30002)
    const picker = empByNo(body.pickerNo)
    if (!picker) return fail('拣货员不存在', 10002)
    if (picker.role !== 'PICKER') return fail(`${picker.employeeName} 不是拣货员`, 10004)

    w.pickerNo = picker.employeeNo
    w.pickerName = picker.employeeName
    w.status = 'ASSIGNED'
    w.assignTime = nowStr()
    db.pickTasks.filter((t) => t.waveNo === w.waveNo).forEach((t) => {
      t.pickerNo = picker.employeeNo
      t.pickerName = picker.employeeName
    })
    addLog('WAVE', w.waveNo, 'WAVE_ASSIGN', '分配拣货员', empByNo(body.operatorNo), `拣货员=${picker.employeeName}`)
    return ok(toWaveVO(w), '已分配拣货员')
  }],
  ['POST', '/wave/:waveNo/close', ({ params }) => {
    const w = waveByNo(params.waveNo)
    if (!w) return fail('波次不存在', 30001)
    if (w.status !== 'HANDOVERED') return fail('只有「已出库交接」的波次可以归档', 30002)
    w.status = 'CLOSED'
    return ok(toWaveVO(w), '波次已归档')
  }],
  ['POST', '/wave/:waveNo/cancel', ({ params }) => {
    const w = waveByNo(params.waveNo)
    if (!w) return fail('波次不存在', 30001)
    if (!['CREATED', 'ASSIGNED'].includes(w.status)) return fail('只有「待分配 / 已分配」的波次可以取消', 30002)
    const rels = db.waveOrders.filter((r) => r.waveNo === w.waveNo)
    rels.forEach((r) => {
      const o = db.orders.find((x) => x.orderNo === r.orderNo)
      if (o) {
        o.status = 'POOLED'
        o.waveNo = null
      }
    })
    db.waveOrders = db.waveOrders.filter((r) => r.waveNo !== w.waveNo)
    const taskNos = db.pickTasks.filter((t) => t.waveNo === w.waveNo).map((t) => t.taskNo)
    db.pickTasks = db.pickTasks.filter((t) => t.waveNo !== w.waveNo)
    db.pickItems = db.pickItems.filter((i) => !taskNos.includes(i.taskNo))
    w.status = 'CANCELLED'
    addLog('WAVE', w.waveNo, 'WAVE_CANCEL', '取消波次', null, `退回 ${rels.length} 笔订单至订单池`)
    return ok(toWaveVO(w), '波次已取消')
  }],
  ['GET', '/wave/page', ({ query }) => {
    let list = db.waves.filter((w) => {
      if (has(query.waveNo) && !includes(w.waveNo, query.waveNo) && !includes(w.waveName, query.waveNo)) return false
      if (has(query.status) && !eq(w.status, query.status)) return false
      if (has(query.strategy) && !eq(w.strategy, query.strategy)) return false
      if (has(query.pickerNo) && !eq(w.pickerNo, query.pickerNo)) return false
      if (query.onlyWorking === true || query.onlyWorking === 'true') return WORKING_WAVE.includes(w.status)
      if (query.onlyShortage === true || query.onlyShortage === 'true') return !!w.hasShortage
      return true
    })
    list.sort((a, b) => {
      const aw = WORKING_WAVE.includes(a.status) ? 0 : 1
      const bw = WORKING_WAVE.includes(b.status) ? 0 : 1
      if (aw !== bw) return aw - bw
      return String(b.createTime || '').localeCompare(String(a.createTime || ''))
    })
    return ok(paginate(list.map(toWaveVO), query))
  }],
  ['GET', '/wave/:waveNo', ({ params }) => {
    const w = waveByNo(params.waveNo)
    return w ? ok(toWaveVO(w)) : fail('波次不存在', 30001)
  }],

  /* ---------------------------- 拣货 ---------------------------- */
  ['GET', '/pick/my-tasks', ({ query }) => {
    const list = db.pickTasks.filter(
      (t) => eq(t.pickerNo, query.pickerNo) && !['FINISHED', 'CANCELLED'].includes(t.status)
    )
    return ok(list.map(toPickTaskVO))
  }],
  ['POST', '/pick/confirm-item', ({ body }) => {
    const item = db.pickItems.find((i) => i.id === Number(body.itemId))
    if (!item) return fail('拣货明细不存在', 10002)
    const task = taskByNo(item.taskNo)
    if (!task) return fail('拣货任务不存在', 40001)
    if (!['PICKING', 'WAITING'].includes(task.status)) return fail('任务已结单，不能继续确认', 40002)
    if (Number(body.actualQty) > item.shouldQty) {
      return fail(`实拣 ${body.actualQty} 件超过应拣 ${item.shouldQty} 件，请改用缺货上报或多货处理`, 40003)
    }

    item.actualQty = Number(body.actualQty)
    item.scanTime = nowStr()
    if (item.actualQty === item.shouldQty) {
      item.status = 'PICKED'
      item.shortageQty = 0
    } else {
      const shortQty = item.shouldQty - item.actualQty
      item.shortageQty = shortQty
      item.status = 'PARTIAL'
      upsertShortage(item, shortQty, 'STOCK_ZERO', task, body.remark)
    }
    if (body.remark) item.remark = body.remark

    if (!task.startTime) {
      task.startTime = nowStr()
      const w = waveByNo(task.waveNo)
      if (w && !w.pickStartTime) {
        w.pickStartTime = nowStr()
        if (['CREATED', 'ASSIGNED'].includes(w.status)) w.status = 'PICKING'
      }
    }
    recalcTask(task)
    return ok(toPickTaskVO(task))
  }],
  ['POST', '/pick/report-shortage', ({ body }) => {
    const item = db.pickItems.find((i) => i.id === Number(body.itemId))
    if (!item) return fail('拣货明细不存在', 10002)
    const task = taskByNo(item.taskNo)
    if (!task) return fail('拣货任务不存在', 40001)
    if (!['PICKING', 'WAITING'].includes(task.status)) return fail('任务已结单，不能上报缺货', 40002)
    const shortQty = Number(body.shortageQty)
    if (shortQty > item.shouldQty) return fail('缺货数量不能大于应拣数量', 40003)

    item.shortageQty = shortQty
    item.actualQty = item.shouldQty - shortQty
    item.status = item.actualQty === 0 ? 'SHORTAGE' : 'PARTIAL'
    item.scanTime = nowStr()
    if (body.remark) item.remark = body.remark

    upsertShortage(item, shortQty, body.reason || 'STOCK_ZERO', task, body.remark)

    const w = waveByNo(task.waveNo)
    if (w) {
      w.hasShortage = true
      if (!w.pickStartTime) w.pickStartTime = nowStr()
      if (['CREATED', 'ASSIGNED'].includes(w.status)) w.status = 'PICKING'
    }
    if (!task.startTime) task.startTime = nowStr()
    recalcTask(task)
    return ok(toPickTaskVO(task), '缺货已上报，等待波次员处理')
  }],
  ['POST', '/pick/:taskNo/start', ({ params, query }) => {
    const task = taskByNo(params.taskNo)
    if (!task) return fail('拣货任务不存在', 40001)
    if (!['WAITING', 'PICKING'].includes(task.status)) return fail('任务当前状态不允许开工', 40002)
    if (has(query.pickerNo) && !eq(query.pickerNo, task.pickerNo)) {
      return fail(`该任务已分配给 ${task.pickerName}`, 40004)
    }
    if (task.status === 'WAITING') {
      task.status = 'PICKING'
      task.startTime = nowStr()
      const w = waveByNo(task.waveNo)
      if (w) {
        if (!w.pickStartTime) w.pickStartTime = nowStr()
        if (['CREATED', 'ASSIGNED'].includes(w.status)) w.status = 'PICKING'
      }
      addLog('PICK', task.taskNo, 'PICK_START', '开始拣货', empByNo(task.pickerNo), `波次 ${task.waveNo}`)
    }
    return ok(toPickTaskVO(task), '已开始拣货')
  }],
  ['POST', '/pick/:taskNo/finish', ({ params }) => {
    const task = taskByNo(params.taskNo)
    if (!task) return fail('拣货任务不存在', 40001)
    if (task.status === 'FINISHED') return fail('任务已结单', 40002)
    const items = db.pickItems.filter((i) => i.taskNo === task.taskNo)
    const pending = items.filter((i) => i.status === 'PENDING').length
    if (pending > 0) return fail(`还有 ${pending} 行未处理，请先逐行确认或上报缺货`, 40005)
    if (!task.startTime) task.startTime = nowStr()
    recalcTask(task)
    if (task.status !== 'FINISHED' && task.status !== 'EXCEPTION') finishTaskInternal(task, items)
    return ok(toPickTaskVO(task), '拣货任务已结单')
  }],
  ['GET', '/pick/page', ({ query }) => {
    let list = db.pickTasks.filter((t) => {
      if (has(query.taskNo) && !includes(t.taskNo, query.taskNo)) return false
      if (has(query.waveNo) && !eq(t.waveNo, query.waveNo)) return false
      if (has(query.pickerNo) && !eq(t.pickerNo, query.pickerNo)) return false
      if (has(query.status) && !eq(t.status, query.status)) return false
      return true
    })
    list.sort((a, b) => {
      const af = a.status === 'FINISHED' ? 1 : 0
      const bf = b.status === 'FINISHED' ? 1 : 0
      if (af !== bf) return af - bf
      return String(b.createTime || '').localeCompare(String(a.createTime || ''))
    })
    return ok(paginate(list.map(toPickTaskVO), query))
  }],
  ['GET', '/pick/:taskNo', ({ params }) => {
    const t = taskByNo(params.taskNo)
    return t ? ok(toPickTaskVO(t)) : fail('拣货任务不存在', 40001)
  }],

  /* ---------------------------- 缺货 ---------------------------- */
  ['GET', '/shortage/pending-count', () => ok(db.shortages.filter((s) => s.status === 'PENDING').length)],
  ['GET', '/shortage/reason-distribution', () => {
    const map = {}
    db.shortages.forEach((s) => {
      const name = dictText('shortageReason', s.reason)
      map[name] = (map[name] || 0) + 1
    })
    return ok(map)
  }],
  ['POST', '/shortage/handle', ({ body }) => {
    const rec = db.shortages.find((s) => s.id === Number(body.id))
    if (!rec) return fail('缺货记录不存在', 70001)
    if (rec.status !== 'PENDING') return fail('该缺货记录已处理，不能重复处理', 70002)
    const valid = ENUMS.shortageHandleType.map((i) => i.code)
    if (!valid.includes(body.handleType)) return fail(`不支持的缺货处理方式: ${body.handleType}`, 70003)

    if (body.handleType === 'SUBSTITUTE') {
      if (!has(body.substituteSkuCode)) return fail('选择「替代商品」时必须指定替代 SKU', 10001)
      rec.substituteSkuCode = body.substituteSkuCode
      const pi = db.pickItems.find(
        (i) => eq(i.taskNo, rec.taskNo) && eq(i.skuCode, rec.skuCode) && eq(i.locationCode, rec.locationCode)
      )
      if (pi) pi.remark = `缺货替代：${rec.skuCode} → ${body.substituteSkuCode}`
    }
    if (body.handleType === 'CANCEL_ORDER' && has(rec.orderNo)) {
      const o = db.orders.find((x) => x.orderNo === rec.orderNo)
      if (o) o.status = 'CANCELLED'
    }
    if (body.handleType === 'CANCEL_ITEM' && has(rec.orderNo)) {
      db.orderItems
        .filter((i) => eq(i.orderNo, rec.orderNo) && eq(i.skuCode, rec.skuCode))
        .forEach((i) => {
          i.qty = 0
          i.pickedQty = 0
          i.remark = '缺货取消'
        })
    }
    if (body.handleType === 'SPLIT' && has(rec.orderNo)) {
      const o = db.orders.find((x) => x.orderNo === rec.orderNo)
      if (o) {
        const tag = `[缺货拆单] ${rec.skuCode} 缺 ${rec.shortageQty} 件`
        o.buyerRemark = o.buyerRemark ? `${o.buyerRemark}；${tag}` : tag
      }
    }

    rec.handleType = body.handleType
    rec.handlerNo = body.handlerNo || null
    rec.handlerName = body.handlerName || '当前用户'
    rec.handleTime = nowStr()
    rec.handleRemark = body.handleRemark || null
    rec.status = 'HANDLED'

    addLog('SHORTAGE', rec.shortageNo, 'SHORTAGE_HANDLE', '缺货处理', null,
      `处理方式=${dictText('shortageHandleType', body.handleType)}；${body.handleRemark || ''}`)
    return ok(rec, '缺货已处理')
  }],
  ['POST', '/shortage/:id/close', ({ params }) => {
    const rec = db.shortages.find((s) => s.id === Number(params.id))
    if (!rec) return fail('缺货记录不存在', 70001)
    if (rec.status !== 'HANDLED') return fail('只有已处理的记录可以归档', 70002)
    rec.status = 'CLOSED'
    return ok(rec, '已归档')
  }],
  ['GET', '/shortage/page', ({ query }) => {
    let list = db.shortages.filter((s) => {
      if (has(query.shortageNo) && !includes(s.shortageNo, query.shortageNo)) return false
      if (has(query.waveNo) && !eq(s.waveNo, query.waveNo)) return false
      if (has(query.skuCode) && !eq(s.skuCode, query.skuCode)) return false
      if (has(query.status) && !eq(s.status, query.status)) return false
      if (has(query.reason) && !eq(s.reason, query.reason)) return false
      return true
    })
    list.sort((a, b) => {
      const ap = a.status === 'PENDING' ? 0 : 1
      const bp = b.status === 'PENDING' ? 0 : 1
      if (ap !== bp) return ap - bp
      return String(b.reportTime || '').localeCompare(String(a.reportTime || ''))
    })
    return ok(paginate(list, query))
  }],
  ['GET', '/shortage/:id', ({ params }) => {
    const s = db.shortages.find((x) => x.id === Number(params.id))
    return s ? ok(s) : fail('缺货记录不存在', 70001)
  }],

  /* ---------------------------- 复核 ---------------------------- */
  ['GET', '/review/pending-waves', () =>
    ok(db.waves.filter((w) => ['PICKED', 'REVIEWING'].includes(w.status)).sort((a, b) => String(a.pickFinishTime || '').localeCompare(String(b.pickFinishTime || ''))))],
  ['POST', '/review/claim', ({ query }) => {
    const w = waveByNo(query.waveNo)
    if (!w) return fail('波次不存在', 30001)
    if (!['PICKED', 'REVIEWING'].includes(w.status)) return fail('只有「拣货完成」的波次可以进入复核', 30002)

    const exists = db.reviews.filter((r) => r.waveNo === w.waveNo)
    if (!exists.length) {
      const rels = db.waveOrders.filter((r) => r.waveNo === w.waveNo).sort((a, b) => a.seqNo - b.seqNo)
      rels.forEach((wo) => {
        const items = db.orderItems.filter((i) => i.orderNo === wo.orderNo).sort((a, b) => a.lineNo - b.lineNo)
        const shouldQty = items.reduce((s, i) => s + i.qty, 0)
        const actualQty = items.reduce((s, i) => s + (i.pickedQty || 0), 0)
        db.reviews.push({
          id: nextId(),
          reviewNo: nextNo('RV'),
          waveNo: w.waveNo,
          orderNo: wo.orderNo,
          reviewerNo: query.reviewerNo,
          reviewerName: query.reviewerName || '当前用户',
          status: 'WAITING',
          shouldSkuCount: items.length,
          shouldQty,
          actualQty,
          diffType: 'NONE',
          diffQty: 0,
          diffDetail: null,
          packMaterial: '纸箱',
          packageCount: 1,
          weight: null,
          startTime: null,
          finishTime: null,
          durationSec: null,
          remark: null,
          createTime: nowStr()
        })
      })
      w.status = 'REVIEWING'
      w.reviewerNo = query.reviewerNo
      w.reviewerName = query.reviewerName || '当前用户'
      addLog('REVIEW', w.waveNo, 'REVIEW_CLAIM', '认领复核', null, `波次 ${w.waveNo}`)
    }
    return ok(db.reviews.filter((r) => r.waveNo === w.waveNo).map(toReviewVO), '已认领波次')
  }],
  ['POST', '/review/submit', ({ body }) => {
    const rec = db.reviews.find((r) => r.reviewNo === body.reviewNo)
    if (!rec) return fail('复核单不存在', 50001)
    if (!['WAITING', 'DIFF'].includes(rec.status)) return fail('该复核单已提交，不能重复提交', 50002)

    const now = new Date()
    if (!rec.startTime) rec.startTime = fmt(now)
    rec.finishTime = fmt(now)
    rec.durationSec = Math.round((now - new Date(rec.startTime)) / 1000)
    if (body.reviewerNo) rec.reviewerNo = body.reviewerNo
    if (body.reviewerName) rec.reviewerName = body.reviewerName
    if (body.packMaterial) rec.packMaterial = body.packMaterial
    rec.packageCount = body.packageCount ?? 1
    rec.weight = body.weight ?? null
    rec.remark = body.remark || null
    if (body.actualQty !== undefined && body.actualQty !== null) rec.actualQty = Number(body.actualQty)

    // 清理旧差异
    db.reviewDiffs = db.reviewDiffs.filter((d) => d.reviewNo !== rec.reviewNo)

    const diffs = body.diffItems || []
    if (!diffs.length) {
      rec.status = 'PASS'
      rec.diffType = 'NONE'
      rec.diffQty = 0
      rec.diffDetail = null
    } else {
      let totalDiff = 0
      const descs = []
      let primary = 'NONE'
      diffs.forEach((d) => {
        const type = d.diffType || 'SHORT'
        const shouldQty = Number(d.shouldQty) || 0
        const actualQty = Number(d.actualQty) || 0
        const diffQty = actualQty - shouldQty
        db.reviewDiffs.push({
          id: nextId(),
          reviewNo: rec.reviewNo,
          waveNo: rec.waveNo,
          orderNo: rec.orderNo,
          skuCode: d.skuCode,
          skuName: d.skuName,
          diffType: type,
          shouldQty,
          actualQty,
          diffQty,
          responsibleStage: d.responsibleStage || 'PICK',
          responsibleNo: d.responsibleNo || null,
          remark: d.remark || null,
          createTime: nowStr()
        })
        totalDiff += Math.abs(diffQty)
        descs.push(`${dictText('diffType', type)} ${d.skuName}（应 ${shouldQty} 实 ${actualQty}）`)
        if (primary === 'NONE') primary = type
      })
      rec.status = 'DIFF'
      rec.diffType = primary
      rec.diffQty = totalDiff
      rec.diffDetail = descs.join('；')
    }

    const o = db.orders.find((x) => x.orderNo === rec.orderNo)
    if (o) o.status = 'REVIEWED'

    // 波次内全部复核完 → 推进
    const all = db.reviews.filter((r) => r.waveNo === rec.waveNo)
    if (all.length && all.every((r) => r.status !== 'WAITING')) {
      const w = waveByNo(rec.waveNo)
      if (w) {
        w.status = 'REVIEWED'
        w.reviewFinishTime = nowStr()
        addLog('REVIEW', w.waveNo, 'REVIEW_FINISH', '复核完成', empByNo(rec.reviewerNo), `波次 ${w.waveNo} 共复核 ${all.length} 单`)
      }
    }

    addLog('REVIEW', rec.reviewNo, 'REVIEW_SUBMIT', '提交复核', empByNo(rec.reviewerNo),
      rec.status === 'PASS' ? '复核通过' : `存在差异：${rec.diffDetail}`)
    return ok(toReviewVO(rec), '复核结果已提交')
  }],
  ['GET', '/review/page', ({ query }) => {
    let list = db.reviews.filter((r) => {
      if (has(query.reviewNo) && !includes(r.reviewNo, query.reviewNo)) return false
      if (has(query.waveNo) && !eq(r.waveNo, query.waveNo)) return false
      if (has(query.orderNo) && !eq(r.orderNo, query.orderNo)) return false
      if (has(query.status) && !eq(r.status, query.status)) return false
      if (has(query.diffType) && !eq(r.diffType, query.diffType)) return false
      if (has(query.reviewerNo) && !eq(r.reviewerNo, query.reviewerNo)) return false
      return true
    })
    list.sort((a, b) => {
      const aw = a.status === 'WAITING' ? 0 : 1
      const bw = b.status === 'WAITING' ? 0 : 1
      if (aw !== bw) return aw - bw
      return String(b.finishTime || b.createTime || '').localeCompare(String(a.finishTime || a.createTime || ''))
    })
    return ok(paginate(list.map(toReviewVO), query))
  }],
  ['GET', '/review/:reviewNo', ({ params }) => {
    const r = db.reviews.find((x) => x.reviewNo === params.reviewNo)
    return r ? ok(toReviewVO(r)) : fail('复核单不存在', 50001)
  }],

  /* ---------------------------- 交接 ---------------------------- */
  ['GET', '/handover/reviewable-waves', ({ query }) => {
    const list = db.waves
      .filter((w) => w.status === 'REVIEWED')
      .filter((w) => {
        if (!has(query.carrierCode)) return true
        const rel = db.waveOrders.find((r) => r.waveNo === w.waveNo)
        const o = rel ? db.orders.find((x) => x.orderNo === rel.orderNo) : null
        return o && eq(o.carrierCode, query.carrierCode)
      })
    return ok(list)
  }],
  ['POST', '/handover/create', ({ body }) => {
    const carrier = db.carriers.find((c) => c.carrierCode === body.carrierCode)
    if (!carrier) return fail('承运商不存在', 10002)

    const waves = []
    for (const waveNo of body.waveNos || []) {
      const w = waveByNo(waveNo)
      if (!w) return fail('波次不存在', 30001)
      if (w.status !== 'REVIEWED') return fail(`波次 ${waveNo} 当前状态为「${dictText('waveStatus', w.status)}」，不能交接`, 60003)
      waves.push(w)
    }

    let orderCount = 0
    let packageCount = 0
    let weight = 0
    waves.forEach((w) => {
      const reviews = db.reviews.filter((r) => r.waveNo === w.waveNo)
      orderCount += w.orderCount || 0
      packageCount += reviews.reduce((s, r) => s + (r.packageCount || 1), 0)
      weight += reviews.reduce((s, r) => s + (r.weight || 0), 0)
    })

    const ho = {
      id: nextId(),
      handoverNo: nextNo('HO'),
      waveNos: (body.waveNos || []).join(','),
      carrierCode: carrier.carrierCode,
      carrierName: carrier.carrierName,
      orderCount,
      packageCount,
      totalWeight: Number(weight.toFixed(2)),
      status: 'CREATED',
      handoverUserNo: body.handoverUserNo || null,
      handoverUserName: body.handoverUserName || '当前用户',
      receiveName: null,
      vehicleNo: body.vehicleNo || null,
      createTime: nowStr(),
      handoverTime: null,
      remark: body.remark || null
    }
    db.handovers.push(ho)
    addLog('HANDOVER', ho.handoverNo, 'HANDOVER_CREATE', '创建交接单', null,
      `承运商=${carrier.carrierName}，波次=${ho.waveNos}，订单 ${orderCount} 单 / 包裹 ${packageCount} 件`)
    return ok(ho, '交接单已创建')
  }],
  ['POST', '/handover/confirm', ({ body }) => {
    const ho = db.handovers.find((h) => h.handoverNo === body.handoverNo)
    if (!ho) return fail('交接单不存在', 60001)
    if (ho.status !== 'CREATED') return fail('该交接单已处理，不能重复确认', 60002)

    const now = nowStr()
    if (body.rejected) {
      ho.status = 'REJECTED'
      ho.receiveName = body.receiveName || null
      ho.handoverTime = now
      ho.remark = body.remark || null
      addLog('HANDOVER', ho.handoverNo, 'HANDOVER_REJECT', '承运商拒收', null, body.remark || '')
      return ok(ho, '已记录拒收')
    }

    if (body.actualPackageCount != null && Number(body.actualPackageCount) !== ho.packageCount) {
      const diff = `实交 ${body.actualPackageCount} 件，系统 ${ho.packageCount} 件`
      ho.remark = ho.remark ? `${ho.remark}；[件数差异] ${diff}` : `[件数差异] ${diff}`
    }
    ho.status = 'CONFIRMED'
    ho.receiveName = body.receiveName || null
    ho.handoverTime = now
    if (body.actualPackageCount != null) ho.packageCount = Number(body.actualPackageCount)
    if (body.remark) ho.remark = ho.remark ? `${ho.remark}；${body.remark}` : body.remark

    // 推进波次与订单
    String(ho.waveNos).split(',').forEach((waveNo) => {
      const w = waveByNo(waveNo.trim())
      if (!w) return
      w.status = 'HANDOVERED'
      w.handoverTime = now
      if (w.createTime) {
        w.totalDurationSec = Math.round((new Date(now) - new Date(w.createTime)) / 1000)
      }
      db.waveOrders
        .filter((r) => r.waveNo === w.waveNo)
        .forEach((r) => {
          const o = db.orders.find((x) => x.orderNo === r.orderNo)
          if (o) o.status = 'OUTBOUND'
        })
    })

    addLog('HANDOVER', ho.handoverNo, 'HANDOVER_CONFIRM', '交接完成', null,
      `接收人=${body.receiveName || '—'}，件数 ${ho.packageCount}`)
    return ok(ho, '交接已完成')
  }],
  ['GET', '/handover/page', ({ query }) => {
    let list = db.handovers.filter((h) => {
      if (has(query.handoverNo) && !includes(h.handoverNo, query.handoverNo)) return false
      if (has(query.carrierCode) && !eq(h.carrierCode, query.carrierCode)) return false
      if (has(query.status) && !eq(h.status, query.status)) return false
      return true
    })
    list.sort((a, b) => {
      const ac = a.status === 'CREATED' ? 0 : 1
      const bc = b.status === 'CREATED' ? 0 : 1
      if (ac !== bc) return ac - bc
      return String(b.createTime || '').localeCompare(String(a.createTime || ''))
    })
    return ok(paginate(list, query))
  }],
  ['GET', '/handover/:handoverNo', ({ params }) => {
    const h = db.handovers.find((x) => x.handoverNo === params.handoverNo)
    return h ? ok(h) : fail('交接单不存在', 60001)
  }],

  /* ---------------------------- 效率复盘 ---------------------------- */
  ['GET', '/analytics/overview', ({ query }) => ok(computeOverview(query))],
  ['GET', '/analytics/employee-efficiency', ({ query }) => ok(computeEmployeeEfficiency(query))],
  ['GET', '/analytics/error-rate', ({ query }) => ok(computeErrorRate(query))],
  ['GET', '/analytics/trend', ({ query }) => ok(computeTrend(query))],
  ['GET', '/analytics/shortage-distribution', ({ query }) => {
    const recs = db.shortages.filter((s) => inRange(s.reportTime || s.createTime, query))
    const map = new Map()
    recs.forEach((s) => map.set(s.reason, (map.get(s.reason) || 0) + 1))
    const total = recs.length || 1
    return ok(
      [...map.entries()]
        .map(([code, count]) => ({ code, name: dictText('shortageReason', code), count, ratio: div(count, total, 4) }))
        .sort((a, b) => b.count - a.count)
    )
  }],

  /* ---------------------------- 基础数据 ---------------------------- */
  ['GET', '/base/enums', () => ok(ENUMS)],
  ['GET', '/base/sku/categories', () => ok([...new Set(db.skus.map((s) => s.category))].sort())],
  ['GET', '/base/sku/page', ({ query }) => {
    let list = db.skus.filter((s) => {
      if (has(query.keyword) && !includes(s.skuCode, query.keyword) && !includes(s.skuName, query.keyword)) return false
      if (has(query.category) && !eq(s.category, query.category)) return false
      return true
    })
    return ok(paginate(list, query))
  }],
  ['GET', '/base/location/zones', () => ok([...new Set(db.locations.map((l) => l.zone))].sort())],
  ['GET', '/base/location/page', ({ query }) => {
    let list = db.locations.filter((l) => !has(query.zone) || eq(l.zone, query.zone))
    list = list.slice().sort((a, b) => a.pathSeq - b.pathSeq)
    return ok(paginate(list, query))
  }],
  ['GET', '/base/employee/by-role', ({ query }) =>
    ok(db.employees.filter((e) => eq(e.role, query.role) && e.status === 1))],
  ['GET', '/base/employee/teams', () => ok([...new Set(db.employees.map((e) => e.team))].sort())],
  ['GET', '/base/employee/page', ({ query }) => {
    let list = db.employees.filter((e) => {
      if (has(query.role) && !eq(e.role, query.role)) return false
      if (has(query.team) && !eq(e.team, query.team)) return false
      return true
    })
    return ok(paginate(list, query))
  }],
  ['GET', '/base/carrier/list', () => ok(db.carriers.filter((c) => c.status === 1))],

  /* ---------------------------- 作业追溯 ---------------------------- */
  ['GET', '/log/trace', ({ query }) =>
    ok(db.logs.filter((l) => eq(l.bizNo, query.bizNo)).sort((a, b) => String(a.operateTime).localeCompare(String(b.operateTime))))],
  ['GET', '/log/page', ({ query }) => {
    let list = db.logs.filter((l) => {
      if (has(query.bizType) && !eq(l.bizType, query.bizType)) return false
      if (has(query.bizNo) && !eq(l.bizNo, query.bizNo)) return false
      if (has(query.operatorNo) && !eq(l.operatorNo, query.operatorNo)) return false
      return true
    })
    list = sortedBy(list, 'operateTime', true)
    return ok(paginate(list, query))
  }]
]

/* ========================================================================== */
/* 适配器                                                                      */
/* ========================================================================== */

function todayStr() {
  return new Date().toISOString().slice(0, 10)
}

function daysAgoStr(n) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d.toISOString().slice(0, 10)
}

/**
 * 把 '/wave/:waveNo/close' 编译成正则，并抽出参数名。
 *
 * 按「段」处理：以 : 开头的段视为路径参数，其余段做正则转义。
 * 注意不要先整体转义再找 `\/:name`——`/` 不在转义字符集里，那样写永远匹配不到参数段。
 */
function compile(pattern) {
  const names = []
  const source = pattern
    .split('/')
    .map((seg) => {
      if (seg.startsWith(':')) {
        names.push(seg.slice(1))
        return '([^/]+)'
      }
      return seg.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    })
    .join('/')
  return { regex: new RegExp(`^${source}/?$`), names }
}

const compiled = routes.map(([method, pattern, handler]) => ({
  method,
  handler,
  ...compile(pattern)
}))

function matchRoute(method, path) {
  for (const r of compiled) {
    if (r.method !== method) continue
    const m = r.regex.exec(path)
    if (m) {
      const params = {}
      r.names.forEach((n, i) => (params[n] = decodeURIComponent(m[i + 1])))
      return { handler: r.handler, params }
    }
  }
  return null
}

/** 解析 URL 上的查询串 */
function parseQuery(url) {
  const idx = url.indexOf('?')
  const q = {}
  if (idx < 0) return q
  new URLSearchParams(url.slice(idx + 1)).forEach((v, k) => {
    if (k === '_t') return
    q[k] = v
  })
  return q
}

/** 模拟网络延迟，让 loading 状态可见（真实感更强） */
function delay() {
  return 60 + Math.floor(Math.random() * 120)
}

/**
 * axios 自定义 adapter。
 * 返回结构必须与 axios 的标准响应一致，否则响应拦截器拿不到数据。
 */
export function mockAdapter(config) {
  return new Promise((resolve) => {
    initDb()

    const method = (config.method || 'get').toUpperCase()
    let url = config.url || ''
    // 去掉 baseURL 前缀，统一成 /xxx 形式
    const base = config.baseURL || ''
    if (base && url.startsWith(base)) url = url.slice(base.length)
    if (!url.startsWith('/')) url = `/${url}`

    const path = url.split('?')[0]
    const query = { ...parseQuery(url), ...(config.params || {}) }
    delete query._t

    let body = {}
    if (config.data) {
      if (typeof config.data === 'string') {
        try {
          body = JSON.parse(config.data)
        } catch {
          body = {}
        }
      } else {
        body = config.data
      }
    }

    const matched = matchRoute(method, path)

    setTimeout(() => {
      let payload
      if (!matched) {
        payload = fail(`[Mock] 未实现的接口: ${method} ${path}`, 10002)
      } else {
        try {
          payload = matched.handler({ params: matched.params, query, body })
        } catch (e) {
          console.error('[Mock] handler 异常', method, path, e)
          payload = fail(`[Mock] 处理异常: ${e.message}`, 90000)
        }
      }

      resolve({
        data: payload,
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
        request: { mock: true }
      })
    }, delay())
  })
}

export default { mockAdapter }
