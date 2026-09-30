---
version: 1.0.0
updated: 2026-09-29
changes:
  - "1.0.0 (2026-09-29): Initial version"
---

# Analysis Skill

**Purpose:** Transform a request into a structured set of requirements, constraints, risks, and acceptance criteria.  
**Prerequisites:** Task classified by orchestration skill.  
**Outputs feed:** design skill, development skill, testing skill.

---

## When to use this skill

- The task has non-trivial requirements (more than a single-line change)
- There are unknowns about scope, constraints, or integrations
- The task touches a domain with rules (finance, identity, compliance, SLAs)

Skip this skill only for trivial changes where the requirement is completely unambiguous (e.g.
"rename this variable"). If in doubt, do a lightweight version of this skill.

---

## Inputs

- User's request
- Existing codebase, design documents, or API specs (if brownfield)
- Non-functional requirements stated by the user

---

## Outputs

Produce a structured analysis document with the following sections.

---

### 1. Problem statement

One paragraph: what problem is being solved and for whom. Not a restatement of the request — a
description of the underlying need.

### 2. Functional requirements

Numbered list of what the system must do. Use "shall" for mandatory, "should" for preferred.

```
FR-1: The system shall accept a URL and return a shortened alias.
FR-2: The system shall redirect a short alias to the original URL.
FR-3: The system should record click events for analytics.
```

### 3. Non-functional requirements

| Attribute | Requirement | Measurement |
|---|---|---|
| Latency | p99 redirect < 50ms | Under 1000 concurrent users |
| Availability | 99.9% uptime | 30-day rolling window |
| Security | No open redirects | Validated in test |

If the user did not specify, note "not specified" and propose a reasonable default.

### 4. Constraints

Things the design cannot change:
- Technology mandates (must use Java 21, must use PostgreSQL)
- Existing API contracts (cannot break callers of v1 endpoints)
- Compliance requirements (GDPR, PCI, SOC 2)
- Budget or timeline constraints

### 5. Assumptions

Things assumed to be true that are not explicitly stated. Each assumption increases risk.
Flag assumptions that, if wrong, would require rework. Format:

```
A-1: [assumption] — risk: [what breaks if wrong]
```

### 6. Out of scope

Explicit list of things that are not part of this task. Prevents scope creep and documents
decisions for future work.

### 7. Existing system analysis (brownfield only)

- Entry points relevant to this change
- Current data model and how it will be affected
- External dependencies and their contracts
- Existing test coverage for the affected area
- Known technical debt in the area

### 8. Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Third-party API rate limit | Medium | High | Implement retry with backoff |
| DB migration irreversibility | Low | Critical | Test rollback procedure |

### 9. Acceptance criteria

Numbered, testable criteria. Each one maps to at least one test case.

```
AC-1: Given a valid URL, when shortened, the redirect returns 301 to the original URL.
AC-2: Given an invalid URL, the API returns HTTP 400 with error code INVALID_URL.
AC-3: Given a non-existent short code, the API returns HTTP 404 with error code LINK_NOT_FOUND.
```

---

## Lightweight version (for small tasks)

For a simple, well-understood change, produce only:
- One sentence problem statement
- 2-5 acceptance criteria
- Any assumptions made

Do not produce a full analysis document for a three-line change.
