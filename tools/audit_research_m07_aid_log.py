#!/usr/bin/env python3
"""Fail-closed M07 aid gameplay evidence audit from real Minecraft server log.

Only checks markers emitted AFTER server transitions in ResearchTableBlockEntity.
It cannot replace actual Minecraft execution or prove every card in BETA26.
"""
import argparse
import pathlib
import re
import sys

STAGES = ("THEORY_CREATED", "CARD_DRAWN", "CARD_ACTIVATED")
BRANCHES = ("ALCHEMY", "INFUSION")


def validate(log: str, branch: str) -> list[int]:
    if branch not in BRANCHES:
        raise ValueError(f"Unsupported M07 branch: {branch}")
    needed = [f"M07_{branch}_AID_{stage}" for stage in STAGES]
    # Match a complete audit entry, not a similarly named marker or a string
    # embedded in a test's missing-marker error message.
    seen = [m.group(1) for line in log.splitlines()
            if (m := re.search(r"(?:^|\s)TC_SMOKE_([A-Z0-9_]+)=YES(?:\s|$)", line))]
    positions = []
    cursor = -1
    for key in needed:
        for i in range(cursor + 1, len(seen)):
            if seen[i] == key:
                positions.append(i)
                cursor = i
                break
        else:
            raise ValueError(f"No complete, ordered server evidence: TC_SMOKE_{key}=YES")
    return positions


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=pathlib.Path, help="real Minecraft server/client log")
    parser.add_argument("--branch", choices=BRANCHES, default="ALCHEMY")
    args = parser.parse_args()
    try:
        log = args.log.read_text(encoding="utf-8", errors="replace")
        indices = validate(log, args.branch)
    except (OSError, ValueError) as exc:
        print(f"M07_{args.branch}_GAMEPLAY_UNVERIFIED: {exc}", file=sys.stderr)
        return 1
    print(f"M07_{args.branch}_GAMEPLAY_MARKERS_PASS indices={indices}")
    print("NOTE: log-only evidence; verify actual screenshot, player items/XP and world save separately")
    return 0


if __name__ == "__main__":
    sys.exit(main())
