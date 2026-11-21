package dev.ehtid.thaumcraft.port.core;

import java.util.Random;

/**
 * Block-write phase for the Silverwood log skeleton in the archived BETA26 tree.
 * The caller maps legacy log metadata 0/1/2 to registered NeoForge block states.
 * This deliberately does not generate leaves, flowers or register a Feature.
 */
public final class SilverwoodLegacyLogs {
    private SilverwoodLegacyLogs() {}

    @FunctionalInterface
    public interface LogWriter {
        void place(int x, int y, int z, int legacyMeta);
    }

    /**
     * Call only after SilverwoodPlacementPreflight succeeds. Writer must preserve
     * the original replaceable/air/leaves check for every individual write.
     * The supplied Random is the same stream used by the canopy phase.
     */
    public static void place(int x, int y, int z, int height, Random random, LogWriter writer) {
        if (!SilverwoodLegacyFootprint.validHeight(height)
                || random == null || writer == null) {
            throw new IllegalArgumentException("Invalid Silverwood log placement");
        }
        for (int dy = 0; dy < height; dy++) {
            writer.place(x, y + dy, z, 1);
            writer.place(x - 1, y + dy, z, 1);
            writer.place(x + 1, y + dy, z, 1);
            writer.place(x, y + dy, z - 1, 1);
            writer.place(x, y + dy, z + 1, 1);
        }
        writer.place(x, y + height, z, 1);
        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) {
                writer.place(x + dx, y, z + dz, 1);
                if (random.nextInt(3) != 0) writer.place(x + dx, y + 1, z + dz, 1);
            }
        }
        for (int dx : new int[]{-2, 2}) {
            writer.place(x + dx, y, z, 0);
            writer.place(x + dx, y - 1, z, 1);
        }
        for (int dz : new int[]{-2, 2}) {
            writer.place(x, y, z + dz, 2);
            writer.place(x, y - 1, z + dz, 1);
        }
        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) {
                writer.place(x + dx, y + height - 4, z + dz, 1);
                if (random.nextInt(3) == 0) writer.place(x + dx, y + height - 5, z + dz, 1);
            }
        }
        for (int dx : new int[]{-2, 2}) writer.place(x + dx, y + height - 4, z, 0);
        for (int dz : new int[]{-2, 2}) writer.place(x, y + height - 4, z + dz, 2);
    }
}
