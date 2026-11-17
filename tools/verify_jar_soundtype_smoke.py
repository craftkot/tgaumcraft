#!/usr/bin/env python3
"""Require real-client coverage for all original BETA26 jar SoundType hooks."""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SMOKE = (ROOT / "tools/client_jar_sound_smoke.py").read_text(encoding="utf-8")
BLOCK = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/block/EssentiaJarBlock.java").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/closure-jar-sound-client.yml").read_text(encoding="utf-8")
OVERLAY = (ROOT / "tools/verify_jar_damage_overlay.py").read_text(encoding="utf-8")
INIT_SCRIPT = (ROOT / "tools/gradle-plugin-portal-fallback.init.gradle").read_text(encoding="utf-8")
CLIENT_BOOT = (ROOT / "tools/client_smoke.sh").read_text(encoding="utf-8")

assert "gradle.beforeSettings" in INIT_SCRIPT
assert "settings.pluginManagement.repositories.mavenCentral()" in INIT_SCRIPT
assert 'gradle --init-script tools/gradle-plugin-portal-fallback.init.gradle runClient --no-daemon --console=plain' in SMOKE
assert CLIENT_BOOT.count("gradle runClient --no-daemon --console=plain") == 1
assert "gradle runClient --no-daemon --console=plain" not in SMOKE.split("gradle --init-script", 1)[1].split("runClient", 1)[0]

for hook in ("Break", "Step", "Place", "Hit", "Fall"):
    pattern = r"@Override\s+public SoundEvent get" + hook + r"Sound\(\)\s*\{\s*return ThaumcraftSounds\.JAR\.get\(\);\s*\}"
    assert re.search(pattern, BLOCK), f"SoundType {hook} hook is not wired to original JAR event"

scenarios = {
    "place": (
        "place-baseline.pcm", "place-event.pcm", "place-event-window.pcm", 26400, 120000,
        "JAR_PLACE_BLOCK_PRESENT", "JAR_PLACE_ORIGINAL_PCM=PASS", "xdotool click 3",
    ),
    "hit": (
        "hit-baseline.pcm", "hit-event.pcm", "hit-event-window.pcm", 26400, 120000,
        "JAR_HIT_BLOCK_RETAINED", "JAR_HIT_ORIGINAL_PCM=PASS", "xdotool mousedown 1",
    ),
    "step": (
        "step-baseline.pcm", "step-event.pcm", "step-event-window.pcm", 16800, 120000,
        "JAR_STEP_MOVED_ON_ROW", "JAR_STEP_ORIGINAL_PCM=PASS", "xdotool keydown w",
    ),
    "fall": (
        "fall-baseline.pcm", "fall-event.pcm", "fall-event-window.pcm", 16800, 144000,
        "JAR_FALL_LANDED_ON_JAR", "JAR_FALL_ORIGINAL_PCM=PASS",
        "send_chat '/tp @p 250.5 155 253.5 0 90'",
    ),
}

if not all(token in SMOKE for token in (
    "crop_pcm_frames()",
    "bs=4 skip=",
    "actual_bytes != expected_bytes",
)):
    raise SystemExit("PCM action-window crop must validate complete stereo frames")
positions = []
for name, (baseline, event, window, first, last, state_marker, pass_marker, action) in scenarios.items():
    assert f"client-smoke/{baseline}" in SMOKE, name
    assert f"client-smoke/{event}" in SMOKE, name
    assert f"crop_pcm_frames client-smoke/{event} client-smoke/{window} {first} {last}" in SMOKE, name
    assert f"client-smoke/{baseline} client-smoke/{window} src/main/resources/assets/thaumcraft/sounds" in SMOKE, name
    baseline_pos = SMOKE.index(f"> client-smoke/{baseline}")
    stop_pos = SMOKE.rfind("send_chat '/stopsound @p'", 0, baseline_pos)
    previous_capture_end = SMOKE.rfind('wait "$capture_pid"', 0, baseline_pos)
    assert stop_pos > previous_capture_end, f"{name}: baseline must start after stopsound"
    assert state_marker in SMOKE, name
    assert pass_marker in SMOKE, name
    assert action in SMOKE, name
    positions.append(SMOKE.index(pass_marker))

