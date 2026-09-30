---
version: 1.0.0
updated: 2026-09-29
changes:
  - "1.0.0 (2026-09-29): Initial version"
---

# Bug Fix Scenario Skill

**Purpose:** Reproduce, diagnose, fix, and verify a defect with the smallest safe change.  
**Prerequisites:** Orchestration plan is defined. Usually combined with `scenarios/brownfield/SKILL.md`.  
**Next skills:** development → testing → observability (confirm fix is visible)

---

## When to use this skill

- A defect has been reported (wrong output, crash, data corruption, performance regression)
- A test is failing and the fix is not obvious
- Behaviour differs between environments (dev vs prod)

---

## Inputs

- Defect description: what was expected vs what actually happened
- Steps to reproduce (or evidence that reproduction is not possible)
- Affected version, environment, and any relevant log output

---

## Workflow

### Step 1 — Reproduce before diagnosing

**A bug you cannot reproduce is a bug you cannot safely fix.**

1. Write a failing test (unit or integration) that captures the reported behaviour.
   - If you cannot write a failing test, document why before proceeding.
2. Confirm the test fails on the current code.
3. Do not touch production code until the reproduction exists.

### Step 2 — Identify the root cause

Trace from the symptom to the origin. Avoid fixing symptoms — fix causes.

Common root cause categories:

| Category | Indicators |
|---|---|
| **Off-by-one / boundary** | Failures at empty collection, single element, max value |
| **Null / missing value** | NullPointerException, missing key in map, absent optional |
| **Concurrency** | Intermittent failures, data corruption under load |
| **State mutation** | Shared mutable object modified unexpectedly |
| **Contract violation** | Caller or callee violates an undocumented assumption |
| **Configuration** | Behaviour differs only between environments |
| **External dependency** | Third-party API changed behaviour |

Document the root cause before writing the fix. If the root cause is unclear, add diagnostic
logging, gather evidence, then diagnose — do not guess.

### Step 3 — Implement the fix

- **Smallest safe change**: fix the root cause only; do not refactor surrounding code.
- If the fix requires touching multiple files, ask: is this actually one bug or several?
- Do not change the public API, data model, or behaviour outside the reported defect.
- If the fix requires a workaround rather than a proper solution, document it as a TODO with the
  reason the proper solution was deferred.

### Step 4 — Verify the fix

1. The reproduction test from Step 1 must now pass.
2. The full existing test suite must still pass.
3. If the defect was intermittent, add a note in the test about the conditions under which it
   manifests and why the fix prevents it.

### Step 5 — Prevent regression

After the fix, answer:
- Why did this bug exist? (missing test? wrong assumption? untested code path?)
- What test was missing that would have caught it earlier?
- Add that test now, even if the reproduction test already covers the immediate defect.

---

## Outputs

- A failing test that reproduces the defect (written before the fix)
- Root cause documented in one paragraph
- Minimal code change that fixes the root cause
- All existing tests green after the fix
- Regression test(s) that prevent recurrence

---

## Anti-patterns to avoid

- Fixing the symptom instead of the cause (e.g. catching an exception that shouldn't be thrown)
- "While I'm here" refactoring in the same commit as the fix
- Skipping reproduction because "I know what's wrong"
- Adding a workaround without documenting it as technical debt
- Removing a test that was failing instead of fixing the code it tests
