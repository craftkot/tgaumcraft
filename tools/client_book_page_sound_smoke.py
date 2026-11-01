#!/usr/bin/env python3
"""Isolated real Minecraft Thaumonomicon page sound dispatch and loaded-resource smoke.

The null OpenAL driver in CI prevents audible/PCM verification. This checks the
real client sound manager's loaded event, actual item right-click and GUI pixels.
"""
from pathlib import Path
import sys

source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
anchor = "  # The initial world is already creative + peaceful, as established"
if source.count(anchor) != 1:
    raise SystemExit("World boot hook changed; refusing unsafe smoke injection")
head = source.split(anchor, 1)[0]
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
  xdotool click 3
  sleep 5
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
