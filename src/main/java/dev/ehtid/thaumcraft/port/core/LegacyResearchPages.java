package dev.ehtid.thaumcraft.port.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Source-exact BETA26 stage metadata from analysis/research_manifest.json:
 * 148 entries, 271 ordered stage text keys, original cost/recipe strings,
 * 16 addenda. Strings here are SOURCE identifiers, NOT resolved ItemStacks
 * and NOT evidence that an unported item/recipe is obtainable in NeoForge.
 *
 * The full original localization of research.* text keys is not currently
 * present in the shipped en_us/ru_ru. Never substitute made-up lore text.
 */
public final class LegacyResearchPages {
    private LegacyResearchPages() {}

    public record Stage(String textKey, List<String> recipeIds,
                        List<String> requiredItemSpecs, List<String> craftIds,
                        List<String> requiredKnowledge, List<String> auramKnowledge,
                        List<String> requiredResearch, int warp) {
        public Stage {
            Objects.requireNonNull(textKey);
            recipeIds = List.copyOf(recipeIds);
            requiredItemSpecs = List.copyOf(requiredItemSpecs);
            craftIds = List.copyOf(craftIds);
            requiredKnowledge = List.copyOf(requiredKnowledge);
            auramKnowledge = List.copyOf(auramKnowledge);
            requiredResearch = List.copyOf(requiredResearch);
        }
    }

    public record Addendum(String textKey, List<String> recipeIds,
                           List<String> requiredResearch) {
        public Addendum {
            recipeIds = List.copyOf(recipeIds);
            requiredResearch = List.copyOf(requiredResearch);
        }
    }

    public record Page(String key, String titleKey, List<Stage> stages,
                       List<Addendum> addenda) {
        public Page {
            stages = List.copyOf(stages);
            addenda = List.copyOf(addenda);
        }
    }

    private static final Map<String, Page> PAGES = create();

    private static Map<String, Page> create() {
        LinkedHashMap<String, Page> dest = new LinkedHashMap<>();
        addALCHEMY(dest);
        addARTIFICE(dest);
        addAUROMANCY(dest);
        addBASICS(dest);
        addELDRITCH(dest);
        addGOLEMANCY(dest);
        addINFUSION(dest);
        if (dest.size() != 148) throw new IllegalStateException(
                "BETA26 pages must exactly match 148 manifest research keys");
        return Collections.unmodifiableMap(dest);
    }

    private static void put(Map<String, Page> dest, Page page) {
        if (dest.putIfAbsent(page.key(), page) != null)
            throw new IllegalStateException("Duplicate BETA26 page " + page.key());
    }

