#!/usr/bin/env python3
"""Generate a real NeoForge-client smoke for BETA26 pedestal item rotation."""
from pathlib import Path
import sys

ANCHOR = "  # The initial world is already creative + peaceful, as established"


def build_body() -> str:
    return r"""
  echo 'PEDESTAL_ROTATION_REAL_CLIENT=YES' | tee -a client-smoke-status.txt

  require_server_marker() {
    local marker="$1"
    for _ in $(seq 1 40); do
      if grep -Fq "$marker" run/logs/latest.log; then
        echo "PEDESTAL_SERVER_MARKER=PASS:$marker" | tee -a client-smoke-status.txt
        return 0
      fi
      sleep 0.25
    done
    echo "PEDESTAL_SERVER_MARKER=FAIL:$marker" | tee -a client-smoke-status.txt
    tail -100 run/logs/latest.log >&2
    exit 1
  }

  send_chat '/say TC_PEDESTAL_COMMAND_CHANNEL_READY'
  require_server_marker 'TC_PEDESTAL_COMMAND_CHANNEL_READY'
  send_chat '/gamemode creative'
  send_chat '/difficulty peaceful'
  send_chat '/time set noon'
  send_chat '/weather clear'
  send_chat '/forceload add 250 250'
  send_chat '/fill 247 147 246 253 147 254 minecraft:stone'
  send_chat '/fill 247 148 246 253 155 254 minecraft:air'
  send_chat '/setblock 250 148 250 thaumcraft:pedestal_arcane'
  send_chat '/execute if block 250 148 250 thaumcraft:pedestal_arcane run say TC_PEDESTAL_BLOCK_PRESENT'
  require_server_marker 'TC_PEDESTAL_BLOCK_PRESENT'
  send_chat '/tp @p 250.5 148 246.8 0 13.2'
  send_chat '/item replace entity @p weapon.mainhand with minecraft:air'

  capture_pedestal_frame() {
    local name="$1" geometry width height crop_x crop_y
    local path="client-smoke/screenshots/$name.png"
    import -display :99 -window root -silent "$path"
    test -s "$path"
    geometry="$(xdotool getwindowgeometry --shell "$WIN")"
    eval "$geometry"
    read -r width height < <(identify -format '%w %h\n' "$path")
    if [ "$WIDTH" -lt 1024 ] || [ "$HEIGHT" -lt 768 ] || \
       [ "$width" -lt 1024 ] || [ "$height" -lt 768 ]; then
      echo "PEDESTAL_BAD_SCREENSHOT=$name:__DL__{width}x__DL__{height}:window=__DL__{WIDTH}x__DL__{HEIGHT}" \
        | tee -a client-smoke-status.txt
      exit 1
    fi
    crop_x=$((X + WIDTH / 2 - 64))
    crop_y=$((Y + HEIGHT / 2 - 64))
    if (( crop_x < 0 || crop_y < 0 || crop_x + 128 > width || crop_y + 128 > height )); then
      echo "PEDESTAL_CROP_OUTSIDE_SCREEN=$name:$crop_x,$crop_y:__DL__{width}x__DL__{height}" \
        | tee -a client-smoke-status.txt
      exit 1
    fi
    convert "$path" -crop "128x128+__DL__{crop_x}+__DL__{crop_y}" +repage \
      "client-smoke/screenshots/$name-center.png"
    echo "PEDESTAL_SCREENSHOT=$name:__DL__{width}x__DL__{height}:crop=__DL__{crop_x},__DL__{crop_y},128,128" \
      | tee -a client-smoke-status.txt
  }

  # Empty pedestal is the negative visual control for the same camera and crop.
  sleep 1
  capture_pedestal_frame 'pedestal-empty'
  send_chat '/item replace block 250 148 250 container.0 with minecraft:diamond_sword'
  send_chat '/execute if items block 250 148 250 container.0 minecraft:diamond_sword run say TC_PEDESTAL_ITEM_PRESENT'
  require_server_marker 'TC_PEDESTAL_ITEM_PRESENT'

  # Keep the camera fixed. The renderer marker records the actual camera tick
  # and angle after ItemRenderer.renderStatic returns; screenshots independently
  # prove that the rendered item is visible and changes orientation.
  sleep 1
  capture_pedestal_frame 'pedestal-rotation-first'
  sleep 2
  capture_pedestal_frame 'pedestal-rotation-later'

  pixel_difference() {
    local left="$1" right="$2" minimum="$3" metric
    metric="$(compare -metric AE \
      "client-smoke/screenshots/$left-center.png" \
      "client-smoke/screenshots/$right-center.png" null: 2>&1 || true)"
    metric="__DL__{metric%% *}"
    if ! [[ "$metric" =~ ^[0-9]+$ ]] || [ "$metric" -lt "$minimum" ]; then
      echo "PEDESTAL_PIXEL_DIFFERENCE=FAIL:$left:$right:$metric:minimum=$minimum" \
        | tee -a client-smoke-status.txt
      exit 1
    fi
    echo "PEDESTAL_PIXEL_DIFFERENCE=PASS:$left:$right:$metric" \
      | tee -a client-smoke-status.txt
  }

  pixel_difference 'pedestal-empty' 'pedestal-rotation-first' 30
  pixel_difference 'pedestal-rotation-first' 'pedestal-rotation-later' 8

  python3 - <<'PY'
import re
from pathlib import Path

log = Path("client-smoke/client.log").read_text(encoding="utf-8", errors="replace")
pattern = re.compile(
    r"TC_PEDESTAL_RENDER_ROTATION cameraTick=(\d+)"
    r" partialTick=([-+0-9.eE]+) angle=([-+0-9.eE]+)"
    r" pos=250,148,250"
)
samples = [
    (int(tick), float(partial), float(angle))
    for tick, partial, angle in pattern.findall(log)
]
sys.path.insert(0, "tools")
from pedestal_rotation_evidence import validate_samples

try:
    metrics = validate_samples(samples)
except ValueError as exc:
    raise SystemExit(f"PEDESTAL_RENDER_MARKERS=FAIL:{exc}")
print(
    f"PEDESTAL_RENDER_MARKERS=PASS count={len(samples)} "
    f"camera_ticks={metrics['first_tick']}..{metrics['last_tick']} "
    f"tick_span={metrics['tick_span']} "
    f"last_step_degrees={metrics['last_step_degrees']:.3f}"
)
with Path("client-smoke-status.txt").open("a", encoding="utf-8") as status:
    status.write(
        f"PEDESTAL_RENDER_MARKERS=PASS count={len(samples)} "
        f"camera_ticks={metrics['first_tick']}..{metrics['last_tick']} "
        f"tick_span={metrics['tick_span']} "
        f"last_step_degrees={metrics['last_step_degrees']:.3f}\\n"
    )
PY

  if grep -Eiq 'Unable to load model.*pedestal|Unable to load texture.*pedestal|Minecraft has crashed|Mod loading has failed|Exception in thread "Render thread"' \
      client-smoke/client.log run/logs/latest.log 2>/dev/null; then
    echo 'PEDESTAL_CLIENT_OR_RESOURCE_ERROR=YES' | tee -a client-smoke-status.txt
    tail -100 client-smoke/client.log >&2
    exit 1
  fi
  echo 'PEDESTAL_ROTATION_REAL_CLIENT=PASS' | tee -a client-smoke-status.txt
fi
"""


def main() -> None:
    source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
    if source.count(ANCHOR) != 1:
        raise SystemExit("Client smoke world/command hook changed; refusing unsafe injection")
    head = source.split(ANCHOR, 1)[0] + ANCHOR
    sys.stdout.write(head + build_body().replace("__DL__", "$"))


if __name__ == "__main__":
    main()
