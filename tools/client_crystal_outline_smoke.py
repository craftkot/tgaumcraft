#!/usr/bin/env python3
"""Generate a real NeoForge-client ray-picking regression for BETA26 crystal outlines."""
from pathlib import Path
import sys

ANCHOR = "  # The initial world is already creative + peaceful, as established"

# (face, support xyz, miss player feet xyz/yaw/pitch, hit player feet xyz/yaw/pitch)
# Player eye height is 1.62. Horizontal rays test the UP/DOWN halves; vertical
# rays test EAST/WEST/SOUTH/NORTH halves without crossing the selected half.
FACE_CASES = (
    ("up",    (250, 152, 251), (250.5, 149.63, 249.5, 0, 0), (250.5, 150.13, 249.5, 0, 0)),
    ("down",  (250, 150, 251), (250.5, 150.13, 249.5, 0, 0), (250.5, 149.63, 249.5, 0, 0)),
    ("east",  (251, 151, 251), (250.25, 154, 251.5, 0, 90), (250.75, 154, 251.5, 0, 90)),
    ("west",  (249, 151, 251), (250.75, 154, 251.5, 0, 90), (250.25, 154, 251.5, 0, 90)),
    ("south", (250, 151, 252), (250.5, 154, 251.25, 0, 90), (250.5, 154, 251.75, 0, 90)),
    ("north", (250, 151, 250), (250.5, 154, 251.75, 0, 90), (250.5, 154, 251.25, 0, 90)),
)


def shell_number(value: int | float) -> str:
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


