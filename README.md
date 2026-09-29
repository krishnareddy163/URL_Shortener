# URL Shortener

A production-ready URL shortener built with Java 25, Spring Boot 3.5, and H2 (dev) / PostgreSQL (prod).

## Features

- Shorten long URLs to memorable short codes
- Optional custom aliases
- Click analytics (daily counts, last-click timestamp)
- Per-client rate limiting (token bucket, 10 req/min default)
- Audit trail of all write operations
- Security headers (CSP, HSTS, X-Frame-Options)
- 98.5% line / 100% branch test coverage enforced by JaCoCo

## Quick start

```bash
./mvnw spring-boot:run
# POST a URL
curl -s -X POST http://localhost:8080/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/some/very/long/path"}' | jq .
# Follow the short link
curl -v http://localhost:8080/<code>
```

## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/links` | Create a short link |
| `GET` | `/{code}` | Redirect to the target URL |
| `GET` | `/links/{code}` | Get link stats (clicks) |
| `DELETE` | `/links/{code}` | Delete a link |

See [openapi.yaml](openapi.yaml) for the full schema.

## Architecture

```
 Client → LinkController → ShortenerService → JdbcLinkRepository → H2/Postgres
                         ↗ UrlValidator
                         ↗ AliasValidator
                         ↗ RateLimiter
                         ↗ CodeGenerator
          AuditFilter  → JdbcAuditRepository
```

See [docs/design.md](docs/design.md) for component and sequence diagrams.

## Building

```bash
./mvnw verify          # compile + test + static analysis + coverage
./mvnw spring-boot:run # run locally
docker build -t url-shortener .
```

## Technology choices

| Concern | Choice | Reason |
|---------|--------|--------|
| Framework | Spring Boot 3.5 | Mature, testable, well-understood |
| Database | H2 (dev), Flyway migrations | Reproducible schema, no local Postgres needed |
| Code quality | SpotBugs + FindSecBugs, PMD, JaCoCo | Catches bugs and security issues automatically |
| Coverage | 98.5% line, 100% branch (JaCoCo floor) | Every code path has a test |
