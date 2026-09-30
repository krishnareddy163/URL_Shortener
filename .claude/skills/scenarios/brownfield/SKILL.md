---
version: 1.0.0
updated: 2026-09-29
changes:
  - "1.0.0 (2026-09-29): Initial version"
---

# Brownfield Scenario Skill

**Purpose:** Guide changes to an existing codebase while preserving its behaviour, patterns, and integrity.  
**Prerequisites:** Orchestration plan is defined.  
**Next skills:** analysis → design (if structural change) → development → testing → documentation

---

## When to use this skill

- Adding a feature to a running system
- Fixing a defect in production code
- Integrating with an existing service
- Modifying an API, data model, or configuration that other components depend on

---

## Inputs

- Description of the change requested
- Existing codebase (read before writing anything)
- Current test suite results

---

## Workflow

### Phase 1 — Understand before touching

**Read the code before writing any.** Specifically:

1. Identify the **entry points** most relevant to this change (controllers, handlers, consumers).
2. Trace the **call chain** from entry point to data store or external system.
3. Read the **existing tests** for the area being changed — they define the intended behaviour.
4. Identify the **patterns already in use**: naming conventions, error handling style, logging style,
   configuration style, dependency injection approach.
5. Note any **existing TODOs, known limitations, or deferred work** in the affected area.

Do not infer patterns from partial reading. Read the actual files.

### Phase 2 — Assess impact before designing

Before writing a single line of implementation:

1. **Blast radius**: list every file and component that this change could affect.
2. **Backward compatibility**: identify any API, event schema, or database column that other
   components depend on. Changes here require a migration strategy, not a direct rename.
3. **Database migrations**: if the change touches a schema, confirm the migration is additive first.
   Never drop a column or table without a deprecation window.
4. **Configuration drift**: confirm any new configuration keys have sensible defaults so the service
   can start without them set.

### Phase 3 — Implement with minimal footprint

- Follow the project's **existing patterns exactly** — do not introduce a new style.
- Make the **smallest change** that satisfies the requirement.
- Do not refactor unrelated code in the same change unless the task explicitly asks for it.
- Keep each logical change in its own commit so the diff is reviewable.

### Phase 4 — Validate backward compatibility

- Run the **existing test suite** before and after the change; it must be fully green after.
- For API changes: add a test that proves the old contract still works (or is intentionally broken
  with a version bump).
- For database changes: add a migration test that starts from an empty schema and migrates forward
  successfully.

---

## Outputs

- Changed files with the minimal diff required
- Existing test suite green
- New tests covering the changed behaviour
- Assessment of backward compatibility documented (or confirmed not applicable)

---

## Anti-patterns to avoid

- Rewriting code that was not part of the task scope ("while I'm in here...")
- Introducing a new framework or library alongside the existing one
- Changing naming conventions in files you were not asked to touch
- Dropping or renaming database columns without a migration strategy
- Hardcoding values that the existing code externalised to configuration
