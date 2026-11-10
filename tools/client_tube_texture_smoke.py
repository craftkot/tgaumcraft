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

  # Freeze the scene and suppress item drops so early-vs-late pixel differences
  # isolate the transient block-destruction particles rather than weather,
  # moving drops, chat fade, or the player's hand.
  send_chat '/gamerule doDaylightCycle false'
  send_chat '/gamerule doWeatherCycle false'
  send_chat '/gamerule doTileDrops false'
  send_chat '/time set noon'
  send_chat '/fill 3 149 7 7 149 13 minecraft:stone'
  send_chat '/setblock 4 150 12 minecraft:air'
  send_chat '/setblock 5 150 12 minecraft:air'
  send_chat '/setblock 6 150 12 minecraft:air'
  send_chat '/tp @p 5.5 150 8.5 0 16'
  xdotool key --clearmodifiers F3+d
  sleep 1

  tube_capture() {
    local name="$1"
    import -display :99 -window root -silent "client-smoke/screenshots/$name.png"
    test -s "client-smoke/screenshots/$name.png"
    echo "TUBE_REAL_CLIENT_SCREEN=$name" | tee -a client-smoke-status.txt
  }

  tube_send_chat_quick() {
    xdotool key --clearmodifiers t
    sleep 0.15
    xdotool type --clearmodifiers --delay 2 "$1"
    xdotool key --clearmodifiers Return
    sleep 0.12
    xdotool key --clearmodifiers F3+d
    sleep 0.12
  }

  tube_pixel_diff() {
    local label="$1" left="$2" right="$3" metric
    metric="$(compare -metric AE "$left" "$right" null: 2>&1 || true)"
    metric="\${metric%% *}"
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
      metric="$(compare -metric AE "$image" "$late" null: 2>&1 || true)"
      metric="\${metric%% *}"
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
  send_chat '/setblock 4 150 12 thaumcraft:tube_valve'
  send_chat '/setblock 5 150 12 thaumcraft:tube'
  send_chat '/setblock 6 150 12 thaumcraft:tube_restrict'

  # These are authoritative server blockstate checks. They prove that the
  # real Tile entities connected and selected the valve/restrict multipart
  # branches, not merely that /setblock text was typed.
  tube_send_chat_quick '/execute if block 4 150 12 thaumcraft:tube_valve[east=true] run say TC_SMOKE_TUBE_VALVE_EAST_CONNECTED=YES'
  tube_send_chat_quick '/execute if block 5 150 12 thaumcraft:tube[west=true,east=true] run say TC_SMOKE_TUBE_MIDDLE_BOTH_SIDES_CONNECTED=YES'
  tube_send_chat_quick '/execute if block 6 150 12 thaumcraft:tube_restrict[west=true] run say TC_SMOKE_TUBE_RESTRICT_WEST_CONNECTED=YES'
  sleep 0.5
  xdotool key --clearmodifiers F3+d
  sleep 0.5
  tube_capture 71-tubes-connected
  tube_pixel_diff connected \
    client-smoke/screenshots/70-tubes-before.png \
    client-smoke/screenshots/71-tubes-connected.png

  # /setblock ... destroy invokes the real level block-destruction event.
  # Item drops are disabled above; the early-vs-late comparison therefore
  # measures the transient particle sprite after the block itself is gone.
  tube_send_chat_quick '/setblock 4 150 12 minecraft:air destroy'
  sleep 0.12
  tube_capture 72-valve-break-early-a
  sleep 0.16
  tube_capture 73-valve-break-early-b
  sleep 0.16
  tube_capture 74-valve-break-early-c
  sleep 2.2
  tube_capture 75-valve-break-late
  tube_particle_diff VALVE \
    client-smoke/screenshots/75-valve-break-late.png \
    client-smoke/screenshots/72-valve-break-early-a.png \
    client-smoke/screenshots/73-valve-break-early-b.png \
    client-smoke/screenshots/74-valve-break-early-c.png

  tube_send_chat_quick '/setblock 6 150 12 minecraft:air destroy'
  sleep 0.12
  tube_capture 76-restrict-break-early-a
  sleep 0.16
  tube_capture 77-restrict-break-early-b
  sleep 0.16
  tube_capture 78-restrict-break-early-c
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
