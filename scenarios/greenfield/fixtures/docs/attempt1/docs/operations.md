# Operations

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `shortener.rate-limit.requests-per-minute` | `20` | Token-bucket capacity and refill rate per client |
| `shortener.base-url` | empty (derived from request) | Public origin used to build `shortUrl`; set it in production |
| `spring.datasource.url` | in-memory H2 | Database; Flyway migrates on startup |

## Observability

- Logs use SLF4J and contain short codes and counters only, never URLs, IPs or other personal data.
- Clicks dropped because the queue was full are logged as a warning on the first drop and every 1,000 drops after that, with the running total. Each click that fails to persist is logged as a warning with its sanitized code and the exception type.
- Every state-changing request (`POST`, `PUT`, `PATCH`, `DELETE`), including rejected ones, is recorded in the `audit_event` table with its UTC time, opaque client key (the rate limiter's hash, never the raw address), method, path without query string, and final status. Query it with SQL, for example `SELECT * FROM audit_event ORDER BY occurred_at DESC`. A failed audit write is logged as a warning and does not fail the request.

## Failure behavior

| Condition | Behavior |
|---|---|
| Database unavailable during redirect | Redirect is served if the link lookup succeeds; click persistence failures are counted |
| Click queue full | Click dropped and counted; the redirect is unaffected |
| Code collisions | Up to 5 attempts, then `500 CODE_GENERATION_FAILED` |
