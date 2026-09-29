# Development agent: commit history

Exported by `scripts/export-sdlc-artifacts.py` from the workspace git history of these runs; do not edit by hand.

- `greenfield-20260929-154142` (greenfield): [report](../sample-runs/greenfield/report.md)
- `brownfield-20260929-154212` (brownfield): [report](../sample-runs/brownfield/report.md)
- `ambiguous-20260929-154255` (ambiguous): [report](../sample-runs/ambiguous/report.md)
- `bugfix-20260929-154330` (bugfix): [report](../sample-runs/bugfix/report.md)

## greenfield

| Commit | Author (agent) | Subject | Approved by |
|---|---|---|---|
| `9788025` | architect | design: Layered design (api/service/domain/storage) with uniqueness enf… | demo-reviewer |
| `7f9dfd7` | docs | docs: Operator- and developer-facing documentation: how to run and test… | - |
| `6d65052` | developer | implement: Implemented the approved design: Spring Boot web + JDBC + Fl… | demo-reviewer |
| `33355d0` | tester | integration_tests: MockMvc integration tests against a real H2 database… | - |
| `5e53f1c` | tester | unit_tests: The unit-tests gate showed generatesSevenCharacterCodes exp… | - |
| `b1c1c69` | docs | release: Release notes for 1.0.0 summarizing capabilities and known lim… | demo-reviewer |

## brownfield

| Commit | Author (agent) | Subject | Approved by |
|---|---|---|---|
| `0ff8225` | architect | design: Additive, backwards-compatible design: nullable expires_at, 410… | - |
| `47364d0` | developer | db_migration: Additive migration adding a nullable expires_at column. | demo-reviewer |
| `69f8c42` | developer | implement: The regression-tests gate showed v1 redirects returning 410:… | - |
| `b4cdd8e` | tester | new_tests: New tests for expiry: redirect before and at expiry (410), l… | - |
| `c524dac` | docs | docs: README documents the optional expiresAt field with an example and… | - |
| `9f6fa85` | docs | release: Release notes for 1.1.0 (link expiry). | demo-reviewer |

## ambiguous

| Commit | Author (agent) | Subject | Approved by |
|---|---|---|---|
| `5ee3976` | architect | design: Enforce HTTPS-only destinations at creation time; smallest chan… | - |
| `a0408f8` | developer | implement: UrlValidator now accepts only https; all other checks untouc… | - |
| `d035062` | docs | docs: Documents the HTTPS-only policy, the error clients will see, and … | - |
| `6f2de03` | tester | tests: Updated validator tests to the HTTPS-only rule (http inputs now … | - |
| `c31a4d6` | docs | release: Release notes for the HTTPS-only policy. | demo-reviewer |
| `b50850a` | agentic-sdlc engine | Revert design, implement, tests, docs, review, release after requirements changed | - |
| `83eeb55` | architect | design: Operator-controlled domain blocklist checked at creation; backw… | - |
| `175d456` | developer | implement: Added DomainBlocklist (config-driven, subdomain-aware) and w… | - |
| `cca11c0` | docs | docs: Documents blocklist configuration, matching rules, the client-vis… | - |
| `991bcca` | tester | tests: Tests for blocklist matching (exact, subdomain, case, lookalikes… | - |
| `4c06163` | docs | release: Release notes for the domain blocklist. | demo-reviewer |

## bugfix

| Commit | Author (agent) | Subject | Approved by |
|---|---|---|---|
| `95da339` | developer | refactor: Behavior-preserving extraction: host classification moves fro… | - |
| `9c80315` | tester | reproduce: Regression test written before the fix. | - |
| `5b57367` | developer | fix: The first attempt special-cased the reported string, and the regre… | demo-reviewer |
| `2beddd1` | docs | docs: Documents the host rules, including the absolute-name case the fi… | - |
| `804cf1e` | docs | release: Release notes for 1.0.1 (security fix). | demo-reviewer |
