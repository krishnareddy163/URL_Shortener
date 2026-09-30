# Security Skill

**Purpose:** Identify and mitigate security risks in design and implementation.  
**Prerequisites:** Design outputs (API contract, data model). Development skill in progress or complete.  
**Companion skills:** `engineering/design/SKILL.md` (security design section)

---

## When to use this skill

- Any endpoint that accepts user input
- Any endpoint that touches authentication, authorisation, or sessions
- Any code that handles secrets, credentials, or tokens
- Any new integration with an external system
- Any code that writes user data to a database, log, or response
- Any file upload, download, or URL redirect

---

## OWASP Top 10 checklist

Work through each category for the code being reviewed or written.

### A01 — Broken Access Control

- [ ] Every endpoint checks authorisation before executing business logic
- [ ] Authorisation is based on verified server-side state, never a client-supplied claim
- [ ] Resource IDs in URLs cannot be used to access another user's data (IDOR)
- [ ] Admin and internal endpoints are not accessible to anonymous or regular users
- [ ] Directory traversal is not possible in file paths or resource names

### A02 — Cryptographic Failures

- [ ] Passwords are hashed with bcrypt/argon2/scrypt (never MD5, SHA-1, or plain SHA-256)
- [ ] Tokens and secrets are generated with a cryptographically secure random source
- [ ] Sensitive data is encrypted at rest (PII, payment data, health data)
- [ ] TLS is enforced for all external connections; certificates are validated
- [ ] No sensitive data in URLs (query parameters are logged by load balancers)

### A03 — Injection

- [ ] All database queries use parameterised statements or an ORM; no string concatenation into SQL
- [ ] All log messages use parameterised logging (`log.info("id={}", id)`, not `"id=" + id`)
- [ ] HTML output is escaped to prevent XSS (use templating engine escaping, not manual)
- [ ] Shell commands are not constructed from user input
- [ ] XML/JSON parsing uses a safe parser with entity expansion disabled

### A04 — Insecure Design

- [ ] Threat model is documented for new attack surfaces
- [ ] Rate limiting is applied to endpoints that can be abused (auth, account creation, expensive operations)
- [ ] Business logic does not rely on client-supplied state for security decisions

### A05 — Security Misconfiguration

- [ ] Debug endpoints, stack traces, and internal error details are not exposed in production
- [ ] Default credentials are changed or disabled
- [ ] Unnecessary features, services, and ports are disabled
- [ ] HTTP security headers are set: `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`

### A06 — Vulnerable Components

- [ ] New dependencies are from well-maintained projects with recent releases
- [ ] Dependency versions are pinned (no unbound `LATEST` or `+`)
- [ ] A software bill of materials (SBOM) is generated for the build

### A07 — Authentication Failures

- [ ] Authentication tokens expire and can be revoked
- [ ] Failed authentication attempts are logged (without logging the submitted credential)
- [ ] Account lockout or rate limiting is applied to login endpoints

### A08 — Software and Data Integrity

- [ ] Serialisation does not deserialise untrusted data into arbitrary objects
- [ ] Signed artefacts are verified before use (JARs, container images)

### A09 — Logging and Monitoring Failures

- [ ] Security events are logged: failed auth, access denied, suspicious input
- [ ] Logs cannot be tampered with by the application (write-only log sink)
- [ ] Sensitive data (passwords, tokens, PII) is never written to logs

### A10 — Server-Side Request Forgery (SSRF)

- [ ] User-supplied URLs are validated against an allowlist before the server fetches them
- [ ] Internal network addresses (169.254.x.x, 10.x.x.x, localhost) are blocked in user-supplied URLs

---

## Input validation rules

Every value that enters the system from outside must be validated:

| Input type | Validation |
|---|---|
| URL | Protocol allowlist (https, http only); length limit; no private IP ranges |
| String identifier | Regex allowlist of safe characters; length min/max |
| Integer/number | Range check; reject NaN and Infinity |
| Enum / fixed set | Exact match against the allowed set; reject everything else |
| File upload | MIME type check; file size limit; scan for malicious content |
| Redirect target | Absolute URL on an allowlisted domain only |

**Validation location:** at the system boundary (controller/handler), not buried inside service logic.

---

## Secret management

- Secrets live in environment variables or a secret manager (Vault, AWS SSM, Kubernetes Secrets)
- No secrets in source code, configuration files checked into git, or log files
- Rotate secrets without redeploying: the application reads them at runtime, not at build time
- Audit access to secrets: who can read the production database password?

---

## HTTP response splitting prevention

When echoing user-supplied values into response headers:
- Validate the value against a strict allowlist pattern before `setHeader()`
- Reject any value containing `\r`, `\n`, or null bytes
- Use a whitelist, not a blacklist (you will miss edge cases with a blacklist)

---

## Security review outputs

After applying this skill, document:

1. **Findings**: any security issue found, its severity (CRITICAL / HIGH / MEDIUM / LOW), and its status (FIXED / DEFERRED / ACCEPTED)
2. **Mitigations implemented**: what was changed and why
3. **Accepted risks**: issues that were reviewed and accepted with documented rationale
4. **Pending items**: items that require a follow-up action (e.g. "add CSP header in the reverse proxy — tracked as issue #42")
