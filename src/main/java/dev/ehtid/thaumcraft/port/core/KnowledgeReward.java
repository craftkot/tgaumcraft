package dev.ehtid.thaumcraft.port.core;

/** TC6 research reward_knowledge entry. amount is expressed in whole knowledge levels in JSON. */
public record KnowledgeReward(KnowledgeType type, String categoryKey, int amount) {
    public KnowledgeReward {
        if (type == null) throw new IllegalArgumentException("type is required");
    }

    public int rawAmount() {
        return type.progression() * amount;
    }
}
