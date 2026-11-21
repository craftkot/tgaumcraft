package dev.ehtid.thaumcraft.entity;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.FluxInfectionEffectRegistration;
import dev.ehtid.thaumcraft.registry.FluxRiftRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCures;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native server acceptance for BETA26 Flux Rift event 2 and Flux Phage spread. */
@GameTestHolder(ThaumcraftPort.MOD_ID)
public final class FluxRiftInfectionGameTests {
    private FluxRiftInfectionGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 100
    )
    public static void appliesBETA26InfectiousVisExhaustRiftEvent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        Pig inside = spawnPig(level,
                origin.getX() + 0.5D, origin.getY() + 15.5D, origin.getZ() + 0.5D);
        Pig outside = spawnPig(level,
                origin.getX() + 0.5D, origin.getY() + 18.5D, origin.getZ() + 0.5D);

        FluxRiftEntity rift = createRift(level, origin);
        rift.setRiftStability(-10.0F);
        int selected = rift.executeWeightedEvent(0.61D);
        var infectious = FluxInfectionEffectRegistration.infectious();
        MobEffectInstance applied = inside.getEffect(infectious);
        if (selected != 2
                || applied == null
                || applied.getDuration() != 3000
                || applied.getAmplifier() != 2
                || !applied.getCures().isEmpty()
                || outside.hasEffect(infectious)
                || Math.abs(rift.getRiftStability()) > 0.001F) {
            throw new AssertionError("BETA26 Rift event 2 must apply 3000t amplifier-2 Flux Phage in the Rift AABB+16 and pay +10 stability");
        }
        inside.removeEffectsCuredBy(EffectCures.MILK);
        MobEffectInstance afterMilk = inside.getEffect(infectious);
        if (afterMilk == null || afterMilk.getDuration() != 3000) {
            throw new AssertionError("BETA26 initial Rift event 2 infection must have no curative items");
        }
        System.out.println("TC_GAMETEST_FLUX_RIFT_EVENT_2=PASS aabb_inflate=16 duration=3000 amplifier=2 stability_cost=10 initial_cures=0 milk_retained=YES");

        inside.discard();
        outside.discard();
        FluxRiftEntity noTargets = createRift(level, origin.above(80));
        noTargets.setRiftStability(-10.0F);
        if (noTargets.executeWeightedEvent(0.61D) != 2
                || Math.abs(noTargets.getRiftStability() + 10.0F) > 0.001F) {
            throw new AssertionError("BETA26 event 2 must not pay its stability cost when no living target exists");
        }
        System.out.println("TC_GAMETEST_FLUX_RIFT_EVENT_2_EMPTY=PASS stability_cost=0");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 100
    )
    public static void spreadsBETA26FluxPhageAndConvertsToFluxFlu(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));

        Pig source = spawnPig(level,
                origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D);
        Pig firstHop = spawnPig(level,
                origin.getX() + 0.5D, origin.getY() + 3.5D, origin.getZ() + 0.5D);
        Pig secondHop = spawnPig(level,
                origin.getX() + 0.5D, origin.getY() + 6.5D, origin.getZ() + 0.5D);
        Pig thirdHop = spawnPig(level,
                origin.getX() + 0.5D, origin.getY() + 9.5D, origin.getZ() + 0.5D);
        Pig alreadyInfected = spawnPig(level,
                origin.getX() + 0.5D, origin.getY() + 1.5D, origin.getZ() + 0.5D);
        Pig diagonalAabbTarget = spawnPig(level,
                origin.getX() + 3.5D, origin.getY() + 3.5D, origin.getZ() + 3.5D);

        var infectious = FluxInfectionEffectRegistration.infectious();
        var visExhaust = FluxInfectionEffectRegistration.visExhaust();
        source.addEffect(new MobEffectInstance(infectious, 3000, 2));
        alreadyInfected.addEffect(new MobEffectInstance(infectious, 123, 0));
        InfectiousVisExhaustMobEffect effect =
                (InfectiousVisExhaustMobEffect) infectious.value();

        if (!effect.shouldApplyEffectTickThisTick(40, 2)
                || effect.shouldApplyEffectTickThisTick(39, 2)
                || !effect.shouldApplyEffectTickThisTick(80, 0)
                || effect.getColor() != InfectiousVisExhaustMobEffect.BETA26_COLOR
                || visExhaust.value().getColor() != VisExhaustMobEffect.BETA26_COLOR
                || visExhaust.value().shouldApplyEffectTickThisTick(40, 0)) {
            throw new AssertionError("BETA26 Flux Phage/Flux Flu color or 40-tick cadence mismatch");
        }

        effect.applyEffectTick(source, 2);
        MobEffectInstance first = firstHop.getEffect(infectious);
        MobEffectInstance diagonal = diagonalAabbTarget.getEffect(infectious);
        MobEffectInstance secondBefore = secondHop.getEffect(infectious);
        MobEffectInstance thirdBefore = thirdHop.getEffect(infectious);
        MobEffectInstance existing = alreadyInfected.getEffect(infectious);
        if (first == null || first.getDuration() != 6000 || first.getAmplifier() != 1
                || diagonal == null || diagonal.getAmplifier() != 1
                || secondBefore != null || thirdBefore != null
                || existing == null || existing.getDuration() != 123 || existing.getAmplifier() != 0) {
            throw new AssertionError("BETA26 first Flux Phage hop must use AABB+4, decrement amplifier, and skip existing infections");
        }

        effect.applyEffectTick(firstHop, 1);
        MobEffectInstance second = secondHop.getEffect(infectious);
        if (second == null || second.getDuration() != 6000 || second.getAmplifier() != 0
                || thirdHop.hasEffect(infectious)) {
            throw new AssertionError("BETA26 second Flux Phage hop must decrement amplifier 1 to 0");
        }

        effect.applyEffectTick(secondHop, 0);
        MobEffectInstance finalExhaust = thirdHop.getEffect(visExhaust);
        if (finalExhaust == null || finalExhaust.getDuration() != 6000
                || finalExhaust.getAmplifier() != 0
                || thirdHop.hasEffect(infectious)) {
            throw new AssertionError("BETA26 amplifier-zero Flux Phage must convert to 6000t Flux Flu");
        }
        if (!first.getCures().contains(EffectCures.MILK)) {
            throw new AssertionError("BETA26 propagated Flux Phage must retain the default milk cure");
        }
        firstHop.removeEffectsCuredBy(EffectCures.MILK);
        if (firstHop.hasEffect(infectious)) {
            throw new AssertionError("BETA26 propagated Flux Phage must be removable by milk");
        }
        System.out.println("TC_GAMETEST_FLUX_PHAGE_SPREAD=PASS interval=40 aabb_inflate=4 hops=2,1,0 final=flux_flu duration=6000 existing=unchanged");
        System.out.println("TC_GAMETEST_FLUX_PHAGE_PROPAGATED_CURE=PASS milk_removes=YES initial_event_only_uncurable=YES");
        helper.succeed();
    }

    private static FluxRiftEntity createRift(ServerLevel level, BlockPos pos) {
        FluxRiftEntity rift = new FluxRiftEntity(FluxRiftRegistration.get(), level);
        rift.setRiftSeed(1);
        rift.setRiftSize(5);
        rift.moveTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 0.0F, 0.0F);
        return rift;
    }

    private static Pig spawnPig(ServerLevel level, double x, double y, double z) {
        Pig pig = EntityType.PIG.create(level);
        if (pig == null) {
            throw new AssertionError("Unable to create Flux Phage test pig");
        }
        pig.setNoAi(true);
        pig.moveTo(x, y, z, 0.0F, 0.0F);
        if (!level.addFreshEntity(pig)) {
            throw new AssertionError("Unable to add Flux Phage test pig");
        }
        return pig;
    }
}
