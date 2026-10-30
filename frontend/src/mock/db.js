/**
 * Mock 数据层 —— 内存数据库。
 *
 * 本文件是后端 `MockDataInitializer` 的前端等价实现：
 * 结构、字段名、状态枚举与后端保持完全一致，
 * 这样页面在 mock 模式与真实接口模式之间切换时，渲染逻辑无需任何改动。
 *
 * 数据只存活在页面内存中，刷新即回到初始状态。
 */

/* ========================================================================== */
/* 一、枚举字典（与后端 /api/base/enums 完全一致）                              */
/* ========================================================================== */

export const ENUMS = {
  orderStatus: [
    { code: 'CREATED', desc: '新建待汇聚' },
    { code: 'POOLED', desc: '已汇聚入池' },
    { code: 'WAVED', desc: '已分配波次' },
    { code: 'PICKED', desc: '已拣货' },
    { code: 'REVIEWED', desc: '已复核' },
    { code: 'OUTBOUND', desc: '已出库' },
    { code: 'CANCELLED', desc: '已取消' }
  ],
  waveStatus: [
    { code: 'CREATED', desc: '待分配' },
    { code: 'ASSIGNED', desc: '已分配拣货员' },
    { code: 'PICKING', desc: '拣货中' },
    { code: 'PICKED', desc: '拣货完成' },
    { code: 'REVIEWING', desc: '复核中' },
    { code: 'REVIEWED', desc: '复核完成' },
    { code: 'HANDOVERED', desc: '已出库交接' },
    { code: 'CLOSED', desc: '已归档' },
    { code: 'CANCELLED', desc: '已取消' }
  ],
  waveStrategy: [
    { code: 'BY_AREA', desc: '按收货区域聚类', detail: '同一省份/城市的订单聚成一个波次，便于后续按线路分拣与装车。' },
    { code: 'BY_CARRIER', desc: '按承运商聚类', detail: '同一快递承运商的订单聚成一个波次，交接环节只需面对一个承运商。' },
    { code: 'BY_TIMELINESS', desc: '按时效优先级聚类', detail: '加急/当日达等高优先级订单单独成波，保证时效订单优先出库。' },
    { code: 'BY_ORDER_COUNT', desc: '按单量均衡聚类', detail: '按顺序均匀切分成多个波次，使各拣货员工作量大致持平。' }
  ],
  pickTaskStatus: [
    { code: 'WAITING', desc: '待领取' },
    { code: 'PICKING', desc: '拣货中' },
    { code: 'FINISHED', desc: '已完成' },
    { code: 'EXCEPTION', desc: '有缺货待处理' },
    { code: 'CANCELLED', desc: '已取消' }
  ],
  pickItemStatus: [
    { code: 'PENDING', desc: '待拣' },
    { code: 'PICKED', desc: '已拣齐' },
    { code: 'SHORTAGE', desc: '缺货' },
    { code: 'PARTIAL', desc: '部分拣货' },
    { code: 'SKIPPED', desc: '已跳过' }
  ],
  reviewStatus: [
    { code: 'WAITING', desc: '待复核' },
    { code: 'PASS', desc: '复核通过' },
    { code: 'DIFF', desc: '存在差异' },
    { code: 'REPICKING', desc: '已退回重拣' }
  ],
  diffType: [
    { code: 'NONE', desc: '无差异' },
    { code: 'SHORT', desc: '少货' },
    { code: 'OVER', desc: '多货' },
    { code: 'WRONG_SKU', desc: '错货' },
    { code: 'DAMAGED', desc: '破损' },
    { code: 'WRONG_ORDER', desc: '串单' }
  ],
  shortageStatus: [
    { code: 'PENDING', desc: '待波次员处理' },
    { code: 'HANDLED', desc: '已处理' },
    { code: 'CLOSED', desc: '已归档' }
  ],
  shortageReason: [
    { code: 'STOCK_ZERO', desc: '库位实物为零' },
    { code: 'STOCK_DIFF', desc: '账实不符' },
    { code: 'LOCATION_WRONG', desc: '库位商品错放' },
    { code: 'DAMAGED', desc: '商品破损不可售' },
    { code: 'EXPIRED', desc: '临期/过期' },
    { code: 'LOCKED', desc: '已被其他波次锁定' },
    { code: 'UNKNOWN', desc: '原因待查' }
  ],
  shortageHandleType: [
    { code: 'SPLIT', desc: '拆单出库', detail: '缺货部分拆分为新订单挂起，其余商品正常出库。' },
    { code: 'CANCEL_ITEM', desc: '取消该明细', detail: '直接取消缺货商品行，订单其余部分继续流转。' },
    { code: 'SUBSTITUTE', desc: '替代商品', detail: '用同品类可替代 SKU 顶替，需人工确认替代关系。' },
    { code: 'WAIT_REPLENISH', desc: '等待补货', detail: '挂起该波次，等待补货上架后继续拣货。' },
    { code: 'CANCEL_ORDER', desc: '整单取消', detail: '缺货导致整单无法履约，取消订单并通知客户。' }
  ],
  handoverStatus: [
    { code: 'CREATED', desc: '待交接' },
    { code: 'CONFIRMED', desc: '已交接' },
    { code: 'REJECTED', desc: '承运商拒收' },
    { code: 'CANCELLED', desc: '已作废' }
  ],
  employeeRole: [
    { code: 'WAVE_OPERATOR', desc: '波次员' },
    { code: 'PICKER', desc: '拣货员' },
    { code: 'REVIEWER', desc: '复核员' },
    { code: 'ADMIN', desc: '仓库主管' }
  ],
  locationStatus: [
    { code: 'ENABLED', desc: '可用' },
    { code: 'LOCKED', desc: '已锁定' },
    { code: 'FULL', desc: '已满' },
    { code: 'DISABLED', desc: '停用' }
  ],
  priority: [
    { code: 'URGENT', desc: '加急' },
    { code: 'NORMAL', desc: '普通' }
  ],
  platform: [
    { code: '天猫', desc: '天猫' },
    { code: '京东', desc: '京东' },
    { code: '拼多多', desc: '拼多多' },
    { code: '抖音商城', desc: '抖音商城' },
    { code: '美团优选', desc: '美团优选' }
  ],
  packMaterial: [
    { code: '纸箱', desc: '纸箱' },
    { code: '气泡袋', desc: '气泡袋' },
    { code: '保温箱', desc: '保温箱' }
  ],
  granularity: [
    { code: 'DAY', desc: '按天' },
    { code: 'WEEK', desc: '按周' }
  ]
}

