# Build Spec: Agentic SDLC Orchestrator (with URL Shortener as the example workload)

> **How to use this file (instructions to the AI coding agent)**
>
> You are building this repository end to end. Follow these rules for the whole job:
>
> 1. Read this entire file before writing any code.
> 2. Work **phase by phase** in the order of Section 13. Finish a phase, run its verification command, fix everything, and only then start the next phase.
> 3. Do not add features, modules, or dependencies that this file does not ask for. If something is ambiguous, pick the simplest choice consistent with this file and record it in `docs/decisions.md`.
> 4. Every claim in the docs must be backed by code or a test in the repo. Do not document capabilities that do not exist.
> 5. No `TODO`, no commented-out code, no placeholder implementations, no stubbed tests. Every public type has Javadoc stating its responsibility.
> 6. After each phase, print a short status: what was built, what was tested, what is left.
> 7. At the end, run the full acceptance checklist (Section 14) and report pass/fail per item.

> **Revision note.** This spec was updated after the build to match what was delivered. The deliberate changes are: JavaParser 3.28.x (3.26.x cannot parse Java 25 syntax); typed event payloads (6.5); the engine split into narrower collaborators (Section 4); stricter graph-patch validation (6.8); a Docker image as an alternative to the local toolchain (Sections 11 and 12); a `scan` target with code-quality and security tooling (Sections 3 and 12); `LinkController` as the controller name (9.2); 21 ADRs; a `bugfix` scenario and `reproduces-defect` gate (9.4, 6.9); LIVE mode for all seven roles (7); one writer per run (6.5); a container sandbox for gate builds (R10, 6.9); the `q-analytics` assumption met by the existing v1 stats (9.3); model token usage recorded per LIVE attempt (7); build plugins, parent POMs, repositories and extensions covered by `dependency-allowlist` (6.9); an optional human-authored node `task` stated to live agents, which graph patches may not set (6.8, 7); and evidence gates for each role (user stories, design diagrams, complete reviews with resolved findings, functional and line/branch coverage) with a greenfield `qa_report` step, an audit trail in the shortener, and a workspace git history of every promotion (6.9, 9.1).

---

## 1. Mission

Build a **working, runnable prototype of an agentic software engineering system** in **Java 25**. The system takes a requirement and drives it through the SDLC (requirements, design, implementation, testing, documentation, review, release readiness) using AI agents under strict governance.

- The **URL shortener** is only the *example workload* the system builds and modifies.
- The **orchestration engine** is the primary deliverable and is what will be evaluated.

**Guiding principle (repeat it in the README):**

> Agents propose. The engine disposes. Agents execute under defined autonomy boundaries; humans own oversight, approvals, and final quality.

## 2. Non-negotiable design rules

| # | Rule |
|---|------|
| R1 | **Agents never touch the filesystem.** An agent returns a `Proposal`. Only the engine's `Workspace` writes files, after path-allowlist validation. |
| R2 | **The append-only event log is the single source of truth.** `RunState` is a pure fold over events. Nothing else stores state. |
| R3 | **Gates run real tools** (`mvn compile`, `mvn test`, real scanners). No gate may be faked or hard-coded to pass. |
| R4 | **Failed attempts leave the workspace byte-identical.** Work happens in a staging directory that is discarded on failure and promoted only on success. |
| R5 | **Approvals are bound to an artifact hash.** If the artifact changes, the approval is void. |
| R6 | **All retries, fallbacks, and loops are bounded**, and the run has global budgets. Exceeding any triggers safe-stop. |
| R7 | **Workflows are data (YAML).** One generic engine runs all scenarios. No scenario-specific code in `core`. |
| R8 | **`core` must not depend on `agents` or `cli`.** Enforce with an ArchUnit test. |
| R9 | **Determinism by default.** Default agent mode is fixture-driven (mock). All time access goes through an injectable `Clock`. |
| R10 | **Generated code is untrusted.** Gates run it with a timeout, in a temp directory, with secrets removed from the environment, and in a network-less container sandbox when Docker is available (`build.sandbox` in `policies.yaml`). |

## 3. Tech stack

| Concern | Choice |
|---|---|
| Language / build | Java 25, Maven. Pin exact versions in the parent POM `dependencyManagement` (use the latest stable at build time). |
| Orchestrator libs | Jackson (`jackson-databind`, `jackson-dataformat-yaml`), Picocli 4.7.x, SLF4J + Logback, JavaParser 3.28.x (the first line that parses Java 25 syntax such as unnamed variables `_`), `org.xerial:sqlite-jdbc` |
| Orchestrator test libs | JUnit 5, AssertJ, Mockito, Awaitility, ArchUnit |
| Shortener (workload) | Spring Boot 3.x (Web, Validation, JDBC), Flyway, H2, JUnit 5, AssertJ, `MockMvc`, Awaitility (tests of the asynchronous click pipeline) |
| Concurrency | `Executors.newVirtualThreadPerTaskExecutor()` with `CompletableFuture` |
| Live LLM (optional) | `java.net.http.HttpClient` calling the Anthropic Messages API. No vendor SDK. |
| Code quality (build plugins) | `-Xlint:all -Werror`, SpotBugs + FindSecBugs, PMD + CPD, JaCoCo, CycloneDX SBOM. Each build fails on any finding; reviewed suppressions live in `spotbugs-exclude.xml` with a justification each |
| Security scans (`make scan`) | The above plus SonarQube (when `SONAR_HOST_URL`/`SONAR_TOKEN` are set), Trivy SCA over the SBOMs, Trivy secret scan, and an OWASP ZAP API scan of the running shortener |
| Static CI checks (`make lint`) | actionlint, zizmor, shellcheck, gitleaks over the git history, Trivy `Dockerfile` config; CI also scans the Docker image with Trivy |
| Runtime packaging | Runs locally (JDK 25, Maven) or from a `Dockerfile` whose image builds and verifies everything, so Docker alone is enough to run demos, tests and the CLI |
| Not used | JGit, Spring in the orchestrator, any UI framework |

