package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;
import java.util.Random;

/**
 * Connects the archived Silverwood canopy and log phases using one RNG stream.
 * Call only after the placement preflight; callbacks are responsible for actual
 * block-state mapping and per-write replaceability. No NeoForge registration.
 */
public final class SilverwoodLegacyTree {
    private SilverwoodLegacyTree() {}

    public static void place(int x, int y, int z, int height, Random random,
                             SilverwoodLegacyCanopy.LeafReplaceability leaves,
                             SilverwoodLegacyCanopy.LeafWriter leafWriter,
                             SilverwoodLegacyLogs.LogWriter logWriter) {
        if (!SilverwoodLegacyFootprint.validHeight(height)) {
            throw new IllegalArgumentException("Invalid Silverwood height");
        }
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(leaves, "leaves");
        Objects.requireNonNull(leafWriter, "leafWriter");
        Objects.requireNonNull(logWriter, "logWriter");
        SilverwoodLegacyTreePhases.place(x, y, z, height, random,
                (px, py, pz, h, rng) -> SilverwoodLegacyCanopy.place(
                        px, py, pz, h, rng, leaves, leafWriter),
                logWriter);
    }
}
