#!/usr/bin/env bash
# Ambiguous: "Make links more secure and add some analytics".
# Shows: a blocking clarification, variant execution, then an answer change that invalidates the whole
# downstream chain (files reverted, approval revoked) and re-plans it with the new interpretation.
# shellcheck source=scripts/lib.sh
source "$(dirname "$0")/lib.sh"
RUN="$(run_id ambiguous)"

step 10 run scenarios/ambiguous/workflow.yaml --run-id "$RUN"
step 0  pending "$RUN"
step 0  answer "$RUN" q-secure "https-only" --by "$APPROVER"
step 10 resume "$RUN"
step 0  approve "$RUN" release --by "$APPROVER" --comment "HTTPS-only policy approved"
step 0  resume "$RUN"
echo
echo "--- The product owner changes their mind: 'secure' should mean a domain blocklist ---"
step 0  answer "$RUN" q-secure "domain-blocklist" --by "$APPROVER"
step 0  status "$RUN"
step 10 resume "$RUN"
step 0  approve "$RUN" release --by "$APPROVER" --comment "Domain blocklist approved"
step 0  resume "$RUN"
step 0  report "$RUN"
echo
echo "Ambiguous demo complete. Report: runs/$RUN/report.md"
