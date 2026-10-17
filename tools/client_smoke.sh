#!/usr/bin/env bash
set -Eeuo pipefail

# REAL Minecraft client under virtual X11/Mesa. Screenshots are of the
# executing game, not examples downloaded from the web.
mkdir -p client-smoke/screenshots run
# Fix the physical-to-logical mouse transform. The old smoke assumed the
# entire 1280x800 Xvfb desktop was the game viewport, yet forcibly resized
# Minecraft to 1024x768; it could click OUTSIDE the research table.
# Scale=2 keeps RU text readable and lets relative GUI coordinates be exact.
printf 'guiScale:2\n' > run/options.txt
source tools/minecraft_gui_coordinates.sh
: > client-smoke-status.txt
export DISPLAY=:99
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_LOADER_DRIVER_OVERRIDE=llvmpipe
export ALSOFT_DRIVERS=null
# Game-server probes emit tagged lines only in this opt-in, CI-only process.
export THAUMCRAFT_SMOKE=1
export XDG_RUNTIME_DIR=/tmp/thaumcraft-client-runtime
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"
CLIENT_PID=''
XVFB_PID=''
WM_PID=''
VIDEO_PID=''

cleanup() {
  echo "Client probe cleanup"
  # FFmpeg background jobs can inherit SIGINT ignored from bash; use
  # SIGTERM, which FFmpeg handles by writing the MP4 trailer.
  if [ -n "$VIDEO_PID" ] && kill -0 "$VIDEO_PID" 2>/dev/null; then
    kill -TERM "$VIDEO_PID" 2>/dev/null || true
    wait "$VIDEO_PID" 2>/dev/null || true
  fi
  if [ -n "$CLIENT_PID" ]; then
    pkill -TERM -P "$CLIENT_PID" 2>/dev/null || true
    kill "$CLIENT_PID" 2>/dev/null || true
  fi
  if [ -n "$WM_PID" ]; then kill "$WM_PID" 2>/dev/null || true; fi
  if [ -n "$XVFB_PID" ]; then kill "$XVFB_PID" 2>/dev/null || true; fi
}
trap cleanup EXIT

Xvfb :99 -screen 0 1280x800x24 +extension GLX +render -noreset > client-smoke/xvfb.log 2>&1 &
XVFB_PID=$!
sleep 3
xdpyinfo -display :99 > client-smoke/display.txt
openbox > client-smoke/wm.log 2>&1 &
WM_PID=$!
sleep 2
glxinfo -B | tee client-smoke/renderer.txt
grep -iq 'OpenGL version string' client-smoke/renderer.txt

echo 'Starting the actual NeoForge Minecraft 1.21.1 client'
gradle runClient --no-daemon --console=plain > client-smoke/client.log 2>&1 &
CLIENT_PID=$!
WIN=''
for i in $(seq 1 210); do
  WIN=$(xdotool search --onlyvisible --name 'Minecraft' 2>/dev/null | tail -n 1 || true)
  if [ -n "$WIN" ]; then
    echo "CLIENT_WINDOW=YES after $i seconds" | tee -a client-smoke-status.txt
    break
  fi
  if grep -Eiq 'Mod loading has failed|Failed to create window|GLFW error|Exception in thread "Render thread"' client-smoke/client.log; then
    echo 'CLIENT_STARTUP_ERROR=YES' | tee -a client-smoke-status.txt
    tail -100 client-smoke/client.log
    exit 1
  fi
  if ! kill -0 "$CLIENT_PID" 2>/dev/null; then
    echo 'CLIENT_EXITED_BEFORE_WINDOW=YES' | tee -a client-smoke-status.txt
    tail -100 client-smoke/client.log
    exit 1
  fi
  sleep 1
done
if [ -z "$WIN" ]; then
  echo 'CLIENT_WINDOW=NO' | tee -a client-smoke-status.txt
  tail -100 client-smoke/client.log
  exit 1
fi
xdotool windowactivate --sync "$WIN" || true
xdotool windowmove "$WIN" 0 0 || true
xdotool windowsize "$WIN" 1024 768 || true
xdotool mousemove 20 20
sleep 35

# The ResearchTableScreen is exactly 256x256 GUI pixels. Translate
# its local button coordinates through the REAL live game-window geometry,
# never the larger virtual desktop or an unverified 1280x800 assumption.
click_research_gui() {
  local local_x=$1 local_y=$2 geometry sx sy
  geometry="$(xdotool getwindowgeometry --shell "$WIN")"
  eval "$geometry"
  read -r sx sy < <(minecraft_gui_point "$X" "$Y" "$WIDTH" "$HEIGHT" \
      2 256 256 "$local_x" "$local_y")
  echo "RESEARCH_GUI_ACTUAL_CLICK=${local_x},${local_y}->${sx},${sy} WINDOW=${WIDTH}x${HEIGHT}" \
      | tee -a client-smoke-status.txt
  xdotool mousemove "$sx" "$sy" click 1
}

