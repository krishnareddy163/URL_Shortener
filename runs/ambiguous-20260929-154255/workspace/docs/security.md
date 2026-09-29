# Security

## Destination blocklist

Operators can block destination domains with `shortener.blocklist.domains`, a comma-separated list. A listed domain also blocks its subdomains. Creating a link to a blocked host returns:

```json
{"error":{"code":"INVALID_URL","message":"URL host is blocklisted"}}
```

The default configuration blocks `malware.test` and `phishing.test`, which are reserved example domains. Changing the list requires a restart.

## Analytics

Statistics are aggregates only: total clicks, last access, and per-UTC-day counts. No IPs, referrers or user agents are stored.
