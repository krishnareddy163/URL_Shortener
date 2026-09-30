# URL Shortener — k6 Performance Tests

Three test suites covering smoke, load, and stress profiles. All scripts read
`BASE_URL` from the k6 environment and fall back to `http://localhost:8080`.

## Prerequisites

Install k6 (https://k6.io/docs/get-started/installation/):

```bash
# macOS
brew install k6

# Linux (Debian/Ubuntu)
sudo gpg --no-default-keyring \
  --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 \
  --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update -q && sudo apt-get install -q k6
```

## Quickstart

Start the service first:
```bash
cd shortener-service && mvn spring-boot:run
```

Then, from the repo root:
```bash
make perf          # smoke only (default)
make perf-load     # load test
make perf-stress   # stress test
```

Or use the script directly:
```bash
scripts/perf.sh smoke    # will start the service automatically if not running
scripts/perf.sh load
scripts/perf.sh stress
```

## Suites

| Suite   | VUs      | Duration  | Purpose                                      | CI?              |
|---------|----------|-----------|----------------------------------------------|------------------|
| smoke   | 1        | 30 s      | All endpoints respond correctly; fail fast    | Every push       |
| load    | 0 → 10   | ~3 min    | Sustained traffic; p95 latency SLOs enforced  | main + dispatch  |
| stress  | 0 → 100  | ~6 min    | Find saturation point; warns, does not block  | main + dispatch  |

## Traffic mix

| Endpoint              | smoke | load | stress |
|-----------------------|-------|------|--------|
| GET /{code}           | ✓     | 60 % | 70 %   |
| GET /…/stats          | ✓     | 20 % | 15 %   |
| POST /api/v1/links    | ✓     | 20 % | 15 %   |
| GET /actuator/health  | ✓     | —    | —      |

## Rate limiting

`POST /api/v1/links` is rate-limited to **20 req/min per source IP**. Under load
and stress all VUs share one IP, so `429` responses are expected on that endpoint
and are tracked by the custom `rate_limited` metric — they are **not** counted as
errors. The `error_rate` metric covers only genuine unexpected failures.

## Results

k6 writes JSON summaries to `target/perf/` and prints a text summary to stdout.
CI uploads the JSON files as the `perf-results` artifact.
