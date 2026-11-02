#!/usr/bin/env python3
"""Guard the real Salis audio/particle fixture against capture-time interference.

This checks the generated *real Minecraft* shell sequence, not pixel rendering
or successful sound playback. The latter remain mandatory in the client CI.
"""
from pathlib import Path
import subprocess
import sys

generated = subprocess.run(
    [sys.executable, "tools/client_dust_sound_smoke.py"],
    check=True, capture_output=True, text=True,
).stdout
subprocess.run(["bash", "-n"], input=generated, text=True, check=True)

def unique(needle: str) -> int:
    count = generated.count(needle)
    assert count == 1, (needle, count)
    return generated.index(needle)

music = unique("soundCategory_music:0.0")
ambient = unique("soundCategory_ambient:0.0")
launch = unique("gradle runClient")
baseline = unique("python3 tools/check_dust_capture_noise.py client-smoke/dust-baseline.pcm")
record = unique("dust-event.pcm &")
click = unique("  xdotool click 3\n  sleep 0.2\n")
frame = unique("import -display :99 -window root -silent client-smoke/screenshots/08k-salis-sparkles-early.png")
wait = unique('  wait "$dust_capture_pid" || test "$?" -eq 124')
thumbnail = unique("convert client-smoke/screenshots/08k-salis-sparkles-early.png -resize")
base64 = unique("base64 -w0 client-smoke/screenshots/08k-salis-sparkles-early-thumb.png")
particle = unique("grep -Fq 'SALIS_SPARKLES_PACKETS_SENT' client-smoke/client.log")
delayed = unique("grep -Fq 'SALIS_TRAIL_DELAY_QUEUE_DRAINED' client-smoke/client.log")
matcher = unique("python3 tools/match_dust_audio_pcm.py")
assert music < ambient < launch < baseline < record < click < frame < wait < thumbnail < base64 < particle < delayed < matcher
assert "soundCategory_players:0.0" not in generated
assert "soundCategory_master:0.0" not in generated
assert "capture 08k-salis-sparkles-early" not in generated
assert "0.33" not in Path("tools/match_dust_audio_pcm.py").read_text(), "matcher must not hardcode game volume"
matcher_source = Path("tools/match_dust_audio_pcm.py").read_text()
assert "MIN_CORRELATION = 0.20" in matcher_source
assert "MIN_MARGIN = 0.08" in matcher_source
assert "send_chat '/tp @p 2.5 150 11.5 0 0'" in generated
print("Salis quiet client categories, clean PCM baseline gate, screenshot ordering and unchanged waveform thresholds: PASS")
