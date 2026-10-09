package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;
import java.util.Set;

/** Real loader-neutral ScanAspect + ScanGeneric handlers for the FIRSTSTEPS path. */
public final class CriticalScanHandlers {
    private CriticalScanHandlers() {}
    public record Target(String genericResearchKey, AspectListData aspects, Set<String> specialResearchKeys) {
        public Target {
            specialResearchKeys = specialResearchKeys == null ? Set.of() : Set.copyOf(specialResearchKeys);
        }

        public Target(String genericResearchKey, AspectListData aspects) {
            this(genericResearchKey, aspects, Set.of());
        }
    }

    public static ScanThing<PlayerKnowledgeData,Target> aspect(AspectData aspect) {
        Objects.requireNonNull(aspect);
        return new ScanThing<>() {
            public boolean checkThing(PlayerKnowledgeData p, Target t) { return t!=null && t.aspects()!=null && t.aspects().getAmount(aspect)>0; }
            public String getResearchKey(PlayerKnowledgeData p, Target t) { return "!"+aspect.tag(); }
            public void onSuccess(PlayerKnowledgeData p, Target t) {
                for (var g: ScanKnowledgeLogic.aspectDiscoveryGains()) p.addKnowledge(g.type(),g.categoryKey(),g.rawAmount());
            }
        };
    }
    public static ScanThing<PlayerKnowledgeData,Target> generic(ResearchCategoryRegistry categories) {
        return new ScanThing<>() {
            public boolean checkThing(PlayerKnowledgeData p, Target t) {
                return t!=null && t.genericResearchKey()!=null && t.aspects()!=null && t.aspects().size()>0;
            }
            public String getResearchKey(PlayerKnowledgeData p, Target t) { return t.genericResearchKey(); }
            public void onSuccess(PlayerKnowledgeData p, Target t) {
                for(var g:ScanKnowledgeLogic.genericObservationGains(t.aspects(),categories)) p.addKnowledge(g.type(),g.categoryKey(),g.rawAmount());
            }
        };
    }
    public static ScanThing<PlayerKnowledgeData,Target> special(String researchKey) {
        Objects.requireNonNull(researchKey);
        return new ScanThing<>() {
            public boolean checkThing(PlayerKnowledgeData p, Target t) {
                return t != null && t.specialResearchKeys().contains(researchKey);
            }
            public String getResearchKey(PlayerKnowledgeData p, Target t) { return researchKey; }
        };
    }

    /**
     * Aspect handlers precede ConfigResearch's ScanGeneric. The vanilla special
     * scans below preserve their BETA26 registration order for the slice already
     * mapped to 1.21.1 identities.
     */
    public static ScanningService<PlayerKnowledgeData,Target> createCriticalRegistry(AspectRegistry aspects, ResearchCategoryRegistry categories) {
        ScanningService<PlayerKnowledgeData,Target> s=new ScanningService<>();
        for(AspectData a:aspects.values()) s.addScannableThing(aspect(a));
        s.addScannableThing(generic(categories));
        // ConfigResearch registers vanilla enchantments, then potion effects,
        // before its explicit ScanEntity/ScanBlock/ScanItem/OreDictionary sites.
        // The loader bridge translates the modern enchantment/effect ids to
        // these original 1.12 getName() research keys.
        for (String key : LegacyEnchantmentEffectScans.allResearchKeys()) {
            s.addScannableThing(special(key));
        }
        for (String key : LegacyVanillaSpecialScans.REGISTRATION_ORDER) s.addScannableThing(special(key));
        return s;
    }
}
