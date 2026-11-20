#!/usr/bin/env python3
"""Source-contract checks for the BETA26 jar aspect-label renderer and real-client fixture."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

renderer = read("src/neoforge/java/dev/ehtid/thaumcraft/client/EssentiaJarLiquidRenderer.java")
label_renderer = read("src/neoforge/java/dev/ehtid/thaumcraft/client/EssentiaJarLabelRenderer.java")
logic = read("src/main/java/dev/ehtid/thaumcraft/port/core/LegacyEssentiaJarLabelRenderLogic.java")
client_config = read("src/neoforge/java/dev/ehtid/thaumcraft/client/ThaumcraftClientConfig.java")
client_entrypoint = read("src/neoforge/java/dev/ehtid/thaumcraft/client/ThaumcraftClient.java")
smoke = read("tools/client_jar_liquid_smoke.py")
workflow = read(".github/workflows/closure-jar-liquid-client.yml")
manifest = json.loads(read("analysis/assets/beta26_jar_label_manifest.json"))
assert_script = read("tools/assert_client_jar_label.py")

call = "EssentiaJarLabelRenderer.render(jar, poseStack, buffers, packedOverlay);"
empty_gate = "if (!LegacyEssentiaJarRenderLogic.hasLiquid(amount))"
if renderer.count(call) != 1 or renderer.index(call) > renderer.index(empty_gate):
    raise SystemExit("JAR_LABEL_RENDER_ORDER_INVALID: label must render before empty-liquid early return")

for token in (
    "jar.snapshot().filter()",
    "textures/models/label.png",
    "textures/aspects/",
    "Axis.XP.rotationDegrees(180.0F)",
    "Axis.ZP.rotationDegrees(crookedRotation)",
    "Axis.YP.rotationDegrees(180.0F)",
    "RenderType.entityTranslucent",
    "LightTexture.FULL_BRIGHT",
    "TC_SMOKE_JAR_LABEL_RENDERED=YES",
    "TC_SMOKE_JAR_LABEL_ICON_RENDERED=YES",
):
    if token not in label_renderer:
        raise SystemExit(f"JAR_LABEL_RENDERER_CONTRACT_MISSING={token}")
if label_renderer.count("Axis.ZP.rotationDegrees(crookedRotation)") != 2:
    raise SystemExit("JAR_LABEL_CROOKED_ROTATION_MUST_APPLY_TO_PAPER_AND_GLYPH")

if "ThaumcraftClientConfig.CROOKED_JAR_LABELS.get()" not in label_renderer:
    raise SystemExit("JAR_LABEL_CROOKED_CONFIG_NOT_READ_BY_RENDERER")
if 'crookedRotationDegrees(boolean enabled, String aspectTag' not in logic:
    raise SystemExit("JAR_LABEL_CROOKED_CONFIG_CORE_GATE_MISSING")
for token in ('builder.push("graphics")', '.define("crooked", true)', 'builder.pop()'):
    if token not in client_config:
        raise SystemExit(f"JAR_LABEL_CLIENT_CONFIG_DEFAULT_MISSING={token}")
for token in ('@Mod(value = ThaumcraftPort.MOD_ID, dist = Dist.CLIENT)',
              'ModConfig.Type.CLIENT',
              'container.registerConfig(ModConfig.Type.CLIENT, ThaumcraftClientConfig.SPEC)'):
    if token not in client_entrypoint:
        raise SystemExit(f"JAR_LABEL_CLIENT_CONFIG_REGISTRATION_MISSING={token}")

for token in (
    "LABEL_CENTER_Y = -0.4F",
    "LABEL_CENTER_Z = 0.315F",
    "ICON_CENTER_Z = 0.316F",
    "ICON_SCALE = 0.021F",
    "(aspectTag.hashCode() + blockX + facing) % 4 - 2",
):
    if token not in logic:
        raise SystemExit(f"JAR_LABEL_CORE_CONTRACT_MISSING={token}")

for token in (
    'AspectFilter:"terra"',
    "JAR_ASPECT_LABEL_FIXTURE_READY",
    "crooked=-2",
    "100-jar-aspect-label",
    "assert_client_jar_label.py",
):
    if token not in smoke:
        raise SystemExit(f"JAR_LABEL_SMOKE_CONTRACT_MISSING={token}")

for token in (
    "EssentiaJarLabelRenderer.java",
    "LegacyEssentiaJarLabelRenderLogic.java",
    "textures/aspects/*.png",
    "textures/models/label.png",
    "assert_client_jar_label.py",
):
    if token not in workflow:
        raise SystemExit(f"JAR_LABEL_WORKFLOW_PATH_MISSING={token}")

if "from PIL" in assert_script or "_decode_png" not in assert_script:
    raise SystemExit("JAR_LABEL_PIXEL_GATE_MUST_USE_REPOSITORY_STDLIB_PNG_DECODER")
if "aspect=terra crooked=-2" not in assert_script:
    raise SystemExit("JAR_LABEL_ICON_SMOKE_MUST_ASSERT_THE_CROOKED_TRANSFORM")

if len(manifest.get("aspects", [])) != 37:
    raise SystemExit("JAR_LABEL_MANIFEST_MUST_CONTAIN_37_BASE_ASPECTS")

print("PASS: BETA26 jar aspect-label source, transform, smoke and workflow contracts")
