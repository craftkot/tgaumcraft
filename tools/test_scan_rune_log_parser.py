#!/usr/bin/env python3
"""Regression tests for the actual ScanRuneParticle client-log format."""
from scan_rune_log_parser import (
    parse_scan_rune_log,
    parse_scan_rune_entity_targets,
    parse_thaumometer_sparkle_log,
    visible_rune_render_samples,
)
from pathlib import Path
import subprocess
import sys

log = """[Render thread/INFO]: TC_SMOKE_SCAN_RUNE_CREATED=YES face=3 lifetime=45 x=0.5 y=151.75 z=4.5 localX=0.12024379 localY=-0.012681067 localZ=-0.51
[Render thread/INFO]: TC_SMOKE_SCAN_RUNE_RENDERED=YES face=2 alpha=0.33333334 size=0.24956688 cameraYaw=-180.0 lookX=-1.7484555E-7 lookY=0.0 lookZ=-1.0 worldX=0.54189284 worldY=151.74066 worldZ=5.01 frontDot=0.51 quadFacingDot=0.51
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
visible = visible_rune_render_samples(rendered)
assert len(visible) == 1
assert visible[0][0] == 2
assert visible[0][2] == 0.24956688
negative_size = rendered[0][:2] + ("-0.24956688",) + rendered[0][3:]
negative_visible = visible_rune_render_samples([negative_size])
assert len(negative_visible) == 1
assert negative_visible[0][2] == -0.24956688
dim = rendered[0][:1] + ("0.1",) + rendered[0][2:]
zero_size = rendered[0][:2] + ("0.0",) + rendered[0][3:]
assert visible_rune_render_samples([rendered[0], negative_size, dim, zero_size]) == visible + negative_visible
assert expired[0] == ("2", "45", "45", "0.0")
assert parse_scan_rune_log("TC_SMOKE_SCAN_RUNE_CREATED=YES face=x lifetime=45") == ([], [], [])
entity_log = "[Render thread/INFO]: TC_SMOKE_SCAN_RUNE_ENTITY_TARGET=YES type=minecraft:cow height=1.4 units=21 x=0.5 y=151.0 eyeHeight=1.3 z=4.5"
entity_targets = parse_scan_rune_entity_targets(entity_log)
assert entity_targets == [("minecraft:cow", "1.4", "21", "0.5", "151.0", "1.3", "4.5")]
assert parse_scan_rune_entity_targets("TC_SMOKE_SCAN_RUNE_ENTITY_TARGET=YES type= height=bad") == []
print("SCAN_RUNE_LOG_PARSER=PASS assertions=17")
print("SCAN_RUNE_ENTITY_TARGET_PARSER=PASS assertions=2")

sparkle_log = """TC_SMOKE_THAUMOMETER_SPARKLE_CREATED=YES id=1 firstFrame=320 lifetime=18 delay=9 alphaKeys=7 red=16 green=165 blue=239 scale=0.4 moonPhase=4 windRoll=9 windX=-0.000032 windZ=-0.000038
TC_SMOKE_THAUMOMETER_SPARKLE_RENDERED=YES id=1 age=1 frame=321 alpha=0.2 scale=0.42
TC_SMOKE_THAUMOMETER_SPARKLE_EXPIRED=YES id=1 age=18 lifetime=18
TC_SMOKE_THAUMOMETER_SPARKLE_ADDITIVE_BLEND_BEGIN=YES src=770 dst=1
"""
sparkle_created, sparkle_rendered, sparkle_expired, sparkle_blend = parse_thaumometer_sparkle_log(sparkle_log)
assert sparkle_created == [("1", "320", "18", "9", "7", "16", "165", "239", "0.4", "4", "9", "-0.000032", "-0.000038")]
assert sparkle_rendered == [("1", "1", "321", "0.2", "0.42")]
assert sparkle_expired == [("1", "18", "18")]
assert sparkle_blend == [("770", "1")]
assert parse_thaumometer_sparkle_log("TC_SMOKE_THAUMOMETER_SPARKLE_CREATED=YES id=x") == ([], [], [], [])
print("THAUMOMETER_SPARKLE_LOG_PARSER=PASS assertions=5")

sparkle_test = subprocess.run(
    ["bash", "tools/test_thaumometer_sparkle.sh"],
    check=True, capture_output=True, text=True
)
assert "BETA26 Thaumometer FXGeneric sparkle: OK" in sparkle_test.stdout
print(sparkle_test.stdout.strip())

idle_source = Path("src/neoforge/java/dev/ehtid/thaumcraft/scanning/ThaumometerIdleHighlight.java").read_text(encoding="utf-8")
particle_source = Path("src/neoforge/java/dev/ehtid/thaumcraft/client/ThaumometerSparkleParticle.java").read_text(encoding="utf-8")
registration_source = Path("src/neoforge/java/dev/ehtid/thaumcraft/registry/ThaumometerSparkleParticleRegistration.java").read_text(encoding="utf-8")
client_provider_source = Path("src/neoforge/java/dev/ehtid/thaumcraft/client/ThaumometerSparkleParticleClientEvents.java").read_text(encoding="utf-8")
particle_json = Path("src/main/resources/assets/thaumcraft/particles/thaumometer_sparkle.json").read_text(encoding="utf-8")
source_checks = [
    "DustParticleOptions" not in idle_source,
    "ThaumometerSparkleParticleRegistration.TYPE" in idle_source,
    "LegacyThaumometerSparkleRules.encodeColorVariant" in idle_source,
    "LegacyThaumometerSparkleRules.encodeScale" in idle_source,
    "LegacyThaumometerSparkleRules.encodeDelay" in idle_source,
    "RenderSystem.blendFunc(770, 1)" in particle_source,
    "LegacyThaumometerSparkleRules.FRAME_COUNT" in particle_source,
    "LegacyThaumometerSparkleRules.alphaAt" in particle_source,
    "LegacyThaumometerSparkleRules.wind" in particle_source,
    "THAUMOMETER_SPARKLE_ADDITIVE_BLEND_BEGIN" in particle_source,
    "Registries.PARTICLE_TYPE" in registration_source,
    "event.registerSpriteSet(type, ThaumometerSparkleParticle.Provider::new)" in client_provider_source,
    '"thaumcraft:particles"' in particle_json,
]
assert all(source_checks), f"Thaumometer sparkle source wiring failed: {source_checks}"
print(f"THAUMOMETER_SPARKLE_SOURCE_WIRING=PASS assertions={len(source_checks)}")

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
    "visible_rendered = visible_rune_render_samples(rendered)" in generated,
    "values[1] >= 0.25" not in generated,
]
assert all(fixture_checks), f"scan-rune fixture checks failed: {fixture_checks}"
print("SCAN_RUNE_SMOKE_FIXTURE=PASS assertions=10")
# The real-client acceptance must exercise BETA26's entity branch as well as blocks.
fx_source = Path("src/neoforge/java/dev/ehtid/thaumcraft/client/ThaumometerRuneFx.java").read_text(encoding="utf-8")
entity_parser_block = generated.rsplit("python3 - <<'PY'", 1)[1]
entity_parser_imports = entity_parser_block.split("from scan_rune_log_parser", 1)[0]
assert "import math" in entity_parser_imports.splitlines(), "entity/sparkle parser heredoc must import math in its own process"
entity_tall_wait = generated.split("rune_capture 84-tall-entity-during", 1)[1].split("rune_capture 85-tall-entity-after", 1)[0]
entity_fixture_checks = [
    "send_chat '/summon minecraft:cow" in generated,
    "send_chat '/summon minecraft:iron_golem" in generated,
    "RUNE_ENTITY_{label.upper()}=PASS" in generated,
    "RUNE_ENTITY_HEIGHT_SCALING=PASS" in generated,
    'sys.path.insert(0, str(Path.cwd() / "tools"))' in entity_parser_block,
    "tall_expiry_deadline=$((SECONDS + 12))" in entity_tall_wait,
    "tall_expired_now >= tall_expired_before + 10" in entity_tall_wait,
    "RUNE_TALL_EXPIRY_TIMEOUT=FAIL" in entity_tall_wait,
    "sleep 0.5" in entity_tall_wait,
    "120 ticks (6 s at 20 TPS)" in entity_tall_wait,
    "parse_scan_rune_entity_targets(log)" in generated,
    "LegacyScanRuneDuration.entityUnits(height)" in fx_source,
    "TC_SMOKE_SCAN_RUNE_ENTITY_TARGET=YES type=" in fx_source,
]
assert all(entity_fixture_checks), f"entity scan-rune fixture checks failed: {entity_fixture_checks}"
print("SCAN_RUNE_ENTITY_SMOKE_FIXTURE=PASS assertions=13")
sparkle_fixture_checks = [
    "parse_thaumometer_sparkle_log(log)" in generated,
    "THAUMOMETER_IDLE_SPARKLE=PASS" in generated,
    "THAUMOMETER_IDLE_SPARKLE_ADDITIVE_BLEND=PASS" in generated,
    "TC_SMOKE_THAUMOMETER_SPARKLE_CREATED=YES" in particle_source,
    "TC_SMOKE_THAUMOMETER_SPARKLE_RENDERED=YES" in particle_source,
    "TC_SMOKE_THAUMOMETER_SPARKLE_EXPIRED=YES" in particle_source,
]
assert all(sparkle_fixture_checks), f"Thaumometer sparkle client fixture failed: {sparkle_fixture_checks}"
print(f"THAUMOMETER_SPARKLE_CLIENT_FIXTURE=PASS assertions={len(sparkle_fixture_checks)}")
# The isolated runner must not inherit the shared smoke's unverified chat sender.
shared_smoke = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
shared_sender = """  send_chat() {
    xdotool key --clearmodifiers t
    sleep 1
    xdotool type --clearmodifiers --delay 24 "$1"
    xdotool key --clearmodifiers Return
    sleep 4
  }"""
assert shared_smoke.count(shared_sender) == 1
sender_body = generated.split("  send_chat() {", 1)[1].split("\n  }", 1)[0]
assert 'xdotool windowactivate --sync "$WIN"' in sender_body
assert 'xdotool windowfocus --sync "$WIN"' in sender_body
assert 'xdotool getactivewindow' in sender_body and 'xdotool getwindowfocus' in sender_body
assert 'xdotool key --clearmodifiers slash' in sender_body
assert 'xdotool key --clearmodifiers t' not in sender_body
assert 'RUNE_CHAT_INPUT_FOCUS=FAIL' in sender_body
assert "RUNE_CHAT_POST_SUBMIT_FOCUS=FAIL" in sender_body
assert "RUNE_CHAT_POST_SUBMIT_FOCUS=PASS" in sender_body
assert sender_body.index("xdotool key --clearmodifiers Return") < sender_body.index("RUNE_CHAT_POST_SUBMIT_FOCUS=PASS")
assert "send_chat '/forceload add -4 -4 4 10'" in generated
assert "send_chat '/forceload add -4 149 -4 4 149 10'" not in generated
assert "send_chat '/forceload add 0 0'" not in generated
print("SCAN_RUNE_CHAT_INPUT=PASS assertions=9")

# Real-client artifact 14175364151 from run #43749075503:
# the screenshot is a 1280x800 ROOT capture; the Minecraft window is
# 1024x768 at root offset (X=1,Y=20). The button bounds in root pixels are
# x=205..502, y=718..756. Always compare root click coordinates to root bounds.
create_x_line = "create_world_x=$((X + WIDTH * 34 / 100))"
create_y_line = "create_world_y=$((Y + HEIGHT * 935 / 1000))"
assert generated.count(create_x_line) == 1
assert generated.count(create_y_line) == 1
assert "create_world_y=$((Y + HEIGHT * 96 / 100))" not in generated
click_line = 'xdotool mousemove "$create_world_x" "$create_world_y" click 1'
assert generated.index(click_line) < generated.index("capture 04a-after-create-world-click") < generated.index("world_started=0")
local_click_marker = 'CREATE_NEW_WORLD_CLICK=${create_world_x},${create_world_y} LOCAL=$((create_world_x - X)),$((create_world_y - Y))'
assert local_click_marker in generated
window_x, window_y, window_width, window_height = 1, 20, 1024, 768
root_x = window_x + window_width * 34 // 100
root_y = window_y + window_height * 935 // 1000
previous_96_root_y = window_y + window_height * 96 // 100
previous_93_root_y = window_y + window_height * 93 // 100
assert 205 <= root_x <= 502, root_x
assert 718 <= root_y <= 756, root_y
assert not 718 <= previous_96_root_y <= 756, previous_96_root_y
assert 718 <= previous_93_root_y <= 756, previous_93_root_y
print(
    f"SCAN_RUNE_WORLD_CREATE_HITBOX=PASS root=({root_x},{root_y}) "
    f"bounds=x205..502,y718..756 previous96=({root_x},{previous_96_root_y})"
)

# Timeout diagnostics must preserve process/thread and world-save evidence without weakening the login gate.
timeout_diagnostics = ["RUNE_LOGIN_TIMEOUT_DIAGNOSTICS=BEGIN", "capture 04b-world-login-timeout", "jcmd -l", "Thread.print -l", "find . -type f -path '*/logs/latest.log'", "python3 tools/assert_client_world_commands.py run/saves", "RUNE_LOGIN_TIMEOUT_LEVEL_DAT_COUNT=", "RUNE_LOGIN_TIMEOUT_DIAGNOSTICS=END"]
assert all(marker in generated for marker in timeout_diagnostics), timeout_diagnostics
assert generated.index("capture 04b-world-login-timeout") < generated.index("jcmd -l")
assert generated.index("jcmd -l") < generated.index("Thread.print -l")
assert generated.index("Thread.print -l") < generated.index("RUNE_LOGIN_TIMEOUT_LATEST_LOG=")
assert generated.index("RUNE_LOGIN_TIMEOUT_DIAGNOSTICS=END") < generated.index("exit 1", generated.index("RUNE_LOGIN_TIMEOUT_DIAGNOSTICS=BEGIN"))
print("SCAN_RUNE_LOGIN_TIMEOUT_DIAGNOSTICS=PASS assertions=12")
