#!/usr/bin/env python3
"""Mark a slice as done in backlog.json.

Usage:
    mark-slice-done.py --slice-id <id> --run-dir <path> [--commit-sha <sha>]

Updates:
- slice.status = "done"
- appends run-dir to slice.doneRuns
- appends commit sha to slice.doneCommits (if provided)
- sets slice.doneAt timestamp
- appends entry to meta runHistory
- updates meta.updatedAt

Exit codes:
    0 - success
    2 - slice not found / backlog missing / IO error
"""
import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
BACKLOG = HERE.parent / "backlog.json"


def now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--slice-id", required=True)
    p.add_argument("--run-dir", required=True,
                   help="Path to .local/harness/<date>/<run-id>/ for this slice")
    p.add_argument("--commit-sha", default=None)
    args = p.parse_args()

    if not BACKLOG.exists():
        print(f"ERROR: backlog not found at {BACKLOG}", file=sys.stderr)
        return 2

    try:
        data = json.loads(BACKLOG.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        print(f"ERROR: backlog is not valid JSON: {e}", file=sys.stderr)
        return 2

    slice_entry = None
    for s in data.get("slices", []):
        if s.get("id") == args.slice_id:
            slice_entry = s
            break
    if slice_entry is None:
        print(f"ERROR: slice '{args.slice_id}' not found in backlog", file=sys.stderr)
        return 2

    now = now_iso()
    slice_entry["status"] = "done"
    slice_entry["doneAt"] = now
    runs = slice_entry.setdefault("doneRuns", [])
    if args.run_dir not in runs:
        runs.append(args.run_dir)
    if args.commit_sha:
        commits = slice_entry.setdefault("doneCommits", [])
        if args.commit_sha not in commits:
            commits.append(args.commit_sha)

    history = data.setdefault("runHistory", [])
    history.append({
        "sliceId": args.slice_id,
        "runDir": args.run_dir,
        "commitSha": args.commit_sha,
        "at": now,
    })
    data.setdefault("meta", {})["updatedAt"] = now[:10]

    BACKLOG.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"DONE: {args.slice_id}")
    print(f"  run-dir    = {args.run_dir}")
    if args.commit_sha:
        print(f"  commit-sha = {args.commit_sha}")
    print(f"  at         = {now}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