# BETA26 research book atlas is 512x356 (NOT the 256x256 ResearchTable GUI).
# FirstStepsBookScreen bounds: x+183..328, y+311..331; click the exact
# centre in unscaled GUI coordinates, translated from the live window.
# Historical CI smoke mistakenly used (512,527), which was in the TEXT area,
# so the stage-advance server packet was never exercised.
click_firststeps_advance() {
  local geometry sx sy
  geometry="$(xdotool getwindowgeometry --shell "$WIN")"
  eval "$geometry"
  read -r sx sy < <(minecraft_gui_point "$X" "$Y" "$WIDTH" "$HEIGHT" \
      2 512 356 255 321)
  echo "FIRSTSTEPS_BOOK_ADVANCE_CLICK=${sx},${sy} WINDOW=${WIDTH}x${HEIGHT}" \
      | tee -a client-smoke-status.txt
  xdotool mousemove "$sx" "$sy" click 1
}

assert_smoke_event() {
  local key=$1
  if ! grep -Fq "TC_SMOKE_${key}=YES" client-smoke/client.log; then
    echo "REAL_GAMEPLAY_MILESTONE_MISSING=${key}" | tee -a client-smoke-status.txt
    echo "Most recent game log:" >&2
    tail -80 client-smoke/client.log >&2
    exit 1
  fi
  echo "REAL_GAMEPLAY_MILESTONE_CONFIRMED=${key}" | tee -a client-smoke-status.txt
}

capture() {
  name="$1"
  import -display :99 -window root -silent "client-smoke/screenshots/$name.png"
  test -s "client-smoke/screenshots/$name.png"
  identify "client-smoke/screenshots/$name.png"
  convert "client-smoke/screenshots/$name.png" -resize 512x320 -colors 64 \
    "PNG8:client-smoke/screenshots/$name-thumb.png"
  echo -n "REAL_CLIENT_SCREEN_$name="
  base64 -w0 "client-smoke/screenshots/$name-thumb.png"
  echo
}

# CircleCI machine job #11 demonstrated that the first screen was already
# the TITLE SCREEN, not an Accessibility/Narrator dialog. The old two
# blind clicks at root (512,716) and (512,308) clicked MULTIPLAYER, opening
# "Caution: Third-Party Online Play" and never creating a local world.
# Compute the center of the actual title-screen Singleplayer button
# using the REAL Minecraft window, not the (larger) Xvfb desktop.
# The #16 evidence proves the title GUI's Singleplayer button occupies
# root pixels around x=512, y=263..295 (thumbnail: x≈160,y≈85).
# Previous x=513,y=296 landed on its LOWER EDGE, so the screen did not
# change; next (512,433) accidentally opened the Mods screen. With this
# window's Y≈20 and HEIGHT=768, 33% is safely INSIDE the button, not
# on its lower border. Record the exact click for further real-run audits.
capture 01-first-launch
echo 'REAL_WINDOW_SCREENSHOT=YES' | tee -a client-smoke-status.txt
xdotool key --clearmodifiers Escape || true
sleep 2
title_geometry="$(xdotool getwindowgeometry --shell "$WIN")"
eval "$title_geometry"
title_click_x=$((X + WIDTH / 2))
title_click_y=$((Y + HEIGHT * 33 / 100))
echo "TITLE_SINGLEPLAYER_CLICK=${title_click_x},${title_click_y} WINDOW=${WIDTH}x${HEIGHT} ROOT=${X},${Y}" \
  | tee -a client-smoke-status.txt
xdotool mousemove "$title_click_x" "$title_click_y" click 1
sleep 8
capture 02-after-singleplayer
# CircleCI #24 generated a real world but with "Allow Commands: OFF".
# The obsolete root click (512,433) missed the actual game-tab toggle;
# all following /give, /setblock and /tp were denied while the script still
# emitted misleading CLIENT_SENT_* success-like status. The source screenshot
# at 1024x768 places the toggle CENTER at window-local x=512, y≈311.
# Convert to live-window pixels. The NEXT screenshot must show ON and the
# world-level NBT probe below MUST independently reject OFF.
world_geometry="$(xdotool getwindowgeometry --shell "$WIN")"
eval "$world_geometry"
world_command_x=$((X + WIDTH / 2))
world_command_y=$((Y + HEIGHT * 405 / 1000))
echo "WORLD_ALLOW_COMMANDS_CLICK=${world_command_x},${world_command_y} WINDOW=${WIDTH}x${HEIGHT} ROOT=${X},${Y}" \
  | tee -a client-smoke-status.txt
xdotool mousemove "$world_command_x" "$world_command_y" click 1
sleep 3
capture 03-world-create-commands
capture 04-before-world-create

# Optional milestone video; ordinary client regressions capture PNGs only.
if [ "${CAPTURE_VIDEO:-0}" = "1" ]; then
  # Record the actual Minecraft/Xvfb game, not a generated slideshow.
# 960x600 @ 12 fps keeps the video small enough to attach in chat.
# Start before world generation so the recording includes the real
# loading screen, spawn, creative commands and Crucible interactions.
ffmpeg -nostdin -hide_banner -loglevel warning -y \
  -f x11grab -draw_mouse 1 -framerate 12 -video_size 1280x800 -i :99.0 \
  -vf "scale=960:600" -c:v libx264 -preset ultrafast -crf 31 \
  -pix_fmt yuv420p -movflags +faststart -an \
  client-smoke/thaumcraft-live-world.mp4 \
  > client-smoke/ffmpeg.log 2>&1 &
VIDEO_PID=$!
sleep 3
kill -0 "$VIDEO_PID"
echo 'CLIENT_SCREEN_RECORDING_STARTED=YES' | tee -a client-smoke-status.txt
fi

