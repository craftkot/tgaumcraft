#!/usr/bin/env python3
"""Generate a focused real Minecraft Flux Rift ambient-sound and Crucible FX smoke."""
from pathlib import Path
import subprocess
import sys

# Run the loader-neutral BETA26 froth contract before generating the real-client script.
subprocess.run(["bash", "tools/test_crucible_froth_rules.sh"], check=True, stdout=sys.stderr, stderr=sys.stderr)
subprocess.run(["python3", "tools/m16/test_rift_shader_camera.py"], check=True, stdout=sys.stderr, stderr=sys.stderr)
subprocess.run(["python3", "tools/verify_crucible_bubble_screenshots.py", "--self-test"],
               check=True, stdout=sys.stderr, stderr=sys.stderr)

full_source = Path("tools/client_smoke.sh").read_text()
source = full_source
marker = "  # Focused alchemy-only regression: actual item + coal enters a real"
if source.count(marker) != 1:
    raise SystemExit("Shared client smoke anchor changed; refuse unsafe generation")
source = source.split(marker, 1)[0]
old_xdg = "export XDG_RUNTIME_DIR=/tmp/thaumcraft-client-runtime"
new_xdg = 'export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/thaumcraft-client-runtime}"'
if source.count(old_xdg) != 1:
    raise SystemExit("Shared XDG runtime fixture changed; refuse unsafe PulseAudio socket rewrite")
source = source.replace(old_xdg, new_xdg)

old_options = "printf 'guiScale:2\\n' > run/options.txt"
new_options = "printf 'guiScale:2\\nsoundCategory_music:0.0\\nsoundCategory_ambient:0.0\\nsoundCategory_blocks:1.0\\nsoundCategory_neutral:1.0\\n' > run/options.txt"
if source.count(old_options) != 1:
    raise SystemExit("Minecraft options fixture changed; refuse unsafe rewrite")
source = source.replace(old_options, new_options)

old_send = """  send_chat() {
    xdotool key --clearmodifiers t
    sleep 1
    xdotool type --clearmodifiers --delay 24 "$1"
    xdotool key --clearmodifiers Return
    sleep 4
  }"""
new_send = """  activate_minecraft_client() {
    xdotool windowactivate --sync "$WIN"
    xdotool windowraise "$WIN"
    xdotool windowfocus --sync "$WIN"
    xdotool mousemove --window "$WIN" 512 384
    sleep 0.25
    local active focused game_pid active_pid focused_pid
    active="$(xdotool getactivewindow 2>/dev/null || true)"
    focused="$(xdotool getwindowfocus 2>/dev/null || true)"
    game_pid="$(xdotool getwindowpid "$WIN" 2>/dev/null || true)"
    active_pid="$(xdotool getwindowpid "$active" 2>/dev/null || true)"
    focused_pid="$(xdotool getwindowpid "$focused" 2>/dev/null || true)"
    if [ -z "$game_pid" ] || [ "$active_pid" != "$game_pid" ] || [ "$focused_pid" != "$game_pid" ]; then
      echo "RIFT_INPUT_FOCUS=FAIL window=$WIN active=$active focus=$focused" | tee -a client-smoke-status.txt
      exit 1
    fi
  }

  ensure_minecraft_command_channel() {
    if [ "${TC_RIFT_COMMAND_CHANNEL_READY:-0}" = "1" ]; then
      return 0
    fi
    xdotool key --clearmodifiers t
    sleep 0.35
    import -display :99 -window root -silent client-smoke/screenshots/rift-command-chat-open.png
    activate_minecraft_client
    xdotool type --clearmodifiers --delay 24 '/say TC_RIFT_COMMAND_CHANNEL_READY'
    sleep 0.15
    import -display :99 -window root -silent client-smoke/screenshots/rift-command-text-before-submit.png
    activate_minecraft_client
    xdotool key --clearmodifiers Return
    local ready=0
    for _ in $(seq 1 40); do
      if grep -Fq 'TC_RIFT_COMMAND_CHANNEL_READY' run/logs/latest.log; then
        ready=1
        break
      fi
      sleep 0.25
    done
    if [ "$ready" -ne 1 ]; then
      echo 'RIFT_COMMAND_CHANNEL=FAIL' | tee -a client-smoke-status.txt
      tail -80 run/logs/latest.log >&2 || true
      exit 1
    fi
    export TC_RIFT_COMMAND_CHANNEL_READY=1
    echo 'RIFT_COMMAND_CHANNEL=PASS' | tee -a client-smoke-status.txt
  }

  send_chat() {
    activate_minecraft_client
    ensure_minecraft_command_channel
    xdotool key --clearmodifiers t
    sleep 1
    xdotool type --clearmodifiers --delay 24 "$1"
    xdotool key --clearmodifiers Return
    sleep 4
  }

  # Fast chat is used only when each command is followed by a fresh server marker.
  # A 0.1s open delay dropped leading "/t" characters on the real client.
  send_chat_fast() {
    activate_minecraft_client
    ensure_minecraft_command_channel
    xdotool key --clearmodifiers t
    sleep 0.5
    xdotool type --clearmodifiers --delay 5 "$1"
    xdotool key --clearmodifiers Return
    sleep 0.35
  }

  wait_for_server_marker() {
    local marker="$1"
    for _ in $(seq 1 40); do
      if grep -Fq "$marker" run/logs/latest.log; then
        return 0
      fi
      sleep 0.25
    done
    echo "RIFT_SERVER_MARKER=FAIL missing=$marker" | tee -a client-smoke-status.txt
    tail -100 run/logs/latest.log >&2 || true
    return 1
  }"""
if source.count(old_send) != 1:
    raise SystemExit("Shared chat helper changed; refuse unverified command-driver replacement")
source = source.replace(old_send, new_send)
if source.count("export ALSOFT_DRIVERS=null") != 1:
    raise SystemExit("OpenAL driver fixture changed; refuse unverified audio routing")
audio_driver_setup = """export ALSOFT_DRIVERS=pulse
export ALSOFT_LOGLEVEL=3
export ALSOFT_LOGFILE="$PWD/client-smoke/openal.log"
printf '%s\\n' '[general]' 'drivers=pulse' > "$HOME/.alsoftrc"
echo "RIFT_AUDIO_ENV=ALSOFT_DRIVERS=$ALSOFT_DRIVERS PULSE_SERVER=$PULSE_SERVER PULSE_SINK=$PULSE_SINK XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR" | tee -a client-smoke-status.txt
"""
source = source.replace("export ALSOFT_DRIVERS=null", audio_driver_setup)
if source.count("export ALSOFT_DRIVERS=pulse") != 1 or "export ALSOFT_DRIVERS=null" in source:
    raise SystemExit("Generated client did not select PulseAudio exclusively")
