#!/usr/bin/env bash
# Pure math for fixed-scale Minecraft GUI click positions in X11 root pixels.
# Arguments: window X Y WIDTH HEIGHT, UI scale, GUI width height,
# and target X Y measured in unscaled GUI pixels.
minecraft_gui_point() {
  if [ "$#" -ne 9 ]; then
    echo 'minecraft_gui_point expects 9 integer arguments' >&2
    return 2
  fi
  local wx=$1 wy=$2 ww=$3 wh=$4 scale=$5 gw=$6 gh=$7 px=$8 py=$9
  if (( ww <= 0 || wh <= 0 || scale <= 0 || gw <= 0 || gh <= 0
      || ww < gw * scale || wh < gh * scale
      || px < 0 || px >= gw || py < 0 || py >= gh )); then
    echo 'invalid Minecraft window/GUI geometry or target' >&2
    return 2
  fi
  printf '%d %d\n' \
    "$((wx + (ww - gw * scale) / 2 + px * scale + scale / 2))" \
    "$((wy + (wh - gh * scale) / 2 + py * scale + scale / 2))"
}
