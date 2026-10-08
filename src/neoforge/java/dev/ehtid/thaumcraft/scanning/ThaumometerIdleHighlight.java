package dev.ehtid.thaumcraft.scanning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

/**
 * BETA26 ItemThaumometer idle highlight path.
 *
 * Target cadence, entity/block priority, wild 16-block ray, AABB face sampling,
 * spawn count and RGB ranges are bytecode-exact. The legacy FXGeneric animated
 * sparkle sprite is temporarily projected with Minecraft's dust sprite; no scan
 * or knowledge semantics depend on that rendering substitution.
 */
public final class ThaumometerIdleHighlight {
    private ThaumometerIdleHighlight() {}

    public static void tick(Level level, Player player) {
        Entity entity = ThaumometerTargeting.findHighlightEntity(level, player);
        if (entity != null && ThaumometerScanDispatcher.isStillScannable(player, entity))
            scanHighlight(level, entity.getBoundingBox());

        // BETA26 does this wild block ray even when an entity was also highlighted.
        BlockHitResult hit = ThaumometerTargeting.rayTraceBlockWild(level, player);
        if (hit.getType() == HitResult.Type.MISS) return;
        BlockPos pos = hit.getBlockPos();
        if (!ThaumometerScanDispatcher.isStillScannable(player, pos)) return;

        var shape = level.getBlockState(pos).getCollisionShape(level, pos);
        if (shape.isEmpty()) return;
        scanHighlight(level, shape.bounds().move(pos));
    }

    static void scanHighlight(Level level, AABB box) {
        RandomSource random = level.random;
        int passes = Mth.ceil(((box.getXsize() + box.getYsize() + box.getZsize()) / 3.0D) * 2.0D);
        double centerX = (box.minX + box.maxX) / 2.0D;
        double centerY = (box.minY + box.maxY) / 2.0D;
        double centerZ = (box.minZ + box.maxZ) / 2.0D;

        for (Direction face : Direction.values()) {
            double baseX = 0.5D + face.getStepX() * 0.51D;
            double baseY = 0.5D + face.getStepY() * 0.51D;
            double baseZ = 0.5D + face.getStepZ() * 0.51D;

            for (int i = 0; i < passes * 2; i++) {
                double ox = Mth.clamp(baseX + random.nextGaussian() * box.getXsize(),
                        box.minX - centerX, box.maxX - centerX);
                double oy = Mth.clamp(baseY + random.nextGaussian() * box.getYsize(),
                        box.minY - centerY, box.maxY - centerY);
                double oz = Mth.clamp(baseZ + random.nextGaussian() * box.getZsize(),
                        box.minZ - centerZ, box.maxZ - centerZ);

                float red = (16 + random.nextInt(17)) / 255.0F;
                float green = (132 + random.nextInt(34)) / 255.0F;
                float blue = (223 + random.nextInt(17)) / 255.0F;
                float scale = 0.4F + (float) random.nextGaussian() * 0.1F;

                level.addParticle(
                        new DustParticleOptions(new Vector3f(red, green, blue), scale),
                        centerX + ox, centerY + oy, centerZ + oz,
                        0.0D, 0.0D, 0.0D);
            }
        }
    }
}
