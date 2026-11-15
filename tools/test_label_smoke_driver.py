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
require = unique("  label_require_slot() {")
set_slot = unique('    label_send_chat "/item replace entity @p hotbar.0 with $item"')
predicate = unique('    label_send_chat "/execute if items entity @p $slot $predicate run say $marker"')
server_ack = unique('grep -Fq "$marker" run/logs/latest.log')
fail_closed = unique('LABEL_SLOT_CONTENT=FAIL slot=$slot marker=$marker')
first_capture = unique("  label_capture 60-jar-brace")
assert focus < send < require < predicate < server_ack < fail_closed < set_slot < first_capture
assert 'xdotool windowactivate --sync "$WIN"' in generated
assert 'xdotool windowfocus --sync "$WIN"' in generated
assert 'xdotool getwindowpid "$WIN"' in generated
# Match the already-proven shared client_smoke.sh chat pacing. Short delays
# can lose the first command immediately after an integrated-world join.
assert """    xdotool key --clearmodifiers t
    sleep 1
    xdotool type --clearmodifiers --delay 24 "$1"
    xdotool key --clearmodifiers Return
    sleep 4""" in generated
assert "sleep 0.35" not in generated and "sleep 0.5" not in generated
assert 'LABEL_INPUT_FOCUS=PASS' in generated
assert "\n  send_chat '/item replace" not in generated
for marker in (
    "TC_LABEL_BRACE_SLOT_READY",
    "TC_LABEL_BLANK_SLOT_READY",
    "TC_LABEL_AER_SLOT_READY",
    "TC_LABEL_IGNIS_SLOT_READY",
):
    assert marker in generated, marker
assert generated.index("TC_LABEL_BRACE_SLOT_READY") < generated.index("label_capture 60-jar-brace")
assert generated.index("TC_LABEL_BLANK_SLOT_READY") < generated.index("label_capture 61-blank-label")
assert generated.index("TC_LABEL_AER_SLOT_READY") < generated.index("label_capture 62-aer-label")
assert generated.index("TC_LABEL_IGNIS_SLOT_READY") < generated.index("label_capture 63-ignis-label")
assert "TC_LABEL_INVENTORY_" not in generated
assert "label_capture 64-all-four-in-real-inventory" not in generated
assert "hotbar.1" not in generated and "hotbar.2" not in generated and "hotbar.3" not in generated
assert "xdotool key --clearmodifiers e" not in generated
last_visual_compare = unique("  label_diff 62-aer-label 63-ignis-label")
color_pass = unique("  echo 'LABEL_REAL_ASPECT_COLOR_PARITY=PASS'")
client_pass = unique("  echo 'LABEL_REAL_CLIENT_VISUAL_SMOKE=PASS'")
assert last_visual_compare < color_pass < client_pass
assert 'minecraft:custom_data~{Aspects:[{key:"aer",amount:1}]}' in generated
assert 'minecraft:custom_data~{Aspects:[{key:"ignis",amount:1}]}' in generated
assert "LABEL_REAL_ASPECT_COLOR_PARITY=PASS" in generated
print("Label smoke requires real X11 focus and server-verified item/component predicates: OK")
