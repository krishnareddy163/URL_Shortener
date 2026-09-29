# SDLC Scenarios — URL Shortener

Three scenarios demonstrate the orchestration model across greenfield, brownfield, and ambiguous requirement contexts. Each scenario follows the same structure: requirement → decomposition → orchestration execution → validation.

---

## Scenario 1 — Greenfield: Core URL Shortening API

### Requirement

> "Build a service that shortens long URLs. A client POSTs a URL and receives a short code. GETting the short code redirects to the original URL."

### Requirement Analysis (Requirements Agent)

**Ambiguities identified:**
- What HTTP status code for redirect? (Resolved: 302 — prevents browser-level click-bypass caching)
- What is the short code format and length? (Resolved: 7-character base62, ~3.5T codes)
- Should the same URL always return the same code? (Resolved: yes — idempotent on normalized URL via `url_hash`)
- What constitutes a "valid" URL? (Resolved: http/https, host required, no credentials, no private IPs)

**Epics scoped for this scenario:** E1 (US-01), E2 (US-04, US-05)

### Task Decomposition

| Task | Dependency | Agent | Parallelisable? |
|------|-----------|-------|-----------------|
| T1: Data model (`link`, `click` tables) | None | Design | — |
| T2: `UrlValidator` — scheme, host, SSRF rules | T1 | Development | — |
| T3: `CodeGenerator` — base62 SecureRandom | T1 | Development | Yes (with T2) |
| T4: `ShortenerService.create()` — idempotent insert | T2, T3 | Development | — |
| T5: `ShortenerService.resolve()` — click enqueue | T4 | Development | — |
| T6: `LinkController` — POST + GET endpoints | T4, T5 | Development | — |
| T7: Unit tests for T2, T3 | T2, T3 | QA | Yes (with T4–T6) |
| T8: Integration test — create → redirect flow | T6 | QA | — |
| T9: Review | T7, T8 | Review | — |

### Orchestration Execution

```
Requirements Agent
  └─ Produces: user stories US-01, US-04, US-05 + acceptance criteria
       │  EXIT GATE: human reviews AC ✓
       ▼
Design Agent
  └─ Produces: V1__init.sql schema, component diagram, key decisions 1–5
       │  EXIT GATE: spectral lint on openapi.yaml passes ✓
       ▼
  ┌────────────┬───────────────┐
  │ Development│ QA (design)   │  ← parallel
  │ T1 → T6   │ Test plan      │
  └────┬───────┴───────┬───────┘
       │   sync gate   │
       └───────┬───────┘
       ▼
Review Agent
  └─ FindSecBugs, SpotBugs, PMD, manual review
       │  EXIT GATE: 0 blockers ✓
       ▼
QA Agent (execution)
  └─ ./mvnw verify — all tests green, 100% line, 100% branch (functional)
       │  EXIT GATE: coverage floor passed ✓
       ▼
  ⚠ HUMAN CHECKPOINT: review coverage report
       ▼
Release Agent — v1.0.0
```

### Validation

| Acceptance Criterion | Verified by | Result |
|---------------------|-------------|--------|
| POST returns 201 with 7-char code | `LinkApiIntegrationTest.createThenRedirectReturns302WithLocation` | PASS |
| Same URL returns 200 (idempotent) | `ShortenerServiceTest.duplicateUrlReturnsExistingLink` | PASS |
| Invalid URL returns 400 INVALID_URL | `UrlValidatorTest`, `LinkApiIntegrationTest` | PASS |
| GET /{code} returns 302 + Location | `LinkApiIntegrationTest.createThenRedirectReturns302WithLocation` | PASS |
| Unknown code returns 404 | `LinkApiIntegrationTest` | PASS |
| Click recorded asynchronously | `LinkApiIntegrationTest.clickCountIncrements` (with Awaitility) | PASS |
| SSRF targets rejected | `UrlValidatorEdgeCaseTest` (loopback, RFC-1918, link-local) | PASS |

**Risk identified:** Code collisions under high concurrency — mitigated by retry loop (up to 5 attempts) before `CODE_GENERATION_FAILED`. Probability: ~2.8e-8 per attempt at 10% code-space fill.

---

## Scenario 2 — Brownfield: Add Rate Limiting to Existing Service

### Requirement

> "The shortener is seeing abuse — bots are creating thousands of links per second. Add per-client rate limiting to the create endpoint."

### Requirement Analysis (Requirements Agent)

**Brownfield impact analysis — modules affected:**

| Module | Impact |
|--------|--------|
| `LinkController` | Must check rate limit before delegating to service |
| `ShortenerService` | No change — limit is enforced at the API layer |
| `AuditFilter` | Must record 429 responses (already records all statuses) |
| `ApiExceptionHandler` | Must map `RATE_LIMITED` error code to 429 HTTP status |
| Test suite | New unit tests for `RateLimiter`; integration tests for 429 flow |
| `application.properties` | New `shortener.rate-limit.requests-per-minute` property |
| `openapi.yaml` | New 429 response object for `POST /links` |

