package dev.ehtid.thaumcraft.entity;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.FluxRiftRegistration;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
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

    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 100
    )
    public static void advancesBETA26CollapseAndAuraPulses(GameTestHelper helper) {
        if ("read".equals(System.getenv("TC_AURA_COLD_RESTART_PHASE"))) {
            System.out.println("TC_GAMETEST_FLUX_RIFT_COLLAPSE_SKIPPED_PHASE=read");
            helper.succeed();
            return;
        }

        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.get(level);
        aura.putChunk(pos, (short) 100, 10.0F, 2.0F);

        FluxRiftEntity beforeSave = createRift(level, pos, 5);
        beforeSave.setCollapse(true);
        CompoundTag saved = beforeSave.saveWithoutId(new CompoundTag());
        FluxRiftEntity restored = createRift(level, pos, 5);
        restored.load(saved);
        if (!restored.getCollapse() || restored.getMaxSize() != 5 || restored.getRiftSize() != 5) {
            throw new AssertionError("BETA26 collapse state/maxSize did not survive entity save/load");
        }
        if (restored.tickCollapse(findCollapseRandom(true, false))
                || restored.getRiftSize() != 4
                || restored.getMaxSize() != 5
                || aura.getVis(pos) != 11.0F
                || aura.getFlux(pos) != 2.0F) {
            throw new AssertionError("BETA26 collapse Vis pulse/countdown mismatch");
        }
        System.out.println("TC_GAMETEST_FLUX_RIFT_COLLAPSE_VIS=PASS size=4 vis=11.0 flux=2.0 maxSize=5");

        aura.putChunk(pos, (short) 100, 10.0F, 2.0F);
        FluxRiftEntity fluxRift = createRift(level, pos, 5);
        fluxRift.setCollapse(true);
        if (fluxRift.tickCollapse(findCollapseRandom(false, false))
                || fluxRift.getRiftSize() != 4
                || aura.getVis(pos) != 10.0F
                || aura.getFlux(pos) != 3.0F) {
            throw new AssertionError("BETA26 collapse Flux pollution/countdown mismatch");
        }
        System.out.println("TC_GAMETEST_FLUX_RIFT_COLLAPSE_FLUX=PASS size=4 vis=10.0 flux=3.0");

        aura.putChunk(pos, (short) 100, 10.0F, 2.0F);
        FluxRiftEntity explodingRift = createRift(level, pos, 5);
        explodingRift.setCollapse(true);
        ExplosionFixture fixture = findExplosionFixture(explodingRift);
        BlockPos blastBlock = BlockPos.containing(fixture.x(), fixture.y(), fixture.z());
        if (!level.setBlock(blastBlock, Blocks.GLASS.defaultBlockState(), 3)) {
            throw new AssertionError("Unable to place no-smoking explosion fixture block");
        }
        Pig pig = EntityType.PIG.create(level);
        if (pig == null) {
            throw new AssertionError("Unable to create explosion damage target");
        }
        pig.setNoAi(true);
        pig.moveTo(fixture.x(), fixture.y(), fixture.z(), 0.0F, 0.0F);
        if (!level.addFreshEntity(pig)) {
            throw new AssertionError("Unable to add explosion damage target");
        }
        float healthBefore = pig.getHealth();
        boolean exploded = explodingRift.tickCollapse(RandomSource.create(fixture.seed()));
        if (!exploded
                || pig.getHealth() >= healthBefore
                || !level.getBlockState(blastBlock).is(Blocks.GLASS)) {
            throw new AssertionError("BETA26 createExplosion(..., false) must damage entities without block destruction");
        }
        float expectedVis = fixture.addVis() ? 11.0F : 10.0F;
        float expectedFlux = fixture.addVis() ? 2.0F : 3.0F;
        if (aura.getVis(pos) != expectedVis || aura.getFlux(pos) != expectedFlux) {
            throw new AssertionError("BETA26 explosion tick changed the wrong aura channel");
        }
        System.out.println("TC_GAMETEST_FLUX_RIFT_COLLAPSE_EXPLOSION=PASS pig_damaged=YES glass_retained=YES");

        aura.putChunk(pos, (short) 100, 10.0F, 2.0F);
        FluxRiftEntity terminal = createRift(level, pos, 2);
        terminal.setCollapse(true);
        if (terminal.tickCollapse(findCollapseRandom(true, false))
                || terminal.getRiftSize() != 1
                || terminal.getMaxSize() != 2
                || !terminal.isRemoved()) {
            throw new AssertionError("BETA26 collapse must remove a Rift once its decremented size is <= 1");
        }
        System.out.println("TC_GAMETEST_FLUX_RIFT_COLLAPSE_TERMINAL=PASS size=1 removed=YES maxSize=2");

        FluxRiftEntity automatic = createRift(level, pos, 0);
        automatic.tick();
        if (!automatic.getCollapse() || !automatic.isRemoved() || automatic.getMaxSize() != 0) {
            throw new AssertionError("BETA26 Rift with fewer than three geometry points must enter collapse");
        }
        System.out.println("TC_GAMETEST_FLUX_RIFT_COLLAPSE_AUTO_TRIGGER=PASS points=2 removed=YES");
        System.out.println("TC_GAMETEST_FLUX_RIFT_COLLAPSE=PASS vis=1 flux=1 explosion=1 terminal=removed maxSize=preserved auto_trigger=YES rewards=deferred");
        helper.succeed();
    }

    private static FluxRiftEntity createRift(ServerLevel level, BlockPos pos, int size) {
        FluxRiftEntity rift = new FluxRiftEntity(FluxRiftRegistration.get(), level);
        rift.setRiftSeed(1);
        rift.setRiftSize(size);
        rift.moveTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 0.0F, 0.0F);
        return rift;
    }

    private static RandomSource findCollapseRandom(boolean addVis, boolean explode) {
        for (long seed = 0; seed < 100_000L; seed++) {
            RandomSource probe = RandomSource.create(seed);
            if (probe.nextBoolean() == addVis && (probe.nextInt(10) == 0) == explode) {
                return RandomSource.create(seed);
            }
        }
        throw new AssertionError("Unable to find deterministic BETA26 collapse RNG fixture");
    }

    private static ExplosionFixture findExplosionFixture(FluxRiftEntity rift) {
        for (long seed = 0; seed < 100_000L; seed++) {
            RandomSource probe = RandomSource.create(seed);
            boolean addVis = probe.nextBoolean();
            if (probe.nextInt(10) != 0) {
                continue;
            }
            double x = rift.getX() + probe.nextGaussian() * 2.0D;
            double y = rift.getY() + probe.nextGaussian() * 2.0D;
            double z = rift.getZ() + probe.nextGaussian() * 2.0D;
            float power = probe.nextFloat() / 2.0F;
            if (power >= 0.30F
                    && Math.abs(x - rift.getX()) < 0.5D
                    && Math.abs(y - rift.getY()) < 0.5D
                    && Math.abs(z - rift.getZ()) < 0.5D) {
                return new ExplosionFixture(seed, addVis, x, y, z);
            }
        }
        throw new AssertionError("Unable to find deterministic BETA26 explosion fixture");
    }

    private record ExplosionFixture(long seed, boolean addVis, double x, double y, double z) {}
}
