# WMS v5.0 — 漏洞修复记录

| 项 | 值 |
| --- | --- |
| 修复日期 | 2026-10-20 |
| 范围 | SEC-01、SEC-02、SEC-08、SEC-05 / SEC-06 / SEC-07（六条 P0） |
| 顺带修复 | SEC-09（异常脱敏）、SEC-18（404 语义）、SEC-20（审计操作人）、SEC-14（缺货超量）、SEC-15（任务归属）、SEC-22（参数错误返回 400） |
| 改动模块 | 后端 `backend/src/main/java/com/example/wms`、`schema.sql`、一处前端路由 |
| 复测结果 | 12 / 12 通过（6 条 P0 + 6 条顺带）；正向流程（登录 → 拣货 → 复核 → 交接）回归通过 |
| 测试报告 | [`security-audit.md`](security-audit.md)（第 6 节是复测证据） |

> 运行中的系统返回中文界面文案和业务消息。下面代码注释和引用的日志按中文写出，技术事实不变。

---

## 1. 修复一览

| 编号 | 漏洞 | 严重性 | 修复 | 状态 |
| --- | --- | --- | --- | --- |
| SEC-01 | 缺少角色授权 | 严重 | `@RequireRole` 注解 + 拦截器集中校验 + 权限矩阵 | ✅ |
| SEC-02 | 可逆的 Base64 口令存储 | 高 | BCrypt（cost 10）+ 启动迁移 + 登录时透明升级 | ✅ |
| SEC-05 | 复核可以重复提交 | 高 | 写入前检查状态 + 条件更新 `WHERE status='WAITING'` | ✅ |
| SEC-06 | 交接可以重复确认 | 高 | 同样做法；只有 `CREATED` 可以确认 | ✅ |
| SEC-07 | 拣货任务可回退 / 跳步 | 高 | 显式迁移表 + 条件更新 + 乐观检查 | ✅ |
| SEC-08 | 操作人取自请求参数 | 高 | 服务端 `AuthContext`（ThreadLocal）提供调用者 | ✅ |
| SEC-09 | 异常回显 SQL | 中 | 兜底分支返回固定消息；堆栈只进日志 | ✅ |
| SEC-14 | 缺货数量没有上限 | 中 | `1 ≤ 缺货 ≤ 应拣 − 已拣`；实拣不能超过应拣 | ✅ |
| SEC-15 | 不校验任务归属 | 中 | 拣货员强制 `picker_no = 自己`；跨用户访问返回 403 | ✅ |
| SEC-18 | 404 被当成 200 | 低 | `NoResourceFoundException` → 404，静态资源兜底同样处理 | ✅ |
| SEC-20 | 审计日志没有操作人 | 低 | 全部 13 处 `repo.log` 都改用 `AuthContext` | ✅ |
| SEC-22 | 参数类型错误返回 200 | 低 | 类型不匹配 / 缺参数 / JSON 损坏 → 400 | ✅ |

---

## 2. 改动文件

### 2.1 新增

| 文件 | 作用 |
| --- | --- |
| `common/LoginUser.java` | 登录用户记录（工号 / 姓名 / 角色 / 班组），带 `hasRole()` |
| `common/AuthContext.java` | 请求级 ThreadLocal：`employeeNo()` / `role()` / `require()` |
| `common/RequireRole.java` | 角色白名单注解，可用于方法或控制器 |
| `common/PasswordHasher.java` | BCrypt 散列与校验；可校验历史 PBKDF2；解码明文 / Base64 供迁移 |
| `common/ForbiddenException.java` | 授权异常，映射为 HTTP 403 |

### 2.2 修改

