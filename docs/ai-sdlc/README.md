# AI SDLC artifacts

Every artifact below was produced by an agent in a governed run and verified by a gate before it was accepted. The
exports in this folder come from the four sample runs (`make artifacts` regenerates them after `make demo-all`); the
full evidence of each run is in its [report](../sample-runs/greenfield/report.md#quality-evidence). How AI assistance
was used to build the system itself is in [ai-assisted-development.md](../ai-assisted-development.md).

| Deliverable | Agent | Artifact | Verified by |
|---|---|---|---|
| User stories and acceptance criteria | Requirements | [user-stories.md](user-stories.md) | `requirements-complete`: every story reads "As a ..., I want ..., so that ..." |
| Design document | Design (architect) | [design.md](../../shortener-service/docs/design.md) (greenfield), plus the brownfield and ambiguous designs in the [sample reports](../sample-runs/) | `design-diagrams`, `schema-valid`, human approval of the design |
| Architecture and design diagrams | Design (architect) | Component and sequence diagrams in [design.md](../../shortener-service/docs/design.md#diagrams); the orchestrator's own diagrams in [architecture.md](../architecture.md) | `design-diagrams`: every design document carries a Mermaid diagram |
| Error handling and logging | Development | [ApiExceptionHandler](../../shortener-service/src/main/java/com/example/shortener/api/ApiExceptionHandler.java) (one error envelope, no internal details), [LogSanitizer](../../shortener-service/src/main/java/com/example/shortener/service/LogSanitizer.java), [operations.md](../../shortener-service/docs/operations.md) | `compile`, `no-raw-ip-logging`, `ApiExceptionHandlerTest` |
| Auditing | Development | [AuditFilter](../../shortener-service/src/main/java/com/example/shortener/api/AuditFilter.java) and the `audit_event` table: every state-changing request with time, opaque client key, method, path and status | `AuditTrailIntegrationTest`, `AuditFilterTest` |
| Meaningful Git commits and history | Development (engine) | [git-history.md](git-history.md): one commit per accepted step, authored by the agent role, with rationale and `Approved-by` trailers | `GitHistoryTest`, `GreenfieldScenarioTest` |
| Code review results: all code reviewed, issues and resolutions | Code review | [code-review.md](code-review.md) | `review-complete`: every submitted file reviewed, every finding FIXED, ACCEPTED or DEFERRED with a resolution |
| Unit tests | QA (tester) | [shortener tests](../../shortener-service/src/test/java/com/example/shortener/): 135 unit and integration tests | `unit-tests`, `regression-tests` |
| Unit test coverage report | QA (tester) | [coverage.md](../coverage.md) (per class, both projects); JaCoCo HTML reports are published by CI | `test-coverage` (measured with JaCoCo in the sandbox), coverage floors in both builds |
| Functional test coverage report | QA (tester) | [functional-coverage.md](functional-coverage.md): every acceptance criterion mapped to the tests that prove it | `functional-coverage`: every criterion mapped, every cited test exists |
| Where the 100% target was not met | QA (tester) | [coverage.md](../coverage.md): the shortener's two documented exceptions and every orchestrator class below 100% | `test-coverage` fails for any undocumented gap |
