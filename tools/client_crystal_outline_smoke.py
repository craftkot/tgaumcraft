#!/usr/bin/env python3
"""Generate a real NeoForge-client ray-picking regression for BETA26 crystal outlines."""
from pathlib import Path
import math
import sys

ANCHOR = "  # The initial world is already creative + peaceful, as established"

# (face, support xyz, miss player feet xyz/yaw/pitch, hit player feet xyz/yaw/pitch)
# Player eye height is 1.62. Horizontal rays test the UP/DOWN halves; vertical
# rays test EAST/WEST/SOUTH/NORTH halves without crossing the selected half.
FACE_CASES = (
    # (face, support xyz, miss player xyz, miss aim xyz, hit player xyz, hit aim xyz)
    # Aim points are world-space points on the intended BETA26 half-cube.
    # Vanilla yaw/pitch is calculated from the player's eye position.
    ("up",    (250, 152, 251), (250.5, 149.63, 249.5), (250.5, 151.25, 251.5),
                                  (250.5, 150.13, 249.5), (250.5, 151.75, 251.5)),
    ("down",  (250, 150, 251), (250.5, 150.13, 249.5), (250.5, 151.75, 251.5),
                                  (250.5, 149.63, 249.5), (250.5, 151.25, 251.5)),
    ("east",  (251, 151, 251), (250.25, 154, 251.5), (250.25, 151.5, 251.5),
                                  (250.75, 154, 251.5), (250.75, 151.5, 251.5)),
    ("west",  (249, 151, 251), (250.75, 154, 251.5), (250.75, 151.5, 251.5),
                                  (250.25, 154, 251.5), (250.25, 151.5, 251.5)),
    ("south", (250, 151, 252), (250.5, 154, 251.25), (250.5, 151.5, 251.25),
                                  (250.5, 154, 251.75), (250.5, 151.5, 251.75)),
    ("north", (250, 151, 250), (250.5, 154, 251.75), (250.5, 151.5, 251.75),
                                  (250.5, 154, 251.25), (250.5, 151.5, 251.25)),
)

PLAYER_EYE_HEIGHT = 1.62
MULTI_FACE_PLAYER = (250.25, 149.63, 249.5)
MULTI_FACE_AIM = (250.25, 151.25, 251.5)


def rotation_for_ray(player_feet: tuple[float, float, float],
                     aim: tuple[float, float, float]) -> tuple[float, float]:
    """Return vanilla yaw/pitch for a ray from the player's eye to a world point."""
    dx = aim[0] - player_feet[0]
    dy = aim[1] - (player_feet[1] + PLAYER_EYE_HEIGHT)
    dz = aim[2] - player_feet[2]
    horizontal = math.hypot(dx, dz)
    yaw = 0.0 if horizontal < 1.0e-12 else math.degrees(math.atan2(-dx, dz))
    pitch = -math.degrees(math.atan2(dy, horizontal))
    return yaw, pitch


def ray_probe_distance(player_feet: tuple[float, float, float],
                       aim: tuple[float, float, float]) -> float:
    """Sample 90% along the eye-origin ray, safely inside the target block cell."""
    dx = aim[0] - player_feet[0]
    dy = aim[1] - (player_feet[1] + PLAYER_EYE_HEIGHT)
    dz = aim[2] - player_feet[2]
    return math.sqrt(dx * dx + dy * dy + dz * dz) * 0.9


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

  # Verify the actual server-side eye-origin look vector enters the crystal's
  # block cell before testing its outline. A retained block after a miss alone
  # is inconclusive: the camera may simply have missed the block entirely.
  assert_ray_cell() {
    local face="$1" phase="$2" distance="$3"
    send_chat '/data get entity @p Rotation'
    send_chat "/execute as @p at @s anchored eyes positioned ^ ^ ^$distance if block ~ ~ ~ thaumcraft:crystal_aqua run say CRYSTAL_OUTLINE_RAY_CELL_${phase}_$face"
    assert_outline_marker "CRYSTAL_OUTLINE_RAY_CELL_${phase}_$face"
  }

  run_outline_case() {
    local face="$1" sx="$2" sy="$3" sz="$4"
    shift 4
    local miss_x="$1" miss_y="$2" miss_z="$3"
    local miss_yaw="$4" miss_pitch="$5" miss_probe="$6"
    shift 6
    local hit_x="$1" hit_y="$2" hit_z="$3"
    local hit_yaw="$4" hit_pitch="$5" hit_probe="$6"
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
    assert_ray_cell "$face" miss "$miss_probe"
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
    assert_ray_cell "$face" hit "$hit_probe"
    capture_outline "crystal-outline-$face-hit"
    xdotool click 1
    sleep 1
    send_chat "/execute unless block 250 151 251 thaumcraft:crystal_aqua run say CRYSTAL_OUTLINE_HIT_REMOVED_$face"
    assert_outline_marker "CRYSTAL_OUTLINE_HIT_REMOVED_$face"
    echo "CRYSTAL_OUTLINE_FACE_$face=PASS" | tee -a client-smoke-status.txt
  }
"""
    for face, support, miss, miss_aim, hit, hit_aim in FACE_CASES:
        miss_yaw, miss_pitch = rotation_for_ray(miss, miss_aim)
        hit_yaw, hit_pitch = rotation_for_ray(hit, hit_aim)
        miss_probe = ray_probe_distance(miss, miss_aim)
        hit_probe = ray_probe_distance(hit, hit_aim)
        values = (face, *support, *miss, miss_yaw, miss_pitch, miss_probe,
                  *hit, hit_yaw, hit_pitch, hit_probe)
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
  send_chat '__MULTI_FACE_TP__'
  assert_ray_cell multiface hit __MULTI_FACE_PROBE__
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
    multi_yaw, multi_pitch = rotation_for_ray(MULTI_FACE_PLAYER, MULTI_FACE_AIM)
    multi_probe = ray_probe_distance(MULTI_FACE_PLAYER, MULTI_FACE_AIM)
    multi_values = (*MULTI_FACE_PLAYER, multi_yaw, multi_pitch)
    multi_command = "  send_chat '/tp @p " + " ".join(shell_number(v) for v in multi_values) + "'"
    body = replace_once(body, "  send_chat '__MULTI_FACE_TP__'", multi_command)
    body = replace_once(body, "__MULTI_FACE_PROBE__", shell_number(multi_probe))
    return body


def replace_once(source: str, old: str, new: str) -> str:
    if source.count(old) != 1:
        raise SystemExit(f"Expected one template marker, found {source.count(old)}")
    return source.replace(old, new)


def build_script() -> str:
    source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
    if source.count(ANCHOR) != 1:
        raise SystemExit("Client smoke anchor changed; refuse blind injection")
    return source.split(ANCHOR, 1)[0] + build_body()


if __name__ == "__main__":
    sys.stdout.write(build_script())
