# Run report: `greenfield-20260929-154142`

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
| 1 | 15:41:43.697 |  | RUN_STARTED | engine | workflow greenfield |
| 2 | 15:41:43.730 | requirements | NODE_STARTED | engine | agent requirements, variant default |
| 3 | 15:41:43.739 | requirements | AGENT_CALLED | requirements | requirements attempt 1 |
| 4 | 15:41:43.744 | requirements | GATE_PASSED | engine | artifact-metadata |
| 5 | 15:41:43.745 | requirements | GATE_PASSED | engine | requirements-complete |
| 6 | 15:41:43.750 | requirements | NODE_DONE | engine | artifact d1f94f38a55e, 0 file(s) |
| 7 | 15:41:43.754 | design | NODE_STARTED | engine | agent architect, variant default |
| 8 | 15:41:43.755 | design | GATE_PASSED | engine | path-allowlist |
| 9 | 15:41:43.757 | design | AGENT_CALLED | architect | architect attempt 1 |
| 10 | 15:41:43.758 | design | GATE_PASSED | engine | artifact-metadata |
| 11 | 15:41:43.760 | design | GATE_PASSED | engine | design-diagrams |
| 12 | 15:41:43.760 | design | GATE_PASSED | engine | path-allowlist |
| 13 | 15:41:43.763 | design | GATE_PASSED | engine | schema-valid |
| 14 | 15:41:43.766 | design | GATE_PASSED | engine | secret-scan |
| 15 | 15:41:43.770 | design | APPROVAL_REQUESTED | engine | hash 1d0b735a0615; [APPROVE_AFTER: human sign-off required, MigrationPathRule: schema migration changed (src/main/resources/db/migration/V1__init.sql), DiffSizeRule: 297 changed lines exceeds 200] |
| 16 | 15:41:43.773 |  | RUN_PAUSED | engine | waiting {design=AWAITING_APPROVAL} |
| 17 | 15:41:44.862 | design | APPROVED | demo-reviewer | by demo-reviewer on 1d0b735a0615: Contract, V1 schema and risk list reviewed |
| 18 | 15:41:44.941 | design | NODE_DONE | demo-reviewer | artifact 1d0b735a0615, 3 file(s), commit 9788025d4eba |
| 19 | 15:41:45.493 |  | RESUMED | engine |  |
| 20 | 15:41:45.505 | implement | NODE_STARTED | engine | agent developer, variant default |
| 21 | 15:41:45.507 | docs | NODE_STARTED | engine | agent docs, variant default |
| 22 | 15:41:45.507 | implement | GATE_PASSED | engine | path-allowlist |
| 23 | 15:41:45.508 | security_review | NODE_STARTED | engine | agent reviewer, variant default |
| 24 | 15:41:45.514 | security_review | AGENT_CALLED | reviewer | reviewer attempt 1 |
| 25 | 15:41:45.516 | docs | AGENT_CALLED | docs | docs attempt 1 |
| 26 | 15:41:45.520 | implement | AGENT_CALLED | developer | developer attempt 1 |
| 27 | 15:41:45.520 | security_review | GATE_PASSED | engine | artifact-metadata |
| 28 | 15:41:45.521 | docs | GATE_PASSED | engine | artifact-metadata |
| 29 | 15:41:45.521 | docs | GATE_PASSED | engine | path-allowlist |
| 30 | 15:41:45.521 | security_review | GATE_PASSED | engine | review-complete |
| 31 | 15:41:45.523 | docs | GATE_PASSED | engine | secret-scan |
| 32 | 15:41:45.526 | security_review | NODE_DONE | engine | artifact 94945665bf47, 0 file(s) |
| 33 | 15:41:45.528 | implement | GATE_PASSED | engine | artifact-metadata |
| 34 | 15:41:45.529 | implement | GATE_PASSED | engine | path-allowlist |
| 35 | 15:41:45.533 | implement | GATE_PASSED | engine | secret-scan |
| 36 | 15:41:45.538 | implement | GATE_PASSED | engine | forbidden-api |
| 37 | 15:41:45.545 | implement | GATE_PASSED | engine | dependency-allowlist |
| 38 | 15:41:45.548 | implement | GATE_PASSED | engine | no-raw-ip-logging |
| 39 | 15:41:45.595 | docs | NODE_DONE | engine | artifact cd35e2d72520, 2 file(s), commit 7f9dfd7fedc9 |
| 40 | 15:41:46.970 | implement | GATE_PASSED | engine | compile |
| 41 | 15:41:46.984 | implement | APPROVAL_REQUESTED | engine | hash 6f8e251e61b6; [PomChangeRule: build file changed (pom.xml), DiffSizeRule: 1350 changed lines exceeds 200] |
| 42 | 15:41:46.987 |  | RUN_PAUSED | engine | waiting {implement=AWAITING_APPROVAL} |
| 43 | 15:41:48.171 | implement | APPROVED | demo-reviewer | by demo-reviewer on 6f8e251e61b6: Dependencies are allowlisted; implementation matches the design |
| 44 | 15:41:48.257 | implement | NODE_DONE | demo-reviewer | artifact 6f8e251e61b6, 30 file(s), commit 6d6505263dc2 |
| 45 | 15:41:48.820 |  | RESUMED | engine |  |
| 46 | 15:41:48.832 | integration_tests | NODE_STARTED | engine | agent tester, variant default |
| 47 | 15:41:48.833 | unit_tests | NODE_STARTED | engine | agent tester, variant default |
| 48 | 15:41:48.842 | integration_tests | AGENT_CALLED | tester | tester attempt 1 |
| 49 | 15:41:48.844 | unit_tests | AGENT_CALLED | tester | tester attempt 1 |
| 50 | 15:41:48.859 | unit_tests | GATE_PASSED | engine | artifact-metadata |
| 51 | 15:41:48.860 | integration_tests | GATE_PASSED | engine | artifact-metadata |
| 52 | 15:41:48.860 | unit_tests | GATE_PASSED | engine | path-allowlist |
| 53 | 15:41:48.860 | integration_tests | GATE_PASSED | engine | path-allowlist |
| 54 | 15:41:48.863 | integration_tests | GATE_PASSED | engine | secret-scan |
| 55 | 15:41:48.864 | unit_tests | GATE_PASSED | engine | secret-scan |
| 56 | 15:41:52.680 | unit_tests | GATE_FAILED | engine | unit-tests: mvn test failed (exit 1): CodeGeneratorTest.generatesSevenCharacterCodes:13 Expected size: 8 but was: 7 |
| 57 | 15:41:52.692 | unit_tests | ATTEMPT_DISCARDED | engine | rolled back attempt 1 (unit-tests, sig c93b06229350) |
| 58 | 15:41:52.699 | unit_tests | AGENT_CALLED | tester | tester attempt 2 |
| 59 | 15:41:52.712 | unit_tests | GATE_PASSED | engine | artifact-metadata |
| 60 | 15:41:52.712 | unit_tests | GATE_PASSED | engine | path-allowlist |
| 61 | 15:41:52.715 | unit_tests | GATE_PASSED | engine | secret-scan |
| 62 | 15:41:55.260 | integration_tests | GATE_PASSED | engine | unit-tests |
| 63 | 15:41:55.349 | integration_tests | NODE_DONE | engine | artifact 351b80301898, 3 file(s), commit 33355d0a8d7a |
| 64 | 15:41:56.894 | unit_tests | GATE_PASSED | engine | unit-tests |
| 65 | 15:41:56.963 | unit_tests | NODE_DONE | engine | artifact b559ddaea5e1, 8 file(s), commit 5e53f1ccdd14 |
| 66 | 15:41:56.969 | qa_report | NODE_STARTED | engine | agent tester, variant default |
| 67 | 15:41:56.970 | qa_report | AGENT_CALLED | tester | tester attempt 1 |
| 68 | 15:41:56.982 | qa_report | GATE_PASSED | engine | artifact-metadata |
| 69 | 15:41:56.988 | qa_report | GATE_PASSED | engine | functional-coverage |
| 70 | 15:42:03.237 | qa_report | GATE_PASSED | engine | test-coverage |
| 71 | 15:42:03.243 | qa_report | NODE_DONE | engine | artifact 28dfb764ac9b, 0 file(s) |
| 72 | 15:42:03.254 | review | NODE_STARTED | engine | agent reviewer, variant default |
| 73 | 15:42:03.256 | review | AGENT_CALLED | reviewer | reviewer attempt 1 |
| 74 | 15:42:03.268 | review | GATE_PASSED | engine | artifact-metadata |
| 75 | 15:42:03.269 | review | GATE_PASSED | engine | review-complete |
| 76 | 15:42:09.390 | review | GATE_PASSED | engine | regression-tests |
| 77 | 15:42:09.394 | review | NODE_DONE | engine | artifact e68b9af804af, 0 file(s) |
| 78 | 15:42:09.405 | release | NODE_STARTED | engine | agent docs, variant default |
| 79 | 15:42:09.407 | release | GATE_PASSED | engine | review-go |
| 80 | 15:42:09.408 | release | AGENT_CALLED | docs | docs attempt 1 |
| 81 | 15:42:09.421 | release | GATE_PASSED | engine | artifact-metadata |
| 82 | 15:42:09.422 | release | GATE_PASSED | engine | path-allowlist |
| 83 | 15:42:09.422 | release | GATE_PASSED | engine | secret-scan |
| 84 | 15:42:09.423 | release | APPROVAL_REQUESTED | engine | hash 793599919e3a; [APPROVE_AFTER: human sign-off required] |
| 85 | 15:42:09.426 |  | RUN_PAUSED | engine | waiting {release=AWAITING_APPROVAL} |
| 86 | 15:42:10.525 | release | APPROVED | demo-reviewer | by demo-reviewer on 793599919e3a: Review GO, full suite green: release 1.0.0 |
| 87 | 15:42:10.586 | release | NODE_DONE | demo-reviewer | artifact 793599919e3a, 1 file(s), commit b1c1c69b900c |
| 88 | 15:42:11.174 |  | RESUMED | engine |  |
| 89 | 15:42:11.180 |  | RUN_COMPLETED | engine |  |

