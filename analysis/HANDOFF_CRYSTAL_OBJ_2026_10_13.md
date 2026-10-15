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

## 2026-10-15 continuation: exact six support faces and original tint

Verified original JAR SHA256 9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f using javap on:
- thaumcraft.client.renderers.block.CrystalModel#getQuads: six independent branches UP/DOWN/EAST/WEST/NORTH/SOUTH, seed offsets respectively +0/+5/+10/+15/+20/+25, each fresh Collections.shuffle(new Random(seed+offset)), size+1 groups.
- thaumcraft.client.lib.obj.MeshModel#rotate: angle around axis through origin, THEN vector translation (not origin/pivot).
- Exact source transformations, matching original bytecode:
  UP Rx(180)+(0,1,1); DOWN identity;
  EAST Rx(90),Ry(270)+(1,1,0); WEST Rx(90),Ry(90)+(0,1,1);
  NORTH Rx(90)+(0,1,0); SOUTH Rx(90),Ry(180)+(1,1,1).
- CrystalModel constructor sets all original MeshPart.tintIndex=0. Modern NeoForge wrapper must override OBJ baked quad tint with index 0 to use the existing Aspect block color.
- src/main/java/.../LegacyCrystalMeshSelection.java now contains original Face offsets plus pure vector orientation and normal rotation; CrystalMeshParityTest checks each face, seed, and coordinate transform.
- CrystalClusterBlock.hasCrystalSupport uses exactly the same adjacent rock/sturdy predicate as canSurvive. SeededCrystalBakedModel collects every support face into NeoForge ModelData and emits independent source OBJ shard geometry for each active face. No synthetic geometry or generated placeholder items.
- Commit 90ed549ca59bc2a792022bfaf919816ae46a0f13 implements six faces; subsequent tint guard keeps original Aspect coloration. Check latest main and CI/real-client smoke before claiming actual screenshots are source-exact.
- Not yet confirmed visually: translucency/color/normal shading and original outline. Estimate remains ~86% until genuine in-game comparison.

## 2026-10-15 — Actual Minecraft visual gameplay audit

User explicitly requested real Minecraft gameplay verification, not merely compilation. Fresh HEAD at this stage: d3ffd2d7c69829feaa117310ca925b71921bc45c; latest CI #38750480305 success and client #38750114587 success (previous code HEAD 90ed549c).

Inspection of actual embedded thumbnail screenshot #26 in client run #38750114587: seven stone pedestals/supports are visible against an ocean in dusk lighting; crystals are very small and cannot be visually validated or compared with original BETA26. #25 confirms actual in-game message MATRIX_ELDRITCH_REPLENISH_0_2_OK. A green process exit is not the same as full parity.

Enhance tools/client_smoke.sh with a daylight/clear weather, fixed-camera, F1 HUD-hidden before/after crystal capture (#28 empty, #29 three same-size crystals at distinct position seeds), and separate #30 multi-face plus wall-only crystals with actual server-side conditional confirmations (#31). Existing core gameplay checks, first 27 screenshots and artifact upload preserved. Emit pixel-change metric (not a visual-parity claim). Client Actions must be triggered by the script change and by future crystal renderer code changes. Compare actual screenshots and errors after the run, then make fixes. Do not raise estimate above ~86% for testing alone or claim infusion works.

## 2026-10-15: real client uncovered missing Arcane Workbench resources

Inspected ACTUAL Minecraft run #38750114587, full GitHub Actions artifact and integrated `run/logs/latest.log`. The runtime reports:
- `thaumcraft:blockstates/arcane_workbench.json` missing normal empty-state variant;
- missing item model `thaumcraft:item/arcane_workbench`;
- missing item models `thaumcraft:item/salis_mundus` and `thaumcraft:item/thaumometer`.
Do not treat a green process result as absence of missing models. Original reference JAR provided by user contains byte-exact `textures/blocks/arcane_workbench_top.png` (SHA256 e4aa96a67d21b5a305911f0e81d8c196f1da1628411711157717199b012a9c17) and `arcane_workbench_side.png` (SHA256 45f376e6b7f4317a855133df4f4b196f0ef053113ebbe534643f21684c115207) plus source JSON with six cuboids. These original resources have been restored as `textures/block` plus faithful model/blockstate/item JSON, and CI resource-hash checks added. New real client `08a`/`08b` snapshots visualize the restored block/item. The other two missing item models are still TODO and require their correct source assets; do not substitute unrelated icons.
The separate new crystal visual audit after HEAD 3a32d573 adds clear-day fixed-camera frames #28-31 for three different position seeds and multi-face/wall-only support; verify actual result and image pixel diff before claiming renderer parity. Keep ~86% completion estimate.

## Verified real Minecraft crystal audit (2026-10-15)

Run https://github.com/craftkot/tgaumcraft/actions/runs/38779831048 on commit 3a32d5738e2245c7546fc0788a92b43a5b367882 completed SUCCESS. This was a genuine NeoForge 1.21.1 client with an integrated playable world, automated chat commands and full-resolution screenshots archived under GitHub Actions artifacts `thaumcraft-real-client-screenshots`:
- screenshot #28: no crystals on the fixed stone platform at noon under clear weather.
- screenshot #29: three real Aer crystals `size=3,gen=4` at distinct BlockPos render yellow-green source OBJ shards with different deterministic shard layouts. ImageMagick 8%-fuzz before/after changed 19,796 pixels. This is positive direct rendering evidence, not only a successful Java build.
- screenshot #30: red/orange Ignis crystal between several support stone faces; Aqua cyan crystal survives on a wall-only support. Exact original rendering/lighting/parity versus the 1.12.2 client has **not** been quantitatively compared yet.
- screenshot #31: actual client message `CRYSTAL_WALL_ONLY_PRESENT_OK`, produced by integrated-server /execute block-state predicate.
- No fatal client crash. Pre-existing missing workbench item/blockstate, Salis Mundus and Thaumometer model warnings remain in this 3a32d client log because the workbench repair was committed later.
- Real source-exact Arcane Workbench geometry and textures plus item model were committed at `79d64fac710da3edf3f886a839fce12a7898e754`. Main CI `38781499183` passed. Separate genuine client run `38781499304` checks in-game screenshots #08a/#08b; inspect its final result/diagnostics before claiming the client-side fix.
- Remaining missing models: salis_mundus and thaumometer (do not claim resolved). Next need inspect screen captures from 38781499304 and finish these missing source resources; then return to actual infusion crafting. Estimate of overall project still ~86%.
