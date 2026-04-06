from __future__ import annotations

import argparse


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--status", required=True)
    parser.add_argument("--summary", required=True)
    parser.add_argument("--artifacts", required=True)
    parser.add_argument("--next", dest="next_step", required=True)
    args = parser.parse_args()

    print("=== HARNESS RESULT ===")
    print(f"STATUS   : {args.status}")
    print(f"SUMMARY  : {args.summary}")
    print(f"ARTIFACTS: {args.artifacts}")
    print(f"NEXT     : {args.next_step}")
    print("======================")


if __name__ == "__main__":
    main()
