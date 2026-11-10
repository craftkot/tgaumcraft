#!/usr/bin/env python3
"""Guard the BETA26 Salis FXGeneric layer-0 additive blend on the real particle class."""
from pathlib import Path
import re

source = Path(
    "src/neoforge/java/dev/ehtid/thaumcraft/client/SalisSparkleParticle.java"
).read_text()

render_type = re.search(
    r"private static final ParticleRenderType BETA26_ADDITIVE = new ParticleRenderType\(\) \{(.*?)\n    \};",
    source,
    re.S,
)
assert render_type, "Salis particle must expose its dedicated BETA26 render type"
body = render_type.group(1)
assert "private static final AtomicBoolean SMOKE_BLEND_BEGIN = new AtomicBoolean();" in source
assert "SMOKE_BLEND_BEGIN.compareAndSet(false, true)" in source
blend_call = body.index("RenderSystem.blendFunc(770, 1);")
marker_call = body.index("TC_SMOKE_SALIS_ADDITIVE_BLEND_BEGIN=YES src=770 dst=1")
assert blend_call < marker_call, "the runtime marker must follow the actual blend-state call"
assert "ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT.begin(" in body
assert "RenderSystem.blendFunc(770, 1);" in body, (
    "BETA26 layer 0 requires SRC_ALPHA, ONE additive blending"
)
assert re.search(r"public boolean isTranslucent\(\) \{\s*return true;", body), (
    "Additive sparkles must remain in the translucent particle ordering"
)

method = re.search(
    r"public ParticleRenderType getRenderType\(\) \{(.*?)\n    \}",
    source,
    re.S,
)
assert method, "getRenderType must be present"
assert "return BETA26_ADDITIVE;" in method.group(1)
assert "return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;" not in method.group(1)

# Preserve the exact 1.12 FXGeneric 64-cell grid and 16-frame 320/512 loop.
assert "private static final int GRID_SIZE = 64;" in source
assert "private static final int FRAME_COUNT = 16;" in source
assert "return firstFrame + this.age % FRAME_COUNT;" in source
assert "this.firstFrame = level.random.nextFloat() < 0.2F ? 320 : 512;" in source

parser = Path("tools/verify_salis_sparkle_particle_log.py").read_text()
assert 'TC_SMOKE_SALIS_ADDITIVE_BLEND_BEGIN=YES src=770 dst=1' in parser
assert 'blend_mode != [("770", "1")]' in parser

print("SALIS_BETA26_LAYER0_ADDITIVE_BLEND=PASS")
print("SALIS_BETA26_320_512_FRAME_LOOP=PASS")
