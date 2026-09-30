# Run report: `greenfield-20260930-194355`

## Run summary

| | |
|---|---|
| Workflow | greenfield |
| Requirement | Build a URL shortener service: create short links (with an optional custom alias), redirect by code, and report click statistics. It must not become an SSRF or abuse vector, must rate-limit link creation, and must never log personal data. |
| Status | **COMPLETED** |
| Exit reason | all 10 nodes DONE |
| Events | 89 |
| Agent calls / attempts | 11 / 11 |

## Clarifications and assumptions

No blocking questions were raised.

Recorded assumptions (`requirements`):

- Single-instance deployment: the rate limiter and click queue are in memory
- No authentication on the API in v1
- Link expiry is out of scope for v1
- q-storage: H2 with Flyway for the prototype; storage sits behind LinkRepository so it can be swapped
- q-click-overflow: Drop and count them; redirect latency matters more than exact analytics

## Workflow graph

```mermaid
graph TD
  requirements["requirements<br/>requirements · AUTO<br/>DONE"]
  design["design<br/>architect · APPROVE_AFTER<br/>DONE"]
  implement["implement<br/>developer · ESCALATE_ON_RISK<br/>DONE"]
  unit_tests["unit_tests<br/>tester · AUTO<br/>DONE"]
  integration_tests["integration_tests<br/>tester · AUTO<br/>DONE"]
  qa_report["qa_report<br/>tester · AUTO<br/>DONE"]
  docs["docs<br/>docs · AUTO<br/>DONE"]
  security_review["security_review<br/>reviewer · AUTO<br/>DONE"]
  review["review<br/>reviewer · AUTO<br/>DONE"]
  release["release<br/>docs · APPROVE_AFTER<br/>DONE"]
  requirements --> design
  design --> implement
  implement --> unit_tests
  implement --> integration_tests
  integration_tests --> qa_report
  unit_tests --> qa_report
  design --> docs
  design --> security_review
  docs --> review
  qa_report --> review
  security_review --> review
  review --> release
  classDef pending fill:#ffffff,stroke:#495057
  classDef running fill:#d0ebff,stroke:#1864ab
  classDef awaiting_approval fill:#fff3bf,stroke:#e67700
  classDef awaiting_clarification fill:#fff3bf,stroke:#e67700
  classDef done fill:#d3f9d8,stroke:#2b8a3e
  classDef failed fill:#ffe3e3,stroke:#c92a2a
  classDef skipped fill:#f1f3f5,stroke:#868e96,stroke-dasharray:4
  class requirements done
  class design done
  class implement done
  class unit_tests done
  class integration_tests done
  class qa_report done
  class docs done
  class security_review done
  class review done
  class release done
```

## Timeline

