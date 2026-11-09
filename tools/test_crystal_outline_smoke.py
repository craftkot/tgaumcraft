#!/usr/bin/env python3
"""Static contract tests for the generated real-client crystal outline smoke."""
from client_crystal_outline_smoke import FACE_CASES, build_body

assert tuple(case[0] for case in FACE_CASES) == (
    "up", "down", "east", "west", "south", "north"
)
body = build_body()
for face, *_ in FACE_CASES:
    assert body.count(f"run_outline_case {face} ") == 1, face
assert "CRYSTAL_OUTLINE_SETUP_$face" in body
assert "CRYSTAL_OUTLINE_MISS_RETAINED_$face" in body
assert "CRYSTAL_OUTLINE_HIT_REMOVED_$face" in body
assert "CRYSTAL_OUTLINE_MULTIFACE_FULL_CUBE_HIT" in body
assert body.count("xdotool click 1") == 3
assert "capture_outline" in body
assert "test \"$(find client-smoke/screenshots" in body
assert body.rstrip().endswith("fi")
print("Crystal outline client-smoke contract: PASS (6 faces + multi-face full cube)")
