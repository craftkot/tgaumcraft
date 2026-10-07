# Stage 8 compatibility notes extracted from BETA26

## PlayerKnowledge / PlayerWarp

Preserved behaviors include:

- `isResearchKnown("") == true`;
- unknown research stage is -1; known research without a stored stage is 0;
- `KEY@N` means the base research is known and current stage is at least N;
- COMPLETE is stored stage **greater than** JSON stage count;
- `removeResearch("R@1")` can report success while leaving `R` in the set;
- `clearResearchFlag` uses the wrong map-removal key in BETA26, leaving empty cached flag mappings;
- old knowledge NBT serializes only positive knowledge rows, while deserialization accepts raw values without validation;
- duplicate research rows are effectively first-wins for stage/flags; duplicate knowledge rows are last-wins;
- legacy warp NBT deserialization writes array values directly and bypasses the ordinary 0..500 setter clamp.

## Research query expressions

`ThaumcraftCapabilities.knowsResearch` and `knowsResearchStrict` have unusual OR control flow which is now preserved in
`ResearchKnowledgeQueries`:

- `A&&B` recursively requires both;
- if any branch in `A||B` matches, the **entire vararg method returns true immediately**, so later arguments are skipped;
- if no branch in `A||B` matches, that OR group itself does **not** return false; evaluation simply continues;
- null terms throw at `String.contains`, which matters because `ScanningManager.isThingStillScannable` catches that exception.

The strict method requires complete research for ordinary keys but only stage-aware known status for `KEY@N` expressions.

## Research requisites

`ResearchManager.doesPlayerHaveRequisites` is now represented directly instead of injected as a placeholder predicate.
Unknown/non-JSON research keys return true, which is essential for scan research such as `!minecraft:stone0`. For a real
entry, only `getParentsStripped()` is tested through `knowsResearchStrict`; stripping removes a leading `~` but leaves
`@stage` suffixes intact.

## Research progression

- one requirement-free stage may be skipped in addition to ordinary +1 progression;
- complete stage is `stageCount + 1`;
- warp arithmetic can count a stage twice due to `previousStage` reuse/reassignment;
- siblings are completed after every successful progression call, not only final completion;
- addenda are scanned across already-complete research when another research completes;
- completion with flags enabled awards 5 XP when a real ResearchEntry exists;
- `ARCANEEAR` has `required_knowledgeauram`, which the BETA26 loader ignores.

## Research categories and ScanGeneric

All seven category formulas are now extracted exactly from `ConfigResearch.initCategories`. `ResearchCategory.applyFormula`
computes for each formula aspect:

`multiplier^2 * scannedAmount * (formulaWeight / 10)`

sums the terms, applies sqrt only when the sum is positive, then ceil.

`ScanGeneric.onSuccess` attempts Observation knowledge for **every category** using this formula. `ScanAspect.onSuccess`
always attempts +1 raw Observation in AUROMANCY, BASICS and ALCHEMY, in that order.

## ScanningManager

The new loader-neutral `ScanningService` preserves these bytecode-level rules:

- handlers are ordered and all matching handlers are considered; scanning does not stop after the first success;
- a keyed handler only succeeds when `progressResearch` returns true;
- null/empty research keys bypass progression and can still call `onSuccess`;
- a successful null/empty key suppresses both `tc.knownobject` and `tc.unknownobject` feedback;
- `getResearchKey()` is intentionally called repeatedly rather than cached (five times on a normal keyed success);
- `isThingStillScannable` catches exceptions only around research lookup; a null research key is therefore ignored there;
- block-position scans recurse into item handlers for an attached inventory and stop after 100 non-empty slots.

`ScanGeneric` key construction is also preserved: entity keys are `!<entity-id>`; non-damageable item stacks append legacy
metadata directly to `!<registry-id>` with no separator; damageable item stacks omit metadata.

The bytecode contains 71 explicit constructor sites in `ConfigResearch.initScannables`: 34 entity, 18 block, 10 item,
4 ore-dictionary and one each generic/enchantment/potion/material/sky. Enchantment and potion sites are registry loops;
ScanBlock additionally installs companion ScanItem handlers.

## Aspects / AspectList

- exact 37 aspect definitions, RGB values and component pairs are preserved;
- `Aspect.setComponents` does not invalidate old primal/compound caches;
- `AspectList.reduce` keeps an explicit zero entry;
- `remove` removes at zero/below;
- `merge` means maximum, not sum;
- sorting is stable with insertion order for ties.


## Stage 9 continuation (2026-09-11)

Verified/implemented after the Stage 8 handoff:

- NeoForge player attachment/full-state sync source layer, with clone performed by legacy persistence round-trip.
- `LegacyScanItemLogic`: wildcard metadata plus directional template-NBT comparison matching BETA26 crafting scan semantics.
- `SkyScanLogic`: celestial geometry/day keys and the BETA26 night-miss acceptance quirk.
- `LegacyThaumometerTargeting`: BETA26 entity targeting geometry (min distance 1, max 9, >=0.8 box expansion, separate LOS).
- `LegacyScanContainerLogic`: block-top inventory recursion stops after scanning the 100th non-empty slot.
- `LegacyOrderedScanningRegistry`: stable ConfigResearch ordinal + ScanBlock companion sub-order.
- NeoForge `ThaumometerTargeting` projection source and original Thaumometer OBJ/textures/scan sound resources.
- Core regression suite now passes 4552 assertions.

