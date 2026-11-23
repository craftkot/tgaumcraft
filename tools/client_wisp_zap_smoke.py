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
    if source.count(anchor) != 1 or source.count("  send_chat() {") != 1:
        raise SystemExit("Client smoke world/command hook changed; refusing unsafe injection")

    head = source.split(anchor, 1)[0]
    body = r'''
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

  wisp_send_chat() {
    local command="$1" marker="$2"
    wisp_command "$command"
    wisp_wait_for_marker "$marker"
  }

  # Pre-aim at the Wisp AABB centre before it exists. Do not capture a desktop
  # screenshot between the first completed frame and the real click: capture is
  # deliberately deferred until the server accepts the hit and stops autonomous flight.
  wisp_wait_for_fresh_render() {
    local after_lines="$1" render_line render_id render_tick frame_marker camera_line
    for _ in $(seq 1 200); do
      render_line="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F 'TC_SMOKE_WISP_RENDERED=YES' | head -1 || true)"
      if [ -n "$render_line" ]; then
        render_id="$(sed -n 's/.*TC_SMOKE_WISP_RENDERED=YES id=\([0-9][0-9]*\) aspect=.*/\1/p' <<<"$render_line")"
        render_tick="$(sed -n 's/.* tick=\([0-9][0-9]*\).*/\1/p' <<<"$render_line")"
        if ! [[ "$render_id" =~ ^[0-9]+$ && "$render_tick" =~ ^[0-9]+$ ]]; then
          echo "WISP_RENDER_CALLBACK_INVALID id=$render_id tick=$render_tick" | tee -a client-smoke-status.txt
          exit 1
        fi
        frame_marker="TC_SMOKE_WISP_FRAME_COMPLETE=YES id=$render_id tick=$render_tick"
        camera_line="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F "TC_SMOKE_WISP_CAMERA=YES id=$render_id aimTarget=hitbox_center tick=$render_tick " | head -1 || true)"
        for _ in $(seq 1 200); do
          if tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -Fq -- "$frame_marker"; then
            if [ -z "$camera_line" ]; then
              echo "WISP_FIRST_FRAME_CAMERA_SAMPLE_MISSING=id=$render_id tick=$render_tick" | tee -a client-smoke-status.txt
              tail -120 client-smoke/client.log >&2
              exit 1
            fi
            echo "WISP_RENDER_FRAME_COMPLETE=YES id=$render_id tick=$render_tick" | tee -a client-smoke-status.txt
            echo "WISP_RENDER_FIRST_FRAME_ID=$render_id" | tee -a client-smoke-status.txt
            echo "WISP_RENDER_FIRST_FRAME_TICK=$render_tick" | tee -a client-smoke-status.txt
            echo "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=$camera_line" | tee -a client-smoke-status.txt
            echo "WISP_RENDER_FIRST_FRAME=YES id=$render_id tick=$render_tick" | tee -a client-smoke-status.txt
            echo 'WISP_RENDER_AFTER_PREAIMED_SPAWN=YES' | tee -a client-smoke-status.txt
            return 0
          fi
          sleep 0.005
        done
        echo "WISP_RENDER_FRAME_COMPLETE_TIMEOUT=$frame_marker" | tee -a client-smoke-status.txt
        tail -120 client-smoke/client.log >&2
        exit 1
      fi
      sleep 0.005
    done
    echo 'WISP_RENDER_AFTER_PREAIMED_SPAWN_TIMEOUT=YES' | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    exit 1
  }

  wisp_reaim_camera_at_live_wisp() {
    local after_lines="$1" teleport_match relative_line
    # A real Wisp can move or knock the Survival player away while the smoke
    # waits. Re-anchor only the test player on the verified platform, then
    # face the live entity. Never move/freeze the Wisp or alter its AI/target.
    # Return the server log line number so callers include the first fresh
    # camera sample instead of skipping it with a later wc -l snapshot.
    wisp_fast_command '/tp @p 12.5 149 10.5 facing entity @e[type=thaumcraft:wisp,limit=1,sort=nearest] eyes' 0.1 1
    for _ in $(seq 1 200); do
      teleport_match="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -n -F 'Teleported Dev to 12.500000, 149.000000, 10.500000' | head -1 || true)"
      if [ -n "$teleport_match" ]; then
        relative_line="$(cut -d: -f1 <<<"$teleport_match")"
        WISP_CAMERA_REAIM_LINE="$(cut -d: -f2- <<<"$teleport_match")"
        WISP_CAMERA_REAIM_LINE_NUMBER="$((after_lines + relative_line))"
        echo "WISP_CAMERA_REAIM=LIVE_ENTITY_FACING $WISP_CAMERA_REAIM_LINE" | tee -a client-smoke-status.txt
        echo 'WISP_CAMERA_REANCHORED=FIXTURE_PLATFORM' | tee -a client-smoke-status.txt
        return 0
      fi
      sleep 0.025
    done
    echo 'WISP_CAMERA_REAIM_TIMEOUT=YES' | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    exit 1
  }

  wisp_wait_for_attack_frame() {
    local after_lines="$1" wisp_id="$2" camera_line camera_tick frame_marker
    local player_x player_y player_z wisp_x wisp_y wisp_z aim_dot distance
    for _ in $(seq 1 200); do
      camera_line="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F "TC_SMOKE_WISP_CAMERA=YES id=$wisp_id aimTarget=hitbox_center " | tail -1 || true)"
      if [ -n "$camera_line" ]; then
        camera_tick="$(sed -n 's/.* aimTarget=hitbox_center tick=\([0-9][0-9]*\) playerX=.*/\1/p' <<<"$camera_line")"
        player_x="$(sed -n 's/.* playerX=\([^ ]*\).*/\1/p' <<<"$camera_line")"
        player_y="$(sed -n 's/.* playerY=\([^ ]*\).*/\1/p' <<<"$camera_line")"
        player_z="$(sed -n 's/.* playerZ=\([^ ]*\).*/\1/p' <<<"$camera_line")"
        wisp_x="$(sed -n 's/.* wispX=\([^ ]*\).*/\1/p' <<<"$camera_line")"
        wisp_y="$(sed -n 's/.* wispY=\([^ ]*\).*/\1/p' <<<"$camera_line")"
        wisp_z="$(sed -n 's/.* wispZ=\([^ ]*\).*/\1/p' <<<"$camera_line")"
        aim_dot="$(sed -n 's/.* aimDot=\([0-9.][0-9.]*\).*/\1/p' <<<"$camera_line")"
        distance="$(awk -v px="$player_x" -v py="$player_y" -v pz="$player_z" -v wx="$wisp_x" -v wy="$wisp_y" -v wz="$wisp_z" 'BEGIN { dx=px-wx; dy=py-wy; dz=pz-wz; printf "%.4f", sqrt(dx*dx+dy*dy+dz*dz) }')"
        frame_marker="TC_SMOKE_WISP_FRAME_COMPLETE=YES id=$wisp_id tick=$camera_tick"
        if [[ "$camera_tick" =~ ^[0-9]+$ && "$player_x" =~ ^[-0-9.]+$ && "$player_y" =~ ^[-0-9.]+$ && "$player_z" =~ ^[-0-9.]+$ && "$wisp_x" =~ ^[-0-9.]+$ && "$wisp_y" =~ ^[-0-9.]+$ && "$wisp_z" =~ ^[-0-9.]+$ && "$aim_dot" =~ ^[0-9.]+$ ]] \
          && tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -Fq -- "$frame_marker" \
          && awk -v x="$player_x" -v y="$player_y" -v z="$player_z" -v d="$distance" -v dot="$aim_dot" \
            'BEGIN { exit !(x >= 12.0 && x <= 13.0 && y >= 148.5 && y <= 149.5 && z >= 10.0 && z <= 11.0 && d <= 3.0 && dot >= 0.99) }'; then
          WISP_ATTACK_CAMERA_ID="$wisp_id"
          WISP_ATTACK_CAMERA_TICK="$camera_tick"
          WISP_ATTACK_CAMERA_DISTANCE="$distance"
          WISP_ATTACK_CAMERA_AIM_DOT="$aim_dot"
          WISP_ATTACK_CAMERA_LINE="$camera_line"
          return 0
        fi
      fi
      sleep 0.005
    done
    echo "WISP_ATTACK_AIM_FRAME_TIMEOUT=id=$wisp_id" | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    return 1
  }

  wisp_attack_live_wisp() {
    local wisp_id attempt reaim_log_lines frame_log_lines attack_log_lines attack_input_line
    local camera_line camera_id camera_tick player_x player_y player_z wisp_x wisp_y wisp_z aim_dot distance frame_marker
    local attack_camera_source attack_camera_detail
    wisp_id="$(sed -n 's/^WISP_RENDER_FIRST_FRAME_ID=//p' client-smoke-status.txt | tail -1)"
    if ! [[ "$wisp_id" =~ ^[0-9]+$ ]]; then
      echo "WISP_ATTACK_WISP_ID_INVALID=$wisp_id" | tee -a client-smoke-status.txt
      exit 1
    fi
    # The first completed frame is already pre-aimed at the exact spawn AABB.
    # Spend that short-lived opportunity immediately; only use chat re-aim on
    # bounded retries after a real BLOCK/missed hit. Never relax ENTITY/distance.
    for attempt in 1 2 3; do
      if [ "$attempt" -eq 1 ]; then
        camera_line="$(sed -n 's/^WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=//p' client-smoke-status.txt | tail -1)"
        camera_id="$(sed -n 's/.*TC_SMOKE_WISP_CAMERA=YES id=\([0-9][0-9]*\) aimTarget=.*/\1/p' <<<"$camera_line")"
        camera_tick="$(sed -n 's/.* aimTarget=hitbox_center tick=\([0-9][0-9]*\) playerX=.*/\1/p' <<<"$camera_line")"
        player_x="$(sed -n 's/.* playerX=\([^ ]*\).*/\1/p' <<<"$camera_line")"
        player_y="$(sed -n 's/.* playerY=\([^ ]*\).*/\1/p' <<<"$camera_line")"
        player_z="$(sed -n 's/.* playerZ=\([^ ]*\).*/\1/p' <<<"$camera_line")"
        wisp_x="$(sed -n 's/.* wispX=\([^ ]*\).*/\1/p' <<<"$camera_line")"
        wisp_y="$(sed -n 's/.* wispY=\([^ ]*\).*/\1/p' <<<"$camera_line")"
        wisp_z="$(sed -n 's/.* wispZ=\([^ ]*\).*/\1/p' <<<"$camera_line")"
        aim_dot="$(sed -n 's/.* aimDot=\([0-9.][0-9.]*\).*/\1/p' <<<"$camera_line")"
        distance="$(awk -v px="$player_x" -v py="$player_y" -v pz="$player_z" -v wx="$wisp_x" -v wy="$wisp_y" -v wz="$wisp_z" 'BEGIN { dx=px-wx; dy=py-wy; dz=pz-wz; printf "%.4f", sqrt(dx*dx+dy*dy+dz*dz) }')"
        frame_marker="TC_SMOKE_WISP_FRAME_COMPLETE=YES id=$camera_id tick=$camera_tick"
        if [[ "$camera_id" =~ ^[0-9]+$ && "$camera_tick" =~ ^[0-9]+$ && "$player_x" =~ ^[-0-9.]+$ && "$player_y" =~ ^[-0-9.]+$ && "$player_z" =~ ^[-0-9.]+$ && "$wisp_x" =~ ^[-0-9.]+$ && "$wisp_y" =~ ^[-0-9.]+$ && "$wisp_z" =~ ^[-0-9.]+$ && "$aim_dot" =~ ^[0-9.]+$ ]] \
          && [ "$camera_id" = "$wisp_id" ] \
          && grep -Fq -- "$frame_marker" client-smoke/client.log \
          && awk -v x="$player_x" -v y="$player_y" -v z="$player_z" -v d="$distance" -v dot="$aim_dot" \
            'BEGIN { exit !(x >= 12.0 && x <= 13.0 && y >= 148.5 && y <= 149.5 && z >= 10.0 && z <= 11.0 && d <= 3.0 && dot >= 0.99) }'; then
          WISP_ATTACK_CAMERA_ID="$camera_id"
          WISP_ATTACK_CAMERA_TICK="$camera_tick"
          WISP_ATTACK_CAMERA_DISTANCE="$distance"
          WISP_ATTACK_CAMERA_AIM_DOT="$aim_dot"
          WISP_ATTACK_CAMERA_LINE="$camera_line"
          attack_camera_source="PREAIMED_FIRST_FRAME"
          attack_camera_detail="id=$camera_id tick=$camera_tick"
        else
          echo "WISP_ATTACK_PREAIM_FRAME_REJECTED=id=$wisp_id tick=$camera_tick distance=$distance aimDot=$aim_dot" | tee -a client-smoke-status.txt
          continue
        fi
      else
        reaim_log_lines="$(wc -l < client-smoke/client.log)"
        wisp_reaim_camera_at_live_wisp "$reaim_log_lines"
        attack_camera_source="LIVE_ENTITY_FACING"
        attack_camera_detail="$WISP_CAMERA_REAIM_LINE"
        frame_log_lines="$WISP_CAMERA_REAIM_LINE_NUMBER"
        if ! wisp_wait_for_attack_frame "$frame_log_lines" "$wisp_id"; then
          echo "WISP_ATTACK_FRAME_RETRY=$attempt" | tee -a client-smoke-status.txt
          continue
        fi
      fi
      attack_log_lines="$(wc -l < client-smoke/client.log)"
      xdotool click 1
      echo "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK attempt=$attempt" | tee -a client-smoke-status.txt
      attack_input_line=""
      for _ in $(seq 1 200); do
        attack_input_line="$(tail -n +"$((attack_log_lines + 1))" client-smoke/client.log | grep -F 'TC_SMOKE_WISP_ATTACK_INPUT=YES' | head -1 || true)"
        if [ -n "$attack_input_line" ]; then
          break
        fi
        sleep 0.005
      done
      if [ -z "$attack_input_line" ]; then
        echo "WISP_ATTACK_INPUT_TIMEOUT=$attempt" | tee -a client-smoke-status.txt
        continue
      fi
      if grep -Fq "targetWisp=true targetId=$wisp_id targetType=thaumcraft:wisp hitType=ENTITY" <<<"$attack_input_line"; then
        echo "WISP_ATTACK_CAMERA_REAIM=$attack_camera_source $attack_camera_detail" | tee -a client-smoke-status.txt
        echo "WISP_ATTACK_CAMERA_FRAME=YES id=$WISP_ATTACK_CAMERA_ID tick=$WISP_ATTACK_CAMERA_TICK distance=$WISP_ATTACK_CAMERA_DISTANCE aimDot=$WISP_ATTACK_CAMERA_AIM_DOT" | tee -a client-smoke-status.txt
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
    tail -120 client-smoke/client.log >&2
    exit 1
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
    local after_lines="$1" telemetry capture_camera
    local player_x player_y player_z wisp_x wisp_y wisp_z distance aim_dot
    local wisp_id camera_tick first_frame_id first_frame_tick capture_id capture_tick
    local capture_camera_id capture_camera_tick capture_player_x capture_player_y capture_player_z
    local capture_wisp_x capture_wisp_y capture_wisp_z capture_distance capture_aim_dot

    telemetry="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F 'TC_SMOKE_WISP_CAMERA=YES' | head -1 || true)"
    if [ -z "$telemetry" ]; then
      echo 'WISP_CAMERA_TELEMETRY_MISSING=YES' | tee -a client-smoke-status.txt
      tail -120 client-smoke/client.log >&2
      exit 1
    fi
    wisp_id="$(sed -n 's/.*TC_SMOKE_WISP_CAMERA=YES id=\([0-9][0-9]*\) aimTarget=.*/\1/p' <<<"$telemetry")"
    camera_tick="$(sed -n 's/.* tick=\([0-9][0-9]*\).*/\1/p' <<<"$telemetry")"
    first_frame_id="$(sed -n 's/^WISP_RENDER_FIRST_FRAME_ID=//p' client-smoke-status.txt | tail -1)"
    first_frame_tick="$(sed -n 's/^WISP_RENDER_FIRST_FRAME_TICK=//p' client-smoke-status.txt | tail -1)"
    capture_id="$(sed -n 's/^WISP_RENDER_CAPTURE_ID=//p' client-smoke-status.txt | tail -1)"
    capture_tick="$(sed -n 's/^WISP_RENDER_CAPTURE_TICK=//p' client-smoke-status.txt | tail -1)"
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
  wisp_command '/time set noon'
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
  aim_log_lines="$(wc -l < client-smoke/client.log)"
  wisp_fast_command '/summon thaumcraft:wisp 12.5 149.3 11.5 {Type:"aer"}'
  echo 'WISP_ZAP_AIM=PREAIMED_SPAWN' | tee -a client-smoke-status.txt
  wisp_wait_for_fresh_render "$aim_log_lines"

  wisp_attack_live_wisp
  wisp_wait_for_marker 'TC_SMOKE_WISP_CLIENT_ATTACK=YES'
  wisp_wait_for_marker 'TC_SMOKE_WISP_HURT=YES accepted=true'
  echo 'WISP_ATTACK_HURT_ACCEPTED=YES' | tee -a client-smoke-status.txt
  wisp_wait_for_marker 'TC_SMOKE_WISP_TARGET_SET=YES'

  reaim_log_lines="$(wc -l < client-smoke/client.log)"
  wisp_reaim_camera_at_live_wisp "$reaim_log_lines"
  after_hit_log_lines="$WISP_CAMERA_REAIM_LINE_NUMBER"
  wisp_capture_aim_after_hit "$after_hit_log_lines"
  wisp_verify_aim_telemetry "$aim_log_lines"
  python tools/verify_wisp_zap_client.py --aim-only client-smoke/screenshots/78-wisp-aim-check.png | tee -a client-smoke-status.txt

  wisp_wait_for_marker 'TC_SMOKE_WISP_ZAP_SENT=YES'
  wisp_wait_for_marker 'TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES'
  wisp_wait_for_marker 'TC_SMOKE_WISP_ZAP_RENDERED=YES'

  # The effect lasts three game ticks. Capture immediately after the renderer
  # reports its first completed additive batch, then capture after expiry.
  wisp_capture 80-wisp-zap-frame-a
  sleep 0.025
  wisp_capture 81-wisp-zap-frame-b
  sleep 0.025
  wisp_capture 82-wisp-zap-frame-c
  sleep 0.35
  wisp_capture 83-wisp-zap-after-expiry

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
    if [ -f "$log" ] && grep -Eiq \
      '(Missing textures|Unable to load texture|Failed to load texture).*essentia\.png|essentia\.png.*(missing texture|Unable to load)' \
      "$log"; then
      echo "WISP_ESSENTIA_TEXTURE_LOAD_ERROR=YES log=$log" | tee -a client-smoke-status.txt
      exit 1
    fi
  done

  echo 'WISP_ESSENTIA_TEXTURE_NO_LOAD_ERRORS=PASS' | tee -a client-smoke-status.txt
  echo 'WISP_ZAP_REAL_CLIENT=PASS' | tee -a client-smoke-status.txt
fi
'''
    return head + body


if __name__ == "__main__":
    sys.stdout.write(render())