openal_ready_check = """for i in $(seq 1 240); do
  if grep -Fq 'OpenAL initialized on device' client-smoke/client.log 2>/dev/null \\
      && grep -Fq '[ALSOFT] (II) Initialized backend "pulse"' client-smoke/openal.log 2>/dev/null \\
      && grep -Fq '[ALSOFT] (II) Connecting to "tc_rift_sink"' client-smoke/openal.log 2>/dev/null; then break; fi
  sleep 0.25
done
openal_device_line="$(grep -F 'OpenAL initialized on device' client-smoke/client.log 2>/dev/null | tail -n 1 || true)"
if [ -z "$openal_device_line" ]; then
  echo 'RIFT_OPENAL_DEVICE=FAIL no-device-log' | tee -a client-smoke-status.txt
  cat client-smoke/openal.log >&2 || true
  exit 1
fi
if ! grep -Fq '[ALSOFT] (II) Initialized backend "pulse"' client-smoke/openal.log 2>/dev/null; then
  echo "RIFT_OPENAL_DEVICE=FAIL PulseAudio backend not initialized: $openal_device_line" | tee -a client-smoke-status.txt
  cat client-smoke/openal.log >&2 || true
  exit 1
fi
if ! grep -Fq '[ALSOFT] (II) Connecting to "tc_rift_sink"' client-smoke/openal.log 2>/dev/null; then
  echo "RIFT_OPENAL_DEVICE=FAIL PulseAudio did not connect to tc_rift_sink: $openal_device_line" | tee -a client-smoke-status.txt
  cat client-smoke/openal.log >&2 || true
  exit 1
fi
echo "RIFT_OPENAL_DEVICE=PASS backend=pulse sink=tc_rift_sink client_device=$openal_device_line" | tee -a client-smoke-status.txt
"""
window_ready_anchor = """if [ -z "$WIN" ]; then
  echo 'CLIENT_WINDOW=NO' | tee -a client-smoke-status.txt
  tail -100 client-smoke/client.log
  exit 1
fi
xdotool windowactivate --sync "$WIN" || true"""
if source.count(window_ready_anchor) != 1:
    raise SystemExit("Minecraft window-ready anchor changed; refuse OpenAL readiness placement")
source = source.replace(
    window_ready_anchor,
    window_ready_anchor.replace(
        'fi\nxdotool windowactivate --sync "$WIN" || true',
        'fi\n' + openal_ready_check + '\nxdotool windowactivate --sync "$WIN" || true'
    )
)

suffix_marker = "\nelse\n  echo 'INTEGRATED_WORLD_START_LOG=NOT_CONFIRMED'"
if full_source.count(suffix_marker) != 1:
    raise SystemExit("Shared integrated-world fallback anchor changed")
suffix = full_source[full_source.index(suffix_marker):]
suffix = suffix.replace(
    "echo 'PASS: real Minecraft client world, overhead crucible PNGs captured'",
    "echo 'PASS: real Minecraft client Flux Rift ambient sound captured'",
)
suffix = suffix.replace(
    "echo 'PASS: real Minecraft client world, overhead crucible PNGs captured'",
    "echo 'PASS: real Minecraft client Flux Rift ambient sound captured'",
)

