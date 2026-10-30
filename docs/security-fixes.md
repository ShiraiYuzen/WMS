# WMS v4.1.0 — Vulnerability Fix Record

| Item | Value |
| --- | --- |
| Fix date | 2026-10-20 |
| Scope | SEC-01, SEC-02, SEC-08, SEC-05 / SEC-06 / SEC-07 (six P0 findings) |
| Incidental fixes | SEC-09 (exception sanitisation), SEC-18 (404 semantics), SEC-20 (audit operator), SEC-14 (shortage over-reporting), SEC-15 (task ownership), SEC-22 (400 on parameter errors) |
| Modules touched | Backend `backend/src/main/java/com/example/wms`, `schema.sql`, one frontend route |
| Retest result | 12 of 12 passed (6 P0 + 6 incidental); positive flow (login → picking → review → handover) regression passed |
| Test report | [`security-audit.md`](security-audit.md) (section 6 holds the retest evidence) |

> Language note: the running system returns Chinese UI labels and business messages. Code comments and quoted
> log lines here are reproduced in English translation; the technical facts are unchanged.

---

## 1. Fix overview

| ID | Vulnerability | Severity | Fix | Status |
| --- | --- | --- | --- | --- |
| SEC-01 | Missing role authorization | Critical | `@RequireRole` annotation + central interceptor check + permission matrix | ✅ |
| SEC-02 | Reversible Base64 password storage | High | BCrypt (cost 10) + startup migration + transparent upgrade on login | ✅ |
| SEC-05 | Review re-submission | High | State guard before write + conditional update `WHERE status='WAITING'` | ✅ |
| SEC-06 | Handover re-confirmation | High | Same approach; only `CREATED` may be confirmed | ✅ |
| SEC-07 | Pick task rollback / skipped steps | High | Explicit transition table + conditional updates + optimistic checks | ✅ |
| SEC-08 | Operator taken from request parameters | High | Server-side `AuthContext` (ThreadLocal) supplies the caller | ✅ |
| SEC-09 | Exceptions echo SQL | Medium | Fallback branch returns a fixed message; stack trace only in logs | ✅ |
| SEC-14 | Unbounded shortage quantity | Medium | `1 ≤ shortage ≤ should − picked`; actual may not exceed should | ✅ |
| SEC-15 | Task ownership not validated | Medium | Pickers forced to `picker_no = self`; cross-user access returns 403 | ✅ |
| SEC-18 | 404 returned as 200 | Low | `NoResourceFoundException` → 404, static fallback handled the same way | ✅ |
| SEC-20 | Audit log missing operator | Low | All 13 `repo.log` call sites now use `AuthContext` | ✅ |
| SEC-22 | Parameter type errors returned 200 | Low | Type mismatch / missing parameter / malformed JSON → 400 | ✅ |

---

## 2. Changed files

### 2.1 Added

| File | Purpose |
| --- | --- |
| `common/LoginUser.java` | Signed-in user record (employee no / name / role / team) with `hasRole()` |
| `common/AuthContext.java` | Request-scoped ThreadLocal context: `employeeNo()` / `role()` / `require()` |
| `common/RequireRole.java` | Role whitelist annotation, applicable to methods or a controller |
| `common/PasswordHasher.java` | BCrypt hashing and verification; verifies legacy PBKDF2; decodes plaintext/Base64 for migration |
| `common/ForbiddenException.java` | Authorization exception mapped to HTTP 403 |

### 2.2 Modified

