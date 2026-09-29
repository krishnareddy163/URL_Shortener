# Agentic SDLC — URL Shortener

A governed, event-sourced SDLC orchestration engine that coordinates AI agents across the full software development lifecycle, using a URL shortener service as its example workload.

## Quick start

```bash
# Build everything (shortener service + orchestrator)
make build

# Run all four demo scenarios (greenfield, brownfield, ambiguous, bugfix)
make demo-all

# Interactive live run (needs ANTHROPIC_API_KEY and ANTHROPIC_MODEL)
ANTHROPIC_API_KEY=... ANTHROPIC_MODEL=claude-opus-4-5 make live

# Run the shortener service directly
mvn -f shortener-service/pom.xml spring-boot:run
```

## What this is

The repository demonstrates **agentic SDLC orchestration**: six AI agents (requirements, architect, developer, tester, reviewer, docs) execute across a dependency graph with entry/exit gates, human approval checkpoints, bounded retries, rollback, policy guardrails, and audit-grade traceability. The URL shortener is both the output the agents produce and the test workload that proves the gates work.

## Structure

```
orchestrator/          Real orchestration engine (Java 25, ~140 source files)
  core/engine/         Scheduler, NodeRunner, retries, safe-stop, circuit breaker
  core/gate/           18 quality gates (Maven build, coverage, review, secrets, ...)
  core/policy/         Policy guardrails (no raw IPs, forbidden APIs, secret scan)
  core/state/          Append-only SQLite event store; RunState is a fold over events
  core/graph/          Workflow DAG loader and validator
  agents/live/         Live Claude API agents (requires ANTHROPIC_API_KEY)
  cli/                 Picocli CLI: run / approve / reject / answer / status / report

shortener-service/     URL shortener (Spring Boot 3.5, Java 25)
  src/main/java/       REST API, service, JDBC storage, rate limiter, audit filter
  src/test/java/       135 unit and integration tests; JaCoCo 100% line / 100% branch
  openapi.yaml         OpenAPI 3.0 spec

scenarios/             Workflow YAML definitions
  greenfield/          Build from scratch (9-node graph, 3 human approval checkpoints)
  brownfield/          Extend running service (impact analysis, graph patch)
  ambiguous/           Interpret vague requirement (clarification flow, dynamic re-plan)
  bugfix/              Test-first bug fix with rollback and release

policies/              Policy configuration (path allowlist, dependency allowlist, ...)
scripts/               Demo, lint, quality-scan, coverage-report, export-artifacts
docker/                Container entrypoint
runs/                  Sample run outputs (event logs, gate results, reports)
docs/                  Architecture, decisions, testing, AI SDLC artifacts
```

## API (shortener service)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/links` | Create a short link (idempotent on normalized URL) |
| `GET` | `/{code}` | Redirect to the target URL (302) |
| `GET` | `/api/v1/links/{code}/stats` | Click statistics (total, last access, per-day) |

See [shortener-service/openapi.yaml](shortener-service/openapi.yaml) for the full schema.

## Building

```bash
make build             # shortener service + orchestrator (about 4-5 min first run)
make test              # both test suites
make demo-greenfield   # build the shortener from scratch with mock agents
make demo-brownfield   # add link expiry to the existing service
make demo-ambiguous    # handle "make links more secure" (vague requirement)
make demo-bugfix       # fix a real SSRF bypass test-first

# Shortener service only
mvn -f shortener-service/pom.xml verify
mvn -f shortener-service/pom.xml spring-boot:run
curl -s -X POST http://localhost:8080/api/v1/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/some/very/long/path"}' | jq .

# Docker (full system)
docker build -t agentic-sdlc .
docker run --rm -it -v "$PWD/runs:/app/runs" agentic-sdlc demo-all
```

## Orchestration model

See [docs/architecture.md](docs/architecture.md) and [docs/orchestration.md](docs/orchestration.md) for the full design including:
- Agent dependency DAG with entry/exit gates
- Human approval checkpoints (APPROVE_AFTER, ESCALATE_ON_RISK autonomy levels)
- Bounded retries (3 attempts, exponential back-off), circuit breaker, safe-stop
- Rollback via staging directories and pre-image records
- SQLite event sourcing — every state transition is an immutable event
- Dynamic re-planning when upstream outputs change

## SDLC Artifacts

| Artifact | Description |
|----------|-------------|
| [docs/requirements-agent.md](docs/requirements-agent.md) | User stories, acceptance criteria, ambiguity log |
| [docs/ai-sdlc/user-stories.md](docs/ai-sdlc/user-stories.md) | User stories exported from real agent runs |
| [docs/architecture.md](docs/architecture.md) | System architecture with component diagram |
| [docs/orchestration.md](docs/orchestration.md) | Orchestration DAG, gates, governance model |
| [docs/decisions.md](docs/decisions.md) | Architecture Decision Records (12 ADRs) |
| [docs/scenarios.md](docs/scenarios.md) | Scenario walkthroughs (greenfield, brownfield, ambiguous) |
| [docs/ai-sdlc/code-review.md](docs/ai-sdlc/code-review.md) | Code review results from real agent runs |
| [docs/code-review-agent.md](docs/code-review-agent.md) | Manual review: all findings, resolutions, sign-off |
| [docs/qa-agent.md](docs/qa-agent.md) | 135 tests, JaCoCo coverage, functional traceability matrix |
| [docs/ai-sdlc/functional-coverage.md](docs/ai-sdlc/functional-coverage.md) | Functional coverage from real agent runs |
| [docs/engineering-summary.md](docs/engineering-summary.md) | Service-level engineering summary |
| [docs/system-engineering-summary.md](docs/system-engineering-summary.md) | System-level summary (orchestrator + service) |
| [docs/testing.md](docs/testing.md) | Testing approach, coverage gaps, limitations |
| [shortener-service/docs/design.md](shortener-service/docs/design.md) | Shortener service design, diagrams, key decisions |
| [shortener-service/docs/operations.md](shortener-service/docs/operations.md) | Operations runbook |
| [docs/sample-runs/](docs/sample-runs/) | Reports from real orchestrator runs |

## Technology choices

| Concern | Choice | Reason |
|---------|--------|--------|
| Orchestrator | Java 25, no Spring | Minimal dependencies; records and pattern matching suit the domain |
| State | SQLite event log | Audit-grade, resumable, single file, no external process |
| Workflow | YAML DAG | Human-readable, diffable, version-controlled |
| Agents | Claude API (Anthropic) | Live agents; fixture-driven mock agents for deterministic tests |
| Shortener framework | Spring Boot 3.5 | Mature, testable, well-understood for REST + JDBC |
| Code quality | SpotBugs, PMD, JaCoCo, ArchUnit | Full static analysis and coverage enforcement in CI |
