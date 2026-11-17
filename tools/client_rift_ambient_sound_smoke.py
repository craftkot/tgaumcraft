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
source = source.replace("export ALSOFT_DRIVERS=null", "export ALSOFT_DRIVERS=pulse")

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
  pactl list short sinks | grep -Fq 'tc_rift_sink'
  timeout --signal=TERM 3s parec --device=tc_rift_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/rift-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 68s parec --device=tc_rift_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/rift-event.pcm &
  rift_capture_pid=$!
  sleep 0.25
  send_chat '/tp @p 2.5 150 11.5 0 0'
  capture rift-ambient-before
  send_chat '/summon thaumcraft:flux_rift 3.5 150 12.5 {RiftSeed:1234567,RiftSize:5,Stability:0.0f}'
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
  # Three BETA26 300-tick opportunities make the PCM gate robust to a rare
  # negative Gaussian volume sample without altering production randomness.
  sleep 46
  wait "$rift_capture_pid" || test "$?" -eq 124
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
