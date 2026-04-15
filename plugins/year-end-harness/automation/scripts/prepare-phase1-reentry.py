#!/usr/bin/env python3
"""Prepare Phase 1 law-pack re-entry artifacts from a slice rule proposal.

This script is intentionally conservative:
- It creates READY_FOR_REVIEW artifacts in the existing run directory.
- It does not write into plugins/year-end-harness/law-packs.
- It does not set any rule pack to PUBLISHED.

Inputs in --run-dir:
  source-manifest.json
  normalized-rule-pack-proposal.json
  expected-rule-codes.txt

Outputs in --run-dir:
  agent-a-tax-pack.md
  normalized-rule-pack.json
  diff-from-previous.md
  phase1-reentry-ready-for-review.md
"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

try:
    sys.stdout.reconfigure(encoding="utf-8")
except (AttributeError, OSError):
    pass

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
VALIDATOR = REPO / "plugins" / "year-end-harness" / "scripts" / "validate-artifacts.py"


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def write_text(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def rel(path: Path) -> str:
    try:
        return path.resolve().relative_to(REPO.resolve()).as_posix()
    except ValueError:
        return path.as_posix()


def result_block(status: str, summary: str, artifact: Path, next_step: str) -> str:
    return (
        "=== HARNESS RESULT ===\n"
        f"STATUS   : {status}\n"
        f"SUMMARY  : {summary}\n"
        f"ARTIFACTS: {rel(artifact)}\n"
        f"NEXT     : {next_step}\n"
        "======================"
    )


def read_expected_codes(run_dir: Path) -> list[str]:
    path = run_dir / "expected-rule-codes.txt"
    if not path.exists():
        return []
    codes: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            codes.append(stripped)
    return codes


def summarize_parameters(parameters: dict[str, Any]) -> str:
    pairs = []
    for key, value in parameters.items():
        if isinstance(value, list):
            rendered = ", ".join(str(v) for v in value)
            pairs.append(f"{key}=[{rendered}]")
        else:
            pairs.append(f"{key}={value}")
    return "; ".join(pairs) if pairs else "no parameters"


def make_source_rows(source_manifest: dict[str, Any]) -> str:
    rows = []
    for source in source_manifest.get("sources", []):
        rows.append(
            "| {sourceName} | {sourceUrl} | {publishedAt} | {effectiveAt} | {checkedAt} | {notes} |".format(
                sourceName=source.get("sourceName", source.get("sourceId", "")),
                sourceUrl=source.get("sourceUrl", ""),
                publishedAt=source.get("publishedAt", ""),
                effectiveAt=source.get("effectiveAt", ""),
                checkedAt=source.get("checkedAt", ""),
                notes=str(source.get("notes", "")).replace("\n", " "),
            )
        )
    return "\n".join(rows) if rows else "| No source | N/A | N/A | N/A | N/A | No source manifest rows found. |"


def make_confirmed_rule_rows(rules: list[dict[str, Any]], category_filter: set[str] | None = None) -> str:
    filtered = [
        rule for rule in rules
        if rule.get("confidence") == "confirmed"
        and (category_filter is None or rule.get("ruleCategory") in category_filter)
    ]
    if not filtered:
        return "- None."
    lines = []
    for rule in filtered:
        lines.append(
            "- `{ruleCode}` ({deductionType}/{subType}, {category}): {params}; sources={sources}".format(
                ruleCode=rule.get("ruleCode", ""),
                deductionType=rule.get("deductionType", ""),
                subType=rule.get("subType"),
                category=rule.get("ruleCategory", ""),
                params=summarize_parameters(rule.get("parameters", {})),
                sources=", ".join(rule.get("sourceRefs", [])),
            )
        )
    return "\n".join(lines)


def make_agent_a_tax_pack(run_dir: Path, source_manifest: dict[str, Any], rule_pack: dict[str, Any]) -> str:
    rules = rule_pack.get("rules", [])
    inferred_rules = [rule for rule in rules if rule.get("confidence") == "inferred"]
    inferred_text = (
        "\n".join(f"- `{rule.get('ruleCode')}` requires human confirmation." for rule in inferred_rules)
        if inferred_rules
        else "- None."
    )
    open_questions = (
        "- Confirm whether this READY_FOR_REVIEW pack should be promoted into "
        "`plugins/year-end-harness/law-packs/{taxYear}/{ruleVersion}/` and then published by a human reviewer."
    ).format(
        taxYear=rule_pack.get("taxYear"),
        ruleVersion=rule_pack.get("ruleVersion"),
    )

    target = run_dir / "agent-a-tax-pack.md"
    return f"""# Agent A Tax Pack

## Context

