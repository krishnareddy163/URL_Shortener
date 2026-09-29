#!/usr/bin/env bash
# Shared helpers for the demo scripts. Every orchestrator command is echoed before it runs so the
# transcript reads as a step-by-step walkthrough. Approvals always come from a named human actor.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
JAR="orchestrator/target/orchestrator.jar"
# shellcheck disable=SC2034  # used by the demo scripts that source this file
APPROVER="demo-reviewer"

if [[ ! -f "$JAR" ]]; then
  echo "Building the orchestrator (first run)..."
  mvn -q -B -pl orchestrator package -DskipTests
fi

if [[ -t 1 ]]; then BOLD=$'\033[1m'; RESET=$'\033[0m'; else BOLD=""; RESET=""; fi

# Joins arguments into a copy-pasteable command line, double-quoting any that contain spaces or shell characters.
quote_args() {
  local out="" arg
  for arg in "$@"; do
    if [[ "$arg" =~ ^[A-Za-z0-9_./:=@%+-]+$ ]]; then out+=" $arg"; else out+=" \"${arg//\"/\\\"}\""; fi
  done
  printf '%s' "${out# }"
}

# step <expected exit codes> <orchestrator args...>
# Runs one CLI command and fails the demo if the exit code is not one of the expected ones
# (0 completed, 10 paused for a human, 20 safe-stopped, 2 usage error).
step() {
  local expected="$1"; shift
  printf '\n%s$ orchestrator %s%s\n' "$BOLD" "$(quote_args "$@")" "$RESET"
  set +e
  java -jar "$JAR" "$@"
  local code=$?
  set -e
  case " $expected " in
    *" $code "*) printf '(exit %s)\n' "$code" ;;
    *) echo "Unexpected exit code $code (expected: $expected)"; exit 1 ;;
  esac
}

run_id() {
  echo "$1-$(date -u +%Y%m%d-%H%M%S)"
}
