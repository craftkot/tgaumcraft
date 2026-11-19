#!/usr/bin/env python3
"""Fail unless a real Minecraft client rendered both BETA26 jar liquids and the empty control."""
from __future__ import annotations

from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: assert_client_jar_liquid_smoke.py CLIENT_LOG SCREENSHOT_PNG")

log_path = Path(sys.argv[1])
screenshot = Path(sys.argv[2])
log = log_path.read_text(encoding="utf-8", errors="replace")

required = (
    "JAR_LIQUID_AER_FIXTURE_READY",
    "JAR_LIQUID_IGNIS_FIXTURE_READY",
    "JAR_LIQUID_EMPTY_FIXTURE_READY",
    "TC_SMOKE_JAR_LIQUID_RENDERED=YES pos=249,150,251 amount=125 aspect=aer rgb=FFFF7E topY=0.3750 texture=thaumcraft:block/animatedglow",
    "TC_SMOKE_JAR_LIQUID_RENDERED=YES pos=250,150,251 amount=250 aspect=ignis rgb=FF5A01 topY=0.6875 texture=thaumcraft:block/animatedglow",
    "TC_SMOKE_JAR_LIQUID_EMPTY_SKIPPED=YES pos=251,150,251 amount=0",
    "JAR_LIQUID_REAL_CLIENT_RENDERER_AND_SCREENSHOT=PASS",
)
for marker in required:
    if marker not in log:
        raise SystemExit(f"missing real-client jar liquid marker: {marker}")
if "TC_SMOKE_JAR_LIQUID_RENDERED=YES pos=251,150,251" in log:
    raise SystemExit("empty jar incorrectly emitted liquid geometry")
if "Missing BETA26 essentia jar liquid atlas sprite" in log:
    raise SystemExit("animatedglow sprite was missing from the block atlas")
if not screenshot.is_file() or screenshot.stat().st_size < 1024:
    raise SystemExit(f"real-client screenshot missing or implausibly small: {screenshot}")
if screenshot.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
    raise SystemExit("real-client screenshot is not a PNG")

print("BETA26 jar liquid real-client markers and screenshot: PASS")
