#!/usr/bin/env python3
"""Generate one real Minecraft client run testing placement audio for six BETA26 crystals.

Aqua placement and Aer break were previously accepted in separate real-client CI.
Each variant here must be placed by a genuine right click, verified in the
integrated world, and matched to the byte-identical original crystal.ogg.
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
  echo 'CRYSTAL_SIX_VARIANTS_REAL_WORLD=YES' | tee -a client-smoke-status.txt
  send_chat '/gamemode creative'
  send_chat '/time set noon'
  send_chat '/weather clear'
  send_chat '/forceload add 250 250'
  send_chat '/fill 249 149 249 251 149 253 minecraft:stone'
  send_chat '/fill 249 150 249 251 153 253 minecraft:air'
  send_chat '/setblock 250 151 252 minecraft:stone'
  pactl list short sinks | grep -Fq 'tc_crystal_variants_sink'
  for variant in aer ignis terra ordo perditio vitium; do
    echo "CRYSTAL_VARIANT_BEGIN=$variant" | tee -a client-smoke-status.txt
    send_chat '/setblock 250 151 251 minecraft:air'
    send_chat "/execute if block 250 151 251 minecraft:air run say CRYSTAL_TARGET_EMPTY_$variant"
    if ! grep -Fq "CRYSTAL_TARGET_EMPTY_$variant" client-smoke/client.log; then
      echo "CRYSTAL_TARGET_NOT_EMPTY=$variant" | tee -a client-smoke-status.txt
      exit 1
    fi
    send_chat "/item replace entity @p hotbar.0 with thaumcraft:crystal_$variant 1"
    send_chat '/tp @p 250.5 150 250.5 0 0'
    xdotool key --clearmodifiers 1
    sleep 3
    import -display :99 -window root -silent "client-smoke/screenshots/97-crystal-$variant-before.png"
    timeout --signal=TERM 2s parec --device=tc_crystal_variants_sink.monitor \
      --format=s16le --rate=48000 --channels=2 --raw \
      > "client-smoke/crystal-$variant-baseline.pcm" || test "$?" -eq 124
    timeout --signal=TERM 5s parec --device=tc_crystal_variants_sink.monitor \
      --format=s16le --rate=48000 --channels=2 --raw \
      > "client-smoke/crystal-$variant-event.pcm" &
    capture_pid=$!
    sleep 0.6
    xdotool click 3
    sleep 5
    wait "$capture_pid" || test "$?" -eq 124
    import -display :99 -window root -silent "client-smoke/screenshots/98-crystal-$variant-after.png"
    send_chat "/execute if block 250 151 251 thaumcraft:crystal_$variant run say CRYSTAL_REAL_PLACED_$variant"
    if ! grep -Fq "CRYSTAL_REAL_PLACED_$variant" client-smoke/client.log; then
      echo "CRYSTAL_REAL_PLACE_FAILED=$variant" | tee -a client-smoke-status.txt
      exit 1
    fi
    python3 tools/match_crystal_audio_pcm.py \
      "client-smoke/crystal-$variant-baseline.pcm" \
      "client-smoke/crystal-$variant-event.pcm" \
      src/main/resources/assets/thaumcraft/sounds/crystal.ogg \
      | tee "client-smoke/crystal-$variant-source-match.txt"
    echo "CRYSTAL_REAL_PLACE_ORIGINAL_PCM_$variant=PASS" | tee -a client-smoke-status.txt
  done
  if grep -Eiq 'Unknown sound event.*thaumcraft:crystal|Unable to load sound.*thaumcraft:crystal|Missing sound.*thaumcraft:crystal|Exception in thread "Render thread"|Mod loading has failed' client-smoke/client.log; then
    echo 'CRYSTAL_VARIANTS_CLIENT_SOUND_ERROR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  test "$(grep -c '^CRYSTAL_REAL_PLACE_ORIGINAL_PCM_.*=PASS$' client-smoke-status.txt)" -eq 6
  echo 'CRYSTAL_SIX_VARIANTS_REAL_PLACE_AND_ORIGINAL_PCM=PASS' | tee -a client-smoke-status.txt
fi
'''
sys.stdout.write(head + body)
