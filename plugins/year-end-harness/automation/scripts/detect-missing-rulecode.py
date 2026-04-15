#!/usr/bin/env python3
"""Detect whether a slice's expected ruleCodes are missing from published law packs.

Reads:
    <RUN_DIR>/expected-rule-codes.txt
        - one ruleCode per line
        - lines starting with '#' are comments
        - blank lines ignored

Scans:
    plugins/year-end-harness/law-packs/**/normalized-rule-pack.json
        - collects all rule.ruleCode values

Output:
    ALL_CODES_PRESENT                 (exit 0)
    NO_EXPECTED_CODES: <detail>       (exit 0)    <- 4-2 did not emit list
    MISSING: <c1>,<c2>,...            (exit 1)    <- HALT_PHASE1_REENTRY

Usage:
    detect-missing-rulecode.py --run-dir .local/harness/2026-04-16/20260416-090000-personal-deduction/
"""
import argparse
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
# automation/scripts -> automation -> year-end-harness -> plugins -> <repo root>
REPO = HERE.parents[3]
LAW_PACKS_DIR = REPO / "plugins" / "year-end-harness" / "law-packs"


def load_all_published_codes() -> set[str]:
    codes: set[str] = set()
    if not LAW_PACKS_DIR.exists():
        return codes
    for rule_pack in LAW_PACKS_DIR.rglob("normalized-rule-pack.json"):
        try:
            data = json.loads(rule_pack.read_text(encoding="utf-8"))
        except Exception:
            continue
        # schema variants: top-level "rules" list, or nested under "ruleSet.rules"
        rules = data.get("rules")
        if rules is None and isinstance(data.get("ruleSet"), dict):
            rules = data["ruleSet"].get("rules")
        if not rules:
            continue
        for rule in rules:
            code = rule.get("ruleCode")
            if code:
                codes.add(code)
    return codes


def read_expected_codes(run_dir: Path) -> list[str]:
    f = run_dir / "expected-rule-codes.txt"
    if not f.exists():
        return []
    codes: list[str] = []
    for line in f.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        codes.append(line)
    return codes


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--run-dir", required=True,
                   help="Path to .local/harness/<date>/<run-id>/")
    args = p.parse_args()

    run_dir = Path(args.run_dir)
    if not run_dir.exists():
        print(f"ERROR: run-dir not found: {run_dir}", file=sys.stderr)
        return 2

    expected = read_expected_codes(run_dir)
    if not expected:
        print("NO_EXPECTED_CODES: expected-rule-codes.txt missing or empty in run-dir")
        return 0

    published = load_all_published_codes()
    missing = [c for c in expected if c not in published]
    if not missing:
        print("ALL_CODES_PRESENT")
        return 0

    print(f"MISSING: {','.join(missing)}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
