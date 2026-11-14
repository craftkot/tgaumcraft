package dev.ehtid.thaumcraft.port.core.golem;

import java.util.List;

/** Immutable built-in part metadata. Trait order is significant for cancellation. */
public record GolemPart(int id, Kind kind, String key, List<String> research, List<GolemTrait> traits) {
    public enum Kind { HEAD, ARMS, LEGS, ADDON }
    public GolemPart {
        research = List.copyOf(research);
        traits = List.copyOf(traits);
    }
}
