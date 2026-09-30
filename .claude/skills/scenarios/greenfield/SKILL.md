---
version: 1.0.0
updated: 2026-09-29
changes:
  - "1.0.0 (2026-09-29): Initial version"
---

# Greenfield Scenario Skill

**Purpose:** Guide the development of a new application, service, or component built from scratch.  
**Prerequisites:** Orchestration plan is defined.  
**Next skills:** analysis → design → development → testing → security → observability → documentation

---

## When to use this skill

- No existing codebase to preserve or integrate with (for this component)
- Building a new microservice, library, CLI tool, or standalone module
- Starting a proof-of-concept that will become production code

Do **not** use this skill when there is a significant existing codebase that constraints the design.
Use `scenarios/brownfield/SKILL.md` in that case.

---

## Inputs

- User's description of the new system (functional requirements, target users, constraints)
- Technology preferences or mandates (language, framework, deployment target)
- Non-functional requirements (SLA, throughput, latency, compliance)

---

## Workflow

### Phase 1 — Foundation decisions (before writing code)

1. Confirm the **primary responsibility** of this component in one sentence.
2. Identify the **deployment target** (container, serverless, embedded, library).
3. Choose the **technology stack** and document the rationale if it was a real choice.
4. Identify the **integration points**: what does this call and what calls it?
5. Identify the **data stores** and access patterns.
6. Define the **API contract** (REST, gRPC, events, library interface) before implementation.

### Phase 2 — Structure

Set up the project structure before any business logic:

- Project layout matching the chosen framework's conventions
- Build file with only the dependencies the project actually needs
- Configuration structure (environment variables, property files, secrets handling)
- A working "hello world" build that compiles, runs, and has one passing test

This is the baseline. Every subsequent step must keep the build green.

### Phase 3 — Incremental implementation

Build in vertical slices — end-to-end thin cuts rather than horizontal layers:

1. Implement the simplest possible path through the system end-to-end
2. Confirm it works before adding the next feature
3. Add error handling, validation, and edge cases to each slice before moving to the next
4. Never leave a partially implemented feature and jump to the next

### Phase 4 — Production readiness checklist

Before declaring the greenfield implementation complete:

- [ ] All acceptance criteria from the analysis skill are met
- [ ] Input validation at every external boundary
- [ ] All error paths return structured, client-safe error responses
- [ ] Configuration is externalised (no hardcoded URLs, credentials, or magic numbers)
- [ ] Health check endpoint or equivalent liveness signal
- [ ] Structured logging on every significant state transition and error
- [ ] At least one integration test that exercises the full stack
- [ ] `README.md` documents how to build, run, and test the application

---

## Outputs

- Runnable application skeleton with working build
- API contract (OpenAPI, proto, or equivalent)
- All acceptance criteria implemented and tested
- Production-readiness checklist above completed

---

## Constraints

- Do not over-engineer for hypothetical future requirements
- Three similar lines of code are better than a premature abstraction
- The first version should do one thing well; extensibility can come later
- Dependencies: add only what the project actually uses right now
