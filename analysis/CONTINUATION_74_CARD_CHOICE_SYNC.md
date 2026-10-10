# Continuation: original 33-card metadata and menu preview, 2026-10-10

**Starting head**: `743600cfe`, verified passing all GitHub Actions core tests, NeoForge build and dedicated-server smoke.

## Real implementation

- Added `LegacyTheorycraftChoicePreview` (`21b8e375`): lossless **identity-only** encoding for each original 33 registered card keys into a compact 16-bit vanilla menu DataSlot. ID 0 means absent, 63 means unknown original card. Bits 64/128 retain `fromAid`/`selected`. Unknown class is **never** replaced with another card. Original `cardNBT` remains entirely server-owned; these ID previews cannot activate anything.
- `CoreParityTest` (`c0289cca`) validates every registered card/combination of aid and selected flags, unregistered keys and ID stability.
- `ResearchTableMenu` (`8fef58e1`) synchronizes three original saved CardChoice IDs/flags to the client using three additional server-authoritative DataSlots, including saved choice recovery after world reload.
- `ResearchTableScreen` (`b2a03f2a`) renders three read-only framed previews; selected/aided flags are visually distinguished. English/Russian hover messages and concise headings (`aa1c83f8`, `a42629fc`) explicitly say activation is unavailable. No button ID 2..6 has been exposed; no false gameplay promises.
- Fresh bytecode inspection of actual user-supplied original JAR: obtained **all 33** original `getInspirationCost()`, `getResearchCategory()` and `isAidOnly()` overrides with `javap -c -p`. Added `LegacyTheorycraftCardMetadata` (`cb3a7458`) with costs (25 cards cost +1, four +2, one 0, two -1, one -2), original fixed categories (20), dynamic category fields requiring true initialization (4), and seven aid-only flags.
- `CoreParityTest` (`501e211a`) verifies all 33 mappings explicitly, including dynamic categories, original 7 aid-only cards and excluding two *unregistered* JAR classes `CardTruth` and `CardDragonEgg`. No guessed category is inserted.

## Next blockers

Original `TheorycraftCard.initialize` and class-specific serialized NBT still need complete 33-card adapter initialization, exact source ItemStack/metadata arrays, player XP transactions, client card-choice activation UI and an actual client playtest. There is no full working research loop yet. The random previously-uncollected lunar note option remains final polish; existing readability improvements retained.

**Overall completion estimate 74% only after latest GitHub Actions runs green**. This percentage is the entire mod estimate, NOT the percentage of working gameplay.
