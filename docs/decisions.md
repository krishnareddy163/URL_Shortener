# Architecture Decision Records

Each record gives the context, the decision, the alternatives considered, and the consequences. ADRs 1 to 8 are the decisions the specification requires to be recorded; ADRs 9 to 12 cover choices made where the specification was open.

---

## ADR-01: Event sourcing with an append-only log

**Context.** The system must be auditable, resumable after a crash, able to replay decisions, and able to compute metrics after the fact. Mutable status tables lose history and drift from what actually happened.

**Decision.** Every fact is an `Event` appended to SQLite. `RunState` is a pure fold over events, and the live engine applies each event it appends through the same `apply` function. `RUN_STARTED` embeds the full graph and `REPLAN` embeds patches, so a run can be rebuilt from its log alone. The table is insert-only at two layers: the DAO has no update or delete, and triggers abort `UPDATE`/`DELETE`.

**Alternatives.** Mutable status rows plus an audit table (two sources of truth that can disagree). In-memory state with snapshots (resume depends on snapshot freshness).

**Consequences.** Resume, reports, metrics and `status` all derive from one source. `DurabilityTest` asserts that the fold of persisted events equals the live state and that crash-then-resume re-runs no DONE node. Every state transition must be representable as an event, which is why `REJECTED` carries the feedback comment and `NODE_DONE` carries the base hash.

## ADR-02: Staging directories and promotion records instead of git for rollback

**Context.** A failed attempt must leave the workspace byte-identical (R4). Invalidated nodes must also have their promoted files undone, and parallel nodes must not clobber each other.

**Decision.** Each attempt stages a fresh copy of the promoted workspace (excluding `target/`), applies the proposal there, and runs gates there. Failure deletes the copy. Success promotes *only the proposed files*, after checking each is unchanged since staging, and records pre-images under `promotions/<node>/<hash>/`. Invalidation restores those pre-images newest-first, and refuses if a file was changed by someone else since.

**Alternatives.** A git branch per attempt with reset/revert: heavier, adds a JGit dependency the spec excludes, and would still need custom conflict rules for parallel nodes. Snapshotting the whole tree per node: simple but costly, and it makes selective revert harder.

**Consequences.** Rollback is a directory delete. Tests hash the tree before and after (`RecoveryTest`, `WorkspaceTest`). Staging copies cost I/O proportional to the workspace, which is fine for service-sized projects. Proposals can add or modify files but not delete them; `DeletionRule` still guards any diff that does.

## ADR-03: Wave scheduler with a join barrier

**Context.** The workflow needs real parallelism (greenfield runs docs, security review and implement at once) with deterministic, reviewable ordering.

**Decision.** Each iteration runs all READY nodes concurrently on `Executors.newVirtualThreadPerTaskExecutor()` and then joins. Events are appended through one synchronized `RunLog`, so the order is total. Promotions take a write lock, stagings a read lock. Starting a node, failing it, and settling it each check "is the run stopped?" atomically under the `SafeStop` monitor. The 200-seed property test found two real races here: two parallel failures both recording `NODE_FAILED`, and a late-scheduled sibling starting after `SAFE_STOP`.

**Alternatives.** An event-driven scheduler that starts a node the moment its dependencies finish (more parallelism, harder to reason about and to replay). Purely sequential topological execution (no parallelism).

**Consequences.** A wave waits for its slowest node, which trades some parallelism for determinism. The first failure stops the run, and concurrent branches end SKIPPED rather than FAILED.

## ADR-04: Fixture-driven mock agents by default

**Context.** Demos and tests must be deterministic, offline and free (R9), yet gates must run on real code (R3).

**Decision.** `MockAgent` reads `fixtures/<node>[/fallback][/<variant>]/attempt<N>/`: a `proposal.json` plus real files mirroring the workspace. Failures come only from fixture content, such as a test asserting 8 characters or a redirect returning 410; there is no inject-failure flag. The attempt number is counted per *(node, agent, input hash)*: rejections continue the numbering and upstream changes restart it. LIVE mode swaps in model-backed agents for every role. The analyst keeps its real JavaParser scan and only its reasoning (impact and graph patch) comes from the model. Their output is gated exactly like fixture output, and each gets a `mock-<id>` fixture fallback with the same path scope, used once its retries or breaker are exhausted.

