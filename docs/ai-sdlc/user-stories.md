# Requirements agent: user stories and acceptance criteria

Exported by `scripts/export-sdlc-artifacts.py` from the requirements artifacts of these runs; do not edit by hand.

- `greenfield-20260929-154142` (greenfield): [report](../sample-runs/greenfield/report.md)
- `brownfield-20260929-154212` (brownfield): [report](../sample-runs/brownfield/report.md)
- `ambiguous-20260929-154255` (ambiguous): [report](../sample-runs/ambiguous/report.md)
- `bugfix-20260929-154330` (bugfix): [report](../sample-runs/bugfix/report.md)

## greenfield

> Build a URL shortener service: create short links (with an optional custom alias), redirect by code, and report click statistics. It must not become an SSRF or abuse vector, must rate-limit link creation, and must never log personal data.

Verified by `requirements-complete` (seq 5). Artifact `d1f94f38a55e`.

**Problem statement.** Provide an HTTP service that turns long http(s) URLs into short codes, redirects visitors from a code to its URL, and reports per-link click analytics, without becoming an SSRF, abuse or privacy liability.

### User stories

1. As a link creator, I want to shorten a long http(s) URL, so that I can share a short, memorable link
2. As a link creator, I want to choose a custom alias, so that the short link is recognizable
3. As a visitor, I want a short link to redirect me to its URL immediately, so that following it feels like a normal link
4. As a link owner, I want per-day click statistics for my link, so that I can see how it is used
5. As the service operator, I want link creation rate-limited and unsafe targets rejected, so that the service cannot be abused for SSRF or spam
6. As the service operator, I want an audit trail of every change request, so that I can trace who changed what and when without storing personal data

### Acceptance criteria

1. POST /api/v1/links returns 201 with {code, shortUrl, url, createdAt}; the same normalized URL without an alias returns the existing link with 200
2. Custom aliases are 3-32 chars of [A-Za-z0-9_-], reserved words api/actuator/health/stats are rejected (400), duplicates return 409
3. Generated codes are 7 base62 characters from SecureRandom; collisions retry up to 5 times, then 500 CODE_GENERATION_FAILED
4. URLs must be http/https, <= 2048 chars, have a host, and not target localhost, *.local, or loopback/link-local/private/unspecified literal IPs
5. GET /{code} returns 302 with Location, or 404; the click is recorded asynchronously and never blocks the redirect
6. GET /api/v1/links/{code}/stats returns totalClicks, lastAccessedAt and per-UTC-day buckets, or 404
7. Link creation is rate limited per client (default 20/minute) with 429; the client key is a hash and raw IPs are never logged
8. Every non-2xx/3xx response uses {"error":{"code","message"}}
9. Every state-changing request is recorded in an audit trail with its time, opaque client key, method, path and final status; reads are not recorded

### Open questions

| Id | Question | Blocking | Answer or assumption |
|---|---|---|---|
| `q-storage` | Which database should back the service? | False | H2 with Flyway for the prototype; storage sits behind LinkRepository so it can be swapped |
| `q-click-overflow` | What happens to clicks when the recorder is saturated? | False | Drop and count them; redirect latency matters more than exact analytics |

### Assumptions

- Single-instance deployment: the rate limiter and click queue are in memory
- No authentication on the API in v1
- Link expiry is out of scope for v1
- q-storage: H2 with Flyway for the prototype; storage sits behind LinkRepository so it can be swapped
- q-click-overflow: Drop and count them; redirect latency matters more than exact analytics

## ambiguous

> Make links more secure and add some analytics.

Verified by `requirements-complete` (seq 5). Artifact `31e4d248c82b`.

**Problem statement.** Harden link creation against misuse of the shortener and confirm what analytics are expected, without breaking existing clients.

### User stories

1. As a visitor, I want short links to lead only to destinations the service considers safe, so that a short link cannot be used against me
2. As a link owner, I want aggregate click analytics without personal data, so that I can measure usage without tracking people

### Acceptance criteria

1. The chosen security measure is enforced at link creation and returns 400 INVALID_URL with a clear message when violated
2. Existing behavior not covered by the chosen measure is unchanged and the v1 test suite (adjusted only where the measure intentionally changes behavior) passes
3. Analytics expose only aggregate, non-personal data

### Open questions

| Id | Question | Blocking | Answer or assumption |
|---|---|---|---|
| `q-secure` | What does 'more secure' mean for this service? | True | domain-blocklist |
| `q-analytics` | Which analytics are needed beyond what exists? | False | click counts and daily buckets, no personal data (already provided by v1 stats; no referrer or IP collection) |

### Assumptions

- Clients already using https URLs must not be affected by the change
- q-analytics: click counts and daily buckets, no personal data (already provided by v1 stats; no referrer or IP collection)
