#!/usr/bin/env python3
"""Regression tests for the workbench-only world safety gate."""
import importlib.util
import os
from pathlib import Path
import subprocess

path = Path(__file__).with_name("assert_client_workbench_world.py")
spec = importlib.util.spec_from_file_location("assert_client_workbench_world", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


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

expect((chr(92) + "nexpect(") not in Path(__file__).read_text(encoding="utf-8"),
       "test source contains a literal escaped newline between Python statements")
generator = Path(__file__).with_name("client_workbench_gui_smoke.py").read_text(encoding="utf-8")
expect('subprocess.run([sys.executable, "tools/test_client_workbench_gui_ocr.py"], check=True, stdout=sys.stderr)' in generator,
       "OCR self-test must write diagnostics to stderr, not corrupt generated shell stdout")
expect("send_chat '/save-all flush'" not in generator,
       "GUI generator still sends the unavailable singleplayer save-all command")
expect('workbench_gate = "python3 tools/assert_client_workbench_world.py run/saves --require-peaceful | tee -a client-smoke-status.txt"' in generator,
       "Workbench smoke must verify Peaceful from authoritative world NBT before any scripted command")
expect("assert_client_workbench_difficulty.py" not in generator,
       "Workbench smoke must not poll client.log for chat feedback that Minecraft does not log")
expect("WORKBENCH_DIFFICULTY_LOG_OFFSET" not in generator,
       "Workbench smoke still captures an irrelevant client-log offset")
expect("send_chat '/difficulty peaceful'" not in generator.split("body = r'''", 1)[1],
       "Workbench smoke still sends a redundant no-op difficulty command")


# The shared smoke's English chat sentence is localized in ru_ru. The isolated
# workbench runner must replace it with the invariant PlayerList server log.
shared_smoke = Path(__file__).with_name("client_smoke.sh").read_text(encoding="utf-8")

title_click_anchor = "title_click_y=$((Y + HEIGHT * 33 / 100))"
expect(shared_smoke.count(title_click_anchor) == 1,
       "shared title-screen Singleplayer click anchor changed; review the isolated replacement")
expect('title_click_y_anchor = "title_click_y=$((Y + HEIGHT * 33 / 100))"' in generator
       and 'title_click_percent = {"2": 33, "3": 39}[scale]' in generator
       and "source.count(title_click_y_anchor) != 1" in generator
       and "source = source.replace(title_click_y_anchor, title_click_y_replacement, 1)" in generator,
       "Workbench generator does not replace the title click with a GUI-scale-specific coordinate")
for gui_scale, percent, expected_y in (("2", 33, 273), ("3", 39, 319)):
    env = os.environ.copy()
    env.update({
        "TC_WORKBENCH_LANG": "en_us",
        "TC_WORKBENCH_TESS_LANG": "eng",
        "TC_WORKBENCH_GUI_SCALE": gui_scale,
    })
    generated = subprocess.run(
        [sys.executable, str(Path(__file__).with_name("client_workbench_gui_smoke.py"))],
        check=True, capture_output=True, text=True, env=env,
    ).stdout
    expect(f"title_click_y=$((Y + HEIGHT * {percent} / 100))" in generated,
           f"GUI scale {gui_scale} generated the wrong title-screen click percentage")
    actual_y = 20 + (768 * percent // 100)
    expect(actual_y == expected_y,
           f"GUI scale {gui_scale} click must land at root y={expected_y}, got {actual_y}")
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
expect('xdotool key --clearmodifiers slash' in replacement_body
       and 'local command_body="${raw_command#/}"' in replacement_body
       and 'xdotool type --clearmodifiers --delay 24 "$command_body"' in replacement_body
       and 'xdotool key --clearmodifiers t' not in replacement_body,
       "Workbench command helper must open command chat explicitly and type only the command body")
expect('if [[ "$raw_command" == "/difficulty peaceful" ]]' not in replacement_body
       and 'capture 05b-chat-open-after-slash' not in replacement_body
       and 'capture 05c-chat-text-before-enter' not in replacement_body
       and 'capture 05d-chat-submit-result' not in replacement_body,
       "Workbench helper must not special-case a no-op difficulty command or depend on chat screenshots")
expect("chat_probe_anchor = (" in generator
       and "  send_chat '/gamemode creative'\\n" in generator
       and "  send_chat '/difficulty peaceful'\\n" in generator
       and "  send_chat '/give @p thaumcraft:crucible'\\n" in generator
       and "chat_probe_replacement = \"  send_chat '/give @p thaumcraft:crucible'\\n\"" in generator
       and "source = source.replace(chat_probe_anchor, chat_probe_replacement, 1)" in generator,
       "Workbench smoke must replace the unique initial bootstrap with only the required Crucible grant")
expect(shared_smoke.count("  send_chat '/gamemode creative'\n") > 1
       and shared_smoke.count("  send_chat '/gamemode creative'\n  send_chat '/difficulty peaceful'\n  send_chat '/give @p thaumcraft:crucible'\n") == 1,
       "Shared creative command changed: verify unique Crucible bootstrap anchor")

expect("WORKBENCH_WORLD_SAFE_STATE=PEACEFUL" in generator,
       "Workbench smoke does not record the authoritative pre-command Peaceful world gate")

# The current generator deliberately keeps the fixture inside the loaded spawn
# chunks. The earlier remote /forceload probe was removed together with the
# remote fixed-coordinate platform, so its old test oracle must not survive.
fixture_replacement = generator.split('world_fixture_replacement = """', 1)[1].split('"""', 1)[0]
fixture_start = "  # A fixed test platform high above all generated terrain/trees avoids\n"
expect(shared_smoke.count(fixture_start) == 1
       and 'world_fixture_start = "  # A fixed test platform high above all generated terrain/trees avoids\\n"' in generator,
       "Workbench generator fixture anchor does not match the current shared smoke exactly")
expect("world_fixture_start = " in generator and "world_fixture_end = " in generator
       and "source = source[:world_fixture_start_index] + world_fixture_replacement + source[world_fixture_end_index:]" in generator,
       "Workbench generator does not replace the remote fixed-coordinate fixture")
expect("send_chat '/tp @p ~ ~ ~ 0 28'" in fixture_replacement
       and "send_chat '/execute at @p run fill ~-3 ~ ~1 ~3 ~5 ~7 minecraft:air'" in fixture_replacement
       and "send_chat '/execute at @p run fill ~-3 ~-1 ~1 ~3 ~-1 ~7 minecraft:stone'" in fixture_replacement
       and "send_chat '/execute at @p run setblock ~ ~-1 ~4 thaumcraft:arcane_workbench'" in fixture_replacement,
       "Workbench fixture is not built relative to the logged-in player inside spawn chunks")
expect("send_chat '/forceload add 0 0'" not in fixture_replacement
       and "send_chat '/fill 0 148 0 4 148 6 minecraft:stone'" not in fixture_replacement
       and "TC_WORKBENCH_POST_FORCELOAD_CHAT_OK" not in generator
       and "post_force_load_" not in generator,
       "Workbench generator still depends on the removed remote-chunk command probe")

expect("send_chat '/execute at @p if block ~ ~-1 ~4 thaumcraft:arcane_workbench run say TC_WORKBENCH_POSITION_CONFIRMED'" in generator,
       "Workbench position gate does not verify the spawn-relative block placed by the current fixture")
expect("send_chat '/execute positioned 12.5 151.5 3.5 if entity @p[distance=..0.5] run say TC_WORKBENCH_POSITION_CONFIRMED'" not in generator
       and "send_chat '/execute positioned 12.5 150 3.5 if entity @p[distance=..1] run say TC_WORKBENCH_POSITION_CONFIRMED'" not in generator,
       "Workbench position gate still depends on obsolete fixed world coordinates")
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



# The shared smoke's old fixed percentages were calibrated for GUI scale 2.
# At scale 3, the same difficulty click landed on the Game Mode row; the real
# client screenshot showed Creative + Normal and the strict NBT gate rejected
# it. Exercise the actual shared coordinate helper for both layouts.
generator_source = generator
expect("world_command_anchor = " in generator_source
       and "world_command_replacement = " in generator_source
       and "source.count(world_command_anchor) != 1" in generator_source
       and "source = source.replace(world_command_anchor, world_command_replacement, 1)" in generator_source
       and "world_settings_anchor = " in generator_source
       and "world_settings_replacement = " in generator_source
       and "source.count(world_settings_anchor) != 1" in generator_source
       and "source = source.replace(world_settings_anchor, world_settings_replacement, 1)" in generator_source,
       "Workbench generator does not patch all three shared world-setting clicks through the scale-aware helper")
expect("minecraft_gui_point" in generator_source
       and "mode_gui_y=99" in generator_source and "difficulty_gui_y=127" in generator_source
       and "commands_gui_y=155" in generator_source
       and "mode_gui_y=81" in generator_source and "difficulty_gui_y=109" in generator_source
       and "commands_gui_y=137" in generator_source
       and "WORLD_SAFE_CREATE_COMMANDS_COORD=" in generator_source
       and "WORLD_SAFE_CREATE_MODE_DIFFICULTY_COORDS=" in generator_source,
       "Workbench generator lacks the measured scale-2/scale-3 coordinates for all world-setting rows")

coordinate_helper = Path(__file__).with_name("minecraft_gui_coordinates.sh")
for scale, gui_height, mode_y, expected_mode_y, difficulty_y, expected_difficulty_y, commands_y, expected_commands_y in (
    (2, 384, 99, 219, 127, 275, 155, 331),
    (3, 256, 81, 264, 109, 348, 137, 432),
):
    gui_width = 1024 // scale
    center_x = (gui_width - 1) // 2
    for label, logical_y, expected_y in (
        ("mode", mode_y, expected_mode_y),
        ("difficulty", difficulty_y, expected_difficulty_y),
        ("commands", commands_y, expected_commands_y),
    ):
        command = (
            f'source "{coordinate_helper}"; '
            f'minecraft_gui_point 1 20 1024 768 {scale} {gui_width} {gui_height} '
            f'{center_x} {logical_y}'
        )
        result = subprocess.run(["bash", "-c", command], check=True,
                                capture_output=True, text=True)
        expect(result.stdout.strip() == f"512 {expected_y}",
               f"scale-{scale} {label} click mapped to {result.stdout.strip()}, expected 512 {expected_y}")

print(f"Workbench world gate: OK ({checks} assertions)")