## Metrics

| Metric | Value |
|---|---|
| Success rate (first-pass DONE / nodes) | 90.0% (9/10) |
| Retries (discarded attempts) | 1 {unit_tests=1} |
| Rollbacks (staging discarded) | 1 |
| Fallbacks | 0 |
| MTTR | 4.282 s over 1 node(s) |
| End-to-end latency (gross) | 27.483 s |
| Human wait excluded | 3.381 s |
| End-to-end latency (net) | 24.101 s |
| Approvals requested / granted / rejected | 3 / 3 / 0 |
| Clarifications requested | 0 |
| Invalidations / replans | 0 / 0 |
| Gate failures by gate | {unit-tests=1} |

## Approvals

| Seq | Node | Decision | Who | When (UTC) | Hash approved | Comment | Revoked later |
|---:|---|---|---|---|---|---|---|
| 17 | design | APPROVED | demo-reviewer | 15:41:44.862 | `1d0b735a0615` | Contract, V1 schema and risk list reviewed | no |
| 43 | implement | APPROVED | demo-reviewer | 15:41:48.171 | `6f8e251e61b6` | Dependencies are allowlisted; implementation matches the design | no |
| 86 | release | APPROVED | demo-reviewer | 15:42:10.525 | `793599919e3a` | Review GO, full suite green: release 1.0.0 | no |