| 文件 | 变更 |
| --- | --- |
| `config/TokenService.java` | 会话表从 `token → 过期时间` 改为 `token → Session(LoginUser, 过期时间)`；身份只以服务端为准 |
| `config/AuthInterceptor.java` | 填充 `AuthContext`；读 `@RequireRole` 做授权；403 和 401 分开；`afterCompletion` 里清 ThreadLocal |
| `config/GlobalExceptionHandler.java` | 增加 `ForbiddenException → 403` |
| `config/DatabaseInitializer.java` | 建表之后跑历史口令迁移 |
| `controller/AuthController.java` | 只使用 `PasswordHasher.matches`；去掉 Base64 分支 |
| `controller/PickController.java` | 状态机、归属检查、操作人取自上下文、数量上下限 |
| `controller/ReviewController.java` | 复核员取自上下文，只有 WAITING 可提交，差异行最后写 |
| `controller/HandoverController.java` | 交接人取自上下文，只有 CREATED 可确认，校验波次状态 |
| `controller/WaveController.java` | generate / assign / close / cancel 加 `@RequireRole`；创建人和操作人取自上下文 |
| `controller/OrderController.java` | pool / cancel 加 `@RequireRole`；审计日志带上操作人 |
| `controller/ShortageController.java` | handle / close 加 `@RequireRole`；处理人取自上下文 |
| `controller/BaseDataController.java` | `employee/page` 和 `employee/teams` 限制为 ADMIN |
| `controller/LogController.java` | `log/page` 限制为 ADMIN（`log/trace` 仍可供单据时间线使用） |
| `resources/schema.sql` / `seed.sql` | `sys_user` 口令换成 BCrypt 散列（数据迁进 `seed.sql`） |
| `pom.xml` | 增加 `spring-security-crypto`（只要 crypto 模块） |
| `frontend/src/router/index.js` | 「员工管理」菜单限制为 ADMIN，避免其他角色撞上 403 |

---

## 3. SEC-01 角色授权

### 3.1 设计

以前 `AuthInterceptor` 只检查令牌是否有效，然后把角色放进 `request.setAttribute("loginRole")`，**没有任何代码去读它**——于是每个已登录用户实际上都有最高权限。

修复分三层：

1. **认证**：令牌必须在服务端会话表里，否则 401。
2. **上下文**：登录用户写入 `AuthContext`；控制器从这里读工号，不再信任请求参数。
3. **授权**：拦截器读 `@RequireRole`，不符则返回 403，响应体里写明当前角色。

```java
// AuthInterceptor#preHandle（节选）
LoginUser user = session.user();
AuthContext.set(user);

if (!hasPermission(handler, user)) {
    AuthContext.clear();
    writeError(response, 403, 403, "当前角色（" + roleText(user.role()) + "）不能执行此操作");
    return false;
}

private boolean hasPermission(Object handler, LoginUser user) {
    if (!(handler instanceof HandlerMethod hm)) return true;
    RequireRole rr = hm.getMethodAnnotation(RequireRole.class);            // 先看方法
    if (rr == null) rr = hm.getBeanType().getAnnotation(RequireRole.class); // 再看类
    return rr == null || user.hasRole(rr.value());                         // 没注解 = 任何已登录用户
}
```

用法：

```java
@RequireRole({"ADMIN", "WAVE_OPERATOR"})
@PostMapping("/generate")
public ApiResult<...> generate(...) { ... }
```

### 3.2 实际权限矩阵

| 接口族 | 读 | 写 | 说明 |
| --- | --- | --- | --- |
| `/api/order/**` | 任何已登录用户 | ADMIN、WAVE_OPERATOR | 汇聚、取消 |
| `/api/wave/**` | 任何已登录用户 | ADMIN、WAVE_OPERATOR | 预览、生成、指派、关闭、取消 |
| `/api/pick/**` | 任何已登录用户 | ADMIN、PICKER、WAVE_OPERATOR | 确认明细、上报缺货、开始、完成 |
| `/api/review/**` | 任何已登录用户 | ADMIN、REVIEWER | 领取、提交 |
| `/api/handover/**` | 任何已登录用户 | ADMIN、REVIEWER | 创建、确认 |
| `/api/shortage/**` | 任何已登录用户 | ADMIN、WAVE_OPERATOR | 处理、关闭 |
| `/api/base/employee/page`、`/employee/teams` | ADMIN | — | 含电话等个人信息 |
| 其余 `/api/base/**` | 任何已登录用户 | — | SKU / 库位 / 承运商 / 字典，拣货和复核页需要 |
| `/api/log/page` | ADMIN | — | 完整审计明细 |
| `/api/log/trace` | 任何已登录用户 | — | 单张单据时间线，嵌在页面里 |
| `/api/dashboard/**`、`/api/stats/**`、`/api/analytics/**` | 任何已登录用户 | — | 每个角色都要看板 |

> **取舍**：读接口没有全部收成 ADMIN。前端菜单对所有角色开放 SKU、库位和承运商，拣货页必须读 SKU，复核页必须读承运商——锁死这些接口页面就空了。真正的风险在写操作和个人信息，这两处已经关上。再收紧读权限，必须同时改前端 `meta.roles`。

