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

  # BETA26 BlockJar: shift-right-click on the labelled face plays SoundsTC.page
  # locally; the server removes the filter and drops one blank label.
  send_chat '/setblock 250 151 251 thaumcraft:jar_normal{AspectFilter:"aer",Amount:0s,facing:3b}'
  send_chat '/execute if data block 250 151 251 {AspectFilter:"aer",Amount:0s,facing:3b} run say JAR_LABEL_FILTER_READY'
  if ! grep -Fq 'JAR_LABEL_FILTER_READY' client-smoke/client.log; then
    echo 'JAR_LABEL_FILTER_SETUP_FAILED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  send_chat '/item replace entity @p hotbar.0 with minecraft:air'
  xdotool key --clearmodifiers 1
  pactl list short sinks | grep -Fq 'tc_jar_sink'

  # First click the wrong face. It must retain the filter and must not play
  # either original page clip. Amount=0 keeps the unrelated empty-jar spill
  # path from mutating this label-only fixture.
  # Stand north of the jar and look south: the ray hits NORTH, not configured SOUTH (facing=3).
  send_chat '/tp @p 250.5 150 249.5 0 0'
  echo 'JAR_LABEL_WRONG_FACE_EXPECTED=NORTH' | tee -a client-smoke-status.txt
  sleep 2
  timeout --signal=TERM 2s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/label-wrong-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 4s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/label-wrong-event.pcm &
  capture_pid=$!
  sleep 0.6
  xdotool keydown Shift_L
  sleep 0.15
  xdotool click 3
  sleep 0.15
  xdotool keyup Shift_L
  sleep 3
  wait "$capture_pid" || test "$?" -eq 124
  send_chat '/execute if data block 250 151 251 {AspectFilter:"aer"} run say JAR_LABEL_WRONG_SIDE_PRESERVED'
  if ! grep -Fq 'JAR_LABEL_WRONG_SIDE_PRESERVED' client-smoke/client.log; then
    echo 'JAR_LABEL_WRONG_SIDE_MUTATED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  python3 - <<'PY'
from pathlib import Path
import sys
sys.path.insert(0, "tools")
from match_page_audio_pcm import check
try:
    check(
        Path("client-smoke/label-wrong-baseline.pcm"),
        Path("client-smoke/label-wrong-event.pcm"),
        Path("src/main/resources/assets/thaumcraft/sounds"),
    )
except AssertionError:
    print("JAR_LABEL_WRONG_SIDE_NO_PAGE_SOUND=PASS")
else:
    raise SystemExit("Wrong-side label interaction unexpectedly played the page event")