**Alternatives.** Record/replay of live model calls (brittle, and needs keys to record). Stub agents that emit trivial content (would make gates meaningless).

**Consequences.** Every gate result in the demos is genuine: the Maven failures are real. Fixtures are canned by design (a documented limitation), and adding a scenario means writing its fixtures.

## ADR-05: Engine-owned writes; agents only propose

**Context.** Agents are the least trusted component. Prompt injection or a bad fixture must not be able to write outside the agent's remit.

**Decision.** `Agent.propose` returns a `Proposal`. Agents see a read-only `WorkspaceView` and only the upstream artifacts they transitively depend on. `PathGuard` validates every path before any write, rejecting blank or NUL paths, backslashes, absolute or drive paths, `..` or `.` segments, symlinks anywhere along the path, and anything outside the agent's globs in `policies.yaml`. Only `Workspace` writes the promoted tree, and ArchUnit enforces that agents do not depend on `Workspace` or `Staging`.

**Alternatives.** Letting agents write into a sandbox and diffing afterwards (a larger attack surface, and harder to attribute).

**Consequences.** All-or-nothing apply, per-agent change control, and an auditable diff for every attempt. Agents cannot run tools; that is the engine's job, through gates.

## ADR-06: Hash-based invalidation plus graph patches

**Context.** Upstream outputs change (a clarification answer is revised, a rejected node re-runs), and some changes need new work that was not in the original plan.

**Decision.** (1) When a node's artifact hash differs from its previous DONE hash, every non-PENDING downstream node is invalidated. Its files are reverted, pending work discarded, and approvals revoked, all in one `REPLAN` summary; nothing happens if the hash is unchanged. (2) An agent may return `data.graphPatch`. The engine validates it (acyclic, known agents and gates, no change to the dependencies of settled nodes) *before* promotion, treating a bad patch as a failed attempt, and records it as `REPLAN`, which the fold applies.

**Alternatives.** Re-running the whole workflow on any change (wasteful, and it discards approvals of unaffected work). Letting agents mutate the graph directly (ungoverned).

**Consequences.** The ambiguous scenario re-plans six nodes and reverts their files cleanly. The brownfield analyst inserts `db_migration` with a recorded reason. Patches cannot remove nodes, since the spec's patch schema has no removal operation.

## ADR-07: Risk-tiered, hash-bound approvals

**Context.** Human attention is scarce. Approving everything invites rubber-stamping; approving nothing is unsafe.

**Decision.** There are three autonomy levels. `ESCALATE_ON_RISK` asks a human only when a risk rule fires (migration path, `pom.xml`, more than 200 changed lines, deletion). `APPROVAL_REQUESTED` carries the reasons, the file list, the unified diff and the artifact hash. `approve` re-hashes the retained staged files, compares them with the requested hash, and optionally checks an approver-supplied `--hash`. That flag is an addition to the spec's CLI: it lets a reviewer bind their approval to the exact version they read. `--by` is mandatory, and `reject` requires a comment, which becomes the agent's feedback.

**Alternatives.** Approval per run (too coarse). Approvals keyed by node id (stale approvals would silently apply to changed content).

**Consequences.** In the demos humans approve 3 of 10 nodes in greenfield, 2 of 9 in brownfield, and 2 in ambiguous (one of which was revoked by invalidation). The brownfield `implement` node escalated on nothing and auto-promoted, because its diff was small and touched no build or migration file.

## ADR-08: SQLite for the orchestrator

**Context.** The orchestrator is a single-process CLI that needs durable, queryable, tamper-resistant storage with zero operations overhead.

**Decision.** One `events.db` per run (`org.xerial:sqlite-jdbc`), in WAL mode, with one synchronized connection. Events and artifacts share the file.

**Alternatives.** PostgreSQL (operational overhead for a prototype). Flat JSON-lines files (no triggers, and no atomic append-plus-query).

**Consequences.** SQLite is single-writer, which is fine within one process. `EventStore` is an interface, so a Postgres implementation could replace it for multi-process use; the tests already run the engine against an in-memory store.

