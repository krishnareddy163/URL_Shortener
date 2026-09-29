# Requirements Agent — URL Shortener

## Agent Prompt

> "Define the full set of user stories and acceptance criteria for a production-grade URL shortener service with core APIs, analytics, and reliability features."

---

## Epics

| ID | Epic | Priority |
|----|------|----------|
| E1 | Link Lifecycle Management | P0 |
| E2 | Redirect & Click Tracking | P0 |
| E3 | Analytics | P1 |
| E4 | Rate Limiting & Abuse Prevention | P1 |
| E5 | Security & Audit | P1 |
| E6 | Operability & Reliability | P2 |

---

## User Stories & Acceptance Criteria

### E1 — Link Lifecycle Management

---

**US-01: Create a short link**

> As a developer integrating via API, I want to POST a long URL and receive a short code so that I can share compact links.

**Acceptance Criteria:**
- [ ] `POST /links` with `{"url": "<valid_url>"}` returns `201 Created` with `code`, `url`, `shortUrl`, and `createdAt`
- [ ] `shortUrl` resolves to `<base>/<code>` where `<base>` is either the configured `shortener.base-url` or derived from the request `Host` header
- [ ] Code is exactly 7 base62 characters (`[0-9A-Za-z]{7}`)
- [ ] Sending the same URL a second time returns `200 OK` with the existing link (idempotent)
- [ ] Sending an invalid URL returns `400 Bad Request` with `{"error":{"code":"INVALID_URL","message":"..."}}`
- [ ] URLs longer than 2048 characters are rejected with `400 INVALID_URL`
- [ ] `http://` and `https://` schemes are accepted; all others (`ftp://`, `file://`, etc.) are rejected

---

**US-02: Create a link with a custom alias**

> As a marketing user, I want to specify a memorable alias (e.g. `/spring-sale`) so that shared links carry brand meaning.

**Acceptance Criteria:**
- [ ] `POST /links` with `{"url": "...", "customAlias": "spring-sale"}` returns `201 Created` with `code = "spring-sale"`
- [ ] Alias must match `[a-zA-Z0-9_-]{1,32}`; violations return `400 INVALID_ALIAS`
- [ ] Requesting an already-taken alias returns `409 ALIAS_TAKEN`
- [ ] Sending the same URL+alias again returns `200 OK` with the existing link
- [ ] Sending an existing URL with a *different* alias returns `409 URL_ALREADY_SHORTENED`

---

**US-03: Delete a short link**

> As an administrator, I want to delete a short link so that it can no longer be followed.

**Acceptance Criteria:**
- [ ] `DELETE /links/{code}` for an existing code returns `204 No Content`
- [ ] `DELETE /links/{code}` for an unknown code returns `404 LINK_NOT_FOUND`
- [ ] After deletion, `GET /{code}` returns `404 LINK_NOT_FOUND`
- [ ] Deletion is recorded in the audit trail

---

### E2 — Redirect & Click Tracking

---

**US-04: Follow a short link**

> As an end user clicking a short link, I want to be immediately redirected to the original URL.

**Acceptance Criteria:**
- [ ] `GET /{code}` for a valid code returns `302 Found` with `Location: <original_url>`
- [ ] `GET /{code}` for an unknown code returns `404 LINK_NOT_FOUND`
- [ ] Response includes `Cache-Control: no-store` to prevent caching
- [ ] Redirect latency (p99) is under 50 ms under normal load (DB available)

---

**US-05: Record a click without blocking the redirect**

> As a product owner, I want every redirect to record a click for analytics without slowing the user's redirect.

**Acceptance Criteria:**
- [ ] Click is enqueued asynchronously; redirect response time is not affected by DB write latency
- [ ] Clicks are persisted with UTC timestamp and the short code
- [ ] When the in-memory queue is full (10,000 items), the click is dropped — the redirect still succeeds
- [ ] Dropped clicks are counted and logged as a warning on the first drop and every 1,000 drops thereafter

---

### E3 — Analytics

---

**US-06: View click statistics for a link**

> As a developer, I want to GET statistics for a link so I can measure its performance.

**Acceptance Criteria:**
- [ ] `GET /links/{code}` returns `200 OK` with `code`, `totalClicks`, `lastAccessedAt`, and `clicksPerDay` array
- [ ] `clicksPerDay` contains one entry per UTC day with clicks, ordered most-recent first
- [ ] `lastAccessedAt` is `null` for a link with zero clicks
- [ ] `GET /links/{code}` for an unknown code returns `404 LINK_NOT_FOUND`
- [ ] Stats reflect clicks recorded by the async worker (eventual consistency is acceptable)

---

### E4 — Rate Limiting & Abuse Prevention

---

**US-07: Rate-limit link creation per client**