/** 字典查中文：dictText('waveStatus', 'PICKING') => '拣货中' */
export function dictText(group, code) {
  const item = (ENUMS[group] || []).find((i) => i.code === code)
  return item ? item.desc : code || ''
}

/* ========================================================================== */
/* 二、种子常量                                                                */
/* ========================================================================== */

const PLATFORMS = ['天猫', '京东', '拼多多', '抖音商城', '美团优选']

const CARRIERS = [
  ['SF', '顺丰速运', '陈师傅', '13800000001', '18:00'],
  ['JD', '京东物流', '林师傅', '13800000002', '17:30'],
  ['ZTO', '中通快递', '黄师傅', '13800000003', '19:00'],
  ['YTO', '圆通速递', '王师傅', '13800000004', '18:30'],
  ['STO', '申通快递', '李师傅', '13800000005', '19:30']
]

const AREAS = [
  ['广东省', '深圳市', '南山区科技园路'],
  ['广东省', '广州市', '天河区体育西路'],
  ['浙江省', '杭州市', '西湖区文三路'],
  ['浙江省', '宁波市', '鄞州区中山东路'],
  ['江苏省', '南京市', '鼓楼区中山北路'],
  ['江苏省', '苏州市', '工业园区星湖街'],
  ['上海市', '上海市', '浦东新区张江路'],
  ['北京市', '北京市', '朝阳区建国路'],
  ['四川省', '成都市', '武侯区人民南路'],
  ['湖北省', '武汉市', '洪山区珞喻路']
]

