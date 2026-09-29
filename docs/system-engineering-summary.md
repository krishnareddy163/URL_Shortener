# Engineering Summary

The AI SDLC artifacts are indexed in [ai-sdlc/](ai-sdlc/README.md); how AI assistance was used to build the system is in [ai-assisted-development.md](ai-assisted-development.md).

## Plan and rationale

The assignment's differentiator is **governed orchestration**, so the engine is the primary deliverable and the URL shortener is its workload. The build followed the specification's phases in order, verifying each before moving on:

| Phase | Built | Verified by |
|---|---|---|
| 1 | Parent POM (Java 25, pinned versions), module skeleton, ArchUnit boundary | `ArchitectureTest` |
| 2 | Shortener v1 with an OpenAPI contract and 88 tests | `mvn -f shortener-service/pom.xml verify` |
| 3 | Graph model, loader and validator; events, SQLite store, `RunState` fold, hashing | `WorkflowLoaderTest`, `SqliteEventStoreTest`, `DurabilityTest` |
| 4 | Workspace, staging, `PathGuard`, `Diff`; 18 gates, 4 risk rules, build sandbox | `WorkspaceTest`, `PathGuardTest`, `PolicyGatesTest`, `QualityGatesTest`, `BuildRunnerTest` |
| 5 | `NodeRunner` (with `AttemptExecutor`, `Settlement`, `GateRunner`), wave `Scheduler`, budgets, breaker, fallback, safe-stop, incident | `SchedulerTest`, `RecoveryTest`, `DurabilityTest` |
| 6 | Approvals, clarifications, the CLI and exit codes | `ApprovalServiceTest`, CLI runs |
| 7 | Invalidation with file revert, graph patch, metrics, report, Mermaid, lineage | `InvalidationTest`, `GraphPatchTest`, `MetricsCalculatorTest`, `SchedulerPropertyTest` |
| 8 | Agents and fixtures, four scenario workflows, demo scripts, Makefile | `*ScenarioTest`, `make demo-all` |
| 9 | Live agents for all seven roles (Anthropic Messages API over `HttpClient`), with scoped fixture fallbacks | `LiveAgentsTest`, `LiveModeEngineTest` (stubbed transport) |
| 10 | Documentation, committed sample reports, acceptance run | this document |

**Key design choices** (details in [decisions.md](decisions.md)):
- Event sourcing is the only state.
- Staging plus pre-image records replace git for rollback and revert.
- A wave scheduler with a join barrier, where the first failure stops the run atomically.
- Fixture agents emit real code, so gate results are genuine.
- Answers are folded into artifacts, so one invalidation mechanism covers every upstream change.
- Approvals are risk-tiered and hash-bound.

## What the prototype demonstrates, per scenario

| Scenario | Decomposition | Orchestration shown | Validation shown |
|---|---|---|---|
| Greenfield | 10 nodes, 2 parallel waves, two joins | 3 human checkpoints, parallel branches, retry with feedback | Real `mvn test` failure (code length 8 vs 7) rolled back; greenfield output equals the committed baseline |
| Brownfield | 8 nodes plus 1 inserted at runtime | Real JavaParser impact analysis; **graph patch** inserts `db_migration`; risk-escalated migration approval; `implement` auto-promotes | A real v1 regression (302 became 410) rolled back and fixed; v1 suite and new expiry tests green |
| Ambiguous | 7 nodes, fixture variants chosen by the answer | Blocking clarification; a changed answer **invalidates 6 nodes**, reverts their files, revokes an approval, and re-plans | Both interpretations built and tested by real gates |

## Artifacts produced

- `orchestrator/`: the engine, agents and CLI (packaged as `orchestrator.jar`), with 160 tests.
- `shortener-service/`: shortener v1 with `openapi.yaml`, Flyway `V1__init.sql`, 88 tests, and design, operations and release documentation.
- `scenarios/*`: three workflows plus fixtures, including brownfield expiry (`V2__add_expiry.sql`, 9 changed sources, 2 new test classes) and two ambiguous variants (https-only, domain blocklist).
- `policies/policies.yaml`, `scripts/`, `Makefile`.
- `docs/`: architecture, 21 ADRs, testing, 4 walkthroughs, 4 committed sample reports.
- For each run: `events.db`, `workspace/`, `artifacts/` (a JSON export of each artifact), `promotions/`, `gate-logs/`, `report.md`, and `incident.md` on safe-stop.

## Risks, trade-offs and mitigations

