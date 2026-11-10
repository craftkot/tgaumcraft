#!/usr/bin/env python3
"""Regression tests for the actual ScanRuneParticle client-log format."""
from scan_rune_log_parser import parse_scan_rune_log

log = """[Render thread/INFO]: TC_SMOKE_SCAN_RUNE_CREATED=YES face=3 lifetime=45 x=0.5 y=151.75 z=4.5 localX=0.12024379 localY=-0.012681067 localZ=-0.51
[Render thread/INFO]: TC_SMOKE_SCAN_RUNE_RENDERED=YES face=2 alpha=0.33333334 size=0.33565626 cameraYaw=-180.0 lookX=-1.7484555E-7 lookY=0.0 lookZ=-1.0 worldX=0.54189284 worldY=151.74066 worldZ=5.01 frontDot=0.51
[Render thread/INFO]: TC_SMOKE_SCAN_RUNE_EXPIRED=YES face=2 age=45 lifetime=45 drift=0.0
"""

created, rendered, expired = parse_scan_rune_log(log)
assert len(created) == len(rendered) == len(expired) == 1
assert created[0] == ("3", "45", "0.5", "151.75", "4.5", "0.12024379", "-0.012681067", "-0.51")
assert rendered[0][0] == "2" and rendered[0][3] == "-180.0"
assert rendered[0][4] == "-1.7484555E-7" and rendered[0][-1] == "0.51"
assert expired[0] == ("2", "45", "45", "0.0")
assert parse_scan_rune_log("TC_SMOKE_SCAN_RUNE_CREATED=YES face=x lifetime=45") == ([], [], [])
print("SCAN_RUNE_LOG_PARSER=PASS assertions=7")