## 4. Repository layout (create exactly this)

```
agentic-sdlc/
├── pom.xml                              # parent POM; modules: orchestrator
├── orchestrator/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/example/agentic/
│       │   ├── core/                    # engine, no dependency on agents/cli
│       │   │   ├── Orchestrator, EngineConfig   # run lifecycle (start, resume) and per-process configuration
│       │   │   ├── agent/               # Agent, AgentContext, Proposal, WorkspaceView (read-only), AgentResolver
│       │   │   ├── graph/               # Node, Autonomy, WorkflowGraph, GraphValidator, GraphPatch, WorkflowLoader
│       │   │   ├── state/               # Event, EventType, Payload (typed event bodies), EventStore, SqliteEventStore,
│       │   │   │                        #   RunLog (single write path), RunState, NodeStatus, ArtifactStore, Hashing
│       │   │   ├── engine/              # Engine (composition root), Scheduler, NodeRunner, AttemptExecutor, Settlement,
│       │   │   │                        #   GateRunner, NodeCompletion, ReplanService, SafeStop, BudgetGuard, CircuitBreaker
│       │   │   ├── gate/                # Gate, GateContext, GateResult, GateRegistry, built-in gates
│       │   │   ├── policy/              # policy gates + RiskRule implementations, PolicyConfig
│       │   │   ├── approval/            # ApprovalService, ClarificationService
│       │   │   ├── workspace/           # Workspace, Staging, Diff, PathGuard
│       │   │   ├── metrics/             # MetricsCalculator, RunMetrics
│       │   │   └── report/              # ReportWriter (one class per section), EventDescriber, MermaidRenderer, LineageWalker, IncidentWriter
│       │   ├── agents/                  # Agent impls; depends on core
│       │   │   ├── AgentRegistry, MockAgent (fixture-driven), CodebaseAnalystAgent, JavaCodebaseScanner
│       │   │   └── live/                # AnthropicClient, LiveRequirementsAgent, LiveReviewerAgent, LiveBuilderAgent (optional)
│       │   └── cli/                     # Picocli commands; depends on core + agents
│       └── test/java/...                # tests mirror main packages
├── baseline/
│   └── shortener-service/               # v1 product; standalone Maven project (NOT a module of the root POM)
├── scenarios/
│   ├── greenfield/   { workflow.yaml, README.md, fixtures/ }
│   ├── brownfield/   { workflow.yaml, README.md, fixtures/ }
│   └── ambiguous/    { workflow.yaml, README.md, fixtures/ }
├── policies/policies.yaml               # allowlists, thresholds, regex patterns, budgets
├── scripts/                             # demo-*.sh, bless-baseline.sh, quality-scan.sh
├── Dockerfile, docker/entrypoint.sh     # self-contained image and its task runner
├── docs/
│   ├── architecture.md
│   ├── decisions.md                     # ADR-style decision log (8 to 16 entries)
│   ├── testing.md
│   ├── engineering-summary.md
│   ├── scenarios/{greenfield,brownfield,ambiguous,bugfix}.md
│   └── sample-runs/{greenfield,brownfield,ambiguous,bugfix}/report.md   # committed outputs
├── runs/                                # gitignored (keep .gitkeep)
├── Makefile
├── .gitignore
└── README.md
```

Base package: `com.example.agentic`. Runs are written to `runs/<runId>/` containing `events.db`, `workspace/`, `artifacts/`, `report.md`, and `incident.md` (only on safe-stop).

---

## 5. Product spec: the URL shortener (keep it modest)

The shortener lives at `shortener-service` and is the **committed v1**. It must be identical in behavior to what the greenfield scenario produces (`scripts/bless-baseline.sh` copies the greenfield final workspace there). Package: `com.example.shortener` with sub-packages `api`, `domain`, `service`, `storage`.

> **Link expiry is deliberately NOT in v1.** It is the brownfield scenario's change.

### 5.1 API contract

| Endpoint | Behavior |
|---|---|
| `POST /api/v1/links` body `{ "url": string, "customAlias": string? }` | `201` with `{code, shortUrl, url, createdAt}`. If the same normalized URL already exists and no alias is given, return the existing link with `200` (idempotent). `400` invalid input, `409` alias taken, `429` rate limited. |
| `GET /{code}` | `302` with `Location` header. `404` if unknown. Records a click asynchronously. |
| `GET /api/v1/links/{code}/stats` | `200` `{code, totalClicks, lastAccessedAt, clicksPerDay:[{date,count}]}`. `404` if unknown. |

Error body for every non-2xx/3xx: `{"error":{"code":"<UPPER_SNAKE>","message":"<text>"}}`. Provide `openapi.yaml` in the project root that matches the implementation.

### 5.2 Rules

