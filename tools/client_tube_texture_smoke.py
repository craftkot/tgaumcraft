#!/usr/bin/env python3
"""Generate an isolated real-client smoke for the original BETA26 tube particle sprite.

The shared tools/client_smoke.sh is read-only. This script cuts it at the
verified integrated-world hook and appends one disposable tube rendering test.
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
  echo 'TUBE_TEXTURE_REAL_CLIENT_WORLD=YES' | tee -a client-smoke-status.txt
  echo 'TUBE_EVIDENCE_ROI=420x150+300+290' | tee -a client-smoke-status.txt

  tube_send_chat() {
    local command="$1" expected="$2"
    # Match the stable chat-open/type timings used by client_smoke.sh, but
    # return on the actual server acknowledgement so particle frames remain
    # inside the transient effect window.
    xdotool key --clearmodifiers t
    sleep 1
    xdotool type --clearmodifiers --delay 24 "$command"
    xdotool key --clearmodifiers Return
    for _ in $(seq 1 80); do
      if grep -Fq -- "$expected" client-smoke/client.log; then
        echo "TUBE_SERVER_COMMAND_CONFIRMED=$expected" | tee -a client-smoke-status.txt
        xdotool key --clearmodifiers F3+d
        sleep 0.1
        return 0
      fi
      sleep 0.05
    done
    echo "TUBE_SERVER_COMMAND_NOT_CONFIRMED=$expected" | tee -a client-smoke-status.txt
    tail -80 client-smoke/client.log >&2
    exit 1
  }



  # Freeze the scene and suppress item drops so early-vs-late pixel differences
  # isolate the transient block-destruction particles rather than weather,
  # moving drops, chat fade, or the player's hand.
  tube_send_chat '/gamerule doDaylightCycle false' 'Gamerule doDaylightCycle is now set to: false'
  tube_send_chat '/gamerule doWeatherCycle false' 'Gamerule doWeatherCycle is now set to: false'
  tube_send_chat '/gamerule doTileDrops false' 'Gamerule doTileDrops is now set to: false'
  tube_send_chat '/time set noon' 'Set the time to 6000'
  # Force-load the shared test chunk before building. The player must stand
  # on a real platform; a creative player still falls when not flying.
  tube_send_chat '/forceload add 0 0' 'Marked chunk [0, 0] in Overworld to be force loaded'
  tube_send_chat '/fill 8 148 8 12 148 14 minecraft:stone' 'Successfully filled 35 block(s)'
  tube_send_chat '/execute if block 10 148 10 minecraft:stone run say TC_SMOKE_TUBE_PLATFORM_READY=YES' 'TC_SMOKE_TUBE_PLATFORM_READY=YES'
  tube_send_chat '/tp @p 10.5 149 8.5 0 18' 'Teleported Dev to 10.500000, 149.000000, 8.500000'
  tube_send_chat '/execute as @p at @s if block ~ ~-1 ~ minecraft:stone run say TC_SMOKE_TUBE_PLAYER_ON_PLATFORM=YES' 'TC_SMOKE_TUBE_PLAYER_ON_PLATFORM=YES'
  xdotool key --clearmodifiers F3+d
  sleep 1

  tube_capture() {
    local name="$1"
    import -display :99 -window root -silent "client-smoke/screenshots/$name.png"
    test -s "client-smoke/screenshots/$name.png"
    echo "TUBE_REAL_CLIENT_SCREEN=$name" | tee -a client-smoke-status.txt
  }


  # Pixel comparisons are restricted to the fixed tube work area. The full
  # framebuffer contains moving clouds even when weather/daylight gamerules
  # are frozen, so full-screen deltas are not particle evidence.
  tube_crop_metric() {
    local left="$1" right="$2" temporary metric
    temporary="$(mktemp -d)"
    convert "$left" -crop 420x150+300+290 +repage "$temporary/left.png"
    convert "$right" -crop 420x150+300+290 +repage "$temporary/right.png"
    metric="$(compare -metric AE "$temporary/left.png" "$temporary/right.png" null: 2>&1 || true)"
    rm -rf "$temporary"
    printf '%s\n' "$metric" | awk 'NR == 1 {print $1}'
  }

  tube_pixel_diff() {
    local label="$1" left="$2" right="$3" metric
    metric="$(tube_crop_metric "$left" "$right")"
    if ! [[ "$metric" =~ ^[0-9]+$ ]] || [ "$metric" -lt 300 ]; then
      echo "TUBE_BLOCK_RENDER_DIFF_FAILED=$label metric=$metric" | tee -a client-smoke-status.txt
      exit 1
    fi
    echo "TUBE_BLOCK_RENDER_DIFF=$metric" | tee -a client-smoke-status.txt
  }

  tube_particle_diff() {
    local label="$1" late="$2" early_a="$3" early_b="$4" early_c="$5"
    local image metric best=0
    for image in "$early_a" "$early_b" "$early_c"; do
      metric="$(tube_crop_metric "$image" "$late")"
      if [[ "$metric" =~ ^[0-9]+$ ]] && [ "$metric" -gt "$best" ]; then
        best="$metric"
      fi
    done
    if [ "$best" -lt 20 ]; then
      echo "TUBE_"$label"_PARTICLE_DIFF_FAILED=$best" | tee -a client-smoke-status.txt
      exit 1
    fi
    echo "TUBE_"$label"_PARTICLE_DIFF=$best" | tee -a client-smoke-status.txt
  }

  tube_capture 70-tubes-before
  tube_send_chat '/setblock 9 149 12 thaumcraft:tube_valve' 'Changed the block at 9, 149, 12'
  tube_send_chat '/setblock 10 149 12 thaumcraft:tube' 'Changed the block at 10, 149, 12'
  tube_send_chat '/setblock 11 149 12 thaumcraft:tube_restrict' 'Changed the block at 11, 149, 12'

  # These are authoritative server blockstate checks. They prove that the
  # real Tile entities connected and selected the valve/restrict multipart
  # branches, not merely that /setblock text was typed.
  tube_send_chat '/execute if block 9 149 12 thaumcraft:tube_valve[east=true] run say TC_SMOKE_TUBE_VALVE_EAST_CONNECTED=YES' 'TC_SMOKE_TUBE_VALVE_EAST_CONNECTED=YES'
  tube_send_chat '/execute if block 10 149 12 thaumcraft:tube[west=true,east=true] run say TC_SMOKE_TUBE_MIDDLE_BOTH_SIDES_CONNECTED=YES' 'TC_SMOKE_TUBE_MIDDLE_BOTH_SIDES_CONNECTED=YES'
  tube_send_chat '/execute if block 11 149 12 thaumcraft:tube_restrict[west=true] run say TC_SMOKE_TUBE_RESTRICT_WEST_CONNECTED=YES' 'TC_SMOKE_TUBE_RESTRICT_WEST_CONNECTED=YES'
  sleep 0.5
  xdotool key --clearmodifiers F3+d
  sleep 0.5
  tube_capture 71-tubes-connected
  tube_pixel_diff connected \
    client-smoke/screenshots/70-tubes-before.png \
    client-smoke/screenshots/71-tubes-connected.png

  # Break each variant through the real client input path. /setblock destroy
  # does not prove that the player's block-destroy event or client particles ran.
  tube_send_chat '/tp @p 9.5 149 8.5 0 16' 'Teleported Dev to 9.500000, 149.000000, 8.500000'
  xdotool mousemove --sync 513 404 click 1
  echo 'TUBE_VALVE_BREAK_INPUT=REAL_CLIENT_LEFT_CLICK' | tee -a client-smoke-status.txt
  sleep 0.18
  tube_capture 72-valve-break-early-a
  sleep 0.12
  tube_capture 73-valve-break-early-b
  sleep 0.12
  tube_capture 74-valve-break-early-c
  tube_send_chat '/execute if block 9 149 12 minecraft:air run say TC_SMOKE_TUBE_VALVE_DESTROYED=YES' 'TC_SMOKE_TUBE_VALVE_DESTROYED=YES'
  tube_send_chat '/execute if block 9 149 12 minecraft:air if block 10 149 12 thaumcraft:tube if block 11 149 12 thaumcraft:tube_restrict run say TC_SMOKE_TUBE_VALVE_ONLY_DESTROYED=YES' 'TC_SMOKE_TUBE_VALVE_ONLY_DESTROYED=YES'
  sleep 2.2
  tube_capture 75-valve-break-late
  tube_particle_diff VALVE \
    client-smoke/screenshots/75-valve-break-late.png \
    client-smoke/screenshots/72-valve-break-early-a.png \
    client-smoke/screenshots/73-valve-break-early-b.png \
    client-smoke/screenshots/74-valve-break-early-c.png

  tube_send_chat '/tp @p 11.5 149 8.5 0 16' 'Teleported Dev to 11.500000, 149.000000, 8.500000'
  xdotool mousemove --sync 513 404 click 1
  echo 'TUBE_RESTRICT_BREAK_INPUT=REAL_CLIENT_LEFT_CLICK' | tee -a client-smoke-status.txt
  sleep 0.18
  tube_capture 76-restrict-break-early-a
  sleep 0.12
  tube_capture 77-restrict-break-early-b
  sleep 0.12
  tube_capture 78-restrict-break-early-c
  tube_send_chat '/execute if block 11 149 12 minecraft:air run say TC_SMOKE_TUBE_RESTRICT_DESTROYED=YES' 'TC_SMOKE_TUBE_RESTRICT_DESTROYED=YES'
  tube_send_chat '/execute if block 9 149 12 minecraft:air if block 10 149 12 thaumcraft:tube if block 11 149 12 minecraft:air run say TC_SMOKE_TUBE_RESTRICT_ONLY_DESTROYED=YES' 'TC_SMOKE_TUBE_RESTRICT_ONLY_DESTROYED=YES'
  sleep 2.2
  tube_capture 79-restrict-break-late
  tube_particle_diff RESTRICT \
    client-smoke/screenshots/79-restrict-break-late.png \
    client-smoke/screenshots/76-restrict-break-early-a.png \
    client-smoke/screenshots/77-restrict-break-early-b.png \
    client-smoke/screenshots/78-restrict-break-early-c.png

  # The atlas/model bake happens during the real client launch. Reject a
  # missing sprite/model warning specifically for the two models whose
  # particle field references thaumcraft:block/base_metal.
  for log in client-smoke/client.log run/logs/latest.log; do
    if [ -f "$log" ] && grep -Eiq \
      '(Missing textures in model.*(tube_core_valve|tube_side_restrict|base_metal)|Unable to load (model|texture).*(tube_core_valve|tube_side_restrict|base_metal)|(tube_core_valve|tube_side_restrict|base_metal).*(missing texture|Unable to load))' \
      "$log"; then
      echo "TUBE_MISSING_MODEL_TEXTURE=YES log=$log" | tee -a client-smoke-status.txt
      exit 1
    fi
  done

  echo 'TUBE_NO_MISSING_MODEL_TEXTURES=PASS' | tee -a client-smoke-status.txt
  echo 'TUBE_REAL_CLIENT_TEXTURE_SMOKE=PASS' | tee -a client-smoke-status.txt
fi
'''
    return head + body


if __name__ == "__main__":
    sys.stdout.write(render())