| File | Change |
| --- | --- |
| `config/TokenService.java` | Session table changed from `token → expiry` to `token → Session(LoginUser, expiry)`; the server is now the only source of identity |
| `config/AuthInterceptor.java` | Populates `AuthContext`; reads `@RequireRole` for authorization; separates 403 from 401; clears the ThreadLocal in `afterCompletion` |
| `config/GlobalExceptionHandler.java` | Adds a `ForbiddenException → 403` mapping |
| `config/DatabaseInitializer.java` | Runs the legacy password migration after the schema |
| `controller/AuthController.java` | Uses `PasswordHasher.matches` only; the Base64 branch is removed |
| `controller/PickController.java` | State machine, ownership checks, operator from context, quantity bounds |
| `controller/ReviewController.java` | Reviewer from context, submission only from WAITING, diff rows written last |
| `controller/HandoverController.java` | Handover user from context, confirmation only from CREATED, wave status validation |
| `controller/WaveController.java` | `@RequireRole` on generate / assign / close / cancel; creator and operator from context |
| `controller/OrderController.java` | `@RequireRole` on pool / cancel; audit log gains the operator |
| `controller/ShortageController.java` | `@RequireRole` on handle / close; handler from context |
| `controller/BaseDataController.java` | `employee/page` and `employee/teams` restricted to ADMIN |
| `controller/LogController.java` | `log/page` restricted to ADMIN (`log/trace` stays available for document timelines) |
| `resources/schema.sql` / `seed.sql` | `sys_user` passwords replaced with BCrypt hashes (data migrated into `seed.sql`) |
| `pom.xml` | Adds `spring-security-crypto` (the crypto module only) |
| `frontend/src/router/index.js` | "Employee management" menu restricted to ADMIN so other roles do not hit 403 |

---

## 3. SEC-01 Role authorization

### 3.1 Design

`AuthInterceptor` used to check only whether the token was valid, then parked the role in
`request.setAttribute("loginRole")` where **nothing ever read it** — so every authenticated user effectively
shared the highest privilege.

The fix has three layers:

1. **Authentication** — the token must exist in the server-side session table, otherwise 401.
2. **Context** — the signed-in user is written into `AuthContext`; controllers read the employee number from
   there and no longer trust request parameters.
3. **Authorization** — the interceptor reads `@RequireRole` and returns 403 with the current role named in the
   body when it does not match.

```java
// AuthInterceptor#preHandle (excerpt)
LoginUser user = session.user();
AuthContext.set(user);

if (!hasPermission(handler, user)) {
    AuthContext.clear();
    writeError(response, 403, 403, "The current role (" + roleText(user.role()) + ") may not perform this operation");
    return false;
}

private boolean hasPermission(Object handler, LoginUser user) {
    if (!(handler instanceof HandlerMethod hm)) return true;
    RequireRole rr = hm.getMethodAnnotation(RequireRole.class);            // method first
    if (rr == null) rr = hm.getBeanType().getAnnotation(RequireRole.class); // then the class
    return rr == null || user.hasRole(rr.value());                         // unannotated = any authenticated user
}
```

Usage:

```java
@RequireRole({"ADMIN", "WAVE_OPERATOR"})
@PostMapping("/generate")
public ApiResult<...> generate(...) { ... }
```

### 3.2 Permission matrix (as implemented)

| Endpoint family | Read | Write | Notes |
| --- | --- | --- | --- |
| `/api/order/**` | any authenticated | ADMIN, WAVE_OPERATOR | Pool, cancel |
| `/api/wave/**` | any authenticated | ADMIN, WAVE_OPERATOR | Preview, generate, assign, close, cancel |
| `/api/pick/**` | any authenticated | ADMIN, PICKER, WAVE_OPERATOR | Confirm items, report shortage, start, finish |
| `/api/review/**` | any authenticated | ADMIN, REVIEWER | Claim, submit |
| `/api/handover/**` | any authenticated | ADMIN, REVIEWER | Create, confirm |
| `/api/shortage/**` | any authenticated | ADMIN, WAVE_OPERATOR | Handle, close |
| `/api/base/employee/page`, `/employee/teams` | ADMIN | — | Contains phone numbers and other PII |
| Remaining `/api/base/**` | any authenticated | — | SKU / location / carrier / dictionary, needed by the picking and review pages |
| `/api/log/page` | ADMIN | — | Full audit detail |
| `/api/log/trace` | any authenticated | — | Per-document timeline, embedded in pages |
| `/api/dashboard/**`, `/api/stats/**`, `/api/analytics/**` | any authenticated | — | Every role needs the dashboard |

> **Trade-off**: read endpoints were not all tightened to ADMIN. The frontend menus expose SKU catalogue,
> location management and carriers to every role, and the picking page must read SKUs while the review page
> must read carriers — locking those down would empty the pages. The real risk sits in writes and PII, and
> those are fully closed. Tightening reads further requires changing the frontend `meta.roles` at the same time.

