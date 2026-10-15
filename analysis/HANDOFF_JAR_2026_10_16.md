# 2026-10-16 02:00 MSK — Essentia Jar runtime milestone

Starting main HEAD: 0b07f5e686151a0ceb8b4db12fdb4f1d67010c00.
Main CI 38816540888 succeeded. Previous genuine Minecraft client run 38789027560 succeeded, showing Thaumometer and Salis item models.

Original BETA26 TileJarFillable.readSyncNBT/writeSyncNBT has keys Aspect, AspectFilter, Amount(short), facing(byte), blocked(boolean). Original jar_normal geometry: two cuboids [3,0,3]-[13,12,13] and [5,12,5]-[11,14,11].

Added registered jar_normal Block, BlockItem, BlockEntity, creative entry, synced NBT persistence and source-side UP transport with atomic extraction using LegacyEssentiaJarLogic. The client smoke now places the jar, writes a 17 Aer NBT fixture with commands and screenshots the real world. This operator fixture is NOT evidence of survival crafting or actual transport.

WARNING: jar source textures are not migrated in this stage; model uses vanilla glass as a temporary visual. No filled-item preservation on breaking yet, so stored essence would be lost. No EssentiaHandler network, no gauntlet activation and no actual infusion output. These are next tasks. Do not advertise jar as finished. Do not raise ~86% completion estimate without validated runtime evidence.

After code commit inspect CI and real client. Next stage: preserve jar contents on break/placement, migrate exact original textures, then connect actual 12-block essentia source search and atomic debits.
