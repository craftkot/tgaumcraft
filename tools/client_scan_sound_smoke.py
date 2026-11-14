#!/usr/bin/env python3
"""Generate isolated REAL Minecraft 1.21.1 Thaumometer air-click audio smoke.

The original BETA26 scan event fires on item use even when the ray misses.
Checks an actual item predicate and records the real OpenAL/PulseAudio PCM.
Source-clip identity is checked separately against the original scan.ogg.
"""
from pathlib import Path
import sys

source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
anchor = "  # The initial world is already creative + peaceful, as established"
if source.count(anchor) != 1:
    raise SystemExit("World boot hook changed; refusing unsafe smoke injection")
if source.count("export ALSOFT_DRIVERS=null") != 1:
    raise SystemExit("OpenAL backend hook changed; refusing unverified audio setup")
head = source.split(anchor, 1)[0].replace("export ALSOFT_DRIVERS=null",
                                           "export ALSOFT_DRIVERS=pulse")
body = r'''
  echo 'SCAN_SOUND_REAL_INTEGRATED_WORLD=YES' | tee -a client-smoke-status.txt
  send_chat '/time set noon'
  send_chat '/weather clear'
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:thaumometer 1'
  send_chat '/execute if items entity @p hotbar.0 thaumcraft:thaumometer run say SCAN_SOUND_ITEM_READY'
  if ! grep -Fq 'SCAN_SOUND_ITEM_READY' client-smoke/client.log; then
    echo 'SCAN_SOUND_REAL_ITEM_MISSING=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'SCAN_SOUND_REAL_ITEM=PASS' | tee -a client-smoke-status.txt
  xdotool key --clearmodifiers 1
  sleep 3
  import -display :99 -window root -silent client-smoke/screenshots/90-scan-sound-before.png
  pactl info > client-smoke/scan-pulse-info.txt
  pactl list short sinks | grep -Fq 'tc_scan_sink'
  timeout --signal=TERM 2s parec --device=tc_scan_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/scan-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_scan_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/scan-event.pcm &
  scan_capture_pid=$!
  sleep 0.6
  xdotool click 3
  sleep 5
  wait "$scan_capture_pid" || test "$?" -eq 124
  import -display :99 -window root -silent client-smoke/screenshots/91-scan-sound-after.png
  python3 tools/check_page_audio_pcm.py \
    client-smoke/scan-baseline.pcm client-smoke/scan-event.pcm \
    | tee client-smoke/scan-pcm-level.txt
  echo 'SCAN_SOUND_REAL_CLIENT_PCM=PASS' | tee -a client-smoke-status.txt
  if grep -Eiq 'Unknown sound event.*thaumcraft:scan|Unable to load sound.*thaumcraft:scan|Missing sound.*thaumcraft:scan|Exception in thread "Render thread"|Mod loading has failed' client-smoke/client.log; then
    echo 'SCAN_SOUND_CLIENT_ERROR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'SCAN_SOUND_REAL_ITEM_AND_AUDIO_CAPTURE=PASS' | tee -a client-smoke-status.txt
fi
'''
sys.stdout.write(head + body)