---

## 4. SEC-02 Password hashing

### 4.1 Algorithm choice

**Decision: BCrypt with cost 10 (about 50–100 ms per verification), provided by `spring-security-crypto`.**

> Evolution: the first iteration used the JDK's PBKDF2 (210,000 iterations) to avoid new dependencies. Once
> dependency downloads were confirmed to work, it was replaced with the BCrypt the report originally
> recommended. The PBKDF2 verification path is kept for **backward compatibility**, so legacy rows upgrade
> transparently on the next successful login and nobody is locked out.

Why BCrypt:

- Salts are built in, so no separate salt column is needed.
- Its memory access pattern is hostile to GPU/ASIC cracking, making offline attacks more expensive than
  PBKDF2.
- `spring-security-crypto` depends only on `spring-core` (already present) and is about 80 KB — it does
  **not** pull in the whole Spring Security framework, adds no filters and changes no endpoint signature.

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

The version is managed by `spring-boot-dependencies:4.1.1` (currently resolving to 7.1.1) and is not pinned.

### 4.2 Storage format

```
$2a$10$GXyebhUCt7l55jcb6AGuJeDv9/BBGEEmROd.JG/ncsjcCAF9DZ3Ge
 │   │  └── 22-char salt + 31-char hash (differs on every hash of the same password)
 │   └── cost (2^10 rounds)
 └── algorithm version
```

`sys_user.password` is `VARCHAR(128)`; BCrypt is a fixed 60 characters, which fits with room to spare.

### 4.3 Compatibility strategy for three formats

| Format | Example | At login | Handling |
| --- | --- | --- | --- |
| BCrypt | `$2a$10$…` | Verified directly | Current standard |
| PBKDF2 (v1 interim) | `pbkdf2$210000$…` | **Rehashed to BCrypt immediately** after a successful check | Transparent upgrade in `AuthController` |
| Plaintext / Base64 (legacy) | `MTIzNDU2` | **Rejected** | One-off migration at startup by `DatabaseInitializer` |

Only the first two are accepted at login; plaintext/Base64 never passes — that is the core requirement of
SEC-02.

```java
// AuthController#login (excerpt)
String stored = u == null ? null : Repo.str(u.get("password"));
if (!PasswordHasher.matches(req.getPassword(), stored)) {
    throw new IllegalArgumentException("Incorrect username or password");   // same message as "no such user", prevents enumeration
}
// Upgrade legacy hashes transparently; the user does not need to change their password
if (PasswordHasher.needsUpgrade(stored)) {
    repo.jdbc().update("UPDATE sys_user SET password=? WHERE username=? AND password=?",
            PasswordHasher.hash(req.getPassword()), req.getUsername().trim(), stored);
}
```

Besides "not BCrypt", `needsUpgrade()` also returns true when **the cost was lowered**
(`BCryptPasswordEncoder#upgradeEncoding`), so raising the strength from 10 to 12 later is a smooth transition.

### 4.4 One-off migration at startup

Plaintext / Base64 cannot be handled at login (login rejects them), so they are migrated by a startup task:

```java
// DatabaseInitializer#migrateLegacyPasswords (excerpt)
String plain = PasswordHasher.decodeLegacy(stored);   // returns null for BCrypt / PBKDF2
if (plain == null || plain.isBlank()) continue;
jdbc.update("UPDATE sys_user SET password=? WHERE id=? AND password=?",
            PasswordHasher.hash(plain), id, stored);   // conditional update, idempotent
```

`decodeLegacy` first attempts Base64; if the result is entirely printable ASCII it treats the value as
"Base64-encoded plaintext", otherwise as plaintext. Both historical forms (`MTIzNDU2` and `123456`) migrate
losslessly without forcing a reset.

The startup log reports each row:

```
WARN  Password for account plaintest upgraded from plaintext/Base64 to a BCrypt hash
WARN  1 legacy password upgraded; ask the affected accounts to confirm password strength
```

### 4.5 Timing and error handling

- BCrypt compares in constant time; the PBKDF2 compatibility branch uses `MessageDigest.isEqual`. Neither
  short-circuits on the first differing byte.
- A malformed hash makes `matches()` return false; `IllegalArgumentException` is never propagated to the API
  layer.

