#!/usr/bin/env python3
"""Static and geometric contract tests for the generated real-client crystal outline smoke."""
from client_crystal_outline_smoke import FACE_CASES, build_body

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


for face, support, miss, miss_aim, hit, hit_aim in FACE_CASES:
    assert len(support) == len(miss) == len(miss_aim) == len(hit) == len(hit_aim) == 3
    assert in_crystal(miss_aim) and in_crystal(hit_aim), face
    assert not in_selected_half(face, miss_aim), (face, "miss target entered selected half")
    assert in_selected_half(face, hit_aim), (face, "hit target missed selected half")
    assert miss != hit, face

# With UP + EAST supports, the original BETA26 outline is the full cube.
# This ray point is inside the block but outside both one-face half-cubes.
multi_face_aim = (250.25, 151.25, 251.5)
assert in_crystal(multi_face_aim)
assert not in_selected_half("up", multi_face_aim)
assert not in_selected_half("east", multi_face_aim)

body = build_body()
for face, *_ in FACE_CASES:
    assert body.count(f"run_outline_case {face} ") == 1, face
assert "CRYSTAL_OUTLINE_SETUP_$face" in body
assert "CRYSTAL_OUTLINE_MISS_RETAINED_$face" in body
assert "CRYSTAL_OUTLINE_HIT_REMOVED_$face" in body
assert "CRYSTAL_OUTLINE_MULTIFACE_FULL_CUBE_HIT" in body
assert body.count("xdotool click 1") == 3
assert body.count(" facing ") == 7, body.count(" facing ")
assert "miss_yaw" not in body and "hit_yaw" not in body
assert "capture_outline" in body
assert 'test "$(find client-smoke/screenshots' in body
assert body.rstrip().endswith("fi")
print("Crystal outline client-smoke contract: PASS (6 faces + multi-face full cube; explicit ray targets)")
