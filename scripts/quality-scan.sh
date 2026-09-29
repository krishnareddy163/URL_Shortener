#!/usr/bin/env bash
# Quality and security scan of both projects. Any finding fails the script.
#   1. Strict builds: javac -Xlint:all -Werror, SpotBugs + FindSecBugs (SAST), PMD + CPD, all tests, JaCoCo coverage.
#   2. SonarQube analysis (only when SONAR_HOST_URL and SONAR_TOKEN are set), failing on the quality gate.
#   3. SCA: Trivy on the CycloneDX SBOMs (orchestrator/target/bom.json, shortener META-INF/sbom).
#   4. Secret scan: Trivy over the repository.
#   5. DAST: OWASP ZAP API scan of the running shortener, driven by its openapi.yaml.
# Needs Docker for steps 3-5. Reports are written to target/quality/.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
OUT="$ROOT/target/quality"
SHORTENER="shortener-service"
PORT="${DAST_PORT:-18080}"
TRIVY_IMAGE="${TRIVY_IMAGE:-aquasec/trivy:0.74.0}"
ZAP_IMAGE="${ZAP_IMAGE:-ghcr.io/zaproxy/zaproxy:2.17.0}"

step() { printf '\n== %s ==\n' "$*"; }

step "Strict build, SAST, PMD/CPD, tests, coverage"
mvn -q -B -f "$SHORTENER/pom.xml" clean verify
mvn -q -B clean verify
mkdir -p "$OUT"

if [[ -n "${SONAR_HOST_URL:-}" && -n "${SONAR_TOKEN:-}" ]]; then
  step "SonarQube (quality gate must pass)"
  SONAR="org.sonarsource.scanner.maven:sonar-maven-plugin:5.8.0.7211:sonar"
  mvn -q -B "$SONAR" -Dsonar.projectKey=agentic-sdlc -Dsonar.qualitygate.wait=true
  mvn -q -B -f "$SHORTENER/pom.xml" "$SONAR" -Dsonar.projectKey=shortener-service -Dsonar.qualitygate.wait=true
else
  step "SonarQube skipped (set SONAR_HOST_URL and SONAR_TOKEN to enable)"
fi

step "SCA: Trivy on SBOMs"
cp orchestrator/target/bom.json "$OUT/orchestrator.cdx.json"
cp "$SHORTENER/target/classes/META-INF/sbom/application.cdx.json" "$OUT/shortener.cdx.json"
for sbom in orchestrator shortener; do
  docker run --rm -v "$OUT":/q -v trivy-cache:/root/.cache "$TRIVY_IMAGE" \
    sbom --quiet --scanners vuln --exit-code 1 "/q/$sbom.cdx.json"
done

step "Secret scan: Trivy"
docker run --rm -v "$ROOT":/repo:ro -v trivy-cache:/root/.cache "$TRIVY_IMAGE" \
  fs --quiet --scanners secret --exit-code 1 --skip-files "**/pom.xml" \
  --skip-dirs /repo/runs --skip-dirs /repo/target --skip-dirs /repo/orchestrator/target \
  --skip-dirs "/repo/$SHORTENER/target" /repo

step "DAST: OWASP ZAP API scan"
java -jar "$SHORTENER/target/shortener-service-1.0.0.jar" --server.port="$PORT" > "$OUT/shortener.log" 2>&1 &
APP_PID=$!
trap 'kill "$APP_PID" 2>/dev/null || true' EXIT
for _ in $(seq 1 60); do
  curl -s -o /dev/null "http://localhost:$PORT/api/v1/links/probe/stats" && break
  sleep 1
done
cp "$SHORTENER/openapi.yaml" "$OUT/openapi.yaml"
chmod -R a+rwX "$OUT"
docker run --rm --add-host=host.docker.internal:host-gateway -v "$OUT":/zap/wrk:rw "$ZAP_IMAGE" zap-api-scan.py \
  -t /zap/wrk/openapi.yaml -f openapi -O "http://host.docker.internal:$PORT" \
  -r zap-report.html -J zap-report.json

step "All quality and security checks passed. Reports: target/quality/"
