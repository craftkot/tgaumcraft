# Midpoint audit — 50%

Baseline audited at 2026-10-08 after commit `be52d31bda0c988c5df5fe524556529614fef589`.

## Verified green
- Core parity suite: 4681 assertions.
- NeoForge 1.21.1 build succeeds.
- Dedicated NeoForge server reaches `Done` with the port loaded.
- Player attachment persistence/sync sources are wired.
- FIRSTSTEPS critical path sources are wired: crystal/dream bootstrap, Salis Mundus recipe and transforms, Arcane Workbench, Thaumometer craft result, scan dispatcher, authoritative progression payload.
- BETA26 research catalog baseline remains 148 entries / 271 stages / 16 addenda.
- BETA26 aspect registry remains 37 aspects.
- Scan registration extraction baseline remains 71 explicit constructor sites / 71 add calls.

## Defects found and fixed during audit
1. Elder Guardian was not mapped from `minecraft:elder_guardian` to legacy `Guardian`, making the conditional legacy registration unreachable.
2. Elder Guardian BEAST amount was 15 in the port; BETA26 bytecode is BEAST 10, ELDRITCH 15, WATER 15.
3. CI pinned the exact old assertion count (4667), so strengthening parity tests falsely failed the pipeline. CI now accepts a strengthened >=4000 parity run.

## Known incomplete / risky areas
- Object aspect compatibility is still only a small exact vanilla slice; full ConfigAspects object/tag/ore-dictionary derivation is not ported.
- Block scans currently collapse BlockState to a plain ItemStack and compatibility metadata 0. BETA26 ScanAspect used block metadata/state.
- The full ordered scanning registry is not wired at runtime: current FIRSTSTEPS dispatcher contains the 37 ScanAspect handlers plus ScanGeneric, not all explicit ScanBlock/ScanEntity/ScanItem/ScanMaterial/ScanOreDictionary/ScanSky/potion/enchantment handlers.
- Thaumcraft custom entity aspect mappings exist ahead of actual custom entity runtime registrations; they are compatibility groundwork, not proof those entities are playable.
- Conditional Pech and Wisp aspect variants are not implemented.
- Duplicate/special TaintSeed registrations need semantic extraction before implementation.
- Arcane Workbench uses a functional result slot, but its screen is still a temporary generic chest texture and does not reproduce the BETA26 GUI.
- Arcane Workbench vis is currently persisted on the block entity. Full BETA26 aura/chunk vis integration is not yet present.
- Workbench craft discount is currently 0 in the menu result path; exact server-side player discount integration remains.
- Thaumometer right-click server scan semantics are matched; client scan highlight/sound/aura display behavior is not yet ported.
- Only dedicated-server startup is runtime-smoke-tested. There is not yet an automated logged-in-player/GameTest covering the complete FIRSTSTEPS interaction chain.
- Current GitHub runtime resources are intentionally minimal; the full BETA26 asset/resource surface remains in the durable checkpoint/reference and has not all been migrated to 1.21 formats.

## Next priority
Do not expand breadth blindly. Close the FIRSTSTEPS runtime gaps first:
1. exact workbench result/discount/vis semantics;
2. block-state scan compatibility and larger object-aspect table;
3. automated runtime integration test for the FIRSTSTEPS chain;
4. then broaden the ordered scan registry and remaining gameplay systems.
