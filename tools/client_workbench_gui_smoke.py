#!/usr/bin/env python3
"""Generate a short real-client Arcane Workbench GUI smoke from the proven world boot.

The shared client_smoke.sh is read-only. This isolated runner stops immediately
after opening the actual block menu and capturing the localized screen.
"""
from pathlib import Path
import os
import sys
import subprocess

source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
options_line = "printf 'guiScale:2\\n' > run/options.txt"
anchor = "  # Source BETA26 scanner: true 56-face OBJ with animated glass screen."
language = os.environ.get("TC_WORKBENCH_LANG", "")
scale = os.environ.get("TC_WORKBENCH_GUI_SCALE", "")
tess_language = os.environ.get("TC_WORKBENCH_TESS_LANG", "")

if language not in {"en_us", "ru_ru"}:
    raise SystemExit("TC_WORKBENCH_LANG must be en_us or ru_ru")
if scale not in {"2", "3"}:
    raise SystemExit("TC_WORKBENCH_GUI_SCALE must be 2 or 3")
if tess_language != ("eng" if language == "en_us" else "rus"):
    raise SystemExit("Tesseract language does not match Minecraft language")
subprocess.run([sys.executable, "tools/test_client_workbench_gui_ocr.py"], check=True, stdout=sys.stderr)
subprocess.run([sys.executable, "tools/test_workbench_crystal_mask_sync.py"], check=True, stdout=sys.stderr)
if source.count(options_line) != 1:
    raise SystemExit("Minecraft options line changed; refusing unsafe injection")
if source.count(anchor) != 1:
    raise SystemExit("Workbench-to-scanner anchor changed; refusing unsafe injection")
world_gate = "python3 tools/assert_client_world_commands.py run/saves | tee -a client-smoke-status.txt"
workbench_gate = "python3 tools/assert_client_workbench_world.py run/saves | tee -a client-smoke-status.txt"
login_gate = "if grep -Fq 'Dev joined the game' client-smoke/client.log; then"
login_replacement = "if grep -Fq 'logged in with entity id' client-smoke/client.log; then"
if source.count(world_gate) != 1:
    raise SystemExit("Shared world NBT gate changed; refusing unsafe injection")
if source.count(login_gate) != 1:
    raise SystemExit("Shared integrated-player login gate changed; refusing unsafe injection")

replacement = (
    "printf 'guiScale:%s\\nlang:%s\\ntutorialStep:none\\n' "
    "\"$TC_WORKBENCH_GUI_SCALE\" \"$TC_WORKBENCH_LANG\" > run/options.txt"
)
source = source.replace(options_line, replacement, 1)
source = source.replace(world_gate, workbench_gate, 1)
# The shared smoke greps a localized chat sentence. In ru_ru the actual
# server log says "Dev присоединился к игре", so use PlayerList's invariant
# server-thread login record in this generated copy only.
source = source.replace(login_gate, login_replacement, 1)

