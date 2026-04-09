from __future__ import annotations

import argparse
import importlib.util
import json
import pathlib
import re
import sys

SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
VALIDATOR_PATH = SCRIPT_DIR / "validate-artifacts.py"
VALIDATOR_SPEC = importlib.util.spec_from_file_location("artifact_validator", VALIDATOR_PATH)
if VALIDATOR_SPEC is None or VALIDATOR_SPEC.loader is None:
    raise RuntimeError(f"Unable to load validator module from {VALIDATOR_PATH}")
artifact_validator = importlib.util.module_from_spec(VALIDATOR_SPEC)
sys.modules[VALIDATOR_SPEC.name] = artifact_validator
VALIDATOR_SPEC.loader.exec_module(artifact_validator)


ARTIFACT_SPECS = [
    {"stage": "agent-a", "file_name": "agent-a-tax-pack.md", "kind": "tax-pack"},
    {"stage": "agent-a", "file_name": "source-manifest.json", "kind": "source-manifest"},
    {"stage": "agent-a", "file_name": "normalized-rule-pack.json", "kind": "normalized-rule-pack"},
    {"stage": "agent-a", "file_name": "diff-from-previous.md", "kind": "rule-diff"},
    {"stage": "agent-b", "file_name": "agent-b-architecture-pack.md", "kind": "architecture-pack"},
    {"stage": "agent-c", "file_name": "agent-c-implementation-notes.md", "kind": "implementation-notes"},
    {"stage": "validation", "file_name": "validation-report.md", "kind": "validation-report"},
    {"stage": "loops", "file_name": "loop-1-sdet-report.md", "kind": "loop-report"},
    {"stage": "loops", "file_name": "loop-2-sdet-report.md", "kind": "loop-report"},
    {"stage": "loops", "file_name": "loop-3-sdet-report.md", "kind": "loop-report"},
    {"stage": "final", "file_name": "agent-e-final-verification.md", "kind": "final-verification"},
]

STAGE_ORDER = ["agent-a", "agent-b", "agent-c", "validation", "loops", "final"]
STAGE_RANK = {stage: index for index, stage in enumerate(STAGE_ORDER)}
PHASE_ALIASES = {
    "agent-a": "agent-a",
    "phase-1": "agent-a",
    "phase-1.5": "agent-a",
    "agent-b": "agent-b",
    "phase-2": "agent-b",
    "agent-c": "agent-c",
    "phase-3": "agent-c",
    "validation": "validation",
    "phase-3.5": "validation",
    "loops": "loops",
    "phase-4": "loops",
    "final": "final",
    "phase-5": "final",
}

DATE_DIR_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
RUN_ID_RE = re.compile(r"^\d{8}-\d{6}(?:-[A-Za-z0-9][A-Za-z0-9_-]*)?$")
RESULT_STATUS_RE = re.compile(r"^STATUS\s*:\s*(success|warning|error)\s*$")


def print_result(status: str, summary: str, artifacts: str, next_step: str) -> None:
    print("=== HARNESS RESULT ===")
    print(f"STATUS   : {status}")
    print(f"SUMMARY  : {summary}")
    print(f"ARTIFACTS: {artifacts}")
    print(f"NEXT     : {next_step}")
    print("======================")


def load_status(path: pathlib.Path) -> str | None:
    text = path.read_text(encoding="utf-8")
    for line in text.splitlines():
        match = RESULT_STATUS_RE.match(line.strip())
        if match:
            return match.group(1)
    return None


def load_rule_pack_status(path: pathlib.Path) -> str | None:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None
    status = payload.get("status")
    return status if isinstance(status, str) else None


def extract_defect_rows(path: pathlib.Path) -> list[dict[str, str]]:
    text = path.read_text(encoding="utf-8")
    section = artifact_validator.extract_section(text, "## Defects Found") or []
    _, rows = artifact_validator.parse_markdown_table(section)
    return rows


def extract_final_decision(path: pathlib.Path) -> str | None:
    text = path.read_text(encoding="utf-8")
    section = artifact_validator.extract_section(text, "## Release Decision") or []
    for line in section:
        stripped = line.strip()
        if stripped.startswith("- Decision:"):
            return stripped.split(":", 1)[1].strip()
    return None


def section_has_only_none(path: pathlib.Path, heading: str) -> bool:
    text = path.read_text(encoding="utf-8")
    section = artifact_validator.extract_section(text, heading) or []
    normalized = []
    for line in section:
        stripped = line.strip().lower()
        if not stripped or stripped.startswith("```"):
            continue
        normalized.append(stripped)
    if not normalized:
        return False
    return all(item in {"- none.", "- none", "none.", "none", "n/a"} for item in normalized)


