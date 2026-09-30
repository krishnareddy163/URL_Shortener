# Development agent: commit history

Exported by `scripts/export-sdlc-artifacts.py` from the workspace git history of these runs; do not edit by hand.

- `greenfield-20260930-194355` (greenfield): [report](../sample-runs/greenfield/report.md)
- `brownfield-20260930-194431` (brownfield): [report](../sample-runs/brownfield/report.md)
- `ambiguous-20260930-194522` (ambiguous): [report](../sample-runs/ambiguous/report.md)
- `bugfix-20260930-194605` (bugfix): [report](../sample-runs/bugfix/report.md)

## greenfield

| Commit | Author (agent) | Subject | Approved by |
|---|---|---|---|
| `4217928` | architect | design: Layered design (api/service/domain/storage) with uniqueness enf… | demo-reviewer |
| `61ac172` | docs | docs: Operator- and developer-facing documentation: how to run and test… | - |
| `8c7b43b` | developer | implement: Implemented the approved design: Spring Boot web + JDBC + Fl… | demo-reviewer |
| `fda5a23` | tester | integration_tests: MockMvc integration tests against a real H2 database… | - |
| `ff89f67` | tester | unit_tests: The unit-tests gate showed generatesSevenCharacterCodes exp… | - |
| `bfdc607` | docs | release: Release notes for 1.0.0 summarizing capabilities and known lim… | demo-reviewer |

## brownfield

| Commit | Author (agent) | Subject | Approved by |
|---|---|---|---|
| `dbbc92c` | architect | design: Additive, backwards-compatible design: nullable expires_at, 410… | - |
| `8c0b5a6` | developer | db_migration: Additive migration adding a nullable expires_at column. | demo-reviewer |
| `65dc5d8` | developer | implement: The regression-tests gate showed v1 redirects returning 410:… | - |
| `5f9ada3` | tester | new_tests: New tests for expiry: redirect before and at expiry (410), l… | - |
| `e98fde7` | docs | docs: README documents the optional expiresAt field with an example and… | - |
| `5937c11` | docs | release: Release notes for 1.1.0 (link expiry). | demo-reviewer |

## ambiguous

| Commit | Author (agent) | Subject | Approved by |
|---|---|---|---|
| `b846793` | architect | design: Enforce HTTPS-only destinations at creation time; smallest chan… | - |
| `a69bcb3` | developer | implement: UrlValidator now accepts only https; all other checks untouc… | - |
| `a9ac5bc` | docs | docs: Documents the HTTPS-only policy, the error clients will see, and … | - |
| `528351a` | tester | tests: Updated validator tests to the HTTPS-only rule (http inputs now … | - |
| `dab6e0b` | docs | release: Release notes for the HTTPS-only policy. | demo-reviewer |
| `a99e29f` | agentic-sdlc engine | Revert design, implement, tests, docs, review, release after requirements changed | - |
| `cc4a6de` | architect | design: Operator-controlled domain blocklist checked at creation; backw… | - |
| `800852c` | developer | implement: Added DomainBlocklist (config-driven, subdomain-aware) and w… | - |
| `e78c85b` | docs | docs: Documents blocklist configuration, matching rules, the client-vis… | - |
| `89a475d` | tester | tests: Tests for blocklist matching (exact, subdomain, case, lookalikes… | - |
| `23f1fa6` | docs | release: Release notes for the domain blocklist. | demo-reviewer |

## bugfix

| Commit | Author (agent) | Subject | Approved by |
|---|---|---|---|
| `de0c861` | developer | refactor: Behavior-preserving extraction: host classification moves fro… | - |
| `139f9ca` | tester | reproduce: Regression test written before the fix. | - |
| `df5e405` | developer | fix: The first attempt special-cased the reported string, and the regre… | demo-reviewer |
| `85f46ee` | docs | docs: Documents the host rules, including the absolute-name case the fi… | - |
| `ff02904` | docs | release: Release notes for 1.0.1 (security fix). | demo-reviewer |
