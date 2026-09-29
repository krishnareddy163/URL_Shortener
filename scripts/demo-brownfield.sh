#!/usr/bin/env bash
# Brownfield: add link expiry to the committed baseline.
# Shows: real JavaParser impact analysis, a graph patch inserting db_migration, a migration approval,
# and a regression failure that is rolled back and fixed on retry.
# shellcheck source=scripts/lib.sh
source "$(dirname "$0")/lib.sh"
RUN="$(run_id brownfield)"

step 10 run scenarios/brownfield/workflow.yaml --run-id "$RUN"
step 0  status "$RUN"
step 0  pending "$RUN" --max-diff-lines 25
step 0  approve "$RUN" db_migration --by "$APPROVER" --comment "Additive nullable column; no backfill"
step 10 resume "$RUN"
step 0  pending "$RUN" --max-diff-lines 25
step 0  approve "$RUN" release --by "$APPROVER" --comment "Review GO, v1 suite and expiry tests green: release 1.1.0"
step 0  resume "$RUN"
step 0  status "$RUN"
step 0  report "$RUN"
step 0  lineage "$RUN" implement
echo
echo "Brownfield demo complete. Report: runs/$RUN/report.md"
