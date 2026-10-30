# 仓库拣货与波次管理系统 · 第三版（融合版）

面向 **电商仓 / 配送仓** 的拣货与波次作业平台，覆盖 **波次员、拣货员、复核员** 三类角色，
打通「**订单汇聚 → 波次生成 → 拣货执行 → 复核打包 → 出库交接 → 效率复盘**」完整闭环，
支持 **缺货处理** 与 **差错率统计**。

> 本版是**融合版**：以「可落地的第二版」为底座（真 MySQL + 登录鉴权 + 前后端联通），
> 吸收「第一版的广度与表现力」（库位/承运商/操作日志、波次策略与预览、拣货员工作量、ECharts 图表、菜单分组）。
> 详细改动见同目录 **`第三版改动说明.md`**。

- 后端：Spring Boot 4.1.1 + JDK 27 + Spring JDBC（JdbcTemplate）+ MySQL 8
- 前端：Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router + Axios + **ECharts 5**
- 鉴权：轻量 Token（登录签发，`Authorization: Bearer <token>`，8 小时）

---

## 一、目录结构

```
F:\素材\code\WMS-v3\
├─ backend\                      Spring Boot 后端
│  ├─ pom.xml                    wms-v3:3.0.0
│  └─ src\main\
│     ├─ java\com\example\wms\
│     │  ├─ WmsApplication.java
│     │  ├─ config\              鉴权 / 操作日志 / 跨域 / 建表初始化 / 全局异常
│     │  ├─ dto\                 统一响应体、分页、各类请求体
│     │  ├─ entity\              14 个实体（新增 WarehouseLocation / Carrier / OperationLog）
│     │  ├─ dao\                 数据访问层（新增 LocationDao / CarrierDao / OperationLogDao）
│     │  ├─ service\             业务层（新增 BaseDataService / OperationLogService）
│     │  └─ controller\          REST 接口（新增 BaseDataController / LogController）
│     └─ resources\
│        ├─ application.properties   端口 8081 / 库 wms_v3
│        └─ schema.sql               14 张表 + 种子数据（启动幂等执行）
└─ frontend\                     Vue 3 前端
   └─ src\
      ├─ api\                    接口封装（新增 base.ts / log.ts）
      ├─ store\user.ts           登录态
      ├─ router\index.ts         路由 + 角色守卫
      └─ views\                  17 个页面（新增 库位/承运商/操作日志）
```

---

## 二、快速启动

### 1. 数据库

本机 MySQL 8（`127.0.0.1:3306`，`root/123456`）。**无需手工建库**：
数据源 URL 带 `createDatabaseIfNotExist=true`，应用启动自动建 `wms_v3` 库，
并由 `DatabaseInitializer` 执行 `schema.sql`（幂等）。

### 2. 启动后端

```bash
cd F:\素材\code\WMS-v3\backend
F:\素材\code\.tools\apache-maven-3.10.0\bin\mvn -DskipTests package
java -jar target\wms-v3-3.0.0.jar --server.port=8081
```

> ⚠️ 本机 shell 注入了 `SERVER__PORT` 变量，会覆盖配置端口，启动时**必须显式加 `--server.port=8081`**。

接口前缀：`http://localhost:8081/api`

### 3. 启动前端

```bash
cd F:\素材\code\WMS-v3\frontend
npm install
npm run dev        # http://localhost:5175
```

开发服务器已配置代理：`/api` → `http://localhost:8081`。

