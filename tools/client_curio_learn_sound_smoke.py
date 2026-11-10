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
options = "printf 'guiScale:2\\n' > run/options.txt"
if source.count(options) != 1:
    raise SystemExit("Minecraft options fixture changed; refusing unsafe audio isolation")
# Mute only unrelated background categories; keep Curio's NEUTRAL and master audible.
head = source.split(anchor, 1)[0].replace(
    "export ALSOFT_DRIVERS=null",
    "export ALSOFT_DRIVERS=pulse\nexport ALSOFT_LOGLEVEL=3"
).replace(options,
    "printf 'guiScale:2\\nsoundCategory_music:0.0\\nsoundCategory_ambient:0.0\\n' > run/options.txt")
body = r'''
  # Confirm the real client loaded the isolated options; do not mute the Curio sound category.
  if ! grep -Fxq 'soundCategory_music:0.0' run/options.txt || ! grep -Fxq 'soundCategory_ambient:0.0' run/options.txt; then
    echo 'CURIO_LEARN_BACKGROUND_AUDIO_ISOLATION=FAIL' | tee -a client-smoke-status.txt
    exit 1
  fi
  if grep -Fq 'soundCategory_neutral:0.0' run/options.txt || grep -Fq 'soundCategory_master:0.0' run/options.txt; then
    echo 'CURIO_LEARN_SOUND_OUTPUT_CATEGORY_MUTED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'CURIO_LEARN_BACKGROUND_AUDIO_ISOLATION=PASS' | tee -a client-smoke-status.txt
  echo 'CURIO_LEARN_REAL_WORLD=YES' | tee -a client-smoke-status.txt
  send_chat '/time set noon'

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
  pactl list short sinks > client-smoke/curio-sinks.txt
  grep -Fq 'tc_curio_sink' client-smoke/curio-sinks.txt
  timeout --signal=TERM 2s parec --device=tc_curio_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/curio-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_curio_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/curio-event.pcm &
  curio_capture_pid=$!
  sleep 0.6
  xdotool click 3
  sleep 0.7
  pactl list short sink-inputs > client-smoke/curio-sink-inputs.txt
  python3 tools/verify_curio_pulse_sink.py \
    client-smoke/curio-sinks.txt client-smoke/curio-sink-inputs.txt
  python3 tools/verify_curio_openal_backend.py client-smoke/client.log tc_curio_sink \
    | tee -a client-smoke-status.txt
  sleep 3.7
  wait "$curio_capture_pid" || test "$?" -eq 124
  import -display :99 -window root -silent client-smoke/screenshots/96-curio-after.png
  # Curio's BETA26 pitch is only 0.33-0.50; a fixed page-sound RMS
  # threshold is inappropriate. Require ORIGINAL waveform attribution instead.
  python3 tools/match_curio_audio_pcm.py \
    client-smoke/curio-baseline.pcm client-smoke/curio-event.pcm \
    src/main/resources/assets/thaumcraft/sounds/learn.ogg \
    | tee client-smoke/curio-source-match.txt
  python3 tools/check_curio_pitch_parity.py client-smoke/client.log client-smoke/curio-source-match.txt \
    | tee client-smoke/curio-pitch-parity.txt
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
 run/options.txt; then
    echo 'CURIO_LEARN_SOUND_OUTPUT_CATEGORY_MUTED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'CURIO_LEARN_BACKGROUND_AUDIO_ISOLATION=PASS' | tee -a client-smoke-status.txt
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
  pactl list short sinks > client-smoke/curio-sinks.txt
  grep -Fq 'tc_curio_sink' client-smoke/curio-sinks.txt
  timeout --signal=TERM 2s parec --device=tc_curio_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/curio-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_curio_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/curio-event.pcm &
  curio_capture_pid=$!
  sleep 0.6
  xdotool click 3
  sleep 0.7
  pactl list short sink-inputs > client-smoke/curio-sink-inputs.txt
  python3 tools/verify_curio_pulse_sink.py \
    client-smoke/curio-sinks.txt client-smoke/curio-sink-inputs.txt
  python3 tools/verify_curio_openal_backend.py client-smoke/client.log tc_curio_sink \
    | tee -a client-smoke-status.txt
  sleep 3.7
  wait "$curio_capture_pid" || test "$?" -eq 124
  import -display :99 -window root -silent client-smoke/screenshots/96-curio-after.png
  # Curio's BETA26 pitch is only 0.33-0.50; a fixed page-sound RMS
  # threshold is inappropriate. Require ORIGINAL waveform attribution instead.
  python3 tools/match_curio_audio_pcm.py \
    client-smoke/curio-baseline.pcm client-smoke/curio-event.pcm \
    src/main/resources/assets/thaumcraft/sounds/learn.ogg \
    | tee client-smoke/curio-source-match.txt
  python3 tools/check_curio_pitch_parity.py client-smoke/client.log client-smoke/curio-source-match.txt \
    | tee client-smoke/curio-pitch-parity.txt
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