## ADR-09: Java 25 and dependency versions

**Context.** The specification requires Java 25 and pins version lines (Spring Boot 3.x, JavaParser 3.26.x, JUnit 5, Picocli 4.7.x). The code must also pass strict static analysis and dependency scanning.

**Decision.** Build with release 25 throughout. Pin the latest stable versions within each required line, checked against Maven Central at build time: Spring Boot 3.5.16, JUnit 5.14.4, Jackson 2.22.3, Mockito 5.24.0, ArchUnit 1.5.1, sqlite-jdbc 3.53.4.0, Logback 1.5.38, SLF4J 2.0.20. There are three deliberate deviations:
- **JavaParser 3.28.2, not 3.26.x.** 3.26.x parses at most Java 21 syntax, so it rejected the Java 22+ unnamed variables (`_`) in the shortener, and the analyst's scan reported parse errors. A Java 25 codebase needs a Java 25 parser (`LanguageLevel.JAVA_25`).
- **Security overrides of Spring Boot-managed versions** in the shortener (`tomcat.version` 10.1.60, `jackson-bom.version` 2.22.3, `log4j2.version` 2.26.1). Trivy found 11 CVEs, 3 of them CRITICAL (Tomcat), in Boot 3.5.16's managed versions.
- **Awaitility (test scope) in the shortener**, replacing a `Thread.sleep` polling loop that SonarQube flagged. It is already in the orchestrator's test stack and on the dependency allowlist.

Quality gates are part of the build: `javac -Xlint:all -Werror`, SpotBugs + FindSecBugs (fail on any finding; the few reviewed exclusions are listed with justifications in `spotbugs-exclude.xml`), and PMD + CPD. `make scan` adds SonarQube, Trivy (SBOM SCA and secrets) and an OWASP ZAP API scan.

**Consequences.** Language features used include records, sealed interfaces, pattern matching, virtual threads, `InetAddress.ofLiteral` (Java 22+, parses IP literals without DNS) and unnamed variables. JavaParser is configured for Java 21 syntax, which covers every construct in the shortener sources it scans.

## ADR-10: Clarification answers are folded into the requirements artifact

**Context.** Answers must influence downstream work, and a changed answer must visibly invalidate that work.

**Decision.** The requirements node's raw output is the *base* artifact. When every blocking question is answered, the engine stores a *final* artifact with `data.answers` and the recorded assumptions (including each unanswered non-blocking question's `assumptionIfUnanswered`), and completes the node with that final hash. Re-answering re-folds the base, producing a new hash, which feeds into ordinary invalidation. Downstream nodes select their fixture variant from the answer (`fixtureVariantFrom`), and the variant is part of their input hash.

**Alternatives.** Keeping answers as run-level metadata outside artifacts. Invalidation would then need a separate trigger, and lineage would not show them.

**Consequences.** Only one invalidation mechanism exists. Reports show answers and assumptions, and answering with the same value is a verified no-op.

## ADR-11: Metric definitions

**Context.** "Net latency excludes approval and clarification wait", but the ambiguous run also sits idle between completing and a human reopening it with a new answer.

**Decision.** Gross latency runs from `RUN_STARTED` to the last `RUN_COMPLETED`. Human wait is the *union* (overlaps counted once) of these intervals: `APPROVAL_REQUESTED` to the node's next `APPROVED`/`REJECTED`/`INVALIDATED`, `CLARIFICATION_REQUESTED` to `ANSWERED`, and `RUN_COMPLETED` to a later `RESUMED`. Net latency is gross minus human wait. Success rate is nodes DONE with no failed gate or discarded attempt, divided by nodes in the final graph. MTTR is the mean, over nodes that recovered, of the time from first failure to next `NODE_DONE`. Rollbacks equal discarded attempts, and fallbacks are reported separately.

**Consequences.** `MetricsCalculatorTest` pins exact values, including overlapping waits. The budget clock is a different measure (active engine time) and is reported by `status`.

## ADR-12: Shortener product decisions left open by the spec

**Context.** Several product behaviors were unspecified.

