package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.item.GolemPlacerItem;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Actual server-world assertions. These tests do not stand in for a cold world restart or a client. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemGameTests {
    private GolemGameTests() {}

    @GameTest(template = "golem_test", timeoutTicks = 40)
    public static void placerChargesOnlyForSuccessfulSurvivalSpawn(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GolemPlacerTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 1, 0))));
        ItemStack held = GolemPlacerItem.stack(GolemDesign.defaultWood(), 0);
        held.setCount(2);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos support = helper.absolutePos(new BlockPos(3, 0, 3));

        InteractionResult placed = GolemModule.GOLEM_PLACER.get().useOn(context(player, support));
        helper.assertTrue(placed.consumesAction(), "A valid survival use must place a golem");
        helper.assertTrue(held.getCount() == 1, "Exactly one placer must be charged");
        var spawned = level.getEntitiesOfClass(ThaumcraftGolem.class, helper.getBounds());
        helper.assertTrue(spawned.size() == 1, "One use must create exactly one entity");
        helper.assertTrue(spawned.getFirst().getMaxHealth() == 16 && spawned.getFirst().getArmorValue() == 2,
                "The runtime wood material must apply source health 16 and armor 2");
        helper.assertTrue(spawned.getFirst().getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) == 0.3,
                "Navigation multiplier must not replace the source movement attribute");
        helper.assertTrue(player.getUUID().equals(spawned.getFirst().ownerId()), "Placer must set its actual owner");
        helper.assertTrue(spawned.getFirst().home().equals(support.above()), "Home must equal the spawn block");

        BlockPos obstructed = helper.absolutePos(new BlockPos(8, 0, 3));
        helper.setBlock(new BlockPos(8, 1, 3), Blocks.STONE);
        InteractionResult blocked = GolemModule.GOLEM_PLACER.get().useOn(context(player, obstructed));
        helper.assertTrue(blocked == InteractionResult.FAIL, "Collision must reject placement");
        helper.assertTrue(held.getCount() == 1, "A rejected placement must not consume the placer");

        CompoundTag invalid = new CompoundTag();
        invalid.putLong("props", -1L);
        held.set(DataComponents.CUSTOM_DATA, CustomData.of(invalid));
        InteractionResult malformed = GolemModule.GOLEM_PLACER.get().useOn(
                context(player, helper.absolutePos(new BlockPos(5, 0, 8))));
        helper.assertTrue(malformed == InteractionResult.FAIL, "Unknown construction IDs must be rejected");
        helper.assertTrue(held.getCount() == 1, "Malformed properties must not charge a placer");
        helper.assertTrue(level.getEntitiesOfClass(ThaumcraftGolem.class, helper.getBounds()).size() == 1,
                "Rejected uses must not create additional entities");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }

    @GameTest(template = "golem_test", timeoutTicks = 240)
    public static void homeGoalNavigatesInTheWorld(GameTestHelper helper) {
        floor(helper);
        BlockPos home = helper.absolutePos(new BlockPos(2, 1, 3));
        ThaumcraftGolem golem = worker(helper, GolemDesign.defaultWood(), new BlockPos(9, 1, 3), home, 0);
        Vec3 start = golem.position();
        // A real obstruction forces the navigation adapter to find a route or jump.
        helper.setBlock(new BlockPos(6, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(6, 1, 3), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertTrue(golem.isAlive(), "The worker must remain alive while navigating");
            helper.assertTrue(golem.position().distanceToSqr(start) > 4,
                    "Home navigation must change the actual entity position");
            helper.assertTrue(golem.distanceToSqr(Vec3.atCenterOf(home)) <= 3,
                    "The worker must reach the source home-goal stopping threshold");
        });
    }

    @GameTest(template = "golem_test", timeoutTicks = 40)
    public static void carriedStacksRespectComponentsAndConserveRemainders(GameTestHelper helper) {
        floor(helper);
        ThaumcraftGolem golem = worker(helper, GolemDesign.defaultWood(), new BlockPos(3, 1, 3),
                helper.absolutePos(new BlockPos(3, 1, 3)), 0);
        ItemStack first = new ItemStack(Items.DIAMOND, 60);
        helper.assertTrue(golem.holdItem(first).isEmpty(), "The first stack must fit");
        helper.assertTrue(first.getCount() == 60, "holdItem must not alias or mutate its caller");
        ItemStack incoming = new ItemStack(Items.DIAMOND, 10);
        ItemStack remainder = golem.holdItem(incoming);
        helper.assertTrue(golem.getMainHandItem().getCount() == 64 && remainder.getCount() == 6,
                "A partial merge must preserve all 70 diamonds");
        helper.assertTrue(incoming.getCount() == 10, "A merge must not mutate the source stack");
        remainder.setCount(1);
        helper.assertTrue(golem.getMainHandItem().getCount() == 64, "Remainder must not alias equipment");

        ItemStack named = new ItemStack(Items.DIAMOND, 7);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("A separate component identity"));
        golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND, 10));
        helper.assertTrue(golem.canCarryAmount(named) == 0, "Different components must not merge");
        ItemStack rejected = golem.holdItem(named);
        helper.assertTrue(rejected.getCount() == 7 && golem.getMainHandItem().getCount() == 10,
                "Rejected component stacks must remain intact");
        helper.assertTrue(golem.getOffhandItem().isEmpty(), "An ordinary golem must have one carrying slot");

        golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        ItemStack swords = new ItemStack(Items.IRON_SWORD, 2);
        ItemStack swordRemainder = golem.holdItem(swords);
        helper.assertTrue(golem.getMainHandItem().getCount() == 1 && swordRemainder.getCount() == 1,
                "An empty hand must still respect the incoming item stack limit");
        helper.assertTrue(swords.getCount() == 2, "The oversized source object must stay independent");
        helper.succeed();
    }

    @GameTest(template = "golem_test", timeoutTicks = 40)
    public static void entityNbtRestoresIdentityConfigurationAndEquipment(GameTestHelper helper) {
        floor(helper);
        BlockPos home = helper.absolutePos(new BlockPos(3, 1, 4));
        GolemDesign design = GolemDesign.defaultWood().withRank(3);
        ThaumcraftGolem original = worker(helper, design, new BlockPos(4, 1, 4), home, 123);
        original.setGolemColor((byte) 7);
        original.setHealth(3.0F);
        original.setWorking(true);
        ItemStack paper = new ItemStack(Items.PAPER, 5);
        CompoundTag identity = new CompoundTag();
        identity.putString("test", "golem-persistence");
        paper.set(DataComponents.CUSTOM_DATA, CustomData.of(identity));
        original.setItemSlot(EquipmentSlot.MAINHAND, paper);
        original.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.DIAMOND, 3));

        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        saved.putByte("gflags", (byte) 10);
        ThaumcraftGolem restored = GolemModule.GOLEM.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "Registered type must construct a load target");
        restored.load(saved);
        assertRestored(helper, original, restored);
        helper.assertTrue(!restored.hasWorkTask(), "Runtime reservations must not survive NBT reload");

        // The clone is deliberately not added beside the live original with the same UUID.
        CompoundTag secondSave = restored.saveWithoutId(new CompoundTag());
        helper.assertTrue(secondSave.getByte("gflags") == 10, "Working flags must round trip independently of leases");
        ThaumcraftGolem secondLoad = GolemModule.GOLEM.get().create(helper.getLevel());
        helper.assertTrue(secondLoad != null, "Second load target must be constructible");
        secondLoad.load(secondSave);
        assertRestored(helper, original, secondLoad);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ThaumcraftGolem.class, helper.getBounds()).size() == 1,
                "NBT validation must not add duplicate entity UUIDs to the world");

        CompoundTag corrupt = saved.copy();
        corrupt.putLong("props", -1L);
        ThaumcraftGolem quarantined = GolemModule.GOLEM.get().create(helper.getLevel());
        helper.assertTrue(quarantined != null, "Malformed data load target must be constructible");
        quarantined.load(corrupt);
        helper.assertTrue(quarantined.isNoAi(), "Malformed construction must be quarantined without active AI");
        helper.assertTrue(quarantined.saveWithoutId(new CompoundTag()).getLong("props") == -1L,
                "Quarantine must preserve malformed raw configuration for repair");
        helper.assertTrue(ItemStack.matches(quarantined.getMainHandItem(), original.getMainHandItem())
                        && ItemStack.matches(quarantined.getOffhandItem(), original.getOffhandItem()),
                "Quarantine must preserve equipment without silently replacing the worker");
        helper.succeed();
    }

    @GameTest(template = "golem_test", batch = "golem_pickup", timeoutTicks = 240)
    public static void twoWorkersCollectOneRealSealTargetWithoutDuplication(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        BlockPos support = helper.absolutePos(new BlockPos(6, 0, 6));
        GolemSeals seals = GolemSeals.get(level);
        helper.assertTrue(seals.place(support, Direction.UP, owner), "Pickup seal must be placed once");
        helper.assertTrue(!seals.place(support, Direction.UP, owner), "Duplicate seals on one face must be rejected");
        GolemDesign design = pickupDesign();
        ThaumcraftGolem first = worker(helper, design, new BlockPos(2, 1, 5),
                helper.absolutePos(new BlockPos(2, 1, 5)), 0);
        ThaumcraftGolem second = worker(helper, design, new BlockPos(2, 1, 7),
                helper.absolutePos(new BlockPos(2, 1, 7)), 0);
        Vec3 firstStart = first.position();
        Vec3 secondStart = second.position();
        ItemEntity item = groundItem(helper, new BlockPos(6, 1, 6), new ItemStack(Items.DIAMOND, 17));

        helper.succeedWhen(() -> {
            int carried = diamonds(first) + diamonds(second);
            int onGround = item.isAlive() ? item.getItem().getCount() : 0;
            helper.assertTrue(carried + onGround == 17, "Seal transfer must conserve exactly 17 diamonds");
            helper.assertTrue(!item.isAlive() && carried == 17, "A real goal must reach and collect the real ItemEntity");
            helper.assertTrue((diamonds(first) == 17) != (diamonds(second) == 17),
                    "Only one worker may collect this target");
            helper.assertTrue(first.position().distanceToSqr(firstStart) > 1
                            || second.position().distanceToSqr(secondStart) > 1,
                    "Collection must follow actual navigation, not a remote inventory mutation");
            helper.assertTrue(seals.remove(support, Direction.UP, owner), "Test must remove its persistent seal");
        });
    }

    @GameTest(template = "golem_test", batch = "golem_cancel", timeoutTicks = 80)
    public static void removedTargetsAndSealsInvalidateReservedWork(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        BlockPos support = helper.absolutePos(new BlockPos(6, 0, 6));
        GolemSeals seals = GolemSeals.get(level);
        helper.assertTrue(seals.place(support, Direction.UP, owner), "Cancellation seal must be placed");
        ThaumcraftGolem golem = worker(helper, pickupDesign(), new BlockPos(5, 1, 6),
                helper.absolutePos(new BlockPos(5, 1, 6)), 0);
        golem.setNoAi(true); // Claim below isolates invalidation from automatic completion.
        ItemEntity item = groundItem(helper, new BlockPos(6, 1, 6), new ItemStack(Items.DIAMOND, 9));

        helper.succeedWhen(() -> {
            var claim = seals.claim(golem, ignored -> true);
            helper.assertTrue(claim.isPresent(), "The actual seal tick must first produce claimable work");
            var assignment = claim.orElseThrow();
            helper.assertTrue(seals.claim(golem, ignored -> true).isEmpty(), "A reserved target must not be claimed again");
            item.discard();
            helper.assertTrue(seals.target(level, golem, assignment) == null,
                    "A discarded target must invalidate an existing assignment");
            helper.assertTrue(!seals.pickup(level, golem, assignment), "An invalid target must not fabricate carried items");
            helper.assertTrue(seals.remove(support, Direction.UP, owner), "Removing a seal must retire its work");
            helper.assertTrue(!seals.remove(support, Direction.UP, owner), "The same seal cannot be removed twice");
            helper.assertTrue(seals.target(level, golem, assignment) == null,
                    "A removed seal must not leave an executable assignment");
            helper.assertTrue(golem.getMainHandItem().isEmpty(), "Cancellation must leave the worker empty");
        });
    }

    @GameTest(template = "golem_test", batch = "golem_death", timeoutTicks = 60)
    public static void cargoDeathDropsOnceWithExactComponents(GameTestHelper helper) {
        floor(helper);
        ThaumcraftGolem golem = worker(helper, GolemDesign.defaultWood(), new BlockPos(5, 1, 5),
                helper.absolutePos(new BlockPos(5, 1, 5)), 0);
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 23);
        diamonds.set(DataComponents.CUSTOM_NAME, Component.literal("Unchanged cargo"));
        ItemStack damagedTool = new ItemStack(Items.IRON_AXE);
        damagedTool.set(DataComponents.DAMAGE, 17);
        damagedTool.set(DataComponents.CUSTOM_NAME, Component.literal("Preserve existing damage"));
        golem.setItemSlot(EquipmentSlot.MAINHAND, diamonds.copy());
        golem.setItemSlot(EquipmentSlot.OFFHAND, damagedTool.copy());
        helper.assertTrue(golem.hurt(golem.damageSources().generic(), 1000), "Real server damage must kill the worker");
        helper.assertTrue(golem.isDeadOrDying(), "The worker must enter its death lifecycle");

        helper.runAfterDelay(25, () -> {
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds());
            var diamondDrops = drops.stream().filter(item -> item.getItem().is(Items.DIAMOND)).toList();
            var toolDrops = drops.stream().filter(item -> item.getItem().is(Items.IRON_AXE)).toList();
            helper.assertTrue(diamondDrops.size() == 1 && ItemStack.matches(diamondDrops.getFirst().getItem(), diamonds),
                    "Death must create exactly one 23-diamond stack with unchanged components");
            helper.assertTrue(toolDrops.size() == 1 && ItemStack.matches(toolDrops.getFirst().getItem(), damagedTool),
                    "Cargo damage and name must bypass vanilla equipment damage randomization");
            helper.assertTrue(golem.getMainHandItem().isEmpty() && golem.getOffhandItem().isEmpty(),
                    "Dropped cargo must be removed before vanilla equipment dropping");
            helper.assertTrue(!golem.hurt(golem.damageSources().generic(), 1000),
                    "Repeated damage to the dead worker must not run a second death transfer");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).size() == 2,
                    "Death must not duplicate cargo through another equipment/drop callback");
            helper.succeed();
        });
    }

    @GameTest(template = "golem_test", batch = "golem_unpack", timeoutTicks = 40)
    public static void ownerReclaimsOnePlacerAndOneCargoStack(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GolemOwnerTest"));
        owner.setGameMode(GameType.SURVIVAL);
        owner.getInventory().clearContent();
        owner.setShiftKeyDown(true);
        owner.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 5))));
        ThaumcraftGolem golem = worker(helper, GolemDesign.defaultWood(), new BlockPos(5, 1, 5),
                helper.absolutePos(new BlockPos(5, 1, 5)), 42);
        golem.initialize(golem.design(), owner.getUUID(), golem.home(), 42);
        ItemStack cargo = new ItemStack(Items.PAPER, 9);
        cargo.set(DataComponents.CUSTOM_NAME, Component.literal("One reclaim only"));
        golem.setItemSlot(EquipmentSlot.MAINHAND, cargo.copy());

        helper.assertTrue(golem.interact(owner, InteractionHand.MAIN_HAND).consumesAction(),
                "The actual owner sneak interaction must reclaim the worker");
        helper.assertTrue(golem.isRemoved(), "Successful reclaim must remove the entity");
        helper.assertTrue(!golem.interact(owner, InteractionHand.MAIN_HAND).consumesAction(),
                "An already removed worker must reject a second reclaim");
        var drops = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds());
        var placers = drops.stream().filter(item -> item.getItem().is(GolemModule.GOLEM_PLACER.get())).toList();
        var cargoDrops = drops.stream().filter(item -> item.getItem().is(Items.PAPER)).toList();
        helper.assertTrue(placers.size() == 1 && placers.getFirst().getItem().getCount() == 1,
                "Reclaim must yield exactly one placer");
        CompoundTag state = placers.getFirst().getItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        helper.assertTrue(state.getLong("props") == golem.design().toLong() && state.getInt("xp") == 42,
                "Reclaimed placer must preserve construction and XP");
        helper.assertTrue(cargoDrops.size() == 1 && ItemStack.matches(cargoDrops.getFirst().getItem(), cargo),
                "Reclaim must return exact cargo once");
        helper.assertTrue(drops.size() == 2 && golem.getMainHandItem().isEmpty(),
                "Removed entity must neither retain nor create another copy of its cargo");
        owner.setShiftKeyDown(false);
        helper.succeed();
    }

    private static UseOnContext context(net.minecraft.world.entity.player.Player player, BlockPos support) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support).add(0, 0.5, 0), Direction.UP, support, false));
    }

    private static GolemDesign pickupDesign() {
        // WOOD supplies LIGHT and BASIC arms have no forbidden CLUMSY trait.
        return GolemDesign.defaultWood();
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static ThaumcraftGolem worker(GameTestHelper helper, GolemDesign design, BlockPos relativeSpawn,
                                         BlockPos absoluteHome, int xp) {
        ThaumcraftGolem golem = GolemModule.GOLEM.get().create(helper.getLevel());
        helper.assertTrue(golem != null, "Registered golem type must create a real entity");
        golem.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(relativeSpawn)));
        golem.initialize(design, UUID.randomUUID(), absoluteHome, xp);
        helper.assertTrue(helper.getLevel().addFreshEntity(golem), "Server must accept the worker entity");
        return golem;
    }

    private static ItemEntity groundItem(GameTestHelper helper, BlockPos relative, ItemStack stack) {
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(relative));
        ItemEntity item = new ItemEntity(helper.getLevel(), pos.x, pos.y, pos.z, stack);
        item.setNoPickUpDelay();
        item.setDeltaMovement(Vec3.ZERO);
        item.setOnGround(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(item), "Server must accept the actual item target");
        return item;
    }

    private static int diamonds(ThaumcraftGolem golem) {
        return golem.getMainHandItem().is(Items.DIAMOND) ? golem.getMainHandItem().getCount() : 0;
    }

    private static void assertRestored(GameTestHelper helper, ThaumcraftGolem original, ThaumcraftGolem restored) {
        helper.assertTrue(restored.getUUID().equals(original.getUUID()), "Entity UUID must round trip");
        helper.assertTrue(original.ownerId().equals(restored.ownerId()), "Owner UUID must round trip");
        helper.assertTrue(restored.design().toLong() == original.design().toLong(), "Construction and rank must round trip");
        helper.assertTrue(restored.home().equals(original.home()), "Home position must round trip");
        helper.assertTrue(restored.rankXp() == original.rankXp(), "Rank XP must round trip");
        helper.assertTrue(restored.golemColor() == original.golemColor(), "Color must round trip");
        helper.assertTrue(restored.getHealth() == original.getHealth(), "Reload must not heal the worker");
        helper.assertTrue(ItemStack.matches(restored.getMainHandItem(), original.getMainHandItem()),
                "Main-hand count and components must round trip");
        helper.assertTrue(ItemStack.matches(restored.getOffhandItem(), original.getOffhandItem()),
                "Off-hand count and components must round trip");
    }
}
