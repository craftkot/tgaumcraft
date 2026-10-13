# 2026-10-13 — BETA26 source-exact Crystal OBJ milestone

Repository: craftkot/tgaumcraft. Parent before this stage: f8c81237638e54d3f18f811a5a58dc86cdc89d96. Always fresh-read main and latest Actions.

Crystal OBJ source copied byte-for-byte from the supplied original 6.1.BETA26 JAR to `src/main/resources/assets/thaumcraft/models/block/crystal_source.obj`. SHA256: `8221ea1f3001ae7abb41f2f87a007c652b77cd138b2d29d707a06253faec23c6`. Git blob SHA: `3963878514680518345edbc563988f8111c1b3fe`. 72 vertices, 40 UVs, 96 triangular faces in eight groups s014/s015/s016/s017/s018/s019/s024/s026. Original MTL constants preserved and texture reference adapted for NeoForge.

Seven existing source `crystal_aer/ignis/aqua/terra/ordo/perditio/vitium` block models: now use `neoforge:obj`, source geometry, the original crystal texture, translucent render and visibility of first (size+1) parts. Blocks/loot/aura growth unchanged. The original BETA26 `CrystalModel` instead uses a position-seeded shuffle per six support faces and original rotations: that remains TODO. Colors/tint need real client screenshot confirmation.

Source-integrity regression: `tools/verify_crystal_assets.py` now validates SHA256, triangle/vertex counts and correct OBJ loader/visibility groups. Full core CI, NeoForge build, server and real client need to complete before treating visuals as verified. Previous client run #38421337097 succeeded but direct inspection of screenshots was unavailable. No user MP4 before 90%. Current estimated overall project readiness: ~86%, not increased.

Next: Inspect current Actions; fix any model parsing/render issues; complete per-face seeded mesh baking, source aura generation and infusion crafting. Update main handoff and this note as needed.
