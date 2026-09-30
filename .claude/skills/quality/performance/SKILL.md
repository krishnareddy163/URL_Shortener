# Performance Quality Skill

**Purpose:** Define how to measure, benchmark, and validate performance for production code.  
**Prerequisites:** Development skill outputs.  
**Companion skills:** `scenarios/performance/SKILL.md` (when performance is the primary task)

---

## When to use this skill

- Writing code that will execute on a hot path (every request, every message)
- Adding database queries to frequently-called code
- Implementing caching, connection pooling, or thread management
- Optimising an identified bottleneck (after `scenarios/performance/SKILL.md` diagnosed it)

---

## Measurement first

Never optimise without a number. For every performance-sensitive change, record:

- **What was measured**: method name, endpoint path, operation
- **How it was measured**: tool, load profile, environment
- **Baseline**: number before the change
- **Target**: number required
- **Result**: number after the change

If you cannot measure it, document why and what proxy metric you are using instead.

---

## Profiling tools

| Language | CPU profiler | Memory profiler | DB query analyser |
|---|---|---|---|
| Java | async-profiler, JFR (Java Flight Recorder) | Eclipse Memory Analyzer, JFR | EXPLAIN ANALYZE (PostgreSQL), slow query log |
| Python | `cProfile`, `py-spy` | `memray`, `tracemalloc` | SQLAlchemy query logging |
| Go | `pprof` | `pprof` heap | pgx query tracing |
| Node | `--prof`, Chrome DevTools | `--inspect` + heap snapshot | Sequelize debug logging |

---

## Common bottleneck patterns and fixes

### N+1 query

**Symptom:** Query count grows linearly with result set size.  
**Detection:** Log query counts per request; > 5 queries for a single business operation is suspicious.  
**Fix:** Eager-load related entities in one query (JOIN or `IN` clause), or use a batch loader.

### Missing index

**Symptom:** Query time grows with table size (full table scan).  
**Detection:** `EXPLAIN ANALYZE` shows `Seq Scan` on a large table.  
**Fix:** Add an index on the column(s) in the `WHERE` clause. Verify the query planner uses it.

### Synchronous I/O on a hot path

**Symptom:** Threads blocked on I/O; high p99 latency even at low load.  
**Detection:** Thread dump shows threads in `WAITING` state in I/O calls.  
**Fix:** Move I/O to an async model or a dedicated thread pool; use reactive/non-blocking libraries.

### Unbounded result sets

**Symptom:** Memory usage grows with data volume; OOM errors.  
**Detection:** Heap profile shows large `List<Entity>` objects; no pagination in queries.  
**Fix:** Add `LIMIT`/`OFFSET` or cursor-based pagination; stream large results instead of loading all.

### Object allocation pressure

**Symptom:** GC pause time is high; CPU is 30%+ in GC even at moderate load.  
**Detection:** JFR allocation profiler; GC logs showing frequent minor collections.  
**Fix:** Reduce short-lived object creation; reuse buffers; use primitive arrays instead of boxed types on hot paths.

### Lock contention

**Symptom:** Throughput plateaus under load; threads spend time in `BLOCKED` state.  
**Detection:** Thread dump; `jstack`; lock profiler.  
**Fix:** Reduce lock scope; use lock-free data structures (`ConcurrentHashMap`, `AtomicLong`);
consider sharding if the contended resource is a counter.

---

## Database performance checklist

- [ ] Every `WHERE` clause column used in high-traffic queries has an index
- [ ] `EXPLAIN ANALYZE` has been run on all new queries with realistic data volumes
- [ ] Queries that could return unbounded rows have a `LIMIT`
- [ ] Connection pool size is configured explicitly (not default); pool exhaustion is monitored
- [ ] Queries inside a transaction are as short as possible (no long-running transactions)
- [ ] N+1 queries are eliminated in list/search endpoints

---

## Caching guidelines

Use caching only when:
- The data is read far more often than it is written
- The data is not required to be perfectly consistent (a 30-second staleness is acceptable)
- The cache improves measured latency or throughput meaningfully

When adding a cache:
- Define the invalidation strategy before caching (TTL, event-driven, or write-through)
- In a horizontally-scaled service, use a shared cache (Redis, Memcached), not an in-process map
- Size the cache and monitor eviction rate (high eviction = cache too small = not helping)
- Test the behaviour when the cache is empty (cold start) and when it is full

---

## Benchmarking

For micro-benchmarks (Java):
- Use JMH (Java Microbenchmark Harness) — never use `System.currentTimeMillis()` loops
- Warm up the JVM before recording results (at least 5 warmup iterations)
- Run with realistic data sizes, not trivially small inputs

For load tests:
- Use k6, Gatling, or wrk for HTTP load testing
- Ramp up load gradually (don't start at peak)
- Measure at p50, p95, p99, and p999
- Run long enough to see steady-state GC and connection pool behaviour (at least 5 minutes)

---

## Performance regression prevention

After an optimisation, add a performance regression guard:

- **Query count assertion**: use a query-counting data source in tests to assert the number of
  queries for a given operation does not exceed a known limit
- **Benchmark baseline**: commit JMH results alongside the code; CI alerts if they regress
- **Load test in CI**: add a lightweight load test that fails if p99 exceeds the SLO
