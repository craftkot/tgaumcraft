package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Random;

public final class CoreParityTest {
    private static int assertions;

    public static void main(String[] args) {
        testWarp();
        testKnowledge();
        testPearl();
        testPechWand();
        testResearchProgression();
        testAspects();
        testAspectList();
        testLegacyResearchParser();
        testPlayerSnapshot();
        testLegacyResourceIds();
        testResearchCategories();
        testBuiltInResearchCatalog();
        testLegacyPlayerDataCodec();
        testResearchKnowledgeQueries();
        testScanningService();
        testScanKnowledgeLogic();
        testResearchRequisites();
        testLegacyPlayerTransfer();
        testLegacyScanItemLogic();
        testSkyScanLogic();
        testLegacyThaumometerTargeting();
        testLegacyScanContainerLogic();
        testLegacyOrderedScanningRegistry();
        testFirstStepsVerticalSlice();
        testLegacyLapisGenericIdentity();
        testLegacyOreDictionaryScans();
        testCriticalScanHandlers();
        testExpandedVanillaObjectAspects();
        testLegacyOreAspectTable();
        testContainedCrystalAspectScanning();
        testOreMaterialIntegratedScan();
        testEntityAspectRuntimeMappings();
        testFirstStepsServerProgression();
        testFirstStepsEndToEnd();
        System.out.println("Core parity tests: OK (" + assertions + " assertions)");
    }

    private static void testWarp() {
        PlayerWarpData w = new PlayerWarpData();
        eq(0, w.get(WarpType.PERMANENT));
        eq(500, w.add(WarpType.PERMANENT, 999));
        eq(0, w.reduce(WarpType.PERMANENT, 999));
        w.set(WarpType.NORMAL, -20); eq(0, w.get(WarpType.NORMAL));
        w.set(WarpType.TEMPORARY, 501); eq(500, w.get(WarpType.TEMPORARY));
        w.setCounter(-7); eq(-7, w.getCounter());
        w.loadLegacyWarpArray(new int[]{3, 4});
        eq(3, w.get(WarpType.PERMANENT)); eq(4, w.get(WarpType.NORMAL)); eq(0, w.get(WarpType.TEMPORARY));
    }

    private static void testKnowledge() {
        PlayerKnowledgeData k = new PlayerKnowledgeData();
        ResearchIndex idx = key -> key.equals("R") ? 3 : -1;
        isTrue(k.isResearchKnown(""));
        isFalse(k.isResearchKnown(null));
        eq(-1, k.getResearchStage("R"));
        isTrue(k.addResearch("R"));
        eq(0, k.getResearchStage("R"));
        isTrue(k.isResearchKnown("R@0"));
        isFalse(k.isResearchKnown("R@1"));
        isTrue(k.setResearchStage("R", 1));
        isTrue(k.isResearchKnown("R@1"));
        eq(ResearchStatus.IN_PROGRESS, k.getResearchStatus("R", idx));
        k.setResearchStage("R", 3);
        eq(ResearchStatus.IN_PROGRESS, k.getResearchStatus("R", idx)); // TC6 uses > stage count
        k.setResearchStage("R", 4);
        eq(ResearchStatus.COMPLETE, k.getResearchStatus("R", idx));

        isTrue(k.addKnowledge(KnowledgeType.OBSERVATION, "BASICS", 15));
        eq(0, k.getKnowledge(KnowledgeType.OBSERVATION, "BASICS"));
        isTrue(k.addKnowledge(KnowledgeType.OBSERVATION, "BASICS", 1));
        eq(1, k.getKnowledge(KnowledgeType.OBSERVATION, "BASICS"));
        isFalse(k.addKnowledge(KnowledgeType.OBSERVATION, "BASICS", -17));
        eq(16, k.getKnowledgeRaw(KnowledgeType.OBSERVATION, "BASICS"));

        isTrue(k.setResearchFlag("R", ResearchFlag.PAGE));
        isFalse(k.setResearchFlag("R", ResearchFlag.PAGE));
        isTrue(k.hasResearchFlag("R", ResearchFlag.PAGE));
        isTrue(k.clearResearchFlag("R", ResearchFlag.PAGE));
        isFalse(k.hasResearchFlag("R", ResearchFlag.PAGE));
    }

    private static void testPearl() {
        eq(PrimordialPearlLogic.Form.PEARL, PrimordialPearlLogic.formForDamage(0));
        eq(PrimordialPearlLogic.Form.PEARL, PrimordialPearlLogic.formForDamage(2));
        eq(PrimordialPearlLogic.Form.NODULE, PrimordialPearlLogic.formForDamage(3));
        eq(PrimordialPearlLogic.Form.NODULE, PrimordialPearlLogic.formForDamage(5));
        eq(PrimordialPearlLogic.Form.MOTE, PrimordialPearlLogic.formForDamage(6));
        eq(PrimordialPearlLogic.Form.MOTE, PrimordialPearlLogic.formForDamage(7));
        eq(1, PrimordialPearlLogic.craftingRemainderDamage(0));
        eq(7, PrimordialPearlLogic.craftingRemainderDamage(6));
        eq(-1, PrimordialPearlLogic.craftingRemainderDamage(7));
    }

    private static void testPechWand() {
        Random random = new Random(12345L);
        PechWandLogic.Result denied = PechWandLogic.use(false, false, List.of("BASICS"), random);
        isFalse(denied.usable());

        for (int i = 0; i < 1000; i++) {
            PechWandLogic.Result r = PechWandLogic.use(true, i > 0, List.of("BASICS", "ALCHEMY", "AUROMANCY"), random);
            isTrue(r.usable());
            if (i == 0) isTrue(r.unlockFocusPech()); else isFalse(r.unlockFocusPech());
            between(r.observation().rawAmount(), 5, 8);
            between(r.theory().rawAmount(), 6, 8);
        }
    }

    private static void testResearchProgression() {
        // Synthetic entries are chosen to exercise exact BETA26 stage arithmetic rather than game content.
        ResearchCatalog catalog = new ResearchCatalog()
                .add(new ResearchEntryData(
                        "OPEN_ONE",
                        List.of(ResearchStageData.open(3)),
                        List.of(), List.of(), List.of(),
                        List.of(new KnowledgeReward(KnowledgeType.OBSERVATION, "BASICS", 2))))
                .add(new ResearchEntryData(
                        "TWO_STAGE",
                        List.of(ResearchStageData.gated(2), ResearchStageData.gated(4)),
                        List.of(), List.of(), List.of(), List.of()))
                .add(new ResearchEntryData(
                        "AUTO_SECOND",
                        List.of(ResearchStageData.gated(1), ResearchStageData.open(5), ResearchStageData.gated(7)),
                        List.of(), List.of(), List.of(), List.of()))
                .add(new ResearchEntryData(
                        "SIBLING_SOURCE",
                        List.of(ResearchStageData.gated(0)),
                        List.of(), List.of("SIBLING_TARGET"), List.of(), List.of()))
                .add(new ResearchEntryData(
                        "SIBLING_TARGET",
                        List.of(ResearchStageData.open(0)),
                        List.of(), List.of(), List.of(), List.of()))
                .add(new ResearchEntryData(
                        "ADDENDUM_HOST",
                        null,
                        List.of(), List.of(),
                        List.of(new ResearchAddendumData(List.of("UNLOCKER"))),
                        List.of()))
                .add(new ResearchEntryData(
                        "UNLOCKER",
                        List.of(ResearchStageData.open(0)),
                        List.of(), List.of(), List.of(), List.of()));

        ResearchProgressionService service = new ResearchProgressionService(catalog);
        PlayerKnowledgeData k = new PlayerKnowledgeData();
        PlayerWarpData w = new PlayerWarpData();

        // One open stage: BETA26 auto-increments local stage, then stores stage+1 => complete at 2.
        ResearchProgressionService.Result one = service.progress(k, w, "OPEN_ONE", x -> true,
                false, true, false, false, true);
        isTrue(one.progressed());
        isTrue(one.completed());
        eq(2, k.getResearchStage("OPEN_ONE"));
        // Completion path counts the final stage warp once when there was no previous stage.
        eq(3, one.warpGain().permanent() + one.warpGain().normal());
        eq(1, one.warpGain().normal());
        eq(2, one.warpGain().permanent());
        eq(2, k.getKnowledge(KnowledgeType.OBSERVATION, "BASICS"));
        isTrue(k.hasResearchFlag("OPEN_ONE", ResearchFlag.POPUP));
        isTrue(k.hasResearchFlag("OPEN_ONE", ResearchFlag.RESEARCH));
        eq(5, one.xpAward());

        // Two gated stages show the legacy warp quirk: the second progress call completes and counts
        // the previous/final stage according to the bytecode's previousStage reassignment.
        k = new PlayerKnowledgeData(); w = new PlayerWarpData();
        ResearchProgressionService.Result t1 = service.progress(k, w, "TWO_STAGE", x -> true,
                false, false, false, false, true);
        isFalse(t1.completed());
        eq(1, k.getResearchStage("TWO_STAGE"));
        eq(0, t1.warpGain().permanent() + t1.warpGain().normal());
        ResearchProgressionService.Result t2 = service.progress(k, w, "TWO_STAGE", x -> true,
                false, false, false, false, true);
        isFalse(t2.completed());
        eq(2, k.getResearchStage("TWO_STAGE"));
        // previous stage 1 warp is charged on entry to stage 2 (2 + 2 because previousStage remains stage 1).
        eq(4, t2.warpGain().permanent() + t2.warpGain().normal());
        ResearchProgressionService.Result t3 = service.progress(k, w, "TWO_STAGE", x -> true,
                false, false, false, false, true);
        isTrue(t3.completed());
        eq(3, k.getResearchStage("TWO_STAGE"));
        // Completion: old previous stage warp 4 + reassigned final stage warp 4 = 8.
        eq(8, t3.warpGain().permanent() + t3.warpGain().normal());

        // A requirement-free next stage is skipped once, not in a loop.
        k = new PlayerKnowledgeData(); w = new PlayerWarpData();
        service.progress(k, w, "AUTO_SECOND", x -> true, false, false, false, true, true);
        eq(1, k.getResearchStage("AUTO_SECOND"));
        service.progress(k, w, "AUTO_SECOND", x -> true, false, false, false, true, true);
        eq(3, k.getResearchStage("AUTO_SECOND"));

        // Siblings are completed after any successful progress, even if the source itself remains incomplete.
        k = new PlayerKnowledgeData(); w = new PlayerWarpData();
        ResearchProgressionService.Result sibling = service.progress(k, w, "SIBLING_SOURCE", x -> true,
                false, false, false, true, true);
        isTrue(sibling.siblingCompletionAttempts().contains("SIBLING_TARGET"));
        isTrue(k.isResearchComplete("SIBLING_TARGET", catalog));

        // Addenda are unlocked when their dependency research completes, but only for an already-complete host.
        k = new PlayerKnowledgeData(); w = new PlayerWarpData();
        k.addResearch("ADDENDUM_HOST"); // no stages => complete immediately in TC6 status semantics
        ResearchProgressionService.Result unlock = service.progress(k, w, "UNLOCKER", x -> true,
                false, false, false, true, true);
        isTrue(unlock.addendaUnlocked().contains("ADDENDUM_HOST"));
        isTrue(k.hasResearchFlag("ADDENDUM_HOST", ResearchFlag.PAGE));

        // Wuss mode and client side both suppress warp assignment.
        k = new PlayerKnowledgeData(); w = new PlayerWarpData();
        ResearchProgressionService.Result suppressed = service.progress(k, w, "OPEN_ONE", x -> true,
                false, false, false, true, true);
        eq(0, suppressed.warpGain().permanent());
        eq(0, w.get(WarpType.PERMANENT));

        // Requisites and event cancellation reject progress without mutating research.
        k = new PlayerKnowledgeData(); w = new PlayerWarpData();
        isFalse(service.progress(k, w, "OPEN_ONE", x -> false, false, true, false, false, true).progressed());
        isFalse(k.isResearchKnown("OPEN_ONE"));
        isFalse(service.progress(k, w, "OPEN_ONE", x -> true, true, true, false, false, true).progressed());
        isFalse(k.isResearchKnown("OPEN_ONE"));
    }

