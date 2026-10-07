package dev.ehtid.thaumcraft.port.core;

/** Exact seven category definitions extracted from BETA26 ConfigResearch.initCategories bytecode. */
public final class BuiltInResearchCategories {
    private static final String OVER = "thaumcraft:textures/gui/gui_research_back_over.png";

    private BuiltInResearchCategories() {}

    public static ResearchCategoryRegistry create(AspectRegistry a) {
        ResearchCategoryRegistry r = new ResearchCategoryRegistry();
        r.register(category("BASICS", null,
                formula(a.PLANT,5, a.ORDER,5, a.ENTROPY,5, a.AIR,5, a.FIRE,5, a.EARTH,3, a.WATER,5),
                "thaumcraft:textures/items/thaumonomicon_cheat.png", "thaumcraft:textures/gui/gui_research_back_1.jpg"));
        r.register(category("AUROMANCY", "UNLOCKAUROMANCY",
                formula(a.AURA,20, a.MAGIC,20, a.FLUX,15, a.CRYSTAL,5, a.COLD,5, a.AIR,5),
                "thaumcraft:textures/research/cat_auromancy.png", "thaumcraft:textures/gui/gui_research_back_2.jpg"));
        r.register(category("ALCHEMY", "UNLOCKALCHEMY",
                formula(a.ALCHEMY,30, a.FLUX,10, a.MAGIC,10, a.LIFE,5, a.AVERSION,5, a.DESIRE,5, a.WATER,5),
                "thaumcraft:textures/research/cat_alchemy.png", "thaumcraft:textures/gui/gui_research_back_3.jpg"));
        r.register(category("ARTIFICE", "UNLOCKARTIFICE",
                formula(a.MECHANISM,10, a.CRAFT,10, a.METAL,10, a.TOOL,10, a.ENERGY,10, a.LIGHT,5, a.FLIGHT,5, a.TRAP,5, a.FIRE,5),
                "thaumcraft:textures/research/cat_artifice.png", "thaumcraft:textures/gui/gui_research_back_4.jpg"));
        r.register(category("INFUSION", "UNLOCKINFUSION",
                formula(a.MAGIC,30, a.PROTECT,10, a.TOOL,10, a.FLUX,5, a.CRAFT,5, a.SOUL,5, a.EARTH,3),
                "thaumcraft:textures/research/cat_infusion.png", "thaumcraft:textures/gui/gui_research_back_7.jpg"));
        r.register(category("GOLEMANCY", "UNLOCKGOLEMANCY",
                formula(a.MAN,20, a.MOTION,10, a.MIND,10, a.MECHANISM,10, a.EXCHANGE,5, a.SENSES,5, a.BEAST,5, a.ORDER,5),
                "thaumcraft:textures/research/cat_golemancy.png", "thaumcraft:textures/gui/gui_research_back_5.jpg"));
        r.register(category("ELDRITCH", "UNLOCKELDRITCH",
                formula(a.ELDRITCH,20, a.DARKNESS,10, a.MAGIC,5, a.MIND,5, a.VOID,5, a.DEATH,5, a.UNDEAD,5, a.ENTROPY,5),
                "thaumcraft:textures/research/cat_eldritch.png", "thaumcraft:textures/gui/gui_research_back_6.jpg"));
        return r;
    }

    private static ResearchCategoryData category(String key, String researchKey, AspectListData formula,
                                                  String icon, String background) {
        return new ResearchCategoryData(key, "tc.research_category." + key, icon, background, OVER, researchKey, formula);
    }

    private static AspectListData formula(Object... pairs) {
        AspectListData list = new AspectListData();
        for (int i = 0; i < pairs.length; i += 2) {
            list.add((AspectData) pairs[i], (Integer) pairs[i + 1]);
        }
        return list;
    }
}
