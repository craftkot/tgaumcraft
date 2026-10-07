package dev.ehtid.thaumcraft.port.core;

/** Minimal lookup needed by player progression, independent of Minecraft classes. */
@FunctionalInterface
public interface ResearchIndex {
    /** Return stage count for a known JSON research entry, or -1 when no entry/stages exists. */
    int stageCount(String researchKey);
}