    private static void testAspects() {
        AspectRegistry r = new AspectRegistry();
        eq(37, r.size());
        String[][] expected = {
            {"AIR", "aer", "16777086", "", "", "e|1"},
            {"EARTH", "terra", "5685248", "", "", "2|1"},
            {"FIRE", "ignis", "16734721", "", "", "c|1"},
            {"WATER", "aqua", "3986684", "", "", "3|1"},
            {"ORDER", "ordo", "14013676", "", "", "7|1"},
            {"ENTROPY", "perditio", "4210752", "", "", "8|771"},
            {"VOID", "vacuos", "8947848", "AIR", "ENTROPY", "|771"},
            {"LIGHT", "lux", "16777152", "AIR", "FIRE", "|1"},
            {"MOTION", "motus", "13487348", "AIR", "ORDER", "|1"},
            {"COLD", "gelum", "14811135", "FIRE", "ENTROPY", "|1"},
            {"CRYSTAL", "vitreus", "8454143", "EARTH", "AIR", "|1"},
            {"METAL", "metallum", "11908557", "EARTH", "ORDER", "|1"},
            {"LIFE", "victus", "14548997", "EARTH", "WATER", "|1"},
            {"DEATH", "mortuus", "6946821", "WATER", "ENTROPY", "|1"},
            {"ENERGY", "potentia", "12648447", "ORDER", "FIRE", "|1"},
            {"EXCHANGE", "permutatio", "5735255", "ENTROPY", "ORDER", "|1"},
            {"MAGIC", "praecantatio", "13566207", "ENERGY", "AIR", "|1"},
            {"AURA", "auram", "16761087", "MAGIC", "AIR", "|1"},
            {"ALCHEMY", "alkimia", "2337949", "MAGIC", "WATER", "|1"},
            {"FLUX", "vitium", "8388736", "ENTROPY", "MAGIC", "|1"},
            {"DARKNESS", "tenebrae", "2236962", "VOID", "LIGHT", "|1"},
            {"ELDRITCH", "alienis", "8409216", "VOID", "DARKNESS", "|1"},
            {"FLIGHT", "volatus", "15198167", "AIR", "MOTION", "|1"},
            {"PLANT", "herba", "109568", "LIFE", "EARTH", "|1"},
            {"TOOL", "instrumentum", "4210926", "METAL", "ENERGY", "|1"},
            {"CRAFT", "fabrico", "8428928", "EXCHANGE", "TOOL", "|1"},
            {"MECHANISM", "machina", "8421536", "MOTION", "TOOL", "|1"},
            {"TRAP", "vinculum", "10125440", "MOTION", "ENTROPY", "|1"},
            {"SOUL", "spiritus", "15461371", "LIFE", "DEATH", "|1"},
            {"MIND", "cognitio", "16356991", "FIRE", "SOUL", "|1"},
            {"SENSES", "sensus", "12648384", "AIR", "SOUL", "|1"},
            {"AVERSION", "aversio", "12603472", "SOUL", "ENTROPY", "|1"},
            {"PROTECT", "praemunio", "49344", "SOUL", "EARTH", "|1"},
            {"DESIRE", "desiderium", "15121988", "SOUL", "VOID", "|1"},
            {"UNDEAD", "exanimis", "3817472", "MOTION", "DEATH", "|1"},
            {"BEAST", "bestia", "10445833", "MOTION", "LIFE", "|1"},
            {"MAN", "humanus", "16766912", "SOUL", "LIFE", "|1"},
        };
        int i = 0;
        for (AspectData a : r.values()) {
            String[] e = expected[i++];
            eq(e[1], a.tag());
            eq(Integer.parseInt(e[2]), a.color());
            String[] tail = e[5].split("\\|", -1);
            eq(Integer.parseInt(tail[1]), a.blend());
            if (e[3].isEmpty()) {
                isTrue(a.isPrimal());
                eq(tail[0], a.chatColor());
                eq(null, a.components());
            } else {
                isFalse(a.isPrimal());
                eq(e[3], a.components().get(0).tag().equals(rFieldTag(r, e[3])) ? e[3] : "bad");
                eq(e[4], a.components().get(1).tag().equals(rFieldTag(r, e[4])) ? e[4] : "bad");
            }
        }
        eq(r.LIGHT, r.combinationResult(r.AIR, r.FIRE));
        eq(r.LIGHT, r.combinationResult(r.FIRE, r.AIR));
        eq(null, r.combinationResult(r.AIR, r.WATER));
        eq(6, r.primalAspects().size());
        eq(31, r.compoundAspects().size());

        // BETA26 caches primal/compound lists and setComponents does not invalidate them.
        r.LIGHT.setComponents(null);
        isTrue(r.LIGHT.isPrimal());
        eq(6, r.primalAspects().size()); // stale cache is intentional compatibility behavior
        eq(31, r.compoundAspects().size());

        boolean duplicateRejected = false;
        try { r.register("aer", 0, null, null, 1); }
        catch (IllegalArgumentException ex) { duplicateRejected = true; }
        isTrue(duplicateRejected);
    }


    private static void testPlayerSnapshot() {
        PlayerThaumcraftData data = new PlayerThaumcraftData();
        data.knowledge().addResearch("R");
        data.knowledge().setResearchStage("R", 3);
        data.knowledge().setResearchFlag("R", ResearchFlag.PAGE);
        data.knowledge().addKnowledge(KnowledgeType.OBSERVATION, "BASICS", 17);
        data.warp().set(WarpType.PERMANENT, 12);
        data.warp().set(WarpType.NORMAL, 34);
        data.warp().set(WarpType.TEMPORARY, 56);
        data.warp().setCounter(-2);

        PlayerThaumcraftSnapshot snap = data.snapshot();
        PlayerThaumcraftData restored = new PlayerThaumcraftData();
        restored.load(snap);
        isTrue(restored.knowledge().isResearchKnown("R@3"));
        isTrue(restored.knowledge().hasResearchFlag("R", ResearchFlag.PAGE));
        eq(17, restored.knowledge().getKnowledgeRaw(KnowledgeType.OBSERVATION, "BASICS"));
        eq(12, restored.warp().get(WarpType.PERMANENT));
        eq(34, restored.warp().get(WarpType.NORMAL));
        eq(56, restored.warp().get(WarpType.TEMPORARY));
        eq(-2, restored.warp().getCounter());

        // Snapshot must be detached from later live mutations.
        data.warp().set(WarpType.PERMANENT, 99);
        data.knowledge().removeResearch("R");
        eq(12, snap.warp().get(0));
        isTrue(snap.research().contains("R"));
    }

    private static void testLegacyResourceIds() {
        isTrue(LegacyResourceIdCompat.isModernResourceId("thaumcraft:nitor"));
        isFalse(LegacyResourceIdCompat.isModernResourceId("thaumcraft:ArcaneBore"));
        eq("thaumcraft:arcanebore", LegacyResourceIdCompat.canonicalLowercase("thaumcraft:ArcaneBore"));
        LegacyResourceIdCompat ids = new LegacyResourceIdCompat();
        eq("thaumcraft:arcanebore", ids.map("thaumcraft:ArcaneBore"));
        eq("thaumcraft:infusionmatrix", ids.map("thaumcraft:InfusionMatrix"));
        eq(2, ids.mappings().size());
    }

    private static void testResearchCategories() {
        ResearchCategoryRegistry categories = new ResearchCategoryRegistry();
        categories.register(new ResearchCategoryData("BASICS", "tc.research_category.BASICS",
                "thaumcraft:textures/research/cat_basic.png", null, null));
        categories.register(new ResearchCategoryData("ALCHEMY", "tc.research_category.ALCHEMY",
                "thaumcraft:textures/research/cat_alchemy.png", null, null));
        eq(2, categories.size());
        isTrue(categories.contains("BASICS"));
        eq("ALCHEMY", categories.get("ALCHEMY").key());
        boolean duplicate = false;
        try { categories.register(new ResearchCategoryData("BASICS", "x", "x", null, null)); }
        catch (IllegalArgumentException expected) { duplicate = true; }
        isTrue(duplicate);

        AspectRegistry a = new AspectRegistry();
        ResearchCategoryRegistry built = BuiltInResearchCategories.create(a);
        eq(7, built.size());
        eq(java.util.List.of("BASICS", "AUROMANCY", "ALCHEMY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH"),
                built.values().stream().map(ResearchCategoryData::key).toList());
        eq(null, built.get("BASICS").researchKey());
        eq("UNLOCKAUROMANCY", built.get("AUROMANCY").researchKey());
        eq("thaumcraft:textures/gui/gui_research_back_over.png", built.get("ELDRITCH").background2());
        eq(20, built.get("AUROMANCY").formula().getAmount(a.AURA));
        eq(30, built.get("ALCHEMY").formula().getAmount(a.ALCHEMY));
        eq(10, built.get("ARTIFICE").formula().getAmount(a.MECHANISM));
        eq(20, built.get("GOLEMANCY").formula().getAmount(a.MAN));
        eq(20, built.get("ELDRITCH").formula().getAmount(a.ELDRITCH));

        AspectListData scanned = new AspectListData().add(a.AIR, 10);
        eq(3, built.get("BASICS").applyFormula(scanned)); // ceil(sqrt(10 * 0.5))
        eq(5, built.get("BASICS").applyFormula(scanned, 2.0D)); // multiplier is squared before sqrt
        eq(0, categories.get("BASICS").applyFormula(scanned)); // no formula in compatibility constructor
    }

    private static void testBuiltInResearchCatalog() {
        ResearchCatalog c = BuiltInResearchCatalog.create();
        eq(148, c.size());
        eq(java.util.Set.of("ALCHEMY", "ARTIFICE", "AUROMANCY", "BASICS", "ELDRITCH", "GOLEMANCY", "INFUSION"), c.categoryKeys());

        java.util.Map<String,Integer> counts = new java.util.HashMap<>();
        int stages = 0, addenda = 0, hidden = 0, round = 0, reverse = 0, spiky = 0, hex = 0, autounlock = 0;
        for (ResearchEntryData e : c.entries()) {
            counts.merge(e.categoryKey(), 1, Integer::sum);
            if (e.stages() != null) stages += e.stages().size();
            addenda += e.addenda().size();
            if (e.hasMeta(ResearchMeta.HIDDEN)) hidden++;
            if (e.hasMeta(ResearchMeta.ROUND)) round++;
            if (e.hasMeta(ResearchMeta.REVERSE)) reverse++;
            if (e.hasMeta(ResearchMeta.SPIKY)) spiky++;
            if (e.hasMeta(ResearchMeta.HEX)) hex++;
            if (e.hasMeta(ResearchMeta.AUTOUNLOCK)) autounlock++;
        }
        eq(271, stages); eq(16, addenda);
        eq(22, counts.get("ALCHEMY")); eq(20, counts.get("ARTIFICE")); eq(23, counts.get("AUROMANCY"));
        eq(32, counts.get("BASICS")); eq(5, counts.get("ELDRITCH")); eq(29, counts.get("GOLEMANCY")); eq(17, counts.get("INFUSION"));
        eq(43, hidden); eq(34, round); eq(18, reverse); eq(10, spiky); eq(12, hex); eq(0, autounlock);

        // ARCANEEAR has the misspelled required_knowledgeauram field in BETA26. It is intentionally ignored,
        // so neither of its stages is knowledge-gated by our generated compatibility projection.
        ResearchEntryData ear = c.get("ARCANEEAR");
        isTrue(ear != null);
        for (ResearchStageData st : ear.stages()) isFalse(st.hasKnowledgeRequirement());

        // Spot-check exact parent stage syntax and real meta projection.
        eq(java.util.List.of("BASEALCHEMY"), c.get("METALLURGY").parents());
        eq(java.util.List.of("METALLURGY@3"), c.get("METALPURIFICATION").parents());
        isTrue(c.get("FIRSTSTEPS").hasMeta(ResearchMeta.ROUND));
        isTrue(c.get("FIRSTSTEPS").hasMeta(ResearchMeta.SPIKY));
        isTrue(c.get("!Wisp").hasMeta(ResearchMeta.HEX));
    }

