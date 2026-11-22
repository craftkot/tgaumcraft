#!/usr/bin/env python3
"""Offline generator/verifier regressions for the real Wisp zap client smoke."""
from pathlib import Path
from unittest.mock import patch
import subprocess
import struct
import tempfile
import zlib

import client_wisp_zap_smoke
import verify_wisp_zap_client

def _chunk(kind: bytes, data: bytes) -> bytes:
    payload = kind + data
    return struct.pack(">I", len(data)) + payload + struct.pack(">I", zlib.crc32(payload) & 0xFFFFFFFF)


def write_png(path: Path, patch: tuple[int, int, int, int] | None = (506, 378, 518, 390)) -> None:
    width, height = 1280, 800
    background = bytes((96, 96, 96))
    aer = bytes((255, 255, 126))
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        row = bytearray(background * width)
        if patch is not None:
            x0, y0, x1, y1 = patch
            if y0 <= y < y1:
                for x in range(x0, x1):
                    offset = x * 3
                    row[offset:offset + 3] = aer
        raw.extend(row)
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + _chunk(b"IHDR", header)
        + _chunk(b"IDAT", zlib.compress(bytes(raw), 6))
        + _chunk(b"IEND", b"")
    )



def main() -> int:
    anchor = "  # The initial world is already creative + peaceful, as established"
    fixture = "#!/usr/bin/env bash\nif grep -q ready; then\n  send_chat() {\n    true\n  }\n" + anchor + "\n"
    with patch.object(Path, "read_text", return_value=fixture):
        generated = client_wisp_zap_smoke.render()

    required = (
        "wisp_send_chat()",
        "/difficulty normal",
        "/gamemode survival",
        "minecraft:resistance infinite 4 true",
        "wisp_wait_for_loaded_chunk()",
        "wisp_wait_for_fill()",
        "Successfully filled 81 block(s)",
        "WISP_FIXTURE_PLATFORM_FILL=YES",
        '/fill 6 148 6 14 148 14 minecraft:stone',
        '/tp @p 12.5 149 10.5 0 22',
        '/execute if block 12 148 10 minecraft:stone run say TC_SMOKE_WISP_SUPPORT_BLOCK=YES',
        "TC_SMOKE_WISP_SUPPORT_BLOCK=YES",
        "/execute if loaded 10 148 10 run say TC_SMOKE_WISP_CHUNK_LOADED=YES",
        '/summon thaumcraft:wisp 10.5 149 10.5 {Type:"aer",Tags:["tc_wisp_zap_smoke"]}',
        "TC_SMOKE_WISP_TARGET_SET=YES",
        "TC_SMOKE_WISP_ZAP_SENT=YES",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES",
        "TC_SMOKE_WISP_ZAP_RENDERED=YES",
        "TC_SMOKE_WISP_RENDERED=YES",
        "wisp_fast_command()",
        "sleep 0.5",
        '/summon thaumcraft:wisp 11.9 149.3 10.5 {Type:"aer",Tags:["tc_wisp_zap_smoke"]}',
        '/execute if entity @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,nbt={Type:"aer"},limit=1] run tp @p 12.5 149 10.5 facing entity @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] eyes',
        "grep -F 'TC_SMOKE_WISP_CAMERA=YES' | head -1",
        "d <= 3.0",
        "wisp_wait_for_exact_aim_teleport",
        "wisp_verify_aim_telemetry",
        "WISP_CAMERA_TELEMETRY=PASS",
        "WISP_ZAP_AIM=PLAYER_FACING_ENTITY",
        "wisp_wait_for_teleport",
        "wisp_wait_for_fresh_render",
        "WISP_RENDER_AFTER_AIM=YES",
        "wisp_capture 78-wisp-aim-check",
        "WISP_FIXTURE_TELEPORT=YES",
        "python tools/verify_wisp_zap_client.py --aim-only client-smoke/screenshots/78-wisp-aim-check.png",
        "--aim-only",
        "wisp_capture 79-wisp-before-a",
        "wisp_capture 79-wisp-before-b",
        'nbt={Type:"aer"}',
        "WISP_FIXTURE_PLATFORM_FILL=YES",
        "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
        "WISP_ZAP_TRANSIENT_PIXEL_DIFF",
        "WISP_ZAP_REAL_CLIENT=PASS",
    )
    for marker in required:
        assert marker in generated, marker
    setup = generated.split("  wisp_command '/difficulty normal'", 1)[1]
    teleport_index = setup.index("wisp_command '/tp @p 10.5 149 8.5 0 22'")
    loaded_index = setup.index("wisp_wait_for_loaded_chunk")
    fill_index = setup.index("wisp_fast_command '/fill 6 148 6 14 148 14 minecraft:stone'")
    fill_wait_index = setup.index('wisp_wait_for_fill "$fill_log_lines"', fill_index)
    fixture_teleport_index = setup.index("wisp_fast_command '/tp @p 12.5 149 10.5 0 22'", fill_wait_index)
    fixture_wait_index = setup.index('wisp_wait_for_teleport "$fixture_teleport_log_lines"', fixture_teleport_index)
    support_index = setup.index("wisp_send_chat '/execute if block 12 148 10 minecraft:stone run say TC_SMOKE_WISP_SUPPORT_BLOCK=YES'", fixture_wait_index)
    summon_index = setup.index("wisp_fast_command '/summon thaumcraft:wisp 11.9 149.3 10.5", support_index)
    aim_index = setup.index("wisp_fast_command '/execute if entity @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,nbt={Type:\"aer\"},limit=1] run tp @p 12.5 149 10.5 facing entity @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] eyes'", summon_index)
    aim_wait_index = setup.index('wisp_wait_for_exact_aim_teleport "$aim_log_lines"', aim_index)
    post_aim_snapshot_index = setup.index('post_aim_log_lines="$(wc -l < client-smoke/client.log)"', aim_wait_index)
    rendered_index = setup.index('wisp_wait_for_fresh_render "$post_aim_log_lines"', post_aim_snapshot_index)
    telemetry_index = setup.index('wisp_verify_aim_telemetry "$post_aim_log_lines"', rendered_index)
    aim_capture_index = setup.index("wisp_capture 78-wisp-aim-check", telemetry_index)
    assert teleport_index < loaded_index < fill_index < fill_wait_index < fixture_teleport_index < fixture_wait_index < support_index < summon_index < present_index < aim_index < aim_wait_index < post_aim_snapshot_index < rendered_index < telemetry_index < aim_capture_index, (
        "absolute platform and support proof must precede Wisp spawn; exact facing teleport and fresh camera telemetry must precede screenshot"
    )
    assert "Successfully filled 81 block(s)" in generated
    assert "WISP_ZAP_AIM=PLAYER_FACING_ENTITY" in generated
    assert "WISP_ZAP_AIM=PLAYER_CONTEXT_EYES" not in generated
    assert "wisp_verify_aim_telemetry" in generated
    assert "grep -F 'TC_SMOKE_WISP_CAMERA=YES' | head -1" in generated
    assert "d <= 3.0" in generated
    assert "wisp_wait_for_marker 'TC_SMOKE_WISP_PRESENT_FOR_AIM=YES'" not in generated
    assert "execute as @p at @s anchored eyes facing entity" not in generated
    assert "run tp @p ~1.5" not in generated
    assert "run tp @s ~ ~ ~ ~ ~" not in generated

    assert "WISP_ZAP_AIM=PLAYER_FACING_ENTITY" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_AIM=PLAYER_CONTEXT_EYES" not in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_CAMERA_TELEMETRY=PASS" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_AIM=LIVE_ENTITY_RELATIVE" not in verify_wisp_zap_client.REQUIRED_STATUS
    assert "~-1.5 ~ ~ facing entity" not in generated
    assert 'wisp_wait_for_fresh_render "$aim_log_lines"' not in generated
    assert "/fill 8 148 8 12 148 14 minecraft:stone" not in generated
    assert generated.count("  send_chat() {") == 1
    wisp_body = generated.split("  wisp_command() {", 1)[1]
    assert "sleep 4" not in wisp_body
    assert "xdotool mousemove" not in generated
    assert "sleep 0.08" not in generated.split("  wisp_fast_command() {", 1)[1].split("  }", 1)[0]
    syntax = subprocess.run(
        ["bash", "-n"], input=generated, text=True, capture_output=True, check=False
    )
    assert syntax.returncode == 0, syntax.stderr

    log = "\n".join((
        "TC_SMOKE_WISP_CHUNK_LOADED=YES",
        "TC_SMOKE_WISP_PLAYER_SURVIVAL=YES",
        "TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer tick=21",
        "TC_SMOKE_WISP_SUPPORT_BLOCK=YES",
        "TC_SMOKE_WISP_TARGET_SET=YES source=17 target=1 aspect=aer",
        "TC_SMOKE_WISP_ZAP_SENT=YES source=17 target=1 aspect=aer",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source=17 target=1 color=#FFFF7E",
        "TC_SMOKE_WISP_ZAP_RENDERED=YES source=17 target=1 points=9 age=0.25 texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
    ))
    status = "\n".join((
        "WISP_FIXTURE_PLATFORM_FILL=YES [Server thread] [Dev: Successfully filled 81 block(s)]",
        "WISP_FIXTURE_TELEPORT=YES [Server thread] Teleported Dev to 12.500000, 149.000000, 10.500000",
        "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
        "WISP_ZAP_AIM=PLAYER_FACING_ENTITY",
        "WISP_RENDER_AFTER_AIM=YES",
        "WISP_ZAP_AIM_TELEPORT=YES [Server thread] Teleported Dev to 12.500000, 149.000000, 10.500000",
        "WISP_CAMERA_TELEMETRY=PASS playerX=12.5 playerY=149.0 playerZ=10.5 wispX=11.9 wispY=149.3 wispZ=10.5 distance=0.6708 aimDot=0.999",
        "WISP_AIM_SCREENSHOT_VISIBLE=YES pixels=144 bbox=58,58,69,69",
        "WISP_AIM_SCREENSHOT_VISIBLE=YES pixels=144 bbox=58,58,69,69",
        "WISP_AIM_SCREENSHOT_VISIBLE=YES pixels=144 bbox=58,58,69,69",
        "WISP_ESSENTIA_TEXTURE_NO_LOAD_ERRORS=PASS",
        "WISP_ZAP_REAL_CLIENT=PASS",
        "WISP_ZAP_BASELINE_NOISE=3",
        "WISP_ZAP_TRANSIENT_PIXEL_DIFF=18",
    ))
    with tempfile.TemporaryDirectory(prefix="tc-wisp-zap-client-") as directory:
        root = Path(directory)
        for name in verify_wisp_zap_client.SCREENSHOTS:
            target = root / name
            if name in verify_wisp_zap_client.AIM_SCREENSHOTS:
                write_png(target)
            else:
                target.write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 256)
        positive = root / "aim-positive.png"
        write_png(positive)
        aim_errors, count, bbox = verify_wisp_zap_client.verify_aim_screenshot(positive)
        assert aim_errors == [], aim_errors
        assert count == 144 and bbox == (58, 58, 69, 69), (count, bbox)

        off_center = root / "aim-off-center.png"
        write_png(off_center, (448, 320, 460, 332))
        aim_errors, count, _ = verify_wisp_zap_client.verify_aim_screenshot(off_center)
        assert aim_errors and "not near the crosshair" in aim_errors[0], aim_errors

        invisible = root / "aim-invisible.png"
        write_png(invisible, None)
        aim_errors, count, _ = verify_wisp_zap_client.verify_aim_screenshot(invisible)
        assert aim_errors and count == 0, aim_errors

        errors = verify_wisp_zap_client.verify(log, status, root)
        assert errors == [], errors
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_RENDER_AFTER_AIM=YES", ""), root
        ), "stale pre-aim renderer callback must fail"
        assert verify_wisp_zap_client.verify(
            log.replace("target=1", "target=2", 1), status, root
        ), "mismatched server target must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("playerY=149.0", "playerY=138.19"), root
        ), "player falling below the verified platform must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("aimDot=0.999", "aimDot=0.8"), root
        ), "camera not facing the Wisp must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("distance=0.6708", "distance=6.7"), root
        ), "Wisp outside the 3-block melee reach must fail"
        assert verify_wisp_zap_client.verify(
            log.replace("TC_SMOKE_WISP_SUPPORT_BLOCK=YES", ""), status, root
        ), "missing exact support block must fail"
        assert verify_wisp_zap_client.verify(
            log.replace("blend=SRC_ALPHA_ONE", "blend=ALPHA"), status, root
        ), "non-additive renderer must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ZAP_TRANSIENT_PIXEL_DIFF=18", "WISP_ZAP_TRANSIENT_PIXEL_DIFF=10"), root
        ), "pixel delta below baseline noise must fail"
        write_png(root / verify_wisp_zap_client.AIM_SCREENSHOTS[0], None)
        assert verify_wisp_zap_client.verify(log, status, root), "invisible Wisp screenshot must fail"
        (root / verify_wisp_zap_client.SCREENSHOTS[-1]).unlink()
        assert verify_wisp_zap_client.verify(log, status, root), "missing screenshot must fail"

    wisp_entity = Path(
        "src/neoforge/java/dev/ehtid/thaumcraft/entity/WispEntity.java"
    ).read_text(encoding="utf-8")
    renderer = Path(
        "src/neoforge/java/dev/ehtid/thaumcraft/client/WispZapClientEffects.java"
    ).read_text(encoding="utf-8")
    assert "TC_SMOKE_WISP_TARGET_SET=YES" in wisp_entity
    assert "TC_SMOKE_WISP_ZAP_SENT=YES" in wisp_entity
    assert "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES" in renderer
    assert "TC_SMOKE_WISP_ZAP_RENDERED=YES" in renderer
    renderer_registration = Path(
        "src/neoforge/java/dev/ehtid/thaumcraft/client/WispRendererRegistration.java"
    ).read_text(encoding="utf-8")
    assert "bus = EventBusSubscriber.Bus.MOD" in renderer_registration
    assert "TC_SMOKE_WISP_RENDERED=YES" in renderer_registration
    assert "import java.util.WeakHashMap;" in renderer_registration
    assert "Map<WispEntity, Integer> SMOKE_RENDERED_TICK = new WeakHashMap<>()" in renderer_registration
    assert "SMOKE_RENDERED_TICK.put(entity, entity.tickCount)" in renderer_registration
    assert "previousTick.intValue() != entity.tickCount" in renderer_registration
    assert "import net.minecraft.client.Minecraft;" in renderer_registration
    assert "import net.minecraft.world.phys.Vec3;" in renderer_registration
    assert "TC_SMOKE_WISP_CAMERA=YES id=" in renderer_registration
    assert "player.getEyePosition(partialTick)" in renderer_registration
    assert "entity.getEyePosition(partialTick)" in renderer_registration
    assert "player.getViewVector(partialTick)" in renderer_registration
    assert "aimDot=" in renderer_registration
    assert renderer_registration.index("TC_SMOKE_WISP_CAMERA=YES id=") < renderer_registration.index("TC_SMOKE_WISP_RENDERED=YES id="), (
        "camera telemetry must be written before the fresh-render marker"
    )
    assert "SMOKE_RENDERED.add(entity.getId())" not in renderer_registration
    assert "WispRenderTypes.additive(PARTICLE_ATLAS)" in renderer_registration
    assert "RenderType.entityTranslucent(PARTICLE_ATLAS)" not in renderer_registration
    assert ".setTransparencyState(LIGHTNING_TRANSPARENCY)" in renderer_registration
    assert ".setWriteMaskState(COLOR_WRITE)" in renderer_registration
    assert ".setCullState(NO_CULL)" in renderer_registration
    assert ".setDepthTestState(LEQUAL_DEPTH_TEST)" in renderer_registration
    assert ".setTransparencyState(LIGHTNING_TRANSPARENCY)" in renderer
    assert ".setWriteMaskState(COLOR_WRITE)" in renderer
    assert "buffers.endBatch(BETA26_ADDITIVE_BOLT)" in renderer

    print("WISP_ZAP_CLIENT_SMOKE_TESTS=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
