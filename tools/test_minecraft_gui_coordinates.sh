#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/minecraft_gui_coordinates.sh"

# Real client window is 1024x768 while the Xvfb desktop is 1280x800.
# BETA26 research table has a 256x256 logical-pixel UI at fixed scale 2.
test "$(minecraft_gui_point 0 0 1024 768 2 256 256 140 31)" = "537 191"
test "$(minecraft_gui_point 0 0 1024 768 2 256 256 140 130)" = "537 389"
test "$(minecraft_gui_point 0 0 1024 768 2 256 256 102 108)" = "461 345"

# Moving the actual Minecraft window must translate click positions.
test "$(minecraft_gui_point 50 25 1024 768 2 256 256 140 31)" = "587 216"
# Changing the client window size must re-center, not reuse Xvfb center.
test "$(minecraft_gui_point 0 0 1280 800 2 256 256 140 31)" = "665 207"

if minecraft_gui_point 0 0 256 256 2 256 256 140 31 >/dev/null 2>&1; then
  echo 'undersized Minecraft window was accepted' >&2
  exit 1
fi
if minecraft_gui_point 0 0 1024 768 2 256 256 260 31 >/dev/null 2>&1; then
  echo 'out-of-bounds GUI target was accepted' >&2
  exit 1
fi
echo 'MINECRAFT_GUI_COORDINATES_PARITY_OK'
