# SDLC Scenarios

The walkthroughs live in [docs/scenarios/](scenarios/); each has a committed sample report in [docs/sample-runs/](sample-runs/) and a runnable workflow in [scenarios/](../scenarios/).

| Scenario | Requirement | Walkthrough | Run it | Shows |
|---|---|---|---|---|
| Greenfield | Build the URL shortener from an empty workspace | [greenfield.md](scenarios/greenfield.md) | `make demo-greenfield` | Decomposition into 10 nodes, parallel waves and joins, 3 human checkpoints, retry with feedback |
| Brownfield | "Links must be able to expire" on the v1 baseline | [brownfield.md](scenarios/brownfield.md) | `make demo-brownfield` | Codebase impact analysis, runtime graph patch (`db_migration`), regression gate |
| Ambiguous | "Make links more secure and add some analytics" | [ambiguous.md](scenarios/ambiguous.md) | `make demo-ambiguous` | Blocking clarification, invalidation cascade and re-plan when the answer changes |
| Bugfix | SSRF bypass via absolute host names | [bugfix.md](scenarios/bugfix.md) | `make demo-bugfix` | Reproduce-before-fix, behaviour-preserving refactor |

`make demo-all` runs all four in MOCK mode with the real build, test and policy gates.