    private static void testLegacyPlayerDataCodec() {
        ResearchCatalog catalog = BuiltInResearchCatalog.create();
        PlayerThaumcraftData data = new PlayerThaumcraftData();
        data.knowledge().addResearch("METALLURGY");
        data.knowledge().setResearchStage("METALLURGY", 3);
        data.knowledge().setResearchFlag("METALLURGY", ResearchFlag.PAGE);
        data.knowledge().setResearchFlag("METALLURGY", ResearchFlag.RESEARCH);
        data.knowledge().addResearch("BASEALCHEMY");
        data.knowledge().addKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY", 17);
        data.knowledge().addKnowledge(KnowledgeType.THEORY, "ALCHEMY", 33);
        data.warp().set(WarpType.PERMANENT, 11);
        data.warp().set(WarpType.NORMAL, 22);
        data.warp().set(WarpType.TEMPORARY, 33);
        data.warp().setCounter(-9);

        LegacyKnowledgePayload k = LegacyPlayerDataCodec.encodeKnowledge(data.knowledge());
        LegacyWarpPayload w = LegacyPlayerDataCodec.encodeWarp(data.warp());
        eq(2, k.research().size()); eq(2, k.knowledge().size());
        eq(java.util.List.of(11,22,33), w.warp()); eq(-9, w.counter());

        PlayerThaumcraftData restored = new PlayerThaumcraftData();
        LegacyPlayerDataCodec.decodeKnowledge(k, restored.knowledge(), catalog);
        LegacyPlayerDataCodec.decodeWarp(w, restored.warp());
        isTrue(restored.knowledge().isResearchKnown("METALLURGY@3"));
        isTrue(restored.knowledge().hasResearchFlag("METALLURGY", ResearchFlag.PAGE));
        isTrue(restored.knowledge().hasResearchFlag("METALLURGY", ResearchFlag.RESEARCH));
        eq(17, restored.knowledge().getKnowledgeRaw(KnowledgeType.OBSERVATION, "ALCHEMY"));
        eq(33, restored.knowledge().getKnowledgeRaw(KnowledgeType.THEORY, "ALCHEMY"));
        eq(11, restored.warp().get(WarpType.PERMANENT)); eq(22, restored.warp().get(WarpType.NORMAL));
        eq(33, restored.warp().get(WarpType.TEMPORARY)); eq(-9, restored.warp().getCounter());

        // Unknown research flags are ignored. Stage <= 0 is not persisted into the stage map. Knowledge rows are
        // accepted without validation and later duplicate keys win exactly like Map.put in the BETA26 deserializer.
        LegacyKnowledgePayload malformed = new LegacyKnowledgePayload(
                java.util.List.of(
                        new LegacyKnowledgePayload.ResearchRow("X", -2, "PAGE,NOT_A_FLAG"),
                        new LegacyKnowledgePayload.ResearchRow("X", 9, "RESEARCH"),
                        new LegacyKnowledgePayload.ResearchRow("Y", 2, "POPUP")),
                java.util.List.of(
                        new LegacyKnowledgePayload.KnowledgeRow("O_BASICS", -7),
                        new LegacyKnowledgePayload.KnowledgeRow("O_BASICS", 19)));
        PlayerKnowledgeData target = new PlayerKnowledgeData();
        LegacyPlayerDataCodec.decodeKnowledge(malformed, target, null);
        eq(0, target.getResearchStage("X")); // duplicate X row is ignored after first research insertion
        isTrue(target.hasResearchFlag("X", ResearchFlag.PAGE));
        isFalse(target.hasResearchFlag("X", ResearchFlag.RESEARCH));
        eq(2, target.getResearchStage("Y"));
        isTrue(target.hasResearchFlag("Y", ResearchFlag.POPUP));
        eq(19, target.getKnowledgeRaw(KnowledgeType.OBSERVATION, "BASICS"));

        // Serialization drops non-positive raw knowledge exactly as the old NBT writer did.
        LegacyKnowledgePayload reencoded = LegacyPlayerDataCodec.encodeKnowledge(target);
        eq(1, reencoded.knowledge().size());
        eq(19, reencoded.knowledge().get(0).amount());

        // Legacy warp deserialization writes raw array values directly; it does not clamp malformed old NBT.
        PlayerWarpData weirdWarp = new PlayerWarpData();
        LegacyPlayerDataCodec.decodeWarp(new LegacyWarpPayload(java.util.List.of(999, -4, 701, 1234), 42), weirdWarp);
        eq(999, weirdWarp.get(WarpType.PERMANENT)); eq(-4, weirdWarp.get(WarpType.NORMAL)); eq(701, weirdWarp.get(WarpType.TEMPORARY));
        eq(42, weirdWarp.getCounter());

        // Synthetic AUTOUNLOCK proves the post-load behavior even though BETA26's shipped 148 JSON entries contain none.
        ResearchCatalog auto = new ResearchCatalog().add(new ResearchEntryData(
                "AUTO", "BASICS", java.util.Set.of(ResearchMeta.AUTOUNLOCK), null,
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()));
        PlayerKnowledgeData autoTarget = new PlayerKnowledgeData();
        LegacyPlayerDataCodec.decodeKnowledge(new LegacyKnowledgePayload(java.util.List.of(), java.util.List.of()), autoTarget, auto);
        isTrue(autoTarget.isResearchKnown("AUTO"));
    }


    private static void testResearchKnowledgeQueries() {
        PlayerKnowledgeData k = new PlayerKnowledgeData();
        ResearchCatalog catalog = new ResearchCatalog()
                .add(new ResearchEntryData("A", java.util.List.of(ResearchStageData.gated(0)), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()))
                .add(new ResearchEntryData("B", java.util.List.of(ResearchStageData.gated(0)), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()))
                .add(new ResearchEntryData("C", java.util.List.of(ResearchStageData.gated(0)), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()));
        k.addResearch("A");
        k.setResearchStage("A", 2); // complete for one-stage entry
        k.addResearch("B");
        k.setResearchStage("B", 1); // known but not complete

        isTrue(ResearchKnowledgeQueries.knowsResearch(k, "A"));
        isTrue(ResearchKnowledgeQueries.knowsResearch(k, "A&&B"));
        isFalse(ResearchKnowledgeQueries.knowsResearch(k, "A&&C"));
        isTrue(ResearchKnowledgeQueries.knowsResearchStrict(k, catalog, "A"));
        isFalse(ResearchKnowledgeQueries.knowsResearchStrict(k, catalog, "B"));
        isTrue(ResearchKnowledgeQueries.knowsResearchStrict(k, catalog, "B@1"));

        // Literal BETA26 OR bug: a matching branch returns true for the *whole* vararg call,
        // so the later missing C is never checked.
        isTrue(ResearchKnowledgeQueries.knowsResearch(k, "A||C", "C"));
        isTrue(ResearchKnowledgeQueries.knowsResearchStrict(k, catalog, "A||C", "C"));
        // The inverse bug is equally odd: if no OR branch matches, the group itself does not fail.
        isTrue(ResearchKnowledgeQueries.knowsResearch(k, "C||NOPE"));
        isTrue(ResearchKnowledgeQueries.knowsResearchStrict(k, catalog, "C||NOPE"));

        boolean threw = false;
        try { ResearchKnowledgeQueries.knowsResearch(k, (String) null); }
        catch (NullPointerException expected) { threw = true; }
        isTrue(threw);
    }

    private static void testScanningService() {
        ScanningService<String, String> scans = new ScanningService<>();
        int[] success = {0};
        scans.addScannableThing(new ScanThing<>() {
            public boolean checkThing(String player, String thing) { return "target".equals(thing); }
            public String getResearchKey(String player, String thing) { return "R1"; }
            public void onSuccess(String player, String thing) { success[0]++; }
        });
        scans.addScannableThing(new ScanThing<>() {
            public boolean checkThing(String player, String thing) { return "target".equals(thing); }
            public String getResearchKey(String player, String thing) { return null; }
            public void onSuccess(String player, String thing) { success[0]++; }
        });
        scans.addScannableThing(new ScanThing<>() {
            public boolean checkThing(String player, String thing) { return false; }
            public String getResearchKey(String player, String thing) { return "NEVER"; }
        });
        eq(3, scans.size());

        java.util.List<String> progressed = new java.util.ArrayList<>();
        ScanningService.ScanResult r = scans.scanTheThing("p", "target", (p, key) -> {
            progressed.add(key);
            return true;
        });
        isTrue(r.anySuccess()); isTrue(r.anyUnkeyedSuccess());
        eq(2, r.matchingHandlers()); eq(2, r.successfulHandlers());
        eq(ScanningService.Feedback.NONE, r.feedback());
        eq(java.util.List.of("R1"), progressed); eq(2, success[0]);

        // A failed keyed progress skips onSuccess for that handler, but later handlers still run.
        success[0] = 0; progressed.clear();
        r = scans.scanTheThing("p", "target", (p, key) -> false);
        isTrue(r.anySuccess()); isTrue(r.anyUnkeyedSuccess());
        eq(2, r.matchingHandlers()); eq(1, r.successfulHandlers());
        eq(1, success[0]);

        // isThingStillScannable swallows the null-key exception and therefore ignores an unkeyed scanner.
        isTrue(scans.isThingStillScannable("p", "target", (p, key) -> {
            if (key == null) throw new NullPointerException("legacy null research key");
            return false;
        }));
        isFalse(scans.isThingStillScannable("p", "target", (p, key) -> {
            if (key == null) throw new NullPointerException("legacy null research key");
            return true;
        }));

        // getResearchKey is intentionally not cached. For a keyed success BETA26 invokes it five times.
        ScanningService<String, String> stateful = new ScanningService<>();
        int[] calls = {0};
        stateful.addScannableThing(new ScanThing<>() {
            public boolean checkThing(String player, String thing) { return true; }
            public String getResearchKey(String player, String thing) { calls[0]++; return "DYNAMIC"; }
        });
        ScanningService.ScanResult sr = stateful.scanTheThing("p", "x", (p, key) -> true);
        eq(5, calls[0]);
        eq(ScanningService.Feedback.KNOWN_OBJECT, sr.feedback());

        ScanningService<String, String> none = new ScanningService<>();
        none.addScannableThing(new ScanThing<>() {
            public boolean checkThing(String p, String t) { return false; }
            public String getResearchKey(String p, String t) { return "X"; }
        });
        eq(ScanningService.Feedback.UNKNOWN_OBJECT, none.scanTheThing("p", "x", (p,k2) -> true).feedback());
    }


    private static void testScanKnowledgeLogic() {
        AspectRegistry a = new AspectRegistry();
        ResearchCategoryRegistry categories = BuiltInResearchCategories.create(a);
        AspectListData scanned = new AspectListData()
                .add(a.AIR, 10)
                .add(a.MAGIC, 25)
                .add(a.FLUX, 4)
                .add(a.ALCHEMY, 16)
                .add(a.MECHANISM, 9);
        java.util.List<ScanKnowledgeLogic.RawKnowledgeGain> gains = ScanKnowledgeLogic.genericObservationGains(scanned, categories);
        eq(7, gains.size());
        eq(java.util.List.of("BASICS", "AUROMANCY", "ALCHEMY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH"),
                gains.stream().map(ScanKnowledgeLogic.RawKnowledgeGain::categoryKey).toList());
        // Re-evaluate each raw amount through the exact category formula to make the coupling explicit.
        for (ScanKnowledgeLogic.RawKnowledgeGain g : gains) {
            eq(KnowledgeType.OBSERVATION, g.type());
            eq(categories.get(g.categoryKey()).applyFormula(scanned), g.rawAmount());
        }
        eq(java.util.List.of("AUROMANCY", "BASICS", "ALCHEMY"),
                ScanKnowledgeLogic.aspectDiscoveryGains().stream().map(ScanKnowledgeLogic.RawKnowledgeGain::categoryKey).toList());
        for (ScanKnowledgeLogic.RawKnowledgeGain g : ScanKnowledgeLogic.aspectDiscoveryGains()) eq(1, g.rawAmount());

        eq("!thaumcraft:wisp", GenericScanKeyLogic.forEntity("thaumcraft:wisp"));
        eq("!thaumcraft:curio6", GenericScanKeyLogic.forItem("thaumcraft:curio", false, 6));
        eq("!minecraft:iron_sword", GenericScanKeyLogic.forItem("minecraft:iron_sword", true, 77));
    }


    private static void testResearchRequisites() {
        ResearchCatalog c = BuiltInResearchCatalog.create();
        PlayerKnowledgeData k = new PlayerKnowledgeData();
        isTrue(ResearchRequisites.hasRequisites(k, c, "!minecraft:stone0")); // no JSON entry => true
        isFalse(ResearchRequisites.hasRequisites(k, c, "METALLURGY"));
        k.addResearch("BASEALCHEMY");
        // BASEALCHEMY is not complete yet, and ordinary strict parents require completion.
        isFalse(ResearchRequisites.hasRequisites(k, c, "METALLURGY"));
        ResearchEntryData base = c.get("BASEALCHEMY");
        k.setResearchStage("BASEALCHEMY", base.stageCount() + 1);
        isTrue(ResearchRequisites.hasRequisites(k, c, "METALLURGY"));

        ResearchEntryData purification = c.get("METALPURIFICATION");
        eq(java.util.List.of("METALLURGY@3"), purification.parentsStripped());
        k.addResearch("METALLURGY");
        k.setResearchStage("METALLURGY", 2);
        isFalse(ResearchRequisites.hasRequisites(k, c, "METALPURIFICATION"));
        k.setResearchStage("METALLURGY", 3);
        isTrue(ResearchRequisites.hasRequisites(k, c, "METALPURIFICATION"));

        // A leading '~' is stripped only for prerequisite testing; this is exact getParentsStripped behavior.
        eq(java.util.List.of("HEDGEALCHEMY", "TUBES"), c.get("POTIONSPRAYER").parentsStripped());
        eq(java.util.List.of("SEALPROVIDE", "GOLEMDIRECT"), c.get("GOLEMLOGISTICS").parentsStripped());

        ResearchProgressionService service = new ResearchProgressionService(c);
        PlayerKnowledgeData k2 = new PlayerKnowledgeData();
        PlayerWarpData w2 = new PlayerWarpData();
        isFalse(service.progress(k2, w2, "METALLURGY", false, false, false, true, true).progressed());
        k2.addResearch("BASEALCHEMY");
        k2.setResearchStage("BASEALCHEMY", c.get("BASEALCHEMY").stageCount() + 1);
        isTrue(service.progress(k2, w2, "METALLURGY", false, false, false, true, true).progressed());
    }

