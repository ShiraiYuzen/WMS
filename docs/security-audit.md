# WMS v5.0 — 安全漏洞评估

| 项 | 值 |
| --- | --- |
| 对象 | WMS v5.0（评估当时为 v4.1.0），后端 8080，前端 5173 |
| 被测构建 | 当时为 `wms-v5-4.1.0.jar`，现版本产物为 `wms-v5-5.0.jar`；数据库 `wms_v5`（16 张表） |
| 测试日期 | 2026-10-20 |
| 方法 | 白盒代码审计 + 黑盒接口探测 + Web 前端与传输层检查 |
| 工具 | `curl`、MySQL 命令行、浏览器开发者工具、`npm audit`、源码静态检索（未使用扫描器） |
| 测试账号 | `admin` / `wave` / `picker` / `reviewer`（种子演示口令） |

> 运行中的系统返回中文界面文案和业务消息。本报告里的控制台输出和接口消息按中文记录；状态码、SQL 和字段值不变。

> 范围说明：第一轮（14 条，接口和前端表面）只用只读请求和无副作用探测。第二轮（10 条，业务逻辑和状态机）必须**真实写入**才能验证重复提交和状态回退，因此测试库 `wms_v5` 的演示数据被改过：两张复核单和一张交接单各提交了两次，两条拣货任务状态被改写，若干备注写入了长文本和换行。改动都留在本地测试库——**没有执行 DROP、DELETE 或批量改状态**。要恢复干净数据，重新跑 `schema.sql`（可重复的 DDL 加 `INSERT IGNORE`）即可。

---

## 1. 发现摘要

**24 条发现**：1 严重、7 高、8 中、8 低。第一轮覆盖接口和前端表面（14 条），第二轮补上业务逻辑、状态机、并发和数据层（10 条）。

