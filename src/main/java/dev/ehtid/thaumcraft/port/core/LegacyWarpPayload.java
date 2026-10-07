package dev.ehtid.thaumcraft.port.core;

import java.util.List;

/** Loader-neutral mirror of BETA26 PlayerWarp NBT: int[] "warp" plus int "counter". */
public record LegacyWarpPayload(List<Integer> warp, int counter) {
    public LegacyWarpPayload {
        warp = warp == null ? List.of() : List.copyOf(warp);
    }
}
