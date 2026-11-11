package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.block.NitorBlock;
import dev.ehtid.thaumcraft.registry.NitorBlockEntityRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BETA26 TileNitor cadence. The original tile updates only on the client:
 * drawNitorFlames once per tick and drawNitorCore when count++ % 10 == 0.
 *
 * Dust/END_ROD remain temporary vanilla visual substitutes for the original
 * FXGeneric atlas frames 264..271 and 457; this class restores timing only.
 */
public final class NitorBlockEntity extends BlockEntity {
    private static final boolean SMOKE_PROBE =
            Boolean.getBoolean("thaumcraft.nitorSmoke");

    private int tickCount;
    private int smokeFlames;
    private int smokeCores;
    private boolean smokeReported;

    public NitorBlockEntity(BlockPos pos, BlockState state) {
        super(NitorBlockEntityRegistration.TYPE, pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                   NitorBlockEntity blockEntity) {
        if (!level.isClientSide || !(state.getBlock() instanceof NitorBlock nitor)) return;

        RandomSource random = level.getRandom();
        double x = pos.getX() + 0.5D + random.nextGaussian() * 0.025D;
        double y = pos.getY() + 0.45D + random.nextGaussian() * 0.025D;
        double z = pos.getZ() + 0.5D + random.nextGaussian() * 0.025D;

        // BETA26 TileNitor.update -> drawNitorFlames: Gaussian X/Z drift,
        // a 0..0.06 upward velocity, and one effect every client tick.
        level.addParticle(nitor.coloredGlow(), x, y, z,
                random.nextGaussian() * 0.0025D,
                random.nextFloat() * 0.06D,
                random.nextGaussian() * 0.0025D);
        blockEntity.smokeFlames++;

        int tick = blockEntity.tickCount++;
        // Original TileNitor starts with count=0, so the first tick also emits
        // the core; subsequent cores are exactly ten client ticks apart.
        if (tick % 10 == 0) {
            level.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + 0.5D, pos.getY() + 0.49D, pos.getZ() + 0.5D,
                    0.0D, 0.0D, 0.0D);
            blockEntity.smokeCores++;
        }

        if (SMOKE_PROBE && !blockEntity.smokeReported && blockEntity.tickCount >= 20) {
            blockEntity.smokeReported = true;
            System.out.println("TC_SMOKE_NITOR_CLIENT_TICK=YES ticks="
                    + blockEntity.tickCount + " flames=" + blockEntity.smokeFlames
                    + " cores=" + blockEntity.smokeCores);
        }
    }
}
