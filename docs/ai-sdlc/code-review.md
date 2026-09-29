# Code review agent: every submitted file reviewed, every finding resolved

Exported by `scripts/export-sdlc-artifacts.py` from the review artifacts of these runs; do not edit by hand.

- `greenfield-20260929-154142` (greenfield): [report](../sample-runs/greenfield/report.md)
- `brownfield-20260929-154212` (brownfield): [report](../sample-runs/brownfield/report.md)
- `ambiguous-20260929-154255` (ambiguous): [report](../sample-runs/ambiguous/report.md)
- `bugfix-20260929-154330` (bugfix): [report](../sample-runs/bugfix/report.md)

## greenfield / `security_review`: GO

`review-complete` passed at seq 30: all 3 files submitted by the upstream steps were reviewed, and every finding has a status and a resolution.

| Severity | File | Finding | Status | Resolution |
|---|---|---|---|---|
| MEDIUM | `docs/design.md` | DNS rebinding can bypass creation-time host checks; acceptable for v1 if documented and re-validated at redirect time later | **ACCEPTED** | Documented as a known limitation in docs/design.md (Risks); re-validating hosts at redirect time is planned for a later version. |
| LOW | `openapi.yaml` | shortUrl derives from the Host header unless shortener.base-url is configured; set it in production | **ACCEPTED** | The design makes shortener.base-url the production setting; the implementation must honour it (checked in the final review). |

<details><summary>Files reviewed</summary>

- `docs/design.md`
- `openapi.yaml`
- `src/main/resources/db/migration/V1__init.sql`

</details>

## greenfield / `review`: GO

`review-complete` passed at seq 75: all 46 files submitted by the upstream steps were reviewed, and every finding has a status and a resolution.

| Severity | File | Finding | Status | Resolution |
|---|---|---|---|---|
| LOW | `src/main/java/com/example/shortener/api/LinkController.java` | Security review: shortUrl must not trust the Host header in production | **FIXED** | LinkController uses shortener.base-url when it is set; ConfiguredBaseUrlIntegrationTest proves a spoofed Host header is ignored. |
| LOW | `src/main/java/com/example/shortener/service/ClickRecorder.java` | Dropped clicks are visible only in logs | **DEFERRED** | Drops are logged with a running total; export a metric when the service gains an actuator endpoint. |

<details><summary>Files reviewed</summary>

- `README.md`
- `docs/design.md`
- `docs/operations.md`
- `openapi.yaml`
- `pom.xml`
- `spotbugs-exclude.xml`
- `src/main/java/com/example/shortener/ShortenerApplication.java`
- `src/main/java/com/example/shortener/api/ApiExceptionHandler.java`
- `src/main/java/com/example/shortener/api/AuditFilter.java`
- `src/main/java/com/example/shortener/api/ClientKeyResolver.java`
- `src/main/java/com/example/shortener/api/CreateLinkRequest.java`
- `src/main/java/com/example/shortener/api/ErrorResponse.java`
- `src/main/java/com/example/shortener/api/LinkController.java`
- `src/main/java/com/example/shortener/api/LinkResponse.java`
- `src/main/java/com/example/shortener/api/SecurityHeadersFilter.java`
- `src/main/java/com/example/shortener/domain/AuditEvent.java`
- `src/main/java/com/example/shortener/domain/DailyClicks.java`
- `src/main/java/com/example/shortener/domain/LinkStats.java`
- `src/main/java/com/example/shortener/domain/ShortLink.java`
- `src/main/java/com/example/shortener/service/AliasValidator.java`
- `src/main/java/com/example/shortener/service/ClickRecorder.java`
- `src/main/java/com/example/shortener/service/CodeGenerator.java`
- `src/main/java/com/example/shortener/service/ErrorCode.java`
- `src/main/java/com/example/shortener/service/LogSanitizer.java`
- `src/main/java/com/example/shortener/service/RateLimiter.java`
- `src/main/java/com/example/shortener/service/Sha256.java`
- `src/main/java/com/example/shortener/service/ShortenerException.java`
- `src/main/java/com/example/shortener/service/ShortenerService.java`
- `src/main/java/com/example/shortener/service/UrlValidator.java`
- `src/main/java/com/example/shortener/storage/AuditRepository.java`
- `src/main/java/com/example/shortener/storage/JdbcAuditRepository.java`
- `src/main/java/com/example/shortener/storage/JdbcLinkRepository.java`
- `src/main/java/com/example/shortener/storage/LinkRepository.java`
- `src/main/resources/application.properties`
- `src/main/resources/db/migration/V1__init.sql`
- `src/test/java/com/example/shortener/api/ApiExceptionHandlerTest.java`
- `src/test/java/com/example/shortener/api/AuditFilterTest.java`
- `src/test/java/com/example/shortener/api/AuditTrailIntegrationTest.java`
- `src/test/java/com/example/shortener/api/ConfiguredBaseUrlIntegrationTest.java`
- `src/test/java/com/example/shortener/api/LinkApiIntegrationTest.java`
- `src/test/java/com/example/shortener/service/ClickRecorderTest.java`
- `src/test/java/com/example/shortener/service/CodeGeneratorTest.java`
- `src/test/java/com/example/shortener/service/RateLimiterTest.java`
- `src/test/java/com/example/shortener/service/ShortenerServiceTest.java`
- `src/test/java/com/example/shortener/service/UrlValidatorEdgeCaseTest.java`
- `src/test/java/com/example/shortener/service/UrlValidatorTest.java`

