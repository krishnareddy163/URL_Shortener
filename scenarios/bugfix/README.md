# bugfix scenario

- `workflow.yaml`: the plan as data (nodes, dependencies, gates, autonomy, retries).
- `fixtures/<node>/attempt<N>/`: what the fixture-driven agent proposes on attempt N: `proposal.json` (rationale, derivedFrom, data) plus files that mirror the workspace. These are real code and real tests, compiled and run by the real gates.

The defect is real in the committed v1 baseline: `http://localhost./admin` is accepted. Run with `make demo-bugfix`. Walkthrough: [docs/scenarios/bugfix.md](../../docs/scenarios/bugfix.md).