- **Code generation:** 7 characters, base62, `SecureRandom`; insert relies on a unique constraint; retry up to 5 times on collision, then fail with `500` `CODE_GENERATION_FAILED`.
- **Alias:** 3 to 32 chars matching `[A-Za-z0-9_-]`; reserved words `api`, `actuator`, `health`, `stats` are rejected.
- **URL validation:** scheme must be `http` or `https`; max 2048 chars; host required; reject `localhost`, `*.local`, and literal IPs in loopback, link-local, unspecified, and private ranges (10/8, 172.16/12, 192.168/16, 169.254/16, `::1`, `fc00::/7`, `0.0.0.0`). No DNS resolution at creation (document DNS rebinding as a known limitation).
- **Rate limiting:** in-memory token bucket per client key on `POST /api/v1/links` (default 20 requests/minute). The client key is a hash of the remote address; **never log a raw IP**.
- **Click recording:** bounded queue (capacity 10,000) drained by a worker; **drop on overflow** and count drops in a metric. The redirect path must never block on click persistence.
- **Storage:** `LinkRepository` interface plus a JDBC implementation. Flyway migration `V1__init.sql` creates `link(code PK, url, url_hash unique-index, created_at)` and `click(id PK, code FK, clicked_at)` with an index on `(code, clicked_at)`.
- **Logging:** SLF4J only; no PII.

### 5.3 Product tests (required)

- Unit: `CodeGenerator` (length, alphabet, collision retry), `UrlValidator` (every rule above), `RateLimiter` (refill and exhaustion, with injected clock).
- Integration (`MockMvc`): create then redirect (302), unknown code 404, idempotent create returns 200, duplicate alias 409, invalid URL 400, rate-limit 429, stats totals and per-day buckets.

---

## 6. Orchestrator spec

### 6.1 Core model

```java
public enum Autonomy { AUTO, APPROVE_AFTER, ESCALATE_ON_RISK }

public record Node(
    String id, String agent, Set<String> dependsOn,
    List<String> entryGates, List<String> exitGates,
    Autonomy autonomy, int maxRetries,
    String fallbackAgent,             // nullable
    String fixtureVariantFrom) {}     // nullable: question id whose answer selects the fixture variant

public enum NodeStatus {
  PENDING, RUNNING, AWAITING_APPROVAL, AWAITING_CLARIFICATION, DONE, FAILED, SKIPPED }

public record Proposal(Map<String,String> files, String rationale,
                       List<String> derivedFrom, Map<String,Object> data) {}

public interface Agent {
  String id();
  Proposal propose(AgentContext ctx) throws AgentException;
  default Map<String,String> metadata() { return Map.of(); }   // audit data for AGENT_CALLED (e.g. model); never secrets
}

public sealed interface GateResult {
  record Pass() implements GateResult {}
  record Fail(String gateId, String reason, String signature) implements GateResult {}
}
public interface Gate { String id(); GateResult evaluate(GateContext ctx); }

public interface RiskRule { Optional<String> flag(Diff diff); }   // returns escalation reason
```

- `Fail.signature` = SHA-256 of `gateId + normalized error text` (strip paths, timestamps, line numbers). It drives the circuit breaker.
- `Proposal.data` carries structured outputs (ambiguity list, impact report, graph patch, review findings).
- **Artifact hash** = SHA-256 of canonical JSON (sorted keys) of `{files, data}`. `rationale` and `derivedFrom` are stored alongside but not hashed.
- **Node input hash** = SHA-256 of the sorted hashes of all upstream artifacts consumed plus the fixture variant key.

### 6.2 Workflow YAML format

```yaml
name: greenfield
requirement: "Build a URL shortener service ..."
workspace: empty              # or: copy:shortener-service
budgets: { maxTotalAttempts: 25, maxWallClockSeconds: 900, maxAgentCalls: 60 }
nodes:
  - id: requirements
    agent: requirements
    exitGates: [artifact-metadata, requirements-complete]
    autonomy: AUTO
  - id: design
    agent: architect
    dependsOn: [requirements]
    exitGates: [artifact-metadata, schema-valid]
    autonomy: APPROVE_AFTER
  # ...
```

