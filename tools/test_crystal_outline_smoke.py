#!/usr/bin/env python3
"""Static, geometric and rotation-contract tests for crystal outline client smoke."""
import math

from client_crystal_outline_smoke import (
    FACE_CASES,
    MULTI_FACE_AIM,
    MULTI_FACE_PLAYER,
    PLAYER_EYE_HEIGHT,
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
    direction = expected_direction(player_feet, aim)
    distance = ray_probe_distance(player_feet, aim)
    full_length = math.dist(
        (player_feet[0], player_feet[1] + PLAYER_EYE_HEIGHT, player_feet[2]), aim
    )
    assert 0.0 < distance < full_length
    point = (
        player_feet[0] + direction[0] * distance,
        player_feet[1] + PLAYER_EYE_HEIGHT + direction[1] * distance,
        player_feet[2] + direction[2] * distance,
    )
    assert in_crystal(point), (player_feet, aim, distance, point)


for face, support, miss, miss_aim, hit, hit_aim in FACE_CASES:
    assert len(support) == len(miss) == len(miss_aim) == len(hit) == len(hit_aim) == 3
    assert in_crystal(miss_aim) and in_crystal(hit_aim), face
    assert not in_selected_half(face, miss_aim), (face, "miss target entered selected half")
    assert in_selected_half(face, hit_aim), (face, "hit target missed selected half")
    assert miss != hit, face
    assert_ray_rotation(miss, miss_aim)
    assert_ray_rotation(hit, hit_aim)
    assert_ray_probe_inside_cell(miss, miss_aim)
    assert_ray_probe_inside_cell(hit, hit_aim)

# UP rays are horizontal from eye height. The previous /tp facing-position
# target was at eye height but the command's FEET anchor introduced a 1.62m
# vertical component; the explicit rotation must now be exactly horizontal.
up_case = FACE_CASES[0]
up_miss_yaw, up_miss_pitch = rotation_for_ray(up_case[2], up_case[3])
assert up_miss_yaw == 0.0
assert abs(up_miss_pitch) < 1.0e-9

# A perfectly vertical ray has no meaningful yaw; pin it to zero and retain
# the exact downward pitch rather than emitting NaN or an unstable angle.
vertical_case = FACE_CASES[2]
vertical_yaw, vertical_pitch = rotation_for_ray(vertical_case[2], vertical_case[3])
assert vertical_yaw == 0.0
assert abs(vertical_pitch - 90.0) < 1.0e-9

# With UP + EAST supports, BETA26 returns the full cube. This point is inside
# the block but outside both one-face half-cubes.
assert in_crystal(MULTI_FACE_AIM)
assert not in_selected_half("up", MULTI_FACE_AIM)
assert not in_selected_half("east", MULTI_FACE_AIM)
assert_ray_rotation(MULTI_FACE_PLAYER, MULTI_FACE_AIM)
assert_ray_probe_inside_cell(MULTI_FACE_PLAYER, MULTI_FACE_AIM)

body = build_body()
for face, *_ in FACE_CASES:
    assert body.count(f"run_outline_case {face} ") == 1, face
assert "CRYSTAL_OUTLINE_SETUP_$face" in body
assert "CRYSTAL_OUTLINE_MISS_RETAINED_$face" in body
assert "CRYSTAL_OUTLINE_HIT_REMOVED_$face" in body
assert "CRYSTAL_OUTLINE_MULTIFACE_FULL_CUBE_HIT" in body
assert body.count("assert_ray_cell()") == 1
assert body.count('assert_ray_cell "$face" miss "$miss_probe"') == 1
assert body.count('assert_ray_cell "$face" hit "$hit_probe"') == 1
assert "assert_ray_cell multiface hit" in body
assert "anchored eyes positioned ^ ^ ^$distance" in body
assert body.count("xdotool click 1") == 3
assert body.count(" facing ") == 0
assert body.count('send_chat "/tp @p') == 2
assert body.count("send_chat '/tp @p") == 1
assert "miss_aim_x" not in body and "hit_aim_x" not in body
assert "capture_outline" in body
assert 'test "$(find client-smoke/screenshots' in body
assert body.rstrip().endswith("fi")
print("Crystal outline client-smoke contract: PASS (6 faces + multi-face; eye-origin yaw/pitch)")
