package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * EXACT BETA26 source research positioning, copied from
 * analysis/research_manifest.json (148 records, original source x/y).
 * The manifest is copied from original Thaumcraft 6.1.BETA26 research JSON.
 * No invented positions, invented recipe items or inferred GUI nodes.
 *
 * Coordinates are the original research-grid coordinates, NOT pixels.
 * The client is responsible for viewport cropping, scaling and scrolling.
 */
public final class LegacyResearchLayout {
    private LegacyResearchLayout() {}

    public record Node(String key, String category, int x, int y,
                       String titleKey, String iconSpec) {}

    private static final Map<String, Node> NODES = build();

    private static Map<String, Node> build() {
        LinkedHashMap<String, Node> ordered = new LinkedHashMap<>();
        register(ordered, "BASEALCHEMY", "ALCHEMY", 0, 0, "research.BASEALCHEMY.title", "thaumcraft:textures/research/cat_alchemy.png");
        register(ordered, "ALUMENTUM", "ALCHEMY", 2, -1, "research.ALUMENTUM.title", "thaumcraft:textures/items/alumentum.png");
        register(ordered, "METALLURGY", "ALCHEMY", 2, 2, "research.METALLURGY.title", "thaumcraft:textures/items/ingot_brass.png");
        register(ordered, "METALPURIFICATION", "ALCHEMY", 2, 4, "research.METALPURIFICATION.title", "thaumcraft:textures/items/cluster_iron.png");
        register(ordered, "HEDGEALCHEMY", "ALCHEMY", -2, 0, "research.HEDGEALCHEMY.title", "thaumcraft:textures/items/tallow.png");
        register(ordered, "LIQUIDDEATH", "ALCHEMY", -4, 2, "research.LIQUIDDEATH.title", "thaumcraft:textures/items/bucket_death.png");
        register(ordered, "BOTTLETAINT", "ALCHEMY", -6, 0, "research.BOTTLETAINT.title", "thaumcraft:bottle_taint");
        register(ordered, "BATHSALTS", "ALCHEMY", -4, -2, "research.BATHSALTS.title", "thaumcraft:bath_salts");
        register(ordered, "SANESOAP", "ALCHEMY", -3, -4, "research.SANESOAP.title", "thaumcraft:sanity_soap");
        register(ordered, "ARCANESPA", "ALCHEMY", -5, -4, "research.ARCANESPA.title", "thaumcraft:spa");
        register(ordered, "ESSENTIASMELTER", "ALCHEMY", 4, 0, "research.ESSENTIASMELTER.title", "thaumcraft:smelter_basic");
        register(ordered, "WARDEDJARS", "ALCHEMY", 4, -2, "research.WARDEDJARS.title", "thaumcraft:jar_normal");
        register(ordered, "TUBES", "ALCHEMY", 6, -2, "research.TUBES.title", "thaumcraft:tube");
        register(ordered, "ESSENTIASMELTERTHAUMIUM", "ALCHEMY", 8, 0, "research.ESSENTIASMELTERTHAUMIUM.title", "thaumcraft:smelter_thaumium");
        register(ordered, "ESSENTIASMELTERVOID", "ALCHEMY", 12, 0, "research.ESSENTIASMELTERVOID.title", "thaumcraft:smelter_void");
        register(ordered, "IMPROVEDSMELTING", "ALCHEMY", 5, -4, "research.IMPROVEDSMELTING.title", "thaumcraft:smelter_aux");
        register(ordered, "IMPROVEDSMELTING2", "ALCHEMY", 3, -4, "research.IMPROVEDSMELTING2.title", "thaumcraft:smelter_vent");
        register(ordered, "CENTRIFUGE", "ALCHEMY", 7, -4, "research.CENTRIFUGE.title", "thaumcraft:centrifuge");
        register(ordered, "THAUMATORIUM", "ALCHEMY", 10, -2, "research.THAUMATORIUM.title", "thaumcraft:thaumatorium");
        register(ordered, "ESSENTIATRANSPORT", "ALCHEMY", 12, -2, "research.ESSENTIATRANSPORT.title", "thaumcraft:essentia_input");
        register(ordered, "EVERFULLURN", "ALCHEMY", -2, 2, "research.EVERFULLURN.title", "thaumcraft:everfull_urn");
        register(ordered, "POTIONSPRAYER", "ALCHEMY", -1, -2, "research.POTIONSPRAYER.title", "thaumcraft:potion_sprayer");
        register(ordered, "BASEARTIFICE", "ARTIFICE", 0, 0, "research.BASEARTIFICE.title", "thaumcraft:textures/research/cat_artifice.png");
        register(ordered, "HUNGRYCHEST", "ARTIFICE", 3, 1, "research.HUNGRYCHEST.title", "thaumcraft:hungry_chest");
        register(ordered, "LEVITATOR", "ARTIFICE", 5, 3, "research.LEVITATOR.title", "thaumcraft:levitator");
        register(ordered, "INFERNALFURNACE", "ARTIFICE", 3, -1, "research.INFERNALFURNACE.title", "thaumcraft:infernal_furnace");
        register(ordered, "BELLOWS", "ARTIFICE", 5, -3, "research.BELLOWS.title", "thaumcraft:bellows");
        register(ordered, "REDSTONERELAY", "ARTIFICE", 0, -2, "research.REDSTONERELAY.title", "thaumcraft:redstone_relay");
        register(ordered, "ARCANEEAR", "ARTIFICE", 0, -4, "research.ARCANEEAR.title", "thaumcraft:arcane_ear");
        register(ordered, "DIOPTRA", "ARTIFICE", -2, 3, "research.DIOPTRA.title", "thaumcraft:dioptra");
        register(ordered, "MIRROR", "ARTIFICE", -6, 0, "research.MIRROR.title", "thaumcraft:mirror");
        register(ordered, "MIRRORHAND", "ARTIFICE", -7, 1, "research.MIRRORHAND.title", "thaumcraft:hand_mirror");
        register(ordered, "MIRRORESSENTIA", "ARTIFICE", -7, -1, "research.MIRRORESSENTIA.title", "thaumcraft:mirror_essentia");
        register(ordered, "ARCANELAMP", "ARTIFICE", -3, 0, "research.ARCANELAMP.title", "thaumcraft:lamp_arcane");
        register(ordered, "LAMPGROWTH", "ARTIFICE", -4, 1, "research.LAMPGROWTH.title", "thaumcraft:lamp_growth");
        register(ordered, "LAMPFERTILITY", "ARTIFICE", -4, -1, "research.LAMPFERTILITY.title", "thaumcraft:lamp_fertility");
        register(ordered, "ARCANEPATTERNCRAFTER", "ARTIFICE", -2, 5, "research.ARCANEPATTERNCRAFTER.title", "thaumcraft:pattern_crafter");
        register(ordered, "BASICTURRET", "ARTIFICE", -2, -4, "research.BASICTURRET.title", "thaumcraft:turret;1;0");
        register(ordered, "ADVANCEDTURRET", "ARTIFICE", -4, -4, "research.ADVANCEDTURRET.title", "thaumcraft:turret;1;1");
        register(ordered, "ARCANEBORE", "ARTIFICE", -3, -6, "research.ARCANEBORE.title", "thaumcraft:turret;1;2");
        register(ordered, "GRAPPLEGUN", "ARTIFICE", 2, 3, "research.GRAPPLEGUN.title", "thaumcraft:grapple_gun");
        register(ordered, "VISGENERATOR", "ARTIFICE", 2, -3, "research.VISGENERATOR.title", "thaumcraft:vis_generator");
        register(ordered, "BASEAUROMANCY", "AUROMANCY", 0, 0, "research.BASEAUROMANCY.title", "thaumcraft:textures/research/cat_auromancy.png");
        register(ordered, "FOCUSADVANCED", "AUROMANCY", 0, -2, "research.FOCUSADVANCED.title", "thaumcraft:focus_2");
        register(ordered, "FOCUSGREATER", "AUROMANCY", 0, -4, "research.FOCUSGREATER.title", "thaumcraft:focus_3");
        register(ordered, "FOCUSELEMENTAL", "AUROMANCY", 0, 2, "research.FOCUSELEMENTAL.title", "focus:thaumcraft.AIR");
        register(ordered, "FOCUSFLUX", "AUROMANCY", 0, 4, "research.FOCUSFLUX.title", "focus:thaumcraft.FLUX");
        register(ordered, "FOCUSHEAL", "AUROMANCY", 0, 7, "research.FOCUSHEAL.title", "focus:thaumcraft.HEAL");
        register(ordered, "FOCUSCURSE", "AUROMANCY", -2, 6, "research.FOCUSCURSE.title", "focus:thaumcraft.CURSE");
        register(ordered, "FOCUSBREAK", "AUROMANCY", 2, 6, "research.FOCUSBREAK.title", "focus:thaumcraft.BREAK");
        register(ordered, "FOCUSEXCHANGE", "AUROMANCY", 2, 4, "research.FOCUSEXCHANGE.title", "focus:thaumcraft.EXCHANGE");
        register(ordered, "FOCUSPLAN", "AUROMANCY", 3, 2, "research.FOCUSPLAN.title", "focus:thaumcraft.PLAN");
        register(ordered, "FOCUSRIFT", "AUROMANCY", 4, 5, "research.FOCUSRIFT.title", "focus:thaumcraft.RIFT");
        register(ordered, "FOCUSPROJECTILE", "AUROMANCY", 3, 0, "research.FOCUSPROJECTILE.title", "focus:thaumcraft.PROJECTILE");
        register(ordered, "FOCUSBOLT", "AUROMANCY", 7, 0, "research.FOCUSBOLT.title", "focus:thaumcraft.BOLT");
        register(ordered, "FOCUSSCATTER", "AUROMANCY", 5, -1, "research.FOCUSSCATTER.title", "focus:thaumcraft.SCATTER");
        register(ordered, "FOCUSMINE", "AUROMANCY", 5, 2, "research.FOCUSMINE.title", "focus:thaumcraft.MINE");
        register(ordered, "FOCUSSPELLBAT", "AUROMANCY", 7, 4, "research.FOCUSSPELLBAT.title", "focus:thaumcraft.SPELLBAT");
        register(ordered, "FOCUSCLOUD", "AUROMANCY", 7, 2, "research.FOCUSCLOUD.title", "focus:thaumcraft.CLOUD");
        register(ordered, "FOCUSSPLIT", "AUROMANCY", 5, -3, "research.FOCUSSPLIT.title", "focus:thaumcraft.SPLITTARGET");
        register(ordered, "RECHARGEPEDESTAL", "AUROMANCY", -3, 2, "research.RECHARGEPEDESTAL.title", "thaumcraft:recharge_pedestal");
        register(ordered, "VISAMULET", "AUROMANCY", -5, 4, "research.VISAMULET.title", "thaumcraft:amulet_vis;1;1");
        register(ordered, "WORKBENCHCHARGER", "AUROMANCY", -3, 4, "research.WORKBENCHCHARGER.title", "thaumcraft:arcane_workbench_charger");
        register(ordered, "FOCUSPOUCH", "AUROMANCY", -2, -1, "research.FOCUSPOUCH.title", "thaumcraft:focus_pouch");
        register(ordered, "VISBATTERY", "AUROMANCY", -5, 0, "research.VISBATTERY.title", "thaumcraft:vis_battery");
        register(ordered, "FIRSTSTEPS", "BASICS", 0, 0, "research.FIRSTSTEPS.title", "thaumcraft:textures/items/thaumonomicon.png");
        register(ordered, "KNOWLEDGETYPES", "BASICS", 2, -1, "research.KNOWLEDGETYPES.title", "thaumcraft:textures/research/knowledge_theory.png");
        register(ordered, "THEORYRESEARCH", "BASICS", 4, -1, "research.THEORYRESEARCH.title", "thaumcraft:textures/research/knowledge_theory.png");
        register(ordered, "FLUX", "BASICS", -2, -2, "research.FLUX.title", "thaumcraft:textures/research/r_flux.png");
        register(ordered, "WARP", "BASICS", -4, -2, "research.WARP.title", "thaumcraft:textures/research/r_warp.png");
        register(ordered, "ORE", "BASICS", -4, 0, "research.ORE.title", "minecraft:iron_pickaxe");
        register(ordered, "PLANTS", "BASICS", -3, 0, "research.PLANTS.title", "thaumcraft:sapling_greatwood");
        register(ordered, "CRYSTALFARMER", "BASICS", -5, 1, "research.CRYSTALFARMER.title", "thaumcraft:crystal_aer");
        register(ordered, "PRIMPEARL", "BASICS", -8, 4, "research.PRIMPEARL.title", "thaumcraft:primordial_pearl");
        register(ordered, "UNLOCKALCHEMY", "BASICS", 0, 2, "research.UNLOCKALCHEMY.title", "thaumcraft:textures/research/cat_alchemy.png");
        register(ordered, "UNLOCKAUROMANCY", "BASICS", -2, 4, "research.UNLOCKAUROMANCY.title", "thaumcraft:textures/research/cat_auromancy.png");
        register(ordered, "UNLOCKARTIFICE", "BASICS", 2, 4, "research.UNLOCKARTIFICE.title", "thaumcraft:textures/research/cat_artifice.png");
        register(ordered, "UNLOCKINFUSION", "BASICS", 0, 4, "research.UNLOCKINFUSION.title", "thaumcraft:textures/research/cat_infusion.png");
        register(ordered, "UNLOCKGOLEMANCY", "BASICS", 0, 6, "research.UNLOCKGOLEMANCY.title", "thaumcraft:textures/research/cat_golemancy.png");
        register(ordered, "CrimsonRites", "BASICS", -6, 4, "research.CrimsonRites.title", "thaumcraft:curio;1;6");
        register(ordered, "UNLOCKELDRITCH", "BASICS", -4, 6, "research.UNLOCKELDRITCH.title", "thaumcraft:textures/research/cat_eldritch.png");
        register(ordered, "CELESTIALSCANNING", "BASICS", 4, 1, "research.CELESTIALSCANNING.title", "thaumcraft:textures/research/r_celestial.png");
        register(ordered, "FLUXCLEANUP", "BASICS", -2, -4, "research.FLUXCLEANUP.title", "thaumcraft:condenser");
        register(ordered, "FLUXRIFT", "BASICS", 0, -3, "research.FLUXRIFT.title", "thaumcraft:textures/research/r_fluxrift.png");
        register(ordered, "RIFTCLOSER", "BASICS", 0, -5, "research.RIFTCLOSER.title", "thaumcraft:causality_collapser");
        register(ordered, "BASEELDRITCH", "ELDRITCH", 0, 0, "research.BASEELDRITCH.title", "thaumcraft:void_seed");
        register(ordered, "VOIDSIPHON", "ELDRITCH", 3, 0, "research.VOIDSIPHON.title", "thaumcraft:void_siphon");
        register(ordered, "VOIDSEERPEARL", "ELDRITCH", -3, 0, "research.VOIDSEERPEARL.title", "thaumcraft:voidseer_charm");
        register(ordered, "VOIDROBEARMOR", "ELDRITCH", 0, 3, "research.VOIDROBEARMOR.title", "thaumcraft:void_robe_helm");
        register(ordered, "PRIMALCRUSHER", "ELDRITCH", 0, -3, "research.PRIMALCRUSHER.title", "thaumcraft:primal_crusher");
        register(ordered, "BASEGOLEMANCY", "GOLEMANCY", 0, -1, "research.BASEGOLEMANCY.title", "thaumcraft:textures/research/cat_golemancy.png");
        register(ordered, "JARBRAIN", "GOLEMANCY", 2, 1, "research.JARBRAIN.title", "thaumcraft:jar_brain");
        register(ordered, "MATSTUDWOOD", "GOLEMANCY", -2, 0, "research.MATSTUDWOOD.title", "thaumcraft:log_greatwood");
        register(ordered, "MATSTUDIRON", "GOLEMANCY", -3, -1, "research.MATSTUDIRON.title", "thaumcraft:plate;1;1");
        register(ordered, "MATSTUDCLAY", "GOLEMANCY", -4, 0, "research.MATSTUDCLAY.title", "minecraft:hardened_clay");
        register(ordered, "MATSTUDBRASS", "GOLEMANCY", -4, -2, "research.MATSTUDBRASS.title", "thaumcraft:plate");
        register(ordered, "MATSTUDTHAUMIUM", "GOLEMANCY", -5, -1, "research.MATSTUDTHAUMIUM.title", "thaumcraft:plate;1;2");
        register(ordered, "MATSTUDVOID", "GOLEMANCY", -6, 0, "research.MATSTUDVOID.title", "thaumcraft:plate;1;3");
        register(ordered, "MINDCLOCKWORK", "GOLEMANCY", 0, 3, "research.MINDCLOCKWORK.title", "thaumcraft:mind");
        register(ordered, "CONTROLSEALS", "GOLEMANCY", -2, 5, "research.CONTROLSEALS.title", "thaumcraft:seal");
        register(ordered, "GOLEMDIRECT", "GOLEMANCY", 1, 4, "research.GOLEMDIRECT.title", "thaumcraft:textures/misc/golem/head_basic.png");
        register(ordered, "GOLEMLOGISTICS", "GOLEMANCY", 3, 4, "research.GOLEMLOGISTICS.title", "thaumcraft:textures/research/golem_logistics.png");
        register(ordered, "MINDBIOTHAUMIC", "GOLEMANCY", 0, 7, "research.MINDBIOTHAUMIC.title", "thaumcraft:mind;1;1");
        register(ordered, "SEALCOLLECT", "GOLEMANCY", -4, 4, "research.SEALCOLLECT.title", "thaumcraft:textures/items/seals/seal_pickup.png");
        register(ordered, "SEALSTORE", "GOLEMANCY", -6, 4, "research.SEALSTORE.title", "thaumcraft:textures/items/seals/seal_fill.png");
        register(ordered, "SEALEMPTY", "GOLEMANCY", -8, 4, "research.SEALEMPTY.title", "thaumcraft:textures/items/seals/seal_empty.png");
        register(ordered, "SEALPROVIDE", "GOLEMANCY", -7, 6, "research.SEALPROVIDE.title", "thaumcraft:textures/items/seals/seal_provider.png");
        register(ordered, "SEALSTOCK", "GOLEMANCY", -6, 8, "research.SEALSTOCK.title", "thaumcraft:textures/items/seals/seal_stock.png");
        register(ordered, "SEALGUARD", "GOLEMANCY", -12, 4, "research.SEALGUARD.title", "thaumcraft:textures/items/seals/seal_guard.png");
        register(ordered, "SEALBUTCHER", "GOLEMANCY", -11, 6, "research.SEALBUTCHER.title", "thaumcraft:textures/items/seals/seal_butcher.png");
        register(ordered, "SEALUSE", "GOLEMANCY", -3, 6, "research.SEALUSE.title", "thaumcraft:textures/items/seals/seal_use.png");
        register(ordered, "SEALHARVEST", "GOLEMANCY", -5, 6, "research.SEALHARVEST.title", "thaumcraft:textures/items/seals/seal_harvest.png");
        register(ordered, "SEALBREAK", "GOLEMANCY", -10, 4, "research.SEALBREAK.title", "thaumcraft:textures/items/seals/seal_breaker.png");
        register(ordered, "SEALLUMBER", "GOLEMANCY", -9, 6, "research.SEALLUMBER.title", "thaumcraft:textures/items/seals/seal_lumber.png");
        register(ordered, "GOLEMVISION", "GOLEMANCY", -2, 2, "research.GOLEMVISION.title", "thaumcraft:module");
        register(ordered, "GOLEMCLIMBER", "GOLEMANCY", -4, 2, "research.GOLEMCLIMBER.title", "thaumcraft:textures/misc/golem/legs_climber.png");
        register(ordered, "GOLEMFLYER", "GOLEMANCY", -6, 2, "research.GOLEMFLYER.title", "thaumcraft:textures/misc/golem/legs_flyer.png");
        register(ordered, "GOLEMBREAKER", "GOLEMANCY", -8, 2, "research.GOLEMBREAKER.title", "thaumcraft:textures/misc/golem/arms_breakers.png");
        register(ordered, "GOLEMCOMBATADV", "GOLEMANCY", -12, 2, "research.GOLEMCOMBATADV.title", "thaumcraft:textures/misc/golem/addon_fighter.png");
        register(ordered, "BASEINFUSION", "INFUSION", 0, 0, "research.BASEINFUSION.title", "thaumcraft:textures/research/cat_infusion.png");
        register(ordered, "INFUSION", "INFUSION", 0, 2, "research.INFUSION.title", "thaumcraft:infusion_matrix");
        register(ordered, "INFUSIONSTABLE", "INFUSION", 1, 4, "research.INFUSIONSTABLE.title", "thaumcraft:stabilizer");
        register(ordered, "INFUSIONBOOST", "INFUSION", -1, 4, "research.INFUSIONBOOST.title", "thaumcraft:matrix_cost");
        register(ordered, "INFUSIONANCIENT", "INFUSION", 0, 6, "research.INFUSIONANCIENT.title", "thaumcraft:stone_ancient");
        register(ordered, "INFUSIONELDRITCH", "INFUSION", -2, 6, "research.INFUSIONELDRITCH.title", "thaumcraft:stone_eldritch_tile");
        register(ordered, "PAVINGSTONES", "INFUSION", 0, -2, "research.PAVINGSTONES.title", "thaumcraft:paving_stone_barrier");
        register(ordered, "BOOTSTRAVELLER", "INFUSION", -2, 2, "research.BOOTSTRAVELLER.title", "thaumcraft:traveller_boots");
        register(ordered, "ELEMENTALTOOLS", "INFUSION", -4, 3, "research.ELEMENTALTOOLS.title", "thaumcraft:elemental_axe");
        register(ordered, "INFUSIONENCHANTMENT", "INFUSION", -6, 4, "research.INFUSIONENCHANTMENT.title", "thaumcraft:textures/research/r_inf_enchant.png");
        register(ordered, "RUNICSHIELDING", "INFUSION", -6, 6, "research.RUNICSHIELDING.title", "thaumcraft:textures/research/r_runicupg.png");
        register(ordered, "ARMORFORTRESS", "INFUSION", -4, 1, "research.ARMORFORTRESS.title", "thaumcraft:fortress_helm");
        register(ordered, "FORTRESSMASK", "INFUSION", -5, 0, "research.FORTRESSMASK.title", "thaumcraft:textures/research/r_mask0.png");
        register(ordered, "CLOUDRING", "INFUSION", 2, 2, "research.CLOUDRING.title", "thaumcraft:cloud_ring");
        register(ordered, "CURIOSITYBAND", "INFUSION", 4, 1, "research.CURIOSITYBAND.title", "thaumcraft:curiosity_band");
        register(ordered, "VERDANTCHARMS", "INFUSION", 4, 3, "research.VERDANTCHARMS.title", "thaumcraft:verdant_charm");
        register(ordered, "CHARMUNDYING", "INFUSION", 3, -1, "research.CHARMUNDYING.title", "thaumcraft:charm_undying");
        register(ordered, "!Wisp", "BASICS", -7, 0, "entity.Wisp.name", "thaumcraft:textures/research/r_wisp.png");
        register(ordered, "!Pech", "BASICS", -8, 0, "research.Pech.title", "thaumcraft:textures/research/r_pech.png");
        register(ordered, "!ThaumSlime", "BASICS", -9, 0, "entity.ThaumSlime.name", "thaumcraft:textures/research/r_thaumslime.png");
        register(ordered, "!Firebat", "BASICS", -10, 0, "entity.Firebat.name", "thaumcraft:textures/research/r_firebat.png");
        register(ordered, "!TaintSeed", "BASICS", -7, 1, "entity.TaintSeed.name", "thaumcraft:textures/research/r_taintseed.png");
        register(ordered, "!TaintCrawler", "BASICS", -8, 1, "entity.TaintCrawler.name", "thaumcraft:textures/research/r_taintcrawler.png");
        register(ordered, "!Taintacle", "BASICS", -9, 1, "entity.Taintacle.name", "thaumcraft:textures/research/r_taintacle.png");
        register(ordered, "!TaintSwarm", "BASICS", -10, 1, "entity.TaintSwarm.name", "thaumcraft:textures/research/r_taintswarm.png");
        register(ordered, "!CrimsonCultist", "BASICS", -7, 2, "research.CrimsonCultist.title", "thaumcraft:textures/research/r_cultist.png");
        register(ordered, "!BrainyZombie", "BASICS", -8, 2, "entity.BrainyZombie.name", "thaumcraft:textures/research/r_angryzombie.png");
        register(ordered, "!EldritchGuardian", "BASICS", -9, 2, "entity.EldritchGuardian.name", "thaumcraft:textures/research/r_eldritchguardian.png");
        register(ordered, "!EldritchCrab", "BASICS", -10, 2, "entity.EldritchCrab.name", "thaumcraft:textures/research/r_crab.png");
        if (ordered.size() != 148) throw new IllegalStateException(
                "BETA26 research layout must have 148 source nodes");
        return Collections.unmodifiableMap(ordered);
    }

    private static void register(Map<String, Node> data, String key, String category,
                                 int x, int y, String titleKey, String iconSpec) {
        if (data.putIfAbsent(key,
                new Node(key, category, x, y, titleKey, iconSpec)) != null)
            throw new IllegalStateException("Duplicate BETA26 research key " + key);
    }

    public static Node get(String researchKey) {
        return NODES.get(researchKey);
    }

    public static List<Node> all() {
        return List.copyOf(NODES.values());
    }

    public static List<Node> category(String categoryKey) {
        Objects.requireNonNull(categoryKey);
        ArrayList<Node> selected = new ArrayList<>();
        for (Node node : NODES.values())
            if (node.category().equals(categoryKey)) selected.add(node);
        return List.copyOf(selected);
    }

    public static int size() {
        return NODES.size();
    }
}
