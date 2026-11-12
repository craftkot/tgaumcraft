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
offset_capture = 'difficulty_log_offset="$(wc -c < client-smoke/client.log)"'
expect(offset_capture in generator,
       "GUI generator does not capture the client-log byte offset before the command")
expect(
    generator.index(offset_capture)
    < generator.index("send_chat '/difficulty peaceful'")
    < generator.index("assert_client_workbench_difficulty.py client-smoke/client.log"),
    "current-command log window is not captured before the Peaceful command",
)
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
expect("-resize 400% -colorspace Gray -contrast-stretch 0x12%" in generator,
       "panel OCR no longer uses the tested preprocessing")
expect("--psm 6" in generator and "--psm 8" in generator and "--psm 11" in generator
       and "title_text_6" in generator and "title_text_8" in generator
       and "panel_text_6" in generator and "panel_text_11" in generator,
       "workbench OCR diagnostics no longer evaluate both title modes and panel modes")
expect("assert_client_workbench_gui_ocr.py" in generator,
       "workbench OCR generator does not check the clipped localized available label")
expect("assert_client_workbench_gui_render.py" in generator
       and "title_ink=" in generator and "panel_ink=" in generator,
       "workbench generator does not validate the actual render marker and screenshot pixels")
ocr_helper = Path(__file__).with_name("assert_client_workbench_gui_ocr.py").read_text(encoding="utf-8")
expect("contains_label_prefix(" in ocr_helper and "localized available label" in ocr_helper
       and "contains_prefix_near(" not in ocr_helper and "has_cost_pair(" not in ocr_helper,
       "OCR helper still treats unreliable title/cost recognition as authoritative")
screen = Path(__file__).parents[1] / "src/neoforge/java/dev/ehtid/thaumcraft/client/ArcaneWorkbenchScreen.java"
screen_source = screen.read_text(encoding="utf-8")
expect("TC_SMOKE_WORKBENCH_GUI_RENDERED=YES" in screen_source
       and "smokeBase64(title.getString())" in screen_source
       and "smokeBase64(availableText.getString())" in screen_source
       and "smokeBase64(costText.getString())" in screen_source,
       "actual ArcaneWorkbenchScreen render path does not emit localized semantic evidence")
expect(screen_source.index("ThaumcraftReadableText.wrapped")
       < screen_source.index("emitSmokeAudit(availableText, costText, available, cost);"),
       "Workbench semantic marker is emitted before the text draw calls")
render_helper = Path(__file__).with_name("assert_client_workbench_gui_render.py").read_text(encoding="utf-8")
expect("TC_SMOKE_WORKBENCH_GUI_RENDERED=YES" in render_helper
       and "title pixels present" in render_helper and "Vis panel pixels present" in render_helper
       and "cost == 20" in render_helper and "crystal_mask" in render_helper,
       "semantic GUI oracle no longer requires title/cost/menu data and screenshot pixels")
workflow = Path(__file__).parents[1] / ".github/workflows/closure-workbench-gui-client.yml"
workflow_source = workflow.read_text(encoding="utf-8")
expect("python3 tools/test_client_workbench_gui_render.py" in workflow_source
       and "tools/assert_client_workbench_gui_render.py" in workflow_source
       and "tools/test_client_workbench_gui_render.py" in workflow_source,
       "dedicated Workbench workflow does not execute the render-marker regression")


print(f"Workbench world gate: OK ({checks} assertions)")

