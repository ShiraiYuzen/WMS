# 仓库拣货与波次管理系统（WMS）

面向 **电商仓 / 配送仓** 的拣货与波次作业平台，覆盖 **波次员、拣货员、复核员** 三类作业角色，
打通「**订单汇聚 → 波次生成 → 拣货执行 → 复核打包 → 出库交接 → 效率复盘**」的完整闭环，
并支持 **缺货处理** 与 **差错率统计**。

- 后端：Spring Boot 4.1.1 + JDK 27 + Spring JDBC（JdbcTemplate）+ MySQL 8
- 前端：Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router + Axios
- 鉴权：轻量 Token（登录签发，`Authorization: Bearer <token>`）

---

## 一、目录结构

```
F:\素材\code\WMS\
├─ backend\                      Spring Boot 后端
│  ├─ pom.xml
│  └─ src\main\
│     ├─ java\com\example\wms\
│     │  ├─ WmsApplication.java           启动类
│     │  ├─ config\                       鉴权 / 拦截器 / 跨域 / 建表初始化 / 全局异常
│     │  ├─ dto\                          统一响应体、分页、各类请求体
│     │  ├─ entity\                       11 个实体
│     │  ├─ dao\                          JdbcTemplate 数据访问层
│     │  ├─ service\                      业务层（闭环逻辑）
│     │  └─ controller\                   REST 接口
│     └─ resources\
│        ├─ application.properties        端口 / 数据源
│        └─ schema.sql                    建表 + 种子数据（启动时自动执行，幂等）
└─ frontend\                     Vue 3 前端
   ├─ package.json / vite.config.ts / tsconfig*.json
   └─ src\
      ├─ api\                             接口封装（axios）
      ├─ store\user.ts                    登录态
      ├─ router\index.ts                  路由 + 角色守卫
      ├─ styles\global.css
      └─ views\                           各业务页面
```

---

## 二、快速启动

### 1. 数据库

本机 MySQL 8（`127.0.0.1:3306`，`root/123456`）。**无需手工建库**：
数据源 URL 带了 `createDatabaseIfNotExist=true`，应用启动时会自动建 `wms` 库，
并由 `DatabaseInitializer` 执行 `schema.sql`（建表 + 种子数据，可重复执行）。

### 2. 启动后端

```bash
cd F:\素材\code\WMS\backend
F:\素材\code\.tools\apache-maven-3.10.0\bin\mvn -DskipTests package
java -jar target\wms-0.0.1-SNAPSHOT.jar --server.port=8080
```

> ⚠️ 注意：本机 shell 环境注入了 `SERVER__PORT` 变量，会覆盖 `application.properties` 的 `server.port`，
> 因此启动时**必须显式加 `--server.port=8080`**，否则端口会被带偏。

接口前缀：`http://localhost:8080/api`

### 3. 启动前端

```bash
cd F:\素材\code\WMS\frontend
npm install        # 依赖已就绪时可直接跳过
npm run dev        # 开发模式，默认 http://localhost:5174
```

开发服务器已配置代理：`/api` → `http://localhost:8080`，前端无需关心跨域。
生产构建：`npm run build`（产物在 `dist/`）。

---

## 三、演示账号（密码均为 `123456`）

| 用户名 | 姓名 | 角色 | 可见菜单 |
| --- | --- | --- | --- |
| `admin` | 系统管理员 | ADMIN | 全部 |
| `planner` | 李波 | WAVE_PLANNER 波次员 | 工作台、订单汇聚、波次管理、出库交接、缺货处理、差错统计、效率复盘、SKU、库存 |
| `picker1` | 张拣 | PICKER 拣货员 | 工作台、拣货执行、缺货处理 |
| `picker2` | 王拣 | PICKER 拣货员 | 同上 |
| `checker1` | 赵核 | CHECKER 复核员 | 工作台、复核打包、出库交接、差错统计 |
| `checker2` | 孙核 | CHECKER 复核员 | 同上 |

登录页提供演示账号一键填充。

---

## 四、业务闭环与操作路径