**Decision.**
- An alias requested for a URL already shortened under another code returns `409 URL_ALREADY_SHORTENED`, since `url_hash` is unique. The same alias again returns `200`.
- Timestamps are stored as UTC wall-clock values, so per-day buckets are UTC days. The previous code bucketed by JVM zone.
- `404`, `405` and `415` also use the error envelope; the previous code turned unknown paths into `500`.
- Embedded credentials and integer, hex or shortened IP hosts (`http://2130706433/`) are rejected. `java.net.URI` accepts these hosts, which the tests showed.
- The rate limiter uses exact integer token arithmetic (floating point missed a refill at exactly 3 s), and evicts idle buckets past 100,000 keys.
- Brownfield: links without `expiresAt` behave exactly as in v1, and `expiresAt` is omitted from responses when absent.
- Ambiguous `domain-blocklist`: `UrlValidator` keeps a no-argument constructor, so v1 tests remain valid.

**Consequences.** Each decision is covered by a shortener test and documented in `shortener-service/docs/design.md`.

## ADR-13: Typed event payloads and narrow collaborators

**Context.** Event details were `Map<String, Object>` read through `event.text("key")`, so a misspelled key silently became `null`. Engine components shared one `RunContext` holding every dependency, which hid what each one used, and `NodeRunner` and `ReportWriter` each did several jobs.

**Decision.**
- Each event type has one record in the sealed `Payload` interface. `RunLog.append(nodeId, payload)` is the only way to write an event, and readers use `event.payload(Type.class)` or pattern-match on `event.payload()`. Decoding is strict (unknown fields fail), and derived accessors are `@JsonIgnore`d.
- `Engine` is the single composition root. Every collaborator gets only what it uses through its constructor: `RunPaths` for file layout, `ArtifactRecorder` for artifact persistence, `GateRunner` for gate execution and recording, `BudgetGuard` for budgets, `ReplanService` (injected, not constructed), and `SafeStop` for the stop protocol.
- `NodeRunner` keeps the retry, breaker and fallback loop. `AttemptExecutor` runs one attempt, and `Settlement` routes an accepted attempt to clarification, approval or promotion.
- `ReportWriter` renders an ordered list of `ReportSection`s. `EventDescriber` is shared by the report timeline and the console trace.

**Consequences.** Adding an event type fails compilation until `Payload.classFor`, `RunState.apply` and `EventDescriber` handle it. Run databases written before this change do not decode, because `RUN_STARTED` now nests its provenance in a `metadata` record and decoding is strict. Runs are disposable demo output and the demos regenerate them. `FileTrees` stays a static I/O utility: it is used only behind `Workspace`, which tests exercise against real temp directories.

## ADR-14: Test-first bug fixes with a reproduction gate

**Context.** A bug-fix pipeline can "fix" something its tests never exercised. A test written after the fix may pass whether or not the bug is gone. The assignment's brownfield scope includes refactors and bug fixes, not only features.

**Decision.** The `bugfix` workflow orders the work as refactor, reproduce, fix. The refactor is behavior-preserving, and the unchanged suite in `regression-tests` proves it. The new `reproduces-defect` gate runs the real `mvn test` on the tester's staged regression test and passes only if the build fails, the failures are assertion failures in the test classes the proposal added, and nothing else fails. The fix is then judged by the same test in `regression-tests`. The scenario uses a defect that is real in the committed v1 baseline (trailing-dot absolute host names bypass the SSRF block), not a planted one.

**Alternatives.** Trusting the tester's claim that a test reproduces the bug. Checking only that the test fails, which would accept compile errors and broken unrelated tests as "reproductions".

**Consequences.** A reproduction test that already passes, does not compile, or breaks other tests is rejected with a precise reason, and the incomplete first fix in the scenario is caught by the reproduction test. The committed baseline keeps the defect on purpose, because greenfield must reproduce v1 byte for byte, and the scenario is the documented fix.

## ADR-15: One writer per run

**Context.** Two processes driving one run, for example `resume` in two terminals, would each fold the log, start the same nodes and promote files over each other. SQLite serializes statements, not workflows.

