#!/usr/bin/env python3
"""Verify BETA26 Nitor client cadence, MapColor, atlas frames and render layers."""
from pathlib import Path
import math
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: verify_nitor_client_tick.py <client.log>")
log = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")

def one(pattern: str, label: str):
    matches = re.findall(pattern, log)
    if len(matches) != 1:
        raise SystemExit(f"{label}: expected exactly one marker, found {len(matches)}")
    return matches[0]

tick = one(
    r"TC_SMOKE_NITOR_CLIENT_TICK=YES ticks=(\d+) flames=(\d+) cores=(\d+)",
    "Nitor client cadence",
)
if tuple(map(int, tick)) != (20, 20, 2):
    raise SystemExit(f"Nitor cadence mismatch: {tick}")

color = one(
    r"TC_SMOKE_NITOR_PARTICLE_COLOR=YES dyeRgb=(\d+) mapColor=(\d+) particleColor=(\d+)",
    "Nitor MapColor source",
)
dye_rgb, map_rgb, particle_rgb = map(int, color)
if not (0 <= dye_rgb <= 0xFFFFFF and 0 <= map_rgb <= 0xFFFFFF
        and 0 <= particle_rgb <= 0xFFFFFF):
    raise SystemExit(f"Nitor RGB outside 24-bit range: {color}")
if map_rgb == dye_rgb or particle_rgb != map_rgb:
    raise SystemExit(
        f"Nitor flame did not use block MapColor: dye={dye_rgb:#08x}, "
        f"map={map_rgb:#08x}, particle={particle_rgb:#08x}"
    )
if "TC_SMOKE_NITOR_PARTICLE_COLOR_SOURCE=FAIL" in log:
    raise SystemExit("Nitor flame particle could not resolve its source block")

flame = one(
    r"TC_SMOKE_NITOR_FLAME_CREATED=YES id=1 frameStart=(\d+) frameCount=(\d+) "
    r"lifetime=(\d+) startScale=([0-9.eE+-]+) rgb=(\d+)",
    "Nitor flame creation",
)
flame_start, flame_count, flame_lifetime = map(int, flame[:3])
start_scale = float(flame[3])
flame_rgb = int(flame[4])
if (flame_start, flame_count) != (264, 8):
    raise SystemExit(f"Nitor flame atlas sequence mismatch: {flame[:2]}")
if not 10 <= flame_lifetime <= 14 or not 3.0 <= start_scale <= 4.0:
    raise SystemExit(f"Nitor flame lifetime/scale outside BETA26 range: {flame}")
if flame_rgb != particle_rgb:
    raise SystemExit(f"Nitor flame particle RGB differs from block MapColor: {flame_rgb:#08x}")

core = one(
    r"TC_SMOKE_NITOR_CORE_CREATED=YES id=1 frame=(\d+) lifetime=(\d+) "
    r"middleScale=([0-9.eE+-]+)",
    "Nitor core creation",
)
if (int(core[0]), int(core[1])) != (457, 10):
    raise SystemExit(f"Nitor core source frame/lifetime mismatch: {core}")
if not math.isfinite(float(core[2])):
    raise SystemExit(f"Nitor core middle scale is not finite: {core}")

flame_render = one(
    r"TC_SMOKE_NITOR_FLAME_RENDERED=YES id=1 frame=(\d+) age=(\d+)",
    "Nitor flame render",
)
core_render = one(
    r"TC_SMOKE_NITOR_CORE_RENDERED=YES id=1 frame=(\d+) age=(\d+)",
    "Nitor core render",
)
if tuple(map(int, core_render)) != (457, 1):
    raise SystemExit(
        f"Nitor first visible core frame does not match FXGeneric age-one phase: {core_render}"
    )
if (int(flame_render[0]), int(flame_render[1])) != (265, 1):
    raise SystemExit(
        f"Nitor first visible flame frame does not match FXGeneric age-one phase: {flame_render}"
    )

frame_matches = re.findall(
    r"TC_SMOKE_NITOR_FLAME_FRAME=YES frame=(\d+)", log
)
frames = {int(frame) for frame in frame_matches}
if len(frame_matches) != 8 or frames != set(range(264, 272)):
    raise SystemExit(
        f"Nitor flame did not render all eight looping atlas frames across visible instances: "
        f"{sorted(frames)} (markers={len(frame_matches)})"
    )

flame_expired = one(
    r"TC_SMOKE_NITOR_FLAME_EXPIRED=YES id=1 age=(\d+) lifetime=(\d+)",
    "Nitor flame expiry",
)
core_expired = one(
    r"TC_SMOKE_NITOR_CORE_EXPIRED=YES id=1 age=(\d+) lifetime=(\d+)",
    "Nitor core expiry",
)
if tuple(map(int, flame_expired)) != (flame_lifetime, flame_lifetime):
    raise SystemExit(f"Nitor flame lifetime did not expire at maxAge: {flame_expired}")
if tuple(map(int, core_expired)) != (10, 10):
    raise SystemExit(f"Nitor core lifetime did not expire at maxAge: {core_expired}")

blend = one(
    r"TC_SMOKE_NITOR_ADDITIVE_BLEND_BEGIN=YES src=(\d+) dst=(\d+)",
    "Nitor additive render layer",
)
if tuple(map(int, blend)) != (770, 1):
    raise SystemExit(f"Nitor flame blend is not BETA26 layer 0 additive: {blend}")

scale_markers = re.findall(
    r"TC_SMOKE_NITOR_FLAME_SCALE=YES id=1 age=(\d+) scale=([0-9.eE+-]+)", log
)
scales = {int(age): float(scale) for age, scale in scale_markers}
if 1 not in scales or flame_lifetime not in scales:
    raise SystemExit(f"Nitor flame keyframe scale markers incomplete: {scales}")
expected_first = start_scale + (0.05 - start_scale) / flame_lifetime
if not math.isclose(scales[1], expected_first, rel_tol=0.0, abs_tol=1e-4):
    raise SystemExit(f"Nitor flame first scale keyframe mismatch: {scales[1]} != {expected_first}")
if not math.isclose(scales[flame_lifetime], 0.05, rel_tol=0.0, abs_tol=1e-4):
    raise SystemExit(f"Nitor flame final scale keyframe mismatch: {scales[flame_lifetime]}")

core_scale_markers = re.findall(
    r"TC_SMOKE_NITOR_CORE_SCALE=YES id=1 age=(\d+) scale=([0-9.eE+-]+)", log
)
core_scales = {int(age): float(scale) for age, scale in core_scale_markers}
if 1 not in core_scales or 10 not in core_scales:
    raise SystemExit(f"Nitor core keyframe scale markers incomplete: {core_scales}")
expected_core_first = 1.0 + (float(core[2]) - 1.0) * 0.2
if not math.isclose(core_scales[1], expected_core_first, rel_tol=0.0, abs_tol=1e-4):
    raise SystemExit(
        f"Nitor core first scale keyframe mismatch: {core_scales[1]} != {expected_core_first}"
    )
if not math.isclose(core_scales[10], 1.0, rel_tol=0.0, abs_tol=1e-4):
    raise SystemExit(f"Nitor core final scale keyframe mismatch: {core_scales[10]}")

if "TC_SMOKE_NITOR_ADDITIVE_BLEND_BEGIN=YES" not in log:
    raise SystemExit("Nitor additive render layer was not entered")

print(
    "NITOR_REAL_CLIENT_PARTICLES=PASS "
    f"flameFrames=264..271 coreFrame=457 "
    f"mapColor={map_rgb:#08x} additive=770/1"
)
