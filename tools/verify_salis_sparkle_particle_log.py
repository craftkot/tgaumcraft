#!/usr/bin/env python3
"""Verify real-client creation, rendering, animation and expiry of Salis hand and face sprites."""
from pathlib import Path
import math
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: verify_salis_sparkle_particle_log.py client.log")
log = Path(sys.argv[1]).read_text(errors="replace")

created = re.findall(
    r"TC_SMOKE_SALIS_SPARKLE_CREATED=YES id=(\d+) frameStart=(\d+) lifetime=(\d+) floaty=(true|false)",
    log,
)
rendered = re.findall(
    r"TC_SMOKE_SALIS_SPARKLE_RENDERED=YES id=(\d+) frame=(\d+) count=(\d+)",
    log,
)
expired = re.findall(
    r"TC_SMOKE_SALIS_SPARKLE_EXPIRED=YES id=(\d+) age=(\d+) lifetime=(\d+) count=(\d+)",
    log,
)
frames = {
    int(age): int(frame)
    for age, frame in re.findall(
        r"TC_SMOKE_SALIS_SPARKLE_FRAME=YES id=1 age=(\d+) frame=(\d+)", log
    )
}

face_created = re.findall(
    r"TC_SMOKE_SALIS_FACE_SPARKLE_CREATED=YES id=(\d+) frameStart=(\d+) lifetime=(\d+) scaleStart=([-+0-9.eE]+)",
    log,
)
face_rendered = re.findall(
    r"TC_SMOKE_SALIS_FACE_SPARKLE_RENDERED=YES id=(\d+) frame=(\d+) count=(\d+)",
    log,
)
face_expired = re.findall(
    r"TC_SMOKE_SALIS_FACE_SPARKLE_EXPIRED=YES id=(\d+) age=(\d+) lifetime=(\d+) count=(\d+)",
    log,
)
face_frames = {
    int(age): int(frame)
    for age, frame in re.findall(
        r"TC_SMOKE_SALIS_FACE_SPARKLE_FRAME=YES id=1 age=(\d+) frame=(\d+)", log
    )
}

if len(created) != 50:
    raise SystemExit(f"SALIS_REAL_SPRITE_CREATED=FAIL count={len(created)} expected=50")
ids = [int(entry[0]) for entry in created]
if sorted(ids) != list(range(1, 51)):
    raise SystemExit(f"SALIS_REAL_SPRITE_IDS=FAIL ids={ids}")
if any(int(start) not in (320, 512) for _, start, _, _ in created):
    raise SystemExit("SALIS_REAL_SPRITE_START_FRAME=FAIL")
if any(not 64 <= int(life) <= 79 for _, _, life, _ in created):
    raise SystemExit("SALIS_REAL_SPRITE_LIFETIME=FAIL")
if sum(flag == "true" for *_, flag in created) != 16:
    raise SystemExit("SALIS_REAL_FLOATY_SPLIT=FAIL expected 16/34")
if sum(flag == "false" for *_, flag in created) != 34:
    raise SystemExit("SALIS_REAL_FLOATY_SPLIT=FAIL expected 16/34")
if len(rendered) < 10:
    raise SystemExit(f"SALIS_REAL_SPRITE_RENDER=FAIL count={len(rendered)} expected>=10")
if len(expired) != 50:
    raise SystemExit(f"SALIS_REAL_SPRITE_EXPIRE=FAIL count={len(expired)} expected=50")
if any(int(age) != int(life) for _, age, life, _ in expired):
    raise SystemExit("SALIS_REAL_SPRITE_EXPIRE_AGE=FAIL")

first_start = int(created[0][1])
expected_frames = {
    1: first_start + 1,
    16: first_start,
    17: first_start + 1,
    31: first_start + 15,
    32: first_start,
}
if frames != expected_frames:
    raise SystemExit(f"SALIS_REAL_SPRITE_ANIMATION=FAIL actual={frames} expected={expected_frames}")

if not face_created:
    raise SystemExit("SALIS_REAL_FACE_SPRITE_CREATED=FAIL count=0")
face_ids = [int(entry[0]) for entry in face_created]
if sorted(face_ids) != list(range(1, len(face_created) + 1)):
    raise SystemExit(f"SALIS_REAL_FACE_SPRITE_IDS=FAIL ids={face_ids}")
if any(int(start) not in (320, 512) for _, start, _, _ in face_created):
    raise SystemExit("SALIS_REAL_FACE_SPRITE_START_FRAME=FAIL")
if any(not 64 <= int(life) <= 79 for _, _, life, _ in face_created):
    raise SystemExit("SALIS_REAL_FACE_SPRITE_LIFETIME=FAIL")
if any(not math.isfinite(float(scale)) for _, _, _, scale in face_created):
    raise SystemExit("SALIS_REAL_FACE_SPRITE_SCALE=FAIL")
if not face_rendered:
    raise SystemExit("SALIS_REAL_FACE_SPRITE_RENDER=FAIL count=0")
if not face_expired:
    raise SystemExit("SALIS_REAL_FACE_SPRITE_EXPIRE=FAIL count=0")
if any(int(age) != int(life) for _, age, life, _ in face_expired):
    raise SystemExit("SALIS_REAL_FACE_SPRITE_EXPIRE_AGE=FAIL")
face_first_start = int(face_created[0][1])
expected_face_frames = {
    1: face_first_start + 1,
    16: face_first_start,
    17: face_first_start + 1,
    31: face_first_start + 15,
    32: face_first_start,
}
if face_frames != expected_face_frames:
    raise SystemExit(f"SALIS_REAL_FACE_SPRITE_ANIMATION=FAIL actual={face_frames} expected={expected_face_frames}")

print("SALIS_REAL_SPRITE_CREATED=PASS count=50")
print(f"SALIS_REAL_SPRITE_RENDER=PASS count={len(rendered)}")
print("SALIS_REAL_SPRITE_ANIMATION=PASS 16-frame 320/512 sequence")
print("SALIS_REAL_SPRITE_EXPIRE=PASS count=50")
print(f"SALIS_REAL_FACE_SPRITE_CREATED=PASS count={len(face_created)}")
print(f"SALIS_REAL_FACE_SPRITE_RENDER=PASS count={len(face_rendered)}")
print("SALIS_REAL_FACE_SPRITE_ANIMATION=PASS 16-frame 320/512 sequence")
print(f"SALIS_REAL_FACE_SPRITE_EXPIRE=PASS count={len(face_expired)}")
