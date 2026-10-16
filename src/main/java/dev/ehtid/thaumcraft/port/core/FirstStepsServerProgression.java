package dev.ehtid.thaumcraft.port.core;

/** PacketSyncProgressToServer-style server gate specialized to the critical FIRSTSTEPS stages. */
public final class FirstStepsServerProgression {
    public static final int ARCANE_WORKBENCH_CRAFT_HASH=405059183; // hash("1xtile.arcane_workbench@0")
    public static final String ARCANE_WORKBENCH_MARKER="[#]"+ARCANE_WORKBENCH_CRAFT_HASH;
    private boolean noFlagsLatch;
    public int requestedStageIndex(PlayerKnowledgeData k){ return k.getResearchStage("FIRSTSTEPS")-1; }
    public boolean requisitesSatisfied(PlayerKnowledgeData k){
        int i=requestedStageIndex(k); // legacy: unknown (-1) becomes -2 and is rejected.
        if(i<0) return false;
        if(i>=3) return true;
        return switch(i){
            case 0 -> k.isResearchKnown(ARCANE_WORKBENCH_MARKER);
            case 1 -> k.isResearchKnown(ThaumometerRecipeLogic.LEGACY_CRAFT_MARKER) && k.getKnowledge(KnowledgeType.OBSERVATION,"BASICS")>=1;
            case 2 -> true;
            default -> false;
        };
    }
    public void setNoFlagsFromPacket(boolean value){ if(value) noFlagsLatch=true; }
    public boolean noFlagsLatch(){ return noFlagsLatch; }
    public ResearchProgressionService.Result request(PlayerKnowledgeData k, PlayerWarpData w, ResearchProgressionService service, boolean packetExpectedKnown, boolean checkRequisites, boolean packetNoFlags){
        if(packetExpectedKnown!=k.isResearchKnown("FIRSTSTEPS")) return rejected();
        // Client-supplied checkRequisites is a legacy UI hint, NOT a
        // permission to skip server progression gates. The FIRSTSTEPS
        // bootstrap is the sole exception: a player who actually acquired
        // their Thaumonomicon can START stage 1 without a craft marker.
        // Every subsequent stage must be authorized from server state.
        if (!k.isResearchKnown("FIRSTSTEPS")) {
            if (!k.isResearchKnown("!gotthaumonomicon")) return rejected();
        } else if (!requisitesSatisfied(k)) {
            return rejected();
        }
        if(packetNoFlags) noFlagsLatch=true;
        var r=service.progress(k,w,"FIRSTSTEPS",false,true,noFlagsLatch,false,true);
        // BETA26 ResearchManager.noFlags is reset by a completed research path; siblings can therefore clear it indirectly.
        if(r.completed()) noFlagsLatch=false;
        return r;
    }
    private static ResearchProgressionService.Result rejected(){ return new ResearchProgressionService.Result(false,false,-1,ResearchProgressionService.WarpGain.NONE,java.util.List.of(),java.util.List.of(),false,0,java.util.List.of()); }
}
