---
version: 1.0.0
updated: 2026-09-29
changes:
  - "1.0.0 (2026-09-29): Initial version"
---

# Refactoring Scenario Skill

**Purpose:** Improve internal code quality without changing observable external behaviour.  
**Prerequisites:** Orchestration plan is defined. Always combined with `scenarios/brownfield/SKILL.md`.  
**Next skills:** development → testing (regression suite must stay green)

---

## When to use this skill

- Code has grown hard to read, test, or extend (complexity, duplication, unclear intent)
- A piece of logic needs to move before a new feature can be added cleanly
- Test coverage is too low to refactor safely — improve coverage first, then refactor

**Do not** use this skill to:
- Change behaviour (that is a feature, use the appropriate scenario skill)
- Fix a bug (use `scenarios/bugfix/SKILL.md`)
- Add observability (use `quality/observability/SKILL.md`)

---

## Inputs

- Area of code to refactor (file, class, module, or concept)
- Reason for the refactoring (why now, what problem it solves)
- Existing test coverage for the area (run tests before starting)

---

## Workflow

### Step 1 — Establish the safety net

Before any change:

1. Run the existing tests. They must be green.
2. If coverage of the target area is below 80% line coverage, add tests first — stop and do not
   refactor until the net is in place.
3. Document the current external behaviour (return values, side effects, error conditions) as a
   comment or test name so you can verify it is preserved.

### Step 2 — Choose the refactoring type

| Type | When to use |
|---|---|
| **Extract method / function** | Block of code doing too much in one place |
| **Rename** | Name no longer reflects intent |
| **Move** | Logic belongs in a different class or module |
| **Inline** | Abstraction adds no value |
| **Replace conditional with polymorphism** | Long if/switch on a type tag |
| **Introduce parameter object** | Method takes 4+ related parameters |
| **Separate concerns** | Class has more than one reason to change |
| **Remove dead code** | Code path is unreachable or unused |

Apply one refactoring type at a time. Run tests after each. Do not combine multiple types in one
step if either could hide a regression from the other.

### Step 3 — Apply the refactoring

Rules:
- One logical change per commit.
- The diff must not contain any behavioural change — if you spot a bug, stop and file it separately.
- Follow the existing project naming conventions exactly.
- Do not change the public API surface (method signatures, return types, error contracts) unless
  the task explicitly requests it.

### Step 4 — Verify no behaviour changed

1. Run the full test suite. It must be fully green.
2. If any test had to be updated, review it carefully: was the test wrong, or did you accidentally
   change behaviour?
3. For any public API change, confirm the change is intentional and documented.

---

## Outputs

- Refactored code with equivalent external behaviour
- All existing tests green (possibly with test name updates if renamed)
- A brief description of what was changed and why (for the commit message / PR description)

---

## Stopping conditions

Stop refactoring and raise a concern if:
- Any refactoring step requires changing a test's assertion (not just its setup or name)
- The refactoring reveals a bug that is not safe to fix in the same commit
- The scope has grown beyond what was originally planned
