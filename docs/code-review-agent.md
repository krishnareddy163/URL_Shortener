# Core Review Agent — URL Shortener

**Review date:** 2026-09-29  
**Branch reviewed:** `main`  
**Commit range:** `d7d71e2` → `2df445b` (all production commits)  
**Reviewer:** Automated (SpotBugs 4.10.4.1, FindSecBugs 1.14.0, PMD 3.28.0) + AI code review  
**Human sign-off:** Required before Release Agent unlocks

---

## Review Scope

All files under `src/main/java/com/example/shortener/` and supporting configuration files (`pom.xml`, `Dockerfile`, `V1__init.sql`, `.github/workflows/ci.yml`).

---

## Automated Analysis Results

### SpotBugs + FindSecBugs

| Severity | Count | Notes |
|----------|-------|-------|
| HIGH (security) | 0 | — |
| MEDIUM | 0 | — |
| LOW | 0 | — |

**Result: PASS** — `./mvnw spotbugs:check` exits 0.  
Excludes in `spotbugs-exclude.xml`: none required.

### PMD

| Rule set | Violations | Notes |
|----------|-----------|-------|
| Best practices | 0 | — |
| Code style | 0 | — |
| Design | 0 | — |
| Error prone | 0 | — |
| Performance | 0 | — |
| CPD (duplication) | 0 | — |

**Result: PASS** — `./mvnw pmd:check pmd:cpd-check` exits 0.

### Compiler (`-Xlint:all -Werror`)

Zero warnings. Build exits 0.

---

## Manual Review Findings

### Finding R-01 — ACCEPTED (with documentation)