---

## 4. SEC-02 口令散列

### 4.1 算法选择

**决定：BCrypt，cost 10（每次校验大约 50–100 毫秒），由 `spring-security-crypto` 提供。**

> 演进：第一版用 JDK 的 PBKDF2（21 万次迭代）以免引入新依赖。确认依赖能下载之后，换成报告最初建议的 BCrypt。PBKDF2 校验路径**为了兼容**保留，历史行在下次登录成功时透明升级，不会把人锁在外面。

选 BCrypt 的原因：

- 盐内置，不需要单独的盐列。
- 内存访问模式对 GPU/ASIC 破解不友好，离线攻击比 PBKDF2 更贵。
- `spring-security-crypto` 只依赖已经存在的 `spring-core`，大约 80 KB——**不会**拉进整个 Spring Security，不加过滤器，也不改接口签名。

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

版本由 `spring-boot-dependencies:4.1.1` 管理（当前解析到 7.1.1），没有单独钉死。这里的 4.1.1 是 Spring Boot 的版本，不是本产品的 5.0。

### 4.2 存储格式

```
$2a$10$GXyebhUCt7l55jcb6AGuJeDv9/BBGEEmROd.JG/ncsjcCAF9DZ3Ge
 │   │  └── 22 字符盐 + 31 字符散列（同一口令每次都不同）
 │   └── cost（2^10 轮）
 └── 算法版本
```

`sys_user.password` 是 `VARCHAR(128)`；BCrypt 固定 60 字符，放得下。

### 4.3 三种格式的兼容

| 格式 | 例子 | 登录时 | 处理 |
| --- | --- | --- | --- |
| BCrypt | `$2a$10$…` | 直接校验 | 现行标准 |
| PBKDF2（过渡） | `pbkdf2$210000$…` | 校验成功后**立刻重散列成 BCrypt** | `AuthController` 里透明升级 |
| 明文 / Base64（历史） | `MTIzNDU2` | **拒绝** | 启动时由 `DatabaseInitializer` 一次性迁移 |

登录只接受前两种；明文 / Base64 永远过不了——这是 SEC-02 的核心要求。

```java
// AuthController#login（节选）
String stored = u == null ? null : Repo.str(u.get("password"));
if (!PasswordHasher.matches(req.getPassword(), stored)) {
    throw new IllegalArgumentException("用户名或密码错误");   // 与「无此用户」同一句话，防止枚举
}
// 透明升级历史散列；用户不用改口令
if (PasswordHasher.needsUpgrade(stored)) {
    repo.jdbc().update("UPDATE sys_user SET password=? WHERE username=? AND password=?",
            PasswordHasher.hash(req.getPassword()), req.getUsername().trim(), stored);
}
```

`needsUpgrade()` 除了「不是 BCrypt」，在**cost 被调低**时也返回 true（`BCryptPasswordEncoder#upgradeEncoding`），以后把强度从 10 提到 12 可以平滑过渡。

### 4.4 启动时一次性迁移

明文 / Base64 不能在登录时处理（登录会拒绝它们），所以由启动任务迁移：

```java
// DatabaseInitializer#migrateLegacyPasswords（节选）
String plain = PasswordHasher.decodeLegacy(stored);   // BCrypt / PBKDF2 返回 null
if (plain == null || plain.isBlank()) continue;
jdbc.update("UPDATE sys_user SET password=? WHERE id=? AND password=?",
            PasswordHasher.hash(plain), id, stored);   // 条件更新，可重复执行
```

`decodeLegacy` 先尝试 Base64；结果若全是可打印 ASCII，就当成「Base64 编码的明文」，否则当成明文。两种历史形态（`MTIzNDU2` 和 `123456`）都能无损迁移，不必强制重置。

启动日志会逐行报告：

```
WARN  账号 plaintest 的口令已从明文/Base64 升级为 BCrypt 散列
WARN  已升级 1 条历史口令；请相关账号确认口令强度
```

### 4.5 时序与错误处理

- BCrypt 比较是常数时间；PBKDF2 兼容分支用 `MessageDigest.isEqual`。都不会在第一个不同字节就返回。
- 散列格式损坏时 `matches()` 返回 false；`IllegalArgumentException` 不会传到接口层。

---

## 5. SEC-08 操作人上下文