> **修复状态（2026-10-20）**：`SEC-01`、`SEC-02`、`SEC-05`、`SEC-06`、`SEC-07`、`SEC-08` 已修复，并顺带修了 `SEC-09`（异常泄露）、`SEC-14`（缺货超量）、`SEC-15`（任务归属）、`SEC-18`（404 返回 200）、`SEC-20`（审计操作人）、`SEC-22`（参数错误返回 200）；`SEC-16` 部分缓解。变更和复测见 [`security-fixes.md`](security-fixes.md) 和[第 6 节](#6-修复验证2026-10-20)。其余 12 条（`SEC-03`、`SEC-04`、`SEC-10`、`SEC-11`、`SEC-12`、`SEC-13`、`SEC-16`、`SEC-17`、`SEC-19`、`SEC-21`、`SEC-23`、`SEC-24`）按[第 4 节](#4-修复路线)排期。

| 编号 | 漏洞 | 严重性 | 位置 | 状态 |
| --- | --- | --- | --- | --- |
| SEC-01 | 没有角色授权——任何已登录用户都能做全部管理操作 | **严重** | `AuthInterceptor` / 全部控制器 | ✅ 已修复 |
| SEC-02 | 口令以可逆 Base64 存储，接口也接受 Base64 口令 | **高** | `sys_user.password` / `AuthController` | ✅ 已修复 |
| SEC-03 | 登录没有锁定或限流——可以无限爆破 | **高** | `AuthController#login` | ❌ 未修复 |
| SEC-04 | CORS 允许任意 Origin，同时 `allowCredentials=true` | **高** | `WebConfig#addCorsMappings` | ❌ 未修复 |
| SEC-05 | 复核单可以重复提交，覆盖已完成结果 | **高** | `ReviewController#submit` | ✅ 已修复 |
| SEC-06 | 交接单可以重复确认，覆盖交接数据 | **高** | `HandoverController#confirm` | ✅ 已修复 |
| SEC-07 | 拣货任务允许状态回退，也允许没开始就完成 | **高** | `PickController#start/#finish` | ✅ 已修复 |
| SEC-08 | 操作人取自请求参数——可以冒充他人操作 | **高** | `PickController#start` / `ReviewController#submit` | ✅ 已修复 |
| SEC-09 | 异常回显 SQL 和框架内部信息（读路径和写路径） | 中 | `GlobalExceptionHandler` | ✅ 已修复（顺带） |
| SEC-10 | 安全响应头全部缺失（CSP、X-Frame-Options 等） | 中 | 后端和前端都没设置 | ❌ 未修复 |
| SEC-11 | 令牌不能吊销；没有登出接口；固定 8 小时有效 | 中 | `TokenService` | ❌ 未修复 |
| SEC-12 | 令牌存在 localStorage——任何 XSS 都能把它带走 | 中 | `frontend/src/stores/app.js` | ❌ 未修复 |
| SEC-13 | 数据库口令以明文提交进仓库 | 中 | `application.yml` | ⚠️ 部分处理 |
| SEC-14 | 缺货数量没有上限——可以超过待拣数量 | 中 | `PickController#reportShortage` | ✅ 已修复（顺带） |
| SEC-15 | 不校验任务归属——可以读写别人的任务 | 中 | `PickController#myTasks/#start` | ✅ 已修复（顺带） |
| SEC-16 | 收件人姓名 / 电话 / 地址 / 金额以明文返回给每个角色 | 中 | `OrderController#page` | ⚠️ 部分缓解 |
| SEC-17 | 分页大小和请求体大小没有上限 | 低 | `JdbcMaps.page` / 容器配置 | ❌ 未修复 |
| SEC-18 | 不存在的路径返回 HTTP 200，并带内部错误文本 | 低 | `GlobalExceptionHandler` | ✅ 已修复 |
| SEC-19 | 登录页写死演示账号和口令 | 低 | `views/login/index.vue` | ❌ 未修复 |
| SEC-20 | 部分审计日志没有操作人（审计链断裂） | 低 | `OrderController` 等，`repo.log(..., null, ...)` | ✅ 已修复（顺带） |
| SEC-21 | 全程明文 HTTP，没有 TLS | 低（部署） | 部署层 | ❌ 未修复 |
| SEC-22 | 参数类型错误返回 200，并带框架异常细节 | 低 | 全局参数绑定 | ✅ 已修复（顺带） |
| SEC-23 | 备注原样保存 CRLF——可以伪造日志行 | 低 | `review.remark` / `pick_item.remark` | ❌ 未修复 |
| SEC-24 | `pageSize=0` 使 `pages` 溢出为 `Long.MAX_VALUE` | 低 | `JdbcMaps.page` | ❌ 未修复 |

---

## 2. 发现详情（24 条，按严重性）

**测试条件**：没有第三方扫描器；只用本机的 `curl`、MySQL 命令行和 `npm audit`。第一轮覆盖接口、认证和前端表面。第二轮针对工具找不到、但生产上最疼的一类问题：状态机、重复提交、篡改参数和绕过归属。24 条按严重性排序——**编号就是名次**（01 最严重）——下面不再区分轮次。

### SEC-01 缺少角色授权（严重）✅ 已修复

**问题**
拦截器只检查有没有有效令牌，从不检查这个角色能不能调这个接口。`AuthInterceptor` 把角色放进 `request.setAttribute("loginRole", ...)`，但**项目里没有任何代码读它**：

```java
// AuthInterceptor.java:43-44
request.setAttribute("loginUser", username);
request.setAttribute("loginRole", tokenService.resolveRole(token)); // 设置了，但从没使用
```

同时前端按角色隐藏菜单（`meta.roles`），所以**界面上看到的权限控制只是装饰**。

**复现**

```bash
# 以权限最低的拣货员登录
PT=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"picker","password":"<demo-password>"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

# 调用仅管理员可用的接口
curl -H "Authorization: Bearer $PT" http://127.0.0.1:8080/api/base/employee/page
curl -H "Authorization: Bearer $PT" http://127.0.0.1:8080/api/log/page
curl -H "Authorization: Bearer $PT" http://127.0.0.1:8080/api/analytics/overview
```

**当时观察到的结果**

```
200 code=0  /api/base/employee/page     ← 完整员工档案（工号 / 姓名 / 班组 / 角色）
200 code=0  /api/log/page               ← 完整审计日志
200 code=0  /api/base/sku/page          ← 含成本价的 SKU
200 code=0  /api/analytics/overview     ← 经营分析
```

拣货员同样可以调用写接口，例如 `POST /api/wave/generate`、`POST /api/order/pool-all` 或 `POST /api/wave/{waveNo}/cancel`。

**影响**

- 垂直越权：低权限角色获得全部管理能力。
- 数据泄露：员工档案、审计日志、成本价和经营指标都可读。
- 业务破坏：批量改订单 / 波次状态，且记在真实账号名下，事后难以追责。

**建议**

1. 在拦截器里集中做授权（优先——一处实现，不容易漏）：

```java
// 1）给接口加注解
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole { String[] value(); }

// 2）在拦截器里校验
HandlerMethod hm = (HandlerMethod) handler;
RequireRole rr = hm.getMethodAnnotation(RequireRole.class);
if (rr != null) {
    String role = (String) request.getAttribute("loginRole");
    if (Arrays.stream(rr.value()).noneMatch(r -> r.equals(role))) {
        writeForbidden(response, "当前角色无权执行");   // 403
        return false;
    }
}

// 3）用法
@RequireRole({"ADMIN", "WAVE_OPERATOR"})
@PostMapping("/generate") ...
```

2. 建议的权限矩阵：

| 模块 | ADMIN | WAVE_OPERATOR | PICKER | REVIEWER |
| --- | --- | --- | --- | --- |
| 订单汇聚 / 取消 | ✓ | ✓ | ✗ | ✗ |
| 波次生成 / 指派 / 关闭 | ✓ | ✓ | ✗ | ✗ |
| 拣货执行 | ✓ | ✓ | ✓ | ✗ |
| 缺货处理 | ✓ | ✓ | ✓（仅上报） | ✗ |
| 复核 / 交接 | ✓ | ✗ | ✗ | ✓ |
| 主数据 / 日志 / 分析 | ✓ | 只读 | ✗ | 只读 |

---

### SEC-02 可逆口令存储，并接受 Base64（高）✅ 已修复

**问题**
`sys_user.password` 存的是 `MTIzNDU2`——`123456` 的 Base64。Base64 是编码，不是加密，**任何人都能立刻还原明文**。更糟的是登录接口同时接受明文和 Base64：

```java
// AuthController.java:47-50
String base64 = Base64.getEncoder().encodeToString(input.getBytes(UTF_8));
if (!stored.equals(input) && !stored.equals(base64)) { throw ... }
```

**复现**

```bash
# 明文登录成功
curl -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"123456"}'

# 直接提交库里存的值也成功
curl -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"MTIzNDU2"}'
```

**当时观察到的结果**

```sql
mysql> select username,password from wms_v5.sys_user;
admin     MTIzNDU2      -- = 123456
wave      MTIzNDU2
picker    MTIzNDU2
reviewer  MTIzNDU2
```

两次登录都返回 `code:0` 并发放令牌。

**影响**
数据库一旦泄露（SQL 注入、备份暴露、操作失误），攻击者**一次拿到全部明文口令**；员工复用口令时，影响会扩到邮箱、VPN 和其他外部系统。

**建议**

```java
// 加上 spring-security-crypto（这一个依赖就够，不必引入完整 Security starter）
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

// 登录
if (!encoder.matches(req.getPassword(), stored)) { throw new IllegalArgumentException("用户名或密码错误"); }

// 初始化 / 改口令
String hash = encoder.encode(rawPassword);   // $2a$10$...
```

另外：

- 去掉「同时接受明文和 Base64」的兼容分支，只留一条校验路径。
- 首次登录强制改口令。
- 口令策略：最少 8 位，不能等于用户名，不能全是数字。

---

### SEC-03 登录没有锁定或限流（高）

**问题**
`/api/auth/login` 没有计数、锁定、验证码或延迟。

**复现**

```bash
for i in $(seq 1 8); do
  curl -s -X POST http://127.0.0.1:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"admin\",\"password\":\"wrong$i\"}"
done
```

**当时观察到的结果**

```
第 1 次 http=200 msg=用户名或密码错误
...
第 8 次 http=200 msg=用户名或密码错误     ← 不锁定、不延迟、没有验证码
```

**影响**
加上 SEC-02（6 位数字口令，且登录页能看到账号名），**不到 10 万条的离线字典就能成功**。测得登录延迟大约 4 毫秒（`time_total` 0.0035–0.0045 秒），单线程大约每小时 90 万次，并发可以到千万级。

**建议**

- 失败 5 次锁定 15 分钟，或指数退避（1 秒 / 2 秒 / 4 秒……）。
- 按 IP **和** 账号限流（Bucket4j 或 Redis 计数）。
- 失败写入 `operation_log` 以便告警。
- 生产环境加验证码。

---

### SEC-04 CORS 任意 Origin 且允许凭证（高）

**问题**

```java
// WebConfig.java:19-24
registry.addMapping("/api/**")
        .allowedOriginPatterns("*")     // 反射任意 Origin
        .allowCredentials(true)         // 同时允许 Cookie / 凭证
```

**复现**

```bash
curl -D - -o /dev/null -H "Origin: https://evil.example.com" \
  -H "Authorization: Bearer $T" \
  "http://127.0.0.1:8080/api/order/page?pageSize=1"

curl -D - -o /dev/null -X OPTIONS \
  -H "Origin: https://evil.example.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: authorization" \
  "http://127.0.0.1:8080/api/wave/generate"
```

**当时观察到的结果**

```
Access-Control-Allow-Origin: https://evil.example.com     ← 原样反射
Access-Control-Allow-Credentials: true
Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
Access-Control-Allow-Headers: authorization
Access-Control-Max-Age: 3600
```

**影响**
任意网站都能从受害者浏览器跨域调用这些接口。令牌目前在 `Authorization` 头里（不是 Cookie），没有令牌的攻击者发不出已认证请求，所以**风险降低了但没有消失**：

- 一旦令牌改成 Cookie（很常见的演进），这就变成可读写的 CSRF。
- 现在仍能跨域探测：发现系统、枚举接口、指纹识别版本。

**建议**

```java
registry.addMapping("/api/**")
        .allowedOrigins("http://127.0.0.1:5173", "https://wms.your-domain.com")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("Content-Type", "Authorization")
        .allowCredentials(true)
        .maxAge(3600);
```

生产白名单从环境变量注入——不要写死，也不要用 `*`。

---

### SEC-05 复核单可以重复提交，覆盖已完成结果（高）✅ 已修复

**问题**
`ReviewController#submit` 只按 `reviewNo` 查记录——**既不校验当前状态，也不校验是否已被领取**。

**复现**

```bash
# 对已经 PASS 的复核单再提交一次，把实拣数量改成 1
curl -X POST http://127.0.0.1:8080/api/review/submit \
  -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"reviewNo":"RV202610160018","actualQty":1}'
```

**当时观察到的结果**

```
之前：PASS  2026-10-13 09:24:55  actual_qty=4
之后：PASS  2026-10-20 02:25:35  actual_qty=1     ← 结果被覆盖
```

**影响**

- 已经完成的质量关卡可以随意改写；`PASS` 和 `actual_qty` 矛盾（本应是 `DIFF`）。
- `duration_sec` 被重算，业绩指标失真。
- 反复提交可以造出「零差异」记录，掩盖错发和缺货。

**建议**
只允许从 `WAITING` / `REVIEWING` 提交，并校验领取人：

```java
String status = Repo.str(r.get("status"));
if (!"PENDING".equals(status) && !"REVIEWING".equals(status))
    throw new IllegalStateException("复核单当前为「" + Dict.desc("reviewStatus", status) + "」，不能提交");
if (!Objects.equals(reviewerNo, Repo.str(r.get("reviewerNo"))))
    throw new IllegalStateException("该复核单不是指派给你的");
```

---

### SEC-06 交接单可以重复确认，覆盖数据（高）✅ 已修复

**复现**

```bash
curl -X POST http://127.0.0.1:8080/api/handover/confirm \
  -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"handoverNo":"HO20261012003","receiveName":"张三","actualPackageCount":1}'
```

**当时观察到的结果**

```
之前：CONFIRMED  2026-10-12 09:41:37  package_count=7
之后：CONFIRMED  2026-10-20 02:25:36  package_count=1  receive_name=张三
```

**影响**
已完成的出库交接可以被改写：包裹数 7 → 1，收货人被替换，交接时间被覆盖。丢件纠纷里，单据和实物对不上，责任无法判定。

**建议**
`confirm` 之前校验 `status='PENDING'`（当前表结构是 `CREATED`）；已确认的交接只能走单独的、经过审批的冲销，不能原地覆盖。

---

### SEC-07 拣货任务允许状态回退，并允许跳过 `start`（高）✅ 已修复

**问题**
`start` 和 `finish` 都不校验前一状态。

**复现 A：重新开始已完成的任务**

```bash
curl -X POST "http://127.0.0.1:8080/api/pick/PK20261010001/start?pickerNo=P001" \
  -H "Authorization: Bearer $T"
```

```
之前：FINISHED  start=2026-10-10 09:05:00  finish=2026-10-10 09:21:13
之后：PICKING   start=2026-10-20 02:25:48  finish=2026-10-10 09:21:13   ← 回退，且 finish < start
```

**复现 B：完成一个从未开始的任务**

```bash
curl -X POST "http://127.0.0.1:8080/api/pick/PK20261016007/finish" -H "Authorization: Bearer $T"
```

```
之前：WAITING  start=NULL
之后：FINISHED start=NULL  finish=2026-10-20 02:25:49  duration=0
```

波次及其订单同时被设成 `PICKED`。

**影响**

- 拣货可以凭空「完成」，跳过实际作业，下游复核和交接照常进行——整条链被污染。
- 状态可以倒退，把已记录的工时清零，业绩可以被做手脚。
- 产生脏数据（`finish_time < start_time`、`duration=0`），所有指标失效。

**建议**
用显式白名单驱动迁移，禁止跳步：

```java
// start：只能是 WAITING
if (!"WAITING".equals(status)) throw new IllegalStateException("当前状态不能开始任务");
// finish：只能是 PICKING，且每条明细都已确认
if (!"PICKING".equals(status)) throw new IllegalStateException("任务尚未开始，不能完成");
long pending = jdbc.queryForObject("SELECT COUNT(*) FROM pick_item WHERE task_no=? AND status='PENDING'", Long.class, taskNo);
if (pending > 0) throw new IllegalStateException(pending + " 条明细尚未确认");
```

---

### SEC-08 操作人取自请求参数（高）✅ 已修复

**问题**
`start` 的 `pickerNo`，以及 `review/submit` 的 `reviewerNo` / `reviewerName`，都来自请求参数；服务端从不核实调用者是不是这个员工。

**复现**

```bash
# 调用者是 admin，却可以把任务记到 P001 名下
curl -X POST "http://127.0.0.1:8080/api/pick/PK20261010001/start?pickerNo=P001" \
  -H "Authorization: Bearer $T"
```

**当时观察到的结果**

```
pick_task: PK20261010001  picker_no=P001  picker_name=张伟  status=PICKING
operation_log: operator_no=P001  operator_name=张伟        ← 日志栽到别人头上
```

**影响**

- 任何人都可以陷害同事；审计日志失去证据价值。
- 加上 SEC-20（操作人缺失），日志要么是空的，要么是假的。

**建议**
工号只从令牌上下文取；请求参数一律忽略：

```java
String pickerNo = (String) request.getAttribute("loginUser");
```

如果确实需要「代操作」，单独做经过审批的委托，并在日志里同时记下操作人和被委托人。

---

### SEC-09 异常回显 SQL 和框架细节（中）✅ 已修复（与 SEC-18 一起解决）

**问题**

```java
// GlobalExceptionHandler.java:29-33
@ExceptionHandler(Exception.class)
public ApiResult<Void> handleException(Exception e) {
    log.error("系统异常", e);
    return ApiResult.fail("系统异常：" + e.getMessage());   // 内部细节返回给调用方
}
```

**复现**

```bash
# 负页码 → 负 OFFSET → SQL 语法错误
curl -H "Authorization: Bearer $T" \
  "http://127.0.0.1:8080/api/order/page?pageNum=-1&pageSize=10"
```

**当时观察到的结果**

```json
{
  "code": 1,
  "message": "系统异常：PreparedStatementCallback; bad SQL grammar [SELECT * FROM `order` WHERE 1=1 ORDER BY CASE priority WHEN 'URGENT' THEN 0 ELSE 1 END, pay_time DESC, id DESC LIMIT 10 OFFSET -20]"
}
```

一次请求就泄露了表名、列名、排序逻辑、分页实现，以及使用了 JdbcTemplate。损坏的 JSON 还会返回 `JSON parse error: Unexpected end-of-input ...`，用来指纹识别框架版本。

**影响**
给后续 SQL 注入和未授权查询提供精确的表结构情报，并暴露技术栈版本，便于对已知 CVE。

**建议**

- 返回通用消息（「服务繁忙，请重试」）加错误编号，细节只写日志：

```java
String traceId = UUID.randomUUID().toString();
log.error("系统异常 traceId={}", traceId, e);
return ApiResult.fail("服务繁忙，请重试（错误码 " + traceId + "）");
```

- 入口处夹住分页：`pageNum = Math.max(1, pageNum)`，`pageSize` 限制在 1–200。

---

### SEC-10 缺少安全响应头（中）

**复现**

```bash
curl -D - -o /dev/null http://127.0.0.1:8080/api/order/page
curl -D - -o /dev/null http://127.0.0.1:5173/
```

**当时观察到的结果**
后端和前端都没有发送下面任何一项：

| 响应头 | 作用 | 状态 |
| --- | --- | --- |
| `X-Content-Type-Options: nosniff` | 防止 MIME 嗅探 | 缺失 |
| `X-Frame-Options: DENY` | 防止点击劫持 | 缺失 |
| `Content-Security-Policy` | 限制脚本来源，给 XSS 兜底 | 缺失 |
| `Referrer-Policy` | 防止 Referer 泄露 | 缺失 |
| `Strict-Transport-Security` | 强制 HTTPS | 缺失 |
| `Cache-Control: no-store` | 防止缓存敏感响应 | 缺失 |

`index.html` 也没有 CSP 的 meta 标签。

**影响**

- 页面可以被嵌入做点击劫持（骗管理员去点「取消波次」）。
- 一旦出现 XSS，没有 CSP 兜底。
- 响应可能被中间设备或浏览器缓存。

**建议**

后端加过滤器：

```java
response.setHeader("X-Content-Type-Options", "nosniff");
response.setHeader("X-Frame-Options", "DENY");
response.setHeader("Referrer-Policy", "no-referrer");
response.setHeader("Content-Security-Policy",
    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'");
```

如果前端由 Nginx 提供，在 `server` 块里用 `add_header` 设一次即可。

---

### SEC-11 令牌不能吊销，没有登出接口（中）

**问题**
`TokenService` 把令牌放在内存 Map 里，没有 `logout`。前端的「退出」只清 localStorage，**服务端令牌仍然有效**。

**复现**

```bash
curl -X POST -H "Authorization: Bearer $T" http://127.0.0.1:8080/api/auth/logout
```

**当时观察到的结果**

```json
{"code":1,"message":"系统异常：No static resource api/auth/logout for request '/api/auth/logout'."}
```

接口不存在。令牌内容也很容易解码：

```
$ echo "$T" | base64 -d
A001:26b5d2b9-4d18-4d99-b537-a93e93ba37f3:1792462755585:ADMIN
```

**影响**

- 员工离职、设备丢失或令牌泄露后，会话在 8 小时内无法终止。
- 工号、角色和过期时间以明文暴露，降低侦察成本。
- 重启会使全部令牌失效（这是可用性问题，不是安全问题，但值得知道）。

**建议**

- 增加 `POST /api/auth/logout`，调用 `tokenService.remove(token)`。
- 前端在清本地状态之前先调它。
- 中期改为短寿命 JWT（15 分钟）加刷新令牌，或 Redis 里的服务端会话。
- 角色不要放进令牌载荷；由服务端从数据库或缓存解析。

---

### SEC-12 令牌存在 localStorage（中）

**问题**

```js
// frontend/src/stores/app.js:63
localStorage.setItem('wms_token', loginResult.token)
```

**影响**
localStorage 对 JavaScript 完全可见，任何 XSS（第三方组件、富文本、浏览器扩展）都能一行代码把令牌带走。本项目目前**没有** `v-html`、`eval` 或 `innerHTML`，Vue 默认转义，所以 XSS 面很小——这只表示「还没被利用」，不是「不可能」。

**建议**

- 生产环境用 `HttpOnly + Secure + SameSite=Lax` Cookie 承载会话（同时做 SEC-04 的 CORS 收紧）。
- 如果继续用请求头，至少缩短令牌寿命、加刷新，并启用 CSP（SEC-10）。

---

### SEC-13 数据库口令以明文提交（中）⚠️ 部分处理

**问题**

```properties
# backend/src/main/resources/application.properties:11
spring.datasource.password=123456
```

**影响**
代码或压缩包一旦离开本机（发给同事、推到 Git、交给客户），数据库就暴露了；再叠上 SEC-01，攻击面会更大。

**建议**

- 用环境变量：`spring.datasource.password=${DB_PASSWORD}`。
- 本地通过 `.env` 或 IDE 运行配置注入，并由 `.gitignore` 排除。
- 生产用密钥管理（Vault / KMS / 云密钥库）。
- 按最小权限拆数据库账号：应用账号只有 DML，没有 DDL。

> 现状：配置已改为 `application.yml` 里的 `${WMS_DB_PASS:}`，仓库里不再提交口令，本地默认故意留空，而不是填演示口令。曾经提交过的口令仍在 Git 历史里，应当轮换。

---

### SEC-14 缺货数量没有上限（中）✅ 已修复（顺带）

**复现**

```bash
# 待拣 3 件，上报缺货 9999
curl -X POST http://127.0.0.1:8080/api/pick/report-shortage \
  -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"itemId":447,"shortageQty":9999,"reason":"STOCK_ZERO"}'
```

**当时观察到的结果**

```
之前：should_qty=3  actual_qty=0  shortage_qty=0
之后：should_qty=3  actual_qty=0  shortage_qty=9999  status=SHORTAGE
```

**影响**
缺货可以远超待拣数量，库存差异、缺货率和补货建议都会失真；也可以掩盖内部盗窃（拿走货物，再报「仓库没货」）。

**建议**
强制 `0 <= shortageQty <= shouldQty`，且 `actual + shortage == should`；超出即拒绝。

---

### SEC-15 不校验任务归属（中）✅ 已修复（顺带）

**复现**

```bash
# 拣货员账号查询 P002 的任务
curl -H "Authorization: Bearer $PT" "http://127.0.0.1:8080/api/pick/my-tasks?pickerNo=P002"
```

**当时观察到的结果**

```
返回任务数 = 2   归属 = {'P002'}
```

**影响**
拣货员可以看彼此的任务和拣货路径；叠上 SEC-01 还可以改它们。

**建议**
`my-tasks` 忽略参数，使用登录工号；其他读写都校验 `picker_no = 当前用户`（管理员除外）。

---

### SEC-16 收件人信息以明文返回给每个角色（中）⚠️ 部分缓解

**复现**

```bash
# 用拣货员账号查订单
curl -H "Authorization: Bearer $PT" "http://127.0.0.1:8080/api/order/page?pageSize=1"
```

**当时观察到的结果**

```
收件人：刘杰 | 电话：132****6840 | 地址：***（此处已打码；接口当时返回的是全文） | 金额：1023.38
```

**影响**
拣货员只需要库位、SKU 和数量，不需要收件人姓名、电话、完整地址和订单金额。这是以明文提供给每个角色的个人数据；账号共用或令牌泄露时，客户资料一起泄露。

**建议**

- 按角色裁剪响应字段（拣货视图去掉收件人 / 电话 / 地址 / 金额）。
- 必须展示时打码：`132****6840`、`***`。
- 导出和日志做同样的打码。

---

### SEC-17 分页和请求体没有上限（低）

**观察**

```bash
pageSize=10      → 15ms  10KB
pageSize=10000   → 52ms  68KB
pageSize=500000  → 39ms  68KB（当时只有 60 行，效果还看不出来）

# 5MB 请求体
POST /api/order/pool  5MB JSON  → http=200  73ms（被完整读入并解析）
```

**影响**
当前数据量小，什么都不会坏；到十万行时，`pageSize=1000000` 会把整张表读进内存再序列化，这是很便宜的拒绝服务。过大的请求体同样消耗内存和带宽。

**建议**

- 把 `pageSize` 夹在 1–200，`pageNum` 最小为 1。
- 用 `server.tomcat.max-http-form-post-size` / `max-swallow-size` 限制请求体（例如 1MB）。
- 网关再加一层限流。

---

### SEC-18 不存在的路径返回 HTTP 200（低）✅ 已修复

**观察**

```bash
curl -H "Authorization: Bearer $T" http://127.0.0.1:8080/api/not-exist
```

```json
{"code":1,"message":"系统异常：No static resource api/not-exist for request '/api/not-exist'."}
```

状态码当时是 **200**，不是 404。`/actuator` 和 `/swagger-ui.html` 一样。

**影响**

- 监控和网关无法按状态码发现异常。
- 攻击者可以从消息差异推断哪些路由存在。
- Spring 静态资源处理的内部信息被暴露。

**建议**

- 404 就返回 404，403 返回 403，401 返回 401。
- 未匹配请求走 `ErrorController`，消息固定。

**修复之后（2026-10-20）**

`GlobalExceptionHandler` 增加了分支，把协议错误和业务错误分开：

| 异常 | 状态码 | 响应体 |
| --- | --- | --- |
| `NoResourceFoundException` / `NoHandlerFoundException` | 404 | `{"code":1,"message":"接口不存在"}` |
| `HttpRequestMethodNotSupportedException` | 405 | `"不支持的请求方法：GET"` |
| `MethodArgumentTypeMismatch` / 缺参数 / JSON 解析失败 | 400 | `"请求参数不合法，请检查后重试"` |
| 其他未预期异常 | 500 | `"系统异常，请稍后重试或联系管理员"`（堆栈只记日志） |
| 业务异常（`IllegalArgumentException` / `IllegalStateException`） | **200** | `code=1` 加具体消息（前端契约不变） |

`/actuator`、`/swagger-ui.html` 这类静态兜底路径现在也返回 404，不再出现 “No static resource api/xxx”。

---

### SEC-19 登录页写死演示账号（低）

**问题**

```vue
<div class="demo-title">演示账号（点击填入，密码 123456）</div>
const form = reactive({ username: 'admin', password: '123456' })
```

**影响**
打开登录页就能看到管理员账号和口令；一旦内网被横向进入，最高权限直接交出去。

**建议**

- 只在 `import.meta.env.DEV` 下渲染演示账号。
- 生产构建去掉，或使用会过期的一次性演示账号。

---

### SEC-20 审计日志缺少操作人（低）✅ 已修复（与 SEC-08 一起解决）

**观察**

```sql
select ifnull(operator_no,'(null)'), ifnull(operator_name,'(null)'), count(*)
from wms_v5.operation_log group by 1,2;

(null)  (null)      1
(null)  当前用户     1
W001    王敏         4
...
```

**问题**
部分写路径调用 `repo.log(..., null, null, null, ...)`，操作人为空；少数写死成「当前用户」。

**影响**
发生纠纷或数据异常时，找不到责任人——审计链断了。

**建议**

- `repo.log` 的操作人从 `request.getAttribute("loginUser"/"loginRole")` 取；禁止业务代码传 null。
- 在服务层用 `ThreadLocal` 或 `RequestContextHolder` 自动注入。

---

### SEC-21 全程明文 HTTP（低，部署）

**现状**
后端 8080 和前端 5173 都是 HTTP；口令和令牌明文传输。

**影响**
同一局域网的人可以通过 ARP 欺骗或抓包拿到口令和令牌。

**建议**

- 在 Nginx 或网关终结 TLS，强制 HTTPS 加 HSTS。
- 内网也要开，至少登录接口要开。

---

### SEC-22 参数类型错误返回 200 并带框架细节（低）✅ 已修复（顺带）

```bash
curl -H "Authorization: Bearer $T" "http://127.0.0.1:8080/api/order/page?pageSize=abc"
```

```json
{"code":1,"message":"系统异常：Method parameter 'pageSize': Failed to convert value of type 'java.lang.String' to required type 'int'..."}
```

状态码当时仍是 200，方法签名和参数名被暴露。

**建议**：`@ExceptionHandler(MethodArgumentTypeMismatchException.class)` 返回 400 和固定消息。

---

### SEC-23 备注原样保存 CRLF（低）

```bash
curl -X POST .../api/review/submit -d '{"reviewNo":"RV202610160016","remark":"正常备注\r\n[INFO] 伪造的日志行"}'
```

数据库存的是 `E6ADA3...0D0A5B494E464F5D...`——`\r\n` 原样保留。

**影响**
这些字段以后如果拼进日志文件或导出 CSV，就可以伪造日志行，或触发 Excel 公式注入。

**建议**
写入前去掉 `\r\n` 和控制字符；写日志时转义；CSV 导出时转义开头的 `=`、`+`、`-`、`@`。

---

### SEC-24 `pageSize=0` 使 `pages` 溢出（低）

```bash
curl -H "Authorization: Bearer $T" "http://127.0.0.1:8080/api/order/page?pageSize=0"
```

```json
{"code":0,"data":{"records":[],"total":60,"pageNum":1,"pageSize":0,"pages":9223372036854775807}}
```

`pages = ceil(total / 0)` 把整数除法的边界处理错了，返回了 `Long.MAX_VALUE`。

**建议**：`pageSize` 最小为 1（建议 10），最大 200。

---

## 3. 验证为不能利用（正向发现）

下面这些类型专门测过，**没有找到可利用的问题**；把它们当作必须保住的基线：

| 检查 | 结果 | 依据 |
| --- | --- | --- |
| SQL 注入 | **未发现** | 动态条件都用 `?` 占位符；`orderNo=' OR '1'='1`、`status=' OR 1=1 --` 和日期注入都返回 0 行，结果集没有被改写 |
| 伪造 / 篡改令牌 | **不可行** | 令牌对照服务端 `ConcurrentHashMap` 白名单校验；手造的 `A001:...:ADMIN` Base64 令牌返回 401 |
| 未认证访问 | **被挡住** | 任何不带令牌的 `/api/**` 都返回 401 |
| XSS | **风险低** | 没有 `v-html`、`eval`、`innerHTML` 或 `document.write`；Vue 模板默认转义 |
| 用户名枚举 | **未发现** | 未知用户和错误口令返回同一句「用户名或密码错误」 |
| Actuator / Swagger 暴露 | **未暴露** | `/actuator`、`/v3/api-docs`、`/swagger-ui.html` 没有对应组件（v4 起就不引这些依赖） |
| TRACE 方法 | **已禁用** | 返回 405 |
| 敏感文件暴露 | **基本正常** | Vite 对 `/.env.development` 返回 403；`/package.json` 只在开发环境可读 |

---

## 4. 修复路线

排序标准：**普通业务账号能不能直接利用、会不会破坏核心业务数据、修复成本多大**。第二轮的 SEC-05 到 SEC-08 和 SEC-01 是同一个根因（服务端不校验调用者身份和单据状态），应当一次交付。

### 4.1 分阶段计划

| 阶段 | 编号 | 工作 | 工作量 |
| --- | --- | --- | --- |
| **P0（本周）** | SEC-01 | 角色授权（`@RequireRole` 注解 + 拦截器 + 矩阵）——**已完成** | 0.5 天 |
| **P0（本周）** | SEC-02 | BCrypt 加盐散列，去掉 Base64 兼容分支——**已完成** | 0.5 天 |
| **P0（本周）** | SEC-08 | 操作人始终来自令牌上下文；拒绝请求体里的 `operator` / `pickerNo`——**已完成** | 0.5 天 |
| **P0（本周）** | SEC-05 / SEC-06 / SEC-07 | 写入前执行单据状态机：只能单向，终态拒绝写入——**已完成** | 1 天 |
| **P1（本周）** | SEC-03 / SEC-04 | 登录失败计数和锁定；CORS 白名单 | 0.5 天 |
| **P1（本周）** | SEC-15 / SEC-16 | 任务归属（`pickerNo = 当前用户`）；按角色裁剪收件人字段 | 0.5 天 |
| **P1（下周）** | SEC-09 / SEC-10 / SEC-11 | 异常脱敏（**已完成**）；安全响应头；登出 + 主动使令牌失效 | 1 天 |
| **P2** | SEC-12 / SEC-13 | 基于 Cookie 的会话（HttpOnly + SameSite）；配置外置 | 1 天 |
| **P2** | SEC-14 | 缺货上限（`0 < 缺货 ≤ shouldQty − actualQty`） | 0.2 天 |
| **P3** | SEC-17 / SEC-18 | 分页和请求体上限；正确的状态码（**SEC-18 已完成**） | 0.5 天 |
| **P3** | SEC-19 / SEC-20 | 从界面去掉演示账号（改写到文档）；补齐审计操作人 | 0.3 天 |
| **P3** | SEC-22 / SEC-23 / SEC-24 | 参数类型校验（**SEC-22 已完成**）；备注去掉控制字符；`pageSize` 最小为 1 | 0.3 天 |
| **部署** | SEC-21 | TLS + HSTS + 网关限流 | 视环境而定 |

### 4.2 三处关键改动的实现说明

**① 角色授权（SEC-01 连同 SEC-08）**

```java
// 1）注解
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole { String[] value(); }

// 2）拦截器读取 AuthInterceptor 已经存下的角色
RequireRole rr = handlerMethod.getMethodAnnotation(RequireRole.class);
if (rr != null) {
    String role = (String) request.getAttribute("loginRole");
    if (Arrays.stream(rr.value()).noneMatch(r -> r.equalsIgnoreCase(role))) {
        response.setStatus(403);
        // 统一响应体 { code: 403, message: "无权限" }
        return false;
    }
}

// 3）控制器不再从参数取操作人
String operator = (String) request.getAttribute("loginEmployeeNo");
```

建议的权限矩阵：

| 接口族 | ADMIN | WAVE | PICKER | REVIEWER |
| --- | :---: | :--: | :----: | :------: |
| `/api/base/**`（员工 / SKU / 库位 / 字典） | ✔ | — | — | — |
| `/api/order/**` 读和写 | ✔ | ✔ | — | — |
| `/api/wave/**` 生成 / 指派 | ✔ | ✔ | — | — |
| `/api/pick/**` 执行 | ✔ | — | ✔ | — |
| `/api/review/**` 复核 | ✔ | — | — | ✔ |
| `/api/stats/**`、`/api/log/**` | ✔ | — | — | — |

**② 单据状态机（SEC-05 / SEC-06 / SEC-07）**

三条写路径前面放同一道守卫，禁止「终态后再写」和「跳步」：

```java
private void assertStatus(String current, String... allowed) {
    if (Arrays.stream(allowed).noneMatch(current::equals)) {
        throw new BizException("当前单据状态不允许此操作：" + current);
    }
}
// review:   assertStatus(status, "PENDING");
// handover: assertStatus(status, "PENDING");   // 直接拒绝 CONFIRMED
// pick start:  assertStatus(status, "WAITING");
// pick finish: assertStatus(status, "PICKING");  // 绝不允许 WAITING → FINISHED
```

数据库层再挡一层并发：

```sql
UPDATE review SET status='PASS' WHERE review_no=? AND status='PENDING';
-- 影响行数 = 0 表示已经被处理 → 返回「该复核单已处理」
```

**③ 终态不可逆（SEC-07 的后半）**

当时 `start` 会把 `FINISHED` 拉回 `PICKING` 并清掉 `finish_time`，审计痕迹被毁掉。除了状态守卫，给 `pick_task` 加 `version` 列做乐观锁，并在 `operation_log` 里记录 `before_status → after_status`。

---

## 5. 附录：复测命令

命令都在本机执行；原文笔记里出现 `123456` 的地方，换成当前演示口令。1–9 属于第一轮，10–22 属于第二轮。

### 5.1 第一轮：接口与传输层

```bash
B=http://127.0.0.1:8080
T=$(curl -s -X POST $B/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"123456"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

# 1 未认证
curl -i $B/api/order/page

# 2 伪造令牌
FAKE=$(printf 'A001:x:9999999999999:ADMIN' | base64 | tr '+/' '-_' | tr -d '=')
curl -i -H "Authorization: Bearer $FAKE" $B/api/order/page

# 3 越权（拣货员调管理接口）
PT=$(curl -s -X POST $B/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"picker","password":"123456"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -H "Authorization: Bearer $PT" $B/api/base/employee/page

# 4 SQL 注入
curl -G -H "Authorization: Bearer $T" --data-urlencode "orderNo=' OR '1'='1" $B/api/order/page

# 5 错误信息泄露
curl -H "Authorization: Bearer $T" "$B/api/order/page?pageNum=-1&pageSize=10"

# 6 CORS
curl -D - -o /dev/null -H "Origin: https://evil.example.com" -H "Authorization: Bearer $T" $B/api/order/page

# 7 安全响应头
curl -D - -o /dev/null $B/api/order/page | grep -iE "x-content|x-frame|content-security|referrer"

# 8 爆破
for i in $(seq 1 10); do curl -s -o /dev/null -w "%{http_code} " -X POST $B/api/auth/login \
  -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"w$i\"}"; done; echo

# 9 登出（当时不存在）
curl -X POST -H "Authorization: Bearer $T" $B/api/auth/logout
```

### 5.2 第二轮：业务逻辑与状态机

```bash
B=http://127.0.0.1:8080
login() { curl -s -X POST $B/api/auth/login -H "Content-Type: application/json" \
  -d "{\"username\":\"$1\",\"password\":\"123456\"}" | sed -E 's/.*"token":"([^"]+)".*/\1/'; }
T=$(login admin); PT=$(login picker); RT=$(login reviewer)

# 10 重复提交复核（SEC-05）：同一条提交两次
curl -X POST -H "Authorization: Bearer $RT" -H "Content-Type: application/json" \
  -d '{"reviewNo":"RV202610160018","result":"PASS","actualQty":4}' $B/api/review/submit
curl -X POST -H "Authorization: Bearer $RT" -H "Content-Type: application/json" \
  -d '{"reviewNo":"RV202610160018","result":"PASS","actualQty":1}' $B/api/review/submit
curl -G -H "Authorization: Bearer $RT" --data-urlencode "reviewNo=RV202610160018" $B/api/review/page

# 11 重复确认交接（SEC-06）
curl -X POST -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"handoverNo":"HO20261012003","packageCount":1,"receiveName":"张三"}' $B/api/handover/confirm

# 12 状态回退（SEC-07）：开始一个已经 FINISHED 的任务
curl -X POST -H "Authorization: Bearer $PT" -H "Content-Type: application/json" \
  -d '{"taskNo":"PK20261010001"}' $B/api/pick/start

# 13 跳步（SEC-07）：完成一个 WAITING 的任务
curl -X POST -H "Authorization: Bearer $PT" -H "Content-Type: application/json" \
  -d '{"taskNo":"PK20261016007"}' $B/api/pick/finish

# 14 冒充（SEC-08）：admin 令牌配别人的操作人
curl -X POST -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"taskNo":"PK20261010001","operator":"P001"}' $B/api/pick/start

# 15 超量上报缺货（SEC-14）：待拣 3，上报 9999
curl -X POST -H "Authorization: Bearer $PT" -H "Content-Type: application/json" \
  -d '{"itemId":447,"shortageQty":9999}' $B/api/pick/report-shortage

# 16 读取别人的任务（SEC-15）
curl -G -H "Authorization: Bearer $PT" --data-urlencode "pickerNo=P002" $B/api/pick/my-tasks

# 17 收件人数据暴露（SEC-16）：拣货员查订单列表
curl -G -H "Authorization: Bearer $PT" --data-urlencode "pageSize=1" $B/api/order/page

# 18 参数类型混乱（SEC-22）
curl -H "Authorization: Bearer $T" "$B/api/order/page?pageSize=abc"

# 19 CRLF 注入（SEC-23）：备注里换行，再看十六进制
curl -X POST -H "Authorization: Bearer $RT" -H "Content-Type: application/json" \
  -d '{"reviewNo":"RV202610160018","result":"PASS","remark":"正常\r\n2026-10-20 00:00:00 [ADMIN] 删除了全部数据"}' \
  $B/api/review/submit

# 20 分页除零（SEC-24）
curl -H "Authorization: Bearer $T" "$B/api/order/page?pageNum=1&pageSize=0"
```

### 5.3 并发复测

```bash
# 复用 5.1 的 $B 和 5.2 的 $T
# 21 并发生成波次（检查是否重复分配波次）
for i in 1 2; do curl -s -X POST -H "Authorization: Bearer $T" \
  -H "Content-Type: application/json" -d '{}' $B/api/wave/generate & done; wait; echo

# 22 并发登录（检查限流，30 路并行）
seq 1 30 | xargs -P 30 -I{} curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST $B/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}' | sort | uniq -c
```

> 验收标准：SEC-05 / SEC-06 的第二次请求必须作为业务错误失败；SEC-07 的 `start` 必须报告状态不合法；SEC-08 落库的 `operator` 必须始终等于令牌里的工号；SEC-14 必须拒绝超过可拣数量的值；SEC-15 用 `pickerNo=P002` 查询必须返回空或 403；SEC-22 必须返回 400 而不是 200；SEC-24 的 `pages` 必须正常；22 个并发请求应当出现 429。

---

## 6. 修复验证（2026-10-20）

六条 P0 已修复——SEC-01、SEC-02、SEC-05、SEC-06、SEC-07、SEC-08——加上顺带的 SEC-09、SEC-18、SEC-20、SEC-14、SEC-15 和 SEC-22，一共十二条。代码变更写在 [`security-fixes.md`](security-fixes.md)；本节**只有复测证据**。

复测环境：当时的 `wms-v5-4.1.0.jar` 在 8080 上对着 `wms_v5` 重启，四个演示账号都已迁到 BCrypt（`$2a$10$…`）。当前构建产物是 `wms-v5-5.0.jar`。

### 6.1 逐条结果

| 编号 | 修复前 | 修复后（实测） | 结论 |
| --- | --- | --- | --- |
| SEC-01 | picker 调用 `GET /api/base/employee/page` 返回全部员工 | `HTTP 403`（`code=403`，「当前角色（拣货员）不能执行此操作」） | ✅ |
| SEC-01 | picker 调用 `POST /api/wave/generate` 和 `/api/order/pool-all` 成功 | 两者都是 `HTTP 403` | ✅ |
| SEC-02 | `password` 列是 `MTIzNDU2`（可逆 Base64） | 列为 `$2a$10$…`（BCrypt，60 字符）；Base64 登录返回「用户名或密码错误」 | ✅ |
| SEC-05 | 再次提交已 PASS 的复核单成功，并改写 `actual_qty` | 返回「该复核单已是「通过」，不能再次提交」 | ✅ |
| SEC-06 | 再次确认已 CONFIRMED 的交接单，改写 `package_count` | 返回「该交接单已是「已交接」，不能再次确认」 | ✅ |
| SEC-07 | `FINISHED` 任务可以被拉回 `PICKING` | 返回「任务当前为「已完成」，不能开始」 | ✅ |
| SEC-07 | `WAITING` 任务可以直接完成（duration=0） | 返回「任务当前为「等待中」，不能完成」 | ✅ |
| SEC-08 | admin 令牌带 `pickerNo=P001` 把操作记到 P001 | 落库 `picker_no` 仍是 `P002`；`operation_log.operator_no=A001` | ✅ |
| SEC-08 | picker 可以开始别人的任务（接管了 P003 的） | `HTTP 403`「该任务指派给陈静，你不能操作」 | ✅ |
| SEC-15 | picker 的 `my-tasks?pickerNo=P002` 返回 P002 的 2 条任务 | 只返回调用者自己的 2 条；`pickerNo` 集合始终是 `{P001}` | ✅ |
| SEC-14 | 缺货 9999 被接受 | 返回「缺货 9999 超过未完成数量 0」 | ✅ |
| SEC-20 | 汇聚 / 取消 / 归档日志的 `operator_no` 为 NULL | 全部 13 处 `repo.log` 都带登录工号 | ✅ |
| SEC-18 | `GET /api/not-exist`、`/actuator`、`/swagger-ui.html` 都返回 200 | 三者都返回 `HTTP 404`，`{"code":1,"message":"接口不存在"}` | ✅ |
| SEC-18 | 对 `/api/auth/login` 发 GET | `HTTP 405`「不支持的请求方法：GET」 | ✅ |
| SEC-09 | `pageNum=-1` 触发 SQL 错误 | `HTTP 500`，固定消息；堆栈只在日志里 | ✅ |
| SEC-22 | `pageNum=abc`（类型错误）、`/api/log/trace` 缺少 `bizNo` | 两者都是 `HTTP 400`「请求参数不合法」 | ✅ |

### 6.2 关键复测输出（节选原文）

```text
== SEC-01 写接口按角色授权
  picker   -> POST /api/order/pool-all          : HTTP 403
  picker   -> POST /api/wave/generate           : HTTP 403
  picker   -> GET  /api/log/page                : HTTP 403
  picker   -> GET  /api/base/employee/page      : HTTP 403
  reviewer -> POST /api/wave/assign             : HTTP 403
  admin    -> GET  /api/base/employee/page      : HTTP 200

== SEC-02 历史口令格式被拒绝
  Base64 形态（MTIzNDU2）：用户名或密码错误
  错误口令             ：用户名或密码错误

== SEC-07 状态机
  开始已完成任务 PK20261012003 ：任务为「已完成」，不能开始
  完成未开始任务 PK20261020001 ：任务为「等待中」，不能完成

== SEC-08 冒充与归属
  admin 带 pickerNo=P001 开始 PK20261019001 → status=PICKING pickerNo=P002 pickerName=刘洋
  picker(P001) 开始 PK20261020001            → HTTP 403 该任务指派给陈静
  picker 查询 my-tasks?pickerNo=P002         → 2 条任务，pickerNo 集合 = {P001}

== SEC-05 / SEC-06
  重复提交复核 ：该复核单已是「通过」；请走差异流程更正
  交接第一次   ：成功
  交接第二次   ：该交接单已是「已交接」，不能再次确认

== 审计轨迹（/api/log/trace?bizNo=HO20261020001）
  HANDOVER_CREATE  R001  吴倩
  HANDOVER_CONFIRM R001  吴倩
```

### 6.2.1 状态码一览（SEC-18 复测，2026-10-20）

```text
正常请求     GET /api/dashboard/summary        HTTP 200  code=0
未知路径     GET /api/not-exist                HTTP 404  {"code":1,"message":"接口不存在"}
静态兜底     GET /actuator                     HTTP 404  同上（没有泄露 Spring 内部信息）
方法不对     GET /api/auth/login               HTTP 405  「不支持的请求方法：GET」
参数类型     GET /api/order/page?pageNum=abc   HTTP 400  「请求参数不合法」
缺少参数     GET /api/log/trace                HTTP 400  同上
服务器错误   GET /api/order/page?pageNum=-1    HTTP 500  「系统异常，请稍后重试……」（堆栈只记日志）
业务错误     POST /api/wave/assign 空请求体    HTTP 200  code=1 「缺少波次号或拣货员」（业务失败仍是 200）
```

### 6.3 回归检查（确认正常流程仍然可用）

| 场景 | 结果 |
| --- | --- |
| 四个账号都能登录 | 全部 `code=0`，发放令牌 |
| 拣货员领取自己的任务 `WAITING → PICKING` | 成功，`picker_no=P001` |
| 确认拣货明细（实拣 = 应拣） | 成功，`finished_count` 增加 |
| 完成 `PICKING → FINISHED` | 成功，算出 `duration_sec` |
| 同一任务完成两次 | 拒绝（「已经完成」） |
| 复核员调用 `POST /api/pick/{taskNo}/start` | `HTTP 403` |
| 拣货员可用的读接口（`/pick/page`、`/base/employee/by-role`、`/base/sku/page`、`/dashboard/summary`、`/log/trace`） | 全部 `HTTP 200`；`pick/page` 只返回调用者自己的任务 |
| 前端 16 个页面（看板 / 拣货任务 / 员工管理……） | 正常渲染，没有 403 |
| 实拣 > 应拣，缺货 > 未完成数量 | 拒绝，并给出可读消息 |

### 6.4 本轮之后仍在的风险

- **SEC-03**：没有登录失败计数或锁定；`for` 循环仍能爆破（现在只剩口令强度这一道防线）。
- **SEC-04**：CORS 仍是 `allowedOriginPatterns("*") + allowCredentials(true)`；任意网站都能发起带凭证的跨域调用。
- **SEC-10 / SEC-11 / SEC-12 / SEC-13 / SEC-19 / SEC-21**：安全响应头、登出接口、Cookie 会话、配置外置、登录页演示口令、TLS，都还没动。
- **SEC-16**：`/api/base/employee/page` 现在仅 ADMIN，但订单列表仍把收件人姓名、电话和地址以明文返回给每个角色。
- **SEC-17**：`pageSize` 和请求体仍无上限；`pageSize=500000` 和 5MB 请求体仍会被完整读入。
- **SEC-23 / SEC-24**：备注仍允许 CRLF；`pageSize=0` 仍会产生负的 OFFSET（至少现在返回 500 而不是 200）。

> 说明：角色授权只覆盖**写操作**和**敏感读接口**。SKU / 库位 / 承运商 / 看板 / 分析的只读接口对任何已登录用户开放，以配合前端菜单（拣货页需要 SKU，复核页需要承运商）。再收紧它们，必须在同一次改动里更新前端菜单可见性。

---

*报告结束。SEC-01 / SEC-02 / SEC-05 / SEC-06 / SEC-07 / SEC-08 于 2026-10-20 修复并复测；其余项按第 4 节路线推进。建议为 SEC-01 / SEC-02 / SEC-03 / SEC-04 / SEC-05 / SEC-06 / SEC-07 / SEC-08 补自动化回归测试。*
