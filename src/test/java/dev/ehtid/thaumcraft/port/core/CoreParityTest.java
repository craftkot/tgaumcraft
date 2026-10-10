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
        testLegacyCelestialScanService();
        testLegacyCelestialTheoryCard();
        testLegacyResearchTableData();
        testLegacyResearchTableExtendedSaveFields();
        testLegacyTheorycraftDrawEngine();
        testLegacyBasicTheoryCards();
        testLegacyAdditionalTheoryCards();
        testLegacyTheorycraftManifest();
        testLegacyTheorycraftAidCards();
        testLegacyTheorycraftAidScanner();
        testLegacyTheorycraftMiscCards();
        testLegacyCardAnalyzeBugfix();
        testLegacySpecialtyTheoryCards();
        testLegacyAlchemyTheoryCards();
        testLegacyLateTheoryCards();
        testLegacyMaterialTheoryCards();
        testLegacyTheorycraftInventoryPlanner();
        testLegacyTheorycraftChoiceFlow();
        testLegacyTheorycraftChoicePreview();
        testLegacyTheorycraftCardMetadata();
        testLegacyTheorycraftInspiration();
        testLegacyTheorycraftAvailableCategories();
        testLegacyThaumometerTargeting();
        testLegacyScanContainerLogic();
        testLegacyOrderedScanningRegistry();
        testFirstStepsVerticalSlice();
        testLegacyLapisGenericIdentity();
        testLegacyOreDictionaryScans();
        testLegacyEnchantmentEffectScans();
        testLegacyEnchantmentAspectBonuses();
        testLegacyObjectAspectFinalizer();
        testCriticalScanHandlers();
        testExpandedVanillaObjectAspects();
        testLegacyOreAspectTable();
        testContainedCrystalAspectScanning();
        testLegacyEquipmentBonuses();
        testLegacyDyeBonuses();
        testOreMaterialIntegratedScan();
        testLegacyPlayerEntityAspects();
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

    private static void testLegacyCelestialScanService() {
        SkyScanLogic.Geometry sun = SkyScanLogic.check(
                true, -90, true, true, true, -90, 0.0f);
        SkyScanLogic.Geometry blocked = SkyScanLogic.check(
                false, -90, true, true, true, -90, 0.0f);
        var denied = LegacyCelestialScanService.examine(
                blocked, 24000, 0, 2, java.util.Set.of(), true, true);
        eq(LegacyCelestialScanService.Status.NOT_SCANNABLE, denied.status());
        eq(null, denied.observation());

        var day = LegacyCelestialScanService.examine(
                sun, 49000, 0, 2, java.util.Set.of(), true, true);
        eq(LegacyCelestialScanService.Status.READY, day.status());
        eq(LegacyCelestialScanService.Kind.SUN, day.observation().kind());
        eq("CEL_2_Sun", day.observation().researchKey());
        eq("CEL_2_", day.observation().dayPrefix());
        eq(0, day.observation().itemMetadata());
        isFalse(day.observation().night());

        var noPen = LegacyCelestialScanService.examine(
                sun, 49000, 0, 2, java.util.Set.of(), false, true);
        eq(LegacyCelestialScanService.Status.NEEDS_MATERIALS, noPen.status());
        var noPaper = LegacyCelestialScanService.examine(
                sun, 49000, 0, 2, java.util.Set.of(), true, false);
        eq(LegacyCelestialScanService.Status.NEEDS_MATERIALS, noPaper.status());
        var duplicate = LegacyCelestialScanService.examine(
                sun, 49000, 0, 2, java.util.Set.of("CEL_2_Sun"), false, false);
        eq(LegacyCelestialScanService.Status.ALREADY_SEEN, duplicate.status());
        eq(0, duplicate.observation().itemMetadata());

        // The original ScanSky generates moon notes based on ACTUAL moon phase,
        // not on any random missing note; preserve this until final polish.
        var alignedMoon = new SkyScanLogic.Geometry(true, true, true, 89, 0, 89);
        for (int phase = 0; phase < 8; phase++) {
            var result = LegacyCelestialScanService.examine(
                    alignedMoon, 24000L * (10 + phase), phase, 2,
                    java.util.Set.of(), true, true);
            eq(LegacyCelestialScanService.Status.READY, result.status());
            eq(LegacyCelestialScanService.Kind.MOON, result.observation().kind());
            eq(5 + phase, result.observation().itemMetadata());
            eq("CEL_" + (10 + phase) + "_Moon" + phase,
                    result.observation().researchKey());
        }

        var stars = new SkyScanLogic.Geometry(true, true, false, 75, 20, 20);
        for (int facing = 2; facing <= 5; facing++) {
            var result = LegacyCelestialScanService.examine(
                    stars, 50000, 4, facing, java.util.Set.of(), true, true);
            eq(LegacyCelestialScanService.Status.READY, result.status());
            eq(LegacyCelestialScanService.Kind.STAR, result.observation().kind());
            eq(facing - 1, result.observation().itemMetadata());
            eq("CEL_2_Star" + (facing - 2), result.observation().researchKey());
        }
        eq(LegacyCelestialScanService.Status.NOT_SCANNABLE,
                LegacyCelestialScanService.examine(
                        stars, 50000, 4, 0, java.util.Set.of(), true, true).status());

        PlayerKnowledgeData knowledge = new PlayerKnowledgeData();
        knowledge.addResearch("CEL_1_Sun");
        knowledge.addResearch("CEL_2_Moon0");
        knowledge.addResearch("CEL_2_Star3");
        knowledge.addResearch("FIRSTSTEPS");
        LegacyCelestialScanService.removeOldDayResearch(knowledge, "CEL_2_");
        isFalse(knowledge.isResearchKnown("CEL_1_Sun"));
        isTrue(knowledge.isResearchKnown("CEL_2_Moon0"));
        isTrue(knowledge.isResearchKnown("CEL_2_Star3"));
        isTrue(knowledge.isResearchKnown("FIRSTSTEPS"));
    }

    private static void testLegacyTheorycraftAvailableCategories() {
        ResearchCatalog catalog = new ResearchCatalog();
        var stage = java.util.List.of(new ResearchStageData(
                false, false, false, false, 0));
        catalog.add(new ResearchEntryData("AURO_ENTRY", "BASICS",
                java.util.Set.of(), stage, java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of()));
        ResearchCategoryRegistry registry = new ResearchCategoryRegistry()
                .register(new ResearchCategoryData("BASICS", "BASICS", "x", null, null))
                .register(new ResearchCategoryData("AUROMANCY", "AUROMANCY", "x",
                        null, null, "AURO_ENTRY", null))
                .register(new ResearchCategoryData("ALCHEMY", "ALCHEMY", "x",
                        null, null, null, null));
        PlayerKnowledgeData player = new PlayerKnowledgeData();
        eq(java.util.List.of("BASICS", "ALCHEMY"),
                LegacyResearchTableData.availableCategories(
                        player, catalog, registry, java.util.Set.of()));
        player.addResearch("AURO_ENTRY");
        eq(java.util.List.of("BASICS", "ALCHEMY"),
                LegacyResearchTableData.availableCategories(
                        player, catalog, registry, java.util.Set.of())); // Strict completion.
        player.setResearchStage("AURO_ENTRY", 2);
        eq(java.util.List.of("BASICS", "AUROMANCY", "ALCHEMY"),
                LegacyResearchTableData.availableCategories(
                        player, catalog, registry, java.util.Set.of()));
        eq(java.util.List.of("BASICS", "ALCHEMY"),
                LegacyResearchTableData.availableCategories(
                        player, catalog, registry, java.util.Set.of("AUROMANCY")));
        eq(java.util.List.of("ALCHEMY"),
                LegacyResearchTableData.availableCategories(
                        player, catalog, registry, java.util.Set.of("BASICS", "AUROMANCY")));
    }

    /** Exact JAR bytecode arithmetic; full GUI/server XP transaction is still pending. */
    private static void testLegacyLateTheoryCards() {
        for (String key : new String[]{
                LegacyLateTheoryCards.ENCHANTMENT,
                LegacyLateTheoryCards.BEACON,
                LegacyLateTheoryCards.SPELLBINDING,
                LegacyLateTheoryCards.DARK_WHISPERS,
                LegacyLateTheoryCards.PORTAL,
                LegacyLateTheoryCards.REVELATION,
                LegacyLateTheoryCards.REALIZATION}) {
            isTrue(LegacyTheorycraftManifest.portedCoreCards().contains(key));
        }
        eq(1, LegacyLateTheoryCards.inspirationCost(LegacyLateTheoryCards.ENCHANTMENT));
        eq(-2, LegacyLateTheoryCards.inspirationCost(LegacyLateTheoryCards.BEACON));
        eq(-1, LegacyLateTheoryCards.inspirationCost(LegacyLateTheoryCards.PORTAL));
        eq(1, LegacyLateTheoryCards.inspirationCost(LegacyLateTheoryCards.SPELLBINDING));
        eq(1, LegacyLateTheoryCards.inspirationCost(LegacyLateTheoryCards.DARK_WHISPERS));
        eq(1, LegacyLateTheoryCards.inspirationCost(LegacyLateTheoryCards.REVELATION));
        eq(1, LegacyLateTheoryCards.inspirationCost(LegacyLateTheoryCards.REALIZATION));
        isTrue(LegacyLateTheoryCards.aidOnly(LegacyLateTheoryCards.ENCHANTMENT));
        isTrue(LegacyLateTheoryCards.aidOnly(LegacyLateTheoryCards.BEACON));
        isTrue(LegacyLateTheoryCards.aidOnly(LegacyLateTheoryCards.DARK_WHISPERS));
        isTrue(LegacyLateTheoryCards.aidOnly(LegacyLateTheoryCards.PORTAL));
        isFalse(LegacyLateTheoryCards.aidOnly(LegacyLateTheoryCards.REVELATION));
        isFalse(LegacyLateTheoryCards.aidOnly(LegacyLateTheoryCards.REALIZATION));
        isFalse(LegacyLateTheoryCards.aidOnly(LegacyLateTheoryCards.SPELLBINDING));

        isFalse(LegacyLateTheoryCards.canEnchant(4));
        isTrue(LegacyLateTheoryCards.canEnchant(5));
        eq(0, LegacyLateTheoryCards.spellbindingLevels(0));
        eq(1, LegacyLateTheoryCards.spellbindingLevels(1));
        eq(5, LegacyLateTheoryCards.spellbindingLevels(10));
        eq(10, LegacyLateTheoryCards.darkWhispersLevelDebit(0));
        eq(19, LegacyLateTheoryCards.darkWhispersLevelDebit(9));

        LegacyResearchTableData table = new LegacyResearchTableData(10);
        LegacyLateTheoryCards.beacon(table);
        eq(1, table.bonusDraws());
        eq(1, table.penaltyStart());
        Random expectation = new Random(73L);
        int expectedInfusion = 15 + expectation.nextInt(6);
        int expectedAuromancy = 15 + expectation.nextInt(6);
        LegacyLateTheoryCards.enchantmentAfterFiveLevelsSpent(table, new Random(73L));
        eq(expectedInfusion, table.getTotal("INFUSION"));
        eq(expectedAuromancy, table.getTotal("AUROMANCY"));
        LegacyLateTheoryCards.spellbindingAfterLevelsSpent(table, 3);
        eq(expectedAuromancy + 15, table.getTotal("AUROMANCY"));

        var categories = List.of("BASICS", "ALCHEMY", "INFUSION");
        PlayerWarpData warp = new PlayerWarpData();
        LegacyResearchTableData portalTable = new LegacyResearchTableData(10);
        Random portalRng = new Random(112L);
        Random portalExpected = new Random(112L);
        String p1 = categories.get(portalExpected.nextInt(categories.size()));
        int a1 = 5 + portalExpected.nextInt(6);
        String p2 = categories.get(portalExpected.nextInt(categories.size()));
        int a2 = 5 + portalExpected.nextInt(6);
        int eld = 5 + portalExpected.nextInt(6);
        isTrue(LegacyLateTheoryCards.portal(portalTable, warp, categories, portalRng));
        eq(a1 + (p1.equals(p2) ? a2 : 0), portalTable.getTotal(p1));
        if (!p1.equals(p2)) eq(a2, portalTable.getTotal(p2));
        eq(eld, portalTable.getTotal("ELDRITCH"));
        eq(2, portalTable.bonusDraws());
        eq(5, warp.get(WarpType.TEMPORARY));
        eq(1, warp.get(WarpType.NORMAL));
        eq(portalExpected.nextInt(32768), portalRng.nextInt(32768));

        LegacyResearchTableData revelation = new LegacyResearchTableData(10);
        PlayerWarpData revWarp = new PlayerWarpData();
        Random revExpected = new Random(33L);
        String revCategory = categories.get(revExpected.nextInt(categories.size()));
        int revAmount = 5 + revExpected.nextInt(6);
        isTrue(LegacyLateTheoryCards.revelation(revelation, revWarp, categories, new Random(33L)));
        eq(revAmount, revelation.getTotal(revCategory));
        eq(30, revelation.getTotal("ELDRITCH"));
        eq(5, revWarp.get(WarpType.TEMPORARY));
        eq(1, revWarp.get(WarpType.NORMAL));
        eq(1, revelation.penaltyStart());

        LegacyResearchTableData realization = new LegacyResearchTableData(10);
        PlayerWarpData realWarp = new PlayerWarpData();
        Random realExpected = new Random(9L);
        String r1 = categories.get(realExpected.nextInt(categories.size()));
        int rv1 = 5 + realExpected.nextInt(6);
        String r2 = categories.get(realExpected.nextInt(categories.size()));
        int rv2 = 5 + realExpected.nextInt(6);
        boolean receivesNormal = realExpected.nextBoolean();
        Random realRng = new Random(9L);
        isTrue(LegacyLateTheoryCards.realization(realization, realWarp, categories, realRng));
        eq(rv1 + (r1.equals(r2) ? rv2 : 0), realization.getTotal(r1));
        if (!r1.equals(r2)) eq(rv2, realization.getTotal(r2));
        eq(15, realization.getTotal("ELDRITCH"));
        eq(5, realWarp.get(WarpType.TEMPORARY));
        eq(receivesNormal ? 1 : 0, realWarp.get(WarpType.NORMAL));
        eq(realExpected.nextInt(32768), realRng.nextInt(32768));

        // Zero-level whispers still yield 1..5 ELDRITCH and one normal warp;
        // zero XP means no category nextBoolean rolls at all.
        LegacyResearchTableData whispers = new LegacyResearchTableData(10);
        PlayerWarpData whisperWarp = new PlayerWarpData();
        Random whisperExpected = new Random(222L);
        int whisperEldritch = 1 + whisperExpected.nextInt(5);
        boolean whisperBonus = whisperExpected.nextBoolean();
        Random whisperRng = new Random(222L);
        LegacyLateTheoryCards.darkWhispersAfterXpDebited(
                whispers, whisperWarp, 0, categories, whisperRng);
        eq(whisperEldritch, whispers.getTotal("ELDRITCH"));
        eq(1, whisperWarp.get(WarpType.NORMAL));
        eq(whisperBonus ? 1 : 0, whispers.bonusDraws());
        eq(whisperExpected.nextInt(32768), whisperRng.nextInt(32768));

        Random nineExpected = new Random(444L);
        LegacyResearchTableData nineWhispers = new LegacyResearchTableData(10);
        PlayerWarpData nineWarp = new PlayerWarpData();
        for (String key : categories) if (!nineExpected.nextBoolean()) {
            nineWhispers.addTotal(key, nineExpected.nextInt(4));
        }
        nineWhispers.addTotal("ELDRITCH", 1 + nineExpected.nextInt(5));
        int bonus = nineExpected.nextBoolean() ? 1 : 0;
        LegacyResearchTableData nineActual = new LegacyResearchTableData(10);
        LegacyLateTheoryCards.darkWhispersAfterXpDebited(
                nineActual, nineWarp, 9, categories, new Random(444L));
        eq(nineWhispers.categoryTotals(), nineActual.categoryTotals());
        eq(3, nineWarp.get(WarpType.NORMAL));
        eq(bonus, nineActual.bonusDraws());

        LegacyResearchTableData empty = new LegacyResearchTableData(5);
        PlayerWarpData emptyWarp = new PlayerWarpData();
        isFalse(LegacyLateTheoryCards.portal(empty, emptyWarp, List.of(), new Random(1)));
        isFalse(LegacyLateTheoryCards.revelation(empty, emptyWarp, List.of(), new Random(1)));
        isFalse(LegacyLateTheoryCards.realization(empty, emptyWarp, List.of(), new Random(1)));
        eq(java.util.Map.of(), empty.categoryTotals());
        eq(0, emptyWarp.get(WarpType.TEMPORARY));
    }

    private static void testLegacyTheorycraftCardMetadata() {
        var keys = LegacyTheorycraftManifest.CARD_REGISTRATION_ORDER;
        eq(33, keys.size());
        int[] counts = new int[7]; // one, two, zero, minus one, minus two, aidOnly, dynamic
        java.util.Map<String, String> fixed = java.util.Map.ofEntries(
                java.util.Map.entry("CardConcentrate", "ALCHEMY"),
                java.util.Map.entry("CardReactions", "ALCHEMY"),
                java.util.Map.entry("CardSynthesis", "ALCHEMY"),
                java.util.Map.entry("CardCalibrate", "ARTIFICE"),
                java.util.Map.entry("CardMindOverMatter", "ARTIFICE"),
                java.util.Map.entry("CardTinker", "ARTIFICE"),
                java.util.Map.entry("CardMeasure", "INFUSION"),
                java.util.Map.entry("CardChannel", "INFUSION"),
                java.util.Map.entry("CardInfuse", "INFUSION"),
                java.util.Map.entry("CardFocus", "AUROMANCY"),
                java.util.Map.entry("CardAwareness", "AUROMANCY"),
                java.util.Map.entry("CardSpellbinding", "AUROMANCY"),
                java.util.Map.entry("CardSculpting", "GOLEMANCY"),
                java.util.Map.entry("CardScripting", "GOLEMANCY"),
                java.util.Map.entry("CardSynergy", "GOLEMANCY"),
                java.util.Map.entry("CardGlyphs", "ELDRITCH"),
                java.util.Map.entry("CardDarkWhispers", "ELDRITCH"),
                java.util.Map.entry("CardPortal", "ELDRITCH"),
                java.util.Map.entry("CardRevelation", "ELDRITCH"),
                java.util.Map.entry("CardRealization", "ELDRITCH"));
        var dynamic = java.util.Set.of("CardStudy", "CardAnalyze",
                "CardInspired", "CardCelestial");
        var aidOnly = java.util.Set.of("CardStudy", "CardNotation", "CardEnchantment",
                "CardBeacon", "CardDarkWhispers", "CardGlyphs", "CardPortal");
        var twoCost = java.util.Set.of("CardAnalyze", "CardExperimentation",
                "CardInspired", "CardPonder");
        var minusOne = java.util.Set.of("CardRethink", "CardPortal");
        for (String key : keys) {
            String simple = key.substring(key.lastIndexOf('.') + 1);
            var metadata = LegacyTheorycraftCardMetadata.get(key);
            int expectedCost = simple.equals("CardBeacon") ? -2
                    : simple.equals("CardReject") ? 0
                    : minusOne.contains(simple) ? -1
                    : twoCost.contains(simple) ? 2 : 1;
            eq(expectedCost, metadata.cost());
            eq(aidOnly.contains(simple), metadata.aidOnly());
            eq(dynamic.contains(simple), metadata.dynamicCategory());
            eq(fixed.get(simple), metadata.fixedCategory());
            eq(dynamic.contains(simple) ? "BASICS" : fixed.get(simple),
                    metadata.categoryAfterInitialization("BASICS"));
            switch (expectedCost) {
                case 1 -> counts[0]++;
                case 2 -> counts[1]++;
                case 0 -> counts[2]++;
                case -1 -> counts[3]++;
                case -2 -> counts[4]++;
                default -> throw new AssertionError("unknown BETA26 cost");
            }
            if (metadata.aidOnly()) counts[5]++;
            if (metadata.dynamicCategory()) counts[6]++;
        }
        eq(java.util.List.of(25, 4, 1, 2, 1, 7, 4),
                java.util.Arrays.stream(counts).boxed().toList());
        // BETA26 has extra concrete card classes in the JAR that were not
        // registered in ConfigResearch.initTheorycraft. Exclude both.
        for (String extra : java.util.List.of(
                "thaumcraft.common.lib.research.theorycraft.CardTruth",
                "thaumcraft.common.lib.research.theorycraft.CardDragonEgg")) {
            try {
                LegacyTheorycraftCardMetadata.get(extra);
                throw new AssertionError("unregistered theory card accepted");
            } catch (IllegalArgumentException expected) { assertions++; }
        }
    }

    private static void testLegacyTheorycraftChoicePreview() {
        var original = LegacyTheorycraftManifest.CARD_REGISTRATION_ORDER;
        eq(33, original.size());
        eq(0, LegacyTheorycraftChoicePreview.encode(null, true, true));
        eq(0, LegacyTheorycraftChoicePreview.id(0));
        isFalse(LegacyTheorycraftChoicePreview.fromAid(0));
        isFalse(LegacyTheorycraftChoicePreview.selected(0));
        eq("", LegacyTheorycraftChoicePreview.displayClassName(0));
        eq(null, LegacyTheorycraftChoicePreview.key(0));
        for (int i = 0; i < original.size(); i++) {
            String card = original.get(i);
            String simple = card.substring(card.lastIndexOf('.') + 1).substring(4);
            for (boolean aid : new boolean[]{false, true}) {
                for (boolean selected : new boolean[]{false, true}) {
                    int packed = LegacyTheorycraftChoicePreview.encode(card, aid, selected);
                    between(packed, 1, 255);
                    eq(i + 1, LegacyTheorycraftChoicePreview.id(packed));
                    eq(card, LegacyTheorycraftChoicePreview.key(packed));
                    eq(aid, LegacyTheorycraftChoicePreview.fromAid(packed));
                    eq(selected, LegacyTheorycraftChoicePreview.selected(packed));
                    isFalse(LegacyTheorycraftChoicePreview.unknown(packed));
                    eq(simple, LegacyTheorycraftChoicePreview.displayClassName(packed));
                }
            }
        }
        int unknown = LegacyTheorycraftChoicePreview.encode("unregistered.CardTruth", true, true);
        eq(63, LegacyTheorycraftChoicePreview.id(unknown));
        eq(null, LegacyTheorycraftChoicePreview.key(unknown));
        isTrue(LegacyTheorycraftChoicePreview.unknown(unknown));
        isTrue(LegacyTheorycraftChoicePreview.selected(unknown));
        isTrue(LegacyTheorycraftChoicePreview.fromAid(unknown));
        eq("Unknown card", LegacyTheorycraftChoicePreview.displayClassName(unknown));
        eq(null, LegacyTheorycraftChoicePreview.key(34));
        eq(null, LegacyTheorycraftChoicePreview.key(62));
        // Bits above vanilla's 8-bit preview value cannot forge a registered
        // card identity and are never used for game logic anyway.
        eq(original.get(0), LegacyTheorycraftChoicePreview.key(1 | (1 << 9)));
    }

    private static void testLegacyTheorycraftChoiceFlow() {
        var session = new LegacyResearchTableData(5);
        var original = java.util.List.of(
                new LegacyTheorycraftChoiceFlow.Choice("thaumcraft.api.research.theorycraft.CardStudy",
                        41L, false, false),
                new LegacyTheorycraftChoiceFlow.Choice("thaumcraft.api.research.theorycraft.CardNotation",
                        42L, true, false),
                new LegacyTheorycraftChoiceFlow.Choice("thaumcraft.api.research.theorycraft.CardBalance",
                        43L, false, false));
        eq(original, LegacyTheorycraftChoiceFlow.afterActivation(session, original, 1, 1, false));
        eq(5, session.inspiration());
        eq(0, session.placedCards());
        eq(original, LegacyTheorycraftChoiceFlow.afterActivation(session, original, -1, 1, true));
        eq(original, LegacyTheorycraftChoiceFlow.afterActivation(session, original, 3, 1, true));
        eq(5, session.inspiration());

        var selected = LegacyTheorycraftChoiceFlow.afterActivation(session, original, 1, 1, true);
        isFalse(selected.get(0).selected());
        isTrue(selected.get(1).selected());
        isFalse(selected.get(2).selected());
        eq(4, session.inspiration());
        eq(0, session.placedCards()); // original ContainerResearchTable doesn't increment it
        eq(false, original.get(1).selected());

        // Original counts inspiration with the exact signed card cost;
        // a negative-cost aid card increases it up to inspirationStart.
        selected = LegacyTheorycraftChoiceFlow.afterActivation(session, selected, 0, -2, true);
        eq(5, session.inspiration()); // capped from 6
        isTrue(selected.get(0).selected());
        isTrue(selected.get(1).selected());

        var prior = new LegacyTheorycraftChoiceFlow.Choice(
                "thaumcraft.api.research.theorycraft.CardStudy", 91L, false, true);
        var kept = LegacyTheorycraftChoiceFlow.keepSelection(
                selected, prior, java.util.List.of(50L, 50L));
        eq(java.util.List.of(), kept.remainingChoices());
        eq(selected.get(0), kept.lastDraw()); // first selected, not last selected
        eq(java.util.List.of(50L, 50L, 91L), kept.savedCardSeeds());
        eq(java.util.List.of(50L, 50L), java.util.List.of(50L, 50L));
        eq(3, selected.size()); // input untouched
        var nothing = LegacyTheorycraftChoiceFlow.keepSelection(original, null,
                java.util.List.of());
        eq(null, nothing.lastDraw());
        eq(java.util.List.of(), nothing.savedCardSeeds());
        var noNewSelection = LegacyTheorycraftChoiceFlow.keepSelection(original, prior,
                java.util.List.of(9L));
        eq(prior, noNewSelection.lastDraw());
        eq(java.util.List.of(9L, 91L), noNewSelection.savedCardSeeds());

        session.replaceAidCards(java.util.List.of("a", "a", "b"));
        session.addBonusDraws(2);
        var draw = new LegacyTheorycraftDrawEngine.Result(
                java.util.List.of(), java.util.List.of("a", "b"), 1, 7);
        LegacyTheorycraftChoiceFlow.afterPaperDebitedForDraw(session, draw, "Tester");
        eq(java.util.List.of("a", "b"), session.aidCards());
        eq(1, session.bonusDraws());
        eq("Tester", session.player());
        eq(5, session.inspiration());
        eq(0, session.placedCards());
        var drawnAgain = new LegacyTheorycraftDrawEngine.Result(
                java.util.List.of(), java.util.List.of("b"), 0, 6);
        LegacyTheorycraftChoiceFlow.afterPaperDebitedForDraw(session, drawnAgain, "Tester");
        eq(java.util.List.of("b"), session.aidCards());
        eq(0, session.bonusDraws());
    }

    private static void testLegacyTheorycraftInventoryPlanner() {
        var plan = LegacyTheorycraftInventoryPlanner.plan(
                List.of(new LegacyTheorycraftInventoryPlanner.Slot<>("paper", 2),
                        new LegacyTheorycraftInventoryPlanner.Slot<>("paper", 1),
                        new LegacyTheorycraftInventoryPlanner.Slot<>("ink", 1),
                        new LegacyTheorycraftInventoryPlanner.Slot<String>(null, 0)),
                List.of(new LegacyTheorycraftInventoryPlanner.Ingredient<>("paper", 2, true),
                        new LegacyTheorycraftInventoryPlanner.Ingredient<>("paper", 1, true),
                        new LegacyTheorycraftInventoryPlanner.Ingredient<>("ink", 1, false)));
        isTrue(plan != null);
        eq(4, plan.slotCount());
        eq(2, plan.consumedAt(0));
        eq(1, plan.consumedAt(1));
        eq(0, plan.consumedAt(2));
        eq(0, plan.consumedAt(3));
        eq(3, plan.totalConsumed());
        int[] snapshot = plan.snapshot();
        snapshot[0] = 777;
        eq(2, plan.consumedAt(0));

        // Original per-requirement checks accept BOTH identical 2-item costs
        // against the same 2-item stack. A safe transaction must reject it.
        var shortStack = List.of(new LegacyTheorycraftInventoryPlanner.Slot<>("paper", 2));
        isTrue(LegacyTheorycraftInventoryPlanner.plan(shortStack,
                List.of(new LegacyTheorycraftInventoryPlanner.Ingredient<>("paper", 2, true),
                        new LegacyTheorycraftInventoryPlanner.Ingredient<>("paper", 2, true)))
                == null);
        var exact = LegacyTheorycraftInventoryPlanner.plan(shortStack,
                List.of(new LegacyTheorycraftInventoryPlanner.Ingredient<>("paper", 1, true),
                        new LegacyTheorycraftInventoryPlanner.Ingredient<>("paper", 1, true)));
        isTrue(exact != null);
        eq(2, exact.consumedAt(0));

        // BETA26 separates a catalyst presence check from consumption: a
        // catalyst may share its original stack with the spent ingredient.
        var overlap = LegacyTheorycraftInventoryPlanner.plan(shortStack,
                List.of(new LegacyTheorycraftInventoryPlanner.Ingredient<>("paper", 2, false),
                        new LegacyTheorycraftInventoryPlanner.Ingredient<>("paper", 2, true)));
        isTrue(overlap != null);
        eq(2, overlap.totalConsumed());
        isTrue(LegacyTheorycraftInventoryPlanner.plan(shortStack,
                List.of(new LegacyTheorycraftInventoryPlanner.Ingredient<>("paper", 3, false)))
                == null);

        var empty = LegacyTheorycraftInventoryPlanner.plan(
                List.<LegacyTheorycraftInventoryPlanner.Slot<String>>of(),
                List.<LegacyTheorycraftInventoryPlanner.Ingredient<String>>of());
        isTrue(empty != null);
        eq(0, empty.slotCount());
        eq(0, empty.totalConsumed());
        eq(0, LegacyTheorycraftInventoryPlanner.plan(shortStack,
                List.<LegacyTheorycraftInventoryPlanner.Ingredient<String>>of()).totalConsumed());
        eq(null, LegacyTheorycraftInventoryPlanner.plan(
                List.of(new LegacyTheorycraftInventoryPlanner.Slot<>("paper", 1)),
                List.of(new LegacyTheorycraftInventoryPlanner.Ingredient<>("ink", 1, true))));

        // Value identity is not always enough; 1.12 aspects and 1.21 stack
        // components must be included in the caller-supplied matcher.
        record ComponentItem(String item, String aspect) {}
        var air = new ComponentItem("thaumcraft:crystal_essence", "aer");
        var fire = new ComponentItem("thaumcraft:crystal_essence", "ignis");
        var itemSlots = List.of(new LegacyTheorycraftInventoryPlanner.Slot<>(air, 2),
                new LegacyTheorycraftInventoryPlanner.Slot<>(fire, 1));
        var needFire = List.of(new LegacyTheorycraftInventoryPlanner.Ingredient<>(fire, 2, true));
        eq(null, LegacyTheorycraftInventoryPlanner.plan(itemSlots, needFire));
        var mixed = LegacyTheorycraftInventoryPlanner.plan(itemSlots,
                List.of(new LegacyTheorycraftInventoryPlanner.Ingredient<>(air, 1, true),
                        new LegacyTheorycraftInventoryPlanner.Ingredient<>(fire, 1, true)));
        isTrue(mixed != null);
        eq(1, mixed.consumedAt(0));
        eq(1, mixed.consumedAt(1));

        // A custom matcher can implement relaxed NBT equivalence after that
        // exception has been independently verified against the original.
        var relaxed = LegacyTheorycraftInventoryPlanner.plan(itemSlots, needFire,
                (left, right) -> left.item().equals(right.item()));
        isTrue(relaxed != null);
        eq(2, relaxed.consumedAt(0));

        // Planner itself does not mutate inputs; all-or-nothing decisions
        // remain possible even when requirements are repeated.
        isTrue(LegacyTheorycraftInventoryPlanner.plan(itemSlots,
                List.of(new LegacyTheorycraftInventoryPlanner.Ingredient<>(air, 3, true))) == null);
        eq(2, itemSlots.get(0).count());
        eq(1, itemSlots.get(1).count());

        try {
            new LegacyTheorycraftInventoryPlanner.Slot<>("paper", -1);
            throw new AssertionError("negative inventory slots must fail");
        } catch (IllegalArgumentException expected) { assertions++; }
        try {
            new LegacyTheorycraftInventoryPlanner.Ingredient<>("paper", 0, true);
            throw new AssertionError("zero ingredient amount must fail");
        } catch (IllegalArgumentException expected) { assertions++; }
    }

    private static void testLegacyMaterialTheoryCards() {
        for (String key : new String[]{
                LegacyMaterialTheoryCards.CURIO,
                LegacyMaterialTheoryCards.MIND_OVER_MATTER,
                LegacyMaterialTheoryCards.TINKER,
                LegacyMaterialTheoryCards.INFUSE
        }) isTrue(LegacyTheorycraftManifest.portedCoreCards().contains(key));
        eq(1, LegacyMaterialTheoryCards.INSPIRATION_COST);
        eq(18, LegacyMaterialTheoryCards.ARTIFICE_OPTION_COUNT);
        eq(31, LegacyMaterialTheoryCards.INFUSE_OPTION_COUNT);
        eq(java.util.List.of("arcane", "preserved", "ancient", "eldritch",
                "knowledge", "twisted", "rites"),
                LegacyMaterialTheoryCards.CURIO_VARIANTS);

        isTrue(LegacyMaterialTheoryCards.initializeCurio(List.of(-1, -1, -1), 12L) == null);
        var inventory = java.util.List.of(-1, 0, 5, -1, 0, 6, -1);
        for (long seed : new long[]{0L, 1L, 42L, 77L, Long.MAX_VALUE}) {
            int pos = new Random(seed).nextInt(4);
            int slot = new int[]{1,2,4,5}[pos];
            var selection = LegacyMaterialTheoryCards.initializeCurio(inventory, seed);
            eq(slot, selection.mainInventorySlot());
            eq(inventory.get(slot), Integer.valueOf(selection.variantMetadata()));
            eq(LegacyMaterialTheoryCards.CURIO_VARIANTS.get(selection.variantMetadata()),
                    selection.variantName());

            int expectedIndex = new Random(seed).nextInt(18);
            eq(expectedIndex, LegacyMaterialTheoryCards.initializeArtificeOption(seed));
            var aspects = new AspectRegistry();
            Random expectedInfuse = new Random(seed);
            var compounds = aspects.compoundAspects();
            var expectedAspect = compounds.get(expectedInfuse.nextInt(compounds.size()));
            int expectedOption = expectedInfuse.nextInt(31);
            var infuseChoice = LegacyMaterialTheoryCards.initializeInfuse(aspects, seed);
            eq(expectedAspect, infuseChoice.aspect());
            eq(expectedOption, infuseChoice.itemOptionIndex());
        }
        eq(0, LegacyMaterialTheoryCards.tinkerBaseValue(0));
        eq(3, LegacyMaterialTheoryCards.tinkerBaseValue(15));
        eq(10, LegacyMaterialTheoryCards.mindOverMatterReward(0));
        eq(13, LegacyMaterialTheoryCards.mindOverMatterReward(15));
        eq(15, LegacyMaterialTheoryCards.mindOverMatterReward(25));
        eq(10, LegacyMaterialTheoryCards.infuseReward(0));
        eq(14, LegacyMaterialTheoryCards.infuseReward(9));
        eq(25, LegacyMaterialTheoryCards.infuseReward(100));
        eq(10, LegacyMaterialTheoryCards.fallbackReward(
                LegacyMaterialTheoryCards.MIND_OVER_MATTER));
        eq(0, LegacyMaterialTheoryCards.fallbackReward(
                LegacyMaterialTheoryCards.TINKER));
        eq(10, LegacyMaterialTheoryCards.fallbackReward(
                LegacyMaterialTheoryCards.INFUSE));

        LegacyResearchTableData table = new LegacyResearchTableData(5);
        LegacyMaterialTheoryCards.mindOverMatterAfterItemConsumed(table, 16);
        eq(14, table.getTotal("ARTIFICE"));
        Random expectedTinker = new Random(71L);
        int randomTinker = expectedTinker.nextInt(11);
        Random actualTinker = new Random(71L);
        LegacyMaterialTheoryCards.tinkerWithRequiredItem(table, 81, actualTinker);
        eq(14 + 18 + randomTinker, table.getTotal("ARTIFICE"));
        eq(expectedTinker.nextInt(200), actualTinker.nextInt(200));
        LegacyMaterialTheoryCards.infuseAfterBothItemsConsumed(table, 16);
        eq(16, table.getTotal("INFUSION"));

        var cats = List.of("BASICS", "ARTIFICE", "ELDRITCH");
        for (int variant = 0; variant < 7; variant++) {
            long seed = 131L + variant;
            Random expected = new Random(seed);
            Random actual = new Random(seed);
            String picked = cats.get(expected.nextInt(cats.size()));
            int first = 0;
            int second = 0;
            String extraCategory = switch (variant) {
                case 0 -> "AUROMANCY";
                case 1 -> "ALCHEMY";
                case 2 -> "GOLEMANCY";
                case 3 -> "ELDRITCH";
                case 4 -> "INFUSION";
                case 5 -> "ARTIFICE";
                default -> null;
            };
            if (variant == 6) {
                first = 15 + expected.nextInt(6);
                second = 10 + expected.nextInt(6);
            } else {
                first = 25 + expected.nextInt(11);
            }
            int bonus = (expected.nextBoolean() ? 1 : 0)
                    + (expected.nextBoolean() ? 1 : 0);
            LegacyResearchTableData t = new LegacyResearchTableData(5);
            isTrue(LegacyMaterialTheoryCards.curioAfterItemConsumed(t, variant, cats, actual));
            eq(5 + (picked.equals("BASICS") ? 5 : 0), t.getTotal("BASICS"));
            eq(5 * (picked.equals("ARTIFICE") ? 1 : 0)
                    + (variant == 5 ? first : 0), t.getTotal("ARTIFICE"));
            eq(5 * (picked.equals("ELDRITCH") ? 1 : 0)
                    + (variant == 3 || variant == 6 ? first : 0), t.getTotal("ELDRITCH"));
            if (extraCategory != null && !cats.contains(extraCategory))
                eq(first, t.getTotal(extraCategory));
            if (variant == 6) eq(second, t.getTotal("AUROMANCY"));
            eq(bonus, t.bonusDraws());
            eq(expected.nextInt(1000), actual.nextInt(1000));
        }

        var empty = new LegacyResearchTableData(5);
        Random noRoll = new Random(17L);
        isFalse(LegacyMaterialTheoryCards.curioAfterItemConsumed(empty, 7, cats, noRoll));
        isFalse(LegacyMaterialTheoryCards.curioAfterItemConsumed(empty, 0, List.of(), noRoll));
        eq(java.util.Map.of(), empty.categoryTotals());
        eq(new Random(17L).nextInt(1000), noRoll.nextInt(1000));
    }

    private static void testLegacyTheorycraftInspiration() {
        ResearchCatalog catalog = new ResearchCatalog();
        var singleStage = java.util.List.of(new ResearchStageData(
                false, false, false, false, 0));
        catalog.add(new ResearchEntryData("SPIKY_A", "BASICS",
                java.util.Set.of(ResearchMeta.SPIKY), singleStage,
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()));
        catalog.add(new ResearchEntryData("HIDDEN_A", "BASICS",
                java.util.Set.of(ResearchMeta.HIDDEN), singleStage,
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()));
        catalog.add(new ResearchEntryData("BOTH", "BASICS",
                java.util.Set.of(ResearchMeta.HIDDEN, ResearchMeta.SPIKY), singleStage,
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()));
        PlayerKnowledgeData p = new PlayerKnowledgeData();
        eq(5, LegacyTheorycraftInspiration.available(p, catalog));
        p.addResearch("SPIKY_A"); // Stage 0 is not a strict completion.
        p.addResearch("HIDDEN_A");
        eq(5, LegacyTheorycraftInspiration.available(p, catalog));
        p.setResearchStage("SPIKY_A", 2);
        eq(6, LegacyTheorycraftInspiration.available(p, catalog));
        p.setResearchStage("HIDDEN_A", 2);
        eq(6, LegacyTheorycraftInspiration.available(p, catalog));
        p.addResearch("BOTH");
        p.setResearchStage("BOTH", 2);
        eq(6, LegacyTheorycraftInspiration.available(p, catalog));
        // Legacy pseudo/scan research keys have no ResearchEntryData; no
        // amount of such markers can raise theorycraft inspiration.
        for (int i = 0; i < 10; i++) p.addResearch("!scan_" + i);
        eq(6, LegacyTheorycraftInspiration.available(p, catalog));

        LegacyResearchTableData table = LegacyTheorycraftInspiration.start(
                p, catalog, java.util.Set.of("bookshelf", "crafting_table"));
        eq(6, table.inspirationStart());
        eq(4, table.inspiration());
        isFalse(table.isComplete());

        // Caps at fifteen even with dozens of SPIKY+HIDDEN completions.
        for (int i = 0; i < 25; i++) {
            String key = "MORE_SPIKY_" + i;
            catalog.add(new ResearchEntryData(key, "BASICS",
                    java.util.Set.of(ResearchMeta.SPIKY, ResearchMeta.HIDDEN),
                    singleStage, java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.List.of()));
            p.addResearch(key);
            p.setResearchStage(key, 2);
        }
        eq(15, LegacyTheorycraftInspiration.available(p, catalog));
        var overwhelmed = LegacyTheorycraftInspiration.start(
                p, catalog, java.util.Set.of(
                        "a","b","c","d","e","f","g","h","i","j",
                        "k","l","m","n","o","p"));
        eq(15, overwhelmed.inspirationStart());
        eq(-1, overwhelmed.inspiration()); // Original never clamps below 0.
        isTrue(overwhelmed.isComplete());
    }

    private static void testLegacyResearchTableData() {
        LegacyResearchTableData empty = new LegacyResearchTableData(8);
        eq(8, empty.inspiration());
        eq(8, empty.inspirationStart());
        isFalse(empty.isComplete());
        eq(0, empty.getTotal("BASICS"));
        eq(java.util.Map.of(), empty.calculateTheoryRewards());
        empty.addInspiration(99);
        eq(8, empty.inspiration()); // Upper cap only, BETA26.
        empty.addInspiration(-12);
        eq(-4, empty.inspiration());
        isTrue(empty.isComplete());

        LegacyResearchTableData table = new LegacyResearchTableData(5);
        table.addTotal("BASICS", 100);
        table.addTotal("ALCHEMY", 50);
        table.addTotal("ELDRITCH", 25);
        eq(100, table.getTotal("BASICS"));
        eq(50, table.getTotal("ALCHEMY"));
        eq(25, table.getTotal("ELDRITCH"));
        eq(3, table.categoryTotals().size());
        eq(java.util.Map.of("BASICS", 32, "ALCHEMY", 10, "ELDRITCH", 5),
                table.calculateTheoryRewards());
        // addTotal drops zero and negative values rather than retaining keys.
        table.addTotal("ELDRITCH", -25);
        eq(0, table.getTotal("ELDRITCH"));
        isFalse(table.categoryTotals().containsKey("ELDRITCH"));
        table.addTotal("ELDRITCH", 25);

        // Two notes 4 and 0: a star yields Eldritch and temporary warp,
        // the Sun increases penaltyStart, and a theory card costs 1 inspiration.
        var starSun = new LegacyCelestialTheoryCard.Selection(4, 0, "BASICS");
        var outcome = LegacyCelestialTheoryCard.activate(
                starSun, new java.util.Random(0L), new int[]{2, 5});
        PlayerWarpData warp = new PlayerWarpData();
        table.applyCelestialEffects(outcome, warp);
        eq(143, table.getTotal("BASICS"));
        eq(33, table.getTotal("ELDRITCH"));
        eq(4, warp.get(WarpType.TEMPORARY));
        eq(1, table.penaltyStart());
        eq(0, table.bonusDraws());
        eq(5, table.inspiration()); // Card.activate does NOT deduct cost.
        table.consumeInspirationAfterActivation(1);
        eq(4, table.inspiration());
        eq(0, table.placedCards());

        // Order BASICS (143), ALCHEMY (50), ELDRITCH (33).
        // Sun note's penaltyStart=1 exempts the second highest category.
        eq(java.util.Map.of("BASICS", 46, "ALCHEMY", 16, "ELDRITCH", 7),
                table.calculateTheoryRewards());
        PlayerKnowledgeData knowledge = new PlayerKnowledgeData();
        table.grantFinishedTheory(knowledge);
        eq(46, knowledge.getKnowledgeRaw(KnowledgeType.THEORY, "BASICS"));
        eq(16, knowledge.getKnowledgeRaw(KnowledgeType.THEORY, "ALCHEMY"));
        eq(7, knowledge.getKnowledgeRaw(KnowledgeType.THEORY, "ELDRITCH"));
        eq(1, knowledge.getKnowledge(KnowledgeType.THEORY, "BASICS"));

        // A moon note grants one bonus draw but no penalty or warp.
        var moon = LegacyCelestialTheoryCard.activate(
                new LegacyCelestialTheoryCard.Selection(5, 9, "BASICS"),
                new java.util.Random(42L), new int[]{1, 7});
        table.applyCelestialEffects(moon, warp);
        eq(1, table.bonusDraws());
        eq(1, table.penaltyStart());
        eq(4, warp.get(WarpType.TEMPORARY));
        eq(168, table.getTotal("BASICS"));

        // Reload a saved theory session mid-work with the original NBT scalar
        // field names, preserving the exact research and inspiration state.
        LegacyResearchTableData.Snapshot snapshot = table.snapshot();
        LegacyResearchTableData loaded = LegacyResearchTableData.fromSnapshot(snapshot);
        eq(snapshot, loaded.snapshot());
        eq(table.inspiration(), loaded.inspiration());
        eq(table.inspirationStart(), loaded.inspirationStart());
        eq(table.placedCards(), loaded.placedCards());
        eq(table.penaltyStart(), loaded.penaltyStart());
        eq(table.bonusDraws(), loaded.bonusDraws());
        eq(table.categoryTotals(), loaded.categoryTotals());
        loaded.addTotal("BASICS", 99);
        eq(168, table.getTotal("BASICS")); // Reload has independent maps.
        var copy = snapshot.categoryTotals();
        boolean rejectedSnapshotMutation = false;
        try {
            copy.put("EXAMPLE", 999);
        } catch (UnsupportedOperationException expected) {
            rejectedSnapshotMutation = true;
        }
        isTrue(rejectedSnapshotMutation);

        // Identical totals must sort by the original TreeMap's key ordering.
        LegacyResearchTableData tied = new LegacyResearchTableData(1);
        tied.addTotal("BASICS", 100);
        tied.addTotal("ALCHEMY", 100);
        eq(java.util.List.of("ALCHEMY", "BASICS"),
                new java.util.ArrayList<>(tied.calculateTheoryRewards().keySet()));
        eq(32, tied.calculateTheoryRewards().get("ALCHEMY"));
        eq(21, tied.calculateTheoryRewards().get("BASICS"));
    }

    private static void testLegacyResearchTableExtendedSaveFields() {
        // BETA26 ResearchTableData.serialize() writes these lists as
        // ordered TAG_COMPOUND rows, not sets or unordered string arrays.
        LegacyResearchTableData state = new LegacyResearchTableData(11);
        state.setPlayer("Researcher");
        state.replaceSavedCards(java.util.List.of(42L, 42L, -1L, Long.MAX_VALUE));
        state.replaceAidCards(java.util.List.of(
                "thaumcraft.api.research.theorycraft.CardAnalyze",
                "thaumcraft.api.research.theorycraft.CardPonder",
                "thaumcraft.api.research.theorycraft.CardAnalyze"));
        state.replaceBlockedCategories(java.util.List.of(
                "ELDRITCH", "ALCHEMY", "ELDRITCH"));
        state.addTotal("ALCHEMY", 91);
        state.addInspiration(-3);
        var snapshot = state.snapshot();
        eq("Researcher", snapshot.player());
        eq(java.util.List.of(42L, 42L, -1L, Long.MAX_VALUE),
                snapshot.savedCards());
        eq(java.util.List.of("thaumcraft.api.research.theorycraft.CardAnalyze",
                "thaumcraft.api.research.theorycraft.CardPonder",
                "thaumcraft.api.research.theorycraft.CardAnalyze"), snapshot.aidCards());
        eq(java.util.List.of("ELDRITCH", "ALCHEMY", "ELDRITCH"),
                snapshot.categoriesBlocked());
        LegacyResearchTableData loaded = LegacyResearchTableData.fromSnapshot(snapshot);
        eq(snapshot, loaded.snapshot());
        eq(8, loaded.inspiration());
        eq(91, loaded.getTotal("ALCHEMY"));

        // A second session must have independent lists and mutable state.
        loaded.setPlayer("Other");
        loaded.replaceSavedCards(java.util.List.of(100L));
        loaded.replaceAidCards(java.util.List.of());
        loaded.replaceBlockedCategories(java.util.List.of());
        eq("Researcher", state.player());
        eq(4, state.savedCards().size());
        eq(3, state.aidCards().size());
        eq(3, state.blockedCategories().size());
        eq(1, loaded.savedCards().size());
        eq(0, loaded.aidCards().size());
        eq(0, loaded.blockedCategories().size());

        boolean blocked = false;
        try {
            snapshot.savedCards().add(99L);
        } catch (UnsupportedOperationException expected) {
            blocked = true;
        }
        isTrue(blocked);
        blocked = false;
        try {
            state.aidCards().clear();
        } catch (UnsupportedOperationException expected) {
            blocked = true;
        }
        isTrue(blocked);
        // All four original ordered-list fields must stay absent/empty for
        // legacy 7-field snapshots created by previous port milestones.
        var legacy = new LegacyResearchTableData.Snapshot(
                5, 6, 1, 0, 0, 0, java.util.Map.of("BASICS", 30));
        eq("", legacy.player());
        eq(java.util.List.of(), legacy.savedCards());
        eq(java.util.List.of(), legacy.aidCards());
        eq(java.util.List.of(), legacy.categoriesBlocked());
    }

    private static void testLegacyTheorycraftDrawEngine() {
        // The factory's responsibility is to instantiate the original card
        // class and consume player's nextLong only for a known card.
        LegacyTheorycraftDrawEngine.CardFactory factory = (key, random) ->
                new LegacyTheorycraftDrawEngine.Candidate(
                        key, Math.abs(random.nextLong()), 1, null, false);
        var first = LegacyTheorycraftDrawEngine.draw(
                3, 0, 5, java.util.List.of(), java.util.List.of("A", "B", "C"),
                java.util.Set.of("BASICS"), java.util.List.of(),
                LegacyTheorycraftDrawEngine.fromJavaRandom(new java.util.Random(42L)), factory);
        eq(2, first.choices().size()); // Third choice requires bonusDraws.
        eq(0, first.remainingBonusDraws());
        eq(0, first.remainingAidCards().size());
        isTrue(first.attempts() >= 2);
        eq(2, new java.util.HashSet<>(
                first.choices().stream().map(choice -> choice.card().key()).toList()).size());

        var withBonus = LegacyTheorycraftDrawEngine.draw(
                3, 2, 5, java.util.List.of(), java.util.List.of("A", "B", "C"),
                java.util.Set.of(), java.util.List.of(),
                LegacyTheorycraftDrawEngine.fromJavaRandom(new java.util.Random(42L)), factory);
        eq(3, withBonus.choices().size());
        eq(1, withBonus.remainingBonusDraws());
        for (var choice : withBonus.choices()) {
            isFalse(choice.fromAid());
            isFalse(choice.selected());
            eq(1, choice.card().inspirationCost());
        }

        // If a registered aid is chosen, it can be used once and removed,
        // and ordinary cards cannot repeat a selected aid key.
        LegacyTheorycraftDrawEngine.RandomSource alwaysAid =
                new LegacyTheorycraftDrawEngine.RandomSource() {
                    long seed = 20;
                    @Override public float nextFloat() { return 0.25F; }
                    @Override public int nextInt(int bound) { return 0; }
                    @Override public long nextLong() { return seed++; }
                };
        var aids = LegacyTheorycraftDrawEngine.draw(
                2, 0, 2, java.util.List.of("AidOne", "AidTwo"),
                java.util.List.of("Ordinary"), java.util.Set.of(),
                java.util.List.of(), alwaysAid, factory);
        eq(2, aids.choices().size());
        eq("AidOne", aids.choices().get(0).card().key());
        eq("AidTwo", aids.choices().get(1).card().key());
        isTrue(aids.choices().get(0).fromAid());
        isTrue(aids.choices().get(1).fromAid());
        eq(0, aids.remainingAidCards().size());
        eq(2, aids.attempts());

        // Exhausting the LAST aid before the requested draw count is met
        // must NOT burn the old 10,000 retry limit with an empty card pool.
        var singleAid = LegacyTheorycraftDrawEngine.draw(
                3, 1, 4, java.util.List.of("AidOne"),
                java.util.List.of(), java.util.Set.of(), java.util.List.of(),
                new LegacyTheorycraftDrawEngine.RandomSource() {
                    @Override public float nextFloat() { return 0.0F; }
                    @Override public int nextInt(int bound) { return 0; }
                    @Override public long nextLong() { return 77L; }
                }, factory);
        eq(1, singleAid.choices().size());
        eq("AidOne", singleAid.choices().get(0).card().key());
        eq(1, singleAid.attempts());
        eq(0, singleAid.remainingAidCards().size());
        eq(0, singleAid.remainingBonusDraws());
        eq(20L, aids.choices().get(0).card().seed());
        eq(21L, aids.choices().get(1).card().seed());

        var blocked = LegacyTheorycraftDrawEngine.draw(
                1, 0, 1, java.util.List.of(), java.util.List.of("A"),
                java.util.Set.of("AUROMANCY"), java.util.List.of(),
                LegacyTheorycraftDrawEngine.fromJavaRandom(new java.util.Random(0L)),
                (key, random) -> new LegacyTheorycraftDrawEngine.Candidate(
                        key, random.nextLong(), 1, "ALCHEMY", false));
        eq(0, blocked.choices().size());
        eq(LegacyTheorycraftDrawEngine.MAX_ATTEMPTS, blocked.attempts());

        // A card's isAidOnly disqualifies it from the normal pool even when
        // inspiration and category requirements would otherwise allow it.
        var aidOnly = LegacyTheorycraftDrawEngine.draw(
                1, 0, 5, java.util.List.of(), java.util.List.of("A"),
                java.util.Set.of("BASICS"), java.util.List.of(),
                LegacyTheorycraftDrawEngine.fromJavaRandom(new java.util.Random(1L)),
                (key, random) -> new LegacyTheorycraftDrawEngine.Candidate(
                        key, random.nextLong(), 0, "BASICS", true));
        eq(0, aidOnly.choices().size());
        eq(LegacyTheorycraftDrawEngine.MAX_ATTEMPTS, aidOnly.attempts());

        // An aid with forbidden category remains available rather than being
        // removed. The rejection does not fall through to an ordinary card.
        var rejectedAid = LegacyTheorycraftDrawEngine.draw(
                1, 0, 5, java.util.List.of("Aid"),
                java.util.List.of("A"), java.util.Set.of("BASICS"),
                java.util.List.of("ELDRITCH"),
                new LegacyTheorycraftDrawEngine.RandomSource() {
                    @Override public float nextFloat() { return 0.0F; }
                    @Override public int nextInt(int bound) { return 0; }
                    @Override public long nextLong() { return 37L; }
                },
                (key, random) -> new LegacyTheorycraftDrawEngine.Candidate(
                        key, random.nextLong(), 1, "ELDRITCH", false));
        eq(0, rejectedAid.choices().size());
        eq(java.util.List.of("Aid"), rejectedAid.remainingAidCards());
        eq(LegacyTheorycraftDrawEngine.MAX_ATTEMPTS, rejectedAid.attempts());

        var empty = LegacyTheorycraftDrawEngine.draw(
                2, 0, 5, java.util.List.of(), java.util.List.of(),
                java.util.Set.of(), java.util.List.of(),
                LegacyTheorycraftDrawEngine.fromJavaRandom(new java.util.Random(0L)), factory);
        eq(0, empty.choices().size());
        // Original bug: 10,000 retries of nextInt(0) on an empty registry.
        // Same outcome, but skip impossible attempts without moving RNG state.
        eq(0, empty.attempts());
        java.util.Random untouchedRandom = new java.util.Random(123L);
        LegacyTheorycraftDrawEngine.draw(
                2, 0, 5, java.util.List.of(), java.util.List.of(),
                java.util.Set.of(), java.util.List.of(),
                LegacyTheorycraftDrawEngine.fromJavaRandom(untouchedRandom), factory);
        eq(new java.util.Random(123L).nextLong(), untouchedRandom.nextLong());

        // No source mutation: the caller decides when to commit draw results.
        java.util.List<String> mutableAidInput = new java.util.ArrayList<>(
                java.util.List.of("AidOne", "AidTwo"));
        var untouched = LegacyTheorycraftDrawEngine.draw(
                1, 0, 5, mutableAidInput, java.util.List.of("A"),
                java.util.Set.of(), java.util.List.of(), alwaysAid, factory);
        eq(2, mutableAidInput.size());
        eq(1, untouched.remainingAidCards().size());

        var again = LegacyTheorycraftDrawEngine.draw(
                3, 2, 5, java.util.List.of(), java.util.List.of("A", "B", "C"),
                java.util.Set.of(), java.util.List.of(),
                LegacyTheorycraftDrawEngine.fromJavaRandom(new java.util.Random(42L)), factory);
        eq(withBonus, again); // Identical player RNG and registry ordering.
    }

    private static void testLegacyAlchemyTheoryCards() {
        AspectRegistry a = new AspectRegistry();
        eq(1, LegacyAlchemyTheoryCards.INSPIRATION_COST);
        for (String key : new String[]{
                LegacyAlchemyTheoryCards.CONCENTRATE,
                LegacyAlchemyTheoryCards.CHANNEL,
                LegacyAlchemyTheoryCards.REACTIONS,
                LegacyAlchemyTheoryCards.SYNTHESIS
        }) isTrue(LegacyTheorycraftManifest.allCardKeys().contains(key));

        var originalOrder = a.compoundAspects();
        for (long seed : new long[]{0L, 1L, 42L, 777L, Long.MAX_VALUE}) {
            AspectData expected = originalOrder.get(new java.util.Random(seed)
                    .nextInt(originalOrder.size()));
            eq(expected, LegacyAlchemyTheoryCards.selectCompound(a, seed));
            var synth = LegacyAlchemyTheoryCards.initializeSynthesis(a, seed);
            eq(expected, synth.result());
            eq(expected.components().get(0), synth.component1());
            eq(expected.components().get(1), synth.component2());
            eq(expected, a.combinationResult(synth.component1(), synth.component2()));

            var reaction = LegacyAlchemyTheoryCards.initializeReactions(a, seed);
            isFalse(reaction.first() == reaction.second());
            java.util.Random random = new java.util.Random(seed);
            int i = random.nextInt(originalOrder.size());
            int j = i;
            while (j == i) j = random.nextInt(originalOrder.size());
            eq(originalOrder.get(i), reaction.first());
            eq(originalOrder.get(j), reaction.second());
        }

        // Actual 1.12 source compares double(nextFloat()) to 0.33d;
        // literal Java float 0.33f is slightly GREATER than 0.33d.
        isFalse(LegacyAlchemyTheoryCards.refundInspiration(0.33F));
        isTrue(LegacyAlchemyTheoryCards.refundInspiration(0.329F));
        isFalse(LegacyAlchemyTheoryCards.refundInspiration(0.98F));

        LegacyResearchTableData table = new LegacyResearchTableData(6);
        table.addInspiration(-2); // 4/6 inspiration.
        var alwaysRefund = new LegacyTheorycraftMiscCards.AwarenessRandom() {
            @Override public float nextFloat() { return 0.0F; }
            @Override public int nextInt(int bound) { throw new AssertionError("extra roll"); }
        };
        var neverRefund = new LegacyTheorycraftMiscCards.AwarenessRandom() {
            @Override public float nextFloat() { return 0.75F; }
            @Override public int nextInt(int bound) { throw new AssertionError("extra roll"); }
        };
        LegacyAlchemyTheoryCards.concentrate(table, alwaysRefund);
        eq(15, table.getTotal("ALCHEMY"));
        eq(1, table.bonusDraws());
        eq(5, table.inspiration()); // +1 refund at 0.0f
        LegacyAlchemyTheoryCards.concentrate(table, neverRefund);
        eq(30, table.getTotal("ALCHEMY"));
        eq(2, table.bonusDraws());
        eq(5, table.inspiration());

        LegacyAlchemyTheoryCards.channel(table);
        eq(25, table.getTotal("INFUSION"));
        LegacyAlchemyTheoryCards.reactions(table, alwaysRefund);
        eq(55, table.getTotal("ALCHEMY"));
        eq(6, table.inspiration()); // Capped to inspirationStart.

        LegacyResearchTableData output = new LegacyResearchTableData(6);
        var synth = LegacyAlchemyTheoryCards.initializeSynthesis(a, 42L);
        AspectData crafted = LegacyAlchemyTheoryCards.synthesisAfterInputsConsumed(
                output, synth, neverRefund);
        eq(synth.result(), crafted);
        eq(40, output.getTotal("ALCHEMY"));
        eq(6, output.inspiration());
        eq(null, LegacyAlchemyTheoryCards.synthesisAfterInputsConsumed(
                output, null, neverRefund));
        eq(40, output.getTotal("ALCHEMY")); // A missing card yields no reward.
    }

    private static void testLegacySpecialtyTheoryCards() {
        eq(1, LegacySpecialtyTheoryCards.INSPIRATION_COST);
        for (String card : new String[]{
                LegacySpecialtyTheoryCards.CALIBRATE,
                LegacySpecialtyTheoryCards.FOCUS,
                LegacySpecialtyTheoryCards.GLYPHS,
                LegacySpecialtyTheoryCards.SYNERGY,
                LegacySpecialtyTheoryCards.SCRIPTING}) {
            isTrue(LegacyTheorycraftManifest.allCardKeys().contains(card));
        }

        LegacyResearchTableData table = new LegacyResearchTableData(6);
        LegacySpecialtyTheoryCards.calibrate(table);
        eq(15, table.getTotal("ARTIFICE"));
        eq(1, table.bonusDraws());
        LegacySpecialtyTheoryCards.focus(table);
        eq(15, table.getTotal("AUROMANCY"));
        eq(2, table.bonusDraws());

        PlayerWarpData warp = new PlayerWarpData();
        int[] calls = {0};
        isTrue(LegacySpecialtyTheoryCards.glyphs(table, warp,
                java.util.List.of("BASICS", "ALCHEMY", "ELDRITCH"),
                bound -> { calls[0]++; return 0; }));
        eq(3, calls[0]); // One category and two inclusive range rolls.
        eq(10, table.getTotal("BASICS"));
        eq(10, table.getTotal("ELDRITCH"));
        eq(5, warp.get(WarpType.TEMPORARY));
        eq(0, warp.get(WarpType.NORMAL));

        isTrue(LegacySpecialtyTheoryCards.glyphs(table, warp,
                java.util.List.of("BASICS", "ALCHEMY", "ELDRITCH"),
                bound -> bound - 1));
        eq(50, table.getTotal("ELDRITCH"));
        eq(10, warp.get(WarpType.TEMPORARY));
        isFalse(LegacySpecialtyTheoryCards.glyphs(table, warp,
                java.util.List.of(), bound -> {
                    throw new AssertionError("empty category list should not roll");
                }));
        eq(10, warp.get(WarpType.TEMPORARY));

        LegacyResearchTableData synergy = new LegacyResearchTableData(6);
        synergy.addTotal("ARTIFICE", 10);
        synergy.addTotal("ALCHEMY", 5);
        synergy.addTotal("INFUSION", 5);
        isTrue(LegacySpecialtyTheoryCards.canSynergize(synergy));
        isTrue(LegacySpecialtyTheoryCards.synergy(synergy));
        eq(5, synergy.getTotal("ARTIFICE"));
        eq(0, synergy.getTotal("ALCHEMY"));
        eq(0, synergy.getTotal("INFUSION"));
        eq(30, synergy.getTotal("GOLEMANCY"));
        eq(1, synergy.penaltyStart());
        isFalse(LegacySpecialtyTheoryCards.canSynergize(synergy));
        isFalse(LegacySpecialtyTheoryCards.synergy(synergy));
        eq(30, synergy.getTotal("GOLEMANCY"));

        // Original CardScripting gives a fixed 25. The adapter must consume
        // exactly one paper and one ink first, or never call this function.
        LegacySpecialtyTheoryCards.scriptingAfterMaterialsConsumed(synergy);
        eq(55, synergy.getTotal("GOLEMANCY"));
        eq(1, synergy.penaltyStart());
    }

    private static void testLegacyCardAnalyzeBugfix() {
        AspectRegistry a = new AspectRegistry();
        ResearchCategoryRegistry categories = BuiltInResearchCategories.create(a);
        PlayerKnowledgeData knowledge = new PlayerKnowledgeData();
        LegacyResearchTableData table = new LegacyResearchTableData(5);
        eq(2, LegacyCardAnalyze.INSPIRATION_COST);
        isTrue(LegacyTheorycraftManifest.allCardKeys().contains(
                LegacyCardAnalyze.CARD_KEY));

        // BETA26 bug: initialize tests OBSERVATION on a NULL card.cat
        // instead of the category being inspected. With only ALCHEMY
        // observation, the original never finds the valid candidate.
        knowledge.addKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY", 16);
        eq("ALCHEMY", LegacyCardAnalyze.initialize(42L, knowledge, categories));
        eq(1, knowledge.getKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY"));
        eq(0, knowledge.getKnowledge(KnowledgeType.OBSERVATION, null));

        isTrue(LegacyCardAnalyze.activate("ALCHEMY", knowledge, table,
                bound -> bound - 1));
        eq(0, knowledge.getKnowledgeRaw(KnowledgeType.OBSERVATION, "ALCHEMY"));
        eq(50, table.getTotal("ALCHEMY"));
        eq(5, table.getTotal("BASICS"));
        isFalse(LegacyCardAnalyze.activate("ALCHEMY", knowledge, table,
                bound -> { throw new AssertionError("must not draw"); }));
        eq(50, table.getTotal("ALCHEMY"));
        eq(5, table.getTotal("BASICS"));

        knowledge.addKnowledge(KnowledgeType.OBSERVATION, "AUROMANCY", 48);
        knowledge.addKnowledge(KnowledgeType.OBSERVATION, "BASICS", 200);
        eq("AUROMANCY", LegacyCardAnalyze.initialize(10L, knowledge, categories));
        isFalse(LegacyCardAnalyze.activate("BASICS", knowledge, table,
                bound -> 0));
        isFalse(LegacyCardAnalyze.activate(null, knowledge, table,
                bound -> 0));
        isTrue(LegacyCardAnalyze.activate("AUROMANCY", knowledge, table,
                bound -> 0));
        eq(32, knowledge.getKnowledgeRaw(KnowledgeType.OBSERVATION, "AUROMANCY"));
        eq(25, table.getTotal("AUROMANCY"));
        eq(10, table.getTotal("BASICS"));
    }

    private static void testLegacyTheorycraftMiscCards() {
        eq(2, LegacyTheorycraftMiscCards.INSPIRED_COST);
        eq(0, LegacyTheorycraftMiscCards.REJECT_COST);
        eq(1, LegacyTheorycraftMiscCards.MEASURE_COST);
        eq(1, LegacyTheorycraftMiscCards.SCULPTING_COST);
        eq(1, LegacyTheorycraftMiscCards.AWARENESS_COST);
        for (String key : new String[]{
                LegacyTheorycraftMiscCards.INSPIRED,
                LegacyTheorycraftMiscCards.REJECT,
                LegacyTheorycraftMiscCards.MEASURE,
                LegacyTheorycraftMiscCards.SCULPTING,
                LegacyTheorycraftMiscCards.AWARENESS
        }) isTrue(LegacyTheorycraftManifest.allCardKeys().contains(key));

        LegacyResearchTableData state = new LegacyResearchTableData(7);
        eq(null, LegacyTheorycraftMiscCards.initializeInspired(state));
        eq(null, LegacyTheorycraftMiscCards.initializeReject(state, 10));
        state.addTotal("BASICS", 15);
        state.addTotal("ALCHEMY", 30);
        state.addTotal("AUROMANCY", 30);
        var inspired = LegacyTheorycraftMiscCards.initializeInspired(state);
        eq("ALCHEMY", inspired.category()); // Earliest TreeMap key wins tie.
        eq(25, inspired.amount()); // 10 + 30/2, stored in cardNBT.
        isTrue(LegacyTheorycraftMiscCards.inspire(state, inspired));
        eq(55, state.getTotal("ALCHEMY"));
        eq(30, state.getTotal("AUROMANCY"));
        isFalse(LegacyTheorycraftMiscCards.inspire(state, null));

        // Seed is used to choose from the eligible ordered category list;
        // original CardReject does NOT subtract or redistribute category totals.
        java.util.List<String> categories = java.util.List.of(
                "ALCHEMY", "AUROMANCY", "BASICS");
        String expected = categories.get(new java.util.Random(1234L)
                .nextInt(categories.size()));
        String rejectedCategory = LegacyTheorycraftMiscCards.initializeReject(state, 1234L);
        eq(expected, rejectedCategory);
        isTrue(LegacyTheorycraftMiscCards.reject(state, rejectedCategory));
        eq(20, state.getTotal("BASICS")); // +5 irrespective of selected cat.
        eq(java.util.List.of(rejectedCategory), state.blockedCategories());
        isFalse(LegacyTheorycraftMiscCards.reject(state, rejectedCategory));
        eq(20, state.getTotal("BASICS")); // No free second activation.
        eq(3, state.categoryTotals().size()); // selected category still exists
        var remaining = new java.util.ArrayList<>(categories);
        remaining.remove(rejectedCategory);
        String second = LegacyTheorycraftMiscCards.initializeReject(state, 1L);
        eq(remaining.get(new java.util.Random(1L).nextInt(remaining.size())), second);
        state.replaceBlockedCategories(categories);
        eq(null, LegacyTheorycraftMiscCards.initializeReject(state, 0L));

        LegacyResearchTableData measure = new LegacyResearchTableData(5);
        LegacyTheorycraftMiscCards.measure(measure);
        eq(15, measure.getTotal("INFUSION"));
        eq(1, measure.bonusDraws());
        LegacyTheorycraftMiscCards.sculpt(measure);
        eq(20, measure.getTotal("GOLEMANCY"));
        eq(2, measure.bonusDraws());

        LegacyResearchTableData awareness = new LegacyResearchTableData(5);
        PlayerWarpData warp = new PlayerWarpData();
        int[] rolls = {0, 0};
        LegacyTheorycraftMiscCards.awareness(awareness, warp,
                new LegacyTheorycraftMiscCards.AwarenessRandom() {
                    @Override public float nextFloat() { rolls[0]++; return 0.33F; }
                    @Override public int nextInt(int bound) { rolls[1]++; return 0; }
                });
        eq(20, awareness.getTotal("AUROMANCY"));
        // Float 0.33f is actually 0.330000013..., just ABOVE double 0.33.
        eq(0, awareness.getTotal("ELDRITCH"));
        eq(0, warp.get(WarpType.NORMAL));
        eq(1, rolls[0]);
        eq(0, rolls[1]); // No conditional roll when >=0.33.

        LegacyTheorycraftMiscCards.awareness(awareness, warp,
                new LegacyTheorycraftMiscCards.AwarenessRandom() {
                    @Override public float nextFloat() { return 0.0F; }
                    @Override public int nextInt(int bound) { eq(5, bound); return bound - 1; }
                });
        eq(40, awareness.getTotal("AUROMANCY"));
        eq(5, awareness.getTotal("ELDRITCH"));
        eq(1, warp.get(WarpType.NORMAL));
        eq(0, warp.get(WarpType.TEMPORARY));
    }

    private static void testLegacyTheorycraftAidScanner() {
        String shelf = "thaumcraft.api.research.theorycraft.AidBookshelf";
        String beacon = "thaumcraft.common.lib.research.theorycraft.AidBeacon";
        String crimson = "thaumcraft.common.lib.research.theorycraft.AidPortal$AidPortalCrimson";
        eq(LegacyTheorycraftAidScanner.Kind.BLOCK,
                LegacyTheorycraftAidScanner.kind(shelf));
        eq(LegacyTheorycraftAidScanner.Kind.PICKED_STACK,
                LegacyTheorycraftAidScanner.kind(beacon));
        eq(LegacyTheorycraftAidScanner.Kind.ENTITY_CLASS,
                LegacyTheorycraftAidScanner.kind(crimson));
        eq(null, LegacyTheorycraftAidScanner.kind("not_a_real_aid"));
        int[] blockCalls = {0};
        int[] entityCalls = {0};
        var observed = LegacyTheorycraftAidScanner.scan(
                new LegacyTheorycraftAidScanner.Probe() {
                    @Override public boolean matches(String key,
                            LegacyTheorycraftAidScanner.Kind kind, int x, int y, int z) {
                        blockCalls[0]++;
                        if (kind == LegacyTheorycraftAidScanner.Kind.ENTITY_CLASS)
                            throw new AssertionError("entity aid tested as block");
                        if (x < -4 || x > 4 || y < -1 || y > 1 || z < -4 || z > 4)
                            throw new AssertionError("original aid scan bounds");
                        return (key.equals(shelf) && x == -4 && y == -1 && z == 4)
                                || (key.equals(beacon) && x == 4 && y == 1 && z == -4);
                    }
                    @Override public boolean hasEntity(String key, double radius) {
                        entityCalls[0]++;
                        eq(crimson, key);
                        eq(5.0D, radius);
                        return true;
                    }
                });
        eq(java.util.Set.of(shelf, beacon, crimson), observed);
        eq(13 * 3 * 9 * 9, blockCalls[0]);
        eq(1, entityCalls[0]);
        eq(3, observed.size());

        var none = LegacyTheorycraftAidScanner.scan(
                new LegacyTheorycraftAidScanner.Probe() {
                    @Override public boolean matches(String key,
                            LegacyTheorycraftAidScanner.Kind kind, int x, int y, int z) {
                        return false;
                    }
                    @Override public boolean hasEntity(String key, double radius) {
                        return false;
                    }
                });
        eq(java.util.Set.of(), none);
    }

    private static void testLegacyTheorycraftAidCards() {
        var aids = LegacyTheorycraftAidCards.all();
        eq(14, aids.size());
        eq(new java.util.HashSet<>(LegacyTheorycraftManifest.AID_REGISTRATION_ORDER),
                aids.keySet());
        for (String aid : LegacyTheorycraftManifest.AID_REGISTRATION_ORDER) {
            var classes = LegacyTheorycraftAidCards.forAid(aid);
            isTrue(classes != null && !classes.isEmpty());
            for (String card : classes) {
                if (!card.endsWith("CardTruth")) {
                    isTrue(LegacyTheorycraftManifest.allCardKeys().contains(card));
                }
            }
        }
        String bookshelf = "thaumcraft.api.research.theorycraft.AidBookshelf";
        var weighted = LegacyTheorycraftAidCards.forAid(bookshelf);
        eq(6, weighted.size());
        eq(1, java.util.Collections.frequency(weighted,
                "thaumcraft.api.research.theorycraft.CardBalance"));
        eq(2, java.util.Collections.frequency(weighted,
                "thaumcraft.api.research.theorycraft.CardNotation"));
        eq(3, java.util.Collections.frequency(weighted,
                "thaumcraft.api.research.theorycraft.CardStudy"));

        String eldritch = "thaumcraft.common.lib.research.theorycraft.AidBasicEldritch";
        isTrue(LegacyTheorycraftAidCards.forAid(eldritch).contains(
                "thaumcraft.common.lib.research.theorycraft.CardTruth"));
        isFalse(LegacyTheorycraftManifest.allCardKeys().contains(
                "thaumcraft.common.lib.research.theorycraft.CardTruth"));
        eq(null, LegacyTheorycraftAidCards.forAid("unknown"));

        java.util.Set<String> selected = new java.util.LinkedHashSet<>();
        selected.add(bookshelf);
        selected.add(eldritch);
        var expanded = LegacyTheorycraftAidCards.forSelected(selected);
        eq(9, expanded.size());
        eq(weighted, expanded.subList(0, 6));
        eq(LegacyTheorycraftAidCards.forAid(eldritch), expanded.subList(6, 9));

        LegacyResearchTableData started = LegacyTheorycraftInspiration.start(
                new PlayerKnowledgeData(), new ResearchCatalog(), selected);
        eq(5, started.inspirationStart());
        eq(3, started.inspiration());
        eq(expanded, started.aidCards());
        // The same list, duplicates included, round-trips the table snapshot.
        eq(expanded, LegacyResearchTableData.fromSnapshot(started.snapshot()).aidCards());

        selected.add("nonexistent-aid");
        LegacyResearchTableData unknown = LegacyTheorycraftInspiration.start(
                new PlayerKnowledgeData(), new ResearchCatalog(), selected);
        eq(2, unknown.inspiration());
        eq(expanded, unknown.aidCards());
        eq(java.util.List.of(), LegacyTheorycraftAidCards.forSelected(java.util.Set.of()));
    }

    private static void testLegacyTheorycraftManifest() {
        eq(33, LegacyTheorycraftManifest.CARD_REGISTRATION_ORDER.size());
        eq(14, LegacyTheorycraftManifest.AID_REGISTRATION_ORDER.size());
        eq(33, LegacyTheorycraftManifest.allCardKeys().size());
        eq(33, new java.util.HashSet<>(
                LegacyTheorycraftManifest.CARD_REGISTRATION_ORDER).size());
        eq(14, new java.util.HashSet<>(
                LegacyTheorycraftManifest.AID_REGISTRATION_ORDER).size());
        eq("thaumcraft.api.research.theorycraft.CardStudy",
                LegacyTheorycraftManifest.CARD_REGISTRATION_ORDER.get(0));
        eq("thaumcraft.common.lib.research.theorycraft.CardRealization",
                LegacyTheorycraftManifest.CARD_REGISTRATION_ORDER.get(32));
        eq("thaumcraft.api.research.theorycraft.AidBookshelf",
                LegacyTheorycraftManifest.AID_REGISTRATION_ORDER.get(0));
        eq("thaumcraft.common.lib.research.theorycraft.AidBeacon",
                LegacyTheorycraftManifest.AID_REGISTRATION_ORDER.get(13));

        java.util.List<String> originalHashOrder =
                LegacyTheorycraftManifest.cardsInHashMapIterationOrder();
        eq(33, originalHashOrder.size());
        eq(LegacyTheorycraftManifest.allCardKeys(),
                new java.util.HashSet<>(originalHashOrder));
        isFalse(originalHashOrder.equals(
                LegacyTheorycraftManifest.CARD_REGISTRATION_ORDER));
        // Same original HashMap initialization is deterministic within JVM.
        eq(originalHashOrder, LegacyTheorycraftManifest.cardsInHashMapIterationOrder());
        eq(14, LegacyTheorycraftManifest.aidsInHashMapIterationOrder().size());
        eq(33, LegacyTheorycraftManifest.portedCoreCards().size());
        eq(0, LegacyTheorycraftManifest.missingCoreCards().size());
        isTrue(LegacyTheorycraftManifest.allCardKeys()
                .containsAll(LegacyTheorycraftManifest.portedCoreCards()));
        eq(33, LegacyTheorycraftManifest.portedCoreCards().size()
                + LegacyTheorycraftManifest.missingCoreCards().size());
        isTrue(LegacyTheorycraftManifest.missingCoreCards().isEmpty());
        eq(LegacyTheorycraftManifest.allCardKeys(), LegacyTheorycraftManifest.portedCoreCards());

        for (String key : new String[]{
                LegacyBasicTheoryCards.PONDER_KEY,
                LegacyBasicTheoryCards.RETHINK_KEY,
                LegacyAdditionalTheoryCards.BALANCE_KEY,
                LegacyAdditionalTheoryCards.NOTATION_KEY,
                LegacyAdditionalTheoryCards.STUDY_KEY,
                LegacyAdditionalTheoryCards.EXPERIMENTATION_KEY,
                "thaumcraft.common.lib.research.theorycraft.CardCelestial"}) {
            isTrue(LegacyTheorycraftManifest.allCardKeys().contains(key));
        }
    }

    private static void testLegacyAdditionalTheoryCards() {
        eq("thaumcraft.api.research.theorycraft.CardBalance",
                LegacyAdditionalTheoryCards.BALANCE_KEY);
        eq("thaumcraft.api.research.theorycraft.CardNotation",
                LegacyAdditionalTheoryCards.NOTATION_KEY);
        eq("thaumcraft.api.research.theorycraft.CardStudy",
                LegacyAdditionalTheoryCards.STUDY_KEY);
        eq("thaumcraft.api.research.theorycraft.CardExperimentation",
                LegacyAdditionalTheoryCards.EXPERIMENTATION_KEY);
        eq(1, LegacyAdditionalTheoryCards.BALANCE_COST);
        eq(1, LegacyAdditionalTheoryCards.NOTATION_COST);
        eq(1, LegacyAdditionalTheoryCards.STUDY_COST);
        eq(2, LegacyAdditionalTheoryCards.EXPERIMENTATION_COST);
        isTrue(LegacyAdditionalTheoryCards.studyAidOnly());
        isTrue(LegacyAdditionalTheoryCards.notationAidOnly());
        isFalse(LegacyAdditionalTheoryCards.balanceAidOnly());
        isFalse(LegacyAdditionalTheoryCards.experimentationAidOnly());

        LegacyResearchTableData balance = new LegacyResearchTableData(5);
        balance.addTotal("ALCHEMY", 10);
        balance.addTotal("AUROMANCY", 30);
        balance.addTotal("BASICS", 20);
        isTrue(LegacyAdditionalTheoryCards.canBalance(balance));
        isTrue(LegacyAdditionalTheoryCards.balance(balance));
        eq(20, balance.getTotal("ALCHEMY"));
        eq(20, balance.getTotal("AUROMANCY"));
        eq(25, balance.getTotal("BASICS")); // +5 AFTER averaging.
        eq(1, balance.penaltyStart());

        LegacyResearchTableData partiallyBlocked = new LegacyResearchTableData(5);
        partiallyBlocked.addTotal("ALCHEMY", 8);
        partiallyBlocked.addTotal("AUROMANCY", 30);
        partiallyBlocked.addTotal("BASICS", 16);
        partiallyBlocked.replaceBlockedCategories(java.util.List.of("AUROMANCY"));
        isTrue(LegacyAdditionalTheoryCards.canBalance(partiallyBlocked));
        isTrue(LegacyAdditionalTheoryCards.balance(partiallyBlocked));
        eq(12, partiallyBlocked.getTotal("ALCHEMY"));
        eq(30, partiallyBlocked.getTotal("AUROMANCY")); // blocked, untouched.
        eq(17, partiallyBlocked.getTotal("BASICS"));
        eq(1, partiallyBlocked.penaltyStart());

        LegacyResearchTableData one = new LegacyResearchTableData(5);
        one.addTotal("BASICS", 100);
        isFalse(LegacyAdditionalTheoryCards.canBalance(one));
        isFalse(LegacyAdditionalTheoryCards.balance(one));
        eq(0, one.penaltyStart());
        eq(100, one.getTotal("BASICS"));

        LegacyResearchTableData notate = new LegacyResearchTableData(5);
        notate.addTotal("ALCHEMY", 9);
        notate.addTotal("BASICS", 30);
        notate.addTotal("AUROMANCY", 13);
        var selected = LegacyAdditionalTheoryCards.initializeNotation(notate);
        eq("ALCHEMY", selected.weakest());
        eq("BASICS", selected.strongest());
        isTrue(LegacyAdditionalTheoryCards.notation(notate, selected, bound -> bound - 1));
        eq(0, notate.getTotal("ALCHEMY")); // Removes weakest category.
        eq(38, notate.getTotal("BASICS")); // 30 + 4 + uniform[0..4]=4
        eq(13, notate.getTotal("AUROMANCY"));
        isFalse(notate.hasTotal("ALCHEMY"));
        isFalse(LegacyAdditionalTheoryCards.notation(notate, selected, bound -> 0));
        eq(38, notate.getTotal("BASICS"));

        LegacyResearchTableData tie = new LegacyResearchTableData(5);
        tie.addTotal("AUROMANCY", 5);
        tie.addTotal("ALCHEMY", 5);
        eq(null, LegacyAdditionalTheoryCards.initializeNotation(tie));
        eq(null, LegacyAdditionalTheoryCards.initializeNotation(one));
        eq(null, LegacyAdditionalTheoryCards.initializeNotation(
                new LegacyResearchTableData(5)));

        eq(null, LegacyAdditionalTheoryCards.initializeStudy(42L, java.util.List.of()));
        var available = java.util.List.of("BASICS", "ALCHEMY", "AUROMANCY");
        int chosenIdx = new java.util.Random(42L).nextInt(available.size());
        eq(available.get(chosenIdx),
                LegacyAdditionalTheoryCards.initializeStudy(42L, available));
        var study = new LegacyResearchTableData(5);
        isTrue(LegacyAdditionalTheoryCards.study(study, "AUROMANCY", bound -> 0));
        eq(15, study.getTotal("AUROMANCY"));
        isTrue(LegacyAdditionalTheoryCards.study(study, "AUROMANCY",
                bound -> bound - 1));
        eq(40, study.getTotal("AUROMANCY"));
        isFalse(LegacyAdditionalTheoryCards.study(study, null, bound -> 0));
        eq(40, study.getTotal("AUROMANCY"));

        LegacyResearchTableData experimentation = new LegacyResearchTableData(5);
        var calls = new int[1];
        LegacyAdditionalTheoryCards.BoundedRandom scripted = bound -> {
            int n = calls[0]++;
            return n == 0 ? 2 : n == 1 ? 15 : 9;
        };
        isTrue(LegacyAdditionalTheoryCards.experiment(experimentation,
                java.util.List.of("BASICS", "ALCHEMY", "ELDRITCH"), scripted));
        eq(3, calls[0]); // Category, theory gain, BASICS gain.
        eq(30, experimentation.getTotal("ELDRITCH"));
        eq(10, experimentation.getTotal("BASICS"));
        eq(0, experimentation.getTotal("ALCHEMY"));
        isFalse(LegacyAdditionalTheoryCards.experiment(experimentation,
                java.util.List.of(), bound -> { throw new AssertionError("must not roll"); }));
        eq(30, experimentation.getTotal("ELDRITCH"));
    }

    private static void testLegacyBasicTheoryCards() {
        eq("thaumcraft.api.research.theorycraft.CardPonder",
                LegacyBasicTheoryCards.PONDER_KEY);
        eq("thaumcraft.api.research.theorycraft.CardRethink",
                LegacyBasicTheoryCards.RETHINK_KEY);
        eq(2, LegacyBasicTheoryCards.PONDER_INSPIRATION_COST);
        eq(-1, LegacyBasicTheoryCards.RETHINK_INSPIRATION_COST);

        LegacyResearchTableData empty = new LegacyResearchTableData(5);
        isFalse(LegacyBasicTheoryCards.canPonder(empty));
        isFalse(LegacyBasicTheoryCards.canRethink(empty));
        isFalse(LegacyBasicTheoryCards.rethink(empty, new java.util.Random(0L)));
        eq(0, empty.bonusDraws());

        LegacyResearchTableData normal = new LegacyResearchTableData(5);
        normal.addTotal("BASICS", 10);
        normal.addTotal("ALCHEMY", 10);
        isTrue(LegacyBasicTheoryCards.canPonder(normal));
        isTrue(LegacyBasicTheoryCards.ponder(normal));
        eq(23, normal.getTotal("ALCHEMY")); // 13 of 25 from TreeMap order.
        eq(27, normal.getTotal("BASICS")); // 12 of 25 + 5 fixed bonus.
        eq(1, normal.bonusDraws());
        normal.consumeInspirationAfterActivation(
                LegacyBasicTheoryCards.PONDER_INSPIRATION_COST);
        eq(3, normal.inspiration());
        eq(0, normal.placedCards());

        LegacyResearchTableData blocked = new LegacyResearchTableData(5);
        blocked.addTotal("ALCHEMY", 12);
        blocked.addTotal("BASICS", 10);
        blocked.replaceBlockedCategories(java.util.List.of("ALCHEMY"));
        isTrue(LegacyBasicTheoryCards.canPonder(blocked));
        isTrue(LegacyBasicTheoryCards.ponder(blocked));
        eq(12, blocked.getTotal("ALCHEMY"));
        eq(40, blocked.getTotal("BASICS")); // 25 points + 5 BASICS.
        eq(1, blocked.bonusDraws());

        LegacyResearchTableData noAccess = new LegacyResearchTableData(5);
        noAccess.addTotal("BASICS", 10);
        noAccess.replaceBlockedCategories(java.util.List.of("BASICS"));
        isFalse(LegacyBasicTheoryCards.canPonder(noAccess));
        isFalse(LegacyBasicTheoryCards.ponder(noAccess));
        eq(10, noAccess.getTotal("BASICS"));
        eq(0, noAccess.bonusDraws());

        // BETA26 bugfix: if both categories are blocked, the old card could
        // loop 1000 times, award +5 BASICS and a free bonusDraw despite
        // making zero progress. Refuse this impossible activation.
        LegacyResearchTableData allBlocked = new LegacyResearchTableData(5);
        allBlocked.addTotal("ALCHEMY", 12);
        allBlocked.addTotal("BASICS", 10);
        allBlocked.replaceBlockedCategories(java.util.List.of(
                "BASICS", "ALCHEMY"));
        isFalse(LegacyBasicTheoryCards.ponder(allBlocked));
        eq(12, allBlocked.getTotal("ALCHEMY"));
        eq(10, allBlocked.getTotal("BASICS"));
        eq(0, allBlocked.bonusDraws());

        // Vanilla CardPonder compares blocked list LENGTH, not how many
        // listed categories are actually in categoryTotals.
        LegacyResearchTableData irrelevantBlock = new LegacyResearchTableData(5);
        irrelevantBlock.addTotal("BASICS", 20);
        irrelevantBlock.replaceBlockedCategories(java.util.List.of("MISSING"));
        isFalse(LegacyBasicTheoryCards.canPonder(irrelevantBlock));

        LegacyResearchTableData rethink = new LegacyResearchTableData(5);
        rethink.addTotal("ALCHEMY", 6);
        rethink.addTotal("BASICS", 4);
        isTrue(LegacyBasicTheoryCards.canRethink(rethink));
        rethink.addInspiration(-1);
        isTrue(LegacyBasicTheoryCards.rethink(rethink, new java.util.Random(0L)));
        eq(0, rethink.getTotal("ALCHEMY"));
        eq(1, rethink.getTotal("BASICS")); // 10 removed, + 1 from Random(0).
        eq(1, rethink.bonusDraws());
        rethink.consumeInspirationAfterActivation(
                LegacyBasicTheoryCards.RETHINK_INSPIRATION_COST);
        eq(5, rethink.inspiration()); // Negative cost gives back inspiration.
        eq(0, rethink.placedCards());

        // Rethink initializes on total>=10, not on individual category sizes.
        LegacyResearchTableData tooSmall = new LegacyResearchTableData(5);
        tooSmall.addTotal("BASICS", 9);
        isFalse(LegacyBasicTheoryCards.canRethink(tooSmall));
        isFalse(LegacyBasicTheoryCards.rethink(tooSmall, new java.util.Random(7L)));
        eq(9, tooSmall.getTotal("BASICS"));
        eq(0, tooSmall.bonusDraws());

        LegacyResearchTableData twoCategories = new LegacyResearchTableData(5);
        twoCategories.addTotal("ALCHEMY", 7);
        twoCategories.addTotal("BASICS", 11);
        isTrue(LegacyBasicTheoryCards.rethink(twoCategories, new java.util.Random(0L)));
        eq(2, twoCategories.getTotal("ALCHEMY"));
        eq(7, twoCategories.getTotal("BASICS")); // -5 then +1.
        eq(1, twoCategories.bonusDraws());
    }

    private static void testLegacyCelestialTheoryCard() {
        eq(null, LegacyCelestialTheoryCard.initialize(false,
                java.util.Map.of("BASICS", 10), 42L));
        eq(null, LegacyCelestialTheoryCard.initialize(true,
                java.util.Map.of(), 42L));

        // The original CardCelestial creates Random(getSeed()) and samples
        // two DISTINCT metadata indices from [0,12]. Tie ordering is TreeMap.
        var card = LegacyCelestialTheoryCard.initialize(true,
                java.util.Map.of("BASICS", 10, "ALCHEMY", 5), 42L);
        eq(0, card.firstMetadata());
        eq(7, card.secondMetadata());
        eq("BASICS", card.category());
        isTrue(card.requiresSun());
        isTrue(card.requiresMoon());
        isFalse(card.requiresStars());

        var tied = LegacyCelestialTheoryCard.initialize(true,
                java.util.Map.of("BASICS", 10, "ALCHEMY", 10, "AUROMANCY", 9), 1L);
        eq(4, tied.firstMetadata());
        eq(0, tied.secondMetadata());
        eq("ALCHEMY", tied.category()); // Strict > picks first TreeMap key.
        isTrue(tied.requiresStars());
        isTrue(tied.requiresSun());
        isFalse(tied.requiresMoon());

        // Even zero-valued categories initialize a card with empty category
        // in original bytecode (cat is nonnull, initialize returns true).
        eq("", LegacyCelestialTheoryCard.initialize(true,
                java.util.Map.of("BASICS", 0), 12345L).category());

        // The in-inventory matching bridge must find both variants, not
        // confuse the two notes or let a missing note consume anything.
        java.util.List<Integer> inventory = java.util.Arrays.asList(
                null, 8, 7, 0, 5, 7, 1, null);
        int[] slots = LegacyCelestialTheoryCard.requiredNoteSlots(card, inventory);
        eq(3, slots[0]);
        eq(2, slots[1]);
        eq(null, LegacyCelestialTheoryCard.requiredNoteSlots(card,
                java.util.Arrays.asList(5, 7, 7, null)));
        eq(null, LegacyCelestialTheoryCard.requiredNoteSlots(tied,
                java.util.Arrays.asList(7, 0, 3, 12)));

        // 25..50 from player's Random, plus a second 0..5 roll only when
        // either required note is Stellar. Moon gives bonusDraws; Sun gives
        // penaltyStart. All three bonuses may coexist only when appropriate.
        var sunMoon = LegacyCelestialTheoryCard.activate(
                card, new java.util.Random(42L), slots);
        eq(25, sunMoon.categoryGain());
        eq(0, sunMoon.temporaryWarp());
        eq(0, sunMoon.eldritchGain());
        eq(1, sunMoon.penaltyStartIncrement());
        eq(1, sunMoon.bonusDrawsIncrement());
        eq("BASICS", sunMoon.category());
        eq(3, sunMoon.consumedSlots()[0]);
        eq(2, sunMoon.consumedSlots()[1]);
        int[] mutated = sunMoon.consumedSlots();
        mutated[0] = 100;
        eq(3, sunMoon.consumedSlots()[0]); // Defensive copy.

        var starSun = LegacyCelestialTheoryCard.activate(
                tied, new java.util.Random(0L), new int[]{6, 1});
        eq(43, starSun.categoryGain());
        eq(4, starSun.temporaryWarp());
        eq(8, starSun.eldritchGain());
        eq(1, starSun.penaltyStartIncrement());
        eq(0, starSun.bonusDrawsIncrement());

        var moonOnly = LegacyCelestialTheoryCard.initialize(
                true, java.util.Map.of("ELDRITCH", 8), 0L);
        eq(5, moonOnly.firstMetadata());
        eq(10, moonOnly.secondMetadata());
        var moonResult = LegacyCelestialTheoryCard.activate(
                moonOnly, new java.util.Random(0L), new int[]{2, 7});
        eq(0, moonResult.temporaryWarp());
        eq(0, moonResult.penaltyStartIncrement());
        eq(1, moonResult.bonusDrawsIncrement());

        // A card with only one note available cannot be activated.
        boolean rejected = false;
        try {
            LegacyCelestialTheoryCard.activate(card, new java.util.Random(0L),
                    new int[]{1, 1});
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        isTrue(rejected);
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

    private static void testLegacyObjectAspectFinalizer() {
        AspectRegistry a = new AspectRegistry();

        AspectListData uncapped = new AspectListData()
                .add(a.AIR, 800).add(a.FIRE, 2).add(a.MAGIC, 999);
        AspectListData capped = LegacyObjectAspectFinalizer.finish(uncapped);
        eq(3, capped.size());
        eq(500, capped.getAmount(a.AIR));
        eq(2, capped.getAmount(a.FIRE));
        eq(500, capped.getAmount(a.MAGIC));
        eq(800, uncapped.getAmount(a.AIR)); // Finalizer must not mutate source.

        // AspectHelper.cullTags defaults to SEVEN distinct aspects, even if
        // each is a registered vanilla aspect with a positive quantity.
        AspectListData nine = new AspectListData()
                .add(a.AIR, 10).add(a.EARTH, 10).add(a.FIRE, 10)
                .add(a.WATER, 10).add(a.ORDER, 10).add(a.ENTROPY, 10)
                .add(a.MAGIC, 10).add(a.ALCHEMY, 10).add(a.MIND, 10);
        AspectListData seven = LegacyObjectAspectFinalizer.finish(nine);
        eq(9, nine.size());
        eq(7, seven.size());
        eq(0, seven.getAmount(a.AIR));
        eq(0, seven.getAmount(a.EARTH));
        eq(10, seven.getAmount(a.FIRE));
        eq(10, seven.getAmount(a.MAGIC));
        eq(10, seven.getAmount(a.ALCHEMY));
        eq(10, seven.getAmount(a.MIND));

        // The least weighted amount is removed: primal x0.9 compared with
        // compound x1.1 for a first-level non-primal component. Amount still
        // takes precedence over the primals' built-in preference.
        AspectListData eight = new AspectListData()
                .add(a.AIR, 20).add(a.EARTH, 20).add(a.FIRE, 20)
                .add(a.WATER, 20).add(a.ORDER, 20).add(a.ENTROPY, 20)
                .add(a.MAGIC, 1).add(a.MIND, 30);
        AspectListData pruned = LegacyObjectAspectFinalizer.finish(eight);
        eq(7, pruned.size());
        eq(0, pruned.getAmount(a.MAGIC));
        eq(20, pruned.getAmount(a.AIR));
        eq(30, pruned.getAmount(a.MIND));

        // Exact strict (<) tie-breaking removes whichever aspect was added
        // FIRST, not the last one or the one with a lexicographic key.
        AspectListData tied = LegacyObjectAspectFinalizer.finish(
                nine, 8, 500);
        eq(8, tied.size());
        eq(0, tied.getAmount(a.AIR));
        eq(10, tied.getAmount(a.EARTH));
        eq(9, LegacyObjectAspectFinalizer.finish(nine, 9, 500).size());
        eq(0, LegacyObjectAspectFinalizer.finish(
                new AspectListData(), 7, 500).size());

        // Legacy cullTags never removes zero-valued registered entries when
        // there are already <=7 types; it only copies them verbatim.
        var zero = LegacyObjectAspectFinalizer.finish(
                new AspectListData().add(a.AIR, 0).add(a.FIRE, -1));
        eq(2, zero.size());
        eq(0, zero.getAmount(a.AIR));
        eq(-1, zero.getAmount(a.FIRE));

        // Original BETA26 cullTags hangs if 8+ weighted aspects are all
        // >= 32767 because no candidate beats its hardcoded minimum.
        // The port fixes this *without* changing normal aspect balance:
        // deterministic culling still removes earliest tied primal tag.
        AspectListData huge = new AspectListData()
                .add(a.AIR, 50000).add(a.EARTH, 50000)
                .add(a.FIRE, 50000).add(a.WATER, 50000)
                .add(a.ORDER, 50000).add(a.ENTROPY, 50000)
                .add(a.MAGIC, 50000).add(a.MIND, 50000);
        AspectListData safe = LegacyObjectAspectFinalizer.finish(huge);
        eq(7, safe.size());
        eq(0, safe.getAmount(a.AIR));
        eq(500, safe.getAmount(a.EARTH));
        eq(500, safe.getAmount(a.MIND));
        eq(8, huge.size()); // Original data remains unchanged.
    }

    private static void testLegacyEnchantmentAspectBonuses() {
        AspectRegistry a = new AspectRegistry();
        String[][] vanilla = {
            // id / 1.12 rarity / aspect bonus at level 2 (scaled=6; half=3).
            {"protection", "COMMON", "praemunio:6"},
            {"fire_protection", "UNCOMMON", "praemunio:3,ignis:3"},
            {"feather_falling", "UNCOMMON", "volatus:6"},
            {"blast_protection", "RARE", "praemunio:3,perditio:3"},
            {"projectile_protection", "UNCOMMON", "praemunio:6"},
            {"respiration", "RARE", "aer:6"},
            {"aqua_affinity", "RARE", "aqua:6"},
            {"thorns", "VERY_RARE", "aversio:6"},
            {"depth_strider", "RARE", "aqua:6"},
            {"frost_walker", "RARE", "gelum:6"},
            {"binding_curse", "VERY_RARE", ""},
            {"sharpness", "COMMON", "aversio:6"},
            {"smite", "UNCOMMON", "exanimis:3,aversio:3"},
            {"bane_of_arthropods", "UNCOMMON", "bestia:3,aversio:3"},
            {"knockback", "UNCOMMON", "aer:6"},
            {"fire_aspect", "RARE", "ignis:3,aversio:3"},
            {"looting", "RARE", "desiderium:6"},
            {"sweeping_edge", "RARE", ""},
            {"efficiency", "COMMON", "instrumentum:6"},
            {"silk_touch", "VERY_RARE", "permutatio:6"},
            {"unbreaking", "UNCOMMON", "terra:6"},
            {"fortune", "RARE", "desiderium:6"},
            {"power", "COMMON", "aversio:6"},
            {"punch", "RARE", "aer:6"},
            {"flame", "RARE", "ignis:6"},
            {"infinity", "VERY_RARE", "fabrico:6"},
            {"luck_of_the_sea", "RARE", "desiderium:6"},
            {"lure", "RARE", "bestia:6"},
            {"mending", "RARE", "fabrico:6"},
            {"vanishing_curse", "VERY_RARE", ""}
        };
        eq(30, vanilla.length);
        for (String[] data : vanilla) {
            String name = "minecraft:" + data[0];
            var rarity = LegacyEnchantmentAspectBonuses.Rarity.valueOf(data[1]);
            eq(rarity, LegacyEnchantmentAspectBonuses.oldVanillaRarity(name));
            var enchantment = new LegacyEnchantmentAspectBonuses.Enchantment(
                    name, 2, LegacyEnchantmentAspectBonuses.Rarity.COMMON);
            // Original vanilla rarity overrides the supplied modern rarity.
            AspectListData result = LegacyEnchantmentAspectBonuses.apply(
                    a, new AspectListData(), java.util.List.of(enchantment));
            eq(6 + rarity.magicBonus(), result.getAmount(a.MAGIC));
            String[] expected = data[2].isEmpty() ? new String[0] : data[2].split(",");
            eq(1 + expected.length, result.size());
            for (String pair : expected) {
                String[] entry = pair.split(":");
                eq(Integer.parseInt(entry[1]), result.getAmount(a.get(entry[0])));
            }
        }
        eq(null, LegacyEnchantmentAspectBonuses.oldVanillaRarity("minecraft:density"));
        eq(LegacyEnchantmentAspectBonuses.Rarity.RARE,
                LegacyEnchantmentAspectBonuses.Rarity.fromWeight(2));
        eq(LegacyEnchantmentAspectBonuses.Rarity.COMMON,
                LegacyEnchantmentAspectBonuses.Rarity.fromWeight(99));

        AspectListData mixed = LegacyEnchantmentAspectBonuses.apply(a,
                new AspectListData().add(a.AVERSION, 40),
                java.util.List.of(
                        new LegacyEnchantmentAspectBonuses.Enchantment(
                                "minecraft:sharpness", 5,
                                LegacyEnchantmentAspectBonuses.Rarity.COMMON),
                        new LegacyEnchantmentAspectBonuses.Enchantment(
                                "minecraft:fire_aspect", 2,
                                LegacyEnchantmentAspectBonuses.Rarity.COMMON),
                        new LegacyEnchantmentAspectBonuses.Enchantment(
                                "minecraft:mending", 1,
                                LegacyEnchantmentAspectBonuses.Rarity.COMMON)));
        // Levels: 15 + 6 + 3; old rarities: 0 + 4 + 4.
        eq(32, mixed.getAmount(a.MAGIC));
        eq(40, mixed.getAmount(a.AVERSION)); // merge(MAX), not addition
        eq(3, mixed.getAmount(a.FIRE));
        eq(3, mixed.getAmount(a.CRAFT));

        var custom = LegacyEnchantmentAspectBonuses.apply(a, new AspectListData(),
                java.util.List.of(new LegacyEnchantmentAspectBonuses.Enchantment(
                        "example:unknown_enchantment", 1,
                        LegacyEnchantmentAspectBonuses.Rarity.VERY_RARE)));
        eq(1, custom.size());
        eq(9, custom.getAmount(a.MAGIC)); // modded enchants still contribute
        eq(0, LegacyEnchantmentAspectBonuses.apply(
                a, new AspectListData(), java.util.List.of()).size());

        // Legacy enchantment NBT getShort(lvl) followed by multiply and i2s.
        var narrowed = LegacyEnchantmentAspectBonuses.apply(
                a, new AspectListData(), java.util.List.of(
                        new LegacyEnchantmentAspectBonuses.Enchantment("minecraft:sharpness",
                                10923, LegacyEnchantmentAspectBonuses.Rarity.COMMON)));
        eq(0, narrowed.getAmount(a.MAGIC)); // 10923 * 3 wraps to -32767; culled

        // End-to-end BETA26 behavior: an enchanted sword has its base weapon
        // Aversio, enchant-derived Ignis/Praecantatio, a ScanGeneric identity,
        // and the distinct ScanEnchantment research markers.
        AspectListData sword = LegacyEquipmentAspectBonuses.apply(
                a, new AspectListData(), "minecraft:iron_sword");
        sword = LegacyEnchantmentAspectBonuses.apply(a, sword, java.util.List.of(
                new LegacyEnchantmentAspectBonuses.Enchantment(
                        "minecraft:sharpness", 2, LegacyEnchantmentAspectBonuses.Rarity.COMMON),
                new LegacyEnchantmentAspectBonuses.Enchantment(
                        "minecraft:fire_aspect", 1, LegacyEnchantmentAspectBonuses.Rarity.RARE)));
        eq(24, sword.getAmount(a.AVERSION)); // Iron sword bonus beats sharpness II.
        eq(1, sword.getAmount(a.FIRE)); // floor((1*3)/2)
        eq(13, sword.getAmount(a.MAGIC)); // 6 + (3 + 4 rarity).
        var scans = CriticalScanHandlers.createCriticalRegistry(
                a, BuiltInResearchCategories.create(a));
        PlayerKnowledgeData player = new PlayerKnowledgeData();
        var target = new CriticalScanHandlers.Target("!minecraft:iron_sword",
                sword, LegacyEnchantmentEffectScans.forItemEnchantments(
                        java.util.Set.of("minecraft:sharpness", "minecraft:fire_aspect")));
        var result = scans.scanTheThing(player, target, (p, key) -> p.addResearch(key));
        eq(6, result.successfulHandlers()); // 3 aspects + Generic + 2 enchants.
        for (String key : new String[]{"!aversio", "!ignis", "!praecantatio",
                "!minecraft:iron_sword", "!enchantment.damage.all",
                "!enchantment.fire"}) isTrue(player.isResearchKnown(key));
        isFalse(scans.scanTheThing(player, target, (p, key) -> p.addResearch(key)).anySuccess());
    }

    private static void testLegacyEnchantmentEffectScans() {
        eq(30, LegacyEnchantmentEffectScans.enchantmentResearchKeys().size());
        eq(27, LegacyEnchantmentEffectScans.effectResearchKeys().size());
        eq(57, LegacyEnchantmentEffectScans.allResearchKeys().size());
        eq(57, new java.util.HashSet<>(LegacyEnchantmentEffectScans.allResearchKeys()).size());

        String[][] enchantments = {
            {"minecraft:protection", "!enchantment.protect.all"},
            {"minecraft:fire_protection", "!enchantment.protect.fire"},
            {"minecraft:feather_falling", "!enchantment.protect.fall"},
            {"minecraft:blast_protection", "!enchantment.protect.explosion"},
            {"minecraft:projectile_protection", "!enchantment.protect.projectile"},
            {"minecraft:respiration", "!enchantment.oxygen"},
            {"minecraft:aqua_affinity", "!enchantment.waterWorker"},
            {"minecraft:thorns", "!enchantment.thorns"},
            {"minecraft:depth_strider", "!enchantment.waterWalker"},
            {"minecraft:frost_walker", "!enchantment.frostWalker"},
            {"minecraft:binding_curse", "!enchantment.binding_curse"},
            {"minecraft:sharpness", "!enchantment.damage.all"},
            {"minecraft:smite", "!enchantment.damage.undead"},
            {"minecraft:bane_of_arthropods", "!enchantment.damage.arthropods"},
            {"minecraft:knockback", "!enchantment.knockback"},
            {"minecraft:fire_aspect", "!enchantment.fire"},
            {"minecraft:looting", "!enchantment.lootBonus"},
            {"minecraft:sweeping_edge", "!enchantment.sweeping"},
            {"minecraft:efficiency", "!enchantment.digging"},
            {"minecraft:silk_touch", "!enchantment.untouching"},
            {"minecraft:unbreaking", "!enchantment.durability"},
            {"minecraft:fortune", "!enchantment.lootBonusDigger"},
            {"minecraft:power", "!enchantment.arrowDamage"},
            {"minecraft:punch", "!enchantment.arrowKnockback"},
            {"minecraft:flame", "!enchantment.arrowFire"},
            {"minecraft:infinity", "!enchantment.arrowInfinite"},
            {"minecraft:luck_of_the_sea", "!enchantment.lootBonusFishing"},
            {"minecraft:lure", "!enchantment.fishingSpeed"},
            {"minecraft:mending", "!enchantment.mending"},
            {"minecraft:vanishing_curse", "!enchantment.vanishing_curse"}
        };
        for (String[] pair : enchantments) {
            eq(java.util.Set.of(pair[1]), LegacyEnchantmentEffectScans.forItemEnchantments(
                    java.util.Set.of(pair[0])));
        }
        String[][] effects = {
            {"speed", "moveSpeed"}, {"slowness", "moveSlowdown"},
            {"haste", "digSpeed"}, {"mining_fatigue", "digSlowDown"},
            {"strength", "damageBoost"}, {"instant_health", "heal"},
            {"instant_damage", "harm"}, {"jump_boost", "jump"},
            {"nausea", "confusion"}, {"regeneration", "regeneration"},
            {"resistance", "resistance"}, {"fire_resistance", "fireResistance"},
            {"water_breathing", "waterBreathing"}, {"invisibility", "invisibility"},
            {"blindness", "blindness"}, {"night_vision", "nightVision"},
            {"hunger", "hunger"}, {"weakness", "weakness"},
            {"poison", "poison"}, {"wither", "wither"},
            {"health_boost", "healthBoost"}, {"absorption", "absorption"},
            {"saturation", "saturation"}, {"glowing", "glowing"},
            {"levitation", "levitation"}, {"luck", "luck"}, {"unluck", "unluck"}
        };
        for (String[] pair : effects) {
            eq(java.util.Set.of("!effect." + pair[1]),
                    LegacyEnchantmentEffectScans.forPotionEffects(
                            java.util.Set.of("minecraft:" + pair[0])));
        }
        eq(java.util.Set.of("!enchantment.damage.all", "!enchantment.durability"),
                LegacyEnchantmentEffectScans.forItemEnchantments(
                        java.util.Set.of("minecraft:sharpness", "minecraft:unbreaking",
                                "minecraft:density")));
        eq(java.util.Set.of("!effect.poison", "!effect.regeneration"),
                LegacyEnchantmentEffectScans.forPotionEffects(
                        java.util.Set.of("minecraft:poison", "minecraft:regeneration",
                                "minecraft:trial_omen")));
        eq(java.util.Set.of(), LegacyEnchantmentEffectScans.forItemEnchantments(
                java.util.Set.of("minecraft:wind_burst", "minecraft:breach")));
        eq(java.util.Set.of(), LegacyEnchantmentEffectScans.forPotionEffects(
                java.util.Set.of("minecraft:darkness", "minecraft:slow_falling")));

        AspectRegistry registry = new AspectRegistry();
        var scans = CriticalScanHandlers.createCriticalRegistry(
                registry, BuiltInResearchCategories.create(registry));
        PlayerKnowledgeData player = new PlayerKnowledgeData();
        var enchanted = new CriticalScanHandlers.Target(
                null, new AspectListData(),
                LegacyEnchantmentEffectScans.forItemEnchantments(
                        java.util.Set.of("minecraft:sharpness", "minecraft:smite")));
        var result = scans.scanTheThing(player, enchanted, (p, key) -> p.addResearch(key));
        eq(2, result.successfulHandlers());
        isTrue(player.isResearchKnown("!enchantment.damage.all"));
        isTrue(player.isResearchKnown("!enchantment.damage.undead"));
        isFalse(scans.scanTheThing(player, enchanted,
                (p, key) -> p.addResearch(key)).anySuccess());

        var potion = new CriticalScanHandlers.Target(null, new AspectListData(),
                LegacyEnchantmentEffectScans.forPotionEffects(
                        java.util.Set.of("minecraft:speed", "minecraft:poison")));
        isTrue(scans.isThingStillScannable(player, potion, PlayerKnowledgeData::isResearchKnown));
        var potionResult = scans.scanTheThing(player, potion, (p, key) -> p.addResearch(key));
        eq(2, potionResult.successfulHandlers());
        isTrue(player.isResearchKnown("!effect.moveSpeed"));
        isTrue(player.isResearchKnown("!effect.poison"));
        isFalse(scans.isThingStillScannable(player, potion, PlayerKnowledgeData::isResearchKnown));
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
        eq(111,scans.size()); // 37 ScanAspect + Generic + 30 enchantment + 27 effect + 16 explicit material/vanilla scan keys.
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

    private static void testLegacyDyeBonuses() {
        AspectRegistry a = new AspectRegistry();
        String[] dyes = {
            "ink_sac", "red_dye", "green_dye", "cocoa_beans",
            "lapis_lazuli", "purple_dye", "cyan_dye", "light_gray_dye",
            "gray_dye", "pink_dye", "lime_dye", "yellow_dye",
            "light_blue_dye", "magenta_dye", "orange_dye", "bone_meal",
            "black_dye", "brown_dye", "blue_dye", "white_dye"
        };
        for (String dye : dyes) {
            AspectListData result = LegacyDyeAspectBonuses.apply(a,
                    new AspectListData(), "minecraft:" + dye, false);
            eq(1, result.size());
            eq(5, result.getAmount(a.SENSES));
        }
        eq(16, LegacyDyeAspectBonuses.modernTagNames().size());
        eq(16, new java.util.HashSet<>(LegacyDyeAspectBonuses.modernTagNames()).size());
        for (String tag : LegacyDyeAspectBonuses.modernTagNames()) {
            isTrue(tag.startsWith("c:dyes/"));
        }
        AspectListData customModDye = LegacyDyeAspectBonuses.apply(a,
                new AspectListData(), "example:custom_red_pigment", true);
        eq(5, customModDye.getAmount(a.SENSES));
        AspectListData lapis = LegacyDyeAspectBonuses.apply(a,
                LegacyObjectAspectTable.get(a, "minecraft:lapis_lazuli"),
                "minecraft:lapis_lazuli", false);
        eq(15, lapis.getAmount(a.SENSES)); // Merge, not 15 + 5.
        AspectListData strong = LegacyDyeAspectBonuses.apply(a,
                new AspectListData().add(a.SENSES, 40).add(a.EARTH, 2),
                "minecraft:red_dye", true);
        eq(40, strong.getAmount(a.SENSES));
        eq(2, strong.getAmount(a.EARTH));
        eq(0, LegacyDyeAspectBonuses.apply(a,
                new AspectListData(), "minecraft:glow_ink_sac", false).size());

        var scans = CriticalScanHandlers.createCriticalRegistry(
                a, BuiltInResearchCategories.create(a));
        PlayerKnowledgeData p = new PlayerKnowledgeData();
        var target = new CriticalScanHandlers.Target("!minecraft:red_dye0",
                LegacyDyeAspectBonuses.apply(a, new AspectListData(), "minecraft:red_dye", false));
        var first = scans.scanTheThing(p, target, (player, key) -> player.addResearch(key));
        isTrue(first.anySuccess());
        eq(2, first.successfulHandlers());
        isTrue(p.isResearchKnown("!sensus"));
        isTrue(p.isResearchKnown("!minecraft:red_dye0"));
    }

    private static void testLegacyEquipmentBonuses() {
        AspectRegistry a = new AspectRegistry();
        String[][] armor = {
            {"leather_helmet", "1"}, {"leather_chestplate", "3"},
            {"leather_leggings", "2"}, {"leather_boots", "1"},
            {"chainmail_helmet", "2"}, {"chainmail_chestplate", "5"},
            {"chainmail_leggings", "4"}, {"chainmail_boots", "1"},
            {"iron_helmet", "2"}, {"iron_chestplate", "6"},
            {"iron_leggings", "5"}, {"iron_boots", "2"},
            {"golden_helmet", "2"}, {"golden_chestplate", "5"},
            {"golden_leggings", "3"}, {"golden_boots", "1"},
            {"diamond_helmet", "3"}, {"diamond_chestplate", "8"},
            {"diamond_leggings", "6"}, {"diamond_boots", "3"}
        };
        for (String[] gear : armor) {
            AspectListData result = LegacyEquipmentAspectBonuses.apply(
                    a, new AspectListData(), "minecraft:" + gear[0]);
            eq(1, result.size());
            eq(Integer.parseInt(gear[1]) * 4, result.getAmount(a.PROTECT));
        }
        String[][] swords = {
            {"wooden_sword", "16"}, {"stone_sword", "20"},
            {"iron_sword", "24"}, {"golden_sword", "16"}, {"diamond_sword", "28"}
        };
        for (String[] sword : swords) {
            AspectListData result = LegacyEquipmentAspectBonuses.apply(
                    a, new AspectListData(), "minecraft:" + sword[0]);
            eq(1, result.size());
            eq(Integer.parseInt(sword[1]), result.getAmount(a.AVERSION));
        }
        for (String type : new String[]{"_pickaxe", "_shovel", "_axe", "_hoe"}) {
            for (String[] tier : new String[][]{
                    {"wooden", "4"}, {"stone", "8"}, {"iron", "12"},
                    {"golden", "4"}, {"diamond", "16"}}) {
                AspectListData result = LegacyEquipmentAspectBonuses.apply(
                        a, new AspectListData(), "minecraft:" + tier[0] + type);
                eq(1, result.size());
                eq(Integer.parseInt(tier[1]), result.getAmount(a.TOOL));
            }
        }
        eq(12, LegacyEquipmentAspectBonuses.apply(a,
                new AspectListData(), "minecraft:shears").getAmount(a.TOOL));
        AspectListData bow = LegacyEquipmentAspectBonuses.apply(
                a, new AspectListData(), "minecraft:bow");
        eq(2, bow.size());
        eq(10, bow.getAmount(a.AVERSION));
        eq(5, bow.getAmount(a.FLIGHT));

        // Thaumcraft's AspectList.merge is MAX: existing stronger tags win.
        AspectListData old = new AspectListData().add(a.AVERSION, 40).add(a.LIGHT, 3);
        AspectListData updated = LegacyEquipmentAspectBonuses.apply(a, old, "minecraft:iron_sword");
        eq(40, updated.getAmount(a.AVERSION));
        eq(3, updated.getAmount(a.LIGHT));
        eq(40, old.getAmount(a.AVERSION));
        // 1.13+ items do not silently gain tags belonging to old vanilla gear.
        for (String modern : new String[]{"minecraft:netherite_sword",
                "minecraft:crossbow", "minecraft:trident", "minecraft:netherite_pickaxe"}) {
            eq(0, LegacyEquipmentAspectBonuses.apply(
                    a, new AspectListData(), modern).size());
        }

        // Original ScanAspect + ScanGeneric work on equipment bonuses without
        // any direct ore-aspect registration (previously these objects had zero tags).
        var scans = CriticalScanHandlers.createCriticalRegistry(
                a, BuiltInResearchCategories.create(a));
        PlayerKnowledgeData player = new PlayerKnowledgeData();
        var target = new CriticalScanHandlers.Target("!minecraft:bow",
                LegacyEquipmentAspectBonuses.apply(a, new AspectListData(), "minecraft:bow"));
        var first = scans.scanTheThing(player, target, (p, key) -> p.addResearch(key));
        isTrue(first.anySuccess());
        eq(3, first.successfulHandlers());
        isTrue(player.isResearchKnown("!aversio"));
        isTrue(player.isResearchKnown("!volatus"));
        isTrue(player.isResearchKnown("!minecraft:bow"));
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

    private static void testLegacyPlayerEntityAspects() {
        AspectRegistry a = new AspectRegistry();
        AspectListData steve = LegacyPlayerEntityAspectTable.get(a, "Steve");
        eq(4, steve.size());
        eq(4, steve.getAmount(a.MAN));
        eq(15, steve.getAmount(a.LIFE));
        eq(15, steve.getAmount(a.CRYSTAL));
        eq(15, steve.getAmount(a.SENSES));
        eq(49, steve.visSize()); // 4 Humanus + 3 x 15 deterministic tags

        AspectListData alex = LegacyPlayerEntityAspectTable.get(a, "Alex");
        eq(4, alex.size());
        eq(4, alex.getAmount(a.MAN));
        eq(15, alex.getAmount(a.AVERSION));
        eq(15, alex.getAmount(a.ENTROPY));
        eq(15, alex.getAmount(a.CRAFT));
        eq(49, alex.visSize());

        // Original uses exact case-sensitive Java String.hashCode seed.
        AspectListData steveAgain = LegacyPlayerEntityAspectTable.get(a, "Steve");
        eq(steve.snapshot(), steveAgain.snapshot());
        isFalse(steve.snapshot().equals(
                LegacyPlayerEntityAspectTable.get(a, "steve").snapshot()));

        // A player is a real ScanGeneric entity even with no equipped items.
        var scans = CriticalScanHandlers.createCriticalRegistry(
                a, BuiltInResearchCategories.create(a));
        PlayerKnowledgeData research = new PlayerKnowledgeData();
        eq("!null", GenericScanKeyLogic.forEntity(null));
        var target = new CriticalScanHandlers.Target(
                GenericScanKeyLogic.forEntity(null), steve);
        var first = scans.scanTheThing(research, target, (p, key) -> p.addResearch(key));
        eq(5, first.successfulHandlers()); // 4 aspect keys + ScanGeneric
        for (String key : new String[]{"!humanus", "!victus", "!vitreus",
                "!sensus", "!null"}) isTrue(research.isResearchKnown(key));
        isFalse(scans.scanTheThing(research, target,
                (p, key) -> p.addResearch(key)).anySuccess());
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
