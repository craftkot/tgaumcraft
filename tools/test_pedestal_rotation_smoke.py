#!/usr/bin/env python3
"""Static and shell-syntax contract for the real pedestal rotation client smoke."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]

sys.path.insert(0, str(ROOT / "tools"))
from pedestal_rotation_evidence import validate_samples
renderer = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/client/PedestalItemRenderer.java").read_text()
helper = (ROOT / "src/main/java/dev/ehtid/thaumcraft/port/core/visual/LegacyPedestalRotationAngle.java").read_text()
driver = (ROOT / "tools/client_pedestal_rotation_smoke.py").read_text()
workflow = (ROOT / ".github/workflows/closure-pedestal-rotation-client.yml").read_text()
boot = (ROOT / "tools/client_smoke.sh").read_text()

def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)

# The previous client failure compared the whole world-setup interval (271 degrees)
# against a screenshot-sized bound. The final adjacent sample is the relevant step.
wrapped = validate_samples([(359, 0.5, 359.5), (369, 0.6, 9.6)])
require(abs(wrapped["last_step_degrees"] - 10.1) < 0.02,
        "angle validation must handle 359 -> 0 wraparound")
long_session = validate_samples([
    (1569, 0.08, 129.08),
    (1832, 0.20, 32.20),
    (1840, 0.76, 40.76),
])
require(long_session["tick_span"] == 271,
        "long world setup must not be mistaken for one screenshot step")
require(abs(long_session["last_step_degrees"] - 8.56) < 0.02,
        "the final adjacent render step must be validated independently")
def reject_samples(samples, message):
    try:
        validate_samples(samples)
    except ValueError:
        return
    raise AssertionError(message)

reject_samples([(10, 0.2, 10.2), (20, 0.2, 11.0)],
               "a mismatched final render angle must fail")
reject_samples([(10, 0.2, 10.2), (10, 0.3, 10.3)],
               "non-advancing camera ticks must fail")
reject_samples([(10, 1.0, 11.0), (20, 0.2, 20.2)],
               "partial ticks outside [0,1) must fail")

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
require("from pedestal_rotation_evidence import validate_samples" in driver,
        "runtime evidence must use the tested wrap-safe validator")
require("metrics = validate_samples(samples)" in driver,
        "real-client marker parser must use adjacent-sample validation")
require("last_step_degrees" in driver,
        "real-client report must expose the final adjacent render step")
require("angle_delta = (samples[-1][2] - samples[0][2])" not in driver,
        "whole-session angular displacement must not be bounded as a screenshot step")
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
