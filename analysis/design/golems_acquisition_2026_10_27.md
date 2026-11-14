# M10 acquisition continuation, 27 October 2026

Parent-approved scope: source acquisition after M59, only feature/golems. No generic infusion runtime, research/worldgen implementation, shared Gradle/CI/ROADMAP edits, commits, branch operations, original PNG/OBJ copying, or full-survival claim.

The existing persisted arcane workbench and its golem adapter already debit Vis, six primal crystal slots, and physical ingredients. Extend that adapter for the missing complex mechanism (BASEARTIFICE,50Vis,Ignis1,Aqua1; two simple mechanisms, two thaumium plates, ordinary piston). Keep metadata-aware thaumium matching and reject sticky pistons.

The four missing infusion recipes are data only at `data/thaumcraft/golem/infusion_recipes.json`. The infusion team owns optional loading and matching through its registry-based catalog. Contract: top-level array, each object has legacyId/research/center/components/outputItemId/outputCount/aspects/instability. Ingredient has itemIds/tagIds/crystalAspect. Components preserve source list order; matching remains the runtime's responsibility. Source distinct metadata and NBT seal outputs map to already-distinct registered modern items. Missing registry entries never become AIR or substitutes.

Source EntityEvents.livingDrops300–415 provides independent brain acquisition: recently hit ordinary Zombie/subclasses, excluding BrainyZombie, nextInt10-looting<1, one brain. NeoForge removed event lootingLevel; derive current Looting enchantment level from the damage-source attacker. BrainyZombie exclusion uses a lane-owned entity-type tag for future registrations; absent optional types are harmless. Do not add M13 mobs. This restores an existing vanilla mob path.

Other approaches rejected: importing unmerged generic infusion classes makes the golem branch uncompilable; copying the infusion engine competes with its owner; granting components through recipes with invented inputs bypasses progression. JSON data and real source acquisition keep teams independent and interoperable.

Execution: source fixture RED before each production slice; isolated targeted javac output; native tests use actual workbench/menu/drop event and actual death. Root owns baseline wiring, full native build slot, combined-checkout interop, review and publication. Source descriptors and arcane cost/pattern fixtures do not establish full research/Greatwood/aura progression.

Source references: `/workspace/reference/javap/ConfigRecipes.txt`, initializeArcaneRecipes124–244 and initializeInfusionRecipes0–400/3804–3902; original JAR EntityEvents.livingDrops300–415; MCP stable39 fields decode vanilla identifiers. Butcher is instability0/Bestia10/Sensus10/Humanus10; Breaker is instability1/Instrumentum10/Perditio10/Humanus10.


Bounded levitator continuation: register the real source device and its exact LEVITATOR35Vis/Aer1 recipe through GolemModule; reuse persisted shared aura. Source golden fixtures verify ranges/scan/1200credits/entity prices/motion. Native adapters cover registration/menu, all six directions, opaque/partial-block boundary, redstone refill nuance, NBT negative remainder and real elapsed-world-tick item movement. Server-authoritative velocity notification replaces old duplicate client motion; look-based placement/full-face empty-hand range use and vanilla-backed model are explicit presentation adaptations. Full M07 research and M08 aura-generation survival flow remain dependencies, not fixture knowledge claims.
