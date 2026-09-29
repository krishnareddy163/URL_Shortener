#!/usr/bin/env bash
# LIVE smoke test: starts a real-model run of the bug-fix scenario and checks that the model API was reached.
# Needs ANTHROPIC_API_KEY and ANTHROPIC_MODEL (and a built orchestrator.jar, see `make build`).
#
# Passes when the run ends in any governed outcome (completed, paused for a human, or safe-stopped: live output
# varies, and a safe-stop is the engine doing its job) and the event log records model tokens. Fails on a usage
# error, or when no tokens were recorded, which means every call failed (for example an invalid key) and the run
# only reached its fixture fallbacks. Nothing is approved: the run stops at its first human checkpoint.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
JAR="orchestrator/target/orchestrator.jar"
RUN="live-smoke-$(date -u +%Y%m%d-%H%M%S)"

if [[ -z "${ANTHROPIC_API_KEY:-}" || -z "${ANTHROPIC_MODEL:-}" ]]; then
  echo "The LIVE smoke test needs ANTHROPIC_API_KEY and ANTHROPIC_MODEL." >&2
  exit 2
fi
if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR: run 'make build' first." >&2
  exit 2
fi

set +e
java -jar "$JAR" run scenarios/bugfix/workflow.yaml --run-id "$RUN" --mode LIVE --no-color
code=$?
set -e
case "$code" in
  0) outcome="completed" ;;
  10) outcome="paused for a human" ;;
  20) outcome="safe-stopped" ;;
  *) echo "LIVE smoke: run failed with exit $code" >&2; exit 1 ;;
esac

usage="$(java -jar "$JAR" status "$RUN" --no-color | sed -n 's/^Model calls \([0-9]*\), input tokens \([0-9]*\), output tokens \([0-9]*\)$/\1 \2 \3/p')"
read -r calls input output <<< "${usage:-0 0 0}"
java -jar "$JAR" report "$RUN" > /dev/null
if (( input == 0 )); then
  echo "LIVE smoke: the run $outcome, but no model tokens were recorded: the API was never reached." >&2
  exit 1
fi
echo "LIVE smoke passed: run $RUN $outcome after $calls model calls ($input input / $output output tokens)."
echo "Report: runs/$RUN/report.md"