</details>

## brownfield / `review`: GO

`review-complete` passed at seq 72: all 15 files submitted by the upstream steps were reviewed, and every finding has a status and a resolution.

| Severity | File | Finding | Status | Resolution |
|---|---|---|---|---|
| LOW | `src/main/java/com/example/shortener/service/ShortenerService.java` | Expired links remain in storage; add a cleanup job if volume grows | **DEFERRED** | Follow-up: add a cleanup job when link volume grows; expired links cost only storage and are never served. |

<details><summary>Files reviewed</summary>

- `README.md`
- `docs/design-expiry.md`
- `openapi.yaml`
- `src/main/java/com/example/shortener/api/ApiExceptionHandler.java`
- `src/main/java/com/example/shortener/api/CreateLinkRequest.java`
- `src/main/java/com/example/shortener/api/LinkController.java`
- `src/main/java/com/example/shortener/api/LinkResponse.java`
- `src/main/java/com/example/shortener/domain/ShortLink.java`
- `src/main/java/com/example/shortener/service/ErrorCode.java`
- `src/main/java/com/example/shortener/service/ShortenerService.java`
- `src/main/java/com/example/shortener/storage/JdbcLinkRepository.java`
- `src/main/java/com/example/shortener/storage/LinkRepository.java`
- `src/main/resources/db/migration/V2__add_expiry.sql`
- `src/test/java/com/example/shortener/api/LinkExpiryIntegrationTest.java`
- `src/test/java/com/example/shortener/domain/ShortLinkExpiryTest.java`

</details>

## ambiguous / `review`: GO

`review-complete` passed at seq 46: all 7 files submitted by the upstream steps were reviewed, and every finding has a status and a resolution.

| Severity | File | Finding | Status | Resolution |
|---|---|---|---|---|
| MEDIUM | `src/main/java/com/example/shortener/service/DomainBlocklist.java` | Static list only; plan a reloadable source or reputation feed | **ACCEPTED** | Restart-to-reload is an accepted prototype trade-off in docs/design-security.md; a reloadable source is the planned follow-up. |

<details><summary>Files reviewed</summary>

- `docs/design-security.md`
- `docs/security.md`
- `src/main/java/com/example/shortener/service/DomainBlocklist.java`
- `src/main/java/com/example/shortener/service/UrlValidator.java`
- `src/main/resources/application.properties`
- `src/test/java/com/example/shortener/api/BlocklistIntegrationTest.java`
- `src/test/java/com/example/shortener/service/DomainBlocklistTest.java`

</details>

## bugfix / `review`: GO

`review-complete` passed at seq 55: all 3 files submitted by the upstream steps were reviewed, and every finding has a status and a resolution.

| Severity | File | Finding | Status | Resolution |
|---|---|---|---|---|
| LOW | `src/main/java/com/example/shortener/service/HostClassifier.java` | DNS rebinding remains out of scope (names are not resolved); unchanged documented limitation | **ACCEPTED** | Unchanged documented limitation; the fix neither widens nor narrows it. |

<details><summary>Files reviewed</summary>

- `src/main/java/com/example/shortener/service/HostClassifier.java`
- `src/main/java/com/example/shortener/service/UrlValidator.java`
- `src/test/java/com/example/shortener/service/TrailingDotHostTest.java`

</details>