| Seq | Time (UTC) | Node | Event | Actor | Detail |
|---:|---|---|---|---|---|
| 1 | 19:43:56.474 |  | RUN_STARTED | engine | workflow greenfield |
| 2 | 19:43:56.503 | requirements | NODE_STARTED | engine | agent requirements, variant default |
| 3 | 19:43:56.512 | requirements | AGENT_CALLED | requirements | requirements attempt 1 |
| 4 | 19:43:56.516 | requirements | GATE_PASSED | engine | artifact-metadata |
| 5 | 19:43:56.517 | requirements | GATE_PASSED | engine | requirements-complete |
| 6 | 19:43:56.523 | requirements | NODE_DONE | engine | artifact d1f94f38a55e, 0 file(s) |
| 7 | 19:43:56.527 | design | NODE_STARTED | engine | agent architect, variant default |
| 8 | 19:43:56.527 | design | GATE_PASSED | engine | path-allowlist |
| 9 | 19:43:56.529 | design | AGENT_CALLED | architect | architect attempt 1 |
| 10 | 19:43:56.531 | design | GATE_PASSED | engine | artifact-metadata |
| 11 | 19:43:56.532 | design | GATE_PASSED | engine | design-diagrams |
| 12 | 19:43:56.533 | design | GATE_PASSED | engine | path-allowlist |
| 13 | 19:43:56.535 | design | GATE_PASSED | engine | schema-valid |
| 14 | 19:43:56.538 | design | GATE_PASSED | engine | secret-scan |
| 15 | 19:43:56.542 | design | APPROVAL_REQUESTED | engine | hash 1d0b735a0615; [APPROVE_AFTER: human sign-off required, MigrationPathRule: schema migration changed (src/main/resources/db/migration/V1__init.sql), DiffSizeRule: 297 changed lines exceeds 200] |
| 16 | 19:43:56.545 |  | RUN_PAUSED | engine | waiting {design=AWAITING_APPROVAL} |
| 17 | 19:43:57.586 | design | APPROVED | demo-reviewer | by demo-reviewer on 1d0b735a0615: Contract, V1 schema and risk list reviewed |
| 18 | 19:43:57.663 | design | NODE_DONE | demo-reviewer | artifact 1d0b735a0615, 3 file(s), commit 4217928781d5 |
| 19 | 19:43:58.287 |  | RESUMED | engine |  |
| 20 | 19:43:58.299 | docs | NODE_STARTED | engine | agent docs, variant default |
| 21 | 19:43:58.301 | implement | NODE_STARTED | engine | agent developer, variant default |
| 22 | 19:43:58.302 | security_review | NODE_STARTED | engine | agent reviewer, variant default |
| 23 | 19:43:58.302 | implement | GATE_PASSED | engine | path-allowlist |
| 24 | 19:43:58.310 | security_review | AGENT_CALLED | reviewer | reviewer attempt 1 |
| 25 | 19:43:58.312 | docs | AGENT_CALLED | docs | docs attempt 1 |
| 26 | 19:43:58.315 | security_review | GATE_PASSED | engine | artifact-metadata |
| 27 | 19:43:58.316 | security_review | GATE_PASSED | engine | review-complete |
| 28 | 19:43:58.316 | docs | GATE_PASSED | engine | artifact-metadata |
| 29 | 19:43:58.317 | docs | GATE_PASSED | engine | path-allowlist |
| 30 | 19:43:58.318 | docs | GATE_PASSED | engine | secret-scan |
| 31 | 19:43:58.321 | implement | AGENT_CALLED | developer | developer attempt 1 |
| 32 | 19:43:58.323 | security_review | NODE_DONE | engine | artifact 94945665bf47, 0 file(s) |
| 33 | 19:43:58.330 | implement | GATE_PASSED | engine | artifact-metadata |
| 34 | 19:43:58.330 | implement | GATE_PASSED | engine | path-allowlist |
| 35 | 19:43:58.336 | implement | GATE_PASSED | engine | secret-scan |
| 36 | 19:43:58.341 | implement | GATE_PASSED | engine | forbidden-api |
| 37 | 19:43:58.349 | implement | GATE_PASSED | engine | dependency-allowlist |
| 38 | 19:43:58.352 | implement | GATE_PASSED | engine | no-raw-ip-logging |
| 39 | 19:43:58.396 | docs | NODE_DONE | engine | artifact cd35e2d72520, 2 file(s), commit 61ac172ea483 |
| 40 | 19:44:00.527 | implement | GATE_PASSED | engine | compile |
| 41 | 19:44:00.535 | implement | APPROVAL_REQUESTED | engine | hash f8125539dd95; [PomChangeRule: build file changed (pom.xml), DiffSizeRule: 1707 changed lines exceeds 200] |
| 42 | 19:44:00.540 |  | RUN_PAUSED | engine | waiting {implement=AWAITING_APPROVAL} |
| 43 | 19:44:01.722 | implement | APPROVED | demo-reviewer | by demo-reviewer on f8125539dd95: Dependencies are allowlisted; implementation matches the design |
| 44 | 19:44:01.808 | implement | NODE_DONE | demo-reviewer | artifact f8125539dd95, 34 file(s), commit 8c7b43bb8482 |
| 45 | 19:44:02.530 |  | RESUMED | engine |  |
| 46 | 19:44:02.543 | integration_tests | NODE_STARTED | engine | agent tester, variant default |
| 47 | 19:44:02.544 | unit_tests | NODE_STARTED | engine | agent tester, variant default |
| 48 | 19:44:02.554 | integration_tests | AGENT_CALLED | tester | tester attempt 1 |
| 49 | 19:44:02.557 | unit_tests | AGENT_CALLED | tester | tester attempt 1 |
| 50 | 19:44:02.569 | unit_tests | GATE_PASSED | engine | artifact-metadata |
| 51 | 19:44:02.570 | integration_tests | GATE_PASSED | engine | artifact-metadata |
| 52 | 19:44:02.570 | unit_tests | GATE_PASSED | engine | path-allowlist |
| 53 | 19:44:02.570 | integration_tests | GATE_PASSED | engine | path-allowlist |
| 54 | 19:44:02.574 | integration_tests | GATE_PASSED | engine | secret-scan |
| 55 | 19:44:02.574 | unit_tests | GATE_PASSED | engine | secret-scan |
| 56 | 19:44:07.330 | unit_tests | GATE_FAILED | engine | unit-tests: mvn test failed (exit 1): CodeGeneratorTest.generatesSevenCharacterCodes:13 Expected size: 8 but was: 7 |
| 57 | 19:44:07.343 | unit_tests | ATTEMPT_DISCARDED | engine | rolled back attempt 1 (unit-tests, sig c93b06229350) |
| 58 | 19:44:07.353 | unit_tests | AGENT_CALLED | tester | tester attempt 2 |
| 59 | 19:44:07.368 | unit_tests | GATE_PASSED | engine | artifact-metadata |
| 60 | 19:44:07.369 | unit_tests | GATE_PASSED | engine | path-allowlist |
| 61 | 19:44:07.373 | unit_tests | GATE_PASSED | engine | secret-scan |
| 62 | 19:44:09.619 | integration_tests | GATE_PASSED | engine | unit-tests |
| 63 | 19:44:09.688 | integration_tests | NODE_DONE | engine | artifact 351b80301898, 3 file(s), commit fda5a2378df5 |
| 64 | 19:44:13.564 | unit_tests | GATE_PASSED | engine | unit-tests |
| 65 | 19:44:13.631 | unit_tests | NODE_DONE | engine | artifact 6395dfeeed45, 13 file(s), commit ff89f6767b44 |
| 66 | 19:44:13.644 | qa_report | NODE_STARTED | engine | agent tester, variant default |
| 67 | 19:44:13.646 | qa_report | AGENT_CALLED | tester | tester attempt 1 |
| 68 | 19:44:13.655 | qa_report | GATE_PASSED | engine | artifact-metadata |
| 69 | 19:44:13.659 | qa_report | GATE_PASSED | engine | functional-coverage |
| 70 | 19:44:20.995 | qa_report | GATE_PASSED | engine | test-coverage |
| 71 | 19:44:20.997 | qa_report | NODE_DONE | engine | artifact 28dfb764ac9b, 0 file(s) |
| 72 | 19:44:21.013 | review | NODE_STARTED | engine | agent reviewer, variant default |
| 73 | 19:44:21.015 | review | AGENT_CALLED | reviewer | reviewer attempt 1 |
| 74 | 19:44:21.046 | review | GATE_PASSED | engine | artifact-metadata |
| 75 | 19:44:21.047 | review | GATE_PASSED | engine | review-complete |
| 76 | 19:44:28.165 | review | GATE_PASSED | engine | regression-tests |
| 77 | 19:44:28.166 | review | NODE_DONE | engine | artifact 9912dbfd0dca, 0 file(s) |
| 78 | 19:44:28.177 | release | NODE_STARTED | engine | agent docs, variant default |
| 79 | 19:44:28.178 | release | GATE_PASSED | engine | review-go |
| 80 | 19:44:28.179 | release | AGENT_CALLED | docs | docs attempt 1 |
| 81 | 19:44:28.189 | release | GATE_PASSED | engine | artifact-metadata |
| 82 | 19:44:28.189 | release | GATE_PASSED | engine | path-allowlist |
| 83 | 19:44:28.189 | release | GATE_PASSED | engine | secret-scan |
| 84 | 19:44:28.190 | release | APPROVAL_REQUESTED | engine | hash 793599919e3a; [APPROVE_AFTER: human sign-off required] |
| 85 | 19:44:28.192 |  | RUN_PAUSED | engine | waiting {release=AWAITING_APPROVAL} |
| 86 | 19:44:29.306 | release | APPROVED | demo-reviewer | by demo-reviewer on 793599919e3a: Review GO, full suite green: release 1.0.0 |
| 87 | 19:44:29.364 | release | NODE_DONE | demo-reviewer | artifact 793599919e3a, 1 file(s), commit bfdc6077b3dc |
| 88 | 19:44:29.945 |  | RESUMED | engine |  |
| 89 | 19:44:29.951 |  | RUN_COMPLETED | engine |  |

