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
        "Successfully filled 35 block(s)",
        "WISP_FIXTURE_PLATFORM_FILL=YES",
        "/execute if loaded 10 148 10 run say TC_SMOKE_WISP_CHUNK_LOADED=YES",
        '/summon thaumcraft:wisp 10.5 149 10.5 {Type:"aer",Tags:["tc_wisp_zap_smoke"]}',
        "TC_SMOKE_WISP_TARGET_SET=YES",
        "TC_SMOKE_WISP_ZAP_SENT=YES",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES",
        "TC_SMOKE_WISP_ZAP_RENDERED=YES",
        "TC_SMOKE_WISP_ASPECT_AER=YES",
        "TC_SMOKE_WISP_RENDERED=YES",
        "wisp_wait_for_marker 'TC_SMOKE_WISP_RENDERED=YES'",
        "wisp_fast_command()",
        "sleep 0.5",
        '/execute if entity @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] run say TC_SMOKE_WISP_PRESENT_FOR_AIM=YES',
        '/execute at @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] run tp @p ~-1.5 ~ ~ facing entity @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] eyes',
        "wisp_wait_for_teleport",
        "TC_SMOKE_WISP_PRESENT_FOR_AIM=YES",
        "wisp_capture 78-wisp-aim-check",
        "WISP_ZAP_AIM=LIVE_ENTITY_RELATIVE",
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
    fill_index = setup.index("wisp_fast_command '/execute at @p run fill ~-2 ~-1 ~-1 ~2 ~-1 ~5 minecraft:stone'")
    fill_wait_index = setup.index("wisp_wait_for_fill", fill_index)
    assert teleport_index < loaded_index < fill_index < fill_wait_index, (
        "fixture chunk must be entered and observed loaded before the platform fill response is accepted"
    )
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
        "TC_SMOKE_WISP_SPAWNED=YES",
        "TC_SMOKE_WISP_ASPECT_AER=YES",
        "TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer",
        "TC_SMOKE_WISP_PRESENT_FOR_AIM=YES",
        "TC_SMOKE_WISP_TARGET_SET=YES source=17 target=1 aspect=aer",
        "TC_SMOKE_WISP_ZAP_SENT=YES source=17 target=1 aspect=aer",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source=17 target=1 color=#FFFF7E",
        "TC_SMOKE_WISP_ZAP_RENDERED=YES source=17 target=1 points=9 age=0.25 texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
    ))
    status = "\n".join((
        "WISP_FIXTURE_PLATFORM_FILL=YES [Server thread] [Dev: Successfully filled 35 block(s)]",
        "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
        "WISP_ZAP_AIM=LIVE_ENTITY_RELATIVE",
        "WISP_ZAP_AIM_TELEPORT=YES [Server thread] Teleported Dev to 9.0, 149.0, 10.5",
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
            log.replace("target=1", "target=2", 1), status, root
        ), "mismatched server target must fail"
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
    assert ".setTransparencyState(LIGHTNING_TRANSPARENCY)" in renderer
    assert ".setWriteMaskState(COLOR_WRITE)" in renderer
    assert "buffers.endBatch(BETA26_ADDITIVE_BOLT)" in renderer

    print("WISP_ZAP_CLIENT_SMOKE_TESTS=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
