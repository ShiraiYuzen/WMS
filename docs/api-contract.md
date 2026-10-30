# WMS v4.1.0 — API Contract

> This document is the **source of truth** for the v4 frontend/backend interface: paths, parameters,
> response shapes and state machines. After changing a controller, sync this file following the checklist
> in [section 7](#7-maintenance).

| Item | Value |
| --- | --- |
| Implemented in | `backend/src/main/java/com/example/wms/controller` |
| Endpoint count | 11 modules / 54 endpoints |
| Frontend consumer | `frontend/src/api/index.js` |
| Last verified | 2026-10-20 (cross-checked line by line against the source) |

---

## 1. Global conventions

- **baseURL**: `VITE_API_BASE_URL || '/api'`. All paths start with `/api`.
- **Authentication**: after login the token is stored in `localStorage.wms_token` and sent as
  `Authorization: Bearer <token>`. The server keeps a `token → session` table with an 8-hour TTL; an
  invalid or expired token returns **401** and the frontend clears it and redirects to the login page.
- **Authorization**: write endpoints validate against a role whitelist and return **403** on mismatch
  (see the matrix in [`../README.md`](../README.md#authentication--authorization)).
- **Response envelope**: `{ code, message, data, timestamp }`. `code === 0` means success; otherwise
  `message` is safe to display to the end user as-is.
- **Pagination request**: `{ pageNum, pageSize, ...filters }`, with `pageNum` defaulting to 1 and
  `pageSize` to 10.
- **Pagination response**: `{ records, total, pageNum, pageSize, pages }`.
- **`_t` on GET requests**: the frontend appends `_t=<timestamp>` as a cache buster; the backend ignores it.
- **Time format**: always the string `yyyy-MM-dd HH:mm:ss`, never an epoch timestamp.
- **Field naming**: JSON is camelCase throughout (`JdbcMaps` converts `snake_case` columns automatically).
- **Acting user**: on write endpoints the operator / picker / reviewer is **resolved from the token**;
  passing the same-named request parameter has no effect.

---

## 2. Entity fields (per table)

JSON is emitted in camelCase (`JdbcMaps` converts `snake_case` column names), so the frontend receives the
names listed below.

- **Master data**
  - `carrier`: carrierCode, carrierName, contactName, contactPhone, dailyPickupTime, status, createTime
  - `employee`: employeeNo, employeeName, role, team, phone, status, createTime
  - `sys_user`: username, password (BCrypt hash), employeeNo, role, nickname, createTime
  - `sku`: skuCode, skuName, spec, unit, category, barcode, defaultLocationCode, weight, safetyStock, stockQty, status
  - `location`: locationCode, zone, zoneName, aisle, rack, level, locationType (PICK/BULK), status, preferredCarrierCode, pathSeq
- **Orders**
  - `order`: orderNo, platform, carrierCode, carrierName, receiverName, receiverPhone, receiverAddress, province, city, priority, payTime, expectedDeliveryTime, status, waveNo, skuCount, totalQty, amount, buyerRemark, createTime
  - `order_item`: orderNo, lineNo, skuCode, skuName, spec, unit, qty, pickedQty, locationCode, price, amount, remark
- **Waves**
  - `wave`: waveNo, waveName, strategy, status, orderCount, skuCount, totalQty, locationCount, provinceScope, creatorNo, creatorName, pickerNo, pickerName, reviewerNo, reviewerName, createTime, assignTime, pickStartTime, pickFinishTime, reviewFinishTime, handoverTime, pickDurationSec, totalDurationSec, hasShortage, remark
  - `wave_order`: waveNo, orderNo, seqNo, held, holdReason
- **Picking**
  - `pick_task`: taskNo, waveNo, pickerNo, pickerName, status, itemCount, finishedCount, locationCount, shouldQty, actualQty, shortageQty, orderCount, startTime, finishTime, durationSec, pathText, remark, createTime
  - `pick_item`: taskNo, waveNo, seqNo, locationCode, skuCode, skuName, spec, unit, shouldQty, actualQty, shortageQty, status, relatedOrderNos, scanTime, remark
- **Exceptions and handover**
  - `shortage`: shortageNo, waveNo, taskNo, orderNo, locationCode, skuCode, skuName, shouldQty, shortageQty, reason, reporterNo, reporterName, reportTime, status, handleType, handlerNo, handlerName, handleTime, handleRemark, substituteSkuCode, createTime
  - `review`: reviewNo, waveNo, orderNo, reviewerNo, reviewerName, status, shouldSkuCount, shouldQty, actualQty, diffType, diffQty, diffDetail, packMaterial, packageCount, weight, startTime, finishTime, durationSec, remark, createTime
  - `review_diff`: reviewNo, waveNo, orderNo, skuCode, skuName, diffType, shouldQty, actualQty, diffQty, responsibleStage, responsibleNo, remark, createTime
  - `handover`: handoverNo, waveNos (comma-separated), carrierCode, carrierName, orderCount, packageCount, totalWeight, status, handoverUserNo, handoverUserName, receiveName, vehicleNo, createTime, handoverTime, remark
- **Traceability**
  - `operation_log`: bizType, bizNo, action, actionDesc, operatorNo, operatorName, operatorRole, detail, operateTime, createTime

---

## 3. Enum dictionaries (`GET /api/base/enums`)

The backend `Dict` class is the single source of truth; the frontend no longer hard-codes any label. The
response looks like `{ "orderStatus": [{ "code": "CREATED", "desc": "Created" }, …] }`.

> Values are language-neutral codes. Descriptions are returned by the server so that a single deployment
> serves one language; the English labels below document the intended meaning of each code.

| Dictionary key | Values (partial) |
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

## 4. Endpoint inventory (11 modules / 54 endpoints)

| Module | Prefix | Count | Main endpoints |
| --- | --- | --- | --- |
| Auth | `/api/auth` | 1 | login |
| Dashboard | `/api/dashboard` | 2 | summary (incl. overview/pipeline/todos), trend |
| Orders | `/api/order` | 6 | page, detail, count-by-status, pool, pool-all, cancel |
| Waves | `/api/wave` | 8 | page, detail, preview, generate, assign, close, cancel, picker-workload |
| Picking | `/api/pick` | 7 | page, detail, my-tasks, start, confirm-item, report-shortage, finish |
| Shortage | `/api/shortage` | 5 | page, handle, close, pending-count, reason-distribution |
| Review | `/api/review` | 5 | page, detail, pending-waves, claim, submit |
| Handover | `/api/handover` | 4 | page, reviewable-waves, create, confirm |
| Analytics | `/api/analytics` | 5 | overview, trend, employee-efficiency, error-rate, shortage-distribution |
| Master data | `/api/base` | 9 | enums, sku/page, sku/categories, location/page, location/zones, employee/page, employee/by-role, employee/teams, carrier/list |
| Traceability | `/api/log` | 2 | page, trace |

Role restrictions for write operations follow the permission matrix in
[`../README.md`](../README.md#authentication--authorization); the notes below describe business rules only.

### 4.1 Auth

| Method | Path | Parameters | Notes |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | `{ username, password }` | Returns `{ token, employeeNo, employeeName, role, team }`. Password checked via BCrypt; unknown account and wrong password return the same message |

### 4.2 Dashboard

| Method | Path | Parameters | Notes |
| --- | --- | --- | --- |
| GET | `/api/dashboard/summary` | `days?` | `{ overview, pipeline[], todos[] }`; `days` defaults to 7, shapes in [5.1](#51-overview) |
| GET | `/api/dashboard/trend` | `days?` | Array of trend buckets; with `days=1` returns 24 hourly points, shapes in [5.2](#52-trend-bucket) |

### 4.3 Orders

| Method | Path | Parameters | Notes |
| --- | --- | --- | --- |
| GET | `/api/order/page` | `orderNo?, platform?, carrierCode?, status?, province?, priority?, waveNo?, startDate?, endDate?, pageNum, pageSize` | Paginated, ordered by `createTime desc` |
| GET | `/api/order/{orderNo}` | path parameter | Order detail including `items[]` |
| GET | `/api/order/count-by-status` | — | `[{ status, statusDesc, count }]` |
| POST | `/api/order/pool` | `{ orderNos: [] }` | Pool the given orders, returns the affected row count |
| POST | `/api/order/pool-all` | — | Move every `CREATED` order to `POOLED` |
| POST | `/api/order/{orderNo}/cancel` | path parameter | Only `CREATED` / `POOLED` may be cancelled |

### 4.4 Waves

| Method | Path | Parameters | Notes |
| --- | --- | --- | --- |
| GET | `/api/wave/page` | `waveNo?, status?, strategy?, pickerNo?, onlyWorking?, onlyShortage?, pageNum, pageSize` | Paginated |
| GET | `/api/wave/{waveNo}` | path parameter | Detail including `orders[]` |
| GET | `/api/wave/picker-workload` | — | In-flight task count and load per picker, used when assigning |
| POST | `/api/wave/preview` | `{ strategy, orderNos?, ... }` | **Dry run** — computes the grouping without persisting |
| POST | `/api/wave/generate` | `{ strategy, autoAssign?, namePrefix?, orderNos? }` | Creates wave + wave_order + pick_task + pick_item as one unit |
| POST | `/api/wave/assign` | `{ waveNo, pickerNo }` | Only `CREATED` / `ASSIGNED` may be reassigned; target must be a PICKER |
| POST | `/api/wave/{waveNo}/close` | path parameter | Only `HANDOVERED` may be archived as `CLOSED` |
| POST | `/api/wave/{waveNo}/cancel` | path parameter | Only `CREATED` / `ASSIGNED`; orders return to the pool, tasks and items are deleted |

### 4.5 Picking

| Method | Path | Parameters | Notes |
| --- | --- | --- | --- |
| GET | `/api/pick/page` | `taskNo?, waveNo?, pickerNo?, status?, pageNum, pageSize` | A picker always sees only their own tasks |
| GET | `/api/pick/{taskNo}` | path parameter | Detail including `items[]`; someone else's task returns 403 |
| GET | `/api/pick/my-tasks` | `pickerNo?` | Same as above; any supplied `pickerNo` is ignored |
| POST | `/api/pick/{taskNo}/start` | path parameter | `WAITING → PICKING`; picker resolved from the login context |
| POST | `/api/pick/confirm-item` | `{ itemId, actualQty }` | Actual ≤ should; task progress is recalculated |
| POST | `/api/pick/report-shortage` | `{ itemId, shortageQty, reason, remark? }` | `1 ≤ shortage ≤ outstanding`; task becomes `EXCEPTION` and a shortage record is created |
| POST | `/api/pick/{taskNo}/finish` | path parameter | `PICKING` / `EXCEPTION → FINISHED`; propagates to wave and orders |

### 4.6 Shortage

| Method | Path | Parameters | Notes |
| --- | --- | --- | --- |
| GET | `/api/shortage/page` | `shortageNo?, waveNo?, skuCode?, status?, reason?, pageNum, pageSize` | Paginated |
| GET | `/api/shortage/pending-count` | — | Pending count, used for the sidebar badge |
| GET | `/api/shortage/reason-distribution` | — | `[{ reason, reasonDesc, count }]` |
| POST | `/api/shortage/handle` | `{ id, handleType, handleRemark?, substituteSkuCode? }` | `PENDING → HANDLED` |
| POST | `/api/shortage/{id}/close` | path parameter | `→ CLOSED` |

### 4.7 Review

| Method | Path | Parameters | Notes |
| --- | --- | --- | --- |
| GET | `/api/review/page` | `reviewNo?, waveNo?, orderNo?, status?, diffType?, reviewerNo?, pageNum, pageSize` | Paginated, includes `shouldItems[]` and `diffItems[]` |
| GET | `/api/review/{reviewNo}` | path parameter | Detail |
| GET | `/api/review/pending-waves` | — | Waves in `PICKED` / `REVIEWING` |
| POST | `/api/review/claim` | `waveNo` (query) | Claim a wave; creates one `WAITING` review per order; reviewer taken from the session |
| POST | `/api/review/submit` | `{ reviewNo, actualQty?, diffItems[], packMaterial?, packageCount?, weight?, remark? }` | Only `WAITING` may be submitted; no diff → `PASS`, otherwise `DIFF` |

### 4.8 Handover

| Method | Path | Parameters | Notes |
| --- | --- | --- | --- |
| GET | `/api/handover/page` | `handoverNo?, carrierCode?, status?, pageNum, pageSize` | Paginated |
| GET | `/api/handover/reviewable-waves` | `carrierCode?` | Waves eligible for handover (`REVIEWED`) |
| POST | `/api/handover/create` | `{ carrierCode, waveNos: [], vehicleNo?, remark? }` | Waves must be `REVIEWED`; handover user taken from the session |
| POST | `/api/handover/confirm` | `{ handoverNo, receiveName?, actualPackageCount?, rejected?, remark? }` | Only `CREATED` may be confirmed; confirmation moves waves and orders to outbound |

### 4.9 Analytics

| Method | Path | Parameters | Notes |
| --- | --- | --- | --- |
| GET | `/api/analytics/overview` | `startDate?, endDate?` | Same shape as `overview` |
| GET | `/api/analytics/employee-efficiency` | `startDate?, endDate?, employeeNo?` | Per-employee order count, item count and efficiency |
| GET | `/api/analytics/error-rate` | `startDate?, endDate?` | Daily error rate |
| GET | `/api/analytics/trend` | `startDate?, endDate?` | Same shape as `trend` |
| GET | `/api/analytics/shortage-distribution` | `startDate?, endDate?` | Shortage reason distribution |

### 4.10 Master data

| Method | Path | Parameters | Notes |
| --- | --- | --- | --- |
| GET | `/api/base/enums` | — | All enum dictionaries, see [section 3](#3-enum-dictionaries-get-apibaseenums) |
| GET | `/api/base/sku/page` | `keyword?, category?, pageNum, pageSize` | Paginated SKUs |
| GET | `/api/base/sku/categories` | — | Distinct SKU categories |
| GET | `/api/base/location/page` | `zone?, pageNum, pageSize` | Paginated locations |
| GET | `/api/base/location/zones` | — | Zone summary (code / name / location count) |
| GET | `/api/base/employee/page` | `role?, team?, pageNum, pageSize` | Paginated employees (contains phone numbers, ADMIN only) |
| GET | `/api/base/employee/by-role` | `role?` | Employees by role, used when assigning pickers |
| GET | `/api/base/employee/teams` | — | Distinct teams (ADMIN only) |
| GET | `/api/base/carrier/list` | — | Carrier list |

### 4.11 Traceability

| Method | Path | Parameters | Notes |
| --- | --- | --- | --- |
| GET | `/api/log/trace` | `bizNo` (query) | Full operation timeline for one document |
| GET | `/api/log/page` | `bizType?, bizNo?, operatorNo?, pageNum, pageSize` | All audit records, ADMIN only |

---

## 5. Aggregate shapes

### 5.1 overview

Shared by `dashboard/summary` and `analytics/overview`.

```json
{
  "totalOrders": 60, "pendingOrders": 12, "pooledOrders": 8,
  "totalWaves": 7, "workingWaves": 3, "finishedWaves": 4,
  "pickedQty": 1280, "outboundOrders": 42, "handedPackages": 96,
  "pickEfficiency": 186.5, "avgPickDurationSec": 1520,
  "errorRate": 0.0031, "shortageRate": 0.012, "reviewPassRate": 0.96,
  "pendingShortageCount": 2,
  "alerts": [{ "metric": "pickEfficiency", "metricName": "Pick efficiency",
               "currentValue": 98, "threshold": 120, "level": "WARN",
               "message": "Pick efficiency below 120 items/hour" }]
}
```

Alert thresholds (defined once in `Stats`):

| Metric | Threshold | Level |
| --- | --- | --- |
| `pickEfficiency` | < 120 items/hour | WARN |
| `errorRate` | > 0.005 | DANGER |
| `shortageRate` | > 0.02 | DANGER |
| `avgPickDurationSec` | > 3600 s | WARN |

### 5.2 trend bucket

Multi-day ranges emit one point per day, with `label` as `YYYY-MM-DD`:

```json
[{ "label": "2026-10-20", "waveCount": 3, "orderCount": 18, "pickedQty": 210,
   "shortageQty": 2, "errorRate": 0.0021, "avgDurationSec": 1380, "pickEfficiency": 198.4 }]
```

When the range is a single day (the dashboard's "today" view) the API emits **24 hourly points** with
`label` as `YYYY-MM-DD HH:00`; the frontend switches the X-axis format based on label length
(`2026-10-20 09:00` → `09:00`). A single daily point would render as one bar and hide the intraday rhythm.

Aggregation basis:

| Field | Bucketed by |
| --- | --- |
| `waveCount`, `orderCount` | Wave **creation** time |
| `pickedQty`, `pickEfficiency`, `shortageQty` | Pick task **finish** time, computed from `actual_qty` / `duration_sec` |
| `errorRate` | Review **finish** time, share of `DIFF` |
| `avgDurationSec` | Mean pick duration of waves in that bucket |

`pickEfficiency` returns `null` (not 0) when no duration was recorded in the bucket — "no data" and "zero
efficiency" are different things. The frontend uses `connectNulls` so known points are joined instead of
drawing a fake dip.

---

## 6. State machines and trigger chains

**Transitions (validated server-side; terminal states are final and steps cannot be skipped)**

| Document | Transition | Constraint |
| --- | --- | --- |
| `order` | CREATED → POOLED → WAVED → PICKED → REVIEWED → OUTBOUND, may be CANCELLED | Only CREATED/POOLED may be cancelled |
| `wave` | CREATED → ASSIGNED → PICKING → PICKED → REVIEWING → REVIEWED → HANDOVERED → CLOSED, may be CANCELLED | Only CREATED/ASSIGNED may be reassigned or cancelled; only HANDOVERED may be archived |
| `pick_task` | WAITING → PICKING → FINISHED (EXCEPTION when a shortage exists) | `start` only from WAITING; `finish` only from PICKING/EXCEPTION |
| `review` | WAITING → PASS / DIFF | Submission requires WAITING; a second submission is a business failure |
| `handover` | CREATED → CONFIRMED / REJECTED | Confirmation requires CREATED |
| `shortage` | PENDING → HANDLED → CLOSED | — |

**Trigger chains (which tables one operation also touches)**

```text
wave/generate    → INSERT wave + wave_order + pick_task + pick_item; order.status = WAVED
pick/start       → pick_task.status = PICKING; wave.status = PICKING
pick/finish      → pick_task.status = FINISHED/EXCEPTION; wave.status = PICKED; order.status = PICKED
review/submit    → review.status = PASS/DIFF; once every review of the wave is settled → wave.status = REVIEWED, order.status = REVIEWED
handover/confirm → handover.status = CONFIRMED; wave.status = HANDOVERED; order.status = OUTBOUND
shortage/handle  → shortage.status = HANDLED; wave.has_shortage = 1
```

**Pick item merge rules**

- Lines are merged per `locationCode + skuCode` within a wave; `shouldQty` is summed and the contributing
  order numbers are recorded in `relatedOrderNos`.
- Items are ordered by `location.pathSeq` to produce the pick path, stored as `pick_item.seq_no`; a
  human-readable route is written to `pick_task.pathText`.
- After merging, the number of `pick_item` rows is on the order of the wave's `locationCount`, not the
  number of order lines.

---

## 7. Maintenance

This document must stay consistent with `controller/*.java`. When the API changes, walk this checklist:

| Change | Where to update |
| --- | --- |
| Add or remove an endpoint | The module table in section 4 **and** the count in the overview table |
| Change parameters | The "Parameters" column of that row |
| Change a response shape | Section 5 |
| Change a state transition | Section 6 |
| Add an enum value | Section 3 |

Verification needs no extra tooling — count the annotations in the controllers and compare with the number
of table rows:

```bash
grep -c "@GetMapping\|@PostMapping" backend/src/main/java/com/example/wms/controller/*.java
```

The total must equal the number of data rows across all tables in section 4. Spot-check a few paths
character by character. A mismatch means an endpoint was changed without a doc update — the code wins, so
fix the document.
