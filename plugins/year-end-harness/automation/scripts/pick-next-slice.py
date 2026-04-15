#!/usr/bin/env python3
"""Pick the next pending slice from backlog.json in priority order.

Output (multi-line, key=value):
    SLICE_ID=<id>
    NAME=<name>
    PRIORITY=<P0|P1|P2|P3>
    CATEGORY=<category>
    FLOW=<comma-separated>
    NOTES=<notes>

Or single line:
    BACKLOG_EMPTY

Exit codes:
    0 - slice found or backlog empty (both are successful states)
    2 - backlog.json missing or malformed
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
PRIORITY_ORDER = {"P0": 0, "P1": 1, "P2": 2, "P3": 3}


def main() -> int:
    if not BACKLOG.exists():
        print(f"ERROR: backlog not found at {BACKLOG}", file=sys.stderr)
        return 2
    try:
        data = json.loads(BACKLOG.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        print(f"ERROR: backlog is not valid JSON: {e}", file=sys.stderr)
        return 2

    slices = data.get("slices", [])
    pending = [s for s in slices if s.get("status") == "pending"]
    if not pending:
        print("BACKLOG_EMPTY")
        return 0

    # Stable sort: priority first, then original backlog order
    indexed = [(i, s) for i, s in enumerate(slices) if s.get("status") == "pending"]
    indexed.sort(key=lambda t: (PRIORITY_ORDER.get(t[1].get("priority", "P9"), 99), t[0]))
    _, s = indexed[0]

    flow = ",".join(s.get("flow", [])) or "(none)"
    print(f"SLICE_ID={s['id']}")
    print(f"NAME={s['name']}")
    print(f"PRIORITY={s.get('priority', '')}")
    print(f"CATEGORY={s.get('category', '')}")
    print(f"FLOW={flow}")
    notes = s.get("notes", "")
    if notes:
        print(f"NOTES={notes}")
    sub = s.get("subCategories", [])
    if sub:
        print(f"SUB_CATEGORIES={','.join(sub)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