---

## 5. SEC-08 Operator context

### 5.1 Problem

In the original implementation the "operator" came from request parameters, so callers could put anything
there:

```java
// Before the fix: PickController#start
public ApiResult<...> start(@PathVariable String taskNo,
                            @RequestParam(required = false) String pickerNo) {
    if (pickerNo != null && !pickerNo.isBlank()) {
        repo.jdbc().update("UPDATE pick_task SET picker_no=?, ...", pickerNo, ...);  // anyone could become anyone
    }
}
```

### 5.2 Fix

`AuthContext` is populated by the interceptor; controllers only read it:

```java
LoginUser me = AuthContext.require();          // throws if absent
repo.log("PICK", taskNo, "PICK_START", "Picking started", me.employeeNo(), me.employeeName(), me.role(), null);
```

Resolution rules per business action:

| Scenario | Rule |
| --- | --- |
| Picking start (role = PICKER) | `picker_no = self`; any `pickerNo` in the request is ignored |
| Picking start (ADMIN / WAVE acting) | Keep the task's original assignee and **do not overwrite** `picker_no`; the log records the real operator |
| Review claim / submit | `reviewer_no = self` |
| Handover create / confirm | `handover_user_no = self` |
| Shortage report / handle | `reporter_no` / `handler_no` = self |
| Wave generate / assign / close / cancel | `creator_no` / `operator_no` = self |
| Order pool / cancel | Log `operator_no` = self |

The frontend still sends `pickerNo`, `reviewerNo` and similar parameters (`api/index.js`), but the backend no
longer accepts them and Spring ignores unknown query parameters, so **no frontend change was required** — the
behaviour simply becomes "the token wins".

---

## 6. SEC-05 / SEC-06 / SEC-07 State machines

### 6.1 Transition tables

**Pick task `pick_task.status`**

| Action | Allowed from | Result |
| --- | --- | --- |
| `start` | `WAITING` | `PICKING` |
| `confirm-item` | `WAITING` / `PICKING` / `EXCEPTION` | unchanged (item updated) |
| `report-shortage` | `WAITING` / `PICKING` / `EXCEPTION` | `EXCEPTION` |
| `finish` | `PICKING` / `EXCEPTION` | `FINISHED` (no shortage) / `EXCEPTION` (shortage exists) |

**Review `review.status`**

| Action | Allowed from | Result |
| --- | --- | --- |
| `submit` | `WAITING` | `PASS` / `DIFF` |

**Handover `handover.status`**

| Action | Allowed from | Result |
| --- | --- | --- |
| `confirm` / reject | `CREATED` | `CONFIRMED` / `REJECTED` |

### 6.2 Two layers of defence

**Layer one: read then judge, with a human-readable error**

```java
private void assertStatus(Map<String, Object> task, String action, String... allowed) {
    String cur = Repo.str(task.get("status"));
    for (String a : allowed) if (a.equals(cur)) return;
    throw new IllegalStateException("Task is now «" + Dict.desc("pickTaskStatus", cur) + "» and cannot " + action);
}
```

**Layer two: conditional update, defeating concurrency**

Even if two requests both pass layer one, only one SQL statement can match:

```java
int n = repo.jdbc().update(
    "UPDATE pick_task SET picker_no=?, picker_name=?, status='PICKING', start_time=? " +
    "WHERE task_no=? AND status='WAITING'", pickerNo, pickerName, now, taskNo);
if (n == 0) throw new IllegalStateException("This task was already started by someone else; refresh and retry");
```

The same applies to review submission:

```java
int n = repo.jdbc().update("UPDATE review SET ... WHERE review_no=? AND status='WAITING'", ...);
if (n == 0) throw new IllegalStateException("This review was already submitted by someone else; refresh to view it");
```

### 6.3 Related issues fixed along the way

- **Diff row write order**: previously `INSERT review_diff` ran before the main table update, so a failed
  update left orphan diff rows. Now diff rows are written only after the conditional main update succeeds.
- **Wave status propagation**: the wave update inside `start` gained `AND status IN ('CREATED','ASSIGNED')` so
  a handed-over wave cannot be dragged back to `PICKING`; `finish` adds `AND status='PICKING'`.
