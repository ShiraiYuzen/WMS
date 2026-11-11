# WMS v5.0 — 接口契约

> 本文是 v5.0 前后端接口的**唯一依据**：路径、参数、响应形状和状态机。改了控制器之后，按[第 7 节](#7-维护)的清单同步本文。

| 项 | 值 |
| --- | --- |
| 实现位置 | `backend/src/main/java/com/example/wms/controller` |
| 接口数量 | 11 个模块 / 54 个接口 |
| 前端调用方 | `frontend/src/api/index.js` |
| 上次核对 | 2026-10-20（对照源码逐行核对） |

---

## 1. 全局约定

- **baseURL**：`VITE_API_BASE_URL || '/api'`。所有路径以 `/api` 开头。
- **认证**：登录后令牌存在 `localStorage.wms_token`，请求头为 `Authorization: Bearer <token>`。服务端维护 `token → session` 表，有效期 8 小时；无效或过期返回 **401**，前端清掉令牌并回到登录页。
- **授权**：写接口按角色白名单校验，不符返回 **403**（矩阵见 [`../README.md`](../README.md#认证与授权)）。
- **响应信封**：`{ code, message, data, timestamp }`。`code === 0` 表示成功；否则 `message` 可以直接展示给用户。
- **分页请求**：`{ pageNum, pageSize, ...filters }`，`pageNum` 默认 1，`pageSize` 默认 10。
- **分页响应**：`{ records, total, pageNum, pageSize, pages }`。
- **GET 上的 `_t`**：前端会追加 `_t=<时间戳>` 防止缓存；后端忽略它。
- **时间格式**：始终是字符串 `yyyy-MM-dd HH:mm:ss`，不是纪元时间戳。
- **字段命名**：JSON 全程驼峰（`JdbcMaps` 自动把 `snake_case` 列转过来）。
- **操作人**：写接口上的操作员 / 拣货员 / 复核员**从令牌解析**；同名请求参数无效。

---

## 2. 实体字段（按表）

JSON 以驼峰输出（`JdbcMaps` 转换 `snake_case` 列名），前端拿到的就是下面这些名字。

- **主数据**
  - `carrier`：carrierCode、carrierName、contactName、contactPhone、dailyPickupTime、status、createTime
  - `employee`：employeeNo、employeeName、role、team、phone、status、createTime
  - `sys_user`：username、password（BCrypt 散列）、employeeNo、role、nickname、createTime
  - `sku`：skuCode、skuName、spec、unit、category、barcode、defaultLocationCode、weight、safetyStock、stockQty、status
  - `location`：locationCode、zone、zoneName、aisle、rack、level、locationType（PICK/BULK）、status、preferredCarrierCode、pathSeq
- **订单**
  - `order`：orderNo、platform、carrierCode、carrierName、receiverName、receiverPhone、receiverAddress、province、city、priority、payTime、expectedDeliveryTime、status、waveNo、skuCount、totalQty、amount、buyerRemark、createTime
  - `order_item`：orderNo、lineNo、skuCode、skuName、spec、unit、qty、pickedQty、locationCode、price、amount、remark
- **波次**
  - `wave`：waveNo、waveName、strategy、status、orderCount、skuCount、totalQty、locationCount、provinceScope、creatorNo、creatorName、pickerNo、pickerName、reviewerNo、reviewerName、createTime、assignTime、pickStartTime、pickFinishTime、reviewFinishTime、handoverTime、pickDurationSec、totalDurationSec、hasShortage、remark
  - `wave_order`：waveNo、orderNo、seqNo、held、holdReason
- **拣货**
  - `pick_task`：taskNo、waveNo、pickerNo、pickerName、status、itemCount、finishedCount、locationCount、shouldQty、actualQty、shortageQty、orderCount、startTime、finishTime、durationSec、pathText、remark、createTime
  - `pick_item`：taskNo、waveNo、seqNo、locationCode、skuCode、skuName、spec、unit、shouldQty、actualQty、shortageQty、status、relatedOrderNos、scanTime、remark
- **异常与交接**
  - `shortage`：shortageNo、waveNo、taskNo、orderNo、locationCode、skuCode、skuName、shouldQty、shortageQty、reason、reporterNo、reporterName、reportTime、status、handleType、handlerNo、handlerName、handleTime、handleRemark、substituteSkuCode、createTime
  - `review`：reviewNo、waveNo、orderNo、reviewerNo、reviewerName、status、shouldSkuCount、shouldQty、actualQty、diffType、diffQty、diffDetail、packMaterial、packageCount、weight、startTime、finishTime、durationSec、remark、createTime
  - `review_diff`：reviewNo、waveNo、orderNo、skuCode、skuName、diffType、shouldQty、actualQty、diffQty、responsibleStage、responsibleNo、remark、createTime
  - `handover`：handoverNo、waveNos（逗号分隔）、carrierCode、carrierName、orderCount、packageCount、totalWeight、status、handoverUserNo、handoverUserName、receiveName、vehicleNo、createTime、handoverTime、remark
- **追溯**
  - `operation_log`：bizType、bizNo、action、actionDesc、operatorNo、operatorName、operatorRole、detail、operateTime、createTime

---

## 3. 枚举字典（`GET /api/base/enums`）

后端 `Dict` 是唯一来源；前端不再写死文案。响应形如 `{ "orderStatus": [{ "code": "CREATED", "desc": "已创建" }, …] }`。

> 取值是与语言无关的代码。说明由服务端返回，一套部署对应一种语言。下表只列出代码。

| 字典键 | 取值（节选） |
| --- | --- |
| `orderStatus` | CREATED / POOLED / WAVED / PICKED / REVIEWED / OUTBOUND / CANCELLED |
| `waveStatus` | CREATED / ASSIGNED / PICKING / PICKED / REVIEWING / REVIEWED / HANDOVERED / CLOSED / CANCELLED |
| `waveStrategy` | BY_AREA / BY_CARRIER / BY_TIMELINESS / BY_ORDER_COUNT |
| `pickTaskStatus` | WAITING / PICKING / FINISHED / EXCEPTION / CANCELLED |
| `pickItemStatus` | PENDING / PICKED / SHORTAGE / PARTIAL / SKIPPED |
| `reviewStatus` | WAITING / PASS / DIFF / REPICKING |
| `diffType` | NONE / SHORT / OVER / WRONG_SKU / DAMAGED / WRONG_ORDER |
| `shortageStatus` | PENDING / HANDLED / CLOSED |
| `handoverStatus` | CREATED / CONFIRMED / REJECTED / CANCELLED |
| `employeeRole` | ADMIN / WAVE_OPERATOR / PICKER / REVIEWER |
| `priority` | URGENT / NORMAL |
| `locationStatus` | ENABLED / LOCKED / FULL / DISABLED |

---

## 4. 接口清单（11 个模块 / 54 个接口）

| 模块 | 前缀 | 数量 | 主要接口 |
| --- | --- | --- | --- |
| 认证 | `/api/auth` | 1 | login |
| 看板 | `/api/dashboard` | 2 | summary（含 overview/pipeline/todos）、trend |
| 订单 | `/api/order` | 6 | page、detail、count-by-status、pool、pool-all、cancel |
| 波次 | `/api/wave` | 8 | page、detail、preview、generate、assign、close、cancel、picker-workload |
| 拣货 | `/api/pick` | 7 | page、detail、my-tasks、start、confirm-item、report-shortage、finish |
| 缺货 | `/api/shortage` | 5 | page、handle、close、pending-count、reason-distribution |
| 复核 | `/api/review` | 5 | page、detail、pending-waves、claim、submit |
| 交接 | `/api/handover` | 4 | page、reviewable-waves、create、confirm |
| 分析 | `/api/analytics` | 5 | overview、trend、employee-efficiency、error-rate、shortage-distribution |
| 主数据 | `/api/base` | 9 | enums、sku/page、sku/categories、location/page、location/zones、employee/page、employee/by-role、employee/teams、carrier/list |
| 追溯 | `/api/log` | 2 | page、trace |

写操作的角色限制见 [`../README.md`](../README.md#认证与授权) 的权限矩阵；下面只写业务规则。

### 4.1 认证

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | `{ username, password }` | 返回 `{ token, employeeNo, employeeName, role, team }`。口令用 BCrypt 校验；账号不存在和口令错误返回同一句话 |

### 4.2 看板

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/dashboard/summary` | `days?` | `{ overview, pipeline[], todos[] }`；`days` 默认 7，形状见 [5.1](#51-overview) |
| GET | `/api/dashboard/trend` | `days?` | 趋势桶数组；`days=1` 时返回 24 个小时点，形状见 [5.2](#52-趋势桶) |

### 4.3 订单

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/order/page` | `orderNo?、platform?、carrierCode?、status?、province?、priority?、waveNo?、startDate?、endDate?、pageNum、pageSize` | 分页，按 `createTime desc` |
| GET | `/api/order/{orderNo}` | 路径参数 | 订单详情，含 `items[]` |
| GET | `/api/order/count-by-status` | — | `[{ status, statusDesc, count }]` |
| POST | `/api/order/pool` | `{ orderNos: [] }` | 汇入指定订单，返回影响行数 |
| POST | `/api/order/pool-all` | — | 把全部 `CREATED` 订单改为 `POOLED` |
| POST | `/api/order/{orderNo}/cancel` | 路径参数 | 只有 `CREATED` / `POOLED` 可以取消 |

### 4.4 波次

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/wave/page` | `waveNo?、status?、strategy?、pickerNo?、onlyWorking?、onlyShortage?、pageNum、pageSize` | 分页 |
| GET | `/api/wave/{waveNo}` | 路径参数 | 详情，含 `orders[]` |
| GET | `/api/wave/picker-workload` | — | 每位拣货员在途任务数和负载，指派时用 |
| POST | `/api/wave/preview` | `{ strategy, orderNos?, ... }` | **试算**，只算分组，不落库 |
| POST | `/api/wave/generate` | `{ strategy, autoAssign?, namePrefix?, orderNos? }` | 一次创建 wave + wave_order + pick_task + pick_item |
| POST | `/api/wave/assign` | `{ waveNo, pickerNo }` | 只有 `CREATED` / `ASSIGNED` 可以改派；目标必须是 PICKER |
| POST | `/api/wave/{waveNo}/close` | 路径参数 | 只有 `HANDOVERED` 可以归档为 `CLOSED` |
| POST | `/api/wave/{waveNo}/cancel` | 路径参数 | 只有 `CREATED` / `ASSIGNED`；订单回到池中，任务和明细删除 |

### 4.5 拣货

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/pick/page` | `taskNo?、waveNo?、pickerNo?、status?、pageNum、pageSize` | 拣货员永远只能看到自己的任务 |
| GET | `/api/pick/{taskNo}` | 路径参数 | 详情含 `items[]`；别人的任务返回 403 |
| GET | `/api/pick/my-tasks` | `pickerNo?` | 同上；传入的 `pickerNo` 被忽略 |
| POST | `/api/pick/{taskNo}/start` | 路径参数 | `WAITING → PICKING`；拣货员从登录上下文取 |
| POST | `/api/pick/confirm-item` | `{ itemId, actualQty }` | 实拣 ≤ 应拣；重算任务进度 |
| POST | `/api/pick/report-shortage` | `{ itemId, shortageQty, reason, remark? }` | `1 ≤ 缺货 ≤ 未完成数量`；任务变为 `EXCEPTION` 并生成缺货单 |
| POST | `/api/pick/{taskNo}/finish` | 路径参数 | `PICKING` / `EXCEPTION → FINISHED`；同步波次和订单 |

### 4.6 缺货

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/shortage/page` | `shortageNo?、waveNo?、skuCode?、status?、reason?、pageNum、pageSize` | 分页 |
| GET | `/api/shortage/pending-count` | — | 待处理数量，侧栏角标用 |
| GET | `/api/shortage/reason-distribution` | — | `[{ reason, reasonDesc, count }]` |
| POST | `/api/shortage/handle` | `{ id, handleType, handleRemark?, substituteSkuCode? }` | `PENDING → HANDLED` |
| POST | `/api/shortage/{id}/close` | 路径参数 | `→ CLOSED` |

### 4.7 复核

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/review/page` | `reviewNo?、waveNo?、orderNo?、status?、diffType?、reviewerNo?、pageNum、pageSize` | 分页，含 `shouldItems[]` 和 `diffItems[]` |
| GET | `/api/review/{reviewNo}` | 路径参数 | 详情 |
| GET | `/api/review/pending-waves` | — | 状态为 `PICKED` / `REVIEWING` 的波次 |
| POST | `/api/review/claim` | `waveNo`（查询参数） | 领取波次；每个订单建一张 `WAITING` 复核单；复核员取自会话 |
| POST | `/api/review/submit` | `{ reviewNo, actualQty?, diffItems[], packMaterial?, packageCount?, weight?, remark? }` | 只有 `WAITING` 可以提交；无差异为 `PASS`，否则 `DIFF` |

### 4.8 交接

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/handover/page` | `handoverNo?、carrierCode?、status?、pageNum、pageSize` | 分页 |
| GET | `/api/handover/reviewable-waves` | `carrierCode?` | 可以交接的波次（`REVIEWED`） |
| POST | `/api/handover/create` | `{ carrierCode, waveNos: [], vehicleNo?, remark? }` | 波次必须是 `REVIEWED`；交接人取自会话 |
| POST | `/api/handover/confirm` | `{ handoverNo, receiveName?, actualPackageCount?, rejected?, remark? }` | 只有 `CREATED` 可以确认；确认后波次和订单进入出库 |

### 4.9 分析

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/analytics/overview` | `startDate?、endDate?` | 形状同 `overview` |
| GET | `/api/analytics/employee-efficiency` | `startDate?、endDate?、employeeNo?` | 按员工的单量、件数和效率 |
| GET | `/api/analytics/error-rate` | `startDate?、endDate?` | 每日差错率 |
| GET | `/api/analytics/trend` | `startDate?、endDate?` | 形状同 `trend` |
| GET | `/api/analytics/shortage-distribution` | `startDate?、endDate?` | 缺货原因分布 |

### 4.10 主数据

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/base/enums` | — | 全部枚举，见[第 3 节](#3-枚举字典get-apibaseenums) |
| GET | `/api/base/sku/page` | `keyword?、category?、pageNum、pageSize` | SKU 分页 |
| GET | `/api/base/sku/categories` | — | SKU 品类去重 |
| GET | `/api/base/location/page` | `zone?、pageNum、pageSize` | 库位分页 |
| GET | `/api/base/location/zones` | — | 库区汇总（代码 / 名称 / 库位数） |
| GET | `/api/base/employee/page` | `role?、team?、pageNum、pageSize` | 员工分页（含电话，仅 ADMIN） |
| GET | `/api/base/employee/by-role` | `role?` | 按角色查员工，指派拣货员时用 |
| GET | `/api/base/employee/teams` | — | 班组去重（仅 ADMIN） |
| GET | `/api/base/carrier/list` | — | 承运商列表 |

### 4.11 追溯

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/log/trace` | `bizNo`（查询参数） | 一张单据的完整操作时间线 |
| GET | `/api/log/page` | `bizType?、bizNo?、operatorNo?、pageNum、pageSize` | 全部审计记录，仅 ADMIN |

---

## 5. 聚合形状

### 5.1 overview

`dashboard/summary` 和 `analytics/overview` 共用。

```json
{
  "totalOrders": 60, "pendingOrders": 12, "pooledOrders": 8,
  "totalWaves": 7, "workingWaves": 3, "finishedWaves": 4,
  "pickedQty": 1280, "outboundOrders": 42, "handedPackages": 96,
  "pickEfficiency": 186.5, "avgPickDurationSec": 1520,
  "errorRate": 0.0031, "shortageRate": 0.012, "reviewPassRate": 0.96,
  "pendingShortageCount": 2,
  "alerts": [{ "metric": "pickEfficiency", "metricName": "拣货效率",
               "currentValue": 98, "threshold": 120, "level": "WARN",
               "message": "拣货效率低于 120 件/小时" }]
}
```

告警阈值（只在 `Stats` 里定义一次）：

| 指标 | 阈值 | 等级 |
| --- | --- | --- |
| `pickEfficiency` | < 120 件/小时 | WARN |
| `errorRate` | > 0.005 | DANGER |
| `shortageRate` | > 0.02 | DANGER |
| `avgPickDurationSec` | > 3600 秒 | WARN |

### 5.2 趋势桶

多日区间每天一个点，`label` 为 `YYYY-MM-DD`：

```json
[{ "label": "2026-10-20", "waveCount": 3, "orderCount": 18, "pickedQty": 210,
   "shortageQty": 2, "errorRate": 0.0021, "avgDurationSec": 1380, "pickEfficiency": 198.4 }]
```

区间只有一天（看板的「今天」）时，接口返回 **24 个小时点**，`label` 为 `YYYY-MM-DD HH:00`。前端按 label 长度切换 X 轴格式（`2026-10-20 09:00` → `09:00`）。如果只给一个日点，图上就是一根柱子，看不出日内节奏。

聚合口径：

| 字段 | 按什么分桶 |
| --- | --- |
| `waveCount`、`orderCount` | 波次**创建**时间 |
| `pickedQty`、`pickEfficiency`、`shortageQty` | 拣货任务**完成**时间，由 `actual_qty` / `duration_sec` 计算 |
| `errorRate` | 复核**完成**时间，`DIFF` 占比 |
| `avgDurationSec` | 该桶内波次拣货时长的均值 |

桶内没有记录时长时，`pickEfficiency` 返回 `null`（不是 0）——「没有数据」和「效率为零」不是一回事。前端用 `connectNulls`，把已知点连起来，而不是画一段假的下跌。

---

## 6. 状态机与触发链

**迁移（服务端校验；终态不可再写，也不能跳步）**

| 单据 | 迁移 | 约束 |
| --- | --- | --- |
| `order` | CREATED → POOLED → WAVED → PICKED → REVIEWED → OUTBOUND，可 CANCELLED | 只有 CREATED/POOLED 可以取消 |
| `wave` | CREATED → ASSIGNED → PICKING → PICKED → REVIEWING → REVIEWED → HANDOVERED → CLOSED，可 CANCELLED | 只有 CREATED/ASSIGNED 可以改派或取消；只有 HANDOVERED 可以归档 |
| `pick_task` | WAITING → PICKING → FINISHED（有缺货时为 EXCEPTION） | `start` 只能从 WAITING；`finish` 只能从 PICKING/EXCEPTION |
| `review` | WAITING → PASS / DIFF | 提交要求 WAITING；第二次提交是业务失败 |
| `handover` | CREATED → CONFIRMED / REJECTED | 确认要求 CREATED |
| `shortage` | PENDING → HANDLED → CLOSED | — |

**触发链（一次操作还会改哪些表）**

```text
wave/generate    → INSERT wave + wave_order + pick_task + pick_item；order.status = WAVED
pick/start       → pick_task.status = PICKING；wave.status = PICKING
pick/finish      → pick_task.status = FINISHED/EXCEPTION；wave.status = PICKED；order.status = PICKED
review/submit    → review.status = PASS/DIFF；该波次全部复核落定后 → wave.status = REVIEWED，order.status = REVIEWED
handover/confirm → handover.status = CONFIRMED；wave.status = HANDOVERED；order.status = OUTBOUND
shortage/handle  → shortage.status = HANDLED；wave.has_shortage = 1
```

**拣货明细合并规则**

- 同一波次内按 `locationCode + skuCode` 合并；`shouldQty` 相加，来源订单号记在 `relatedOrderNos`。
- 按 `location.pathSeq` 排序得到拣货路径，写入 `pick_item.seq_no`；可读路线写入 `pick_task.pathText`。
- 合并之后，`pick_item` 行数大约等于波次的 `locationCount`，而不是订单行数。

---

## 7. 维护

本文必须和 `controller/*.java` 一致。接口变更时走这张清单：

| 变更 | 改哪里 |
| --- | --- |
| 增加或删除接口 | 第 4 节的模块表**以及**文首数量 |
| 改参数 | 该行的「参数」列 |
| 改响应形状 | 第 5 节 |
| 改状态迁移 | 第 6 节 |
| 增加枚举值 | 第 3 节 |

核对不需要额外工具——数控制器上的注解，再和第 4 节表格行数比较：

```bash
grep -c "@GetMapping\|@PostMapping" backend/src/main/java/com/example/wms/controller/*.java
```

合计必须等于第 4 节所有表的数据行数。抽几条路径逐字核对。对不上就说明改了接口却没改文档——以代码为准，把文档补上。