## Decision lineage

- **release** `793599919e3a`: Release notes for 1.0.0 summarizing capabilities and known limitations; release readiness requires the GO review and a human sign-off.
  - **review** `e68b9af804af`: Joined review of implementation, tests and docs. The full suite passes in the review gate; no high-severity findings.
    - **unit_tests** `b559ddaea5e1`: The unit-tests gate showed generatesSevenCharacterCodes expecting 8 characters while the design and CodeGenerator.LENGTH specify 7. Corrected the expectation...
      - **implement** `6f8e251e61b6`: Implemented the approved design: Spring Boot web + JDBC + Flyway on H2, service rules exactly as specified, and a global error envelope. The pom.xml and the ...
        - **design** `1d0b735a0615`: Layered design (api/service/domain/storage) with uniqueness enforced by the database, validation at creation time, and an asynchronous bounded click pipeline...
          - **requirements** `d1f94f38a55e`: Normalized the request into an HTTP service with three endpoints, explicit validation and abuse controls, and aggregate-only analytics. Non-blocking gaps are...
            - requirement: "Build a URL shortener service: create short links (with an optional custom alias), redirect by code, and report click statistics. It must not become an SSRF ..."
    - **integration_tests** `351b80301898`: MockMvc integration tests against a real H2 database covering create/redirect, 404, idempotency, alias conflicts, invalid input, rate limiting and per-day st...
      - **implement** `6f8e251e61b6` (see above)
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
| implement | `6f8e251e61b6` | 30 | Implemented the approved design: Spring Boot web + JDBC + Flyway on H2, service rules exactly as specified, and a global error envelope. The pom.xml and the size of the change require human approval under policy. |
| unit_tests | `b559ddaea5e1` | 8 | The unit-tests gate showed generatesSevenCharacterCodes expecting 8 characters while the design and CodeGenerator.LENGTH specify 7. Corrected the expectation; all other tests unchanged. |
| integration_tests | `351b80301898` | 3 | MockMvc integration tests against a real H2 database covering create/redirect, 404, idempotency, alias conflicts, invalid input, rate limiting and per-day statistics with a controllable clock. |
| qa_report | `28dfb764ac9b` | 0 | Unit and integration suites are both in place. Every acceptance criterion maps to the tests that prove it; the test-coverage gate measures line and branch coverage and names every class below the 100% target. |
| docs | `cd35e2d72520` | 2 | Operator- and developer-facing documentation: how to run and test, configuration, observability counters and failure behavior. |
| security_review | `94945665bf47` | 0 | Threat-model review of the approved design before implementation lands. |
| review | `e68b9af804af` | 0 | Joined review of implementation, tests and docs. The full suite passes in the review gate; no high-severity findings. |
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
| `qa_report / test-coverage` | line 98.8% (324/328), branch 100.0% (161/161), target 100% over 29 classes; below target (documented): com.example.shortener.ShortenerApplication (line 50.0%, branch 100.0%): main() only launches Spring Boot; tests start the application context through @SpringBootTest; com.example.shortener.service.Sha256 (line 33.3%, branch 100.0%): the NoSuchAlgorithmException handler cannot run: every Java platform must provide SHA-256 |

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

