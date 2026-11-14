#!/usr/bin/env python3
"""Regression tests for the workbench-only world safety gate."""
import importlib.util
from pathlib import Path

path = Path(__file__).with_name("assert_client_workbench_world.py")
spec = importlib.util.spec_from_file_location("assert_client_workbench_world", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

difficulty_path = Path(__file__).with_name("assert_client_workbench_difficulty.py")
difficulty_spec = importlib.util.spec_from_file_location("assert_client_workbench_difficulty", difficulty_path)
difficulty_module = importlib.util.module_from_spec(difficulty_spec)
difficulty_spec.loader.exec_module(difficulty_module)


def byte_tag(name: str, value: int) -> bytes:
    encoded = name.encode("utf-8")
    return b"\x01" + len(encoded).to_bytes(2, "big") + encoded + bytes([value])


def int_tag(name: str, value: int) -> bytes:
    encoded = name.encode("utf-8")
    return b"\x03" + len(encoded).to_bytes(2, "big") + encoded + value.to_bytes(4, "big", signed=True)


checks = 0


def expect(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


def expect_rejected(blob: bytes, message: str, *, require_peaceful: bool = False) -> None:
    try:
        module.assert_workbench_world(blob, require_peaceful=require_peaceful)
    except AssertionError:
        expect(True, message)
    else:
        expect(False, message)


commands = byte_tag("allowCommands", 1)
creative = int_tag("GameType", 1)
normal = byte_tag("Difficulty", 2)
peaceful = byte_tag("Difficulty", 0)

expect(module.assert_workbench_world(commands + creative + normal) == 2,
       "initial Creative world must pass before its first save even if difficulty is still Normal")
expect(module.assert_workbench_world(commands + creative) is None,
       "initial world gate must not require Difficulty to have been flushed")
expect(module.assert_workbench_world(commands + creative + peaceful, require_peaceful=True) == 0,
       "final gate must accept the persisted Peaceful byte")
expect_rejected(commands + creative + normal,
                "final gate accepted a world that remained Normal", require_peaceful=True)
expect_rejected(commands + creative,
                "final gate accepted a missing Difficulty byte", require_peaceful=True)
expect_rejected(commands + creative + peaceful + normal,
                "final gate accepted ambiguous Difficulty tags", require_peaceful=True)

expect_rejected(byte_tag("allowCommands", 0) + creative,
                "commands-off world passed")
expect_rejected(commands + int_tag("GameType", 0),
                "non-Creative world passed")
expect_rejected(commands + commands + creative,
                "ambiguous allowCommands tag passed")
expect_rejected(commands + creative + byte_tag("Difficulty", 4),
                "invalid difficulty diagnostic passed")
expect_rejected(commands + b"\\x03\\x00\\x08GameType\\x00\\x00",
                "truncated GameType passed")

# Integrated-server difficulty command evidence (save-all is dedicated-server-only).
def expect_difficulty(
    log: str, accepted: bool, message: str, *, start_offset: int = 0
) -> None:
    try:
        difficulty_module.assert_peaceful_response(log, start_offset=start_offset)
    except AssertionError:
        expect(not accepted, message)
    else:
        expect(accepted, message)


server_set_log = (
    "[09:13:35] [Server thread/INFO] [minecraft/MinecraftServer]: "
    "[Dev: The difficulty has been set to Peaceful]\n"
    "[09:13:35] [Render thread/INFO] [minecraft/ChatComponent]: "
    "[System] [CHAT] The difficulty has been set to Peaceful\n"
)
already_peaceful_log = (
    "[09:13:35] [Render thread/INFO] [minecraft/ChatComponent]: "
    "[System] [CHAT] The difficulty did not change; it is already set to peaceful\n"
)
already_peaceful_log_ru = (
    "[16:33:49] [Render thread/INFO] [minecraft/ChatComponent]: "
    "[System] [CHAT] Сложность не изменилась; уже выставлено значение «peaceful»\n"
)
client_only_log = (
    "[09:13:35] [Render thread/INFO] [minecraft/ChatComponent]: "
    "[System] [CHAT] The difficulty has been set to Peaceful\n"
)
expect_difficulty(server_set_log, True, "server-confirmed Peaceful command was rejected")
expect_difficulty(already_peaceful_log, True, "already-Peaceful English response was rejected")
expect_difficulty(already_peaceful_log_ru, True, "already-Peaceful Russian response was rejected")
expect_difficulty(
    "prefix\n" + already_peaceful_log_ru,
    True,
    "current-command Russian response after the captured byte offset was rejected",
    start_offset=len("prefix\n".encode("utf-8")),
)
expect_difficulty(
    already_peaceful_log_ru + "prefix\n",
    False,
    "a stale Russian response written before the command offset was trusted",
    start_offset=len((already_peaceful_log_ru + "prefix\n").encode("utf-8")),
)
expect_difficulty(client_only_log, False, "client-only echo was trusted without server confirmation")
expect_difficulty("CLIENT_SENT_DIFFICULTY=YES\n", False, "typed-command marker was trusted")
expect_difficulty(
    "prefix\n" + server_set_log,
    True,
    "current server response after the captured byte offset was rejected",
    start_offset=len("prefix\n".encode("utf-8")),
)
expect_difficulty(
    server_set_log + client_only_log,
    False,
    "a stale server response written before the command offset was trusted",
    start_offset=len(server_set_log.encode("utf-8")),
)
expect_difficulty(
    server_set_log,
    False,
    "an offset beyond the current log length was accepted",
    start_offset=len(server_set_log.encode("utf-8")) + 1,
)
expect_difficulty(
    server_set_log + "[Render thread/INFO] [minecraft/ChatComponent]: "
    "[System] [CHAT] save-all flush<--[HERE]\n",
    False,
    "rejected singleplayer save-all command was ignored",
)
expect_difficulty(
    "[09:13:35] [Server thread/INFO] [minecraft/MinecraftServer]: "
    "[Dev: The difficulty has been set to Hard]\n",
    False,
    "Hard difficulty was accepted as Peaceful",
)

expect((chr(92) + "nexpect(") not in Path(__file__).read_text(encoding="utf-8"),
       "test source contains a literal escaped newline between Python statements")
generator = Path(__file__).with_name("client_workbench_gui_smoke.py").read_text(encoding="utf-8")
expect('subprocess.run([sys.executable, "tools/test_client_workbench_gui_ocr.py"], check=True, stdout=sys.stderr)' in generator,
       "OCR self-test must write diagnostics to stderr, not corrupt generated shell stdout")
expect("send_chat '/save-all flush'" not in generator,
       "GUI generator still sends the unavailable singleplayer save-all command")
expect("assert_client_workbench_difficulty.py client-smoke/client.log" in generator,
       "GUI generator does not verify live integrated-server difficulty feedback")
offset_capture = 'WORKBENCH_DIFFICULTY_LOG_OFFSET="$(wc -c < client-smoke/client.log)"'
expect(offset_capture in generator,
       "GUI helper does not capture the client-log byte offset before the first Peaceful command")
expect('difficulty_log_offset="${WORKBENCH_DIFFICULTY_LOG_OFFSET:-}"' in generator,
       "Workbench smoke does not reuse the offset captured by the first real command")
expect("send_chat '/difficulty peaceful'" not in generator,
       "Workbench smoke sends a second Peaceful command after the captured response window")
expect('--from-offset "$difficulty_log_offset" --wait-seconds 12' in generator,
       "GUI generator does not use bounded current-command log polling")
expect("assert_client_workbench_world.py run/saves --require-peaceful" not in generator,
       "GUI generator still reads the stale pre-shutdown difficulty byte")


# The shared smoke's English chat sentence is localized in ru_ru. The isolated
# workbench runner must replace it with the invariant PlayerList server log.
shared_smoke = Path(__file__).with_name("client_smoke.sh").read_text(encoding="utf-8")
english_login_gate = "if grep -Fq 'Dev joined the game' client-smoke/client.log; then"
expect(english_login_gate in shared_smoke,
       "shared smoke login anchor changed; generator replacement must be reviewed")
expect("login_gate = \"if grep -Fq 'Dev joined the game' client-smoke/client.log; then\"" in generator,
       "workbench generator does not identify the localized shared login gate")
expect("login_replacement = \"if grep -Fq 'logged in with entity id' client-smoke/client.log; then\"" in generator,
       "workbench generator does not use invariant integrated-server login evidence")
expect("source = source.replace(login_gate, login_replacement, 1)" in generator,
       "workbench generator does not replace the English-only login gate")

shared_send_chat = """  send_chat() {
    xdotool key --clearmodifiers t
    sleep 1
    xdotool type --clearmodifiers --delay 24 "$1"
    xdotool key --clearmodifiers Return
    sleep 4
  }"""
expect(shared_smoke.count(shared_send_chat) == 1,
       "shared send_chat helper changed; isolated generator replacement must be reviewed")
expect("send_chat_anchor = " in generator
       and "send_chat_replacement = " in generator
       and "source.count(send_chat_anchor) != 1" in generator
       and "source = source.replace(send_chat_anchor, send_chat_replacement, 1)" in generator,
       "Workbench generator does not replace the shared chat helper with its focused copy")
expect('xdotool windowactivate --sync "$WIN"' in generator
       and 'xdotool windowfocus --sync "$WIN"' in generator
       and "WORKBENCH_INPUT_TARGET=$WIN active=$active_window focus=$focused_window" in generator,
       "Workbench chat helper does not log the target, active and focused X11 windows")
expect("mapfile -t workbench_windows" in generator
       and "WORKBENCH_POST_LOGIN_WINDOW=$WIN" in generator,
       "Workbench smoke does not re-resolve and log the visible GLFW window after world login")
expect("xdotool key --clearmodifiers F3" in generator
       and "capture 05a-after-f3-input-probe" in generator,
       "Workbench smoke lacks a client-side F3 keyboard/focus probe")
replacement_body = generator.split('send_chat_replacement = """', 1)[1].split('"""', 1)[0]
expect('xdotool key --clearmodifiers t' in replacement_body
       and 'xdotool type --clearmodifiers --delay 24 "$raw_command"' in replacement_body
       and 'xdotool key --clearmodifiers slash' not in replacement_body,
       "Workbench command helper must open the default T chat and type the complete slash command")
expect('if [[ "$raw_command" == "/say TC_WORKBENCH_CHAT_INPUT_OK" ]]' in replacement_body
       and 'capture 05b-chat-open-after-t' in replacement_body
       and 'capture 05c-chat-text-before-enter' in replacement_body
       and 'capture 05d-chat-submit-result' in replacement_body,
       "Workbench chat probe must capture the real GUI before text entry, before Enter, and after submit")
expect('WORKBENCH_DIFFICULTY_LOG_OFFSET="$(wc -c < client-smoke/client.log)"' in replacement_body,
       "Workbench helper does not capture the first Peaceful command response window")
expect("chat_probe_anchor = \\\"  send_chat '/gamemode creative'\\\\n\\\"" in generator
       and "grep -Fq 'TC_WORKBENCH_CHAT_INPUT_OK' client-smoke/client.log" in generator
       and "WORKBENCH_CHAT_INPUT_PROBE=PASS" in generator
       and "source = source.replace(chat_probe_anchor, chat_probe_replacement, 1)" in generator,
       "Workbench smoke does not require a vanilla server-side chat-input probe before GUI interaction")
expect("capture 05e-after-peaceful-command" in generator,
       "Workbench smoke does not retain the screenshot after server-confirmed Peaceful command")
expect("gui_left=$(((WIDTH - 190 * scale) / 2))" in generator,
       "workbench crop still adds Openbox's horizontal client-frame offset")
expect("gui_top=$(((HEIGHT - 234 * scale) / 2))" in generator,
       "workbench crop still adds Openbox's vertical client-frame offset")
expect("gui_top=$((Y + (HEIGHT - 234 * scale) / 2))" not in generator,
       "workbench crop still uses the decorated window Y coordinate")
expect("title_x=$((16 * scale))" in generator
       and "title_y=$((5 * scale))" in generator
       and "title_width=$((120 * scale))" in generator
       and "title_height=$((9 * scale))" in generator,
       "workbench title crop no longer isolates the rendered title baseline")
expect("panel_x=$((144 * scale))" in generator
       and "panel_y=$((42 * scale))" in generator
       and "panel_width=$((46 * scale))" in generator
       and "panel_height=$((54 * scale))" in generator,
       "workbench Vis panel OCR crop no longer matches the rendered panel")
expect("client-smoke/screenshots/workbench-title-ocr.png" in generator
       and "client-smoke/screenshots/workbench-vis-ocr.png" in generator
       and "test -s client-smoke/screenshots/workbench-title-ocr.png" in generator
       and "test -s client-smoke/screenshots/workbench-vis-ocr.png" in generator,
       "workbench OCR inputs are not generated and checked before Tesseract")
expect("-resize 800% -colorspace Gray -contrast-stretch 0x5% -threshold 55%" in generator,
       "title pixel crop no longer uses the tested tight bitmap-font preprocessing")
expect("-resize 600% -colorspace Gray -contrast-stretch 0x12%" in generator,
       "panel pixel crop no longer preserves the full Vis panel")
expect("available_height=$((24 * scale))" in generator
       and "workbench-available-ocr.png" in generator
       and "test -s client-smoke/screenshots/workbench-available-ocr.png" in generator,
       "available-label OCR crop must isolate the BETA26 readout line")
expect("-resize 1000% -colorspace Gray -contrast-stretch 0x12% -threshold 55%" in generator,
       "available-label OCR crop must use the tested 10x bitmap preprocessing")
expect("--psm 6" in generator and "--psm 8" in generator
       and "--psm 7" in generator and "--psm 11" in generator
       and "title_text_6" in generator and "title_text_8" in generator
       and "available_text_7" in generator and "available_text_11" in generator
       and "available_text_8" in generator and "available-ocr-inverted.png" in generator
       and "-negate" in generator,
       "workbench OCR must include the inverted glyph-only PSM8 fallback")
expect("assert_client_workbench_gui_ocr.py" in generator,
       "workbench OCR generator does not check the clipped localized available label")
expect("assert_client_workbench_gui_render.py" in generator
       and "title_ink=" in generator and "panel_ink=" in generator,
       "workbench generator does not validate the actual render marker and screenshot pixels")
ocr_helper = Path(__file__).with_name("assert_client_workbench_gui_ocr.py").read_text(encoding="utf-8")
expect("contains_full_label(" in ocr_helper and "localized available label" in ocr_helper
       and "contains_label_prefix(" not in ocr_helper
       and "contains_prefix_near(" not in ocr_helper and "has_cost_pair(" not in ocr_helper,
       "OCR helper must reject clipped prefixes and ignore unreliable title/cost OCR")
expect("len(argv) != 6" in ocr_helper and "AVAILABLE_INVERTED_PSM8" in ocr_helper
       and "available8" in ocr_helper and "panel11" in ocr_helper
       and "available_variants" in ocr_helper,
       "OCR helper must evaluate inverted-crop PSM8 and full-panel PSM11 results")
screen = Path(__file__).parents[1] / "src/neoforge/java/dev/ehtid/thaumcraft/client/ArcaneWorkbenchScreen.java"
screen_source = screen.read_text(encoding="utf-8")
expect("TC_SMOKE_WORKBENCH_GUI_RENDERED=YES" in screen_source
       and "smokeBase64(title.getString())" in screen_source
       and "smokeBase64(availableText.getString())" in screen_source
       and "smokeBase64(costText.getString())" in screen_source
       and "cost != 20 || available < cost || crystalMask != 0b111111" in screen_source,
       "actual ArcaneWorkbenchScreen render path does not wait for the synced Thaumometer preview before emitting evidence")
expect(screen_source.index("drawAvailableLabel(")
       < screen_source.index("emitSmokeAudit(availableText, costText, available, cost,")
       and "AVAILABLE_TEXT_SCALE = 0.5F" in screen_source
       and "available_rendered_width=" in screen_source,
       "Workbench marker must follow the BETA26 half-scale available-label draw")
render_helper = Path(__file__).with_name("assert_client_workbench_gui_render.py").read_text(encoding="utf-8")
expect("TC_SMOKE_WORKBENCH_GUI_RENDERED=YES" in render_helper
       and "title pixels present" in render_helper and "Vis panel pixels present" in render_helper
       and "cost == 20" in render_helper and "crystal_mask" in render_helper
       and "available label fits panel without clipping" in render_helper,
       "semantic GUI oracle no longer requires title/cost/menu data and screenshot pixels")
workflow = Path(__file__).parents[1] / ".github/workflows/closure-workbench-gui-client.yml"
workflow_source = workflow.read_text(encoding="utf-8")
expect("python3 tools/test_client_workbench_gui_render.py" in workflow_source
       and "tools/assert_client_workbench_gui_render.py" in workflow_source
       and "tools/test_client_workbench_gui_render.py" in workflow_source,
       "dedicated Workbench workflow does not execute the render-marker regression")


print(f"Workbench world gate: OK ({checks} assertions)")

