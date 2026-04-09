from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys
from dataclasses import dataclass
from typing import Any


MARKDOWN_REQUIREMENTS = {
    "tax-pack": {
        "headings": [
            "## Context",
            "## Inputs",
            "## Source Register",
            "## Confirmed Rules",
            "## Inferred Rules",
            "## Open Questions",
            "## Files",
            "## Validation",
        ],
        "subheadings": [
            "### 세율표",
            "### 공제 한도표",
            "### 인적공제 판단표",
            "### 항목별 가족 합산 가능 여부 표",
            "### 증빙 요구사항 표",
        ],
        "metadata": [
            "As-of date",
            "Run id",
            "Run directory",
            "Target law context",
            "Requested scope",
        ],
        "content_sections": [
            "## Context",
            "## Inputs",
            "## Inferred Rules",
            "## Open Questions",
            "## Files",
            "## Validation",
        ],
        "requires_result_block": True,
    },
    "rule-diff": {
        "headings": [
            "## Context",
            "## Inputs",
            "## Version Pair",
            "## Added Rules",
            "## Changed Rules",
            "## Removed Rules",
            "## Impact Summary",
            "## Files",
            "## Validation",
        ],
        "subheadings": [],
        "metadata": [
            "As-of date",
            "Run id",
            "Run directory",
            "Target law context",
            "Current rule version",
            "Previous rule version",
        ],
        "content_sections": [
            "## Context",
            "## Inputs",
            "## Version Pair",
            "## Impact Summary",
            "## Files",
            "## Validation",
        ],
        "requires_result_block": True,
    },
    "architecture-pack": {
        "headings": [
            "## Context",
            "## Inputs",
            "## Decisions",
            "## Open Questions",
            "## Files",
            "## Validation",
        ],
        "subheadings": [
            "### Entity and Field Proposal",
            "### Family Mapping Logic Tree",
            "### Claimability Flow",
            "### Evidence Verification Flow",
            "### API and DTO Impact",
            "### Implementation Priority",
        ],
        "metadata": [
            "As-of date",
            "Run id",
            "Run directory",
            "Target law context",
            "Scope",
        ],
        "content_sections": [
            "## Context",
            "## Inputs",
            "## Open Questions",
            "## Files",
            "## Validation",
        ],
        "requires_result_block": True,
    },
    "implementation-notes": {
        "headings": [
            "## Context",
            "## Inputs",
            "## Changed Files",
            "## Decisions",
            "## Validation",
            "## Open Questions",
            "## Files",
        ],
        "subheadings": [],
        "metadata": [
            "As-of date",
            "Run id",
            "Run directory",
            "Target law context",
            "Scope",
        ],
        "content_sections": [
            "## Context",
            "## Inputs",
            "## Changed Files",
            "## Decisions",
            "## Validation",
            "## Open Questions",
            "## Files",
        ],
        "requires_result_block": True,
    },
    "loop-report": {
        "headings": [
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
        "subheadings": [],
        "metadata": [
            "Loop number",
            "As-of date",
            "Run id",
            "Run directory",
            "Target law context",
        ],
        "content_sections": [
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
        "requires_result_block": True,
    },
    "final-verification": {
        "headings": [
            "## Context",
            "## Inputs",
            "## Release Decision",
            "## Blocking Risks",
            "## Remaining Warnings",
            "## Files",
            "## Validation",
        ],
        "subheadings": [],
        "metadata": [
            "As-of date",
            "Run id",
            "Run directory",
            "Target law context",
            "Reviewed loops",
        ],
        "content_sections": [
            "## Context",
            "## Inputs",
            "## Blocking Risks",
            "## Remaining Warnings",
            "## Files",
            "## Validation",
        ],
        "requires_result_block": True,
    },
    "validation-report": {
        "headings": [
            "## Context",
            "## Changed Areas",
            "## Commands Run",
            "## Results",
            "## Open Questions",
            "## Files",
            "## Validation",
        ],
        "subheadings": [],
        "metadata": [
            "As-of date",
            "Run id",
            "Run directory",
            "Changed scope",
            "Trigger",
        ],
        "content_sections": [
            "## Context",
            "## Changed Areas",
            "## Results",
            "## Open Questions",
            "## Files",
            "## Validation",
        ],
        "requires_result_block": True,
    },
}

JSON_KINDS = {"source-manifest", "normalized-rule-pack"}

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
ALLOWED_RULE_PACK_STATUS = {"DRAFT", "READY_FOR_REVIEW", "PUBLISHED", "RETIRED", "BLOCKED"}
ALLOWED_CONFIDENCE = {"confirmed", "inferred"}
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


def load_json(path: pathlib.Path, errors: list[str]) -> Any | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        errors.append(f"Invalid JSON: {exc}")
        return None


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


def collect_placeholder_paths(value: Any, prefix: str = "$") -> list[str]:
    found: list[str] = []
    if isinstance(value, dict):
        for key, nested in value.items():
            found.extend(collect_placeholder_paths(nested, f"{prefix}.{key}"))
    elif isinstance(value, list):
        for index, nested in enumerate(value):
            found.extend(collect_placeholder_paths(nested, f"{prefix}[{index}]"))
    elif isinstance(value, str) and PLACEHOLDER_RE.search(value):
        found.append(prefix)
    return found


def validate_markdown_artifact(kind: str, text: str, target: pathlib.Path, errors: list[str]) -> None:
    requirements = MARKDOWN_REQUIREMENTS[kind]
    for heading in requirements["headings"]:
        if heading not in text:
            errors.append(f"Missing required section: {heading}")

    for heading in requirements["subheadings"]:
        if heading not in text:
            errors.append(f"Missing required subsection: {heading}")

    validate_required_metadata(text, requirements["metadata"], errors)

    for line in text.splitlines():
        stripped = line.strip()
        if EMPTY_METADATA_RE.match(stripped):
            errors.append(f"Unfilled metadata line remains: {stripped}")

    placeholder_tokens = sorted(set(PLACEHOLDER_RE.findall(text)))
    for token in placeholder_tokens:
        errors.append(f"Unreplaced placeholder token: {token}")

    for heading in requirements["content_sections"]:
        content = extract_section(text, heading) or []
        if not has_meaningful_content(content):
            errors.append(f"Section is empty: {heading}")

    if requirements["requires_result_block"]:
        validate_result_block(text, target, errors)


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


def validate_rule_diff(text: str, errors: list[str]) -> None:
    if "first version" in text.lower():
        return

    for heading in ("## Added Rules", "## Changed Rules", "## Removed Rules"):
        content = extract_section(text, heading) or []
        if not has_meaningful_content(content):
            errors.append(f"Section is empty: {heading}")


def validate_architecture_pack(text: str, errors: list[str]) -> None:
    for heading in MARKDOWN_REQUIREMENTS["architecture-pack"]["subheadings"]:
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


def require_json_field(payload: dict[str, Any], field: str, errors: list[str]) -> Any | None:
    if field not in payload:
        errors.append(f"Missing required JSON field: {field}")
        return None
    return payload[field]


def validate_source_manifest(payload: Any, errors: list[str]) -> None:
    if not isinstance(payload, dict):
        errors.append("Source manifest root must be a JSON object")
        return

    for field in ("asOfDate", "runId", "runDirectory", "taxYear", "filingYear", "ruleVersion", "generatedAt", "sources"):
        require_json_field(payload, field, errors)

    placeholder_paths = collect_placeholder_paths(payload)
    for path in placeholder_paths:
        errors.append(f"Unreplaced placeholder token in JSON at {path}")

    sources = payload.get("sources")
    if not isinstance(sources, list) or not sources:
        errors.append("Source manifest must contain a non-empty sources array")
        return

    required_source_fields = (
        "sourceId",
        "sourceName",
        "authority",
        "sourceUrl",
        "publishedAt",
        "effectiveAt",
        "checkedAt",
        "contentType",
        "notes",
    )
    seen_source_ids: set[str] = set()
    for index, source in enumerate(sources):
        if not isinstance(source, dict):
            errors.append(f"sources[{index}] must be a JSON object")
            continue
        for field in required_source_fields:
            value = source.get(field)
            if value in (None, ""):
                errors.append(f"sources[{index}] is missing value for {field}")
        source_id = source.get("sourceId")
        if isinstance(source_id, str):
            if source_id in seen_source_ids:
                errors.append(f"Duplicate sourceId in source manifest: {source_id}")
            seen_source_ids.add(source_id)


def validate_normalized_rule_pack(payload: Any, errors: list[str]) -> None:
    if not isinstance(payload, dict):
        errors.append("Normalized rule pack root must be a JSON object")
        return

    for field in ("ruleSetId", "taxYear", "filingYear", "ruleVersion", "status", "generatedAt", "generatedFrom", "rules"):
        require_json_field(payload, field, errors)

    placeholder_paths = collect_placeholder_paths(payload)
    for path in placeholder_paths:
        errors.append(f"Unreplaced placeholder token in JSON at {path}")

    status = payload.get("status")
    if status not in ALLOWED_RULE_PACK_STATUS:
        errors.append(f"Invalid normalized rule pack status: {status or '<empty>'}")

    generated_from = payload.get("generatedFrom")
    if not isinstance(generated_from, dict) or not generated_from:
        errors.append("generatedFrom must be a non-empty JSON object")

    rules = payload.get("rules")
    if not isinstance(rules, list) or not rules:
        errors.append("Normalized rule pack must contain a non-empty rules array")
        return

    seen_rule_windows: dict[str, list[tuple[str, Any]]] = {}
    for index, rule in enumerate(rules):
        if not isinstance(rule, dict):
            errors.append(f"rules[{index}] must be a JSON object")
            continue

        for field in (
            "deductionType",
            "subType",
            "ruleCode",
            "ruleCategory",
            "parameters",
            "effectiveFrom",
            "effectiveTo",
            "sourceRefs",
            "confidence",
        ):
            if field not in rule:
                errors.append(f"rules[{index}] is missing field: {field}")

        if not isinstance(rule.get("parameters"), dict) or not rule.get("parameters"):
            errors.append(f"rules[{index}] parameters must be a non-empty object")
        source_refs = rule.get("sourceRefs")
        if not isinstance(source_refs, list) or not source_refs:
            errors.append(f"rules[{index}] sourceRefs must be a non-empty array")
        confidence = rule.get("confidence")
        if confidence not in ALLOWED_CONFIDENCE:
            errors.append(f"rules[{index}] confidence must be confirmed or inferred")

        rule_code = rule.get("ruleCode")
        effective_from = rule.get("effectiveFrom")
        effective_to = rule.get("effectiveTo")
        if isinstance(rule_code, str):
            seen_rule_windows.setdefault(rule_code, []).append((str(effective_from), effective_to))

    for rule_code, windows in seen_rule_windows.items():
        if len(windows) <= 1:
            continue
        if len(windows) != len(set(windows)):
            errors.append(f"Duplicate rule window detected for ruleCode: {rule_code}")


def validate_artifact(kind: str, path: pathlib.Path) -> ValidationResult:
    errors: list[str] = []

    if kind in JSON_KINDS:
        payload = load_json(path, errors)
        if payload is not None:
            if kind == "source-manifest":
                validate_source_manifest(payload, errors)
            elif kind == "normalized-rule-pack":
                validate_normalized_rule_pack(payload, errors)
        return ValidationResult(errors=list(dict.fromkeys(errors)))

    text = load_text(path)
    validate_markdown_artifact(kind, text, path, errors)

    if kind == "tax-pack":
        validate_tax_pack(text, errors)
    elif kind == "rule-diff":
        validate_rule_diff(text, errors)
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

    return ValidationResult(errors=list(dict.fromkeys(errors)))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--kind", choices=sorted(set(MARKDOWN_REQUIREMENTS) | JSON_KINDS), required=True)
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