- **Quantity bounds**: actual may not exceed should (overages go through review discrepancies); a shortage
  must fall within `1 ~ outstanding quantity`.
- **Task ownership**: `assertOwnership()` centrally blocks "picker operating someone else's task", and both
  `my-tasks` and `page` force `picker_no = self` for pickers.

---

## 7. Build and run

Environment: JDK 27, Maven 3.10.0.

```bash
cd backend
export JAVA_HOME="/path/to/jdk-27"
MVN="/path/to/maven/bin/mvn"

"$MVN" -o -DskipTests package          # offline build → target/wms-v4-4.1.0.jar
"$JAVA_HOME/bin/java" -jar target/wms-v4-4.1.0.jar --server.port=8080
```

> Stop any running instance before packaging; on Windows the jar stays locked and the build fails with
> `Unable to rename ... wms-v4-4.1.0.jar to ... .jar.original`.

`spring-security-crypto` is the **only added dependency** (about 80 KB, with a single optional transitive
dependency). The first build needs network access; afterwards the `.m2` cache allows `-o` offline builds:

```bash
# First time (online)
"$MVN" dependency:get -Dartifact=org.springframework.security:spring-security-crypto:7.1.1
# Afterwards (offline)
"$MVN" -o -DskipTests package
```

The two key startup log lines:

```
schema.sql executed (idempotent DDL)
N legacy passwords upgraded; ask the affected accounts to confirm password strength   ← first run only
```

The frontend needs no dependency changes — `npm run dev` on 5173, still proxying to 8080.

---

## 8. Retest results

Full evidence is in [`security-audit.md`](security-audit.md) section 6; conclusions only here:

| ID | Retest action | Result |
| --- | --- | --- |
| SEC-01 | picker → `POST /api/order/pool-all`, `POST /api/wave/generate`, `GET /api/log/page`, `GET /api/base/employee/page` | All `403` |
| SEC-01 | admin → `GET /api/base/employee/page` | `200` |
| SEC-02 | Logging in with `MTIzNDU2` (Base64 form) | "Incorrect username or password" |
| SEC-02 | `password` column contents | BCrypt `$2a$10$…` hashes |
| SEC-05 | Re-submitting the PASSed `RV202610160018` | Rejected |
| SEC-06 | Re-confirming a CONFIRMED handover | Rejected |
| SEC-07 | `start` on finished task `PK20261012003` | Rejected ("finished") |
| SEC-07 | `finish` on waiting task `PK20261020001` | Rejected ("waiting") |
| SEC-08 | admin with `pickerNo=P001` starting `PK20261019001` | `picker_no` stays `P002`, operator logged as `A001` |
| SEC-08 | picker(P001) starting P003's task | `403` "this task is assigned to Chen Jing; you may not operate it" |
| SEC-14 | Reporting a shortage of 9999 (1 to pick) | Rejected ("exceeds the unfulfilled quantity") |
| SEC-15 | picker querying `my-tasks?pickerNo=P002` | Only the caller's 2 tasks returned |
| SEC-20 | Checking `operation_log` after pool / cancel / close | `operator_no` always the logged-in employee, no NULLs |

Positive regression: login → start → item confirmation → finish works end to end; `duration_sec` is computed
correctly; the dashboard, pick task and employee management pages render normally.

> SEC-09 / SEC-18 / SEC-22 are later status-code fixes; their retest output is in section 11.3.

---

## 9. Remaining risks

Not addressed in this round (see section 4 of the test report for scheduling):

| ID | Issue | Recommendation |
| --- | --- | --- |
| SEC-03 | No login failure counting or lockout | Count per `username + IP`, lock for 10 minutes after 5 failures; add gateway rate limiting |
| SEC-04 | CORS any Origin with credentials | Switch to an `allowedOrigins` whitelist, or deploy same-origin |
| SEC-10 | Missing security headers | Add a filter injecting CSP / X-Frame-Options / Referrer-Policy |
| SEC-11 | No logout, tokens valid for 8 hours | `TokenService.remove` already exists; add `POST /api/auth/logout` |
| SEC-12 | Token in localStorage | Move to HttpOnly + SameSite cookie (requires SEC-04) |
| SEC-13 | Database password in the config file | Environment variables or a config centre |
| SEC-16 | Order list returns receiver name / phone / address to pickers | Trim fields by role; pickers only need SKU and location |
| SEC-17 | Unbounded page size and request body | Cap `pageSize`, limit body size |
| SEC-19 | Demo accounts hard-coded on the login page | Remove the demo block from production builds |
| SEC-21 | No TLS | Terminate TLS at the gateway plus HSTS |
| SEC-23 / SEC-24 | Remarks allow CRLF; `pageSize=0` yields a negative OFFSET | Strip control characters on write; set `pageSize` minimum to 1 |

