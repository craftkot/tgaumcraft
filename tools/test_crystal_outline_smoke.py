#!/usr/bin/env python3
"""Static, geometric and rotation-contract tests for crystal outline client smoke."""
import math
from pathlib import Path

from client_crystal_outline_smoke import (
    FACE_CASES,
    MULTI_FACE_AIM,
    MULTI_FACE_PLAYER,
    PLAYER_EYE_HEIGHT,
    ray_probe_point,
    ray_probe_distance,
    build_body,
    rotation_for_ray,
)

assert tuple(case[0] for case in FACE_CASES) == (
    "up", "down", "east", "west", "south", "north"
)


def in_selected_half(face, point):
    x, y, z = point
    if face == "up":
        return y >= 151.5
    if face == "down":
        return y <= 151.5
    if face == "east":
        return x >= 250.5
    if face == "west":
        return x <= 250.5
    if face == "south":
        return z >= 251.5
    if face == "north":
        return z <= 251.5
    raise AssertionError(face)


def in_crystal(point):
    x, y, z = point
    return 250 <= x <= 251 and 151 <= y <= 152 and 251 <= z <= 252


def bounds_for_face(face):
    return {
        "up": ((250, 151.5, 251), (251, 152, 252)),
        "down": ((250, 151, 251), (251, 151.5, 252)),
        "east": ((250.5, 151, 251), (251, 152, 252)),
        "west": ((250, 151, 251), (250.5, 152, 252)),
        "south": ((250, 151, 251.5), (251, 152, 252)),
        "north": ((250, 151, 251), (251, 152, 251.5)),
    }[face]


def segment_intersects_bounds(player_feet, aim, bounds):
    """Slab-test the entire eye-to-aim segment against a world-space AABB."""
    origin = (player_feet[0], player_feet[1] + PLAYER_EYE_HEIGHT, player_feet[2])
    low, high = bounds
    t_min, t_max = 0.0, 1.0
    for axis in range(3):
        delta = aim[axis] - origin[axis]
        if abs(delta) < 1.0e-12:
            if origin[axis] < low[axis] or origin[axis] > high[axis]:
                return False
            continue
        first = (low[axis] - origin[axis]) / delta
        last = (high[axis] - origin[axis]) / delta
        if first > last:
            first, last = last, first
        t_min = max(t_min, first)
        t_max = min(t_max, last)
        if t_min > t_max:
            return False
    return t_max >= 0.0 and t_min <= 1.0


def expected_direction(player_feet, aim):
    dx = aim[0] - player_feet[0]
    dy = aim[1] - (player_feet[1] + PLAYER_EYE_HEIGHT)
    dz = aim[2] - player_feet[2]
    length = math.sqrt(dx * dx + dy * dy + dz * dz)
    assert length > 0
    return dx / length, dy / length, dz / length


def direction_from_rotation(yaw, pitch):
    yaw_r = math.radians(yaw)
    pitch_r = math.radians(pitch)
    return (
        -math.sin(yaw_r) * math.cos(pitch_r),
        -math.sin(pitch_r),
        math.cos(yaw_r) * math.cos(pitch_r),
    )


def assert_ray_rotation(player_feet, aim):
    yaw, pitch = rotation_for_ray(player_feet, aim)
    assert -180.0 <= yaw <= 180.0, (yaw, pitch)
    assert -90.0 <= pitch <= 90.0, (yaw, pitch)
    actual = direction_from_rotation(yaw, pitch)
    expected = expected_direction(player_feet, aim)
    assert max(abs(a - b) for a, b in zip(actual, expected)) < 1.0e-9, (
        player_feet, aim, yaw, pitch, actual, expected
    )


def assert_ray_probe_inside_cell(player_feet, aim):
    distance = ray_probe_distance(player_feet, aim)
    origin = (player_feet[0], player_feet[1] + PLAYER_EYE_HEIGHT, player_feet[2])
    full_length = math.dist(origin, aim)
    assert 0.0 < distance < full_length, (player_feet, aim, distance, full_length)
    point = ray_probe_point(player_feet, aim)
    assert in_crystal(point), (player_feet, aim, distance, point)


for face, support, miss, miss_aim, hit, hit_aim in FACE_CASES:
    assert len(support) == len(miss) == len(miss_aim) == len(hit) == len(hit_aim) == 3
    assert in_crystal(miss_aim) and in_crystal(hit_aim), face
    assert not in_selected_half(face, miss_aim), (face, "miss target entered selected half")
    assert in_selected_half(face, hit_aim), (face, "hit target missed selected half")
    assert miss == hit, (face, "miss and hit must share a grounded origin")
    assert miss_aim != hit_aim, face
    selected_bounds = bounds_for_face(face)
    assert not segment_intersects_bounds(miss, miss_aim, selected_bounds), (
        face, "miss ray crosses the selected half", miss, miss_aim
    )
    assert segment_intersects_bounds(hit, hit_aim, selected_bounds), (
        face, "hit ray never enters the selected half", hit, hit_aim
    )
    assert_ray_rotation(miss, miss_aim)
    assert_ray_rotation(hit, hit_aim)
    assert_ray_probe_inside_cell(miss, miss_aim)
    assert_ray_probe_inside_cell(hit, hit_aim)

