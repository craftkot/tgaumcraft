package dev.ehtid.thaumcraft.gametest;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.block.CrystalClusterBlock;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real server-world coverage for the six BETA26 crystal mesh support flags. */
@GameTestHolder(ThaumcraftPort.MOD_ID)
public final class CrystalMeshStateGameTests {
    private CrystalMeshStateGameTests() {}

    @SuppressWarnings("deprecation")
    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 40
    )
    public static void supportFacePropertiesTrackPlacementAndNeighbours(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        CrystalClusterBlock crystal = ThaumcraftBlocks.CRYSTAL_AER.get();

        for (Direction direction : Direction.values()) {
            level.setBlock(pos.relative(direction), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, crystal.defaultBlockState(), 3);
        assertFace(level, pos, Direction.DOWN, true, "placement");
        for (Direction direction : new Direction[]{
                Direction.UP, Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
            assertFace(level, pos, direction, false, "placement");
        }

        for (Direction direction : new Direction[]{
                Direction.UP, Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
            level.setBlock(pos.relative(direction), Blocks.STONE.defaultBlockState(), 3);
            assertFace(level, pos, direction, true, "support-added");
        }

        for (Direction direction : Direction.values()) {
            level.setBlock(pos.relative(direction), Blocks.AIR.defaultBlockState(), 3);
            assertFace(level, pos, direction, false, "support-removed");
        }

        System.out.println("TC_GAMETEST_CRYSTAL_MESH_FACE_STATE=PASS faces=6 placement=1 add=5 remove=6");
        helper.succeed();
    }

    private static void assertFace(ServerLevel level, BlockPos pos, Direction direction,
                                   boolean expected, String stage) {
        BlockState state = level.getBlockState(pos);
        BooleanProperty property = switch (direction) {
            case UP -> CrystalClusterBlock.UP;
            case DOWN -> CrystalClusterBlock.DOWN;
            case EAST -> CrystalClusterBlock.EAST;
            case WEST -> CrystalClusterBlock.WEST;
            case NORTH -> CrystalClusterBlock.NORTH;
            case SOUTH -> CrystalClusterBlock.SOUTH;
        };
        if (!state.hasProperty(property)) {
            throw new AssertionError("Crystal lost support property " + direction + " at " + stage);
        }
        boolean actual = state.getValue(property);
        if (actual != expected) {
            throw new AssertionError("Crystal support " + direction + " at " + stage
                    + ": expected " + expected + ", got " + actual + ", state=" + state);
        }
    }
}
