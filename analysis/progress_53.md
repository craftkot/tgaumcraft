# Progress milestone — 53%

Verified scope after the post-50% FIRSTSTEPS/aura pass.

## Added since the 50% midpoint audit
- Arcane Workbench client vis readout synced from the server.
- Recipe vis cost is shown before vis/crystal craftability gates, matching BETA26 flow.
- Negative discounted-vis quirk is preserved: a >100% discount never adds aura back.
- Original BETA26 Arcane Workbench GUI texture restored.
- BETA26 crystal requirement swirls restored on the workbench screen.
- Workbench vis moved from block-entity-local storage to persistent per-chunk aura storage.
- BETA26 aura chunk generation arithmetic extracted into parity-tested core logic.
- BETA26 biome aura modifier table preserved, including the final DRY overwrite.
- Exact CasterManager vis-discount arithmetic preserved, including unclamped values.
- Forge 1.12 vanilla BiomeDictionary registrations captured for aura compatibility.
- Aura drain/simulation semantics aligned with BETA26.

## Validation rule
This milestone is considered confirmed only when GitHub Actions is green for this commit:
core parity, NeoForge build, dedicated-server smoke test, and JAR artifact upload.
