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

cleanup() {
  echo "Client probe cleanup"
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
xdotool mousemove 275 716 click 1
sleep 110
capture 05-after-world-start
if grep -Eiq 'Starting integrated minecraft server|Preparing start region|Preparing spawn area|Joining world' client-smoke/client.log; then
  echo 'INTEGRATED_WORLD_START_LOG=YES' | tee -a client-smoke-status.txt
  # Real creative-mode commands, sent through the actual game chat UI.
  # Commands may still fail if the GUI has not yet released focus; the
  # captured game screenshot and logs remain the ground truth.
  xdotool key t
  sleep 2
  xdotool type --clearmodifiers --delay 30 '/gamemode creative'
  xdotool key Return
  sleep 4
  xdotool key t
  sleep 2
  xdotool type --clearmodifiers --delay 30 '/give @p thaumcraft:crucible'
  xdotool key Return
  sleep 5
  capture 06-crucible-give-attempt
else
  echo 'INTEGRATED_WORLD_START_LOG=NOT_CONFIRMED' | tee -a client-smoke-status.txt
  exit 1
fi
if grep -Eiq 'Minecraft has crashed|Mod loading has failed|Exception in thread "Render thread"' client-smoke/client.log; then
  echo 'CLIENT_CRASH=YES' | tee -a client-smoke-status.txt
  exit 1
fi
echo 'PASS: real Minecraft client window + title screenshot + no observed crash' | tee -a client-smoke-status.txt
