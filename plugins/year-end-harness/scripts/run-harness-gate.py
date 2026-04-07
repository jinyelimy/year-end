from __future__ import annotations

import argparse
import importlib.util
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
    ("agent-a", "agent-a-tax-pack.md", "tax-pack"),
    ("agent-b", "agent-b-architecture-pack.md", "architecture-pack"),
    ("agent-c", "agent-c-implementation-notes.md", "implementation-notes"),
    ("validation", "validation-report.md", "validation-report"),
    ("loop-1", "loop-1-sdet-report.md", "loop-report"),
    ("loop-2", "loop-2-sdet-report.md", "loop-report"),
    ("loop-3", "loop-3-sdet-report.md", "loop-report"),
    ("final", "agent-e-final-verification.md", "final-verification"),
]

PHASE_REQUIREMENTS = {
    "agent-a": 1,
    "agent-b": 2,
    "agent-c": 3,
    "validation": 4,
    "loops": 7,
    "final": 8,
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
        choices=sorted(PHASE_REQUIREMENTS),
        default="final",
        help="Validate prerequisites and artifact contracts through this phase.",
    )
    args = parser.parse_args()

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

    artifact_paths = [(phase, run_dir / file_name, kind) for phase, file_name, kind in ARTIFACT_SPECS]
    required_count = PHASE_REQUIREMENTS[args.through_phase]

    for index, (phase, artifact_path, _kind) in enumerate(artifact_paths):
        if artifact_path.exists():
            for previous_phase, previous_path, _ in artifact_paths[:index]:
                if not previous_path.exists():
                    errors.append(f"{artifact_path.name} exists before prerequisite artifact {previous_phase} is present")

    for phase, artifact_path, kind in artifact_paths[:required_count]:
        if not artifact_path.exists():
            errors.append(f"Missing required artifact for phase {phase}: {artifact_path.name}")
            continue
        result = artifact_validator.validate_artifact(kind, artifact_path)
        errors.extend(f"{artifact_path.name}: {message}" for message in result.errors)

        status = load_status(artifact_path)
        if status == "error":
            errors.append(f"{artifact_path.name}: HARNESS RESULT status is error")

    if args.through_phase in {"loops", "final"}:
        open_high_defects: list[str] = []
        accepted_risks: list[str] = []
        for _phase, artifact_path, _kind in artifact_paths[4:7]:
            if not artifact_path.exists():
                continue
            for row in extract_defect_rows(artifact_path):
                severity = row.get("Severity", "").strip()
                state = row.get("State", "").strip()
                summary = row.get("Summary", "").strip()
                defect_label = f"{artifact_path.name}::{row.get('ID', '').strip() or 'unknown'}"
                if severity in {"blocking", "major"} and state == "open":
                    open_high_defects.append(f"{defect_label} ({summary})")
                if state == "accepted-risk":
                    accepted_risks.append(f"{defect_label} ({summary})")

        if open_high_defects:
            errors.append("Open blocking/major defects remain: " + ", ".join(open_high_defects))

        if args.through_phase == "final":
            final_path = artifact_paths[7][1]
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
        f"Phase gate passed through {args.through_phase}.",
        str(run_dir),
        "Proceed to the next harness phase.",
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
