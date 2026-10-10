# Continuation 2026-10-10: BETA26 card subclass NBT and readable names

Start: `craftkot/tgaumcraft` main `cf86d987`, core + NeoForge Gradle + dedicated server CI all green. All existing research/Thaumometer code and original assets retained.

## Actual changes

- `LegacyTheorycraftCardFields` (`f305524f`, corrected superclass seed type `61cd6eb2`) defines precise original `TheorycraftCard.serialize()` data: `seed` is NBT **long**, and exactly 14 of the registered 33 subclasses add named fields. Types: `CardStudy/Analyze/Reject.cat` strings; `Inspired.cat` string/`amt` int; `Notation.cat1/cat2` strings; `Curio/MindOverMatter/Tinker.stack` original item-stack compounds; `Celestial.md1/md2` ints/`cat` string; `Concentrate/Channel.aspect` strings; `Reactions.aspect1/2` strings; `Synthesis.aspect1/2/3` strings; `Infuse.aspect` string/`stack` compound. All exact names extracted by `javap -c -p` from user-provided original BETA26 JAR. **Never convert the old serialized ItemStack blindly to Minecraft 1.21 items**.
- `LegacyTheorycraftCardTagValidator` (`9206a38a`) checks the actual 33-class/14-subclass NBT type schema, never consumes an item or changes the original tag. `ResearchTableBlockEntity.storeCardChoices/storeLastDrawChoice` (`0b8e75b6`) now refuse to generate malformed new saved card rows. Existing/unknown saved raw NBT still survives autosaves without data loss.
- `CoreParityTest` (`3e271f69`) verifies all 33 class mappings, fourteen original subtype field lists and all 22 historical extra fields. CI **success** for this commit (including NeoForge build + dedicated-server smoke).
- `LegacyTheorycraftCardLocalization` (`e064d0d7`) maps each of 33 original registered classes to its **actual** `getLocalizedName()` key recovered from original bytecode; handles special historical names `mindmatter`, `darkwhisper`, `glyph`; four names Study/Reject/Analyze/Channel expect category/aspect placeholder.
- The original **33 English and Russian** card name translations were extracted from the supplied original JAR, not improvised, and restored to `assets/thaumcraft/lang/en_us.json` and `ru_ru.json` (commits `4a45e2a9`, `4f2d87e8`). Client `ResearchTableScreen` (`517cf2e1`) now renders localized card previews rather than English Java class names; it passes an ellipsis for the four dynamic names until original subclass category is synchronized. Russian/English unknown-card fallbacks provided (`af2235c2`, `9de6213a`).
- Additional `CoreParityTest` (`cbe71eb7`) checks all 33 localization keys and four dynamic argument markers. `.github/workflows/ci.yml` (`9d7162d5`) verifies presence of every original card name in both languages and relevant UI keys.
- Overall estimated **75% only if newest CI is fully green**. Not proof that full 33-card activation works.

## Blockers

Menu draw buttons 2/3 and activation 4..6 must remain disabled until all 33 real card initializers, exact legacy ItemStack ingredients/NBT, client card state and transaction ordering are correctly wired and tested. No manual NeoForge client playtest yet. Original moon scanning remains phase-based; user-requested optional random previously uncollected note is deferred. Readable-font improvements retained.

Continue by fresh-reading main and handoff and then implementing authentic per-card factory initialization **without substituting partial card pools**.
