# How AI assistance was used to build this

This project was built with an AI coding agent (Claude Code, running Claude Opus 5.5) as the development team and a
human as the technical lead: setting direction, making decisions, reviewing, and approving. This page describes the
working method, where AI raised quality and speed, where it got things wrong and how that was caught, and where
humans stayed in control. Every claim links to evidence in the repository.

## At a glance

| | |
|---|---|
| Commits co-authored by the AI agent | 15 of the 16 commits before this page; the other is a Dependabot update. See the `Co-Authored-By` trailers in `git log` |
| Tests | 160 orchestrator and 135 shortener tests, all run in CI on every push |
| Shortener coverage raised by AI-written tests | from 89.7% line and 81.9% branch to 100% line and 100% branch (on all 27 functional classes) |
| Automation | GitHub Actions (lint, SAST, SCA, secrets, container and DAST scans, all demos, tests inside the image, a real-model smoke test), Dependabot, JaCoCo floors, and scripts that export every AI SDLC artifact |
| Decision records | 21 ADRs in [decisions.md](decisions.md), most of them written while working with the agent |

## Working method

1. **Specification first, written for the agent.** [requirements.md](../requirements.md) is a build specification addressed to an AI coding agent: work phase by phase, run each phase's verification before moving on, add nothing the spec does not ask for, no placeholders or stubbed tests, and no documentation claim without code or a test behind it. Its revision note records every deliberate deviation.
2. **Small, verified steps.** Each change ran through the strict build (warnings as errors, SpotBugs and FindSecBugs, PMD and CPD) and the full test suite, including the scenario tests that build the generated code for real, before it was committed.
3. **AI as reviewer, not only author.** AI code review (`/code-review` in Claude Code) was run on commits, and its findings were fixed in follow-up commits.
4. **Real-model testing.** The agents were run against a real model (LIVE mode), not only against prepared fixtures, and what that exposed was fixed at the root.
5. **Humans own decisions.** Direction, trade-offs and every approval of agent work were the human's.

## Where AI assistance raised quality

| What happened | Evidence |
|---|---|
| An AI review of the guardrails found that a model-written `pom.xml` could add a build plugin that runs code during the `compile` gate, before any human approval. The dependency gate was extended to plugins, parent POMs, repositories and extensions. | [ADR-18](decisions.md#adr-18-build-plugins-are-allowlisted-like-dependencies), `PolicyGatesTest` |
| The first real-model run failed: the `refactor` step fixed the bug it was supposed to preserve, because agents were not told which step they were on. The gates stopped the run safely; the fix gives every step a written task. Later real-model runs behaved correctly, in CI as well. | [ADR-19](decisions.md#adr-19-node-tasks-in-the-workflow-file) |
| AI code review of a CI change found two real defects: a newer push could cancel a running real-model test and waste its API credit, and a missing secret or an API outage turned `main` red. Both were fixed. | commit `5d5b8c6` |
| New CI stages proposed by the agent found real problems: the Docker image ran as root while running generated code, a test failed only inside the image, and actions were not pinned. | commit `002b3b6` |
| Measuring role-level evidence found gaps: no user stories, no design diagrams, no proof that reviews covered every file, no coverage report. Each became a gate, and the shortener gained an audit trail. | [ADR-20](decisions.md#adr-20-every-role-must-prove-its-output) |
| Model token usage, retries and truncation became visible per attempt, so the cost of each run is known. | [ADR-17](decisions.md#adr-17-model-usage-in-the-event-log-no-external-llm-tooling) |

## Where AI got it wrong, and how that was caught

Treating AI output as a proposal to verify, never as a result, is the core of the method. These were all caught by automation or review before they could matter:

| Mistake | Caught by |
|---|---|
| The agent's Dependabot configuration let a pull request move the base image to Java 26, which the analyzers cannot read yet. | CI failed the pull request; the configuration was tightened and the pull request closed |
| A comment in the SpotBugs exclusion file contained `--`, which is not allowed in XML, so every reviewed exclusion was silently ignored. | The build reported 36 findings instead of 4, which exposed the cause |
| A new gate would have crashed on a finding with no status (`Set.contains(null)` throws in Java). | Its own unit test |
| A mock-based test compiled with an unchecked-generics warning. | The strict compiler (warnings are errors) |
| Documentation kept a claim that had stopped being true ("real model output has not been evaluated") and old coverage figures. | Review against the rule that every doc claim needs evidence; both corrected |
| A sample review finding described code that an earlier clean-up had removed. | Checking the delivery against the role criteria |

## Where humans stayed in control

- **Approvals of agent work are human.** The engine never approves anything itself. When the AI assistant was asked to approve a real-model run's fix on the lead's behalf, its own guardrails refused it, and the approval stayed with the human, the same principle this system enforces on its agents.
- **Secrets stayed with the human.** API keys were passed through environment variables and repository secrets, never committed, and rotated when one was exposed. The assistant was likewise blocked from writing to the repository's secret store.
- **Destructive or outward actions were confirmed first**: pushing to the repository, merging a dependency pull request, closing pull requests, and deleting a failed CI run.

## Reproducing the AI SDLC artifacts

`make demo-all` runs the four scenarios; `make artifacts` exports the requirements, code reviews, functional coverage and commit history from those runs into [ai-sdlc/](ai-sdlc/README.md); `make coverage` writes the coverage inventory. The artifacts are generated from what the agents produced and the gates verified, not written by hand.
