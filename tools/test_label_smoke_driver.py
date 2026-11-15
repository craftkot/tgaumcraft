#!/usr/bin/env python3
"""Regression contract for the real Minecraft label smoke driver."""
from pathlib import Path
import subprocess
import sys

generated = subprocess.run(
    [sys.executable, "tools/client_label_smoke.py"],
    check=True, capture_output=True, text=True,
).stdout
subprocess.run(["bash", "-n"], input=generated, text=True, check=True)

def unique(needle: str) -> int:
    count = generated.count(needle)
    assert count == 1, (needle, count)
    return generated.index(needle)

focus = unique("  label_activate_minecraft() {")
send = unique("  label_send_chat() {")
set_slot = unique("  label_set_slot0() {")
server_ack = unique('grep -Fq "Replaced a slot on Dev with [$expected_name]" run/logs/latest.log')
fail_closed = unique('LABEL_SLOT_CONTENT=FAIL slot=hotbar.0 item=$expected_name')
first_capture = unique("  label_capture 60-jar-brace")
assert focus < send < set_slot < server_ack < fail_closed < first_capture
assert 'xdotool windowactivate --sync "$WIN"' in generated
assert 'xdotool windowfocus --sync "$WIN"' in generated
assert 'xdotool getwindowpid "$WIN"' in generated
assert 'LABEL_INPUT_FOCUS=PASS' in generated
assert """    xdotool key --clearmodifiers t
    sleep 1
    if [ "${LABEL_CAPTURE_INPUT_DIAGNOSTICS:-0}" = "1" ]; then
      label_capture 58-label-chat-open
    fi
    xdotool type --clearmodifiers --delay 24 "$1"
    if [ "${LABEL_CAPTURE_INPUT_DIAGNOSTICS:-0}" = "1" ]; then
      sleep 1
      label_capture 58a-label-command-before-submit
    fi
    xdotool key --clearmodifiers Return
    sleep 4""" in generated
assert "sleep 0.25\n    local active focused" not in generated
assert """LABEL_CAPTURE_INPUT_DIAGNOSTICS=1
  label_set_slot0 'thaumcraft:jar_brace' 'Jar Brace'
  LABEL_CAPTURE_INPUT_DIAGNOSTICS=0""" in generated
assert "sleep 0.35" not in generated and "sleep 0.5" not in generated
assert "\\n  send_chat '/item replace" not in generated
assert "label_require_slot" not in generated
assert "execute if items" not in generated
assert "TC_LABEL_" not in generated
aer_call = """label_set_slot0 'thaumcraft:label[minecraft:custom_model_data=1,minecraft:custom_data={Aspects:[{key:"aer",amount:1}]}]' \\
    'Air Jar Label'"""
ignis_call = """label_set_slot0 'thaumcraft:label[minecraft:custom_model_data=1,minecraft:custom_data={Aspects:[{key:"ignis",amount:1}]}]' \\
    'Fire Jar Label'"""
assert aer_call in generated
assert ignis_call in generated
assert generated.count("label_set_slot0 'thaumcraft:label[") == 2
for call in (
    "label_set_slot0 'thaumcraft:jar_brace' 'Jar Brace'",
    "label_set_slot0 'thaumcraft:label' 'Jar Label'",
    aer_call,
    ignis_call,
):
    assert call in generated, call
for name, expected in (
    ("60-jar-brace", "Jar Brace"),
    ("61-blank-label", "Jar Label"),
    ("62-aer-label", "Air Jar Label"),
    ("63-ignis-label", "Fire Jar Label"),
):
    assert generated.index(expected) < generated.index(f"label_capture {name}")
assert 'minecraft:custom_data={Aspects:[{key:"aer",amount:1}]}' in generated
assert 'minecraft:custom_data={Aspects:[{key:"ignis",amount:1}]}' in generated
assert "LABEL_REAL_ASPECT_COLOR_PARITY=PASS" in generated
assert unique("  label_diff 62-aer-label 63-ignis-label") < unique("  echo 'LABEL_REAL_ASPECT_COLOR_PARITY=PASS'") < unique("  echo 'LABEL_REAL_CLIENT_VISUAL_SMOKE=PASS'")
print("Label smoke uses one server-acknowledged item command per slot and checks real rendered aspect colors: OK")
