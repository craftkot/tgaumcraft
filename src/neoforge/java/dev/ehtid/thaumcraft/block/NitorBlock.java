package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.blockentity.NitorBlockEntity;
import dev.ehtid.thaumcraft.port.core.LegacyNitorColors;
import dev.ehtid.thaumcraft.registry.NitorBlockEntityRegistration;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.DyeColor;
import org.joml.Vector3f;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * BETA26 BlockNitor's floating, incorporeal, full-light source.
 * Original bounds are 0.33..0.66 on all axes and have NO collision.
 *
 * BETA26 TileNitor does not use the dye/firework RGB for its flame. It reads
 * BlockNitor.getMapColor(...).colorValue and passes that map color to
 * FXDispatcher.drawNitorFlames. Keep the modern particle color tied to the
 * same DyeColor map color used by the registered block.
 */
public final class NitorBlock extends BaseEntityBlock {
    public static final MapCodec<NitorBlock> CODEC = simpleCodec(NitorBlock::new);
    private static final VoxelShape OUTLINE =
            box(5.28, 5.28, 5.28, 10.56, 10.56, 10.56);
    private static final boolean SMOKE_PROBE =
            Boolean.getBoolean("thaumcraft.nitorSmoke");

    private final int flameRgb;
    private final int particleRgb;
    private final DustParticleOptions coloredGlow;
    private boolean smokeColorReported;

    public NitorBlock(BlockBehaviour.Properties props) {
        this(props, LegacyNitorColors.variants().get(0).particleRgb());
    }

    public NitorBlock(BlockBehaviour.Properties props, int flameRgb) {
        super(props);
        this.flameRgb = flameRgb & 0xFFFFFF;
        this.particleRgb = mapColorForDyeRgb(this.flameRgb);
        this.coloredGlow = new DustParticleOptions(new Vector3f(
                ((this.particleRgb >> 16) & 0xFF) / 255.0f,
                ((this.particleRgb >> 8) & 0xFF) / 255.0f,
                (this.particleRgb & 0xFF) / 255.0f), 0.85f);
    }

    /**
     * LegacyNitorColors carries the original dye RGB used to identify each
     * variant. BETA26's BlockNitor.getMapColor returns MapColor.getBlockColor
     * for that dye, so use modern DyeColor.getMapColor().col for the particle.
     */
    private static int mapColorForDyeRgb(int dyeRgb) {
        for (LegacyNitorColors.Variant variant : LegacyNitorColors.variants()) {
            if (variant.particleRgb() == dyeRgb) {
                return DyeColor.valueOf(variant.modernDye().toUpperCase(Locale.ROOT))
                        .getMapColor().col & 0xFFFFFF;
            }
        }
        throw new IllegalArgumentException("Unknown BETA26 Nitor dye RGB: 0x"
                + Integer.toHexString(dyeRgb));
    }

    public int flameRgb() { return flameRgb; }

    public int particleRgb() { return particleRgb; }

    public DustParticleOptions coloredGlow() {
        if (SMOKE_PROBE && !smokeColorReported) {
            smokeColorReported = true;
            System.out.println("TC_SMOKE_NITOR_PARTICLE_COLOR=YES dyeRgb="
                    + flameRgb + " mapColor=" + particleRgb
                    + " particleColor=" + particleRgb);
        }
        return coloredGlow;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NitorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, NitorBlockEntityRegistration.TYPE, NitorBlockEntity::clientTick)
                : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world,
                               BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world,
                                        BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
