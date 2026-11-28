#!/usr/bin/env python3
"""Generate isolated REAL Minecraft 1.21.1 Thaumometer air-click audio smoke.

The original BETA26 scan event fires on item use even when the ray misses.
Checks an actual item predicate and records the real OpenAL/PulseAudio PCM.
Source-clip identity is checked separately against the original scan.ogg.
"""
from pathlib import Path
import sys

source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
anchor = "  # The initial world is already creative + peaceful, as established"
if source.count(anchor) != 1:
    raise SystemExit("World boot hook changed; refusing unsafe smoke injection")
if source.count("export ALSOFT_DRIVERS=null") != 1:
    raise SystemExit("OpenAL backend hook changed; refusing unverified audio setup")
head = source.split(anchor, 1)[0].replace("export ALSOFT_DRIVERS=null",
                                           "export ALSOFT_DRIVERS=pulse")
body = r'''
  echo 'SCAN_SOUND_REAL_INTEGRATED_WORLD=YES' | tee -a client-smoke-status.txt

  # Re-resolve and activate the real game window before every command. The
  # previous smoke used the shared send_chat helper without proving that the
  # Minecraft window still owned X11 focus after the first command.
  scan_chat_window=""
  scan_windows=()
  focus_scan_window() {
    local active_window focused_window last_index
    mapfile -t scan_windows < <(xdotool search --onlyvisible --name 'Minecraft' 2>/dev/null || true)
    if [ "${#scan_windows[@]}" -eq 0 ]; then
      echo 'SCAN_SOUND_CHAT_WINDOW=NOT_FOUND' | tee -a client-smoke-status.txt
      return 1
    fi
    active_window="$(xdotool getactivewindow 2>/dev/null || true)"
    scan_chat_window="$active_window"
    if [[ ! " ${scan_windows[*]} " =~ " ${scan_chat_window} " ]]; then
      last_index=$(("${#scan_windows[@]}" - 1))
      scan_chat_window="${scan_windows[$last_index]}"
    fi
    xdotool windowactivate --sync "$scan_chat_window"
    xdotool windowfocus --sync "$scan_chat_window"
    focused_window="$(xdotool getwindowfocus 2>/dev/null || true)"
    if [[ "$focused_window" != "$scan_chat_window" ]]; then
      echo "SCAN_SOUND_CHAT_FOCUS_MISMATCH=YES target=$scan_chat_window focus=$focused_window" | tee -a client-smoke-status.txt
      return 1
    fi
    echo "SCAN_SOUND_CHAT_FOCUS=PASS window=$scan_chat_window" | tee -a client-smoke-status.txt
  }
  scan_first_command=1
  send_scan_chat() {
    local raw_command="$1"
    if [[ "$raw_command" != /* ]]; then
      echo "SCAN_SOUND_COMMAND_MISSING_SLASH=YES command=$raw_command" | tee -a client-smoke-status.txt
      exit 1
    fi
    local command_body="${raw_command#/}"
    focus_scan_window || exit 1
    echo "SCAN_SOUND_CHAT_TARGET=$scan_chat_window command=${command_body%% *}" | tee -a client-smoke-status.txt
    # Match the proven Workbench driver: Slash opens the command-entry screen
    # with its prefix already present. Typing a leading slash after T is less
    # reliable in this X11/GLFW client and previously produced no server ack.
    xdotool key --clearmodifiers slash
    sleep 1
    if [[ "$scan_first_command" -eq 1 ]]; then
      import -display :99 -window root -silent client-smoke/screenshots/80a-scan-chat-open.png
    fi
    xdotool type --clearmodifiers --delay 24 "$command_body"
    sleep 1
    if [[ "$scan_first_command" -eq 1 ]]; then
      import -display :99 -window root -silent client-smoke/screenshots/80b-scan-chat-text-before-enter.png
    fi
    xdotool key --clearmodifiers Return
    sleep 4
    if [[ "$scan_first_command" -eq 1 ]]; then
      import -display :99 -window root -silent client-smoke/screenshots/80c-scan-chat-submit-result.png
      scan_first_command=0
    fi
  }
  wait_for_scan_marker() {
    local marker="$1"
    for attempt in {1..12}; do
      # latest.log is the integrated server's authoritative log; do not use
      # the Gradle client stdout stream as a substitute for server execution.
      if grep -Fq "$marker" run/logs/latest.log; then
        echo "SCAN_SOUND_MARKER_SEEN=$marker" | tee -a client-smoke-status.txt
        return 0
      fi
      sleep 1
    done
    echo "SCAN_SOUND_MARKER_MISSING=$marker" | tee -a client-smoke-status.txt
    tail -80 run/logs/latest.log >&2
    import -display :99 -window root -silent client-smoke/screenshots/90-scan-sound-marker-missing.png
    exit 1
  }

  # Prove the command path, then prove that the actual registered Thaumometer
  # occupies hotbar slot 0 before measuring its client-side use sound.
  send_scan_chat '/say TC_SCAN_SOUND_COMMAND_CHANNEL_READY'
  wait_for_scan_marker 'TC_SCAN_SOUND_COMMAND_CHANNEL_READY'
  send_scan_chat '/item replace entity @p hotbar.0 with thaumcraft:thaumometer 1'
  send_scan_chat '/execute if items entity @p hotbar.0 thaumcraft:thaumometer run say SCAN_SOUND_ITEM_READY'
  wait_for_scan_marker 'SCAN_SOUND_ITEM_READY'
  echo 'SCAN_SOUND_REAL_ITEM=PASS' | tee -a client-smoke-status.txt
  focus_scan_window
  import -display :99 -window root -silent client-smoke/screenshots/90a-scan-sound-item-ready.png
  xdotool key --clearmodifiers 1
  sleep 3
  import -display :99 -window root -silent client-smoke/screenshots/90-scan-sound-before.png
  pactl info > client-smoke/scan-pulse-info.txt
  pactl list short sinks | grep -Fq 'tc_scan_sink'
  timeout --signal=TERM 2s parec --device=tc_scan_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/scan-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_scan_sink.monitor \
    --format=s16le --rate=48000 --channels=2 --raw \
    > client-smoke/scan-event.pcm &
  scan_capture_pid=$!
  sleep 0.6
  xdotool click 3
  sleep 5
  wait "$scan_capture_pid" || test "$?" -eq 124
  import -display :99 -window root -silent client-smoke/screenshots/91-scan-sound-after.png
  python3 tools/check_page_audio_pcm.py \
    client-smoke/scan-baseline.pcm client-smoke/scan-event.pcm \
    | tee client-smoke/scan-pcm-level.txt
  echo 'SCAN_SOUND_REAL_CLIENT_PCM=PASS' | tee -a client-smoke-status.txt
  if grep -Eiq 'Unknown sound event.*thaumcraft:scan|Unable to load sound.*thaumcraft:scan|Missing sound.*thaumcraft:scan|Exception in thread "Render thread"|Mod loading has failed' client-smoke/client.log; then
    echo 'SCAN_SOUND_CLIENT_ERROR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'SCAN_SOUND_REAL_ITEM_AND_AUDIO_CAPTURE=PASS' | tee -a client-smoke-status.txt
fi
'''
sys.stdout.write(head + body)
