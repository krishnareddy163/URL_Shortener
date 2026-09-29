# QA agent: functional and measured test coverage

Exported by `scripts/export-sdlc-artifacts.py` from the QA report artifacts and gate results of these runs; do not edit by hand.

- `greenfield-20260929-154142` (greenfield): [report](../sample-runs/greenfield/report.md)
- `brownfield-20260929-154212` (brownfield): [report](../sample-runs/brownfield/report.md)
- `ambiguous-20260929-154255` (ambiguous): [report](../sample-runs/ambiguous/report.md)
- `bugfix-20260929-154330` (bugfix): [report](../sample-runs/bugfix/report.md)

## greenfield

- **Functional coverage** (`functional-coverage`, seq 69): 9 of 9 acceptance criteria mapped to 32 existing tests.
- **Measured unit and integration coverage** (`test-coverage`, JaCoCo in the build sandbox, seq 70): line 98.8% (324/328), branch 100.0% (161/161), target 100% over 29 classes; below target (documented): com.example.shortener.ShortenerApplication (line 50.0%, branch 100.0%): main() only launches Spring Boot; tests start the application context through @SpringBootTest; com.example.shortener.service.Sha256 (line 33.3%, branch 100.0%): the NoSuchAlgorithmException handler cannot run: every Java platform must provide SHA-256.
- The full per-class inventory for both projects is in [coverage.md](../coverage.md).

| # | Acceptance criterion | Tests that prove it |
|---:|---|---|
| 1 | POST /api/v1/links returns 201 with {code, shortUrl, url, createdAt}; the same normalized URL without an alias returns the existing link with 200 | `LinkApiIntegrationTest#createThenRedirectReturns302WithLocation`<br>`LinkApiIntegrationTest#creatingTheSameNormalizedUrlAgainIsIdempotent`<br>`ShortenerServiceTest#returnsExistingLinkForSameNormalizedUrl` |
| 2 | Custom aliases are 3-32 chars of [A-Za-z0-9_-], reserved words api/actuator/health/stats are rejected (400), duplicates return 409 | `ShortenerServiceTest#createsLinkWithCustomAlias`<br>`ShortenerServiceTest#rejectsInvalidOrReservedAliases`<br>`LinkApiIntegrationTest#invalidAliasAndMalformedBodiesReturn400`<br>`LinkApiIntegrationTest#duplicateAliasReturns409`<br>`ShortenerServiceTest#rejectsAliasThatIsAlreadyTaken` |
| 3 | Generated codes are 7 base62 characters from SecureRandom; collisions retry up to 5 times, then 500 CODE_GENERATION_FAILED | `CodeGeneratorTest#generatesSevenCharacterCodes`<br>`CodeGeneratorTest#generatedCodesUseOnlyTheBase62Alphabet`<br>`ShortenerServiceTest#retriesGeneratedCodeAfterCollision`<br>`ShortenerServiceTest#failsWithCodeGenerationFailedAfterFiveCollisions`<br>`ApiExceptionHandlerTest#codeGenerationFailureIsA500WithItsCode` |
| 4 | URLs must be http/https, <= 2048 chars, have a host, and not target localhost, *.local, or loopback/link-local/private/unspecified literal IPs | `UrlValidatorTest#rejectsNonHttpSchemes`<br>`UrlValidatorTest#rejectsUrlsLongerThan2048Characters`<br>`UrlValidatorTest#requiresHost`<br>`UrlValidatorTest#rejectsLocalHostNames`<br>`UrlValidatorTest#rejectsNonPublicLiteralAddresses`<br>`UrlValidatorEdgeCaseTest#nonPublicAddressesAreRejected`<br>`LinkApiIntegrationTest#invalidUrlReturns400` |
| 5 | GET /{code} returns 302 with Location, or 404; the click is recorded asynchronously and never blocks the redirect | `LinkApiIntegrationTest#createThenRedirectReturns302WithLocation`<br>`LinkApiIntegrationTest#unknownCodeReturns404WithErrorEnvelope`<br>`ShortenerServiceTest#resolveEnqueuesClickWithoutWaiting`<br>`ClickRecorderTest#aFailedClickDoesNotStopTheWorker` |
| 6 | GET /api/v1/links/{code}/stats returns totalClicks, lastAccessedAt and per-UTC-day buckets, or 404 | `LinkApiIntegrationTest#statsReportTotalsLastAccessAndPerDayBuckets`<br>`LinkApiIntegrationTest#statsForUnknownCodeReturns404` |
| 7 | Link creation is rate limited per client (default 20/minute) with 429; the client key is a hash and raw IPs are never logged | `RateLimiterTest#allowsBurstUpToCapacityThenRejects`<br>`LinkApiIntegrationTest#rateLimitReturns429AfterTwentyCreatesPerMinute`<br>`AuditTrailIntegrationTest#createdAndRejectedRequestsAreAuditedWithoutPersonalData` |
| 8 | Every non-2xx/3xx response uses {"error":{"code","message"}} | `LinkApiIntegrationTest#unknownCodeReturns404WithErrorEnvelope`<br>`LinkApiIntegrationTest#unmappedRoutesAndMethodsUseTheErrorEnvelope`<br>`ApiExceptionHandlerTest#unexpectedFailuresHideTheirDetails` |
| 9 | Every state-changing request is recorded in an audit trail with its time, opaque client key, method, path and final status; reads are not recorded | `AuditTrailIntegrationTest#createdAndRejectedRequestsAreAuditedWithoutPersonalData`<br>`AuditTrailIntegrationTest#readsAreNotAudited`<br>`AuditFilterTest#recordsStateChangingRequestsWithTheirFinalStatus` |
