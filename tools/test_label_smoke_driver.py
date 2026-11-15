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
first_capture = unique("label_capture 60-jar-brace")
assert focus < send < set_slot < server_ack < fail_closed < first_capture

assert 'xdotool windowactivate --sync "$WIN"' in generated
assert 'xdotool windowfocus --sync "$WIN"' in generated
assert 'xdotool getwindowpid "$WIN"' in generated
assert "LABEL_INPUT_FOCUS=PASS" in generated

send_body = generated[send:set_slot]
assert send_body.count("label_activate_minecraft") == 2
assert "LABEL_COMMAND_TEXT=%s" in send_body
assert send_body.index("LABEL_COMMAND_TEXT") < send_body.index('xdotool type --clearmodifiers --delay 24 "$1"')
send_sequence = (
    send_body.index("label_activate_minecraft"),
    send_body.index("xdotool key --clearmodifiers t"),
    send_body.index('label_capture "${LABEL_DIAGNOSTIC_PREFIX:-58}-label-chat-open"'),
    send_body.index('xdotool type --clearmodifiers --delay 24 "$1"'),
    send_body.index('label_capture "${LABEL_DIAGNOSTIC_PREFIX:-58}a-label-command-before-submit"'),
    send_body.rindex("label_activate_minecraft"),
    send_body.index("LABEL_INPUT_FOCUS_AFTER_CAPTURE=PASS"),
    send_body.index("xdotool key --clearmodifiers Return"),
    send_body.index("LABEL_COMMAND_RETURN_SENT=YES"),
    send_body.index("sleep 0.5"),
    send_body.index('label_capture "${LABEL_DIAGNOSTIC_PREFIX:-58}b-label-command-feedback"'),
    send_body.index("sleep 3.5"),
)
assert list(send_sequence) == sorted(send_sequence), send_sequence
assert "else\n      sleep 4\n    fi" in send_body

assert "sleep 0.35" not in generated
setup_sequence = (
    unique("  LABEL_CAPTURE_INPUT_DIAGNOSTICS=1"),
    unique("  LABEL_DIAGNOSTIC_PREFIX=58"),
    unique("  label_set_slot0 'thaumcraft:jar_brace' 'Jar Brace'"),
    unique("  label_capture 60-jar-brace"),
    unique("  LABEL_DIAGNOSTIC_PREFIX=59"),
    unique("  label_set_slot0 'thaumcraft:label' 'Jar Label'"),
    unique("  LABEL_CAPTURE_INPUT_DIAGNOSTICS=0"),
)
assert list(setup_sequence) == sorted(setup_sequence), setup_sequence
assert 'label_capture "${LABEL_DIAGNOSTIC_PREFIX:-58}b-label-command-feedback"' in generated

aer_start = unique("  LABEL_DIAGNOSTIC_PREFIX=62\n  label_set_slot0 'thaumcraft:label[")
aer_end = unique("  label_capture 62-aer-label")
ignis_start = unique("  LABEL_DIAGNOSTIC_PREFIX=63\n  label_set_slot0 'thaumcraft:label[")
ignis_end = unique("  label_capture 63-ignis-label")
assert aer_start < aer_end < ignis_start < ignis_end

channel_handshake = unique("label_send_chat '/say TC_LABEL_COMMAND_CHANNEL_READY'\n  label_require_command_channel\n\n  # Capture the first chat transition")
assert channel_handshake < unique("label_set_slot0 'thaumcraft:jar_brace' 'Jar Brace'")
assert unique("grep -Fq 'TC_LABEL_COMMAND_CHANNEL_READY' run/logs/latest.log") < channel_handshake

return_command = unique("    xdotool key --clearmodifiers Return\n    echo 'LABEL_COMMAND_RETURN_SENT=YES'")
assert unique('label_capture "${LABEL_DIAGNOSTIC_PREFIX:-58}a-label-command-before-submit"') < unique("LABEL_INPUT_FOCUS_AFTER_CAPTURE=PASS") < return_command
assert return_command < unique("LABEL_COMMAND_RETURN_SENT=YES")
assert "label_activate_minecraft\n      echo 'LABEL_INPUT_FOCUS_AFTER_CAPTURE=PASS'" in generated
assert "\\n  send_chat '/item replace" not in generated
assert "label_require_slot" not in generated
assert "execute if items" not in generated
assert generated.count("TC_LABEL_COMMAND_CHANNEL_READY") == 2
assert "TC_LABEL_" not in generated.replace("TC_LABEL_COMMAND_CHANNEL_READY", "").replace("TC_LABEL_POST_FAILURE_CHANNEL_READY", "")
assert "label_send_chat '/give @p thaumcraft:label 1'" in generated
assert "label_send_chat '/say TC_LABEL_POST_FAILURE_CHANNEL_READY'" in generated
assert generated.index("label_send_chat '/give @p thaumcraft:label 1'") < generated.index("label_send_chat '/say TC_LABEL_POST_FAILURE_CHANNEL_READY'")
assert generated.index("label_send_chat '/say TC_LABEL_POST_FAILURE_CHANNEL_READY'") < generated.index('LABEL_SLOT_CONTENT=FAIL slot=hotbar.0 item=$expected_name')
assert 'LABEL_POST_FAILURE_COMMAND_CHANNEL=PASS' in generated
assert 'label_capture "${LABEL_DIAGNOSTIC_PREFIX:-59}c-label-give-probe"' in generated
assert 'if [[ "$item" == thaumcraft:label* ]]; then' in generated
fallback_body = generated[generated.index('if [[ "$item" == thaumcraft:label* ]]; then'):generated.index('    echo "LABEL_SLOT_CONTENT=FAIL', generated.index('if [[ "$item" == thaumcraft:label* ]]; then'))]
assert fallback_body.index("LABEL_CAPTURE_INPUT_DIAGNOSTICS=0") < fallback_body.index("label_send_chat '/give @p thaumcraft:label 1'")

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

print("Label smoke captures each command's immediate chat feedback and preserves fail-closed slot checks: OK")
