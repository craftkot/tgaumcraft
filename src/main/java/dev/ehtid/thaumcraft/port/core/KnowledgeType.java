package dev.ehtid.thaumcraft.port.core;

/** Values extracted from IPlayerKnowledge.EnumKnowledgeType in BETA26. */
public enum KnowledgeType {
    THEORY(32, "T"),
    OBSERVATION(16, "O");

    private final int progression;
    private final String abbreviation;

    KnowledgeType(int progression, String abbreviation) {
        this.progression = progression;
        this.abbreviation = abbreviation;
    }

    public int progression() {
        return progression;
    }

    public String abbreviation() {
        return abbreviation;
    }
}
