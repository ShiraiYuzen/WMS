# 仓库拣货与波次管理系统

面向 **电商仓 / 配送仓** 的仓储作业平台。打通 **订单汇聚 → 波次生成 → 拣货执行 → 复核打包 → 出库交接 → 效率复盘** 六段闭环，
覆盖 **波次员 / 拣货员 / 复核员** 三类角色的分工协作，内置 **缺货处理** 与 **差错率统计** 两条质量主线。

> **当前阶段：纯框架，未接数据库。**
> 前端走内置 Mock 数据层，后端走内存仓储，两端**尚未联通**（这是有意为之）。
> 数据库、前后端联调、登录鉴权由后续迭代接入，届时**业务代码零改动**。

---

## 一、技术栈

| 分层 | 选型 | 说明 |
| --- | --- | --- |
| 前端 | Vue 3.5 + Vite 8 + Vue Router 5 + Pinia 4 | hash 路由，Element Plus 全量引入，ECharts 6 图表 |
| 后端 | Spring Boot 3.5 + JDK 17（release 17） | starter-web / validation / cache / actuator / springdoc |
| 构建 | **项目内置 Maven 3.9.11** | 见 `tools/`，依赖仓库锁定在项目内，不污染 `~/.m2` |
| 文档 | springdoc-openapi | 启动后访问 `/swagger-ui.html` |

---

## 二、目录结构

```
仓库拣货与波次管理系统/
├── README.md                     ← 本文件
├── tools/                        ← 项目自带构建工具（无需本机安装 Maven）
│   ├── apache-maven-3.9.11/      Maven 主程序
│   ├── maven-repo/               本地依赖仓库（项目内）
│   ├── mvnw.sh                   包装器：自动定位 JDK + 锁定本地仓库
│   └── settings.xml              可选镜像源 / JDK profile
├── backend/                      Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/freshmart/wms/
│       │   ├── common/           统一响应 R、分页、错误码、业务异常、单号发号器
│       │   ├── config/           CORS / OpenAPI / 内存事务管理器 / Mock 数据初始化
│       │   ├── controller/       10 个 REST 控制器
│       │   ├── domain/
│       │   │   ├── entity/       15 个实体
│       │   │   └── enums/        13 个业务枚举
│       │   ├── dto/              16 个入参对象
│       │   ├── repository/       CrudRepository 抽象 + 内存实现（接库替换点）
│       │   ├── service/          8 个业务服务
│       │   └── vo/               10 个出参对象
│       └── resources/application.yml
└── frontend/                     Vue 3 前端
    ├── vite.config.js            @ 别名 / api 代理 / 分包策略
    ├── .env.development          VITE_USE_MOCK=true（默认演示模式）
    ├── .env.production           接后端后使用的配置
    ├── .env.mock                 npm run build:mock 的演示构建配置
    └── src/
        ├── api/                  axios 实例 + 各模块接口定义
        ├── mock/                 前端内存数据层（与后端同构）
        ├── router/               18 条路由（含 meta.roles 权限）
        ├── stores/               app（角色切换 / 红点）、dict（字典缓存）
        ├── layouts/MainLayout.vue
        ├── components/           PageHeader / StatCard / StatusTag / ChartBox
        └── views/                16 个业务页面
```

---

## 三、快速开始

### 1. 前端（当前即可完整跑通）

```bash
cd frontend
npm install            # 首次执行
npm run dev            # http://127.0.0.1:5173
```

默认 `VITE_USE_MOCK=true`，**不需要启动后端**，所有页面、所有交互（生成波次、拣货、报缺货、复核、交接）都能真实跑通，
数据保存在浏览器内存中，刷新即复位。

构建演示版（产物自带 Mock，可直接丢到任意静态服务器）：

```bash
npm run build:mock     # 产物在 frontend/dist
npm run build          # 生产构建（走真实后端，需先接库）
```

### 2. 后端

```bash
cd backend
../tools/mvnw.sh clean compile          # 编译
../tools/mvnw.sh spring-boot:run        # 启动，端口 8080
../tools/mvnw.sh clean package -DskipTests   # 打包 → target/wms-picking-wave.jar

java -jar target/wms-picking-wave.jar   # 直接运行
```

