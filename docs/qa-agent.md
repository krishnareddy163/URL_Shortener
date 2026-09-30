# QA Agent — URL Shortener

**Date:** 2026-09-29  
**Build:** `mvn -f shortener-service/pom.xml verify`  
**Java:** 25  
**Spring Boot:** 3.5.16

---

## Test Suite Inventory

| Class | Type | Layer | Test cases |
|-------|------|-------|-----------|
| `LinkApiIntegrationTest` | Integration | API + full stack | 12 |
| `AuditTrailIntegrationTest` | Integration | API + DB | 2 |
| `ConfiguredBaseUrlIntegrationTest` | Integration | API config | 1 |
| `ShortenerApplicationTest` | Integration | Application entry point | 1 |
| `UrlValidatorTest` | Unit | Service | 49 |
| `UrlValidatorEdgeCaseTest` | Unit (parameterized) | Service | 26 |
| `ShortenerServiceTest` | Unit | Service | 22 |
| `IncidentNotificationServiceTest` | Unit | Service | 8 |
| `ApiExceptionHandlerTest` | Unit | API | 7 |
| `RequestIdFilterTest` | Unit | Filter | 7 |
| `RateLimiterTest` | Unit | Service | 6 |
| `ClickRecorderTest` | Unit | Service | 5 |
| `AuditFilterTest` | Unit | Filter | 4 |
| `CodeGeneratorTest` | Unit | Service | 4 |
| `IncidentSeverityTest` | Unit | Service | 4 |
| `Sha256Test` | Unit | Service | 3 |
| **Total** | | | **161** |

---

## Test Results