> As a platform engineer, I want per-client rate limiting on creation so that a single caller cannot exhaust the code space.

**Acceptance Criteria:**
- [ ] Each client is limited to 20 `POST /links` requests per minute (configurable via `shortener.rate-limit.requests-per-minute`)
- [ ] Exceeding the limit returns `429 Too Many Requests` with `{"error":{"code":"RATE_LIMITED","message":"..."}}`
- [ ] The rate limit resets after one minute (token-bucket refill)
- [ ] The client key is an opaque hash of the remote address — raw IPs are never logged
- [ ] Rate limit is applied only to write operations (`POST`, `PUT`, `PATCH`), not to redirects or reads

---

**US-08: Reject unsafe redirect targets**

> As a security engineer, I want the service to reject URLs that could enable SSRF so that internal services are not reachable via redirects.

**Acceptance Criteria:**
- [ ] `localhost`, `*.local`, and loopback IPs (`127.x.x.x`, `::1`) are rejected with `400 INVALID_URL`
- [ ] RFC-1918 private ranges (`10.x`, `172.16–31.x`, `192.168.x`) are rejected
- [ ] Link-local, unspecified, and ambiguous numeric addresses are rejected
- [ ] URLs with embedded credentials (`user:pass@host`) are rejected
- [ ] Internationalized domain names arrive as punycode; ASCII-only validation prevents Unicode look-alike bypass

---

### E5 — Security & Audit

---

**US-09: Audit trail for all state-changing operations**

> As a compliance officer, I want every write operation logged to an immutable audit table so that I can reconstruct the history of changes.

**Acceptance Criteria:**
- [ ] Every `POST`, `PUT`, `PATCH`, and `DELETE` request is recorded in `audit_event` with: `occurred_at` (UTC), `client_key` (opaque hash), `method`, `path` (no query string), `status`
- [ ] Rejected requests (4xx) are also audited
- [ ] An audit write failure is logged as a warning and does not fail the request
- [ ] The audit table is queryable: `SELECT * FROM audit_event ORDER BY occurred_at DESC`

---

**US-10: Security response headers**

> As a security engineer, I want every response to include hardened HTTP headers so that browsers are protected from common attacks.

**Acceptance Criteria:**
- [ ] All responses include `X-Content-Type-Options: nosniff`
- [ ] All responses include `Cache-Control: no-store`
- [ ] All responses include a `Content-Security-Policy` header
- [ ] All responses include `X-Frame-Options: DENY`
- [ ] Redirect responses (`302`) include the security headers before the `Location` header

---

### E6 — Operability & Reliability

---

**US-11: Schema-managed database**

> As a DevOps engineer, I want the database schema to be version-controlled and applied automatically on startup so that deployments are repeatable.

**Acceptance Criteria:**
- [ ] Flyway applies all migrations on startup; the app does not start if migration fails
- [ ] `V1__init.sql` creates `link`, `click`, and `audit_event` tables with appropriate indexes
- [ ] The app works with in-memory H2 for local development with no additional configuration
- [ ] The schema is compatible with PostgreSQL for production deployment

---

**US-12: Containerised deployment**

> As a platform engineer, I want a production-ready Docker image so that the service can be deployed to any container platform.

**Acceptance Criteria:**
- [ ] Multi-stage Dockerfile: build stage uses JDK, runtime stage uses JRE only
- [ ] The runtime process runs as a non-root user
- [ ] The image exposes port `8080`
- [ ] `docker build -t url-shortener . && docker run -p 8080:8080 url-shortener` starts a working service

---

**US-13: Configurable base URL**

> As a platform engineer, I want to override the base URL used in `shortUrl` responses so that deployed instances return the correct public address.

**Acceptance Criteria:**
- [ ] Setting `shortener.base-url=https://sho.rt` causes `shortUrl` in responses to use `https://sho.rt/<code>`
- [ ] When `shortener.base-url` is unset, the base URL is derived from the HTTP `Host` request header
- [ ] The base URL never includes a trailing slash in the output

---

## Ambiguities Identified and Resolved

| Ambiguity | Resolution |
|-----------|------------|
| Should redirects use 301 or 302? | **302** — prevents browser caching of the redirect target, which would break click counting and future expiry |
| Should stats include the current day? | **Yes** — daily buckets use UTC; the current day is included with its partial count |
| What happens to clicks for a deleted link? | **Clicks already recorded are retained** — deletion removes the link row but not historical analytics (v1 scope) |
| Should custom aliases be case-sensitive? | **Yes** — `sale` and `SALE` are different codes; normalisation to lowercase is not applied |
| Should DNS resolution validate hosts? | **No** — DNS rebinding is a known limitation documented in the design; resolution adds latency and requires network access at creation time |
| What is the max alias length? | **32 characters** — long enough for readable slugs, short enough to keep URLs compact |