**Decision.** Every operation that changes a run takes an exclusive OS file lock on `runs/<id>/run.lock` (`RunLock`) and holds it until the orchestrator closes. Read-only commands never take it. The OS releases the lock if the holder dies, so a crash never leaves a run locked. Different runs are independent directories, databases and workspaces, so they scale out across processes.

**Alternatives.** A lock row in SQLite (it survives crashes and needs expiry). An advisory PID file (stale after a crash). Serializing every CLI command, including reads (it would block `status` during long runs).

**Consequences.** A second writer fails fast with the holder's PID. Scaling a single run across machines would still need a shared event store with optimistic concurrency on `seq`; `EventStore` is the seam for that.

## ADR-16: Container sandbox for gate builds

**Context.** Build gates compile and run generated tests. With fixtures that code is reviewed, but in LIVE mode it is model output, and running it on the host exposed the user's files, network and credentials to it. A timeout and a stripped environment limit damage but do not contain it.

**Decision.** `BuildRunner` runs each build in a container when `build.sandbox` allows it: `--network none`, a read-only root, `--cap-drop ALL`, `no-new-privileges`, user `65534`, memory/CPU/PID limits, and the host Maven cache mounted read-only as Maven's "tail" repository with `-o`. The staged tree is streamed in as a tar archive on stdin and built in a tmpfs, so nothing on the host is writable by the build, including the staged files that promotion will copy. `AUTO` (default) uses the sandbox when Docker and the image are present and otherwise falls back to the host. `DOCKER` fails closed, and `NONE` disables it. A timeout removes the container. Exit 137 is reported as a probable memory-limit kill.

**Alternatives.** Bind-mounting the staging directory, which was tried first: Docker Desktop's file sharing intermittently did not see freshly created directories, failing 4 of about 15 full-suite runs, and it let the build modify staged files. Always requiring Docker (breaks the zero-dependency local path and our own container image). A VM per build (heavier than a prototype needs).

**Consequences.** Generated code cannot reach the network or write to the host. Scenario tests take about 30% longer because each build streams its sources in and resolves from the read-only cache. Five consecutive full `verify` runs passed with the sandbox active. Where the sandbox is unavailable, the run banner states that gates run on the host.

## ADR-17: Model usage in the event log; no external LLM tooling

**Context.** LIVE mode recorded which model ran and the prompt and response hashes, but not what each call cost or how long it took. A reply cut off at `ANTHROPIC_MAX_TOKENS` surfaced only as "invalid JSON", which hid the actual cause. LLM applications often add a tracing platform (Langfuse, OpenTelemetry), an agent-graph framework (LangGraph), retrieval (RAG) or MCP servers, so each was weighed against this codebase.

**Decision.** `AnthropicClient` reads `usage.input_tokens`, `usage.output_tokens` and `stop_reason` from every reply and adds them, with the call's wall-clock time, to a per-attempt `UsageMeter` in the `AgentContext`. The engine records the total as a typed `ModelUsage` in `AGENT_CALLED`, including for attempts that fail gates or return malformed output, because those calls are billed too. `MetricsCalculator` sums it, and the report, `status` and the console trace show it when a model was called. MOCK runs record no usage, so their events and reports are unchanged. A `max_tokens` stop is a failed attempt with a message that names the limit. No other tooling is added:

- *Langfuse / OpenTelemetry:* the append-only event log already holds every prompt hash, response hash, attempt, gate result and approval, and the report is computed from it. A second trace store would need network access and a new dependency, and it could drift from the log that is the only state (ADR-01).
- *LangGraph:* the workflow graph, wave scheduler, durable resume, re-planning and human interrupts are this project's core deliverable, and they are hash-bound in ways a generic framework does not provide (ADR-03, ADR-06, ADR-07). LangGraph is also a Python library.
- *RAG:* the analyst already retrieves context with a real JavaParser scan, and the `impact-files-exist` gate checks its claims against that scan. Embedding search over a service-sized codebase would add nondeterminism without adding coverage.
- *MCP:* exposing `approve` as a tool would let an agent take the human reviewer's role, which the governance model forbids. Agents need no tools because they only propose (ADR-05).

