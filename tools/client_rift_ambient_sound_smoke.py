#!/usr/bin/env python3
"""Generate a focused real Minecraft Flux Rift ambient-sound smoke."""
from pathlib import Path

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

  # Use this only for the two ordered setup commands: the player starts high
  # in the newly loaded target chunk, so a short chat round-trip cannot make
  # it fall below the platform before the barrier is placed.
  send_chat_fast() {
    activate_minecraft_client
    ensure_minecraft_command_channel
    xdotool key --clearmodifiers t
    sleep 0.1
    xdotool type --clearmodifiers --delay 5 "$1"
    xdotool key --clearmodifiers Return
    sleep 0.25
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
  # The event is a variable-range SoundEvent: volume scales its hearing radius.
  # Keep the listener at the source so Gaussian low-volume rolls cannot exclude it.
  rift_x=3.5
  rift_y=150
  rift_z=12.5
  # Teleport before filling: /fill rejects an unloaded target chunk. The old order
  # emitted "That position is not loaded", so the player later fell out of sound range.
  # Load the target chunk while the player is safely above the future platform.
  # The normal send_chat delay is four seconds; using it here would let the
  # player fall below Y=149 before the barrier exists.
  send_chat_fast "/tp @p $rift_x 300 $rift_z 0 0"
  send_chat_fast "/fill 1 149 10 5 149 14 minecraft:barrier"
  send_chat '/execute if block 3 149 12 minecraft:barrier run say TC_RIFT_PLATFORM_PRESENT'
  if ! grep -Fq 'TC_RIFT_PLATFORM_PRESENT' run/logs/latest.log; then
    echo 'RIFT_LISTENER_PLATFORM=FAIL barrier block was not placed after teleport' | tee -a client-smoke-status.txt
    tail -100 run/logs/latest.log >&2 || true
    exit 1
  fi
  echo "RIFT_LISTENER_PLATFORM=PASS y=149" | tee -a client-smoke-status.txt
  # Only teleport to the measured sound position after the support block exists.
  # The size-5 polycone is centered at the player's feet. Pitch 0 leaves it
  # below the horizontal view frustum; aim straight down so the real client
  # actually submits the four render passes while remaining at the sound source.
  send_chat "/tp @p $rift_x $rift_y $rift_z 0 90"
  send_chat '/execute if entity @p[x=3,y=150,z=12,distance=..1] run say TC_RIFT_LISTENER_AT_SOURCE'
  if ! grep -Fq 'TC_RIFT_LISTENER_AT_SOURCE' run/logs/latest.log; then
    send_chat '/data get entity @p Pos'
    echo 'RIFT_LISTENER_AT_SOURCE=FAIL player is not at the Rift source after supported teleport' | tee -a client-smoke-status.txt
    tail -100 run/logs/latest.log >&2 || true
    exit 1
  fi
  echo "RIFT_LISTENER_AT_SOURCE=PASS x=$rift_x y=$rift_y z=$rift_z" | tee -a client-smoke-status.txt
  capture rift-ambient-before
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
  send_chat "/summon thaumcraft:flux_rift $rift_x $rift_y $rift_z {RiftSeed:1234567,RiftSize:5,Stability:0.0f}"
  send_chat '/execute if entity @e[type=thaumcraft:flux_rift,limit=1] run say TC_RIFT_AMBIENT_ENTITY_PRESENT'
  rift_spawned=0
  for _ in $(seq 1 40); do
    if grep -Fq 'TC_RIFT_AMBIENT_ENTITY_PRESENT' run/logs/latest.log; then
      rift_spawned=1
      break
    fi
    sleep 0.25
  done
  if [ "$rift_spawned" -ne 1 ]; then
    echo 'RIFT_AMBIENT_ENTITY_PRESENT=FAIL' | tee -a client-smoke-status.txt
    tail -100 run/logs/latest.log >&2
    exit 1
  fi
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
'''
high_teleport_command = 'send_chat_fast "/tp @p $rift_x 300 $rift_z 0 0"'
fill_command = 'send_chat_fast "/fill 1 149 10 5 149 14 minecraft:barrier"'
platform_marker = "TC_RIFT_PLATFORM_PRESENT"
source_teleport_command = 'send_chat "/tp @p $rift_x $rift_y $rift_z 0 90"'
listener_marker = "TC_RIFT_LISTENER_AT_SOURCE"
if any(probe.count(command) != 1 for command in (
    high_teleport_command, fill_command, source_teleport_command
)):
    raise SystemExit("Rift listener setup commands are not unique; refuse generated smoke")
if not (
    probe.index(high_teleport_command)
    < probe.index(fill_command)
    < probe.index(platform_marker)
    < probe.index(source_teleport_command)
    < probe.index(listener_marker)
):
    raise SystemExit("Rift listener must load the chunk, place support, then teleport to the source")
for required_marker in (
    "TC_RIFT_PLATFORM_PRESENT",
    "TC_RIFT_LISTENER_AT_SOURCE",
):
    if probe.count(required_marker) < 2:
        raise SystemExit("Rift listener smoke lacks fail-closed runtime marker: " + required_marker)
print(source + probe + suffix)