const SKUS = [
  ['红富士苹果', '5kg/箱', '箱', '仁果类'],
  ['新疆库尔勒香梨', '4kg/箱', '箱', '仁果类'],
  ['海南贵妃芒', '3kg/箱', '箱', '热带水果'],
  ['智利车厘子', '2kg/盒', '盒', '浆果类'],
  ['泰国金枕榴莲', '1个', '个', '热带水果'],
  ['赣南脐橙', '5kg/箱', '箱', '柑橘类'],
  ['越南红心火龙果', '2kg/箱', '箱', '热带水果'],
  ['山东沾化冬枣', '2kg/箱', '箱', '核果类'],
  ['陕西猕猴桃', '3kg/箱', '箱', '浆果类'],
  ['广西百香果', '1kg/袋', '袋', '浆果类'],
  ['云南蓝莓', '125g/盒', '盒', '浆果类'],
  ['智利牛油果', '6个/盒', '盒', '热带水果'],
  ['菲律宾香蕉', '5kg/箱', '箱', '热带水果'],
  ['福建蜜柚', '2个/袋', '袋', '柑橘类'],
  ['四川丑橘', '5kg/箱', '箱', '柑橘类'],
  ['台湾凤梨', '2个/箱', '箱', '热带水果'],
  ['甘肃花牛苹果', '4kg/箱', '箱', '仁果类'],
  ['山东烟台大樱桃', '1kg/盒', '盒', '核果类'],
  ['新疆哈密瓜', '1个', '个', '瓜类'],
  ['海南小台农芒果', '5kg/箱', '箱', '热带水果'],
  ['广西沃柑', '5kg/箱', '箱', '柑橘类'],
  ['云南红提', '2kg/箱', '箱', '浆果类'],
  ['陕西冬枣', '2kg/箱', '箱', '核果类'],
  ['海南香水菠萝', '3个/箱', '箱', '热带水果'],
  ['福建琯溪蜜柚', '4个/箱', '箱', '柑橘类'],
  ['江西南丰蜜桔', '5kg/箱', '箱', '柑橘类'],
  ['湖北秭归脐橙', '5kg/箱', '箱', '柑橘类'],
  ['湖南冰糖橙', '5kg/箱', '箱', '柑橘类'],
  ['浙江象山红美人', '2kg/箱', '箱', '柑橘类'],
  ['广东增城挂绿荔枝', '1kg/盒', '盒', '核果类'],
  ['海南椰青', '9个/箱', '箱', '热带水果'],
  ['云南阳光玫瑰葡萄', '2kg/箱', '箱', '浆果类'],
  ['山东莱阳梨', '5kg/箱', '箱', '仁果类'],
  ['河北黄冠梨', '5kg/箱', '箱', '仁果类'],
  ['山西玉露香梨', '4kg/箱', '箱', '仁果类'],
  ['辽宁丹东草莓', '1kg/盒', '盒', '浆果类'],
  ['江苏阳山水蜜桃', '8个/箱', '箱', '核果类'],
  ['安徽砀山酥梨', '5kg/箱', '箱', '仁果类'],
  ['河南灵宝苹果', '5kg/箱', '箱', '仁果类'],
  ['宁夏硒砂瓜', '1个', '个', '瓜类']
]

const EMPLOYEES = [
  ['W001', '王敏', 'WAVE_OPERATOR', '波次调度组'],
  ['W002', '李强', 'WAVE_OPERATOR', '波次调度组'],
  ['P001', '张伟', 'PICKER', '拣货一组'],
  ['P002', '刘洋', 'PICKER', '拣货一组'],
  ['P003', '陈静', 'PICKER', '拣货二组'],
  ['P004', '赵磊', 'PICKER', '拣货二组'],
  ['P005', '孙悦', 'PICKER', '拣货三组'],
  ['P006', '周鹏', 'PICKER', '拣货三组'],
  ['R001', '吴倩', 'REVIEWER', '复核组'],
  ['R002', '郑浩', 'REVIEWER', '复核组'],
  ['R003', '冯雪', 'REVIEWER', '复核组'],
  ['A001', '何军', 'ADMIN', '仓库管理']
]

const ZONES = [
  ['A', '冷藏区', '0-4℃'],
  ['B', '常温区', '15-25℃'],
  ['C', '干货区', '常温干燥'],
  ['D', '周转区', '临时周转']
]

/* ========================================================================== */
/* 三、内存库                                                                  */
/* ========================================================================== */

export const db = {
  carriers: [],
  employees: [],
  locations: [],
  skus: [],
  orders: [],
  orderItems: [],
  waves: [],
  waveOrders: [],
  pickTasks: [],
  pickItems: [],
  shortages: [],
  reviews: [],
  reviewDiffs: [],
  handovers: [],
  logs: []
}

/* 自增主键 */
const seq = { v: 0 }
export const nextId = () => ++seq.v

/* 业务单号发号器 */
const noCounters = {}
export function nextNo(prefix, date) {
  const d = date ? new Date(date) : new Date()
  const day = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
  const key = prefix + day
  noCounters[key] = (noCounters[key] || 0) + 1
  return key + String(noCounters[key]).padStart(4, '0')
}

/* 固定种子的伪随机，保证每次刷新数据一致 */
let seed = 20261016
function rnd() {
  seed = (seed * 1103515245 + 12345) & 0x7fffffff
  return seed / 0x7fffffff
}
const randInt = (n) => Math.floor(rnd() * n)
const pick = (arr) => arr[randInt(arr.length)]

