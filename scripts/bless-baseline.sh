#!/usr/bin/env bash
# Copies the final workspace of a COMPLETED greenfield run into shortener-service, making it the
# committed v1 that the brownfield and ambiguous scenarios start from.
# Usage: scripts/bless-baseline.sh [runId]   (default: the most recent greenfield-* run)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
RUN="${1:-$(find runs -mindepth 1 -maxdepth 1 -type d -name 'greenfield-*' 2>/dev/null | sort | tail -1 | xargs -n1 basename 2>/dev/null || true)}"
if [[ -z "$RUN" || ! -d "runs/$RUN/workspace" ]]; then
  echo "No greenfield run found. Run 'make demo-greenfield' first or pass a run id." >&2
  exit 2
fi
if ! java -jar orchestrator/target/orchestrator.jar status "$RUN" | head -1 | grep -q "COMPLETED"; then
  echo "Run $RUN is not COMPLETED; refusing to bless an unfinished workspace." >&2
  exit 2
fi
echo "Blessing runs/$RUN/workspace -> shortener-service"
diff -rq -x target -x .DS_Store "runs/$RUN/workspace" shortener-service || true
rsync -a --delete --exclude target --exclude .DS_Store "runs/$RUN/workspace/" shortener-service/
echo "Done. Verify with: mvn -q -f shortener-service/pom.xml verify"