```
[INFO] Tests run: 161, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**All 161 tests pass.**

---

## Coverage Report (JaCoCo)

Coverage enforced by `jacoco-maven-plugin` with floor checks at `verify` phase.

| Package | Classes | Lines covered | Line % | Branches covered | Branch % |
|---------|---------|--------------|--------|-----------------|---------|
| `root` | 1 | 4 / 4 | 100% | — | — |
| `api` | 11 | 89 / 89 | 100% | 19 / 19 | 100% |
| `domain` | 4 | 8 / 8 | 100% | 2 / 2 | 100% |
| `service` | 17 | 268 / 268 | 100% | 162 / 162 | 100% |
| `storage` | 4 | 31 / 31 | 100% | — | — |
| **Total** | **37** | **400 / 400** | **100%** | **183 / 183** | **100%** |

> No class is excluded from the floor check: every class, including `ShortenerApplication` and `Sha256`, is at 100% line and branch. `Sha256` takes the algorithm name in a package-private overload so the `NoSuchAlgorithmException` handler is testable, and `ShortenerApplicationTest` calls `main()`.

**Floor enforcement (no exclusions):**
- Line coverage: 100% = 100% floor ✓
- Branch coverage: 100% = 100% floor ✓

---

## Functional Test Coverage

Traceability between user stories (from `docs/requirements-agent.md`) and test coverage:

| User Story | Acceptance Criterion | Test | Status |
|-----------|---------------------|------|--------|
| US-01 | POST returns 201 with 7-char base62 code | `LinkApiIntegrationTest.createThenRedirectReturns302WithLocation` | PASS |
| US-01 | Same URL returns 200 (idempotent) | `ShortenerServiceTest.sameUrlReturnsExistingLink` | PASS |
| US-01 | Invalid URL returns 400 INVALID_URL | `UrlValidatorTest`, `LinkApiIntegrationTest.invalidUrlReturns400` | PASS |
| US-01 | URL > 2048 chars rejected | `UrlValidatorTest.urlTooLong` | PASS |
| US-01 | Non-http(s) scheme rejected | `UrlValidatorTest.ftpSchemeRejected` | PASS |
| US-02 | Custom alias returns 201 with correct code | `LinkApiIntegrationTest.duplicateAliasReturns409` (first create with alias succeeds) | PASS |
| US-02 | Invalid alias format returns 400 INVALID_ALIAS | `LinkApiIntegrationTest.invalidAliasAndMalformedBodiesReturn400` | PASS |
| US-02 | Taken alias returns 409 ALIAS_TAKEN | `LinkApiIntegrationTest.duplicateAliasReturns409` | PASS |
| US-02 | Existing URL + same alias returns 200 | `ShortenerServiceTest` | PASS |
| US-02 | Existing URL + different alias returns 409 | `ShortenerServiceTest` | PASS |
| US-03 | DELETE returns 204 | — | DEFERRED (v1 scope; core requirement is create/redirect/stats) |
| US-03 | DELETE unknown returns 404 | — | DEFERRED (v1 scope) |
| US-03 | Deleted code returns 404 on redirect | — | DEFERRED (v1 scope) |
| US-03 | Deletion audited | — | DEFERRED (v1 scope) |
| US-04 | GET /{code} returns 302 + Location | `LinkApiIntegrationTest.createThenRedirectReturns302WithLocation` | PASS |
| US-04 | GET unknown code returns 404 | `LinkApiIntegrationTest.unknownCodeReturns404WithErrorEnvelope` | PASS |
| US-04 | Response includes Cache-Control: no-store | `LinkApiIntegrationTest.responsesAreNotCacheableAndNotSniffable` | PASS |
| US-05 | Click recorded asynchronously | `LinkApiIntegrationTest.statsReportTotalsLastAccessAndPerDayBuckets` (Awaitility) | PASS |
| US-05 | Queue-full drop does not affect redirect | `ClickRecorderTest` | PASS |
| US-05 | Drop is counted and logged | `ClickRecorderTest` | PASS |
| US-06 | GET /api/v1/links/{code}/stats returns stats | `LinkApiIntegrationTest.statsReportTotalsLastAccessAndPerDayBuckets` | PASS |
| US-06 | clicksPerDay entries present after clicks | `LinkApiIntegrationTest.statsReportTotalsLastAccessAndPerDayBuckets` | PASS |
| US-06 | lastAccessedAt absent for zero-click link | `LinkApiIntegrationTest.statsReportTotalsLastAccessAndPerDayBuckets` | PASS |
| US-06 | Unknown code returns 404 on stats | `LinkApiIntegrationTest.statsForUnknownCodeReturns404` | PASS |
| US-07 | 21st request returns 429 RATE_LIMITED | `LinkApiIntegrationTest.rateLimitReturns429AfterTwentyCreatesPerMinute` | PASS |
| US-07 | Rate limit resets after 1 minute | `RateLimiterTest` | PASS |
| US-07 | Raw IP not logged | `AuditTrailIntegrationTest.createdAndRejectedRequestsAreAuditedWithoutPersonalData` + R-04 | PASS |
| US-07 | GET /{code} not rate-limited | `LinkApiIntegrationTest` — no 429 on redirect | PASS |
| US-07 | Rejected create audited | `AuditTrailIntegrationTest.createdAndRejectedRequestsAreAuditedWithoutPersonalData` | PASS |
| US-08 | localhost rejected | `UrlValidatorEdgeCaseTest` (parameterized) | PASS |
| US-08 | Loopback IPs rejected (IPv4 + IPv6) | `UrlValidatorEdgeCaseTest` (parameterized) | PASS |
| US-08 | RFC-1918 ranges rejected | `UrlValidatorEdgeCaseTest` (parameterized) | PASS |
| US-08 | Link-local rejected | `UrlValidatorEdgeCaseTest` (parameterized) | PASS |
| US-08 | Credentials in URL rejected | `UrlValidatorEdgeCaseTest` (parameterized) | PASS |
| US-09 | POST audited (201 + 400 in same table) | `AuditTrailIntegrationTest.createdAndRejectedRequestsAreAuditedWithoutPersonalData` | PASS |
| US-09 | Reads are NOT audited | `AuditTrailIntegrationTest.readsAreNotAudited` | PASS |
| US-09 | Audit write failure does not fail request | `AuditFilterTest` | PASS |
| US-10 | X-Content-Type-Options: nosniff | `LinkApiIntegrationTest.responsesAreNotCacheableAndNotSniffable` | PASS |
| US-10 | Cache-Control: no-store | `LinkApiIntegrationTest.responsesAreNotCacheableAndNotSniffable` | PASS |
| US-10 | Unmapped routes use error envelope | `LinkApiIntegrationTest.unmappedRoutesAndMethodsUseTheErrorEnvelope` | PASS |
| US-11 | Flyway migration runs on startup | `LinkApiIntegrationTest` (Spring context loads against H2) | PASS |
| US-13 | shortener.base-url honoured in shortUrl | `ConfiguredBaseUrlIntegrationTest` | PASS |
| US-13 | Host-derived shortUrl when base-url unset | `LinkApiIntegrationTest.shortUrlPointsAtTheRedirectEndpoint` | PASS |

**Functional coverage: 34 / 38 acceptance criteria PASS · 4 DEFERRED (US-03 delete endpoint, out of v1 scope)**

---

## Areas Where 100% Was Not Achieved

| Area | Target | Actual | Reason |
|------|--------|--------|--------|
| Line coverage | 100% | **100%** | All 37 classes at 100% (no exclusions) |
| Branch coverage | 100% | **100%** | All branches exercised |
| Functional AC coverage | 100% | 34/38 = 89% | 4 ACs for US-03 (DELETE) deferred to v2 — not a core assignment requirement (core = create/redirect/stats) |
| US-12 (Docker) | Manual | Manual | Docker build validated in CI; automated Testcontainers-based container tests are a v2 improvement |

---

## Static Analysis Results

| Tool | Run as part of | Result |
|------|---------------|--------|
| SpotBugs 4.10.4.1 + FindSecBugs 1.14.0 | `mvn -f shortener-service/pom.xml verify` | PASS — 0 findings |
| PMD 3.28.0 (rules + CPD) | `mvn -f shortener-service/pom.xml verify` | PASS — 0 violations |
| Compiler (`-Xlint:all -Werror`) | `mvn -f shortener-service/pom.xml compile` | PASS — 0 warnings |

---

## Reliability Metrics (CI run)

| Metric | Value |
|--------|-------|
| Test suite runtime | ~12 seconds |
| Tests run | 161 |
| Tests failed | 0 |
| Tests skipped | 0 |
| Retry count (flaky tests) | 0 |
| Build total time | ~45 seconds |

---

## Known Gaps & v2 Improvements

| Gap | Impact | Planned fix |
|-----|--------|------------|
| No container-level integration test | US-12 not fully automated | Add Testcontainers + PostgreSQL test profile in v2 |
| Load tests run locally and on one CI runner only | Redirect latency SLA (US-04) is measured by the k6 smoke/load/stress suites in `perf/k6/`, not against production-like hardware | Run them against a deployed environment |
| Rate limiter is not integration-tested against HTTP layer (only unit) | 429 HTTP flow tested via MockMvc, not a real socket | Acceptable; MockMvc exercises the full filter chain |
| Click worker daemon thread not tested for shutdown drain | JVM-exit click loss is accepted for v1 | Add `@PreDestroy` drain + test in v2 |