| 环节 | 角色 | 页面 | 说明 |
| --- | --- | --- | --- |
| ① 订单汇聚 | 波次员 | 订单汇聚 | 订单列表（按状态/平台/关键字筛选）、手工新建订单、查看明细、取消待分波订单 |
| ② 波次生成 | 波次员 | 波次管理 → 生成波次 | 勾选「待分波」订单，系统**按 SKU 聚合**拣货清单、**校验并锁定库存**、生成波次号 `W+日期+序号`，可指派拣货员 |
| ③ 拣货执行 | 拣货员 | 拣货执行 | 选波次 → 开始拣货 → 按库位顺序填写实拣数量；不足部分自动登记**缺货**（可选原因）→ 提交 → 完成拣货 |
| ④ 复核打包 | 复核员 | 复核打包 | 逐单复核，登记**差错**（错拣/漏拣/多拣/破损/其他）与包装件数，提交后波次进入待出库 |
| ⑤ 出库交接 | 复核员/波次员 | 出库交接 | 填写承运商与交接人，确认后**扣减库存**、订单置为已出库 |
| ⑥ 效率复盘 | 波次员/管理员 | 效率复盘 | 拣货员人效（件/小时、单波平均耗时）、复核员异常率、整体作业指标 |
| 缺货处理 | 波次员/拣货员 | 缺货处理 | 拣货环节产生的缺货记录，标记「已补货 / 已核减」 |
| 差错统计 | 复核员/波次员 | 差错统计 | 差错率、差错类型分布、按拣货员归因、近 7 天趋势、差错明细 |

> 状态流转：订单 `待分波 → 已分波 → 拣货中 → 已拣货 → 已复核 → 已出库`；
> 波次 `待拣货 → 拣货中 → 待复核 → 待出库 → 已出库`。

---

## 五、主要接口

| 模块 | 方法与路径 |
| --- | --- |
| 鉴权 | `POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/auth/me` |
| 订单 | `GET /api/order/list`、`GET /api/order/pending`、`GET /api/order/detail/{id}`、`POST /api/order/add`、`POST /api/order/cancel` |
| 波次 | `GET /api/wave/list`、`GET /api/wave/detail/{id}`、`GET /api/wave/pickable`、`GET /api/wave/reviewable`、`GET /api/wave/outboundable`、`POST /api/wave/create`、`POST /api/wave/assign`、`POST /api/wave/cancel` |
| 拣货 | `GET /api/pick/detail/{waveId}`、`POST /api/pick/start`、`POST /api/pick/submit`、`POST /api/pick/finish` |
| 复核 | `GET /api/review/detail/{waveId}`、`POST /api/review/submit` |
| 出库 | `GET /api/outbound/list`、`POST /api/outbound/submit` |
| 缺货 | `GET /api/shortage/list`、`POST /api/shortage/handle` |
| 差错 | `GET /api/error/list` |
| 统计 | `GET /api/stats/dashboard`、`GET /api/stats/efficiency`、`GET /api/stats/errors` |
| 基础数据 | `GET /api/sku/*`、`GET /api/inventory/list`、`GET /api/user/*` |

除 `POST /api/auth/login` 外，所有接口都需要 `Authorization: Bearer <token>`，否则返回 401。

---

## 六、数据表

| 表 | 说明 |
| --- | --- |
| `users` | 用户与角色（ADMIN / WAVE_PLANNER / PICKER / CHECKER） |
| `skus` | 商品主数据（含默认拣货库位） |
| `inventory` | 库存（按 SKU + 库位；总库存 / 波次锁定 / 可用库存） |
| `orders` | 订单主表（状态、所属波次、平台、优先级） |
| `order_items` | 订单明细 |
| `waves` | 波次（状态、订单数、品项数、件数、拣货员、复核员、各环节时间戳） |
| `wave_items` | 波次拣货清单（按 SKU 聚合） |
| `shortages` | 缺货记录（缺货数、原因、处理状态） |
| `review_records` | 复核记录（通过/异常、包装件数、差错数） |
| `error_records` | 差错明细（错拣/漏拣/多拣/破损/其他） |
| `outbounds` | 出库交接记录（承运商、交接人、件数） |

种子数据包含 6 个账号、16 个 SKU、16 条库存、26 张订单、7 个波次（含 3 个已出库、1 个待拣货、1 个拣货中、1 个待复核、1 个待出库）、
若干缺货 / 复核 / 差错 / 出库记录，登录后各页面即有数据可操作。