probe = r'''
  query_game_time() {
    local before_count after_count value
    before_count="$(grep -Ec 'The time is [0-9]+' run/logs/latest.log || true)"
    send_chat '/time query gametime'
    after_count="$(grep -Ec 'The time is [0-9]+' run/logs/latest.log || true)"
    if [ "$after_count" -le "$before_count" ]; then
      echo 'RIFT_GAME_TIME_QUERY=FAIL no fresh server response' >> client-smoke-status.txt
      tail -100 run/logs/latest.log >&2 || true
      return 1
    fi
    value="$(grep -Eo 'The time is [0-9]+' run/logs/latest.log | tail -n 1 | grep -Eo '[0-9]+$' || true)"
    if ! [[ "$value" =~ ^[0-9]+$ ]]; then
      echo 'RIFT_GAME_TIME_QUERY=FAIL response was not numeric' >> client-smoke-status.txt
      tail -100 run/logs/latest.log >&2 || true
      return 1
    fi
    echo "RIFT_GAME_TIME_QUERY=PASS value=$value" >> client-smoke-status.txt
    printf '%s\n' "$value"
  }

  pactl list short sinks | grep -Fq 'tc_rift_sink'
  timeout --signal=TERM 3s parec --device=tc_rift_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/rift-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 30s parec --device=tc_rift_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/rift-positive-control.pcm &
  rift_control_pid=$!
  sleep 0.25
  send_chat '/playsound thaumcraft:evilportal neutral @p ~ ~ ~ 1 1'
  wait "$rift_control_pid" || test "$?" -eq 124
  python3 tools/match_rift_audio_pcm.py \
    client-smoke/rift-baseline.pcm client-smoke/rift-positive-control.pcm \
    src/main/resources/assets/thaumcraft/sounds/evilportal.ogg \
    | tee client-smoke/rift-positive-control-match.txt
  echo 'RIFT_EVILPORTAL_POSITIVE_CONTROL=PASS' | tee -a client-smoke-status.txt
  # Keep the sound listener at the source. The visual probe uses a valid size-30
  # BETA26 Rift and a side-on camera two blocks east, then returns here for audio.
  rift_x=3.5
  rift_y=150
  rift_z=12.5
  rift_visual_x=5.5
  rift_visual_yaw=90
  rift_visual_pitch=39
  rift_size=30
  # Teleport before filling: /fill rejects an unloaded target chunk.
  # The player is already falling when the marker command reaches the server;
  # an exact one-block distance selector can therefore miss a successful tp.
  # This X/Z column plus Y=150..400 proves the player reached the high platform
  # area while still excluding the normal world spawn.
  send_chat_fast "/tp @p $rift_x 300 $rift_z 0 0"
  send_chat_fast '/execute if entity @p[x=3,y=150,z=12,dx=1,dy=250,dz=1] run say TC_RIFT_HIGH_TELEPORT_PRESENT'
  if ! wait_for_server_marker 'TC_RIFT_HIGH_TELEPORT_PRESENT'; then
    echo 'RIFT_HIGH_TELEPORT=FAIL player did not reach the safe high position' | tee -a client-smoke-status.txt
    exit 1
  fi
  # send_chat submits after one second; its four-second delay is post-submit,
  # so the barrier is placed long before the player can fall through Y=149.
  send_chat "/fill 1 149 10 5 149 14 minecraft:barrier"
  send_chat '/execute if block 3 149 12 minecraft:barrier run say TC_RIFT_PLATFORM_PRESENT'
  if ! grep -Fq 'TC_RIFT_PLATFORM_PRESENT' run/logs/latest.log; then
    echo 'RIFT_LISTENER_PLATFORM=FAIL barrier block was not placed after teleport' | tee -a client-smoke-status.txt
    tail -100 run/logs/latest.log >&2 || true
    exit 1
  fi
  echo "RIFT_LISTENER_PLATFORM=PASS y=149" | tee -a client-smoke-status.txt
  # Establish the exact audio-listener position first. The visual probe below
  # temporarily moves to a side-on camera; it returns here before the 300-tick sound.
  send_chat "/tp @p $rift_x $rift_y $rift_z 0 90"
  send_chat '/execute if entity @p[x=3,y=150,z=12,distance=..1] run say TC_RIFT_LISTENER_AT_SOURCE'
  if ! grep -Fq 'TC_RIFT_LISTENER_AT_SOURCE' run/logs/latest.log; then
    send_chat '/data get entity @p Pos'
    echo 'RIFT_LISTENER_AT_SOURCE=FAIL player is not at the Rift source after supported teleport' | tee -a client-smoke-status.txt
    tail -100 run/logs/latest.log >&2 || true
    exit 1
  fi
  echo "RIFT_LISTENER_AT_SOURCE=PASS x=$rift_x y=$rift_y z=$rift_z" | tee -a client-smoke-status.txt
  send_chat_fast "/tp @p $rift_visual_x $rift_y $rift_z $rift_visual_yaw $rift_visual_pitch"
  send_chat_fast '/execute if entity @p[x=5,y=150,z=12,distance=..1] run say TC_RIFT_VISUAL_CAMERA_BEFORE'
  if ! wait_for_server_marker 'TC_RIFT_VISUAL_CAMERA_BEFORE'; then
    send_chat '/data get entity @p Pos'
    echo 'RIFT_VISUAL_CAMERA_BEFORE=FAIL player did not reach the side-on view' | tee -a client-smoke-status.txt
    tail -100 run/logs/latest.log >&2 || true
    exit 1
  fi
  echo "RIFT_VISUAL_CAMERA_BEFORE=PASS x=$rift_visual_x yaw=$rift_visual_yaw pitch=$rift_visual_pitch" | tee -a client-smoke-status.txt
  sleep 0.5
  capture rift-ambient-before
  # Keep the side-on camera through the Rift spawn so the renderer's first
  # probe cannot be consumed by an edge-on frame at the entity origin.
  spawn_game_time="$(query_game_time)" || exit 1
  probe_log=client-smoke/client.log
  event_log_offset="$(wc -c < "$probe_log")"
  # Capture only the natural first event. A direct parec PID is required so
  # SIGTERM reaps the recorder itself rather than orphaning a timeout child.
  parec --device=tc_rift_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/rift-event.pcm &
  rift_capture_pid=$!
  trap 'if [ -n "${rift_capture_pid:-}" ] && kill -0 "$rift_capture_pid" 2>/dev/null; then kill -TERM "$rift_capture_pid" 2>/dev/null || true; wait "$rift_capture_pid" 2>/dev/null || true; fi; cleanup' EXIT
  sleep 0.25
  send_chat_fast "/summon thaumcraft:flux_rift $rift_x $rift_y $rift_z {RiftSeed:1234567,RiftSize:$rift_size,Stability:0.0f}"
  send_chat_fast '/execute if entity @e[type=thaumcraft:flux_rift,limit=1] run say TC_RIFT_AMBIENT_ENTITY_PRESENT'
  if ! wait_for_server_marker 'TC_RIFT_AMBIENT_ENTITY_PRESENT'; then
    echo 'RIFT_AMBIENT_ENTITY_PRESENT=FAIL' | tee -a client-smoke-status.txt
    exit 1
  fi
  # The player is still on the validated side-on camera; record a fresh
  # server-side position marker without consuming another renderer first-call.
  send_chat_fast '/execute if entity @p[x=5,y=150,z=12,distance=..1] run say TC_RIFT_VISUAL_CAMERA_AFTER'
  if ! wait_for_server_marker 'TC_RIFT_VISUAL_CAMERA_AFTER'; then
    send_chat '/data get entity @p Pos'
    echo 'RIFT_VISUAL_CAMERA_AFTER=FAIL post-spawn camera position command was not acknowledged' | tee -a client-smoke-status.txt
    tail -100 run/logs/latest.log >&2 || true
    exit 1
  fi
  echo "RIFT_VISUAL_CAMERA_AFTER=PASS x=$rift_visual_x yaw=$rift_visual_yaw pitch=$rift_visual_pitch" | tee -a client-smoke-status.txt
  sleep 0.5
  # Preserve the post-spawn frame even when the render marker gate fails.
  capture rift-ambient-entity-present
  test -s client-smoke/screenshots/rift-ambient-entity-present.png
  render_probe_log=client-smoke/client.log
  for _ in $(seq 1 40); do
    if grep -Fq 'TC_SMOKE_RIFT_SHADER_ACTIVE=YES' "$render_probe_log"; then break; fi
    sleep 0.25
  done
  shader_marker_count="$(grep -Fc 'TC_SMOKE_RIFT_SHADER_ACTIVE=YES' "$render_probe_log" || true)"
  if [ "$shader_marker_count" -ne 1 ]; then
    echo "RIFT_SHADER=FAIL expected=1 actual=$shader_marker_count" | tee -a client-smoke-status.txt
    tail -100 "$render_probe_log" >&2 || true
    exit 1
  fi
  echo 'RIFT_SHADER=PASS registered=YES sampler=Sampler0' | tee -a client-smoke-status.txt
  if ! python3 tools/m16/verify_rift_shader_camera.py "$render_probe_log" \
      --yaw-degrees "$rift_visual_yaw" \
      --pitch-degrees "$rift_visual_pitch" >> client-smoke-status.txt 2>&1; then
    tail -100 "$render_probe_log" >&2 || true
    tail -40 client-smoke-status.txt >&2 || true
    exit 1
  fi
  for _ in $(seq 1 40); do
    if grep -Fq 'TC_SMOKE_RIFT_RENDER_LAYER=PASS' "$render_probe_log"; then break; fi
    sleep 0.25
  done
  for expected in \
    'layer=0 blend=ADDITIVE depthWrite=OFF' \
    'layer=1 blend=ADDITIVE depthWrite=OFF' \
    'layer=2 blend=ADDITIVE depthWrite=OFF' \
    'layer=3 blend=ALPHA depthWrite=ON'; do
    if ! grep -Fq "$expected" "$render_probe_log"; then
      echo "RIFT_RENDER_LAYER=FAIL missing=$expected" | tee -a client-smoke-status.txt
      tail -100 "$render_probe_log" >&2 || true
      exit 1
    fi
  done
  render_marker_count="$(grep -Fc 'TC_SMOKE_RIFT_RENDER_LAYER=PASS' "$render_probe_log" || true)"
  if [ "$render_marker_count" -ne 4 ]; then
    echo "RIFT_RENDER_LAYER=FAIL expected=4 actual=$render_marker_count" | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'RIFT_RENDER_LAYER_SELECTION=PASS additive=3 alpha=1 depth_write=outer_off_core_on' | tee -a client-smoke-status.txt
  # Return to the exact source before the natural tick-300 audio event.
  send_chat_fast "/tp @p $rift_x $rift_y $rift_z 0 89"
  send_chat_fast '/execute if entity @p[x=3,y=150,z=12,distance=..1] run say TC_RIFT_LISTENER_RETURNED_AFTER_VISUAL'
  if ! wait_for_server_marker 'TC_RIFT_LISTENER_RETURNED_AFTER_VISUAL'; then
    send_chat '/data get entity @p Pos'
    echo 'RIFT_LISTENER_RETURN_TO_SOURCE=FAIL phase=after_visual' | tee -a client-smoke-status.txt
    tail -100 run/logs/latest.log >&2 || true
    exit 1
  fi
  echo 'RIFT_LISTENER_RETURN_TO_SOURCE=PASS phase=after_visual' | tee -a client-smoke-status.txt
  # BETA26 uses a 300-tick cadence. Capture only the first due event:
  # evilportal.ogg is 14.524s and can last up to 32.3s at the matcher's
  # minimum pitch (0.45), so freeze server ticks while that one clip finishes.
  rift_due_line=''
  for _ in $(seq 1 120); do
    rift_due_line="$(tail -c +$((event_log_offset + 1)) "$probe_log" | grep -F 'TC_SMOKE_RIFT_AMBIENT_DUE=YES' | head -n 1 || true)"
    if [ -n "$rift_due_line" ]; then break; fi
    sleep 0.25
  done
  if [ -z "$rift_due_line" ]; then
    echo 'RIFT_SERVER_AMBIENT_DUE=NOT_OBSERVED' | tee -a client-smoke-status.txt
    tail -120 "$probe_log" >&2 || true
    exit 1
  fi
  random_seed_line="$(tail -c +$((event_log_offset + 1)) "$probe_log" | grep -F 'TC_SMOKE_RIFT_AMBIENT_RANDOM=DETERMINISTIC seed=' | head -n 1 || true)"
  if [ -z "$random_seed_line" ]; then
    echo 'RIFT_SERVER_AMBIENT_RANDOM=FAIL deterministic smoke seed marker missing' | tee -a client-smoke-status.txt
    tail -120 "$probe_log" >&2 || true
    exit 1
  fi
  smoke_seed="${random_seed_line##*seed=}"
  if ! [[ "$smoke_seed" =~ ^[0-9]+$ ]]; then
    echo "RIFT_SERVER_AMBIENT_RANDOM=FAIL malformed seed [$smoke_seed]" | tee -a client-smoke-status.txt
    exit 1
  fi
  echo "RIFT_SERVER_AMBIENT_RANDOM=PASS seed=$smoke_seed" | tee -a client-smoke-status.txt
  server_tick_count="${rift_due_line#* tickCount=}"
  server_tick_count="${server_tick_count%% *}"
  server_game_time="${rift_due_line#* gameTime=}"
  server_game_time="${server_game_time%% *}"
  if ! [[ "$server_tick_count" =~ ^[0-9]+$ && "$server_game_time" =~ ^[0-9]+$ ]]; then
    echo "RIFT_SERVER_AMBIENT_DUE=FAIL malformed tick/time: $rift_due_line" | tee -a client-smoke-status.txt
    exit 1
  fi
  if [ "$server_tick_count" -ne 300 ]; then
    echo "RIFT_GAME_TICK_WINDOW=FAIL expected_first_tick=300 actual=$server_tick_count" | tee -a client-smoke-status.txt
    exit 1
  fi
  game_tick_delta=$((server_game_time - spawn_game_time))
  if (( game_tick_delta < 300 )); then
    echo "RIFT_GAME_TICK_WINDOW=FAIL start=$spawn_game_time end=$server_game_time delta=$game_tick_delta required=300" | tee -a client-smoke-status.txt
    exit 1
  fi
  echo "RIFT_GAME_TICK_WINDOW=PASS start=$spawn_game_time end=$server_game_time delta=$game_tick_delta entity_tick=$server_tick_count" | tee -a client-smoke-status.txt

  server_volume="${rift_due_line#* volume=}"
  server_volume="${server_volume%% *}"
  if [ -z "$server_volume" ] || ! awk -v volume="$server_volume" 'BEGIN { exit !(volume > 0.0) }'; then
    echo "RIFT_SERVER_AMBIENT_VOLUME_NONPOSITIVE=$server_volume" | tee -a client-smoke-status.txt
    echo "$rift_due_line" >&2
    exit 1
  fi
  server_distance_sqr="${rift_due_line#* nearestPlayerDistanceSqr=}"
  server_distance_sqr="${server_distance_sqr%% *}"
  if ! python3 -c 'import sys; d=float(sys.argv[1]); sys.exit(0 if 0 <= d <= 1.0 else 1)' "$server_distance_sqr"; then
    echo "RIFT_LISTENER_AT_EVENT=FAIL distanceSqr=$server_distance_sqr" | tee -a client-smoke-status.txt
    exit 1
  fi
  echo "RIFT_LISTENER_AT_EVENT=PASS distanceSqr=$server_distance_sqr" | tee -a client-smoke-status.txt
  echo "RIFT_SERVER_AMBIENT_DUE=PASS $rift_due_line" | tee -a client-smoke-status.txt

  server_call_line="$(tail -c +$((event_log_offset + 1)) "$probe_log" | grep -F 'TC_SMOKE_RIFT_AMBIENT_CALL=YES' | grep -F "tickCount=$server_tick_count" | head -n 1 || true)"
  if [ -z "$server_call_line" ]; then
    echo 'RIFT_SERVER_SOUND_CALL=NOT_CONFIRMED' | tee -a client-smoke-status.txt
    tail -120 "$probe_log" >&2 || true
    exit 1
  fi
  echo "RIFT_SERVER_SOUND_CALL=PASS $server_call_line" | tee -a client-smoke-status.txt

  server_pos="${rift_due_line#* pos=}"
  server_pos="${server_pos%% *}"
  server_pitch="${rift_due_line#* pitch=}"
  server_pitch="${server_pitch%% *}"
  client_event_line=''
  for _ in $(seq 1 40); do
    client_event_line="$(tail -c +$((event_log_offset + 1)) "$probe_log" | grep -F 'TC_SMOKE_RIFT_SOUND_CLIENT_' | grep -F "pos=$server_pos" | grep -F "volume=$server_volume" | grep -F "pitch=$server_pitch" | head -n 1 || true)"
    if [ -n "$client_event_line" ]; then break; fi
    sleep 0.25
  done
  if [ -z "$client_event_line" ]; then
    echo "RIFT_CLIENT_SOUND_EVENT=NOT_RECEIVED pos=$server_pos volume=$server_volume pitch=$server_pitch" | tee -a client-smoke-status.txt
    grep -F 'TC_SMOKE_RIFT_SOUND_CLIENT_' "$probe_log" >&2 || true
    tail -120 "$probe_log" >&2 || true
    exit 1
  fi
  echo "RIFT_CLIENT_SOUND_EVENT=PASS $client_event_line" | tee -a client-smoke-status.txt

  # Stop the 300-tick entity cadence after its first sound. Freezing server
  # ticks does not stop the client sound engine; it prevents the next 300-tick
  # event from overlapping this long, pitch-shifted source clip.
  send_chat '/tick freeze'
  tick_markers_before="$(grep -Fc 'TC_SMOKE_RIFT_TICK=YES' "$probe_log" || true)"
  due_markers_before="$(tail -c +$((event_log_offset + 1)) "$probe_log" | grep -Fc 'TC_SMOKE_RIFT_AMBIENT_DUE=YES' || true)"
  if [ "$due_markers_before" -ne 1 ]; then
    echo "RIFT_SINGLE_EVENT_CAPTURE=FAIL events_before_freeze=$due_markers_before" | tee -a client-smoke-status.txt
    exit 1
  fi
  sleep 34
  tick_markers_after="$(grep -Fc 'TC_SMOKE_RIFT_TICK=YES' "$probe_log" || true)"
  due_markers_after="$(tail -c +$((event_log_offset + 1)) "$probe_log" | grep -Fc 'TC_SMOKE_RIFT_AMBIENT_DUE=YES' || true)"
  if [ "$tick_markers_after" -ne "$tick_markers_before" ] || [ "$due_markers_after" -ne 1 ]; then
    echo "RIFT_TICK_FREEZE=FAIL tick_markers=$tick_markers_before->$tick_markers_after due_events=$due_markers_after" | tee -a client-smoke-status.txt
    exit 1
  fi
  client_event_signatures="$(tail -c +$((event_log_offset + 1)) "$probe_log" | grep -F 'TC_SMOKE_RIFT_SOUND_CLIENT_' | sed -nE 's/.* pos=([^ ]+) volume=([^ ]+) pitch=([^ ]+).*/\1 \2 \3/p' | sort -u)"
  client_event_signature_count="$(printf '%s\n' "$client_event_signatures" | sed '/^$/d' | wc -l)"
  expected_signature="$server_pos $server_volume $server_pitch"
  if [ "$client_event_signature_count" -ne 1 ] || [ "$client_event_signatures" != "$expected_signature" ]; then
    echo "RIFT_SINGLE_EVENT_CAPTURE=FAIL signatures=$client_event_signature_count [$client_event_signatures] expected=[$expected_signature]" | tee -a client-smoke-status.txt
    exit 1
  fi
  echo "RIFT_TICK_FREEZE=PASS tick_markers=$tick_markers_before due_events=$due_markers_after" | tee -a client-smoke-status.txt
  echo "RIFT_SINGLE_EVENT_CAPTURE_WINDOW=PASS seconds=34 pitch=$server_pitch" | tee -a client-smoke-status.txt

  kill -TERM "$rift_capture_pid" 2>/dev/null || true
  capture_status=0
  wait "$rift_capture_pid" || capture_status=$?
  if [ "$capture_status" -ne 0 ] && [ "$capture_status" -ne 143 ]; then
    echo "RIFT_PCM_CAPTURE_STOP=FAIL exit=$capture_status" | tee -a client-smoke-status.txt
    exit 1
  fi
  rift_capture_pid=''
  echo 'RIFT_PCM_CAPTURE_STOP=PASS' | tee -a client-smoke-status.txt
  sink_id="$(pactl list short sinks | awk '$2 == "tc_rift_sink" { print $1; exit }')"
  if [ -z "$sink_id" ] || ! pactl list short sink-inputs | awk -v sink="$sink_id" '$2 == sink { found=1 } END { exit !found }'; then
    echo "RIFT_PULSE_SINK_INPUT=FAIL sink_id=$sink_id" | tee -a client-smoke-status.txt
    pactl list short sink-inputs >&2 || true
    cat client-smoke/openal.log >&2 || true
    exit 1
  fi
  echo "RIFT_PULSE_SINK_INPUT=PASS sink_id=$sink_id" | tee -a client-smoke-status.txt
  python3 tools/match_rift_audio_pcm.py \
    client-smoke/rift-baseline.pcm client-smoke/rift-event.pcm \
    src/main/resources/assets/thaumcraft/sounds/evilportal.ogg \
    | tee client-smoke/rift-original-pcm-match.txt
  if grep -Eiq 'Unknown sound event.*thaumcraft:evilportal|Unable to load sound.*thaumcraft:evilportal|Minecraft has crashed|Mod loading has failed|Exception in thread "Render thread"' client-smoke/client.log run/logs/latest.log; then
    echo 'RIFT_AMBIENT_SOUND_LOAD_OR_CLIENT_ERROR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'RIFT_AMBIENT_SOUND_REAL_CLIENT=PASS' | tee -a client-smoke-status.txt

  # BETA26 TileCrucible.drawEffects -> FXDispatcher.crucibleBubble.
  # Remove the Rift and resume world ticks; the audio acceptance above is complete.
  send_chat '/kill @e[type=thaumcraft:flux_rift]'
  send_chat '/tick unfreeze'

  # Build a lit, open test stage so the camera cannot end up inside terrain.
  send_chat '/time set noon'
  send_chat '/gamerule doDaylightCycle false'
  send_chat '/weather clear'
  send_chat '/fill 18 63 0 24 63 4 minecraft:stone'
  send_chat '/fill 18 64 0 24 72 4 minecraft:air'
  send_chat_fast '/setblock 20 64 2 thaumcraft:crucible'
  send_chat '/setblock 20 64 1 minecraft:glowstone'
  send_chat '/execute if block 20 64 2 thaumcraft:crucible run say TC_CRUCIBLE_PRESENT'
  if ! wait_for_server_marker 'TC_CRUCIBLE_PRESENT'; then
    echo 'CRUCIBLE_BUBBLE_REAL_CLIENT=FAIL crucible block was not placed' | tee -a client-smoke-status.txt
    exit 1
  fi
  send_chat '/data merge block 20 64 2 {Heat:151s,Water:1000,Aspects:[]}'
  # /execute if data accepts an NBT path, not an SNBT compound literal.
  send_chat '/execute if data block 20 64 2 Heat if data block 20 64 2 Water if data block 20 64 2 Aspects unless data block 20 64 2 Aspects[0] run say TC_CRUCIBLE_BASELINE_READY'
  if ! wait_for_server_marker 'TC_CRUCIBLE_BASELINE_READY'; then
    echo 'CRUCIBLE_BUBBLE_REAL_CLIENT=FAIL empty-aspect water baseline was not accepted' | tee -a client-smoke-status.txt
    exit 1
  fi
  # Aim the crosshair at the crucible surface from a clear east-side camera.
  # Support the camera during the asynchronous chat/marker round-trip; without
  # this block the player falls from Y=68.5 and the strict Y=68 selector misses.
  send_chat '/setblock 22 67 2 minecraft:stone'
  send_chat_fast '/tp @p 22.5 68.5 2.5 90 70'
  send_chat '/execute if entity @p[x=22,y=68,z=2,distance=..1] run say TC_CRUCIBLE_CAMERA_READY'
  if ! wait_for_server_marker 'TC_CRUCIBLE_CAMERA_READY'; then
    echo 'CRUCIBLE_BUBBLE_REAL_CLIENT=FAIL overhead crucible camera was not acknowledged' | tee -a client-smoke-status.txt
    exit 1
  fi
  sleep 1
  capture crucible-bubble-before
  test -s client-smoke/screenshots/crucible-bubble-before.png
  if grep -Fq 'TC_SMOKE_CRUCIBLE_BUBBLE_CREATED=YES' "$probe_log"; then
    echo 'CRUCIBLE_BUBBLE_BASELINE=FAIL particle spawned with an empty aspect list' | tee -a client-smoke-status.txt
    exit 1
  fi

  crucible_log_offset="$(wc -c < "$probe_log")"
  # The source FXGeneric lifetime is only 15..24 client ticks (0.75..1.2s).
  # Use the verified fast chat path for both state mutation and its NBT ack;
  # the normal send_chat helper sleeps four seconds and expires every bubble.
  send_chat_fast '/data merge block 20 64 2 {Aspects:[{key:"aer",amount:1}]}'
  # Match the compound with an NBT list-filter path without a four-second delay.
  send_chat_fast '/execute if data block 20 64 2 Aspects[{key:"aer",amount:1}] run say TC_CRUCIBLE_ASPECT_READY'
  if ! wait_for_server_marker 'TC_CRUCIBLE_ASPECT_READY'; then
    echo 'CRUCIBLE_BUBBLE_REAL_CLIENT=FAIL aspect payload was not accepted by the server' | tee -a client-smoke-status.txt
    exit 1
  fi

  bubble_created_line=''
  bubble_rendered_line=''
  for _ in $(seq 1 80); do
    bubble_created_line="$(tail -c +$((crucible_log_offset + 1)) "$probe_log" | grep -F 'TC_SMOKE_CRUCIBLE_BUBBLE_CREATED=YES' | head -n 1 || true)"
    bubble_rendered_line="$(tail -c +$((crucible_log_offset + 1)) "$probe_log" | grep -F 'TC_SMOKE_CRUCIBLE_BUBBLE_RENDERED=YES' | head -n 1 || true)"
    if [ -n "$bubble_created_line" ] && [ -n "$bubble_rendered_line" ]; then break; fi
    sleep 0.25
  done
  if [ -z "$bubble_created_line" ] || [ -z "$bubble_rendered_line" ]; then
    echo 'CRUCIBLE_BUBBLE_REAL_CLIENT=FAIL no created-and-rendered particle after aspect sync' | tee -a client-smoke-status.txt
    tail -120 "$probe_log" >&2 || true
    exit 1
  fi
  if grep -Fq 'Unknown particle type thaumcraft:crucible_bubble' "$probe_log"; then
    echo 'CRUCIBLE_BUBBLE_REAL_CLIENT=FAIL particle type was not registered' | tee -a client-smoke-status.txt
    exit 1
  fi
  # Capture a short burst before the 15..24-tick particles expire. Keep the
  # original pixel thresholds; only move image analysis after the raw captures.
  bubble_frame=''
  for frame in $(seq 1 12); do
    frame_path="client-smoke/screenshots/crucible-bubble-frame-${frame}.png"
    import -display :99 -window root -silent "$frame_path"
    test -s "$frame_path"
    sleep 0.05
  done
  for frame in $(seq 1 12); do
    frame_path="client-smoke/screenshots/crucible-bubble-frame-${frame}.png"
    result_path="client-smoke/crucible-bubble-frame-${frame}.txt"
    if python3 tools/verify_crucible_bubble_screenshots.py \
        client-smoke/screenshots/crucible-bubble-before.png "$frame_path" \
        > "$result_path" 2>&1; then
      bubble_frame="$frame_path"
      cp "$frame_path" client-smoke/screenshots/crucible-bubble-after.png
      cat "$result_path" | tee -a client-smoke-status.txt
      break
    fi
  done
  if [ -z "$bubble_frame" ]; then
    echo 'CRUCIBLE_BUBBLE_REAL_CLIENT=FAIL no visible bubble in 12-frame capture burst' | tee -a client-smoke-status.txt
    for frame in $(seq 1 12); do
      result_path="client-smoke/crucible-bubble-frame-${frame}.txt"
      if [ -s "$result_path" ]; then cat "$result_path" | tee -a client-smoke-status.txt; fi
    done
    exit 1
  fi
  identify client-smoke/screenshots/crucible-bubble-after.png
  convert client-smoke/screenshots/crucible-bubble-after.png -resize 512x320 -colors 64 \
    PNG8:client-smoke/screenshots/crucible-bubble-after-thumb.png
  echo -n 'REAL_CLIENT_SCREEN_crucible-bubble-after='
  base64 -w0 client-smoke/screenshots/crucible-bubble-after-thumb.png
  echo
  echo "CRUCIBLE_BUBBLE_CAPTURE_BURST=PASS selected=${bubble_frame##*/} frames=12 interval_ms=50" | tee -a client-smoke-status.txt
  echo "CRUCIBLE_BUBBLE_REAL_CLIENT=PASS $bubble_created_line | $bubble_rendered_line" | tee -a client-smoke-status.txt

  # BETA26 TileCrucible.drawEffects hot-surface froth and >500-Vis overflow.
  froth_log_offset="$(wc -c < "$probe_log")"
  send_chat_fast '/setblock 20 63 2 minecraft:magma_block'
  send_chat_fast '/data merge block 20 64 2 {Heat:200s,Water:1000,Aspects:[{key:"aer",amount:2000}]}'
  send_chat_fast '/execute if data block 20 64 2 Aspects[{key:"aer",amount:2000}] run say TC_CRUCIBLE_FROTH_ASPECT_READY'
  if ! wait_for_server_marker 'TC_CRUCIBLE_FROTH_ASPECT_READY'; then
    echo 'CRUCIBLE_FROTH_REAL_CLIENT=FAIL high-Vis hot-water payload was not accepted' | tee -a client-smoke-status.txt
    exit 1
  fi
  # BETA26 spillRandom removes one aspect per server tick while visSize > 500.
  # A 2000-Vis fixture leaves a 75-second margin; the Crucible is removed after
  # capture so the rest of the shared client smoke can continue ticking normally.

  froth_created_line=''
  froth_rendered_line=''
  froth_down_created_line=''
  froth_down_rendered_line=''
  for _ in $(seq 1 80); do
    froth_created_line="$(tail -c +$((froth_log_offset + 1)) "$probe_log" | grep -F 'TC_SMOKE_CRUCIBLE_FROTH_CREATED=YES' | head -n 1 || true)"
    froth_rendered_line="$(tail -c +$((froth_log_offset + 1)) "$probe_log" | grep -F 'TC_SMOKE_CRUCIBLE_FROTH_RENDERED=YES' | head -n 1 || true)"
    froth_down_created_line="$(tail -c +$((froth_log_offset + 1)) "$probe_log" | grep -F 'TC_SMOKE_CRUCIBLE_FROTH_DOWN_CREATED=YES' | head -n 1 || true)"
    froth_down_rendered_line="$(tail -c +$((froth_log_offset + 1)) "$probe_log" | grep -F 'TC_SMOKE_CRUCIBLE_FROTH_DOWN_RENDERED=YES' | head -n 1 || true)"
    if [ -n "$froth_created_line" ] && [ -n "$froth_rendered_line" ] \
       && [ -n "$froth_down_created_line" ] && [ -n "$froth_down_rendered_line" ]; then break; fi
    sleep 0.25
  done
  if [ -z "$froth_created_line" ] || [ -z "$froth_rendered_line" ] \
     || [ -z "$froth_down_created_line" ] || [ -z "$froth_down_rendered_line" ]; then
    echo 'CRUCIBLE_FROTH_REAL_CLIENT=FAIL surface and overflow particles must both be created and rendered' | tee -a client-smoke-status.txt
    tail -120 "$probe_log" >&2 || true
    exit 1
  fi
  if grep -Fq 'Unknown particle type thaumcraft:crucible_froth' "$probe_log" \
     || grep -Fq 'Unknown particle type thaumcraft:crucible_froth_down' "$probe_log"; then
    echo 'CRUCIBLE_FROTH_REAL_CLIENT=FAIL particle type was not registered' | tee -a client-smoke-status.txt
    exit 1
  fi
  capture crucible-froth-after
  test -s client-smoke/screenshots/crucible-froth-after.png
  send_chat_fast '/setblock 20 64 2 minecraft:air'
  echo "CRUCIBLE_FROTH_REAL_CLIENT=PASS $froth_created_line | $froth_rendered_line | $froth_down_created_line | $froth_down_rendered_line" | tee -a client-smoke-status.txt
'''
high_teleport_command = 'send_chat_fast "/tp @p $rift_x 300 $rift_z 0 0"'
high_teleport_probe = "send_chat_fast '/execute if entity @p[x=3,y=150,z=12,dx=1,dy=250,dz=1] run say TC_RIFT_HIGH_TELEPORT_PRESENT'"
fill_command = 'send_chat "/fill 1 149 10 5 149 14 minecraft:barrier"'
platform_marker = "TC_RIFT_PLATFORM_PRESENT"
source_teleport_command = 'send_chat "/tp @p $rift_x $rift_y $rift_z 0 90"'
visual_teleport_command = 'send_chat_fast "/tp @p $rift_visual_x $rift_y $rift_z $rift_visual_yaw $rift_visual_pitch"'
source_return_command = 'send_chat_fast "/tp @p $rift_x $rift_y $rift_z 0 89"'
rift_summon_command = 'send_chat_fast "/summon thaumcraft:flux_rift $rift_x $rift_y $rift_z'
render_gate_marker = "layer=0 blend=ADDITIVE depthWrite=OFF"
listener_marker = "TC_RIFT_LISTENER_AT_SOURCE"
if any(probe.count(command) != 1 for command in (
    high_teleport_command, high_teleport_probe, fill_command, source_teleport_command
)):
    raise SystemExit("Rift listener setup commands are not unique; refuse generated smoke")
