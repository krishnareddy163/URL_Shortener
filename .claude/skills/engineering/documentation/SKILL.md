---
version: 1.0.0
updated: 2026-09-29
changes:
  - "1.0.0 (2026-09-29): Initial version"
---

# Documentation Skill

**Purpose:** Produce and maintain the documentation that enables others to understand, operate, and extend the system.  
**Prerequisites:** Development and testing skill outputs.  
**Outputs:** README, API docs, design docs, runbooks, changelogs.

---

## When to use this skill

- New endpoints, services, or components are added
- Configuration options change (new keys, changed defaults, removed options)
- Deployment or operational procedures change
- An API contract changes (especially breaking changes)
- A bug fix changes user-observable behaviour

Skip for pure internal refactoring that does not change any external interface, configuration,
or operational procedure.

---

## Documentation types and when to produce each

### README

**Always update when:**
- Build or run instructions change
- New environment variables or configuration keys are required
- New dependencies are needed to develop or run the service
- The project's purpose, structure, or entry points change

**README must include:**
- What the service does (one paragraph)
- How to build and run it locally (exact commands, not "just run Maven")
- How to run the tests
- Key configuration properties and their defaults
- How to verify it is working (a curl example, a test URL, or a smoke test)

### API documentation

**Always update when** an endpoint is added, changed, or removed.

- Use OpenAPI 3.x or the project's existing API documentation format
- Every endpoint must document: HTTP method, path, request body, response body, all status codes,
  all error codes, authentication requirements
- Breaking changes must be flagged with the version they affect
- Provide request/response examples for each endpoint

### Architecture / design document

**Produce when:**
- A new component is introduced
- A significant architectural decision is made
- A complex integration is added

**Contents:**
- Problem being solved and why this approach was chosen
- Component diagram (textual or drawn)
- Data flow
- Key design decisions and the alternatives that were considered
- Known limitations and deferred work

### Configuration documentation

**Produce when** new configuration properties are added.

For each property:

| Property | Type | Default | Description |
|---|---|---|---|
| `shortener.base-url` | String | `""` | Base URL prepended to generated short codes. Empty = use request Host header. |
| `shortener.rate-limit.requests-per-minute` | Integer | `20` | Max requests per client per minute. |

### Runbook / operations guide

**Produce when** the service has operational complexity: health checks, restart procedures, common
failure modes, scaling steps, or monitoring setup.

**Contents:**
- Health check URL and expected response
- How to restart safely (graceful shutdown, drain time)
- Common failure modes and how to diagnose them (log patterns, metrics to check)
- How to scale (horizontal vs vertical, stateless vs stateful)
- How to roll back a deployment

### Troubleshooting guide

**Produce when** the service has known failure modes that operators will encounter.

Format each entry as:
```
## Symptom: [what the operator sees]
**Cause:** [what is actually happening]
**Diagnosis:** [what to check — log pattern, metric, query]
**Resolution:** [what to do]
```

### Changelog / release notes

**Always produce when** a change will be deployed. Format:

```markdown
## [version] — [date]

### Added
- Description of new features

### Changed
- Description of changed behaviour (note if breaking)

### Fixed
- Description of bugs fixed

### Removed
- Description of removed features (note migration path)
```

---

## Documentation quality rules

- Documentation describes **what** and **why**, not **how** (the code shows how)
- Every curl or code example must be runnable as-is, not pseudocode
- Configuration examples must use realistic values, not `<your-value-here>`
- Never say "refer to source code for details" in user-facing documentation
- Keep documentation co-located with code so it gets updated when code changes

---

## Outputs

- Updated README (if applicable)
- Updated API documentation / OpenAPI spec (if applicable)
- Design document for new components (if applicable)
- Configuration reference for new properties (if applicable)
- Runbook updated for operational changes (if applicable)
- Changelog entry for this release