| Risk | Mitigation in this prototype | What production would need |
|---|---|---|
| **Prompt injection** through requirement text or upstream artifacts | Agents only propose; `PathGuard` per-agent scopes; policy and build gates; live agents' replies are framed as data and schema-validated, and their files pass the same scopes and gates as any agent's; humans approve risky diffs | Content provenance, output classifiers, stricter tool isolation |
| **Generated code is executed** during gates | With Docker available (`build.sandbox`, default `AUTO`), every build gate runs in a container: no network, read-only root, all capabilities dropped, user `nobody`, memory/CPU/process limits, the Maven cache mounted read-only, and the staged sources streamed in as a tar archive so the build cannot write anything on the host. `DOCKER` mode fails closed. Always: the 120 s timeout kills the process tree and the container, and the environment is stripped (API keys never reach it). Without Docker, `AUTO` falls back to the host, and the run banner says which applies | A dedicated build farm with per-run VMs (for example Firecracker) and signed base images |
| **The engine's runtime gates on generated code use regex policies**, which comments, strings or obfuscation can evade | Applied to *added* lines; dependency changes parsed as XML (DTDs refused). The repository's own code is additionally held to SpotBugs/FindSecBugs, PMD, SonarQube, Trivy and ZAP (all 0 findings; see [testing.md](testing.md#code-quality-and-security-scans)) | Run Semgrep/CodeQL and SCA as gates on each staged proposal |
| **SQLite is single-writer** | One writer per run, enforced by an OS file lock (`RunLock`, ADR-15); readers never block; independent runs scale out as separate processes | `EventStore` is an interface; a Postgres implementation with optimistic concurrency on `seq` for multiple workers per run |
| **The wave barrier trades parallelism for determinism** | Waves are usually short; branches that pause do not block independent ones | An event-driven scheduler if throughput matters |
| **DNS-rebinding SSRF** (shortener) | Literal IPs and local names are rejected at creation, including integer and hex IPv4 forms | Resolve and pin at redirect time, or an egress proxy |
| **Fixtures are canned** | Chosen deliberately for determinism; failures are real because gates are real. LIVE mode puts all seven roles on a model (the analyst reasons over a real scan), gated identically, with fixture fallbacks | Evaluation suites that score live runs over many requirements |
| **Approver identity is asserted via `--by`** | Mandatory, recorded, and hash-bound | Authenticated identity (SSO) and signed approvals |
| **Revert conflicts** during invalidation | Detected by hash; the engine safe-stops instead of overwriting | A merge strategy or human resolution flow |

## Code quality and security status

CI ([.github/workflows/ci.yml](../.github/workflows/ci.yml)) runs all of the following plus the demos on every push. On the final code, both projects have: 0 compiler warnings under `-Werror`; 0 SpotBugs/FindSecBugs findings; 0 PMD/CPD violations; SonarQube 0 bugs, 0 vulnerabilities, 0 hotspots and 0 code smells (quality gate OK); JaCoCo coverage of 100% line and 100% branch (functional classes) for the shortener and 90.8% line and 78.7% branch for the orchestrator, with floors in both builds and every gap in [coverage.md](coverage.md); 0 known-vulnerable dependencies (Trivy on CycloneDX SBOMs); no secrets. The shortener also passes an OWASP ZAP API scan with 0 failures and 0 warnings. Veracode was not run (it needs a commercial account). Reproduce with `make scan`; details are in [testing.md](testing.md#code-quality-and-security-scans).

## Assumptions

- One writer process per run (enforced by `RunLock`); separate runs can execute in parallel processes.
- Gate builds run in the container sandbox when Docker and `maven:3.9-eclipse-temurin-25` are available (`make build` pulls the image); otherwise on the host with timeout and environment stripping only.
- The workload is a Maven project; build gates call `mvn`, configurable in `policies.yaml`.
- Local `mvn` and a Maven cache populated once (`make build` does this); after that, runs work offline.
- Humans interact through the CLI and are trusted to be who `--by` says.
- The shortener runs as a single instance: its rate limiter and click queue are in memory. `LinkRepository` is the storage seam; running several instances would need a shared rate-limit store (for example Redis) and a durable click queue, which are documented in its design and out of scope for v1.

## Limitations

- Out of scope by specification: distributed execution and locking, a web UI, production SAST/DAST inside the gates, CLI authentication, deployment, DNS-resolving SSRF protection.
- LIVE mode (all seven roles; the analyst's scan is always real) is verified with a stubbed transport, including an engine-level test in which live output fails its gates and the fixture fallback completes the node. `scripts/live-run.sh` was also run end to end against the real API endpoint with an invalid key: every agent made a real HTTP call, got 401, tripped its breaker and fell back to its fixture agent, and the run completed after two interactive approvals. Real model output has been exercised on one scenario only: two LIVE runs of `bugfix` with `claude-sonnet-5-5`. The first safe-stopped because the `refactor` step fixed the defect it was meant to preserve, which led to node tasks (ADR-19). The second preserved the defect, reproduced it on the first attempt, and produced a fix that passed the regression suite on its second attempt and reached human approval. Greenfield, brownfield and ambiguous have not been run live, and there is no evaluation across many requirements.
- Proposals can add or modify files but cannot delete them. `DeletionRule` exists and is tested on diffs, but no fixture exercises it end to end.
- Graph patches can add nodes and edges or remove edges, but cannot remove nodes (the specification's patch schema has no removal).
- The wall-clock budget is checked between attempts, so a running gate is bounded by its own 120 s timeout rather than interrupted by the budget.
- Report latencies are machine-dependent; the metric definitions are fixed by tests.
- Test coverage is 100% of lines and 100% of branches (functional classes) for the shortener, but 90.8% of lines and 78.7% of branches for the orchestrator itself (the CLI package is lowest, at 68% of lines, because the demos exercise it end to end). A floor stops regressions and [coverage.md](coverage.md) lists every class below 100%; reaching the target for the orchestrator is open work.

## Acceptance checklist (Section 14)

| Item | Result | Evidence |
|---|---|---|
| AC-1 `make test` green, includes all Section 10 tests | **PASS** | 135 shortener + 160 orchestrator tests, 0 failures; mapping in [testing.md](testing.md#mapping-to-section-10) |
| AC-2 Each `make demo-*` runs end to end and writes `report.md` | **PASS** | `make demo-all` exits 0 in about 70 s; reports committed under `docs/sample-runs/` |
| AC-3 Invalid workflows rejected at load with clear messages | **PASS** | `WorkflowLoaderTest` (named cycle, duplicates, unknown references, negative retries, unknown fields); CLI exit 2 in `ResilienceScenarioTest` |
| AC-4 A join starts only after all dependencies are DONE | **PASS** | `SchedulerTest`, `GreenfieldScenarioTest`; greenfield timeline shows `review` NODE_STARTED at seq 64, after the 4 NODE_DONEs |
| AC-5 A failing gate discards staging; workspace byte-identical | **PASS** | `RecoveryTest.failedAttemptLeavesThePromotedWorkspaceByteIdentical`, `WorkspaceTest` |
| AC-6 Bounded retries, breaker, safe-stop with SKIPPED and `incident.md` | **PASS** | `RecoveryTest`, `DurabilityTest`, `ResilienceScenarioTest` (exit 20 via the CLI) |
| AC-7 Out-of-scope writes rejected by PathGuard | **PASS** | `PathGuardTest`, `RecoveryTest.outOfScopeWrites...` |
| AC-8 Changed-hash approval rejected; approvals record approver, comment and hash | **PASS** | `ApprovalServiceTest`; approvals tables in the sample reports |
| AC-9 Upstream change invalidates transitively and revokes approvals | **PASS** | `InvalidationTest`, `AmbiguousScenarioTest`; [ambiguous report](sample-runs/ambiguous/report.md) |
| AC-10 Brownfield: JavaParser analysis, graph patch, migration approval, rolled-back regression | **PASS** | `BrownfieldScenarioTest`, `JavaCodebaseScannerTest`; [brownfield report](sample-runs/brownfield/report.md) |
| AC-11 Greenfield: real failing test, retry with feedback, success | **PASS** | `GreenfieldScenarioTest`; [greenfield report](sample-runs/greenfield/report.md) seq 47 to 65 |
| AC-12 Replayed state equals live state; kill-and-resume skips DONE nodes | **PASS** | `DurabilityTest` |
| AC-13 Reports include graph, timeline, metrics, approvals, lineage | **PASS** | `ReportWriter`; section assertions in `GreenfieldScenarioTest`; sample reports |
| AC-14 Shortener satisfies Section 5 and its tests pass | **PASS** | 88 tests: `CodeGeneratorTest` (length, alphabet), `ShortenerServiceTest` (collision retry, exhaustion, aliases), `UrlValidatorTest` (every rule), `RateLimiterTest` (refill and exhaustion with an injected clock), `LinkApiIntegrationTest` (all seven integration cases) |
| AC-15 Section 11 docs exist and match behavior | **PASS** | README and `docs/` as listed above; numbers quoted from the sample runs and test reports |