### 5.1 问题

原来的「操作人」来自请求参数，调用方可以随便填：

```java
// 修复前：PickController#start
public ApiResult<...> start(@PathVariable String taskNo,
                            @RequestParam(required = false) String pickerNo) {
    if (pickerNo != null && !pickerNo.isBlank()) {
        repo.jdbc().update("UPDATE pick_task SET picker_no=?, ...", pickerNo, ...);  // 谁都可以变成谁
    }
}
```

### 5.2 修复

`AuthContext` 由拦截器填充；控制器只读它：

```java
LoginUser me = AuthContext.require();          // 没有就抛异常
repo.log("PICK", taskNo, "PICK_START", "开始拣货", me.employeeNo(), me.employeeName(), me.role(), null);
```

各业务动作的取值规则：

| 场景 | 规则 |
| --- | --- |
| 开始拣货（角色 = PICKER） | `picker_no = 自己`；请求里的 `pickerNo` 忽略 |
| 开始拣货（ADMIN / 波次员代操作） | 保留任务原指派人，**不覆盖** `picker_no`；日志记真实操作人 |
| 领取 / 提交复核 | `reviewer_no = 自己` |
| 创建 / 确认交接 | `handover_user_no = 自己` |
| 上报 / 处理缺货 | `reporter_no` / `handler_no` = 自己 |
| 生成 / 指派 / 关闭 / 取消波次 | `creator_no` / `operator_no` = 自己 |
| 汇聚 / 取消订单 | 日志 `operator_no` = 自己 |

前端仍会发送 `pickerNo`、`reviewerNo` 等参数（`api/index.js`），后端不再接受它们，Spring 会忽略未知查询参数，所以**前端不用改**——行为变成「令牌说了算」。

---

## 6. SEC-05 / SEC-06 / SEC-07 状态机

### 6.1 迁移表

**拣货任务 `pick_task.status`**

| 动作 | 允许的当前状态 | 结果 |
| --- | --- | --- |
| `start` | `WAITING` | `PICKING` |
| `confirm-item` | `WAITING` / `PICKING` / `EXCEPTION` | 不变（更新明细） |
| `report-shortage` | `WAITING` / `PICKING` / `EXCEPTION` | `EXCEPTION` |
| `finish` | `PICKING` / `EXCEPTION` | `FINISHED`（无缺货）/ `EXCEPTION`（有缺货） |

**复核 `review.status`**

| 动作 | 允许的当前状态 | 结果 |
| --- | --- | --- |
| `submit` | `WAITING` | `PASS` / `DIFF` |

**交接 `handover.status`**

| 动作 | 允许的当前状态 | 结果 |
| --- | --- | --- |
| `confirm` / 驳回 | `CREATED` | `CONFIRMED` / `REJECTED` |

### 6.2 两层防护

**第一层：先读再判断，给出人能看懂的错误**

```java
private void assertStatus(Map<String, Object> task, String action, String... allowed) {
    String cur = Repo.str(task.get("status"));
    for (String a : allowed) if (a.equals(cur)) return;
    throw new IllegalStateException("任务当前为「" + Dict.desc("pickTaskStatus", cur) + "」，不能" + action);
}
```

**第二层：条件更新，挡住并发**

即使两个请求都过了第一层，也只有一条 SQL 能命中：

```java
int n = repo.jdbc().update(
    "UPDATE pick_task SET picker_no=?, picker_name=?, status='PICKING', start_time=? " +
    "WHERE task_no=? AND status='WAITING'", pickerNo, pickerName, now, taskNo);
if (n == 0) throw new IllegalStateException("该任务已被他人开始，请刷新后重试");
```

复核提交同样：

```java
int n = repo.jdbc().update("UPDATE review SET ... WHERE review_no=? AND status='WAITING'", ...);
if (n == 0) throw new IllegalStateException("该复核单已被他人提交，请刷新查看");
```

### 6.3 顺带修掉的相关问题

- **差异行的写入顺序**：以前先 `INSERT review_diff` 再更新主表，主表更新失败会留下孤儿差异行。现在条件更新主表成功之后才写差异行。
- **波次状态传递**：`start` 里更新波次增加了 `AND status IN ('CREATED','ASSIGNED')`，已交接的波次不能被拉回 `PICKING`；`finish` 增加 `AND status='PICKING'`。
- **数量边界**：实拣不能超过应拣（超出走复核差异）；缺货必须落在 `1 ~ 未完成数量`。
- **任务归属**：`assertOwnership()` 集中挡住「拣货员操作别人的任务」，`my-tasks` 和 `page` 对拣货员强制 `picker_no = 自己`。

