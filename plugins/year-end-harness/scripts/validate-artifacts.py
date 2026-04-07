from __future__ import annotations

import argparse
import pathlib
import re
import sys
from dataclasses import dataclass


REQUIRED_HEADINGS = {
    "tax-pack": [
        "## Context",
        "## Inputs",
        "## Source Register",
        "## Confirmed Rules",
        "## Inferred Rules",
        "## Open Questions",
        "## Files",
        "## Validation",
    ],
    "architecture-pack": [
        "## Context",
        "## Inputs",
        "## Decisions",
        "## Open Questions",
        "## Files",
        "## Validation",
    ],
    "implementation-notes": [
        "## Context",
        "## Inputs",
        "## Changed Files",
        "## Decisions",
        "## Validation",
        "## Open Questions",
        "## Files",
    ],
    "loop-report": [
        "## Context",
        "## Inputs",
        "## Input Scenario",
        "## Expected Result",
        "## Actual Result",
        "## Defects Found",
        "## Requested Fix",
        "## Re-validation Result",
        "## Files",
        "## Validation",
    ],
    "final-verification": [
        "## Context",
        "## Inputs",
        "## Release Decision",
        "## Blocking Risks",
        "## Remaining Warnings",
        "## Files",
        "## Validation",
    ],
    "validation-report": [
        "## Context",
        "## Changed Areas",
        "## Commands Run",
        "## Results",
        "## Open Questions",
        "## Files",
        "## Validation",
    ],
}

REQUIRED_SUBHEADINGS = {
    "tax-pack": [
        "### 세율표",
        "### 공제 한도표",
        "### 인적공제 판단표",
        "### 항목별 가족 합산 가능 여부 표",
        "### 증빙 요구사항 표",
    ],
    "architecture-pack": [
        "### Entity and Field Proposal",
        "### Family Mapping Logic Tree",
        "### Claimability Flow",
        "### Evidence Verification Flow",
        "### API and DTO Impact",
        "### Implementation Priority",
    ],
}

REQUIRED_METADATA = {
    "tax-pack": [
        "As-of date",
        "Run id",
        "Run directory",
        "Target law context",
        "Requested scope",
    ],
    "architecture-pack": [
        "As-of date",
        "Run id",
        "Run directory",
        "Target law context",
        "Scope",
    ],
    "implementation-notes": [
        "As-of date",
        "Run id",
        "Run directory",
        "Target law context",
        "Scope",
    ],
    "loop-report": [
        "Loop number",
        "As-of date",
        "Run id",
        "Run directory",
        "Target law context",
    ],
    "final-verification": [
        "As-of date",
        "Run id",
        "Run directory",
        "Target law context",
        "Reviewed loops",
    ],
    "validation-report": [
        "As-of date",
        "Run id",
        "Run directory",
        "Changed scope",
        "Trigger",
    ],
}

REQUIRED_CONTENT_SECTIONS = {
    "tax-pack": [
        "## Context",
        "## Inputs",
        "## Inferred Rules",
        "## Open Questions",
        "## Files",
        "## Validation",
    ],
    "architecture-pack": [
        "## Context",
        "## Inputs",
        "## Open Questions",
        "## Files",
        "## Validation",
    ],
    "implementation-notes": [
        "## Context",
        "## Inputs",
        "## Changed Files",
        "## Decisions",
        "## Validation",
        "## Open Questions",
        "## Files",
    ],
    "loop-report": [
        "## Context",
        "## Inputs",
        "## Input Scenario",
        "## Expected Result",
        "## Actual Result",
        "## Requested Fix",
        "## Re-validation Result",
        "## Files",
        "## Validation",
    ],
    "final-verification": [
        "## Context",
        "## Inputs",
        "## Blocking Risks",
        "## Remaining Warnings",
        "## Files",
        "## Validation",
    ],
    "validation-report": [
        "## Context",
        "## Changed Areas",
        "## Results",
        "## Open Questions",
        "## Files",
        "## Validation",
    ],
}

