#!/usr/bin/env python3
"""Generate a REAL Minecraft 1.21.1 scan-rune client smoke.

Reuses the existing tested creative/peaceful integrated-world bootstrap.
Requires real Thaumometer use, actual particle construction, render callback,
expiry, and framebuffer change. No shared client_smoke.sh modifications.
"""
from pathlib import Path

from scan_rune_log_parser import parse_scan_rune_log, visible_rune_render_samples
import sys

source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")

world_create_y_anchor = "create_world_y=$((Y + HEIGHT * 93 / 100))"
world_create_y_replacement = """# In #43749075503 the root screenshot is 1280x800 while the window is
# 1024x768 at root offset (1,20). The Create New World button occupies root
# y=718..756. Adding Y to 96% produced root y=757, one pixel below it;
# 93.5% local plus Y produces root y=738, near the button centre.
create_world_y=$((Y + HEIGHT * 935 / 1000))"""
if source.count(world_create_y_anchor) != 1:
    raise SystemExit("Shared world-create coordinate changed; refusing unsafe scan-rune patch")
source = source.replace(world_create_y_anchor, world_create_y_replacement, 1)

world_create_log_anchor = 'echo "CREATE_NEW_WORLD_CLICK=${create_world_x},${create_world_y}" | tee -a client-smoke-status.txt'
world_create_log_replacement = 'echo "CREATE_NEW_WORLD_CLICK=${create_world_x},${create_world_y} LOCAL=$((create_world_x - X)),$((create_world_y - Y))" | tee -a client-smoke-status.txt'
if source.count(world_create_log_anchor) != 1:
    raise SystemExit("Shared world-create logging changed; refusing unsafe scan-rune patch")
source = source.replace(world_create_log_anchor, world_create_log_replacement, 1)

world_create_click_anchor = """xdotool mousemove "$create_world_x" "$create_world_y" click 1
# Wait only until the real integrated server announces player login, not an"""
world_create_click_replacement = """xdotool mousemove "$create_world_x" "$create_world_y" click 1
sleep 2
capture 04a-after-create-world-click
# Wait only until the real integrated server announces player login, not an"""
if source.count(world_create_click_anchor) != 1:
    raise SystemExit("Shared world-create click changed; refusing unsafe scan-rune patch")
source = source.replace(world_create_click_anchor, world_create_click_replacement, 1)
world_login_timeout_anchor = """if [ "$world_started" -ne 1 ]; then
  echo 'INTEGRATED_WORLD_PLAYER_LOGIN=NOT_CONFIRMED' | tee -a client-smoke-status.txt
  tail -80 client-smoke/client.log >&2
  exit 1
fi"""
world_login_timeout_replacement = """if [ "$world_started" -ne 1 ]; then
  echo 'INTEGRATED_WORLD_PLAYER_LOGIN=NOT_CONFIRMED' | tee -a client-smoke-status.txt
  echo 'RUNE_LOGIN_TIMEOUT_DIAGNOSTICS=BEGIN' | tee -a client-smoke-status.txt
  capture 04b-world-login-timeout || true
  {
    echo 'RUNE_LOGIN_TIMEOUT_WINDOW:'
    xdotool getwindowgeometry --shell "$WIN" 2>&1 || true
    echo "active=$(xdotool getactivewindow 2>/dev/null || true) focus=$(xdotool getwindowfocus 2>/dev/null || true)"
    echo 'RUNE_LOGIN_TIMEOUT_PROCESSES:'
    ps -eo pid,ppid,stat,pcpu,pmem,etime,args | grep -E '[j]ava|[G]radle|[M]inecraft' || true
    echo 'RUNE_LOGIN_TIMEOUT_JAVA_PROCESSES:'
    jcmd -l 2>&1 || true
  } | tee -a client-smoke-status.txt
  mkdir -p client-smoke/thread-dumps
  while read -r pid main_class; do
    [[ "$pid" =~ ^[0-9]+$ ]] || continue
    [[ "$main_class" == jdk.jcmd/* ]] && continue
    timeout 12s jcmd "$pid" Thread.print -l > "client-smoke/thread-dumps/$pid.txt" 2>&1 || echo "RUNE_THREAD_DUMP_CAPTURE_FAILED pid=$pid class=$main_class" | tee -a client-smoke-status.txt
  done < <(jcmd -l 2>/dev/null)
  while IFS= read -r world_log; do
    echo "RUNE_LOGIN_TIMEOUT_LATEST_LOG=$world_log" | tee -a client-smoke-status.txt
    tail -200 "$world_log" | tee -a client-smoke-status.txt
  done < <(find . -type f -path '*/logs/latest.log' -print 2>/dev/null | sort)
  level_dat_count="$(find run/saves -maxdepth 3 -type f -name level.dat -print 2>/dev/null | wc -l)"
  echo "RUNE_LOGIN_TIMEOUT_LEVEL_DAT_COUNT=$level_dat_count" | tee -a client-smoke-status.txt
  if (( level_dat_count == 1 )); then
    python3 tools/assert_client_world_commands.py run/saves > client-smoke/world-level-dat-check.txt 2>&1 || echo 'RUNE_LOGIN_TIMEOUT_LEVEL_DAT_ASSERT=FAIL' | tee -a client-smoke-status.txt
    cat client-smoke/world-level-dat-check.txt | tee -a client-smoke-status.txt
  fi
  tail -120 client-smoke/client.log >&2
  echo 'RUNE_LOGIN_TIMEOUT_DIAGNOSTICS=END' | tee -a client-smoke-status.txt
  exit 1
fi"""
if source.count(world_login_timeout_anchor) != 1:
    raise SystemExit("Shared player-login timeout changed; refusing unsafe scan-rune diagnostics")
