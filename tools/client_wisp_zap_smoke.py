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
    # Keep the reliable chat-open delay; only character typing is accelerated.
    sleep 0.5
    xdotool type --clearmodifiers --delay 1 "$command"
    xdotool key --clearmodifiers Return
    sleep 0.2
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

  wisp_wait_for_teleport() {
    local after_lines="$1"
    for _ in $(seq 1 200); do
      local teleport_line
      teleport_line="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F 'Teleported Dev to ' | tail -1 || true)"
      if [ -n "$teleport_line" ]; then
        echo "WISP_ZAP_AIM_TELEPORT=YES $teleport_line" | tee -a client-smoke-status.txt
        return 0
      fi
      sleep 0.025
    done
    echo 'WISP_AIM_TELEPORT_TIMEOUT=YES' | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    exit 1
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

  wisp_wait_for_fill() {
    local after_lines="$1"
    for _ in $(seq 1 200); do
      local fill_line
      fill_line="$(tail -n +"$((after_lines + 1))" client-smoke/client.log | grep -F 'Successfully filled 35 block(s)' | tail -1 || true)"
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
  wisp_fast_command '/execute at @p run fill ~-2 ~-1 ~-1 ~2 ~-1 ~5 minecraft:stone'
  # The vanilla command's own server response is the authoritative success
  # signal; avoid a second coordinate predicate that can obscure the result.
  wisp_wait_for_fill "$fill_log_lines"
  wisp_send_chat '/execute if entity @p[gamemode=survival] run say TC_SMOKE_WISP_PLAYER_SURVIVAL=YES' 'TC_SMOKE_WISP_PLAYER_SURVIVAL=YES'

  # 2.0 blocks keeps the Wisp inside survival melee reach while leaving its
  # complete source-to-player zap path in the camera frustum.
  wisp_command '/summon thaumcraft:wisp 10.5 149 10.5 {Type:"aer",Tags:["tc_wisp_zap_smoke"]}'
  wisp_send_chat '/execute if entity @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] run say TC_SMOKE_WISP_SPAWNED=YES' 'TC_SMOKE_WISP_SPAWNED=YES'
  wisp_send_chat '/execute if entity @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,nbt={Type:"aer"},limit=1] run say TC_SMOKE_WISP_ASPECT_AER=YES' 'TC_SMOKE_WISP_ASPECT_AER=YES'
  # Require the actual entity renderer before attempting a melee hit.
  wisp_wait_for_marker 'TC_SMOKE_WISP_RENDERED=YES'

  # Confirm the live entity, then move the player relative to its current
  # server position. The previous same-position tp left the Wisp off-screen.
  wisp_fast_command '/execute if entity @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] run say TC_SMOKE_WISP_PRESENT_FOR_AIM=YES'
  wisp_wait_for_marker 'TC_SMOKE_WISP_PRESENT_FOR_AIM=YES'
  aim_log_lines="$(wc -l < client-smoke/client.log)"
  wisp_fast_command '/execute at @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] run tp @p ~-1.5 ~ ~ facing entity @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] eyes'
  wisp_wait_for_teleport "$aim_log_lines"
  echo 'WISP_ZAP_AIM=LIVE_ENTITY_RELATIVE' | tee -a client-smoke-status.txt
  wisp_capture 78-wisp-aim-check
  python tools/verify_wisp_zap_client.py --aim-only client-smoke/screenshots/78-wisp-aim-check.png | tee -a client-smoke-status.txt

  # Two clean pre-attack frames now include the centered Wisp, so their
  # animation delta is a real baseline rather than an empty platform.
  xdotool key --clearmodifiers F1
  sleep 0.2
  wisp_capture 79-wisp-before-a
  python tools/verify_wisp_zap_client.py --aim-only client-smoke/screenshots/79-wisp-before-a.png | tee -a client-smoke-status.txt
  sleep 0.05
  wisp_capture 79-wisp-before-b
  python tools/verify_wisp_zap_client.py --aim-only client-smoke/screenshots/79-wisp-before-b.png | tee -a client-smoke-status.txt
  xdotool key --clearmodifiers F1
  sleep 0.05

  # A genuine survival left-click invokes WispEntity.hurt(Player), which sets
  # the normal target. No synthetic packet, direct target setter or command
  # invokes the zap itself.
  xdotool click 1
  echo 'WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK' | tee -a client-smoke-status.txt
  wisp_wait_for_marker 'TC_SMOKE_WISP_TARGET_SET=YES'
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
