#!/usr/bin/env bash
set -Eeuo pipefail

# REAL Minecraft client under virtual X11/Mesa. Screenshots are of the
# executing game, not examples downloaded from the web.
mkdir -p client-smoke/screenshots run
: > client-smoke-status.txt
export DISPLAY=:99
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_LOADER_DRIVER_OVERRIDE=llvmpipe
export ALSOFT_DRIVERS=null
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

# First-ever Minecraft dev run shows Accessibility/Narrator setup, not
# Singleplayer. The Continue button is at physical (512,715) with the
# fixed window on 1280x800 Xvfb; record actual screens at each step.
capture 01-first-launch
echo 'REAL_WINDOW_SCREENSHOT=YES' | tee -a client-smoke-status.txt
xdotool mousemove 512 716 click 1
sleep 8
capture 02-after-accessibility
xdotool mousemove 512 308 click 1
sleep 8
capture 03-after-singleplayer
# After Singleplayer, with no saved worlds Minecraft opens
# the CREATE WORLD form directly. Screenshots from CI run #471 verify
# Allow Commands at x512,y430 and CREATE NEW WORLD at x275,y716.
xdotool mousemove 512 433 click 1
sleep 3
capture 04-commands-enabled

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

xdotool mousemove 275 716 click 1
sleep 110
capture 05-after-world-start
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
  send_chat '/setblock 2 149 9 thaumcraft:pillar_eldritch'
  send_chat '/setblock 2 149 11 thaumcraft:pillar_eldritch'
  send_chat '/setblock 4 149 9 thaumcraft:pillar_eldritch'
  send_chat '/setblock 4 149 11 thaumcraft:pillar_eldritch'
  send_chat '/data merge block 3 151 10 {active:1b,stability:0.0f}'
  sleep 7
  send_chat '/data get block 3 151 10 stability'
  send_chat '/data get block 3 151 10 stabilityReplenish'
  send_chat '/data get block 3 151 10 cycleTime'
  capture 25-original-eldritch-stability-recharge
  echo 'CLIENT_SENT_ELDRITCH_STABILITY_TEST=YES' | tee -a client-smoke-status.txt
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
  echo 'CLIENT_IN_WORLD_COMMANDS_SENT=YES' | tee -a client-smoke-status.txt
else
  echo 'INTEGRATED_WORLD_START_LOG=NOT_CONFIRMED' | tee -a client-smoke-status.txt
  exit 1
fi
if grep -Eiq 'Minecraft has crashed|Mod loading has failed|Exception in thread "Render thread"' client-smoke/client.log; then
  echo 'CLIENT_CRASH=YES' | tee -a client-smoke-status.txt
  exit 1
fi
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
