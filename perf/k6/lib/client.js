/**
 * Shared HTTP helpers for the URL shortener k6 test suite.
 * BASE_URL is read from the k6 environment variable (--env BASE_URL=...) and
 * falls back to http://localhost:8080 for local runs.
 */
import http from 'k6/http';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

/**
 * POST /api/v1/links
 * Returns the raw k6 Response. 201 = new, 200 = already shortened, 429 = rate limited.
 * 429 is expected under load and is NOT counted as a failed request by k6
 * (responseCallback whitelists it so it does not inflate http_req_failed).
 */
export function createLink(url, customAlias) {
  const payload = { url };
  if (customAlias) payload.customAlias = customAlias;
  return http.post(`${BASE_URL}/api/v1/links`, JSON.stringify(payload), {
    headers: JSON_HEADERS,
    redirects: 0,
    tags: { endpoint: 'create' },
    responseCallback: http.expectedStatuses(200, 201, 429),
  });
}

/**
 * GET /{code} — short-link redirect.
 * redirects:0 so we see the 302 directly rather than chasing the destination.
 */
export function resolveLink(code) {
  return http.get(`${BASE_URL}/${code}`, {
    redirects: 0,
    tags: { endpoint: 'redirect' },
  });
}

/**
 * GET /api/v1/links/{code}/stats
 */
export function getStats(code) {
  return http.get(`${BASE_URL}/api/v1/links/${code}/stats`, {
    tags: { endpoint: 'stats' },
  });
}

/**
 * GET /actuator/health
 */
export function health() {
  return http.get(`${BASE_URL}/actuator/health`, {
    tags: { endpoint: 'health' },
  });
}

/**
 * Creates a single baseline link and returns its code.
 * Used in k6 setup() functions so every VU shares a resolvable code.
 */
export function setupBaseline(label) {
  const res = createLink(`https://perf.example.com/${label}/${Date.now()}`);
  if (res.status !== 201 && res.status !== 200) {
    throw new Error(`setup: baseline link creation failed with ${res.status}: ${res.body}`);
  }
  return JSON.parse(res.body).code;
}
