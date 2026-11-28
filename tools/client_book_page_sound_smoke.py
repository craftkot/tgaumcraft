#!/usr/bin/env python3
"""Isolated real Minecraft Thaumonomicon GUI, sound dispatch and PCM smoke.

Runs OpenAL Soft through a PulseAudio virtual sink and measures monitor PCM.
A non-silent monitor plus resource resolution proves a playback path, but
not exact perceptual parity with BETA26 clips.
"""
from pathlib import Path
import sys

source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
anchor = "  # The initial world is already creative + peaceful, as established"
if source.count(anchor) != 1:
    raise SystemExit("World boot hook changed; refusing unsafe smoke injection")
if source.count("export ALSOFT_DRIVERS=null") != 1:
    raise SystemExit("OpenAL backend hook changed; refusing an unverified audio smoke")
head = source.split(anchor, 1)[0].replace("export ALSOFT_DRIVERS=null", "export ALSOFT_DRIVERS=pulse")
body = r'''
  echo 'PAGE_REAL_INTEGRATED_WORLD=YES' | tee -a client-smoke-status.txt
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:thaumonomicon 1'
  send_chat '/execute if items entity @p hotbar.0 thaumcraft:thaumonomicon run say PAGE_BOOK_ITEM_READY'
  if ! grep -Fq 'PAGE_BOOK_ITEM_READY' client-smoke/client.log; then
    echo 'PAGE_REAL_BOOK_ITEM_MISSING=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'PAGE_REAL_BOOK_ITEM=PASS' | tee -a client-smoke-status.txt
  xdotool key --clearmodifiers 1
  sleep 3
  import -display :99 -window root -silent client-smoke/screenshots/80-page-before-use.png
  # PulseAudio monitor is a genuine PCM stream, unlike the old null OpenAL driver.
  # Capture the quiet pre-event baseline separately to reject a permanently noisy sink.
  pactl info > client-smoke/pulse-info.txt
  pactl list short sinks | grep -Fq 'tc_page_sink'
  timeout --signal=TERM 2s parec --device=tc_page_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/page-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_page_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/page-event.pcm &
  page_audio_capture_pid=$!
  sleep 0.6
  xdotool click 3
  sleep 5
  wait "$page_audio_capture_pid" || test "$?" -eq 124
  python3 tools/check_page_audio_pcm.py \
    client-smoke/page-baseline.pcm client-smoke/page-event.pcm \
    | tee client-smoke/page-pcm-check.txt
  echo 'PAGE_REAL_CLIENT_PCM=PASS' | tee -a client-smoke-status.txt
  import -display :99 -window root -silent client-smoke/screenshots/81-page-book-open.png
  for event in THAUMONOMICON_PAGE_SOUND_DISPATCHED THAUMONOMICON_PAGE_SOUND_RESOLVED THAUMONOMICON_GUI_OPENED; do
    count="$(grep -Fc "TC_SMOKE_${event}=YES" client-smoke/client.log || true)"
    if (( count != 1 )); then
      echo "PAGE_REAL_CLIENT_EVENT_MISSING_OR_DUPLICATE=${event} count=${count}" | tee -a client-smoke-status.txt
      exit 1
    fi
    echo "PAGE_REAL_CLIENT_EVENT=PASS ${event} count=${count}" | tee -a client-smoke-status.txt
  done
  # Require a real GUI framebuffer change at the same camera/viewport.
  # Dispatch and sound-manager resolution are separately proven by client markers.
  geometry="$(xdotool getwindowgeometry --shell "$WIN")"
  eval "$geometry"
  crop_x=$((X + WIDTH / 2 - 160))
  crop_y=$((Y + HEIGHT / 2 - 130))
  for name in 80-page-before-use 81-page-book-open; do
    convert "client-smoke/screenshots/$name.png" \
      -crop "320x260+${crop_x}+${crop_y}" +repage \
      "client-smoke/screenshots/$name-center.png"
  done
  difference="$(compare -metric AE \
    client-smoke/screenshots/80-page-before-use-center.png \
    client-smoke/screenshots/81-page-book-open-center.png null: 2>&1 || true)"
  difference="${difference%% *}"
  if ! [[ "$difference" =~ ^[0-9]+$ ]] || (( difference < 1000 )); then
    echo "PAGE_REAL_GUI_FRAMEBUFFER_MISSING=${difference}" | tee -a client-smoke-status.txt
    exit 1
  fi
  echo "PAGE_REAL_GUI_FRAMEBUFFER=PASS pixels=${difference}" | tee -a client-smoke-status.txt
  if grep -Eiq 'Unknown sound event.*thaumcraft:page|Unable to load sound.*thaumcraft:page|Missing sound.*thaumcraft:page|Exception in thread "Render thread"|Mod loading has failed' client-smoke/client.log; then
    echo 'PAGE_CLIENT_SOUND_OR_RENDER_ERROR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'PAGE_REAL_CLIENT_SOUND_DISPATCH_AND_GUI=PASS' | tee -a client-smoke-status.txt
fi
'''
sys.stdout.write(head + body)