Build caveat remains hard: this environment has Java 21 but no Gradle launcher/wrapper JAR/cache and currently cannot resolve
`services.gradle.org`, so no full NeoForge build/client/server run is claimed.

## Stage 10 critical FIRSTSTEPS vertical slice recovery (2026-10-02)

Recovered from BETA26 bytecode/resources and proved in the pure Java core (not merely copied from chat handoff):

- server-side bootstrap markers `!gotcrystals`, `!gotdream`, `!gotthaumonomicon`, idempotently;
- `RecipeMagicDust`: exactly bowl + flint + redstone + 3 distinct-aspect crystals, minimum grid area 6, bowl/flint remainder;
- legacy Crystal Essence `Aspects:[{key,amount:1}]` custom-data shape plus aspectless random assignment behavior;
- Salis Mundus critical dust gates (`!gotdream` bookshelf, `FIRSTSTEPS@1` crafting table), 50-tick delayed transform model and source-state recheck contract;
- critical ScanAspect registrations before ScanGeneric, with real ordered ScanningService progression and Observation grants;
- minimal persistent chunk-vis core sufficient for Arcane Workbench crafting;
- exact 15-slot Arcane Workbench model: 9 craft + 6 primal crystal slots in AIR/FIRE/WATER/EARTH/ORDER/ENTROPY order;
- exact Thaumometer arcane recipe: `FIRSTSTEPS@2`, base 20 vis, six primal crystals, gold cross around glass pane, truncating discount and >100% negative-cost quirk;
- verified legacy Thaumometer craft hash `1777627067` from BETA26 `ItemStack.toString()` semantics (`1xitem.thaumometer@0`), recorded as `[#]1777627067`;
- server-side FIRSTSTEPS requisites for the critical stages, including unknown stage index -2 behavior;
- end-to-end pure-core regression covering Crystal -> dream -> Salis -> Thaumonomicon -> FIRSTSTEPS -> Arcane Workbench marker -> Thaumometer -> real scanning Observation -> FIRSTSTEPS complete.

Pure core now passes `4667 assertions`. Stage 10 audit is `AUDIT OK`.

Hard limitation remains: this environment has neither a Gradle executable nor `gradle-wrapper.jar`, and DNS resolution for `services.gradle.org` fails. Therefore this is not a successful NeoForge build and the new vertical slice still needs loader registration/events/menu/block/item wiring before it is playable in Minecraft.

## Stage 10 loader wiring continuation (2026-10-03)

Added the first real NeoForge server event bridge for the FIRSTSTEPS bootstrap path:

- `ItemEntityPickupEvent.Post` records `!gotcrystals` only after an authoritative crystal pickup;
- the same post-pickup path records `!gotthaumonomicon` for the actual Thaumonomicon item;
- `PlayerWakeUpEvent` applies the BETA26 `!gotcrystals` -> `!gotdream` gate server-side;
- `PlayerEvent.ItemCraftedEvent` records the verified `[#]405059183` Arcane Workbench craft marker;
- every successful mutation immediately requests a full player-data sync.

This source wiring is intentionally not claimed as Gradle-compiled: the environment still cannot resolve `services.gradle.org` and has no wrapper JAR. Item/block registration and the Salis delayed world transform remain required before this path is playable.

## FIRSTSTEPS authoritative C2S continuation (2026-10-04)

- Added a narrow `FirstStepsProgressPayload` C2S bridge: client packet booleans are accepted, but current stage and requisites are recomputed exclusively from the server player attachment through `FirstStepsServerProgression`.
- Successful progression performs server-side XP side effects from the core result and full authoritative state sync.
- Added the verified BETA26 Thaumometer craft marker to the server crafted-result bridge alongside the Arcane Workbench marker.
- This is source wiring only until a real NeoForge Gradle build succeeds; it is not claimed compiled against NeoForge APIs.

## Stage 10 critical content registration / Salis world bridge (2026-10-04)

Added the first concrete FIRSTSTEPS content registrations and delayed world transform boundary:

- registered `crystal_essence`, `salis_mundus`, `thaumonomicon`, `thaumometer`, and `arcane_workbench` through NeoForge deferred registers;
- Crystal Essence now carries the exact legacy `Aspects:[{key,amount:1}]` payload in `DataComponents.CUSTOM_DATA`, assigning one of all 37 registered BETA26 aspects only on the authoritative side when missing;
- Salis Mundus now preserves the critical `ItemMagicDust` interaction gates and ordering for FIRSTSTEPS, including consume-before-trigger execution and the immediate Arcane Workbench crafting-marker side effect;
- the two critical dust transforms capture the source block state and execute after the BETA26 50-tick delay, aborting if that exact captured state is no longer present;
- bookshelf conversion drops the Thaumonomicon at the legacy offset with zero initial velocity; crafting-table conversion places the registered Arcane Workbench block;
- original BETA26 textures/model geometry needed by this slice were restored into modern singular resource directories, with synchronized EN/RU runtime aliases.

The current audit also hard-gates presence of these loader sources/resources and current EN/RU key symmetry (1469/1469). Loader-independent core regression remains `4667 assertions` and Stage 10 audit remains `AUDIT OK`.

Hard limitation remains unchanged: no real NeoForge Gradle compile/client launch is claimed because this workspace has no `gradle-wrapper.jar`/system Gradle and the execution environment cannot currently retrieve the Gradle distribution. The Arcane Workbench still needs its real block-entity/menu bridge before FIRSTSTEPS is interactively playable.
