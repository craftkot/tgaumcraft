#!/usr/bin/env python3
"""Generate a REAL Minecraft 1.21.1 scan-rune client smoke.

Reuses the existing tested creative/peaceful integrated-world bootstrap.
Requires real Thaumometer use, actual particle construction, render callback,
expiry, and framebuffer change. No shared client_smoke.sh modifications.
"""
from pathlib import Path
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
    send_chat '/fill -4 149 -4 4 149 7 minecraft:stone'
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
  xdotool key --clearmodifiers F1

  # Real-client diagnostics are emitted from the actual ScanRuneParticle
  # constructor, FacingCameraMode render callback, and tick expiration.
  # Merely sending a mouse click or having a green process exit is insufficient.
  python3 - <<'PY' | tee -a client-smoke-status.txt
import re
from pathlib import Path

log = Path("client-smoke/client.log").read_text(errors="replace")
created = re.findall(
    r"TC_SMOKE_SCAN_RUNE_CREATED=YES face=(\d) lifetime=(\d+) "
    r"x=(-?[\d.Ee]+) y=(-?[\d.Ee]+) z=(-?[\d.Ee]+)", log)
rendered = re.findall(r"TC_SMOKE_SCAN_RUNE_RENDERED=YES face=(\d)", log)
expired = re.findall(
    r"TC_SMOKE_SCAN_RUNE_EXPIRED=YES face=(\d) age=(\d+) lifetime=(\d+)", log)
if len(created) < 10:
    raise SystemExit(f"RUNE_ACTUAL_CLIENT_SPAWN_MISSING count={len(created)}")
if not rendered:
    raise SystemExit("RUNE_ACTUAL_CLIENT_RENDER_CALLBACK_MISSING")
if len(expired) < 10:
    raise SystemExit(f"RUNE_ACTUAL_CLIENT_EXPIRY_MISSING count={len(expired)}")
faces = set()
for face, life, x, y, z in created:
    face, life = int(face), int(life)
    x, y, z = float(x), float(y), float(z)
    if life != 45 or not 151.74 <= y <= 151.96:
        raise SystemExit(f"RUNE_BLOCK_DURATION_OR_HEIGHT_WRONG {face} {life} {y}")
    if face == 0:
        good = abs(z - 3.99) < 0.0001 and 0.19 <= x <= 0.81
    elif face == 1:
        good = abs(x + 0.01) < 0.0001 and 4.19 <= z <= 4.81
    elif face == 2:
        good = abs(z - 5.01) < 0.0001 and 0.19 <= x <= 0.81
    elif face == 3:
        good = abs(x - 1.01) < 0.0001 and 4.19 <= z <= 4.81
    else:
        good = False
    if not good:
        raise SystemExit(f"RUNE_FACE_PLACEMENT_WRONG face={face} xyz={x},{y},{z}")
    faces.add(face)
if len(faces) < 2:
    raise SystemExit(f"RUNE_FACE_VARIETY_MISSING faces={faces}")
if any(int(age) != 45 or int(life) != 45 for _, age, life in expired):
    raise SystemExit("RUNE_ACTUAL_CLIENT_EXPIRY_TICK_WRONG")
print(f"RUNE_ACTUAL_CLIENT_SPAWN=PASS count={len(created)} faces={sorted(faces)}")
print(f"RUNE_ACTUAL_CLIENT_RENDER=PASS count={len(rendered)}")
print(f"RUNE_ACTUAL_CLIENT_EXPIRY=PASS count={len(expired)}")
PY

  # Compare the ACTUAL framebuffer around the fixed target. This is a
  # supplementary visual check; the render-callback proof above is separate.
  geometry="$(xdotool getwindowgeometry --shell "$WIN")"
  eval "$geometry"
  crop_x=$((X + WIDTH / 2 - 150))
  crop_y=$((Y + HEIGHT / 2 - 150))
  for name in 70-rune-before-use 71-rune-during-use; do
    convert "client-smoke/screenshots/$name.png" \
      -crop "300x300+$crop_x+$crop_y" +repage \
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
  if grep -Eiq 'Minecraft has crashed|Mod loading has failed|Exception in thread "Render thread"' \
    client-smoke/client.log; then
    echo 'RUNE_CLIENT_CRASH=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'RUNE_REAL_CLIENT_SMOKE=PASS' | tee -a client-smoke-status.txt
fi
'''
sys.stdout.write(head + body)
