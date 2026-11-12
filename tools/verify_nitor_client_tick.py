#!/usr/bin/env python3
"""Verify the real Minecraft client's BETA26 Nitor cadence and map-color particle."""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: verify_nitor_client_tick.py <client.log>")
log = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")

tick_matches = re.findall(
    r"TC_SMOKE_NITOR_CLIENT_TICK=YES ticks=(\d+) flames=(\d+) cores=(\d+)",
    log,
)
if len(tick_matches) != 1:
    raise SystemExit(
        f"NITOR_REAL_CLIENT_TICK=FAIL expected one 20/20/2 marker, got {len(tick_matches)}"
    )
actual = tuple(int(value) for value in tick_matches[0])
if actual != (20, 20, 2):
    raise SystemExit(f"NITOR_REAL_CLIENT_TICK=FAIL expected (20, 20, 2), got {actual}")

color_matches = re.findall(
    r"TC_SMOKE_NITOR_PARTICLE_COLOR=YES dyeRgb=(\d+) mapColor=(\d+) particleColor=(\d+)",
    log,
)
if len(color_matches) != 1:
    raise SystemExit(
        "NITOR_REAL_CLIENT_COLOR=FAIL expected one dye/map/particle color marker, "
        f"got {len(color_matches)}"
    )
dye_rgb, map_color, particle_color = (int(value) for value in color_matches[0])
if any(value < 0 or value > 0xFFFFFF for value in (dye_rgb, map_color, particle_color)):
    raise SystemExit("NITOR_REAL_CLIENT_COLOR=FAIL RGB value outside 24-bit range")
if map_color != particle_color:
    raise SystemExit(
        "NITOR_REAL_CLIENT_COLOR=FAIL particle RGB does not match BlockState map color: "
        f"map={map_color:#08x} particle={particle_color:#08x}"
    )
if map_color == dye_rgb:
    raise SystemExit(
        "NITOR_REAL_CLIENT_COLOR=FAIL particle still uses the legacy dye/firework RGB"
    )
if "TC_SMOKE_NITOR_CLIENT_TICK=FAIL" in log or "TC_SMOKE_NITOR_PARTICLE_COLOR=FAIL" in log:
    raise SystemExit("NITOR_REAL_CLIENT=FAIL client emitted an explicit failure marker")

print(
    "NITOR_REAL_CLIENT_TICK=PASS ticks=20 flames=20 cores=2 "
    f"mapColor={map_color:#08x} particleColor={particle_color:#08x} dyeRgb={dye_rgb:#08x}"
)