**Ambiguities resolved:**
- Per-IP or per-user? (Resolved: per-client, keyed on opaque hash of remote address — no auth in v1)
- Sliding window or token bucket? (Resolved: token bucket — smoother burst handling; simpler stateless implementation)
- Should `GET /{code}` be rate-limited? (Resolved: no — redirect abuse is less impactful; limits apply only to write operations)
- Limit value? (Resolved: 20 req/min default, overridable via config — matches US-07)

### Task Decomposition

| Task | Dependency | Agent | Parallelisable? |
|------|-----------|-------|-----------------|
| T1: `RateLimiter` — token-bucket, ConcurrentHashMap, client-key hashing | None | Development | — |
| T2: Wire `RateLimiter` into `LinkController.createLink()` | T1 | Development | — |
| T3: `ApiExceptionHandler` — map `RATE_LIMITED` → 429 | T1 | Development | Yes (with T2) |
| T4: Add `shortener.rate-limit.requests-per-minute` to `application.properties` | T1 | Development | Yes |
| T5: Update `openapi.yaml` with 429 response | T1 | Design | Yes |
| T6: Unit tests — `RateLimiterTest` (allows/denies at boundary, refill) | T1 | QA | Yes (with T2–T5) |
| T7: Integration test — verify 429 after N+1 requests | T2, T3 | QA | — |
| T8: Review — check client-key implementation for privacy | T6, T7 | Review | — |
| T9: Verify existing tests still pass (regression) | T8 | QA | — |

### Orchestration Execution

```
Codebase Reasoning (Design Agent re-scan)
  └─ Identifies: LinkController, ApiExceptionHandler, AuditFilter in impact set
       │
       ▼
Design Agent (incremental)
  └─ Updates: openapi.yaml (429), key-decision for rate-limit algorithm
       │  EXIT GATE: schema diff reviewed — no breaking changes ✓
       ▼
  ┌────────────────┬──────────────┐
  │ Development    │ QA (design)  │  ← parallel
  │ T1 → T4        │ T6 test plan │
  └──────┬─────────┴──────┬───────┘
         │   sync gate    │
         └───────┬────────┘
         ▼
Review Agent
  └─ Focus: raw-IP logging (must use hash), thread safety of Bucket map
       │  Finding: ConcurrentHashMap.computeIfAbsent is safe ✓
       │  Finding: LogSanitizer used consistently ✓
       │  EXIT GATE: 0 blockers ✓
       ▼
QA Agent (execution)
  └─ RateLimiterTest (12 cases), integration 429 flow, regression suite
       │  EXIT GATE: all pass, coverage maintained ✓
       ▼
Release Agent — v1.1.0
```

### Validation

| Acceptance Criterion | Verified by | Result |
|---------------------|-------------|--------|
| 21st request from same client returns 429 | `RateLimiterTest.exceedingLimitReturnsFalse` | PASS |
| Rate limit resets after 1 minute | `RateLimiterTest` (MutableClock advance) | PASS |
| Raw IP never logged | `LogSanitizer` + `ClientKeyResolver` review | PASS |
| GET /{code} is not rate-limited | `LinkApiIntegrationTest` — redirect not gated | PASS |
| 429 recorded in audit trail | `AuditTrailIntegrationTest` | PASS |
| Existing tests unaffected (regression) | Full `./mvnw verify` | PASS |

**Risk identified:** In-memory rate limiter is per-instance — horizontal scale means the limit multiplies by instance count. Documented in design risks; a shared store (Redis) is the v2 remedy.

**Rollback plan:** Rate limiting is feature-flagged via `shortener.rate-limit.requests-per-minute=0` disables it (0 = unlimited). A single config change reverts the behavior without a code deployment.

---

## Scenario 3 — Ambiguous: "Make the service more reliable"

### Requirement

> "We need to make the URL shortener more reliable. It's had some incidents."

### Requirement Analysis (Requirements Agent)

**Ambiguity decomposition** — "reliable" is undefined. The Requirements Agent applied structured disambiguation:

| Interpretation | Candidate requirement |
|---------------|----------------------|
| Availability | Service stays up when the DB is slow |
| Data integrity | Clicks are not silently lost |
| Observability | We know *when* and *why* incidents happen |
| Change safety | Bad deployments can be reverted quickly |
| Security posture | Attacks don't cause outages |

**Incident pattern analysis (assumed from context):** "incidents" most likely means either silent click loss or unhandled exceptions crashing the service.

**Resolved scope for this scenario:**
1. Async click recording with bounded queue + drop counting (prevents analytics writes from blocking redirects)
2. Audit trail for all mutations (compliance + forensics)
3. Security headers on all responses (defensive posture)
4. Operational runbook (`docs/operations.md`)

