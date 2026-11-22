package dev.ehtid.thaumcraft.entity;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.port.core.LegacyWispSpawnRules;
import dev.ehtid.thaumcraft.registry.FluxRiftRegistration;
import dev.ehtid.thaumcraft.registry.WispRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Native server acceptance for the BETA26 Flux Rift Wisp event and combat branch. */
@GameTestHolder(ThaumcraftPort.MOD_ID)
public final class WispEntityGameTests {
    private WispEntityGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 40
    )
    public static void spawnGateMatchesBETA26Boundaries(GameTestHelper helper) {
        check(LegacyWispSpawnRules.canSpawn(false, 7, 0, 0, 0, 0), "dark valid spawn");
        check(!LegacyWispSpawnRules.canSpawn(true, 0, 0, 0, 0, 0), "peaceful difficulty");
        check(!LegacyWispSpawnRules.canSpawn(false, 8, 0, 0, 0, 0), "eight nearby wisps");
        check(!LegacyWispSpawnRules.canSpawn(false, 0, 15, 14, 0, 0), "sky threshold");
        check(LegacyWispSpawnRules.canSpawn(false, 0, 15, 15, 0, 0), "sky threshold inclusive");
        check(!LegacyWispSpawnRules.canSpawn(false, 0, 0, 0, 8, 7), "local light threshold");
        check(LegacyWispSpawnRules.canSpawn(false, 0, 0, 0, 7, 7), "local light inclusive");
        System.out.println("TC_GAMETEST_WISP_SPAWN_GATE=PASS assertions=7");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 120
    )
    public static void fluxRiftEventZeroSpawnsWispAndPaysFiveStability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(8, 2, 8));
        buildDarkRoom(level, center);

        FluxRiftEntity rift = new FluxRiftEntity(FluxRiftRegistration.get(), level);
        rift.moveTo(center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D, 0.0F, 0.0F);
        rift.setRiftStability(-5.0F);

        helper.runAfterDelay(10, () -> {
            BlockPos spawnPos = center.offset(0, 1, 0);
            int sky = level.getBrightness(LightLayer.SKY, spawnPos);
            int local = level.getMaxLocalRawBrightness(spawnPos);
            boolean spawned = rift.spawnWispAt(
                    center.getX() + 0.5D,
                    center.getY() + 1.0D,
                    center.getZ() + 0.5D,
                    true,
                    RandomSource.create(1234L));
            if (!spawned) {
                throw new AssertionError("BETA26 event 0 failed in dark enclosure: sky=" + sky
                        + " local=" + local + " difficulty=" + level.getDifficulty());
            }

            List<WispEntity> wisps = level.getEntitiesOfClass(
                    WispEntity.class,
                    new AABB(center.getX() - 2, center.getY(), center.getZ() - 2,
                            center.getX() + 3, center.getY() + 4, center.getZ() + 3));
            if (wisps.size() != 1) {
                throw new AssertionError("Expected one spawned Rift Wisp, got " + wisps.size());
            }
            if (!"vitium".equals(wisps.get(0).getAspectType())) {
                throw new AssertionError("The one-in-five Flux Wisp override was not retained");
            }
            if (Math.abs(rift.getRiftStability()) > 0.0001F) {
                throw new AssertionError("Successful Wisp event must add exactly five stability");
            }
            System.out.println("TC_GAMETEST_FLUX_RIFT_EVENT0_WISP=PASS type=vitium stabilityDelta=5");
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 40
    )
    public static void zapDamageMatchesTargetMovement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 5));

        WispEntity wisp = new WispEntity(WispRegistration.get(), level);
        wisp.moveTo(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D, 0.0F, 0.0F);
        Zombie stationary = EntityType.ZOMBIE.create(level);
        Zombie moving = EntityType.ZOMBIE.create(level);
        if (stationary == null || moving == null) {
            throw new AssertionError("Unable to create Wisp zap targets");
        }
        stationary.setNoAi(true);
        moving.setNoAi(true);
        stationary.moveTo(origin.getX() + 2.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D, 0.0F, 0.0F);
        moving.moveTo(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D, 0.0F, 0.0F);

        if (!level.addFreshEntity(wisp)
                || !level.addFreshEntity(stationary)
                || !level.addFreshEntity(moving)) {
            throw new AssertionError("Wisp zap GameTest could not add all three entities");
        }

        // Reassert both velocity vectors after insertion so spawn/finalization hooks
        // cannot accidentally move the stationary fixture across BETA26's 0.1 threshold.
        stationary.setDeltaMovement(Vec3.ZERO);
        moving.setDeltaMovement(new Vec3(0.2D, 0.0D, 0.0D));

        double attackDamage = wisp.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (Math.abs(attackDamage - 3.0D) > 0.001D) {
            throw new AssertionError("Wisp attack attribute must be 3.0 before stationary=" + attackDamage);
        }

        float stationaryBefore = stationary.getHealth();
        boolean stationaryHit = wisp.tryZap(stationary, 0.0F);
        float stationaryDamage = stationaryBefore - stationary.getHealth();
        if (!stationaryHit || Math.abs(stationaryDamage - 4.0F) > 0.001F) {
            throw new AssertionError("Stationary target must take four damage: hit=" + stationaryHit
                    + " damage=" + stationaryDamage + " attack=" + attackDamage
                    + " motion=" + stationary.getDeltaMovement());
        }

        float movingBefore = moving.getHealth();
        boolean movingHit = wisp.tryZap(moving, 0.0F);
        float movingDamage = movingBefore - moving.getHealth();
        if (!movingHit || Math.abs(movingDamage - 3.0F) > 0.001F) {
            throw new AssertionError("Moving target must take three damage: hit=" + movingHit
                    + " damage=" + movingDamage + " attack=" + attackDamage
                    + " motion=" + moving.getDeltaMovement());
        }

        System.out.println("TC_GAMETEST_WISP_ZAP_DAMAGE=PASS stationary=4 moving=3 attack=" + attackDamage);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 40
    )
    public static void aspectTagPersistsAndBuildsOriginalCrystalPayload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WispEntity source = new WispEntity(WispRegistration.get(), level);
        source.setAspectType("vitium");

        CompoundTag saved = new CompoundTag();
        source.addAdditionalSaveData(saved);
        WispEntity restored = new WispEntity(WispRegistration.get(), level);
        restored.readAdditionalSaveData(saved);
        if (!"vitium".equals(restored.getAspectType())) {
            throw new AssertionError("Wisp Type NBT did not round-trip");
        }

        if (!"vitium".equals(CrystalEssenceItem.legacyAspect(source.createAspectCrystalDrop()))) {
            throw new AssertionError("Wisp death crystal must preserve the Wisp's exact aspect tag");
        }

        System.out.println("TC_GAMETEST_WISP_ASPECT_DROP=PASS aspect=vitium");
        helper.succeed();
    }

    private static void buildDarkRoom(ServerLevel level, BlockPos center) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                level.setBlock(center.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(center.offset(x, 5, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = 1; y <= 4; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (Math.abs(x) == 4 || Math.abs(z) == 4) {
                        level.setBlock(center.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
