#!/usr/bin/env python3
"""Exports the AI SDLC artifacts of completed runs into docs/ai-sdlc/ as readable Markdown.

Usage: scripts/export-sdlc-artifacts.py runs/<greenfield-run> [runs/<other-run> ...]

For each run it reads the event log and the artifact store (events.db) and the run's workspace git history, and
writes: the requirements agent's user stories and acceptance criteria, the review agent's code reviews (files
reviewed, findings, resolutions), the QA agent's functional and measured coverage, and the development agent's
commit history. Nothing is hand-written: every line comes from what the agents produced and the gates verified.
Standard library only.
"""
import json
import sqlite3
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/ai-sdlc"


def load(run_dir):
    db = sqlite3.connect(run_dir / "events.db")
    events = [dict(seq=seq, node=node, type=kind, actor=actor, output=output, details=json.loads(details))
              for seq, node, kind, actor, output, details in db.execute(
                  "SELECT seq, node_id, type, actor, output_hash, details_json FROM events ORDER BY seq")]
    current = {}
    for event in events:
        if event["type"] == "NODE_DONE":
            current[event["node"]] = event["output"]
    artifacts = {}
    for node, digest in current.items():
        row = db.execute("SELECT rationale, content_json FROM artifacts WHERE hash = ?", (digest,)).fetchone()
        if row:
            content = json.loads(row[1])
            artifacts[node] = dict(rationale=row[0], files=content.get("files", {}), data=content.get("data", {}), hash=digest)
    started = next(event for event in events if event["type"] == "RUN_STARTED")
    return dict(id=run_dir.name, dir=run_dir, events=events, artifacts=artifacts,
                workflow=started["details"]["graph"]["name"], requirement=started["details"]["graph"]["requirement"])


def cell(text):
    return " ".join(str(text).split()).replace("|", "\\|")


def gate_detail(run, node, gate):
    for event in run["events"]:
        if event["type"] == "GATE_PASSED" and event["node"] == node and event["details"].get("gate") == gate:
            return event["seq"], event["details"].get("detail")
    return None, None


def header(title, runs, what):
    lines = [f"# {title}", "", f"Exported by `scripts/export-sdlc-artifacts.py` from {what} of these runs; do not edit by hand.", ""]
    lines += [f"- `{run['id']}` ({run['workflow']}): [report](../sample-runs/{run['workflow']}/report.md)" for run in runs]
    return lines + [""]


def requirements(runs):
    lines = header("Requirements agent: user stories and acceptance criteria", runs, "the requirements artifacts")
    for run in runs:
        artifact = run["artifacts"].get("requirements")
        if not artifact:
            continue
        data = artifact["data"]
        seq, _ = gate_detail(run, "requirements", "requirements-complete")
        lines += [f"## {run['workflow']}", "", f"> {run['requirement']}", "",
                  f"Verified by `requirements-complete` (seq {seq}). Artifact `{artifact['hash'][:12]}`.", "",
                  "**Problem statement.** " + data.get("problemStatement", ""), "", "### User stories", ""]
        lines += [f"{index}. {story}" for index, story in enumerate(data.get("userStories", []), 1)]
        lines += ["", "### Acceptance criteria", ""]
        lines += [f"{index}. {criterion}" for index, criterion in enumerate(data.get("acceptanceCriteria", []), 1)]
        lines += ["", "### Open questions", "", "| Id | Question | Blocking | Answer or assumption |", "|---|---|---|---|"]
        answers = data.get("answers", {})
        for question in data.get("ambiguities", []):
            outcome = answers.get(question["id"]) or question.get("assumptionIfUnanswered", "")
            lines.append(f"| `{question['id']}` | {cell(question['question'])} | {question['blocking']} | {cell(outcome)} |")
        lines += ["", "### Assumptions", ""] + [f"- {item}" for item in data.get("assumptions", [])] + [""]
    return lines


def reviews(runs):
    lines = header("Code review agent: every submitted file reviewed, every finding resolved", runs, "the review artifacts")
    for run in runs:
        for node, artifact in run["artifacts"].items():
            data = artifact["data"]
            if "recommendation" not in data:
                continue
            seq, _ = gate_detail(run, node, "review-complete")
            reviewed = data.get("reviewedFiles", [])
            lines += [f"## {run['workflow']} / `{node}`: {data['recommendation']}", "",
                      f"`review-complete` passed at seq {seq}: all {len(reviewed)} files submitted by the upstream steps were "
                      "reviewed, and every finding has a status and a resolution.", "",
                      "| Severity | File | Finding | Status | Resolution |", "|---|---|---|---|---|"]
            for finding in data.get("findings", []):
                lines.append(f"| {finding['severity']} | `{finding['file']}` | {cell(finding['message'])} | "
                             f"**{finding['status']}** | {cell(finding['resolution'])} |")
            lines += ["", "<details><summary>Files reviewed</summary>", ""] + [f"- `{path}`" for path in reviewed]
            lines += ["", "</details>", ""]
    return lines


def functional(runs):
    lines = header("QA agent: functional and measured test coverage", runs, "the QA report artifacts and gate results")
    for run in runs:
        artifact = run["artifacts"].get("qa_report")
        if not artifact:
            continue
        seq, detail = gate_detail(run, "qa_report", "functional-coverage")
        coverage_seq, coverage = gate_detail(run, "qa_report", "test-coverage")
        lines += [f"## {run['workflow']}", "", f"- **Functional coverage** (`functional-coverage`, seq {seq}): {detail}.",
                  f"- **Measured unit and integration coverage** (`test-coverage`, JaCoCo in the build sandbox, seq "
                  f"{coverage_seq}): {coverage}.", "- The full per-class inventory for both projects is in "
                  "[coverage.md](../coverage.md).", "", "| # | Acceptance criterion | Tests that prove it |", "|---:|---|---|"]
        for index, row in enumerate(artifact["data"].get("functionalCoverage", []), 1):
            tests = "<br>".join(f"`{test}`" for test in row["tests"])
            lines.append(f"| {index} | {cell(row['criterion'])} | {tests} |")
        lines.append("")
    return lines


def git_history(runs):
    lines = header("Development agent: commit history", runs, "the workspace git history")
    for run in runs:
        workspace = run["dir"] / "workspace"
        log = subprocess.run(["git", "-C", str(workspace), "log", "--reverse", "--format=%h%x09%an%x09%s%x09%(trailers:key=Approved-by,valueonly,separator=%x2C )"],
                             capture_output=True, text=True, check=False)
        if log.returncode != 0 or not log.stdout.strip():
            continue
        lines += [f"## {run['workflow']}", "", "| Commit | Author (agent) | Subject | Approved by |", "|---|---|---|---|"]
        for entry in log.stdout.strip().splitlines():
            commit, author, subject, approved = (entry.split("\t") + [""] * 4)[:4]
            lines.append(f"| `{commit}` | {author} | {cell(subject)} | {approved.strip() or '-'} |")
        lines.append("")
    return lines


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    runs = [load(Path(argument)) for argument in sys.argv[1:]]
    OUT.mkdir(parents=True, exist_ok=True)
    outputs = {"user-stories.md": requirements(runs), "code-review.md": reviews(runs),
               "functional-coverage.md": functional(runs), "git-history.md": git_history(runs)}
    for name, lines in outputs.items():
        (OUT / name).write_text("\n".join(lines).rstrip() + "\n")
        print(f"wrote docs/ai-sdlc/{name}")


if __name__ == "__main__":
    main()
