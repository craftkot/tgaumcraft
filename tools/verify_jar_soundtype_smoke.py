#!/usr/bin/env python3
"""Require real-client coverage for all original BETA26 jar SoundType hooks."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SMOKE = (ROOT / "tools/client_jar_sound_smoke.py").read_text(encoding="utf-8")
BLOCK = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/block/EssentiaJarBlock.java").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/closure-jar-sound-client.yml").read_text(encoding="utf-8")

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
assert re.search(r"xdotool mousedown 1\s+sleep 0\.30\s+xdotool mouseup 1", hit_section), (
    "Hit probe must hold long enough for the tick-driven block-breaking sound loop"
)
assert "100a-jar-hit-aim.png" in hit_section, "Hit probe must preserve a pre-input aim screenshot"
assert "JAR_PLACE_ITEM_READY" in SMOKE and "thaumcraft:jar_normal" in SMOKE
assert "JAR_HIT_BLOCK_RETAINED" in SMOKE
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

print("BETA26 jar SoundType client contract: place, hit, step and fall each have real interaction, server-state guard and original-PCM matcher: PASS")
