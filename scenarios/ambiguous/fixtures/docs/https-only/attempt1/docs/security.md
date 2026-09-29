# Security

## Destination policy

Only `https` destinations can be shortened. `POST /api/v1/links` with an `http` URL returns:

```json
{"error":{"code":"INVALID_URL","message":"URL scheme must be https"}}
```

Links created before this change keep working.

## Analytics

Statistics are aggregates only: total clicks, last access, and per-UTC-day counts. No IPs, referrers or user agents are stored.