- As-of date: {source_manifest.get("asOfDate")}
- Run id: {source_manifest.get("runId")}
- Run directory: {source_manifest.get("runDirectory")}
- Target law context: {rule_pack.get("taxYear")} income / {rule_pack.get("filingYear")} filing
- Target tax year: {rule_pack.get("taxYear")}
- Target filing year: {rule_pack.get("filingYear")}
- Target rule version: {rule_pack.get("ruleVersion")}
- Requested scope: automated Phase 1 re-entry for missing personal deduction rule codes

## Inputs

- AGENTS.md
- docs/notes/command_list.md
- plugins/year-end-harness/automation/inner-workflow.md
- {rel(run_dir / "source-manifest.json")}
- {rel(run_dir / "normalized-rule-pack-proposal.json")}
- {rel(run_dir / "expected-rule-codes.txt")}

## Source Register

| Source | URL | Published At | Effective At | Checked At | Notes |
|---|---|---|---|---|---|
{make_source_rows(source_manifest)}

## Confirmed Rules

### 세율표

- None. This re-entry scope only covers personal deduction rules.

### 공제 한도표

{make_confirmed_rule_rows(rules, {"LIMIT", "DEDUCTION", "FORMULA"})}

### 인적공제 판단표

{make_confirmed_rule_rows(rules, {"ELIGIBILITY"})}

### 항목별 가족 합산 가능 여부 표

- Basic personal deduction targets are counted per eligible person.
- Senior and disabled additional deductions require the person to be a basic deduction target.
- Single-parent additional deduction suppresses woman additional deduction when both would otherwise apply.

### 증빙 요구사항 표

- Eligibility and additional-deduction flags require evidence in the dependent/filer input model before runtime calculation consumes these rules.
- Runtime implementation must preserve ruleCode traceability for each counted target and amount.

## Normalization Notes

- Rule version decision: {rule_pack.get("ruleVersion")} was selected from the slice proposal generated in this run.
- Previous rule set: first version because `plugins/year-end-harness/law-packs/` has no published normalized rule pack.
- Diff summary: added {len(rules)} personal deduction rule codes.
- Publish readiness: `READY_FOR_REVIEW`

## Inferred Rules

{inferred_text}

## Open Questions

{open_questions}

## Files

- `{rel(run_dir / "source-manifest.json")}`
- `{rel(run_dir / "normalized-rule-pack.json")}`
- `{rel(run_dir / "diff-from-previous.md")}`
- `{rel(run_dir / "phase1-reentry-ready-for-review.md")}`

## Validation

```text
{result_block(
    "success",
    "Automated Phase 1 re-entry tax pack prepared for human review.",
    target,
    "Human reviewer may promote this READY_FOR_REVIEW pack; do not auto-PUBLISHED.",
)}
```
"""


def make_diff(run_dir: Path, rule_pack: dict[str, Any]) -> str:
    rules = rule_pack.get("rules", [])
    target = run_dir / "diff-from-previous.md"
    added_rows = "\n".join(
        "| {ruleCode} | {deductionType} | {params} | {sources} |".format(
            ruleCode=rule.get("ruleCode", ""),
            deductionType=rule.get("deductionType", ""),
            params=summarize_parameters(rule.get("parameters", {})),
            sources=", ".join(rule.get("sourceRefs", [])),
        )
        for rule in rules
    )
    if not added_rows:
        added_rows = "| None | None | No rules were added. | None |"

    return f"""# Rule Diff

## Context

- As-of date: {datetime.now(timezone.utc).strftime("%Y-%m-%d")}
- Run id: {run_dir.name}
- Run directory: {rel(run_dir)}
- Target law context: {rule_pack.get("taxYear")} income / {rule_pack.get("filingYear")} filing
- Current rule version: {rule_pack.get("ruleVersion")}
- Previous rule version: first version

## Inputs

- `{rel(run_dir / "source-manifest.json")}`
- `{rel(run_dir / "normalized-rule-pack.json")}`
- first version because no published normalized rule pack exists under `plugins/year-end-harness/law-packs/`

## Version Pair

- Current rule set id: {rule_pack.get("ruleSetId")}
- Previous rule set id: first version
- Change summary: first version of personal deduction rules for the automated Phase 1 re-entry.

## Added Rules

| Rule Code | Deduction Type | Summary | Source Refs |
|---|---|---|---|
{added_rows}

## Changed Rules

| Rule Code | Change Type | Impact | Source Refs |
|---|---|---|---|
| None | first version | No prior published rule to compare. | None |

## Removed Rules

| Rule Code | Reason | Impact |
|---|---|---|
| None | first version | No prior published rule to remove. |

## Impact Summary

- This is the first version of the personal deduction normalized rule pack for this repository.
- Runtime implementation remains blocked until a human reviewer promotes or publishes the READY_FOR_REVIEW pack.

## Files

- `{rel(target)}`
- `{rel(run_dir / "normalized-rule-pack.json")}`

## Validation