def build_body() -> str:
    body = r"""
  echo 'CRYSTAL_OUTLINE_REAL_CLIENT=YES' | tee -a client-smoke-status.txt
  send_chat '/gamemode creative'
  send_chat '/difficulty peaceful'
  send_chat '/time set noon'
  send_chat '/weather clear'
  send_chat '/forceload add 250 250'
  send_chat '/fill 247 149 248 253 160 254 minecraft:air'
  send_chat '/fill 247 148 248 253 148 254 minecraft:stone'

  # Double-tap space to enable creative flight before top-down rays.
  xdotool key --clearmodifiers space
  sleep 0.15
  xdotool key --clearmodifiers space
  sleep 1

  capture_outline() {
    local name="$1" width height path
    path="client-smoke/screenshots/$name.png"
    import -display :99 -window root -silent "$path"
    test -s "$path"
    read -r width height < <(identify -format '%w %h\n' "$path")
    if [ "$width" -lt 1024 ] || [ "$height" -lt 768 ]; then
      echo "CRYSTAL_OUTLINE_BAD_SCREENSHOT=$name:$width x $height" | tee -a client-smoke-status.txt
      exit 1
    fi
    echo "CRYSTAL_OUTLINE_SCREENSHOT=$name:$width x $height" | tee -a client-smoke-status.txt
  }

  assert_outline_marker() {
    local marker="$1"
    if ! grep -Fq "$marker" client-smoke/client.log; then
      echo "CRYSTAL_OUTLINE_MARKER_MISSING=$marker" | tee -a client-smoke-status.txt
      tail -100 client-smoke/client.log >&2
      exit 1
    fi
    echo "CRYSTAL_OUTLINE_MARKER_CONFIRMED=$marker" | tee -a client-smoke-status.txt
  }

  run_outline_case() {
    local face="$1" sx="$2" sy="$3" sz="$4"
    shift 4
    local miss_x="$1" miss_y="$2" miss_z="$3" miss_yaw="$4" miss_pitch="$5"
    shift 5
    local hit_x="$1" hit_y="$2" hit_z="$3" hit_yaw="$4" hit_pitch="$5"
    local geometry X Y WIDTH HEIGHT aim_x aim_y

    # Clear the previous crystal and all six possible supports, then reset
    # the floor because a correct miss ray may continue down to it.
    send_chat '/fill 249 150 250 251 152 252 minecraft:air'
    send_chat '/fill 247 148 248 253 148 254 minecraft:stone'
    send_chat "/setblock $sx $sy $sz minecraft:stone"
    send_chat '/setblock 250 151 251 thaumcraft:crystal_aqua'
    send_chat "/execute if block 250 151 251 thaumcraft:crystal_aqua run say CRYSTAL_OUTLINE_SETUP_$face"
    assert_outline_marker "CRYSTAL_OUTLINE_SETUP_$face"

    geometry="$(xdotool getwindowgeometry --shell "$WIN")"
    eval "$geometry"
    aim_x=$((X + WIDTH / 2))
    aim_y=$((Y + HEIGHT / 2))
    xdotool mousemove "$aim_x" "$aim_y"
    send_chat "/tp @p $miss_x $miss_y $miss_z $miss_yaw $miss_pitch"
    capture_outline "crystal-outline-$face-miss"
    xdotool click 1
    sleep 1
    send_chat "/execute if block 250 151 251 thaumcraft:crystal_aqua run say CRYSTAL_OUTLINE_MISS_RETAINED_$face"
    assert_outline_marker "CRYSTAL_OUTLINE_MISS_RETAINED_$face"

    geometry="$(xdotool getwindowgeometry --shell "$WIN")"
    eval "$geometry"
    aim_x=$((X + WIDTH / 2))
    aim_y=$((Y + HEIGHT / 2))
    xdotool mousemove "$aim_x" "$aim_y"
    send_chat "/tp @p $hit_x $hit_y $hit_z $hit_yaw $hit_pitch"
    capture_outline "crystal-outline-$face-hit"
    xdotool click 1
    sleep 1
    send_chat "/execute unless block 250 151 251 thaumcraft:crystal_aqua run say CRYSTAL_OUTLINE_HIT_REMOVED_$face"
    assert_outline_marker "CRYSTAL_OUTLINE_HIT_REMOVED_$face"
    echo "CRYSTAL_OUTLINE_FACE_$face=PASS" | tee -a client-smoke-status.txt
  }
"""
    for face, support, miss, hit in FACE_CASES:
        values = (face, *support, *miss, *hit)
        body += "  run_outline_case " + " ".join(shell_number(v) for v in values) + "\n"

    body += r"""
  # Two support faces must restore the original full-cube outline. The ray
  # travels through x<0.5 AND y<0.5, so neither one-face half-cube would hit.
  send_chat '/fill 249 150 250 251 152 252 minecraft:air'
  send_chat '/fill 247 148 248 253 148 254 minecraft:stone'
  send_chat '/setblock 250 152 251 minecraft:stone'
  send_chat '/setblock 251 151 251 minecraft:stone'
  send_chat '/setblock 250 151 251 thaumcraft:crystal_aqua'
  send_chat '/execute if block 250 151 251 thaumcraft:crystal_aqua run say CRYSTAL_OUTLINE_SETUP_MULTIFACE'
  assert_outline_marker 'CRYSTAL_OUTLINE_SETUP_MULTIFACE'
  geometry="$(xdotool getwindowgeometry --shell "$WIN")"
  eval "$geometry"
  xdotool mousemove "$((X + WIDTH / 2))" "$((Y + HEIGHT / 2))"
  send_chat '/tp @p 250.25 149.63 249.5 0 0'
  capture_outline 'crystal-outline-multiface-full-cube'
  xdotool click 1
  sleep 1
  send_chat '/execute unless block 250 151 251 thaumcraft:crystal_aqua run say CRYSTAL_OUTLINE_MULTIFACE_FULL_CUBE_HIT'
  assert_outline_marker 'CRYSTAL_OUTLINE_MULTIFACE_FULL_CUBE_HIT'

  test "$(grep -c '^CRYSTAL_OUTLINE_FACE_.*=PASS$' client-smoke-status.txt)" -eq 6
  test "$(find client-smoke/screenshots -maxdepth 1 -name 'crystal-outline-*.png' | wc -l)" -eq 13
  if grep -Eiq 'Minecraft has crashed|Mod loading has failed|Exception in thread "Render thread"' client-smoke/client.log; then
    echo 'CRYSTAL_OUTLINE_CLIENT_CRASH=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'CRYSTAL_OUTLINE_REAL_CLIENT_RAY_PICK=PASS (six faces + multi-face full cube)' | tee -a client-smoke-status.txt
  exit 0
fi
"""
    return body


def build_script() -> str:
    source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
    if source.count(ANCHOR) != 1:
        raise SystemExit("Client smoke anchor changed; refuse blind injection")
    return source.split(ANCHOR, 1)[0] + build_body()


if __name__ == "__main__":
    sys.stdout.write(build_script())