---

## 7. 构建与运行

环境：JDK 27，Maven 3.10.0。

```bash
cd backend
export JAVA_HOME="/path/to/jdk-27"
MVN="/path/to/maven/bin/mvn"

"$MVN" -o -DskipTests package          # 离线构建 → target/wms-v5-5.0.jar
"$JAVA_HOME/bin/java" -jar target/wms-v5-5.0.jar --server.port=8080
```

> 打包前先停掉正在跑的实例。Windows 上 jar 会被锁住，构建失败信息是 `Unable to rename ... wms-v5-5.0.jar to ... .jar.original`。

`spring-security-crypto` 是**唯一新增的依赖**（约 80 KB，另有一个可选传递依赖）。第一次构建需要网络；之后 `.m2` 缓存允许 `-o` 离线构建：

```bash
# 第一次（在线）
"$MVN" dependency:get -Dartifact=org.springframework.security:spring-security-crypto:7.1.1
# 之后（离线）
"$MVN" -o -DskipTests package
```

两条关键启动日志：

```
schema.sql executed (idempotent DDL)
N legacy passwords upgraded; ask the affected accounts to confirm password strength   ← 仅第一次
```

前端依赖不用改——`npm run dev` 仍在 5173，代理到 8080。

---

## 8. 复测结果

完整证据在 [`security-audit.md`](security-audit.md) 第 6 节；这里只列结论：

| 编号 | 复测动作 | 结果 |
| --- | --- | --- |
| SEC-01 | picker → `POST /api/order/pool-all`、`POST /api/wave/generate`、`GET /api/log/page`、`GET /api/base/employee/page` | 全部 `403` |
| SEC-01 | admin → `GET /api/base/employee/page` | `200` |
| SEC-02 | 用 `MTIzNDU2`（Base64 形态）登录 | 「用户名或密码错误」 |
| SEC-02 | `password` 列内容 | BCrypt `$2a$10$…` 散列 |
| SEC-05 | 再次提交已 PASS 的 `RV202610160018` | 拒绝 |
| SEC-06 | 再次确认已 CONFIRMED 的交接单 | 拒绝 |
| SEC-07 | 对已完成任务 `PK20261012003` 执行 `start` | 拒绝（「已完成」） |
| SEC-07 | 对等待中任务 `PK20261020001` 执行 `finish` | 拒绝（「等待中」） |
| SEC-08 | admin 带 `pickerNo=P001` 开始 `PK20261019001` | `picker_no` 仍是 `P002`，操作人记为 `A001` |
| SEC-08 | picker（P001）开始 P003 的任务 | `403`「该任务指派给陈静，你不能操作」 |
| SEC-14 | 上报缺货 9999（待拣 1） | 拒绝（「超过未完成数量」） |
| SEC-15 | picker 查询 `my-tasks?pickerNo=P002` | 只返回调用者自己的 2 条任务 |
| SEC-20 | 汇聚 / 取消 / 关闭后查 `operation_log` | `operator_no` 始终是登录员工，没有 NULL |

正向回归：登录 → 开始 → 确认明细 → 完成可以走通；`duration_sec` 计算正确；看板、拣货任务和员工管理页面正常渲染。

> SEC-09 / SEC-18 / SEC-22 是后来的状态码修复；复测输出在第 11.3 节。

---

## 9. 残留风险

本轮没有处理（排期见测试报告第 4 节）：

| 编号 | 问题 | 建议 |
| --- | --- | --- |
| SEC-03 | 登录失败不计数、不锁定 | 按 `用户名 + IP` 计数，5 次失败锁 10 分钟；网关再限流 |
| SEC-04 | CORS 任意 Origin 且允许凭证 | 改成 `allowedOrigins` 白名单，或同源部署 |
| SEC-10 | 缺少安全响应头 | 加过滤器注入 CSP / X-Frame-Options / Referrer-Policy |
| SEC-11 | 没有登出，令牌 8 小时有效 | `TokenService.remove` 已存在；增加 `POST /api/auth/logout` |
| SEC-12 | 令牌在 localStorage | 改成 HttpOnly + SameSite Cookie（依赖 SEC-04） |
| SEC-13 | 数据库口令在配置文件里 | 环境变量或配置中心 |
| SEC-16 | 订单列表把收件人姓名 / 电话 / 地址返回给拣货员 | 按角色裁剪字段；拣货员只需要 SKU 和库位 |
| SEC-17 | 分页大小和请求体没有上限 | 限制 `pageSize` 和请求体大小 |
| SEC-19 | 登录页写死演示账号 | 生产构建去掉演示块 |
| SEC-21 | 没有 TLS | 在网关终结 TLS 并加 HSTS |
| SEC-23 / SEC-24 | 备注允许 CRLF；`pageSize=0` 得到负的 OFFSET | 写入时去掉控制字符；`pageSize` 最小为 1 |

