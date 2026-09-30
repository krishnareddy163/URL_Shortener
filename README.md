# Agentic SDLC Orchestrator

[![CI](https://github.com/krishnareddy163/URL_Shortener/actions/workflows/ci.yml/badge.svg)](https://github.com/krishnareddy163/URL_Shortener/actions/workflows/ci.yml)

A runnable prototype of a governed, agentic software-engineering system. It takes a requirement through requirements, design, implementation, testing, documentation, review and release readiness. AI agents do the work under strict engine control. A **URL shortener** is the example workload the system builds and then changes.

> **Agents propose. The engine disposes.** Agents execute under defined autonomy boundaries; humans own oversight, approvals, and final quality.

What that means in this codebase:

- **Agents never write files.** An agent returns a `Proposal`. The engine validates every path, applies the proposal in a throwaway staging copy, runs real gates (`mvn compile`, `mvn test`, policy scanners), and promotes only what passes.
- **The append-only event log is the only state.** Every status, attempt, approval and re-plan is an event in SQLite, and the database rejects updates and deletes. Reports, metrics and resume are all computed from it.
- **Humans approve exact artifacts.** An approval is bound to the artifact's SHA-256. Changing an upstream answer invalidates downstream work, reverts its files and revokes its approvals.

**For evaluators:** the AI SDLC artifacts (user stories, design and diagrams, code reviews with resolutions, coverage and functional coverage reports, commit history) are indexed in [docs/ai-sdlc/](docs/ai-sdlc/README.md), and how AI assistance was used to build this is in [docs/ai-assisted-development.md](docs/ai-assisted-development.md).

## What each role produces, and how it is checked

| Role (agent) | Produces | Proved by |
|---|---|---|
| Requirements | User stories ("As a ..., I want ..., so that ..."), acceptance criteria, open questions | `requirements-complete` |
| Design (architect) | Design document with Mermaid component and sequence diagrams, OpenAPI contract, schema migration | `design-diagrams`, `schema-valid` |
| Development | Code with a uniform error envelope, sanitized logging and an audit trail of every change request; every promotion becomes a commit in the run's workspace git history | `compile`, security gates |
| Code review | Every submitted file reviewed; findings with severity, status and resolution; GO/NO_GO | `review-complete`, `review-go` |
| QA (tester) | Unit and integration tests; the functional coverage matrix; line and branch coverage measured against a 100% target, every class below it named | `unit-tests`, `functional-coverage`, `test-coverage`, [docs/coverage.md](docs/coverage.md) |

Every report has a **Quality evidence** section with all of the above for its run; see the [greenfield sample](docs/sample-runs/greenfield/report.md#quality-evidence).

## How to run

There are two ways to run the application. Both give the same demos, tests, CLI and results.

| | Option A: Docker | Option B: Local |
|---|---|---|
| You install | Docker only | JDK 25, Maven 3.9+, `make` and `bash` (on Windows, use WSL) |
| First step | `docker build -t agentic-sdlc .` | `make build` |
| Run a demo | `docker run --rm -it -v "$PWD/runs:/app/runs" agentic-sdlc demo-greenfield` | `make demo-greenfield` |
| Run the tests | `docker run --rm agentic-sdlc test` | `make test` |
| Use the CLI | `docker run --rm -it -v "$PWD/runs:/app/runs" agentic-sdlc <command>` | `java -jar orchestrator/target/orchestrator.jar <command>` |
| Results | `runs/<runId>/` on your machine (through the volume mount) | `runs/<runId>/` |

Run every command from the repository root. Only the first step needs internet access, to download dependencies. After that, demos and tests run offline.

### Option A: Docker

**1. Build the image** (once, about 3 minutes):

```sh
docker build -t agentic-sdlc .
```

**2. Run a demo:**

```sh
docker run --rm -it -v "$PWD/runs:/app/runs" agentic-sdlc demo-greenfield   # build the URL shortener from scratch
docker run --rm -it -v "$PWD/runs:/app/runs" agentic-sdlc demo-brownfield   # add link expiry to the existing service
docker run --rm -it -v "$PWD/runs:/app/runs" agentic-sdlc demo-ambiguous    # "make links more secure": clarify, then re-plan
docker run --rm -it -v "$PWD/runs:/app/runs" agentic-sdlc demo-bugfix       # fix a real SSRF bypass test-first
docker run --rm -it -v "$PWD/runs:/app/runs" agentic-sdlc demo-all          # all four, about 3 minutes
```

**3. Run the tests** (shortener suite, then the orchestrator suite with the real-build scenario tests):

```sh
docker run --rm agentic-sdlc test
```

Run `docker run --rm agentic-sdlc help` to list every task. The `-v "$PWD/runs:/app/runs"` mount keeps runs on your machine between commands; without it, each container starts with an empty `runs/`. Workflows and policies are baked into the image, so rebuild it after editing anything under `scenarios/` or `policies/`. The container runs as an unprivileged user with UID 1000, so on Linux the files it writes belong to the first host user; if your UID differs, make `runs/` writable for UID 1000.

### Option B: Local

**1. Check the prerequisites and build** (the first build downloads dependencies):

```sh
java -version    # must report 25
mvn -version     # 3.9 or newer
make build       # verifies the shortener baseline, then builds orchestrator/target/orchestrator.jar
```

**2. Run a demo:**

```sh
make demo-greenfield   # build the URL shortener from scratch
make demo-brownfield   # add link expiry to the existing service
make demo-ambiguous    # "make links more secure": clarify, then re-plan
make demo-bugfix       # fix a real SSRF bypass test-first
make demo-all          # all four, about 2 minutes with a warm Maven cache
```

**3. Run the tests and scans:**

```sh
make test        # 161 shortener tests + 163 orchestrator tests (including the scenarios with real Maven gates)
make live-smoke  # real-model bug-fix run that must reach the API (needs ANTHROPIC_API_KEY/ANTHROPIC_MODEL)
make coverage    # docs/coverage.md: line and branch coverage of both projects, every class below 100%
make lint        # actionlint, zizmor, shellcheck, gitleaks (full history), Trivy Dockerfile config
make scan        # SAST, PMD/CPD, SonarQube (if SONAR_HOST_URL/SONAR_TOKEN are set), Trivy SCA and secrets, ZAP DAST
```

`make lint` and `make scan` also need Docker, for the pinned scanner images. The same checks, all four demos, and the Docker image (a Trivy scan, then the full test suite and all four demos with no network) run in GitHub Actions on every push and pull request ([.github/workflows/ci.yml](.github/workflows/ci.yml)); SonarQube runs there when the `SONAR_HOST_URL` and `SONAR_TOKEN` secrets are set. Results are listed in [docs/testing.md](docs/testing.md#code-quality-and-security-scans). `make clean` removes build output and runs.

### What the demos show and where the results are

Each demo drives a full run step by step and prints every command it issues. Every approval is made as `demo-reviewer`; the engine never approves on its own. Each run writes to `runs/<runId>/`:

- `report.md`: summary, workflow graph, timeline, metrics, approvals, lineage, gate results, and re-plan history
- `workspace/`: the code the agents produced
- `events.db`: the append-only event log that everything else is computed from
- `incident.md`: only if the run safe-stopped

### Driving a run yourself with the CLI

Define an `orchestrator` alias for the option you use:

```sh
# Option A: Docker
alias orchestrator='docker run --rm -it -v "$PWD/runs:/app/runs" agentic-sdlc'
# Option B: Local (after make build)
alias orchestrator='java -jar orchestrator/target/orchestrator.jar'
```

Then the commands are identical:

```sh
orchestrator run scenarios/greenfield/workflow.yaml --run-id my-run   # exits 10: paused for a human
orchestrator pending my-run                                           # what needs approval, with the diff
orchestrator approve my-run design --by alice --comment "Looks right"
orchestrator resume my-run                                            # repeat pending/approve/resume until done
orchestrator status my-run
orchestrator report my-run
```

## CLI

`orchestrator <command>` (options: `--runs-dir`, `--policies`, `--repo-root`, `--no-color`).

| Command | Purpose |
|---|---|
| `run <workflow.yaml> [--run-id ID] [--mode MOCK\|LIVE]` | Validate the workflow and start a run with a live `[seq] node EVENT detail` trace |
| `status <run>` | Node table with status, attempts, artifact and pending hashes |
| `pending <run> [--max-diff-lines N]` | Waiting approvals (summary, risk reasons, files, unified diff, hash) and open questions |
| `approve <run> <node> --by X --comment "..." [--hash H]` | Hash-bound approval; promotes the retained staged files |
| `reject <run> <node> --by X --comment "..."` | Discard the pending artifact; the node re-runs with the comment as feedback |
| `answer <run> <questionId> "<text>" --by X` | Answer a clarification (changing an answer later triggers invalidation) |
| `resume <run>` | Continue after approvals or answers, a fixed safe-stop cause, or a crash; DONE nodes are never re-run |
| `report <run>` | Write `report.md` and print the metrics table |
| `lineage <run> <node\|hash>` | Print the decision chain back to the requirement |

Exit codes: `0` completed, `10` paused for a human, `20` safe-stopped, `2` usage or validation error.

Runs use **MOCK** mode by default: agents replay checked-in fixtures, so no API key or network is needed. **LIVE mode** (`run ... --mode LIVE`) puts all seven roles on a model: `requirements`, `analyst`, `architect`, `developer`, `tester`, `docs` and `reviewer`. The analyst's JavaParser scan stays real; the model reasons over it, and the `impact-files-exist` gate checks every claimed file against the scan. Live agents only *propose*: generated files go through exactly the same path scopes, policy gates, real `mvn` builds and human approvals as fixture output. A live attempt that fails its gates is rolled back and retried with the failure as feedback. When a node's retries run out, its fixture agent (`mock-<id>`) takes over, so a run still completes. Each model call's input and output tokens and its duration are recorded in `AGENT_CALLED`, and `status`, the trace and the report's metrics show the totals. LIVE mode needs `ANTHROPIC_API_KEY` and `ANTHROPIC_MODEL`, plus an optional `ANTHROPIC_MAX_TOKENS` (default 16000). Only `run` and `resume` call agents, so only they need the key: `status`, `pending`, `approve`, `reject`, `answer`, `report` and `lineage` work on a LIVE run without it. For LIVE, use the interactive runner, where **you** are the reviewer at every checkpoint; nothing is approved automatically:

```sh
export ANTHROPIC_API_KEY=... ANTHROPIC_MODEL=...
make live WORKFLOW=scenarios/bugfix/workflow.yaml        # local
docker run --rm -it -e ANTHROPIC_API_KEY -e ANTHROPIC_MODEL -v "$PWD/runs:/app/runs" agentic-sdlc live scenarios/bugfix/workflow.yaml
```

**Build gates are sandboxed.** Generated code is compiled and tested in a container when Docker is available: no network, read-only root filesystem, no capabilities, user `nobody`, memory/CPU/process limits, and a read-only view of the Maven cache. The staged sources are streamed in as an archive, so a build cannot change anything on your machine. `make build` pulls the image (`maven:3.9-eclipse-temurin-25`), and each run prints a line saying where its gates run. Without Docker the gates run on the host with the timeout and a stripped environment; set `build.sandbox.mode: DOCKER` in `policies/policies.yaml` to refuse that instead. Inside this project's own Docker image, gates run directly in that container.

**One writer per run.** Commands that change a run (`run`, `resume`, `approve`, `reject`, `answer`) take an exclusive OS file lock on `runs/<id>/run.lock`. A second process trying to change the same run gets a clear error. Read-only commands (`status`, `pending`, `report`, `lineage`) never wait. Different runs are independent directories and can run in parallel.

## Repository map

| Path | Contents |
|---|---|
| `orchestrator/` | The engine (`core`), agent implementations (`agents`, `agents/live`) and the Picocli CLI (`cli`) |
| `shortener-service/` | The URL shortener: Spring Boot 3.5, H2 and Flyway. A standalone project, and byte-identical to the greenfield scenario output |
| `scenarios/{greenfield,brownfield,ambiguous,bugfix}/` | `workflow.yaml` (the plan as data) and `fixtures/` (the agents' checked-in outputs: real code and real tests) |
| `policies/policies.yaml` | Budgets, per-agent path scopes, dependency allowlist, scanner patterns, risk thresholds, build sandbox |
| `Dockerfile`, `docker/entrypoint.sh` | The self-contained image and its task runner |
| `scripts/` | `demo-*.sh`, `live-run.sh`, `bless-baseline.sh`, `quality-scan.sh` |
| `docs/` | [architecture](docs/architecture.md), [decisions](docs/decisions.md), [testing](docs/testing.md), [orchestrator engineering summary](docs/system-engineering-summary.md), [service summary](docs/engineering-summary.md), [orchestration model](docs/orchestration.md), [scenario walkthroughs](docs/scenarios/) |
| `docs/sample-runs/*/report.md` | Committed reports from the demo runs |
| `docs/ai-sdlc/` | The AI SDLC artifacts exported from the sample runs, with an index mapping each deliverable to its agent and gate |
| `docs/ai-assisted-development.md` | How AI assistance was used to build the system, what it caught, what it got wrong, and where humans stayed in control |
| `docs/coverage.md` | Line and branch coverage of both projects, every class below 100% |
| `runs/` | Run output (git-ignored): `events.db`, `workspace/`, `artifacts/`, `report.md`, `incident.md` |

## Quality evidence

Every check below runs automatically on every push via [GitHub Actions](.github/workflows/ci.yml). Reproduce locally with `make test`, `make scan`, `make lint` and `make demo-all`.

| Category | Check | Result |
|---|---|---|
| **Testing** | Unit and integration tests | 324 tests (161 shortener + 163 orchestrator), all passing |
| | Shortener coverage (JaCoCo floor) | 100% line, 100% branch — 400/400 lines, 183/183 branches, 37 classes, no exclusions |
| | Orchestrator coverage (JaCoCo floor) | 91.4% line, 78.7% branch — 88%/75% floor enforced, every gap categorized in [coverage.md](docs/coverage.md) |
| | Functional coverage | Every acceptance criterion mapped to the tests that prove it ([functional-coverage.md](docs/ai-sdlc/functional-coverage.md)) |
| | Scenario tests | 4 end-to-end demos (greenfield, brownfield, ambiguous, bugfix) with real gates |
| **Performance** | k6 smoke (every push) | 1 VU · 30 s · all endpoints · p95 < 500 ms |
| | k6 load (main) | 10 VUs · 3 min · p95 redirect < 150 ms, stats < 200 ms, create < 300 ms |
| | k6 stress (main) | 0→100 VUs · ramp to saturation · informational |
| **SAST** | SpotBugs + FindSecBugs | 0 findings — Java bytecode security analysis |
| | PMD + CPD | 0 findings — code quality rules, copy-paste detection |
| | Strict compiler | `javac -Xlint:all -Werror` — warnings are errors |
| | actionlint + zizmor | GitHub Actions syntax, expressions, security (unpinned actions, template injection) |
| | ShellCheck | Every shell script linted |
| **SCA & Secrets** | Trivy SCA (CycloneDX SBOMs) | 0 HIGH/CRITICAL vulnerabilities in dependencies |
| | Trivy image scan | 0 fixable HIGH/CRITICAL in Docker image (CVE-2026-84782 patched) |
| | Trivy config scan | Dockerfile misconfigurations checked |
| | Gitleaks | Full git history scanned — 0 secrets |
| | Trivy secret scan | Working tree scanned — 0 leaked credentials |
| **DAST** | OWASP ZAP API scan | Live API scan driven by `openapi.yaml` — injection, XSS, auth, headers |
| **Code review** | Review complete | 55/55 files reviewed — every finding FIXED, ACCEPTED or DEFERRED with resolution ([code-review.md](docs/ai-sdlc/code-review.md)) |
| | Regression tests | Full 161-test suite on the merged workspace after review |
| **Docker** | Tests inside image (`--network none`) | Full test suite runs offline inside the container |
| | Demos inside image (`--network none`) | All 4 scenario demos run offline |
| **Observability** | Event-sourced audit log | 20 event types · insert-only SQLite · full decision lineage · crash recovery |
| | Metrics | MTTR, success rate, gross/net/human-wait latency, gate failures — all derived from events |
| | Incident email alerts | P1–P4 severity classification · Spring Mail · fail-safe (SMTP errors logged, not thrown) |
| | Log sanitization | CR/LF stripped · no raw IP logging · gate-enforced |
| | Audit trail | `audit_event` table — every state-changing request with opaque client key, no PII |

<details>
<summary><strong>Orchestrator policy gates (enforced per agent, per node)</strong></summary>

`path-allowlist` · `secret-scan` · `forbidden-api` · `dependency-allowlist` · `compile` · `unit-tests` · `regression-tests` · `test-coverage` · `functional-coverage` · `requirements-complete` · `design-diagrams` · `review-complete` · `no-raw-ip-logging` · `artifact-metadata` · `schema-valid`

</details>

## Where to look first

1. [docs/sample-runs/ambiguous/report.md](docs/sample-runs/ambiguous/report.md): before/after graphs, the invalidation cascade, and a revoked approval.
2. [docs/architecture.md](docs/architecture.md): how one node executes, and the requirement-to-code-to-test traceability table.
3. [NodeRunner.java](orchestrator/src/main/java/com/example/agentic/core/engine/NodeRunner.java) and [RunState.java](orchestrator/src/main/java/com/example/agentic/core/state/RunState.java): the node algorithm and the event fold.
