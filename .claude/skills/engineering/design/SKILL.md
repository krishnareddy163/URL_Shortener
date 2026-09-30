---
version: 1.0.0
updated: 2026-09-29
changes:
  - "1.0.0 (2026-09-29): Initial version"
---

# Design Skill

**Purpose:** Produce an architectural and detailed design that satisfies the analysis outputs before any code is written.  
**Prerequisites:** Analysis skill outputs (requirements, constraints, acceptance criteria).  
**Outputs feed:** development skill, testing skill.

---

## When to use this skill

- Adding a new component, service, or module
- Changing the API contract (endpoints, payloads, error codes)
- Changing the data model (new table, column, index, schema migration)
- Introducing a new integration or dependency
- Making an architectural decision that affects multiple components

Skip for trivial changes that require no design decision (single-file bug fixes, renames, config changes).

---

## Inputs

- Analysis outputs (requirements, constraints, acceptance criteria)
- Existing architecture documentation and diagrams (if brownfield)
- Technology constraints from the analysis

---

## Outputs

The design document contains the following sections as applicable to the task. Not every section
is needed for every task — omit sections that are not relevant and state why.

---

### 1. High-level architecture

Describe the component(s) involved and how they interact. Use a diagram when the interaction is
non-trivial. For a simple addition to an existing service, describe where the new code fits within
the existing architecture.

### 2. Component design

For each new or significantly changed component:

- **Responsibility**: single sentence describing what it does
- **Interface**: what it exposes (methods, endpoints, events) and what it consumes
- **Dependencies**: what it calls and what calls it
- **State**: what data it owns and how it manages it
- **Failure modes**: what happens when its dependencies fail

### 3. API design

For new or changed REST/gRPC/event APIs:

```
POST /links
Request:  { "url": "https://...", "alias": "optional" }
Response: 201 { "shortUrl": "https://svc/abc123" }
Errors:   400 INVALID_URL, 409 ALIAS_TAKEN
```

- All new endpoints must be documented in OpenAPI or equivalent
- Breaking changes require a version bump or backward-compatible extension

### 4. Data model

For new or changed schemas:

- Entity-relationship description or table definition
- Migration strategy (additive first, backward-compatible)
- Index strategy (access patterns drive index choices)
- Data retention and deletion rules

### 5. Data flow

For any non-trivial data transformation, integration, or async processing:
- Source of truth for each piece of data
- Transformation steps
- Where failures are handled
- Idempotency guarantees (especially for retries and message queues)

### 6. Error handling strategy

Define the error taxonomy for this feature:
- Which errors are client errors (4xx) vs server errors (5xx)
- Which errors are retryable
- What error information is safe to expose to clients
- What gets logged vs what gets returned in the response

### 7. Security design

- Authentication and authorisation for new endpoints
- Input validation (where, what, how)
- Sensitive data (what must not be logged, stored, or transmitted in plain text)
- Threat model for new attack surfaces

### 8. Scalability and reliability

- How the component behaves under 10x expected load
- Single points of failure and how they are mitigated
- Graceful degradation when a dependency is unavailable
- Timeout and circuit breaker strategy

### 9. Design decisions and trade-offs

For each non-obvious choice, document:
- The option chosen
- The alternatives considered
- The reason for the choice
- What would cause this decision to be revisited

### 10. Open questions

Anything the design cannot resolve without more information. These must be resolved or converted
to documented assumptions before implementation begins.

---

## Design review checklist

Before passing the design to the development skill, confirm:

- [ ] All acceptance criteria from analysis are addressed by the design
- [ ] No acceptance criterion is silently out of scope
- [ ] API contracts are explicit (request/response shapes, status codes, error codes)
- [ ] Data model changes are additive or have a migration strategy
- [ ] Error handling is defined, not left to "figure out in implementation"
- [ ] Security requirements are addressed (not deferred to a later phase)
- [ ] All open questions are resolved or converted to documented assumptions