还有一条： **授权依赖令牌会话**，重启后端会让所有人退出（会话表在内存里）。多实例部署必须把会话表挪到 Redis。

---

## 10. 回滚

改动集中在后端，回滚代价小：

```bash
# 1）还原代码（如果在 git 里）
git checkout -- backend/src/main/java/com/example/wms \
               backend/src/main/resources/schema.sql \
               frontend/src/router/index.js

# 2）回滚口令（只有必须恢复旧 Base64 散列时才做）
UPDATE sys_user SET password = 'MTIzNDU2' WHERE username IN ('admin','wave','picker','reviewer');

# 3）重新构建并重启
```

注意：做了第 2 步还必须还原 `AuthController`，否则历史散列无法登录。同时从 `pom.xml` 去掉 `spring-security-crypto`，并把 `PasswordHasher` 改回纯 PBKDF2，否则会留下没人调用的死代码。本轮没有破坏性数据变更：`pick_task` / `review` / `handover` 的状态列只是被规则约束，不需要回滚数据。

---

## 11. 后续修复：HTTP 状态码语义（SEC-09 / SEC-18 / SEC-22）

修 SEC-18 时发现三条是同一个根因：`GlobalExceptionHandler` 的兜底分支把每种异常都变成 **HTTP 200 + code=1**，于是「路径不存在」「方法不对」「参数类型错误」「SQL 错误」在网关和监控看来都像成功。

### 11.1 变更

`config/GlobalExceptionHandler.java` 现在按异常类型分发：

| 异常 | 状态码 | 消息 |
| --- | --- | --- |
| `NoResourceFoundException`、`NoHandlerFoundException` | 404 | 接口不存在 |
| `HttpRequestMethodNotSupportedException` | 405 | 不支持的请求方法：GET |
| `MethodArgumentTypeMismatchException`、缺参数、请求体解析失败 | 400 | 请求参数不合法，请检查后重试 |
| 其他未预期异常 | 500 | 系统异常，请稍后重试或联系管理员 |
| `IllegalArgumentException` / `IllegalStateException`（业务） | 200 | 具体消息，前端契约不变 |

关键决定：**业务失败仍返回 200 + code=1**。前端 `request.js` 在 `res.code !== 0` 时抛错；把业务失败改成 4xx 就要改 15 个页面的错误处理，收益不大。只有协议层错误改成了真实状态码。

### 11.2 顺带解决的三条

- **SEC-09**：兜底不再回显 `e.getMessage()`；堆栈留在日志里，SQL 片段不再泄露。
- **SEC-18**：`/api/not-exist`、`/actuator`、`/swagger-ui.html` 返回 404 而不是 200，也不再输出 Spring 的 “No static resource”。
- **SEC-22**：参数类型错误返回 400 而不是 200。

### 11.3 复测

```text
正常请求     GET /api/dashboard/summary        HTTP 200  code=0
未知路径     GET /api/not-exist                HTTP 404  接口不存在
静态兜底     GET /actuator                     HTTP 404  接口不存在
方法不对     GET /api/auth/login               HTTP 405  不支持的请求方法：GET
参数类型     GET /api/order/page?pageNum=abc   HTTP 400  请求参数不合法，请检查后重试
缺少参数     GET /api/log/trace                HTTP 400  请求参数不合法，请检查后重试
服务器错误   GET /api/order/page?pageNum=-1    HTTP 500  系统异常，请稍后重试或联系管理员
业务错误     POST /api/wave/assign 空请求体    HTTP 200  code=1 缺少波次号或拣货员
```

回归：看板、缺货和波次页面正常渲染，没有错误提示；401 / 403 行为不变。
