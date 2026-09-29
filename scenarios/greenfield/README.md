# greenfield scenario

- `workflow.yaml`: the plan as data (nodes, dependencies, gates, autonomy, retries).
- `fixtures/<node>[/fallback][/<variant>]/attempt<N>/`: what the fixture-driven agent proposes on attempt N: `proposal.json` (rationale, derivedFrom, data) plus files that mirror the workspace. These are real code and real tests, compiled and run by the real gates.

Run with `make demo-greenfield`. Walkthrough: [docs/scenarios/greenfield.md](../../docs/scenarios/greenfield.md).
