package dev.ehtid.thaumcraft.entity;

import io.netty.buffer.Unpooled;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.network.WispZapPayload;
import dev.ehtid.thaumcraft.port.core.LegacyWispSpawnRules;
import dev.ehtid.thaumcraft.registry.FluxRiftRegistration;
import dev.ehtid.thaumcraft.registry.WispRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
            timeoutTicks = 40
    )
    public static void verticalDampingMatchesBETA26Coefficient(GameTestHelper helper) {
        Vec3[] inputs = {
                new Vec3(0.25D, 1.0D, -0.5D),
                new Vec3(-1.0D, -0.75D, 0.0D),
                new Vec3(0.0D, 0.125D, 1.0D)
        };
        for (Vec3 input : inputs) {
            Vec3 actual = WispEntity.applyBETA26VerticalDamping(input);
            double expectedY = input.y * 0.6000000238418579D;
            if (Double.doubleToLongBits(actual.x) != Double.doubleToLongBits(input.x)
                    || Double.doubleToLongBits(actual.y) != Double.doubleToLongBits(expectedY)
                    || Double.doubleToLongBits(actual.z) != Double.doubleToLongBits(input.z)) {
                throw new AssertionError("BETA26 Wisp vertical damping mismatch: input="
                        + input + " actual=" + actual + " expectedY=" + expectedY);
            }
        }

        System.out.println("TC_GAMETEST_WISP_VERTICAL_DAMPING=PASS coefficient=0.6000000238418579 xz=unchanged");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 40
    )
    public static void wispZapPayloadPreservesBETA26EntityIds(GameTestHelper helper) {
        WispZapPayload expected = new WispZapPayload(0x12345678, 0x76543210);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            WispZapPayload.STREAM_CODEC.encode(buffer, expected);
            WispZapPayload decoded = WispZapPayload.STREAM_CODEC.decode(buffer);
            if (!expected.equals(decoded)) {
                throw new AssertionError("Wisp zap payload changed BETA26 source/target IDs: " + decoded);
            }
        } finally {
            buffer.release();
        }

        System.out.println("TC_GAMETEST_WISP_ZAP_PAYLOAD=PASS source=0x12345678 target=0x76543210");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 40
    )
    public static void thunderSpawnGateUsesBETA26SkylightSubtraction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos spawnPos = new BlockPos(
                anchor.getX(), level.getMaxBuildHeight() - 3, anchor.getZ());
        WispEntity wisp = new WispEntity(WispRegistration.get(), level);
        wisp.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(),
                spawnPos.getZ() + 0.5D, 0.0F, 0.0F);

        long previousDayTime = level.getDayTime();
        boolean previousRaining = level.getLevelData().isRaining();
        boolean previousThundering = level.getLevelData().isThundering();
        float previousRainLevel = level.getRainLevel(1.0F);
        float previousThunderLevel = level.getThunderLevel(1.0F);
        level.setDayTime(6000L);
        level.setWeatherParameters(0, 1200, true, true);
        // isRaining()/isThundering() read interpolated gradients, not only
        // saved weather flags. Set them directly for this short GameTest.
        level.setRainLevel(1.0F);
        level.setThunderLevel(1.0F);

        helper.runAfterDelay(5, () -> {
            try {
                if (!level.isThundering()) {
                    throw new AssertionError("Thunder fixture did not enter thunder state: raining="
                            + level.isRaining() + " thundering=" + level.isThundering()
                            + " dataRaining=" + level.getLevelData().isRaining()
                            + " dataThundering=" + level.getLevelData().isThundering()
                            + " rainLevel=" + level.getRainLevel(1.0F)
                            + " thunderLevel=" + level.getThunderLevel(1.0F)
                            + " dayTime=" + level.getDayTime());
                }

                // BETA26 tests raw sky brightness, not the additional canSeeSky() predicate.
                int skyLight = level.getBrightness(LightLayer.SKY, spawnPos);
                int ordinaryLocalLight = level.getMaxLocalRawBrightness(spawnPos);
                int thunderLocalLight = level.getMaxLocalRawBrightness(spawnPos, 10);
                int actualLocalLight = wisp.getLocalLightForSpawn(spawnPos);
                if (skyLight != 15 || ordinaryLocalLight <= 7
                        || thunderLocalLight > 7 || ordinaryLocalLight <= thunderLocalLight) {
                    throw new AssertionError("Thunder fixture cannot distinguish BETA26 fixed subtraction: sky="
                            + skyLight + " ordinary=" + ordinaryLocalLight
                            + " thunder=" + thunderLocalLight + " canSeeSky=" + level.canSeeSky(spawnPos)
                        + " spawnPos=" + spawnPos + " maxBuildHeight=" + level.getMaxBuildHeight());
                }
                if (actualLocalLight != thunderLocalLight) {
                    throw new AssertionError("Wisp must use fixed thunder skylight subtraction 10: actual="
                            + actualLocalLight + " expected=" + thunderLocalLight);
                }

                long seed = findThunderSpawnSeed(skyLight, thunderLocalLight, ordinaryLocalLight);
                RandomSource expectedRolls = RandomSource.create(seed);
                int skyRoll = expectedRolls.nextInt(32);
                int localRoll = expectedRolls.nextInt(8);
                if (!LegacyWispSpawnRules.canSpawn(false, 0, skyLight, skyRoll,
                        thunderLocalLight, localRoll)) {
                    throw new AssertionError("BETA26 thunder-adjusted local light must allow this spawn");
                }
                if (LegacyWispSpawnRules.canSpawn(false, 0, skyLight, skyRoll,
                        ordinaryLocalLight, localRoll)) {
                    throw new AssertionError("Without the fixed thunder subtraction the same spawn must be rejected");
                }

                System.out.println("TC_GAMETEST_WISP_THUNDER_LIGHT=PASS sky=" + skyLight
                        + " ordinary=" + ordinaryLocalLight + " thunder=" + thunderLocalLight
                        + " rolls=" + skyRoll + "," + localRoll);
            } finally {
                level.setWeatherParameters(0, 1200,
                        previousRaining, previousThundering);
                level.setRainLevel(previousRainLevel);
                level.setThunderLevel(previousThunderLevel);
                level.setDayTime(previousDayTime);
            }
            helper.succeed();
        });
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
        Zombie exactThreshold = EntityType.ZOMBIE.create(level);
        if (stationary == null || moving == null || exactThreshold == null) {
            throw new AssertionError("Unable to create Wisp zap targets");
        }
        stationary.setNoAi(true);
        moving.setNoAi(true);
        exactThreshold.setNoAi(true);
        stationary.moveTo(origin.getX() + 2.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D, 0.0F, 0.0F);
        moving.moveTo(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D, 0.0F, 0.0F);
        exactThreshold.moveTo(origin.getX() + 6.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D, 0.0F, 0.0F);

        if (!level.addFreshEntity(wisp)
                || !level.addFreshEntity(stationary)
                || !level.addFreshEntity(moving)
                || !level.addFreshEntity(exactThreshold)) {
            throw new AssertionError("Wisp zap GameTest could not add all four entities");
        }

        // Zombie armor reduces a raw four-point hit to 3.936; zero it so this
        // test measures the Wisp's BETA26 damage branch rather than armor mitigation.
        setArmorToZero(stationary);
        setArmorToZero(moving);
        setArmorToZero(exactThreshold);

        // BETA26 classifies velocity before hurt() applies knockback to the target.
        stationary.setDeltaMovement(Vec3.ZERO);
        moving.setDeltaMovement(new Vec3(0.2D, 0.0D, 0.0D));
        exactThreshold.setDeltaMovement(new Vec3(0.10000000149011612D, 0.0D, 0.0D));

        double attackDamage = wisp.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (Math.abs(attackDamage - 3.0D) > 0.001D) {
            throw new AssertionError("Wisp attack attribute must be 3.0 before stationary=" + attackDamage);
        }

        Vec3 stationaryMotionBefore = stationary.getDeltaMovement();
        float stationaryBefore = stationary.getHealth();
        boolean stationaryHit = wisp.tryZap(stationary, 0.0F);
        float stationaryDamage = stationaryBefore - stationary.getHealth();
        if (!stationaryHit || Math.abs(stationaryDamage - 4.0F) > 0.001F) {
            throw new AssertionError("Stationary target must take four raw damage: hit=" + stationaryHit
                    + " damage=" + stationaryDamage + " attack=" + attackDamage
                    + " motionBefore=" + stationaryMotionBefore
                    + " motionAfter=" + stationary.getDeltaMovement());
        }

        Vec3 movingMotionBefore = moving.getDeltaMovement();
        float movingBefore = moving.getHealth();
        boolean movingHit = wisp.tryZap(moving, 0.0F);
        float movingDamage = movingBefore - moving.getHealth();
        if (!movingHit || Math.abs(movingDamage - 3.0F) > 0.001F) {
            throw new AssertionError("Moving target must take three raw damage: hit=" + movingHit
                    + " damage=" + movingDamage + " attack=" + attackDamage
                    + " motionBefore=" + movingMotionBefore
                    + " motionAfter=" + moving.getDeltaMovement());
        }

        // The source uses strict > 0.10000000149011612, so equality is stationary.
        Vec3 thresholdMotionBefore = exactThreshold.getDeltaMovement();
        float thresholdBefore = exactThreshold.getHealth();
        boolean thresholdHit = wisp.tryZap(exactThreshold, 0.5F);
        float thresholdDamage = thresholdBefore - exactThreshold.getHealth();
        if (!thresholdHit || Math.abs(thresholdDamage - 4.0F) > 0.001F) {
            throw new AssertionError("Exact BETA26 movement threshold must use stationary branch: hit="
                    + thresholdHit + " damage=" + thresholdDamage
                    + " motionBefore=" + thresholdMotionBefore
                    + " motionAfter=" + exactThreshold.getDeltaMovement());
        }

        System.out.println("TC_GAMETEST_WISP_ZAP_DAMAGE=PASS stationary=4 moving=3 attack=" + attackDamage);
        System.out.println("TC_GAMETEST_WISP_ZAP_THRESHOLD=PASS exact=0.10000000149011612 branch=stationary");
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

    private static long findThunderSpawnSeed(
            int skyLight, int thunderLocalLight, int ordinaryLocalLight) {
        for (long seed = 0L; seed < 100_000L; seed++) {
            RandomSource candidate = RandomSource.create(seed);
            int skyRoll = candidate.nextInt(32);
            int localRoll = candidate.nextInt(8);
            if (skyRoll >= skyLight
                    && localRoll >= thunderLocalLight
                    && localRoll < ordinaryLocalLight) {
                return seed;
            }
        }
        throw new AssertionError("Unable to find deterministic BETA26 thunder spawn rolls");
    }

    private static void setArmorToZero(Zombie target) {
        var armor = target.getAttribute(Attributes.ARMOR);
        if (armor == null) {
            throw new AssertionError("Zombie GameTest target is missing its armor attribute");
        }
        armor.setBaseValue(0.0D);
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
