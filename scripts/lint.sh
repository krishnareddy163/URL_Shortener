#!/usr/bin/env bash
# Static checks that need no build. Any finding fails the script. Needs Docker; every tool runs from a pinned image.
#   1. actionlint: GitHub Actions workflow syntax and expressions.
#   2. zizmor: GitHub Actions security (unpinned actions, credential persistence, template injection).
#   3. shellcheck: every shell script.
#   4. gitleaks: secrets in the whole git history (Trivy in `make scan` covers only the working tree).
#   5. Trivy config: Dockerfile misconfigurations (HIGH and CRITICAL).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

ACTIONLINT_IMAGE="rhysd/actionlint:1.7.12"
ZIZMOR_IMAGE="ghcr.io/zizmorcore/zizmor:1.30.1"
SHELLCHECK_IMAGE="koalaman/shellcheck:v0.11.0"
GITLEAKS_IMAGE="zricethezav/gitleaks:v8.30.1"
TRIVY_IMAGE="${TRIVY_IMAGE:-aquasec/trivy:0.74.0}"

step() { printf '\n== %s ==\n' "$*"; }

step "actionlint"
docker run --rm -v "$ROOT":/repo:ro -w /repo "$ACTIONLINT_IMAGE" -no-color

step "zizmor"
docker run --rm -v "$ROOT":/repo:ro -w /repo "$ZIZMOR_IMAGE" --no-progress .github/workflows

step "shellcheck"
docker run --rm -v "$ROOT":/mnt:ro -w /mnt "$SHELLCHECK_IMAGE" -x scripts/*.sh docker/entrypoint.sh

step "gitleaks (full history)"
docker run --rm -v "$ROOT":/repo:ro "$GITLEAKS_IMAGE" git /repo --no-banner --no-color --redact --gitleaks-ignore-path /repo/.gitleaksignore

step "Trivy config: Dockerfile"
docker run --rm -v "$ROOT":/repo:ro -v trivy-cache:/root/.cache "$TRIVY_IMAGE" \
  config --quiet --exit-code 1 --severity HIGH,CRITICAL /repo/Dockerfile

step "All static checks passed"
