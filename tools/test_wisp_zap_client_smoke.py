#!/usr/bin/env python3
"""Offline generator/verifier regressions for the real Wisp zap client smoke."""
from pathlib import Path
from unittest.mock import patch
import subprocess
import tempfile

import client_wisp_zap_smoke
import verify_wisp_zap_client


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
        '/summon thaumcraft:wisp 10.5 149 11.4 {Type:"aer",Tags:["tc_wisp_zap_smoke"]}',
        "TC_SMOKE_WISP_TARGET_SET=YES",
        "TC_SMOKE_WISP_ZAP_SENT=YES",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES",
        "TC_SMOKE_WISP_ZAP_RENDERED=YES",
        "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
        "WISP_ZAP_TRANSIENT_PIXEL_DIFF",
        "WISP_ZAP_REAL_CLIENT=PASS",
    )
    for marker in required:
        assert marker in generated, marker
    assert generated.count("  send_chat() {") == 1
    assert "sleep 4" not in generated
    syntax = subprocess.run(
        ["bash", "-n"], input=generated, text=True, capture_output=True, check=False
    )
    assert syntax.returncode == 0, syntax.stderr

    log = "\n".join((
        "TC_SMOKE_WISP_TARGET_SET=YES source=17 target=1 aspect=aer",
        "TC_SMOKE_WISP_ZAP_SENT=YES source=17 target=1 aspect=aer",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source=17 target=1 color=#FFFF7E",
        "TC_SMOKE_WISP_ZAP_RENDERED=YES source=17 target=1 points=9 age=0.25 texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
    ))
    status = "\n".join((
        "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
        "WISP_ESSENTIA_TEXTURE_NO_LOAD_ERRORS=PASS",
        "WISP_ZAP_REAL_CLIENT=PASS",
        "WISP_ZAP_BASELINE_NOISE=3",
        "WISP_ZAP_TRANSIENT_PIXEL_DIFF=18",
    ))
    with tempfile.TemporaryDirectory(prefix="tc-wisp-zap-client-") as directory:
        root = Path(directory)
        for name in verify_wisp_zap_client.SCREENSHOTS:
            (root / name).write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 256)
        assert verify_wisp_zap_client.verify(log, status, root) == []
        assert verify_wisp_zap_client.verify(
            log.replace("target=1", "target=2", 1), status, root
        ), "mismatched server target must fail"
        assert verify_wisp_zap_client.verify(
            log.replace("blend=SRC_ALPHA_ONE", "blend=ALPHA"), status, root
        ), "non-additive renderer must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ZAP_TRANSIENT_PIXEL_DIFF=18", "WISP_ZAP_TRANSIENT_PIXEL_DIFF=10"), root
        ), "pixel delta below baseline noise must fail"
        (root / verify_wisp_zap_client.SCREENSHOTS[0]).unlink()
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

    print("WISP_ZAP_CLIENT_SMOKE_TESTS=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