assert positions == sorted(positions), "SoundType scenarios must run place -> hit -> step -> fall"
hit_section = SMOKE.split("# M43 HIT:", 1)[1].split("# Rebuild a five-block row", 1)[0]
press_pos = hit_section.index("xdotool mousedown 1")
# The same section has an earlier 0.80s no-hit control delay. Search for the
# fatigue-controlled hold only after the actual left-button press.
hold_pos = hit_section.index("sleep 0.80", press_pos)
capture_marker_pos = hit_section.index("JAR_HIT_CAPTURE_AFTER_0_80S=YES")
damage_frame_pos = hit_section.index("100b-jar-hit-damage-overlay.png")
release_pos = hit_section.index("xdotool mouseup 1")
assert press_pos < hold_pos < capture_marker_pos < damage_frame_pos < release_pos, (
    "Hit probe must capture the damage overlay after the complete 0.80s fatigue-controlled hold and before releasing the left button"
)
assert "100-pre-hit-viewport.png" in hit_section, "Hit probe must preserve the viewport frame used for crosshair detection"
assert "JAR_HIT_CROSSHAIR_DETECTED=YES" in hit_section, "Hit probe must detect the actual rendered crosshair"
assert "JAR_HIT_CROSSHAIR_CENTER=" in hit_section, "Hit probe must log the screenshot-derived crosshair"
assert 'python3 tools/client_crosshair_locator.py' in hit_section
assert 'xdotool mousemove --sync "$jar_hit_window_center_x" "$jar_hit_window_center_y"' in hit_section
assert 'xdotool mousemove --sync "$jar_hit_center_x" "$jar_hit_center_y"' not in hit_section
assert "JAR_HIT_FOCUS_CONFIRMED=YES" in hit_section
assert 'xdotool windowactivate --sync "$WIN"' in hit_section
assert 'xdotool windowfocus --sync "$WIN"' in hit_section
assert 'jar_hit_active_window="$(xdotool getactivewindow)"' in hit_section
assert hit_section.index("windowactivate --sync") < hit_section.index("JAR_HIT_FOCUS_CONFIRMED=YES") < hit_section.index("xdotool mousedown 1")
assert hit_section.index("xdotool key --clearmodifiers t") < hit_section.index("xdotool mousemove --sync") < hit_section.index("xdotool key --clearmodifiers Return") < hit_section.index("100a-jar-hit-aim.png")
assert "send_chat '/effect give @p minecraft:mining_fatigue 30 0 true'" in hit_section
assert "send_chat '/data get entity @p active_effects'" in hit_section
assert "minecraft:mining_fatigue" in hit_section
assert "JAR_HIT_CAPTURE_AFTER_0_80S=YES" in hit_section
assert "verify_jar_damage_overlay.py --diagnostic" not in hit_section
assert "python3 tools/verify_jar_damage_overlay.py \\" in hit_section
assert "hit-nohit-before-roi.png" in hit_section and "hit-nohit-after-roi.png" in hit_section
assert "hit-before-roi.png" in hit_section and "hit-after-roi.png" in hit_section
assert hit_section.index("verify_jar_damage_overlay.py") < hit_section.index("JAR_HIT_BLOCK_RETAINED") < hit_section.index("match_jar_audio_pcm.py") < hit_section.index("JAR_HIT_ORIGINAL_PCM=PASS") < hit_section.index("send_chat '/effect clear @p minecraft:mining_fatigue'"), (
    "Hit acceptance must fail closed on the real ROI crack overlay before checking retained block, original PCM and clearing the test-only effect"
)
assert "set -Eeuo pipefail" in CLIENT_BOOT, "The overlay verifier pipeline must propagate failures"
assert "compare -metric AE" not in hit_section
assert "JAR_HIT_CLIENT_DAMAGE_OVERLAY=PASS" in OVERLAY
assert "JAR_HIT_CLIENT_DAMAGE_OVERLAY_MISSING=YES" in OVERLAY
assert "JAR_HIT_CLIENT_DAMAGE_OVERLAY_DIAGNOSTIC " in OVERLAY
assert "no_hit_before: Path" in OVERLAY and "no_hit_after: Path" in OVERLAY
assert "MIN_HIT_PIXELS = 300" in OVERLAY and "MIN_OVER_BASELINE_PIXELS = 200" in OVERLAY
assert 'FUZZ = "6%"' in OVERLAY and "MIN_HIT_PIXELS = 300" in OVERLAY and "MIN_OVER_BASELINE_PIXELS = 200" in OVERLAY
assert "tools/verify_jar_damage_overlay.py" in WORKFLOW
assert WORKFLOW.count("tools/test_jar_damage_overlay.py") >= 2
assert "python tools/test_jar_damage_overlay.py" in WORKFLOW
assert "JAR_PLACE_ITEM_READY" in SMOKE and "thaumcraft:jar_normal" in SMOKE
assert "JAR_HIT_BLOCK_RETAINED" in SMOKE
assert "tools/client_crosshair_locator.py" in SMOKE
assert "JAR_HIT_CROSSHAIR_DETECTED=YES" in SMOKE
assert "JAR_HIT_CAPTURE_AFTER_0_30S" not in SMOKE
assert "JAR_HIT_POINTER_CENTER=" not in SMOKE
assert "JAR_STEP_STANDING_READY" in SMOKE and "JAR_STEP_MOVED_ON_ROW" in SMOKE
assert "JAR_FALL_LANDED_ON_JAR" in SMOKE
assert "JAR_SOUNDTYPE_PLACE_STEP_HIT_FALL=PASS" in SMOKE
assert WORKFLOW.count("tools/verify_jar_soundtype_smoke.py") >= 2, "PR path filter and job must run this guard"
assert "pull_request:" in WORKFLOW and "  push:" not in WORKFLOW, "expensive real-client smoke must run once per PR update, not again on the same branch push"
assert all(mirror in WORKFLOW for mirror in (
    "mirrors.edge.kernel.org/ubuntu/",
    "mirror.math.princeton.edu/pub/ubuntu/",
    "ubuntu.osuosl.org/ubuntu/",
    "archive.ubuntu.com/ubuntu/",
)), "runner must have independent Ubuntu mirror fallbacks"
assert "getent ahostsv4" in WORKFLOW and "curl --fail" in WORKFLOW, "mirror candidates must pass DNS and HTTPS preflight"
assert "APT::Update::Error-Mode=any" in WORKFLOW and "Acquire::Retries=0" in WORKFLOW, "failed apt indexes must not silently fall back to stale package lists"
assert "APT_MIRROR_SELECTION_FAILED=YES" in WORKFLOW, "mirror outage must fail fast with an explicit diagnostic"
assert WORKFLOW.index('if [ -z "$selected" ]') < WORKFLOW.index('apt-get "${apt_options[@]}" install'), "packages must not install before a mirror has updated successfully"
assert "timeout-minutes: 45" in WORKFLOW and "timeout 360s" in WORKFLOW, "dependency installation and client runtime need bounded, sufficient time"
assert "python tools/verify_jar_soundtype_smoke.py" in WORKFLOW

