#!/usr/bin/env bash
# Run a k6 performance test against the shortener service.
# If the service is not already running on BASE_URL it is started automatically
# and shut down after the test completes.
#
# Usage:
#   scripts/perf.sh [smoke|load|stress]   (default: smoke)
#   BASE_URL=http://localhost:9090 scripts/perf.sh load
set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:-smoke}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
OUT_DIR="target/perf"

case "$MODE" in
  smoke|load|stress) ;;
  *) echo "Usage: $0 [smoke|load|stress]"; exit 1 ;;
esac

if ! command -v k6 &>/dev/null; then
  cat >&2 <<'MSG'
k6 is not installed. Install it from https://k6.io/docs/get-started/installation/:

  macOS:  brew install k6
  Linux:  see perf/k6/README.md
MSG
  exit 1
fi

mkdir -p "$OUT_DIR"

# ── Start the service if needed ───────────────────────────────────────────────
SERVICE_PID=""
health_ok() { curl -sf "${BASE_URL}/actuator/health" > /dev/null 2>&1; }

if health_ok; then
  echo "==> Service already running at ${BASE_URL}"
else
  echo "==> Starting shortener-service (logs → target/perf/service.log)"
  mvn -q -B -f shortener-service/pom.xml spring-boot:run \
    > "${OUT_DIR}/service.log" 2>&1 &
  SERVICE_PID=$!

  echo -n "==> Waiting for service"
  for i in $(seq 1 30); do
    if health_ok; then
      echo " ready"
      break
    fi
    printf '.'
    sleep 2
    if [[ $i -eq 30 ]]; then
      echo " TIMEOUT"
      echo "Service log:" && tail -30 "${OUT_DIR}/service.log" || true
      exit 1
    fi
  done
fi

# ── Cleanup on exit ───────────────────────────────────────────────────────────
cleanup() {
  if [[ -n "$SERVICE_PID" ]]; then
    echo "==> Stopping service (PID ${SERVICE_PID})"
    kill "$SERVICE_PID" 2>/dev/null || true
    wait "$SERVICE_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

# ── Run k6 ───────────────────────────────────────────────────────────────────
RESULT_FILE="${OUT_DIR}/${MODE}-results.json"
echo "==> k6 ${MODE} → ${BASE_URL}  (results → ${RESULT_FILE})"

k6 run \
  --env BASE_URL="${BASE_URL}" \
  --out "json=${RESULT_FILE}" \
  "perf/k6/${MODE}.js"

echo "==> Done. Results: ${RESULT_FILE}"
