#!/usr/bin/env python3
"""Generate a short real-client Arcane Workbench GUI smoke from the proven world boot.

The shared client_smoke.sh is read-only. This isolated runner stops immediately
after opening the actual block menu and capturing the localized screen.
"""
from pathlib import Path
import os
import sys

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
if source.count(options_line) != 1:
    raise SystemExit("Minecraft options line changed; refusing unsafe injection")
if source.count(anchor) != 1:
    raise SystemExit("Workbench-to-scanner anchor changed; refusing unsafe injection")
world_gate = "python3 tools/assert_client_world_commands.py run/saves | tee -a client-smoke-status.txt"
workbench_gate = "python3 tools/assert_client_workbench_world.py run/saves | tee -a client-smoke-status.txt"
if source.count(world_gate) != 1:
    raise SystemExit("Shared world NBT gate changed; refusing unsafe injection")

replacement = (
    "printf 'guiScale:%s\\nlang:%s\\n' "
    "\"$TC_WORKBENCH_GUI_SCALE\" \"$TC_WORKBENCH_LANG\" > run/options.txt"
)
source = source.replace(options_line, replacement, 1)
source = source.replace(world_gate, workbench_gate, 1)

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
  gui_left=$((X + (WIDTH - 190 * scale) / 2))
  gui_top=$((Y + (HEIGHT - 234 * scale) / 2))
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

  # OCR the actual title and the two-line Vis/cost readout at physical pixels.
  title_x=$((12 * scale))
  title_width=$((120 * scale))
  title_height=$((24 * scale))
  panel_x=$((144 * scale))
  panel_y=$((42 * scale))
  panel_width=$((46 * scale))
  panel_height=$((54 * scale))
  convert client-smoke/screenshots/workbench-gui-crop.png \
    -crop "$title_width"x"$title_height"+"$title_x"+0 +repage \
    -resize 300% -colorspace Gray -contrast-stretch 0x12% \
    client-smoke/screenshots/workbench-title-ocr.png
  convert client-smoke/screenshots/workbench-gui-crop.png \
    -crop "$panel_width"x"$panel_height"+"$panel_x"+"$panel_y" +repage \
    -resize 400% -colorspace Gray -contrast-stretch 0x12% \
    client-smoke/screenshots/workbench-vis-ocr.png

  title_text="$(tesseract client-smoke/screenshots/workbench-title-ocr.png stdout -l "$TC_WORKBENCH_TESS_LANG" --psm 6 2>/dev/null || true)"
  panel_text="$(tesseract client-smoke/screenshots/workbench-vis-ocr.png stdout -l "$TC_WORKBENCH_TESS_LANG" --psm 6 2>/dev/null || true)"
  printf 'WORKBENCH_TITLE_OCR_BEGIN\n%s\nWORKBENCH_TITLE_OCR_END\n' "$title_text" | tee -a client-smoke-status.txt
  printf 'WORKBENCH_VIS_OCR_BEGIN\n%s\nWORKBENCH_VIS_OCR_END\n' "$panel_text" | tee -a client-smoke-status.txt

  python3 - "$TC_WORKBENCH_LANG" "$title_text" "$panel_text" <<'PY'
import sys
language, title, panel = sys.argv[1], sys.argv[2].casefold(), sys.argv[3].casefold()
if language == "en_us":
    expected_title = "arcane workbench"
    expected_available = "available"
    expected_cost = "vis"
else:
    expected_title = "магический верстак"
    expected_available = "доступно"
    expected_cost = "вис"
checks = {
    "localized title": expected_title in title,
    "localized available label": expected_available in panel,
    "localized vis label": expected_cost in panel,
    "20-vis recipe preview": "20" in panel,
}
for name, passed in checks.items():
    print(f"WORKBENCH_OCR_{name.upper().replace(' ', '_')}={'PASS' if passed else 'FAIL'}")
if not all(checks.values()):
    raise SystemExit("Real workbench screenshot did not contain every localized label")
PY

  if grep -Eiq 'Unable to load model.*thaumcraft:arcane_workbench|Unable to load texture.*arcaneworkbench|Exception in thread "Render thread"|Minecraft has crashed' client-smoke/client.log; then
    echo 'WORKBENCH_GUI_RENDER_OR_CLIENT_ERROR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  printf 'WORKBENCH_GUI_REAL_CLIENT_%s_%s=PASS\n' "$TC_WORKBENCH_LANG" "$TC_WORKBENCH_GUI_SCALE" | tee -a client-smoke-status.txt
  exit 0
fi
'''
sys.stdout.write(source.split(anchor, 1)[0] + body)
