# WMS 仓库拣货与波次管理系统 —— 全链路说明（v4 主文档）

> 本文档整合了四个版本的项目结构、前后端操作、数据库设计、联调验证与常见问题。  
> 面向「要把它跑起来、要改它、要排查它」的场景，按操作顺序组织。

---

## 一、这套系统是什么

面向电商仓 / 配送仓的拣货作业平台，覆盖三类角色（波次员、拣货员、复核员），  
打通一条完整作业链：

```
订单汇聚 → 波次生成 → 拣货执行 → 复核打包 → 出库交接 → 效率复盘
                ↘ 缺货处理 ↗        ↘ 差错统计 ↗
```

### 版本谱系

| 版本         | 目录                      | 定位                                   | 后端                                                         | 前端                                      | 数据库                          | 端口                |
| ---------- | ----------------------- | ------------------------------------ | ---------------------------------------------------------- | --------------------------------------- | ---------------------------- | ----------------- |
| **A 版**    | `F:\素材\code\WMS`        | 第一版，功能最全、带登录与 11 张表                  | Spring Boot 4.1.1 / JDK 27 / JdbcTemplate                  | Vue3 + **TypeScript** + Element Plus    | MySQL `wms`                  | 后端 8080 / 前端 5174 |
| **B 版**    | `F:\素材\code\WMS-v2`     | 第二版，架构分层更规范，但**未接数据库**（内存仓储）         | Spring Boot 3.5.16 / JDK 17 / springdoc + actuator + cache | Vue3 + **JS** + Element Plus（自带 mock 层） | 无（application.yml 里数据源是注释预留） | 后端 8080 / 前端 5173 |
| **v3**     | `F:\素材\code\WMS-v3`     | 融合版：A 的模块 + B 的分层                    | Spring Boot 4.1.1 / JDK 27 / JdbcTemplate                  | Vue3 + TypeScript                       | MySQL `wms_v3`               | 后端 8081 / 前端 5175 |
| **v4.1.0** | `F:\素材\code\WMS-v4.1.0` | **当前版本**：B 的界面 + 重写后端 + 真实数据库 + 真实登录 | Spring Boot 4.1.1 / JDK 27 / JdbcTemplate                  | Vue3 + JS + Element Plus（**已删 mock**）   | MySQL `wms_v4`               | 后端 8080 / 前端 5173 |

> v4 的前端工程结构与 `WMS-v2/frontend` 一致（同一套界面与交互），区别是：删掉了整个 `src/mock`、  
> 请求层只保留真实 axios、补了登录页与路由守卫。后端则完全重写，按 v4 的接口契约实现 54 个接口。

---

## 二、目录结构

```
F:\素材\code\
├─ WMS\                 # A 版（第一版）
├─ WMS-v2\              # B 版（第二版）
├─ WMS-v3\              # 融合版（第三版）
└─ WMS-v4.1.0\              # 当前版本
   ├─ backend\          # Spring Boot 后端
   │  ├─ pom.xml
   │  └─ src\main\
   │     ├─ java\com\example\wms\
   │     │  ├─ WmsApplication.java
   │     │  ├─ config\        TokenService / AuthInterceptor / WebConfig
   │     │  │                 GlobalExceptionHandler / DatabaseInitializer
   │     │  ├─ common\        Dict / JdbcMaps / PasswordHasher（BCrypt）
   │     │  │                 AuthContext / LoginUser / RequireRole / ForbiddenException
   │     │  ├─ service\       Repo（通用查询 + 流水） / Stats（统计口径）
   │     │  ├─ controller\    11 个控制器（54 个接口）
   │     │  └─ dto\           ApiResult / PageResult / LoginRequest / LoginResponse
   │     └─ resources\
   │        ├─ application.properties
   │        ├─ schema.sql      # 建表（幂等）
   │        └─ seed.sql        # 演示数据快照（仅全新库执行）
   ├─ frontend\         # Vue3 前端
   │  ├─ vite.config.js        # 5173，/api → 127.0.0.1:8080
   │  ├─ .env.development      # VITE_USE_MOCK=false
   │  └─ src\
   │     ├─ api\               request.js（真实 axios） / index.js（authApi 等）
   │     ├─ stores\            app.js（登录态 + 待办红点） / dict.js（字典）
   │     ├─ router\            index.js（hash 路由 + 登录守卫）
   │     ├─ layouts\           MainLayout.vue（侧边栏 + 顶栏）
   │     ├─ views\             login / dashboard / order / wave / pick / shortage
   │     │                     review / handover / analytics / base / error
   │     └─ components\        ChartBox / PageHeader / StatCard / StatusTag
   ├─ tools\
   │  ├─ gen-demo.sh      # 一键入口：把演示数据续写到当前时刻
   │  └─ gen-demo-data.py # 生成器本体（随机、自洽、带写入前自检与整批回滚）
   ├─ v4-接口契约.md     # 接口事实依据：全局约定 / 实体字段 / 54 接口 / 状态机
   ├─ 安全漏洞测试.md    # 白盒 + 黑盒 + Web 安全测试报告（24 项问题，含修复验证）
   ├─ 漏洞修复.md        # 12 项已修漏洞的改动清单、实现细节与回滚方式
   ├─ shots-v4\          # 逐页真数据截图
   └─ README.md          # 本文档（怎么跑起来 + 运维）
```