def validate_run_directory(run_dir: pathlib.Path, errors: list[str]) -> None:
    if not run_dir.exists():
        errors.append("Run directory does not exist")
        return
    if not run_dir.is_dir():
        errors.append("Run directory path is not a directory")
        return

    normalized = run_dir.as_posix()
    if "/.local/harness/" not in normalized and not normalized.startswith(".local/harness/"):
        errors.append("Run directory must live under .local/harness/<date>/<run-id>")

    date_dir = run_dir.parent.name
    run_id = run_dir.name
    if not DATE_DIR_RE.match(date_dir):
        errors.append(f"Run directory parent must be a date folder YYYY-MM-DD, got: {date_dir}")
    if not RUN_ID_RE.match(run_id):
        errors.append(f"Run directory name must match YYYYMMDD-HHmmss[-worktag], got: {run_id}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", required=True, help="Path to .local/harness/<date>/<run-id>")
    parser.add_argument(
        "--through-phase",
        choices=sorted(PHASE_ALIASES),
        default="final",
        help="Validate prerequisites and artifact contracts through a stage or phase alias.",
    )
    args = parser.parse_args()

    target_stage = PHASE_ALIASES[args.through_phase]
    target_rank = STAGE_RANK[target_stage]
    run_dir = pathlib.Path(args.run_dir)
    errors: list[str] = []

    validate_run_directory(run_dir, errors)
    if errors:
        print_result(
            "error",
            "; ".join(errors),
            str(run_dir),
            "Use a valid .local/harness/<date>/<run-id> directory and rerun the gate.",
        )
        return 1

    artifact_paths = [{**spec, "path": run_dir / spec["file_name"]} for spec in ARTIFACT_SPECS]
    required_stages = {stage for stage, rank in STAGE_RANK.items() if rank <= target_rank}

    for spec in artifact_paths:
        if not spec["path"].exists():
            continue
        current_rank = STAGE_RANK[spec["stage"]]
        for prior_spec in artifact_paths:
            if STAGE_RANK[prior_spec["stage"]] >= current_rank:
                continue
            if not prior_spec["path"].exists():
                errors.append(
                    f"{spec['file_name']} exists before prerequisite stage artifact {prior_spec['file_name']} is present"
                )

    for spec in artifact_paths:
        if spec["stage"] not in required_stages:
            continue
        if not spec["path"].exists():
            errors.append(f"Missing required artifact for stage {spec['stage']}: {spec['file_name']}")
            continue

        result = artifact_validator.validate_artifact(spec["kind"], spec["path"])
        errors.extend(f"{spec['file_name']}: {message}" for message in result.errors)

        if spec["kind"] not in {"source-manifest", "normalized-rule-pack"}:
            status = load_status(spec["path"])
            if status == "error":
                errors.append(f"{spec['file_name']}: HARNESS RESULT status is error")

    normalized_rule_pack_path = run_dir / "normalized-rule-pack.json"
    if normalized_rule_pack_path.exists():
        rule_pack_status = load_rule_pack_status(normalized_rule_pack_path)
        if target_stage == "agent-a":
            if rule_pack_status not in {"READY_FOR_REVIEW", "BLOCKED", "PUBLISHED"}:
                errors.append(
                    "normalized-rule-pack.json must end Agent A with status READY_FOR_REVIEW, BLOCKED, or PUBLISHED"
                )
        else:
            if rule_pack_status not in {"READY_FOR_REVIEW", "PUBLISHED"}:
                errors.append(
                    "Later phases require normalized-rule-pack.json status READY_FOR_REVIEW or PUBLISHED"
                )

    if target_stage in {"loops", "final"}:
        open_high_defects: list[str] = []
        accepted_risks: list[str] = []
        for spec in artifact_paths:
            if spec["stage"] != "loops" or not spec["path"].exists():
                continue
            for row in extract_defect_rows(spec["path"]):
                severity = row.get("Severity", "").strip()
                state = row.get("State", "").strip()
                summary = row.get("Summary", "").strip()
                defect_label = f"{spec['file_name']}::{row.get('ID', '').strip() or 'unknown'}"
                if severity in {"blocking", "major"} and state == "open":
                    open_high_defects.append(f"{defect_label} ({summary})")
                if state == "accepted-risk":
                    accepted_risks.append(f"{defect_label} ({summary})")

        if open_high_defects:
            errors.append("Open blocking/major defects remain: " + ", ".join(open_high_defects))

        if target_stage == "final":
            final_path = run_dir / "agent-e-final-verification.md"
            if final_path.exists():
                decision = extract_final_decision(final_path)
                if decision == "approved" and accepted_risks:
                    errors.append("Final decision cannot be approved while accepted-risk defects remain")
                if decision == "approved" and not section_has_only_none(final_path, "## Blocking Risks"):
                    errors.append("Final decision cannot be approved while Blocking Risks section is non-empty")

    if errors:
        deduped_errors = list(dict.fromkeys(errors))
        print_result(
            "error",
            "Phase gate failed: " + "; ".join(deduped_errors),
            str(run_dir),
            "Add or fix the required artifacts, then rerun the gate.",
        )
        return 1

    print_result(
        "success",
        f"Phase gate passed through {args.through_phase} ({target_stage}).",
        str(run_dir),
        "Proceed to the next harness phase.",
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