/** 格式化为 'YYYY-MM-DD HH:mm:ss' */
export function fmt(d) {
  if (!d) return null
  const t = d instanceof Date ? d : new Date(d)
  if (Number.isNaN(t.getTime())) return null
  const p = (n) => String(n).padStart(2, '0')
  return `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())} ${p(t.getHours())}:${p(t.getMinutes())}:${p(t.getSeconds())}`
}

/** 相对今天偏移若干天/小时 */
function ago(days, hours = 0, minutes = 0) {
  const d = new Date()
  d.setDate(d.getDate() - days)
  d.setHours(d.getHours() - hours)
  d.setMinutes(d.getMinutes() - minutes)
  return d
}

function atHour(daysAgo, hour, minute = 30) {
  const d = new Date()
  d.setDate(d.getDate() - daysAgo)
  d.setHours(hour, minute, 0, 0)
  return d
}

/* ========================================================================== */
/* 四、初始化                                                                  */
/* ========================================================================== */

let inited = false

export function initDb(force = false) {
  if (inited && !force) return db

  seed = 20261016
  Object.keys(db).forEach((k) => {
    db[k] = []
  })
  seq.v = 0
  Object.keys(noCounters).forEach((k) => delete noCounters[k])

  initCarriers()
  initEmployees()
  initLocations()
  initSkus()
  initOrders()
  initHistoricalWaves()

  inited = true
  return db
}

function initCarriers() {
  CARRIERS.forEach((c, i) => {
    db.carriers.push({
      id: nextId(),
      carrierCode: c[0],
      carrierName: c[1],
      contactName: c[2],
      contactPhone: c[3],
      dailyPickupTime: c[4],
      status: 1,
      createTime: fmt(ago(30))
    })
  })
}

function initEmployees() {
  EMPLOYEES.forEach((e, i) => {
    db.employees.push({
      id: nextId(),
      employeeNo: e[0],
      employeeName: e[1],
      role: e[2],
      team: e[3],
      phone: `139${String(10000000 + i).padStart(8, '0')}`,
      status: 1,
      createTime: fmt(ago(60))
    })
  })
}

function initLocations() {
  ZONES.forEach((z, zi) => {
    for (let aisle = 1; aisle <= 3; aisle++) {
      for (let rack = 1; rack <= 2; rack++) {
        for (let level = 1; level <= 2; level++) {
          const code = `${z[0]}-${String(aisle).padStart(2, '0')}-${String(rack).padStart(2, '0')}-${String(level).padStart(2, '0')}`
          db.locations.push({
            id: nextId(),
            locationCode: code,
            zone: z[0],
            zoneName: z[1],
            aisle: String(aisle).padStart(2, '0'),
            rack: String(rack).padStart(2, '0'),
            level: String(level).padStart(2, '0'),
            locationType: zi === 3 ? 'BULK' : 'PICK',
            status: 'ENABLED',
            preferredCarrierCode: zi < CARRIERS.length ? CARRIERS[zi][0] : null,
            pathSeq: zi * 10000 + aisle * 100 + rack * 10 + level
          })
        }
      }
    }
  })
}

function initSkus() {
  const locs = db.locations.map((l) => l.locationCode).sort()
  SKUS.forEach((s, i) => {
    db.skus.push({
      id: nextId(),
      skuCode: `SKU${String(10001 + i).padStart(5, '0')}`,
      skuName: s[0],
      spec: s[1],
      unit: s[2],
      category: s[3],
      barcode: `69${String(10000000000 + i)}`,
      defaultLocationCode: locs[i % locs.length],
      weight: Number((0.5 + randInt(80) / 10).toFixed(1)),
      safetyStock: 20 + randInt(60),
      // 前 3 个 SKU 库存留低，便于演示缺货
      stockQty: i < 3 ? 3 + randInt(5) : 120 + randInt(500),
      status: 1
    })
  })
}

const SURNAMES = ['张', '李', '王', '刘', '陈', '杨', '黄', '赵', '吴', '周', '徐', '孙', '马', '朱', '胡']
const GIVEN = ['伟', '芳', '娜', '敏', '静', '磊', '洋', '强', '军', '杰', '涛', '明', '超', '霞', '丽']