---

## 三、数据库

### 3.1 连接信息

| 项       | 值                                          |
| ------- | ------------------------------------------ |
| 地址      | `127.0.0.1:3306`                           |
| 库名      | `wms_v4`（v3 是 `wms_v3`，A 版是 `wms`，互不干扰）    |
| 账号 / 密码 | `root` / `123456`                          |
| 驱动      | `com.mysql.cj.jdbc.Driver`                 |
| 连接池     | HikariCP，最大 10、最小空闲 2，池名 `WmsV4HikariPool` |
| 自动建库    | URL 带 `createDatabaseIfNotExist=true`      |

配置位置：`backend/src/main/resources/application.properties`

### 3.2 表结构（16 张）

按业务分层：

- **主数据**：`sku`（商品）、`location`（库位）、`carrier`（承运商）、`employee`（员工）、`sys_user`（登录账号）
- **订单**：`order`（订单主表）、`order_item`（订单行）
- **波次**：`wave`（波次）、`wave_order`（波次-订单关系）
- **拣货**：`pick_task`（拣货任务）、`pick_item`（拣货明细）
- **异常**：`shortage`（缺货单）、`review`（复核单）、`review_diff`（复核差异）
- **交接**：`handover`（交接单）
- **追溯**：`operation_log`（操作流水）

> 注意：`order` 是 MySQL 保留字，建表与查询都要加反引号 `` `order` ``。

### 3.3 初始化机制

后端启动时 `DatabaseInitializer` 按两步走：

1. **`schema.sql`** —— 只有建表语句（`CREATE TABLE IF NOT EXISTS`），每次启动都跑，幂等；
2. **`seed.sql`** —— 演示数据（`INSERT IGNORE` + 显式主键），**只在全新库（`carrier` 表为空）时执行一次**。

这样设计是因为数据量已经不小（约 1MB），每次启动都让 MySQL 解析一遍纯属浪费。

`seed.sql` 是**数据库快照**导出的，不是手写的，内容就是当前库里那套数据：  
862 单、1385 条订单行、151 个波次 / 拣货任务、1001 条拣货明细、788 条复核、140 条交接……

想换成新的快照，改完数据后重新导出一份即可：

```bash
mysqldump -uroot -p123456 \
  --default-character-set=utf8mb4 --no-create-info --insert-ignore \
  --complete-insert --compact --no-tablespaces --single-transaction \
  --set-gtid-purged=OFF wms_v4 \
  > backend/src/main/resources/seed.sql
```

> 三点注意：`--insert-ignore` 保证重复执行不会插重；`--set-gtid-purged=OFF` 是为了让导出文件
> 能被回灌（否则带 GTID 的机器会报 `@@GLOBAL.GTID_PURGED cannot be changed`）；
> 导出后记得在文件头补一段注释，说明「这是数据库快照、不是手写脚本」，免得后来人误以为要手工维护。

### 3.4 把演示数据推到当天

快照是静态的，放几天「今日 / 近 7 天」就会空掉。不要手工改时间字段（会把 `pick_duration_sec`、
`total_duration_sec` 这类冗余字段改错），用生成器补数据。

**一条命令就够**（在项目根目录执行，随时可以跑）：

```bash
./tools/gen-demo.sh
```