## Metrics

| Metric | Value |
|---|---|
| Success rate (first-pass DONE / nodes) | 90.0% (9/10) |
| Retries (discarded attempts) | 1 {unit_tests=1} |
| Rollbacks (staging discarded) | 1 |
| Fallbacks | 0 |
| MTTR | 6.300 s over 1 node(s) |
| End-to-end latency (gross) | 33.477 s |
| Human wait excluded | 3.346 s |
| End-to-end latency (net) | 30.130 s |
| Approvals requested / granted / rejected | 3 / 3 / 0 |
| Clarifications requested | 0 |
| Invalidations / replans | 0 / 0 |
| Gate failures by gate | {unit-tests=1} |

## Approvals

| Seq | Node | Decision | Who | When (UTC) | Hash approved | Comment | Revoked later |
|---:|---|---|---|---|---|---|---|
| 17 | design | APPROVED | demo-reviewer | 19:43:57.586 | `1d0b735a0615` | Contract, V1 schema and risk list reviewed | no |
| 43 | implement | APPROVED | demo-reviewer | 19:44:01.722 | `f8125539dd95` | Dependencies are allowlisted; implementation matches the design | no |
| 86 | release | APPROVED | demo-reviewer | 19:44:29.306 | `793599919e3a` | Review GO, full suite green: release 1.0.0 | no |

