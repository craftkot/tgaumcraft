#!/usr/bin/env python3
"""Generate a REAL Minecraft 1.21.1 scan-rune client smoke.

Reuses the existing tested creative/peaceful integrated-world bootstrap.
Requires real Thaumometer use, actual particle construction, render callback,
expiry, and framebuffer change. No shared client_smoke.sh modifications.
"""
from pathlib import Path

from scan_rune_log_parser import parse_scan_rune_log
import sys

source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
anchor = "  # The initial world is already creative + peaceful, as established"
if source.count(anchor) != 1:
    raise SystemExit("World boot hook changed; refusing unsafe smoke injection")
head = source.split(anchor, 1)[0]
body = r'''
  echo 'RUNE_SMOKE_REAL_INTEGRATED_WORLD=YES' | tee -a client-smoke-status.txt
  # Controlled target: 1x1 gold block at eye level, 3.5 blocks in front.
  # A fixed platform avoids falling during the timed particle observation.
  send_chat '/time set noon'
  send_chat '/weather clear'
  # Real CI #41314620065: the fresh world spawned far from 0,0.
  # /fill and /setblock were rejected with "That position is not loaded",
  # leaving the Thaumometer pointing at empty water. Force-load FIRST,
  # then require an actual vanilla block predicate before any right-click.
  send_chat '/forceload add 0 0'
  sleep 5
  target_ready=0
  for attempt in 1 2 3; do
    send_chat '/fill -4 149 -4 4 149 10 minecraft:stone'
    send_chat '/setblock 0 151 4 minecraft:gold_block'
    send_chat '/execute if block 0 151 4 minecraft:gold_block run say RUNE_TARGET_GOLD_OK'
    if grep -Fq 'RUNE_TARGET_GOLD_OK' client-smoke/client.log; then
      target_ready=1
      break
    fi
    sleep 3
  done
  if (( target_ready != 1 )); then
    echo 'RUNE_WORLD_TARGET_NOT_CONFIRMED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'RUNE_REAL_WORLD_TARGET=PASS' | tee -a client-smoke-status.txt
  send_chat '/tp @p 0.5 150 0.5 0 0'
  item_ready=0
  for attempt in 1 2 3; do
    send_chat '/item replace entity @p hotbar.0 with thaumcraft:thaumometer 1'
    send_chat '/execute if items entity @p hotbar.0 thaumcraft:thaumometer run say RUNE_ITEM_READY'
    if grep -Fq 'RUNE_ITEM_READY' client-smoke/client.log; then
      item_ready=1
      break
    fi
  done
  if (( item_ready != 1 )); then
    echo 'RUNE_REAL_ITEM_NOT_CONFIRMED=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'RUNE_REAL_ITEM=PASS' | tee -a client-smoke-status.txt
  xdotool key --clearmodifiers 1
  sleep 3

  # Hide chat/HUD and the animated aura meter to keep the pixel comparison
  # focused on the real particle render, not on GUI animations.
  xdotool key --clearmodifiers F1
  sleep 2
  rune_capture() {
    local name="$1"
    import -display :99 -window root -silent "client-smoke/screenshots/$name.png"
    test -s "client-smoke/screenshots/$name.png"
    echo "REAL_CLIENT_RUNE_SCREEN=$name" | tee -a client-smoke-status.txt
  }
  rune_capture 70-rune-before-use
  for attempt in 1 2 3 4; do
    xdotool click 3
    sleep 0.3
  done
  sleep 0.5
  rune_capture 71-rune-during-use
  sleep 1
  rune_capture 72-rune-fading
  sleep 12
  rune_capture 73-rune-after-expiry

  # Independent opposite-face view: prove the real rune effect is visible
  # from both Z directions, not merely one favorable camera angle. The
  # target is the same block; the second viewpoint is on the platform.
  send_chat '/tp @p 0.5 150 8.5 180 0'
  send_chat '/execute if entity @p[x=0,y=150,z=8,dx=1,dy=2,dz=1] run say RUNE_OPPOSITE_POSITION_OK'
  if ! grep -Fq 'RUNE_OPPOSITE_POSITION_OK' client-smoke/client.log; then
    echo 'RUNE_OPPOSITE_REAL_TELEPORT_MISSING=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'RUNE_OPPOSITE_REAL_TELEPORT=PASS' | tee -a client-smoke-status.txt
  sleep 3
  rune_capture 74-rune-opposite-before-use
  first_view_created="$(grep -Fc 'TC_SMOKE_SCAN_RUNE_CREATED=YES' client-smoke/client.log || true)"
  for attempt in 1 2 3 4; do
    xdotool click 3
    sleep 0.3
  done
  sleep 0.5
  rune_capture 75-rune-opposite-during-use
  sleep 12
  rune_capture 76-rune-opposite-after-expiry
  second_view_created="$(grep -Fc 'TC_SMOKE_SCAN_RUNE_CREATED=YES' client-smoke/client.log || true)"
  if (( second_view_created < first_view_created + 10 )); then
    echo "RUNE_OPPOSITE_ACTUAL_PARTICLE_SPAWN_MISSING=before:$first_view_created after:$second_view_created" \
      | tee -a client-smoke-status.txt
    exit 1
  fi
  echo "RUNE_OPPOSITE_ACTUAL_PARTICLE_SPAWN=PASS count=$((second_view_created-first_view_created))" \
    | tee -a client-smoke-status.txt
  xdotool key --clearmodifiers F1

  # Real-client diagnostics are emitted from the actual ScanRuneParticle
  # constructor, camera-relative render transform, FacingCameraMode callback,
  # and tick expiration. The front-dot check proves the offset reaches the
  # camera-facing block face from BOTH viewpoints; pixel diff remains separate.
  python3 - <<'PY' | tee -a client-smoke-status.txt
import math
from pathlib import Path

log = Path("client-smoke/client.log").read_text(errors="replace")
created, rendered, expired = parse_scan_rune_log(log)
if len(created) < 80:
    raise SystemExit(f"RUNE_ACTUAL_CLIENT_SPAWN_MISSING count={len(created)}")
visible_rendered = []
for row in rendered:
    face, alpha, size, yaw, lx, ly, lz, wx, wy, wz, front = row
    values = tuple(float(v) for v in (alpha,size,yaw,lx,ly,lz,wx,wy,wz,front))
    if values[0] >= 0.25 and values[1] >= 0.25:
        visible_rendered.append((int(face), *values))
if len(visible_rendered) < 40:
    raise SystemExit(f"RUNE_ACTUAL_CLIENT_NONZERO_ALPHA_RENDER_MISSING count={len(visible_rendered)}")
if len(expired) < 80:
    raise SystemExit(f"RUNE_ACTUAL_CLIENT_EXPIRY_MISSING count={len(expired)}")
faces = set()
for face, life, x, y, z, local_x, local_y, local_z in created:
    face, life = int(face), int(life)
    x, y, z, local_x, local_y, local_z = map(
        float, (x,y,z,local_x,local_y,local_z))
    if life != 45 or abs(x-0.5)>0.0001 or abs(y-151.75)>0.0001 or abs(z-4.5)>0.0001:
        raise SystemExit(f"RUNE_BLOCK_ANCHOR_OR_DURATION_WRONG {face} {life} {x},{y},{z}")
    if not 0.0 <= local_x < 0.2 or not -0.3 <= local_y < 0.3 or abs(local_z+0.51)>0.0001:
        raise SystemExit(f"RUNE_LOCAL_CAMERA_OFFSET_WRONG {face} {local_x},{local_y},{local_z}")
    faces.add(face)
if faces != {0,1,2,3}:
    raise SystemExit(f"RUNE_FACE_VARIETY_MISSING faces={sorted(faces)}")

def normalized_yaw(value):
    return (value + 180.0) % 360.0 - 180.0

first_view = [r for r in visible_rendered if abs(normalized_yaw(r[3])) < 5.0]
opposite_view = [r for r in visible_rendered
                 if abs(abs(normalized_yaw(r[3]))-180.0) < 5.0]
if len(first_view) < 10 or len(opposite_view) < 10:
    raise SystemExit(
        f"RUNE_CAMERA_VIEWS_MISSING first={len(first_view)} opposite={len(opposite_view)}")
front_first = [r for r in first_view if r[10] >= 0.49]
front_opposite = [r for r in opposite_view if r[10] >= 0.49]
if len(front_first) < 2 or len(front_opposite) < 2:
    raise SystemExit(
        f"RUNE_CAMERA_RELATIVE_FRONT_OFFSET_MISSING first={len(front_first)} "
        f"opposite={len(front_opposite)}")
for label, group in (("first",front_first),("opposite",front_opposite)):
    for face,alpha,size,yaw,look_x,look_y,look_z,world_x,world_y,world_z,front_dot in group:
        if not 151.70 <= world_y <= 151.98:
            raise SystemExit(f"RUNE_RENDERED_WORLD_HEIGHT_WRONG {label} {world_y}")
        if abs(look_x) > 0.02 or abs(look_y) > 0.02 or abs(abs(look_z)-1.0) > 0.02:
            raise SystemExit(f"RUNE_SMOKE_CAMERA_NOT_HORIZONTAL {label} look={look_x},{look_y},{look_z}")
        expected_front_z = 4.5 - look_z * 0.51
        if abs(world_z-expected_front_z) > 0.02:
            raise SystemExit(
                f"RUNE_CAMERA_OFFSET_ON_WRONG_FACE {label} worldZ={world_z} expected={expected_front_z}")
if any(int(age) != 45 or int(life) != 45 for _, age, life, _ in expired):
    raise SystemExit("RUNE_ACTUAL_CLIENT_EXPIRY_TICK_WRONG")
if any(float(drift) > 0.0001 for _, _, _, drift in expired):
    raise SystemExit("RUNE_HORIZONTAL_DRIFT_FROM_VANILLA_CONSTRUCTOR")
print(f"RUNE_ACTUAL_CLIENT_SPAWN=PASS count={len(created)} faces={sorted(faces)}")
print(f"RUNE_ACTUAL_CLIENT_RENDER=PASS count={len(rendered)} "
      f"visibleAlpha={max(r[1] for r in visible_rendered):.3f}")
print(f"RUNE_CAMERA_RELATIVE_OFFSET=PASS views=2 frontFacing={len(front_first)}/{len(front_opposite)}")
print(f"RUNE_ACTUAL_CLIENT_EXPIRY=PASS count={len(expired)}")
PY

  # Compare the ACTUAL framebuffer around the fixed target. This is a
  # supplementary visual check; the render-callback proof above is separate.
  geometry="$(xdotool getwindowgeometry --shell "$WIN")"
  eval "$geometry"
  # The camera is fixed on the gold target block. Crop only its front face;
  # moving clouds above it must never count as rune-particle pixels.
  crop_x=$((X + WIDTH * 424 / 1000))
  crop_y=$((Y + HEIGHT * 396 / 1000))
  crop_width=$((WIDTH * 152 / 1000))
  crop_height=$((HEIGHT * 203 / 1000))
  echo "RUNE_VISUAL_CROP=block-face ${crop_width}x${crop_height}+${crop_x}+${crop_y}" \
    | tee -a client-smoke-status.txt
  for name in 70-rune-before-use 71-rune-during-use \
              74-rune-opposite-before-use 75-rune-opposite-during-use; do
    convert "client-smoke/screenshots/$name.png" \
      -crop "${crop_width}x${crop_height}+${crop_x}+${crop_y}" +repage \
      "client-smoke/screenshots/$name-target.png"
  done
  pixel_diff="$(compare -metric AE \
    client-smoke/screenshots/70-rune-before-use-target.png \
    client-smoke/screenshots/71-rune-during-use-target.png null: 2>&1 || true)"
  pixel_diff="${pixel_diff%% *}"
  if ! [[ "$pixel_diff" =~ ^[0-9]+$ ]] || (( pixel_diff < 40 )); then
    echo "RUNE_REAL_FRAMEBUFFER_DIFFERENCE_MISSING=$pixel_diff" | tee -a client-smoke-status.txt
    exit 1
  fi
  echo "RUNE_REAL_FRAMEBUFFER_DIFFERENCE=PASS pixels=$pixel_diff" | tee -a client-smoke-status.txt
  # A second independent before/after framebuffer pair from the opposite
  # side. Do not compare frames across camera angles: that would produce
  # a false positive from the changed terrain/background.
  opposite_diff="$(compare -metric AE \
    client-smoke/screenshots/74-rune-opposite-before-use-target.png \
    client-smoke/screenshots/75-rune-opposite-during-use-target.png null: 2>&1 || true)"
  opposite_diff="${opposite_diff%% *}"
  if ! [[ "$opposite_diff" =~ ^[0-9]+$ ]] || (( opposite_diff < 40 )); then
    echo "RUNE_OPPOSITE_REAL_FRAMEBUFFER_DIFFERENCE_MISSING=$opposite_diff" \
      | tee -a client-smoke-status.txt
    exit 1
  fi
  echo "RUNE_OPPOSITE_REAL_FRAMEBUFFER_DIFFERENCE=PASS pixels=$opposite_diff" \
    | tee -a client-smoke-status.txt
  if grep -Eiq 'Minecraft has crashed|Mod loading has failed|Exception in thread "Render thread"' \
    client-smoke/client.log; then
    echo 'RUNE_CLIENT_CRASH=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'RUNE_REAL_CLIENT_SMOKE=PASS' | tee -a client-smoke-status.txt
fi
'''
sys.stdout.write(head + body)
