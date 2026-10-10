# Thaumcraft 6.1.BETA26 → NeoForge 1.21.1: milestone 78, filled essentia phials

**Continuation origin:** fresh-read `craftkot/tgaumcraft` main `3a8a47a5`, `analysis/HANDOFF_NEW_CHAT_RU.md` and `analysis/CONTINUATION_77_THEORY_OPTION_POOLS.md`; previous checkpoint's CoreParity, NeoForge build and dedicated-server smoke fully passed.

## Original JAR bytecode audit

Disassembled `thaumcraft.common.items.consumables.ItemPhial` and its superclass `thaumcraft.api.items.ItemGenericEssentiaContainer` directly from the user-supplied ORIGINAL Thaumcraft 1.12.2 6.1.BETA26 JAR with `javap -c -p`:

- `new ItemPhial()` sets registry name `phial`, stack capacity **64**, essentia capacity **10**, original subtypes **empty (metadata 0)** and **filled (metadata 1)**.
- `ItemPhial.makePhial(Aspect, amount)` creates filled metadata-1 phial containing `AspectList{Aspect:amount}` with the historical `{Aspects:[{key:tag,amount:int}]}` NBT. `makeFilledPhial(Aspect)` uses exactly **10**, the item requirement of **CardChannel** and **CardInfuse**.
- `ItemPhial.getAspects` recognizes original `AspectList`; empty phials have no contained aspects.
- Original `assets/thaumcraft/blockstates/phial.json` has separate empty and filled sprites, with a colored overlay. The original `phial.png` was restored exactly as binary; a 16×16 first frame was extracted from the original 16×512 animation strip `phial_overlay.png` for initial client rendering. **Animated 32-frame overlay is NOT yet restored**. Use the original strip, not an invented image, when adding its animation back later.
- Original text: `item.phial.empty.name` = Glass Phial / Стеклянный пузырёк; `item.phial.filled.name` = Phial of %s Essentia / Пузырёк с эссенцией %s.

## Implemented in GitHub

1. `PhialItem.java` (`2a7e3a84`, `f0fa7ac5`, `0e16f647`): original filled and empty phials; exact historical 1..10 aspect amount serialization under modern `DataComponents.CUSTOM_DATA`; subtype model data for variants, original localization and aspect-aware readback, refuse invalid aspects/quantities.
2. `ThaumcraftItems.PHIAL` (`1be4113d`): actual NeoForge `phial` registry item.
3. `NeoForgeTheorycraftMaterialResolver` (`4c941692`) no longer rejects original filled phial requirements. It constructs **genuine 10-essentia** phials for CardChannel (nonconsumed) and CardInfuse (consumed). The atomic per-player inventory preflight already compares actual ItemStack data components.
4. Resources (`a12484edd`, `2f2f994b`): restored original bottle texture and original filled overlay first frame as PNG Git blobs, plus empty/filled item models; no synthetic replacement icons.
5. Original EN/RU names (`63789e5f`, `9c21a44a`) and aspect colors on filled overlay (`b7603fd3`): client-only `RegisterColorHandlersEvent.Item` tints only texture layer 1 from real BETA26 `AspectRegistry.color`, leaving the glass intact.
6. `.github/workflows/ci.yml` (`7af4ce2d`, corrected PNG byte comparison `89d09ae6`): checks actual sprites, model variants and original EN/RU names; builds and starts dedicated server.

**CI caveat:** `2a7e3a84` intermediate red due absent PHIAL registration until later commit (fixed `1be4113d`). `7af4ce2d` red due double-escaped PNG magic in a test, fixed `89d09ae6`. Verify latest CI including `0e16f647` and this handoff commit is green before claiming final verified milestone.

## Still pending

The item currently stores/identifies phials and supports real material matching; **glass phial filling/draining from crucibles, warded jars and related machines is NOT ported yet**. Full original 33-card init/draw/activate dispatch and GUI buttons 2..6 remain deliberately disabled; do not allow imaginary research rewards. Animating the original overlay 32 frames, manual client tests, and additional missing original machinery/content remain.

Original sky-scanning still uses lunar phase. User-requested random **uncollected** moon note option remains final polish.

**Whole-mod completion estimate: 78% after all-green CI**, not 78% user-playable mechanics.
