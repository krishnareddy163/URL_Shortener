/**
 * Stress test — find the service's saturation point.
 *
 * Profile:
 *   0 → 20 VUs  in 1 min  (warm-up)
 *  20 → 50 VUs  over 2 min (medium stress)
 *  50 → 100 VUs over 2 min (high stress)
 * 100 → 0  VUs  in 1 min  (recovery)
 *
 * Mix : 70 % redirect · 15 % stats · 15 % create
 * Redirect and stats are not rate-limited; create will 429 heavily — tracked
 * separately and not counted in the error budget.
 *
 * Thresholds here are intentionally loose: the goal is to observe where
 * latency climbs and the error rate rises, not to enforce SLOs at peak load.
 * The pipeline treats threshold failures in this job as warnings (continue-on-error).
 *
 * Usage:
 *   k6 run perf/k6/stress.js
 *   k6 run --env BASE_URL=http://localhost:8080 perf/k6/stress.js
 */
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { createLink, resolveLink, getStats, BASE_URL, setupBaseline } from './lib/client.js';

const errorRate   = new Rate('error_rate');
const rateLimited = new Rate('rate_limited');
const redirectLat = new Trend('redirect_latency', true);
const statsLat    = new Trend('stats_latency', true);

export const options = {
  stages: [
    { duration: '1m',  target: 20  },
    { duration: '2m',  target: 50  },
    { duration: '2m',  target: 100 },
    { duration: '1m',  target: 0   },
  ],
  thresholds: {
    // p99 must stay under 2 s even at peak — beyond this the service is unusable
    'http_req_duration{endpoint:redirect}': ['p(99)<2000'],
    'http_req_duration{endpoint:stats}':    ['p(99)<2000'],
    // Non-rate-limit error rate must stay under 5 % even at saturation
    error_rate: ['rate<0.05'],
  },
};

export function setup() {
  console.log(`Stress target: ${BASE_URL}`);
  const code = setupBaseline('stress');
  console.log(`Baseline short code: ${code}`);
  return { code };
}

export default function ({ code }) {
  const rng = Math.random();

  if (rng < 0.70) {
    // ── Redirect (70 %) ───────────────────────────────────────────────────
    const res = resolveLink(code);
    redirectLat.add(res.timings.duration);
    const ok = res.status === 302;
    errorRate.add(!ok);
    check(res, { 'redirect: 302': () => ok });

  } else if (rng < 0.85) {
    // ── Stats (15 %) ──────────────────────────────────────────────────────
    const res = getStats(code);
    statsLat.add(res.timings.duration);
    const ok = res.status === 200;
    errorRate.add(!ok);
    check(res, { 'stats: 200': () => ok });

  } else {
    // ── Create (15 %) ─────────────────────────────────────────────────────
    const res = createLink(`https://stress.example.com/${__VU}/${__ITER}`);
    const isRateLimited = res.status === 429;
    const isSuccess     = res.status === 200 || res.status === 201;
    rateLimited.add(isRateLimited);
    errorRate.add(!isSuccess && !isRateLimited);
  }

  // Minimal think time: stress deliberately saturates the service.
  sleep(0.05);
}

export function teardown() {
  console.log('Stress test complete. Check redirect_latency and stats_latency trends for saturation point.');
}
