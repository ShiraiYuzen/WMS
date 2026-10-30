# WMS v4.1.0 — Security Vulnerability Assessment

| Item | Value |
| --- | --- |
| Target | WMS v4.1.0, backend on 8080, frontend on 5173 |
| Build under test | `wms-v4-4.1.0.jar`, database `wms_v4` (16 tables) |
| Test date | 2026-10-20 |
| Method | White-box code audit + black-box API probing + web frontend/transport inspection |
| Tooling | `curl`, MySQL CLI, browser DevTools, `npm audit`, static source search (no scanners used) |
| Test accounts | `admin` / `wave` / `picker` / `reviewer` (seeded demo passwords) |

> Language note: the running system returns Chinese UI labels and business messages. Quoted console output and
> API messages in this report are reproduced in English translation so the document reads consistently; the
> technical facts (status codes, SQL, field values) are unchanged.

> Scope note: round one (14 findings, API and frontend surface) used read-only requests and side-effect-free
> probing. Round two (10 findings, business logic and state machines) required **actual writes** to verify
> duplicate submission and state rollback, so the demo dataset in the test database `wms_v4` was modified:
> two review records and one handover record were submitted twice, two pick task statuses were rewritten,
> and several remark fields received long text and newline characters. All changes stayed inside the local
> test database — **no DROP, DELETE or bulk status updates were executed**. To restore a clean dataset, simply
> re-run `schema.sql` (idempotent DDL plus `INSERT IGNORE`).

---

## 1. Summary of findings

**24 findings**: 1 critical, 7 high, 8 medium, 8 low. Round one covered the API and frontend surface (14),
round two added business logic, state machines, concurrency and the data layer (10).

