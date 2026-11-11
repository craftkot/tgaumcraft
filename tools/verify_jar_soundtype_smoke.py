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
        "place-baseline.pcm", "place-event.pcm",
        "JAR_PLACE_BLOCK_PRESENT", "JAR_PLACE_ORIGINAL_PCM=PASS",
        "xdotool click 3",
    ),
    "hit": (
        "hit-baseline.pcm", "hit-event.pcm",
        "JAR_HIT_BLOCK_RETAINED", "JAR_HIT_ORIGINAL_PCM=PASS",
        "xdotool mousedown 1",
    ),
    "step": (
        "step-baseline.pcm", "step-event.pcm",
        "JAR_STEP_MOVED_ON_ROW", "JAR_STEP_ORIGINAL_PCM=PASS",
        "xdotool keydown w",
    ),
    "fall": (
        "fall-baseline.pcm", "fall-event.pcm",
        "JAR_FALL_LANDED_ON_JAR", "JAR_FALL_ORIGINAL_PCM=PASS",
        "send_chat '/tp @p 250.5 155 253.5 0 90'",
    ),
}

positions = []
for name, (baseline, event, state_marker, pass_marker, action) in scenarios.items():
    assert f"client-smoke/{baseline}" in SMOKE, name
    assert f"client-smoke/{event}" in SMOKE, name
    assert f"client-smoke/{baseline} client-smoke/{event} src/main/resources/assets/thaumcraft/sounds" in SMOKE, name
    assert state_marker in SMOKE, name
    assert pass_marker in SMOKE, name
    assert action in SMOKE, name
    positions.append(SMOKE.index(pass_marker))

assert positions == sorted(positions), "SoundType scenarios must run place -> hit -> step -> fall"
assert "JAR_PLACE_ITEM_READY" in SMOKE and "thaumcraft:jar_normal" in SMOKE
assert "JAR_HIT_BLOCK_RETAINED" in SMOKE
assert "JAR_STEP_STANDING_READY" in SMOKE and "JAR_STEP_MOVED_ON_ROW" in SMOKE
assert "JAR_FALL_LANDED_ON_JAR" in SMOKE
assert "JAR_SOUNDTYPE_PLACE_STEP_HIT_FALL=PASS" in SMOKE
assert WORKFLOW.count("tools/verify_jar_soundtype_smoke.py") >= 2, "PR path filter and job must run this guard"
assert "pull_request:" in WORKFLOW and "  push:" not in WORKFLOW, "expensive real-client smoke must run once per PR update, not again on the same branch push"
assert "apt-mirrors.txt" in WORKFLOW and "archive.ubuntu.com" in WORKFLOW, "runner must bypass the failing regional Azure apt mirror"
assert "Acquire::Retries=1" in WORKFLOW and "timeout-minutes: 45" in WORKFLOW, "dependency installation and client runtime need bounded, sufficient time"
assert "python tools/verify_jar_soundtype_smoke.py" in WORKFLOW

print("BETA26 jar SoundType client contract: place, hit, step and fall each have real interaction, server-state guard and original-PCM matcher: PASS")
