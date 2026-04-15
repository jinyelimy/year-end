#!/usr/bin/env python3
"""Check whether backlog has any pending slice left.

Output:
    BACKLOG_EMPTY                 (exit 0)
or:
    PENDING: <count>              (exit 1)
      - <id> (<priority>) <name>
      ...

Use for ralph completion-promise evaluation.
"""
import json
import sys
from pathlib import Path

# Force UTF-8 stdout so Korean text doesn't garble on Windows terminals
try:
    sys.stdout.reconfigure(encoding="utf-8")
except (AttributeError, OSError):
    pass

HERE = Path(__file__).resolve().parent
BACKLOG = HERE.parent / "backlog.json"


def main() -> int:
    if not BACKLOG.exists():
        print(f"ERROR: backlog not found at {BACKLOG}", file=sys.stderr)
        return 2
    try:
        data = json.loads(BACKLOG.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        print(f"ERROR: backlog is not valid JSON: {e}", file=sys.stderr)
        return 2

    pending = [s for s in data.get("slices", []) if s.get("status") == "pending"]
    if not pending:
        print("BACKLOG_EMPTY")
        return 0

    print(f"PENDING: {len(pending)}")
    for s in pending:
        print(f"  - {s['id']} ({s.get('priority', '?')}) {s['name']}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
