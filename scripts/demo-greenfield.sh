#!/usr/bin/env bash
# Greenfield: build the URL shortener from an empty workspace.
# Shows: parallel waves and a join, three human approvals, a real failing unit test rolled back and retried.
# shellcheck source=scripts/lib.sh
source "$(dirname "$0")/lib.sh"
RUN="$(run_id greenfield)"

step 10 run scenarios/greenfield/workflow.yaml --run-id "$RUN"
step 0  pending "$RUN" --max-diff-lines 25
step 0  approve "$RUN" design --by "$APPROVER" --comment "Contract, V1 schema and risk list reviewed"
step 10 resume "$RUN"
step 0  pending "$RUN" --max-diff-lines 25
step 0  approve "$RUN" implement --by "$APPROVER" --comment "Dependencies are allowlisted; implementation matches the design"
step 10 resume "$RUN"
step 0  pending "$RUN" --max-diff-lines 25
step 0  approve "$RUN" release --by "$APPROVER" --comment "Review GO, full suite green: release 1.0.0"
step 0  resume "$RUN"
step 0  status "$RUN"
step 0  report "$RUN"
step 0  lineage "$RUN" release
echo
echo "Greenfield demo complete. Report: runs/$RUN/report.md"
