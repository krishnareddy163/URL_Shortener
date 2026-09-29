# Final Engineering Summary — URL Shortener

**Author:** Krishna Reddy  
**Date:** 2026-09-29  
**Version:** 1.0.0  
**Repository:** `main` @ `2df445b`

---

## 1. What Was Built

A production-grade URL shortener service implemented in Java 25 / Spring Boot 3.5. The service provides link creation, redirect, analytics, rate limiting, audit trail, and security hardening — built end-to-end using an agentic SDLC model with six coordinated agents spanning requirements through release.

### Delivered Artifacts

| Artifact | Location | Description |
|----------|----------|-------------|
| Service application | `src/main/java/` | Spring Boot REST service |
| Database schema | `src/main/resources/db/migration/V1__init.sql` | Flyway-managed schema |
| OpenAPI spec | `openapi.yaml` | Full API definition |
| Dockerfile | `Dockerfile` | Multi-stage, non-root runtime |
| CI pipeline | `.github/workflows/ci.yml` | Build, test, static analysis, Docker |
| Dependabot config | `.github/dependabot.yml` | Maven + Actions dependency updates |
| Requirements Agent output | `docs/requirements-agent.md` | User stories, acceptance criteria, ambiguities |
| Design doc + diagrams | `docs/design.md` | Components, data model, decisions, risks |
| Orchestration design | `docs/orchestration.md` | Agent DAG, gates, governance model |
| Three SDLC scenarios | `docs/scenarios.md` | Greenfield, brownfield, ambiguous walkthroughs |
| Code Review Agent output | `docs/code-review-agent.md` | 7 findings, 4 fixed, 3 accepted |
| QA Agent output | `docs/qa-agent.md` | 135 tests, coverage report, functional traceability |
| Operations runbook | `docs/operations.md` | Config, observability, failure behavior |
| Release notes | `docs/release-notes.md` | v1.0.0 changelog |

---

## 2. Architecture Summary

```
 Client
   │
   ├─ SecurityHeadersFilter  (CSP, HSTS, X-Frame-Options, no-store)
   ├─ AuditFilter            (records all mutations to audit_event table)
   │
   └─ LinkController
         ├─ RateLimiter      (token bucket, opaque client key, 20 req/min)
         └─ ShortenerService
               ├─ UrlValidator     (scheme, SSRF, host, credential checks)
               ├─ AliasValidator   (format, length)
               ├─ CodeGenerator    (SecureRandom, 7-char base62)
               ├─ ClickRecorder    (async, bounded queue, single worker)
               └─ LinkRepository   (JDBC, Flyway-managed H2/Postgres)
```

**Key architectural decisions:**
- Idempotency enforced by database (`url_hash` unique constraint) — no application-level locking
- Click recording is non-blocking: clicks go into an in-memory queue; the redirect response does not wait for DB writes
- Rate limiting is per-instance (acceptable for v1); distributed rate limiting (Redis) is a v2 improvement
- All error responses use a stable `{"error":{"code":"UPPER_SNAKE","message":"..."}}` envelope
- Raw IPs, URLs, and addresses are never logged; all log tokens pass through `LogSanitizer`

---

## 3. Agentic SDLC Orchestration

The project was built using six specialist agents coordinated by an explicit dependency DAG with entry/exit gates, human approval checkpoints, bounded retries, and rollback triggers. Full design in `docs/orchestration.md`.

**Orchestration highlights:**
- Requirements Agent → Design Agent run sequentially (design depends on approved requirements)
- Development Agent and QA test-design phase run in parallel
- Review Agent is a synchronisation point — both development and test-design must complete first
- QA execution phase follows review sign-off
- Release Agent requires a human approval checkpoint (coverage report + release notes review)
- Any agent failure beyond 3 retries emits a `HALT` signal — pipeline pauses for human resolution

**Three scenarios documented** (`docs/scenarios.md`):
1. **Greenfield** — building the core shorten/redirect API from nothing
2. **Brownfield** — adding rate limiting to a running service (impact analysis, regression testing)
3. **Ambiguous** — interpreting "make it more reliable" via structured disambiguation into four concrete improvements

---

## 4. Plan and Rationale

### What was prioritised (and why)

