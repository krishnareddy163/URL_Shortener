# Testing

## How to run

With Docker only (see the [README](../README.md#how-to-run)):

```sh
docker build -t agentic-sdlc .
docker run --rm agentic-sdlc test                   # both suites; runs offline from the image's Maven cache
```

On a host with the project toolchain:

```sh
make test                                           # both suites (about 45 s with a warm Maven cache)
mvn -q -f shortener-service/pom.xml verify  # shortener only: 135 tests
mvn -q verify                                       # orchestrator only: 160 tests
mvn -q -pl orchestrator test -Dtest='*ScenarioTest' # the four scenario integration tests
```

The scenario tests execute real `mvn` builds of the generated shortener, so the shortener's dependencies must already be in the local Maven cache. `make test` verifies the baseline first, which guarantees that. No test needs network access to a model or an API key.

## Strategy: what each layer proves

| Layer | Proves | Speed |
|---|---|---|
| **Engine unit tests** with in-process gates (`TestEngine`, `ScriptedAgent`, `InMemoryEventStore` or SQLite) | The orchestration semantics in isolation: scheduling, retries, breaker, fallback, rollback, approvals, invalidation, patches, budgets, replay | milliseconds |
| **Property test** (200 seeded random DAGs and failure patterns) | Invariants that hold for *any* graph: no node is DONE before its dependencies, no node is DONE after a failed gate in the same attempt, nothing settles after `SAFE_STOP`, and every run ends consistently | about 2 s |
| **Gate and policy tests** against the real `policies.yaml` | Each security, compliance and quality gate has positive and negative cases; each risk rule has hit and miss cases; the build sandbox enforces its timeout, strips the environment and condenses feedback (using a fake build tool script) | fast |
| **Agent tests** | Fixture resolution (attempts, variants, fallback directory); real JavaParser routes from the baseline; live agents against a stubbed HTTP transport (schema validation, key never leaked, config from the environment) | fast |
| **Scenario integration tests** (MOCK mode, **real gates**) | End-to-end behavior from spec §9, with real Maven compile and test runs on generated code: event sequences, approvals and metrics. Greenfield also proves its output equals the committed baseline | about 30 s (run concurrently) |
| **Shortener tests** | The product itself: unit tests for every validation, generation and rate-limiting rule, and MockMvc integration tests against real H2 and Flyway with a controllable clock | about 5 s |

## Mapping to Section 10

| # | Requirement | Test |
|---|---|---|
| 1 | Cycle, duplicate id, unknown dependency/gate/agent rejected at load | `WorkflowLoaderTest` |
| 2 | A join node never starts until all dependencies are DONE | `SchedulerTest.siblingsRunConcurrentlyAndTheJoinWaitsForAllDependencies` (a latch proves the siblings overlap), `GreenfieldScenarioTest` |
| 3 | Retries bounded; breaker trips on identical signatures; fallback runs once | `RecoveryTest` (bounded retries, breaker, one fallback round, fallback recovery), `ResilienceScenarioTest` |
| 4 | Failed attempt leaves the workspace tree hash unchanged | `RecoveryTest.failedAttemptLeavesThePromotedWorkspaceByteIdentical`, `WorkspaceTest.discardedStagingLeavesWorkspaceByteIdentical` |
| 5 | PathGuard rejects `..`, absolute paths, symlinks, out-of-scope globs | `PathGuardTest`, `RecoveryTest.outOfScopeWritesAreRejectedByThePathGuard`, `WorkspaceTest.viewDoesNotFollowSymlinkedDirectoriesOutOfTheWorkspace` |
| 6 | Stale approval hash throws; `--by` required | `ApprovalServiceTest` (stale hash, tampered staging, already-approved, `--by` mandatory, reject with feedback), `ClarificationServiceTest` (a promotion conflict after an answer safe-stops so resume re-runs the node), `ResilienceScenarioTest.approveRequiresBy...` |
| 7 | Invalidation transitive, revokes approvals, no-op when the hash is unchanged | `InvalidationTest`, `AmbiguousScenarioTest`, `WorkspaceTest.revertAllIsAllOrNothingWhenAnyRecordConflicts` |
| 8 | Graph patch accepted when valid; rejected on a cycle or touching a DONE/RUNNING node | `GraphPatchTest` (also: a malformed patch fails the `graph-patch` gate instead of crashing the engine) |
| 9 | `RunState.fold(events)` equals live state | `DurabilityTest.foldOfPersistedEventsEqualsTheLiveState` (also after reopening from SQLite) |
| 10 | Kill-and-resume does not re-execute DONE nodes; one writer per run (`RunLockTest`) | `DurabilityTest.killAndResumeDoesNotReExecuteDoneNodes` (an `Error` simulates the process dying mid-node; agent calls are counted) |
| 11 | Budget exhaustion: safe-stop, SKIPPED, `incident.md` | `DurabilityTest.budgetExhaustion...`, `DurabilityTest.wallClockBudgetExcludesTimeSpentWaitingForHumans` |
| 12 | Event log is insert-only | `SqliteEventStoreTest.updateAndDeleteAreRejectedByTriggers`; typed payloads round-trip and decode strictly: `PayloadTest` |
| 13 | Each policy gate positive/negative; each risk rule hit/miss | `PolicyGatesTest`, `QualityGatesTest`, `BuildRunnerTest`, `BuildSandboxTest` (container isolation: no network, read-only root and cache, host files untouched, fail-closed), `FailureSignatureTest`, `ReproducesDefectGateTest` (every verdict of the test-first gate) |
| 14 | `MetricsCalculator` exact values, net vs gross | `MetricsCalculatorTest` (including overlapping waits and post-completion idle) |
| 15 | Property test over 200 seeded failure patterns | `SchedulerPropertyTest` |
| 16 | ArchUnit: `core` does not depend on `agents` or `cli` | `ArchitectureTest` (also: agents cannot use `Workspace` or `Staging`) |
| Scenarios | Event sequences and metrics from §9, reports generated, approvers listed | `GreenfieldScenarioTest`, `BrownfieldScenarioTest`, `AmbiguousScenarioTest`, `BugfixScenarioTest` (refactor proven by the unchanged suite, reproduction proven to fail first, incomplete fix rolled back), `ResilienceScenarioTest` |

## Continuous integration

[.github/workflows/ci.yml](../.github/workflows/ci.yml) runs on every push to `main` and every pull request:

- **lint:** `scripts/lint.sh` (also `make lint`): `actionlint` and `zizmor` for the workflow, `shellcheck` for every script, `gitleaks` over the whole git history, and Trivy's misconfiguration scan of the `Dockerfile`. Every tool runs from a pinned image, and every action is pinned to a commit SHA, with Dependabot proposing updates weekly (Maven majors, the Docker base image and the shortener baseline are updated by hand; see `.github/dependabot.yml`).
- **quality:** `scripts/quality-scan.sh` (strict builds, SpotBugs/FindSecBugs, PMD/CPD, all tests with build gates in the container sandbox, JaCoCo, CycloneDX, Trivy SCA and secret scan, OWASP ZAP), then all four demos. SonarQube runs when its secrets are configured. Reports, surefire output and any scenario incidents are uploaded as artifacts.
- **docker:** builds the image, scans it with Trivy (HIGH and CRITICAL vulnerabilities with a fix, in OS packages and application jars), then runs the full test suite and all four demos inside it with `--network none` (the README's Option A).
- **live-smoke** (every push to `main` of this repository, and manually from the Actions tab; its condition excludes pull requests and forks): `scripts/live-smoke.sh` (also `make live-smoke`) starts a real-model run of the bug-fix scenario and fails unless the model API recorded tokens. Any governed outcome passes (completed, paused for a human, or safe-stopped), because live output varies; nothing is approved. It needs the `ANTHROPIC_API_KEY` secret and the `ANTHROPIC_MODEL` repository variable, and each run spends about 170k input tokens of API credit. On a push, a missing secret or variable skips it with a notice, and a failed run (for example during an API outage) is reported on the job without failing the workflow; a manual run fails in both cases. It has its own concurrency group and is never cancelled mid-run, while the other jobs are cancelled when a newer push supersedes them.

The workflow passes `actionlint` and `zizmor`, and every step runs the same commands verified locally. Every job, `live-smoke` included, has passed on GitHub; the live-smoke run reached the model on all four calls and paused at the `fix` approval, as designed.

## Code quality and security scans

Enforced on every build (`mvn verify`, both projects): `javac -Xlint:all -Werror`, SpotBugs + FindSecBugs at the lowest threshold, PMD and CPD. Any finding fails the build. `make scan` (`scripts/quality-scan.sh`, which needs Docker) adds the scanners below and fails on any finding.

| Check | Tool | Orchestrator | Shortener |
|---|---|---|---|
| Compiler warnings | `javac -Xlint:all -Werror` | 0 | 0 |
| SAST | SpotBugs 4.10 + FindSecBugs 1.14 | 0 findings | 0 findings |
| Static analysis / duplication | PMD 7 + CPD | 0 / 0 | 0 / 0 |
| SonarQube (26.9 Community, Sonar way) | bugs / vulnerabilities / hotspots / code smells | 0 / 0 / 0 / 0, quality gate OK | 0 / 0 / 0 / 0, quality gate OK |
| Coverage (JaCoCo, target 100%) | line / branch; floor enforced by `mvn verify` | 90.8% / 78.7%; floor 88% / 75%; every gap in [coverage.md](coverage.md) | **100% / 100%** (functional classes); floor 100% / 100%; `ShortenerApplication.main()` and `Sha256` catch excluded — see [coverage.md](coverage.md) |
| SBOM | CycloneDX 1.6 | `orchestrator/target/bom.json` (11 runtime components) | `META-INF/sbom/application.cdx.json` in the jar (46 components) |
| SCA | Trivy on the SBOMs | 0 vulnerabilities | 0 vulnerabilities (after patching Tomcat, Jackson, Log4j) |
| Secrets | Trivy secret scan of the repository | none | none |
| Secrets in history | gitleaks over every commit (`make lint`) | none (one reviewed test fixture in `.gitleaksignore`) | none |
| Container | Trivy config (`Dockerfile`) and image scan (CI) | 0 misconfigurations; 0 fixable HIGH/CRITICAL in OS packages and application jars | n/a |
| CI supply chain | zizmor, actions pinned to SHAs, scanner images pinned to versions | 0 findings | n/a |
| DAST | OWASP ZAP API scan driven by `openapi.yaml` (40 URLs) | n/a (CLI, no HTTP surface) | 118 rules passed, 0 failures, 0 warnings |

**Coverage.** The target is 100% line and branch coverage on all functional code. The shortener enforces 100% line and 100% branch via the `coverage-floor` JaCoCo execution. Two classes are excluded from the floor and documented in [coverage.md](coverage.md): `ShortenerApplication.main()` (Spring Boot entry point unreachable via `@SpringBootTest`) and `Sha256`'s `NoSuchAlgorithmException` catch (impossible per JCA spec). The orchestrator is at 90.8% line and 78.7% branch coverage; its floor stops regressions, and [coverage.md](coverage.md) lists every class below 100%. `make coverage` regenerates that file, and CI publishes it with both JaCoCo HTML reports.

The image scan skips the image's pre-warmed Maven cache (`/home/app/.m2`). It holds only the dependencies of build plugins, which run offline, unprivileged and at build time. Scanned in full, it has 18 fixable HIGH findings (for example `plexus-utils` and an old `jackson-databind` used by plugins), which can be fixed only by upstream plugin releases. Both runtime SBOMs, which the SCA row covers, are clean. The image runs as an unprivileged `app` user (UID 1000), because its gates execute generated code.

Every SpotBugs exclusion is scoped to a class and pattern, with its justification, in `spotbugs-exclude.xml`. The exclusions cover constructor-injected collaborators, the build runner's intentional process execution, and Spring endpoint review markers.

**Not run: Veracode.** It is a commercial SaaS that needs an account and an upload; no credentials are available in this environment. SpotBugs/FindSecBugs, SonarQube, Trivy and ZAP cover the same SAST, SCA and DAST categories locally. The artifacts are ready to upload (`orchestrator/target/orchestrator.jar`, `shortener-service/target/shortener-service-1.0.0.jar`).

## Bugs the tests caught during development

- **Rate limiter refill (shortener).** Floating-point drift meant a token did not refill at exactly 3 s. The fix uses integer token units.
- **Numeric hosts (shortener).** `java.net.URI` accepts `http://2130706433/` (which is 127.0.0.1) as a host. An explicit check was added.
- **Parallel failure races (engine).** The property test found two in the same place. First, two branches failing in the same wave both recorded `NODE_FAILED`, and one could promote after `SAFE_STOP`. Second, found only under CPU contention in the full suite: a sibling scheduled late appended `NODE_STARTED` after another branch's `SAFE_STOP`, leaving a RUNNING node in a stopped run. Starting, failing and settling are now all atomic under the `SafeStop` monitor. The invariant covers all three, and was checked with 8 consecutive runs (1,600 scenarios) under deliberate CPU load.

## Known gaps

- LIVE mode is tested automatically against a stubbed transport (`LiveAgentsTest`, and `LiveModeEngineTest` through the real engine); the only automated check against the real API is the `live-smoke` CI job, which runs on every push to `main`. Against the real API, the `bugfix` scenario was run twice with `claude-sonnet-5-5` (see the engineering summary's limitations); the other scenarios have not been run live.
- Scenario tests depend on local `mvn` and a populated Maven cache.
- Latency values in the committed sample reports are machine-dependent; the metric *definitions* are pinned by `MetricsCalculatorTest`.