**Alternatives.** Returning usage from `Agent.metadata()`: that method is read before the call, and one agent instance can serve parallel nodes, so per-call state there would race. Dollar-cost estimates: prices change and the model comes from the environment, so tokens are recorded and cost is left to the reader.

**Consequences.** Token spend per node, per attempt and per run can be computed from `events.db` alone, and retries show what they cost. Tested in `LiveAgentsTest` (metering and truncation), `LiveModeEngineTest` (usage on rolled-back attempts, run totals, report row), `PayloadTest` and `MetricsCalculatorTest` (no usage row in MOCK runs).

## ADR-18: Build plugins are allowlisted like dependencies

**Context.** The developer agent may edit `pom.xml`. `dependency-allowlist` checked only project dependencies, and `PomChangeRule` asks for human approval only after the gates have passed. The `compile` gate therefore ran a model-written POM before any human saw it. A plugin such as `exec-maven-plugin` or `maven-antrun-plugin`, a plugin dependency, a new parent POM, a build extension or a new repository can each run or fetch arbitrary code during that build. The container sandbox (ADR-16) contains this when Docker is available, but the `AUTO` host fallback runs the build on the user's machine, online.

**Decision.** `dependency-allowlist`, which workflows already run before `compile`, also requires every added plugin, plugin dependency and parent POM to be on a new `buildAllowlist` in `policies.yaml`. A plugin without a `groupId` counts as `org.apache.maven.plugins`. It refuses any added `<repository>`, `<pluginRepository>` or `<extension>`. As with dependencies, only additions are checked, so an existing POM is never re-flagged. The allowlist holds exactly the plugins the shortener already uses.

**Alternatives.** Forcing the Docker sandbox in LIVE mode would contain the build but also lock out users without Docker, and it would not stop a build from pulling in a harmful plugin. Running host builds with `-o` would block downloads but not plugins already in the local cache. Escalating pom changes to a human before building would add an approval to every pom change, including allowlisted ones.

**Consequences.** A model can no longer add build-time code without a policy change that a human commits. Adding a plugin legitimately means extending `buildAllowlist`. Tested in `PolicyGatesTest` (each bypass is refused, allowlisted plugins pass, existing entries are not re-flagged, and the committed shortener POM passes) and by the scenario tests, whose fixture POMs pass through the gate.

## ADR-19: Node tasks in the workflow file

**Context.** The first LIVE run of the bug-fix scenario safe-stopped at `reproduce`. The live developer agent serves both `refactor` and `fix`, and its prompt contained the requirement ("Fix it without changing any other behavior") but nothing about which node it was working on. At `refactor` it fixed the defect. The unchanged suite does not cover the defect, so the change passed and was promoted without review. `reproduces-defect` then correctly rejected every reproduction test, including the fixture fallback's, because the defect no longer existed.

**Decision.** A workflow node may carry an optional `task`. `AttemptExecutor` passes it in the `AgentContext`, it is part of the prompt hash, and every live agent's prompt states it with an instruction to do only that step. The four scenario workflows set a task on every node whose agent is shared, and on every bug-fix node. Graph patches may not set a task: patches are agent-proposed, and a patched-in task would let one agent write instructions for another.

**Alternatives.** Distinct agent ids per node (for example `developer-refactor`), which would multiply path scopes and fixture fallbacks. Stating only the node id, which does not carry intent. Relying on gates alone: the gates did stop the bad run, but only after the refactor was promoted and the retries were spent.

**Consequences.** The rerun of the same LIVE scenario completed `refactor` with the defect intact, reproduced it on the first attempt, and reached the human approval of `fix`. MOCK runs are unchanged: fixtures ignore the task, and the prompt hash includes the task only when one is set. Tested in `WorkflowLoaderTest`, `LiveAgentsTest` and `GraphPatchTest`.

## ADR-20: Every role must prove its output

**Context.** A review of the delivery against role-level criteria found gaps. The requirements agent produced acceptance criteria but no user stories. The design had no diagrams. The reviewer returned findings, but nothing showed that it had reviewed every submitted file or how each finding was resolved. The QA role produced tests but no coverage report, the shortener was at 89.7% line and 81.9% branch coverage, and nothing mapped acceptance criteria to tests. The shortener had logging but no audit trail.