body = r'''
  echo "WORKBENCH_GUI_MATRIX=$TC_WORKBENCH_LANG/$TC_WORKBENCH_GUI_SCALE" | tee -a client-smoke-status.txt

  # The shared bootstrap's initial NBT gate is narrowed only in this generated
  # copy: require real cheats + Creative, then set Peaceful and persist it before
  # any scripted block/item interaction. The shared client_smoke.sh is untouched.
  # Integrated singleplayer does not register /save-all (dedicated-server-only).
  # The initial NBT gate already proved cheats + Creative; now require the
  # integrated server's actual command feedback instead of reading stale
  # pre-save level.dat metadata.
  difficulty_log_offset="$(wc -c < client-smoke/client.log)"
  send_chat '/difficulty peaceful'
  python3 tools/assert_client_workbench_difficulty.py client-smoke/client.log \
    --from-offset "$difficulty_log_offset" --wait-seconds 12 \
    | tee -a client-smoke-status.txt

  # The existing real workbench at 12,149,7 is opened by an actual right click.
  # Fill the authentic BETA26 Thaumometer preview pattern: four gold ingots
  # around a center glass pane. This exposes the synced 20-vis preview and all
  # six requirement icons without granting research or crafting the result.
  send_chat '/fill 11 149 3 13 149 4 minecraft:stone'
  send_chat '/item replace block 12 149 7 container.1 with minecraft:gold_ingot 1'
  send_chat '/item replace block 12 149 7 container.3 with minecraft:gold_ingot 1'
  send_chat '/item replace block 12 149 7 container.4 with minecraft:glass_pane 1'
  send_chat '/item replace block 12 149 7 container.5 with minecraft:gold_ingot 1'
  send_chat '/item replace block 12 149 7 container.7 with minecraft:gold_ingot 1'
  send_chat '/data get block 12 149 7 Items'

  # Stand on the real platform and aim at the block's south face.
  send_chat '/tp @p 12.5 150 3.5 0 31'
  sleep 2
  capture 08c-workbench-before-open
  xdotool click 3
  sleep 4
  capture 08d-workbench-gui-open

  geometry="$(xdotool getwindowgeometry --shell "$WIN")"
  eval "$geometry"
  scale="$TC_WORKBENCH_GUI_SCALE"
  # capture() screenshots the X root framebuffer. client_smoke.sh has already
  # moved the game window to (0,0); xdotool's X/Y include Openbox decorations
  # (the observed inner offset is 1,20), not the framebuffer content origin.
  # Using them shifted this crop down 20px and cut off the actual GUI title.
  gui_left=$(((WIDTH - 190 * scale) / 2))
  gui_top=$(((HEIGHT - 234 * scale) / 2))
  read -r root_width root_height < <(
    identify -format '%w %h\n' client-smoke/screenshots/08d-workbench-gui-open.png
  )
  gui_width=$((190 * scale))
  gui_height=$((234 * scale))
  if (( gui_left < 0 || gui_top < 0 || gui_left + gui_width > root_width || gui_top + gui_height > root_height )); then
    echo "WORKBENCH_GUI_CROP_OUTSIDE_SCREEN=$gui_left,$gui_top $gui_width x $gui_height on $root_width x $root_height" | tee -a client-smoke-status.txt
    exit 1
  fi
  convert client-smoke/screenshots/08d-workbench-gui-open.png \
    -crop "$gui_width"x"$gui_height"+"$gui_left"+"$gui_top" +repage \
    client-smoke/screenshots/workbench-gui-crop.png

  # Recreate the tested OCR regions from the actual GUI crop. These files were
  # removed during the PSM 6/11 switch, leaving Tesseract to read nonexistent
  # paths and the OCR helper to receive empty strings.
  title_x=$((16 * scale))
  title_y=$((5 * scale))
  title_width=$((120 * scale))
  title_height=$((9 * scale))
  panel_x=$((144 * scale))
  panel_y=$((42 * scale))
  panel_width=$((46 * scale))
  panel_height=$((54 * scale))
  convert client-smoke/screenshots/workbench-gui-crop.png \
    -crop "$title_width"x"$title_height"+"$title_x"+"$title_y" +repage \
    -resize 800% -colorspace Gray -contrast-stretch 0x5% -threshold 55% \
    client-smoke/screenshots/workbench-title-ocr.png
  convert client-smoke/screenshots/workbench-gui-crop.png \
    -crop "$panel_width"x"$panel_height"+"$panel_x"+"$panel_y" +repage \
    -resize 400% -colorspace Gray -contrast-stretch 0x12% \
    client-smoke/screenshots/workbench-vis-ocr.png
  test -s client-smoke/screenshots/workbench-title-ocr.png
  test -s client-smoke/screenshots/workbench-vis-ocr.png

  # The tutorial toast can cover the title at GUI scale 3. Disable it in the
  # isolated options file above; do not dismiss it by moving the player.
  # Run two Tesseract page-segmentation modes because Minecraft's bitmap glyphs
  # are inconsistently split/merged by a single OCR mode.
  title_text_6="$(tesseract client-smoke/screenshots/workbench-title-ocr.png stdout -l "$TC_WORKBENCH_TESS_LANG" --psm 6 2>/dev/null || true)"
  title_text_8="$(tesseract client-smoke/screenshots/workbench-title-ocr.png stdout -l "$TC_WORKBENCH_TESS_LANG" --psm 8 2>/dev/null || true)"
  panel_text_6="$(tesseract client-smoke/screenshots/workbench-vis-ocr.png stdout -l "$TC_WORKBENCH_TESS_LANG" --psm 6 2>/dev/null || true)"
  panel_text_11="$(tesseract client-smoke/screenshots/workbench-vis-ocr.png stdout -l "$TC_WORKBENCH_TESS_LANG" --psm 11 2>/dev/null || true)"
  printf 'WORKBENCH_TITLE_OCR_PSM6_BEGIN\n%s\nWORKBENCH_TITLE_OCR_PSM6_END\n' "$title_text_6" | tee -a client-smoke-status.txt
  printf 'WORKBENCH_TITLE_OCR_PSM8_BEGIN\n%s\nWORKBENCH_TITLE_OCR_PSM8_END\n' "$title_text_8" | tee -a client-smoke-status.txt
  printf 'WORKBENCH_VIS_OCR_PSM6_BEGIN\n%s\nWORKBENCH_VIS_OCR_PSM6_END\n' "$panel_text_6" | tee -a client-smoke-status.txt
  printf 'WORKBENCH_VIS_OCR_PSM11_BEGIN\n%s\nWORKBENCH_VIS_OCR_PSM11_END\n' "$panel_text_11" | tee -a client-smoke-status.txt

  # Tesseract is supplemental: it can misread the pixel-font '0' as '8' and
  # cannot reliably classify the title at all scales. The actual render-path
  # marker verifies the localized Components and synced menu data; pixels
  # independently prove that the title and panel regions were painted.
  python3 tools/assert_client_workbench_gui_ocr.py \
    "$TC_WORKBENCH_LANG" "$title_text_6" "$title_text_8" "$panel_text_6" "$panel_text_11" \
    | tee -a client-smoke-status.txt
  title_ink="$(identify -format '%[fx:mean]' client-smoke/screenshots/workbench-title-ocr.png)"
  panel_ink="$(convert client-smoke/screenshots/workbench-vis-ocr.png -threshold 85% -format '%[fx:mean]' info:)"
  printf 'WORKBENCH_GUI_TITLE_INK=%s\nWORKBENCH_GUI_PANEL_INK=%s\n' "$title_ink" "$panel_ink" | tee -a client-smoke-status.txt
  python3 tools/assert_client_workbench_gui_render.py \
    "$TC_WORKBENCH_LANG" client-smoke/client.log "$title_ink" "$panel_ink" \
    | tee -a client-smoke-status.txt

  if grep -Eiq 'Unable to load model.*thaumcraft:arcane_workbench|Unable to load texture.*arcaneworkbench|Exception in thread "Render thread"|Minecraft has crashed' client-smoke/client.log; then
    echo 'WORKBENCH_GUI_RENDER_OR_CLIENT_ERROR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  printf 'WORKBENCH_GUI_REAL_CLIENT_%s_%s=PASS\n' "$TC_WORKBENCH_LANG" "$TC_WORKBENCH_GUI_SCALE" | tee -a client-smoke-status.txt
  exit 0
fi
'''
sys.stdout.write(source.split(anchor, 1)[0] + body)