# The UP miss ray starts from the grounded floor (eyes Y=150.62) and
# remains below the UP half-cube boundary at Y=151.5.
up_case = FACE_CASES[0]
up_miss_yaw, up_miss_pitch = rotation_for_ray(up_case[2], up_case[3])
assert up_miss_yaw == 0.0
assert abs(up_miss_pitch - (-17.484427323220704)) < 1.0e-9

# A perfectly vertical upward ray has no meaningful yaw; pin it to zero
# and retain the exact upward pitch rather than emitting NaN or an unstable angle.
vertical_case = FACE_CASES[2]
vertical_yaw, vertical_pitch = rotation_for_ray(vertical_case[2], vertical_case[3])
assert vertical_yaw == 0.0
assert abs(vertical_pitch + 90.0) < 1.0e-9

# With UP + EAST supports, BETA26 returns the full cube. This point is inside
# the block but outside both one-face half-cubes.
assert in_crystal(MULTI_FACE_AIM)
assert not in_selected_half("up", MULTI_FACE_AIM)
assert not in_selected_half("east", MULTI_FACE_AIM)
assert_ray_rotation(MULTI_FACE_PLAYER, MULTI_FACE_AIM)
assert_ray_probe_inside_cell(MULTI_FACE_PLAYER, MULTI_FACE_AIM)
full_cube = ((250, 151, 251), (251, 152, 252))
assert segment_intersects_bounds(MULTI_FACE_PLAYER, MULTI_FACE_AIM, full_cube)
assert not segment_intersects_bounds(
    MULTI_FACE_PLAYER, MULTI_FACE_AIM, bounds_for_face("up")
)
assert not segment_intersects_bounds(
    MULTI_FACE_PLAYER, MULTI_FACE_AIM, bounds_for_face("east")
)

# The only elevated origin stands on a real platform outside the eye-ray path;
# all other player feet positions are exactly on the y=148 stone floor.
assert all(case[2][1] == case[4][1] == 149 for case in FACE_CASES if case[0] != "down")
down_case = next(case for case in FACE_CASES if case[0] == "down")
assert down_case[2] == down_case[4] == (249.5, 154, 249.5)
down_platform = ((249, 153, 249), (250, 154, 250))
assert not segment_intersects_bounds(down_case[2], down_case[3], down_platform)
assert not segment_intersects_bounds(down_case[4], down_case[5], down_platform)
assert MULTI_FACE_PLAYER[1] == 149

# getShape is world-dependent: support faces are read from the live level.
# Every CrystalClusterBlock instance must opt out of the static BlockState shape cache.
crystal_source = (
    Path(__file__).resolve().parents[1]
    / "src/neoforge/java/dev/ehtid/thaumcraft/block/CrystalClusterBlock.java"
).read_text(encoding="utf-8")
assert crystal_source.count("props.dynamicShape()") == 1
assert "super(props.dynamicShape().sound(ORIGINAL_CRYSTAL_SOUND));" in crystal_source

body = build_body()
for face, *_ in FACE_CASES:
    assert body.count(f"run_outline_case {face} ") == 1, face
assert "CRYSTAL_OUTLINE_SETUP_$face" in body
assert "CRYSTAL_OUTLINE_MISS_RETAINED_$face" in body
assert "CRYSTAL_OUTLINE_HIT_REMOVED_$face" in body
assert "CRYSTAL_OUTLINE_MULTIFACE_FULL_CUBE_HIT" in body
assert body.count("assert_player_position()") == 1
assert body.count("assert_player_rotation()") == 1
assert body.count("assert_ray_cell()") == 1
assert body.count('assert_ray_cell "$face" miss "$miss_yaw" "$miss_pitch" "$miss_x" "$miss_y" "$miss_z" "$miss_probe_distance"') == 1
assert body.count('assert_ray_cell "$face" hit "$hit_yaw" "$hit_pitch" "$hit_x" "$hit_y" "$hit_z" "$hit_probe_distance"') == 1
assert "assert_ray_cell multiface hit" in body
assert "data get entity @p Pos" in body
assert "data get entity @p Rotation" in body
assert body.count("anchored eyes positioned ^ ^ ^$probe_distance") == 2
assert "CRYSTAL_OUTLINE_RAY_PROBE_EXECUTED_${phase}_$face" in body
assert "CRYSTAL_OUTLINE_RAY_CELL_${phase}_$face" in body
assert body.count("xdotool click 1") == 3
assert body.count(" facing ") == 0
assert body.count('send_chat "/tp @p') == 2
assert body.count("send_chat '/tp @p") == 1
assert "miss_aim_x" not in body and "hit_aim_x" not in body
assert "capture_outline" in body
assert "setblock 249 153 249 minecraft:stone" in body
assert 'if [ "$face" = "down" ]' in body
assert "xdotool key --clearmodifiers space" not in body
assert 'test "$(find client-smoke/screenshots' in body
assert body.rstrip().endswith("fi")
print("Crystal outline client-smoke contract: PASS (6 faces + multi-face; eye-origin yaw/pitch)")