One more caveat: **authorization depends on the token session**, so restarting the backend logs everyone out
(the session table is in memory). For multi-instance deployment the session table must move to Redis.

---

## 10. Rollback

Changes are confined to the backend, so rollback is cheap:

```bash
# 1) Revert the code (if it is under git)
git checkout -- backend/src/main/java/com/example/wms \
               backend/src/main/resources/schema.sql \
               frontend/src/router/index.js

# 2) Roll back passwords (only if the old Base64 hashes must be restored)
UPDATE sys_user SET password = 'MTIzNDU2' WHERE username IN ('admin','wave','picker','reviewer');

# 3) Rebuild and restart
```

Note: after step 2 you must also revert `AuthController`, otherwise legacy hashes cannot log in. Also remove
`spring-security-crypto` from `pom.xml` and return `PasswordHasher` to a pure PBKDF2 implementation, otherwise
you leave dead code behind — a dependency nobody calls. No destructive data changes were made in this round:
the status columns of `pick_task` / `review` / `handover` are only constrained by rules, so no data rollback
is needed.

---

## 11. Follow-up fix: HTTP status code semantics (SEC-09 / SEC-18 / SEC-22)

While fixing SEC-18 it became clear that all three share one root cause: the fallback branch of
`GlobalExceptionHandler` turned every exception into **HTTP 200 + code=1**, so "route not found", "wrong
method", "bad parameter type" and "SQL error" all looked like successes to gateways and monitoring.

### 11.1 Changes

`config/GlobalExceptionHandler.java` now dispatches by exception type:

| Exception | Status | Message |
| --- | --- | --- |
| `NoResourceFoundException`, `NoHandlerFoundException` | 404 | Endpoint not found |
| `HttpRequestMethodNotSupportedException` | 405 | Request method not supported: GET |
| `MethodArgumentTypeMismatchException`, missing parameter, body parse failure | 400 | Invalid request parameters |
| Any other unexpected exception | 500 | System error, please retry later or contact the administrator |
| `IllegalArgumentException` / `IllegalStateException` (business) | 200 | Specific message, frontend contract unchanged |

The key decision: **business failures still return 200 + code=1**. The frontend `request.js` raises errors on
`res.code !== 0`; converting business failures to 4xx would mean touching error handling in 15 pages for no
real gain. Only protocol-layer errors were moved to real status codes.

### 11.2 The three findings resolved along the way

- **SEC-09**: the fallback no longer echoes `e.getMessage()`; stack traces stay in the log and SQL fragments
  no longer leak.
- **SEC-18**: `/api/not-exist`, `/actuator` and `/swagger-ui.html` return 404 instead of 200, and no longer
  emit Spring's "No static resource".
- **SEC-22**: parameter type errors return 400 instead of 200.

### 11.3 Retest

```text
normal request   GET /api/dashboard/summary        HTTP 200  code=0
unknown route    GET /api/not-exist                HTTP 404  Endpoint not found
static fallback  GET /actuator                     HTTP 404  Endpoint not found
wrong method     GET /api/auth/login               HTTP 405  Request method not supported: GET
parameter type   GET /api/order/page?pageNum=abc   HTTP 400  Invalid request parameters
missing param    GET /api/log/trace                HTTP 400  Invalid request parameters
server error     GET /api/order/page?pageNum=-1    HTTP 500  System error, please retry later or contact the administrator
business error   POST /api/wave/assign empty body  HTTP 200  code=1 missing wave number or picker
```

Regression: dashboard, shortage and wave pages render normally with no error toasts; 401 / 403 behaviour is
unchanged.