REQUIRED_ENTITY_FIELDS = {
    "ownerPersonKey",
    "claimantDependentId",
    "mappingConfidence",
    "mappingReason",
    "claimability",
    "claimabilityReason",
    "evidenceRequirementCode",
    "evidenceStatus",
}

ALLOWED_RESULT_STATUS = {"success", "warning", "error"}
ALLOWED_DECISIONS = {"approved", "approved-with-warning", "rejected"}
ALLOWED_DEFECT_SEVERITIES = {"blocking", "major", "minor", "-", "none"}
ALLOWED_DEFECT_STATES = {"open", "resolved", "accepted-risk", "none"}

PLACEHOLDER_RE = re.compile(r"<[^>\n]+>")
EMPTY_METADATA_RE = re.compile(r"^- [^:]+:\s*$")
RESULT_FIELD_RE = re.compile(r"^(STATUS|SUMMARY|ARTIFACTS|NEXT)\s*:\s*(.+)$")


@dataclass
class ValidationResult:
    errors: list[str]

    @property
    def ok(self) -> bool:
        return not self.errors


def print_result(status: str, summary: str, artifacts: str, next_step: str) -> None:
    print("=== HARNESS RESULT ===")
    print(f"STATUS   : {status}")
    print(f"SUMMARY  : {summary}")
    print(f"ARTIFACTS: {artifacts}")
    print(f"NEXT     : {next_step}")
    print("======================")


def load_text(path: pathlib.Path) -> str:
    return path.read_text(encoding="utf-8")


def extract_section(text: str, heading: str) -> list[str] | None:
    lines = text.splitlines()
    try:
        start_index = next(index for index, line in enumerate(lines) if line.strip() == heading)
    except StopIteration:
        return None

    level = len(heading) - len(heading.lstrip("#"))
    content: list[str] = []
    for line in lines[start_index + 1 :]:
        stripped = line.strip()
        if stripped.startswith("#"):
            current_level = len(stripped) - len(stripped.lstrip("#"))
            if current_level <= level:
                break
        content.append(line)
    return content


def parse_markdown_table(lines: list[str]) -> tuple[list[str], list[dict[str, str]]]:
    table_lines = [line.strip() for line in lines if line.strip().startswith("|")]
    if len(table_lines) < 2:
        return [], []

    header = [cell.strip() for cell in table_lines[0].strip("|").split("|")]
    rows: list[dict[str, str]] = []
    for line in table_lines[2:]:
        cells = [cell.strip() for cell in line.strip("|").split("|")]
        if len(cells) != len(header):
            continue
        rows.append(dict(zip(header, cells)))
    return header, rows


def has_meaningful_content(lines: list[str]) -> bool:
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("```") or stripped.startswith("#"):
            continue
        if EMPTY_METADATA_RE.match(stripped):
            continue
        if PLACEHOLDER_RE.search(stripped):
            continue
        if stripped.startswith("|"):
            cells = [cell.strip() for cell in stripped.strip("|").split("|")]
            if any(cells):
                return True
            continue
        return True
    return False


def validate_required_metadata(text: str, labels: list[str], errors: list[str]) -> None:
    lines = text.splitlines()
    for label in labels:
        prefix = f"- {label}:"
        matches = [line.strip() for line in lines if line.strip().startswith(prefix)]
        if not matches:
            errors.append(f"Missing required metadata line: {label}")
            continue
        value = matches[0].split(":", 1)[1].strip()
        if not value:
            errors.append(f"Metadata line is empty: {label}")