**Deferred to v2 (out of scope for this increment):**
- Circuit breaker for DB
- Distributed rate limiting
- Prometheus metrics endpoint
- Alert rules

### Task Decomposition

| Task | Dependency | Agent | Parallelisable? |
|------|-----------|-------|-----------------|
| T1: `ClickRecorder` — bounded ArrayBlockingQueue, single worker thread | None | Development | — |
| T2: Wire `ClickRecorder` into `ShortenerService.resolve()` | T1 | Development | — |
| T3: Drop counter + warning log (first drop + every 1,000) | T1 | Development | Yes (with T2) |
| T4: `AuditFilter` — servlet filter records all mutations to `audit_event` | None | Development | Yes (with T1–T3) |
| T5: `SecurityHeadersFilter` — CSP, HSTS, X-Frame-Options, nosniff | None | Development | Yes |
| T6: Update `V1__init.sql` — add `audit_event` table | T4 | Design | — |
| T7: Unit tests — `ClickRecorderTest` (enqueue, drop, worker drain) | T1, T3 | QA | Yes |
| T8: Integration test — audit trail present on POST/DELETE | T4 | QA | Yes |
| T9: `docs/operations.md` — config reference, failure behavior table | All | Design | — |
| T10: Review — AuditFilter timing (response already committed before write) | T4–T8 | Review | — |

### Orchestration Execution

```
Requirements Agent
  └─ Ambiguity resolution: structured interpretation → scoped to 4 improvements
       │  Note: deferred items documented explicitly
       │  HUMAN CHECKPOINT: confirm scope is correct ✓
       ▼
Design Agent
  └─ Updates schema (audit_event table), adds sequence diagram for async click flow
       │  EXIT GATE: schema change flagged for human review ✓
       │  HUMAN APPROVAL: schema migration approved ✓
       ▼
  ┌──────────────────────────┬───────────────┐
  │ Development              │ QA (design)   │  ← parallel
  │ T1→T3 (ClickRecorder)    │ Test plan     │
  │ T4 (AuditFilter)         │               │
  │ T5 (SecurityHeaders)     │               │
  └──────────┬───────────────┴───────┬───────┘
             │      sync gate        │
             └──────────┬────────────┘
             ▼
Review Agent
  └─ Key finding: AuditFilter writes AFTER response committed —
     if DB is down, audit fails silently (acceptable, logged as warning) ✓
  └─ Key finding: ClickRecorder worker is a daemon thread —
     clicks in queue may be lost on JVM shutdown (documented, accepted) ✓
       │  EXIT GATE: 0 blockers, 2 warnings formally accepted ✓
       ▼
QA Agent (execution)
  └─ ClickRecorderTest (Awaitility), AuditTrailIntegrationTest, SecurityHeaders assertions
       │  EXIT GATE: all pass ✓
       ▼
  ⚠ HUMAN CHECKPOINT: review operations.md runbook before release
       ▼
Release Agent — v1.2.0
```

### Validation

| Acceptance Criterion | Verified by | Result |
|---------------------|-------------|--------|
| Redirect is not blocked by click DB write | `ClickRecorderTest.redirectNotBlockedOnSlowDb` (mocked) | PASS |
| Dropped clicks are counted and logged | `ClickRecorderTest.droppedClickIsCountedAndLogged` | PASS |
| POST /links recorded in audit_event | `AuditTrailIntegrationTest.createLinkAudited` | PASS |
| DELETE /links/{code} recorded in audit_event | `AuditTrailIntegrationTest.deleteLinkAudited` | PASS |
| 429 (rate limit) recorded in audit_event | `AuditTrailIntegrationTest.rateLimitedRequestAudited` | PASS |
| Audit write failure does not fail request | `AuditFilterTest.auditWriteFailureDoesNotPropagateException` | PASS |
| X-Content-Type-Options: nosniff on all responses | `LinkApiIntegrationTest.securityHeadersPresent` | PASS |
| Cache-Control: no-store on all responses | `LinkApiIntegrationTest.securityHeadersPresent` | PASS |

**Decision lineage:**
- Chose `ArrayBlockingQueue(10_000)` over `LinkedBlockingQueue` (bounded by default, no unbounded memory growth)
- Chose single worker thread over thread pool (analytics writes are ordered by enqueue time; pooling would reorder them)
- Chose `AuditFilter` as a servlet filter (not Spring AOP) so that rejected requests (4xx from Spring) are also audited before the response is committed

**Known limitations documented:**
- Clicks in the queue are lost on non-graceful JVM shutdown (acceptable for v1 analytics)
- Audit write is best-effort; a persistent DB failure will leave a gap in the audit log
- SecurityHeadersFilter does not set HSTS (HTTP Strict Transport Security) because local dev runs on HTTP; set this at the load-balancer in production
