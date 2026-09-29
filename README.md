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

## Running and testing scenarios

### What each scenario demonstrates

| Scenario | Requirement | Key mechanics |
|----------|-------------|---------------|
| **greenfield** | Build the URL shortener from an empty workspace | 9-node dependency graph, 3 human approvals, parallel wave execution, a real failing unit test rolled back and retried on attempt 2 |
| **brownfield** | Add link expiry to the running service | Starts from the existing codebase (`copy:shortener-service`), impact analysis, risk-triggered escalation, regression gate guards existing behavior |
| **ambiguous** | "Make links more secure and add some analytics" — intentionally vague | Clarification flow pauses the run for a human answer, then a dynamic graph patch re-plans the remaining nodes around the answer |
| **bugfix** | SSRF bypass: short links accept `http://localhost./admin` | Test-first fix (defect-reproduction gate must fail before the fix, then pass after), rollback on a bad patch, release approval |

Each scenario has a `workflow.yaml` that is the complete plan as data, and a `fixtures/` tree of what each mock agent proposes on each attempt (real code and real tests that the gates compile and run).

### Run a demo (mock agents, no API key)

Demo scripts drive the CLI automatically — they start the run, show the pending approval, approve it, resume, and repeat until done. Output lands in `runs/<scenario>-<timestamp>/`.

```bash
make demo-greenfield    # ~3-5 min; runs real Maven gates inside each wave
make demo-brownfield
make demo-ambiguous
make demo-bugfix
make demo-all           # all four in sequence
```

After a demo you can inspect the run:

```bash
# Human-readable Markdown report (timeline, metrics, gate results, decision lineage)
cat runs/greenfield-*/report.md

# Every event in order (JSON)
cat runs/greenfield-*/events.db   # SQLite; query with: sqlite3 runs/greenfield-*/events.db "select * from events"

# Gate build logs (Maven output for compile, test, coverage gates)
ls runs/greenfield-*/gate-logs/
```

### Run the scenario tests (automated, in CI)

The scenario tests are JUnit integration tests in the orchestrator module. They use the same mock agents and real gates as the demo scripts, so they actually compile and run the generated code.

```bash
# Run all tests (shortener service + orchestrator unit tests + all scenario integration tests)
make test

# Orchestrator tests only (unit tests + scenario integration tests)
mvn -pl orchestrator verify

# One scenario test class
mvn -pl orchestrator -Dtest=GreenfieldScenarioTest test
mvn -pl orchestrator -Dtest=BrownfieldScenarioTest test
mvn -pl orchestrator -Dtest=AmbiguousScenarioTest test
mvn -pl orchestrator -Dtest=BugfixScenarioTest test

# Resilience tests (budget enforcement, circuit breaker, safe-stop, rollback)
mvn -pl orchestrator -Dtest=ResilienceScenarioTest test
```

The scenario tests run concurrently by default (`@Execution(ExecutionMode.CONCURRENT)`). Each test creates a fresh temporary workspace and run directory, so they can all run in parallel safely.

If a scenario test fails with `SAFE_STOPPED` when `PAUSED` is expected, the test harness copies `incident.md` and all gate logs to `orchestrator/target/scenario-incidents/` for diagnosis.

### Run a live scenario (real Claude model)

A live run calls the Claude API for every agent call. You are the human reviewer at every approval checkpoint: the CLI prints the diff, waits for your input, and continues.

```bash
# Prerequisites: ANTHROPIC_API_KEY and ANTHROPIC_MODEL must be set
export ANTHROPIC_API_KEY=sk-ant-...
export ANTHROPIC_MODEL=claude-opus-4-5

# Greenfield (default)
make live

# Any scenario
make live WORKFLOW=scenarios/brownfield/workflow.yaml
make live WORKFLOW=scenarios/bugfix/workflow.yaml

# Or drive the CLI directly after building the jar
java -jar orchestrator/target/orchestrator.jar run scenarios/greenfield/workflow.yaml \
  --run-id my-run --mode LIVE
java -jar orchestrator/target/orchestrator.jar pending my-run
java -jar orchestrator/target/orchestrator.jar approve my-run design \
  --by alice --comment "Looks good"
java -jar orchestrator/target/orchestrator.jar resume my-run
java -jar orchestrator/target/orchestrator.jar report my-run
```

### Add or modify a scenario

```
scenarios/<name>/
  workflow.yaml                        # nodes, dependencies, gates, autonomy, budgets
  fixtures/<node>/attempt<N>/          # what the mock agent proposes on attempt N
    proposal.json                      # { rationale, derivedFrom, data }
    src/...  or  docs/...              # files to be promoted into the workspace
  fixtures/<node>/attempt<N>/          # attempt N+1 for retry cases
  README.md                            # short description and walkthrough link
```

To add a new attempt (e.g. to simulate a gate failure and retry), create `fixtures/<node>/attempt2/` alongside `attempt1/`. The mock agent picks the directory that matches the current attempt counter. A missing attempt directory counts as a failed agent call, which triggers a retry or safe-stop depending on `maxRetries`.

To run the scenario test for a new scenario, create a test class in `orchestrator/src/test/java/com/example/agentic/scenarios/` following the pattern in `GreenfieldScenarioTest`. Each `assertThat(run.approveAndResume(...))` call corresponds to one human approval checkpoint.

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
