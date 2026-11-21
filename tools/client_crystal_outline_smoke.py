#!/usr/bin/env python3
"""Generate a real NeoForge-client ray-picking regression for BETA26 crystal outlines."""
from pathlib import Path
import math
import re
import sys

ANCHOR = "  # The initial world is already creative + peaceful, as established"

# (face, support xyz, miss player feet xyz/yaw/pitch, hit player feet xyz/yaw/pitch)
# Every origin is grounded on a fixed platform. UP and side-face rays approach
# from below; DOWN approaches from above so the miss ray never crosses its
# lower-half outline before reaching the excluded upper half.
SUPPORT_BLOCK_FOR_FACE = {
    "up": "minecraft:stone",
    "down": "minecraft:stone",
    "east": "minecraft:stone",
    "west": "minecraft:stone",
    # These two are Material.ROCK in BETA26 but were omitted by the old stone-only tag.
    "north": "minecraft:sandstone",
    "south": "minecraft:nether_bricks",
}

FACE_CASES = (
    # (face, support xyz, miss player xyz, miss aim xyz, hit player xyz, hit aim xyz)
    # Aim points are world-space points on the intended BETA26 half-cube.
    # Vanilla yaw/pitch is calculated from the player's eye position.
    ("up",    (250, 152, 251), (250.5, 149, 249.5), (250.5, 151.25, 251.5),
                                  (250.5, 149, 249.5), (250.5, 151.75, 251.5)),
    ("down",  (250, 150, 251), (249.5, 154, 249.5), (250.95, 151.75, 251.5),
                                  (249.5, 154, 249.5), (250.5, 151.25, 251.5)),
    ("east",  (251, 151, 251), (250.25, 149, 251.5), (250.25, 151.5, 251.5),
                                  (250.25, 149, 251.5), (250.75, 151.5, 251.5)),
    ("west",  (249, 151, 251), (250.75, 149, 251.5), (250.75, 151.5, 251.5),
                                  (250.75, 149, 251.5), (250.25, 151.5, 251.5)),
    ("south", (250, 151, 252), (250.5, 149, 251.25), (250.5, 151.5, 251.25),
                                  (250.5, 149, 251.25), (250.5, 151.5, 251.75)),
    ("north", (250, 151, 250), (250.5, 149, 251.75), (250.5, 151.5, 251.75),
                                  (250.5, 149, 251.75), (250.5, 151.5, 251.25)),
)

PLAYER_EYE_HEIGHT = 1.62
CLIENT_BLOCK_PICK_REACH = 5.0

def parse_block_interaction_range(log: str) -> float | None:
    """Parse vanilla 1.21.1 /attribute get output for the live player."""
    number = r"[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?"
    matches = re.findall(
        rf"(?:Dev has a value of|Value of attribute Block Interaction Range for entity Dev is) ({number})",
        log,
    )
    return float(matches[-1]) if matches else None


MULTI_FACE_PLAYER = (250.25, 149, 249.5)
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


def ray_probe_point(player_feet: tuple[float, float, float],
                    aim: tuple[float, float, float]) -> tuple[float, float, float]:
    """Return a world point 98% along the eye-origin ray, inside the target cell."""
    dx = aim[0] - player_feet[0]
    dy = aim[1] - (player_feet[1] + PLAYER_EYE_HEIGHT)
    dz = aim[2] - player_feet[2]
    return (
        player_feet[0] + dx * 0.98,
        player_feet[1] + PLAYER_EYE_HEIGHT + dy * 0.98,
        player_feet[2] + dz * 0.98,
    )


def ray_probe_distance(player_feet: tuple[float, float, float],
                       aim: tuple[float, float, float]) -> float:
    """Return 98% of the eye-origin ray length, inside even a near-top entry."""
    origin = (player_feet[0], player_feet[1] + PLAYER_EYE_HEIGHT, player_feet[2])
    return math.dist(origin, aim) * 0.98


def ray_endpoint_at_reach(player_feet: tuple[float, float, float],
                          aim: tuple[float, float, float],
                          reach: float = CLIENT_BLOCK_PICK_REACH) -> tuple[float, float, float]:
    """Extend the eye-origin direction to the client's full block-picking reach."""
    origin = (player_feet[0], player_feet[1] + PLAYER_EYE_HEIGHT, player_feet[2])
    delta = tuple(aim[i] - origin[i] for i in range(3))
    length = math.sqrt(sum(component * component for component in delta))
    if length <= 1.0e-12 or reach <= 0.0:
        raise ValueError("ray and reach must be positive")
    scale = reach / length
    return tuple(origin[i] + delta[i] * scale for i in range(3))


def shell_number(value: int | float) -> str:
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


