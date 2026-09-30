# Claude Agent — Modular Skill-Based Architecture

This project uses a **modular skill library**. Before starting any engineering task, always load
`.claude/skills/orchestration/SKILL.md` to classify the task and select the appropriate skill combination.

## How the skill system works

Each skill file lives at `.claude/skills/<category>/<name>/SKILL.md`. Skills are self-contained
instruction sets with explicit inputs, outputs, and prerequisites. They are composed by the
orchestration layer — never executed blindly one after another.

```
.claude/skills/
├── orchestration/SKILL.md       ← always load this first
│
├── scenarios/
│   ├── greenfield/SKILL.md      ← net-new application or service
│   ├── brownfield/SKILL.md      ← changes to an existing codebase
│   ├── ambiguous/SKILL.md       ← unclear or conflicting requirements
│   ├── bugfix/SKILL.md          ← defect investigation and repair
│   ├── refactoring/SKILL.md     ← internal quality improvement
│   └── performance/SKILL.md     ← throughput, latency, resource optimisation
│
├── engineering/
│   ├── analysis/SKILL.md        ← requirements, constraints, acceptance criteria
│   ├── design/SKILL.md          ← architecture, API, data model, interactions
│   ├── development/SKILL.md     ← implementation, error handling, logging
│   ├── testing/SKILL.md         ← unit, integration, regression, coverage
│   └── documentation/SKILL.md  ← README, runbook, API docs, changelogs
│
└── quality/
    ├── security/SKILL.md        ← threat model, input validation, secrets, OWASP
    ├── performance/SKILL.md     ← profiling, bottleneck analysis, benchmarking
    └── observability/SKILL.md  ← structured logging, metrics, tracing, health
```

## Entry point

**Always start here:**

```
Read .claude/skills/orchestration/SKILL.md
```

The orchestration skill will instruct you which further skills to load and in what order.

## Principles

- Load only the skills the task requires — do not execute every skill for every task.
- Skills compose: a brownfield bug fix uses both the brownfield scenario skill and the bugfix skill.
- Each skill produces concrete, reviewable outputs; do not move to the next skill until the current
  one's outputs are complete.
- When a skill says "document assumptions", do so explicitly in the response before proceeding.
