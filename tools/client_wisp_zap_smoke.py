#!/usr/bin/env python3
"""Generate an isolated real Minecraft-client smoke for BETA26 Wisp zap rendering.

The shared tools/client_smoke.sh is read-only. This generator reuses its
verified integrated-world bootstrap, then performs one real player-vs-Wisp
attack and captures the transient three-tick additive bolt.
"""
from pathlib import Path
import sys


def render() -> str:
    source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
    anchor = "  # The initial world is already creative + peaceful, as established"
    options_line = "printf 'guiScale:2\\n' > run/options.txt"
    if source.count(anchor) != 1 or source.count("  send_chat() {") != 1:
        raise SystemExit("Client smoke world/command hook changed; refusing unsafe injection")
    if source.count(options_line) != 1:
        raise SystemExit("Client smoke options bootstrap changed; refusing Wisp render profile")

    render_profile = (
        "printf 'guiScale:2\\nrenderDistance:2\\nsimulationDistance:5\\n"
        "graphicsMode:0\\nparticles:2\\nentityShadows:false\\n"
        "mipmapLevels:0\\nbiomeBlendRadius:0\\nenableVsync:false\\n"
        "maxFps:260\\nrenderClouds:false\\nao:false\\n' > run/options.txt"
    )
    source = source.replace(options_line, render_profile)
    head = source.split(anchor, 1)[0]
    body = r'''
  wisp_verify_render_profile() {
    local option
    for option in \
      'renderDistance:2' \
      'simulationDistance:5' \
      'graphicsMode:0' \
      'particles:2' \
      'entityShadows:false' \
      'mipmapLevels:0' \
      'biomeBlendRadius:0' \
      'enableVsync:false' \
      'maxFps:260' \
      'renderClouds:false' \
      'ao:false'
    do
      if ! grep -Fxq -- "$option" run/options.txt; then
        echo "WISP_CLIENT_RENDER_OPTION_MISSING=$option" | tee -a client-smoke-status.txt
        return 1
      fi
    done
    echo 'WISP_CLIENT_RENDER_PROFILE=llvmpipe-fast' | tee -a client-smoke-status.txt
    echo 'WISP_CLIENT_RENDER_PROFILE_APPLIED=YES' | tee -a client-smoke-status.txt
  }
  wisp_verify_render_profile

  echo 'WISP_ZAP_REAL_CLIENT_WORLD=YES' | tee -a client-smoke-status.txt

  wisp_command() {
    local command="$1"
    xdotool key --clearmodifiers t
    sleep 0.5
    xdotool type --clearmodifiers --delay 24 "$command"
    xdotool key --clearmodifiers Return
    sleep 0.2
  }

  wisp_fast_command() {
    local command="$1" open_delay="0.5" type_delay="2"
    if [ "$#" -ge 2 ]; then open_delay="$2"; fi
    if [ "$#" -ge 3 ]; then type_delay="$3"; fi
    xdotool key --clearmodifiers t
    # Delay only before sending. Callers wait for authoritative markers after Return.
    sleep "$open_delay"
    xdotool type --clearmodifiers --delay "$type_delay" "$command"
    xdotool key --clearmodifiers Return
  }

  wisp_wait_for_marker() {
    local marker="$1"
    for _ in $(seq 1 200); do
      if grep -Fq -- "$marker" client-smoke/client.log; then
        echo "WISP_SERVER_OR_CLIENT_MARKER_CONFIRMED=$marker" | tee -a client-smoke-status.txt
        return 0
      fi
      sleep 0.025
    done
    echo "WISP_MARKER_TIMEOUT=$marker" | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    exit 1
  }

  wisp_start_zap_video_capture() {
    local progress="client-smoke/wisp-zap-video-progress.txt"
    rm -f "$progress" client-smoke/wisp-zap-video.mp4
    if ! command -v ffmpeg >/dev/null 2>&1 || ! command -v ffprobe >/dev/null 2>&1; then
      echo 'WISP_ZAP_VIDEO_TOOLING_MISSING=YES' | tee -a client-smoke-status.txt
      return 1
    fi
    if [ -n "${VIDEO_PID:-}" ]; then
      echo "WISP_ZAP_VIDEO_RECORDER_ALREADY_ACTIVE=$VIDEO_PID" | tee -a client-smoke-status.txt
      return 1
    fi
    ffmpeg -nostdin -hide_banner -loglevel error -y \
      -f x11grab -draw_mouse 0 -framerate 60 -video_size 1280x800 -i :99.0 \
      -an -c:v libx264 -preset ultrafast -crf 26 -pix_fmt yuv420p \
      -movflags +faststart -stats_period 0.01 -progress "$progress" \
      client-smoke/wisp-zap-video.mp4 > client-smoke/wisp-zap-video.log 2>&1 &
    VIDEO_PID=$!
    for _ in $(seq 1 200); do
      if grep -q '^frame=1$' "$progress" 2>/dev/null; then
        WISP_ZAP_VIDEO_FIRST_FRAME_NS="$(date +%s%N)"
        echo "WISP_ZAP_VIDEO_STARTED=YES firstFrameEpochNs=$WISP_ZAP_VIDEO_FIRST_FRAME_NS frameRate=60" | tee -a client-smoke-status.txt
        return 0
      fi
      if ! kill -0 "$VIDEO_PID" 2>/dev/null; then
        echo 'WISP_ZAP_VIDEO_PROCESS_EXITED_BEFORE_FIRST_FRAME=YES' | tee -a client-smoke-status.txt
        tail -80 client-smoke/wisp-zap-video.log >&2
        return 1
      fi
      sleep 0.01
    done
    echo 'WISP_ZAP_VIDEO_FIRST_FRAME_TIMEOUT=YES' | tee -a client-smoke-status.txt
    tail -80 client-smoke/wisp-zap-video.log >&2
    return 1
  }

  wisp_stop_zap_video_capture() {
    if [ -z "${VIDEO_PID:-}" ]; then
      echo 'WISP_ZAP_VIDEO_PROCESS_MISSING=YES' | tee -a client-smoke-status.txt
      return 1
    fi
    if kill -0 "$VIDEO_PID" 2>/dev/null; then
      kill -TERM "$VIDEO_PID" 2>/dev/null || true
    fi
    wait "$VIDEO_PID" 2>/dev/null || true
    VIDEO_PID=''
    if [ ! -s client-smoke/wisp-zap-video.mp4 ]; then
      echo 'WISP_ZAP_VIDEO_OUTPUT_MISSING=YES' | tee -a client-smoke-status.txt
      tail -80 client-smoke/wisp-zap-video.log >&2
      return 1
    fi
    echo 'WISP_ZAP_VIDEO_STOPPED=YES' | tee -a client-smoke-status.txt
  }

  wisp_wait_for_zap_frame() {
    local after_lines="$1" wisp_id="$2" created_at="$3" expected_age="$4"
    local created_filter="" candidate marker_line attempt
    local log="client-smoke/client.log"
    if [ -n "$created_at" ]; then created_filter="createdAt=$created_at "; fi
    # Poll finite snapshots instead of tail -f: a grep/head pipeline can find a
    # matching line but leave the follow process waiting for a future append.
    # Continuous video capture preserves the frame; this watcher only timestamps
    # the marker and must return immediately after finding it.
    for attempt in $(seq 1 1000); do
      candidate="$(tail -n +"$((after_lines + 1))" "$log" \
        | grep -F "TC_SMOKE_WISP_ZAP_FRAME=YES source=$wisp_id " \
        | grep -F "$created_filter" \
        | grep -F "ageTick=$expected_age " \
        | head -n 1 || true)"
      if [ -n "$candidate" ]; then
        break
      fi
      sleep 0.005
    done
    if [ -z "$candidate" ]; then
      echo "WISP_ZAP_FRAME_TIMEOUT=YES source=$wisp_id createdAt=$created_at ageTick=$expected_age" | tee -a client-smoke-status.txt
      tail -120 "$log" >&2
      return 1
    fi
    marker_line="$(grep -n -F -- "$candidate" "$log" | tail -1 | cut -d: -f1)"
    if ! [[ "$marker_line" =~ ^[0-9]+$ ]]; then
      echo "WISP_ZAP_FRAME_LINE_NUMBER_INVALID=$marker_line" | tee -a client-smoke-status.txt
      return 1
    fi
    WISP_ZAP_FRAME_LINE="$candidate"
    WISP_ZAP_FRAME_LINE_NUMBER="$marker_line"
    WISP_ZAP_FRAME_CREATED_AT="$(sed -n 's/.* createdAt=\([0-9][0-9]*\) .*/\1/p' <<<"$candidate")"
    WISP_ZAP_FRAME_AGE_TICK="$expected_age"
    WISP_ZAP_FRAME_OBSERVED_AT_NS="$(date +%s%N)"
    return 0
  }
  wisp_wait_for_completed_wisp_frame() {
    local after_line="$1" wisp_id="$2" wisp_tick="$3" marker frame_line
    marker="TC_SMOKE_WISP_FRAME_COMPLETE=YES id=$wisp_id tick=$wisp_tick"
    for _ in $(seq 1 200); do
      frame_line="$(tail -n +"$((after_line + 1))" client-smoke/client.log | grep -F -- "$marker" | head -1 || true)"
      if [ -n "$frame_line" ]; then
        WISP_ZAP_FRAME_COMPLETE_LINE_NUMBER="$(grep -n -F -- "$marker" client-smoke/client.log | tail -1 | cut -d: -f1)"
        if [[ "$WISP_ZAP_FRAME_COMPLETE_LINE_NUMBER" =~ ^[0-9]+$ ]]; then return 0; fi
        echo "WISP_ZAP_FRAME_COMPLETE_LINE_NUMBER_INVALID=$WISP_ZAP_FRAME_COMPLETE_LINE_NUMBER" | tee -a client-smoke-status.txt
        return 1
      fi
      sleep 0.005
    done
    echo "WISP_ZAP_FRAME_COMPLETE_TIMEOUT=YES id=$wisp_id tick=$wisp_tick" | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    return 1
  }

  wisp_capture_zap_frames() {
    local after_lines="$1" wisp_id="$2" cursor="$1" created_at="" target_id payload_line rendered_line
    local expected_age wisp_tick observed_at_ns
    wisp_wait_for_zap_frame "$cursor" "$wisp_id" "" 1
    created_at="$WISP_ZAP_FRAME_CREATED_AT"
    payload_line="$(grep -F "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source=$wisp_id " client-smoke/client.log \
      | grep -F "createdAt=$created_at " | tail -1 || true)"
    if [ -z "$payload_line" ]; then
      echo "WISP_ZAP_CAPTURE_PAYLOAD_MISSING=YES source=$wisp_id createdAt=$created_at" | tee -a client-smoke-status.txt
      return 1
    fi
    target_id="$(sed -n 's/.* target=\([0-9][0-9]*\) .*/\1/p' <<<"$payload_line")"
    if ! [[ "$target_id" =~ ^[0-9]+$ ]]; then
      echo "WISP_ZAP_CAPTURE_TARGET_INVALID=$target_id" | tee -a client-smoke-status.txt
      return 1
    fi
    wisp_wait_for_marker "TC_SMOKE_WISP_ZAP_SENT=YES source=$wisp_id target=$target_id"
    rendered_line="$(grep -F "TC_SMOKE_WISP_ZAP_RENDERED=YES source=$wisp_id target=$target_id " client-smoke/client.log \
      | grep -F "createdAt=$created_at " | tail -1 || true)"
    if [ -z "$rendered_line" ]; then
      echo "WISP_ZAP_CAPTURE_RENDER_MISSING=YES source=$wisp_id target=$target_id createdAt=$created_at" | tee -a client-smoke-status.txt
      return 1
    fi
    for expected_age in 1 2 3; do
      if [ "$expected_age" -gt 1 ]; then
        cursor="$WISP_ZAP_FRAME_COMPLETE_LINE_NUMBER"
        wisp_wait_for_zap_frame "$cursor" "$wisp_id" "$created_at" "$expected_age"
      fi
      wisp_tick="$(sed -n 's/.* wispTick=\([0-9][0-9]*\) .*/\1/p' <<<"$WISP_ZAP_FRAME_LINE")"
      if ! [[ "$wisp_tick" =~ ^[0-9]+$ ]]; then
        echo "WISP_ZAP_FRAME_WISP_TICK_INVALID=$wisp_tick" | tee -a client-smoke-status.txt
        return 1
      fi
      wisp_wait_for_completed_wisp_frame "$WISP_ZAP_FRAME_LINE_NUMBER" "$wisp_id" "$wisp_tick"
      observed_at_ns="$WISP_ZAP_FRAME_OBSERVED_AT_NS"
      echo "WISP_ZAP_FRAME_OBSERVED=YES source=$wisp_id createdAt=$created_at ageTick=$expected_age wispTick=$wisp_tick observedAtNs=$observed_at_ns" | tee -a client-smoke-status.txt
      cursor="$WISP_ZAP_FRAME_COMPLETE_LINE_NUMBER"
    done
    wisp_wait_for_marker "TC_SMOKE_WISP_ZAP_EXPIRED=YES source=$wisp_id target=$target_id createdAt=$created_at"
    echo "WISP_ZAP_EFFECT_EXPIRED=YES source=$wisp_id createdAt=$created_at" | tee -a client-smoke-status.txt
    wisp_stop_zap_video_capture
    wisp_capture 83-wisp-zap-after-expiry
    python tools/verify_wisp_zap_client.py --extract-video-frames-only \
      --video-status client-smoke-status.txt \
      --video-file client-smoke/wisp-zap-video.mp4 \
      --video-screenshots client-smoke/screenshots | tee -a client-smoke-status.txt
  }

  wisp_send_chat() {
    local command="$1" marker="$2"
    wisp_command "$command"
    wisp_wait_for_marker "$marker"
  }

  # Pre-aim at the Wisp AABB centre before it exists. Do not capture a desktop
  # screenshot between the first completed frame and the real click: capture is
  # deliberately deferred until the server accepts the hit and stops autonomous flight.
  wisp_wait_for_fresh_render() {
    local after_lines="$1" track_marker track_line track_id track_tick track_line_number
    local render_line render_tick frame_marker camera_line frame_line frame_line_number
    track_marker='TC_SMOKE_WISP_CAMERA_TRACK_PRE_ATTACK=YES id='
    for _ in $(seq 1 200); do
      track_line="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F "$track_marker" | head -1 || true)"
      if [ -n "$track_line" ]; then
        break
      fi
      sleep 0.005
    done
    if [ -z "$track_line" ]; then
      echo 'WISP_PREATTACK_CAMERA_TRACK_TIMEOUT=YES' | tee -a client-smoke-status.txt
      tail -120 client-smoke/client.log >&2
      exit 1
    fi
    track_id="$(sed -n 's/.*TC_SMOKE_WISP_CAMERA_TRACK_PRE_ATTACK=YES id=\([0-9][0-9]*\) tick=.*/\1/p' <<<"$track_line")"
    track_tick="$(sed -n 's/.* tick=\([0-9][0-9]*\) gameTime=.*/\1/p' <<<"$track_line")"
    track_line_number="$(grep -n -F -- "$track_line" client-smoke/client.log | head -1 | cut -d: -f1)"
    if ! [[ "$track_id" =~ ^[0-9]+$ && "$track_tick" =~ ^[0-9]+$ && "$track_line_number" =~ ^[0-9]+$ ]]; then
      echo "WISP_PREATTACK_CAMERA_TRACK_INVALID id=$track_id tick=$track_tick line=$track_line_number" | tee -a client-smoke-status.txt
      exit 1
    fi
    echo "WISP_RENDER_PREATTACK_TRACKING=YES id=$track_id tick=$track_tick line=$track_line_number" | tee -a client-smoke-status.txt

    for _ in $(seq 1 200); do
      render_line="$(tail -n +"$((track_line_number + 1))" client-smoke/client.log | grep -F "TC_SMOKE_WISP_RENDERED=YES id=$track_id aspect=aer" | head -1 || true)"
      if [ -n "$render_line" ]; then
        break
      fi
      sleep 0.005
    done
    if [ -z "$render_line" ]; then
      echo "WISP_RENDER_AFTER_PREATTACK_TRACK_TIMEOUT=id=$track_id" | tee -a client-smoke-status.txt
      tail -120 client-smoke/client.log >&2
      exit 1
    fi
    render_tick="$(sed -n 's/.* tick=\([0-9][0-9]*\).*/\1/p' <<<"$render_line")"
    if ! [[ "$render_tick" =~ ^[0-9]+$ ]]; then
      echo "WISP_RENDER_CALLBACK_INVALID id=$track_id tick=$render_tick" | tee -a client-smoke-status.txt
      exit 1
    fi
    frame_marker="TC_SMOKE_WISP_FRAME_COMPLETE=YES id=$track_id tick=$render_tick"
    camera_line=""
    frame_line=""
    for _ in $(seq 1 200); do
      camera_line="$(tail -n +"$((track_line_number + 1))" client-smoke/client.log | grep -F "TC_SMOKE_WISP_CAMERA=YES id=$track_id aimTarget=hitbox_center tick=$render_tick " | head -1 || true)"
      frame_line="$(tail -n +"$((track_line_number + 1))" client-smoke/client.log | grep -F "$frame_marker" | head -1 || true)"
      if [ -n "$camera_line" ] && [ -n "$frame_line" ]; then
        break
      fi
      sleep 0.005
    done
    if [ -z "$camera_line" ] || [ -z "$frame_line" ]; then
      echo "WISP_RENDER_TRACKED_FRAME_INCOMPLETE=id=$track_id tick=$render_tick" | tee -a client-smoke-status.txt
      tail -120 client-smoke/client.log >&2
      exit 1
    fi
    frame_line_number="$(grep -n -F -- "$frame_marker" client-smoke/client.log | awk -F: -v start="$track_line_number" '$1 > start { print $1; exit }')"
    if ! [[ "$frame_line_number" =~ ^[0-9]+$ ]]; then
      echo "WISP_FIRST_FRAME_LINE_NUMBER_INVALID=$frame_line_number" | tee -a client-smoke-status.txt
      exit 1
    fi
    WISP_RENDER_FIRST_FRAME_LINE_NUMBER="$frame_line_number"
    echo "WISP_RENDER_FIRST_FRAME_LINE_NUMBER=$frame_line_number" | tee -a client-smoke-status.txt
    echo "WISP_RENDER_FRAME_COMPLETE=YES id=$track_id tick=$render_tick" | tee -a client-smoke-status.txt
    echo "WISP_RENDER_FIRST_FRAME_ID=$track_id" | tee -a client-smoke-status.txt
    echo "WISP_RENDER_FIRST_FRAME_TICK=$render_tick" | tee -a client-smoke-status.txt
    echo "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=$camera_line" | tee -a client-smoke-status.txt
    echo "WISP_RENDER_FIRST_FRAME=YES id=$track_id tick=$render_tick" | tee -a client-smoke-status.txt
    echo 'WISP_RENDER_AFTER_PREAIMED_SPAWN=YES' | tee -a client-smoke-status.txt
    return 0
  }

  wisp_reanchor_player_for_capture() {
    local after_lines="$1" teleport_match relative_line
    # Re-anchor only the test player on the verified platform. The client-only
    # tick probe keeps the exact attacked Wisp centred; never move/freeze it.
    # Return the server log line number so callers include the first fresh
    # camera sample instead of skipping it with a later wc -l snapshot.
    # Keep the default chat-open/type pacing used by the native client.
    wisp_fast_command '/execute as @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] at @s run tp @p 12.5 149 10.5'
    for _ in $(seq 1 200); do
      teleport_match="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -n -F 'Teleported Dev to 12.500000, 149.000000, 10.500000' | head -1 || true)"
      if [ -n "$teleport_match" ]; then
        relative_line="$(cut -d: -f1 <<<"$teleport_match")"
        WISP_CAMERA_REANCHOR_LINE="$(cut -d: -f2- <<<"$teleport_match")"
        WISP_CAMERA_REANCHOR_LINE_NUMBER="$((after_lines + relative_line))"
        echo "WISP_CAMERA_REANCHORED=FIXTURE_PLATFORM $WISP_CAMERA_REANCHOR_LINE" | tee -a client-smoke-status.txt
        echo "WISP_CAMERA_REAIM=CLIENT_TRACKING" | tee -a client-smoke-status.txt
        return 0
      fi
      sleep 0.025
    done
    echo 'WISP_CAMERA_REANCHOR_TIMEOUT=YES' | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    exit 1
  }

  wisp_move_player_to_live_wisp() {
    local after_lines="$1" teleport_match relative_line
    # The first click can be delayed by client tick scheduling while the Wisp
    # keeps flying. Retry from a melee position relative to the exact tagged
    # smoke entity; do not select an unrelated naturally spawned Wisp.
    # This moves only the Survival player. Wisp AI, motion and target remain live.
    # Resolve facing against the same tagged Wisp, rather than a relative point whose rotation stayed stale in the real client.
    wisp_fast_command '/execute as @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] at @s run tp @p ~1.0 ~ ~ facing entity @s eyes'
    for _ in $(seq 1 200); do
      teleport_match="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -n -F 'Teleported Dev to ' | head -1 || true)"
      if [ -n "$teleport_match" ]; then
        relative_line="$(cut -d: -f1 <<<"$teleport_match")"
        WISP_ATTACK_REPOSITION_LINE="$(cut -d: -f2- <<<"$teleport_match")"
        WISP_ATTACK_REPOSITION_LINE_NUMBER="$((after_lines + relative_line))"
        echo "WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION $WISP_ATTACK_REPOSITION_LINE" | tee -a client-smoke-status.txt
        echo 'WISP_CAMERA_REANCHORED=LIVE_WISP_MELEE_POSITION' | tee -a client-smoke-status.txt
        return 0
      fi
      sleep 0.025
    done
    echo 'WISP_CAMERA_REPOSITION_TIMEOUT=YES' | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    exit 1
  }

  wisp_wait_for_attack_frame() {
    local after_lines="$1" wisp_id="$2" position_mode="${3:-PLATFORM}" attempt="${4:-1}" frame_pattern frame_line camera_tick game_time player_tick camera_line click_log_lines frame_fd frame_tail_pid candidate_line watcher_status deadline remaining
    local player_x player_y player_z wisp_x wisp_y wisp_z aim_dot distance
    frame_pattern="TC_SMOKE_WISP_FRAME_COMPLETE=YES id=$wisp_id .*hitType=ENTITY targetId=$wisp_id targetType=thaumcraft:wisp blockPos=none"
    click_log_lines="$(wc -l < client-smoke/client.log)"
    WISP_ATTACK_INPUT_LOG_LINES="$click_log_lines"
    frame_line=""
    watcher_status=0
    # On runners without inotify, GNU tail otherwise polls once per second; bound the live hit-to-click fallback.
    exec {frame_fd}< <(tail --sleep-interval=0.01 -n +"$((after_lines + 1))" -f client-smoke/client.log)
    frame_tail_pid="$!"
    deadline=$((SECONDS + 5))
    while :; do
      remaining=$((deadline - SECONDS))
      if [ "$remaining" -le 0 ]; then
        break
      fi
      if ! IFS= read -r -t "$remaining" -u "$frame_fd" candidate_line; then
        break
      fi
      if [[ "$candidate_line" =~ $frame_pattern ]]; then
        frame_line="$candidate_line"
        # Dispatch the real OS click inside the streaming reader. Do not wait
        # for tail/grep teardown or parse camera telemetry before sending input.
        if ! xdotool click 1; then
          echo "WISP_ATTACK_CLICK_DISPATCH_FAILED=YES id=$wisp_id" | tee -a client-smoke-status.txt
          watcher_status=1
        else
          echo "WISP_ATTACK_CLICK_DISPATCHED=YES id=$wisp_id" | tee -a client-smoke-status.txt
        fi
        break
      fi
    done
    kill "$frame_tail_pid" 2>/dev/null || true
    wait "$frame_tail_pid" 2>/dev/null || true
    exec {frame_fd}<&-
    if [ "$watcher_status" -ne 0 ]; then
      tail -100 client-smoke/client.log >&2
      exit 1
    fi
    if [ -z "$frame_line" ]; then
      echo "WISP_ATTACKABLE_FRAME_TIMEOUT=id=$wisp_id" | tee -a client-smoke-status.txt
      tail -100 client-smoke/client.log >&2
      return 1
    fi
    echo "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK attempt=$attempt" | tee -a client-smoke-status.txt
    if [[ "$frame_line" =~ TC_SMOKE_WISP_FRAME_COMPLETE=YES\ id=([0-9]+)\ tick=([0-9]+)\ gameTime=([0-9]+)\ playerTick=([0-9]+) ]]; then
      camera_tick="${BASH_REMATCH[2]}"
      game_time="${BASH_REMATCH[3]}"
      player_tick="${BASH_REMATCH[4]}"
    else
      echo "WISP_ATTACKABLE_FRAME_PARSE_FAILED=$frame_line" | tee -a client-smoke-status.txt
      exit 1
    fi
    camera_line="$(grep -F "TC_SMOKE_WISP_CAMERA=YES id=$wisp_id aimTarget=hitbox_center tick=$camera_tick " client-smoke/client.log | tail -1 || true)"
    if [[ "$camera_line" =~ playerX=([-0-9.]+)\ playerY=([-0-9.]+)\ playerZ=([-0-9.]+).*wispX=([-0-9.]+)\ wispY=([-0-9.]+)\ wispZ=([-0-9.]+).*aimDot=(-?[0-9.]+) ]]; then
      player_x="${BASH_REMATCH[1]}"
      player_y="${BASH_REMATCH[2]}"
      player_z="${BASH_REMATCH[3]}"
      wisp_x="${BASH_REMATCH[4]}"
      wisp_y="${BASH_REMATCH[5]}"
      wisp_z="${BASH_REMATCH[6]}"
      aim_dot="${BASH_REMATCH[7]}"
    else
      echo "WISP_ATTACKABLE_FRAME_CAMERA_MISSING=$frame_line" | tee -a client-smoke-status.txt
      exit 1
    fi
    distance="$(awk -v px="$player_x" -v py="$player_y" -v pz="$player_z" -v wx="$wisp_x" -v wy="$wisp_y" -v wz="$wisp_z" 'BEGIN { dx=px-wx; dy=py-wy; dz=pz-wz; printf "%.4f", sqrt(dx*dx+dy*dy+dz*dz) }')"
    # The same-frame, same-ID vanilla EntityHitResult is the ray test. This dot
    # compares an interpolated view vector with the AABB center and is diagnostic;
    # it must not reject an actual ENTITY hit that Minecraft has already selected.
    if ! awk -v mode="$position_mode" -v x="$player_x" -v y="$player_y" -v z="$player_z" -v d="$distance" -v dot="$aim_dot" \
      'BEGIN {
        on_platform = x >= 12.0 && x <= 13.0 && y >= 148.5 && y <= 149.5 && z >= 10.0 && z <= 11.0;
        position_ok = mode == "PLATFORM" ? on_platform : y >= 0.0;
        exit !(position_ok && d <= 3.0 && dot >= -1.0001 && dot <= 1.0001)
      }'; then
      echo "WISP_ATTACKABLE_FRAME_CAMERA_REJECTED=id=$wisp_id tick=$camera_tick position=$position_mode distance=$distance aimDot=$aim_dot" | tee -a client-smoke-status.txt
      exit 1
    fi
    WISP_ATTACK_CAMERA_ID="$wisp_id"
    WISP_ATTACK_CAMERA_TICK="$camera_tick"
    WISP_ATTACK_CAMERA_GAME_TIME="$game_time"
    WISP_ATTACK_CAMERA_PLAYER_TICK="$player_tick"
    WISP_ATTACK_CAMERA_DISTANCE="$distance"
    WISP_ATTACK_CAMERA_AIM_DOT="$aim_dot"
    WISP_ATTACK_CAMERA_LINE="$camera_line"
    echo "WISP_ATTACKABLE_FRAME=YES id=$wisp_id tick=$camera_tick hitType=ENTITY targetId=$wisp_id position=$position_mode gameTime=$game_time playerTick=$player_tick distance=$distance aimDot=$aim_dot" | tee -a client-smoke-status.txt
    return 0
  }

  wisp_attack_live_wisp() {
    local wisp_id attempt reanchor_log_lines frame_log_lines attack_log_lines attack_input_line
    local attack_camera_source attack_camera_detail
    wisp_id="$(sed -n 's/^WISP_RENDER_FIRST_FRAME_ID=//p' client-smoke-status.txt | tail -1)"
    if ! [[ "$wisp_id" =~ ^[0-9]+$ ]]; then
      echo "WISP_ATTACK_WISP_ID_INVALID=$wisp_id" | tee -a client-smoke-status.txt
      exit 1
    fi
    # A geometric camera sample is not enough: wait for AFTER_LEVEL to report
    # the real Wisp EntityHitResult, then issue the real mouse click immediately.
    for attempt in 1 2 3; do
      if [ "$attempt" -eq 1 ]; then
        # wisp_wait_for_fresh_render already consumed the first completed frame for visibility.
        # Start after it and wait for a new AFTER_LEVEL frame while the click watcher is live.
        # Reusing the old line makes tail replay a stale hit result before input reaches Minecraft.
        frame_log_lines="$WISP_RENDER_FIRST_FRAME_LINE_NUMBER"
        if ! [[ "$frame_log_lines" =~ ^[0-9]+$ ]]; then
          echo "WISP_FIRST_FRAME_LINE_NUMBER_MISSING=$frame_log_lines" | tee -a client-smoke-status.txt
          exit 1
        fi
        attack_camera_source="PREAIMED_NEXT_FRAME"
        if ! wisp_wait_for_attack_frame "$frame_log_lines" "$wisp_id" PLATFORM "$attempt"; then
          echo "WISP_ATTACK_FRAME_RETRY=$attempt" | tee -a client-smoke-status.txt
          continue
        fi
        attack_camera_detail="id=$wisp_id tick=$WISP_ATTACK_CAMERA_TICK gameTime=$WISP_ATTACK_CAMERA_GAME_TIME"
      else
        reanchor_log_lines="$(wc -l < client-smoke/client.log)"
        wisp_move_player_to_live_wisp "$reanchor_log_lines"
        attack_camera_source="LIVE_ENTITY_FACING"
        attack_camera_detail="$WISP_ATTACK_REPOSITION_LINE"
        frame_log_lines="$WISP_ATTACK_REPOSITION_LINE_NUMBER"
        if ! wisp_wait_for_attack_frame "$frame_log_lines" "$wisp_id" LIVE_WISP "$attempt"; then
          echo "WISP_ATTACK_FRAME_RETRY=$attempt" | tee -a client-smoke-status.txt
          continue
        fi
      fi
      attack_log_lines="$WISP_ATTACK_INPUT_LOG_LINES"
      attack_input_line="$(timeout 5s bash -c 'tail -n +"$1" -f "$2" | grep --line-buffered -m1 -F "TC_SMOKE_WISP_ATTACK_INPUT=YES"' _ "$((attack_log_lines + 1))" client-smoke/client.log || true)"
      if [ -z "$attack_input_line" ]; then
        echo "WISP_ATTACK_INPUT_TIMEOUT=$attempt" | tee -a client-smoke-status.txt
        continue
      fi
      if grep -Fq "targetWisp=true targetId=$wisp_id targetType=thaumcraft:wisp hitType=ENTITY" <<<"$attack_input_line"; then
        echo "WISP_ATTACK_CAMERA_REAIM=$attack_camera_source $attack_camera_detail" | tee -a client-smoke-status.txt
        echo "WISP_ATTACK_CAMERA_FRAME=YES id=$WISP_ATTACK_CAMERA_ID tick=$WISP_ATTACK_CAMERA_TICK hitType=ENTITY targetId=$wisp_id distance=$WISP_ATTACK_CAMERA_DISTANCE aimDot=$WISP_ATTACK_CAMERA_AIM_DOT" | tee -a client-smoke-status.txt
        echo "WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES id=$WISP_ATTACK_CAMERA_ID tick=$WISP_ATTACK_CAMERA_TICK" | tee -a client-smoke-status.txt
        echo "WISP_ATTACK_CAMERA_SAMPLE=$WISP_ATTACK_CAMERA_LINE" | tee -a client-smoke-status.txt
        echo "WISP_ATTACK_CAMERA_FRAME_USED=YES attempt=$attempt" | tee -a client-smoke-status.txt
        echo "WISP_ATTACK_TARGET_CONFIRMED=YES" | tee -a client-smoke-status.txt
        return 0
      fi
      echo "WISP_ATTACK_ATTEMPT_REJECTED=$attempt $attack_input_line" | tee -a client-smoke-status.txt
    done
    echo 'WISP_ATTACK_RETRIES_EXHAUSTED=YES' | tee -a client-smoke-status.txt
    tail -160 client-smoke/client.log >&2
    exit 1
  }

  wisp_capture_aim_after_hit() {
    local after_lines="$1" wisp_id camera_line camera_tick aim_dot frame_marker
    wisp_id="$(sed -n 's/^WISP_RENDER_FIRST_FRAME_ID=//p' client-smoke-status.txt | tail -1)"
    if ! [[ "$wisp_id" =~ ^[0-9]+$ ]]; then
      echo "WISP_RENDER_FIRST_FRAME_ID_INVALID=$wisp_id" | tee -a client-smoke-status.txt
      exit 1
    fi
    for _ in $(seq 1 200); do
      camera_line="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F "TC_SMOKE_WISP_CAMERA=YES id=$wisp_id " | tail -1 || true)"
      if [ -n "$camera_line" ]; then
        camera_tick="$(sed -n 's/.* aimTarget=hitbox_center tick=\([0-9][0-9]*\) playerX=.*/\1/p' <<<"$camera_line")"
        aim_dot="$(sed -n 's/.* aimDot=\([0-9.][0-9.]*\).*/\1/p' <<<"$camera_line")"
        if [[ "$camera_tick" =~ ^[0-9]+$ && "$aim_dot" =~ ^[0-9.]+$ ]] \
          && awk -v dot="$aim_dot" 'BEGIN { exit !(dot >= 0.99) }'; then
          frame_marker="TC_SMOKE_WISP_FRAME_COMPLETE=YES id=$wisp_id tick=$camera_tick"
          if tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -Fq -- "$frame_marker"; then
            wisp_capture 78-wisp-aim-check
            echo "WISP_RENDER_CAPTURE_ID=$wisp_id" | tee -a client-smoke-status.txt
            echo "WISP_RENDER_CAPTURE_TICK=$camera_tick" | tee -a client-smoke-status.txt
            echo "WISP_RENDER_CAPTURE_AIM_DOT=$aim_dot" | tee -a client-smoke-status.txt
            echo "$camera_line" | tee -a client-smoke-status.txt
            echo 'WISP_RENDER_CAPTURE_AFTER_HIT=YES' | tee -a client-smoke-status.txt
            return 0
          fi
        fi
      fi
      sleep 0.005
    done
    echo 'WISP_RENDER_CAPTURE_AFTER_HIT_TIMEOUT=YES' | tee -a client-smoke-status.txt
    return 1
  }

  wisp_capture_aim_after_hit_with_retries() {
    local attempt reanchor_log_lines after_hit_log_lines
    for attempt in 1 2 3; do
      reanchor_log_lines="$(wc -l < client-smoke/client.log)"
      wisp_reanchor_player_for_capture "$reanchor_log_lines"
      after_hit_log_lines="$WISP_CAMERA_REANCHOR_LINE_NUMBER"
      if wisp_capture_aim_after_hit "$after_hit_log_lines"; then
        echo "WISP_RENDER_CAPTURE_REANCHOR_ATTEMPT=$attempt" | tee -a client-smoke-status.txt
        return 0
      fi
      echo "WISP_RENDER_CAPTURE_RETRY=$attempt" | tee -a client-smoke-status.txt
    done
    echo 'WISP_RENDER_CAPTURE_RETRIES_EXHAUSTED=YES' | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    return 1
  }

  wisp_wait_for_teleport() {
    local after_lines="$1"
    for _ in $(seq 1 200); do
      local teleport_line
      teleport_line="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F 'Teleported Dev to ' | tail -1 || true)"
      if [ -n "$teleport_line" ]; then
        echo "WISP_FIXTURE_TELEPORT=YES $teleport_line" | tee -a client-smoke-status.txt
        return 0
      fi
      sleep 0.025
    done
    echo 'WISP_FIXTURE_TELEPORT_TIMEOUT=YES' | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    exit 1
  }

  wisp_verify_aim_telemetry() {
    local after_lines="$1" telemetry capture_camera frame_camera_marker camera_sample_count
    local player_x player_y player_z wisp_x wisp_y wisp_z distance aim_dot
    local wisp_id camera_tick first_frame_id first_frame_tick capture_id capture_tick
    local capture_camera_id capture_camera_tick capture_player_x capture_player_y capture_player_z
    local capture_wisp_x capture_wisp_y capture_wisp_z capture_distance capture_aim_dot

    first_frame_id="$(sed -n 's/^WISP_RENDER_FIRST_FRAME_ID=//p' client-smoke-status.txt | tail -1)"
    first_frame_tick="$(sed -n 's/^WISP_RENDER_FIRST_FRAME_TICK=//p' client-smoke-status.txt | tail -1)"
    capture_id="$(sed -n 's/^WISP_RENDER_CAPTURE_ID=//p' client-smoke-status.txt | tail -1)"
    capture_tick="$(sed -n 's/^WISP_RENDER_CAPTURE_TICK=//p' client-smoke-status.txt | tail -1)"
    if ! [[ "$first_frame_id" =~ ^[0-9]+$ && "$first_frame_tick" =~ ^[0-9]+$ ]]; then
      echo "WISP_CAMERA_FIRST_FRAME_STATUS_INVALID id=$first_frame_id tick=$first_frame_tick" | tee -a client-smoke-status.txt
      exit 1
    fi
    frame_camera_marker="TC_SMOKE_WISP_CAMERA=YES id=$first_frame_id aimTarget=hitbox_center tick=$first_frame_tick "
    camera_sample_count="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F -c -- "$frame_camera_marker" || true)"
    if [ "$camera_sample_count" -ne 1 ]; then
      echo "WISP_CAMERA_FIRST_FRAME_SAMPLE_COUNT=$camera_sample_count expected=1 id=$first_frame_id tick=$first_frame_tick" | tee -a client-smoke-status.txt
      tail -120 client-smoke/client.log >&2
      exit 1
    fi
    telemetry="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F -- "$frame_camera_marker" | head -1 || true)"
    if [ -z "$telemetry" ]; then
      echo 'WISP_CAMERA_TELEMETRY_MISSING=YES' | tee -a client-smoke-status.txt
      tail -120 client-smoke/client.log >&2
      exit 1
    fi
    wisp_id="$(sed -n 's/.*TC_SMOKE_WISP_CAMERA=YES id=\([0-9][0-9]*\) aimTarget=.*/\1/p' <<<"$telemetry")"
    camera_tick="$(sed -n 's/.* tick=\([0-9][0-9]*\).*/\1/p' <<<"$telemetry")"

    player_x="$(sed -n 's/.* playerX=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    player_y="$(sed -n 's/.* playerY=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    player_z="$(sed -n 's/.* playerZ=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    wisp_x="$(sed -n 's/.* wispX=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    wisp_y="$(sed -n 's/.* wispY=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    wisp_z="$(sed -n 's/.* wispZ=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    aim_dot="$(sed -n 's/.* aimDot=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    distance="$(awk -v px="$player_x" -v py="$player_y" -v pz="$player_z" -v wx="$wisp_x" -v wy="$wisp_y" -v wz="$wisp_z" 'BEGIN { dx=px-wx; dy=py-wy; dz=pz-wz; printf "%.4f", sqrt(dx*dx+dy*dy+dz*dz) }')"
    if ! [[ "$wisp_id" =~ ^[0-9]+$ && "$camera_tick" =~ ^[0-9]+$ && "$first_frame_id" =~ ^[0-9]+$ && "$first_frame_tick" =~ ^[0-9]+$ && "$capture_id" =~ ^[0-9]+$ && "$capture_tick" =~ ^[0-9]+$ ]] \
      || [ "$camera_tick" != "$first_frame_tick" ] || [ "$wisp_id" != "$first_frame_id" ] || [ "$wisp_id" != "$capture_id" ]; then
      echo "WISP_CAMERA_RENDER_TICK_MISMATCH id=$wisp_id camera_tick=$camera_tick first_frame_id=$first_frame_id first_frame_tick=$first_frame_tick capture_id=$capture_id capture_tick=$capture_tick" | tee -a client-smoke-status.txt
      echo "$telemetry" >&2
      exit 1
    fi
    if ! awk -v x="$player_x" -v y="$player_y" -v z="$player_z" -v d="$distance" -v dot="$aim_dot" \
      'BEGIN { exit !(x >= 12.0 && x <= 13.0 && y >= 148.5 && y <= 149.5 && z >= 10.0 && z <= 11.0 && d <= 3.0 && dot >= 0.99) }'; then
      echo "WISP_CAMERA_TELEMETRY_FAILED player=($player_x,$player_y,$player_z) wisp=($wisp_x,$wisp_y,$wisp_z) distance=$distance aimDot=$aim_dot" | tee -a client-smoke-status.txt
      echo "$telemetry" >&2
      exit 1
    fi

    capture_camera="$(grep -F 'TC_SMOKE_WISP_CAMERA=YES' client-smoke-status.txt | tail -1 || true)"
    capture_camera_id="$(sed -n 's/.*TC_SMOKE_WISP_CAMERA=YES id=\([0-9][0-9]*\) aimTarget=.*/\1/p' <<<"$capture_camera")"
    capture_camera_tick="$(sed -n 's/.* aimTarget=hitbox_center tick=\([0-9][0-9]*\) playerX=.*/\1/p' <<<"$capture_camera")"
    capture_player_x="$(sed -n 's/.* playerX=\([^ ]*\).*/\1/p' <<<"$capture_camera")"
    capture_player_y="$(sed -n 's/.* playerY=\([^ ]*\).*/\1/p' <<<"$capture_camera")"
    capture_player_z="$(sed -n 's/.* playerZ=\([^ ]*\).*/\1/p' <<<"$capture_camera")"
    capture_wisp_x="$(sed -n 's/.* wispX=\([^ ]*\).*/\1/p' <<<"$capture_camera")"
    capture_wisp_y="$(sed -n 's/.* wispY=\([^ ]*\).*/\1/p' <<<"$capture_camera")"
    capture_wisp_z="$(sed -n 's/.* wispZ=\([^ ]*\).*/\1/p' <<<"$capture_camera")"
    capture_aim_dot="$(sed -n 's/.* aimDot=\([^ ]*\).*/\1/p' <<<"$capture_camera")"
    capture_distance="$(awk -v px="$capture_player_x" -v py="$capture_player_y" -v pz="$capture_player_z" -v wx="$capture_wisp_x" -v wy="$capture_wisp_y" -v wz="$capture_wisp_z" 'BEGIN { dx=px-wx; dy=py-wy; dz=pz-wz; printf "%.4f", sqrt(dx*dx+dy*dy+dz*dz) }')"
    if ! [[ "$capture_camera_id" =~ ^[0-9]+$ && "$capture_camera_tick" =~ ^[0-9]+$ && "$capture_player_x" =~ ^[-0-9.]+$ && "$capture_player_y" =~ ^[-0-9.]+$ && "$capture_player_z" =~ ^[-0-9.]+$ && "$capture_wisp_x" =~ ^[-0-9.]+$ && "$capture_wisp_y" =~ ^[-0-9.]+$ && "$capture_wisp_z" =~ ^[-0-9.]+$ && "$capture_aim_dot" =~ ^[0-9.]+$ ]] \
      || [ "$capture_camera_id" != "$capture_id" ] || [ "$capture_camera_tick" != "$capture_tick" ] \
      || ! awk -v x="$capture_player_x" -v y="$capture_player_y" -v z="$capture_player_z" -v d="$capture_distance" -v dot="$capture_aim_dot" \
        'BEGIN { exit !(x >= 12.0 && x <= 13.0 && y >= 148.5 && y <= 149.5 && z >= 10.0 && z <= 11.0 && d <= 16.0 && dot >= 0.99) }'; then
      echo "WISP_CAMERA_CAPTURE_TELEMETRY_FAILED id=$capture_camera_id tick=$capture_camera_tick distance=$capture_distance aimDot=$capture_aim_dot" | tee -a client-smoke-status.txt
      echo "$capture_camera" >&2
      exit 1
    fi
    echo "WISP_CAMERA_CAPTURE_TELEMETRY=PASS id=$capture_camera_id tick=$capture_camera_tick distance=$capture_distance aimDot=$capture_aim_dot" | tee -a client-smoke-status.txt
    echo "WISP_CAMERA_TELEMETRY=PASS id=$wisp_id tick=$camera_tick playerX=$player_x playerY=$player_y playerZ=$player_z wispX=$wisp_x wispY=$wisp_y wispZ=$wisp_z distance=$distance aimDot=$aim_dot" | tee -a client-smoke-status.txt
  }
  wisp_wait_for_loaded_chunk() {
    local marker='TC_SMOKE_WISP_CHUNK_LOADED=YES'
    # Poll the destination chunk after teleport. "execute if loaded" is a
    # read-only check: it does not force-load the chunk or mutate the world.
    for _ in $(seq 1 20); do
      wisp_fast_command '/execute if loaded 10 148 10 run say TC_SMOKE_WISP_CHUNK_LOADED=YES'
      if grep -Fq -- "$marker" client-smoke/client.log; then
        echo 'WISP_FIXTURE_CHUNK_LOADED=YES' | tee -a client-smoke-status.txt
        return 0
      fi
    done
    echo 'WISP_FIXTURE_CHUNK_LOAD_TIMEOUT=YES' | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    exit 1
  }

  # Treat the new vanilla server fill result as the platform gate. This
  # avoids a second chat predicate whose own input/coordinate path can fail
  # after the server has already confirmed all 35 blocks were placed.
  wisp_wait_for_fill() {
    local after_lines="$1"
    for _ in $(seq 1 200); do
      local fill_line
      fill_line="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F 'Successfully filled 81 block(s)' | tail -1 || true)"
      if [ -n "$fill_line" ]; then
        echo "WISP_FIXTURE_PLATFORM_FILL=YES $fill_line" | tee -a client-smoke-status.txt
        return 0
      fi
      sleep 0.025
    done
    echo 'WISP_FIXTURE_PLATFORM_FILL_TIMEOUT=YES' | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    exit 1
  }

  wisp_capture() {
    local name="$1"
    import -display :99 -window root -silent "client-smoke/screenshots/$name.png"
    test -s "client-smoke/screenshots/$name.png"
    echo "WISP_REAL_CLIENT_SCREEN=$name" | tee -a client-smoke-status.txt
  }

  # The base fixture creates a safe creative/peaceful world. Switch only this
  # isolated scenario to normal difficulty and survival so WispEntity.hurt()
  # receives a real Player attacker and exercises its ordinary target path.
  wisp_command '/difficulty normal'
  wisp_command '/gamerule doMobSpawning false'
  wisp_command '/gamerule doDaylightCycle false'
  wisp_command '/gamerule doWeatherCycle false'
  wisp_send_chat '/time set midnight' 'Set the time to 18000'
  echo 'WISP_ZAP_TEST_TIME=MIDNIGHT' | tee -a client-smoke-status.txt
  wisp_command '/gamemode survival'
  wisp_command '/effect give @p minecraft:resistance infinite 4 true'
  # The integrated-world bootstrap leaves the player at a random spawn
  # (currently around -327,-245). Teleport into the fixture area BEFORE
  # placing blocks there; an absolute /fill from the old spawn fails with
  # "That position is not loaded" and silently leaves the platform absent.
  wisp_command '/tp @p 10.5 149 8.5 0 22'
  wisp_wait_for_loaded_chunk
  fill_log_lines="$(wc -l < client-smoke/client.log)"
  # Use an absolute 9x9 floor inside the already-loaded chunk. The previous
  # relative fill reported 35 changed blocks, but telemetry showed the player
  # falling immediately after teleport; a fill count did not prove support.
  wisp_fast_command '/fill 6 148 6 14 148 14 minecraft:stone'
  wisp_wait_for_fill "$fill_log_lines"

  # Put the player on one known floor block and ask the server to prove that
  # exact block is stone before spawning or aiming at the Wisp.
  fixture_teleport_log_lines="$(wc -l < client-smoke/client.log)"
  wisp_fast_command '/tp @p 12.5 149 10.5 0 41'
  wisp_wait_for_teleport "$fixture_teleport_log_lines"
  wisp_send_chat '/execute if block 12 148 10 minecraft:stone run say TC_SMOKE_WISP_SUPPORT_BLOCK=YES' 'TC_SMOKE_WISP_SUPPORT_BLOCK=YES'
  wisp_send_chat '/execute if entity @p[gamemode=survival] run say TC_SMOKE_WISP_PLAYER_SURVIVAL=YES' 'TC_SMOKE_WISP_PLAYER_SURVIVAL=YES'

  # Capture stable animation-noise baselines BEFORE the Wisp exists. BETA26
  # autonomous flight starts immediately; post-spawn baseline delays let it
  # leave the crosshair before the real melee click.
  xdotool key --clearmodifiers F1
  sleep 0.05
  wisp_capture 79-wisp-before-a
  python tools/verify_wisp_zap_client.py --baseline-only client-smoke/screenshots/79-wisp-before-a.png | tee -a client-smoke-status.txt
  sleep 0.05
  wisp_capture 79-wisp-before-b
  python tools/verify_wisp_zap_client.py --baseline-only client-smoke/screenshots/79-wisp-before-b.png | tee -a client-smoke-status.txt
  echo 'WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN' | tee -a client-smoke-status.txt

  # Pre-aim at the exact spawn AABB centre. The first completed level frame is
  # the trigger for the real click; no external screenshot is taken beforehand.
  wisp_start_zap_video_capture
  aim_log_lines="$(wc -l < client-smoke/client.log)"
  wisp_fast_command "/summon thaumcraft:wisp 12.5 149.3 11.5 {Type:\"aer\",Tags:[\"tc_wisp_zap_smoke\"],CustomName:'{\"text\":\"tc_wisp_zap_smoke\"}',CustomNameVisible:0b}"
  echo 'WISP_ZAP_AIM=PREAIMED_SPAWN' | tee -a client-smoke-status.txt
  wisp_wait_for_fresh_render "$aim_log_lines"

  wisp_attack_live_wisp
  wisp_wait_for_marker 'TC_SMOKE_WISP_CLIENT_ATTACK=YES'
  wisp_wait_for_marker 'TC_SMOKE_WISP_CAMERA_TRACK=YES'
  wisp_wait_for_marker 'TC_SMOKE_WISP_HURT=YES accepted=true'
  echo 'WISP_ATTACK_HURT_ACCEPTED=YES' | tee -a client-smoke-status.txt
  wisp_wait_for_marker 'TC_SMOKE_WISP_TARGET_SET=YES'

  # The real Survival hit and target acquisition are already complete. Keep
  # the player on the verified platform; the client-only probe tracks the same
  # attacked Wisp's AABB centre while its BETA26 flight and zap wind-up continue.
  # No Wisp movement, target, damage or FX state is changed by the camera probe.
  wisp_id="$(sed -n 's/^WISP_RENDER_FIRST_FRAME_ID=//p' client-smoke-status.txt | tail -1)"
  zap_log_lines="$(wc -l < client-smoke/client.log)"
  wisp_send_chat '/attribute @p minecraft:generic.knockback_resistance base set 1' 'Base value for attribute Knockback Resistance for entity Dev set to 1.0'
  echo 'WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL' | tee -a client-smoke-status.txt

  # Capture three distinct BETA26 effect ages immediately, each after its
  # matching AFTER_LEVEL frame. A later camera re-anchor would miss the
  # three-tick bolt entirely.
  wisp_capture_zap_frames "$zap_log_lines" "$wisp_id"

  wisp_capture_aim_after_hit_with_retries
  wisp_verify_aim_telemetry "$aim_log_lines"
  python tools/verify_wisp_zap_client.py --aim-only client-smoke/screenshots/78-wisp-aim-check.png | tee -a client-smoke-status.txt

  wisp_crop_metric() {
    local left="$1" right="$2" temporary metric
    temporary="$(mktemp -d)"
    convert "$left" -crop 360x320+332+340 +repage "$temporary/left.png"
    convert "$right" -crop 360x320+332+340 +repage "$temporary/right.png"
    metric="$(compare -metric AE -fuzz 4% "$temporary/left.png" "$temporary/right.png" null: 2>&1 || true)"
    rm -rf "$temporary"
    printf '%s\n' "$metric" | awk 'NR == 1 {print $1}'
  }

  baseline_noise="$(wisp_crop_metric \
    client-smoke/screenshots/79-wisp-before-a.png \
    client-smoke/screenshots/79-wisp-before-b.png)"
  transient_diff="$(wisp_crop_metric \
    client-smoke/screenshots/80-wisp-zap-frame-a.png \
    client-smoke/screenshots/83-wisp-zap-after-expiry.png)"
  if ! [[ "$baseline_noise" =~ ^[0-9]+$ ]] || ! [[ "$transient_diff" =~ ^[0-9]+$ ]]; then
    echo "WISP_ZAP_PIXEL_METRIC_INVALID baseline=$baseline_noise transient=$transient_diff" | tee -a client-smoke-status.txt
    exit 1
  fi
  threshold=$((baseline_noise * 2 + 8))
  if [ "$transient_diff" -lt "$threshold" ]; then
    echo "WISP_ZAP_TRANSIENT_PIXEL_DIFF_FAILED=$transient_diff threshold=$threshold baseline=$baseline_noise" | tee -a client-smoke-status.txt
    exit 1
  fi
  echo "WISP_ZAP_BASELINE_NOISE=$baseline_noise" | tee -a client-smoke-status.txt
  echo "WISP_ZAP_TRANSIENT_PIXEL_DIFF=$transient_diff" | tee -a client-smoke-status.txt

  for log in client-smoke/client.log run/logs/latest.log; do
    [ -f "$log" ] || continue
    for texture in essentia.png auranodes.png particles.png; do
      texture_error="$(grep -Ei '(Missing textures|Unable to load texture|Failed to load texture|missing texture)' "$log" | grep -F "$texture" | head -1 || true)"
      if [ -n "$texture_error" ]; then
        echo "WISP_TEXTURE_LOAD_ERROR=YES texture=$texture log=$log detail=$texture_error" | tee -a client-smoke-status.txt
        exit 1
      fi
    done
  done

  echo 'WISP_ALL_WISP_TEXTURES_NO_LOAD_ERRORS=PASS' | tee -a client-smoke-status.txt
  echo 'WISP_ESSENTIA_TEXTURE_NO_LOAD_ERRORS=PASS' | tee -a client-smoke-status.txt
  echo 'WISP_ZAP_REAL_CLIENT=PASS' | tee -a client-smoke-status.txt
fi
'''
    return head + body


if __name__ == "__main__":
    sys.stdout.write(render())