if probe.count(visual_teleport_command) != 1 or probe.count(source_return_command) != 1:
    raise SystemExit("Rift visual camera must be set once before spawn and source return once after rendering")
generated_script = source + probe + suffix
if generated_script.count("wait_for_server_marker() {") != 1:
    raise SystemExit("Generated Rift smoke must contain exactly one bounded server-marker poller")
if probe.count("TC_RIFT_HIGH_TELEPORT_PRESENT") != 2 or probe.count("TC_RIFT_AMBIENT_ENTITY_PRESENT") != 2:
    raise SystemExit("Rift high teleport and spawn must be confirmed by fresh server markers")
if any(line.strip() == "sleep 0.1" for line in probe.splitlines()):
    raise SystemExit("Generated fast chat must not regress to the 0.1s prefix-loss race")
if not (
    probe.index(high_teleport_command)
    < probe.index(high_teleport_probe)
    < probe.index("wait_for_server_marker 'TC_RIFT_HIGH_TELEPORT_PRESENT'")
    < probe.index(fill_command)
    < probe.index(platform_marker)
    < probe.index(source_teleport_command)
    < probe.index(listener_marker)
    < probe.index(visual_teleport_command)
    < probe.index("TC_RIFT_VISUAL_CAMERA_BEFORE")
    < probe.index("capture rift-ambient-before")
    < probe.index("spawn_game_time=")
    < probe.index("> client-smoke/rift-event.pcm")
    < probe.index(rift_summon_command)
    < probe.index("TC_RIFT_AMBIENT_ENTITY_PRESENT")
    < probe.index("TC_RIFT_VISUAL_CAMERA_AFTER")
    < probe.index("capture rift-ambient-entity-present")
    < probe.index(render_gate_marker)
    < probe.index(source_return_command)
    < probe.index("TC_RIFT_LISTENER_RETURNED_AFTER_VISUAL")
):
    raise SystemExit("Rift smoke must compare side-on frames and return to the source before audio acceptance")