function initOrders() {
  for (let i = 0; i < 60; i++) {
    const area = pick(AREAS)
    const carrier = pick(CARRIERS)
    const urgent = randInt(10) < 2
    const payTime = ago(0, 2 + randInt(46), randInt(60))

    const order = {
      id: nextId(),
      orderNo: nextNo('SO'),
      platform: pick(PLATFORMS),
      carrierCode: carrier[0],
      carrierName: carrier[1],
      receiverName: pick(SURNAMES) + pick(GIVEN),
      receiverPhone: `13${String(randInt(1000000000)).padStart(9, '0')}`,
      receiverAddress: `${area[2]}${10 + randInt(200)}号`,
      province: area[0],
      city: area[1],
      priority: urgent ? 'URGENT' : 'NORMAL',
      payTime: fmt(payTime),
      expectedDeliveryTime: fmt(new Date(payTime.getTime() + (urgent ? 1 : 3) * 86400000)),
      status: 'CREATED',
      waveNo: null,
      skuCount: 0,
      totalQty: 0,
      amount: 0,
      buyerRemark: randInt(5) === 0 ? '请尽快发货，谢谢' : null,
      createTime: fmt(payTime)
    }
    db.orders.push(order)

    const lineCount = 1 + randInt(4)
    const used = new Set()
    let amount = 0
    let totalQty = 0
    for (let line = 1; line <= lineCount; line++) {
      let idx
      do {
        idx = randInt(db.skus.length)
      } while (used.has(idx))
      used.add(idx)
      const sku = db.skus[idx]
      const qty = 1 + randInt(3)
      const price = Number((15 + rnd() * 180).toFixed(2))
      const lineAmount = Number((price * qty).toFixed(2))
      amount += lineAmount
      totalQty += qty

      db.orderItems.push({
        id: nextId(),
        orderNo: order.orderNo,
        lineNo: line,
        skuCode: sku.skuCode,
        skuName: sku.skuName,
        spec: sku.spec,
        unit: sku.unit,
        qty,
        pickedQty: 0,
        locationCode: sku.defaultLocationCode,
        price,
        amount: lineAmount,
        remark: null
      })
    }
    order.skuCount = lineCount
    order.totalQty = totalQty
    order.amount = Number(amount.toFixed(2))
  }
}

/** 波次状态在流程中的推进序号 */
const WAVE_RANK = {
  CREATED: 0,
  ASSIGNED: 1,
  PICKING: 2,
  PICKED: 3,
  REVIEWING: 4,
  REVIEWED: 5,
  HANDOVERED: 6,
  CLOSED: 7,
  CANCELLED: -1
}

/** 波次状态映射到订单状态 */
function mapOrderStatus(s) {
  if (s === 'CREATED' || s === 'ASSIGNED' || s === 'PICKING') return 'WAVED'
  if (s === 'PICKED' || s === 'REVIEWING') return 'PICKED'
  if (s === 'REVIEWED') return 'REVIEWED'
  if (s === 'HANDOVERED' || s === 'CLOSED') return 'OUTBOUND'
  return 'CANCELLED'
}

function initHistoricalWaves() {
  // 后 28 笔订单留给历史波次，前 32 笔保持「待作业」供交互演示
  const pool = db.orders.slice(32)
  // 固定顺序打乱
  for (let i = pool.length - 1; i > 0; i--) {
    const j = randInt(i + 1)
    ;[pool[i], pool[j]] = [pool[j], pool[i]]
  }

  const plan = [
    { strategy: 'BY_AREA', status: 'CLOSED', n: 5, picker: 'P001', reviewer: 'R001', creator: 'W001', shortage: false, days: 6 },
    { strategy: 'BY_CARRIER', status: 'CLOSED', n: 6, picker: 'P002', reviewer: 'R002', creator: 'W001', shortage: true, days: 5 },
    { strategy: 'BY_AREA', status: 'HANDOVERED', n: 4, picker: 'P003', reviewer: 'R003', creator: 'W002', shortage: false, days: 4 },
    { strategy: 'BY_TIMELINESS', status: 'REVIEWED', n: 3, picker: 'P004', reviewer: 'R001', creator: 'W002', shortage: false, days: 3 },
    { strategy: 'BY_AREA', status: 'PICKED', n: 4, picker: 'P005', reviewer: 'R002', creator: 'W001', shortage: false, days: 2 },
    { strategy: 'BY_CARRIER', status: 'PICKING', n: 4, picker: 'P006', reviewer: 'R003', creator: 'W001', shortage: false, days: 1 },
    { strategy: 'BY_AREA', status: 'ASSIGNED', n: 2, picker: 'P001', reviewer: 'R001', creator: 'W002', shortage: false, days: 0 }
  ]

  let cursor = 0
  plan.forEach((p, idx) => {
    const orders = pool.slice(cursor, cursor + p.n)
    cursor += p.n
    if (orders.length) buildWave(idx + 1, p, orders)
  })
}