source = source.replace(world_login_timeout_anchor, world_login_timeout_replacement, 1)

send_chat_anchor = """  send_chat() {
    xdotool key --clearmodifiers t
    sleep 1
    xdotool type --clearmodifiers --delay 24 "$1"
    xdotool key --clearmodifiers Return
    sleep 4
  }"""
send_chat_replacement = """  send_chat() {
    local raw_command="$1"
    if [[ "$raw_command" != /* ]]; then
      echo "RUNE_CHAT_COMMAND_MISSING_SLASH=YES command=$raw_command" | tee -a client-smoke-status.txt
      exit 1
    fi
    local command_body="${raw_command#/}"
    local active_window focused_window target_pid
    xdotool windowactivate --sync "$WIN"
    xdotool windowfocus --sync "$WIN"
    active_window="$(xdotool getactivewindow)"
    focused_window="$(xdotool getwindowfocus)"
    target_pid="$(xdotool getwindowpid "$WIN" 2>/dev/null || true)"
    echo "RUNE_CHAT_INPUT_TARGET=$WIN active=$active_window focus=$focused_window pid=$target_pid" | tee -a client-smoke-status.txt
    if [[ "$active_window" != "$WIN" || "$focused_window" != "$WIN" || -z "$target_pid" ]]; then
      echo "RUNE_CHAT_INPUT_FOCUS=FAIL command=$raw_command" | tee -a client-smoke-status.txt
      exit 1
    fi
    xdotool key --clearmodifiers slash
    sleep 0.5
    xdotool type --clearmodifiers --delay 24 "$command_body"
    sleep 0.5
    echo "RUNE_CHAT_COMMAND_TEXT=$raw_command" | tee -a client-smoke-status.txt
    xdotool key --clearmodifiers Return
    echo "RUNE_CHAT_RETURN_SENT=YES" | tee -a client-smoke-status.txt
    sleep 4
    local post_active_window post_focused_window
    post_active_window="$(xdotool getactivewindow)"
    post_focused_window="$(xdotool getwindowfocus)"
    if [[ "$post_active_window" != "$WIN" || "$post_focused_window" != "$WIN" ]]; then
      echo "RUNE_CHAT_POST_SUBMIT_FOCUS=FAIL command=$raw_command active=$post_active_window focus=$post_focused_window" | tee -a client-smoke-status.txt
      exit 1
    fi
    echo "RUNE_CHAT_POST_SUBMIT_FOCUS=PASS" | tee -a client-smoke-status.txt
  }"""
if source.count(send_chat_anchor) != 1:
    raise SystemExit("Shared chat sender changed; refusing unsafe scan-rune replacement")
