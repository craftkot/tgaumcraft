# Thaumcraft 6.1.BETA26 -> NeoForge 1.21.1 — checkpoint 83: full server theory-card interaction (2026-10-11)

Repository: **craftkot/tgaumcraft**, continuation of checkpoint 82 on original `main=b18aff2cf3a3db94c136ae1c11b7b44d4ea0c555`. Fresh-read handoff, original BETA26 classfiles, exact live adapters, and newest green GitHub Actions before changes. Original JAR was disassembled again with `javap -c -p thaumcraft.common.container.ContainerResearchTable` and `thaumcraft.client.gui.GuiResearchTable`; original buttons **1 keep, 2/3 draw, 4..6 activate, 7 finish, 9 abandon** confirmed. BETA26 client requests **3** only if `bonusDraws>0`, otherwise **2**; drawing costs exactly 1 table paper; activation charges 1 ink and CardScripting charges **2 ink + 1 paper**, plus genuine item and XP rules.

## Actual GitHub commits in this continuation

- `11fc0fe18e6`: reuses existing exact original aspect crystal builder for CardSynthesis input and output.
- `e6c0a2da6c9`: `ResearchTableBlockEntity.activateOriginalTheoryCard`, entire *server synchronous commit*: validates raw original card choice row count and every subclass NBT; checks real inventory, inspiration, paper, ink, XP; stages original all-33 effects against COPIES (including category points/knowledge/warp) and checks again; rejects unsafe reward aspects; atomically consumes planned original item requirements (including duplicates/retained), ink, optional paper and XP; applies staged knowledge/warp and original session snapshot; flags the chosen original saved card NBT; delivers a genuine thaumcraft:crystal_essence Synthesis reward with inventory/drop fallback, and syncs player.
- `af431205621`: prevented a particularly severe **lost research progress** bug: `getResearchList()` is a LIVE view that cannot be passed to mutating `loadSnapshot.clear()` without making a defensive copy.
- `f59eebfb1d5`: fixed preview tooltip previously indexing the card array by *encoded card ID* instead of the actual 0..2 choice index.
- `1a96d2b35b` + `beda57412f`: fixed the alias issue for all loader-neutral callers in `PlayerKnowledgeData.loadSnapshot` and added a parity regression verifying known R-stage and 16 observation points survive.
- `5941de6d61b`: drawing refuses to overwrite any pending or uninterpretable old-world card offer.
- `419b1250e2a`: server menu now actually exposes the original BETA26 numeric IDs 2/3 and 4/5/6, synchronizes `bonusDraws`, and prevents 3-card requests without a real bonus.
- `bd203fc5ecde`: 1.21 GUI retains original art, full-size legible labels and original 3 choice slots; it now allows click-to-activate, conditional 2/3-card paper-funded draws, keep selected, finish and abandon.
- `b5248eb2df` + `446d886e14`: English/Russian draw buttons and updated action tooltips, no more false "preview only" notice.
- `36582e7656`: GitHub CI's **obsolete safety rule requiring disabled menu buttons** was replaced with assertions checking real server atomic stages, exact numeric IDs, bonus protection, translated UI and selected-row save. Old CI failures at `419b125` and `bd203f` were *expected assertions* from that obsolete rule; the fixed head must be verified green.

## Safety and fidelity

No placeholder recipe/item conversions. Unported original requirements fail closed; unrecognized 1.12 ingredient components and dynamically unknown item vis size refuse activation instead of silently charging or awarding arbitrary research. Server never accepts material or card NBT supplied by the client. Pure stage isn't called from a tooltip. Original BETA26 uses a client-side 333ms antispam limiter for selected cards; this port instead relies on main-thread repeated cost/selected-flag checks to prevent double-credit. The original 14 aid card sets and 33 card factory remain intact. New activation is now **exposed**, unlike checkpoint 82. This is not a claim of a manual rendered-game client playtest.

## Further work

1. On the next turn, fresh-read head and handoff and verify current GitHub Actions CI (core parity, NeoForge Gradle build, dedicated server smoke) before reporting completion.
2. Strongly prioritize a manual 1.21.1 client+dedicated-server smoke of at least one full theory run (bookshelf aid, 2/3 draws, paid card activation, keep/finish), and full BETA26 edge behavior (Scripting no free credits, XP DarkWhispers, Synthesis material reward). Avoid claiming manual testing from CI alone.
3. Show real cost and exact requirements in card hover by server-controlled safe metadata; currently tooltip names and localized activation hint only, server correctly rejects unpaid clicks.
4. Continue remaining non-theory systems (alchemy/crucible, infusion, golems, foci, entities/dimension, mod inventory of original assets), and fill missing original aid blocks. Preserve original paper/ink consumption and original JAR parity. User's optional random uncollected moon-phase note QoL is deferred until near completion, and original readable font changes must remain.

**Progress estimate:** 83% ONLY after latest CI success. Percentage is an approximation, NOT a claim that 83% of in-game content was playtested.
