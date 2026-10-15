# 2026-10-13 — BETA26 source-exact Crystal OBJ milestone

Repository: craftkot/tgaumcraft. Parent before this stage: f8c81237638e54d3f18f811a5a58dc86cdc89d96. Always fresh-read main and latest Actions.

Crystal OBJ source copied byte-for-byte from the supplied original 6.1.BETA26 JAR to `src/main/resources/assets/thaumcraft/models/block/crystal_source.obj`. SHA256: `8221ea1f3001ae7abb41f2f87a007c652b77cd138b2d29d707a06253faec23c6`. Git blob SHA: `3963878514680518345edbc563988f8111c1b3fe`. 72 vertices, 40 UVs, 96 triangular faces in eight groups s014/s015/s016/s017/s018/s019/s024/s026. Original MTL constants preserved and texture reference adapted for NeoForge.

Seven existing source `crystal_aer/ignis/aqua/terra/ordo/perditio/vitium` block models: now use `neoforge:obj`, source geometry, the original crystal texture, translucent render and visibility of first (size+1) parts. Blocks/loot/aura growth unchanged. The original BETA26 `CrystalModel` instead uses a position-seeded shuffle per six support faces and original rotations: that remains TODO. Colors/tint need real client screenshot confirmation.

Source-integrity regression: `tools/verify_crystal_assets.py` now validates SHA256, triangle/vertex counts and correct OBJ loader/visibility groups. Full core CI, NeoForge build, server and real client need to complete before treating visuals as verified. Previous client run #38421337097 succeeded but direct inspection of screenshots was unavailable. No user MP4 before 90%. Current estimated overall project readiness: ~86%, not increased.

Next: Inspect current Actions; fix any model parsing/render issues; complete per-face seeded mesh baking, source aura generation and infusion crafting. Update main handoff and this note as needed.

## 2026-10-13 follow-up: original per-face seeded OBJ shard choice
HEAD at start of this note: `e81305dcd7f3c34c5119342403c25998a5f1628a`.
Original BETA26 `CrystalModel.getQuads` bytecode has SIX branches (UP/DOWN/EAST/WEST/NORTH/SOUTH). Each constructs the eight OBJ groups [s014,s015,s016,s017,s018,s019,s024,s026], shuffles with a *fresh* `new java.util.Random(seed)` via `Collections.shuffle`, picks exactly `size+1` shards and rotates them into the appropriate attached face. New pure core `LegacyCrystalMeshSelection.select(int,long)` exactly reproduces this Java seeded shuffle; new `CrystalMeshParityTest` validates known original Java Random sequences (seed 0, 42), growth sizes, deterministic repeat; CI executes it. The live NeoForge models currently use the original OBJ geometry but fixed first N groups; next step must wire this dynamic selection into the client model baker and restore per-face rotations. No video below 90%. Preserve honest 86% estimate. Recheck latest main and Actions first.

## 2026-10-15: seeded OBJ shards now wired to NeoForge

Commit 511844dd134d48fe55307038ddbe9641284cc88e:
- Registered eight standalone NeoForge OBJ models, one group visible per model, retaining the byte-identical original crystal_source.obj.
- Added CrystalModelEvents with ModelEvent.RegisterAdditional and ModifyBakingResult. Wraps all seven crystal types and each of their 16 BlockState variants.
- Added SeededCrystalBakedModel: obtains position seed from BlockState.getSeed(BlockPos) through immutable ModelData and invokes existing exact LegacyCrystalMeshSelection.select(size, seed) for size+1 source shards. If world seed is absent the previous static baked model remains as fallback.
- Expanded tools/verify_crystal_assets.py to validate the eight models and hook integrity.
- Thaumcraft Port CI #38688544989 passed: source validation, core test, NeoForge build, dedicated server smoke and JAR upload.
- Real-client test #38688545040 was in progress at time of note. Inspect actual client screenshots and logs before claiming color/shard visual parity.
- Outstanding: original six support-face orientations, per-face rotations, and verified Aspect tint/lighting. The 2026-10-15 earlier real client screenshot #26 was dark and insufficient to judge shard fidelity.
- Overall completion estimate stays ~86%.
