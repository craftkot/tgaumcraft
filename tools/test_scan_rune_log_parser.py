#!/usr/bin/env python3
"""Regression tests for the actual ScanRuneParticle client-log format."""
from scan_rune_log_parser import parse_scan_rune_log
from pathlib import Path
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
