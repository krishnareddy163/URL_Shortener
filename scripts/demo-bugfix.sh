#!/usr/bin/env bash
# Bug fix: a real v1 SSRF bypass (http://localhost./admin is accepted), fixed on the committed baseline.
# Shows: impact analysis, a behavior-preserving refactor proven by the unchanged suite, a regression test proven
# to fail before the fix (reproduces-defect gate), an incomplete fix rolled back, and two human approvals.
# shellcheck source=scripts/lib.sh
source "$(dirname "$0")/lib.sh"
RUN="$(run_id bugfix)"

step 10 run scenarios/bugfix/workflow.yaml --run-id "$RUN"
step 0  pending "$RUN" --max-diff-lines 40
step 0  approve "$RUN" fix --by "$APPROVER" --comment "Root cause fixed in one place; reproduction test green"
step 10 resume "$RUN"
step 0  pending "$RUN" --max-diff-lines 25
step 0  approve "$RUN" release --by "$APPROVER" --comment "Review GO, full suite green: release 1.0.1"
step 0  resume "$RUN"
step 0  status "$RUN"
step 0  report "$RUN"
step 0  lineage "$RUN" fix
echo
echo "Bug-fix demo complete. Report: runs/$RUN/report.md"
