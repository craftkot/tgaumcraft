package dev.ehtid.thaumcraft.infusion.test;

import dev.ehtid.thaumcraft.block.EssentiaMirrorBlock;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.blockentity.EssentiaMirrorBlockEntity;
import dev.ehtid.thaumcraft.crafting.InfusionJarSourceBridge;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Physical source identity across dimensions and changes to a cached directional scan. */
@GameTestHolder("thaumcraft_infusion_mirror")
@PrefixGameTestTemplate(false)
public final class InfusionMirrorIsolationGameTests {
    private static final String TEMPLATE = "infusion_mirror_blank";

    @GameTest(template=TEMPLATE, batch="infusion_mirror_isolation", timeoutTicks=100)
    public static void netherPartnerDebitsItsJarWithoutTouchingOverworldCoordinateDecoy(GameTestHelper h) {
        ServerLevel overworld = h.getLevel();
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        h.assertTrue(overworld.dimension().equals(Level.OVERWORLD) && nether != null,
                "Fixture has separate real Overworld and Nether server levels");
        BlockPos origin = h.absolutePos(new BlockPos(12, 8, 12));
        BlockPos remote = new BlockPos(384_008, 96, 384_008);
        BlockPos sourcePos = remote.above(2);
        // Fixture setup alone may load these two distant chunks; the production
        // bridge still uses hasChunkAt and never obtains missing chunks itself.
        nether.getChunkAt(remote);
        overworld.getChunkAt(remote);
        List<SavedBlock> saved = new ArrayList<>();
        try {
            var local = mirror(overworld, origin.east(), Direction.UP, saved);
            var partner = mirror(nether, remote, Direction.UP, saved);
            var source = jar(h, nether, sourcePos, 3, saved);
            var decoy = jar(h, overworld, sourcePos, 9, saved);
            h.assertTrue(local.linkTo(nether.dimension().location().toString(), remote)
                            && local.hasValidLink() && partner.hasValidLink(),
                    "Actual reciprocal link includes the remote dimension identity");
            var bridge = new InfusionJarSourceBridge();
            h.assertTrue(bridge.drainOne(overworld, origin, "aer", 8, 5)
                            && bridge.drainOne(overworld, origin, "aer", 8, 5),
                    "Both initial and cached scans debit the real Nether source");
            h.assertTrue(source.snapshot().amount() == 1 && decoy.snapshot().amount() == 9,
                    "Identical coordinates in another dimension cannot supply or lose essence");
            h.assertTrue(local.getUpdateTag(overworld.registryAccess()).getInt("instability") == 2
                            && partner.getUpdateTag(nether.registryAccess()).getInt("instability") == 0,
                    "Cross-dimension wireless debit charges only the calling endpoint");
        } finally {
            restore(saved);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch="infusion_mirror_isolation", timeoutTicks=40)
    public static void rotatingPartnerReplacesItsCachedDirectionalSources(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(12, 8, 12));
        BlockPos remote = origin.south(24);
        List<SavedBlock> saved = new ArrayList<>();
        try {
            var local = mirror(level, origin.east(), Direction.UP, saved);
            var partner = mirror(level, remote, Direction.UP, saved);
            var above = jar(h, level, remote.above(2), 3, saved);
            var below = jar(h, level, remote.below(2), 5, saved);
            h.assertTrue(local.linkTo(level.dimension().location().toString(), remote),
                    "Actual reciprocal pair is ready before caching a source");
            var bridge = new InfusionJarSourceBridge();
            h.assertTrue(bridge.drainOne(level, origin, "aer", 8, 5)
                            && above.snapshot().amount() == 2 && below.snapshot().amount() == 5,
                    "UP partner initially caches and debits only its upper source");
            level.setBlockAndUpdate(remote, partner.getBlockState().setValue(EssentiaMirrorBlock.FACING, Direction.DOWN));
            h.assertTrue(level.getBlockEntity(remote) == partner && local.hasValidLink(),
                    "Facing change retains the live partner and its already populated source cache");
            h.assertTrue(bridge.drainOne(level, origin, "aer", 8, 5)
                            && above.snapshot().amount() == 2 && below.snapshot().amount() == 4,
                    "Same bridge switches to the new face without debiting its old cached source");
        } finally {
            restore(saved);
        }
        h.succeed();
    }

    private record SavedBlock(ServerLevel level, BlockPos pos, BlockState state, CompoundTag entity) {}

    private static void remember(ServerLevel level, BlockPos pos, List<SavedBlock> saved) {
        var entity = level.getBlockEntity(pos);
        saved.add(new SavedBlock(level, pos.immutable(), level.getBlockState(pos),
                entity == null ? null : entity.saveWithFullMetadata(level.registryAccess())));
    }

    private static EssentiaMirrorBlockEntity mirror(ServerLevel level, BlockPos pos,
                                                     Direction facing, List<SavedBlock> saved) {
        remember(level, pos, saved);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, ThaumcraftBlocks.MIRROR_ESSENTIA.get().defaultBlockState()
                .setValue(EssentiaMirrorBlock.FACING, facing));
        return (EssentiaMirrorBlockEntity) level.getBlockEntity(pos);
    }

    private static EssentiaJarBlockEntity jar(GameTestHelper h, ServerLevel level, BlockPos pos,
                                               int amount, List<SavedBlock> saved) {
        remember(level, pos, saved);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, ThaumcraftBlocks.JAR_NORMAL.get().defaultBlockState());
        var jar = (EssentiaJarBlockEntity) level.getBlockEntity(pos);
        h.assertTrue(jar.addFromTransport("aer", amount, Direction.UP) == amount,
                "Fixture fills the actual jar with its exact initial amount");
        return jar;
    }

    private static void restore(List<SavedBlock> saved) {
        for (int i = saved.size() - 1; i >= 0; i--) {
            var block = saved.get(i);
            block.level().setBlockAndUpdate(block.pos(), block.state());
        }
        // Restore inventory/link NBT after all replacement callbacks have run.
        for (var block : saved) {
            var entity = block.level().getBlockEntity(block.pos());
            if (block.entity() != null && entity != null) {
                entity.loadWithComponents(block.entity(), block.level().registryAccess());
                entity.setChanged();
            }
        }
    }
}
