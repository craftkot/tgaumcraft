#!/usr/bin/env python3
"""Static and shell-syntax contract for the real pedestal rotation client smoke."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
renderer = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/client/PedestalItemRenderer.java").read_text()
helper = (ROOT / "src/main/java/dev/ehtid/thaumcraft/port/core/visual/LegacyPedestalRotationAngle.java").read_text()
driver = (ROOT / "tools/client_pedestal_rotation_smoke.py").read_text()
workflow = (ROOT / ".github/workflows/closure-pedestal-rotation-client.yml").read_text()
boot = (ROOT / "tools/client_smoke.sh").read_text()

def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)

require("LegacyPedestalRotationAngle.degrees(camera.tickCount, partialTick)" in renderer,
        "renderer must use render-view camera ticks")
require("Axis.YP.rotationDegrees(age)" in renderer,
        "computed angle must drive the displayed item rotation")
require('Boolean.getBoolean("thaumcraft.pedestalRotationDebug")' in renderer,
        "runtime marker must remain opt-in")
require("TC_PEDESTAL_RENDER_ROTATION cameraTick=" in renderer,
        "renderer marker missing")
require(renderer.index("renderStatic") < renderer.index("TC_PEDESTAL_RENDER_ROTATION"),
        "marker must be emitted only after the actual item render call")
require("getGameTime()" not in renderer,
        "world time must not replace the BETA26 render-view clock")
require("return (renderViewEntityTicks + partialTick) % 360.0F;" in helper,
        "core angle formula changed")

anchor = "  # The initial world is already creative + peaceful, as established"
require(boot.count(anchor) == 1, "shared client boot anchor changed")
require("container.0 with minecraft:diamond_sword" in driver,
        "smoke must place a real item in the pedestal container")
require("TC_PEDESTAL_ITEM_PRESENT" in driver,
        "server-side inventory predicate marker missing")
require("pedestal-empty" in driver and "pedestal-rotation-first" in driver
        and "pedestal-rotation-later" in driver,
        "empty, first and later screenshots are required")
require("pixel_difference 'pedestal-empty' 'pedestal-rotation-first' 30" in driver,
        "empty-pedestal negative control missing")
require("pixel_difference 'pedestal-rotation-first' 'pedestal-rotation-later' 8" in driver,
        "visible rotation pixel-difference gate missing")
require("TC_PEDESTAL_RENDER_ROTATION" in driver and "angle_delta" in driver,
        "runtime camera-tick/angle evidence is not parsed")
require("thaumcraft.pedestalRotationDebug=true" in workflow,
        "client workflow must enable the opt-in renderer marker")
require("gradle runClient" in boot,
        "smoke must reuse the real integrated-client boot")

generated = subprocess.run(
    [sys.executable, str(ROOT / "tools/client_pedestal_rotation_smoke.py")],
    cwd=ROOT, text=True, capture_output=True, check=True,
)
subprocess.run(["bash", "-n"], input=generated.stdout, text=True, check=True)
require("PEDESTAL_ROTATION_REAL_CLIENT=PASS" in generated.stdout,
        "generated client script does not contain its final acceptance gate")
print("PEDESTAL_ROTATION_SMOKE_CONTRACT=PASS")