def validate_result_block(text: str, target: pathlib.Path, errors: list[str]) -> None:
    lines = text.splitlines()
    try:
        start_index = next(index for index, line in enumerate(lines) if line.strip() == "=== HARNESS RESULT ===")
    except StopIteration:
        errors.append("Missing HARNESS RESULT block")
        return

    fields: dict[str, str] = {}
    for line in lines[start_index + 1 : start_index + 5]:
        match = RESULT_FIELD_RE.match(line.strip())
        if match:
            fields[match.group(1)] = match.group(2).strip()

    for field in ("STATUS", "SUMMARY", "ARTIFACTS", "NEXT"):
        if field not in fields:
            errors.append(f"HARNESS RESULT block is missing field: {field}")

    status = fields.get("STATUS")
    if status and status not in ALLOWED_RESULT_STATUS:
        errors.append(f"Invalid HARNESS RESULT status: {status}")

    artifacts = fields.get("ARTIFACTS")
    if artifacts:
        if ".local/harness/" not in artifacts.replace("\\", "/"):
            errors.append("HARNESS RESULT artifacts path must point into .local/harness/")
        if target.name not in artifacts:
            errors.append(f"HARNESS RESULT artifacts path must include file name {target.name}")


def validate_tax_pack(text: str, errors: list[str]) -> None:
    source_section = extract_section(text, "## Source Register") or []
    header, rows = parse_markdown_table(source_section)
    expected_header = ["Source", "URL", "Published At", "Effective At", "Checked At", "Notes"]
    if header != expected_header:
        errors.append("Source Register table header must be: Source | URL | Published At | Effective At | Checked At | Notes")
    if not rows:
        errors.append("Source Register must contain at least one populated source row")
    for row in rows:
        for key in expected_header:
            value = row.get(key, "").strip()
            if not value:
                errors.append(f"Source Register row is missing value for: {key}")

    for heading in REQUIRED_SUBHEADINGS["tax-pack"]:
        content = extract_section(text, heading) or []
        if not has_meaningful_content(content):
            errors.append(f"Subsection is empty: {heading}")


def validate_architecture_pack(text: str, errors: list[str]) -> None:
    for heading in REQUIRED_SUBHEADINGS["architecture-pack"]:
        content = extract_section(text, heading) or []
        if not has_meaningful_content(content):
            errors.append(f"Subsection is empty: {heading}")

    for field in sorted(REQUIRED_ENTITY_FIELDS):
        if field not in text:
            errors.append(f"Architecture pack is missing required entity field: {field}")


def validate_implementation_notes(text: str, errors: list[str]) -> None:
    changed_files = extract_section(text, "## Changed Files") or []
    if not has_meaningful_content(changed_files):
        errors.append("Changed Files section must list at least one file or an explicit no-change decision")


def validate_validation_report(text: str, errors: list[str]) -> None:
    commands_section = extract_section(text, "## Commands Run") or []
    header, rows = parse_markdown_table(commands_section)
    expected_header = ["Command", "Working Directory", "Exit Code", "Notes"]
    if header != expected_header:
        errors.append("Commands Run table header must be: Command | Working Directory | Exit Code | Notes")
    if not rows:
        errors.append("Commands Run must contain at least one executed command row")
    for row in rows:
        command = row.get("Command", "").strip()
        working_directory = row.get("Working Directory", "").strip()
        exit_code = row.get("Exit Code", "").strip()
        notes = row.get("Notes", "").strip()
        if not command:
            errors.append("Commands Run row is missing Command")
        if not working_directory:
            errors.append("Commands Run row is missing Working Directory")
        if not notes:
            errors.append("Commands Run row is missing Notes")
        try:
            int(exit_code)
        except ValueError:
            errors.append(f"Commands Run row has non-integer Exit Code: {exit_code or '<empty>'}")


