package dev.ehtid.thaumcraft.port.core.golem;

import java.util.List;
import static dev.ehtid.thaumcraft.port.core.golem.GolemTrait.*;
import static dev.ehtid.thaumcraft.port.core.golem.GolemPart.Kind.*;

/** All default BETA26 parts, in their original registration order. */
public final class GolemCatalog {
    private GolemCatalog() {}
    public static final List<GolemPart> HEADS = List.of(
        part(0, HEAD, "BASIC", List.of("MINDCLOCKWORK")),
        part(1, HEAD, "SMART", List.of("MINDBIOTHAUMIC"), SMART, FRAGILE),
        part(2, HEAD, "SMART_ARMORED", List.of("MINDBIOTHAUMIC", "GOLEMCOMBATADV"), SMART),
        part(3, HEAD, "SCOUT", List.of("GOLEMVISION"), SCOUT, FRAGILE),
        part(4, HEAD, "SMART_SCOUT", List.of("GOLEMVISION", "MINDBIOTHAUMIC"), SCOUT, SMART, FRAGILE));
    public static final List<GolemPart> ARMS = List.of(
        part(0, GolemPart.Kind.ARMS, "BASIC", List.of("MINDCLOCKWORK")),
        part(1, GolemPart.Kind.ARMS, "FINE", List.of("MATSTUDBRASS"), DEFT, FRAGILE),
        part(2, GolemPart.Kind.ARMS, "CLAWS", List.of("GOLEMCOMBATADV"), FIGHTER, CLUMSY, BRUTAL),
        part(3, GolemPart.Kind.ARMS, "BREAKERS", List.of("GOLEMBREAKER"), BREAKER, CLUMSY, BRUTAL),
        part(4, GolemPart.Kind.ARMS, "DARTS", List.of("GOLEMCOMBATADV"), FIGHTER, CLUMSY, RANGED, FRAGILE));
    public static final List<GolemPart> LEGS = List.of(
        part(0, GolemPart.Kind.LEGS, "WALKER", List.of("MINDCLOCKWORK")),
        part(1, GolemPart.Kind.LEGS, "ROLLER", List.of("MINDCLOCKWORK"), WHEELED),
        part(2, GolemPart.Kind.LEGS, "CLIMBER", List.of("GOLEMCLIMBER"), CLIMBER),
        part(3, GolemPart.Kind.LEGS, "FLYER", List.of("GOLEMFLYER"), FLYER, FRAGILE));
    public static final List<GolemPart> ADDONS = List.of(
        part(0, ADDON, "NONE", List.of("MINDCLOCKWORK")),
        part(1, ADDON, "ARMORED", List.of("GOLEMCOMBATADV"), ARMORED, HEAVY),
        part(2, ADDON, "FIGHTER", List.of("SEALGUARD"), FIGHTER),
        part(3, ADDON, "HAULER", List.of("MINDCLOCKWORK"), HAULER));

    private static GolemPart part(int id, GolemPart.Kind kind, String key, List<String> research, GolemTrait... traits) {
        return new GolemPart(id, kind, key, research, List.of(traits));
    }
    public static GolemPart head(int id) { return get(HEADS, id); }
    public static GolemPart arms(int id) { return get(ARMS, id); }
    public static GolemPart legs(int id) { return get(LEGS, id); }
    public static GolemPart addon(int id) { return get(ADDONS, id); }
    private static GolemPart get(List<GolemPart> parts, int id) {
        if (id < 0 || id >= parts.size()) throw new IllegalArgumentException("Unknown BETA26 part ID: " + id);
        return parts.get(id);
    }
}
