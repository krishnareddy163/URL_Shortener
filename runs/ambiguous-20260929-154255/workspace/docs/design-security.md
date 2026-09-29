# Security hardening: destination domain blocklist

**Decision (from q-secure = domain-blocklist):** operators can block destination domains, for example known phishing or malware hosts.

- New `DomainBlocklist` component. It is configured by `shortener.blocklist.domains` (comma-separated) and matches a host exactly or any subdomain of a listed domain, case-insensitively.
- `UrlValidator` consults the blocklist after the v1 checks. A blocked host returns `400 INVALID_URL` ("URL host is blocklisted").
- The `http` and `https` schemes stay allowed, so v1 behavior is unchanged for hosts that are not blocked.

```mermaid
flowchart LR
  req[POST /api/v1/links] --> v1[v1 checks: http/https,<br/>public host, no credentials, length]
  v1 -- fail --> reject[400 INVALID_URL]
  v1 -- pass --> block{host or a parent domain<br/>in shortener.blocklist.domains?}
  block -- yes --> blocked[400 INVALID_URL<br/>URL host is blocklisted]
  block -- no --> create[create link]
```

**Trade-offs:** a static list needs a restart to change, and there is no reputation feed. Both are acceptable for the prototype; a feed would replace the property source later.
