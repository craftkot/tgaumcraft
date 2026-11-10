#!/usr/bin/env python3
"""Regression tests for the actual ScanRuneParticle client-log format."""
from scan_rune_log_parser import parse_scan_rune_log
import subprocess
import sys

log = """[Render thread/INFO]: TC_SMOKE_SCAN_RUNE_CREATED=YES face=3 lifetime=45 x=0.5 y=151.75 z=4.5 localX=0.12024379 localY=-0.012681067 localZ=-0.51
[Render thread/INFO]: TC_SMOKE_SCAN_RUNE_RENDERED=YES face=2 alpha=0.33333334 size=0.33565626 cameraYaw=-180.0 lookX=-1.7484555E-7 lookY=0.0 lookZ=-1.0 worldX=0.54189284 worldY=151.74066 worldZ=5.01 frontDot=0.51 quadFacingDot=0.51
[Render thread/INFO]: TC_SMOKE_SCAN_RUNE_EXPIRED=YES face=2 age=45 lifetime=45 drift=0.0
"""

created, rendered, expired = parse_scan_rune_log(log)
assert len(created) == 1
assert len(rendered) == 1
assert len(expired) == 1
assert created[0] == ("3", "45", "0.5", "151.75", "4.5", "0.12024379", "-0.012681067", "-0.51")
assert rendered[0][0] == "2"
assert rendered[0][3] == "-180.0"
assert rendered[0][4] == "-1.7484555E-7"
assert rendered[0][10] == "0.51"
assert rendered[0][11] == "0.51"
assert expired[0] == ("2", "45", "45", "0.0")
assert parse_scan_rune_log("TC_SMOKE_SCAN_RUNE_CREATED=YES face=x lifetime=45") == ([], [], [])
print("SCAN_RUNE_LOG_PARSER=PASS assertions=11")

# The real-client fixture must clear generated terrain from both sight lines.
# Otherwise the particle log can be internally consistent while proving a scan
# of a tree/foliage block instead of the intended BETA26 gold target.
generated = subprocess.run(
    [sys.executable, "tools/client_scan_rune_smoke.py"],
    check=True, capture_output=True, text=True
).stdout
clear_volume = "send_chat '/fill -4 150 -4 4 155 10 minecraft:air'"
place_target = "send_chat '/setblock 0 151 4 minecraft:gold_block'"
fixture_checks = [
    clear_volume in generated,
    generated.index(clear_volume) < generated.index(place_target),
    "send_chat '/execute if block 0 151 4 minecraft:gold_block run say RUNE_TARGET_GOLD_OK'" in generated,
    "send_chat '/execute if block 0 151 0 minecraft:air run say RUNE_ORIGIN_VIEW_CLEAR'" in generated,
    "grep -Fq 'RUNE_ORIGIN_VIEW_CLEAR' client-smoke/client.log" in generated,
    "send_chat '/execute if block 0 151 7 minecraft:air run say RUNE_OPPOSITE_VIEW_CLEAR'" in generated,
    "grep -Fq 'RUNE_OPPOSITE_VIEW_CLEAR' client-smoke/client.log" in generated,
    "abs(z-4.5)>0.0001" in generated,
]
assert all(fixture_checks), f"scan-rune fixture checks failed: {fixture_checks}"
print("SCAN_RUNE_SMOKE_FIXTURE=PASS assertions=8")
