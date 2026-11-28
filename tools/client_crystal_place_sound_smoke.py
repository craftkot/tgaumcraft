#!/usr/bin/env python3
"""Generate an isolated real-client BETA26 crystal *placement* sound smoke.

Unlike setblock, this exercises BlockItem.useOn, the survival support check,
Minecraft's place-sound dispatch, and actual OpenAL/PulseAudio output.
"""
from pathlib import Path
import sys

source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
anchor = "  # The initial world is already creative + peaceful, as established"
if source.count(anchor) != 1 or source.count("export ALSOFT_DRIVERS=null") != 1:
    raise SystemExit("Client boot/audio hooks changed; refuse blind injection")
head = source.split(anchor, 1)[0].replace("export ALSOFT_DRIVERS=null",
                                           "export ALSOFT_DRIVERS=pulse")
body = r'''
  echo 'CRYSTAL_PLACE_REAL_WORLD=YES' | tee -a client-smoke-status.txt
  send_chat '/gamemode creative'
  send_chat '/time set noon'
  send_chat '/weather clear'
  send_chat '/forceload add 250 250'
  send_chat '/fill 249 149 249 251 149 253 minecraft:stone'
  send_chat '/fill 249 150 249 251 153 253 minecraft:air'
  send_chat '/setblock 250 151 252 minecraft:stone'
  send_chat '/execute if block 250 151 251 minecraft:air run say CRYSTAL_PLACE_TARGET_EMPTY'
  if ! grep -Fq 'CRYSTAL_PLACE_TARGET_EMPTY' client-smoke/client.log; then
    echo 'CRYSTAL_PLACE_TARGET_NOT_EMPTY=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:crystal_aqua 1'
  send_chat '/tp @p 250.5 150 250.5 0 0'
  xdotool key --clearmodifiers 1
  sleep 3
  import -display :99 -window root -silent client-smoke/screenshots/97-crystal-place-before.png
  pactl list short sinks | grep -Fq 'tc_crystal_place_sink'
  timeout --signal=TERM 2s parec --device=tc_crystal_place_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/crystal-place-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_crystal_place_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/crystal-place-event.pcm &
  capture_pid=$!
  sleep 0.6
  xdotool click 3
  sleep 5
  wait "$capture_pid" || test "$?" -eq 124
  import -display :99 -window root -silent client-smoke/screenshots/98-crystal-place-after.png
  send_chat '/execute if block 250 151 251 thaumcraft:crystal_aqua run say CRYSTAL_REAL_BLOCK_PLACED_AQUA'
  if ! grep -Fq 'CRYSTAL_REAL_BLOCK_PLACED_AQUA' client-smoke/client.log; then
    echo 'CRYSTAL_REAL_PLACE_FAILED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'CRYSTAL_REAL_PLACE_AQUA=PASS' | tee -a client-smoke-status.txt
  python3 tools/match_crystal_audio_pcm.py \
    client-smoke/crystal-place-baseline.pcm client-smoke/crystal-place-event.pcm \
    src/main/resources/assets/thaumcraft/sounds/crystal.ogg \
    | tee client-smoke/crystal-place-source-match.txt
  if grep -Eiq 'Unknown sound event.*thaumcraft:crystal|Unable to load sound.*thaumcraft:crystal|Missing sound.*thaumcraft:crystal|Exception in thread "Render thread"|Mod loading has failed' client-smoke/client.log; then
    echo 'CRYSTAL_REAL_PLACE_CLIENT_SOUND_ERROR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'CRYSTAL_REAL_PLACE_AND_ORIGINAL_PCM=PASS' | tee -a client-smoke-status.txt
fi
'''
sys.stdout.write(head + body)
