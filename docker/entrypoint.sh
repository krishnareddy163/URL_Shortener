#!/usr/bin/env bash
# Container entry point: named tasks run the demo scripts or test suites; anything else is passed to the CLI.
set -euo pipefail
cd /app

usage() {
  cat <<'TEXT'
Usage: docker run --rm -it -v "$PWD/runs:/app/runs" agentic-sdlc <task | cli-command ...>

Tasks:
  demo-greenfield   build the URL shortener from scratch (parallel waves, retry, 3 approvals)
  demo-brownfield   add link expiry to the existing service (impact analysis, graph patch)
  demo-ambiguous    "make links more secure" (clarification, then an answer change that re-plans)
  demo-bugfix       fix a real v1 SSRF bypass test-first (refactor, reproduce, fix, release 1.0.1)
  demo-all          all four demos
  live <workflow>   interactive LIVE run; needs -e ANTHROPIC_API_KEY -e ANTHROPIC_MODEL and -it
  test              shortener tests, then orchestrator tests
  help              this text

CLI commands (see README): run, status, pending, approve, reject, answer, resume, report, lineage
  e.g. docker run --rm -it -v "$PWD/runs:/app/runs" agentic-sdlc status <runId>
TEXT
}

task="${1:-help}"
case "$task" in
  demo-greenfield | demo-brownfield | demo-ambiguous | demo-bugfix | demo-all)
    exec "scripts/$task.sh" ;;
  live)
    shift
    exec scripts/live-run.sh "${@:-scenarios/greenfield/workflow.yaml}" ;;
  test)
    echo "==> [1/2] Shortener tests (88)"
    mvn -q -B -f shortener-service/pom.xml verify -Dmaven.test.redirectTestOutputToFile=true
    echo "==> [2/2] Orchestrator tests (160), including real-build scenarios (3-4 min)"
    mvn -q -B verify
    echo "==> All tests passed" ;;
  help | -h | --help)
    usage ;;
  *)
    exec java -jar orchestrator/target/orchestrator.jar "$@" ;;
esac
