package dev.ehtid.thaumcraft.infusion.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.infusion.InfusionHazardContent;
import dev.ehtid.thaumcraft.infusion.InfusionInstabilityEffects;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionInstability;
import dev.ehtid.thaumcraft.port.core.WarpType;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.List;
import java.util.UUID;

/** Real blocks, item entities, saved aura/player state and ticking potion damage. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class InfusionInstabilityGameTests {
    private InfusionInstabilityGameTests() {}
    private static final LegacyInfusionInstability.RandomSource LOW = new LegacyInfusionInstability.RandomSource() {
        public int nextInt(int bound) { return 0; }
        public float nextFloat() { return 0; }
        public boolean nextBoolean() { return true; }
    };
    private static final LegacyInfusionInstability.RandomSource EXHAUST = new LegacyInfusionInstability.RandomSource() {
        public int nextInt(int bound) { return 0; }
        public float nextFloat() { return 0.5F; }
        public boolean nextBoolean() { return false; }
    };
    private static BlockPos matrix(GameTestHelper h) { return h.absolutePos(new BlockPos(12, 8, 12)); }
    private static BlockPos pedestal(GameTestHelper h) {
        BlockPos pos = matrix(h).offset(3, -2, 0);
        h.getLevel().setBlockAndUpdate(pos, ThaumcraftBlocks.PEDESTAL_ARCANE.get().defaultBlockState());
        ((PedestalBlockEntity) h.getLevel().getBlockEntity(pos)).putFromInfusion(new ItemStack(Items.DIAMOND));
        return pos;
    }

    @GameTest(template="infusion_paid_blank",batch="infusion_hazards",timeoutTicks=60)
    public static void ejectCreatesOnePhysicalItemAndCannotReplay(GameTestHelper h) {
        BlockPos pos = pedestal(h);
        var first = InfusionInstabilityEffects.perform(h.getLevel(), matrix(h),
                LegacyInfusionInstability.Event.EJECT_ITEM, List.of(pos), LOW);
        h.assertTrue(!((PedestalBlockEntity) h.getLevel().getBlockEntity(pos)).isOccupied(), "Actual slot cleared");
        h.assertTrue(first.droppedItems() == 1, "Exactly one physical item removed");
        var second = InfusionInstabilityEffects.perform(h.getLevel(), matrix(h),
                LegacyInfusionInstability.Event.EJECT_ITEM, List.of(pos), LOW);
        h.assertTrue(second.droppedItems() == 0, "Repeated hazard cannot reconstruct a snapshot");
        int diamonds = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))
                .stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(diamonds == 1, "Exactly one real diamond entity, with no duplication");
        h.succeed();
    }

    @GameTest(template="infusion_paid_blank",batch="infusion_hazards",timeoutTicks=60)
    public static void destroyModeDeletesItemAndPollutesExistingAura(GameTestHelper h) {
        BlockPos pos = pedestal(h);
        var aura = ThaumcraftAuraSavedData.get(h.getLevel());
        aura.putChunk(pos, (short)100, 100, 2);
        var result = InfusionInstabilityEffects.perform(h.getLevel(), matrix(h),
                LegacyInfusionInstability.Event.DESTROY_ITEM_FLUX, List.of(pos), LOW);
        h.assertTrue(result.droppedItems() == 0 && result.pollutionAdded() == 5, "Mode4 destroys and pollutes5");
        h.assertTrue(aura.getFlux(pos) == 7, "Persistent real aura flux changed");
        h.assertTrue(!((PedestalBlockEntity) h.getLevel().getBlockEntity(pos)).isOccupied(), "Actual slot deleted");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).isEmpty(),
                "Destroyed item was never spawned");
        h.succeed();
    }

    @GameTest(template="infusion_paid_blank",batch="infusion_hazards",timeoutTicks=60)
    public static void gooEventReplacesBlockAboveAndAppliesRealExhaustion(GameTestHelper h) {
        BlockPos pos = pedestal(h);
        h.getLevel().setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
        var result = InfusionInstabilityEffects.perform(h.getLevel(), matrix(h),
                LegacyInfusionInstability.Event.EJECT_ITEM_GOO, List.of(pos), LOW);
        h.assertTrue(result.droppedItems() == 1, "Goo accident drops original item");
        h.assertTrue(h.getLevel().getBlockState(pos.above()).is(InfusionHazardContent.FLUX_GOO.get()),
                "Source unconditionally overwrites above block with actual goo");
        var pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(15, 7, 12));
        pig.setPos(pos.getX() + .5, pos.getY() + 1.01, pos.getZ() + .5);
        pig.setDeltaMovement(1, 0, 1);
        h.getLevel().getBlockState(pos.above()).entityInside(h.getLevel(), pos.above(), pig);
        var effect = pig.getEffect(InfusionHazardContent.VIS_EXHAUST);
        h.assertTrue(effect != null && effect.getDuration() == 600 && effect.getAmplifier() == 2,
                "Full goo imposes actual600t amp2 exhaustion");
        h.assertTrue(effect.getCures().isEmpty(), "Source exhaustion cannot be cured by milk");
        h.assertTrue(Math.abs(InfusionHazardContent.visExhaustDiscountPenalty(pig) - .3F) < .00001F,
                "Caster discount penalty is30 percentagepoints");
        h.assertTrue(pig.getDeltaMovement().x == 0 && pig.getDeltaMovement().z == 0, "Fullgoo source horizontal drag");
        h.succeed();
    }

    @GameTest(template="infusion_paid_blank",batch="infusion_hazards",timeoutTicks=80)
    public static void fluxTaintHazardActuallyDamagesOnSourceTick(GameTestHelper h) {
        var pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(12, 8, 12));
        float before = pig.getHealth();
        var result = InfusionInstabilityEffects.perform(h.getLevel(), matrix(h),
                LegacyInfusionInstability.Event.HARM_ALL, List.of(), LOW);
        var effect = pig.getEffect(InfusionHazardContent.FLUX_TAINT);
        h.assertTrue(result.affectedEntities() >= 1 && effect != null && effect.getDuration() == 120,
                "Source harm grants actual120tick FluxTaint");
        h.runAtTickTime(45, () -> {
            h.assertTrue(pig.getHealth() <= before - 1, "FluxTaint ticks real taint damage, not only an icon");
            h.succeed();
        });
    }

    @GameTest(template="infusion_paid_blank",batch="infusion_hazards",timeoutTicks=60)
    public static void exhaustionHazardHasOriginalFlagsAndCostPenalty(GameTestHelper h) {
        var pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(12, 8, 12));
        InfusionInstabilityEffects.perform(h.getLevel(), matrix(h), LegacyInfusionInstability.Event.HARM_ALL,
                List.of(), EXHAUST);
        var effect = pig.getEffect(InfusionHazardContent.VIS_EXHAUST);
        h.assertTrue(effect != null && effect.getDuration() == 2400 && effect.getAmplifier() == 0
                && effect.isAmbient() && effect.isVisible() && effect.getCures().isEmpty(),
                "Original2400ticks ambient amp0 uncureable VisExhaust");
        h.assertTrue(Math.abs(InfusionHazardContent.visExhaustDiscountPenalty(pig) - .1F) < .00001F,
                "Original10 percentagepoint casterdiscount penalty");
        h.succeed();
    }

    @GameTest(template="infusion_paid_blank",batch="infusion_hazards",timeoutTicks=60)
    public static void zapDealsMagicDamageAndMatrixExplosionPreservesTerrain(GameTestHelper h) {
        var pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(12, 8, 12));
        float health = pig.getHealth();
        var result = InfusionInstabilityEffects.perform(h.getLevel(), matrix(h),
                LegacyInfusionInstability.Event.ZAP_ALL, List.of(), LOW);
        h.assertTrue(result.affectedEntities() >= 1 && pig.getHealth() == health - 4, "Source minimum zap damage4");
        BlockPos stone = matrix(h).below();
        h.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        InfusionInstabilityEffects.perform(h.getLevel(), matrix(h),
                LegacyInfusionInstability.Event.MATRIX_EXPLOSION, List.of(), LOW);
        h.assertTrue(h.getLevel().getBlockState(stone).is(Blocks.STONE), "Source explosion does not break blocks");
        h.succeed();
    }

    @GameTest(template="infusion_paid_blank",batch="infusion_hazards_player",timeoutTicks=60)
    public static void warpAndInstabilityResearchPersistInActualPlayerState(GameTestHelper h) {
        // Vanilla's mock login builds an unnegotiated embedded connection;
        // NeoForge correctly rejects mod packets at its login event. A native
        // FakePlayer has the supported no-client connection and bypasses login.
        var player = FakePlayerFactory.get(h.getLevel(),
                new GameProfile(UUID.randomUUID(), "InfusionHazardTest"));
        BlockPos center = matrix(h);
        player.setPos(center.getX() + .5, center.getY(), center.getZ() + .5);
        h.getLevel().addNewPlayer(player);
        var data = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA);
        data.warp().clear();
        data.knowledge().removeResearch("!INSTABILITY");
        var result = InfusionInstabilityEffects.perform(h.getLevel(), center,
                LegacyInfusionInstability.Event.WARP, List.of(), LOW);
        h.assertTrue(result.affectedEntities() == 1 && data.warp().get(WarpType.NORMAL) == 1,
                "Actual selected player's normal warp increased1");
        h.assertTrue(data.warp().getCounter() == 1, "Original warp event counter reset");
        h.assertTrue(data.knowledge().isResearchKnown("!INSTABILITY"), "Actual research grant stored");
        var empty = InfusionInstabilityEffects.perform(h.getLevel(), center,
                LegacyInfusionInstability.Event.EJECT_ITEM, List.of(), LOW);
        h.assertTrue(empty.researchGranted() == 0 && data.warp().get(WarpType.NORMAL) == 1,
                "Targetless event neither duplicates research nor unrelated warp");
        h.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        h.succeed();
    }
}