def build_body() -> str:
    body = r"""
  echo 'CRYSTAL_OUTLINE_REAL_CLIENT=YES' | tee -a client-smoke-status.txt
  send_chat '/gamemode creative'
  # Creative mode extends the vanilla block-interaction range to 5 blocks.
  # Read the effective live attribute so the geometry test never assumes a
  # reach value that a mod or future game mode silently changed.
  send_chat '/attribute @p minecraft:player.block_interaction_range get'
  python3 - client-smoke/client.log client-smoke-status.txt <<'PY'
import sys
sys.path.insert(0, "tools")
from client_crystal_outline_smoke import parse_block_interaction_range

log = open(sys.argv[1], encoding="utf-8").read()
actual = parse_block_interaction_range(log)
if actual is None:
    raise SystemExit("CRYSTAL_OUTLINE_BLOCK_REACH_MISSING")
if abs(actual - 5.0) > 0.001:
    raise SystemExit(f"CRYSTAL_OUTLINE_BLOCK_REACH_MISMATCH={actual}")
marker = f"CRYSTAL_OUTLINE_BLOCK_REACH_CONFIRMED={actual}"
print(marker)
with open(sys.argv[2], "a", encoding="utf-8") as status:
    status.write(marker + "\n")
PY
  send_chat '/difficulty peaceful'
  send_chat '/time set noon'
  send_chat '/weather clear'
  send_chat '/forceload add 250 250'
  send_chat '/fill 247 149 248 253 160 254 minecraft:air'
  send_chat '/fill 247 148 248 253 148 254 minecraft:stone'

  # Do not depend on creative-flight key timing. Most origins stand on the
  # y=148 floor; the DOWN case creates a separate elevated platform below.

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

  # A previous absolute-coordinate probe only proved that a crystal existed
  # at a predicted point; it did NOT prove the live player's ray reached it.
  # Check authoritative player Pos/Rotation, then probe from the actual eyes.
  assert_player_position() {
    local expected_x="$1" expected_y="$2" expected_z="$3" label="$4"
    send_chat '/data get entity @p Pos'
    python3 - "$expected_x" "$expected_y" "$expected_z" "$label" client-smoke/client.log <<'PY'
import re
import sys
expected = tuple(map(float, sys.argv[1:4]))
label = sys.argv[4]
log = open(sys.argv[5], encoding="utf-8").read()
number = r"[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?"
matches = re.findall(
    rf"Dev has the following entity data: \[({number})d, ({number})d, ({number})d\]",
    log,
)
if not matches:
    raise SystemExit("CRYSTAL_OUTLINE_POSITION_MISSING=" + label)
actual = tuple(map(float, matches[-1]))
if max(abs(a - b) for a, b in zip(actual, expected)) > 0.02:
    raise SystemExit(
        f"CRYSTAL_OUTLINE_POSITION_MISMATCH={label}:"
        f"expected={expected} actual={actual}"
    )
print(f"CRYSTAL_OUTLINE_POSITION_CONFIRMED={label}:{actual}")
PY
  }

  assert_player_rotation() {
    local expected_yaw="$1" expected_pitch="$2" label="$3"
    send_chat '/data get entity @p Rotation'
    python3 - "$expected_yaw" "$expected_pitch" "$label" client-smoke/client.log <<'PY'
import re
import sys
expected_yaw = float(sys.argv[1])
expected_pitch = float(sys.argv[2])
label = sys.argv[3]
log = open(sys.argv[4], encoding="utf-8").read()
matches = re.findall(
    r"Dev has the following entity data: \[(-?\d+(?:\.\d+)?)f, (-?\d+(?:\.\d+)?)f\]",
    log,
)
if not matches:
    raise SystemExit("CRYSTAL_OUTLINE_ROTATION_MISSING=" + label)
actual_yaw, actual_pitch = map(float, matches[-1])
if abs(actual_yaw - expected_yaw) > 0.11 or abs(actual_pitch - expected_pitch) > 0.11:
    raise SystemExit(
        f"CRYSTAL_OUTLINE_ROTATION_MISMATCH={label}:"
        f"expected={expected_yaw},{expected_pitch} actual={actual_yaw},{actual_pitch}"
    )
print(f"CRYSTAL_OUTLINE_ROTATION_CONFIRMED={label}:{actual_yaw},{actual_pitch}")
PY
  }

  assert_ray_cell() {
    local face="$1" phase="$2" yaw="$3" pitch="$4"
    local expected_x="$5" expected_y="$6" expected_z="$7" probe_distance="$8"
    assert_player_position "$expected_x" "$expected_y" "$expected_z" "$face-$phase"
    assert_player_rotation "$yaw" "$pitch" "$face-$phase"

    # First prove the anchored-eyes command itself executed. If this marker
    # exists but the conditional marker does not, the live ray missed the cell.
    send_chat "/execute as @p at @s anchored eyes positioned ^ ^ ^$probe_distance run say CRYSTAL_OUTLINE_RAY_PROBE_EXECUTED_${phase}_$face"
    assert_outline_marker "CRYSTAL_OUTLINE_RAY_PROBE_EXECUTED_${phase}_$face"
    send_chat "/execute as @p at @s anchored eyes positioned ^ ^ ^$probe_distance if block ~ ~ ~ thaumcraft:crystal_aqua run say CRYSTAL_OUTLINE_RAY_CELL_${phase}_$face"
    assert_outline_marker "CRYSTAL_OUTLINE_RAY_CELL_${phase}_$face"
  }

  run_outline_case() {
    local face="$1" sx="$2" sy="$3" sz="$4"
    shift 4
    local miss_x="$1" miss_y="$2" miss_z="$3"
    local miss_yaw="$4" miss_pitch="$5" miss_probe_distance="$6"
    shift 6
    local hit_x="$1" hit_y="$2" hit_z="$3"
    local hit_yaw="$4" hit_pitch="$5" hit_probe_distance="$6"
    local geometry X Y WIDTH HEIGHT aim_x aim_y support_block
    support_block=minecraft:stone
    case "$face" in
      north) support_block=minecraft:sandstone ;;
      south) support_block=minecraft:nether_bricks ;;
    esac

    # Clear the previous crystal and all six possible supports, then reset
    # the floor because a correct miss ray may continue down to it.
    send_chat '/fill 249 150 250 251 152 252 minecraft:air'
    send_chat '/fill 247 148 248 253 148 254 minecraft:stone'
    send_chat "/setblock $sx $sy $sz $support_block"
    echo "CRYSTAL_OUTLINE_SUPPORT_$face=$support_block" | tee -a client-smoke-status.txt
    send_chat '/setblock 250 151 251 thaumcraft:crystal_aqua'
    if [ "$face" = "down" ]; then
      # Stable top-down origin; this platform is offset from the ray path.
      send_chat '/setblock 249 153 249 minecraft:stone'
    fi
    send_chat "/execute if block 250 151 251 thaumcraft:crystal_aqua run say CRYSTAL_OUTLINE_SETUP_$face"
    assert_outline_marker "CRYSTAL_OUTLINE_SETUP_$face"

    geometry="$(xdotool getwindowgeometry --shell "$WIN")"
    eval "$geometry"
    aim_x=$((X + WIDTH / 2))
    aim_y=$((Y + HEIGHT / 2))
    xdotool mousemove "$aim_x" "$aim_y"
    send_chat "/tp @p $miss_x $miss_y $miss_z $miss_yaw $miss_pitch"
    assert_ray_cell "$face" miss "$miss_yaw" "$miss_pitch" "$miss_x" "$miss_y" "$miss_z" "$miss_probe_distance"
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
    assert_ray_cell "$face" hit "$hit_yaw" "$hit_pitch" "$hit_x" "$hit_y" "$hit_z" "$hit_probe_distance"
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
        miss_probe_distance = ray_probe_distance(miss, miss_aim)
        hit_probe_distance = ray_probe_distance(hit, hit_aim)
        values = (face, *support, *miss, miss_yaw, miss_pitch, miss_probe_distance,
                  *hit, hit_yaw, hit_pitch, hit_probe_distance)
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
  assert_ray_cell multiface hit __MULTI_FACE_ARGS__
  capture_outline 'crystal-outline-multiface-full-cube'
  xdotool click 1
  sleep 1
  send_chat '/execute unless block 250 151 251 thaumcraft:crystal_aqua run say CRYSTAL_OUTLINE_MULTIFACE_FULL_CUBE_HIT'
  assert_outline_marker 'CRYSTAL_OUTLINE_MULTIFACE_FULL_CUBE_HIT'

  test "$(grep -c '^CRYSTAL_OUTLINE_FACE_.*=PASS$' client-smoke-status.txt)" -eq 6
  test "$(grep -c '^CRYSTAL_OUTLINE_SUPPORT_north=minecraft:sandstone$' client-smoke-status.txt)" -eq 1
  test "$(grep -c '^CRYSTAL_OUTLINE_SUPPORT_south=minecraft:nether_bricks$' client-smoke-status.txt)" -eq 1
  echo 'CRYSTAL_OUTLINE_LEGACY_ROCK_SUPPORTS=PASS' | tee -a client-smoke-status.txt
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
    multi_probe = ray_probe_point(MULTI_FACE_PLAYER, MULTI_FACE_AIM)
    multi_values = (*MULTI_FACE_PLAYER, multi_yaw, multi_pitch)
    multi_command = "  send_chat '/tp @p " + " ".join(shell_number(v) for v in multi_values) + "'"
    multi_distance = ray_probe_distance(MULTI_FACE_PLAYER, MULTI_FACE_AIM)
    multi_args = " ".join(shell_number(v) for v in (
        multi_yaw, multi_pitch, *MULTI_FACE_PLAYER, multi_distance
    ))
    body = replace_once(body, "  send_chat '__MULTI_FACE_TP__'", multi_command)
    body = replace_once(body, "__MULTI_FACE_ARGS__", multi_args)
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
