package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/** Real loader-neutral ScanAspect + ScanGeneric handlers for the FIRSTSTEPS path. */
public final class CriticalScanHandlers {
    private CriticalScanHandlers() {}
    public record Target(String genericResearchKey, AspectListData aspects) {}

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
            public boolean checkThing(PlayerKnowledgeData p, Target t) { return t!=null && t.aspects()!=null && t.aspects().size()>0; }
            public String getResearchKey(PlayerKnowledgeData p, Target t) { return t.genericResearchKey(); }
            public void onSuccess(PlayerKnowledgeData p, Target t) {
                for(var g:ScanKnowledgeLogic.genericObservationGains(t.aspects(),categories)) p.addKnowledge(g.type(),g.categoryKey(),g.rawAmount());
            }
        };
    }
    /** Aspect handlers are registered before ConfigResearch's first explicit ScanGeneric. */
    public static ScanningService<PlayerKnowledgeData,Target> createCriticalRegistry(AspectRegistry aspects, ResearchCategoryRegistry categories) {
        ScanningService<PlayerKnowledgeData,Target> s=new ScanningService<>();
        for(AspectData a:aspects.values()) s.addScannableThing(aspect(a));
        s.addScannableThing(generic(categories)); return s;
    }
}
