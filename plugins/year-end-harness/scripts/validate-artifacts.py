from __future__ import annotations

import argparse
import pathlib
import sys


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


def print_result(status: str, summary: str, artifacts: str, next_step: str) -> None:
    print("=== HARNESS RESULT ===")
    print(f"STATUS   : {status}")
    print(f"SUMMARY  : {summary}")
    print(f"ARTIFACTS: {artifacts}")
    print(f"NEXT     : {next_step}")
    print("======================")


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

    text = target.read_text(encoding="utf-8")
    missing = [heading for heading in REQUIRED_HEADINGS[args.kind] if heading not in text]
    if "=== HARNESS RESULT ===" not in text:
        missing.append("HARNESS RESULT block")

    if missing:
        print_result(
            "error",
            f"Artifact is missing required sections: {', '.join(missing)}",
            str(target),
            "Add the missing sections and validate again.",
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
