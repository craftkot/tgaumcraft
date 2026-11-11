#!/usr/bin/env python3
"""Verify the real Minecraft client's opt-in BETA26 Nitor tick marker."""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: verify_nitor_client_tick.py <client.log>")
log = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
matches = re.findall(
    r"TC_SMOKE_NITOR_CLIENT_TICK=YES ticks=(\d+) flames=(\d+) cores=(\d+)",
    log,
)
if len(matches) != 1:
    raise SystemExit(f"NITOR_REAL_CLIENT_TICK=FAIL expected one 20/20/2 marker, got {len(matches)}")
actual = tuple(int(value) for value in matches[0])
if actual != (20, 20, 2):
    raise SystemExit(f"NITOR_REAL_CLIENT_TICK=FAIL expected (20, 20, 2), got {actual}")
if "TC_SMOKE_NITOR_CLIENT_TICK=FAIL" in log:
    raise SystemExit("NITOR_REAL_CLIENT_TICK=FAIL client emitted explicit failure marker")
print("NITOR_REAL_CLIENT_TICK=PASS ticks=20 flames=20 cores=2")
