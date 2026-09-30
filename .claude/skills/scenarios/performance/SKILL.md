---
version: 1.0.0
updated: 2026-09-29
changes:
  - "1.0.0 (2026-09-29): Initial version"
---

# Performance Optimisation Scenario Skill

**Purpose:** Identify, measure, and fix performance bottlenecks with evidence-driven changes.  
**Prerequisites:** Orchestration plan is defined. Always combined with `scenarios/brownfield/SKILL.md`.  
**Companion skills:** `quality/performance/SKILL.md` (benchmarking and profiling techniques)

---

## When to use this skill

- A latency or throughput target is being missed in production or load tests
- Resource consumption (CPU, memory, DB connections) is unsustainable at scale
- A specific slow path has been identified and needs optimisation

**Do not** use this skill to speculatively optimise code that has no measured performance problem.
Measure first, optimise second.

---

## Inputs

- Performance target (e.g. "p99 < 200ms at 1000 req/s")
- Current measured performance (baseline numbers, not estimates)
- Evidence of where time or resources are spent (profiler output, slow query log, trace)

**If you do not have a baseline measurement, stop and produce one before proceeding.**

---

## Workflow

### Step 1 — Establish the baseline

Before any change:

1. Define the performance metric precisely (p50, p99, throughput, memory at steady state).
2. Record the baseline measurement under realistic conditions.
3. Identify the bottleneck from profiler data, not from reading the code.

Common bottleneck locations:
- N+1 database queries (use query count logging or slow-query log)
- Missing database index (EXPLAIN ANALYZE on slow queries)
- Synchronous I/O on a hot path that could be async
- Object allocation rate causing GC pressure
- Lock contention (thread dump, lock profiler)
- Repeated computation that could be cached
- Oversized payloads (serialisation cost, network cost)

### Step 2 — Prioritise the bottleneck

Fix the largest bottleneck first. An optimisation that saves 5ms on a path that takes 2000ms
is invisible. Use Amdahl's Law: the speedup from optimising a portion of the work is bounded by
the fraction of time that portion takes.

### Step 3 — Implement the optimisation

Rules:
- One optimisation per change. Combining two makes it impossible to know which one helped.
- Follow the project's existing patterns; do not introduce a caching library the project does not
  already use without design approval.
- Add a comment next to the optimised code explaining *why* it is written the way it is, because
  optimised code is often non-obvious.
- Never trade correctness for performance. If the optimisation introduces a race condition or
  incorrect result under any input, it is not acceptable.

### Step 4 — Measure the result

After the change:

1. Re-run the benchmark under the same conditions as the baseline.
2. Record the new numbers.
3. Calculate the improvement: did it meet the target?
4. If it did not, return to Step 2 — do not guess at a second optimisation.

### Step 5 — Regression check

- Run the full test suite. All tests must pass.
- Add a performance regression test (benchmark, load test, or assertion on query count) that will
  catch a future regression to the pre-fix behaviour.

---

## Outputs

- Baseline measurement (before)
- Identified bottleneck with evidence
- Code change with explanation
- Post-change measurement (after)
- Performance regression test

---

## Anti-patterns to avoid

- Optimising without measuring ("this must be slow because...")
- Caching data that must be consistent, without an invalidation strategy
- Adding an in-memory cache in a horizontally-scaled service (each instance has a different cache)
- Moving work to a background thread and hiding a correctness bug behind timing
- Premature micro-optimisation (saving nanoseconds when the bottleneck is network or disk)
