# 2026-10-16 02:00 MSK — Essentia Jar runtime milestone

Starting main HEAD: 0b07f5e686151a0ceb8b4db12fdb4f1d67010c00.
Main CI 38816540888 succeeded. Previous genuine Minecraft client run 38789027560 succeeded, showing Thaumometer and Salis item models.

Original BETA26 TileJarFillable.readSyncNBT/writeSyncNBT has keys Aspect, AspectFilter, Amount(short), facing(byte), blocked(boolean). Original jar_normal geometry: two cuboids [3,0,3]-[13,12,13] and [5,12,5]-[11,14,11].

Added registered jar_normal Block, BlockItem, BlockEntity, creative entry, synced NBT persistence and source-side UP transport with atomic extraction using LegacyEssentiaJarLogic. The client smoke now places the jar, writes a 17 Aer NBT fixture with commands and screenshots the real world. This operator fixture is NOT evidence of survival crafting or actual transport.

WARNING: jar source textures are not migrated in this stage; model uses vanilla glass as a temporary visual. No filled-item preservation on breaking yet, so stored essence would be lost. No EssentiaHandler network, no gauntlet activation and no actual infusion output. These are next tasks. Do not advertise jar as finished. Do not raise ~86% completion estimate without validated runtime evidence.

After code commit inspect CI and real client. Next stage: preserve jar contents on break/placement, migrate exact original textures, then connect actual 12-block essentia source search and atomic debits.

## 2026-10-16 03:00 MSK — filled jar drop preservation and filtered intake

Previous HEAD `9053aacc821fd4270e397def36ec63226e3ad512`: main CI #38821465388 SUCCESS and genuine Minecraft client #38821465349 SUCCESS, including jar placement/NBT fixture screenshot #32. This confirms runtime load but not infusion or actual survival transfer.

Fix: the live jar's addFromTransport now checks LegacyEssentiaJarLogic.accepts before low-level add, so a labeled jar refuses a mismatched aspect (the BETA26 low-level addToContainer intentionally does not enforce the label). Mining a filled jar now preserves its stored Aspect/AspectFilter/Amount/facing/blocked in the dropped item's vanilla minecraft:block_entity_data via BlockEntity.saveToItem, and vanilla BlockItem should restore on placement. Empty default jars keep ordinary loot. This is deliberately implemented in getDrops rather than onRemove to avoid a second drop and duplication. An automated real-client test now destroys a 17-Aer jar and checks the dropped item NBT component (screenshot #33). Check new CI and client run conclusions and actual test chat before claiming validated. No full EssentiaHandler network, survival source filling or ritual completion yet; estimate remains ~86%.

## 2026-10-16 06:18 MSK — source-era essentia search bridge

Fresh-read of `main` before work: `e1ab87ad22b39dc52766312fe8b6f8cb89c6d151`; CI #38827198327 and actual client #38827198375 both SUCCESS. Confirmed scheduled nighttime sessions added real jar block runtime and preserved filled jar drops.

Read ACTUAL BETA26 `thaumcraft.common.lib.events.EssentiaHandler` bytecode from original user JAR with javap. For infusion `drainEssentia(tile,aspect,null,12,delay)` source finder scans x,z inclusive -12..12, y half-open -12..11, skips (0,0,0), sorts discovered `IAspectSource` by squared distance with discovery order as a tie breaker, caches found sources and has 10,000 ms wall-clock delay after failed search. Original version searches **all** IAspectSource implementations; current port supports **only registered EssentiaJarBlockEntity**, with full jar NBT persistence and blocked state. Other sources (pipes/mirrors/ale mbics) and FX packets still TODO.

Added `LegacyEssentiaSourceOrder.nullFacing(range)` with immutable cached original offset order, asymmetric Y bounds and deterministic priority. Added `EssentiaSourceOrderParityTest` to CoreParity CI (r=1 and r=12, no duplicates, ties, limits, immutable snapshots). Added `InfusionJarSourceBridge` with instance-local world/position cache, no forced chunk loads, blocked jar checks, exact successful one-unit server-side debit and original wall-clock 10-sec rescan delay. Added gated `InfusionMatrixBlockEntity.drainRealJarEssentia(AspectListData)` that uses existing `LegacyInfusionEssentiaCycle.cycle` only if matrix is active AND crafting AND layout valid. This METHOD IS NOT INVOKED BY TICKS YET: the verified recipe-start/persistence/ingredients/finish lifecycle is incomplete. Never call this method without persisting its returned reduced AspectList and enforcing verified recipe state, or items could spend essentia twice.

Test outcome and final commit SHA to be checked via latest Actions. Completion estimate still ~86%, not a source-exact whole network, and not a craftable infusion.
