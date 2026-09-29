# Operations

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `shortener.rate-limit.requests-per-minute` | `20` | Token-bucket capacity and refill rate per client |
| `shortener.base-url` | empty (derived from request) | Public origin used to build `shortUrl`; set it in production |
| `spring.datasource.url` | in-memory H2 | Database; Flyway migrates on startup |

## Observability

- Logs use SLF4J and contain short codes and counters only, never URLs, IPs or other personal data.
- `ClickRecorder.droppedClicks()` and `ClickRecorder.failedClicks()` count clicks that were dropped because the queue was full or that failed to persist. A warning is logged on the first drop and every 1,000 drops after that.

## Failure behavior

| Condition | Behavior |
|---|---|
| Database unavailable during redirect | Redirect is served if the link lookup succeeds; click persistence failures are counted |
| Click queue full | Click dropped and counted; the redirect is unaffected |
| Code collisions | Up to 5 attempts, then `500 CODE_GENERATION_FAILED` |
