package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.registry.ThaumcraftParticles;
import dev.ehtid.thaumcraft.scanning.ThaumometerTargeting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Exact ItemThaumometer.drawFX spawn count, targeting priority, colors and anchor positions. */
public final class ThaumometerRuneFx {
    private ThaumometerRuneFx() {}

    public static void draw(Level level, Player player) {
        if (!(level instanceof ClientLevel client)) return;
        RandomSource random = client.random;

        Entity entity = ThaumometerTargeting.findPointedEntity(client, player);
        if (entity != null) {
            for (int i = 0; i < 10; i++) {
                spawn(client,
                        entity.getX(),
                        entity.getY() + entity.getEyeHeight() / 2.0F + 0.5D,
                        entity.getZ(),
                        random);
            }
            return;
        }

        BlockHitResult hit = ThaumometerTargeting.rayTraceBlock(client, player);
        if (hit.getType() == HitResult.Type.MISS) return;
        BlockPos pos = hit.getBlockPos();
        for (int i = 0; i < 10; i++) {
            spawn(client, pos.getX() + 0.5D, pos.getY() + 0.75D, pos.getZ() + 0.5D, random);
        }
    }

    private static void spawn(ClientLevel level, double x, double y, double z, RandomSource random) {
        double red = 0.3D + random.nextFloat() * 0.7D;
        double blue = 0.3D + random.nextFloat() * 0.7D;
        level.addParticle(ThaumcraftParticles.SCAN_RUNE.get(), x, y, z, red, 0.0D, blue);
    }
}
