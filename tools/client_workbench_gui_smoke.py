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
workbench_gate = "python3 tools/assert_client_workbench_world.py run/saves --require-peaceful | tee -a client-smoke-status.txt"
login_gate = "if grep -Fq 'Dev joined the game' client-smoke/client.log; then"
world_settings_anchor = """mode_x=$((X + WIDTH / 2))
mode_y=$((Y + HEIGHT * 260 / 1000))
difficulty_y=$((Y + HEIGHT * 340 / 1000))"""
world_settings_replacement = """world_gui_scale="${TC_WORKBENCH_GUI_SCALE:-2}"
world_gui_width=$((WIDTH / world_gui_scale))
world_gui_height=$((HEIGHT / world_gui_scale))
case "$world_gui_scale" in
  2)
    # Screenshot-derived centers for the 512x384 logical Create World screen.
    mode_gui_y=99
    difficulty_gui_y=127
    ;;
  3)
    # Scale 3 compresses the screen to 341x256; fixed scale-2 percentages
    # clicked the Game Mode row instead of Difficulty in the real client.
    mode_gui_y=81
    difficulty_gui_y=109
    ;;
  *)
    echo "unsupported Workbench GUI scale: $world_gui_scale" >&2
    exit 1
    ;;
esac
world_gui_center_x=$(((world_gui_width - 1) / 2))
read -r mode_x mode_y < <(
  minecraft_gui_point "$X" "$Y" "$WIDTH" "$HEIGHT" "$world_gui_scale" \
    "$world_gui_width" "$world_gui_height" "$world_gui_center_x" "$mode_gui_y"
)
read -r _ difficulty_y < <(
  minecraft_gui_point "$X" "$Y" "$WIDTH" "$HEIGHT" "$world_gui_scale" \
    "$world_gui_width" "$world_gui_height" "$world_gui_center_x" "$difficulty_gui_y"
)
echo "WORLD_SAFE_CREATE_COORDS=scale:$world_gui_scale mode:$mode_x,$mode_y difficulty:$mode_x,$difficulty_y gui:${world_gui_width}x${world_gui_height}" \
  | tee -a client-smoke-status.txt"""
login_replacement = "if grep -Fq 'logged in with entity id' client-smoke/client.log; then"

# The shared helper is used after the title-to-world transition, which can
# leave X11 focus on PointerRoot. Patch only this generated Workbench smoke;
# keep shared client_smoke.sh read-only.
send_chat_anchor = """  send_chat() {
    xdotool key --clearmodifiers t
    sleep 1
    xdotool type --clearmodifiers --delay 24 "$1"
    xdotool key --clearmodifiers Return
    sleep 4
  }"""
send_chat_replacement = """  send_chat() {
    local raw_command="$1"
    local command_body="${raw_command#/}"
    xdotool windowactivate --sync "$WIN"
    xdotool windowfocus --sync "$WIN"
    active_window="$(xdotool getactivewindow)"
    focused_window="$(xdotool getwindowfocus)"
    target_name="$(xdotool getwindowname "$WIN" 2>/dev/null || true)"
    target_pid="$(xdotool getwindowpid "$WIN" 2>/dev/null || true)"
    echo "WORKBENCH_INPUT_TARGET=$WIN active=$active_window focus=$focused_window pid=$target_pid name=$target_name" | tee -a client-smoke-status.txt
    xdotool key --clearmodifiers slash
    sleep 1
    xdotool type --clearmodifiers --delay 24 "$command_body"
    xdotool key --clearmodifiers Return
    sleep 4
  }"""
if source.count(send_chat_anchor) != 1:
    raise SystemExit("Shared send_chat helper changed; refusing unsafe injection")

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
if source.count(world_settings_anchor) != 1:
    raise SystemExit("Shared world-setting coordinates changed; refusing unsafe injection")
source = source.replace(world_settings_anchor, world_settings_replacement, 1)
source = source.replace(send_chat_anchor, send_chat_replacement, 1)

# Match the unique initial Crucible bootstrap, not other creative commands.
chat_probe_anchor = (
    "  send_chat '/gamemode creative'\n"
    "  send_chat '/difficulty peaceful'\n"
    "  send_chat '/give @p thaumcraft:crucible'\n"
)
chat_probe_replacement = "  send_chat '/give @p thaumcraft:crucible'\n"
if source.count(chat_probe_anchor) != 1:
    raise SystemExit("Initial creative command anchor changed; refusing unsafe chat probe replacement")
