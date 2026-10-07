package dev.ehtid.thaumcraft.port.core;

import java.util.List;

/** Required-research part of a TC6 ResearchAddendum. */
public record ResearchAddendumData(List<String> requiredResearch) {
    public ResearchAddendumData {
        requiredResearch = requiredResearch == null ? List.of() : List.copyOf(requiredResearch);
    }
}