启动后：

- 接口文档：<http://localhost:8080/swagger-ui.html>
- 健康检查：<http://localhost:8080/actuator/health>

> 首次编译需要联网下载依赖（会落到 `tools/maven-repo/`）。之后可加 `-o` 走离线模式。

---

## 四、业务闭环与角色

```
订单汇聚 ──▶ 波次生成 ──▶ 拣货执行 ──▶ 复核打包 ──▶ 出库交接 ──▶ 效率复盘
  │            │             │            │            │            │
 订单池      波次管理      拣货任务     复核打包     出库交接     效率复盘
 待汇聚订单   聚类策略      库位合并     逐单核对     按承运商     人效/差错率
             派工分配      路径排序     差异登记     归集交接     缺货原因
                 │             │            │
                 └── 缺货处理 ◀─┘            └── 差错率统计
```

| 角色 | 可见菜单 | 核心职责 |
| --- | --- | --- |
| 波次员 | 看板 / 订单池 / 波次管理 / 缺货处理 / 效率复盘 | 汇聚订单、按策略生成波次、派工、处理缺货 |
| 拣货员 | 看板 / 拣货任务 / 拣货作业 | 按路径逐行扫码确认，缺货上报 |
| 复核员 | 看板 / 复核打包 / 出库交接 | 逐单核对、登记差异与打包、承运商交接 |
| 仓库主管 | 全部 + 基础数据 | 全局监控与主数据维护 |

顶部右侧的**角色切换器**用于演示三种身份下的菜单与待办差异。接入登录后替换为登录用户即可，权限判断逻辑无需改动。

### 关键业务规则

- **波次生成**：支持按区域 / 承运商 / 时效 / 单量四种聚类策略，可设每波上限（订单数、SKU 数、件数），超限自动切批。
- **拣货路线**：波次内所有订单明细按 `库位 | SKU` 合并数量，再按库位 `pathSeq` 排序，形成拣货员单向行走路线。
- **缺货处理**：五种处置方式 —— 拆单出库 / 取消该明细 / 替代商品 / 等待补货 / 整单取消，处理后归档留痕。
- **差错率**：复核差异单数 ÷ 复核总单数；差异需登记类型（少货/多货/错货/破损/串单）与责任环节（拣货/波次/基础数据）。
- **指标预警**：拣货效率 < 120 件/小时、差错率 > 0.5%、缺货率 > 2%、平均拣货时长 > 60 分钟时自动告警（阈值见 `application.yml` 的 `wms.metric`）。

---

## 五、接口清单

统一响应体：`{ "code": 0, "message": "success", "data": {...}, "timestamp": 1730000000000 }`，`code != 0` 为业务失败。
错误码分段：`1xxxx` 通用 / `2xxxx` 订单 / `3xxxx` 波次 / `4xxxx` 拣货 / `5xxxx` 复核 / `6xxxx` 交接 / `7xxxx` 缺货 / `9xxxx` 系统。

<details>
<summary>展开完整接口列表</summary>

**作业看板 `/api/dashboard`**
- `GET /summary` 看板汇总（KPI + 全链路水位 + 待办）
- `GET /trend` 近 7 天趋势

**订单汇聚 `/api/order`**
- `GET /page` 分页 · `GET /{orderNo}` 详情 · `GET /count-by-status` 状态计数
- `POST /pool` 汇聚指定订单 · `POST /pool-all` 一键汇聚 · `POST /{orderNo}/cancel` 取消

**波次生成 `/api/wave`**
- `GET /page` 分页 · `GET /{waveNo}` 详情 · `GET /picker-workload` 拣货员负载
- `POST /preview` 生成预览（不落库） · `POST /generate` 正式生成 · `POST /assign` 派工
- `POST /{waveNo}/close` 归档 · `POST /{waveNo}/cancel` 取消

**拣货执行 `/api/pick`**
- `GET /page` · `GET /{taskNo}` · `GET /my-tasks` 我的待办
- `POST /{taskNo}/start` 开工 · `POST /confirm-item` 确认拣货 · `POST /report-shortage` 上报缺货 · `POST /{taskNo}/finish` 结单