source = source.replace(chat_probe_anchor, chat_probe_replacement, 1)

body = r'''
  echo "WORKBENCH_GUI_MATRIX=$TC_WORKBENCH_LANG/$TC_WORKBENCH_GUI_SCALE" | tee -a client-smoke-status.txt

  # Re-resolve the visible GLFW window after the title -> integrated-world
  # transition. The pre-world handle may no longer be the active game window.
  mapfile -t workbench_windows < <(xdotool search --onlyvisible --name 'Minecraft' 2>/dev/null || true)
  if [ "${#workbench_windows[@]}" -eq 0 ]; then
    echo 'WORKBENCH_POST_LOGIN_WINDOW=NOT_FOUND' | tee -a client-smoke-status.txt
    exit 1
  fi
  active_window="$(xdotool getactivewindow)"
  WIN="$active_window"
  if [[ ! " ${workbench_windows[*]} " =~ " $WIN " ]]; then
    WIN="${workbench_windows[$((${#workbench_windows[@]} - 1))]}"
  fi
  target_name="$(xdotool getwindowname "$WIN" 2>/dev/null || true)"
  target_pid="$(xdotool getwindowpid "$WIN" 2>/dev/null || true)"
  echo "WORKBENCH_POST_LOGIN_WINDOW=$WIN active=$active_window pid=$target_pid name=$target_name candidates=${workbench_windows[*]}" | tee -a client-smoke-status.txt
  xdotool windowactivate --sync "$WIN"
  xdotool windowfocus --sync "$WIN"

  # F3 is a client-side input probe: its debug overlay must appear in the
  # immediately following real framebuffer capture, independently of chat.
  xdotool key --clearmodifiers F3
  sleep 1
  capture 05a-after-f3-input-probe
  xdotool key --clearmodifiers F3
  sleep 1

  # The generated world gate above has already checked the authoritative
  # level.dat Difficulty byte is Peaceful, along with cheats and Creative.
  # Do not issue a redundant /difficulty peaceful command: Minecraft's
  # no-op response is not reliably written to client.log.
  echo 'WORKBENCH_WORLD_SAFE_STATE=PEACEFUL' | tee -a client-smoke-status.txt


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
  # Native server-side position gate: the previous failed run captured jungle
  # at the spawn point, then OCR'd world pixels as if the GUI were open.
  # The marker is emitted ONLY if the server sees the player near the bench.
  # Check the fresh log tail, not an old chat line from a previous attempt.
  workbench_position_log_offset="$(wc -c < client-smoke/client.log)"
  send_chat '/execute positioned 12.5 150 3.5 if entity @p[distance=..1] run say TC_WORKBENCH_POSITION_CONFIRMED'
  workbench_position_confirmed=NO
  for attempt in {1..12}; do
    if tail -c "+$((workbench_position_log_offset + 1))" client-smoke/client.log | grep -Fq 'TC_WORKBENCH_POSITION_CONFIRMED'; then
      workbench_position_confirmed=YES
      break
    fi
    sleep 1
  done
  echo "WORKBENCH_SERVER_POSITION_GATE=$workbench_position_confirmed" | tee -a client-smoke-status.txt
  if [ "$workbench_position_confirmed" != YES ]; then
    capture 08c-workbench-position-not-confirmed
    exit 1
  fi
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
    -resize 600% -colorspace Gray -contrast-stretch 0x12% \
    client-smoke/screenshots/workbench-vis-ocr.png
  # OCR the compact BETA26 half-scale available line separately from the larger
  # 20-vis cost line. Keep 24 logical pixels of vertical context: a 12px crop
  # rendered the label correctly but left Tesseract PSM 7/11 with no tokens.
  # At this height the crop still ends above the separate 20-vis cost readout.
  available_height=$((24 * scale))
  convert client-smoke/screenshots/workbench-gui-crop.png \
    -crop "$panel_width"x"$available_height"+"$panel_x"+"$panel_y" +repage \
    -resize 1000% -colorspace Gray -contrast-stretch 0x12% -threshold 55% \
    client-smoke/screenshots/workbench-available-ocr.png
  # The thresholded source is white-on-black. Invert a tight glyph-row crop for
  # Tesseract's single-word PSM 8 pass; RU scale-3 otherwise misreads bitmap strokes.
  available_ocr_width=$((panel_width * 10))
  convert client-smoke/screenshots/workbench-available-ocr.png \
    -crop "$available_ocr_width"x190+0+70 +repage -negate \
    client-smoke/screenshots/workbench-available-ocr-inverted.png
  test -s client-smoke/screenshots/workbench-title-ocr.png
  test -s client-smoke/screenshots/workbench-vis-ocr.png
  test -s client-smoke/screenshots/workbench-available-ocr.png
  test -s client-smoke/screenshots/workbench-available-ocr-inverted.png

  # The tutorial toast can cover the title at GUI scale 3. Disable it in the
  # isolated options file above; do not dismiss it by moving the player.
  title_text_6="$(tesseract client-smoke/screenshots/workbench-title-ocr.png stdout -l "$TC_WORKBENCH_TESS_LANG" --psm 6 2>/dev/null || true)"
  title_text_8="$(tesseract client-smoke/screenshots/workbench-title-ocr.png stdout -l "$TC_WORKBENCH_TESS_LANG" --psm 8 2>/dev/null || true)"
  available_text_7="$(tesseract client-smoke/screenshots/workbench-available-ocr.png stdout -l "$TC_WORKBENCH_TESS_LANG" --psm 7 2>/dev/null || true)"
  available_text_11="$(tesseract client-smoke/screenshots/workbench-available-ocr.png stdout -l "$TC_WORKBENCH_TESS_LANG" --psm 11 2>/dev/null || true)"
  available_text_8="$(tesseract client-smoke/screenshots/workbench-available-ocr-inverted.png stdout -l "$TC_WORKBENCH_TESS_LANG" --psm 8 2>/dev/null || true)"
  # PSM 11 on the full panel crop is a separate visual fallback. The tighter
  # thresholded line can distort Russian bitmap strokes at GUI scale 3 even
  # when the complete label is clearly rendered; the panel crop retains it.
  panel_text_11="$(tesseract client-smoke/screenshots/workbench-vis-ocr.png stdout -l "$TC_WORKBENCH_TESS_LANG" --psm 11 2>/dev/null || true)"
  printf 'WORKBENCH_TITLE_OCR_PSM6_BEGIN\n%s\nWORKBENCH_TITLE_OCR_PSM6_END\n' "$title_text_6" | tee -a client-smoke-status.txt
  printf 'WORKBENCH_TITLE_OCR_PSM8_BEGIN\n%s\nWORKBENCH_TITLE_OCR_PSM8_END\n' "$title_text_8" | tee -a client-smoke-status.txt
  printf 'WORKBENCH_AVAILABLE_OCR_PSM7_BEGIN\n%s\nWORKBENCH_AVAILABLE_OCR_PSM7_END\n' "$available_text_7" | tee -a client-smoke-status.txt
  printf 'WORKBENCH_AVAILABLE_OCR_PSM11_BEGIN\n%s\nWORKBENCH_AVAILABLE_OCR_PSM11_END\n' "$available_text_11" | tee -a client-smoke-status.txt
  printf 'WORKBENCH_AVAILABLE_OCR_INVERTED_PSM8_BEGIN\n%s\nWORKBENCH_AVAILABLE_OCR_INVERTED_PSM8_END\n' "$available_text_8" | tee -a client-smoke-status.txt
  printf 'WORKBENCH_PANEL_OCR_PSM11_BEGIN\n%s\nWORKBENCH_PANEL_OCR_PSM11_END\n' "$panel_text_11" | tee -a client-smoke-status.txt

  # The OCR guard accepts at most two bitmap-glyph substitutions only when
  # all but one character remain; clipped "availab..." prefixes still fail.
  # The render-path marker independently checks the complete Component and
  # the actual half-scale text footprint inside the 46px panel.
  python3 tools/assert_client_workbench_gui_ocr.py \
    "$TC_WORKBENCH_LANG" "$available_text_7" "$available_text_11" "$available_text_8" "$panel_text_11" \
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
