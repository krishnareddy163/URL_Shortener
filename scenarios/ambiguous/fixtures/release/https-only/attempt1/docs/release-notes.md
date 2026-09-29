# Release Notes

## 1.1.0

- Security: only `https` destination URLs are accepted (`400 INVALID_URL` for `http`). Existing links keep working.

## 1.0.0

- `POST /api/v1/links`: create a short link with an optional custom alias. Creation is idempotent on the normalized URL.
- `GET /{code}`: `302` redirect. Clicks are recorded asynchronously.
- `GET /api/v1/links/{code}/stats`: total clicks, last access, and per-UTC-day buckets.
- Validation rejects non-http(s) schemes, embedded credentials, local host names and non-public literal IPs.
- Rate limit: 20 creates per minute per client.

Known limitations: DNS rebinding is not prevented, and the rate limiter and click queue are per instance.