for required_marker in (
    "TC_RIFT_PLATFORM_PRESENT",
    "TC_RIFT_HIGH_TELEPORT_PRESENT",
    "TC_RIFT_LISTENER_AT_SOURCE",
    "TC_RIFT_AMBIENT_ENTITY_PRESENT",
    "TC_RIFT_VISUAL_CAMERA_BEFORE",
    "TC_RIFT_VISUAL_CAMERA_AFTER",
    "TC_RIFT_LISTENER_RETURNED_AFTER_VISUAL",
):
    if probe.count(required_marker) < 2:
        raise SystemExit("Rift listener smoke lacks fail-closed runtime marker: " + required_marker)
crucible_set_command = "send_chat_fast '/setblock 20 64 2 thaumcraft:crucible'"
crucible_baseline_command = "send_chat '/data merge block 20 64 2 {Heat:151s,Water:1000,Aspects:[]}'"
crucible_baseline_probe = """send_chat '/execute if data block 20 64 2 Heat if data block 20 64 2 Water if data block 20 64 2 Aspects unless data block 20 64 2 Aspects[0] run say TC_CRUCIBLE_BASELINE_READY'"""
crucible_aspects_command = "send_chat_fast '/data merge block 20 64 2 {Aspects:[{key:\"aer\",amount:1}]}'"
crucible_aspects_probe = """send_chat_fast '/execute if data block 20 64 2 Aspects[{key:"aer",amount:1}] run say TC_CRUCIBLE_ASPECT_READY'"""
crucible_froth_heater_command = "send_chat_fast '/setblock 20 63 2 minecraft:magma_block'"
crucible_froth_aspects_command = "send_chat_fast '/data merge block 20 64 2 {Heat:200s,Water:1000,Aspects:[{key:\"aer\",amount:2000}]}'"
crucible_froth_aspects_probe = """send_chat_fast '/execute if data block 20 64 2 Aspects[{key:"aer",amount:2000}] run say TC_CRUCIBLE_FROTH_ASPECT_READY'"""
crucible_froth_cleanup_command = "send_chat_fast '/setblock 20 64 2 minecraft:air'"
if any(probe.count(command) != 1 for command in (
    crucible_set_command, crucible_baseline_command, crucible_baseline_probe,
    crucible_aspects_command, crucible_aspects_probe,
    crucible_froth_heater_command, crucible_froth_aspects_command, crucible_froth_aspects_probe,
    crucible_froth_cleanup_command, "send_chat '/tick unfreeze'", "capture crucible-bubble-before",
    "bubble_frame=''", "CRUCIBLE_BUBBLE_CAPTURE_BURST=PASS",
    "CRUCIBLE_BUBBLE_REAL_CLIENT=PASS", "capture crucible-froth-after",
)):
    raise SystemExit("Crucible bubble smoke must set one baseline and one aspect-bearing state with valid NBT paths")
