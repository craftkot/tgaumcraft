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
  timeout --signal=TERM 420s parec --device=tc_rift_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/rift-event.pcm &
  rift_capture_pid=$!
  # Preserve the shared client cleanup while guaranteeing the bounded PulseAudio
  # recorder is reaped on every fail-fast exit before the normal stop point.
  trap 'if [ -n "${rift_capture_pid:-}" ] && kill -0 "$rift_capture_pid" 2>/dev/null; then kill -TERM "$rift_capture_pid" 2>/dev/null || true; wait "$rift_capture_pid" 2>/dev/null || true; fi; cleanup' EXIT
  sleep 0.25
  # The event is a variable-range SoundEvent: volume scales its hearing radius.
  # Keep the listener at the source so Gaussian low-volume rolls cannot exclude it.
  rift_x=3.5
  rift_y=150
  rift_z=12.5
  send_chat "/tp @p $rift_x $rift_y $rift_z 0 0"
  echo "RIFT_LISTENER_AT_SOURCE=PASS x=$rift_x y=$rift_y z=$rift_z" | tee -a client-smoke-status.txt
  capture rift-ambient-before
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
  capture rift-ambient-entity-present
  # Measure one full BETA26 300-tick cadence in server game time, not wall time.
  start_game_time="$(query_game_time)" || exit 1
  current_game_time="$start_game_time"
  for _ in $(seq 1 60); do
    if (( current_game_time - start_game_time >= 300 )); then break; fi
    current_game_time="$(query_game_time)" || exit 1
  done
  game_tick_delta=$((current_game_time - start_game_time))
  if (( game_tick_delta < 300 )); then
    echo "RIFT_GAME_TICK_WINDOW=FAIL start=$start_game_time end=$current_game_time delta=$game_tick_delta required=300" | tee -a client-smoke-status.txt
    tail -100 run/logs/latest.log >&2
    exit 1
  fi
  echo "RIFT_GAME_TICK_WINDOW=PASS start=$start_game_time end=$current_game_time delta=$game_tick_delta" | tee -a client-smoke-status.txt
  send_chat '/execute if entity @e[type=thaumcraft:flux_rift,limit=1] run say TC_RIFT_AMBIENT_ENTITY_STILL_PRESENT'
  if ! grep -Fq 'TC_RIFT_AMBIENT_ENTITY_STILL_PRESENT' run/logs/latest.log; then
    echo 'RIFT_AMBIENT_ENTITY_AFTER_TICK_WINDOW=FAIL' | tee -a client-smoke-status.txt
    tail -100 run/logs/latest.log >&2
    exit 1
  fi
  echo 'RIFT_AMBIENT_ENTITY_AFTER_TICK_WINDOW=PASS' | tee -a client-smoke-status.txt
  probe_log=client-smoke/client.log
  if ! grep -Fq 'TC_SMOKE_RIFT_AMBIENT_DUE=YES' "$probe_log"; then
    echo 'RIFT_SERVER_AMBIENT_DUE=NOT_OBSERVED' | tee -a client-smoke-status.txt
    tail -120 "$probe_log" >&2 || true
    exit 1
  fi
  server_due_line="$(grep -F 'TC_SMOKE_RIFT_AMBIENT_DUE=YES' "$probe_log" | tail -n 1)"
  server_volume="${server_due_line#* volume=}"
  server_volume="${server_volume%% *}"
  if [ -z "$server_volume" ] || ! awk -v volume="$server_volume" 'BEGIN { exit !(volume > 0.0) }'; then
    echo "RIFT_SERVER_AMBIENT_VOLUME_NONPOSITIVE=$server_volume" | tee -a client-smoke-status.txt
    echo "$server_due_line" >&2
    exit 1
  fi
  echo "RIFT_SERVER_AMBIENT_DUE=PASS $server_due_line" | tee -a client-smoke-status.txt
  if ! grep -Fq 'TC_SMOKE_RIFT_AMBIENT_CALL=YES' "$probe_log"; then
    echo 'RIFT_SERVER_SOUND_CALL=NOT_CONFIRMED' | tee -a client-smoke-status.txt
    tail -120 "$probe_log" >&2 || true
    exit 1
  fi
  echo 'RIFT_SERVER_SOUND_CALL=PASS' | tee -a client-smoke-status.txt
  if ! grep -Fq 'TC_SMOKE_RIFT_SOUND_CLIENT_EVENT=YES' "$probe_log"; then
    echo 'RIFT_CLIENT_SOUND_EVENT=NOT_RECEIVED' | tee -a client-smoke-status.txt
    tail -120 "$probe_log" >&2 || true
    exit 1
  fi
  echo 'RIFT_CLIENT_SOUND_EVENT=PASS' | tee -a client-smoke-status.txt
  kill -TERM "$rift_capture_pid" 2>/dev/null || true
  capture_status=0
  wait "$rift_capture_pid" || capture_status=$?
  if [ "$capture_status" -ne 143 ]; then
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
print(source + probe + suffix)