# Centre of the actual 1.21 Create New World button (left bottom of
# the 1024x768 Minecraft WINDOW, not the 1280x800 Xvfb desktop).
world_geometry="$(xdotool getwindowgeometry --shell "$WIN")"
eval "$world_geometry"
create_world_x=$((X + WIDTH * 34 / 100))
create_world_y=$((Y + HEIGHT * 93 / 100))
echo "CREATE_NEW_WORLD_CLICK=${create_world_x},${create_world_y}" | tee -a client-smoke-status.txt
xdotool mousemove "$create_world_x" "$create_world_y" click 1
sleep 110
capture 05-after-world-start
# Hard gate: starting an integrated server does NOT prove that the client
# received operator commands. Audit the real world level.dat before issuing
# ANY scripted cheats. This is a genuine vanilla 1.21 game setting persisted
# by the game itself, not a status echo from our test.
python3 tools/assert_client_world_commands.py run/saves | tee -a client-smoke-status.txt
if grep -Eiq 'Starting integrated minecraft server|Preparing start region|Preparing spawn area|Joining world' client-smoke/client.log; then
  echo 'INTEGRATED_WORLD_START_LOG=YES' | tee -a client-smoke-status.txt
  # Actual interactive world commands, typed through the real Minecraft
  # client chat; each step has a screenshot and may be checked independently.
  send_chat() {
    xdotool key --clearmodifiers t
    sleep 1
    xdotool type --clearmodifiers --delay 24 "$1"
    xdotool key --clearmodifiers Return
    sleep 4
  }
  send_chat '/gamemode creative'
  send_chat '/give @p thaumcraft:crucible'
  capture 06-crucible-in-hand

  # First attempt genuine first-person RIGHT CLICK placement of the held
  # crucible at the original player's crosshair (may miss on snow terrain).
  xdotool click 3
  sleep 3
  capture 07-right-click-place-attempt

  # A fixed test platform high above all generated terrain/trees avoids
  # random leaves obscuring the Crucible in server-run screenshots.
  # Chunk (0,0) is forcibly loaded before changing distant blocks.
  send_chat '/forceload add 0 0'
  send_chat '/fill 0 148 0 4 148 6 minecraft:stone'
  send_chat '/tp @p 0.5 149 0.5 0 27'
  send_chat '/setblock 0 149 3 thaumcraft:crucible'
  sleep 5
  capture 08-actual-crucible-block
  # Client-side regression for the original six-element BETA26 Arcane
  # Workbench model and the registered BlockItem. Old real Minecraft logs
  # reported missing thaumcraft:arcane_workbench# and its item model.
  send_chat '/fill 10 148 5 13 148 8 minecraft:stone'
  send_chat '/setblock 12 149 7 thaumcraft:arcane_workbench'
  send_chat '/gamemode spectator'
  send_chat '/tp @p 12.5 151.5 3.5 0 32'
  sleep 3
  capture 08a-source-arcane-workbench-world-model
  send_chat '/gamemode creative'
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:arcane_workbench 1'
  xdotool key --clearmodifiers 1
  sleep 3
  capture 08b-source-arcane-workbench-item-model

  # Source BETA26 scanner: true 56-face OBJ with animated glass screen.
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:thaumometer 1'
  xdotool key --clearmodifiers 1
  sleep 3
  capture 08c-original-scanner-obj-in-hand
  # Salis Mundus must show the 16-frame original animated texture.
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:salis_mundus 1'
  xdotool key --clearmodifiers 1
  sleep 3
  capture 08d-original-animated-salis-mundus-in-hand

  # First real FIRSTSTEPS/ScanGeneric integration: equip the actual
  # Thaumometer, ray-trace an ORIGINAL diamond-ore target in the world,
  # right-click twice, and save the action-bar feedback from both scans.
  # The second click must not award the same keyed discovery twice.
  send_chat '/forceload add 0 0'
  send_chat '/fill -1 149 0 1 149 5 minecraft:stone'
  send_chat '/setblock 0 151 3 minecraft:diamond_ore'
  send_chat '/gamemode creative'
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:thaumometer 1'
  send_chat '/tp @p 0.5 150 0.5 0 0'
  xdotool key --clearmodifiers 1
  sleep 3
  capture 08e-firststeps-diamond-ore-before-scan
  xdotool click 3
  sleep 1
  capture 08f-firststeps-diamond-ore-first-actual-scan
  sleep 2
  xdotool click 3
  sleep 1
  capture 08g-firststeps-diamond-ore-repeat-scan
  echo 'CLIENT_SENT_FIRSTSTEPS_REAL_SCAN_REGRESSION=YES' | tee -a client-smoke-status.txt

  # FIRSTSTEPS integration fixture, no NBT research grants: physically pick
  # up a dropped Crystal Essence, sleep in a genuinely placed vanilla bed
  # (triggers PlayerWakeUpEvent), then use Salis Mundus on bookshelf.
  # The original two hidden flags must be granted by pickup/wake listeners.
  send_chat '/difficulty peaceful'
  send_chat '/fill 1 149 5 3 149 12 minecraft:stone'
  send_chat '/tp @p 2.5 150 5.5 0 45'
  send_chat '/summon minecraft:item 2.5 150 5.5 {Item:{id:"thaumcraft:crystal_essence",count:1}}'
  sleep 4
  capture 08h-firststeps-real-world-crystal-pickup
  assert_smoke_event CRYSTAL_PICKUP
  send_chat '/item replace entity @p hotbar.0 with minecraft:red_bed 1'
  xdotool key --clearmodifiers 1
  xdotool click 3
  sleep 2
  capture 08i-firststeps-real-bed-placed
  send_chat '/clear @p minecraft:red_bed'
  send_chat '/time set night'
  # CircleCI #32 confirmed the bed was physically placed, but the old
  # 45-degree downward look intersected the STONE below its foot, not the
  # bed collision. The (2.5,150,5.5) eye is ~1.62m above its feet: at
  # ~2.5m distance a 22-degree look targets the bed mattress, not the
  # platform. Reorient AFTER placing, with the camera aimed at the bed.
  send_chat '/tp @p 2.5 150 5.5 0 22'
  sleep 2
  capture 08i1-firststeps-bed-targeted-at-night
  xdotool click 3
  sleep 9
  capture 08j-firststeps-post-sleep-wake
  assert_smoke_event WAKE_UNLOCK
  send_chat '/setblock 2 151 12 minecraft:bookshelf'
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:salis_mundus 1'
  send_chat '/tp @p 2.5 150 8.5 0 0'
  xdotool key --clearmodifiers 1
  sleep 2
  capture 08k-firststeps-real-bookshelf-before-salis
  xdotool click 3
  sleep 4
  send_chat '/execute if block 2 151 12 minecraft:air run say FIRSTSTEPS_REAL_DREAM_SALIS_BOOKSHELF_OK'
  capture 08l-firststeps-real-bookshelf-after-salis
  assert_smoke_event BOOKSHELF_SALIS_USED
  assert_smoke_event BOOKSHELF_TRANSFORMED
  # A /give or /item replace does not invoke ItemEntityPickupEvent.Post and
  # therefore does not grant the server !gotthaumonomicon marker. The original
  # Salis swapper produces a REAL dropped book at the shelf position: look for
  # that item and move the player onto it to fire the actual pickup listener.
  send_chat '/execute if entity @e[type=minecraft:item,x=2,y=151,z=12,distance=..3,nbt={Item:{id:"thaumcraft:thaumonomicon"}}] run say FIRSTSTEPS_REAL_BOOK_DROPPED_OK'
  send_chat '/tp @p 2.5 151 12.5 0 0'
  sleep 4
  capture 08l1-firststeps-real-thaumonomicon-pickup
  assert_smoke_event BOOK_PICKUP
  echo 'CLIENT_SENT_FIRSTSTEPS_PICKUP_WAKE_SALIS_CHAIN=YES' | tee -a client-smoke-status.txt

  # Actually using the book opens the playable FIRSTSTEPS chapter. Using
  # /item replace only equips it; the critical !gotthaumonomicon permission
  # must have been obtained from the prior real-world item pickup.
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:thaumonomicon 1'
  xdotool key --clearmodifiers 1
  xdotool click 3
  sleep 3
  capture 08m-firststeps-original-researchbook-open
  click_firststeps_advance
  sleep 4
  capture 08n-firststeps-researchbook-server-synced-progression
  assert_smoke_event FIRSTSTEPS_STAGE_ADVANCED
  xdotool key --clearmodifiers Escape
  echo 'CLIENT_SENT_FIRSTSTEPS_THAUMONOMICON_GUI_TEST=YES' | tee -a client-smoke-status.txt



  echo 'CLIENT_SENT_ORIGINAL_SCANNER_AND_SALIS_RENDER_TEST=YES' | tee -a client-smoke-status.txt
  send_chat '/tp @p 0.5 149 0.5 0 27'
  echo 'CLIENT_SENT_ARCANE_WORKBENCH_RENDER_TEST=YES' | tee -a client-smoke-status.txt

  # REAL RESEARCH TABLE PLAYTHROUGH.  The original gui_research_table.png
  # must open as a Minecraft inventory, not merely parse server classes.
  # Fill the two ORIGINAL research-table container slots using integrated
  # server commands; clicks below are physical X11 mouse events on the UI.
  send_chat '/fill 5 148 1 8 148 6 minecraft:stone'
  send_chat '/setblock 6 149 5 thaumcraft:research_table[facing=north]'
  send_chat '/item replace block 6 149 5 container.0 with thaumcraft:scribing_tools 1'
  send_chat '/item replace block 6 149 5 container.1 with minecraft:paper 32'
  assert_smoke_event TABLE_SCRIBING_TOOLS_INSERTED
  assert_smoke_event TABLE_PAPER_INSERTED
  send_chat '/clear @p'
  send_chat '/tp @p 6.5 149 2.5 0 24'
  sleep 2
  capture 37-original-research-table-before-opening
  xdotool click 3
  sleep 3
  capture 38-original-research-table-live-gui-with-supplies
  # Original GUI pixels, not Xvfb-root hard-coded coordinates.
  # Center of BETA26 create button at (116,22) with expanded 132x18 hitbox.
  click_research_gui 140 31
  sleep 4
  capture 39-original-research-table-create-theory
  assert_smoke_event THEORY_CREATED
  # Physical draw click at (116,121). Real server must debit one paper.
  click_research_gui 140 130
  sleep 4
  capture 40-original-research-table-draw-two-cards
  # Choice #0 occupies (77..133,97..119); click safely inside it.
  click_research_gui 102 108
  sleep 3
  capture 41-original-research-table-activate-first-offer
  xdotool key --clearmodifiers Escape
  # Independent saved-block-entity evidence following physical clicks,
  # not a self-asserted PASS marker: screenshots #42-43 must show actual
  # note, original cardChoices/ink/paper, and the real GUI after re-open.
  send_chat '/data get block 6 149 5 note'
  capture 42-research-table-server-saved-nbt
  xdotool click 3
  sleep 3
  capture 43-research-table-reopened-from-world
  xdotool key --clearmodifiers Escape
  echo 'CLIENT_SENT_REAL_THEORYCRAFT_TABLE_INTERACTIONS=YES' | tee -a client-smoke-status.txt


  echo 'CLIENT_SENT_SETBLOCK=YES' | tee -a client-smoke-status.txt

  # Freeze the camera using vanilla spectator mode. A precisely identical
  # elevated viewpoint can now be captured empty and full WITHOUT gravity.
  send_chat '/gamemode spectator'
  send_chat '/tp @p 0.5 152 0.5 0 52'
  sleep 3
  capture 08-empty-overhead-crucible
  send_chat '/gamemode creative'
  send_chat '/tp @p 0.5 149 0.5 0 27'

  # Genuine client item interaction: vanilla water bucket aimed at the
  # same known-coordinate Crucible and checked by the integrated server.
  send_chat '/give @p minecraft:water_bucket'
  xdotool key --clearmodifiers 2
  sleep 2
  xdotool click 3
  sleep 5
  capture 09-water-bucket-on-crucible

  # Absolutely addressed /data avoids player-position drift; the
  # integrated server must expose Water:1000, not a simulated counter.
  send_chat '/data get block 0 149 3'
  sleep 2
  capture 10-crucible-blockentity-nbt

  # Exact same stable top view as empty snapshot, now with filled water.
  send_chat '/gamemode spectator'
  send_chat '/tp @p 0.5 152 0.5 0 52'
  sleep 4
  capture 11-overhead-filled-crucible
  send_chat '/data get block 0 149 3'
  sleep 2
  capture 12-overhead-fluid-and-nbt

  # Verify the original magic heat source in actual Minecraft, not a
  # theoretical unit test: replace the STONE below this exact Crucible
  # with yellow Nitor, wait >=200 client/server ticks and inspect Heat.
  send_chat '/setblock 0 148 3 thaumcraft:nitor_yellow'
  sleep 13
  capture 13-nitor-heating-crucible
  send_chat '/data get block 0 149 3'
  sleep 2
  capture 14-crucible-heat-after-nitor

  # Original BETA26 color block registry smoke: replace yellow heater
  # with a blue heater on the SAME water-filled crucible platform.
  # Screenshot proves the blue registry/block and client particles render;
  # previous Heat:200 screenshot proves yellow heat independently.
  send_chat '/setblock 0 148 3 thaumcraft:nitor_blue'
  sleep 5
  capture 15-blue-nitor-variant
  # Alumentum is a genuine thrown entity, not a decorative material.
  # This takes place AFTER the earlier 15 fixed Crucible/Nitor screenshots.
  # Target a low-blast-resistance GLASS wall; 1.1-strength original
  # impact should break it rather than relying on stone destruction RNG.
  send_chat '/fill -2 149 8 2 156 8 minecraft:glass'
  send_chat '/gamemode creative'
  send_chat '/tp @p 0.5 150 0.5 0 5'
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:alumentum 16'
  xdotool key --clearmodifiers 1
  sleep 2
  capture 16-alumentum-original-item
  xdotool click 3
  sleep 3
  capture 17-alumentum-after-right-click
  # Real integrated-server block query, not rendered guesswork:
  # conditional text appears in screenshot 18 ONLY if glass was destroyed.
  send_chat '/execute if block 0 151 8 minecraft:air run say ALUMENTUM_GLASS_DESTROYED'
  capture 18-alumentum-explosion-block-query
  echo 'CLIENT_SENT_ALUMENTUM_THROW=YES' | tee -a client-smoke-status.txt
  # Original BETA26 Arcane Pedestal is a real world BlockEntity with one
  # item slot and an actual rotating item renderer (no fake overlay).
  send_chat '/setblock 2 149 2 thaumcraft:pedestal_arcane'
  send_chat '/setblock 3 149 2 thaumcraft:pedestal_ancient'
  send_chat '/setblock 4 149 2 thaumcraft:pedestal_eldritch'
  send_chat '/data merge block 2 149 2 {Items:[{Slot:0b,id:"minecraft:diamond",count:1}]}'
  send_chat '/data get block 2 149 2 Items'
  sleep 3
  capture 19-three-original-infusion-pedestals
  echo 'CLIENT_SENT_PEDESTAL_WORLD_TEST=YES' | tee -a client-smoke-status.txt
  # Three real BETA26 BlockPillar registry entries and orientations.
  send_chat '/setblock 1 149 5 thaumcraft:pillar_arcane[facing=north]'
  send_chat '/setblock 2 149 5 thaumcraft:pillar_ancient[facing=east]'
  send_chat '/setblock 3 149 5 thaumcraft:pillar_eldritch[facing=south]'
  send_chat '/gamemode spectator'
  send_chat '/tp @p 1.5 154 1.5 0 41'
  sleep 3
  capture 20-original-pillar-geometry-in-world
  send_chat '/data get block 1 149 5'
  capture 21-pillar-world-query
  echo 'CLIENT_SENT_PILLAR_WORLD_TEST=YES' | tee -a client-smoke-status.txt
  # Real matrix + original actual world layout. Server NBT test checks
  # 100-tick invalidation after physically removing one support pillar.
  send_chat '/setblock 3 151 10 thaumcraft:infusion_matrix'
  send_chat '/setblock 3 149 10 thaumcraft:pedestal_arcane'
  send_chat '/setblock 2 149 9 thaumcraft:pillar_arcane'
  send_chat '/setblock 2 149 11 thaumcraft:pillar_ancient'
  send_chat '/setblock 4 149 9 thaumcraft:pillar_eldritch'
  send_chat '/setblock 4 149 11 thaumcraft:pillar_arcane'
  send_chat '/data merge block 3 151 10 {active:1b}'
  sleep 7
  send_chat '/data get block 3 151 10 active'
  capture 22-matrix-valid-structure-active
  send_chat '/setblock 4 149 11 minecraft:air'
  sleep 7
  send_chat '/data get block 3 151 10 active'
  capture 23-matrix-invalid-auto-deactivation
  echo 'CLIENT_SENT_INFUSION_STRUCTURE_TEST=YES' | tee -a client-smoke-status.txt
  # SOURCE BETA26: four eldritch corner pillars => +0.2/tick-recharge,
  # time 7, cost multiplier 1.05. Observe actual synced BE NBT in world.
  # A SEPARATE isolated 20-block-away site: previous smoke creates
  # three unsymmetrical pedestals near x=3,z=10 that reduce stability.
  # The original ring scan has radius 8 so these cannot interfere here.
  send_chat '/forceload add 30 30'
  send_chat '/setblock 30 149 30 thaumcraft:pedestal_arcane'
  send_chat '/setblock 29 149 29 thaumcraft:pillar_eldritch'
  send_chat '/setblock 29 149 31 thaumcraft:pillar_eldritch'
  send_chat '/setblock 31 149 29 thaumcraft:pillar_eldritch'
  send_chat '/setblock 31 149 31 thaumcraft:pillar_eldritch'
  send_chat '/setblock 30 151 30 thaumcraft:infusion_matrix'
  send_chat '/data merge block 30 151 30 {active:1b,stability:0.0f}'
  send_chat '/tp @p 30.5 154 24.5 0 28'
  sleep 7
  send_chat '/data get block 30 151 30 stability'
  send_chat '/data get block 30 151 30 stabilityReplenish'
  send_chat '/data get block 30 151 30 cycleTime'
  send_chat '/execute if data block 30 151 30 {cycleTime:7} run say MATRIX_ELDRITCH_CYCLE_7_OK'
  send_chat '/execute if data block 30 151 30 {stabilityReplenish:0.2f} run say MATRIX_ELDRITCH_REPLENISH_0_2_OK'
  capture 25-original-eldritch-stability-recharge
  echo 'CLIENT_SENT_ELDRITCH_STABILITY_TEST=YES' | tee -a client-smoke-status.txt

  # REAL PLAYER interaction: ItemCaster#onItemUseFirst -> matrix IInteractWithCaster.
  # Unlike older /data merge fixtures this changes active through a genuine
  # Minecraft mouse right-click while actually holding the BASIC GAUNTLET.
  send_chat '/forceload add 130 130'
  send_chat '/fill 128 149 125 132 149 134 minecraft:stone'
  send_chat '/setblock 130 149 130 thaumcraft:pedestal_arcane'
  send_chat '/setblock 129 149 129 thaumcraft:pillar_arcane'
  send_chat '/setblock 129 149 131 thaumcraft:pillar_arcane'
  send_chat '/setblock 131 149 129 thaumcraft:pillar_arcane'
  send_chat '/setblock 131 149 131 thaumcraft:pillar_arcane'
  send_chat '/setblock 130 151 130 thaumcraft:infusion_matrix'
  send_chat '/gamemode creative'
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:caster_basic 1'
  send_chat '/tp @p 130.5 150 126.5 0 0'
  xdotool key --clearmodifiers 1
  sleep 2
  capture 34-original-caster-gauntlet-aimed-matrix
  xdotool click 3
  sleep 5
  send_chat '/execute if data block 130 151 130 {active:1b} run say CASTER_CLICK_ACTIVATED_MATRIX_OK'
  capture 35-original-caster-activated-real-matrix
  xdotool click 3
  sleep 4
  send_chat '/execute unless data block 130 151 130 {crafting:1b} run say CASTER_NO_UNVERIFIED_RECIPE_OK'
  capture 36-caster-without-research-cannot-start-infusion
  echo 'CLIENT_SENT_REAL_CASTER_ITEM_MATRIX_TEST=YES' | tee -a client-smoke-status.txt

  # Source BETA26 BlockPillar.breakBlock yields exactly 2 corresponding
  # BlockStoneTC materials, not the pillar itself. Vanilla /loot uses the
  # installed data pack loot tables, and /clear amount=0 only counts.
  send_chat '/setblock 1 149 6 thaumcraft:pillar_arcane'
  send_chat '/setblock 2 149 6 thaumcraft:pillar_ancient'
  send_chat '/setblock 3 149 6 thaumcraft:pillar_eldritch'
  send_chat '/loot give @p mine 1 149 6 minecraft:diamond_pickaxe'
  send_chat '/loot give @p mine 2 149 6 minecraft:diamond_pickaxe'
  send_chat '/loot give @p mine 3 149 6 minecraft:diamond_pickaxe'
  send_chat '/clear @p thaumcraft:stone_arcane 0'
  send_chat '/clear @p thaumcraft:stone_ancient 0'
  send_chat '/clear @p thaumcraft:stone_eldritch_tile 0'
  capture 24-original-stone-pillar-loot
  echo 'CLIENT_SENT_ORIGINAL_PILLAR_LOOT_TEST=YES' | tee -a client-smoke-status.txt
  # Seven actual BETA26 crystal variants and persisted size/gen properties.
  send_chat '/setblock 50 149 50 minecraft:stone'
  send_chat '/setblock 50 150 50 thaumcraft:crystal_aer[size=0,gen=1]'
  send_chat '/setblock 52 149 50 minecraft:stone'
  send_chat '/setblock 52 150 50 thaumcraft:crystal_ignis[size=1,gen=2]'
  send_chat '/setblock 54 149 50 minecraft:stone'
  send_chat '/setblock 54 150 50 thaumcraft:crystal_aqua[size=2,gen=3]'
  send_chat '/setblock 56 149 50 minecraft:stone'
  send_chat '/setblock 56 150 50 thaumcraft:crystal_terra[size=3,gen=4]'
  send_chat '/setblock 58 149 50 minecraft:stone'
  send_chat '/setblock 58 150 50 thaumcraft:crystal_ordo[size=0,gen=1]'
  send_chat '/setblock 60 149 50 minecraft:stone'
  send_chat '/setblock 60 150 50 thaumcraft:crystal_perditio[size=1,gen=2]'
  send_chat '/setblock 62 149 50 minecraft:stone'
  send_chat '/setblock 62 150 50 thaumcraft:crystal_vitium[size=2,gen=3]'
  send_chat '/tp @p 55 153 44 0 30'
  sleep 3
  capture 26-original-seven-world-crystals
  send_chat '/loot give @p mine 50 150 50 minecraft:diamond_pickaxe'
  send_chat '/clear @p thaumcraft:crystal_essence 0'
  capture 27-crystal-essence-source-drop

  # DEDICATED VISUAL REGRESSION: previous crystal screenshots were shot
  # against an ocean at dusk and could not prove any shard was rendered.
  # Fix time/lighting, freeze the weather, isolate same-size crystals and
  # capture before/after frames with the camera at exactly one position.
  send_chat '/time set noon'
  send_chat '/weather clear'
  send_chat '/gamerule doDaylightCycle false'
  send_chat '/gamerule doWeatherCycle false'
  send_chat '/forceload add 80 80'
  send_chat '/fill 78 149 80 82 149 80 minecraft:stone'
  send_chat '/gamemode spectator'
  send_chat '/tp @p 80.5 152.5 73.5 0 30'
  sleep 5
  xdotool key F1
  sleep 2
  capture 28-daylight-empty-crystal-test
  xdotool key F1

  # All source BETA26 Air crystals are size=3 / gen=4, while their
  # position seeds differ. Screenshot 29 must differ from baseline 28
  # by THREE actual crystal meshes, not placeholder colored rectangles.
  send_chat '/setblock 78 150 80 thaumcraft:crystal_aer[size=3,gen=4]'
  send_chat '/setblock 80 150 80 thaumcraft:crystal_aer[size=3,gen=4]'
  send_chat '/setblock 82 150 80 thaumcraft:crystal_aer[size=3,gen=4]'
  send_chat '/tp @p 80.5 152.5 73.5 0 30'
  sleep 4
  xdotool key F1
  sleep 2
  capture 29-daylight-original-position-seeded-crystals
  xdotool key F1

  # A second, physically separate build tests multi-face attachment:
  # two side stones and a floor stone at one crystal site.
  # The same source OBJ model must show 3 independently seeded sets of
  # size+1 shards. Another crystal tests wall-only survival.
  send_chat '/forceload add 96 80'
  send_chat '/setblock 94 149 80 minecraft:stone'
  send_chat '/setblock 93 150 80 minecraft:stone'
  send_chat '/setblock 94 150 79 minecraft:stone'
  send_chat '/setblock 94 150 80 thaumcraft:crystal_ignis[size=3,gen=4]'
  send_chat '/setblock 98 150 80 minecraft:stone'
  send_chat '/setblock 99 150 80 thaumcraft:crystal_aqua[size=2,gen=3]'
  send_chat '/tp @p 96.5 153.0 73.5 0 28'
  sleep 5
  xdotool key F1
  sleep 2
  capture 30-daylight-multiface-and-wall-crystal
  xdotool key F1
  send_chat '/execute if block 94 150 80 thaumcraft:crystal_ignis[size=3,gen=4] run say CRYSTAL_MULTIFACE_PRESENT_OK'
  send_chat '/execute if block 99 150 80 thaumcraft:crystal_aqua[size=2,gen=3] run say CRYSTAL_WALL_ONLY_PRESENT_OK'
  capture 31-original-crystal-server-conditions
  echo 'CLIENT_SENT_DAYLIGHT_AND_SIX_FACE_CRYSTAL_TEST=YES' | tee -a client-smoke-status.txt

  # BETA26 jar: real block entity + source-shaped model. /data merge is
  # an operator fixture, not proof of survival transport or infusion.
  send_chat '/setblock 104 149 80 thaumcraft:jar_normal'
  send_chat '/data merge block 104 149 80 {Aspect:"aer",AspectFilter:"aer",Amount:17s,facing:2b,blocked:0b}'
  send_chat '/execute if data block 104 149 80 {Aspect:"aer",Amount:17s} run say JAR_NBT_17_AER_OK'
  send_chat '/tp @p 104.5 152.5 73.5 0 28'
  sleep 3
  capture 32-original-jar-world-nbt
  # Source glass jar texture is translucent: capture actual in-world renderer
  # with daylight and hidden HUD, not merely /data-merge confirmation.
  send_chat '/time set noon'
  send_chat '/weather clear'
  sleep 2
  xdotool key --clearmodifiers F1
  sleep 2
  capture 32a-original-jar-png-translucent-world
  xdotool key --clearmodifiers F1
  echo 'CLIENT_SENT_JAR_WORLD_NBT_TEST=YES' | tee -a client-smoke-status.txt

  # Survival-style block loot: destroy the ACTUAL filled jar and inspect
  # its dropped BlockItem component. No /give of prefilled jars is involved.
  send_chat '/setblock 104 149 80 air destroy'
  sleep 3
  send_chat '/execute if entity @e[type=minecraft:item,x=104,y=149,z=80,distance=..4,nbt={Item:{id:"thaumcraft:jar_normal",components:{"minecraft:block_entity_data":{Aspect:"aer",Amount:17s}}}}] run say JAR_DROP_PRESERVES_17_AER_OK'
  capture 33-filled-jar-drop-nbt
  echo 'CLIENT_SENT_JAR_DROP_PRESERVATION_TEST=YES' | tee -a client-smoke-status.txt

  # Quantify that the game camera sees new geometry; exact parity and
  # absence of checkerboard models still require screenshot inspection.
  # Before/after share camera/time and minimize ambient variation.
  compare -metric AE -fuzz 8% \
    client-smoke/screenshots/28-daylight-empty-crystal-test.png \
    client-smoke/screenshots/29-daylight-original-position-seeded-crystals.png \
    null: > client-smoke/crystal-image-diff.txt 2>&1 || true
  echo -n 'REAL_CLIENT_CRYSTAL_BEFORE_AFTER_PIXEL_CHANGE=' | tee -a client-smoke-status.txt
  cat client-smoke/crystal-image-diff.txt | tee -a client-smoke-status.txt
  echo 'CLIENT_SENT_CRYSTAL_WORLD_TEST=YES' | tee -a client-smoke-status.txt
  echo 'CLIENT_IN_WORLD_COMMANDS_SENT=YES' | tee -a client-smoke-status.txt