if "execute if data block 20 64 2 {Heat:" in probe or "execute if data block 20 64 2 {Aspects:" in probe:
    raise SystemExit("execute if data must use NBT paths, not SNBT compound literals")
if not (
    probe.index("RIFT_AMBIENT_SOUND_REAL_CLIENT=PASS")
    < probe.index("send_chat '/kill @e[type=thaumcraft:flux_rift]'")
    < probe.index("send_chat '/tick unfreeze'")
    < probe.index(crucible_set_command)
    < probe.index("TC_CRUCIBLE_PRESENT")
    < probe.index(crucible_baseline_command)
    < probe.index(crucible_baseline_probe)
    < probe.index("TC_CRUCIBLE_BASELINE_READY")
    < probe.index("capture crucible-bubble-before")
    < probe.index(crucible_aspects_command)
    < probe.index(crucible_aspects_probe)
    < probe.index("TC_CRUCIBLE_ASPECT_READY")
    < probe.index("TC_SMOKE_CRUCIBLE_BUBBLE_CREATED=YES", probe.index(crucible_aspects_probe))
    < probe.index("TC_SMOKE_CRUCIBLE_BUBBLE_RENDERED=YES", probe.index(crucible_aspects_probe))
    < probe.index("bubble_frame=''")
    < probe.index("python3 tools/verify_crucible_bubble_screenshots.py")
    < probe.index("CRUCIBLE_BUBBLE_CAPTURE_BURST=PASS")
    < probe.index("CRUCIBLE_BUBBLE_REAL_CLIENT=PASS")
    < probe.index(crucible_froth_heater_command)
    < probe.index(crucible_froth_aspects_command)
    < probe.index(crucible_froth_aspects_probe)
    < probe.index("TC_CRUCIBLE_FROTH_ASPECT_READY")
    < probe.index("TC_SMOKE_CRUCIBLE_FROTH_CREATED=YES", probe.index(crucible_froth_aspects_probe))
    < probe.index("TC_SMOKE_CRUCIBLE_FROTH_RENDERED=YES", probe.index(crucible_froth_aspects_probe))
    < probe.index("TC_SMOKE_CRUCIBLE_FROTH_DOWN_CREATED=YES", probe.index(crucible_froth_aspects_probe))
    < probe.index("TC_SMOKE_CRUCIBLE_FROTH_DOWN_RENDERED=YES", probe.index(crucible_froth_aspects_probe))
    < probe.index("capture crucible-froth-after")
    < probe.index(crucible_froth_cleanup_command)
    < probe.index("CRUCIBLE_FROTH_REAL_CLIENT=PASS")
):
    raise SystemExit("Crucible bubble/froth smoke must verify empty, aspect-bearing and frozen high-Vis states in order")
