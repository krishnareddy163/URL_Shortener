MVN ?= mvn
BASELINE := shortener-service/pom.xml
# The shortener's tests print Spring startup logs; keep them in target/surefire-reports/*-output.txt instead of the console.
SANDBOX_IMAGE := maven:3.9-eclipse-temurin-25
QUIET_TESTS := -Dmaven.test.redirectTestOutputToFile=true

.PHONY: build test coverage artifacts lint scan live-smoke demo-greenfield demo-brownfield demo-ambiguous demo-bugfix demo-all live bless-baseline clean

## build: verify the baseline (also warms the Maven cache the gates use), then package the orchestrator jar
build:
	@echo "==> [1/2] Verifying the shortener baseline: tests; also warms the Maven cache the gates use (about 30 s)"
	@$(MVN) -q -B -f $(BASELINE) verify $(QUIET_TESTS)
	@echo "==> [2/2] Building orchestrator.jar and running its tests, including real-build scenarios (3-4 min)"
	@$(MVN) -q -B package
	@if command -v docker > /dev/null 2>&1 && docker info > /dev/null 2>&1; then \
		echo "==> Docker found: preparing the gate sandbox image $(SANDBOX_IMAGE)"; \
		docker image inspect $(SANDBOX_IMAGE) > /dev/null 2>&1 || docker pull -q $(SANDBOX_IMAGE) > /dev/null \
			|| echo "    (pull failed: build gates will run on the host)"; \
	else \
		echo "==> Docker not found: build gates will run on the host (see build.sandbox in policies/policies.yaml)"; \
	fi
	@echo "==> Build complete: orchestrator/target/orchestrator.jar"

## test: shortener suite, then the orchestrator suite (unit tests + scenario integration tests with real gates)
test:
	@echo "==> [1/2] Shortener tests"
	@$(MVN) -q -B -f $(BASELINE) verify $(QUIET_TESTS)
	@echo "==> [2/2] Orchestrator tests (including real-build scenarios)"
	@$(MVN) -q -B verify
	@echo "==> All tests passed"

## coverage: write docs/coverage.md (line and branch coverage of both projects; every class below 100%) after make test
coverage:
	python3 scripts/coverage-report.py > docs/coverage.md

## artifacts: export the AI SDLC artifacts of the latest demo runs to docs/ai-sdlc/ (after make demo-all)
artifacts:
	scripts/export-sdlc-artifacts.py $$(for s in greenfield brownfield ambiguous bugfix; do ls -d runs/$$s-* | tail -1; done)

## lint: actionlint, zizmor, shellcheck, gitleaks (full history), Trivy Dockerfile config (Docker only, no build)
lint:
	scripts/lint.sh

## scan: strict builds + SAST + PMD/CPD, SonarQube (if SONAR_HOST_URL/SONAR_TOKEN set), Trivy SCA + secrets, ZAP DAST
scan:
	scripts/quality-scan.sh

## live-smoke: real-model bug-fix run that must reach the API (needs ANTHROPIC_API_KEY and ANTHROPIC_MODEL)
live-smoke:
	scripts/live-smoke.sh

demo-greenfield:
	scripts/demo-greenfield.sh

demo-brownfield:
	scripts/demo-brownfield.sh

demo-ambiguous:
	scripts/demo-ambiguous.sh

demo-bugfix:
	scripts/demo-bugfix.sh

demo-all:
	scripts/demo-all.sh

## live: interactive LIVE run of WORKFLOW (default: greenfield); you review every checkpoint
WORKFLOW ?= scenarios/greenfield/workflow.yaml
live:
	scripts/live-run.sh $(WORKFLOW)

## bless-baseline: copy the latest completed greenfield workspace into shortener-service
bless-baseline:
	scripts/bless-baseline.sh

clean:
	$(MVN) -q -B clean
	$(MVN) -q -B -f $(BASELINE) clean
	find runs -mindepth 1 -maxdepth 1 ! -name .gitkeep -exec rm -rf {} +
