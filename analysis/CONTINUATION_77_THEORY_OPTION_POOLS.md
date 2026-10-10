# Thaumcraft BETA26 → NeoForge 1.21.1: 77% original item-option parity checkpoint

**Base:** repository `craftkot/tgaumcraft`, fresh-read main `273fcb7d` and main handoff + 76% continuation. Prior CI complete SUCCESS: core parity, Gradle NeoForge compilation and dedicated server smoke.

## Implemented

1. **All 49 original seeded item-option descriptors:** `LegacyTheorycraftOptionPools.java` (`c41398ee`) encodes exact BETA26 `CardMindOverMatter.options` and identical `CardTinker.options` arrays (18 entries each), plus `CardInfuse.options` (31 entries), directly extracted from original JAR by `javap -c -p` static initializers. Keeps original SRG field identity, original order, two different ingot metadata 0 and 2, and specifically **YELLOW Nitor** selection. Original infusion RNG order still chooses compound aspect BEFORE item. The array sizes and seeded picks are tested in `CoreParityTest` (`49c7bade`). **Do not replace unresolved original modded options with a random available one.**
2. **Legacy saved ItemStack support, safe subset:** New `NeoForgeLegacyItemStackDecoder.java` (`c84d9834`, wool correction `54183aa7`) reads original 1.12 `{id,Count,Damage,tag?}` compounds, but resolves only count-1, damage-0, no-extra-tag entries with unchanged registered vanilla IDs or exact ported Thaumometer/Salis Mundus IDs; old `minecraft:wool` damage 0 becomes modern `minecraft:white_wool`. Empty/corrupt/unported/metadata/enchanted stacks are deliberately **rejected rather than modified**. No original cardNBT erased.
3. **Real card material preflight widened:** `NeoForgeTheorycraftMaterialResolver.java` (`bd2fc6af`) now uses that strict decoder for original saved `CardMindOverMatter`/`CardTinker`/`CardInfuse`/`CardCurio` stack requirements, while still rejecting unported phials and unsupported curios and using original previously ported celestial notes/aspect crystals. Original inventory atomic planner remains unchanged; activation buttons 2–6 are **still disabled** pending all 33 real factory initializers.
4. **All 38 vanilla entries deobfuscated and mapped to 1.21 IDs:** Historical `Blocks.field_...` and `Items.field_...` were checked in `Akarin-project/Paper2Srg` 1.12 `Blocks.java`/`Items.java` static initializers (not guessed). New immutable `LegacyTheorycraftVanillaOptionIds.java` (`f4cc44ec`) maps all 16 vanilla options in the artifice pool and 22 in infusion to modern IDs; colored original wool slot maps **white_wool**. `CoreParityTest` (`e3858407`) checks each field, correct count, no omissions and immutable map. Original mod options remain 11 slots across both arrays.
5. **Actual NeoForge option converter:** `NeoForgeTheorycraftOriginalOptionResolver.java` (`dfce5ef0`) builds real 1.21 ItemStacks for any original mapped vanilla option and two already-ported Thaumcraft items; missing TC items/YELLOW Nitor return empty without changing seed/re-rolling. This is material preparation only, not an enabled card factory.

## Tests/limits

- All prior original research, Thaumometer, magic systems and font/RU GUI fixes retained. Original BETA26 JAR must stay private; **never commit it**.
- All 33 original theory-card core formulas exist but full server initialization and GUI button 2/3 draw + 4/5/6 activation are **not playable** yet. Additional item and colored-block registry content must be ported before all 49 options can resolve; Curio and phial NBT conversions need separate audited treatment. Actual client playtest still outstanding.
- User-requested optional randomized uncollected Moon note is deferred to final polish; ORIGINAL phase-dependent SkyScan preserved.
- **CI:** `bd2fc6af` + earlier source commits had full-green GitHub Actions. Verify the newest `54183aa7` and this docs commit CI before asserting latest HEAD green.
- Whole-mod estimate **77% conditional on latest green**, NOT measured feature playability.

Next: per-card initialization of original 33 cards with exact `CardChoice.cardNBT` and inventory requirements, plus normal-vs-aid BETA26 draw pool and safe button 2/3/4/5/6 validation. Continue without starting over.