    private static String rFieldTag(AspectRegistry r, String field) {
        try { return ((AspectData) AspectRegistry.class.getField(field).get(r)).tag(); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void testAspectList() {
        AspectRegistry r = new AspectRegistry();
        AspectListData list = new AspectListData();
        eq(0, list.size()); eq(0, list.visSize());
        list.add(r.FIRE, 2).add(r.AIR, 5).add(r.FIRE, 3);
        eq(5, list.getAmount(r.FIRE));
        eq(5, list.getAmount(r.AIR));
        eq(10, list.visSize());
        eq(r.FIRE, list.getAspects()[0]); // insertion order
        eq(r.AIR, list.getAspects()[1]);

        list.merge(r.FIRE, 2); eq(5, list.getAmount(r.FIRE));
        list.merge(r.FIRE, 9); eq(9, list.getAmount(r.FIRE));
        isTrue(list.reduce(r.AIR, 5));
        eq(0, list.getAmount(r.AIR));
        eq(2, list.size()); // reduce keeps explicit zero entry
        list.remove(r.AIR, 1); eq(1, list.size());

        list.add(r.WATER, 1).add(r.EARTH, 12).add(r.ORDER, -3);
        AspectData[] byAmount = list.getAspectsSortedByAmount();
        eq(r.EARTH, byAmount[0]);
        eq(r.FIRE, byAmount[1]);
        eq(r.WATER, byAmount[2]);
        eq(r.ORDER, byAmount[3]); // non-positive entries don't participate in swaps

        AspectData[] byName = list.getAspectsSortedByName();
        for (int x = 1; x < byName.length; x++) {
            isTrue(byName[x-1].tag().compareTo(byName[x].tag()) <= 0);
        }

        AspectListData copy = list.copy();
        eq(list.visSize(), copy.visSize());
        copy.remove(r.FIRE);
        isTrue(list.getAmount(r.FIRE) > 0); // independent copy
    }

    private static void testLegacyResearchParser() {
        LegacyItemStackSpec a = LegacyResearchParser.parseItemStack("thaumcraft:curio;1;6");
        eq("thaumcraft:curio", a.itemId()); eq(1, a.count()); eq(6, a.meta()); eq(null, a.snbt());

        LegacyItemStackSpec b = LegacyResearchParser.parseItemStack("thaumcraft:phial;1;1;{Aspects:[{amount:10,key:'vitium'}]}");
        eq("thaumcraft:phial", b.itemId()); eq(1, b.count()); eq(1, b.meta());
        eq("{Aspects:[{amount:10,key:'vitium'}]}", b.snbt());

        LegacyItemStackSpec defaults = LegacyResearchParser.parseItemStack("minecraft:hopper");
        eq(1, defaults.count()); eq(0, defaults.meta());
        LegacyItemStackSpec malformed = LegacyResearchParser.parseItemStack("minecraft:stone;wat;3;9");
        eq(3, malformed.count()); eq(9, malformed.meta());

        var parsed = LegacyResearchParser.parseRequirementList(List.of(
                "oredict:chest",
                "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'aer'}]}"));
        eq(2, parsed.size());
        eq("chest", ((LegacyResearchRequirement.OreDictionary) parsed.get(0)).name());
        LegacyItemStackSpec quoted = ((LegacyResearchRequirement.Item) parsed.get(1)).stack();
        eq("{Aspects:[{amount:1,key:\"aer\"}]}", quoted.snbt()); // parseJsonOreList quote replacement

        var categories = java.util.Set.of("BASICS", "AUROMANCY");
        KnowledgeReward kr = LegacyResearchParser.parseKnowledge("OBSERVATION;BASICS;1", categories);
        eq(KnowledgeType.OBSERVATION, kr.type()); eq("BASICS", kr.categoryKey()); eq(1, kr.amount());
        eq(null, LegacyResearchParser.parseKnowledge("THEORY;3", categories)); // hasFields=true in BETA26
        eq(null, LegacyResearchParser.parseKnowledge("OBSERVATION;NOPE;1", categories));
        eq(null, LegacyResearchParser.parseKnowledge("OBSERVATION;BASICS;0", categories));
    }

    private static void between(int value, int min, int max) {
        assertions++;
        if (value < min || value > max) throw new AssertionError(value + " not in [" + min + "," + max + "]");
    }
    private static void isTrue(boolean v) { assertions++; if (!v) throw new AssertionError("expected true"); }
    private static void isFalse(boolean v) { assertions++; if (v) throw new AssertionError("expected false"); }
    private static void testLegacyPlayerTransfer() {
        ResearchCatalog catalog = new ResearchCatalog()
                .add(new ResearchEntryData("KNOWN", List.of(ResearchStageData.open(0)), List.of(), List.of(), List.of(), List.of()))
                .add(new ResearchEntryData("AUTO", "BASICS", java.util.Set.of(ResearchMeta.AUTOUNLOCK), null,
                        List.of(), List.of(), List.of(), List.of()));

        PlayerThaumcraftData source = new PlayerThaumcraftData();
        source.knowledge().addResearch("KNOWN");
        source.knowledge().setResearchStage("KNOWN", 2);
        source.knowledge().setResearchFlag("KNOWN", ResearchFlag.POPUP);
        source.knowledge().addKnowledge(KnowledgeType.OBSERVATION, "BASICS", 5);
        source.knowledge().addKnowledge(KnowledgeType.THEORY, "BASICS", 0);
        source.warp().loadLegacyWarpArray(new int[]{901, -37, 502});
        source.warp().setCounter(-11);

        PlayerThaumcraftData clone = new PlayerThaumcraftData();
        LegacyPlayerTransfer.cloneViaLegacyPersistence(source, clone, catalog);
        isTrue(clone.knowledge().isResearchKnown("KNOWN@2"));
        isTrue(clone.knowledge().isResearchKnown("AUTO")); // post-load AUTOUNLOCK
        eq(5, clone.knowledge().getKnowledgeRaw(KnowledgeType.OBSERVATION, "BASICS"));
        eq(0, clone.knowledge().getKnowledgeRaw(KnowledgeType.THEORY, "BASICS"));
        eq(901, clone.warp().get(WarpType.PERMANENT));
        eq(-37, clone.warp().get(WarpType.NORMAL));
        eq(502, clone.warp().get(WarpType.TEMPORARY));
        eq(-11, clone.warp().getCounter());

        PlayerThaumcraftData client = new PlayerThaumcraftData();
        LegacyPlayerTransfer.ClientApplyResult applied = LegacyPlayerTransfer.applyClientSnapshot(client, clone.snapshot(), catalog);
        eq(1, applied.popupResearch().size());
        eq("KNOWN", applied.popupResearch().get(0));
        isFalse(client.knowledge().hasResearchFlag("KNOWN", ResearchFlag.POPUP));

        clone.knowledge().addResearch("UNKNOWN_SCAN");
        clone.knowledge().setResearchFlag("UNKNOWN_SCAN", ResearchFlag.POPUP);
        applied = LegacyPlayerTransfer.applyClientSnapshot(client, clone.snapshot(), catalog);
        isFalse(applied.popupResearch().contains("UNKNOWN_SCAN"));
        isFalse(client.knowledge().hasResearchFlag("UNKNOWN_SCAN", ResearchFlag.POPUP));
    }

    private static void testLegacyScanItemLogic() {
        java.util.Map<String,String> scanned = new java.util.LinkedHashMap<>();
        scanned.put("Aspects", "[{key:\"aer\",amount:1}]");
        scanned.put("Extra", "1b");
        java.util.Map<String,String> template = java.util.Map.of("Aspects", "[{key:\"aer\",amount:1}]");
        isTrue(LegacyScanItemLogic.matches("thaumcraft:crystal_essence", 4, scanned,
                "thaumcraft:crystal_essence", LegacyScanItemLogic.WILDCARD_META, template));
        isTrue(LegacyScanItemLogic.templateTagsMatch(scanned, template)); // extra scanned NBT ignored
        isFalse(LegacyScanItemLogic.templateTagsMatch(template, scanned)); // comparison is directional
        isFalse(LegacyScanItemLogic.matches("minecraft:stone", 1, null, "minecraft:stone", 0, null));
        isTrue(LegacyScanItemLogic.matches("minecraft:stone", 15, null, "minecraft:stone", 32767, null));
        isFalse(LegacyScanItemLogic.matches("minecraft:dirt", 0, null, "minecraft:stone", 32767, null));
    }

    private static void testSkyScanLogic() {
        SkyScanLogic.Geometry deniedTarget = SkyScanLogic.check(false, -45, true, true, true, -90, 0.0f);
        isFalse(deniedTarget.eligible());
        SkyScanLogic.Geometry deniedPitch = SkyScanLogic.check(true, 1, true, true, true, -90, 0.0f);
        isFalse(deniedPitch.eligible());

        // Daytime requires actual sun alignment. celestialAngle 0 -> transformed 90 degrees.
        SkyScanLogic.Geometry sun = SkyScanLogic.check(true, -90, true, true, true, -90, 0.0f);
        isTrue(sun.eligible());
        isFalse(sun.night());
        isTrue(sun.celestialAligned());
        SkyScanLogic.Geometry dayMiss = SkyScanLogic.check(true, -20, true, true, true, 40, 0.0f);
        isFalse(dayMiss.eligible());

        // Nighttime quirk: once target/pitch/sky/dimension/research pass, geometry may miss and checkThing still returns true.
        SkyScanLogic.Geometry nightMiss = SkyScanLogic.check(true, -1, true, true, true, 47, 0.6f);
        isTrue(nightMiss.night());
        isTrue(nightMiss.eligible());
        isFalse(nightMiss.celestialAligned());

        eq("CEL_0_", SkyScanLogic.dayPrefix(23999));
        eq("CEL_1_", SkyScanLogic.dayPrefix(24000));
        eq("CEL_12_Sun", SkyScanLogic.sunOrMoonKey("CEL_12_", false, 7));
        eq("CEL_12_Moon7", SkyScanLogic.sunOrMoonKey("CEL_12_", true, 7));
        eq("CEL_12_Star1", SkyScanLogic.starKey("CEL_12_", 3));
        isTrue(SkyScanLogic.shouldRemoveOldCelestialResearch("CEL_11_Moon2", "CEL_12_"));
        isFalse(SkyScanLogic.shouldRemoveOldCelestialResearch("CEL_12_Star0", "CEL_12_"));
        isFalse(SkyScanLogic.shouldRemoveOldCelestialResearch("BASEAUROMANCY", "CEL_12_"));
    }

    private static void testLegacyThaumometerTargeting() {
        LegacyThaumometerTargeting.Vec eye = new LegacyThaumometerTargeting.Vec(0, 1.6, 0);
        LegacyThaumometerTargeting.Vec look = new LegacyThaumometerTargeting.Vec(0, 0, 1);
        LegacyThaumometerTargeting.Box qb = LegacyThaumometerTargeting.queryBox(eye,
                new LegacyThaumometerTargeting.Box(-0.3, 0, -0.3, 0.3, 1.8, 0.3), look, 9.0, 0.0);
        eq(-0.3, qb.minX());
        eq(9.3, qb.maxZ());

        LegacyThaumometerTargeting.Candidate<String> tooClose = new LegacyThaumometerTargeting.Candidate<>(
                "close", new LegacyThaumometerTargeting.Vec(0, 1.0, 0.5), new LegacyThaumometerTargeting.Vec(0, 1.6, 0.5),
                new LegacyThaumometerTargeting.Box(-0.1, 0.9, 0.4, 0.1, 1.1, 0.6), true, 0.1f);
        LegacyThaumometerTargeting.Candidate<String> near = new LegacyThaumometerTargeting.Candidate<>(
                "near", new LegacyThaumometerTargeting.Vec(0.7, 1.0, 3), new LegacyThaumometerTargeting.Vec(0.7, 1.6, 3),
                new LegacyThaumometerTargeting.Box(0.65, 0.9, 2.9, 0.75, 1.1, 3.1), true, 0.0f);
        LegacyThaumometerTargeting.Candidate<String> far = new LegacyThaumometerTargeting.Candidate<>(
                "far", new LegacyThaumometerTargeting.Vec(0, 1.0, 5), new LegacyThaumometerTargeting.Vec(0, 1.6, 5),
                new LegacyThaumometerTargeting.Box(-0.2, 0.8, 4.8, 0.2, 1.2, 5.2), true, 0.0f);

        // The near entity is hittable only because TC6 enforces a minimum 0.8 collision border.
        eq("near", LegacyThaumometerTargeting.selectEntity(eye, look, 1.0, 9.0, true,
                java.util.List.of(tooClose, far, near), (v, a, b) -> true));
        // Separate block LOS rejection is part of EntityUtils#getPointedEntity.
        eq("far", LegacyThaumometerTargeting.selectEntity(eye, look, 1.0, 9.0, true,
                java.util.List.of(near, far), (v, a, b) -> !v.equals("near")));
        // With the boolean false, a non-collidable candidate is skipped. Thaumometer passes true.
        LegacyThaumometerTargeting.Candidate<String> ghost = new LegacyThaumometerTargeting.Candidate<>(
                "ghost", new LegacyThaumometerTargeting.Vec(0, 1, 2), new LegacyThaumometerTargeting.Vec(0, 1.6, 2),
                new LegacyThaumometerTargeting.Box(-0.1, 0.9, 1.9, 0.1, 1.1, 2.1), false, 0.0f);
        eq("far", LegacyThaumometerTargeting.selectEntity(eye, look, 1.0, 9.0, false,
                java.util.List.of(ghost, far), (v, a, b) -> true));
        eq("ghost", LegacyThaumometerTargeting.selectEntity(eye, look, 1.0, 9.0, true,
                java.util.List.of(ghost, far), (v, a, b) -> true));

        eq(LegacyThaumometerTargeting.ScanTargetKind.ENTITY, LegacyThaumometerTargeting.targetKind(true, true));
        eq(LegacyThaumometerTargeting.ScanTargetKind.BLOCK, LegacyThaumometerTargeting.targetKind(false, true));
        eq(LegacyThaumometerTargeting.ScanTargetKind.SKY_OR_NULL, LegacyThaumometerTargeting.targetKind(false, false));
    }

    private static void testLegacyScanContainerLogic() {
        java.util.List<Integer> scanned = new java.util.ArrayList<>();
        LegacyScanContainerLogic.Result r = LegacyScanContainerLogic.scanTopInventory(150,
                i -> i, i -> i % 3 == 0, scanned::add);
        eq(100, r.scannedNonEmptySlots());
        isTrue(r.inventoryTooLarge());
        eq(149, r.lastVisitedSlot()); // exactly 100 non-empty values among slots 0..149 when multiples of 3 are empty
        eq(100, scanned.size());
        eq(149, scanned.get(99));

        scanned.clear();
        LegacyScanContainerLogic.Result small = LegacyScanContainerLogic.scanTopInventory(4,
                i -> i, i -> i == 1, scanned::add);
        eq(3, small.scannedNonEmptySlots());
        eq(3, small.lastVisitedSlot());
        isFalse(small.inventoryTooLarge());
        eq(java.util.List.of(0, 2, 3), scanned);
    }

    private static void testLegacyOrderedScanningRegistry() {
        LegacyOrderedScanningRegistry<String, String> ordered = new LegacyOrderedScanningRegistry<>();
        java.util.function.Function<String, ScanThing<String, String>> h = key -> new ScanThing<>() {
            public boolean checkThing(String p, String t) { return true; }
            public String getResearchKey(String p, String t) { return key; }
        };
        ordered.add(70, h.apply("sky"));
        ordered.add(48, h.apply("dragon"));
        ordered.addScanBlockSite(50, java.util.List.of(h.apply("portal-item"), h.apply("gateway-item")), h.apply("portal-block"));
        ordered.add(49, h.apply("totem"));
        java.util.List<String> keys = ordered.entriesInLegacyOrder().stream()
                .map(e -> e.thing().getResearchKey("p", "t")).toList();
        eq(java.util.List.of("dragon", "totem", "portal-item", "gateway-item", "portal-block", "sky"), keys);
        eq(-2, ordered.entriesInLegacyOrder().get(2).subOrder());
        eq(-1, ordered.entriesInLegacyOrder().get(3).subOrder());
        eq(0, ordered.entriesInLegacyOrder().get(4).subOrder());
    }


    private static void testFirstStepsVerticalSlice() {
        PlayerKnowledgeData k = new PlayerKnowledgeData();
        isTrue(FirstStepsBootstrapLogic.onCrystalPickup(k));
        isFalse(FirstStepsBootstrapLogic.onCrystalPickup(k));
        isTrue(FirstStepsBootstrapLogic.onWake(k));
        isFalse(FirstStepsBootstrapLogic.onWake(k));
        eq(FirstStepsBootstrapLogic.DustTarget.THAUMONOMICON_ITEM, FirstStepsBootstrapLogic.dustTarget(k,"minecraft:bookshelf"));
        eq(50, FirstStepsBootstrapLogic.dustDelayTicks());
        isTrue(FirstStepsBootstrapLogic.applyDelayed("minecraft:bookshelf","minecraft:bookshelf"));
        isFalse(FirstStepsBootstrapLogic.applyDelayed("minecraft:bookshelf","minecraft:air"));
        isTrue(FirstStepsBootstrapLogic.onThaumonomiconPickup(k));
        isFalse(FirstStepsBootstrapLogic.onThaumonomiconPickup(k));

        java.util.Map<String,Object> crystal = new java.util.LinkedHashMap<>();
        eq(null, LegacyCrystalEssenceData.singleAspectTag(crystal));
        eq("aer", LegacyCrystalEssenceData.assignRandomIfAspectless(crystal, java.util.List.of("aer"), new java.util.Random(1)));
        eq("aer", LegacyCrystalEssenceData.singleAspectTag(crystal));
        eq("aer", ((java.util.Map<?,?>)((java.util.List<?>)crystal.get("Aspects")).get(0)).get("key"));
        eq(1, ((java.util.Map<?,?>)((java.util.List<?>)crystal.get("Aspects")).get(0)).get("amount"));
        eq("aer", LegacyCrystalEssenceData.assignRandomIfAspectless(crystal, java.util.List.of("ignis"), new java.util.Random(1)));

        var B=RecipeMagicDustLogic.Kind.BOWL; var F=RecipeMagicDustLogic.Kind.FLINT; var R=RecipeMagicDustLogic.Kind.REDSTONE; var C=RecipeMagicDustLogic.Kind.CRYSTAL_ESSENCE;
        java.util.List<RecipeMagicDustLogic.Ingredient> dust = java.util.List.of(
                new RecipeMagicDustLogic.Ingredient(B,null), new RecipeMagicDustLogic.Ingredient(F,null), new RecipeMagicDustLogic.Ingredient(R,null),
                new RecipeMagicDustLogic.Ingredient(C,"aer"), new RecipeMagicDustLogic.Ingredient(C,"ignis"), new RecipeMagicDustLogic.Ingredient(C,"aqua"));
        isTrue(RecipeMagicDustLogic.canFit(2,3)); isFalse(RecipeMagicDustLogic.canFit(1,5)); isTrue(RecipeMagicDustLogic.matches(dust));
        isFalse(RecipeMagicDustLogic.matches(java.util.List.of(dust.get(0),dust.get(1),dust.get(2),dust.get(3),dust.get(3),dust.get(5))));
        isTrue(RecipeMagicDustLogic.returnsContainer(B)); isTrue(RecipeMagicDustLogic.returnsContainer(F)); isFalse(RecipeMagicDustLogic.returnsContainer(R));

        k.addResearch("!gotthaumonomicon"); k.addResearch("FIRSTSTEPS"); k.setResearchStage("FIRSTSTEPS",1);
        eq(FirstStepsBootstrapLogic.DustTarget.ARCANE_WORKBENCH_BLOCK, FirstStepsBootstrapLogic.dustTarget(k,"minecraft:crafting_table"));
        ArcaneWorkbenchRuntime w=new ArcaneWorkbenchRuntime();
        eq(15, ArcaneWorkbenchRuntime.SLOT_COUNT); eq(9,ArcaneWorkbenchRuntime.CRAFT_SLOTS); eq(6,ArcaneWorkbenchRuntime.CRYSTAL_SLOTS);
        eq(java.util.List.of("aer","ignis","aqua","terra","ordo","perditio"), java.util.List.of(ArcaneWorkbenchRuntime.PRIMAL_ORDER));
        for(int i:new int[]{1,3,5,7}) w.set(i,new ArcaneWorkbenchRuntime.Stack("minecraft:gold_ingot",1,null));
        w.set(4,new ArcaneWorkbenchRuntime.Stack("minecraft:glass_pane",1,null));
        for(int i=0;i<6;i++) w.set(9+i,new ArcaneWorkbenchRuntime.Stack("thaumcraft:crystal_essence",1,ArcaneWorkbenchRuntime.PRIMAL_ORDER[i]));
        isFalse(ThaumometerRecipeLogic.matches(k,w));
        k.setResearchStage("FIRSTSTEPS",2);
        isTrue(ThaumometerRecipeLogic.matches(k,w));
        for(int i=0;i<6;i++) isTrue(w.crystalSlotAccepts(9+i,w.get(9+i)));
        isFalse(w.crystalSlotAccepts(9,w.get(10)));
        eq(20,ThaumometerRecipeLogic.discountedVis(0f)); eq(15,ThaumometerRecipeLogic.discountedVis(.25f));
        eq(0,ThaumometerRecipeLogic.discountedVis(1f)); eq(-5,ThaumometerRecipeLogic.discountedVis(1.25f));
        MinimalAuraRuntime aura=new MinimalAuraRuntime(); long ck=123; aura.set(ck,19);
        var fail=ThaumometerRecipeLogic.craft(k,w,aura,ck,0f); isFalse(fail.crafted()); eq(19f,aura.get(ck)); eq(1,w.get(1).count());
        aura.set(ck,20); var ok=ThaumometerRecipeLogic.craft(k,w,aura,ck,0f); isTrue(ok.crafted()); eq(20,ok.visCost()); eq("thaumcraft:thaumometer",ok.outputId()); isTrue(ok.syncRequested()); eq(0f,aura.get(ck)); eq(1777627067,ThaumometerRecipeLogic.LEGACY_CRAFT_HASH); isTrue(k.isResearchKnown("[#]1777627067"));
        for(int i:new int[]{1,3,4,5,7,9,10,11,12,13,14}) eq(0,w.get(i).count());
        aura.set(ck,7); java.util.Map<Long,Float> snap=aura.snapshot(); MinimalAuraRuntime aura2=new MinimalAuraRuntime(); aura2.load(snap); eq(7f,aura2.get(ck));

        // Negative discounted vis is a preserved BETA26 quirk: craft is allowed and aura is not drained.
        ArcaneWorkbenchRuntime w2=new ArcaneWorkbenchRuntime();
        for(int i:new int[]{1,3,5,7}) w2.set(i,new ArcaneWorkbenchRuntime.Stack("minecraft:gold_ingot",1,null));
        w2.set(4,new ArcaneWorkbenchRuntime.Stack("minecraft:glass_pane",1,null));
        for(int i=0;i<6;i++) w2.set(9+i,new ArcaneWorkbenchRuntime.Stack("thaumcraft:crystal_essence",1,ArcaneWorkbenchRuntime.PRIMAL_ORDER[i]));
        aura2.set(ck,3); var neg=ThaumometerRecipeLogic.craft(k,w2,aura2,ck,1.25f); isTrue(neg.crafted()); eq(-5,neg.visCost()); eq(3f,aura2.get(ck));

        // AuraHandler.generateAura arithmetic is preserved independently from loader biome mapping.
        eq(250, AuraGenerationLogic.generatedBase(.5f,.5f,.5f,.5f,.5f,0.0));
        eq(275, AuraGenerationLogic.generatedBase(.5f,.5f,.5f,.5f,.5f,1.0));
        eq(225, AuraGenerationLogic.generatedBase(.5f,.5f,.5f,.5f,.5f,-1.0));
        eq(500, AuraGenerationLogic.generatedBase(2f,2f,2f,2f,2f,0.0));
        eq(0, AuraGenerationLogic.generatedBase(.5f,.5f,.5f,.5f,.5f,-20.0));

        eq(.125f, LegacyBiomeAuraModifiers.modifier("DRY"));
        eq(.75f, LegacyBiomeAuraModifiers.modifier("MAGICAL"));
        eq(.45f, LegacyBiomeAuraModifiers.average(java.util.List.of("FOREST","WET")));
        eq(.5f, LegacyBiomeAuraModifiers.average(java.util.List.of("FOREST","UNKNOWN")));
        eq(29, LegacyBiomeAuraModifiers.table().size());

        eq(.25f, VisDiscountLogic.totalDiscount(25, null, null));
        eq(1.25f, VisDiscountLogic.totalDiscount(125, null, null));
        eq(-.1f, VisDiscountLogic.totalDiscount(0, 0, null));
        eq(-.3f, VisDiscountLogic.totalDiscount(0, null, 2));
        eq(.1f, VisDiscountLogic.totalDiscount(50, 1, 3));

        isTrue(LegacyVanillaBiomeTypes.types("OCEAN").contains("WATER"));
        isTrue(LegacyVanillaBiomeTypes.types("RIVER").contains("WATER"));
        eq(.33f, LegacyVanillaBiomeTypes.auraModifier("OCEAN"));
        eq((.33f + .125f + .25f) / 3.0f, LegacyVanillaBiomeTypes.auraModifier("DESERT"));
        eq(.5f, LegacyVanillaBiomeTypes.auraModifier("MUTATED_PLAINS")); // RARE has no TC biomeInfo row -> catch fallback.
        eq(.5f, LegacyVanillaBiomeTypes.auraModifier("VOID")); // VOID likewise falls through BETA26's exception fallback.
        eq(62, LegacyVanillaBiomeTypes.table().size());

        var hud = ThaumometerHudMath.calculate((short) 525, 262.5f, 0f);
        eq(1f, hud.base()); eq(.5f, hud.vis()); eq(0f, hud.flux());
        eq(42f, hud.visTop()); eq(42f, hud.fluxTop()); eq(8f, hud.baseTop());
        var overflowHud = ThaumometerHudMath.calculate((short) 525, 525f, 525f);
        eq(.5f, overflowHud.base()); eq(.5f, overflowHud.vis()); eq(.5f, overflowHud.flux());
        eq(42f, overflowHud.visTop()); eq(10f, overflowHud.fluxTop()); eq(40f, overflowHud.baseTop());
    }


    private static void testLegacyLapisGenericIdentity() {
        eq("!minecraft:dye4", GenericScanKeyLogic.forModernItem("minecraft:lapis_lazuli", false, 0));
        eq("!minecraft:iron_ingot0", GenericScanKeyLogic.forModernItem("minecraft:iron_ingot", false, 0));
        eq("!minecraft:diamond_sword", GenericScanKeyLogic.forModernItem("minecraft:diamond_sword", true, 20));
        eq("!minecraft:dye4", GenericScanKeyLogic.forItem("minecraft:dye", false, 4));
        String[] dyes = {
            "black", "red", "green", "brown", "blue", "purple", "cyan", "light_gray",
            "gray", "pink", "lime", "yellow", "light_blue", "magenta", "orange", "white"
        };
        for (int metadata = 0; metadata < dyes.length; metadata++) {
            eq("!minecraft:dye" + metadata,
                    GenericScanKeyLogic.forModernItem("minecraft:" + dyes[metadata] + "_dye", false, 0));
        }
        eq("!minecraft:dye0", GenericScanKeyLogic.forModernItem("minecraft:ink_sac", false, 0));
        eq("!minecraft:dye3", GenericScanKeyLogic.forModernItem("minecraft:cocoa_beans", false, 0));
        eq("!minecraft:dye15", GenericScanKeyLogic.forModernItem("minecraft:bone_meal", false, 0));
        String[] blockColors = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
        };
        for (int metadata = 0; metadata < blockColors.length; metadata++) {
            eq("!minecraft:stained_hardened_clay" + metadata,
                    GenericScanKeyLogic.forModernItem("minecraft:" + blockColors[metadata] + "_terracotta", false, 0));
        }
        String[][] legacyFamilies = {
            {"_wool", "wool"}, {"_carpet", "carpet"},
            {"_stained_glass", "stained_glass"},
            {"_stained_glass_pane", "stained_glass_pane"},
            {"_concrete", "concrete"}, {"_concrete_powder", "concrete_powder"}
        };
        for (String[] family : legacyFamilies) {
            for (int metadata = 0; metadata < blockColors.length; metadata++) {
                eq("!minecraft:" + family[1] + metadata,
                        GenericScanKeyLogic.forModernItem(
                                "minecraft:" + blockColors[metadata] + family[0], false, 0));
            }
        }
        String[] wood = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        for (int metadata = 0; metadata < wood.length; metadata++) {
            eq("!minecraft:planks" + metadata,
                    GenericScanKeyLogic.forModernItem("minecraft:" + wood[metadata] + "_planks", false, 0));
            eq("!minecraft:sapling" + metadata,
                    GenericScanKeyLogic.forModernItem("minecraft:" + wood[metadata] + "_sapling", false, 0));
        }
        for (int metadata = 0; metadata < wood.length; metadata++) {
            String oldLog = metadata < 4 ? "log" : "log2";
            int oldMetadata = metadata < 4 ? metadata : metadata - 4;
            String oldLeaves = metadata < 4 ? "leaves" : "leaves2";
            eq("!minecraft:" + oldLog + oldMetadata,
                    GenericScanKeyLogic.forModernItem("minecraft:" + wood[metadata] + "_log", false, 0));
            eq("!minecraft:" + oldLeaves + oldMetadata,
                    GenericScanKeyLogic.forModernItem("minecraft:" + wood[metadata] + "_leaves", false, 0));
        }
        eq("!minecraft:cherry_log0",
                GenericScanKeyLogic.forModernItem("minecraft:cherry_log", false, 0));
        String[][] classicVariants = {
            {"grass_block", "grass", "0"},
            {"short_grass", "tallgrass", "1"}, {"fern", "tallgrass", "2"},
            {"granite", "stone", "1"}, {"polished_granite", "stone", "2"},
            {"diorite", "stone", "3"}, {"polished_diorite", "stone", "4"},
            {"andesite", "stone", "5"}, {"polished_andesite", "stone", "6"},
            {"coarse_dirt", "dirt", "1"}, {"podzol", "dirt", "2"},
            {"red_sand", "sand", "1"},
            {"prismarine_bricks", "prismarine", "1"}, {"dark_prismarine", "prismarine", "2"},
            {"chiseled_quartz_block", "quartz_block", "1"}, {"quartz_pillar", "quartz_block", "2"},
            {"chiseled_sandstone", "sandstone", "1"}, {"cut_sandstone", "sandstone", "2"},
            {"chiseled_red_sandstone", "red_sandstone", "1"},
            {"cut_red_sandstone", "red_sandstone", "2"}
        };
        for (String[] mapping : classicVariants) {
            eq("!minecraft:" + mapping[1] + mapping[2],
                    GenericScanKeyLogic.forModernItem("minecraft:" + mapping[0], false, 0));
        }
        // Block pick stacks must derive the generic key from the item, not
        // from the facing/axis of the world block (BETA26 ScanGeneric).
        eq("!minecraft:dispenser0",
                GenericScanKeyLogic.forModernItem("minecraft:dispenser", false, 0));
        eq("!minecraft:log0",
                GenericScanKeyLogic.forModernItem("minecraft:oak_log", false, 0));
        eq("!minecraft:log2" ,
                GenericScanKeyLogic.forModernItem("minecraft:birch_log", false, 0));
        eq("!minecraft:polished_deepslate0",
                GenericScanKeyLogic.forModernItem("minecraft:polished_deepslate", false, 0));
        eq("!minecraft:smooth_red_sandstone0",
                GenericScanKeyLogic.forModernItem("minecraft:smooth_red_sandstone", false, 0));
        eq("!minecraft:cherry_planks0",
                GenericScanKeyLogic.forModernItem("minecraft:cherry_planks", false, 0));
        eq("!minecraft:mangrove_propagule0",
                GenericScanKeyLogic.forModernItem("minecraft:mangrove_propagule", false, 0));
        // BETA26 bed item metadata is the color index; 1.21 separates bed ids.
        for (int metadata = 0; metadata < blockColors.length; metadata++) {
            eq("!minecraft:bed" + metadata,
                    GenericScanKeyLogic.forModernItem("minecraft:" + blockColors[metadata] + "_bed", false, 0));
        }
        eq("!minecraft:unrecognized_bed0",
                GenericScanKeyLogic.forModernItem("minecraft:unrecognized_bed", false, 0));
        eq("!minecraft:hardened_clay0",
                GenericScanKeyLogic.forModernItem("minecraft:terracotta", false, 0));
        eq("!minecraft:blue_glazed_terracotta0",
                GenericScanKeyLogic.forModernItem("minecraft:blue_glazed_terracotta", false, 0));
        eq("!minecraft:unrecognized_terracotta0",
                GenericScanKeyLogic.forModernItem("minecraft:unrecognized_terracotta", false, 0));
        eq("!minecraft:glow_ink_sac0",
                GenericScanKeyLogic.forModernItem("minecraft:glow_ink_sac", false, 0));
        eq("!minecraft:diamond_sword",
                GenericScanKeyLogic.forModernItem("minecraft:diamond_sword", true, 83));
    }

    private static void testLegacyOreDictionaryScans() {
        eq(13, LegacyOreDictionaryScans.modernTagNames().size());
        eq(13, new java.util.HashSet<>(LegacyOreDictionaryScans.modernTagNames()).size());
        String[][] mapped = {
            {"c:ores/iron", "f_MATIRON"},
            {"c:ingots/iron", "f_MATIRON"},
            {"c:storage_blocks/iron", "f_MATIRON"},
            {"c:plates/iron", "f_MATIRON"},
            {"c:ingots/brass", "f_MATBRASS"},
            {"c:storage_blocks/brass", "f_MATBRASS"},
            {"c:plates/brass", "f_MATBRASS"},
            {"c:ingots/thaumium", "f_MATTHAUMIUM"},
            {"c:storage_blocks/thaumium", "f_MATTHAUMIUM"},
            {"c:plates/thaumium", "f_MATTHAUMIUM"},
            {"c:ingots/void", "f_MATVOID"},
            {"c:storage_blocks/void", "f_MATVOID"},
            {"c:plates/void", "f_MATVOID"}
        };
        for (String[] mapping : mapped) {
            eq(java.util.Set.of(mapping[1]),
                    LegacyOreDictionaryScans.fromCommonTags(java.util.Set.of(mapping[0])));
        }
        eq(java.util.Set.of("f_MATIRON"),
                LegacyOreDictionaryScans.fromCommonTags(
                        java.util.Set.of("c:ores/iron", "c:ingots/iron", "c:plates/iron")));
        eq(java.util.Set.of("f_MATBRASS", "f_MATTHAUMIUM"),
                LegacyOreDictionaryScans.fromCommonTags(
                        java.util.Set.of("c:ingots/brass", "c:plates/thaumium")));
        eq(java.util.Set.of(), LegacyOreDictionaryScans.fromCommonTags(java.util.Set.of(
                "c:raw_materials/iron", "c:nuggets/iron", "c:ingots/gold",
                "c:ingots/void_metal", "c:ores/brass", "c:gems/thaumium")));
        eq(java.util.List.of("f_MATIRON", "f_MATBRASS", "f_MATTHAUMIUM", "f_MATVOID"),
                LegacyVanillaSpecialScans.REGISTRATION_ORDER.subList(9, 13));

        AspectRegistry aspectRegistry = new AspectRegistry();
        var scans = CriticalScanHandlers.createCriticalRegistry(
                aspectRegistry, BuiltInResearchCategories.create(aspectRegistry));
        PlayerKnowledgeData player = new PlayerKnowledgeData();
        var first = scans.scanTheThing(player,
                new CriticalScanHandlers.Target(null, new AspectListData(),
                        LegacyOreDictionaryScans.fromCommonTags(
                                java.util.Set.of("c:ingots/brass", "c:plates/brass"))),
                (p, key) -> p.addResearch(key));
        isTrue(first.anySuccess());
        eq(1, first.successfulHandlers());
        isTrue(player.isResearchKnown("f_MATBRASS"));
        var second = scans.scanTheThing(player,
                new CriticalScanHandlers.Target(null, new AspectListData(),
                        LegacyOreDictionaryScans.fromCommonTags(
                                java.util.Set.of("c:storage_blocks/brass"))),
                (p, key) -> p.addResearch(key));
        isFalse(second.anySuccess());
        eq(0, second.successfulHandlers());
    }

    private static void testCriticalScanHandlers() {
        AspectRegistry aspects=new AspectRegistry(); ResearchCategoryRegistry cats=BuiltInResearchCategories.create(aspects);
        ScanningService<PlayerKnowledgeData,CriticalScanHandlers.Target> scans=CriticalScanHandlers.createCriticalRegistry(aspects,cats);
        eq(54,scans.size()); // 37 ScanAspect + ScanGeneric + sixteen mapped vanilla and OreDictionary research keys.
        PlayerKnowledgeData p=new PlayerKnowledgeData();
        AspectListData targetAspects=new AspectListData().add(aspects.AIR,10).add(aspects.MAGIC,4);
        CriticalScanHandlers.Target t=new CriticalScanHandlers.Target("!minecraft:test0",targetAspects);
        var r=scans.scanTheThing(p,t,(player,key)->player.addResearch(key));
        isTrue(r.anySuccess()); eq(3,r.matchingHandlers()); eq(3,r.successfulHandlers());
        isTrue(p.isResearchKnown("!aer")); isTrue(p.isResearchKnown("!praecantatio")); isTrue(p.isResearchKnown("!minecraft:test0"));
        eq(2+cats.get("BASICS").applyFormula(targetAspects),p.getKnowledgeRaw(KnowledgeType.OBSERVATION,"BASICS"));
        eq(2+cats.get("AUROMANCY").applyFormula(targetAspects),p.getKnowledgeRaw(KnowledgeType.OBSERVATION,"AUROMANCY")); eq(2+cats.get("ALCHEMY").applyFormula(targetAspects),p.getKnowledgeRaw(KnowledgeType.OBSERVATION,"ALCHEMY"));
        // Repeat scan: known keyed handlers fail progressResearch and cannot grant knowledge again.
        var again=scans.scanTheThing(p,t,(player,key)->player.addResearch(key)); isFalse(again.anySuccess());
        eq(2+cats.get("BASICS").applyFormula(targetAspects),p.getKnowledgeRaw(KnowledgeType.OBSERVATION,"BASICS"));
        // A target with enough aspects gets genuine ScanGeneric BASICS Observation.
        PlayerKnowledgeData p2=new PlayerKnowledgeData(); AspectListData rich=new AspectListData().add(aspects.MAGIC,100).add(aspects.AIR,100).add(aspects.EARTH,100);
        var rr=scans.scanTheThing(p2,new CriticalScanHandlers.Target("!rich",rich),(player,key)->player.addResearch(key)); isTrue(rr.anySuccess());
        int expected=cats.get("BASICS").applyFormula(rich)+3; eq(expected,p2.getKnowledgeRaw(KnowledgeType.OBSERVATION,"BASICS")); isTrue(expected>=1);

        // Special BETA26 ScanItem/ScanBlock/ScanEntity registrations do not depend on object aspects.
        PlayerKnowledgeData p3=new PlayerKnowledgeData();
        var special=scans.scanTheThing(p3,
                new CriticalScanHandlers.Target(null,new AspectListData(),java.util.Set.of(LegacyVanillaSpecialScans.TELEPORT)),
                (player,key)->player.addResearch(key));
        isTrue(special.anySuccess()); eq(1,special.successfulHandlers()); isTrue(p3.isResearchKnown("f_TELEPORT"));
        isFalse(scans.isThingStillScannable(p3,
                new CriticalScanHandlers.Target(null,new AspectListData(),java.util.Set.of(LegacyVanillaSpecialScans.TELEPORT)),
                PlayerKnowledgeData::isResearchKnown));

        eq(java.util.Set.of("!DRAGONBREATH"),LegacyVanillaSpecialScans.forItem("minecraft:dragon_breath"));
        eq(java.util.Set.of("!TOTEMUNDYING"),LegacyVanillaSpecialScans.forItem("minecraft:totem_of_undying"));
        eq(java.util.Set.of("f_TELEPORT"),LegacyVanillaSpecialScans.forItem("minecraft:ender_pearl"));
        eq(java.util.Set.of("f_TELEPORT"),LegacyVanillaSpecialScans.forItem("minecraft:end_portal_frame"));
        eq(java.util.Set.of(),LegacyVanillaSpecialScans.forItem("minecraft:ender_eye"));
        eq(java.util.Set.of(),LegacyVanillaSpecialScans.forItem("minecraft:obsidian"));
        eq(java.util.Set.of("f_TELEPORT"),LegacyVanillaSpecialScans.forEntity("minecraft:enderman"));
        eq(java.util.Set.of("f_DISPENSER"),LegacyVanillaSpecialScans.forBlock("minecraft:dispenser"));
        // ScanBlock installs an additional ScanItem matcher; BETA26 also explicitly
        // registers the dispenser item. Legacy clay/terracotta item forms must work.
        eq(java.util.Set.of("f_DISPENSER"),LegacyVanillaSpecialScans.forItem("minecraft:dispenser"));
        eq(java.util.Set.of("f_MATCLAY"),LegacyVanillaSpecialScans.forItem("minecraft:clay_ball"));
        // ScanMaterial.checkThing accepts BlockPos only (BETA26 bytecode).
        eq(java.util.Set.of(),LegacyVanillaSpecialScans.forItem("minecraft:clay"));
        eq(java.util.Set.of("f_MATCLAY"),LegacyVanillaSpecialScans.forBlock("minecraft:clay"));
        // ScanBlock companion ItemStack has metadata 0, unlike the placed block
        // which matches all block states. Confirm the original 1.12 bytecode.
        eq(java.util.Set.of("f_MATCLAY"),LegacyVanillaSpecialScans.forItem("minecraft:terracotta"));
        eq(java.util.Set.of("f_MATCLAY"),LegacyVanillaSpecialScans.forItem("minecraft:white_terracotta"));
        eq(java.util.Set.of(),LegacyVanillaSpecialScans.forItem("minecraft:blue_terracotta"));
        eq(java.util.Set.of("f_MATCLAY"),LegacyVanillaSpecialScans.forBlock("minecraft:blue_terracotta"));
        eq(java.util.Set.of("f_MATIRON"),LegacyVanillaSpecialScans.forItem("minecraft:iron_ingot"));
        eq(java.util.Set.of("f_MATIRON"),LegacyVanillaSpecialScans.forItem("minecraft:iron_ore"));
        eq(java.util.Set.of("f_MATIRON"),LegacyVanillaSpecialScans.forItem("minecraft:iron_block"));
        eq(java.util.Set.of("f_MATIRON"),LegacyVanillaSpecialScans.forBlock("minecraft:iron_ore"));
        eq(java.util.Set.of("f_MATIRON"),LegacyVanillaSpecialScans.forBlock("minecraft:iron_block"));
        eq(java.util.Set.of(),LegacyVanillaSpecialScans.forItem("minecraft:raw_iron"));
        eq(java.util.Set.of(),LegacyVanillaSpecialScans.forItem("minecraft:iron_nugget"));
        // The item identity must actually progress the BETA26 research marker,
        // without inventing an aspect tag for an otherwise unmapped object.
        PlayerKnowledgeData ironKnowledge = new PlayerKnowledgeData();
        var ironScan = scans.scanTheThing(ironKnowledge,
                new CriticalScanHandlers.Target(null, new AspectListData(),
                        LegacyVanillaSpecialScans.forItem("minecraft:iron_ingot")),
                (player, key) -> player.addResearch(key));
        isTrue(ironScan.anySuccess());
        eq(1, ironScan.successfulHandlers());
        isTrue(ironKnowledge.isResearchKnown("f_MATIRON"));
        isFalse(scans.scanTheThing(ironKnowledge,
                new CriticalScanHandlers.Target(null, new AspectListData(),
                        LegacyVanillaSpecialScans.forItem("minecraft:iron_block")),
                (player, key) -> player.addResearch(key)).anySuccess());
        eq(java.util.Set.of(),LegacyVanillaSpecialScans.forItem("minecraft:blue_glazed_terracotta"));
        eq(java.util.Set.of("f_MATCLAY"),LegacyVanillaSpecialScans.forBlock("minecraft:red_terracotta"));
        eq(java.util.Set.of(),LegacyVanillaSpecialScans.forBlock("minecraft:red_glazed_terracotta"));
        eq(java.util.Set.of("f_arrow"),LegacyVanillaSpecialScans.forItem("minecraft:arrow"));
        eq(java.util.Set.of("f_arrow"),LegacyVanillaSpecialScans.forEntity("minecraft:spectral_arrow"));
        eq(java.util.Set.of("f_fireball"),LegacyVanillaSpecialScans.forEntity("minecraft:fireball"));
        eq(java.util.Set.of("f_fireball"),LegacyVanillaSpecialScans.forEntity("minecraft:small_fireball"));
        eq(java.util.Set.of("f_fireball"),LegacyVanillaSpecialScans.forEntity("minecraft:dragon_fireball"));
        eq(java.util.Set.of("f_fireball"),LegacyVanillaSpecialScans.forEntity("minecraft:wither_skull"));
        eq(java.util.Set.of("f_spit"),LegacyVanillaSpecialScans.forEntity("minecraft:llama_spit"));
        eq(java.util.Set.of("f_golem"),LegacyVanillaSpecialScans.forEntity("minecraft:iron_golem"));
        eq(java.util.Set.of("f_golem"),LegacyVanillaSpecialScans.forEntity("minecraft:snow_golem"));
        eq(java.util.Set.of("f_SPIDER"),LegacyVanillaSpecialScans.forEntity("minecraft:spider"));
        eq(java.util.Set.of("f_SPIDER"),LegacyVanillaSpecialScans.forEntity("minecraft:cave_spider"));
        eq(java.util.Set.of("f_BAT","f_FLY"),LegacyVanillaSpecialScans.forEntity("minecraft:bat"));
        eq(java.util.Set.of("f_FLY"),LegacyVanillaSpecialScans.forEntity("minecraft:parrot"));
        eq(java.util.Set.of("f_FLY"),LegacyVanillaSpecialScans.forEntity("minecraft:ghast"));
        eq(java.util.Set.of("f_FLY"),LegacyVanillaSpecialScans.forEntity("minecraft:blaze"));
        eq(java.util.Set.of(),LegacyVanillaSpecialScans.forEntity("minecraft:phantom"));
    }


    private static void testOreMaterialIntegratedScan() {
        AspectRegistry a = new AspectRegistry();
        var scans = CriticalScanHandlers.createCriticalRegistry(
                a, BuiltInResearchCategories.create(a));
        PlayerKnowledgeData p = new PlayerKnowledgeData();
        AspectListData brassAspects = LegacyOreAspectTable.fromCommonTags(
                a, java.util.Set.of("c:ingots/brass"));
        java.util.Set<String> brassMaterial = LegacyOreDictionaryScans.fromCommonTags(
                java.util.Set.of("c:ingots/brass"));
        CriticalScanHandlers.Target brass = new CriticalScanHandlers.Target(
                "!example:brass_ingot0", brassAspects, brassMaterial);
        var first = scans.scanTheThing(p, brass, (player, key) -> player.addResearch(key));
        isTrue(first.anySuccess());
        eq(4, first.matchingHandlers()); // Metallum, Instrumentum, Generic, f_MATBRASS.
        eq(4, first.successfulHandlers());
        for (String key : new String[]{"!metallum", "!instrumentum",
                "!example:brass_ingot0", "f_MATBRASS"}) {
            isTrue(p.isResearchKnown(key));
        }
        isFalse(scans.isThingStillScannable(p, brass, PlayerKnowledgeData::isResearchKnown));
        var repeat = scans.scanTheThing(p, brass, (player, key) -> player.addResearch(key));
        isFalse(repeat.anySuccess());
        eq(0, repeat.successfulHandlers());

        // A brass plate has the same original material research marker, but
        // its own generic item research identity remains scannable.
        CriticalScanHandlers.Target plate = new CriticalScanHandlers.Target(
                "!example:brass_plate0", LegacyOreAspectTable.fromCommonTags(
                        a, java.util.Set.of("c:ingots/brass")),
                LegacyOreDictionaryScans.fromCommonTags(java.util.Set.of("c:plates/brass")));
        var plateResult = scans.scanTheThing(p, plate, (player, key) -> player.addResearch(key));
        isTrue(plateResult.anySuccess());
        eq(1, plateResult.successfulHandlers());
        isTrue(p.isResearchKnown("!example:brass_plate0"));
    }

    private static void testContainedCrystalAspectScanning() {
        AspectRegistry a = new AspectRegistry();
        var single = LegacyContainedAspectTags.fromLegacyNbt(a, java.util.List.of(
                new LegacyContainedAspectTags.Entry("aer", 1)));
        eq(1, single.size());
        eq(1, single.getAmount(a.AIR));

        var merged = LegacyContainedAspectTags.fromLegacyNbt(a, java.util.List.of(
                new LegacyContainedAspectTags.Entry("aer", 3),
                new LegacyContainedAspectTags.Entry("ignis", 2),
                new LegacyContainedAspectTags.Entry("aer", 4),
                new LegacyContainedAspectTags.Entry("terra", -2),
                new LegacyContainedAspectTags.Entry("perditio", 0),
                new LegacyContainedAspectTags.Entry("unknown_aspect", 30)));
        eq(2, merged.size());
        eq(7, merged.getAmount(a.AIR));
        eq(2, merged.getAmount(a.FIRE));
        eq(0, merged.getAmount(a.EARTH));
        eq(0, merged.getAmount(a.ENTROPY));
        var cap = LegacyContainedAspectTags.fromLegacyNbt(a, java.util.List.of(
                new LegacyContainedAspectTags.Entry("praecantatio", 400),
                new LegacyContainedAspectTags.Entry("praecantatio", 400)));
        eq(1, cap.size());
        eq(500, cap.getAmount(a.MAGIC));
        eq(0, LegacyContainedAspectTags.fromLegacyNbt(a, java.util.List.of()).size());

        // An essence can be aspectless until the first server inventory tick;
        // after its legacy NBT is assigned it participates in real ScanAspect
        // and ScanGeneric with exactly the contained amount.
        var categories = BuiltInResearchCategories.create(a);
        var scanning = CriticalScanHandlers.createCriticalRegistry(a, categories);
        PlayerKnowledgeData player = new PlayerKnowledgeData();
        var crystal = new CriticalScanHandlers.Target("!thaumcraft:crystal_essence0", single);
        var first = scanning.scanTheThing(player, crystal, (p, key) -> p.addResearch(key));
        isTrue(first.anySuccess());
        eq(2, first.matchingHandlers());
        eq(2, first.successfulHandlers());
        isTrue(player.isResearchKnown("!aer"));
        isTrue(player.isResearchKnown("!thaumcraft:crystal_essence0"));
        isFalse(scanning.scanTheThing(player, crystal, (p, key) -> p.addResearch(key)).anySuccess());
        eq(0, scanning.isThingStillScannable(player, crystal,
                PlayerKnowledgeData::isResearchKnown) ? 1 : 0);
    }

    private static void testLegacyOreAspectTable() {
        AspectRegistry a = new AspectRegistry();
        // Exact quantities read from original BETA26 ConfigAspects.registerItemAspects
        // ore dictionary registrations; these tags project its legacy ore names.
        String[][] expected = {
            {"c:ores/lapis", "terra:5,sensus:15"},
            {"c:ores/diamond", "terra:5,desiderium:15,vitreus:15"},
            {"c:gems/diamond", "vitreus:15,desiderium:15"},
            {"c:ores/redstone", "terra:5,potentia:15"},
            {"c:ores/emerald", "terra:5,desiderium:10,vitreus:15"},
            {"c:gems/emerald", "vitreus:15,desiderium:10"},
            {"c:ores/quartz", "terra:5,vitreus:10"},
            {"c:gems/quartz", "vitreus:5"},
            {"c:ores/iron", "terra:5,metallum:15"},
            {"c:dusts/iron", "metallum:15,perditio:1"},
            {"c:ingots/iron", "metallum:15"},
            {"c:ores/gold", "terra:5,metallum:10,desiderium:10"},
            {"c:dusts/gold", "metallum:10,desiderium:10,perditio:1"},
            {"c:ingots/gold", "metallum:10,desiderium:10"},
            {"c:dusts/redstone", "potentia:10"},
            {"c:dusts/glowstone", "sensus:5,lux:10"},
            {"c:ingots/copper", "metallum:10,permutatio:5"},
            {"c:dusts/copper", "metallum:10,perditio:1,permutatio:5"},
            {"c:ores/copper", "metallum:10,terra:5,permutatio:5"},
            {"c:ingots/tin", "metallum:10,vitreus:5"},
            {"c:dusts/tin", "metallum:10,perditio:1,vitreus:5"},
            {"c:ores/tin", "metallum:10,terra:5,vitreus:5"},
            {"c:ingots/silver", "metallum:10,desiderium:5"},
            {"c:dusts/silver", "metallum:10,perditio:1,desiderium:5"},
            {"c:ores/silver", "metallum:10,terra:5,desiderium:5"},
            {"c:ingots/lead", "metallum:10,ordo:5"},
            {"c:dusts/lead", "metallum:10,perditio:1,ordo:5"},
            {"c:ores/lead", "metallum:10,terra:5,ordo:5"},
            {"c:ingots/brass", "metallum:10,instrumentum:5"},
            {"c:dusts/brass", "metallum:10,perditio:1,instrumentum:5"},
            {"c:ingots/bronze", "metallum:10,instrumentum:5"},
            {"c:dusts/bronze", "metallum:10,perditio:1,instrumentum:5"},
            {"c:ores/uranium", "metallum:10,mortuus:5,potentia:10"},
            {"c:ingots/uranium", "metallum:10,mortuus:5,potentia:10"},
            {"c:gems/ruby", "vitreus:10,desiderium:10"},
            {"c:gems/green_sapphire", "vitreus:10,desiderium:10"},
            {"c:gems/sapphire", "vitreus:10,desiderium:10"},
            {"c:ingots/steel", "metallum:15,ordo:5"}
        };
        eq(expected.length, LegacyOreAspectTable.modernTagNames().size());
        for (String[] row : expected) {
            isTrue(LegacyOreAspectTable.modernTagNames().contains(row[0]));
            AspectListData tags = LegacyOreAspectTable.fromCommonTags(a, java.util.Set.of(row[0]));
            String[] pairs = row[1].split(",");
            eq(pairs.length, tags.size());
            for (String pair : pairs) {
                String[] entry = pair.split(":");
                eq(Integer.parseInt(entry[1]), tags.getAmount(a.get(entry[0])));
            }
        }
        AspectListData priority = LegacyOreAspectTable.fromCommonTags(a,
                java.util.Set.of("c:ingots/iron", "c:ores/iron"));
        eq(5, priority.getAmount(a.EARTH));
        eq(15, priority.getAmount(a.METAL));
        eq(2, priority.size()); // First legacy registration, not a sum of both.
        eq(0, LegacyOreAspectTable.fromCommonTags(a, java.util.Set.of(
                "c:raw_materials/iron", "c:nuggets/brass", "c:ingots/void")).size());
    }

    private static void testExpandedVanillaObjectAspects() {
        AspectRegistry a = new AspectRegistry();
        // Exact 1.12 ConfigAspects registerObjectTag names / AspectList additions,
        // not modern guesses from similar-looking new Minecraft items.
        String[][] sources = {
            {"minecraft:stone", "terra:5"},
            {"minecraft:granite", "terra:5"},
            {"minecraft:andesite", "terra:5"},
            {"minecraft:cobblestone", "terra:5", "perditio:1"},
            {"minecraft:sand", "terra:5", "perditio:5"},
            {"minecraft:grass_block", "terra:5", "herba:2"},
            {"minecraft:end_stone", "terra:5", "tenebrae:5"},
            {"minecraft:gravel", "terra:5", "perditio:2"},
            {"minecraft:netherrack", "terra:5", "ignis:2"},
            {"minecraft:nether_brick", "ignis:1", "ordo:1"},
            {"minecraft:glass", "vitreus:5"},
            {"minecraft:obsidian", "terra:5", "ignis:5", "tenebrae:5"},
            {"minecraft:oak_log", "herba:20"},
            {"minecraft:dark_oak_log", "herba:20"},
            {"minecraft:oak_sapling", "herba:15", "victus:5"},
            {"minecraft:birch_leaves", "herba:5"},
            {"minecraft:vine", "herba:5"},
            {"minecraft:nether_wart", "herba:1", "vitium:2", "alkimia:3"},
            {"minecraft:cactus", "herba:5", "aqua:5", "aversio:1"},
            {"minecraft:sugar_cane", "herba:5", "aqua:3", "aer:2"},
            {"minecraft:wheat", "herba:5", "victus:5"},
            {"minecraft:carrot", "herba:5", "victus:5", "sensus:5"},
            {"minecraft:potato", "herba:5", "victus:5", "terra:5"},
            {"minecraft:string", "bestia:5", "fabrico:1"},
            {"minecraft:slime_ball", "aqua:5", "victus:5", "alkimia:1"},
            {"minecraft:leather", "bestia:5", "praemunio:5"},
            {"minecraft:feather", "volatus:5", "aer:5"},
            {"minecraft:bone", "mortuus:5", "victus:5"},
            {"minecraft:egg", "victus:5", "bestia:5"},
            {"minecraft:gunpowder", "ignis:10", "perditio:10", "alkimia:5"},
            {"minecraft:ender_pearl", "alienis:10", "motus:15"},
            {"minecraft:nether_star", "alienis:10", "praecantatio:20", "ordo:20", "auram:10"}
        };
        for (String[] row : sources) {
            AspectListData aspects = LegacyObjectAspectTable.get(a, row[0]);
            eq(row.length - 1, aspects.size());
            for (int i = 1; i < row.length; i++) {
                String[] pair = row[i].split(":");
                eq(Integer.parseInt(pair[1]), aspects.getAmount(a.get(pair[0])));
            }
        }
        for (String wood : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"}) {
            eq(20, LegacyObjectAspectTable.get(a, "minecraft:" + wood + "_log").getAmount(a.PLANT));
            eq(15, LegacyObjectAspectTable.get(a, "minecraft:" + wood + "_sapling").getAmount(a.PLANT));
            eq(5, LegacyObjectAspectTable.get(a, "minecraft:" + wood + "_leaves").getAmount(a.PLANT));
        }
        eq(0, LegacyObjectAspectTable.get(a, "minecraft:cherry_log").size());
        eq(0, LegacyObjectAspectTable.get(a, "minecraft:raw_copper").size());
        // Exact standalone BETA26 registerObjectTag(ItemStack) from ConfigAspects.
        AspectListData salis = LegacyObjectAspectTable.get(a, "thaumcraft:salis_mundus");
        eq(2, salis.size());
        eq(5, salis.getAmount(a.MAGIC));
        eq(5, salis.getAmount(a.ENERGY));

    }

    private static void testEntityAspectRuntimeMappings() {
        AspectRegistry a = new AspectRegistry();
        eq("Guardian", LegacyEntityAspectTable.legacyName("minecraft:guardian"));
        eq("Guardian", LegacyEntityAspectTable.legacyName("minecraft:elder_guardian"));
        AspectListData guardian = LegacyEntityAspectTable.get(a, "Guardian", false, false);
        eq(10, guardian.getAmount(a.BEAST)); eq(10, guardian.getAmount(a.ELDRITCH)); eq(10, guardian.getAmount(a.WATER));
        AspectListData elder = LegacyEntityAspectTable.get(a, "Guardian", false, true);
        eq(10, elder.getAmount(a.BEAST)); eq(15, elder.getAmount(a.ELDRITCH)); eq(15, elder.getAmount(a.WATER));
        AspectListData creeper = LegacyEntityAspectTable.get(a, "Creeper", false, false);
        eq(15, creeper.getAmount(a.PLANT)); eq(15, creeper.getAmount(a.FIRE)); eq(0, creeper.getAmount(a.ENERGY));
        AspectListData powered = LegacyEntityAspectTable.get(a, "Creeper", true, false);
        eq(15, powered.getAmount(a.PLANT)); eq(15, powered.getAmount(a.FIRE)); eq(15, powered.getAmount(a.ENERGY));
    }


    private static void testFirstStepsServerProgression() {
        PlayerKnowledgeData k=new PlayerKnowledgeData(); FirstStepsServerProgression gate=new FirstStepsServerProgression();
        eq(-2,gate.requestedStageIndex(k)); isFalse(gate.requisitesSatisfied(k)); eq(405059183,FirstStepsServerProgression.ARCANE_WORKBENCH_CRAFT_HASH);
        k.addResearch("!gotthaumonomicon"); k.addResearch("FIRSTSTEPS"); k.setResearchStage("FIRSTSTEPS",1); eq(0,gate.requestedStageIndex(k)); isFalse(gate.requisitesSatisfied(k));
        k.addResearch(FirstStepsServerProgression.ARCANE_WORKBENCH_MARKER); isTrue(gate.requisitesSatisfied(k));
        k.setResearchStage("FIRSTSTEPS",2); isFalse(gate.requisitesSatisfied(k));
        k.addResearch(ThaumometerRecipeLogic.LEGACY_CRAFT_MARKER); k.addKnowledge(KnowledgeType.OBSERVATION,"BASICS",15); isFalse(gate.requisitesSatisfied(k));
        k.addKnowledge(KnowledgeType.OBSERVATION,"BASICS",1); isTrue(gate.requisitesSatisfied(k));
        gate.setNoFlagsFromPacket(true); isTrue(gate.noFlagsLatch());
        ResearchProgressionService svc=new ResearchProgressionService(BuiltInResearchCatalog.create()); PlayerWarpData warp=new PlayerWarpData();
        var r=gate.request(k,warp,svc,true,true,false); isTrue(r.progressed());
        // Depending on sibling completion, the legacy global latch can be cleared before FIRSTSTEPS itself completes.
        isTrue(k.getResearchStage("FIRSTSTEPS")>=3);
        var mismatch=gate.request(k,warp,svc,false,false,false); isFalse(mismatch.progressed());
    }


    private static void testFirstStepsEndToEnd() {
        PlayerKnowledgeData k=new PlayerKnowledgeData(); PlayerWarpData warp=new PlayerWarpData(); ResearchCatalog cat=BuiltInResearchCatalog.create(); ResearchProgressionService svc=new ResearchProgressionService(cat);
        // Crystal -> dream -> Salis Mundus -> bookshelf -> Thaumonomicon.
        isTrue(FirstStepsBootstrapLogic.onCrystalPickup(k)); isTrue(FirstStepsBootstrapLogic.onWake(k));
        var dust=java.util.List.of(new RecipeMagicDustLogic.Ingredient(RecipeMagicDustLogic.Kind.BOWL,null),new RecipeMagicDustLogic.Ingredient(RecipeMagicDustLogic.Kind.FLINT,null),new RecipeMagicDustLogic.Ingredient(RecipeMagicDustLogic.Kind.REDSTONE,null),new RecipeMagicDustLogic.Ingredient(RecipeMagicDustLogic.Kind.CRYSTAL_ESSENCE,"aer"),new RecipeMagicDustLogic.Ingredient(RecipeMagicDustLogic.Kind.CRYSTAL_ESSENCE,"ignis"),new RecipeMagicDustLogic.Ingredient(RecipeMagicDustLogic.Kind.CRYSTAL_ESSENCE,"aqua"));
        isTrue(RecipeMagicDustLogic.matches(dust)); eq(FirstStepsBootstrapLogic.DustTarget.THAUMONOMICON_ITEM,FirstStepsBootstrapLogic.dustTarget(k,"minecraft:bookshelf"));
        isTrue(FirstStepsBootstrapLogic.onThaumonomiconPickup(k));
        var start=svc.progress(k,warp,"FIRSTSTEPS",false,true,false,false,true); isTrue(start.progressed()); eq(1,k.getResearchStage("FIRSTSTEPS"));
        // Salis on crafting table fires the crafting event for the result before delayed replacement in BETA26.
        eq(FirstStepsBootstrapLogic.DustTarget.ARCANE_WORKBENCH_BLOCK,FirstStepsBootstrapLogic.dustTarget(k,"minecraft:crafting_table")); k.addResearch(FirstStepsServerProgression.ARCANE_WORKBENCH_MARKER);
        FirstStepsServerProgression gate=new FirstStepsServerProgression(); isTrue(gate.requisitesSatisfied(k)); isTrue(gate.request(k,warp,svc,true,true,false).progressed()); eq(2,k.getResearchStage("FIRSTSTEPS"));
        // Workbench + six primal crystals + exact recipe -> thaumometer + legacy craft marker.
        ArcaneWorkbenchRuntime w=new ArcaneWorkbenchRuntime(); for(int i:new int[]{1,3,5,7})w.set(i,new ArcaneWorkbenchRuntime.Stack("minecraft:gold_ingot",1,null)); w.set(4,new ArcaneWorkbenchRuntime.Stack("minecraft:glass_pane",1,null)); for(int i=0;i<6;i++)w.set(9+i,new ArcaneWorkbenchRuntime.Stack("thaumcraft:crystal_essence",1,ArcaneWorkbenchRuntime.PRIMAL_ORDER[i]));
        MinimalAuraRuntime aura=new MinimalAuraRuntime(); aura.set(77,100); var craft=ThaumometerRecipeLogic.craft(k,w,aura,77,0f); isTrue(craft.crafted()); eq(80f,aura.get(77)); isTrue(k.isResearchKnown(ThaumometerRecipeLogic.LEGACY_CRAFT_MARKER));
        // Real ScanAspect/ScanGeneric path supplies the required BASICS observation (16 raw = level 1).
        AspectRegistry ar=new AspectRegistry(); ResearchCategoryRegistry cats=BuiltInResearchCategories.create(ar); var scans=CriticalScanHandlers.createCriticalRegistry(ar,cats);
        int n=0; while(k.getKnowledge(KnowledgeType.OBSERVATION,"BASICS")<1 && n<20){ AspectData a=ar.values().stream().toList().get(n); AspectListData al=new AspectListData().add(a,100); String key="!e2e"+n; scans.scanTheThing(k,new CriticalScanHandlers.Target(key,al),(player,rk)->player.addResearch(rk)); n++; }
        isTrue(k.getKnowledge(KnowledgeType.OBSERVATION,"BASICS")>=1); isTrue(gate.requisitesSatisfied(k)); isTrue(gate.request(k,warp,svc,true,true,false).progressed());
        // Stage 3 has no requisites; one final server progression completes FIRSTSTEPS at stageCount+1 == 4.
        isTrue(gate.requisitesSatisfied(k)); isTrue(gate.request(k,warp,svc,true,true,false).progressed()); eq(4,k.getResearchStage("FIRSTSTEPS")); isTrue(k.isResearchComplete("FIRSTSTEPS",cat));
    }

    private static void eq(Object a, Object b) { assertions++; if (!java.util.Objects.equals(a,b)) throw new AssertionError("expected " + a + " got " + b); }
}