const empByNo = (no) => db.employees.find((e) => e.employeeNo === no)

function buildWave(idx, plan, orders) {
  const picker = empByNo(plan.picker)
  const reviewer = empByNo(plan.reviewer)
  const creator = empByNo(plan.creator)

  const base = atHour(plan.days, 8, 30)
  const assignTime = new Date(base.getTime() + 20 * 60000)
  const pickStart = new Date(base.getTime() + 35 * 60000)
  const pickSeconds = 180 + orders.length * 150 + randInt(240)
  const pickFinish = new Date(pickStart.getTime() + pickSeconds * 1000)
  const reviewSeconds = 90 + orders.length * 55 + randInt(120)
  const reviewFinish = new Date(pickFinish.getTime() + reviewSeconds * 1000)
  const handoverTime = new Date(reviewFinish.getTime() + (12 + randInt(20)) * 60000)

  const dayStr = `${base.getFullYear()}${String(base.getMonth() + 1).padStart(2, '0')}${String(base.getDate()).padStart(2, '0')}`
  const waveNo = `WV${dayStr}${String(idx).padStart(3, '0')}`
  const scope = [...new Set(orders.map((o) => o.province))].slice(0, 3).join('/')
  const r = WAVE_RANK[plan.status]

  const wave = {
    id: nextId(),
    waveNo,
    waveName: `${base.getFullYear()}-${String(base.getMonth() + 1).padStart(2, '0')}-${String(base.getDate()).padStart(2, '0')} ${scope}波次 ${String(idx).padStart(2, '0')}`,
    strategy: plan.strategy,
    status: plan.status,
    orderCount: orders.length,
    skuCount: 0,
    totalQty: 0,
    locationCount: 0,
    provinceScope: scope,
    creatorNo: creator.employeeNo,
    creatorName: creator.employeeName,
    pickerNo: picker.employeeNo,
    pickerName: picker.employeeName,
    reviewerNo: reviewer.employeeNo,
    reviewerName: reviewer.employeeName,
    createTime: fmt(base),
    assignTime: plan.status === 'CREATED' ? null : fmt(assignTime),
    pickStartTime: r >= WAVE_RANK.PICKING ? fmt(pickStart) : null,
    pickFinishTime: r >= WAVE_RANK.PICKED ? fmt(pickFinish) : null,
    reviewFinishTime: r >= WAVE_RANK.REVIEWED ? fmt(reviewFinish) : null,
    handoverTime: r >= WAVE_RANK.HANDOVERED ? fmt(handoverTime) : null,
    pickDurationSec: r >= WAVE_RANK.PICKED ? pickSeconds : null,
    totalDurationSec: r >= WAVE_RANK.HANDOVERED ? Math.round((handoverTime - base) / 1000) : null,
    hasShortage: plan.shortage,
    remark: null
  }

  const skuSet = new Set()
  const locSet = new Set()
  let totalQty = 0
  let seqNo = 1
  orders.forEach((o) => {
    const items = db.orderItems.filter((i) => i.orderNo === o.orderNo)
    items.forEach((it) => {
      skuSet.add(it.skuCode)
      locSet.add(it.locationCode)
      totalQty += it.qty
      if (r >= WAVE_RANK.PICKED) it.pickedQty = it.qty
    })
    o.waveNo = waveNo
    o.status = mapOrderStatus(plan.status)
    db.waveOrders.push({ id: nextId(), waveNo, orderNo: o.orderNo, seqNo: seqNo++, held: false, holdReason: null })
  })
  wave.skuCount = skuSet.size
  wave.totalQty = totalQty
  wave.locationCount = locSet.size
  db.waves.push(wave)

  addLog('WAVE', waveNo, 'WAVE_CREATE', '生成波次', creator, `策略=${dictText('waveStrategy', plan.strategy)}，订单数=${orders.length}`)

  buildPickTask(wave, orders, picker, plan.status, plan.shortage)
  if (r >= WAVE_RANK.REVIEWED) buildReviews(wave, orders, reviewer, plan.shortage)
  if (r >= WAVE_RANK.HANDOVERED) buildHandover(wave, orders, reviewer, handoverTime)
}