def validate_loop_report(text: str, errors: list[str]) -> None:
    defects_section = extract_section(text, "## Defects Found") or []
    header, rows = parse_markdown_table(defects_section)
    expected_header = ["ID", "Severity", "State", "Summary", "Owner"]
    if header != expected_header:
        errors.append("Defects Found table header must be: ID | Severity | State | Summary | Owner")
    if not rows:
        errors.append("Defects Found must contain at least one defect status row")
    for row in rows:
        severity = row.get("Severity", "").strip()
        state = row.get("State", "").strip()
        summary = row.get("Summary", "").strip()
        if severity not in ALLOWED_DEFECT_SEVERITIES:
            errors.append(f"Invalid defect severity: {severity or '<empty>'}")
        if state not in ALLOWED_DEFECT_STATES:
            errors.append(f"Invalid defect state: {state or '<empty>'}")
        if not summary:
            errors.append("Defects Found row is missing Summary")
        if state == "none" and severity not in {"-", "none"}:
            errors.append("Defect rows with state 'none' must use severity '-' or 'none'")


def validate_final_verification(text: str, errors: list[str]) -> None:
    decision_section = extract_section(text, "## Release Decision") or []
    lines = [line.strip() for line in decision_section if line.strip()]
    decision_line = next((line for line in lines if line.startswith("- Decision:")), None)
    reason_line = next((line for line in lines if line.startswith("- Reason:")), None)
    if decision_line is None:
        errors.append("Release Decision section must include '- Decision:'")
    else:
        decision_value = decision_line.split(":", 1)[1].strip()
        if decision_value not in ALLOWED_DECISIONS:
            errors.append(f"Invalid release decision: {decision_value or '<empty>'}")
    if reason_line is None:
        errors.append("Release Decision section must include '- Reason:'")
    elif not reason_line.split(":", 1)[1].strip():
        errors.append("Release Decision reason must not be empty")


def validate_generic_rules(kind: str, text: str, target: pathlib.Path, errors: list[str]) -> None:
    for heading in REQUIRED_HEADINGS[kind]:
        if heading not in text:
            errors.append(f"Missing required section: {heading}")

    for heading in REQUIRED_SUBHEADINGS.get(kind, []):
        if heading not in text:
            errors.append(f"Missing required subsection: {heading}")

    validate_required_metadata(text, REQUIRED_METADATA[kind], errors)

    for line in text.splitlines():
        stripped = line.strip()
        if EMPTY_METADATA_RE.match(stripped):
            errors.append(f"Unfilled metadata line remains: {stripped}")

    placeholder_tokens = sorted(set(PLACEHOLDER_RE.findall(text)))
    for token in placeholder_tokens:
        errors.append(f"Unreplaced placeholder token: {token}")

    for heading in REQUIRED_CONTENT_SECTIONS[kind]:
        content = extract_section(text, heading) or []
        if not has_meaningful_content(content):
            errors.append(f"Section is empty: {heading}")

    validate_result_block(text, target, errors)


def validate_artifact(kind: str, path: pathlib.Path) -> ValidationResult:
    errors: list[str] = []
    text = load_text(path)

    validate_generic_rules(kind, text, path, errors)

    if kind == "tax-pack":
        validate_tax_pack(text, errors)
    elif kind == "architecture-pack":
        validate_architecture_pack(text, errors)
    elif kind == "implementation-notes":
        validate_implementation_notes(text, errors)
    elif kind == "validation-report":
        validate_validation_report(text, errors)
    elif kind == "loop-report":
        validate_loop_report(text, errors)
    elif kind == "final-verification":
        validate_final_verification(text, errors)

    deduped_errors = list(dict.fromkeys(errors))
    return ValidationResult(errors=deduped_errors)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--kind", choices=sorted(REQUIRED_HEADINGS), required=True)
    parser.add_argument("path")
    args = parser.parse_args()

    target = pathlib.Path(args.path)
    if not target.exists():
        print_result(
            "error",
            "Artifact file does not exist.",
            str(target),
            "Create the artifact from the matching template and rerun validation.",
        )
        return 1

    result = validate_artifact(args.kind, target)
    if result.errors:
        print_result(
            "error",
            "Artifact validation failed: " + "; ".join(result.errors),
            str(target),
            "Fix the reported contract issues and rerun validation.",
        )
        return 1

    print_result(
        "success",
        "Artifact contract validation passed.",
        str(target),
        "Continue to the next harness phase.",
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
