---
version: 1.0.0
updated: 2026-09-29
changes:
  - "1.0.0 (2026-09-29): Initial version"
---

# Ambiguous Requirements Scenario Skill

**Purpose:** Surface and resolve unclear, missing, or conflicting requirements before writing code.  
**Prerequisites:** Orchestration plan is defined.  
**Next skills:** after resolution → return to orchestration and re-plan

---

## When to use this skill

- The request does not specify expected behaviour for an important input or state
- Two parts of the request contradict each other
- The request uses vague terms ("fast", "secure", "flexible") without measurable definitions
- The scope is unclear (what is in, what is out)
- Key stakeholders, users, or systems are unnamed

Use this skill **before** analysis, design, or development. Ambiguity that reaches implementation
creates waste and rework.

---

## Inputs

- The user's original request (verbatim)
- Any supporting context provided (existing docs, prior conversation, code)

---

## Workflow

### Step 1 — Identify ambiguity categories

Scan the request against these categories and list every instance found:

| Category | Examples |
|---|---|
| **Missing requirement** | No mention of authentication, error behaviour, pagination, rate limits |
| **Conflicting requirement** | "must be synchronous" AND "must not block the caller" |
| **Undefined term** | "should be fast" — fast means different things at p50 vs p99 |
| **Unbounded scope** | "support all file types", "handle any input" |
| **Implicit dependency** | Requires a user concept but no user system is defined |
| **Untested assumption** | "users will always provide a valid email" |
| **Missing non-functional** | No SLA, no scale target, no compliance requirement stated |

### Step 2 — Prioritise by impact

For each ambiguity, assess:
- **Impact if wrong**: will it require a rework of the API, data model, or architecture?
- **Resolvable by assumption**: can a reasonable, explicit assumption be made without risk?

Only escalate ambiguities that have high rework impact **and** cannot be safely assumed.

### Step 3 — Resolve (choose one path)

**Path A — Ask targeted questions (preferred when user is available)**

Ask only the questions that block progress. No more than 5 questions at once. Each question must:
- Be specific and answerable with a short response
- Explain why the answer changes the design

**Path B — Document assumptions and proceed**

When clarification is not possible (batch processing, automated pipeline), document every assumption
explicitly before proceeding:

```
ASSUMPTIONS (must be validated before production):
1. [assumption] because [reason it was assumed]
2. ...
```

Place this block at the top of the analysis output. Flag each assumption-dependent design decision
inline with `// ASSUMPTION: <N>` comments.

### Step 4 — Update the execution plan

After resolution, return to the orchestration skill and revise the plan if the clarified
requirements change the scenario classification or skill selection.

---

## Outputs

- Numbered list of identified ambiguities with category and impact assessment
- Either: targeted questions for the user
- Or: explicit documented assumptions ready for validation
- Revised orchestration plan (if classification changed)

---

## Stopping conditions

Stop asking questions and document assumptions if:
- The user has already answered twice and re-introduced the same ambiguity
- The ambiguity is at a detail level that a reasonable default handles (e.g. default page size = 20)
- Resolving the ambiguity requires domain knowledge the user would have to research themselves