Reviewed 46 of 46 submitted files.

| Severity | File | Finding | Status | Resolution |
|---|---|---|---|---|
| LOW | `src/main/java/com/example/shortener/api/LinkController.java` | Security review: shortUrl must not trust the Host header in production | FIXED | LinkController uses shortener.base-url when it is set; ConfiguredBaseUrlIntegrationTest proves a spoofed Host header is ignored. |
| LOW | `src/main/java/com/example/shortener/service/ClickRecorder.java` | Dropped clicks are visible only in logs | DEFERRED | Drops are logged with a running total; export a metric when the service gains an actuator endpoint. |

### Workspace git history

Each promotion is one commit in the run's workspace (`git log` there shows the rationale and trailers).

| Seq | Node | Commit | Files | Approved by |
|---|---|---|---:|---|
| 18 | `design` | `9788025d4eba` | 3 | demo-reviewer |
| 39 | `docs` | `7f9dfd7fedc9` | 2 | - |
| 44 | `implement` | `6d6505263dc2` | 30 | demo-reviewer |
| 63 | `integration_tests` | `33355d0a8d7a` | 3 | - |
| 65 | `unit_tests` | `5e53f1ccdd14` | 8 | - |
| 87 | `release` | `b1c1c69b900c` | 1 | demo-reviewer |

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

- seq 56 `unit_tests` / `unit-tests`: mvn test failed (exit 1): ⏎ [ERROR] Tests run: 4, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.027 s <<< FAILURE! -- in com.example.shortener.service.CodeGeneratorTest ⏎ [ERROR] com.example.shortener.service.CodeGeneratorTest.gene...

## Invalidation and replan history

| Seq | Event | Node | Detail |
|---:|---|---|---|
| | none | | |
