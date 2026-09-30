# Observability Skill

**Purpose:** Ensure every production change is visible, diagnosable, and measurable in operations.  
**Prerequisites:** Development skill outputs.  
**Companion skills:** `quality/performance/SKILL.md` (SLI/SLO measurement)

---

## When to use this skill

- Adding a new endpoint, background job, consumer, or scheduled task
- Adding or changing an integration with an external system
- Adding new error paths or error codes
- Deploying to a new environment (Kubernetes, cloud function, container)
- Any change where "is it working?" is not immediately obvious from the application logs

---

## The three pillars

### 1. Structured logging

**Format:** JSON (or key=value) — never free-text log lines that cannot be indexed.

**Required fields on every log line:**

| Field | Description | Example |
|---|---|---|
| `timestamp` | ISO-8601 UTC | `2026-09-29T14:22:01.123Z` |
| `level` | Log level | `INFO` |
| `logger` | Class or component name | `com.example.shortener.api.LinkController` |
| `requestId` | Correlation ID (from MDC/context) | `a3f2c1d9-…` |
| `message` | What happened | `Link created` |
| `traceId` | Distributed trace ID (if tracing enabled) | `4bf92f3577b34da6` |

**Log levels:**

| Level | When to use |
|---|---|
| `ERROR` | Something failed that requires human investigation; page-worthy |
| `WARN` | Degraded operation; something is wrong but the system is still running |
| `INFO` | Normal business events: request received, resource created, job completed |
| `DEBUG` | Developer details: SQL queries, serialised payloads, internal state |

**Never log:**
- Passwords, tokens, API keys, private keys
- PII (email, name, SSN, card numbers) without masking
- Full request bodies that may contain any of the above
- User-controlled input directly into a log message (log injection risk)

**Implementation for Spring Boot (this project):**
- Use `logback.xml` with `LogstashEncoder` for JSON output
- Use `MDC.put("requestId", ...)` in the request filter for correlation
- Use `MDC.put("traceId", ...)` when integrating with OpenTelemetry

### 2. Metrics

**Categories:**

| Category | Metric examples |
|---|---|
| **RED (Request rate, Errors, Duration)** | `http_requests_total{method,path,status}`, `http_request_duration_seconds` |
| **USE (Utilisation, Saturation, Errors)** | `jvm_memory_used_bytes`, `db_connection_pool_active`, `db_connection_pool_pending` |
| **Business metrics** | `links_created_total`, `redirects_total`, `clicks_recorded_total` |

**Implementation for Spring Boot (this project):**
- `spring-boot-starter-actuator` exposes `/actuator/metrics` via Micrometer
- Tag metrics with `application`, `instance`, `environment` for filtering
- Expose at `/actuator/prometheus` when a Prometheus scraper is configured
- Custom counters: `meterRegistry.counter("links.created").increment()`

**Histogram for latency:**
```java
Timer.Sample sample = Timer.start(meterRegistry);
// ... do the work ...
sample.stop(meterRegistry.timer("http.server.requests", "uri", "/links"));
```

### 3. Distributed tracing

**When to add:**
- Service calls another service over HTTP, gRPC, or message queue
- Async processing where a request spawns background work
- Database queries on hot paths (to identify slow queries in traces)

**Implementation:**
- Use OpenTelemetry SDK with the OTLP exporter (Jaeger, Tempo, X-Ray)
- Spring Boot: `io.opentelemetry:opentelemetry-spring-boot-starter`
- Propagate context via W3C `traceparent` / `tracestate` headers
- Add the `traceId` and `spanId` from the current span to MDC for log correlation:
  ```java
  Span span = Span.current();
  MDC.put("traceId", span.getSpanContext().getTraceId());
  MDC.put("spanId",  span.getSpanContext().getSpanId());
  ```

**Span naming:**
- HTTP server spans: auto-instrumented by the agent / starter
- DB spans: auto-instrumented by JDBC instrumentation
- Custom business spans: name them as operations, not code: `"link.create"` not `"LinkService.createLink"`

---

## Health checks

**Liveness probe** (`/actuator/health/liveness`):  
Returns `UP` if the application is running and not in a broken state that requires restart.  
Never check external dependencies here — a slow database should not kill the pod.

**Readiness probe** (`/actuator/health/readiness`):  
Returns `UP` if the application can serve traffic. Check database connectivity here.  
A failed readiness probe removes the pod from the load balancer without killing it.

**Spring Boot Actuator configuration:**
```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.probes.enabled=true
management.endpoint.health.show-details=never   # never in production
```

**Custom health indicator:**
```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    public Health health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Health.up().build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

---

## Correlation IDs

Every inbound request must carry a unique request ID through the entire call chain:

1. **At the edge**: read `X-Request-Id` from the incoming request header. If absent, generate a UUID.
2. **In MDC**: store the ID in `MDC.put("requestId", id)` before any log line is written.
3. **In responses**: echo the ID in the `X-Request-Id` response header so clients can correlate.
4. **On outbound calls**: propagate the ID in the `X-Request-Id` header of every downstream HTTP call.
5. **Cleanup**: remove from MDC in a `finally` block to prevent thread-pool leakage.

---

## SLI / SLO definition

For each new service or feature, define:

| SLI | Measurement | SLO |
|---|---|---|
| Availability | `sum(rate(http_requests_total{status!~"5.."}[5m])) / sum(rate(http_requests_total[5m]))` | ≥ 99.9% |
| Latency | `histogram_quantile(0.99, http_request_duration_seconds_bucket)` | p99 < 200ms |
| Error rate | `sum(rate(http_requests_total{status=~"5.."}[5m]))` | < 0.1% |

---

## Alerting

Define alerts for:
- Error rate above SLO threshold for 5 minutes
- p99 latency above SLO threshold for 5 minutes
- Health check failing (pod restarts or readiness probe failure)
- DB connection pool exhausted (saturation > 90%)
- Background job not running (heartbeat metric not updated within 2× its expected interval)

---

## Observability checklist

Before declaring any code change complete, verify:

- [ ] Every new endpoint has ERROR-level logging for 5xx errors
- [ ] Every new endpoint has INFO-level logging for the request received and response returned
- [ ] Correlation ID (requestId) is present in every log line
- [ ] New error codes log at the appropriate severity (P1=ERROR, P2=WARN, P3/P4=INFO)
- [ ] Health check endpoints are configured
- [ ] `/actuator/metrics` includes relevant counters and timers for the new functionality
- [ ] Any new external integration has timeout and error logging on failure