    private static void addALCHEMY(Map<String, Page> dest) {
        put(dest, new Page("BASEALCHEMY", "research.BASEALCHEMY.title",
                List.of(new Stage("research.BASEALCHEMY.stage.1", List.of("thaumcraft:nitor", "thaumcraft:nitorgroup", "thaumcraft:viscrystalgroup", "thaumcraft:phial", "thaumcraft:Filter", "thaumcraft:MorphicResonator"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ALUMENTUM", "research.ALUMENTUM.title",
                List.of(new Stage("research.ALUMENTUM.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;BASICS;1"), List.of(), List.of(), 0),
                        new Stage("research.ALUMENTUM.stage.2", List.of("thaumcraft:alumentum"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("METALLURGY", "research.METALLURGY.title",
                List.of(new Stage("research.METALLURGY.stage.1", List.of("thaumcraft:brassingot"), List.of(), List.of("thaumcraft:ingot;1;2"), List.of("OBSERVATION;ALCHEMY;1"), List.of(), List.of(), 0),
                        new Stage("research.METALLURGY.stage.2", List.of("thaumcraft:brassingot", "thaumcraft:brass_stuff", "thaumcraft:thaumiumingot"), List.of(), List.of("thaumcraft:ingot;1;0"), List.of("THEORY;ALCHEMY;1"), List.of(), List.of(), 0),
                        new Stage("research.METALLURGY.stage.3", List.of("thaumcraft:brassingot", "thaumcraft:brass_stuff", "thaumcraft:thaumiumingot", "thaumcraft:thaumium_stuff"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("METALPURIFICATION", "research.METALPURIFICATION.title",
                List.of(new Stage("research.METALPURIFICATION.stage.1", List.of("thaumcraft:metal_purification_iron", "thaumcraft:metal_purification_gold", "thaumcraft:metal_purification_cinnabar", "thaumcraft:metal_purification_copper", "thaumcraft:metal_purification_tin", "thaumcraft:metal_purification_silver", "thaumcraft:metal_purification_lead"), List.of(), List.of(), List.of("THEORY;ALCHEMY;2"), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("HEDGEALCHEMY", "research.HEDGEALCHEMY.title",
                List.of(new Stage("research.HEDGEALCHEMY.stage.1", List.of("thaumcraft:hedge_tallow", "thaumcraft:hedge_leather"), List.of(), List.of("thaumcraft:tallow", "thaumcraft:leather"), List.of("OBSERVATION;ALCHEMY;1"), List.of(), List.of(), 0),
                        new Stage("research.HEDGEALCHEMY.stage.2", List.of("thaumcraft:hedge_tallow", "thaumcraft:tallowcandles", "thaumcraft:hedge_leather", "thaumcraft:hedge_gunpowder", "thaumcraft:hedge_slime", "thaumcraft:hedge_dye", "thaumcraft:hedge_glowstone"), List.of(), List.of("minecraft:gunpowder", "minecraft:slime_ball", "minecraft:dye", "minecraft:glowstone_dust"), List.of("OBSERVATION;ALCHEMY;1"), List.of(), List.of(), 0),
                        new Stage("research.HEDGEALCHEMY.stage.3", List.of("thaumcraft:hedge_tallow", "thaumcraft:tallowcandles", "thaumcraft:hedge_leather", "thaumcraft:hedge_gunpowder", "thaumcraft:hedge_slime", "thaumcraft:hedge_dye", "thaumcraft:hedge_glowstone", "thaumcraft:hedge_clay", "thaumcraft:hedge_string", "thaumcraft:hedge_web", "thaumcraft:hedge_lava"), List.of(), List.of("minecraft:clay_ball", "minecraft:string", "minecraft:web", "minecraft:lava_bucket"), List.of("OBSERVATION;ALCHEMY;1"), List.of(), List.of(), 0),
                        new Stage("research.HEDGEALCHEMY.stage.4", List.of("thaumcraft:hedge_tallow", "thaumcraft:tallowcandles", "thaumcraft:hedge_leather", "thaumcraft:hedge_gunpowder", "thaumcraft:hedge_slime", "thaumcraft:hedge_dye", "thaumcraft:hedge_glowstone", "thaumcraft:hedge_clay", "thaumcraft:hedge_string", "thaumcraft:hedge_web", "thaumcraft:hedge_lava"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("LIQUIDDEATH", "research.LIQUIDDEATH.title",
                List.of(new Stage("research.LIQUIDDEATH.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;ALCHEMY;1"), List.of(), List.of(), 3),
                        new Stage("research.LIQUIDDEATH.stage.2", List.of("thaumcraft:LiquidDeath"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("BOTTLETAINT", "research.BOTTLETAINT.title",
                List.of(new Stage("research.BOTTLETAINT.stage.1", List.of("thaumcraft:BottleTaint"), List.of(), List.of(), List.of("OBSERVATION;ALCHEMY;1", "THEORY;ALCHEMY;1"), List.of(), List.of(), 2)),
                List.of()));
        put(dest, new Page("BATHSALTS", "research.BATHSALTS.title",
                List.of(new Stage("research.BATHSALTS.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ELDRITCH;1", "THEORY;ALCHEMY;1"), List.of(), List.of(), 0),
                        new Stage("research.BATHSALTS.stage.2", List.of("thaumcraft:BathSalts"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("SANESOAP", "research.SANESOAP.title",
                List.of(new Stage("research.SANESOAP.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ELDRITCH;1", "THEORY;ALCHEMY;1"), List.of(), List.of(), 0),
                        new Stage("research.SANESOAP.stage.2", List.of("thaumcraft:SaneSoap"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ARCANESPA", "research.ARCANESPA.title",
                List.of(new Stage("research.ARCANESPA.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ARTIFICE;1"), List.of(), List.of(), 0),
                        new Stage("research.ARCANESPA.stage.2", List.of("thaumcraft:ArcaneSpa"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ESSENTIASMELTER", "research.ESSENTIASMELTER.title",
                List.of(new Stage("research.ESSENTIASMELTER.stage.1", List.of(), List.of("thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'aer'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'terra'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'ignis'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'aqua'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'ordo'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'perditio'}]}"), List.of(), List.of(), List.of(), List.of(), 0),
                        new Stage("research.ESSENTIASMELTER.stage.2", List.of(), List.of("thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'vacuos'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'lux'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'motus'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'gelum'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'vitreus'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'metallum'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'victus'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'mortuus'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'potentia'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'permutatio'}]}"), List.of(), List.of(), List.of(), List.of(), 0),
                        new Stage("research.ESSENTIASMELTER.stage.3", List.of("thaumcraft:EssentiaSmelter"), List.of(), List.of("thaumcraft:smelter_basic"), List.of("OBSERVATION;ALCHEMY;1", "THEORY;ALCHEMY;1"), List.of(), List.of(), 0),
                        new Stage("research.ESSENTIASMELTER.stage.4", List.of("thaumcraft:EssentiaSmelter", "thaumcraft:Alembic"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of(new Addendum("research.ESSENTIASMELTER.bellows", List.of(), List.of("BELLOWS")))));
        put(dest, new Page("WARDEDJARS", "research.WARDEDJARS.title",
                List.of(new Stage("research.WARDEDJARS.stage.1", List.of("thaumcraft:WardedJar", "thaumcraft:JarVoid", "thaumcraft:BrassBrace", "thaumcraft:JarLabel", "thaumcraft:JarLabelEssence"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("TUBES", "research.TUBES.title",
                List.of(new Stage("research.TUBES.stage.1", List.of(), List.of("thaumcraft:phial;1;1;{Aspects:[{amount:10,key:'vitium'}]}", "thaumcraft:phial;1;1;{Aspects:[{amount:10,key:'vinculum'}]}", "thaumcraft:phial;1;1;{Aspects:[{amount:10,key:'alienis'}]}", "thaumcraft:phial;1;1;{Aspects:[{amount:10,key:'alkimia'}]}"), List.of(), List.of("OBSERVATION;ALCHEMY;1", "OBSERVATION;ARTIFICE;1"), List.of(), List.of(), 0),
                        new Stage("research.TUBES.stage.2", List.of("thaumcraft:Resonator", "thaumcraft:Tube", "thaumcraft:TubeValve", "thaumcraft:TubeFilter", "thaumcraft:TubeRestrict", "thaumcraft:TubeOneway", "thaumcraft:TubeBuffer", "thaumcraft:AlchemicalConstruct"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ESSENTIASMELTERTHAUMIUM", "research.ESSENTIASMELTERTHAUMIUM.title",
                List.of(new Stage("research.ESSENTIASMELTERTHAUMIUM.stage.1", List.of(), List.of("thaumcraft:plate;1;2"), List.of("thaumcraft:metal;1;2"), List.of("THEORY;ALCHEMY;1"), List.of(), List.of(), 0),
                        new Stage("research.ESSENTIASMELTERTHAUMIUM.stage.2", List.of("thaumcraft:EssentiaSmelterThaumium"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ESSENTIASMELTERVOID", "research.ESSENTIASMELTERVOID.title",
                List.of(new Stage("research.ESSENTIASMELTERVOID.stage.1", List.of("thaumcraft:AdvAlchemyConstruct"), List.of("thaumcraft:plate;1;3"), List.of("thaumcraft:metal;1;3"), List.of("THEORY;ALCHEMY;1", "THEORY;ELDRITCH;1"), List.of(), List.of(), 0),
                        new Stage("research.ESSENTIASMELTERVOID.stage.2", List.of("thaumcraft:AdvAlchemyConstruct", "thaumcraft:EssentiaSmelterVoid"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("IMPROVEDSMELTING", "research.IMPROVEDSMELTING.title",
                List.of(new Stage("research.IMPROVEDSMELTING.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ALCHEMY;1", "OBSERVATION;ARTIFICE;1"), List.of(), List.of(), 0),
                        new Stage("research.IMPROVEDSMELTING.stage.2", List.of("thaumcraft:SmelterAux"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("IMPROVEDSMELTING2", "research.IMPROVEDSMELTING2.title",
                List.of(new Stage("research.IMPROVEDSMELTING2.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ALCHEMY;1", "OBSERVATION;ARTIFICE;1"), List.of(), List.of(), 0),
                        new Stage("research.IMPROVEDSMELTING2.stage.2", List.of("thaumcraft:SmelterVent"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("CENTRIFUGE", "research.CENTRIFUGE.title",
                List.of(new Stage("research.CENTRIFUGE.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;ALCHEMY;1"), List.of(), List.of(), 0),
                        new Stage("research.CENTRIFUGE.stage.2", List.of("thaumcraft:Centrifuge"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("THAUMATORIUM", "research.THAUMATORIUM.title",
                List.of(new Stage("research.THAUMATORIUM.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;ALCHEMY;1", "OBSERVATION;ARTIFICE;1"), List.of(), List.of(), 0),
                        new Stage("research.THAUMATORIUM.stage.2", List.of("thaumcraft:Thaumatorium", "thaumcraft:MnemonicMatrix"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ESSENTIATRANSPORT", "research.ESSENTIATRANSPORT.title",
                List.of(new Stage("research.ESSENTIATRANSPORT.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ALCHEMY;1", "OBSERVATION;ARTIFICE;1"), List.of(), List.of(), 0),
                        new Stage("research.ESSENTIATRANSPORT.stage.2", List.of("thaumcraft:EssentiaTransportIn", "thaumcraft:EssentiaTransportOut"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("EVERFULLURN", "research.EVERFULLURN.title",
                List.of(new Stage("research.EVERFULLURN.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;BASICS;1", "OBSERVATION;ALCHEMY;1"), List.of(), List.of(), 0),
                        new Stage("research.EVERFULLURN.stage.2", List.of("thaumcraft:EverfullUrn"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("POTIONSPRAYER", "research.POTIONSPRAYER.title",
                List.of(new Stage("research.POTIONSPRAYER.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ALCHEMY;1", "THEORY;ALCHEMY;1"), List.of(), List.of(), 0),
                        new Stage("research.POTIONSPRAYER.stage.2", List.of("thaumcraft:PotionSprayer"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
    }

    private static void addARTIFICE(Map<String, Page> dest) {
        put(dest, new Page("BASEARTIFICE", "research.BASEARTIFICE.title",
                List.of(new Stage("research.BASEARTIFICE.stage.1", List.of("thaumcraft:Goggles", "thaumcraft:mechanism_simple", "thaumcraft:mechanism_complex", "thaumcraft:mirrorglass", "thaumcraft:ironplate"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("HUNGRYCHEST", "research.HUNGRYCHEST.title",
                List.of(new Stage("research.HUNGRYCHEST.stage.1", List.of(), List.of("oredict:chest", "minecraft:hopper"), List.of(), List.of("OBSERVATION;GOLEMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.HUNGRYCHEST.stage.2", List.of("thaumcraft:HungryChest"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("LEVITATOR", "research.LEVITATOR.title",
                List.of(new Stage("research.LEVITATOR.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ARTIFICE;1"), List.of(), List.of("!volatus"), 0),
                        new Stage("research.LEVITATOR.stage.2", List.of("thaumcraft:Levitator"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("INFERNALFURNACE", "research.INFERNALFURNACE.title",
                List.of(new Stage("research.INFERNALFURNACE.stage.1", List.of(), List.of("minecraft:obsidian", "minecraft:nether_brick", "minecraft:blaze_rod"), List.of(), List.of("OBSERVATION;ARTIFICE;1"), List.of(), List.of("!ignis", "m_hellandback"), 0),
                        new Stage("research.INFERNALFURNACE.stage.2", List.of("thaumcraft:infernalfurnace", "thaumcraft:triplemeattreatfake"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of(new Addendum("research.INFERNALFURNACE.bellows", List.of(), List.of("BELLOWS")))));
        put(dest, new Page("BELLOWS", "research.BELLOWS.title",
                List.of(new Stage("research.BELLOWS.stage.1", List.of(), List.of("thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'aer'}]}"), List.of(), List.of("OBSERVATION;GOLEMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.BELLOWS.stage.2", List.of("thaumcraft:Bellows"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("REDSTONERELAY", "research.REDSTONERELAY.title",
                List.of(new Stage("research.REDSTONERELAY.stage.1", List.of(), List.of("minecraft:redstone_torch"), List.of(), List.of("OBSERVATION;ARTIFICE;1"), List.of(), List.of("!potentia", "!machina"), 0),
                        new Stage("research.REDSTONERELAY.stage.2", List.of("thaumcraft:RedstoneRelay"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ARCANEEAR", "research.ARCANEEAR.title",
                List.of(new Stage("research.ARCANEEAR.stage.1", List.of(), List.of("minecraft:noteblock"), List.of(), List.of(), List.of("OBSERVATION;ARTIFICE;1"), List.of("!sensus"), 0),
                        new Stage("research.ARCANEEAR.stage.2", List.of("thaumcraft:ArcaneEar", "thaumcraft:ArcaneEarToggle"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("DIOPTRA", "research.DIOPTRA.title",
                List.of(new Stage("research.DIOPTRA.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;AUROMANCY;1"), List.of(), List.of("!sensus", "!auram"), 0),
                        new Stage("research.DIOPTRA.stage.2", List.of("thaumcraft:dioptra"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("MIRROR", "research.MIRROR.title",
                List.of(new Stage("research.MIRROR.stage.1", List.of(), List.of("minecraft:ender_pearl", "thaumcraft:mirrored_glass"), List.of(), List.of("THEORY;ARTIFICE;1", "OBSERVATION;ARTIFICE;1"), List.of(), List.of("f_TELEPORT"), 0),
                        new Stage("research.MIRROR.stage.2", List.of("thaumcraft:Mirror"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("MIRRORHAND", "research.MIRRORHAND.title",
                List.of(new Stage("research.MIRRORHAND.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;ARTIFICE;1"), List.of(), List.of(), 0),
                        new Stage("research.MIRRORHAND.stage.2", List.of("thaumcraft:MirrorHand"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("MIRRORESSENTIA", "research.MIRRORESSENTIA.title",
                List.of(new Stage("research.MIRRORESSENTIA.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;ARTIFICE;1"), List.of(), List.of(), 0),
                        new Stage("research.MIRRORESSENTIA.stage.2", List.of("thaumcraft:MirrorEssentia"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ARCANELAMP", "research.ARCANELAMP.title",
                List.of(new Stage("research.ARCANELAMP.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ARTIFICE;1"), List.of(), List.of("!lux"), 0),
                        new Stage("research.ARCANELAMP.stage.2", List.of("thaumcraft:ArcaneLamp"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("LAMPGROWTH", "research.LAMPGROWTH.title",
                List.of(new Stage("research.LAMPGROWTH.stage.1", List.of(), List.of("minecraft:dye;1;15"), List.of(), List.of("THEORY;ARTIFICE;1"), List.of(), List.of("!herba"), 0),
                        new Stage("research.LAMPGROWTH.stage.2", List.of("thaumcraft:LampGrowth"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("LAMPFERTILITY", "research.LAMPFERTILITY.title",
                List.of(new Stage("research.LAMPFERTILITY.stage.1", List.of(), List.of("minecraft:wheat", "minecraft:carrot"), List.of(), List.of("THEORY;ARTIFICE;1"), List.of(), List.of("!desiderium"), 0),
                        new Stage("research.LAMPFERTILITY.stage.2", List.of("thaumcraft:LampFertility"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ARCANEPATTERNCRAFTER", "research.ARCANEPATTERNCRAFTER.title",
                List.of(new Stage("research.ARCANEPATTERNCRAFTER.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1", "THEORY;ARTIFICE;1", "THEORY;AUROMANCY;1"), List.of(), List.of("!fabrico"), 0),
                        new Stage("research.ARCANEPATTERNCRAFTER.stage.2", List.of("thaumcraft:patterncrafter"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("BASICTURRET", "research.BASICTURRET.title",
                List.of(new Stage("research.BASICTURRET.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1", "OBSERVATION;ARTIFICE;1"), List.of(), List.of("f_DISPENSER"), 0),
                        new Stage("research.BASICTURRET.stage.2", List.of("thaumcraft:AutomatedCrossbow", "thaumcraft:activatorrail"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ADVANCEDTURRET", "research.ADVANCEDTURRET.title",
                List.of(new Stage("research.ADVANCEDTURRET.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;GOLEMANCY;1", "OBSERVATION;ARTIFICE;1"), List.of(), List.of(), 0),
                        new Stage("research.ADVANCEDTURRET.stage.2", List.of("thaumcraft:AdvancedCrossbow"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ARCANEBORE", "research.ARCANEBORE.title",
                List.of(new Stage("research.ARCANEBORE.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1", "OBSERVATION;ARTIFICE;1", "OBSERVATION;AUROMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.ARCANEBORE.stage.2", List.of("thaumcraft:ArcaneBore"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("GRAPPLEGUN", "research.GRAPPLEGUN.title",
                List.of(new Stage("research.GRAPPLEGUN.stage.1", List.of(), List.of("thaumcraft:nugget;1;10"), List.of(), List.of("THEORY;ARTIFICE;1", "THEORY;BASICS;1"), List.of(), List.of(), 0),
                        new Stage("research.GRAPPLEGUN.stage.2", List.of("thaumcraft:GrappleGunTip", "thaumcraft:GrappleGunSpool", "thaumcraft:GrappleGun"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("VISGENERATOR", "research.VISGENERATOR.title",
                List.of(new Stage("research.VISGENERATOR.stage.1", List.of(), List.of("minecraft:redstone_block", "minecraft:piston"), List.of(), List.of("THEORY;BASICS;1", "THEORY;ARTIFICE;1"), List.of(), List.of("!potentia"), 0),
                        new Stage("research.VISGENERATOR.stage.2", List.of("thaumcraft:VisGenerator"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
    }

    private static void addAUROMANCY(Map<String, Page> dest) {
        put(dest, new Page("BASEAUROMANCY", "research.BASEAUROMANCY.title",
                List.of(new Stage("research.BASEAUROMANCY.stage.1", List.of("thaumcraft:vis_resonator", "thaumcraft:caster_basic", "thaumcraft:focus_1"), List.of(), List.of("thaumcraft:focus_1"), List.of(), List.of(), List.of(), 0),
                        new Stage("research.BASEAUROMANCY.stage.2", List.of("thaumcraft:vis_resonator", "thaumcraft:caster_basic", "thaumcraft:focus_1", "thaumcraft:tablestone", "thaumcraft:wand_workbench"), List.of(), List.of("thaumcraft:wand_workbench"), List.of(), List.of(), List.of("f_onfire"), 0),
                        new Stage("research.BASEAUROMANCY.stage.3", List.of("thaumcraft:vis_resonator", "thaumcraft:caster_basic", "thaumcraft:focus_1", "thaumcraft:tablestone", "thaumcraft:wand_workbench"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSADVANCED", "research.FOCUSADVANCED.title",
                List.of(new Stage("research.FOCUSADVANCED.stage.1", List.of("thaumcraft:focus_2"), List.of(), List.of("thaumcraft:focus_2"), List.of("THEORY;AUROMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.FOCUSADVANCED.stage.2", List.of("thaumcraft:focus_2"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSGREATER", "research.FOCUSGREATER.title",
                List.of(new Stage("research.FOCUSGREATER.stage.1", List.of("thaumcraft:focus_3"), List.of(), List.of("thaumcraft:focus_3"), List.of("THEORY;AUROMANCY;1"), List.of(), List.of("f_onfire"), 0),
                        new Stage("research.FOCUSGREATER.stage.2", List.of("thaumcraft:focus_3"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSELEMENTAL", "research.FOCUSELEMENTAL.title",
                List.of(new Stage("research.FOCUSELEMENTAL.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;AUROMANCY;1"), List.of(), List.of("!aer", "!gelum", "!terra"), 0),
                        new Stage("research.FOCUSELEMENTAL.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSFLUX", "research.FOCUSFLUX.title",
                List.of(new Stage("research.FOCUSFLUX.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;AUROMANCY;1"), List.of(), List.of("!vitium"), 0)),
                List.of()));
        put(dest, new Page("FOCUSHEAL", "research.FOCUSHEAL.title",
                List.of(new Stage("research.FOCUSHEAL.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;AUROMANCY;1"), List.of(), List.of("!victus"), 0)),
                List.of()));
        put(dest, new Page("FOCUSCURSE", "research.FOCUSCURSE.title",
                List.of(new Stage("research.FOCUSCURSE.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;AUROMANCY;1"), List.of(), List.of("!mortuus"), 0),
                        new Stage("research.FOCUSCURSE.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 2)),
                List.of()));
        put(dest, new Page("FOCUSBREAK", "research.FOCUSBREAK.title",
                List.of(new Stage("research.FOCUSBREAK.stage.1", List.of(), List.of("thaumcraft:enchanted_placeholder;1;0;{ench:[{id:33s,lvl:1s}]}", "thaumcraft:enchanted_placeholder;1;0;{ench:[{id:35s,lvl:1s}]}"), List.of(), List.of("THEORY;AUROMANCY;1"), List.of(), List.of("!perditio"), 0),
                        new Stage("research.FOCUSBREAK.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSEXCHANGE", "research.FOCUSEXCHANGE.title",
                List.of(new Stage("research.FOCUSEXCHANGE.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;AUROMANCY;1"), List.of(), List.of("!permutatio"), 0),
                        new Stage("research.FOCUSEXCHANGE.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSPLAN", "research.FOCUSPLAN.title",
                List.of(new Stage("research.FOCUSPLAN.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;AUROMANCY;1"), List.of(), List.of("!fabrico"), 0),
                        new Stage("research.FOCUSPLAN.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSRIFT", "research.FOCUSRIFT.title",
                List.of(new Stage("research.FOCUSRIFT.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ELDRITCH;1", "THEORY;AUROMANCY;1"), List.of(), List.of("!vacuos"), 2),
                        new Stage("research.FOCUSRIFT.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSPROJECTILE", "research.FOCUSPROJECTILE.title",
                List.of(new Stage("research.FOCUSPROJECTILE.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;AUROMANCY;1"), List.of(), List.of("!motus"), 0),
                        new Stage("research.FOCUSPROJECTILE.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of("f_arrow", "f_fireball", "f_spit"), 0),
                        new Stage("research.FOCUSPROJECTILE.stage.3", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSBOLT", "research.FOCUSBOLT.title",
                List.of(new Stage("research.FOCUSBOLT.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;AUROMANCY;1", "THEORY;AUROMANCY;1"), List.of(), List.of("!potentia"), 0),
                        new Stage("research.FOCUSBOLT.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSSCATTER", "research.FOCUSSCATTER.title",
                List.of(new Stage("research.FOCUSSCATTER.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;AUROMANCY;1", "THEORY;AUROMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.FOCUSSCATTER.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSMINE", "research.FOCUSMINE.title",
                List.of(new Stage("research.FOCUSMINE.stage.1", List.of(), List.of("minecraft:tripwire_hook"), List.of(), List.of("THEORY;ARTIFICE;1", "THEORY;AUROMANCY;1"), List.of(), List.of("!vinculum"), 0),
                        new Stage("research.FOCUSMINE.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSSPELLBAT", "research.FOCUSSPELLBAT.title",
                List.of(new Stage("research.FOCUSSPELLBAT.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;AUROMANCY;1", "THEORY;ELDRITCH;1"), List.of(), List.of("!bestia"), 0),
                        new Stage("research.FOCUSSPELLBAT.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 2)),
                List.of()));
        put(dest, new Page("FOCUSCLOUD", "research.FOCUSCLOUD.title",
                List.of(new Stage("research.FOCUSCLOUD.stage.1", List.of(), List.of("minecraft:dragon_breath"), List.of(), List.of("THEORY;ALCHEMY;1", "THEORY;AUROMANCY;1"), List.of(), List.of("!alkimia"), 0),
                        new Stage("research.FOCUSCLOUD.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSSPLIT", "research.FOCUSSPLIT.title",
                List.of(new Stage("research.FOCUSSPLIT.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;AUROMANCY;1", "THEORY;AUROMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.FOCUSSPLIT.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("RECHARGEPEDESTAL", "research.RECHARGEPEDESTAL.title",
                List.of(new Stage("research.RECHARGEPEDESTAL.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;AUROMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.RECHARGEPEDESTAL.stage.2", List.of("thaumcraft:rechargepedestal"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("VISAMULET", "research.VISAMULET.title",
                List.of(new Stage("research.VISAMULET.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;AUROMANCY;2"), List.of(), List.of(), 0),
                        new Stage("research.VISAMULET.stage.2", List.of("thaumcraft:VisAmulet"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("WORKBENCHCHARGER", "research.WORKBENCHCHARGER.title",
                List.of(new Stage("research.WORKBENCHCHARGER.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;AUROMANCY;1", "THEORY;AUROMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.WORKBENCHCHARGER.stage.2", List.of("thaumcraft:workbenchcharger"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FOCUSPOUCH", "research.FOCUSPOUCH.title",
                List.of(new Stage("research.FOCUSPOUCH.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;AUROMANCY;1", "OBSERVATION;ARTIFICE;1"), List.of(), List.of(), 0),
                        new Stage("research.FOCUSPOUCH.stage.2", List.of("thaumcraft:FocusPouch"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("VISBATTERY", "research.VISBATTERY.title",
                List.of(new Stage("research.VISBATTERY.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;AUROMANCY;1", "OBSERVATION;ARTIFICE;1"), List.of(), List.of(), 0),
                        new Stage("research.VISBATTERY.stage.2", List.of("thaumcraft:VisBattery"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
    }

    private static void addBASICS(Map<String, Page> dest) {
        put(dest, new Page("FIRSTSTEPS", "research.FIRSTSTEPS.title",
                List.of(new Stage("research.FIRSTSTEPS.stage.1", List.of("thaumcraft:salismundusfake"), List.of(), List.of("thaumcraft:arcane_workbench"), List.of(), List.of(), List.of(), 0),
                        new Stage("research.FIRSTSTEPS.stage.2", List.of("thaumcraft:thaumometer", "thaumcraft:salismundusfake"), List.of(), List.of("thaumcraft:thaumometer"), List.of("OBSERVATION;BASICS;1"), List.of(), List.of(), 0),
                        new Stage("research.FIRSTSTEPS.stage.3", List.of("thaumcraft:thaumometer", "thaumcraft:salismundusfake", "thaumcraft:StoneArcane", "thaumcraft:BrickArcane"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("KNOWLEDGETYPES", "research.KNOWLEDGETYPES.title",
                List.of(new Stage("research.KNOWLEDGETYPES.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("THEORYRESEARCH", "research.THEORYRESEARCH.title",
                List.of(new Stage("research.THEORYRESEARCH.stage.1", List.of("thaumcraft:tablewood", "thaumcraft:inkwell"), List.of(), List.of("thaumcraft:scribing_tools", "thaumcraft:research_table"), List.of(), List.of(), List.of(), 0),
                        new Stage("research.THEORYRESEARCH.stage.2", List.of("thaumcraft:tablewood", "thaumcraft:inkwell"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FLUX", "research.FLUX.title",
                List.of(new Stage("research.FLUX.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of("f_toomuchflux"), 0),
                        new Stage("research.FLUX.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of(new Addendum("research.FLUX.stage.2", List.of(), List.of("f_toomuchflux")))));
        put(dest, new Page("WARP", "research.WARP.title",
                List.of(new Stage("research.WARP.stage.1", List.of("thaumcraft:sanitychecker"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ORE", "research.ORE.title",
                List.of(new Stage("research.ORE.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of(new Addendum("research.ORE.amber", List.of(), List.of("!OREAMBER")), new Addendum("research.ORE.cinnabar", List.of(), List.of("!ORECINNABAR")), new Addendum("research.ORE.crystal", List.of(), List.of("!ORECRYSTAL")))));
        put(dest, new Page("PLANTS", "research.PLANTS.title",
                List.of(new Stage("research.PLANTS.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of(new Addendum("research.PLANTS.greatwood", List.of(), List.of("!PLANTWOOD")), new Addendum("research.PLANTS.silverwood", List.of(), List.of("!PLANTWOOD")), new Addendum("research.PLANTS.cinderpearl", List.of(), List.of("!PLANTCINDERPEARL")), new Addendum("research.PLANTS.shimmerleaf", List.of(), List.of("!PLANTSHIMMERLEAF")), new Addendum("research.PLANTS.vishroom", List.of(), List.of("!PLANTVISHROOM")))));
        put(dest, new Page("CRYSTALFARMER", "research.CRYSTALFARMER.title",
                List.of(new Stage("research.CRYSTALFARMER.stage.1", List.of(), List.of("thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'aer'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'terra'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'ignis'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'aqua'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'ordo'}]}", "thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'perditio'}]}"), List.of(), List.of("OBSERVATION;AUROMANCY;1", "OBSERVATION;BASICS;1"), List.of(), List.of(), 0),
                        new Stage("research.CRYSTALFARMER.stage.2", List.of("thaumcraft:CrystalClusterAir", "thaumcraft:CrystalClusterFire", "thaumcraft:CrystalClusterWater", "thaumcraft:CrystalClusterEarth", "thaumcraft:CrystalClusterOrder", "thaumcraft:CrystalClusterEntropy", "thaumcraft:CrystalClusterFlux"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("PRIMPEARL", "research.PRIMPEARL.title",
                List.of(new Stage("research.PRIMPEARL.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("UNLOCKALCHEMY", "research.UNLOCKALCHEMY.title",
                List.of(new Stage("research.UNLOCKALCHEMY.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ALCHEMY;1", "OBSERVATION;BASICS;1"), List.of(), List.of(), 0),
                        new Stage("research.UNLOCKALCHEMY.stage.2", List.of(), List.of(), List.of("thaumcraft:crucible"), List.of(), List.of(), List.of(), 0),
                        new Stage("research.UNLOCKALCHEMY.stage.3", List.of("thaumcraft:nitor", "thaumcraft:nitorcolor"), List.of(), List.of("thaumcraft:nitor;1;4"), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("UNLOCKAUROMANCY", "research.UNLOCKAUROMANCY.title",
                List.of(new Stage("research.UNLOCKAUROMANCY.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of("m_deepdown", "m_uphigh"), 0),
                        new Stage("research.UNLOCKAUROMANCY.stage.2", List.of("thaumcraft:vis_resonator", "thaumcraft:caster_basic"), List.of(), List.of("thaumcraft:vis_resonator", "thaumcraft:caster_basic"), List.of(), List.of(), List.of(), 0),
                        new Stage("research.UNLOCKAUROMANCY.stage.3", List.of("thaumcraft:vis_resonator", "thaumcraft:caster_basic"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("UNLOCKARTIFICE", "research.UNLOCKARTIFICE.title",
                List.of(new Stage("research.UNLOCKARTIFICE.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ARTIFICE;1"), List.of(), List.of("!sensus", "!machina"), 0),
                        new Stage("research.UNLOCKARTIFICE.stage.2", List.of("thaumcraft:Goggles"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("UNLOCKINFUSION", "research.UNLOCKINFUSION.title",
                List.of(new Stage("research.UNLOCKINFUSION.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;INFUSION;1"), List.of(), List.of("!auram", "!praecantatio"), 0),
                        new Stage("research.UNLOCKINFUSION.stage.2", List.of("thaumcraft:EnchantedFabric", "thaumcraft:RobeChest", "thaumcraft:RobeLegs", "thaumcraft:RobeBoots"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("UNLOCKGOLEMANCY", "research.UNLOCKGOLEMANCY.title",
                List.of(new Stage("research.UNLOCKGOLEMANCY.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;GOLEMANCY;1", "OBSERVATION;BASICS;1"), List.of(), List.of("f_golem"), 0),
                        new Stage("research.UNLOCKGOLEMANCY.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("CrimsonRites", "research.CrimsonRites.title",
                List.of(new Stage("research.CrimsonRites.stage.1", List.of(), List.of("thaumcraft:curio;1;6"), List.of(), List.of(), List.of(), List.of(), 0),
                        new Stage("research.CrimsonRites.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("UNLOCKELDRITCH", "research.UNLOCKELDRITCH.title",
                List.of(new Stage("research.UNLOCKELDRITCH.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;BASICS;1", "OBSERVATION;ELDRITCH;1"), List.of(), List.of("f_VOIDSEED"), 5),
                        new Stage("research.UNLOCKELDRITCH.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("CELESTIALSCANNING", "research.CELESTIALSCANNING.title",
                List.of(new Stage("research.CELESTIALSCANNING.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;BASICS;1", "OBSERVATION;ARTIFICE;1", "OBSERVATION;AUROMANCY;1"), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FLUXCLEANUP", "research.FLUXCLEANUP.title",
                List.of(new Stage("research.FLUXCLEANUP.stage.1", List.of(), List.of("thaumcraft:phial;1;1;{Aspects:[{amount:10,key:'vitium'}]}"), List.of(), List.of("OBSERVATION;AUROMANCY;2", "OBSERVATION;ARTIFICE;1", "OBSERVATION;BASICS;1", "THEORY;AUROMANCY;2"), List.of(), List.of(), 0),
                        new Stage("research.FLUXCLEANUP.stage.2", List.of("thaumcraft:Condenser", "thaumcraft:CondenserLattice"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FLUXRIFT", "research.FLUXRIFT.title",
                List.of(new Stage("research.FLUXRIFT.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("RIFTCLOSER", "research.RIFTCLOSER.title",
                List.of(new Stage("research.RIFTCLOSER.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;AUROMANCY;1", "THEORY;ALCHEMY;2", "THEORY;AUROMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.RIFTCLOSER.stage.2", List.of("thaumcraft:CausalityCollapser"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("!Wisp", "entity.Wisp.name",
                List.of(new Stage("research.Wisp.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("!Pech", "research.Pech.title",
                List.of(new Stage("research.Pech.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("!ThaumSlime", "entity.ThaumSlime.name",
                List.of(new Stage("research.ThaumSlime.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("!Firebat", "entity.Firebat.name",
                List.of(new Stage("research.Firebat.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("!TaintSeed", "entity.TaintSeed.name",
                List.of(new Stage("research.TaintSeed.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("!TaintCrawler", "entity.TaintCrawler.name",
                List.of(new Stage("research.TaintCrawler.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("!Taintacle", "entity.Taintacle.name",
                List.of(new Stage("research.Taintacle.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("!TaintSwarm", "entity.TaintSwarm.name",
                List.of(new Stage("research.TaintSwarm.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("!CrimsonCultist", "research.CrimsonCultist.title",
                List.of(new Stage("research.CrimsonCultist.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("!BrainyZombie", "entity.BrainyZombie.name",
                List.of(new Stage("research.BrainyZombie.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("!EldritchGuardian", "entity.EldritchGuardian.name",
                List.of(new Stage("research.EldritchGuardian.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("!EldritchCrab", "entity.EldritchCrab.name",
                List.of(new Stage("research.EldritchCrab.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
    }

    private static void addELDRITCH(Map<String, Page> dest) {
        put(dest, new Page("BASEELDRITCH", "research.BASEELDRITCH.title",
                List.of(new Stage("research.BASEELDRITCH.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;BASICS;1", "THEORY;ALCHEMY;1", "THEORY;ELDRITCH;1"), List.of(), List.of(), 2),
                        new Stage("research.BASEELDRITCH.stage.2", List.of("thaumcraft:voidingot", "thaumcraft:void_stuff"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("VOIDSIPHON", "research.VOIDSIPHON.title",
                List.of(new Stage("research.VOIDSIPHON.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ELDRITCH;1", "THEORY;ELDRITCH;1"), List.of(), List.of(), 1),
                        new Stage("research.VOIDSIPHON.stage.2", List.of("thaumcraft:VoidSiphon"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("VOIDSEERPEARL", "research.VOIDSEERPEARL.title",
                List.of(new Stage("research.VOIDSEERPEARL.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ELDRITCH;1", "THEORY;ELDRITCH;1"), List.of(), List.of(), 3),
                        new Stage("research.VOIDSEERPEARL.stage.2", List.of("thaumcraft:VoidseerPearl"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("VOIDROBEARMOR", "research.VOIDROBEARMOR.title",
                List.of(new Stage("research.VOIDROBEARMOR.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ELDRITCH;1", "THEORY;ELDRITCH;1"), List.of(), List.of(), 1),
                        new Stage("research.VOIDROBEARMOR.stage.2", List.of("thaumcraft:VoidRobeHelm", "thaumcraft:VoidRobeChest", "thaumcraft:VoidRobeLegs"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("PRIMALCRUSHER", "research.PRIMALCRUSHER.title",
                List.of(new Stage("research.PRIMALCRUSHER.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;ELDRITCH;1", "THEORY;ELDRITCH;1"), List.of(), List.of(), 1),
                        new Stage("research.PRIMALCRUSHER.stage.2", List.of("thaumcraft:PrimalCrusher"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
    }

    private static void addGOLEMANCY(Map<String, Page> dest) {
        put(dest, new Page("BASEGOLEMANCY", "research.BASEGOLEMANCY.title",
                List.of(new Stage("research.BASEGOLEMANCY.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("JARBRAIN", "research.JARBRAIN.title",
                List.of(new Stage("research.JARBRAIN.stage.1", List.of(), List.of("thaumcraft:brain"), List.of(), List.of("OBSERVATION;GOLEMANCY;1", "THEORY;GOLEMANCY;1"), List.of(), List.of("f_BRAIN"), 0),
                        new Stage("research.JARBRAIN.stage.2", List.of("thaumcraft:JarBrain"), List.of(), List.of(), List.of(), List.of(), List.of(), 3)),
                List.of()));
        put(dest, new Page("MATSTUDWOOD", "research.MATSTUDWOOD.title",
                List.of(new Stage("research.MATSTUDWOOD.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("MATSTUDIRON", "research.MATSTUDIRON.title",
                List.of(new Stage("research.MATSTUDIRON.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of("f_MATIRON"), 0)),
                List.of()));
        put(dest, new Page("MATSTUDCLAY", "research.MATSTUDCLAY.title",
                List.of(new Stage("research.MATSTUDCLAY.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of("f_MATCLAY"), 0)),
                List.of()));
        put(dest, new Page("MATSTUDBRASS", "research.MATSTUDBRASS.title",
                List.of(new Stage("research.MATSTUDBRASS.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of("f_MATBRASS"), 0)),
                List.of()));
        put(dest, new Page("MATSTUDTHAUMIUM", "research.MATSTUDTHAUMIUM.title",
                List.of(new Stage("research.MATSTUDTHAUMIUM.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of("f_MATTHAUMIUM"), 0)),
                List.of()));
        put(dest, new Page("MATSTUDVOID", "research.MATSTUDVOID.title",
                List.of(new Stage("research.MATSTUDVOID.stage.1", List.of(), List.of(), List.of(), List.of(), List.of(), List.of("f_MATVOID"), 0)),
                List.of()));
        put(dest, new Page("MINDCLOCKWORK", "research.MINDCLOCKWORK.title",
                List.of(new Stage("research.MINDCLOCKWORK.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;GOLEMANCY;1"), List.of(), List.of("!cognitio", "!victus"), 0),
                        new Stage("research.MINDCLOCKWORK.stage.2", List.of("thaumcraft:MindClockwork"), List.of(), List.of(), List.of("THEORY;ARTIFICE;1", "THEORY;GOLEMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.MINDCLOCKWORK.stage.3", List.of("thaumcraft:MindClockwork", "thaumcraft:GolemPress"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("CONTROLSEALS", "research.CONTROLSEALS.title",
                List.of(new Stage("research.CONTROLSEALS.stage.1", List.of("thaumcraft:GolemBell", "thaumcraft:SealBlank"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("GOLEMDIRECT", "research.GOLEMDIRECT.title",
                List.of(new Stage("research.GOLEMDIRECT.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.GOLEMDIRECT.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("GOLEMLOGISTICS", "research.GOLEMLOGISTICS.title",
                List.of(new Stage("research.GOLEMLOGISTICS.stage.1", List.of(), List.of("minecraft:map", "minecraft:ender_eye"), List.of(), List.of("OBSERVATION;BASICS;1", "OBSERVATION;GOLEMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.GOLEMLOGISTICS.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("MINDBIOTHAUMIC", "research.MINDBIOTHAUMIC.title",
                List.of(new Stage("research.MINDBIOTHAUMIC.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1", "OBSERVATION;ARTIFICE;1"), List.of(), List.of("f_BRAIN"), 0),
                        new Stage("research.MINDBIOTHAUMIC.stage.2", List.of("thaumcraft:MindBiothaumic"), List.of(), List.of(), List.of(), List.of(), List.of(), 3)),
                List.of()));
        put(dest, new Page("SEALCOLLECT", "research.SEALCOLLECT.title",
                List.of(new Stage("research.SEALCOLLECT.stage.1", List.of("thaumcraft:SealCollect"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of(new Addendum("research.SEALCOLLECT.stage.ADV", List.of("thaumcraft:SealCollectAdv"), List.of("MINDBIOTHAUMIC")))));
        put(dest, new Page("SEALSTORE", "research.SEALSTORE.title",
                List.of(new Stage("research.SEALSTORE.stage.1", List.of("thaumcraft:SealStore"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of(new Addendum("research.SEALSTORE.stage.ADV", List.of("thaumcraft:SealStoreAdv"), List.of("MINDBIOTHAUMIC")))));
        put(dest, new Page("SEALEMPTY", "research.SEALEMPTY.title",
                List.of(new Stage("research.SEALEMPTY.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of("!vacuos"), 0),
                        new Stage("research.SEALEMPTY.stage.2", List.of("thaumcraft:SealEmpty"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of(new Addendum("research.SEALEMPTY.stage.ADV", List.of("thaumcraft:SealEmptyAdv"), List.of("MINDBIOTHAUMIC")))));
        put(dest, new Page("SEALPROVIDE", "research.SEALPROVIDE.title",
                List.of(new Stage("research.SEALPROVIDE.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of("!desiderium"), 0),
                        new Stage("research.SEALPROVIDE.stage.2", List.of("thaumcraft:SealProvide"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("SEALSTOCK", "research.SEALSTOCK.title",
                List.of(new Stage("research.SEALSTOCK.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.SEALSTOCK.stage.2", List.of("thaumcraft:SealStock"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("SEALGUARD", "research.SEALGUARD.title",
                List.of(new Stage("research.SEALGUARD.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of("!mortuus"), 0),
                        new Stage("research.SEALGUARD.stage.2", List.of("thaumcraft:SealGuard", "thaumcraft:modaggression"), List.of(), List.of(), List.of(), List.of(), List.of(), 1)),
                List.of(new Addendum("research.SEALGUARD.stage.ADV", List.of("thaumcraft:SealGuardAdv"), List.of("MINDBIOTHAUMIC")))));
        put(dest, new Page("SEALBUTCHER", "research.SEALBUTCHER.title",
                List.of(new Stage("research.SEALBUTCHER.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of("!bestia"), 0),
                        new Stage("research.SEALBUTCHER.stage.2", List.of("thaumcraft:SealButcher"), List.of(), List.of(), List.of(), List.of(), List.of(), 1)),
                List.of()));
        put(dest, new Page("SEALUSE", "research.SEALUSE.title",
                List.of(new Stage("research.SEALUSE.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of("!instrumentum"), 0),
                        new Stage("research.SEALUSE.stage.2", List.of("thaumcraft:SealUse"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("SEALHARVEST", "research.SEALHARVEST.title",
                List.of(new Stage("research.SEALHARVEST.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of("!herba"), 0),
                        new Stage("research.SEALHARVEST.stage.2", List.of("thaumcraft:SealHarvest"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("SEALBREAK", "research.SEALBREAK.title",
                List.of(new Stage("research.SEALBREAK.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of("!perditio"), 0),
                        new Stage("research.SEALBREAK.stage.2", List.of("thaumcraft:SealBreak"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of(new Addendum("research.SEALBREAK.stage.ADV", List.of("thaumcraft:SealBreakAdv"), List.of("MINDBIOTHAUMIC")))));
        put(dest, new Page("SEALLUMBER", "research.SEALLUMBER.title",
                List.of(new Stage("research.SEALLUMBER.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of("!herba"), 0),
                        new Stage("research.SEALLUMBER.stage.2", List.of("thaumcraft:SealLumber"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("GOLEMVISION", "research.GOLEMVISION.title",
                List.of(new Stage("research.GOLEMVISION.stage.1", List.of(), List.of("minecraft:fermented_spider_eye"), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of("!sensus"), 0),
                        new Stage("research.GOLEMVISION.stage.2", List.of("thaumcraft:modvision"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("GOLEMCLIMBER", "research.GOLEMCLIMBER.title",
                List.of(new Stage("research.GOLEMCLIMBER.stage.1", List.of(), List.of("minecraft:flint"), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of("f_SPIDER"), 0),
                        new Stage("research.GOLEMCLIMBER.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("GOLEMFLYER", "research.GOLEMFLYER.title",
                List.of(new Stage("research.GOLEMFLYER.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of("f_FLY"), 0),
                        new Stage("research.GOLEMFLYER.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("GOLEMBREAKER", "research.GOLEMBREAKER.title",
                List.of(new Stage("research.GOLEMBREAKER.stage.1", List.of(), List.of("minecraft:diamond", "minecraft:piston"), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of(), 0),
                        new Stage("research.GOLEMBREAKER.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("GOLEMCOMBATADV", "research.GOLEMCOMBATADV.title",
                List.of(new Stage("research.GOLEMCOMBATADV.stage.1", List.of(), List.of("minecraft:arrow", "minecraft:shears"), List.of(), List.of("THEORY;GOLEMANCY;1"), List.of(), List.of("!aversio", "f_DISPENSER"), 0),
                        new Stage("research.GOLEMCOMBATADV.stage.2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
    }

    private static void addINFUSION(Map<String, Page> dest) {
        put(dest, new Page("BASEINFUSION", "research.BASEINFUSION.title",
                List.of(new Stage("research.BASEINFUSION.stage.1", List.of("thaumcraft:Banners", "thaumcraft:baubles_stuff"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("INFUSION", "research.INFUSION.title",
                List.of(new Stage("research.INFUSION.stage.1", List.of(), List.of("minecraft:stone", "minecraft:feather", "thaumcraft:phial;1;1;{Aspects:[{amount:10,key:'aer'}]}"), List.of(), List.of("OBSERVATION;INFUSION;1", "THEORY;INFUSION;1"), List.of(), List.of(), 0),
                        new Stage("research.INFUSION.stage.2", List.of("thaumcraft:InfusionMatrix"), List.of(), List.of("thaumcraft:infusion_matrix"), List.of(), List.of(), List.of(), 0),
                        new Stage("research.INFUSION.stage.3", List.of("thaumcraft:InfusionMatrix", "thaumcraft:ArcanePedestal", "thaumcraft:infusionaltar"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("INFUSIONSTABLE", "research.INFUSIONSTABLE.title",
                List.of(new Stage("research.INFUSIONSTABLE.stage.1", List.of(), List.of("minecraft:redstone", "thaumcraft:phial;1;1;{Aspects:[{amount:10,key:'vitium'}]}"), List.of(), List.of("OBSERVATION;INFUSION;1", "THEORY;INFUSION;1"), List.of(), List.of(), 0),
                        new Stage("research.INFUSIONSTABLE.stage.2", List.of("thaumcraft:RedstoneInlay", "thaumcraft:Stabilizer"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("INFUSIONBOOST", "research.INFUSIONBOOST.title",
                List.of(new Stage("research.INFUSIONBOOST.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;INFUSION;2"), List.of(), List.of(), 0),
                        new Stage("research.INFUSIONBOOST.stage.2", List.of("thaumcraft:MatrixMotion", "thaumcraft:MatrixCost"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("INFUSIONANCIENT", "research.INFUSIONANCIENT.title",
                List.of(new Stage("research.INFUSIONANCIENT.stage.1", List.of(), List.of("thaumcraft:stone_ancient"), List.of(), List.of("OBSERVATION;BASICS;1"), List.of(), List.of(), 0),
                        new Stage("research.INFUSIONANCIENT.stage.2", List.of("thaumcraft:AncientPedestal", "thaumcraft:infusionaltarancient"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("INFUSIONELDRITCH", "research.INFUSIONELDRITCH.title",
                List.of(new Stage("research.INFUSIONELDRITCH.stage.1", List.of(), List.of("thaumcraft:stone_eldritch_tile"), List.of(), List.of("OBSERVATION;ELDRITCH;1"), List.of(), List.of(), 0),
                        new Stage("research.INFUSIONELDRITCH.stage.2", List.of("thaumcraft:EldritchPedestal", "thaumcraft:infusionaltareldritch"), List.of(), List.of(), List.of(), List.of(), List.of(), 2)),
                List.of()));
        put(dest, new Page("PAVINGSTONES", "research.PAVINGSTONES.title",
                List.of(new Stage("research.PAVINGSTONES.stage.1", List.of("thaumcraft:arcane_stone"), List.of(), List.of("thaumcraft:arcane_stone"), List.of("OBSERVATION;INFUSION;1"), List.of(), List.of(), 0),
                        new Stage("research.PAVINGSTONES.stage.2", List.of("thaumcraft:PaveBarrier", "thaumcraft:PaveTravel", "thaumcraft:arcane_stone", "thaumcraft:arcane_brick"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("BOOTSTRAVELLER", "research.BOOTSTRAVELLER.title",
                List.of(new Stage("research.BOOTSTRAVELLER.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;INFUSION;1"), List.of(), List.of("!motus", "m_walker", "m_runner", "m_swimmer", "m_jumper"), 0),
                        new Stage("research.BOOTSTRAVELLER.stage.2", List.of("thaumcraft:BootsTraveller"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ELEMENTALTOOLS", "research.ELEMENTALTOOLS.title",
                List.of(new Stage("research.ELEMENTALTOOLS.stage.1", List.of(), List.of(), List.of("thaumcraft:thaumium_axe", "thaumcraft:thaumium_sword", "thaumcraft:thaumium_pick", "thaumcraft:thaumium_shovel", "thaumcraft:thaumium_hoe"), List.of("THEORY;INFUSION;1", "THEORY;BASICS;1"), List.of(), List.of(), 0),
                        new Stage("research.ELEMENTALTOOLS.stage.2", List.of("thaumcraft:ElementalAxe", "thaumcraft:ElementalSword", "thaumcraft:ElementalPick", "thaumcraft:ElementalShovel", "thaumcraft:ElementalHoe"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("INFUSIONENCHANTMENT", "research.INFUSIONENCHANTMENT.title",
                List.of(new Stage("research.INFUSIONENCHANTMENT.stage.1", List.of(), List.of("minecraft:thaumcraft:enchanted_placeholder;1;0;{ench:[{id:16s,lvl:1s}]}"), List.of(), List.of("THEORY;INFUSION;2"), List.of(), List.of(), 0),
                        new Stage("research.INFUSIONENCHANTMENT.stage.2", List.of("thaumcraft:IEBURROWINGFAKE", "thaumcraft:IECOLLECTORFAKE", "thaumcraft:IEDESTRUCTIVEFAKE", "thaumcraft:IEREFININGFAKE", "thaumcraft:IESOUNDINGFAKE", "thaumcraft:IEARCINGFAKE", "thaumcraft:IEESSENCEFAKE", "thaumcraft:IELAMPLIGHTFAKE"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("RUNICSHIELDING", "research.RUNICSHIELDING.title",
                List.of(new Stage("research.RUNICSHIELDING.stage.1", List.of(), List.of("minecraft:thaumcraft:enchanted_placeholder;1;0;{ench:[{id:0s,lvl:1s}]}", "thaumcraft:amber"), List.of(), List.of("THEORY;INFUSION;1"), List.of(), List.of(), 0),
                        new Stage("research.RUNICSHIELDING.stage.2", List.of("thaumcraft:RunicArmorFake0", "thaumcraft:RunicArmorFake1", "thaumcraft:RunicArmorFake2"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("ARMORFORTRESS", "research.ARMORFORTRESS.title",
                List.of(new Stage("research.ARMORFORTRESS.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;INFUSION;1"), List.of(), List.of("!praemunio"), 0),
                        new Stage("research.ARMORFORTRESS.stage.2", List.of("thaumcraft:ThaumiumFortressHelm", "thaumcraft:ThaumiumFortressChest", "thaumcraft:ThaumiumFortressLegs"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("FORTRESSMASK", "research.FORTRESSMASK.title",
                List.of(new Stage("research.FORTRESSMASK.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;INFUSION;1"), List.of(), List.of(), 0),
                        new Stage("research.FORTRESSMASK.stage.2", List.of("thaumcraft:HelmGoggles", "thaumcraft:MaskGrinningDevil", "thaumcraft:MaskAngryGhost", "thaumcraft:MaskSippingFiend"), List.of(), List.of(), List.of(), List.of(), List.of(), 2)),
                List.of()));
        put(dest, new Page("CLOUDRING", "research.CLOUDRING.title",
                List.of(new Stage("research.CLOUDRING.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;INFUSION;1"), List.of(), List.of(), 0),
                        new Stage("research.CLOUDRING.stage.2", List.of("thaumcraft:CLOUDRING"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("CURIOSITYBAND", "research.CURIOSITYBAND.title",
                List.of(new Stage("research.CURIOSITYBAND.stage.1", List.of(), List.of(), List.of(), List.of("OBSERVATION;BASICS;1", "THEORY;INFUSION;1"), List.of(), List.of(), 0),
                        new Stage("research.CURIOSITYBAND.stage.2", List.of("thaumcraft:CuriosityBand"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("VERDANTCHARMS", "research.VERDANTCHARMS.title",
                List.of(new Stage("research.VERDANTCHARMS.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;INFUSION;2"), List.of(), List.of(), 0),
                        new Stage("research.VERDANTCHARMS.stage.2", List.of("thaumcraft:VerdantHeart", "thaumcraft:VerdantHeartLife", "thaumcraft:VerdantHeartSustain"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
        put(dest, new Page("CHARMUNDYING", "research.CHARMUNDYING.title",
                List.of(new Stage("research.CHARMUNDYING.stage.1", List.of(), List.of(), List.of(), List.of("THEORY;INFUSION;1"), List.of(), List.of(), 0),
                        new Stage("research.CHARMUNDYING.stage.1", List.of("thaumcraft:CHARMUNDYING"), List.of(), List.of(), List.of(), List.of(), List.of(), 0)),
                List.of()));
    }

    public static Page get(String key) { return PAGES.get(key); }
    public static List<Page> all() { return List.copyOf(PAGES.values()); }
    public static int size() { return PAGES.size(); }
}
