package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.AspectData;
import dev.ehtid.thaumcraft.port.core.AspectListData;
import dev.ehtid.thaumcraft.port.core.LegacyCrucibleSurface;
import dev.ehtid.thaumcraft.port.core.visual.LegacyCrucibleBubbleRules;
import dev.ehtid.thaumcraft.registry.CrucibleBubbleParticleRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-only visual half of BETA26 TileCrucible.drawEffects.
 * Kept outside CrucibleBlockEntity so the alchemy server/NBT lane remains
 * untouched; CrucibleBlock installs this ticker only on the client.
 */
public final class CrucibleBubbleClientEffects {
    private static final boolean SMOKE_PROBE = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    private static final AtomicInteger SMOKE_SPAWNED = new AtomicInteger();

    private CrucibleBubbleClientEffects() {}

    public static void tick(Level level, BlockPos pos, CrucibleBlockEntity tile) {
        if (!level.isClientSide || tile.waterMb() <= 0) return;

        AspectListData aspects = tile.getAspects();
        RandomSource random = level.getRandom();
        if (!LegacyCrucibleBubbleRules.shouldSpawn(random.nextInt(6), aspects.size())) return;

        AspectData[] values = aspects.getAspects();
        if (values.length == 0) return;
        AspectData aspect = values[random.nextInt(values.length)];
        int rgb = aspect.color();

        double x = pos.getX() + (5 + random.nextInt(22)) / 32.0D + 0.015625D;
        double z = pos.getZ() + (5 + random.nextInt(22)) / 32.0D + 0.015625D;
        double y = pos.getY() + 0.05D
                + LegacyCrucibleSurface.height(tile.waterMb(), aspects.visSize());

        // The original FXGeneric particle receives the chosen aspect's RGB.
        // The provider consumes these three doubles as color and resets motion.
        level.addParticle(CrucibleBubbleParticleRegistration.TYPE, x, y, z,
                ((rgb >>> 16) & 0xFF) / 255.0D,
                ((rgb >>> 8) & 0xFF) / 255.0D,
                (rgb & 0xFF) / 255.0D);

        if (SMOKE_PROBE) {
            int id = SMOKE_SPAWNED.incrementAndGet();
            if (id <= 6) {
                System.out.println("TC_SMOKE_CRUCIBLE_BUBBLE_SPAWN=YES id=" + id
                        + " aspect=" + aspect.tag() + " pos=" + x + "," + y + "," + z);
            }
        }
    }
}
