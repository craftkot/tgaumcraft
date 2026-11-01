#!/usr/bin/env python3
"""Generate isolated real Minecraft 1.21.1 Curio learn-sound PCM smoke."""
from pathlib import Path
import sys

source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
anchor = "  # The initial world is already creative + peaceful, as established"
if source.count(anchor) != 1:
    raise SystemExit("World boot hook changed; refusing unsafe smoke injection")
if source.count("export ALSOFT_DRIVERS=null") != 1:
    raise SystemExit("OpenAL backend hook changed; refusing unsafe audio setup")
head = source.split(anchor, 1)[0].replace("export ALSOFT_DRIVERS=null",
                                           "export ALSOFT_DRIVERS=pulse")
body = r'''
  echo 'CURIO_LEARN_REAL_WORLD=YES' | tee -a client-smoke-status.txt
  send_chat '/time set noon'
  send_chat '/weather clear'
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:curio 1'
  send_chat '/execute if items entity @p hotbar.0 thaumcraft:curio run say CURIO_LEARN_ITEM_READY'
  if ! grep -Fq 'CURIO_LEARN_ITEM_READY' client-smoke/client.log; then
    echo 'CURIO_LEARN_REAL_ITEM_MISSING=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'CURIO_LEARN_REAL_ITEM=PASS' | tee -a client-smoke-status.txt
  xdotool key --clearmodifiers 1
  sleep 3
  import -display :99 -window root -silent client-smoke/screenshots/95-curio-before.png
  pactl info > client-smoke/curio-pulse-info.txt
  pactl list short sinks | grep -Fq 'tc_curio_sink'
  timeout --signal=TERM 2s parec --device=tc_curio_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/curio-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_curio_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/curio-event.pcm &
  curio_capture_pid=$!
  sleep 0.6
  xdotool click 3
  sleep 5
  wait "$curio_capture_pid" || test "$?" -eq 124
  import -display :99 -window root -silent client-smoke/screenshots/96-curio-after.png
  python3 tools/check_page_audio_pcm.py \
    client-smoke/curio-baseline.pcm client-smoke/curio-event.pcm \
    | tee client-smoke/curio-pcm-level.txt
  echo 'CURIO_LEARN_REAL_PCM=PASS' | tee -a client-smoke-status.txt
  count="$(grep -Fc 'TC_SMOKE_CURIO_LEARN_SOUND_DISPATCHED=YES' client-smoke/client.log || true)"
  if (( count != 1 )); then
    echo "CURIO_LEARN_DISPATCH_COUNT_BAD=$count" | tee -a client-smoke-status.txt
    exit 1
  fi
  echo "CURIO_LEARN_SERVER_DISPATCH=PASS count=$count" | tee -a client-smoke-status.txt
  if grep -Eiq 'Unknown sound event.*thaumcraft:learn|Unable to load sound.*thaumcraft:learn|Missing sound.*thaumcraft:learn|Exception in thread "Render thread"|Mod loading has failed' client-smoke/client.log; then
    echo 'CURIO_LEARN_CLIENT_ERROR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'CURIO_LEARN_REAL_ITEM_AND_PCM_CAPTURE=PASS' | tee -a client-smoke-status.txt
fi
'''
sys.stdout.write(head + body)