fixture_commands = (
    "send_chat '/time set noon'",
    "send_chat '/gamerule doDaylightCycle false'",
    "send_chat '/weather clear'",
    "send_chat '/fill 18 63 0 24 63 4 minecraft:stone'",
    "send_chat '/fill 18 64 0 24 72 4 minecraft:air'",
    "send_chat '/setblock 20 64 1 minecraft:glowstone'",
    "send_chat_fast '/setblock 20 63 2 minecraft:magma_block'",
    "send_chat_fast '/setblock 20 64 2 minecraft:air'",
    "send_chat '/setblock 22 67 2 minecraft:stone'",
    "send_chat_fast '/tp @p 22.5 68.5 2.5 90 70'",
    "send_chat '/execute if entity @p[x=22,y=68,z=2,distance=..1] run say TC_CRUCIBLE_CAMERA_READY'",
    "python3 tools/verify_crucible_bubble_screenshots.py",
)
if any(probe.count(command) != 1 for command in fixture_commands):
    raise SystemExit("Crucible screenshot smoke must build one lit stage, aim one camera, and run one pixel verifier")
if not (
    probe.index("send_chat '/time set noon'")
    < probe.index("send_chat '/gamerule doDaylightCycle false'")
    < probe.index("send_chat '/fill 18 63 0 24 63 4 minecraft:stone'")
    < probe.index("send_chat '/fill 18 64 0 24 72 4 minecraft:air'")
    < probe.index("send_chat_fast '/setblock 20 64 2 thaumcraft:crucible'")
    < probe.index("send_chat '/setblock 20 64 1 minecraft:glowstone'")
    < probe.index("TC_CRUCIBLE_PRESENT")
    < probe.index("TC_CRUCIBLE_BASELINE_READY")
    < probe.index("send_chat '/setblock 22 67 2 minecraft:stone'")
    < probe.index("send_chat_fast '/tp @p 22.5 68.5 2.5 90 70'")
    < probe.index("TC_CRUCIBLE_CAMERA_READY")
    < probe.index("capture crucible-bubble-before")
    < probe.index("TC_CRUCIBLE_ASPECT_READY")
    < probe.index("bubble_frame=''")
    < probe.index("python3 tools/verify_crucible_bubble_screenshots.py")
):
    raise SystemExit("Crucible pixel evidence must follow a lit scene, camera confirmation, empty baseline, and aspect-bearing capture")
print(generated_script)

