#!/usr/bin/env python3
"""Fail-closed source contract for the BETA26 jar lid brace and pipe extension."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


model = read("src/neoforge/java/dev/ehtid/thaumcraft/client/EssentiaJarMetalPartsModel.java")
renderer = read("src/neoforge/java/dev/ehtid/thaumcraft/client/EssentiaJarLiquidRenderer.java")
events = read("src/neoforge/java/dev/ehtid/thaumcraft/client/EssentiaJarRendererEvents.java")
logic = read("src/main/java/dev/ehtid/thaumcraft/port/core/LegacyEssentiaJarMetalPartsLogic.java")
smoke = read("tools/client_jar_liquid_smoke.py")
workflow = read(".github/workflows/closure-jar-liquid-client.yml")
shell = read("tools/test_essentia_jar_render_logic.sh")
verifier = read("tools/verify_jar_brine_source_art.py")
assertion = read("tools/assert_client_jar_metal_parts.py")

if model.count(".mirror()") != 2:
    raise SystemExit("BETA26 ModelJar mirrored UV must be retained for both lid cuboids")
if "LayerDefinition.create(mesh" not in model or "RenderType.entityCutoutNoCull(TEXTURE)" not in model:
    raise SystemExit("BETA26 jar metal parts model layer/cutout renderer missing")
if "textures/models/jarbrine.png" not in model:
    raise SystemExit("BETA26 jarbrine model texture path changed")
if "registerLayerDefinition" not in events or "EssentiaJarMetalPartsModel::createLayer" not in events:
    raise SystemExit("jar metal parts layer definition is not registered")
if "jar.blocked()" not in renderer or "metalParts.renderLidBrace" not in renderer:
    raise SystemExit("blocked jar does not submit the BETA26 lid brace")
if "transportConnects(Direction.DOWN)" not in renderer or "level.hasChunkAt(above)" not in renderer:
    raise SystemExit("lid extension must inspect only a loaded, connectable DOWN face above")
if "metalParts.renderLidBrace" not in renderer or renderer.index("metalParts.renderLidBrace") > renderer.index(
        "if (!LegacyEssentiaJarRenderLogic.hasLiquid(amount))"):
    raise SystemExit("empty jars must render the lid brace before the empty-liquid return")
if "metalParts.renderLidExtension" not in renderer or renderer.index("metalParts.renderLidExtension") > renderer.index(
        "if (!LegacyEssentiaJarRenderLogic.hasLiquid(amount))"):
    raise SystemExit("empty jars must render the lid extension before the empty-liquid return")
if "BRACE_SCALE = 1.001F" not in logic or "EXTENSION_SCALE_XZ = 0.9F" not in logic:
    raise SystemExit("BETA26 lid/extension transforms changed")
for marker in (
    "JAR_BRACE_FIXTURE_READY",
    "JAR_EXTENSION_TUBE_FIXTURE_READY",
    "98-jar-brace",
    "99-jar-lid-extension",
    "TC_SMOKE_JAR_BRACE_RENDERED=YES",
    "TC_SMOKE_JAR_LID_EXTENSION_RENDERED=YES",
):
    if marker not in smoke:
        raise SystemExit(f"real-client metal parts fixture is missing: {marker}")
if "assert_client_jar_metal_parts.py --self-test" not in shell:
    raise SystemExit("metal parts screenshot parser self-test is not in the core gate")
if "test_client_jar_metal_parts.py" not in shell:
    raise SystemExit("metal parts source contract is not in the core gate")
if "verify_jar_brine_source_art.py" not in workflow or "assert_client_jar_metal_parts.py" not in workflow:
    raise SystemExit("real-client workflow does not verify the BETA26 source texture and screenshot")
if "bafd0aaa30ce2f3aa0b2c7c1e0152777b145e5b4" not in verifier:
    raise SystemExit("jarbrine source blob pin changed")

print("BETA26 jar metal parts source/render/smoke contract: PASS")
