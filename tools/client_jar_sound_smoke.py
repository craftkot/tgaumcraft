#!/usr/bin/env python3
"""Generate isolated real Minecraft jar-break PCM smoke from the verified client boot."""
from pathlib import Path
import sys

source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
anchor = "  # The initial world is already creative + peaceful, as established"
if source.count(anchor) != 1 or source.count("export ALSOFT_DRIVERS=null") != 1:
    raise SystemExit("Client boot/audio hooks changed; refuse blind injection")
head = source.split(anchor, 1)[0].replace("export ALSOFT_DRIVERS=null",
                                           "export ALSOFT_DRIVERS=pulse")
body = r'''
  echo 'JAR_REAL_WORLD=YES' | tee -a client-smoke-status.txt
  mkdir -p client-smoke/screenshots
  send_chat '/time set noon'
  send_chat '/weather clear'
  send_chat '/forceload add 250 250'
  send_chat '/fill 249 149 249 251 149 253 minecraft:stone'
  send_chat '/fill 249 150 249 251 153 253 minecraft:air'
  send_chat '/setblock 250 151 251 thaumcraft:jar_normal'
  send_chat '/execute if block 250 151 251 thaumcraft:jar_normal run say JAR_REAL_BLOCK_READY'
  if ! grep -Fq 'JAR_REAL_BLOCK_READY' client-smoke/client.log; then
    echo 'JAR_REAL_BLOCK_MISSING=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  send_chat '/tp @p 250.5 150 250.5 0 0'
  xdotool key --clearmodifiers 1
  sleep 3
  import -display :99 -window root -silent client-smoke/screenshots/97-jar-before.png
  pactl list short sinks | grep -Fq 'tc_jar_sink'
  timeout --signal=TERM 2s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/jar-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/jar-event.pcm &
  capture_pid=$!
  sleep 0.6
  xdotool click 1
  sleep 5
  wait "$capture_pid" || test "$?" -eq 124
  import -display :99 -window root -silent client-smoke/screenshots/98-jar-after.png
  send_chat '/execute unless block 250 151 251 thaumcraft:jar_normal run say JAR_REAL_BLOCK_BROKEN'
  if ! grep -Fq 'JAR_REAL_BLOCK_BROKEN' client-smoke/client.log; then
    echo 'JAR_REAL_BREAK_MISSING=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'JAR_REAL_BLOCK_BREAK=PASS' | tee -a client-smoke-status.txt
  python3 tools/match_jar_audio_pcm.py client-smoke/jar-baseline.pcm client-smoke/jar-event.pcm src/main/resources/assets/thaumcraft/sounds | tee client-smoke/jar-source-match.txt
  if grep -Eiq 'Unknown sound event.*thaumcraft:jar|Unable to load sound.*thaumcraft:jar|Missing sound.*thaumcraft:jar|Exception in thread "Render thread"|Mod loading has failed' client-smoke/client.log; then
    echo 'JAR_REAL_CLIENT_SOUND_ERROR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'JAR_REAL_BREAK_AND_ORIGINAL_PCM=PASS' | tee -a client-smoke-status.txt
fi
'''
sys.stdout.write(head + body)
