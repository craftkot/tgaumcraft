#!/usr/bin/env python3
"""Create an isolated REAL Minecraft label-art smoke from the existing proven world boot.

No edits to the shared tools/client_smoke.sh or any other feature lane. This
reuses its Xvfb, world creation, command-permission validation and cleanup,
but stops before the unrelated alchemy/infusion smoke scenarios.
"""
from pathlib import Path
import sys

source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
anchor = "  # The initial world is already creative + peaceful, as established"
if source.count(anchor) != 1:
    raise SystemExit("Client smoke world/command hook changed; refusing unsafe injection")

# We insert inside the existing 'if integrated world started' branch, after
# the real send_chat() helper and before unrelated tests. Complete the 'if'
# explicitly; the generated script is syntactically valid on its own.
head = source.split(anchor, 1)[0]
body = r'''
  echo 'LABEL_SMOKE_REAL_INTEGRATED_WORLD=YES' | tee -a client-smoke-status.txt
  # Real 1.21.1 item components, not Java mock ItemStacks or fabricated PNGs.
  # Use ONE selected slot and identical camera to isolate item model pixels.
  label_capture() {
    local name="$1"
    import -display :99 -window root -silent "client-smoke/screenshots/$name.png"
    test -s "client-smoke/screenshots/$name.png"
    echo "REAL_CLIENT_LABEL_SCREEN=$name" | tee -a client-smoke-status.txt
  }
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:jar_brace 1'
  sleep 3
  label_capture 60-jar-brace
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:label 1'
  sleep 3
  label_capture 61-blank-label
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:label[minecraft:custom_model_data=1,minecraft:custom_data={Aspects:[{key:"aer",amount:1}]}] 1'
  sleep 3
  label_capture 62-aer-label
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:label[minecraft:custom_model_data=1,minecraft:custom_data={Aspects:[{key:"ignis",amount:1}]}] 1'
  sleep 3
  label_capture 63-ignis-label

  # Inventory screenshot: all four distinct models in actual GUI, not a
  # synthetic icon montage. Keep the camera/hotbar fixed for pixel comparison.
  send_chat '/item replace entity @p hotbar.0 with thaumcraft:jar_brace 1'
  send_chat '/item replace entity @p hotbar.1 with thaumcraft:label 1'
  send_chat '/item replace entity @p hotbar.2 with thaumcraft:label[minecraft:custom_model_data=1,minecraft:custom_data={Aspects:[{key:"aer",amount:1}]}] 1'
  send_chat '/item replace entity @p hotbar.3 with thaumcraft:label[minecraft:custom_model_data=1,minecraft:custom_data={Aspects:[{key:"ignis",amount:1}]}] 1'
  xdotool key --clearmodifiers e
  sleep 4
  label_capture 64-all-four-in-real-inventory
  xdotool key --clearmodifiers e

  # guiScale:2: a 16x16 item icon is 32 physical pixels. The earlier
  # crop used Y+HEIGHT-22 and captured only the LOWER HALF of the icon plus
  # the hotbar border. Real run #41287870087 showed this; move the crop
  # center 20 pixels up to include the entire item, not the slot decoration.
  geometry="$(xdotool getwindowgeometry --shell "$WIN")"
  eval "$geometry"
  hotbar_x=$((X + WIDTH / 2 - 162))
  hotbar_y=$((Y + HEIGHT - 42))
  read -r screen_width screen_height < <(
    identify -format '%w %h' client-smoke/screenshots/60-jar-brace.png
  )
  if (( hotbar_x < 16 || hotbar_y < 16 ||
        hotbar_x + 16 > screen_width || hotbar_y + 16 > screen_height )); then
    echo "LABEL_ICON_CROP_OUTSIDE_SCREEN=x:$hotbar_x y:$hotbar_y screen:${screen_width}x${screen_height}" \
      | tee -a client-smoke-status.txt
    exit 1
  fi
  for name in 60-jar-brace 61-blank-label 62-aer-label 63-ignis-label; do
    convert "client-smoke/screenshots/$name.png" \
      -crop "32x32+$((hotbar_x-16))+$((hotbar_y-16))" +repage \
      "client-smoke/screenshots/$name-slot0.png"
  done

  label_diff() {
    local left="$1" right="$2" metric
    metric="$(compare -metric AE \
      "client-smoke/screenshots/$left-slot0.png" \
      "client-smoke/screenshots/$right-slot0.png" null: 2>&1 || true)"
    metric="${metric%% *}"
    if ! [[ "$metric" =~ ^[0-9]+$ ]] || [ "$metric" -eq 0 ]; then
      echo "LABEL_VISUAL_DIFFERENCE_MISSING=$left:$right metric=$metric" \
        | tee -a client-smoke-status.txt
      exit 1
    fi
    echo "LABEL_REAL_PIXEL_DIFFERENCE=$left:$right:$metric" \
      | tee -a client-smoke-status.txt
  }
  label_diff 60-jar-brace 61-blank-label
  label_diff 61-blank-label 62-aer-label
  label_diff 61-blank-label 63-ignis-label
  label_diff 62-aer-label 63-ignis-label

  # A nonzero pixel difference alone is weak evidence: camera movement,
  # chat or a shifting highlight can make a broken model look "different".
  # Check the CENTER of the rendered icon, away from the hotbar frame.
  # Ignis must be substantially redder than Aer (original aspect colors).
  aer_red_minus_green="$(convert client-smoke/screenshots/62-aer-label-slot0.png \
    -crop 16x16+8+8 +repage -format '%[fx:mean.r-mean.g]' info:)"
  ignis_red_minus_green="$(convert client-smoke/screenshots/63-ignis-label-slot0.png \
    -crop 16x16+8+8 +repage -format '%[fx:mean.r-mean.g]' info:)"
  echo "LABEL_AER_RED_MINUS_GREEN=$aer_red_minus_green" | tee -a client-smoke-status.txt
  echo "LABEL_IGNIS_RED_MINUS_GREEN=$ignis_red_minus_green" | tee -a client-smoke-status.txt
  if ! awk -v aer="$aer_red_minus_green" -v ignis="$ignis_red_minus_green" \
      'BEGIN { exit !(ignis - aer > 0.12 && ignis > 0.14 && aer < 0.14) }'; then
    echo 'LABEL_ASPECT_COLOR_PARITY_MISSING=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'LABEL_REAL_ASPECT_COLOR_PARITY=PASS' | tee -a client-smoke-status.txt

  if grep -Eiq "Unable to load model.*thaumcraft:item/(label|label_filled|jar_brace)|Unable to load texture.*(label_overlay|jar_brace)" \
    client-smoke/client.log run/logs/latest.log 2>/dev/null; then
    echo 'LABEL_MODEL_LOAD_ERROR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  if grep -Eiq 'Minecraft has crashed|Mod loading has failed|Exception in thread "Render thread"' \
    client-smoke/client.log; then
    echo 'LABEL_CLIENT_CRASH=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'LABEL_REAL_CLIENT_VISUAL_SMOKE=PASS' | tee -a client-smoke-status.txt
fi
'''
sys.stdout.write(head + body)