| Decision | Rationale |
|----------|-----------|
| Spring Boot + JDBC (not JPA/Hibernate) | Explicit SQL gives full control over query shape; avoids N+1 risks; lighter than Hibernate for a service with three small tables |
| H2 in-memory for dev, Flyway for schema | Zero-configuration local development; Flyway ensures prod schema matches dev exactly |
| 7-character base62 codes | ~3.5 trillion codes — effectively unlimited for any realistic v1 load; SecureRandom prevents enumeration |
| Async click recording (queue + worker) | Redirect latency is UX-critical; analytics writes are not — decoupling the two is the right trade |
| Token bucket rate limiter (in-memory) | Simplest correct implementation; single instance is the stated v1 constraint; Redis is the obvious v2 upgrade path |
| AuditFilter at servlet level (not AOP) | Servlet filter captures all requests, including those rejected before Spring dispatches to a controller. AOP would miss filter-level rejections. |
| 100% line / 100% branch JaCoCo floor (functional classes) | 100% line requires excluding two platform bootstrap classes (ShortenerApplication.main() and Sha256's unreachable catch); 100% branch ensures every conditional is exercised; the floor enforces this in CI rather than relying on convention |

---

## 5. Risks, Trade-offs, and Validation

### Risks

| Risk | Likelihood | Impact | Mitigation | Residual |
|------|-----------|--------|-----------|---------|
| SSRF via redirect target | Medium | High | Literal-IP + local-name validation | DNS rebinding (known, documented) |
| Click loss on crash / queue overflow | High | Low | Drops counted and logged; redirect unaffected | Accepted for analytics |
| Rate limiter bypass via horizontal scale | High (scaled) | Medium | Per-instance limit ×N; acceptable for v1 single instance | v2: Redis distributed limiter |
| Host-header injection into `shortUrl` | Low | Low | Set `shortener.base-url` in production | Config discipline required |
| Code collision storm | Very low | Medium | Up to 5 retry attempts; `CODE_GENERATION_FAILED` error after | Practically impossible at v1 scale |
| Audit gap on DB outage | Low | Medium | Logged as warning; request not failed | Accepted best-effort |

### Trade-offs

| Trade-off | What was chosen | What was rejected | Why |
|-----------|----------------|------------------|-----|
| Redirect status | 302 Temporary | 301 Permanent | 301 is cached by browsers; breaks click counting and future expiry |
| DNS host validation | Not performed | Resolve at creation time | Latency, network dependency, still bypassable via TTL; SSRF protection via literal-IP check is simpler and sound |
| Audit timing | After response committed | Before response | A pre-response audit failure would turn a valid request into a 500 |
| Click storage | Async bounded queue | Synchronous DB write | Redirect p99 latency would depend on DB write latency |
| Stats consistency | Eventual (worker-drained) | Strong consistency | Read-after-write delay of <1ms is acceptable for analytics |

---

## 6. Assumptions

1. The service runs as a single instance in v1; horizontal scaling is a v2 concern.
2. Analytics are for aggregate reporting only — individual click loss (on crash or queue overflow) is acceptable.
3. The production database is PostgreSQL-compatible; the Flyway migration and JDBC queries are written for both H2 and Postgres.
4. Custom aliases are case-sensitive and ASCII-only; Unicode alias support is out of scope.
5. Link expiry (TTL) is out of scope for v1.
6. Authentication / authorization is out of scope for v1 — the rate limiter keys on the client IP hash as a proxy.
7. The base URL in `shortUrl` must be configured explicitly in production via `shortener.base-url`; the `Host` header derivation is for local development only.

---

## 7. Limitations

| Limitation | Impact | v2 Path |
|-----------|--------|---------|
| No link expiry (TTL) | Links live forever | Add `expires_at` column + background cleanup job |
| In-memory rate limiter | Limit multiplies with instance count | Redis-backed rate limiter |
| No authentication | Anyone can create or delete any link | Add API-key or OAuth 2 authentication |
| No Prometheus / metrics endpoint | Latency and error-rate observability requires log parsing | Add Spring Actuator + Micrometer |
| DNS-rebinding SSRF not mitigated at application layer | Internal services accessible via SSRF if DNS can be manipulated | Egress proxy with allowlist; or DNS resolution at creation with short TTL check |
| Click worker not drained on shutdown | Queued clicks lost on non-graceful JVM exit | `@PreDestroy` drain with timeout |
| No PostgreSQL integration test in CI | Flyway migrations only tested against H2 | Add Testcontainers Postgres test profile |
| No load testing | Redirect latency SLA not measured under realistic concurrency | k6 or Gatling load suite |

---

## 8. How to Run

```bash
# Local development (H2 in-memory)
./mvnw spring-boot:run

# Full verify (compile + test + static analysis + coverage)
./mvnw verify

# Docker
docker build -t url-shortener .
docker run -p 8080:8080 url-shortener

# Smoke test
curl -s -X POST http://localhost:8080/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/long/path"}' | jq .

# Follow the redirect
curl -v http://localhost:8080/<code>
```

---

## 9. Evaluation Checklist

| Criterion | Evidence |
|-----------|---------|
| Working prototype (end-to-end runnable) | `./mvnw spring-boot:run` + smoke test |
| Architecture overview | `docs/design.md`, `README.md` |
| Greenfield scenario | `docs/scenarios.md` — Scenario 1 |
| Brownfield scenario | `docs/scenarios.md` — Scenario 2 |
| Ambiguous scenario | `docs/scenarios.md` — Scenario 3 |
| Requirements Agent output | `docs/requirements-agent.md` |
| Design Agent output | `docs/design.md`, `openapi.yaml` |
| Development Agent output | `src/main/java/`, `Dockerfile`, `V1__init.sql` |
| Core Review Agent output | `docs/code-review-agent.md` |
| QA Agent output | `docs/qa-agent.md` |
| Agentic orchestration design | `docs/orchestration.md` |
| Setup instructions | `README.md`, this document §8 |
| Testing approach | `docs/qa-agent.md` |
| Limitations and trade-offs | This document §5–7 |
| 100% line / 100% branch coverage (functional classes) | JaCoCo floor in `pom.xml`, enforced in CI |
| Static analysis (zero violations) | SpotBugs, FindSecBugs, PMD — all passing in CI |
| CI pipeline | `.github/workflows/ci.yml` |