```text
{result_block(
    "success",
    "Automated first version rule diff prepared for human review.",
    target,
    "Review the READY_FOR_REVIEW normalized rule pack before runtime implementation resumes.",
)}
```
"""


def validate(kind: str, path: Path) -> tuple[int, str]:
    command = [sys.executable, str(VALIDATOR), "--kind", kind, str(path)]
    completed = subprocess.run(command, cwd=REPO, text=True, capture_output=True)
    return completed.returncode, completed.stdout + completed.stderr


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", required=True)
    parser.add_argument("--validate", action="store_true")
    args = parser.parse_args()

    run_dir = Path(args.run_dir)
    if not run_dir.is_absolute():
        run_dir = REPO / run_dir
    if not run_dir.exists():
        print(f"ERROR: run-dir not found: {run_dir}", file=sys.stderr)
        return 2

    source_manifest_path = run_dir / "source-manifest.json"
    proposal_path = run_dir / "normalized-rule-pack-proposal.json"
    if not source_manifest_path.exists():
        print(f"ERROR: missing {source_manifest_path}", file=sys.stderr)
        return 2
    if not proposal_path.exists():
        print(f"ERROR: missing {proposal_path}", file=sys.stderr)
        return 2

    source_manifest = read_json(source_manifest_path)
    rule_pack = read_json(proposal_path)
    rule_pack["status"] = "READY_FOR_REVIEW"
    generated_from = rule_pack.setdefault("generatedFrom", {})
    if isinstance(generated_from, dict):
        generated_from.setdefault("sourceManifestPath", rel(source_manifest_path))
        generated_from.setdefault("proposalPath", rel(proposal_path))
        generated_from.setdefault("phase1Reentry", True)
    rule_pack_path = run_dir / "normalized-rule-pack.json"
    rule_pack_path.write_text(json.dumps(rule_pack, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    agent_a_path = run_dir / "agent-a-tax-pack.md"
    diff_path = run_dir / "diff-from-previous.md"
    write_text(agent_a_path, make_agent_a_tax_pack(run_dir, source_manifest, rule_pack))
    write_text(diff_path, make_diff(run_dir, rule_pack))

    summary_path = run_dir / "phase1-reentry-ready-for-review.md"
    expected_codes = read_expected_codes(run_dir)
    missing_text = "\n".join(f"- {code}" for code in expected_codes) if expected_codes else "- None listed."
    validation_rows = []

    if args.validate:
        validations = [
            ("source-manifest", source_manifest_path),
            ("normalized-rule-pack", rule_pack_path),
            ("tax-pack", agent_a_path),
            ("rule-diff", diff_path),
        ]
        for kind, path in validations:
            code, output = validate(kind, path)
            validation_rows.append((kind, path, code, output.strip().splitlines()[-5:]))
            if code != 0:
                print(f"ERROR: validation failed for {path}", file=sys.stderr)
                print(output, file=sys.stderr)
                return 1

    command_rows = [
        "| Command | Working Directory | Exit Code | Notes |",
        "|---|---|---:|---|",
    ]
    if args.validate:
        for kind, path, code, _ in validation_rows:
            command_rows.append(
                f"| `python {rel(VALIDATOR)} --kind {kind} {rel(path)}` | `{REPO}` | {code} | {kind} contract validation passed. |"
            )
    else:
        command_rows.append("| `prepare-phase1-reentry.py --run-dir ...` | repository root | 0 | Generated artifacts without validation flag. |")

    summary = f"""# Phase 1 Re-entry Ready For Review

- As-of date: {datetime.now(timezone.utc).strftime("%Y-%m-%d")}
- Run id: {run_dir.name}
- Run directory: {rel(run_dir)}
- Target law context: {rule_pack.get("taxYear")} income / {rule_pack.get("filingYear")} filing
- Slice: {run_dir.name.split('-', 2)[-1] if '-' in run_dir.name else run_dir.name}

## Generated Artifacts

- `{rel(agent_a_path)}`
- `{rel(source_manifest_path)}`
- `{rel(rule_pack_path)}`
- `{rel(diff_path)}`

## Rule Codes Prepared

{missing_text}

## Commands Run

{chr(10).join(command_rows)}

## Review Boundary

- The generated normalized rule pack is `READY_FOR_REVIEW`.
- This script did not write to `plugins/year-end-harness/law-packs/**`.
- This script did not set any rule pack to `PUBLISHED`.
- Runtime implementation must remain blocked until human review/publish approval.

```text
{result_block(
    "warning",
    "Phase 1 re-entry artifacts are ready for human review; publish approval is still required.",
    summary_path,
    "Human reviewer should promote/publish the READY_FOR_REVIEW rule pack, then rerun Gate 3.",
)}
```
"""
    write_text(summary_path, summary)

    print("PHASE1_REENTRY_READY_FOR_REVIEW")
    print(f"RUN_DIR={rel(run_dir)}")
    print(f"RULE_PACK={rel(rule_pack_path)}")
    print(f"SUMMARY={rel(summary_path)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