else
  echo 'INTEGRATED_WORLD_START_LOG=NOT_CONFIRMED' | tee -a client-smoke-status.txt
  echo 'The client was launched, but this is NOT a confirmed integrated world.' \
    | tee -a client-smoke-status.txt
  echo 'Inspect 01/02/03/04/05 PNG artifacts to find blocked title/create screens.' \
    | tee -a client-smoke-status.txt
  echo 'Client startup diagnostics (last 120 lines):' | tee -a client-smoke-status.txt
  tail -120 client-smoke/client.log | tee -a client-smoke-status.txt
  exit 1
fi
if grep -Eiq 'Minecraft has crashed|Mod loading has failed|Exception in thread "Render thread"' client-smoke/client.log; then
  echo 'CLIENT_CRASH=YES' | tee -a client-smoke-status.txt
  exit 1
fi
# Refuse misleading green runs where source item models were not loaded.
if grep -Eiq "Unable to load model: 'thaumcraft:item/(salis_mundus|thaumometer)'|Failed to load model.*(scanner.obj|scanner.mtl)|Exception loading.*(scanner.obj|scanner.mtl)" client-smoke/client.log run/logs/latest.log 2>/dev/null; then
  echo 'CLIENT_ORIGINAL_ITEM_MODEL_ERROR=YES' | tee -a client-smoke-status.txt
  exit 1
fi
echo 'CLIENT_ORIGINAL_ITEM_MODELS_PRESENT=YES' | tee -a client-smoke-status.txt
if [ "${CAPTURE_VIDEO:-0}" = "1" ]; then
# SIGTERM FFmpeg gracefully so the MP4 is finalized and browser-playable.
if [ -n "$VIDEO_PID" ]; then
  kill -TERM "$VIDEO_PID" 2>/dev/null || true
  wait "$VIDEO_PID" 2>/dev/null || true
  VIDEO_PID=''
fi
test -s client-smoke/thaumcraft-live-world.mp4
ffprobe -v error -show_entries format=duration,size \
  -of default=noprint_wrappers=1 client-smoke/thaumcraft-live-world.mp4 \
  | tee client-smoke/video-info.txt
echo 'CLIENT_REAL_MP4=YES' | tee -a client-smoke-status.txt
fi
echo 'PASS: real Minecraft client world, overhead crucible PNGs captured' | tee -a client-smoke-status.txt
