package dev.ehtid.thaumcraft.port.core;

import java.util.Random;
import java.util.Objects;

/**
 * Exact BETA26 AspectHelper.getEntityAspects(EntityPlayer) branch.
 *
 * It seeds java.util.Random with the player's legacy name.hashCode(), then
 * makes THREE independent uniform selections from Aspect.aspects.values()
 * in registration order, adding 15 each to a base of 4 Humanus. Duplicate
 * picks ADD their values (not merge/max). The same name therefore always
 * has the same discovered aspects across scans and server sessions.
 */
public final class LegacyPlayerEntityAspectTable {
    private LegacyPlayerEntityAspectTable() {}

    public static AspectListData get(AspectRegistry registry, String playerName) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(playerName, "playerName");
        AspectData[] types = registry.values().toArray(AspectData[]::new);
        AspectListData aspects = new AspectListData().add(registry.MAN, 4);
        Random random = new Random(playerName.hashCode());
        for (int i = 0; i < 3; i++) {
            aspects.add(types[random.nextInt(types.length)], 15);
        }
        return aspects;
    }
}