它做的事：读出库内最后一张订单的时间 → 从这里接着往后随机造单、造波次、造拣货/复核/交接 →
一直写到**当前这一分钟**。跑完打印本次新增的行数；如果数据已经是最新，它会直接说
`数据已是最新，无需生成` 并退出，反复执行不会重复造数据。

常用参数：

```bash
./tools/gen-demo.sh --dry-run        # 只把 SQL 写到 /tmp/wms_gen.sql，不写库（先看看要造什么）
./tools/gen-demo.sh --days 3         # 补最近 3 天（自动跳过库里已有的部分，不会叠加重复）
./tools/gen-demo.sh --start "2026-11-01 09:00" --end "2026-11-03 18:00"   # 精确区间
./tools/gen-demo.sh --no-verify      # 跳过内置自检（不推荐，只在自检误报时用）
```

**内置自检**是这套脚本的关键：写入前会先建一个临时存储过程 `wms_gen_verify`，在同一个事务里
逐条校验，任何一条不通过就 `SIGNAL SQLSTATE '45000'`，**整批 INSERT 一起回滚**，库保持原样。
11 条校验覆盖：

| # | 校验项 | 防的是什么 |
|---|--------|-----------|
| 1-3 | 波次 / 订单 / 拣货·复核·交接·缺货·流水的未来时间 | 造出「明天才发生」的数据 |
| 4 | 单号日期 = 业务时间（SO/WV/PK/RV/HO/SH 六类） | 单号里嵌的日期和实际时间对不上 |
| 5 | 订单下单时间 ≤ 所属波次创建时间 | 波次比订单还早建（时间倒挂） |
| 6 | 波次节点顺序：拣完 ≥ 拣始、复核 ≥ 拣完、交接 ≥ 复核 | 节点乱序 |
| 7-8 | `wave.order_count`、`pick_task.should_qty` 与明细一致 | 汇总字段和明细对不上 |
| 9 | 订单状态与波次状态咬合 | 波次已交接、订单还挂在 `WAVED` |
| 10-11 | 主键重复、孤儿记录 | 明细指向不存在的单据 |

失败时输出形如：

```
写入失败，事务已回滚：
ERROR 1644 (45000) at line 34: 数据自检未通过：订单存在晚于当前时间的节点
```

生成器只**追加**、不删旧数据，规则与库内既有数据一致：

- 单号接库内最大序号往后排（`SO`+日期+6 位、`WV/RV/HO/SH`+日期+3 位以上），不会撞号；
- **单号里的日期取单据自己的时间**，所以跨零点的复核单、交接单会自然落到第二天，不会出现
  「单号写 10-15、时间却是 10-16 凌晨」的错位；
- 状态互相咬合：波次 `HANDOVERED` 的订单必是 `OUTBOUND`，`REVIEWING` 的波次才有 `WAITING` 复核单；
- 汇总字段自洽：`wave.order_count` = 关联订单数、`pick_task.should_qty` = Σ 拣货明细；
- 按「波次创建时刻距现在多久」推导进度，所以历史日全部走完、今天会自然停在
  「早上已交接、午后在拣货、刚建的还在待分配」，各角色登录都有待办。

数据库连接从 `backend/src/main/resources/application.properties` 自动读取，也可用环境变量
`WMS_DB_HOST / WMS_DB_PORT / WMS_DB_NAME / WMS_DB_USER / WMS_DB_PASS` 覆盖；`mysql` 客户端
不在 PATH 时用 `MYSQL_BIN=/path/to/mysql` 指定。

灌完记得重新导出 `seed.sql`（见 3.3 的 mysqldump 命令），否则重建空库又会回到旧日期。

### 3.5 常用运维命令

```bash
# 进入 MySQL
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -uroot -p123456

# 看表与数据量
USE wms_v4;
SHOW TABLES;
SELECT COUNT(*) FROM `order`;

# 重置数据库（谨慎：会清空所有业务数据，重启后端会重新灌种子）
DROP DATABASE wms_v4;

# 备份 / 恢复
mysqldump -uroot -p123456 wms_v4 > wms_v4_backup.sql
mysql -uroot -p123456 wms_v4 < wms_v4_backup.sql
```

---

### 3.6 关键字段与枚举

16 张表的字段清单、各枚举字典的取值，统一维护在 [`v4-接口契约.md`](v4-接口契约.md) 的「二、数据实体字段」与「三、枚举字典」，  
这里不再重复，避免两边不同步。

