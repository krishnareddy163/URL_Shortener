/**
 * Smoke test — sanity check that every endpoint is alive.
 *
 * Profile : 1 VU · 30 s
 * Purpose : Confirm the service starts correctly and all three API paths
 *           (create, redirect, stats) return the expected status codes.
 *           Runs on every CI push; a threshold breach fails the pipeline.
 *
 * Usage:
 *   k6 run perf/k6/smoke.js
 *   k6 run --env BASE_URL=http://localhost:8080 perf/k6/smoke.js
 */
import { check, sleep } from 'k6';
import { createLink, resolveLink, getStats, health, BASE_URL, setupBaseline } from './lib/client.js';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    // All checks must pass — create checks accept 429 explicitly (see below)
    checks:            ['rate==1.0'],
    // p95 under 500 ms on a cold local JVM is generous but safe for CI
    http_req_duration: ['p(95)<500'],
    // 429 on create is whitelisted in client.js; this catches genuine errors only
    http_req_failed:   ['rate<0.01'],
  },
};

// Create one link before the test loop so redirect/stats tests always
// have a valid code — avoids accidentally testing 404 paths.
export function setup() {
  console.log(`Smoke target: ${BASE_URL}`);
  const code = setupBaseline('smoke');
  console.log(`Baseline short code: ${code}`);
  return { code };
}

export default function ({ code }) {
  // ── Health ────────────────────────────────────────────────────────────────
  const h = health();
  check(h, {
    'health: 200':    r => r.status === 200,
    'health: UP':     r => JSON.parse(r.body).status === 'UP',
  });

  // ── Create ────────────────────────────────────────────────────────────────
  // Counter-based URLs avoid the duplicate-URL 200 branch.
  // 429 is acceptable: the token bucket starts full (20 tokens) but a 30 s
  // run at ~1 req/s exhausts burst capacity after ~20 iterations.
  // client.js whitelists 429 in responseCallback so it never inflates
  // http_req_failed; the checks below accept it explicitly.
  const url = `https://smoke.example.com/${__VU}/${__ITER}`;
  const cr = createLink(url);
  const createOk = cr.status === 200 || cr.status === 201 || cr.status === 429;
  check(cr, {
    'create: 2xx or rate-limited': () => createOk,
    'create: has code (when not 429)': r => {
      if (r.status === 429) return true;
      try { return Boolean(JSON.parse(r.body).code); } catch { return false; }
    },
    'create: has shortUrl (when not 429)': r => {
      if (r.status === 429) return true;
      try { return Boolean(JSON.parse(r.body).shortUrl); } catch { return false; }
    },
  });

  // ── Redirect ──────────────────────────────────────────────────────────────
  const rr = resolveLink(code);
  check(rr, {
    'redirect: 302':           r => r.status === 302,
    'redirect: Location set':  r => Boolean(r.headers['Location']),
  });

  // ── Stats ─────────────────────────────────────────────────────────────────
  const sr = getStats(code);
  check(sr, {
    'stats: 200':            r => r.status === 200,
    'stats: has totalClicks':r => {
      try { return typeof JSON.parse(r.body).totalClicks === 'number'; } catch { return false; }
    },
  });

  sleep(1);
}

export function teardown({ code }) {
  console.log(`Smoke complete. Baseline code was: ${code}`);
}
