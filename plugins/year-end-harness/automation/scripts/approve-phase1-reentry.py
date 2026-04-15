#!/usr/bin/env python3
"""Approve and promote a READY_FOR_REVIEW Phase 1 re-entry law pack.

This script is the explicit human approval step. It is intentionally not called
by the autonomous slice loop. Running it means a human has reviewed the generated
rule pack and wants to promote it into the canonical law-packs directory.

Usage:
    python plugins/year-end-harness/automation/scripts/approve-phase1-reentry.py \
      --run-dir .local/harness/2026-04-15/20260415-152140-personal-deduction

By default it refuses to overwrite an existing canonical law pack.
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
LAW_PACKS = REPO / "plugins" / "year-end-harness" / "law-packs"
VALIDATOR = REPO / "plugins" / "year-end-harness" / "scripts" / "validate-artifacts.py"

REQUIRED_FILES = [
    "agent-a-tax-pack.md",
    "source-manifest.json",
    "normalized-rule-pack.json",
    "diff-from-previous.md",
]


def rel(path: Path) -> str:
    try:
        return path.resolve().relative_to(REPO.resolve()).as_posix()
    except ValueError:
        return path.as_posix()


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def print_result(status: str, summary: str, artifacts: str, next_step: str) -> None:
    print("=== HARNESS RESULT ===")
    print(f"STATUS   : {status}")
    print(f"SUMMARY  : {summary}")
    print(f"ARTIFACTS: {artifacts}")
    print(f"NEXT     : {next_step}")
    print("======================")


def validate(kind: str, path: Path) -> tuple[int, str]:
    command = [sys.executable, str(VALIDATOR), "--kind", kind, str(path)]
    completed = subprocess.run(command, cwd=REPO, text=True, capture_output=True)
    return completed.returncode, completed.stdout + completed.stderr


def fail(summary: str, artifacts: str, next_step: str) -> int:
    print_result("error", summary, artifacts, next_step)
    return 1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", required=True, help="Run dir containing READY_FOR_REVIEW Phase 1 artifacts")
    parser.add_argument("--force", action="store_true", help="Overwrite an existing canonical law pack directory")
    args = parser.parse_args()

    run_dir = Path(args.run_dir)
    if not run_dir.is_absolute():
        run_dir = REPO / run_dir
    if not run_dir.exists():
        return fail(
            f"Run directory does not exist: {run_dir}",
            str(run_dir),
            "Pass a valid .local/harness/<date>/<run-id> directory.",
        )

    missing = [name for name in REQUIRED_FILES if not (run_dir / name).exists()]
    if missing:
        return fail(
            "Required Phase 1 re-entry artifacts are missing: " + ", ".join(missing),
            rel(run_dir),
            "Run prepare-phase1-reentry.py --validate first.",
        )

    rule_pack_path = run_dir / "normalized-rule-pack.json"
    rule_pack = load_json(rule_pack_path)
    if rule_pack.get("status") != "READY_FOR_REVIEW":
        return fail(
            f"Rule pack status must be READY_FOR_REVIEW before approval, got {rule_pack.get('status')!r}.",
            rel(rule_pack_path),
            "Regenerate or inspect the rule pack before approving it.",
        )

    inferred = [
        rule.get("ruleCode", "<unknown>")
        for rule in rule_pack.get("rules", [])
        if rule.get("confidence") != "confirmed"
    ]
    if inferred:
        return fail(
            "Cannot approve rule pack with non-confirmed rules: " + ", ".join(inferred),
            rel(rule_pack_path),
            "Resolve inferred rules with official sources before approval.",
        )

    tax_year = str(rule_pack.get("taxYear"))
    rule_version = str(rule_pack.get("ruleVersion"))
    if not tax_year or tax_year == "None" or not rule_version or rule_version == "None":
        return fail(
            "Rule pack must contain taxYear and ruleVersion.",
            rel(rule_pack_path),
            "Fix normalized-rule-pack.json and rerun approval.",
        )

    dest_dir = LAW_PACKS / tax_year / rule_version
    if dest_dir.exists() and any(dest_dir.iterdir()) and not args.force:
        return fail(
            f"Canonical law pack directory already exists: {rel(dest_dir)}",
            rel(dest_dir),
            "Review existing files and rerun with --force only if overwrite is intended.",
        )

    dest_dir.mkdir(parents=True, exist_ok=True)
    for name in REQUIRED_FILES:
        source = run_dir / name
        target = dest_dir / name
        if target.exists() and not args.force:
            return fail(
                f"Destination file already exists: {rel(target)}",
                rel(target),
                "Rerun with --force only after confirming overwrite is safe.",
            )
        if name == "normalized-rule-pack.json":
            promoted = dict(rule_pack)
            promoted["status"] = "PUBLISHED"
            generated_from = promoted.setdefault("generatedFrom", {})
            if isinstance(generated_from, dict):
                generated_from["approvedFromRunDir"] = rel(run_dir)
                generated_from["approvedAt"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
                generated_from["approvalCommand"] = "approve-phase1-reentry.py"
            write_json(target, promoted)
        else:
            shutil.copyfile(source, target)

    approval_manifest = {
        "approvedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "sourceRunDir": rel(run_dir),
        "destinationDir": rel(dest_dir),
        "taxYear": rule_pack.get("taxYear"),
        "ruleVersion": rule_pack.get("ruleVersion"),
        "ruleSetId": rule_pack.get("ruleSetId"),
        "status": "PUBLISHED",
        "approvedBy": "manual CLI approval",
    }
    write_json(dest_dir / "approval-manifest.json", approval_manifest)

    validations = [
        ("source-manifest", dest_dir / "source-manifest.json"),
        ("normalized-rule-pack", dest_dir / "normalized-rule-pack.json"),
        ("tax-pack", dest_dir / "agent-a-tax-pack.md"),
        ("rule-diff", dest_dir / "diff-from-previous.md"),
    ]
    for kind, path in validations:
        code, output = validate(kind, path)
        if code != 0:
            print(output, file=sys.stderr)
            return fail(
                f"Promoted artifact failed validation: {rel(path)}",
                rel(path),
                "Fix the destination artifact or rerun approval after regenerating the source run-dir.",
            )

    print_result(
        "success",
        f"Approved and published Phase 1 law pack {tax_year}/{rule_version}.",
        rel(dest_dir),
        "Rerun detect-missing-rulecode.py or continue the deduction autopilot.",
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