---

## 四、后端操作

### 4.1 环境要求

- JDK 27（本机 `C:\Program Files\Java\jdk-27`）
- Maven 3.9+（本机在 `C:\Users\F\.m2\wrapper\dists\apache-maven-3.10.0\...\bin\mvn.cmd`，  
  或用 IDEA 自带 Maven；`WMS-v2\tools\` 下也有一份 3.9.11）
- MySQL 8 已启动（3306）

### 4.2 编译与打包

```bash
cd F:\素材\code\WMS-v4.1.0\backend

# 只编译（快，用于验证语法）
mvn -B -DskipTests compile

# 打包成可执行 jar
mvn -B -DskipTests package
# 产物：target\wms-v4-4.1.0.jar（约 26MB，内含依赖）
```

### 4.3 启动

```bash
java -jar target\wms-v4-4.1.0.jar --server.port=8080
```

> **必须显式带 `--server.port=8080`**。本机 shell 环境注入了 `SERVER__PORT=<随机端口>`，  
> 它的优先级高于 `application.properties`，不带参数会被顶到那个随机端口，  
> 而那个端口往往已被宿主占用，直接启动失败（`Port xxxxx was already in use`）。

也可以直接跑 `com.example.wms.WmsApplication`（IDEA 里 Run），启动参数同理。

启动成功标志：

```
Tomcat started on port 8080 (http)
Started WmsApplication in 2.0 seconds
schema.sql 执行完成（建表，幂等）
全新库，seed.sql 执行完成（演示数据已灌入）
```

### 4.4 关键配置项

`application.properties`：

| 配置                              | 说明                |
| ------------------------------- | ----------------- |
| `server.port`                   | 8080              |
| `spring.datasource.url`         | 库名 `wms_v4`，带自动建库 |
| `spring.datasource.hikari.*`    | 连接池参数             |
| `logging.level.com.example.wms` | 日志级别，排查时可调 DEBUG  |

### 4.5 鉴权与统一响应

- 登录：`POST /api/auth/login`，body `{ username, password }`
- 返回：`{ token, employeeNo, employeeName, role, team }`
- 之后所有请求带 `Authorization: Bearer <token>`，token 有效期 8 小时
- 服务端维护 `token → 登录人` 会话表，**身份只认会话，不解析 token 内容**，因此伪造/篡改 token 无效
- 口令存储：BCrypt（cost 10，自带随机盐，依赖 `spring-security-crypto`），库里看不到可逆口令；  
  老库的明文/Base64 口令在启动时自动升级一次
- 授权：写接口用 `@RequireRole` 注解声明角色白名单，不匹配返回 **403**；未标注的只读接口"登录即可访问"

| 接口族                       | 写操作允许的角色                   |
| ------------------------- | -------------------------- |
| `/api/order/**` 汇聚/取消     | ADMIN、WAVE_OPERATOR        |
| `/api/wave/**` 生成/指派/归档   | ADMIN、WAVE_OPERATOR        |
| `/api/pick/**` 执行         | ADMIN、PICKER、WAVE_OPERATOR |
| `/api/review/**` 复核       | ADMIN、REVIEWER             |
| `/api/handover/**` 交接     | ADMIN、REVIEWER             |
| `/api/shortage/**` 处理     | ADMIN、WAVE_OPERATOR        |
| `/api/base/employee/page` | 仅 ADMIN（含手机号等 PII）         |
| `/api/log/page`           | 仅 ADMIN                    |

- 操作人一律由服务端从 token 取（`AuthContext`），**请求参数里的 `pickerNo` / `reviewerNo` 等不再生效**
- 单据状态机在服务端校验：拣货 `WAITING→PICKING→FINISHED`、复核 `WAITING→PASS/DIFF`、交接 `CREATED→CONFIRMED/REJECTED`，  
  终态不可回退、不能跳步，重复提交返回业务失败
- 统一响应体：

```json
{ "code": 0, "message": "ok", "data": {}, "timestamp": 1792389015103 }
```

`code === 0` 表示成功，非 0 时 `message` 是给用户看的错误文案（全局异常处理器统一转换）。  
分页数据固定为 `{ records, total, pageNum, pageSize, pages }`；分页入参 `{ pageNum, pageSize, ...筛选 }`，  
`pageNum` 默认 1、`pageSize` 默认 10。前端 GET 请求会自动追加一个 `_t` 时间戳参数防缓存，后端忽略即可。

**HTTP 状态码**分两层，别混：

| 场景                     | 状态码       | 响应体                     |
| ---------------------- | --------- | ----------------------- |
| 正常                     | 200       | `code=0`                |
| 业务失败（参数校验、状态机冲突）       | **200**   | `code=1` + 具体文案，前端直接弹提示 |
| 未登录 / token 失效         | 401       | `code=401`              |
| 无权限                    | 403       | `code=403`              |
| 路径不存在、方法不对             | 404 / 405 | 固定文案                    |
| 请求参数不合法（类型错、缺参、JSON 坏） | 400       | 固定文案                    |
| 服务端未预期异常               | 500       | 固定文案（堆栈只进日志）            |

### 4.6 接口清单（11 模块 / 54 个）

| 模块   | 前缀               | 数量 | 主要接口                                                                                                                  |
| ---- | ---------------- | -- | --------------------------------------------------------------------------------------------------------------------- |
| 鉴权   | `/api/auth`      | 1  | login                                                                                                                 |
| 看板   | `/api/dashboard` | 2  | summary（含 overview/pipeline/todos）、trend                                                                              |
| 订单   | `/api/order`     | 6  | page、detail、count-by-status、pool、pool-all、cancel                                                                      |
| 波次   | `/api/wave`      | 8  | page、detail、preview、generate、assign、close、cancel、picker-workload                                                      |
| 拣货   | `/api/pick`      | 7  | page、detail、my-tasks、start、confirm-item、report-shortage、finish                                                        |
| 缺货   | `/api/shortage`  | 5  | page、handle、close、pending-count、reason-distribution                                                                   |
| 复核   | `/api/review`    | 5  | page、detail、pending-waves、claim、submit                                                                                |
| 交接   | `/api/handover`  | 4  | page、reviewable-waves、create、confirm                                                                                  |
| 分析   | `/api/analytics` | 5  | overview、trend、employee-efficiency、error-rate、shortage-distribution                                                   |
| 基础数据 | `/api/base`      | 9  | enums、sku/page、sku/categories、location/page、location/zones、employee/page、employee/by-role、employee/teams、carrier/list |
| 追溯   | `/api/log`       | 2  | page、trace                                                                                                            |


写操作的角色限制见 4.5 的权限矩阵。逐接口的方法、入参、业务约束，以及聚合结构（overview / trend）、  
枚举字典、状态机与触发链，全部在 **[`v4-接口契约.md`](v4-接口契约.md)**：  
「四、接口清单」是 54 个接口的明细，「五」是看板聚合结构，「六」是状态机与触发链。  
改完 Controller 记得同步更新契约文档的「四、接口清单」——表格行数应与 Controller 里的注解数一致。

### 4.7 代码分层约定

- `controller` 只做参数校验 + 拼 SQL 条件，不写业务规则以外的逻辑；
- `JdbcMaps` 统一把 snake_case 列名映射成 camelCase，避免为 16 张表写实体类；
- `JdbcMaps.page()` 自动包一层 `COUNT(*)` + `LIMIT/OFFSET`，返回 `PageResult`；
- `Repo` 提供常用查询与 `operation_log` 流水写入；
- `Stats` 集中存放看板与复盘的口径（拣货效率、差错率、通过率等），改算法只改这一处；
- `Dict` 是前后端共用的枚举字典，返回中文描述，前端不再硬编码文案。

---

## 五、前端操作

### 5.1 环境要求

- Node 18+（本机 22.22）
- 依赖已通过目录联接复用 `WMS-v2\frontend\node_modules`（`frontend\node_modules` 是指向它的 junction），  
  所以**不需要重新 npm install**；如果要独立安装，先删掉这个 junction 再 `npm install`。

### 5.2 启动

```bash
cd F:\素材\code\WMS-v4.1.0\frontend
npm run dev        # http://127.0.0.1:5173
```

构建：

```bash
npm run build      # 产物在 dist\
```

### 5.3 端口与代理

`vite.config.js`：

```js
server: {
  port: 5173,
  host: '127.0.0.1',
  proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true } }
}
```

- 前端只监听本机回环，局域网访问不到（内部系统默认收窄）；
- 所有 `/api/**` 请求由 Vite 转发到后端 8080，前端代码里不写死后端地址；
- 若 5173 被占用，Vite 会自动顺延到 5174 并在控制台提示。

### 5.4 登录与账号

已接入真实登录，未登录访问任何页面都会跳 `/login`。

| 用户名      | 姓名 | 角色                 | 密码     |
| -------- | -- | ------------------ | ------ |
| admin    | 何军 | ADMIN（仓库主管）        | 123456 |
| wave     | 王敏 | WAVE_OPERATOR（波次员） | 123456 |
| picker   | 张伟 | PICKER（拣货员）        | 123456 |
| reviewer | 吴倩 | REVIEWER（复核员）      | 123456 |

不同角色看到的菜单不同（由路由 `meta.roles` + `app.canAccess()` 控制）。  
右上角头像菜单可以「切换账号」（会用对应账号重新登录换 token）或「退出登录」。

> 口令在库里存的是 BCrypt 散列，上表只是演示账号的明文口令，便于本机登录。  
> 「人员管理」菜单与 `/api/base/employee/page` 仅 ADMIN 可见，避免普通角色看到全公司手机号。

### 5.5 前端改造点（相对 B 版界面）

| 文件                           | 改动                                                      |
| ---------------------------- | ------------------------------------------------------- |
| `src/api/request.js`         | 删除 mockAdapter 分支，只保留真实 axios；401 自动清 token 跳登录         |
| `src/api/index.js`           | 新增 `authApi.login`                                      |
| `src/stores/app.js`          | `currentUser` 改为登录返回；新增 `setUser / logout / isLoggedIn` |
| `src/router/index.js`        | 新增 `/login` 路由 + 全局守卫（未登录跳登录、角色越权回首页）                   |
| `src/views/login/index.vue`  | 新增登录页（品牌区 + 表单 + 演示账号一键填入）                              |
| `src/layouts/MainLayout.vue` | 去掉「演示数据」标签；账号切换改为真登录；菜单计数改为胶囊/红点                        |
| `.env.development`           | `VITE_USE_MOCK=false`                                   |
| `src/mock/`                  | **整个目录删除**                                              |

### 5.6 侧边栏待办提示

- 展开态：菜单标题右侧显示计数胶囊（`31`），与文字同一基线、右对齐；
- 收起态：标题被 Element Plus 收进 tooltip，改为在图标右上角显示 7px 红点（带呼吸动画），  
  悬停可看到「订单池：31 项待处理」；
- 数据来源：`/api/dashboard/summary` 的 `todos`，页面操作后调用 `window.__wmsRefreshBadge()` 刷新。

---

## 六、A 版 / B 版怎么跑（对照）

### A 版（`F:\素材\code\WMS`）

```bash
# 后端（8080，库 wms）
cd F:\素材\code\WMS\backend
mvn -DskipTests package
java -jar target\wms-0.0.1-SNAPSHOT.jar --server.port=8080

# 前端（5174，TypeScript，带登录页）
cd F:\素材\code\WMS\frontend
npm install
npm run dev
```

特点：11 张表（users / skus / inventory / orders / order_items / waves / wave_items /  
shortages / review_records / error_records / outbounds），14 个页面（含登录、用户管理、库存查询），  
有 `smoke_test.py` 冒烟脚本和完整文档（README / 使用文档 / 开发记录 / 版本对比）。

### B 版（`F:\素材\code\WMS-v2`）

```bash
# 后端（8080，无数据库，数据在内存里）
cd F:\素材\code\WMS-v2\backend
mvn -DskipTests package
java -jar target\wms-picking-wave.jar --server.port=8080
# 接口文档：http://127.0.0.1:8080/swagger-ui.html

# 前端（5173，JS，默认走 mock，不需要后端）
cd F:\素材\code\WMS-v2\frontend
npm run dev
```

特点：Spring Boot 3.5.16 + JDK 17，分层最规范（domain / repository / service / vo / dto），  
带 springdoc（Swagger UI）、actuator、cache、lombok；前端自带 `src/mock`，  
`VITE_USE_MOCK=true` 时完全离线可跑。

---

## 七、联调验证记录（v4）

| 项         | 结果                                           |
| --------- | -------------------------------------------- |
| 后端编译 / 打包 | `BUILD SUCCESS`，30 个源文件，产出约 26MB 可执行 jar     |
| 启动        | Tomcat 8080，启动耗时约 2s，schema 自动执行             |
| 查询接口冒烟    | 19 个 GET 全部 `HTTP 200` + `code=0`            |
| 写操作闭环     | 订单汇聚 → 波次预览 → 波次生成 → 自动分配拣货员 → 联动生成拣货任务，全部成功 |
| 前端页面      | 16 个页面逐页截图，均为真实数据（见 `shots-v4\`）             |
| 代理链路      | 从 5173 请求 `/api/auth/login` 正常返回 token       |

看板真实数据示例：总订单 60、待汇聚 32、在制波次 2、拣货效率 65.3 件/小时、待处理缺货 1。

安全测试（白盒 + 黑盒 + Web）结果见 `安全漏洞测试.md`：两轮共 24 项问题，  
其中严重 1 项（缺角色鉴权）、高 7 项（口令可逆、登录无限流、CORS 任意源、复核/交接重复提交、状态回退、冒名操作）。  
SQL 注入、伪造 token、XSS、用户名枚举均已验证不存在。

已修复 12 项（SEC-01 / SEC-02 / SEC-05 / SEC-06 / SEC-07 / SEC-08 / SEC-09 / SEC-14 / SEC-15 / SEC-18 / SEC-20 / SEC-22），改动清单与复测证据见 `漏洞修复.md`；  
剩余项按测试报告第五章的路线图排期。

---

## 八、常见问题

**1. 后端启动报 `Port 8080 was already in use`**  
先看谁占着：`netstat -ano | findstr :8080`，再用 `taskkill /PID <pid> /F`。  
常见原因是上一个 java 进程没退干净，或同时开了两个版本（A / B / v4 默认都用 8080）。

**2. 后端端口莫名其妙变成 6xxxx**

环境里有 `SERVER__PORT`。启动时显式加 `--server.port=8080` 即可覆盖。

**3. IDEA 里跑不起来，提示「在模块 'WMS-v4.1.0' 中找不到类 WmsApplication」**  
说明 Maven 模块没挂上。右键 `backend/pom.xml` → **Add as Maven Project** → 点 Maven 面板刷新  
（Reload All Maven Projects），然后在运行配置里把模块选成小写 **wms-v4**（不是大写的根模块）。

**4. 前端起来是白屏 / 接口 404**

- 白屏：看浏览器 Console，多半是后端没起，代理 502；
- 404：确认 Vite 代理 target 是 `127.0.0.1:8080`，且后端 `context-path` 是 `/`。

**5. 前端端口变成 5174**  
5173 被占用（比如 A/B 版前端也在跑），Vite 会自动顺延并打印提示，不是错误。

**6. npm 报 `'vite' 不是内部或外部命令`**  
`node_modules/.bin` 丢了（从 Mac 拷过来的工程常见）。删掉 `node_modules` 重新 `npm install`。

**7. 中文路径相关**  
工程路径含中文（`F:\素材\code\...`）。Maven 打包日志里中文可能显示成乱码，属正常；  
但在 Git Bash 里用 `mklink` 创建联接会因引号解析失败，改用 PowerShell 的 `New-Item -ItemType Junction`。

---

## 九、版本演进小结

- **A 版**：把业务闭环、11 张表、登录鉴权都做全了，但前端是 TypeScript、页面偏"管理后台"风格。
- **B 版**：后端分层最规范、带 Swagger，但**没接数据库**，前端靠 mock 演示，数据一刷新就复位。
- **v3**：试着把 A 的模块塞进 B 的骨架，能跑，但两套代码风格混在一起，维护成本高。
- **v4.0.0**：以 B 的界面为底座，后端按界面实际调用重写（56 个接口 / 16 张表 / 真实登录），  
  删掉 mock、补上登录与守卫，接口契约单独成文，前后端各自能独立启动、也能一键联调。
- **v4.1.0**（当前）：安全加固与工程化收尾——角色鉴权、BCrypt 口令、单据状态机、操作人上下文，  
  修掉 12 项安全漏洞；接口精简到 54 个，HTTP 状态码归位（404/400/500 不再一律 200）；  
  表结构拆成 `schema.sql` + `seed.sql`（数据按快照导出）；清掉重复依赖与死代码，目录转为可交付形态。
