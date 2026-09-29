#!/usr/bin/env bash
# Interactive LIVE run: every agent is backed by a model, and YOU are the human reviewer at every checkpoint.
# Nothing is approved automatically. Usage: scripts/live-run.sh <workflow.yaml> [--by <your name>]
# Needs ANTHROPIC_API_KEY and ANTHROPIC_MODEL (optionally ANTHROPIC_MAX_TOKENS).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
JAR="orchestrator/target/orchestrator.jar"

WORKFLOW="${1:-}"
BY="${USER:-reviewer}"
if [[ "${2:-}" == "--by" && -n "${3:-}" ]]; then BY="$3"; fi
if [[ -z "$WORKFLOW" || ! -f "$WORKFLOW" ]]; then
  echo "usage: scripts/live-run.sh <workflow.yaml> [--by <your name>]" >&2; exit 2
fi
if [[ -z "${ANTHROPIC_API_KEY:-}" || -z "${ANTHROPIC_MODEL:-}" ]]; then
  echo "LIVE mode needs ANTHROPIC_API_KEY and ANTHROPIC_MODEL in the environment." >&2; exit 2
fi
if [[ ! -f "$JAR" ]]; then
  echo "Building the orchestrator (first run)..."
  mvn -q -B -pl orchestrator package -DskipTests
fi

orchestrator() { java -jar "$JAR" "$@"; }
ask() { local reply; read -r -p "$1" reply < /dev/tty; printf '%s' "${reply//$'\r'/}"; }

RUN="live-$(basename "$(dirname "$WORKFLOW")")-$(date -u +%Y%m%d-%H%M%S)"
echo "Run $RUN in LIVE mode; reviewer: $BY"
set +e
orchestrator run "$WORKFLOW" --run-id "$RUN" --mode LIVE
code=$?
while [[ $code -eq 10 ]]; do
  PENDING="$(orchestrator pending "$RUN")"
  printf '\n%s\n' "$PENDING"
  while read -r node; do
    [[ -z "$node" ]] && continue
    while true; do
      choice="$(ask "Review '$node': [a]pprove, [r]eject with feedback, [s]kip for now? ")"
      case "$choice" in
        a) comment="$(ask "Approval comment: ")"
           orchestrator approve "$RUN" "$node" --by "$BY" --comment "${comment:-approved after review}" && break ;;
        r) comment="$(ask "What must change (becomes the agent's feedback): ")"
           [[ -n "$comment" ]] && orchestrator reject "$RUN" "$node" --by "$BY" --comment "$comment" && break ;;
        s) break ;;
      esac
    done
  done <<< "$(sed -n 's/^=== APPROVAL: \([^ ]*\) ===$/\1/p' <<< "$PENDING")"
  while read -r question; do
    [[ -z "$question" ]] && continue
    answer="$(ask "Answer for $question (empty to skip): ")"
    [[ -n "$answer" ]] && orchestrator answer "$RUN" "$question" "$answer" --by "$BY"
  done <<< "$(sed -n 's/^=== QUESTION: \([^ ]*\) .*$/\1/p' <<< "$PENDING")"
  if [[ "$(ask "Resume the run? [y/n] ")" != "y" ]]; then
    echo "Paused. Continue later with: java -jar $JAR pending $RUN, then approve/answer and resume $RUN."
    exit 10
  fi
  orchestrator resume "$RUN"
  code=$?
done
set -e
orchestrator report "$RUN" > /dev/null
echo
echo "LIVE run $RUN finished with exit code $code. Report: runs/$RUN/report.md"
exit "$code"