**缺货处理 `/api/shortage`**
- `GET /page` · `GET /{id}` · `GET /pending-count` · `GET /reason-distribution`
- `POST /handle` 处理 · `POST /{id}/close` 归档

**复核打包 `/api/review`**
- `GET /page` · `GET /{reviewNo}` · `GET /pending-waves` 待复核波次
- `POST /claim` 认领波次 · `POST /submit` 提交复核结果

**出库交接 `/api/handover`**
- `GET /page` · `GET /{handoverNo}` · `GET /reviewable-waves` 可交接波次
- `POST /create` 创建交接单 · `POST /confirm` 确认 / 拒收

**效率复盘 `/api/analytics`**
- `GET /overview` · `GET /employee-efficiency` · `GET /error-rate` · `GET /trend` · `GET /shortage-distribution`

**基础数据 `/api/base`**
- `GET /enums` 全量字典 · `GET /sku/page` · `GET /sku/categories`
- `GET /location/page` · `GET /location/zones` · `GET /employee/page` · `GET /employee/by-role` · `GET /employee/teams` · `GET /carrier/list`

**作业追溯 `/api/log`**
- `GET /page` 操作日志 · `GET /trace` 按业务单号追溯全链路

</details>

---

## 六、接数据库与联调指引

本阶段刻意把「无数据库」做成可替换结构，接库时按下面三步走，**Service / Controller / 前端页面都不用动**。

### 步骤 1：后端启用数据库

`backend/pom.xml` 中已预留依赖区，取消注释即可：

```xml
<dependency>mybatis-plus-boot-starter 3.5.17</dependency>
<dependency>mysql-connector-j</dependency>
<dependency>druid-spring-boot-3-starter</dependency>
```

`backend/src/main/resources/application.yml` 中同样预留了 `spring.datasource` 与 `mybatis-plus` 配置块，填上连接信息后取消注释。

### 步骤 2：把内存仓储换成 Mapper

`repository/CrudRepository<T>` 的方法签名是**刻意按 MyBatis-Plus `BaseMapper` 语义设计**的：

| CrudRepository | BaseMapper 对应 |
| --- | --- |
| `save(entity)` | `insert` / `updateById` |
| `findById(id)` | `selectById` |
| `findAll()` | `selectList(null)` |
| `find(predicate)` | `selectList(Wrapper)` |
| `deleteById(id)` | `deleteById` |

所以只需：

1. 新增 `XxxMapper extends BaseMapper<Xxx>`（15 个实体各一个）；
2. 让对应的 `XxxRepository` 从「继承 `InMemoryCrudRepository`」改为「委托 Mapper」；
3. 删掉 `MockDataInitializer`（或把 `wms.mock.enabled` 置为 `false`）。

> `InMemoryTransactionManagerConfig` 上的 `@ConditionalOnMissingBean(PlatformTransactionManager.class)`
> 会让它在 DataSource 就位后**自动让位**给 Spring Boot 的真实事务管理器，无需手工删除。

### 步骤 3：前端切到真实接口

改一行环境变量：

```dotenv
# frontend/.env.development
VITE_USE_MOCK=false
```

`src/api/request.js` 会自动摘掉 mock 适配器，改用真实 HTTP；Vite 的 `/api` 代理已指向 `http://localhost:8080`。
`src/mock/` 目录可以整体删除，不影响任何业务代码。

---

## 七、已知约束

- **JDK 27 + Lombok**：Lombok 1.18.42 及以下在 JDK 27 下编译会抛 `ExceptionInInitializerError: com.sun.tools.javac.tree.EndPosTable`，本项目已锁定 **1.18.48**。若升级 JDK 请同步确认 Lombok 版本。
- **Maven 位于项目内**：请统一使用 `tools/mvnw.sh`，不要用系统 `mvn`，否则依赖会落到 `~/.m2` 而不是 `tools/maven-repo/`。
- **CORS 当前全放开**（`WebMvcConfig`），仅便于本地联调，上生产前必须收敛到白名单。
- **无登录鉴权**：角色由前端切换器模拟，接入登录后需补 `token` 校验与接口级权限。
- **前端 Mock 数据存内存**，刷新即复位；固定随机种子 `20261016`，保证每次数据一致，便于演示与截图。
