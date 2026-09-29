# Agentic Orchestration — URL Shortener SDLC

## Overview

This document describes the agentic orchestration model used to execute the full Software Development Lifecycle (SDLC) for the URL Shortener service. The orchestrator coordinates six specialist agents across eight stages, enforces entry/exit gates between stages, supports parallel execution where dependencies allow, preserves cross-stage context, and enforces human approval checkpoints for high-impact actions.

---

## Orchestration Principles

| Principle | Implementation |
|-----------|---------------|
| **Non-linear execution** | Stages with no mutual dependency run in parallel (see DAG below) |
| **Stateful context** | Each agent receives the full outputs of its upstream dependencies as context |
| **Entry/exit gates** | Each stage defines preconditions (entry) and quality checks (exit) before downstream stages unlock |
| **Human approval checkpoints** | Schema changes, dependency upgrades, and release tagging require explicit human sign-off |
| **Bounded retries** | Each agent task retries up to 3 times with exponential back-off; a persistent failure triggers the fallback path |
| **Rollback** | Failed deployments trigger an automated rollback to the last known-good image tag |
| **Safe-stop** | Any agent can emit a `HALT` signal that pauses the pipeline and alerts the human operator |
| **Policy guardrails** | Security, compliance, and change-control rules are checked before and after every agent action |

---

## Agent Roster

| Agent | Role | Autonomy Level |
|-------|------|---------------|
| **Requirements Agent** | Interpret requirements, generate user stories and acceptance criteria | High — executes without approval |
| **Design Agent** | Produce architecture, data model, sequence diagrams, key decisions | High — executes without approval |
| **Development Agent** | Implement code, error handling, logging, audit trail | High — executes without approval |
| **Review Agent** | Static analysis, security scan, code review | High — executes without approval |
| **QA Agent** | Unit + integration tests, coverage enforcement, functional scenarios | High — executes without approval |
| **Release Agent** | Changelog, Docker image, Git tag, deployment readiness | **Low — requires human approval gate** |

---

## Dependency Graph (DAG)

```
┌─────────────────────────────────────────────────────────────────┐
│                    REQUIREMENTS AGENT                           │
│  Entry: raw requirement text                                    │
│  Exit: user stories + acceptance criteria reviewed by human ✓   │
└────────────────────────┬────────────────────────────────────────┘
                         │
            ┌────────────▼────────────┐
            │      DESIGN AGENT       │
            │  Entry: AC doc          │
            │  Exit: design.md +      │
            │  openapi.yaml approved  │
            └──────┬──────────────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
┌───────▼────────┐   ┌────────▼────────┐
│ DEVELOPMENT    │   │   QA AGENT      │
│ AGENT          │   │  (test design   │
│ Entry: design  │   │   phase only)   │
│ Exit: all      │   │ Entry: design   │
│ compiler warns │   │ Exit: test plan │
│ = 0, no TODOs  │   │ approved        │
└───────┬────────┘   └────────┬────────┘
        │                     │
        └──────────┬──────────┘
                   │ (synchronisation point — both must pass)
        ┌──────────▼──────────┐
        │    REVIEW AGENT     │
        │  Entry: source +    │
        │  test code          │
        │  Exit: 0 blockers,  │
        │  ≤3 warnings        │
        └──────────┬──────────┘
                   │
        ┌──────────▼──────────┐
        │     QA AGENT        │
        │  (execution phase)  │
        │  Entry: reviewed    │
        │  source             │
        │  Exit: 98.5% line   │
        │  100% branch,       │
        │  0 test failures    │
        └──────────┬──────────┘
                   │
                   │  ⚠ HUMAN APPROVAL CHECKPOINT
                   │  Operator reviews coverage report,
                   │  release notes, and security scan
                   │  before Release Agent unlocks
                   ▼
        ┌──────────────────────┐
        │    RELEASE AGENT     │
        │  Entry: human sign-  │
        │  off + green CI      │
        │  Exit: Docker image  │
        │  tagged + changelog  │
        │  published           │
        └──────────────────────┘
```

---

## Stage Specifications

### Stage 1 — Requirements Agent

**Entry gate:** Raw requirements text provided  
**Outputs:** `docs/requirements-agent.md` — epics, user stories, acceptance criteria, ambiguity log  
**Exit gate:** Human reviews and approves the user stories before Design Agent starts  
**Retry:** Up to 3 regenerations if the output is missing epics or acceptance criteria  
**Policy guardrail:** Requirements must not include PII-handling, payment processing, or OAuth flows without explicit compliance review flag

---

### Stage 2 — Design Agent

**Entry gate:** Approved requirements doc  
**Outputs:** `docs/design.md` (components, data model, key decisions, risks), `openapi.yaml`, Mermaid diagrams  
**Exit gate:** OpenAPI spec is valid (run `spectral lint`); design doc covers all acceptance criteria  
**Retry:** Up to 3 iterations if spec is invalid  
**Human approval checkpoint:** Any database schema change or new external dependency requires human sign-off before development begins  
**Policy guardrail:** Design must include a security risk section; SSRF mitigations must be documented

---

### Stage 3a — Development Agent (parallel with 3b)

**Entry gate:** Approved design doc and OpenAPI spec  
**Outputs:** All production source files under `src/main/`; Flyway migration; Dockerfile  
**Exit gate:**
- `./mvnw compile` with `-Werror` produces zero warnings
- No `TODO` / `FIXME` markers in committed code
- SpotBugs + PMD pass with zero violations