function buildPickTask(wave, orders, picker, waveStatus, withShortage) {
  const r = WAVE_RANK[waveStatus]
  const started = r >= WAVE_RANK.PICKING
  const done = r >= WAVE_RANK.PICKED
  const start = wave.pickStartTime ? new Date(wave.pickStartTime) : null
  const finish = wave.pickFinishTime ? new Date(wave.pickFinishTime) : null

  // 合并：库位 + SKU
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

  const pathOrder = db.locations
    .slice()
    .sort((a, b) => a.pathSeq - b.pathSeq)
    .map((l) => l.locationCode)

  const list = [...merged.values()].sort(
    (a, b) => pathOrder.indexOf(a.locationCode) - pathOrder.indexOf(b.locationCode)
  )

  const taskNo = `PK${wave.waveNo.slice(2)}`
  let shouldTotal = 0
  let actualTotal = 0
  let shortageTotal = 0
  let finished = 0

  const shortageIdx = withShortage && list.length > 1 ? 1 : -1

  list.forEach((m, i) => {
    const item = {
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
    }
    shouldTotal += m.shouldQty

    if (done) {
      if (i === shortageIdx) {
        const shortQty = Math.min(2, Math.max(1, m.shouldQty - 1))
        item.shortageQty = shortQty
        item.actualQty = m.shouldQty - shortQty
        item.status = shortQty >= m.shouldQty ? 'SHORTAGE' : 'PARTIAL'
        shortageTotal += shortQty
      } else {
        item.actualQty = m.shouldQty
        item.status = 'PICKED'
      }
      item.scanTime = fmt(new Date(finish.getTime() - (list.length - i) * 60000))
      actualTotal += item.actualQty
      finished++
    } else if (started && i <= Math.floor(list.length / 2)) {
      item.actualQty = m.shouldQty
      item.status = 'PICKED'
      item.scanTime = fmt(new Date(start.getTime() + (i + 1) * 45000))
      actualTotal += item.actualQty
      finished++
    }
    db.pickItems.push(item)
  })

  let taskStatus = 'WAITING'
  if (done) taskStatus = shortageTotal > 0 ? 'EXCEPTION' : 'FINISHED'
  else if (started) taskStatus = 'PICKING'

  db.pickTasks.push({
    id: nextId(),
    taskNo,
    waveNo: wave.waveNo,
    pickerNo: picker.employeeNo,
    pickerName: picker.employeeName,
    status: taskStatus,
    itemCount: list.length,
    finishedCount: finished,
    locationCount: wave.locationCount,
    shouldQty: shouldTotal,
    actualQty: actualTotal,
    shortageQty: shortageTotal,
    orderCount: orders.length,
    startTime: started ? wave.pickStartTime : null,
    finishTime: done ? wave.pickFinishTime : null,
    durationSec: done ? wave.pickDurationSec : null,
    pathText: [...new Set(list.map((i) => i.locationCode))].join(' → '),
    remark: null,
    createTime: wave.assignTime || wave.createTime
  })

  if (done) {
    addLog('PICK', taskNo, 'PICK_FINISH', '拣货完成', picker,
      `应拣 ${shouldTotal} 件，实拣 ${actualTotal} 件，缺货 ${shortageTotal} 件`)
  }

  if (withShortage && done) {
    const shortItem = db.pickItems.find((i) => i.taskNo === taskNo && i.shortageQty > 0)
    if (shortItem) {
      db.shortages.push({
        id: nextId(),
        shortageNo: nextNo('SH'),
        waveNo: wave.waveNo,
        taskNo,
        orderNo: shortItem.relatedOrderNos.split(',')[0],
        locationCode: shortItem.locationCode,
        skuCode: shortItem.skuCode,
        skuName: shortItem.skuName,
        shouldQty: shortItem.shouldQty,
        shortageQty: shortItem.shortageQty,
        reason: 'STOCK_ZERO',
        reporterNo: picker.employeeNo,
        reporterName: picker.employeeName,
        reportTime: wave.pickFinishTime,
        status: 'PENDING',
        handleType: null,
        handlerNo: null,
        handlerName: null,
        handleTime: null,
        handleRemark: null,
        substituteSkuCode: null,
        createTime: wave.pickFinishTime
      })
    }
  }
}

