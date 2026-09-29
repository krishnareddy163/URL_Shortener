# Release Notes

## 1.1.0

- Links can expire: optional `expiresAt` on create; expired redirects return `410 LINK_EXPIRED`; links without expiry behave exactly as in 1.0.0.
- Migration `V2__add_expiry.sql` adds a nullable `expires_at` column (no backfill).

## 1.0.0

- `POST /api/v1/links`: create a short link with an optional custom alias. Creation is idempotent on the normalized URL.
- `GET /{code}`: `302` redirect. Clicks are recorded asynchronously.
- `GET /api/v1/links/{code}/stats`: total clicks, last access, and per-UTC-day buckets.
- Validation rejects non-http(s) schemes, embedded credentials, local host names and non-public literal IPs.
- Rate limit: 20 creates per minute per client.

Known limitations: DNS rebinding is not prevented, and the rate limiter and click queue are per instance.