**Retry:** Individual file edits retry on compiler error; after 3 total failures the Development Agent emits `HALT`  
**Policy guardrail:**
- Raw IPs/PII must never appear in log statements (enforced by `LogSanitizer`)
- All DB access through the repository interface (no ad-hoc SQL in controllers)
- No hardcoded credentials

---

### Stage 3b — QA Agent: Test Design (parallel with 3a)

**Entry gate:** Approved design doc  
**Outputs:** Test plan document, test class stubs  
**Exit gate:** Test plan covers all acceptance criteria (traceability matrix)  
**Policy guardrail:** Test plan must include at least one negative/failure scenario per user story

---

### Stage 4 — Review Agent (synchronisation point)

**Entry gate:** Both Stage 3a and 3b outputs complete and their exit gates passed  
**Outputs:** `docs/code-review-agent.md` — findings list with severity, resolution, sign-off  
**Exit gate:** Zero BLOCKER findings; CRITICAL findings resolved or formally accepted with justification  
**Retry:** If new findings appear after a fix, the review re-runs (bounded at 3 full cycles)  
**Policy guardrail:**
- FindSecBugs must complete with zero HIGH-severity security findings
- No Spring `@SuppressWarnings("unchecked")` without documented justification
- Dependency versions must match the approved SBOM

---

### Stage 5 — QA Agent: Execution

**Entry gate:** Review Agent sign-off  
**Outputs:** `docs/qa-agent.md` — test results, coverage report, functional scenario results  
**Exit gate:**
- Zero test failures
- JaCoCo: ≥ 98.5% line coverage, 100% branch coverage
- All functional scenarios (in `docs/scenarios.md`) pass end-to-end

**Retry:** A single test failure triggers a targeted fix loop in the Development Agent (up to 3 iterations) before the QA Agent re-runs  
**Rollback trigger:** If coverage drops below the floor after a fix attempt, the change is reverted and the pipeline halts for human review

---

### Stage 6 — Release Agent

**Entry gate:** Human approval checkpoint + green CI on `main`  
**Outputs:** `docs/release-notes.md`, Docker image tagged with version, `git tag vX.Y.Z`  
**Exit gate:** Docker image runs locally and passes smoke test (`POST /links` + `GET /{code}`)  
**Human approval checkpoint:** Release Agent proposes the version bump and changelog; human confirms before the tag is pushed  
**Rollback:** If the smoke test fails, the image tag is removed and the previous release tag is restored  
**Policy guardrail:** Release notes must reference all closed user stories; no release without a passing SBOM scan

---

## Cross-Stage Context Preservation

Each agent receives a **context bundle** consisting of:

| Context item | Produced by | Consumed by |
|---|---|---|
| Approved requirements doc | Requirements Agent | Design Agent, QA Agent (test design) |
| OpenAPI spec + design doc | Design Agent | Development Agent, Review Agent, QA Agent |
| Source code | Development Agent | Review Agent, QA Agent (execution) |
| Test plan | QA Agent (design) | QA Agent (execution), Review Agent |
| Review findings + resolutions | Review Agent | QA Agent (execution), Release Agent |
| Coverage report | QA Agent (execution) | Release Agent |
| Human approval record | Human | Release Agent entry gate |

Decision lineage is recorded in each agent output document with a `## Agent decisions` section listing what was chosen, what was rejected, and why.

---

## Reliability Metrics

The orchestrator tracks the following metrics per pipeline run:

| Metric | Target | Measurement |
|--------|--------|-------------|
| Stage success rate | > 95% | Passed exit gates / total stage runs |
| Retry frequency | < 1 retry per stage per run | Retry count logged per stage |
| Rollback frequency | < 5% of releases | Rollback events / total release attempts |
| MTTR (mean time to recover) | < 30 min from HALT to resume | Time between HALT signal and human approval |
| End-to-end pipeline latency | < 4 hours | Timestamp delta: requirements received → release tagged |
| Human checkpoint wait time | Tracked (not targeted) | Logged for process improvement |

---

## Policy Guardrails Summary

| Category | Guardrail | Enforcement point |
|----------|-----------|-------------------|
| Security | No raw IPs or URLs in logs | Development Agent exit gate + Review Agent |
| Security | FindSecBugs zero HIGH findings | Review Agent exit gate |
| Security | SSRF mitigations documented | Design Agent exit gate |
| Compliance | Audit trail covers all mutations | QA Agent functional scenarios |
| Change control | Schema changes need human approval | Design Agent → Development Agent gate |
| Change control | Release tag needs human approval | QA Agent → Release Agent gate |
| Dependency | All deps in approved SBOM | Release Agent exit gate |
| Code quality | Zero compiler warnings | Development Agent exit gate |
| Code quality | Zero PMD / SpotBugs violations | Review Agent exit gate |

---

## Failure Handling

```
Agent task fails
       │
       ├─ attempt ≤ 3 → retry with exponential back-off (2s, 4s, 8s)
       │
       ├─ attempt > 3, fallback defined → run fallback path
       │    (e.g. fall back to manual implementation hint)
       │
       └─ attempt > 3, no fallback → emit HALT signal
              │
              ├─ notify human operator with full context bundle
              ├─ pipeline pauses (no downstream stages execute)
              └─ human resolves and resumes, or triggers rollback
```

---

## Dynamic Re-planning

If an upstream agent's output changes after a downstream stage has already started (e.g. a design revision during development), the orchestrator:

1. Suspends in-progress downstream tasks
2. Diffs the changed output against the prior version
3. Identifies which downstream stages are affected by the diff
4. Re-runs only the affected stages (partial re-plan)
5. Records the re-plan event in the audit log with the change summary

This prevents full pipeline restarts for minor upstream corrections while maintaining traceability.