assert "send_chat '/kill @e[type=minecraft:item]'" in SMOKE
assert "send_chat '/data get entity @e[type=minecraft:item,sort=nearest,limit=1] Item'" in SMOKE
assert "send_chat '/data get entity @p Inventory'" in SMOKE
assert """grep -Eq '(^|[^[:alnum:]_])id:[[:space:]]*"thaumcraft:label"'""" in SMOKE
assert "JAR_LABEL_SERVER_BLANK_DROP_OR_PICKUP_PRESENT=PASS" in SMOKE
assert 'nbt={Item:{id:"thaumcraft:label"}}' not in SMOKE
assert "hotbar.* thaumcraft:label" not in SMOKE


brace_section = SMOKE.split("# M44:", 1)[1].split("# M43:", 1)[0]
for token in (
    "JAR_BRACE_FIXTURE_READY",
    "JAR_BRACE_ITEM_READY",
    "client-smoke/key-baseline.pcm",
    "client-smoke/key-event.pcm",
    "xdotool click 3",
    "JAR_BRACE_SERVER_APPLIED",
    "JAR_BRACE_ITEM_CONSUMED",
    "match_key_audio_pcm.py",
    "JAR_BRACE_KEY_INTERACTION_AND_ORIGINAL_PCM=PASS",
):
    assert token in brace_section, f"Real-client jar-brace KEY scenario missing {token}"
assert brace_section.index("JAR_BRACE_SERVER_APPLIED") < brace_section.index("JAR_BRACE_ITEM_CONSUMED")
assert "tools/verify_jar_brace_key_sound.py" in WORKFLOW
assert "tools/test_key_audio_match.py" in WORKFLOW
assert "src/neoforge/java/dev/ehtid/thaumcraft/item/JarBraceItem.java" in WORKFLOW
assert "src/main/resources/assets/thaumcraft/sounds/key.ogg" in WORKFLOW

subprocess.run([sys.executable, str(ROOT / "tools/test_client_crosshair_locator.py")], check=True)
print("BETA26 jar SoundType + brace KEY client contract: place, hit, step and fall each have real interaction, server-state guard and original-PCM matcher: PASS")