PY

  # Then approach the configured SOUTH face (facing=3) and remove the label.
  # Stay outside the one-block item pickup radius while remaining within interaction reach.
  # Stand south of the jar and look north: the ray hits configured SOUTH (facing=3).
  send_chat '/tp @p 250.5 150 253.5 180 0'
  echo 'JAR_LABEL_CONFIGURED_FACE=SOUTH' | tee -a client-smoke-status.txt
  sleep 2
  timeout --signal=TERM 2s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/label-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/label-event.pcm &
  capture_pid=$!
  sleep 0.6
  xdotool keydown Shift_L
  sleep 0.15
  xdotool click 3
  sleep 0.15
  xdotool keyup Shift_L
  sleep 4
  wait "$capture_pid" || test "$?" -eq 124
  import -display :99 -window root -silent client-smoke/screenshots/99-jar-label-removed.png
  send_chat '/execute unless data block 250 151 251 {AspectFilter:"aer"} run say JAR_LABEL_SERVER_FILTER_REMOVED'
  send_chat '/execute if entity @e[type=minecraft:item,distance=..3,nbt={Item:{id:"thaumcraft:label"}}] run say JAR_LABEL_SERVER_BLANK_DROP_ENTITY_PRESENT'
  send_chat '/execute if items entity @p hotbar.* thaumcraft:label run say JAR_LABEL_SERVER_BLANK_DROP_INVENTORY_PRESENT'
  if ! grep -Fq 'JAR_LABEL_SERVER_FILTER_REMOVED' client-smoke/client.log; then
    echo 'JAR_LABEL_SERVER_FILTER_NOT_REMOVED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  if grep -Eq 'JAR_LABEL_SERVER_BLANK_DROP_(ENTITY|INVENTORY)_PRESENT' client-smoke/client.log; then
    echo 'JAR_LABEL_SERVER_BLANK_DROP_OR_PICKUP_PRESENT=PASS' | tee -a client-smoke-status.txt
  else
    echo 'JAR_LABEL_SERVER_BLANK_DROP_MISSING=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  # The real drop may already be picked up; clear both representations before
  # the next shift-empty-hand scenario so the player definitely holds nothing.
  send_chat '/kill @e[type=minecraft:item,nbt={Item:{id:"thaumcraft:label"}}]'
  send_chat '/clear @p thaumcraft:label'
  python3 tools/match_page_audio_pcm.py client-smoke/label-baseline.pcm client-smoke/label-event.pcm src/main/resources/assets/thaumcraft/sounds | tee client-smoke/label-source-match.txt
  echo 'JAR_LABEL_REMOVE_AND_ORIGINAL_PAGE_PCM=PASS' | tee -a client-smoke-status.txt

  # M42: BETA26 fallback branch when a labelled jar is clicked from the wrong face.
  # The original empties its contents, retains the label, and plays jar + bottle-fill.
  # Remove the previous M41 jar first: /setblock cannot replace a block with itself,
  # so repeating the same block ID would leave the old zero-amount block entity intact.
  send_chat '/setblock 250 151 251 minecraft:air'
  send_chat '/execute unless block 250 151 251 thaumcraft:jar_normal run say JAR_EMPTY_FIXTURE_CLEARED'
  if ! grep -Fq 'JAR_EMPTY_FIXTURE_CLEARED' client-smoke/client.log; then
    echo 'JAR_EMPTY_FIXTURE_CLEAR_FAILED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  send_chat '/setblock 250 151 251 thaumcraft:jar_normal{Aspect:"aer",AspectFilter:"aer",Amount:12s,facing:3b}'
  send_chat '/execute if data block 250 151 251 {Aspect:"aer",AspectFilter:"aer",Amount:12s,facing:3b} run say JAR_EMPTY_FIXTURE_READY'
  if ! grep -Fq 'JAR_EMPTY_FIXTURE_READY' client-smoke/client.log; then
    echo 'JAR_EMPTY_FIXTURE_SETUP_FAILED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  send_chat '/tp @p 250.5 150 249.5 0 0'
  echo 'JAR_EMPTY_WRONG_FACE_EXPECTED=NORTH' | tee -a client-smoke-status.txt
  sleep 2
  timeout --signal=TERM 2s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/empty-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/empty-event.pcm &
  capture_pid=$!
  sleep 0.6
  xdotool keydown Shift_L
  sleep 0.15
  xdotool click 3
  sleep 0.15
  xdotool keyup Shift_L
  sleep 3
  wait "$capture_pid" || test "$?" -eq 124
  send_chat '/execute if data block 250 151 251 {Aspect:"aer",AspectFilter:"aer",Amount:0s} run say JAR_EMPTY_SERVER_RESET_FILTER_RETAINED'
  if ! grep -Fq 'JAR_EMPTY_SERVER_RESET_FILTER_RETAINED' client-smoke/client.log; then
    echo 'JAR_EMPTY_SERVER_RESET_OR_FILTER_FAILED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  python3 tools/match_jar_audio_pcm.py client-smoke/empty-baseline.pcm client-smoke/empty-event.pcm src/main/resources/assets/thaumcraft/sounds | tee client-smoke/empty-jar-source-match.txt
  python3 tools/match_bottle_fill_audio_pcm.py client-smoke/empty-baseline.pcm client-smoke/empty-event.pcm | tee client-smoke/empty-bottle-source-match.txt
  python3 - <<'PY'
from pathlib import Path
import sys
sys.path.insert(0, "tools")
from match_page_audio_pcm import check
try:
    check(
        Path("client-smoke/empty-baseline.pcm"),
        Path("client-smoke/empty-event.pcm"),
        Path("src/main/resources/assets/thaumcraft/sounds"),
    )
except AssertionError:
    print("JAR_EMPTY_NO_PAGE_SOUND=PASS")
else:
    raise SystemExit("Wrong-face emptying unexpectedly played the label PAGE event")
PY
  echo 'JAR_EMPTY_BETA26_SOUND_PAIR_AND_SERVER_RESET=PASS' | tee -a client-smoke-status.txt

fi
'''
sys.stdout.write(head + body)
