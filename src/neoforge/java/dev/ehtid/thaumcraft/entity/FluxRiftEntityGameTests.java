package dev.ehtid.thaumcraft.entity;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.FluxRiftRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Native server acceptance for the BETA26 Flux Rift segment block ray trace.
 */
@GameTestHolder(ThaumcraftPort.MOD_ID)
public final class FluxRiftEntityGameTests {
    private FluxRiftEntityGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 100
    )
    public static void destroysOnlyCollidableBreakableBlocksAlongSegment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(8, 2, 8));

        FluxRiftEntity rift = new FluxRiftEntity(FluxRiftRegistration.get(), level);
        rift.setRiftSeed(1);
        rift.setRiftSize(5);
        rift.moveTo(target.getX(), target.getY() + 0.5D, target.getZ() + 0.5D, 0.0F, 0.0F);

        var points = rift.getGeometry();
        if (points.size() <= 3 || points.get(2).x() >= 0.0D || points.get(3).x() <= 0.0D) {
            throw new AssertionError("Seed 1 size 5 must make segment 2 cross the target block's west face");
        }

        if (!level.setBlock(target, Blocks.STONE.defaultBlockState(), 3)) {
            throw new AssertionError("Unable to place breakable Flux Rift ray target");
        }
        rift.applySegmentEffectsAt(2);
        if (!level.getBlockState(target).isAir()) {
            throw new AssertionError("BETA26 Flux Rift must remove a collidable block with non-negative hardness");
        }
        System.out.println("TC_GAMETEST_FLUX_RIFT_BLOCK_RAYTRACE_BREAKABLE=PASS stone=REMOVED");

        if (!level.setBlock(target, Blocks.BEDROCK.defaultBlockState(), 3)) {
            throw new AssertionError("Unable to place unbreakable Flux Rift ray target");
        }
        rift.applySegmentEffectsAt(2);
        if (!level.getBlockState(target).is(Blocks.BEDROCK)) {
            throw new AssertionError("BETA26 Flux Rift must retain blocks with negative hardness");
        }
        System.out.println("TC_GAMETEST_FLUX_RIFT_BLOCK_RAYTRACE_HARDNESS=PASS bedrock=RETAINED");

        if (!level.setBlock(target, Blocks.WATER.defaultBlockState(), 3)) {
            throw new AssertionError("Unable to place liquid Flux Rift ray target");
        }
        rift.applySegmentEffectsAt(2);
        if (!level.getBlockState(target).is(Blocks.WATER)) {
            throw new AssertionError("BETA26 rayTraceBlocks(..., false) must ignore fluids");
        }
        System.out.println("TC_GAMETEST_FLUX_RIFT_BLOCK_RAYTRACE_FLUID=PASS water=IGNORED");
        System.out.println("TC_GAMETEST_FLUX_RIFT_BLOCK_RAYTRACE=PASS breakable=REMOVED unbreakable=RETAINED fluid=IGNORED");

        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
    }
}
