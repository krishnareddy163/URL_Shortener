---
version: 1.0.0
updated: 2026-09-29
changes:
  - "1.0.0 (2026-09-29): Initial version"
---

# Orchestration Skill

**Purpose:** Classify the incoming task, select the right skill combination, and define execution order.  
**Load this first** before any other skill.

---

## Step 1 — Classify the task

Read the user's request and assign it to one or more of the following task types.

### Primary scenario (pick one)

| Scenario | Load skill | When to use |
|---|---|---|
| **Greenfield** | `scenarios/greenfield/SKILL.md` | Building something new: no existing codebase relevant to the change |
| **Brownfield** | `scenarios/brownfield/SKILL.md` | Modifying, extending, or integrating with an existing system |
| **Bug fix** | `scenarios/bugfix/SKILL.md` | Repairing a defect in existing code |
| **Ambiguous** | `scenarios/ambiguous/SKILL.md` | Requirements are missing, contradictory, or unclear |
| **Refactoring** | `scenarios/refactoring/SKILL.md` | Improving internal quality without changing external behaviour |
| **Performance** | `scenarios/performance/SKILL.md` | Reducing latency, increasing throughput, or lowering resource use |

A task may span **two scenarios** (e.g. brownfield + bug fix, brownfield + refactoring). Load both.

---

## Step 2 — Select engineering lifecycle skills

Based on the scenario and the scope of the change, decide which lifecycle stages are needed.

| Stage | Load skill | Required for |
|---|---|---|
| **Analysis** | `engineering/analysis/SKILL.md` | Any task with non-trivial requirements or unknowns |
| **Design** | `engineering/design/SKILL.md` | New components, API changes, data model changes, architecture decisions |
| **Development** | `engineering/development/SKILL.md` | Any code changes |
| **Testing** | `engineering/testing/SKILL.md` | Any code changes |
| **Documentation** | `engineering/documentation/SKILL.md` | User-facing changes, new APIs, configuration changes, operational changes |

---

## Step 3 — Select quality skills

Always consider these three; include when relevant.

| Quality | Load skill | Include when |
|---|---|---|
| **Observability** | `quality/observability/SKILL.md` | Adding endpoints, services, background jobs, integrations, or error paths |
| **Security** | `quality/security/SKILL.md` | Accepting user input, touching auth/authz, handling secrets, network calls |
| **Performance** | `quality/performance/SKILL.md` | Hot paths, database queries, caching, large payloads, high-concurrency code |

---

## Step 4 — Build the execution plan

Output a numbered plan before proceeding. Example:

```
Task type   : Brownfield + Bug Fix
Scenario    : brownfield/SKILL.md, bugfix/SKILL.md
Engineering : analysis/SKILL.md → development/SKILL.md → testing/SKILL.md
Quality     : observability/SKILL.md

Execution order:
  1. Brownfield analysis (understand existing code, trace the defect)
  2. Bug fix analysis    (root cause, reproduction, blast radius)
  3. Development         (smallest safe fix)
  4. Testing             (regression test + existing suite green)
  5. Observability       (confirm the fix is visible in logs/metrics)
```

Do not proceed until this plan is stated explicitly. If the task is ambiguous, load
`scenarios/ambiguous/SKILL.md` first; it may change the plan before any code is written.

---

## Composition patterns

### Greenfield service
```
scenarios/greenfield → analysis → design → development → testing → security → observability → documentation
```

### Brownfield feature addition
```
scenarios/brownfield → analysis → design → development → testing → observability → documentation
```

### Bug fix (existing service)
```
scenarios/brownfield → scenarios/bugfix → development → testing → observability
```

### Refactoring
```
scenarios/brownfield → scenarios/refactoring → development → testing
```

### Performance optimisation
```
scenarios/brownfield → scenarios/performance → quality/performance → development → testing → observability
```

### Unclear requirements
```
scenarios/ambiguous → [resolve] → <appropriate pattern above>
```

---

## Validation checklist

Before declaring a task complete, verify:

- [ ] Every skill in the execution plan produced its defined outputs
- [ ] No skill was skipped without a recorded reason
- [ ] Code compiles and all tests pass
- [ ] Observability outputs (logs, metrics, traces) are present if the skill was loaded
- [ ] Documentation is updated if the documentation skill was loaded
- [ ] All documented assumptions are still valid at the end of the task
