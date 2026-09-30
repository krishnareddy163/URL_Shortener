/**
 * Load test — sustained realistic traffic.
 *
 * Profile : ramp 0→10 VUs over 30 s · hold 10 VUs for 2 min · ramp down 30 s
 * Mix     : 60 % redirect · 20 % stats · 20 % create
 *
 * The rate limiter allows 20 req/min per client IP for POST /api/v1/links.
 * In load tests all VUs share the same source IP, so 429 responses on create
 * are expected and tracked via the custom `rate_limited` metric, not counted
 * as errors. Redirect and stats are not rate-limited.
 *
 * Thresholds (hard — fail CI):
 *   p95 redirect < 150 ms
 *   p95 stats    < 200 ms
 *   p95 create   < 300 ms  (includes rate-limited 429s, which are fast)
 *   non-rate-limit error rate < 1 %
 *
 * Usage:
 *   k6 run perf/k6/load.js
 *   k6 run --env BASE_URL=http://localhost:8080 perf/k6/load.js
 */
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { createLink, resolveLink, getStats, BASE_URL, setupBaseline } from './lib/client.js';

// True errors: unexpected 4xx/5xx (429 does NOT count).
const errorRate    = new Rate('error_rate');
// How often the rate limiter fired on create requests.
const rateLimited  = new Rate('rate_limited');

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // ramp up
    { duration: '2m',  target: 10 },   // hold
    { duration: '30s', target: 0  },   // ramp down
  ],
  thresholds: {
    // Per-endpoint latency — tagged by k6 { endpoint } tag set in client.js
    'http_req_duration{endpoint:redirect}': ['p(95)<150'],
    'http_req_duration{endpoint:stats}':    ['p(95)<200'],
    'http_req_duration{endpoint:create}':   ['p(95)<300'],
    // Real error rate (excluding expected 429s)
    error_rate: ['rate<0.01'],
  },
};

export function setup() {
  console.log(`Load target: ${BASE_URL}`);
  const code = setupBaseline('load');
  console.log(`Baseline short code: ${code}`);
  return { code };
}

export default function ({ code }) {
  const rng = Math.random();

  if (rng < 0.60) {
    // ── Redirect (60 %) — not rate-limited ───────────────────────────────
    const res = resolveLink(code);
    const ok  = res.status === 302;
    errorRate.add(!ok);
    check(res, { 'redirect: 302': () => ok });

  } else if (rng < 0.80) {
    // ── Stats (20 %) — not rate-limited ──────────────────────────────────
    const res = getStats(code);
    const ok  = res.status === 200;
    errorRate.add(!ok);
    check(res, { 'stats: 200': () => ok });

  } else {
    // ── Create (20 %) — rate-limited after burst ──────────────────────────
    const url = `https://load.example.com/${__VU}/${__ITER}`;
    const res = createLink(url);

    const isRateLimited = res.status === 429;
    const isSuccess     = res.status === 200 || res.status === 201;
    rateLimited.add(isRateLimited);
    // 429 is expected; only flag genuine errors
    errorRate.add(!isSuccess && !isRateLimited);

    check(res, {
      'create: 2xx or 429': () => isSuccess || isRateLimited,
    });
  }

  // 0–300 ms think time keeps total throughput realistic and avoids a
  // thundering herd that would trivially trip the rate limiter.
  sleep(Math.random() * 0.3);
}

export function teardown() {
  console.log('Load test complete.');
}
