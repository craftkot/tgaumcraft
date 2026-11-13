#!/usr/bin/env python3
"""Generate isolated real Minecraft jar SoundType and interaction PCM smoke from the verified client boot."""
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

  # PCM is signed 16-bit little-endian stereo at 48 kHz: four bytes per frame.
  # Keep only the post-action window, excluding the pre-click ambient capture.
  crop_pcm_frames() {
    local input="$1" output="$2" first="$3" last="$4"
    if (( first < 0 || last <= first )); then
      echo "PCM_CROP_INVALID_RANGE=$first..$last" >&2
      return 1
    fi
    dd if="$input" of="$output" bs=4 skip="$first" count="$((last-first))" status=none
    local expected_bytes=$(((last-first)*4))
    local actual_bytes
    actual_bytes=$(wc -c < "$output")
    if (( actual_bytes != expected_bytes )); then
      echo "PCM_CROP_SHORT=$input expected=$expected_bytes actual=$actual_bytes" >&2
      return 1
    fi
    echo "PCM_CROP=$output frames=$first..$last bytes=$actual_bytes"
  }
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

  # Positive control only: prove the same PAGE event, BLOCKS category and
  # PulseAudio monitor are audible before attributing the real interaction.
  # This /playsound control is diagnostic and is never counted as gameplay.
  send_chat '/stopsound @p'
  sleep 0.25
  pactl info > client-smoke/pulse-info.txt
  pactl list short sinks > client-smoke/pulse-sinks.txt
  pactl list short sink-inputs > client-smoke/pulse-sink-inputs.txt
  pactl list sink-inputs > client-smoke/pulse-sink-input-details.txt
  timeout --signal=TERM 2s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/page-control-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/page-control-event.pcm &
  capture_pid=$!
  sleep 0.6
  send_chat '/playsound thaumcraft:page block @p 250.5 151.5 251.5 1 1 0'
  wait "$capture_pid" || test "$?" -eq 124
  python3 tools/match_page_audio_pcm.py client-smoke/page-control-baseline.pcm client-smoke/page-control-event.pcm src/main/resources/assets/thaumcraft/sounds | tee client-smoke/page-control-source-match.txt
  echo 'JAR_PAGE_AUDIO_COMMAND_CONTROL_ONLY=PASS' | tee -a client-smoke-status.txt
  send_chat '/stopsound @p'
  sleep 0.25

  # BETA26 BlockJar: shift-right-click on the labelled face plays SoundsTC.page
  # locally; the server removes the filter and drops one blank label.
  # M40 can leave a jar/brace item nearby; clear all item entities so the
  # following server-side Item NBT read can identify only this label drop.
  send_chat '/kill @e[type=minecraft:item]'
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
  send_chat '/stopsound @p'
  sleep 0.25
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
  if ! grep -Fq 'TC_SMOKE_JAR_LABEL_CLIENT_FALLBACK_SOUND_REQUESTED=YES' client-smoke/client.log; then
    echo 'JAR_LABEL_WRONG_SIDE_CLIENT_FALLBACK_NOT_OBSERVED=YES' | tee -a client-smoke-status.txt
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
  send_chat '/stopsound @p'
  sleep 0.25
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
  # Query the actual nearest item entity's serialized Item compound instead
  # of relying on a legacy NBT selector predicate. If it was picked up,
  # inspect the player's full Inventory compound as a separate fallback.
  send_chat '/data get entity @e[type=minecraft:item,sort=nearest,limit=1] Item'
  send_chat '/data get entity @p Inventory'
  if ! grep -Fq 'JAR_LABEL_SERVER_FILTER_REMOVED' client-smoke/client.log; then
    echo 'JAR_LABEL_SERVER_FILTER_NOT_REMOVED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  if ! grep -Fq 'TC_SMOKE_JAR_LABEL_CLIENT_PAGE_SOUND_REQUESTED=YES' client-smoke/client.log; then
    echo 'JAR_LABEL_CONFIGURED_FACE_CLIENT_PAGE_BRANCH_NOT_OBSERVED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  if grep -Eq '(^|[^[:alnum:]_])id:[[:space:]]*"thaumcraft:label"' client-smoke/client.log; then
    echo 'JAR_LABEL_SERVER_BLANK_DROP_OR_PICKUP_PRESENT=PASS source=entity-or-inventory-nbt' | tee -a client-smoke-status.txt
  else
    echo 'JAR_LABEL_SERVER_BLANK_DROP_MISSING=YES' | tee -a client-smoke-status.txt
    tail -80 client-smoke/client.log >&2
    exit 1
  fi
  # The real drop may already be picked up; clear both representations before
  # the next shift-empty-hand scenario so the player definitely holds nothing.
  send_chat '/kill @e[type=minecraft:item]'
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
  send_chat '/stopsound @p'
  sleep 0.25
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
  crop_pcm_frames client-smoke/empty-event.pcm client-smoke/empty-event-window.pcm 31200 117600
  echo 'JAR_EMPTY_POST_CLICK_WINDOW=PASS' | tee -a client-smoke-status.txt
  send_chat '/execute if data block 250 151 251 {Aspect:"aer",AspectFilter:"aer",Amount:0s} run say JAR_EMPTY_SERVER_RESET_FILTER_RETAINED'
  if ! grep -Fq 'JAR_EMPTY_SERVER_RESET_FILTER_RETAINED' client-smoke/client.log; then
    echo 'JAR_EMPTY_SERVER_RESET_OR_FILTER_FAILED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  python3 tools/match_jar_audio_pcm.py client-smoke/empty-baseline.pcm client-smoke/empty-event-window.pcm src/main/resources/assets/thaumcraft/sounds | tee client-smoke/empty-jar-source-match.txt
  python3 tools/match_bottle_fill_audio_pcm.py client-smoke/empty-baseline.pcm client-smoke/empty-event-window.pcm | tee client-smoke/empty-bottle-source-match.txt
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


  # M44: original BETA26 BlockJar brace application plays SoundsTC.key on the client.
  send_chat '/setblock 250 151 251 minecraft:air'
  send_chat '/setblock 250 151 251 thaumcraft:jar_normal'
  send_chat '/execute if block 250 151 251 thaumcraft:jar_normal run say JAR_BRACE_FIXTURE_READY'
  if ! grep -Fq 'JAR_BRACE_FIXTURE_READY' client-smoke/client.log; then
    echo 'JAR_BRACE_FIXTURE_SETUP_FAILED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  send_chat '/gamemode survival @p'
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:jar_brace'
  send_chat '/execute if items entity @p hotbar.0 thaumcraft:jar_brace run say JAR_BRACE_ITEM_READY'
  if ! grep -Fq 'JAR_BRACE_ITEM_READY' client-smoke/client.log; then
    echo 'JAR_BRACE_ITEM_MISSING=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  send_chat '/tp @p 250.5 150 250.5 0 0'
  xdotool key --clearmodifiers 1
  sleep 2
  send_chat '/stopsound @p'
  sleep 0.25
  timeout --signal=TERM 2s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/key-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/key-event.pcm &
  capture_pid=$!
  sleep 0.6
  xdotool click 3
  sleep 3
  wait "$capture_pid" || test "$?" -eq 124
  import -display :99 -window root -silent client-smoke/screenshots/101-jar-braced.png
  send_chat '/execute if data block 250 151 251 {blocked:1b} run say JAR_BRACE_SERVER_APPLIED'
  send_chat '/execute unless items entity @p hotbar.0 thaumcraft:jar_brace run say JAR_BRACE_ITEM_CONSUMED'
  if ! grep -Fq 'JAR_BRACE_SERVER_APPLIED' client-smoke/client.log; then
    echo 'JAR_BRACE_SERVER_STATE_MISSING=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  if ! grep -Fq 'JAR_BRACE_ITEM_CONSUMED' client-smoke/client.log; then
    echo 'JAR_BRACE_ITEM_NOT_CONSUMED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  python3 tools/match_key_audio_pcm.py client-smoke/key-baseline.pcm client-smoke/key-event.pcm src/main/resources/assets/thaumcraft/sounds | tee client-smoke/key-source-match.txt
  echo 'JAR_BRACE_KEY_INTERACTION_AND_ORIGINAL_PCM=PASS' | tee -a client-smoke-status.txt

  # M43: exercise the four SoundType hooks not covered by the earlier break test.
  # Use a real BlockItem placement, survival mining tap, grounded movement across
  # a narrow but continuous jar row, and a real fall onto the jar collision top.
  # The block shape is the original 10x10x12 px (0.625x0.75x0.625); the row gap
  # (0.375) is smaller than the player's 0.6-block width, so the player remains
  # supported while the block under the feet changes between adjacent jars.
  send_chat '/gamemode survival @p'
  send_chat '/fill 249 149 249 251 149 255 minecraft:stone'
  send_chat '/fill 249 150 249 251 155 255 minecraft:air'
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:jar_normal'
  send_chat '/execute if items entity @p hotbar.0 thaumcraft:jar_normal run say JAR_PLACE_ITEM_READY'
  if ! grep -Fq 'JAR_PLACE_ITEM_READY' client-smoke/client.log; then
    echo 'JAR_PLACE_ITEM_MISSING=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  send_chat '/tp @p 250.5 150 250.5 0 47'
  sleep 1
  send_chat '/stopsound @p'
  sleep 0.25
  timeout --signal=TERM 2s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/place-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/place-event.pcm &
  capture_pid=$!
  sleep 0.6
  xdotool click 3
  sleep 0.4
  wait "$capture_pid" || test "$?" -eq 124
  crop_pcm_frames client-smoke/place-event.pcm client-smoke/place-event-window.pcm 26400 120000
  import -display :99 -window root -silent client-smoke/screenshots/100-jar-placed.png
  send_chat '/execute if block 250 150 252 thaumcraft:jar_normal run say JAR_PLACE_BLOCK_PRESENT'
  if ! grep -Fq 'JAR_PLACE_BLOCK_PRESENT' client-smoke/client.log; then
    echo 'JAR_PLACE_BLOCK_MISSING=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  python3 tools/match_jar_audio_pcm.py client-smoke/place-baseline.pcm client-smoke/place-event-window.pcm src/main/resources/assets/thaumcraft/sounds | tee client-smoke/place-source-match.txt
  echo 'JAR_PLACE_ORIGINAL_PCM=PASS' | tee -a client-smoke-status.txt

  # M43 HIT: SoundType hit audio belongs to the continuing block-break loop;
  # a 0.12s tap can end before that tick-driven loop emits anything. Hold for
  # about six client ticks (0.30s), still below the 0.3-hardness jar's bare-hand
  # break time. The authoritative server block check below rejects any break.
  send_chat '/item replace entity @p hotbar.0 with minecraft:air'
  # Open chat to release the captured pointer, move it to the actual Minecraft
  # window center, then submit the teleport. Closing chat re-grabs the pointer
  # at the crosshair; moving it after chat closes can rotate the camera instead.
  hit_geometry="$(xdotool getwindowgeometry --shell "$WIN")"
  eval "$hit_geometry"
  jar_hit_center_x=$((X + WIDTH / 2))
  jar_hit_center_y=$((Y + HEIGHT / 2))
  xdotool key --clearmodifiers t
  sleep 0.2
  xdotool mousemove --sync "$jar_hit_center_x" "$jar_hit_center_y"
  xdotool type --clearmodifiers --delay 24 '/tp @p 250.5 150 250.5 0 35'
  xdotool key --clearmodifiers Return
  sleep 4
  echo "JAR_HIT_POINTER_CENTER=$jar_hit_center_x,$jar_hit_center_y" | tee -a client-smoke-status.txt
  import -display :99 -window root -silent client-smoke/screenshots/100a-jar-hit-aim.png
  send_chat '/stopsound @p'
  sleep 0.25
  timeout --signal=TERM 2s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/hit-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/hit-event.pcm &
  capture_pid=$!
  sleep 0.6
  xdotool mousedown 1
  # Capture after the full 0.30s hold; the earlier 0.18s screenshot sampled
  # before the six-tick window described above had elapsed.
  sleep 0.30
  echo 'JAR_HIT_CAPTURE_AFTER_0_30S=YES' | tee -a client-smoke-status.txt
  import -display :99 -window root -silent client-smoke/screenshots/100b-jar-hit-damage-overlay.png
  xdotool mouseup 1
  sleep 0.4
  wait "$capture_pid" || test "$?" -eq 124

  # A server-side "jar still exists" check cannot prove that the client attack
  # reached the block. Require the real block-damage overlay in a fixed ROI
  # around the crosshair before interpreting a silent PCM capture as a sound bug.
  jar_hit_roi_x=$((jar_hit_center_x - 110))
  jar_hit_roi_y=$((jar_hit_center_y - 110))
  convert client-smoke/screenshots/100a-jar-hit-aim.png \
    -crop "220x220+$jar_hit_roi_x+$jar_hit_roi_y" +repage \
    client-smoke/hit-aim-roi.png
  convert client-smoke/screenshots/100b-jar-hit-damage-overlay.png \
    -crop "220x220+$jar_hit_roi_x+$jar_hit_roi_y" +repage \
    client-smoke/hit-damage-roi.png
  jar_hit_damage_pixels="$(compare -metric AE client-smoke/hit-aim-roi.png \
    client-smoke/hit-damage-roi.png null: 2>&1 || true)"
  jar_hit_damage_pixels="${jar_hit_damage_pixels%% *}"
  if ! [[ "$jar_hit_damage_pixels" =~ ^[0-9]+$ ]] || (( jar_hit_damage_pixels < 40 )); then
    echo "JAR_HIT_CLIENT_DAMAGE_OVERLAY_MISSING=YES pixels=$jar_hit_damage_pixels" | tee -a client-smoke-status.txt
    exit 1
  fi
  echo "JAR_HIT_CLIENT_DAMAGE_OVERLAY=PASS pixels=$jar_hit_damage_pixels" | tee -a client-smoke-status.txt

  crop_pcm_frames client-smoke/hit-event.pcm client-smoke/hit-event-window.pcm 26400 120000
  send_chat '/execute if block 250 150 252 thaumcraft:jar_normal run say JAR_HIT_BLOCK_RETAINED'
  if ! grep -Fq 'JAR_HIT_BLOCK_RETAINED' client-smoke/client.log; then
    echo 'JAR_HIT_BROKE_OR_MISSED_BLOCK=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  python3 tools/match_jar_audio_pcm.py client-smoke/hit-baseline.pcm client-smoke/hit-event-window.pcm src/main/resources/assets/thaumcraft/sounds | tee client-smoke/hit-source-match.txt
  echo 'JAR_HIT_ORIGINAL_PCM=PASS' | tee -a client-smoke-status.txt

  # Rebuild a five-block row. Its collision tops overlap the player's bounding
  # box across the 0.375 gaps; movement must traverse several jar block positions
  # while the server confirms the player's feet stay at the 0.75-block jar top.
  send_chat '/fill 250 150 251 250 150 255 minecraft:air'
  send_chat '/fill 250 150 251 250 150 255 thaumcraft:jar_normal'
  send_chat '/execute if block 250 150 251 thaumcraft:jar_normal if block 250 150 255 thaumcraft:jar_normal run say JAR_SOUND_ROW_READY'
  if ! grep -Fq 'JAR_SOUND_ROW_READY' client-smoke/client.log; then
    echo 'JAR_SOUND_ROW_SETUP_FAILED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  send_chat '/tp @p 250.5 154 251.5 0 0'
  send_chat '/execute if entity @p[x=250.4,y=150.7,z=251,dx=0.2,dy=0.2,dz=1] run say JAR_STEP_STANDING_READY'
  if ! grep -Fq 'JAR_STEP_STANDING_READY' client-smoke/client.log; then
    echo 'JAR_STEP_NOT_STANDING_ON_JAR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  send_chat '/stopsound @p'
  sleep 0.25
  timeout --signal=TERM 2s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/step-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/step-event.pcm &
  capture_pid=$!
  sleep 0.4
  xdotool keydown w
  sleep 0.7
  xdotool keyup w
  wait "$capture_pid" || test "$?" -eq 124
  crop_pcm_frames client-smoke/step-event.pcm client-smoke/step-event-window.pcm 16800 120000
  import -display :99 -window root -silent client-smoke/screenshots/101-jar-step.png
  send_chat '/execute if entity @p[x=250.4,y=150.7,z=253,dx=0.2,dy=0.2,dz=2] run say JAR_STEP_MOVED_ON_ROW'
  if ! grep -Fq 'JAR_STEP_MOVED_ON_ROW' client-smoke/client.log; then
    echo 'JAR_STEP_MOVEMENT_FAILED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  python3 tools/match_jar_audio_pcm.py client-smoke/step-baseline.pcm client-smoke/step-event-window.pcm src/main/resources/assets/thaumcraft/sounds | tee client-smoke/step-source-match.txt
  echo 'JAR_STEP_ORIGINAL_PCM=PASS' | tee -a client-smoke-status.txt

  # A real survival fall from y=155 lands 4.25 blocks above the jar's 0.75 top.
  # The server position check proves the entity actually landed on a jar.
  send_chat '/stopsound @p'
  sleep 0.25
  timeout --signal=TERM 2s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/fall-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 6s parec --device=tc_jar_sink.monitor --format=s16le --rate=48000 --channels=2 --raw > client-smoke/fall-event.pcm &
  capture_pid=$!
  sleep 0.4
  send_chat '/tp @p 250.5 155 253.5 0 90'
  wait "$capture_pid" || test "$?" -eq 124
  crop_pcm_frames client-smoke/fall-event.pcm client-smoke/fall-event-window.pcm 16800 144000
  import -display :99 -window root -silent client-smoke/screenshots/102-jar-fall.png
  send_chat '/execute if entity @p[x=250.4,y=150.7,z=253,dx=0.2,dy=0.2,dz=1] run say JAR_FALL_LANDED_ON_JAR'
  if ! grep -Fq 'JAR_FALL_LANDED_ON_JAR' client-smoke/client.log; then
    echo 'JAR_FALL_DID_NOT_LAND_ON_JAR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  python3 tools/match_jar_audio_pcm.py client-smoke/fall-baseline.pcm client-smoke/fall-event-window.pcm src/main/resources/assets/thaumcraft/sounds | tee client-smoke/fall-source-match.txt
  echo 'JAR_FALL_ORIGINAL_PCM=PASS' | tee -a client-smoke-status.txt

  echo 'JAR_SOUNDTYPE_PLACE_STEP_HIT_FALL=PASS' | tee -a client-smoke-status.txt

fi
'''
sys.stdout.write(head + body)
