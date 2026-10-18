package dev.ehtid.thaumcraft.port.core.golem;

/** Trait names and cancellation pairs from the published BETA26 golem API. */
public enum GolemTrait {
    SMART, DEFT, CLUMSY, FIGHTER, WHEELED, FLYER, CLIMBER, HEAVY, LIGHT,
    FRAGILE, REPAIR, SCOUT, ARMORED, BRUTAL, FIREPROOF, BREAKER, HAULER,
    RANGED, BLASTPROOF;

    public GolemTrait opposite() {
        return switch (this) {
            case DEFT -> CLUMSY;
            case CLUMSY -> DEFT;
            case HEAVY -> LIGHT;
            case LIGHT -> HEAVY;
            case FRAGILE -> ARMORED;
            case ARMORED -> FRAGILE;
            default -> null;
        };
    }
}