> 第二版（`F:\素材\code\WMS\`）用 8080 / 5174，**两版可同时运行**。

---

## 三、演示账号（密码均为 `123456`）

| 用户名 | 姓名 | 角色 | 可见菜单 |
| --- | --- | --- | --- |
| `admin` | 系统管理员 | ADMIN | 全部（含用户管理、操作日志） |
| `planner` | 李波 | WAVE_PLANNER 波次员 | 工作台、订单汇聚、波次管理、出库交接、缺货处理、差错统计、效率复盘、SKU、库位、库存、承运商 |
| `picker1` / `picker2` | 张拣 / 王拣 | PICKER 拣货员 | 工作台、拣货执行、缺货处理 |
| `checker1` / `checker2` | 赵核 / 孙核 | CHECKER 复核员 | 工作台、复核打包、出库交接、差错统计 |

登录页提供演示账号一键填充。

---

## 四、业务闭环与操作路径

| 环节 | 角色 | 页面 | 说明 |
| --- | --- | --- | --- |
| ① 订单汇聚 | 波次员 | 订单汇聚 | 订单查询 / 新建 / 取消 |
| ② 波次生成 | 波次员 | 波次管理 | 勾选待分波订单 → **可选策略** → **预览**（校验库存）→ 生成波次（锁库存 + 指派） |
| ③ 拣货执行 | 拣货员 | 拣货执行 | 开始 → 按库位填实拣数（不足自动登记缺货）→ 提交 → 完成 |
| ④ 复核打包 | 复核员 | 复核打包 | 逐单复核，登记差错 |
| ⑤ 出库交接 | 复核员/波次员 | 出库交接 | 选**承运商**（来自承运商管理）+ 交接人 → 扣库存出库 |
| ⑥ 效率复盘 | 波次员/管理员 | 效率复盘 | 人效、异常率、整体指标 |
| 缺货处理 | 波次员/拣货员 | 缺货处理 | 标记已补货 / 已核减 |
| 差错统计 | 复核员/波次员 | 差错统计 | 差错率、类型分布、按人归因、趋势 |
| 库位管理 | 波次员/管理员 | 库位管理 | 库位增删改查、按库区筛选 |
| 承运商管理 | 波次员/管理员 | 承运商管理 | 承运商增删改查、启停用 |
| 操作日志 | 管理员 | 操作日志 | 所有写操作自动留痕，可按模块查询 |

> 状态流转：订单 `待分波 → 已分波 → 拣货中 → 已拣货 → 已复核 → 已出库`；
> 波次 `待拣货 → 拣货中 → 待复核 → 待出库 → 已出库`。

---

## 五、第三版新增接口

| 模块 | 方法与路径 |
| --- | --- |
| 库位 | `GET /api/base/location/page`、`/all`、`/zones`、`POST /api/base/location/save`、`/delete` |
| 承运商 | `GET /api/base/carrier/page`、`/list`、`POST /api/base/carrier/save`、`/delete` |
| 操作日志 | `GET /api/log/page` |
| 波次增强 | `POST /api/wave/preview`、`GET /api/wave/picker-workload` |

其余接口与第二版一致（鉴权 / 订单 / 波次 / 拣货 / 复核 / 出库 / 缺货 / 差错 / 统计 / SKU / 库存 / 用户）。
除 `POST /api/auth/login` 外，所有接口都需要 `Authorization: Bearer <token>`。

---

## 六、数据表（14 张）

原有 11 张：`users`、`skus`、`inventory`、`orders`、`order_items`、`waves`、`wave_items`、
`shortages`、`review_records`、`error_records`、`outbounds`。

**第三版新增 3 张**：

| 表 | 说明 |
| --- | --- |
| `warehouse_location` | 库位（编码、库区、类型、状态、容量），19 条种子 |
| `carrier` | 承运商（编码、名称、联系人、电话、状态），6 条种子 |
| `operation_log` | 操作日志（模块、动作、方法、接口、操作人、角色、结果、时间） |

---

## 七、版本说明

- **第一版**：`F:\素材\仓库拣货与波次管理系统\`（纯框架 / 演示，未接库、无鉴权）
- **第二版**：`F:\素材\code\WMS\`（真 MySQL + 鉴权 + 联通，模块较少）
- **第三版（本版）**：`F:\素材\code\WMS-v3\`（第二版底座 + 第一版广度）