> **Remediation status (2026-10-20)**: `SEC-01`, `SEC-02`, `SEC-05`, `SEC-06`, `SEC-07`, `SEC-08` are fixed,
> together with `SEC-09` (exception leakage), `SEC-14` (shortage over-reporting), `SEC-15` (task ownership),
> `SEC-18` (404 returned 200), `SEC-20` (audit operator), `SEC-22` (parameter errors returned 200);
> `SEC-16` is partially mitigated. Change details and retest evidence are in
> [`security-fixes.md`](security-fixes.md) and [section 6](#6-fix-verification-2026-10-20).
> The remaining 12 (`SEC-03`, `SEC-04`, `SEC-10`, `SEC-11`, `SEC-12`, `SEC-13`, `SEC-16`, `SEC-17`,
> `SEC-19`, `SEC-21`, `SEC-23`, `SEC-24`) are scheduled per the roadmap in [section 4](#4-remediation-roadmap).

| ID | Vulnerability | Severity | Location | Status |
| --- | --- | --- | --- | --- |
| SEC-01 | No role authorization — any authenticated user can perform every admin action | **Critical** | `AuthInterceptor` / all controllers | ✅ Fixed |
| SEC-02 | Passwords stored as reversible Base64, and the API accepts Base64 passwords | **High** | `sys_user.password` / `AuthController` | ✅ Fixed |
| SEC-03 | No lockout or rate limiting on login — unlimited brute force | **High** | `AuthController#login` | ❌ Open |
| SEC-04 | CORS allows any Origin together with `allowCredentials=true` | **High** | `WebConfig#addCorsMappings` | ❌ Open |
| SEC-05 | Review records can be re-submitted, overwriting a completed result | **High** | `ReviewController#submit` | ✅ Fixed |
| SEC-06 | Handover records can be re-confirmed, overwriting handover data | **High** | `HandoverController#confirm` | ✅ Fixed |
| SEC-07 | Pick tasks allow state rollback and allow finishing without starting | **High** | `PickController#start/#finish` | ✅ Fixed |
| SEC-08 | Acting user taken from request parameters — operations can be impersonated | **High** | `PickController#start` / `ReviewController#submit` | ✅ Fixed |
| SEC-09 | Exceptions echo SQL statements and framework internals (read and write paths) | Medium | `GlobalExceptionHandler` | ✅ Fixed (incidental) |
| SEC-10 | All security response headers missing (CSP, X-Frame-Options, …) | Medium | Neither backend nor frontend sets them | ❌ Open |
| SEC-11 | Tokens cannot be revoked; no logout endpoint; fixed 8-hour lifetime | Medium | `TokenService` | ❌ Open |
| SEC-12 | Token stored in localStorage — any XSS can exfiltrate it | Medium | `frontend/src/stores/app.js` | ❌ Open |
| SEC-13 | Database password committed in plaintext in the repository | Medium | `application.properties` | ⚠️ Partially addressed |
| SEC-14 | No upper bound on shortage quantity — can exceed the quantity to pick | Medium | `PickController#reportShortage` | ✅ Fixed (incidental) |
| SEC-15 | Task ownership not validated — other users' tasks can be read and modified | Medium | `PickController#myTasks/#start` | ✅ Fixed (incidental) |
| SEC-16 | Receiver name / phone / address / amount returned in plaintext to every role | Medium | `OrderController#page` | ⚠️ Partially mitigated |
| SEC-17 | No upper bound on page size or request body size | Low | `JdbcMaps.page` / container config | ❌ Open |
| SEC-18 | Non-existent paths return HTTP 200 with internal error text | Low | `GlobalExceptionHandler` | ✅ Fixed |
| SEC-19 | Login page hard-codes demo accounts and passwords | Low | `views/login/index.vue` | ❌ Open |
| SEC-20 | Some audit log entries have no operator (broken audit trail) | Low | `OrderController` etc., `repo.log(..., null, ...)` | ✅ Fixed (incidental) |
| SEC-21 | Plain HTTP end to end, no TLS | Low (deployment) | Deployment layer | ❌ Open |
| SEC-22 | Parameter type errors return 200 plus framework exception details | Low | Global parameter binding | ✅ Fixed (incidental) |
| SEC-23 | Remark fields persist CRLF verbatim — log lines can be forged | Low | `review.remark` / `pick_item.remark` | ❌ Open |
| SEC-24 | `pageSize=0` makes `pages` overflow to `Long.MAX_VALUE` | Low | `JdbcMaps.page` | ❌ Open |

---

## 2. Findings in detail (24, ordered by severity)

**Test conditions**: no third-party scanners; only the locally available `curl`, MySQL CLI and `npm audit`.
Round one covered the API, authentication and the frontend surface. Round two targeted the class of problem
that tools cannot find but that hurts the most in production: state machines, duplicate submission,
parameter tampering and ownership bypass. The 24 findings are ordered by severity — **the ID is the rank**
(01 is the most severe) — and rounds are not distinguished below.

### SEC-01 Missing role authorization (Critical) ✅ Fixed

**Problem**
The interceptor only checked whether a valid token existed; it never checked whether that role was allowed to
call that endpoint. `AuthInterceptor` put the role into `request.setAttribute("loginRole", ...)`, but **nothing
in the project ever read it**:

```java
// AuthInterceptor.java:43-44
request.setAttribute("loginUser", username);
request.setAttribute("loginRole", tokenService.resolveRole(token)); // set, but never used
```

Meanwhile the frontend hides menus by role (`meta.roles`), so **the permission control visible in the UI was
purely cosmetic**.

**Reproduction**

```bash
# Log in as the least privileged picker
PT=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"picker","password":"<demo-password>"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

# Call admin-only endpoints
curl -H "Authorization: Bearer $PT" http://127.0.0.1:8080/api/base/employee/page
curl -H "Authorization: Bearer $PT" http://127.0.0.1:8080/api/log/page
curl -H "Authorization: Bearer $PT" http://127.0.0.1:8080/api/analytics/overview
```

**Observed result**

```
200 code=0  /api/base/employee/page     ← full employee records (ID / name / team / role)
200 code=0  /api/log/page               ← full audit log
200 code=0  /api/base/sku/page          ← SKUs including cost price
200 code=0  /api/analytics/overview     ← business analytics
```

The picker could equally call writes such as `POST /api/wave/generate`, `POST /api/order/pool-all` or
`POST /api/wave/{waveNo}/cancel`.

**Impact**

- Vertical privilege escalation: a low-privilege role gains every admin capability.
- Data disclosure: employee records, audit log, cost prices and business metrics all readable.
- Business damage: bulk order/wave status changes, recorded under a real account, making accountability hard.

**Recommendation**

1. Enforce authorization centrally in the interceptor (preferred — one place, hard to forget):

```java
// 1) Annotate endpoints
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole { String[] value(); }

// 2) Validate in the interceptor
HandlerMethod hm = (HandlerMethod) handler;
RequireRole rr = hm.getMethodAnnotation(RequireRole.class);
if (rr != null) {
    String role = (String) request.getAttribute("loginRole");
    if (Arrays.stream(rr.value()).noneMatch(r -> r.equals(role))) {
        writeForbidden(response, "Not permitted for this role");   // 403
        return false;
    }
}

// 3) Usage
@RequireRole({"ADMIN", "WAVE_OPERATOR"})
@PostMapping("/generate") ...
```

2. Suggested permission matrix:

| Module | ADMIN | WAVE_OPERATOR | PICKER | REVIEWER |
| --- | --- | --- | --- | --- |
| Order pooling / cancellation | ✓ | ✓ | ✗ | ✗ |
| Wave generation / assignment / close | ✓ | ✓ | ✗ | ✗ |
| Picking execution | ✓ | ✓ | ✓ | ✗ |
| Shortage handling | ✓ | ✓ | ✓ (report only) | ✗ |
| Review / handover | ✓ | ✗ | ✗ | ✓ |
| Master data / log / analytics | ✓ | read-only | ✗ | read-only |

---

### SEC-02 Reversible password storage plus Base64 acceptance (High) ✅ Fixed

**Problem**
`sys_user.password` stored `MTIzNDU2` — the Base64 of `123456`. Base64 is an encoding, not encryption, so
**anyone can recover the plaintext instantly**. Worse, the login endpoint accepted both plaintext and Base64:

```java
// AuthController.java:47-50
String base64 = Base64.getEncoder().encodeToString(input.getBytes(UTF_8));
if (!stored.equals(input) && !stored.equals(base64)) { throw ... }
```

**Reproduction**

```bash
# Plaintext login succeeds
curl -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"123456"}'

# Submitting the stored value directly also succeeds
curl -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"MTIzNDU2"}'
```

**Observed result**

```sql
mysql> select username,password from wms_v4.sys_user;
admin     MTIzNDU2      -- = 123456
wave      MTIzNDU2
picker    MTIzNDU2
reviewer  MTIzNDU2
```

Both login requests returned `code:0` and issued a token.

**Impact**
After any database leak (SQL injection, exposed backup, operational mistake) an attacker **recovers every
plaintext password at once**; because staff reuse passwords, the compromise spreads to email, VPN and other
external systems.

**Recommendation**

```java
// Add spring-security-crypto (this one dependency is enough; no need for the full Security starter)
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

// Login
if (!encoder.matches(req.getPassword(), stored)) { throw new IllegalArgumentException("Incorrect username or password"); }

// Initialisation / password change
String hash = encoder.encode(rawPassword);   // $2a$10$...
```

Also:

- Remove the "accept plaintext and Base64" compatibility branch; keep exactly one verification path.
- Force a password change on first login.
- Password policy: minimum length 8, not equal to the username, no all-numeric passwords.

---

### SEC-03 No login lockout or rate limiting (High)

**Problem**
`/api/auth/login` has no counter, lockout, CAPTCHA or delay.

**Reproduction**

```bash
for i in $(seq 1 8); do
  curl -s -X POST http://127.0.0.1:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"admin\",\"password\":\"wrong$i\"}"
done
```

**Observed result**

```
attempt 1 http=200 msg=Incorrect username or password
...
attempt 8 http=200 msg=Incorrect username or password     ← no lockout, no delay, no CAPTCHA
```

**Impact**
Combined with SEC-02 (6-digit numeric passwords, and the account name is visible on the login page), **an
offline dictionary of under 100k entries succeeds**. Measured login latency was about 4 ms
(`time_total` 0.0035–0.0045 s), so a single thread reaches roughly 900k attempts per hour and concurrency
scales to tens of millions.

**Recommendation**

- Lock an account for 15 minutes after 5 failures, or apply exponential backoff (1s/2s/4s…).
- Rate-limit per IP **and** per account (Bucket4j or a Redis counter).
- Record failures in `operation_log` for alerting.
- Add a CAPTCHA in production.

---

### SEC-04 CORS any Origin with credentials allowed (High)

**Problem**

```java
// WebConfig.java:19-24
registry.addMapping("/api/**")
        .allowedOriginPatterns("*")     // reflects any Origin
        .allowCredentials(true)         // and allows cookies / credentials
```

**Reproduction**

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

**Observed result**

```
Access-Control-Allow-Origin: https://evil.example.com     ← reflected verbatim
Access-Control-Allow-Credentials: true
Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
Access-Control-Allow-Headers: authorization
Access-Control-Max-Age: 3600
```

**Impact**
Any site can call these APIs cross-origin from a victim's browser. Because the token currently lives in an
`Authorization` header (not a cookie), an attacker without the token cannot make authenticated requests, so
**the risk is reduced but not eliminated**:

- The moment the token moves to a cookie (a very common evolution), this becomes a CSRF with read/write
  access.
- Even now it enables cross-origin probing: detecting the system, enumerating endpoints, fingerprinting the
  version.

**Recommendation**

```java
registry.addMapping("/api/**")
        .allowedOrigins("http://127.0.0.1:5173", "https://wms.your-domain.com")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("Content-Type", "Authorization")
        .allowCredentials(true)
        .maxAge(3600);
```

Inject the production whitelist from environment variables — never hard-code it and never use `*`.

---

### SEC-05 Review records can be re-submitted, overwriting a completed result (High) ✅ Fixed

**Problem**
`ReviewController#submit` looked the record up by `reviewNo` only — it **validated neither the current status
nor whether the record had been claimed**.

**Reproduction**

```bash
# Submit an already-PASSed review again, changing the actual quantity to 1
curl -X POST http://127.0.0.1:8080/api/review/submit \
  -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"reviewNo":"RV202610160018","actualQty":1}'
```

**Observed result**

```
before: PASS  2026-10-13 09:24:55  actual_qty=4
after:  PASS  2026-10-20 02:25:35  actual_qty=1     ← result overwritten
```

**Impact**

- A completed quality gate can be rewritten at will; `PASS` now contradicts `actual_qty` (which should have
  been `DIFF`).
- `duration_sec` is recalculated, distorting performance metrics.
- Repeated submissions can manufacture "zero diff" records that mask mis-shipments and shortages.

**Recommendation**
Allow submission only from `WAITING`/`REVIEWING` and validate the claimant:

```java
String status = Repo.str(r.get("status"));
if (!"PENDING".equals(status) && !"REVIEWING".equals(status))
    throw new IllegalStateException("Review is currently «" + Dict.desc("reviewStatus", status) + "» and cannot be submitted");
if (!Objects.equals(reviewerNo, Repo.str(r.get("reviewerNo"))))
    throw new IllegalStateException("This review is not assigned to you");
```

---

### SEC-06 Handover records can be re-confirmed, overwriting data (High) ✅ Fixed

**Reproduction**

```bash
curl -X POST http://127.0.0.1:8080/api/handover/confirm \
  -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"handoverNo":"HO20261012003","receiveName":"Zhang San","actualPackageCount":1}'
```

**Observed result**

```
before: CONFIRMED  2026-10-12 09:41:37  package_count=7
after:  CONFIRMED  2026-10-20 02:25:36  package_count=1  receive_name=Zhang San
```

**Impact**
A completed outbound handover can be rewritten: package count 7 → 1, receiver replaced, handover time
overwritten. In a lost-shipment dispute the paper and system records no longer match the physical goods and
liability becomes undeterminable.

**Recommendation**
Validate `status='PENDING'` (or `CREATED` in the current schema) before `confirm`; a confirmed handover may
only be reversed through a separate, approved reversal operation — never overwritten in place.

---

### SEC-07 Pick tasks allow state rollback and skipping `start` (High) ✅ Fixed

**Problem**
Neither `start` nor `finish` validated the preceding state.

**Reproduction A: restarting a finished task**

```bash
curl -X POST "http://127.0.0.1:8080/api/pick/PK20261010001/start?pickerNo=P001" \
  -H "Authorization: Bearer $T"
```

```
before: FINISHED  start=2026-10-10 09:05:00  finish=2026-10-10 09:21:13
after:  PICKING   start=2026-10-20 02:25:48  finish=2026-10-10 09:21:13   ← rolled back, and finish < start
```

**Reproduction B: finishing a task that never started**

```bash
curl -X POST "http://127.0.0.1:8080/api/pick/PK20261016007/finish" -H "Authorization: Bearer $T"
```

```
before: WAITING  start=NULL
after:  FINISHED start=NULL  finish=2026-10-20 02:25:49  duration=0
```

The wave was simultaneously set to `PICKED`, as were its orders.

**Impact**

- Picking can be "completed" out of thin air, skipping the actual work while downstream review and handover
  continue normally — corrupting the entire chain.
- States can move backwards, zeroing out recorded labour so performance can be gamed.
- Produces dirty data (`finish_time < start_time`, `duration=0`) that invalidates every metric.

**Recommendation**
Drive transitions from an explicit whitelist and forbid skipping:

```java
// start: WAITING only
if (!"WAITING".equals(status)) throw new IllegalStateException("Task cannot be started in its current state");
// finish: PICKING only, and every item must be confirmed
if (!"PICKING".equals(status)) throw new IllegalStateException("Task has not started and cannot be finished");
long pending = jdbc.queryForObject("SELECT COUNT(*) FROM pick_item WHERE task_no=? AND status='PENDING'", Long.class, taskNo);
if (pending > 0) throw new IllegalStateException(pending + " item(s) still unconfirmed");
```

---

### SEC-08 Acting user taken from request parameters (High) ✅ Fixed

**Problem**
`pickerNo` on `start` and `reviewerNo` / `reviewerName` on `review/submit` all came from request parameters;
the server never verified that the caller actually is that employee.

**Reproduction**

```bash
# The caller is admin, yet the task can be booked under P001
curl -X POST "http://127.0.0.1:8080/api/pick/PK20261010001/start?pickerNo=P001" \
  -H "Authorization: Bearer $T"
```

**Observed result**

```
pick_task: PK20261010001  picker_no=P001  picker_name=Zhang Wei  status=PICKING
operation_log: operator_no=P001  operator_name=Zhang Wei        ← log blames someone else
```

**Impact**

- Anyone can frame a colleague; the audit log loses its evidentiary value.
- Combined with SEC-20 (missing operators) the log is either empty or false.

**Recommendation**
Take the employee number from the token context only; ignore request parameters entirely:

```java
String pickerNo = (String) request.getAttribute("loginUser");
```

If "act on behalf of" is genuinely required, build a separate approved delegation mechanism and record both
the operator and the delegate in the log.

---

### SEC-09 Exceptions echo SQL and framework details (Medium) ✅ Fixed (resolved together with SEC-18)

**Problem**

```java
// GlobalExceptionHandler.java:29-33
@ExceptionHandler(Exception.class)
public ApiResult<Void> handleException(Exception e) {
    log.error("System error", e);
    return ApiResult.fail("System error: " + e.getMessage());   // internal detail returned to the caller
}
```

**Reproduction**

```bash
# Negative page number → negative OFFSET → SQL syntax error
curl -H "Authorization: Bearer $T" \
  "http://127.0.0.1:8080/api/order/page?pageNum=-1&pageSize=10"
```

**Observed result**

```json
{
  "code": 1,
  "message": "System error: PreparedStatementCallback; bad SQL grammar [SELECT * FROM `order` WHERE 1=1 ORDER BY CASE priority WHEN 'URGENT' THEN 0 ELSE 1 END, pay_time DESC, id DESC LIMIT 10 OFFSET -20]"
}
```

A single request leaked table names, column names, ordering logic, the pagination implementation and the fact
that JdbcTemplate is used. Malformed JSON additionally returned
`JSON parse error: Unexpected end-of-input ...`, fingerprinting the framework version.

**Impact**
Provides precise schema intelligence for follow-up SQL injection and unauthorized querying, and exposes the
tech stack version for matching known CVEs.

**Recommendation**

- Return a generic message ("Service is busy, please retry") plus an error ID, and log the detail:

```java
String traceId = UUID.randomUUID().toString();
log.error("System error traceId={}", traceId, e);
return ApiResult.fail("Service is busy, please retry (error code " + traceId + ")");
```

- Clamp pagination at the entry point: `pageNum = Math.max(1, pageNum)` and bound `pageSize` to 1–200.

---

### SEC-10 Missing security response headers (Medium)

**Reproduction**

```bash
curl -D - -o /dev/null http://127.0.0.1:8080/api/order/page
curl -D - -o /dev/null http://127.0.0.1:5173/
```

**Observed result**
Neither backend nor frontend sends any of the following:

| Header | Purpose | State |
| --- | --- | --- |
| `X-Content-Type-Options: nosniff` | Prevent MIME sniffing | Missing |
| `X-Frame-Options: DENY` | Prevent clickjacking | Missing |
| `Content-Security-Policy` | Restrict script origins, backstop XSS | Missing |
| `Referrer-Policy` | Prevent Referer leakage | Missing |
| `Strict-Transport-Security` | Enforce HTTPS | Missing |
| `Cache-Control: no-store` | Prevent caching of sensitive responses | Missing |

`index.html` has no CSP meta tag either.

**Impact**

- The app can be framed for clickjacking (tricking an admin into pressing "cancel wave").
- No CSP backstop if an XSS appears.
- Responses may be cached by intermediaries or the browser.

**Recommendation**

Add a filter on the backend:

```java
response.setHeader("X-Content-Type-Options", "nosniff");
response.setHeader("X-Frame-Options", "DENY");
response.setHeader("Referrer-Policy", "no-referrer");
response.setHeader("Content-Security-Policy",
    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'");
```

If the frontend is served by Nginx, set them once in the `server` block with `add_header`.

---

### SEC-11 Tokens cannot be revoked, no logout endpoint (Medium)

**Problem**
`TokenService` keeps tokens in an in-memory map and exposes no `logout`. The frontend's "sign out" only clears
localStorage, so **the server-side token stays valid**.

**Reproduction**

```bash
curl -X POST -H "Authorization: Bearer $T" http://127.0.0.1:8080/api/auth/logout
```

**Observed result**

```json
{"code":1,"message":"System error: No static resource api/auth/logout for request '/api/auth/logout'."}
```

The endpoint does not exist. The token content is also trivially decodable:

```
$ echo "$T" | base64 -d
A001:26b5d2b9-4d18-4d99-b537-a93e93ba37f3:1792462755585:ADMIN
```

**Impact**

- After an employee leaves, a device is lost or a token leaks, the session cannot be terminated for 8 hours.
- Employee number, role and expiry are exposed in plaintext, lowering reconnaissance cost.
- A restart invalidates every token (an availability issue rather than a security one, but worth knowing).

**Recommendation**

- Add `POST /api/auth/logout` calling `tokenService.remove(token)`.
- Have the frontend call it before clearing local state.
- Mid-term, move to JWT with a short lifetime (15 min) plus refresh tokens, or server-side sessions in Redis.
- Keep the role out of the token payload; resolve it server-side from the database or cache.

---

### SEC-12 Token stored in localStorage (Medium)

**Problem**

```js
// frontend/src/stores/app.js:63
localStorage.setItem('wms_token', loginResult.token)
```

**Impact**
localStorage is fully visible to JavaScript, so any XSS (third-party component, rich text, browser extension)
can exfiltrate the token in one line. This project currently has **no** `v-html`, `eval` or `innerHTML` and
Vue escapes by default, so the XSS surface is small — but that means "not exploited yet", not "impossible".

**Recommendation**

- In production, carry the session in an `HttpOnly + Secure + SameSite=Lax` cookie (together with the CORS
  tightening from SEC-04).
- If the header approach is kept, at least shorten token lifetimes, add refresh, and enable CSP (SEC-10).

---

### SEC-13 Database password committed in plaintext (Medium) ⚠️ Partially addressed

**Problem**

```properties
# backend/src/main/resources/application.properties:11
spring.datasource.password=123456
```

**Impact**
As soon as the code or an archive leaves the machine (shared with a colleague, pushed to Git, shipped to a
customer) the database is compromised; combined with SEC-01 the attack surface grows further.

**Recommendation**

- Use an environment variable: `spring.datasource.password=${DB_PASSWORD}`.
- Inject it locally via `.env` or an IDE run configuration, excluded by `.gitignore`.
- Use a secret manager in production (Vault / KMS / cloud secret store).
- Split database accounts by least privilege: the application account gets DML, never DDL.

> Current state: the property now reads `${WMS_DB_PASS:}` so no credential is committed, but the local default
> was intentionally left empty rather than populated with a demo password. Rotate any password that was ever
> committed — it remains in the Git history.

---

### SEC-14 No upper bound on shortage quantity (Medium) ✅ Fixed (incidental)

**Reproduction**

```bash
# 3 items to pick, report a shortage of 9999
curl -X POST http://127.0.0.1:8080/api/pick/report-shortage \
  -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"itemId":447,"shortageQty":9999,"reason":"STOCK_ZERO"}'
```

**Observed result**

```
before: should_qty=3  actual_qty=0  shortage_qty=0
after:  should_qty=3  actual_qty=0  shortage_qty=9999  status=SHORTAGE
```

**Impact**
The shortage can far exceed the quantity to pick, corrupting stock variance, shortage rate and replenishment
suggestions; it also masks internal theft (take the goods, report "warehouse is out of stock").

**Recommendation**
Enforce `0 <= shortageQty <= shouldQty` and `actual + shortage == should`; reject anything outside.

---

### SEC-15 Task ownership not validated (Medium) ✅ Fixed (incidental)

**Reproduction**

```bash
# picker account querying P002's tasks
curl -H "Authorization: Bearer $PT" "http://127.0.0.1:8080/api/pick/my-tasks?pickerNo=P002"
```

**Observed result**

```
returned tasks = 2   owners = {'P002'}
```

**Impact**
Pickers can read each other's tasks and pick paths; combined with SEC-01 they can modify them.

**Recommendation**
`my-tasks` ignores the parameter and uses the logged-in employee number; every other read/write validates
`picker_no = current user` (admins excepted).

---

### SEC-16 Receiver details returned in plaintext to every role (Medium) ⚠️ Partially mitigated

**Reproduction**

```bash
# Query orders as the picker account
curl -H "Authorization: Bearer $PT" "http://127.0.0.1:8080/api/order/page?pageSize=1"
```

**Observed result**

```
receiver: Liu Jie | phone: 132****6840 | address: *** (masked here; the API returned it in full) | amount: 1023.38
```

**Impact**
A picker only needs location, SKU and quantity — not the receiver name, phone number, full address and order
amount. This is personal data served in plaintext to every role; if an account is shared or a token leaks, the
customer records leak with it.

**Recommendation**

- Trim response fields by role (the picking view drops receiver / phone / address / amount).
- Mask when display is required: `132****6840`, `***`.
- Apply the same masking to exports and logs.

---

### SEC-17 No upper bound on pagination or request body (Low)

**Observed**

```bash
pageSize=10      → 15ms  10KB
pageSize=10000   → 52ms  68KB
pageSize=500000  → 39ms  68KB (only 60 rows exist today, so the effect is not yet visible)

# 5MB request body
POST /api/order/pool  5MB JSON  → http=200  73ms (fully read and parsed)
```

**Impact**
With the current small dataset nothing breaks; at a hundred thousand rows, `pageSize=1000000` reads and
serialises the whole table into memory, a cheap denial of service. Oversized bodies consume memory and
bandwidth the same way.

**Recommendation**

- Clamp `pageSize` to 1–200 and `pageNum` to a minimum of 1.
- Limit body size via `server.tomcat.max-http-form-post-size` / `max-swallow-size` (e.g. 1MB).
- Add another rate limit at the gateway.

---

### SEC-18 Non-existent paths return HTTP 200 (Low) ✅ Fixed

**Observed**

```bash
curl -H "Authorization: Bearer $T" http://127.0.0.1:8080/api/not-exist
```

```json
{"code":1,"message":"System error: No static resource api/not-exist for request '/api/not-exist'."}
```

The status code was **200**, not 404. `/actuator` and `/swagger-ui.html` behaved the same way.

**Impact**

- Monitoring and gateways cannot detect anomalies by status code.
- Attackers can infer which routes exist from message differences.
- Spring's static resource handling internals are exposed.

**Recommendation**

- Return 404 for 404, 403 for 403, 401 for 401.
- Route unmatched requests through an `ErrorController` with a fixed message.

**After the fix (2026-10-20)**

`GlobalExceptionHandler` gained branches that separate protocol errors from business errors:

| Exception | Status | Body |
| --- | --- | --- |
| `NoResourceFoundException` / `NoHandlerFoundException` | 404 | `{"code":1,"message":"Endpoint not found"}` |
| `HttpRequestMethodNotSupportedException` | 405 | `"Request method not supported: GET"` |
| `MethodArgumentTypeMismatch` / missing parameter / JSON parse failure | 400 | `"Invalid request parameters"` |
| Any other unexpected exception | 500 | `"System error, please retry later or contact the administrator"` (stack trace logged only) |
| Business exceptions (`IllegalArgumentException` / `IllegalStateException`) | **200** | `code=1` plus a specific message (frontend contract unchanged) |

Static fallback paths such as `/actuator` and `/swagger-ui.html` now return 404 as well instead of
"No static resource api/xxx".

---

### SEC-19 Login page hard-codes demo accounts (Low)

**Problem**

```vue
<div class="demo-title">Demo accounts (click to fill, password is 123456)</div>
const form = reactive({ username: 'admin', password: '123456' })
```

**Impact**
Anyone who opens the login page sees the administrator account and password; on an intranet that is later
pivoted into, that hands over the highest privilege directly.

**Recommendation**

- Render demo accounts only under `import.meta.env.DEV`.
- Remove them from production builds, or use one-time demo accounts that expire.

---

### SEC-20 Audit log entries missing the operator (Low) ✅ Fixed (resolved together with SEC-08)

**Observed**

```sql
select ifnull(operator_no,'(null)'), ifnull(operator_name,'(null)'), count(*)
from wms_v4.operation_log group by 1,2;

(null)  (null)      1
(null)  current user 1
W001    Wang Min     4
...
```

**Problem**
Some write paths call `repo.log(..., null, null, null, ...)`, leaving the operator empty; a few hard-code
"current user".

**Impact**
When a dispute or data anomaly occurs, no responsible party can be identified — the audit chain is broken.

**Recommendation**

- Resolve `repo.log`'s operator from `request.getAttribute("loginUser"/"loginRole")`; forbid business code
  from passing null.
- Inject it automatically in the service layer via `ThreadLocal` or `RequestContextHolder`.

---

### SEC-21 Plain HTTP end to end (Low, deployment)

**Current state**
Backend on 8080 and frontend on 5173 both use HTTP; passwords and tokens travel in plaintext.

**Impact**
Anyone on the same LAN can recover the password and token via ARP spoofing or packet capture.

**Recommendation**

- Terminate TLS at Nginx or the gateway, enforce HTTPS plus HSTS.
- Enable it on the intranet as well, at least for the login endpoint.

---

### SEC-22 Parameter type errors return 200 with framework details (Low) ✅ Fixed (incidental)

```bash
curl -H "Authorization: Bearer $T" "http://127.0.0.1:8080/api/order/page?pageSize=abc"
```

```json
{"code":1,"message":"System error: Method parameter 'pageSize': Failed to convert value of type 'java.lang.String' to required type 'int'..."}
```

The status was still 200, and the method signature and parameter name were exposed.

**Recommendation**: an `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` returning 400 with a
fixed message.

---

### SEC-23 Remark fields persist CRLF verbatim (Low)

```bash
curl -X POST .../api/review/submit -d '{"reviewNo":"RV202610160016","remark":"normal remark\r\n[INFO] forged log line"}'
```

The database stored `E6ADA3...0D0A5B494E464F5D...` — `\r\n` preserved as-is.

**Impact**
If these fields are later concatenated into log files or exported to CSV, log lines can be forged or Excel
formula injection triggered.

**Recommendation**
Strip `\r\n` and control characters before persisting; escape on log output; escape leading `=`, `+`, `-`,
`@` on CSV export.

---

### SEC-24 `pageSize=0` overflows `pages` (Low)

```bash
curl -H "Authorization: Bearer $T" "http://127.0.0.1:8080/api/order/page?pageSize=0"
```

```json
{"code":0,"data":{"records":[],"total":60,"pageNum":1,"pageSize":0,"pages":9223372036854775807}}
```

`pages = ceil(total / 0)` handled the integer division edge case incorrectly and returned `Long.MAX_VALUE`.

**Recommendation**: bound `pageSize` to a minimum of 1 (10 recommended) and a maximum of 200.

---

## 3. Verified as not exploitable (positive findings)

The following classes were specifically tested and **no exploitable issue was found**; treat them as a
baseline to preserve:

| Check | Result | Basis |
| --- | --- | --- |
| SQL injection | **Not found** | All dynamic conditions use `?` placeholders; `orderNo=' OR '1'='1`, `status=' OR 1=1 --` and date injection all returned 0 rows without altering the result set |
| Token forgery / tampering | **Not feasible** | Tokens are validated against a server-side `ConcurrentHashMap` whitelist; a hand-built `A001:...:ADMIN` base64 token returns 401 |
| Unauthenticated access | **Blocked** | Any `/api/**` request without a token returns 401 |
| XSS | **Low risk** | No `v-html`, `eval`, `innerHTML` or `document.write` anywhere; Vue templates escape by default |
| Username enumeration | **Not found** | Unknown user and wrong password return the same "Incorrect username or password" |
| Actuator / Swagger exposure | **Not exposed** | `/actuator`, `/v3/api-docs`, `/swagger-ui.html` have no backing component (v4 omits the dependencies) |
| TRACE method | **Disabled** | Returns 405 |
| Sensitive file exposure | **Mostly fine** | Vite returns 403 for `/.env.development`; `/package.json` is readable in dev only |

---

## 4. Remediation roadmap

Ordering criteria: **can an ordinary business account exploit it directly, does it corrupt core business
data, and what does the fix cost**. Findings SEC-05–08 from round two share a root cause with SEC-01 (the
server does not validate caller identity or document state) and should be delivered as one change.

### 4.1 Phased plan

| Phase | IDs | Work | Effort |
| --- | --- | --- | --- |
| **P0 (this week)** | SEC-01 | Role authorization (`@RequireRole` annotation + interceptor + matrix) — **done** | 0.5 d |
| **P0 (this week)** | SEC-02 | BCrypt salted hashing, drop the Base64 compatibility branch — **done** | 0.5 d |
| **P0 (this week)** | SEC-08 | Operator always from the token context; reject `operator`/`pickerNo` in the body — **done** | 0.5 d |
| **P0 (this week)** | SEC-05 / SEC-06 / SEC-07 | Document state machines enforced before writes: one-way only, terminal states reject writes — **done** | 1 d |
| **P1 (this week)** | SEC-03 / SEC-04 | Login failure counting and lockout; CORS whitelist | 0.5 d |
| **P1 (this week)** | SEC-15 / SEC-16 | Task ownership (`pickerNo = current user`); trim receiver fields by role | 0.5 d |
| **P1 (next week)** | SEC-09 / SEC-10 / SEC-11 | Exception sanitisation (**done**); security headers; logout + active token invalidation | 1 d |
| **P2** | SEC-12 / SEC-13 | Cookie-based sessions (HttpOnly + SameSite); externalised configuration | 1 d |
| **P2** | SEC-14 | Shortage bound (`0 < shortage ≤ shouldQty − actualQty`) | 0.2 d |
| **P3** | SEC-17 / SEC-18 | Pagination and body limits; correct status codes (**SEC-18 done**) | 0.5 d |
| **P3** | SEC-19 / SEC-20 | Remove demo accounts from the UI (document them instead); complete audit operators | 0.3 d |
| **P3** | SEC-22 / SEC-23 / SEC-24 | Parameter type validation (**SEC-22 done**); strip control characters from remarks; `pageSize` minimum 1 | 0.3 d |
| **Deployment** | SEC-21 | TLS + HSTS + gateway rate limiting | Environment-dependent |

### 4.2 Implementation notes for the three key changes

**① Role authorization (SEC-01 together with SEC-08)**

```java
// 1) Annotation
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole { String[] value(); }

// 2) The interceptor reads the role that AuthInterceptor already stored
RequireRole rr = handlerMethod.getMethodAnnotation(RequireRole.class);
if (rr != null) {
    String role = (String) request.getAttribute("loginRole");
    if (Arrays.stream(rr.value()).noneMatch(r -> r.equalsIgnoreCase(role))) {
        response.setStatus(403);
        // unified body { code: 403, message: "Forbidden" }
        return false;
    }
}

// 3) Controllers no longer take the operator from parameters
String operator = (String) request.getAttribute("loginEmployeeNo");
```

Suggested permission matrix:

| Endpoint family | ADMIN | WAVE | PICKER | REVIEWER |
| --- | :---: | :--: | :----: | :------: |
| `/api/base/**` (employee / SKU / location / dictionary) | ✔ | — | — | — |
| `/api/order/**` read and write | ✔ | ✔ | — | — |
| `/api/wave/**` generate / assign | ✔ | ✔ | — | — |
| `/api/pick/**` execute | ✔ | — | ✔ | — |
| `/api/review/**` review | ✔ | — | — | ✔ |
| `/api/stats/**`, `/api/log/**` | ✔ | — | — | — |

**② Document state machines (SEC-05 / SEC-06 / SEC-07)**

Put the same guard in front of all three write paths, forbidding "write after terminal" and "skipping steps":

```java
private void assertStatus(String current, String... allowed) {
    if (Arrays.stream(allowed).noneMatch(current::equals)) {
        throw new BizException("Operation not allowed in the current document state: " + current);
    }
}
// review:   assertStatus(status, "PENDING");
// handover: assertStatus(status, "PENDING");   // reject CONFIRMED outright
// pick start:  assertStatus(status, "WAITING");
// pick finish: assertStatus(status, "PICKING");  // never WAITING → FINISHED
```

Back it up at the database level to defeat concurrency:

```sql
UPDATE review SET status='PASS' WHERE review_no=? AND status='PENDING';
-- affected rows = 0 means it was already handled → return "this review has already been processed"
```

**③ Terminal states are final (the second half of SEC-07)**

Currently `start` moves `FINISHED` back to `PICKING` and clears `finish_time`, which destroys the audit trail.
Beyond the status guard, add a `version` column to `pick_task` for optimistic locking and record
`before_status → after_status` in `operation_log`.

---

## 5. Appendix: retest command reference

All commands run locally; substitute the current demo password where `123456` appears in the original notes.
Commands 1–9 belong to round one, 10–22 to round two.

### 5.1 Round one: API and transport layer

```bash
B=http://127.0.0.1:8080
T=$(curl -s -X POST $B/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"123456"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

# 1 Unauthenticated
curl -i $B/api/order/page

# 2 Forged token
FAKE=$(printf 'A001:x:9999999999999:ADMIN' | base64 | tr '+/' '-_' | tr -d '=')
curl -i -H "Authorization: Bearer $FAKE" $B/api/order/page

# 3 Privilege escalation (picker calling an admin endpoint)
PT=$(curl -s -X POST $B/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"picker","password":"123456"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -H "Authorization: Bearer $PT" $B/api/base/employee/page

# 4 SQL injection
curl -G -H "Authorization: Bearer $T" --data-urlencode "orderNo=' OR '1'='1" $B/api/order/page

# 5 Error message leakage
curl -H "Authorization: Bearer $T" "$B/api/order/page?pageNum=-1&pageSize=10"

# 6 CORS
curl -D - -o /dev/null -H "Origin: https://evil.example.com" -H "Authorization: Bearer $T" $B/api/order/page

# 7 Security headers
curl -D - -o /dev/null $B/api/order/page | grep -iE "x-content|x-frame|content-security|referrer"

# 8 Brute force
for i in $(seq 1 10); do curl -s -o /dev/null -w "%{http_code} " -X POST $B/api/auth/login \
  -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"w$i\"}"; done; echo

# 9 Logout (did not exist at the time)
curl -X POST -H "Authorization: Bearer $T" $B/api/auth/logout
```

### 5.2 Round two: business logic and state machines

```bash
B=http://127.0.0.1:8080
login() { curl -s -X POST $B/api/auth/login -H "Content-Type: application/json" \
  -d "{\"username\":\"$1\",\"password\":\"123456\"}" | sed -E 's/.*"token":"([^"]+)".*/\1/'; }
T=$(login admin); PT=$(login picker); RT=$(login reviewer)

# 10 Duplicate review submission (SEC-05): submit the same record twice
curl -X POST -H "Authorization: Bearer $RT" -H "Content-Type: application/json" \
  -d '{"reviewNo":"RV202610160018","result":"PASS","actualQty":4}' $B/api/review/submit
curl -X POST -H "Authorization: Bearer $RT" -H "Content-Type: application/json" \
  -d '{"reviewNo":"RV202610160018","result":"PASS","actualQty":1}' $B/api/review/submit
curl -G -H "Authorization: Bearer $RT" --data-urlencode "reviewNo=RV202610160018" $B/api/review/page

# 11 Duplicate handover confirmation (SEC-06)
curl -X POST -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"handoverNo":"HO20261012003","packageCount":1,"receiveName":"Zhang San"}' $B/api/handover/confirm

# 12 State rollback (SEC-07): start an already FINISHED task
curl -X POST -H "Authorization: Bearer $PT" -H "Content-Type: application/json" \
  -d '{"taskNo":"PK20261010001"}' $B/api/pick/start

# 13 Skipped step (SEC-07): finish a WAITING task
curl -X POST -H "Authorization: Bearer $PT" -H "Content-Type: application/json" \
  -d '{"taskNo":"PK20261016007"}' $B/api/pick/finish

# 14 Impersonation (SEC-08): admin token with someone else's operator
curl -X POST -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"taskNo":"PK20261010001","operator":"P001"}' $B/api/pick/start

# 15 Over-reporting a shortage (SEC-14): 3 to pick, report 9999
curl -X POST -H "Authorization: Bearer $PT" -H "Content-Type: application/json" \
  -d '{"itemId":447,"shortageQty":9999}' $B/api/pick/report-shortage

# 16 Reading someone else's tasks (SEC-15)
curl -G -H "Authorization: Bearer $PT" --data-urlencode "pickerNo=P002" $B/api/pick/my-tasks

# 17 Receiver data exposure (SEC-16): picker queries the order list
curl -G -H "Authorization: Bearer $PT" --data-urlencode "pageSize=1" $B/api/order/page

# 18 Parameter type confusion (SEC-22)
curl -H "Authorization: Bearer $T" "$B/api/order/page?pageSize=abc"

# 19 CRLF injection (SEC-23): newline in a remark, then inspect the hex
curl -X POST -H "Authorization: Bearer $RT" -H "Content-Type: application/json" \
  -d '{"reviewNo":"RV202610160018","result":"PASS","remark":"normal\r\n2026-10-20 00:00:00 [ADMIN] deleted all data"}' \
  $B/api/review/submit

# 20 Pagination division by zero (SEC-24)
curl -H "Authorization: Bearer $T" "$B/api/order/page?pageNum=1&pageSize=0"
```

### 5.3 Concurrency retest

```bash
# Reuse $B from 5.1 and $T from 5.2
# 21 Concurrent wave generation (checks for duplicate wave assignment)
for i in 1 2; do curl -s -X POST -H "Authorization: Bearer $T" \
  -H "Content-Type: application/json" -d '{}' $B/api/wave/generate & done; wait; echo

# 22 Concurrent logins (checks for rate limiting, 30 in parallel)
seq 1 30 | xargs -P 30 -I{} curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST $B/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}' | sort | uniq -c
```

> Acceptance criteria: the second SEC-05 / SEC-06 request must fail as a business error; SEC-07 `start` must
> report an invalid state; for SEC-08 the stored `operator` must always equal the employee number in the
> token; SEC-14 must reject values above the pickable quantity; SEC-15 queried with `pickerNo=P002` must
> return empty or 403; SEC-22 must return 400 instead of 200; SEC-24 `pages` must be sane; the 22 concurrent
> requests should surface 429s.

---

## 6. Fix verification (2026-10-20)

Six P0 findings were fixed — SEC-01, SEC-02, SEC-05, SEC-06, SEC-07, SEC-08 — plus the incidental
SEC-09, SEC-18, SEC-20, SEC-14, SEC-15 and SEC-22, twelve in total. Code changes are documented in
[`security-fixes.md`](security-fixes.md); this section contains **retest evidence only**.

Retest environment: `wms-v4-4.1.0.jar` restarted on 8080 against `wms_v4`, with all four demo accounts
migrated to BCrypt (`$2a$10$…`).

### 6.1 Item-by-item results

| ID | Before | After (measured) | Verdict |
| --- | --- | --- | --- |
| SEC-01 | picker calling `GET /api/base/employee/page` returned all employees | `HTTP 403` (`code=403`, "the current role (picker) may not perform this operation") | ✅ |
| SEC-01 | picker calling `POST /api/wave/generate` and `/api/order/pool-all` succeeded | Both `HTTP 403` | ✅ |
| SEC-02 | `password` column held `MTIzNDU2` (reversible Base64) | Column holds `$2a$10$…` (BCrypt, 60 chars); Base64 login returns "Incorrect username or password" | ✅ |
| SEC-05 | Re-submitting a PASSed review succeeded and rewrote `actual_qty` | Returns "this review is already «passed» and cannot be resubmitted" | ✅ |
| SEC-06 | Re-confirming a CONFIRMED handover rewrote `package_count` | Returns "this handover is already «handed over» and cannot be re-confirmed" | ✅ |
| SEC-07 | A `FINISHED` task could be pulled back to `PICKING` | Returns "the task is now «finished» and cannot be started" | ✅ |
| SEC-07 | A `WAITING` task could be finished directly (duration=0) | Returns "the task is now «waiting» and cannot be finished" | ✅ |
| SEC-08 | Admin token with `pickerNo=P001` booked the operation under P001 | Stored `picker_no` stays `P002`; `operation_log.operator_no=A001` | ✅ |
| SEC-08 | picker could start someone else's task (took over P003's) | `HTTP 403` "this task is assigned to Chen Jing; you may not operate it" | ✅ |
| SEC-15 | picker `my-tasks?pickerNo=P002` returned P002's 2 tasks | Only the caller's 2 tasks; `pickerNo` set is always `{P001}` | ✅ |
| SEC-14 | A shortage of 9999 was accepted | Returns "shortage 9999 exceeds the unfulfilled quantity 0" | ✅ |
| SEC-20 | Pooling / cancel / archive logs had `operator_no` NULL | All 13 `repo.log` call sites carry the logged-in employee number | ✅ |
| SEC-18 | `GET /api/not-exist`, `/actuator`, `/swagger-ui.html` all returned 200 | All three return `HTTP 404` with `{"code":1,"message":"Endpoint not found"}` | ✅ |
| SEC-18 | Sending GET to `/api/auth/login` | `HTTP 405` "Request method not supported: GET" | ✅ |
| SEC-09 | `pageNum=-1` triggered a SQL error | `HTTP 500` with a fixed message; stack trace only in logs | ✅ |
| SEC-22 | `pageNum=abc` (type error), `/api/log/trace` missing `bizNo` | Both `HTTP 400` "invalid request parameters" | ✅ |

### 6.2 Key retest output (verbatim excerpts)

```text
== SEC-01 write endpoints authorize by role
  picker   -> POST /api/order/pool-all          : HTTP 403
  picker   -> POST /api/wave/generate           : HTTP 403
  picker   -> GET  /api/log/page                : HTTP 403
  picker   -> GET  /api/base/employee/page      : HTTP 403
  reviewer -> POST /api/wave/assign             : HTTP 403
  admin    -> GET  /api/base/employee/page      : HTTP 200

== SEC-02 legacy password format rejected
  Base64 form (MTIzNDU2) : Incorrect username or password
  wrong password         : Incorrect username or password

== SEC-07 state machine
  start finished task PK20261012003 : task is «finished» and cannot be started
  finish unstarted task PK20261020001: task is «waiting» and cannot be finished

== SEC-08 impersonation and ownership
  admin with pickerNo=P001 starting PK20261019001 → status=PICKING pickerNo=P002 pickerName=Liu Yang
  picker(P001) starting PK20261020001              → HTTP 403 this task is assigned to Chen Jing
  picker querying my-tasks?pickerNo=P002           → 2 tasks, pickerNo set = {P001}

== SEC-05 / SEC-06
  duplicate review submission : this review is already «passed»; use the discrepancy flow to correct it
  handover first attempt      : ok
  handover second attempt     : this handover is already «handed over» and cannot be re-confirmed

== Audit trail (/api/log/trace?bizNo=HO20261020001)
  HANDOVER_CREATE  R001  Wu Qian
  HANDOVER_CONFIRM R001  Wu Qian
```

### 6.2.1 Status code overview (SEC-18 retest, 2026-10-20)

```text
normal request   GET /api/dashboard/summary        HTTP 200  code=0
unknown route    GET /api/not-exist                HTTP 404  {"code":1,"message":"Endpoint not found"}
static fallback  GET /actuator                     HTTP 404  same (no Spring internals leaked)
wrong method     GET /api/auth/login               HTTP 405  "Request method not supported: GET"
parameter type   GET /api/order/page?pageNum=abc   HTTP 400  "Invalid request parameters"
missing param    GET /api/log/trace                HTTP 400  same
server error     GET /api/order/page?pageNum=-1    HTTP 500  "System error, please retry later…" (stack trace logged only)
business error   POST /api/wave/assign empty body  HTTP 200  code=1 "missing wave number or picker" (business failures keep 200)
```

### 6.3 Regression check (confirming normal flows still work)

| Scenario | Result |
| --- | --- |
| All four accounts log in | All `code=0`, tokens issued |
| Picker claims own task `WAITING → PICKING` | Success, `picker_no=P001` |
| Confirming pick items (actual = should) | Success, `finished_count` increments |
| Finishing `PICKING → FINISHED` | Success, `duration_sec` computed |
| Finishing the same task twice | Rejected ("already finished") |
| Reviewer calling `POST /api/pick/{taskNo}/start` | `HTTP 403` |
| Read endpoints available to pickers (`/pick/page`, `/base/employee/by-role`, `/base/sku/page`, `/dashboard/summary`, `/log/trace`) | All `HTTP 200`; `pick/page` returns only the caller's tasks |
| Frontend, 16 pages (dashboard / pick tasks / employee management …) | Render normally, no 403 errors |
| Actual quantity > should, shortage > outstanding | Rejected with readable messages |

### 6.4 Risks that remain after this round

- **SEC-03**: no login failure counter or lockout; a `for` loop still brute-forces (password strength is now
  the only defence).
- **SEC-04**: CORS is still `allowedOriginPatterns("*") + allowCredentials(true)`; any site can make
  credentialed cross-origin calls.
- **SEC-10 / SEC-11 / SEC-12 / SEC-13 / SEC-19 / SEC-21**: security headers, logout endpoint, cookie-based
  sessions, externalised config, demo credentials on the login page and TLS are all untouched.
- **SEC-16**: `/api/base/employee/page` is now ADMIN-only, but the order list still returns receiver name,
  phone and address in plaintext to every role.
- **SEC-17**: `pageSize` and request bodies remain unbounded; `pageSize=500000` and 5MB bodies are still read
  in full.
- **SEC-23 / SEC-24**: remarks still allow CRLF; `pageSize=0` still produces a negative OFFSET (it now at
  least returns 500 instead of 200).

> Note: role authorization covers **write operations** and **sensitive read endpoints** only. Read-only
> endpoints for SKU / location / carrier / dashboard / analytics stay open to any authenticated user, to match
> the frontend menus (the picking page needs SKUs, the review page needs carriers). Tightening them requires
> updating the frontend menu visibility in the same change.

---

*End of report. SEC-01 / SEC-02 / SEC-05 / SEC-06 / SEC-07 / SEC-08 were fixed and retested on 2026-10-20;
remaining items follow the section 4 roadmap. Automated regression tests are recommended for
SEC-01 / SEC-02 / SEC-03 / SEC-04 / SEC-05 / SEC-06 / SEC-07 / SEC-08.*