function buildReviews(wave, orders, reviewer, withShortage) {
  const finish = new Date(wave.reviewFinishTime)
  let idx = 0
  orders.forEach((o) => {
    const items = db.orderItems.filter((i) => i.orderNo === o.orderNo)
    const shouldQty = items.reduce((s, i) => s + i.qty, 0)
    const actualQty = items.reduce((s, i) => s + (i.pickedQty || 0), 0)
    const thisDiff = withShortage && idx === 1

    const start = new Date(finish.getTime() - (orders.length - idx) * 40000)
    const end = new Date(finish.getTime() - (orders.length - idx - 1) * 30000)
    const reviewNo = nextNo('RV')

    const rec = {
      id: nextId(),
      reviewNo,
      waveNo: wave.waveNo,
      orderNo: o.orderNo,
      reviewerNo: reviewer.employeeNo,
      reviewerName: reviewer.employeeName,
      status: thisDiff ? 'DIFF' : 'PASS',
      shouldSkuCount: items.length,
      shouldQty,
      actualQty: thisDiff ? shouldQty - 2 : actualQty,
      diffType: thisDiff ? 'SHORT' : 'NONE',
      diffQty: thisDiff ? 2 : 0,
      diffDetail: thisDiff ? '缺货 2 件：库位实物不足' : null,
      packMaterial: pick(['纸箱', '气泡袋', '保温箱']),
      packageCount: 1 + randInt(2),
      weight: Number((1.5 + rnd() * 6).toFixed(2)),
      startTime: fmt(start),
      finishTime: fmt(end),
      durationSec: Math.round((end - start) / 1000),
      remark: null,
      createTime: fmt(start)
    }
    db.reviews.push(rec)

    if (thisDiff) {
      const first = items[0]
      db.reviewDiffs.push({
        id: nextId(),
        reviewNo,
        waveNo: wave.waveNo,
        orderNo: o.orderNo,
        skuCode: first.skuCode,
        skuName: first.skuName,
        diffType: 'SHORT',
        shouldQty: first.qty,
        actualQty: first.qty - 2,
        diffQty: -2,
        responsibleStage: 'PICK',
        responsibleNo: wave.pickerNo,
        remark: '拣货员反馈库位实物不足',
        createTime: fmt(start)
      })
    }
    idx++
  })

  addLog('REVIEW', wave.waveNo, 'REVIEW_FINISH', '复核完成', reviewer, `复核 ${orders.length} 单`)
}

function buildHandover(wave, orders, operator, handoverTime) {
  const reviews = db.reviews.filter((r) => r.waveNo === wave.waveNo)
  const packageCount = reviews.reduce((s, r) => s + (r.packageCount || 1), 0)
  const weight = reviews.reduce((s, r) => s + (r.weight || 0), 0)

  db.handovers.push({
    id: nextId(),
    handoverNo: `HO${wave.waveNo.slice(2)}`,
    waveNos: wave.waveNo,
    carrierCode: orders[0].carrierCode,
    carrierName: orders[0].carrierName,
    orderCount: orders.length,
    packageCount,
    totalWeight: Number(weight.toFixed(2)),
    status: 'CONFIRMED',
    handoverUserNo: operator.employeeNo,
    handoverUserName: operator.employeeName,
    receiveName: db.carriers.find((c) => c.carrierCode === orders[0].carrierCode)?.contactName || '承运商司机',
    vehicleNo: `粤B${String(randInt(100000)).padStart(5, '0')}`,
    createTime: fmt(new Date(handoverTime.getTime() - 15 * 60000)),
    handoverTime: fmt(handoverTime),
    remark: null
  })

  addLog('HANDOVER', `HO${wave.waveNo.slice(2)}`, 'HANDOVER_CONFIRM', '交接完成', operator,
    `交接 ${orders.length} 单 / ${packageCount} 件`)
}

/* ========================================================================== */
/* 五、操作日志                                                                */
/* ========================================================================== */

export function addLog(bizType, bizNo, action, actionDesc, operator, detail) {
  db.logs.push({
    id: nextId(),
    bizType,
    bizNo,
    action,
    actionDesc,
    operatorNo: operator?.employeeNo || null,
    operatorName: operator?.employeeName || null,
    operatorRole: operator ? dictText('employeeRole', operator.role) : null,
    detail,
    operateTime: fmt(new Date()),
    createTime: fmt(new Date())
  })
}

/* ========================================================================== */
/* 六、通用工具                                                                */
/* ========================================================================== */

export function paginate(list, params = {}) {
  const pageNum = Number(params.pageNum) || 1
  const pageSize = Number(params.pageSize) || 10
  const total = list.length
  const from = Math.min((pageNum - 1) * pageSize, total)
  const to = Math.min(from + pageSize, total)
  return {
    records: list.slice(from, to),
    total,
    pageNum,
    pageSize,
    pages: Math.ceil(total / pageSize) || 1
  }
}

/** 安全除法，保留 n 位小数 */
export function div(a, b, n = 2) {
  if (!b) return 0
  const p = Math.pow(10, n)
  return Math.round((a / b) * p) / p
}

export const WAVE_RANK_MAP = WAVE_RANK
