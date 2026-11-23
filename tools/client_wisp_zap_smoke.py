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
    local command="$1"
    xdotool key --clearmodifiers t
    # Delay only before sending. Callers wait for authoritative markers after Return.
    sleep 0.5
    xdotool type --clearmodifiers --delay 2 "$command"
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

  # The camera is pre-aimed at the billboard render origin before the Wisp exists.
  # Only accept a renderer callback appended after the summon command.
  wisp_wait_for_fresh_render() {
    local after_lines="$1" capture_name="$2" render_line render_id render_tick frame_marker
    for _ in $(seq 1 200); do
      render_line="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F 'TC_SMOKE_WISP_RENDERED=YES' | head -1 || true)"
      if [ -n "$render_line" ]; then
        render_id="$(sed -n 's/.*TC_SMOKE_WISP_RENDERED=YES id=\([0-9][0-9]*\) aspect=.*/\1/p' <<<"$render_line")"
        render_tick="$(sed -n 's/.* tick=\([0-9][0-9]*\).*/\1/p' <<<"$render_line")"
        if ! [[ "$render_id" =~ ^[0-9]+$ && "$render_tick" =~ ^[0-9]+$ ]]; then
          echo "WISP_RENDER_CALLBACK_INVALID id=$render_id tick=$render_tick" | tee -a client-smoke-status.txt
          exit 1
        fi
        # The renderer callback is inside LevelRenderer. Wait for AFTER_LEVEL,
        # when the entity buffers have been submitted and the world frame is complete.
        frame_marker="TC_SMOKE_WISP_FRAME_COMPLETE=YES id=$render_id tick=$render_tick"
        for _ in $(seq 1 200); do
          if tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -Fq -- "$frame_marker"; then
            # Allow the completed level pass to reach the display; one render
            # interval is shorter than the Wisp's 50 ms server tick.
            sleep 0.01
            wisp_capture "$capture_name"
            echo "WISP_RENDER_FRAME_COMPLETE=YES id=$render_id tick=$render_tick" | tee -a client-smoke-status.txt
            echo "WISP_RENDER_CAPTURE_TICK=$render_tick" | tee -a client-smoke-status.txt
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
    local after_lines="$1"
    local telemetry player_x player_y player_z wisp_x wisp_y wisp_z distance aim_dot wisp_id camera_tick capture_tick
    telemetry="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F 'TC_SMOKE_WISP_CAMERA=YES' | head -1 || true)"
    if [ -z "$telemetry" ]; then
      echo 'WISP_CAMERA_TELEMETRY_MISSING=YES' | tee -a client-smoke-status.txt
      tail -120 client-smoke/client.log >&2
      exit 1
    fi
    wisp_id="$(sed -n 's/.*TC_SMOKE_WISP_CAMERA=YES id=\([0-9][0-9]*\) aimTarget=.*/\1/p' <<<"$telemetry")"
    camera_tick="$(sed -n 's/.* tick=\([0-9][0-9]*\).*/\1/p' <<<"$telemetry")"
    capture_tick="$(sed -n 's/^WISP_RENDER_CAPTURE_TICK=//p' client-smoke-status.txt | tail -1)"
    player_x="$(sed -n 's/.* playerX=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    player_y="$(sed -n 's/.* playerY=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    player_z="$(sed -n 's/.* playerZ=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    wisp_x="$(sed -n 's/.* wispX=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    wisp_y="$(sed -n 's/.* wispY=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    wisp_z="$(sed -n 's/.* wispZ=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    aim_dot="$(sed -n 's/.* aimDot=\([^ ]*\).*/\1/p' <<<"$telemetry")"
    distance="$(awk -v px="$player_x" -v py="$player_y" -v pz="$player_z" -v wx="$wisp_x" -v wy="$wisp_y" -v wz="$wisp_z" 'BEGIN { dx=px-wx; dy=py-wy; dz=pz-wz; printf "%.4f", sqrt(dx*dx+dy*dy+dz*dz) }')"
    if ! [[ "$wisp_id" =~ ^[0-9]+$ && "$camera_tick" =~ ^[0-9]+$ && "$capture_tick" =~ ^[0-9]+$ ]] \
      || [ "$camera_tick" != "$capture_tick" ]; then
      echo "WISP_CAMERA_RENDER_TICK_MISMATCH id=$wisp_id camera_tick=$camera_tick capture_tick=$capture_tick" | tee -a client-smoke-status.txt
      echo "$telemetry" >&2
      exit 1
    fi
    if ! awk -v x="$player_x" -v y="$player_y" -v z="$player_z" -v d="$distance" -v dot="$aim_dot" \
      'BEGIN { exit !(x >= 12.0 && x <= 13.0 && y >= 148.5 && y <= 149.5 && z >= 10.0 && z <= 11.0 && d <= 3.0 && dot >= 0.99) }'; then
      echo "WISP_CAMERA_TELEMETRY_FAILED player=($player_x,$player_y,$player_z) wisp=($wisp_x,$wisp_y,$wisp_z) distance=$distance aimDot=$aim_dot" | tee -a client-smoke-status.txt
      echo "$telemetry" >&2
      exit 1
    fi
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

  # Aim at the fixed spawn point before the Wisp exists. Only a fresh callback
  # followed by AFTER_LEVEL may capture the first completed billboard frame.
  aim_log_lines="$(wc -l < client-smoke/client.log)"
  wisp_fast_command '/summon thaumcraft:wisp 12.5 149.3 11.5 {Type:"aer"}'
  echo 'WISP_ZAP_AIM=PREAIMED_SPAWN' | tee -a client-smoke-status.txt
  wisp_wait_for_fresh_render "$aim_log_lines" 78-wisp-aim-check

  # Attack immediately while the first rendered tick is still within melee
  # reach. Do not spend more ticks parsing screenshots or camera telemetry first.
  xdotool click 1
  echo 'WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK' | tee -a client-smoke-status.txt
  wisp_wait_for_marker 'TC_SMOKE_WISP_TARGET_SET=YES'
  wisp_wait_for_marker 'TC_SMOKE_WISP_ZAP_SENT=YES'
  wisp_wait_for_marker 'TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES'
  wisp_wait_for_marker 'TC_SMOKE_WISP_ZAP_RENDERED=YES'

  # Validate the already-captured first frame after the attack has been accepted.
  wisp_verify_aim_telemetry "$aim_log_lines"
  python tools/verify_wisp_zap_client.py --aim-only client-smoke/screenshots/78-wisp-aim-check.png | tee -a client-smoke-status.txt

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