`WorkflowLoader` must reject: duplicate ids, unknown dependency ids, unknown gate ids, unknown agent ids, negative retries, and any cycle (Kahn's algorithm; the error message names the cycle members).

### 6.3 Node execution algorithm (implement exactly)

```
for a READY node (status PENDING and all dependsOn are DONE):
  1. Append NODE_STARTED (inputHash). Status -> RUNNING.
  2. Evaluate entry gates. On Fail: append GATE_FAILED, treat as blocking failure (no agent call, no retry) -> node FAILED -> safe-stop.
  3. attempt = 1; agentId = node.agent; feedback = none
  4. loop:
       a. Budget check (attempts, wall clock excluding approval wait, agent calls). Exceeded -> safe-stop.
       b. Build AgentContext: requirement, ONLY the upstream artifacts this node depends on (transitively needed), answers, feedback, attempt number.
       c. proposal = agent.propose(ctx). Append AGENT_CALLED (agentId, promptHash, responseHash).
          AgentException -> failure with signature; go to (g).
       d. Create staging = copy of promoted workspace. PathGuard validates every proposed path against the agent's globs in policies.yaml
          (reject '..', absolute paths, symlinks, out-of-scope paths). Violation = Fail(path-allowlist). Apply files to staging only.
       e. Run exit gates in order against staging; first Fail stops evaluation. Append GATE_PASSED / GATE_FAILED per gate.
       f. If all pass: compute artifact hash, store artifact, run RiskRules on Diff(promoted, staging).
            - autonomy APPROVE_AFTER, or ESCALATE_ON_RISK with any risk flag -> append APPROVAL_REQUESTED (hash, summary, diff, reasons),
              status -> AWAITING_APPROVAL, stage retained on disk under runs/<id>/pending/<node>. Stop this node.
            - otherwise promote staging to workspace, append NODE_DONE, status -> DONE.
       g. On failure: discard staging, append ATTEMPT_DISCARDED (signature). feedback = failure reason.
            - CircuitBreaker: same signature twice in a row for this node+agent -> stop retrying this agent.
            - attempts > maxRetries or breaker tripped:
                 fallbackAgent exists and unused -> append FALLBACK, agentId = fallback, attempt = 1, continue
                 else -> append NODE_FAILED, then SAFE_STOP.
            - else attempt++ and continue.
```

`maxRetries = 2` means at most 3 attempts. A rejection by a human does not consume retries but does count toward `maxTotalAttempts`.

### 6.4 Scheduler (wave with join barrier)

- Each iteration: compute all READY nodes, run them concurrently on the virtual-thread executor, `join` all (the synchronization barrier), reload `RunState` from the event log, re-check budgets.
- A join node (many `dependsOn`) is READY only when **all** dependencies are DONE.
- If nothing is READY and some nodes are `AWAITING_APPROVAL` / `AWAITING_CLARIFICATION`: emit `RUN_PAUSED` and return exit code 10. Other branches keep running until they also block or finish.
- If nothing is READY and everything is DONE: append `RUN_COMPLETED`.
- Parallel nodes must never clobber each other: each stages from the promoted workspace and promotion copies only that node's proposed files (path scopes of parallel nodes are disjoint by policy; a promotion conflict is a hard failure).

### 6.5 Persistence

SQLite via JDBC. Tables:

```
events(seq INTEGER PK AUTOINCREMENT, run_id, node_id NULL, type, actor,
       input_hash NULL, output_hash NULL, ts, details_json)
artifacts(hash PK, node_id, derived_from_json, rationale, content_json)
```

- `events` is **insert-only**: the DAO exposes no update/delete, and SQLite triggers `BEFORE UPDATE` / `BEFORE DELETE` raise `ABORT`. A test verifies the triggers.
- `RunState.fold(events)` derives node statuses, attempt counters, the current graph (including applied patches), answers, approvals, and budgets used.
- Every event body is a typed record: one record per event type in a sealed `Payload` interface (for example `GateFailed(gate, phase, reason, signature)`). `details_json` holds the record's JSON form. Writers append records through `RunLog`, the single write path, and readers pattern-match on them. Decoding rejects unknown fields, and an event is decoded before it is persisted, so the log never holds an event the fold cannot read.
- Event types (exact set): `RUN_STARTED, NODE_STARTED, AGENT_CALLED, GATE_PASSED, GATE_FAILED, ATTEMPT_DISCARDED, FALLBACK, NODE_FAILED, APPROVAL_REQUESTED, APPROVED, REJECTED, CLARIFICATION_REQUESTED, ANSWERED, INVALIDATED, REPLAN, NODE_DONE, RUN_PAUSED, RESUMED, SAFE_STOP, RUN_COMPLETED`.
- One writer per run: operations that change a run take an exclusive OS file lock on `runs/<id>/run.lock`; read-only commands never lock.
- Resume: `resume` loads events, rebuilds state, and continues. Nodes already `DONE` are never re-executed (verified by a test that counts agent calls).

### 6.6 Approvals and clarifications

- `pending <run>` prints, for each waiting node: summary, risk reasons, file list, unified diff, artifact hash.
- `approve <run> <node> --by <name> --comment <text>`: verify the hash in the most recent `APPROVAL_REQUESTED` equals the node's current artifact hash, else throw `StaleApprovalException`. On success append `APPROVED` (approver, comment, hash), promote the retained staging, append `NODE_DONE`. `--by` is mandatory.
- `reject <run> <node> --by <name> --comment <text>`: append `REJECTED`; re-run the node with the comment as feedback; the changed output triggers invalidation of downstream nodes.
- Clarification: the requirements agent's `data.ambiguities` is a list of `{id, question, blocking, options[], assumptionIfUnanswered}`. Blocking ambiguities put the requirements node in `AWAITING_CLARIFICATION` (append `CLARIFICATION_REQUESTED` per question). `answer <run> <questionId> "<text>" --by <name>` appends `ANSWERED`. When every blocking question is answered, the node finalizes; **the answers are folded into the requirements artifact**, so a changed answer changes its hash. Non-blocking ambiguities become recorded assumptions in the artifact.
- Answering an already-answered question after downstream nodes ran creates a new requirements artifact version (new hash) and triggers invalidation.

### 6.7 Recovery, budgets, safe-stop

- **Retries:** feed the gate failure text to the agent as `feedback`.
- **Circuit breaker:** two identical consecutive signatures for the same node+agent stop retries for that agent.
- **Fallback:** one round with `fallbackAgent`, if configured.
- **Rollback:** discard staging; verified byte-identical workspace (hash the tree before and after in tests).
- **Budgets** (defaults in `policies.yaml`): `maxTotalAttempts=25`, `maxWallClockSeconds=900` (approval wait time excluded), `maxAgentCalls=60`.
- **Safe-stop:** append `SAFE_STOP` (reason, node), mark every non-DONE node `SKIPPED`, write `runs/<id>/incident.md` (failing node, signature history, last gate output, DONE nodes preserved, how to resume or restart), and exit with code 20. DONE nodes stay DONE.

### 6.8 Dynamic re-planning (two mechanisms, both required)

1. **Hash invalidation.** After any node produces a new artifact hash that differs from its previous hash, compute `downstreamOf(node)` transitively. For each non-PENDING downstream node: append `INVALIDATED(reason, upstream, oldHash, newHash)`, treat any approval on it as revoked, reset its status to PENDING. Append one `REPLAN` event summarizing the cascade. No-op if the hash is unchanged.
   Promoted files of invalidated DONE nodes are reverted from their promotion records as one validated batch, newest first. If any file changed since promotion, nothing is reverted.
2. **Graph patch.** An agent may return `data.graphPatch = {addNodes[], removeEdges[], addEdges[], reason}`. `ReplanService` must: validate acyclicity, reject any change that redefines an existing node or alters the dependencies of a DONE, RUNNING, AWAITING_APPROVAL or AWAITING_CLARIFICATION node, verify referenced nodes, gates and agents exist, then append `REPLAN(patch, reason)`. A malformed or invalid patch fails the proposing attempt as the `graph-patch` gate; it never crashes the engine. `RunState` folds patches into the current graph.

### 6.9 Gates and policies (implement these; no more)

| Gate id | Type | Behavior |
|---|---|---|
| `artifact-metadata` | compliance | Proposal has non-empty `rationale` and at least one `derivedFrom` (except the requirements node). |
| `requirements-complete` | quality | Requirements artifact has problem statement, acceptance criteria, ambiguities list, assumptions list. |
| `schema-valid` | quality | `openapi.yaml` parses and every path has responses; SQL migrations are non-empty and filenames match `V<n>__*.sql`. |
| `path-allowlist` | change control | Enforced by `PathGuard` during apply (see 6.3d); also exposed as a gate for entry checks. |
| `secret-scan` | security | Regex over added lines: AWS access key ids, `-----BEGIN .* PRIVATE KEY-----`, `password\s*=`, `api[_-]?key\s*=` with a literal value. |
| `forbidden-api` | security | In `src/main/**` only: `Runtime.getRuntime().exec`, `ProcessBuilder`, `ObjectInputStream`, `Class.forName` on non-literals, `ScriptEngine`. |
| `dependency-allowlist` | security | Dependencies added in `pom.xml` must be in `dependencyAllowlist`, and build plugins, plugin dependencies and parent POMs in `buildAllowlist`; added repositories or build extensions Fail. |
| `review-complete` | quality | `reviewedFiles` must list every file the upstream steps submitted; every finding needs severity, file, message, status (FIXED, ACCEPTED, DEFERRED) and resolution; an unfixed HIGH or CRITICAL finding forces NO_GO. |
| `design-diagrams` | quality | Every `docs/design*.md` the proposal writes contains a Mermaid diagram, and there is at least one. |
| `test-coverage` | quality | JaCoCo line and branch coverage of the staged project against `coverage` in `policies.yaml` (target 100%): fails below the minimums or when a class misses the target without a documented exception; the pass records the numbers and names every class below target. |
| `functional-coverage` | quality | Every upstream acceptance criterion is mapped to at least one test in `functionalCoverage`, and every cited `ClassName#method` exists in the staged tests. |
| `no-raw-ip-logging` | compliance | Fail if a `log.`/`logger.` call line contains `getRemoteAddr` or `remoteAddr`. |
| `compile` | build | Runs `mvn -q -B compile` in staging. |
| `unit-tests` | build | Runs `mvn -q -B test` in staging; failure text (trimmed to 4,000 chars) becomes feedback. |
| `regression-tests` | build | Runs the full existing suite `mvn -q -B test` in staging; used where prior behavior must be preserved. |
| `impact-files-exist` | quality | Every file in the analyst's impact report exists in the scan of the workspace. |
| `reproduces-defect` | build | Runs `mvn -q -B test` in staging and passes only if the build **fails**, the failures are assertion failures in test classes the proposal adds, and no other test class fails. Proves a bug-fix regression test reproduces the bug before any fix exists. |

**Risk rules** (evaluated on the diff; any hit escalates `ESCALATE_ON_RISK` nodes to human approval): `MigrationPathRule` (`**/db/migration/**`), `PomChangeRule` (any `pom.xml` change), `DiffSizeRule` (more than 200 changed lines, configurable), `DeletionRule` (any deleted file).

**Build tool execution (R10):** run `mvn` via `ProcessBuilder` in the staging dir with a 120 s timeout, `MAVEN_OPTS` limited, environment stripped to `PATH`, `JAVA_HOME`, `HOME`; kill on timeout and return `Fail` with signature `timeout`. With `build.sandbox.mode` `AUTO` (Docker available) or `DOCKER` (required, fails closed), run it instead in a container with no network, a read-only root, all capabilities dropped, an unprivileged user, resource limits and the host Maven cache mounted read-only; the staged tree is streamed in as a tar archive so the build cannot write to the host.

### 6.10 Metrics (computed only from the event log)

| Metric | Definition |
|---|---|
| Success rate | Nodes that reached DONE with zero `GATE_FAILED`/`ATTEMPT_DISCARDED` events, divided by total nodes in the final graph |
| Retry frequency | Count of `ATTEMPT_DISCARDED` per run and per node |
| Rollback frequency | Same events (each discard is a rollback); report also `FALLBACK` count |
| MTTR | Mean of (first subsequent `NODE_DONE` ts minus first `GATE_FAILED`/`NODE_FAILED` ts) per node that recovered |
| End-to-end latency | `RUN_STARTED` to `RUN_COMPLETED`, reported **net** and **gross**; approval/clarification wait (from `APPROVAL_REQUESTED`/`CLARIFICATION_REQUESTED` to `APPROVED`/`ANSWERED`) excluded from net |
| Governance counts | approvals requested/granted/rejected, invalidations, replans, policy failures by gate id |

### 6.11 Report (`runs/<id>/report.md`, generated by `ReportWriter`)

Sections, in order: run summary (status, exit reason); Mermaid `graph TD` with nodes colored by final status; timeline table (seq, time, node, event, actor); metrics table; approvals table (who, when, hash approved, comment); decision lineage (for each key artifact, the chain of `derivedFrom` back to the requirement); policy and gate results; invalidation and replan history. `MermaidRenderer` also supports a before/after graph for the ambiguous scenario.

---

## 7. Agents and fixtures

| Agent id | Purpose | Output |
|---|---|---|
| `requirements` | Normalize the request | `data.problemStatement`, `data.acceptanceCriteria[]`, `data.ambiguities[]`, `data.assumptions[]` |
| `architect` | Design | design doc (`docs/design.md`), `openapi.yaml`, SQL migrations, risk list in `data.risks[]` |
| `analyst` | Codebase reasoning | real JavaParser scan + impact report in `data.impact[]` (`file`, `reason`) and optional `data.graphPatch` |
| `developer` | Implementation | files under `src/main/**` and `src/main/resources/**`, `pom.xml` |
| `tester` | Tests | files under `src/test/**` |
| `docs` | Documentation | `README.md`, `docs/**` of the workspace project |
| `reviewer` | Review and security review | `data.findings[]` (severity, file, message), `data.recommendation` (`GO`/`NO_GO`) |

**MockAgent (default):** resolves the fixture directory `scenarios/<scenario>/fixtures/<nodeId>[/<variantKey>]/attempt<N>/`. It contains the files to propose (mirroring the workspace tree) and a `proposal.json` with `rationale`, `derivedFrom`, and `data`. If `attempt<N>` does not exist, throw `AgentException("no fixture for attempt N")`, which counts as a failed attempt. The variant key is the slugified answer to the node's `fixtureVariantFrom` question, or `default`.

**Fixtures are real code and real tests**, checked in and compiled by real gates. The engine has no "inject failure" flag; failures come from fixture content.

**CodebaseAnalystAgent (real scan + pluggable reasoning):** uses JavaParser to scan the workspace and produce `{classes[], imports[], routes[]}` (Spring `@GetMapping`/`@PostMapping`/`@RequestMapping`). The scan always runs first. The mapping from requirement to impacted files (an `ImpactReasoner`) comes from the fixture in MOCK mode and from the model, given the scan, in LIVE mode; the `impact-files-exist` gate validates it against the real scan either way.

**Live mode (optional, Phase 9, cut first if time is short):** `LiveRequirementsAgent`, `LiveReviewerAgent` and `LiveBuilderAgent` (for `architect`, `developer`, `tester`, `docs`, which return whole files) call the Anthropic Messages API via `HttpClient`, and `LiveAnalystReasoner` reasons over the analyst's real scan. Live output passes the same path scopes and gates as fixture output, and each live agent has a `mock-<id>` fixture fallback with the same scope in `policies.yaml`. `ANTHROPIC_MAX_TOKENS` optionally sizes the reply budget. Read `ANTHROPIC_API_KEY` and `ANTHROPIC_MODEL` from the environment (no hard-coded model). Request JSON-only output validated against a schema; on malformed output, count it as a failed attempt and fall back to the fixture agent. Log prompt hash, model, response hash, and the attempt's model usage (calls, input/output tokens, time, even for failed attempts) in `AGENT_CALLED`; treat a `max_tokens` stop as a failed attempt. Never log the API key; never pass it into gate subprocesses.

---

## 8. CLI (Picocli, executable JAR `orchestrator.jar`)

| Command | Purpose |
|---|---|
| `run <workflow.yaml> [--run-id ID] [--mode MOCK\|LIVE]` | Start (or continue) a run; prints a live trace |
| `status <run>` | Node table with status, attempts, hashes |
| `pending <run>` | Show waiting approvals/clarifications with diff and risk reasons |
| `approve <run> <node> --by X --comment "..."` | Approve (hash-bound) |
| `reject <run> <node> --by X --comment "..."` | Reject and re-run with feedback |
| `answer <run> <questionId> "<text>" --by X` | Answer a clarification |
| `resume <run>` | Continue after approvals/answers or a crash |
| `report <run>` | Generate `report.md` and print the metrics table |
| `lineage <run> <artifactOrNode>` | Print the decision chain back to the requirement |

Exit codes: `0` completed, `10` paused awaiting a human, `20` safe-stopped, `2` usage/validation error. Console trace lines: `[seq] node event detail` with color only when the terminal supports it.

---

## 9. Scenarios (each needs `workflow.yaml`, `fixtures/`, a walkthrough in `docs/scenarios/`, and a committed sample report)

### 9.1 Greenfield: "Build the core URL shortener"

Workspace starts empty. Graph:

```
requirements ─► design ─┬─► implement ─┬─► unit_tests ────────┐
                        │              └─► integration_tests ─┼─► review ─► release
                        ├─► docs ──────────────────────────────┤
                        └─► security_review ───────────────────┘
```

- `design` (`APPROVE_AFTER`) produces `openapi.yaml`, `V1__init.sql`, `docs/design.md`.
- `implement` (`ESCALATE_ON_RISK`): creating `pom.xml` triggers `PomChangeRule` and `MigrationPathRule`, so a human approves.
- `unit_tests` and `integration_tests` run in parallel with `docs` and `security_review`. `review` is the join.
- **Real failure:** `unit_tests` `attempt1` fixture contains a test with a wrong expectation (for example asserts code length 8 while the spec is 7), so the real `unit-tests` gate fails; the attempt is discarded and `attempt2` (given the failure text) passes.
- `release` is `APPROVE_AFTER` and is the release-readiness gate: it requires `review.data.recommendation == GO` (add an entry gate `review-go`).
- Expected metrics: exactly 1 retry, 1 rollback, 3 human approvals (`design`, `implement`, `release`).

### 9.2 Brownfield: "Links must be able to expire"

Workspace: `copy:shortener-service`. Initial graph: `analysis ─► design ─► implement ─► {regression_check, new_tests} ─► docs ─► review ─► release`.

- `analysis` runs the real JavaParser scan and reports impacted files with reasons (at minimum: `ShortLink`, `LinkRepository`, `JdbcLinkRepository`, the `LinkController` redirect path, `ShortenerService`, `openapi.yaml`, tests).
- The analyst emits a **graph patch** inserting `db_migration` (agent `developer`, `ESCALATE_ON_RISK`) between `design` and `implement`; the `REPLAN` event records why. The new migration is `V2__add_expiry.sql` (adds nullable `expires_at`).
- `implement` exit gates: `compile`, `regression-tests`. **Real failure:** `implement` `attempt1` changes redirect logic so a link with null expiry returns 410, breaking the existing redirect test; the gate fails, staging is discarded (workspace byte-identical), `attempt2` fixes it.
- New behavior: `POST` accepts optional `expiresAt` (future ISO-8601 instant, else 400); redirect of an expired link returns `410`; null expiry behaves exactly as v1.
- `new_tests` adds tests for expiry; `regression_check` reruns the full suite.
- Expected: 1 graph patch, migration approval, 1 retry, all v1 tests still green.

### 9.3 Ambiguous: "Make links more secure and add some analytics"

Workspace: `copy:shortener-service`.

- `requirements` outputs ambiguities:
  - `q-secure` (**blocking**): what does "secure" mean? options `https-only`, `domain-blocklist`, `authentication`, `abuse-prevention`.
  - `q-analytics` (non-blocking): assumption "click counts and daily buckets, no personal data", which v1 stats already provide, so no analytics code changes (no referrer or IP collection).
- The run pauses (`AWAITING_CLARIFICATION`, exit code 10). `answer ... q-secure "https-only"` resumes it; downstream nodes use `fixtureVariantFrom: q-secure`.
- Full downstream execution with variant `https-only` (validator rejects `http` URLs; tests updated).
- Then run `answer ... q-secure "domain-blocklist"`: the requirements artifact hash changes, the engine emits `INVALIDATED` for design, implement, tests, docs, review, release, revokes approvals, and re-executes them with the `domain-blocklist` fixtures. The report shows the before/after graph and the invalidation cascade.
- Non-answered non-blocking items appear as assumptions in the report.

### 9.4 Bug fix: "Trailing-dot hosts bypass the SSRF block"

Workspace: `copy:shortener-service`. The defect is real in v1: `java.net.URI` keeps the trailing dot of an absolute DNS name, so `http://localhost./admin` and `http://printer.local./` pass the exact-string host rules. Graph: `analysis ─► refactor ─► reproduce ─► fix ─► {review, docs} ─► release`.

- `refactor` (developer) extracts host classification from `UrlValidator` into `HostClassifier` with identical behavior; the unchanged suite in `regression-tests` proves it.
- `reproduce` (tester) adds `TrailingDotHostTest` before any fix; the `reproduces-defect` gate proves it fails for the right reason.
- `fix` (`APPROVE_AFTER`): `attempt1` special-cases only `localhost.` and fails the regression gate on `*.local.` and `*.localhost.`; `attempt2` canonicalizes the absolute name once and applies every rule to it.
- Expected: 1 retry, 1 rollback, 2 human approvals (`fix`, `release`), release notes 1.0.1. The baseline is not modified.

### 9.5 Resilience demo (test-only, no scenario folder)

An integration test with inline fixtures where every attempt fails with the same error proves: circuit breaker trip after two identical signatures, optional fallback, safe-stop with `SKIPPED` nodes, `incident.md` written, exit code 20.

---

## 10. Tests (all required; `mvn -q verify` must be green offline except where Maven Central is needed to resolve dependencies)

**Orchestrator unit tests**

1. Cycle, duplicate id, unknown dependency/gate/agent rejected at load.
2. A join node never starts until all dependencies are DONE.
3. Retries bounded by `maxRetries`; circuit breaker trips on identical signatures; fallback runs once.
4. Failed attempt leaves the workspace tree hash unchanged.
5. `PathGuard` rejects `..`, absolute paths, symlinks, and out-of-scope globs.
6. Stale approval hash throws `StaleApprovalException`; `--by` is required.
7. Invalidation is transitive, revokes approvals, and is a no-op when the hash is unchanged.
8. Graph patch: accepted when valid; rejected when it creates a cycle or touches a DONE node.
9. `RunState.fold(events)` equals live state (replay test).
10. Kill-and-resume does not re-execute DONE nodes (count agent calls).
11. Budget exhaustion produces safe-stop with remaining nodes `SKIPPED` and `incident.md`.
12. Event log is insert-only (update/delete triggers abort).
13. Each policy gate has positive and negative cases; each risk rule has hit and miss cases.
14. `MetricsCalculator` on a hand-built event list yields exact expected values (including net vs gross latency).
15. Property-style test: for 200 random failure patterns (seeded), no node is ever DONE with an unmet dependency or a failed exit gate.
16. ArchUnit: `core` has no dependency on `agents` or `cli`.

**Orchestrator integration tests (one per scenario, MOCK mode, real gates)** assert the event sequence and final metrics stated in Section 9, that reports are generated, and that the approvals table lists the expected approvers.

**Shortener tests:** Section 5.3, part of `shortener-service`.

---

## 11. Documentation deliverables (write after the code works; keep accurate)

- `README.md`: purpose, the guiding principle, how to run it both ways, via Docker (Docker is the only prerequisite) and locally (JDK 25, Maven 3.9+, `make`), each with build, demo, test and CLI steps, the CLI table, repo map, where to find sample reports.
- `docs/architecture.md`: component diagram (Mermaid), control flow of one node, orchestration model (DAG, gates, waves, joins), state model (event sourcing), governance model (autonomy levels, risk tiers, approvals), failure handling, and how each requirement in the assignment maps to code (a traceability table: requirement, component, test).
- `docs/decisions.md`: 8 to 21 ADRs, each with context, decision, alternatives, consequences. Must include: event sourcing; staging directory instead of git rollback; wave barrier scheduler; fixture-driven mock agents; engine-owned writes; hash-based invalidation plus graph patch; risk-tiered approvals; SQLite for the orchestrator.
- `docs/testing.md`: strategy, what each layer proves, how to run.
- `docs/scenarios/*.md`: for each scenario: requirement, decomposition graph, timeline, approvals, failure and recovery shown, metrics, lineage.
- `docs/engineering-summary.md`: plan and rationale, artifacts produced, risks/trade-offs/validation, assumptions, limitations.
- **Risks to state explicitly:** prompt injection through requirement text (mitigated: propose-only agents, path and policy gates); generated code executed during tests (mitigated by timeout, temp dir, env stripping; production needs a container sandbox); regex policies are shallow (production: Semgrep/CodeQL); SQLite single-writer (fine for single process, `EventStore` interface allows Postgres); wave barrier trades some parallelism for determinism; DNS-rebinding SSRF not covered; fixtures are canned by design.

---

## 12. Makefile targets

`build` (verify the shortener baseline, which also warms the Maven cache the gates use, then `mvn -q package`), `test` (shortener then orchestrator `mvn -q verify`), `scan` (`scripts/quality-scan.sh`; see Section 3), `demo-greenfield`, `demo-brownfield`, `demo-ambiguous`, `demo-all`, `bless-baseline`, `clean`. The Docker image exposes the same tasks (`demo-*`, `test`) plus direct CLI commands through `docker/entrypoint.sh`. Each `demo-*` target runs `scripts/demo-*.sh`, which invokes the CLI step by step (run, pending, approve with `--by demo-reviewer`, answer, resume, report) with echoed commands so the transcript is readable. Approvals in demos use a clearly named actor; they are never auto-approved by the engine.

## 13. Build phases (verification after each)

| Phase | Build | Verify |
|---|---|---|
| 1 | Parent POM, module skeleton, `.gitignore`, ArchUnit rule | `mvn -q verify` green |
| 2 | Shortener v1 in `shortener-service` with all tests and `openapi.yaml` | `mvn -q -f shortener-service/pom.xml verify` green |
| 3 | Core: graph, validator, loader, events, `SqliteEventStore`, `RunState`, artifact store, hashing | Tests 1, 9, 12 green |
| 4 | Workspace, staging, `PathGuard`, `Diff`, gate framework, all gates and risk rules | Tests 4, 5, 13 green |
| 5 | `NodeRunner`, `Scheduler`, budgets, circuit breaker, fallback, safe-stop, `IncidentWriter` | Tests 2, 3, 10, 11 green |
| 6 | Approvals, clarifications, CLI (all commands), exit codes | Test 6 green; manual `run`/`pending`/`approve` works |
| 7 | Invalidation, graph patch, `ReplanService`, metrics, report, Mermaid, lineage | Tests 7, 8, 14, 15 green |
| 8 | Agents, fixtures, four scenario workflows, scripts, Makefile, scenario integration tests | Every `make demo-*` succeeds; scenario tests green |
| 9 | (Optional) live agents for every role (the analyst's scan stays real) | Live agents compile and are tested with a stubbed HTTP client, including through the engine |
| 10 | Docs, committed sample reports, final acceptance run | Section 14 all pass |
| 11 | Quality and packaging: strict compiler, SAST, SCA, SBOM, DAST scans; Docker image | `make scan` passes; `docker run --rm agentic-sdlc test` and every `demo-*` pass in the image |

**If time runs short, cut in this order:** Phase 9 (live mode), fallback agent, graph-patch (keep hash invalidation), extra risk rules. **Never cut:** event log, real-tool gates, hash-bound approvals, safe-stop, metrics, the three scenarios.

## 14. Acceptance checklist (report pass/fail for each)

- [ ] AC-1 `make test` is green and includes all tests in Section 10.
- [ ] AC-2 Each `make demo-*` runs end to end and writes `runs/<id>/report.md`.
- [ ] AC-3 Invalid workflows are rejected at load with clear messages.
- [ ] AC-4 A join node starts only after all dependencies are DONE (test + visible in a report timeline).
- [ ] AC-5 A failing gate discards staging and the workspace is byte-identical afterward (test).
- [ ] AC-6 Retries are bounded; identical failures trip the circuit breaker; safe-stop marks the rest SKIPPED and writes `incident.md`.
- [ ] AC-7 Out-of-scope agent writes are rejected by `PathGuard`.
- [ ] AC-8 Approval on a changed artifact hash is rejected; every approval records approver, comment, and hash.
- [ ] AC-9 Changing an upstream artifact transitively invalidates downstream nodes and revokes approvals (ambiguous scenario shows it).
- [ ] AC-10 Brownfield shows a real JavaParser impact analysis, a graph patch inserting `db_migration`, a migration approval, and a rolled-back regression failure.
- [ ] AC-11 Greenfield shows a real failing test, retry with feedback, and success.
- [ ] AC-12 `RunState` rebuilt from events equals live state; kill-and-resume does not repeat DONE nodes.
- [ ] AC-13 Reports include Mermaid graph, timeline, metrics (success rate, retries, rollbacks, MTTR, net/gross latency), approvals, and lineage.
- [ ] AC-14 `shortener-service` satisfies every rule in Section 5 and its tests pass.
- [ ] AC-15 All docs in Section 11 exist and match the implemented behavior.

## 15. Out of scope (state in the docs as limitations, do not build)

Distributed execution and multi-node locking, a web UI, production-grade SAST/DAST *inside the gates* (the gates stay regex-based; `make scan` covers this repository's own code), authentication for the CLI (approver identity is asserted via `--by`), deployment or infrastructure provisioning, DNS-resolving SSRF protection.