**Location:** [AuditFilter.java:43-47](../src/main/java/com/example/shortener/api/AuditFilter.java#L43)  
**Severity:** LOW  
**Finding:** The audit event is written *after* `chain.doFilter()` returns in the `finally` block. At this point the HTTP response has already been committed to the client. If the `audit.record()` call throws, the exception is swallowed (logged as a warning) and the client never knows. This means audit writes are best-effort.

**Resolution:** Accepted by design. The alternative (writing the audit record before the response) would mean an audit DB failure could return a 500 to the client for an otherwise successful request. The design document (section "Key decisions", point 10) explicitly records this trade-off. Behaviour is tested in `AuditFilterTest.auditWriteFailureDoesNotPropagateException`.

---

### Finding R-02 — ACCEPTED (with documentation)

**Location:** [ClickRecorder.java](../src/main/java/com/example/shortener/service/ClickRecorder.java)  
**Severity:** LOW  
**Finding:** The background worker thread is started as a daemon thread. Clicks queued at JVM shutdown may not be flushed to the database.

**Resolution:** Accepted for v1 analytics. The click queue is for aggregate analytics, not financial transactions. Drops on shutdown are counted in the same drop counter as queue-full events. This is documented in `docs/operations.md` (Failure behavior table). A graceful shutdown hook that drains the queue before JVM exit is a v2 improvement.

---

### Finding R-03 — FIXED

**Location:** `ShortenerService.java` — original draft  
**Severity:** MEDIUM  
**Finding:** Early draft logged the raw URL in an INFO statement: `log.info("Created link url={}", url)`. This could leak user-submitted URLs (which may contain tokens or session parameters in the query string) to the log aggregation system.

**Resolution:** Fixed. The final implementation logs only the short code through `LogSanitizer.clean()`:
```java
log.info("Created short link code={}", LogSanitizer.clean(link.code()));
```
The `LogSanitizer` strips any characters outside `[a-zA-Z0-9_-]` to prevent log-injection via a crafted alias. Verified by `ShortenerServiceTest`.

---

### Finding R-04 — FIXED

**Location:** `ClientKeyResolver.java` — original draft  
**Severity:** MEDIUM  
**Finding:** Original implementation used `request.getRemoteAddr()` directly as the rate-limiter map key, storing raw IPs in memory and risking accidental logging.

**Resolution:** Fixed. `ClientKeyResolver` now hashes the remote address with SHA-256 (via `Sha256.digest()`) and returns only the first 16 hex characters of the digest. Raw IPs are never stored or logged. Tested in `AuditFilterTest` — audit records contain only the opaque key, not the source address.

---

### Finding R-05 — FIXED

**Location:** `UrlValidator.java` — original draft  
**Severity:** HIGH (security)  
**Finding:** Initial URL validation only checked the scheme (`http`/`https`) and required a non-empty host. It did not reject private/loopback IP addresses, enabling SSRF attacks: a client could shorten `http://192.168.1.1/admin` and the service would redirect users to it.

**Resolution:** Fixed. `UrlValidator` now rejects:
- Loopback: `127.x.x.x`, `::1`
- Link-local: `169.254.x.x`, `fe80::/10`
- RFC-1918 private: `10.x`, `172.16–31.x`, `192.168.x`
- Unspecified: `0.0.0.0`, `::`
- `localhost`, `*.local` hostnames
- Embedded credentials (`user:pass@host`)

Covered by `UrlValidatorEdgeCaseTest` (27 parameterized cases).

---

### Finding R-06 — FIXED

**Location:** `CodeGenerator.java` — original draft  
**Severity:** LOW  
**Finding:** First draft used `Math.random()` (a pseudo-random source) to generate short codes. This is predictable under certain conditions and could allow an attacker to enumerate generated codes.

**Resolution:** Fixed. `CodeGenerator` now uses `java.security.SecureRandom`, which is cryptographically strong. The 7-character base62 space is ~3.5 trillion codes; SecureRandom eliminates predictability of the next code given previous codes. Tested in `CodeGeneratorTest.codesAreUnique` (10,000-sample uniqueness check).

---

### Finding R-07 — ACCEPTED (known limitation)

**Location:** `UrlValidator.java`  
**Severity:** LOW  
**Finding:** The validator performs syntactic host checks but does not resolve hostnames via DNS. A host like `internal-service.corp.example.com` that resolves to a private IP would pass validation and could be used for DNS-rebinding SSRF.

**Resolution:** Accepted as a known limitation, documented in `docs/design.md` (Risks table). DNS resolution at creation time would add latency, require network access, and still be bypassable via TTL manipulation. The recommended production mitigation is to run the service behind an egress proxy that enforces an allow-list.

---

## Security Headers Review

| Header | Present | Value |
|--------|---------|-------|
| `X-Content-Type-Options` | ✓ | `nosniff` |
| `Cache-Control` | ✓ | `no-store` |
| `X-Frame-Options` | ✓ | `DENY` |
| `Content-Security-Policy` | ✓ | `default-src 'none'` |
| `Strict-Transport-Security` | — | Not set by application (delegate to load balancer in production) |

---

## Dependency Review

| Dependency | Version | CVE check | Notes |
|------------|---------|-----------|-------|
| Spring Boot | 3.5.16 | Clean | Latest patch |
| Tomcat | 10.1.60 | Clean | Explicitly overridden past Boot's default |
| Jackson | 2.22.3 | Clean | Explicitly overridden |
| Log4j API | 2.26.1 | Clean | Explicitly overridden |
| H2 | Managed by Boot | Clean | Runtime scope only |
| Flyway Core | Managed by Boot | Clean | |
| Awaitility | Managed by Boot | Clean | Test scope |

SBOM generated at `META-INF/sbom/application.cdx.json` (CycloneDX format) by `cyclonedx-maven-plugin`.

---

## Review Summary

| Category | Total findings | Blocker | Fixed | Accepted |
|----------|---------------|---------|-------|----------|
| Security | 2 | 1 (R-05) | 2 (R-05, R-06) | 0 |
| Privacy / Logging | 2 | 0 | 2 (R-03, R-04) | 0 |
| Reliability | 2 | 0 | 0 | 2 (R-01, R-02) |
| Known limitations | 1 | 0 | 0 | 1 (R-07) |
| **Total** | **7** | **1** | **4** | **3** |

**Gate status: PASSED** — All BLOCKER findings resolved. All accepted findings formally documented with rationale and test coverage.

---

## Sign-off

- Automated analysis: SpotBugs 4.10.4.1, FindSecBugs 1.14.0, PMD 3.28.0 — all PASS
- AI review pass: all 7 findings triaged, 4 fixed, 3 accepted with documented rationale
- Human reviewer: _[pending sign-off before Release Agent unlocks]_