source = source.replace(send_chat_anchor, send_chat_replacement, 1)
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
  send_chat '/forceload add -4 -4 4 10'
  sleep 5
  target_ready=0
  for attempt in 1 2 3; do
    send_chat '/fill -4 149 -4 4 149 10 minecraft:stone'
    # The previous smoke left generated trees/foliage above the platform. The
    # first view then scanned a nearby obstruction at z=0 and the opposite
    # view scanned z=7, so neither proved the intended gold target at z=4.
    # Clear both sight lines while preserving the floor, then place the target.
    send_chat '/fill -4 150 -4 4 155 10 minecraft:air'
    send_chat '/setblock 0 151 4 minecraft:gold_block'
    send_chat '/execute if block 0 151 4 minecraft:gold_block run say RUNE_TARGET_GOLD_OK'
    send_chat '/execute if block 0 151 0 minecraft:air run say RUNE_ORIGIN_VIEW_CLEAR'
    send_chat '/execute if block 0 151 7 minecraft:air run say RUNE_OPPOSITE_VIEW_CLEAR'
    if grep -Fq 'RUNE_TARGET_GOLD_OK' client-smoke/client.log && grep -Fq 'RUNE_ORIGIN_VIEW_CLEAR' client-smoke/client.log && grep -Fq 'RUNE_OPPOSITE_VIEW_CLEAR' client-smoke/client.log; then
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
import sys
from pathlib import Path
sys.path.insert(0, str(Path.cwd() / "tools"))
from scan_rune_log_parser import parse_scan_rune_log, visible_rune_render_samples

log = Path("client-smoke/client.log").read_text(errors="replace")
created, rendered, expired = parse_scan_rune_log(log)
if len(created) < 80:
    raise SystemExit(f"RUNE_ACTUAL_CLIENT_SPAWN_MISSING count={len(created)}")
visible_rendered = visible_rune_render_samples(rendered)
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
front_first = [r for r in first_view if r[10] >= 0.49 and r[11] >= 0.49]
front_opposite = [r for r in opposite_view if r[10] >= 0.49 and r[11] >= 0.49]
if len(front_first) < 2 or len(front_opposite) < 2:
    raise SystemExit(
        f"RUNE_CAMERA_RELATIVE_FRONT_OFFSET_MISSING first={len(front_first)} "
        f"opposite={len(front_opposite)}")
for label, group in (("first",front_first),("opposite",front_opposite)):
    for face,alpha,size,yaw,look_x,look_y,look_z,world_x,world_y,world_z,front_dot,quad_facing_dot in group:
        if not 151.70 <= world_y <= 151.98:
            raise SystemExit(f"RUNE_RENDERED_WORLD_HEIGHT_WRONG {label} {world_y}")
        if abs(look_x) > 0.02 or abs(look_y) > 0.02 or abs(abs(look_z)-1.0) > 0.02:
            raise SystemExit(f"RUNE_SMOKE_CAMERA_NOT_HORIZONTAL {label} look={look_x},{look_y},{look_z}")
        expected_front_z = 4.5 - look_z * 0.51
        if abs(world_z-expected_front_z) > 0.02:
            raise SystemExit(
                f"RUNE_CAMERA_OFFSET_ON_WRONG_FACE {label} worldZ={world_z} expected={expected_front_z}")
        if quad_facing_dot < 0.49:
            raise SystemExit(f"RUNE_CAMERA_RELATIVE_QUAD_NOT_FACING_CAMERA {label} dot={quad_facing_dot}")
if any(int(age) != 45 or int(life) != 45 for _, age, life, _ in expired):
    raise SystemExit("RUNE_ACTUAL_CLIENT_EXPIRY_TICK_WRONG")
if any(float(drift) > 0.0001 for _, _, _, drift in expired):
    raise SystemExit("RUNE_HORIZONTAL_DRIFT_FROM_VANILLA_CONSTRUCTOR")
print(f"RUNE_ACTUAL_CLIENT_SPAWN=PASS count={len(created)} faces={sorted(faces)}")
print(f"RUNE_ACTUAL_CLIENT_RENDER=PASS count={len(rendered)} "
      f"visibleAlpha={max(r[1] for r in visible_rendered):.3f}")
print(f"RUNE_CAMERA_RELATIVE_OFFSET=PASS views=2 frontFacing={len(front_first)}/{len(front_opposite)} quadFacing=PASS")
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