## Decision lineage

- **release** `793599919e3a`: Release notes for 1.0.0 summarizing capabilities and known limitations; release readiness requires the GO review and a human sign-off.
  - **review** `9912dbfd0dca`: Joined review of implementation, tests and docs. The full suite passes in the review gate; no high-severity findings.
    - **unit_tests** `6395dfeeed45`: The unit-tests gate showed generatesSevenCharacterCodes expecting 8 characters while the design and CodeGenerator.LENGTH specify 7. Corrected the expectation...
      - **implement** `f8125539dd95`: Implemented the approved design: Spring Boot web + JDBC + Flyway on H2, service rules exactly as specified, and a global error envelope. The pom.xml and the ...
        - **design** `1d0b735a0615`: Layered design (api/service/domain/storage) with uniqueness enforced by the database, validation at creation time, and an asynchronous bounded click pipeline...
          - **requirements** `d1f94f38a55e`: Normalized the request into an HTTP service with three endpoints, explicit validation and abuse controls, and aggregate-only analytics. Non-blocking gaps are...
            - requirement: "Build a URL shortener service: create short links (with an optional custom alias), redirect by code, and report click statistics. It must not become an SSRF ..."
    - **integration_tests** `351b80301898`: MockMvc integration tests against a real H2 database covering create/redirect, 404, idempotency, alias conflicts, invalid input, rate limiting and per-day st...
      - **implement** `f8125539dd95` (see above)
      - **design** `1d0b735a0615` (see above)
    - **docs** `cd35e2d72520`: Operator- and developer-facing documentation: how to run and test, configuration, observability counters and failure behavior.
      - **design** `1d0b735a0615` (see above)
    - **security_review** `94945665bf47`: Threat-model review of the approved design before implementation lands.
      - **design** `1d0b735a0615` (see above)

Artifacts by node:

| Node | Artifact | Files | Rationale |
|---|---|---:|---|
| requirements | `d1f94f38a55e` | 0 | Normalized the request into an HTTP service with three endpoints, explicit validation and abuse controls, and aggregate-only analytics. Non-blocking gaps are recorded as assumptions so design can proceed. |
| design | `1d0b735a0615` | 3 | Layered design (api/service/domain/storage) with uniqueness enforced by the database, validation at creation time, and an asynchronous bounded click pipeline. The OpenAPI contract and V1 schema make the design reviewable before code exists. |
| implement | `f8125539dd95` | 34 | Implemented the approved design: Spring Boot web + JDBC + Flyway on H2, service rules exactly as specified, and a global error envelope. The pom.xml and the size of the change require human approval under policy. |
| unit_tests | `6395dfeeed45` | 13 | The unit-tests gate showed generatesSevenCharacterCodes expecting 8 characters while the design and CodeGenerator.LENGTH specify 7. Corrected the expectation; all other tests unchanged. |
| integration_tests | `351b80301898` | 3 | MockMvc integration tests against a real H2 database covering create/redirect, 404, idempotency, alias conflicts, invalid input, rate limiting and per-day statistics with a controllable clock. |
| qa_report | `28dfb764ac9b` | 0 | Unit and integration suites are both in place. Every acceptance criterion maps to the tests that prove it; the test-coverage gate measures line and branch coverage and names every class below the 100% target. |
| docs | `cd35e2d72520` | 2 | Operator- and developer-facing documentation: how to run and test, configuration, observability counters and failure behavior. |
| security_review | `94945665bf47` | 0 | Threat-model review of the approved design before implementation lands. |
| review | `9912dbfd0dca` | 0 | Joined review of implementation, tests and docs. The full suite passes in the review gate; no high-severity findings. |
| release | `793599919e3a` | 1 | Release notes for 1.0.0 summarizing capabilities and known limitations; release readiness requires the GO review and a human sign-off. |

