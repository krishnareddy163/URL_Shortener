# Skills Registry

Central manifest of all skill files, their current versions, and change history.  
**Update this file in the same commit as any skill change.**

---

## How to read this file

- **Version**: current version of the skill file (semver: MAJOR.MINOR.PATCH)
- **Updated**: date the skill was last changed
- **Latest change**: one-line summary of what the most recent version changed

---

## Orchestration

| Skill | File | Version | Updated | Latest change |
|---|---|---|---|---|
| Orchestration | [orchestration/SKILL.md](orchestration/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |

---

## Scenarios

| Skill | File | Version | Updated | Latest change |
|---|---|---|---|---|
| Greenfield | [scenarios/greenfield/SKILL.md](scenarios/greenfield/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |
| Brownfield | [scenarios/brownfield/SKILL.md](scenarios/brownfield/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |
| Ambiguous | [scenarios/ambiguous/SKILL.md](scenarios/ambiguous/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |
| Bug Fix | [scenarios/bugfix/SKILL.md](scenarios/bugfix/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |
| Refactoring | [scenarios/refactoring/SKILL.md](scenarios/refactoring/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |
| Performance | [scenarios/performance/SKILL.md](scenarios/performance/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |

---

## Engineering

| Skill | File | Version | Updated | Latest change |
|---|---|---|---|---|
| Analysis | [engineering/analysis/SKILL.md](engineering/analysis/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |
| Design | [engineering/design/SKILL.md](engineering/design/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |
| Development | [engineering/development/SKILL.md](engineering/development/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |
| Testing | [engineering/testing/SKILL.md](engineering/testing/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |
| Documentation | [engineering/documentation/SKILL.md](engineering/documentation/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |

---

## Quality

| Skill | File | Version | Updated | Latest change |
|---|---|---|---|---|
| Observability | [quality/observability/SKILL.md](quality/observability/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |
| Security | [quality/security/SKILL.md](quality/security/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |
| Performance | [quality/performance/SKILL.md](quality/performance/SKILL.md) | 1.0.0 | 2026-09-29 | Initial version |

---

## Full changelog

All changes across all skills, newest first.

| Date | Skill | Version | Change |
|---|---|---|---|
| 2026-09-29 | ALL | 1.0.0 | Initial skill library created |

---

## Versioning rules (summary)

| What changed | Version bump |
|---|---|
| Removed/renamed required section; changed decision logic | MAJOR (`2.0.0`) |
| New section, checklist item, workflow step, or example | MINOR (`1.1.0`) |
| Wording clarification, typo fix, reordering | PATCH (`1.0.1`) |

Steps when editing a skill:
1. Bump the version in the skill file's frontmatter
2. Prepend a new entry to its `changes` list (newest first)
3. Update `updated` date in the frontmatter
4. Update the matching row in this registry
5. Append a row to the **Full changelog** table above
6. Commit skill file + registry together
