# Testing Skill

**Purpose:** Verify that the implementation satisfies every acceptance criterion and does not regress existing behaviour.  
**Prerequisites:** Development skill outputs. Acceptance criteria from analysis.  
**Outputs feed:** documentation skill (test results), release.

---

## When to use this skill

Every task that involves code changes.

---

## Test strategy by type

### Unit tests

**Scope:** A single class or function in isolation, with collaborators mocked or stubbed.  
**When to write:** For every class with business logic, validation, or complex branching.  
**Target:** Every code path, including error paths.

Rules:
- One assertion per concept (a test can have multiple `assert` calls if they verify one thing)
- Test name describes the scenario: `givenInvalidUrl_whenShortenCalled_thenThrowsInvalidUrlException`
  or equivalently `shortensUrl_rejectsInvalidUrl`
- Arrange / Act / Assert structure, separated by blank lines
- No production I/O (no DB, no HTTP, no file system) in unit tests
- Tests must run without any infrastructure (no running database, no network)

### Integration tests

**Scope:** Multiple components working together, typically with a real (in-memory or test container) database.  
**When to write:** For every API endpoint, repository method, and integration point.  
**Target:** Happy path + key error paths at the integration level.

Rules:
- Test the full stack from HTTP request to database and back
- Use a real (but isolated) database — in-memory or test container
- Reset state between tests (transactions rolled back or tables truncated)
- Do not mock components that you own — mock only external systems (third-party APIs)

### Contract tests

**Scope:** The interface between two services (producer + consumer).  
**When to write:** When this service is consumed by another service, or consumes one.  
**Target:** Verify the producer's response matches what the consumer expects.

### Regression tests

**When to write:** After every bug fix — one test that would have caught the bug before the fix.  
**Naming:** Include the bug identifier or a description of the scenario that failed.

### Negative tests

For every validation rule, write a test that triggers the rejection:
- Missing required field → error code X
- Value out of range → error code Y
- Duplicate resource → error code Z
- Unauthorised access → 403

### Edge case tests

| Category | Test cases |
|---|---|
| Collections | Empty, single element, maximum size |
| Numbers | Zero, negative, `Long.MAX_VALUE` |
| Strings | Empty, whitespace-only, max length, special characters, Unicode |
| Time | Epoch, future dates, past dates, DST transitions |
| Nulls | Any nullable parameter |

---

## Coverage requirements

- **Target**: 100% line and branch coverage for production business logic
- **Exceptions must be documented** with a reason (not "hard to test" — that means the code needs
  to be restructured)
- Acceptable exceptions:
  - `main()` method that only starts a framework
  - Exception paths that the platform guarantees cannot occur (e.g. SHA-256 always available)
  - Generated code

Coverage gates should fail the build — low coverage is a blocked CI, not a TODO.

---

## Test data

- Use descriptive, realistic test data, not `"test"`, `"foo"`, `1`
- Never use production data in tests
- Shared test fixtures should be in a dedicated factory or builder class
- Test data should make the test's intent obvious: `"https://evil.com/<script>alert(1)</script>"`
  communicates the security scenario immediately

---

## Test execution

Run the full suite before declaring a task complete:

```
# All tests
mvn test             # Java / Maven
./gradlew test       # Java / Gradle
pytest               # Python
go test ./...        # Go
npm test             # Node.js
```

A single failing test is a blocker, not a warning. Fix the failure or explicitly document why the
test is wrong (not why the test is inconvenient).

---

## Outputs

- Unit tests covering all acceptance criteria
- Integration tests covering the full stack for each acceptance criterion
- Negative tests for every validation rule
- Regression tests for any bug fixed in this task
- All tests passing
- Coverage report meeting the project's coverage target