## Quality evidence

### User stories (`requirements`)

- As a link creator, I want to shorten a long http(s) URL, so that I can share a short, memorable link
- As a link creator, I want to choose a custom alias, so that the short link is recognizable
- As a visitor, I want a short link to redirect me to its URL immediately, so that following it feels like a normal link
- As a link owner, I want per-day click statistics for my link, so that I can see how it is used
- As the service operator, I want link creation rate-limited and unsafe targets rejected, so that the service cannot be abused for SSRF or spam
- As the service operator, I want an audit trail of every change request, so that I can trace who changed what and when without storing personal data

Acceptance criteria: 9.

### Design documents

| Node | Document | Mermaid diagrams |
|---|---|---:|
| `design` | `docs/design.md` | 2 |

### Measured evidence from gates

| Node / gate | Result |
|---|---|
| `qa_report / functional-coverage` | 9 of 9 acceptance criteria mapped to 32 existing tests |
| `qa_report / test-coverage` | line 100.0% (400/400), branch 100.0% (183/183), target 100% over 32 classes; every class at target |

### Functional coverage (`qa_report`)

| Acceptance criterion | Tests |
|---|---|
| POST /api/v1/links returns 201 with {code, shortUrl, url, createdAt}; the same normalized URL without an alias returns the existing link with 200 | [LinkApiIntegrationTest#createThenRedirectReturns302WithLocation, LinkApiIntegrationTest#creatingTheSameNormalizedUrlAgainIsIdempotent, ShortenerServiceTest#returnsExistingLinkForSameNormalizedUrl] |
| Custom aliases are 3-32 chars of [A-Za-z0-9_-], reserved words api/actuator/health/stats are rejected (400), duplicates return 409 | [ShortenerServiceTest#createsLinkWithCustomAlias, ShortenerServiceTest#rejectsInvalidOrReservedAliases, LinkApiIntegrationTest#invalidAliasAndMalformedBodiesReturn400, LinkApiIntegrationTest#duplicateAliasReturns409, ShortenerServiceTest#rejectsAliasThatIsAlreadyTaken] |
| Generated codes are 7 base62 characters from SecureRandom; collisions retry up to 5 times, then 500 CODE_GENERATION_FAILED | [CodeGeneratorTest#generatesSevenCharacterCodes, CodeGeneratorTest#generatedCodesUseOnlyTheBase62Alphabet, ShortenerServiceTest#retriesGeneratedCodeAfterCollision, ShortenerServiceTest#failsWithCodeGenerationFailedAfterFiveCollisions, ApiExceptionHandlerTest#codeGenerationFailureIsA500WithItsCode] |
| URLs must be http/https, <= 2048 chars, have a host, and not target localhost, *.local, or loopback/link-local/private/unspecified literal IPs | [UrlValidatorTest#rejectsNonHttpSchemes, UrlValidatorTest#rejectsUrlsLongerThan2048Characters, UrlValidatorTest#requiresHost, UrlValidatorTest#rejectsLocalHostNames, UrlValidatorTest#rejectsNonPublicLiteralAddresses, UrlValidatorEdgeCaseTest#nonPublicAddressesAreRejected, LinkApiIntegrationTest#invalidUrlReturns400] |
| GET /{code} returns 302 with Location, or 404; the click is recorded asynchronously and never blocks the redirect | [LinkApiIntegrationTest#createThenRedirectReturns302WithLocation, LinkApiIntegrationTest#unknownCodeReturns404WithErrorEnvelope, ShortenerServiceTest#resolveEnqueuesClickWithoutWaiting, ClickRecorderTest#aFailedClickDoesNotStopTheWorker] |
| GET /api/v1/links/{code}/stats returns totalClicks, lastAccessedAt and per-UTC-day buckets, or 404 | [LinkApiIntegrationTest#statsReportTotalsLastAccessAndPerDayBuckets, LinkApiIntegrationTest#statsForUnknownCodeReturns404] |
| Link creation is rate limited per client (default 20/minute) with 429; the client key is a hash and raw IPs are never logged | [RateLimiterTest#allowsBurstUpToCapacityThenRejects, LinkApiIntegrationTest#rateLimitReturns429AfterTwentyCreatesPerMinute, AuditTrailIntegrationTest#createdAndRejectedRequestsAreAuditedWithoutPersonalData] |
| Every non-2xx/3xx response uses {"error":{"code","message"}} | [LinkApiIntegrationTest#unknownCodeReturns404WithErrorEnvelope, LinkApiIntegrationTest#unmappedRoutesAndMethodsUseTheErrorEnvelope, ApiExceptionHandlerTest#unexpectedFailuresHideTheirDetails] |
| Every state-changing request is recorded in an audit trail with its time, opaque client key, method, path and final status; reads are not recorded | [AuditTrailIntegrationTest#createdAndRejectedRequestsAreAuditedWithoutPersonalData, AuditTrailIntegrationTest#readsAreNotAudited, AuditFilterTest#recordsStateChangingRequestsWithTheirFinalStatus] |

### Code review (`security_review`): GO

Reviewed 3 of 3 submitted files.

| Severity | File | Finding | Status | Resolution |
|---|---|---|---|---|
| MEDIUM | `docs/design.md` | DNS rebinding can bypass creation-time host checks; acceptable for v1 if documented and re-validated at redirect time later | ACCEPTED | Documented as a known limitation in docs/design.md (Risks); re-validating hosts at redirect time is planned for a later version. |
| LOW | `openapi.yaml` | shortUrl derives from the Host header unless shortener.base-url is configured; set it in production | ACCEPTED | The design makes shortener.base-url the production setting; the implementation must honour it (checked in the final review). |

### Code review (`review`): GO

Reviewed 55 of 55 submitted files.

| Severity | File | Finding | Status | Resolution |
|---|---|---|---|---|
| LOW | `src/main/java/com/example/shortener/api/LinkController.java` | Security review: shortUrl must not trust the Host header in production | FIXED | LinkController uses shortener.base-url when it is set; ConfiguredBaseUrlIntegrationTest proves a spoofed Host header is ignored. |
| LOW | `src/main/java/com/example/shortener/service/ClickRecorder.java` | Dropped clicks are visible only in logs | DEFERRED | Drops are logged with a running total; export a metric when the service gains an actuator endpoint. |

### Workspace git history

Each promotion is one commit in the run's workspace (`git log` there shows the rationale and trailers).

| Seq | Node | Commit | Files | Approved by |
|---|---|---|---:|---|
| 18 | `design` | `4217928781d5` | 3 | demo-reviewer |
| 39 | `docs` | `61ac172ea483` | 2 | - |
| 44 | `implement` | `8c7b43bb8482` | 34 | demo-reviewer |
| 63 | `integration_tests` | `fda5a2378df5` | 3 | - |
| 65 | `unit_tests` | `ff89f6767b44` | 13 | - |
| 87 | `release` | `bfdc6077b3dc` | 1 | demo-reviewer |

## Policy and gate results

| Gate | Passed | Failed |
|---|---:|---:|
| artifact-metadata | 11 | 0 |
| compile | 1 | 0 |
| dependency-allowlist | 1 | 0 |
| design-diagrams | 1 | 0 |
| forbidden-api | 1 | 0 |
| functional-coverage | 1 | 0 |
| no-raw-ip-logging | 1 | 0 |
| path-allowlist | 9 | 0 |
| regression-tests | 1 | 0 |
| requirements-complete | 1 | 0 |
| review-complete | 2 | 0 |
| review-go | 1 | 0 |
| schema-valid | 1 | 0 |
| secret-scan | 7 | 0 |
| test-coverage | 1 | 0 |
| unit-tests | 2 | 1 |

Failures:

- seq 56 `unit_tests` / `unit-tests`: mvn test failed (exit 1): ⏎ [ERROR] Tests run: 4, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.035 s <<< FAILURE! -- in com.example.shortener.service.CodeGeneratorTest ⏎ [ERROR] com.example.shortener.service.CodeGeneratorTest.gene...

## Invalidation and replan history

| Seq | Event | Node | Detail |
|---:|---|---|---|
| | none | | |