**Decision.** Each role's output is checked by a gate, so the evidence is proved rather than claimed.

- `requirements-complete` requires user stories in the form "As a ..., I want ..., so that ...".
- `design-diagrams` requires a Mermaid diagram in every design document a design step writes.
- `review-complete` requires `reviewedFiles` to cover every file the upstream steps submitted, and every finding to carry a status (FIXED, ACCEPTED, DEFERRED) and a resolution. An unfixed HIGH or CRITICAL finding forces NO_GO. The live reviewer is shown every submitted file in full; files beyond its prompt budget are listed as not shown, so the gate fails rather than accepting a claim it could not have made.
- A greenfield `qa_report` step (the tester) joins the two test steps. `functional-coverage` requires every acceptance criterion to be mapped to tests that exist in the staged tree. `test-coverage` runs JaCoCo in the build sandbox and checks line and branch coverage against `coverage` in `policies.yaml`: a 100% target, minimums, and exceptions that each state why a class cannot reach the target. The sandbox hands back only the fixed JaCoCo CSV path, after a marker line; nothing else leaves the container. A passing gate records its measurement (`GATE_PASSED.detail`), and the report's **Quality evidence** section shows user stories, diagrams, measured coverage, the functional coverage matrix and review coverage with every finding's resolution.
- The shortener records every state-changing request in an `audit_event` table (time, opaque client key, method, path, status), and its tests were raised to 100% branch and 100% line coverage on all functional classes. No class is excluded from the floor check: `main()` is covered by a test and `Sha256` takes the algorithm name so its `NoSuchAlgorithmException` handler is testable. Unreachable validator branches that `java.net.URI` already rules out were removed rather than excused.
- Both builds enforce a JaCoCo coverage floor, and `scripts/coverage-report.py` writes `docs/coverage.md`, which names every class below 100% in both projects. CI publishes it with the JaCoCo reports.

**Alternatives.** A coverage report written by the agent: a fixture or a model could state any number, so the engine measures it instead. 100% for the orchestrator now: it is at 91.4% line and 78.7% branch, and closing that is a larger effort than the prototype justifies; the floor prevents regressions and the inventory names every gap. Excluding code with JaCoCo filters: that hides future gaps in the same class, so nothing is excluded; code that looked unreachable was made testable instead.

**Consequences.** Greenfield gained a step (10 nodes). A review can no longer skip files silently, and a coverage drop fails the build and the QA step. The shortener suite grew from 88 to 135 tests. Tested in `EvidenceGatesTest`, `QualityGatesTest`, `LiveAgentsTest`, `BuildRunnerTest`, `BuildSandboxTest` and `GreenfieldScenarioTest`, which asserts the measured coverage, the functional coverage and the complete review end to end.

## ADR-21: A git history of each run's workspace

**Context.** Agents do not use git: the engine promotes staged files and keeps promotion records for rollback (ADR-02). The work therefore had no commit history that a developer could read with ordinary tools.

**Decision.** `GitHistory` turns every promotion into one commit in the run's workspace, and every invalidation revert into a "Revert ..." commit. A commit is authored by the agent role, committed by the engine, and has the first sentence of the rationale as its subject. The body is the rationale plus `Node`, `Agent`, `Artifact`, `Run` and, when a human approved the step, `Approved-by` trailers. Only the step's own files are committed, so parallel steps are never mixed. `NODE_DONE` records the commit id, and the report lists the commits. Git runs with no shell, a stripped environment, no global or system configuration (so a user's hooks and signing never apply) and a timeout. `PathGuard` rejects any path through `.git`, `FileTrees` already ignores it, and a missing or failing git switches history off with one warning instead of failing the run.

**Alternatives.** Git as the rollback mechanism: rejected in ADR-02, and still worse for parallel steps. Committing only at the end of a run: loses the per-step attribution and approvals that make the history useful.

**Consequences.** `git log` in `runs/<id>/workspace` reads as the run's change history, and it stays consistent with the event log, which remains the source of truth. Tested in `GitHistoryTest`, `PathGuardTest` and `GreenfieldScenarioTest` (one commit per promoting step, approvals in the trailers, a clean working tree).

