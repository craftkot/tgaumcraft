package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/** Opt-in two-process disk persistence check, run only in build/golem-restart via tools/golems.gradle. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID)
public final class GolemRestartProbe {
    private static final String PHASE = System.getProperty("thaumcraft.golemRestartPhase", "");
    private static final boolean NBT_SCENARIO = "nbt".equals(System.getProperty("thaumcraft.golemRestartScenario", ""));
    private static final boolean LEAVE_SCENARIO = NBT_SCENARIO || "leave".equals(System.getProperty("thaumcraft.golemRestartScenario", ""));
    private static final boolean EMPTY_SCENARIO = LEAVE_SCENARIO || "empty".equals(System.getProperty("thaumcraft.golemRestartScenario", ""));
    private static final boolean AREA_SCENARIO = EMPTY_SCENARIO || "area".equals(System.getProperty("thaumcraft.golemRestartScenario", ""));
    private static final boolean BELL_SCENARIO = AREA_SCENARIO || "bell".equals(System.getProperty("thaumcraft.golemRestartScenario", ""));
    private static final boolean CONTROLS_SCENARIO = BELL_SCENARIO || "controls".equals(System.getProperty("thaumcraft.golemRestartScenario", ""));
    private static final boolean FILTER_SCENARIO = CONTROLS_SCENARIO || "filter".equals(System.getProperty("thaumcraft.golemRestartScenario", ""));
    private static final boolean FILL_SCENARIO = FILTER_SCENARIO || "fill".equals(System.getProperty("thaumcraft.golemRestartScenario", ""));
    private static final UUID GOLEM_ID = UUID.fromString("671c5d1c-8533-490b-9ac2-e06c973d0d10");
    private static final UUID OWNER_ID = UUID.fromString("712b8f88-92c3-4bee-a3d8-53e8248ac89d");
    private static final BlockPos HOME = new BlockPos(8, 120, 8);
    private static MinecraftServer active;
    private static int ticks;
    private static boolean checked;

    private GolemRestartProbe() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if ("press".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("formation".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("logistics".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("climber".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("guard".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("use".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("silk".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("lumber".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("breaker".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("advanced".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("matching".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("cycle".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("cocoa".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("farm".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if ("construction".equals(System.getProperty("thaumcraft.golemRestartScenario", ""))) return;
        if (!PHASE.equals("write") && !PHASE.equals("read")) return;
        active = event.getServer();
        ticks = 0;
        checked = false;
        active.overworld().setChunkForced(0, 0, true); // Test fixture only; production seals never force-load.
        active.overworld().getChunk(0, 0); // A persisted forced flag alone may not initiate entity loading yet.
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (active != event.getServer() || checked || ++ticks < 60) return;
        var level = active.overworld();
        boolean ready = level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(0, 0))
                && level.isPositionEntityTicking(HOME);
        if (!ready && ticks < 600) return; // Bound asynchronous entity loading, never retry a failed assertion.
        checked = true;
        try {
            require(ready, "Fixture chunk and persisted entities loaded within 600 ticks");
            if (PHASE.equals("write")) write();
            else read();
            LogUtils.getLogger().info("GOLEM_RESTART_{}_ASSERTIONS_OK", PHASE.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException | AssertionError e) {
            LogUtils.getLogger().error("GOLEM_RESTART_ASSERTIONS_FAILED", e);
        } finally {
            active.halt(false); // Normal server shutdown saves chunks/entities/SavedData to disk.
        }
    }

    private static ItemStack cargo() {
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 37);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("M10 restart cargo"));
        return stack;
    }

    private static void write() {
        var level = active.overworld();
        require(level.getEntity(GOLEM_ID) == null, "Write phase requires a fresh test world");
        level.setBlockAndUpdate(HOME.below(), Blocks.STONE.defaultBlockState());
        var golem = GolemModule.GOLEM.get().create(level);
        require(golem != null, "Registered entity factory");
        golem.setUUID(GOLEM_ID);
        golem.moveTo(8.5, 120, 8.5, 0, 0);
        golem.initialize(GolemDesign.defaultWood(), OWNER_ID, HOME, 13);
        golem.setNoAi(true);
        golem.setGolemColor((byte) 6);
        golem.setHealth(7);
        require(golem.holdItem(cargo()).isEmpty(), "Cargo fits");
        require(level.addFreshEntity(golem), "Entity accepted");
        require(GolemSeals.get(level).place(HOME.below(), Direction.UP, OWNER_ID), "Seal placed");
        if (FILL_SCENARIO) {
            var pos = HOME.offset(3, -1, 0);
            level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
            require(GolemSeals.get(level).place(pos, Direction.UP, OWNER_ID, GolemSeals.Type.FILL), "Fill seal placed");
            var handler = dev.ehtid.thaumcraft.golem.inventory.GolemContainers.handler(level,
                    new GolemSeals.SealKey(pos, Direction.UP));
            require(handler != null && handler.insertItem(0, new ItemStack(Items.GOLD_INGOT, 11), false).isEmpty(),
                    "Actual persistent destination populated");
        }
        if (FILTER_SCENARIO) {
            var data = GolemSeals.get(level);
            var pickup = data.get(new GolemSeals.SealKey(HOME.below(), Direction.UP));
            var fill = data.get(new GolemSeals.SealKey(HOME.offset(3, -1, 0), Direction.UP));
            require(data.configure(pickup, OWNER_ID, new dev.ehtid.thaumcraft.golem.seal.SealFilter(cargo(), true, 0)), "Pickup filter stored");
            require(data.configure(fill, OWNER_ID, new dev.ehtid.thaumcraft.golem.seal.SealFilter(cargo(), false, 70000)), "Fill stock cap stored");
        }
        if (CONTROLS_SCENARIO) {
            var data = GolemSeals.get(level);
            require(data.configureControls(data.get(new GolemSeals.SealKey(HOME.below(), Direction.UP)), OWNER_ID, -5, 16, true, true), "Pickup controls stored");
            require(data.configureControls(data.get(new GolemSeals.SealKey(HOME.offset(3,-1,0), Direction.UP)), OWNER_ID, 5, 6, true, false), "Fill controls stored");
        }
        if (BELL_SCENARIO) golem.setFollowingOwner(true);
        if (AREA_SCENARIO) {
            var data = GolemSeals.get(level);
            require(data.configureArea(data.get(new GolemSeals.SealKey(HOME.below(), Direction.UP)), OWNER_ID,
                    new BlockPos(2, 3, 4)), "Nondefault asymmetric Pickup area stored");
        }
        if (EMPTY_SCENARIO) {
            var data = GolemSeals.get(level);
            var key = new GolemSeals.SealKey(HOME.offset(-3, -1, 0), Direction.UP);
            level.setBlockAndUpdate(key.pos(), Blocks.BARREL.defaultBlockState());
            require(data.place(key.pos(), key.face(), OWNER_ID, GolemSeals.Type.EMPTY), "Empty type placed");
            require(data.configureControls(data.get(key), OWNER_ID, 1, 6, true, false), "Empty controls stored");
            var source = dev.ehtid.thaumcraft.golem.inventory.GolemContainers.handler(level, key);
            require(source != null && source.insertItem(0, new ItemStack(Items.EMERALD, 23), false).isEmpty(), "Persistent extraction source populated");
            golem.holdExtracted(new ItemStack(Items.DIAMOND, 3));
            require(golem.hasTransferRecovery(), "Unexpected payload retained beside incompatible normal cargo");
            if (LEAVE_SCENARIO) require(data.configureLeaveOne(data.get(key), OWNER_ID, true), "Source pleave stored");
            if (NBT_SCENARIO) {
                ItemStack ghost = new ItemStack(Items.EMERALD);
                ghost.set(DataComponents.CUSTOM_NAME, Component.literal("M10 pnbt disk ghost"));
                require(data.configure(data.get(key), OWNER_ID, new dev.ehtid.thaumcraft.golem.seal.SealFilter(ghost, false, 0, false)), "Loose pnbt filter stored without changing source");
            }
        }
        require(GolemSeals.get(level).pendingTasks() == 0, "No persisted task required");
    }

    private static void read() {
        var level = active.overworld();
        require(level.getEntity(GOLEM_ID) instanceof ThaumcraftGolem, "Entity restored from disk");
        var golem = (ThaumcraftGolem) level.getEntity(GOLEM_ID);
        require(golem.design().equals(GolemDesign.defaultWood()), "Design restored");
        require(OWNER_ID.equals(golem.ownerId()), "Owner restored");
        require(HOME.equals(golem.home()) && golem.rankXp() == 13 && golem.golemColor() == 6,
                "Home, XP and color restored");
        require(golem.getHealth() == 7, "Current health restored");
        require(ItemStack.matches(cargo(), golem.getMainHandItem()) && golem.getOffhandItem().isEmpty(),
                "Exact cargo count and components restored once");
        require(GolemSeals.get(level).contains(HOME.below(), Direction.UP), "Seal restored");
        require(GolemSeals.get(level).reservedTasks() == 0 && !golem.hasWorkTask(), "No stale work replayed");
        if (FILL_SCENARIO) {
            var pos = HOME.offset(3, -1, 0);
            require(GolemSeals.get(level).all().stream().anyMatch(seal -> seal.key().pos().equals(pos)
                    && seal.type() == GolemSeals.Type.FILL && seal.owner().equals(OWNER_ID)), "Fill type and owner restored");
            var handler = dev.ehtid.thaumcraft.golem.inventory.GolemContainers.handler(level,
                    new GolemSeals.SealKey(pos, Direction.UP));
            require(handler != null && ItemStack.matches(handler.getStackInSlot(0), new ItemStack(Items.GOLD_INGOT, 11)),
                    "Destination contents restored once");
        }
        if (FILTER_SCENARIO) {
            var data = GolemSeals.get(level);
            var pickup = data.get(new GolemSeals.SealKey(HOME.below(), Direction.UP)).filter();
            var fill = data.get(new GolemSeals.SealKey(HOME.offset(3, -1, 0), Direction.UP)).filter();
            require(pickup.blacklist() && !pickup.accepts(cargo()) && pickup.item().getCount() == 1, "Blacklist and ghost restored");
            require(!fill.blacklist() && fill.limit() == 70000 && fill.accepts(cargo())
                    && ItemStack.isSameItemSameComponents(fill.item(), cargo()), "Whitelist, components and integer limit restored");
        }
        if (CONTROLS_SCENARIO) {
            var data = GolemSeals.get(level);
            var pickup = data.get(new GolemSeals.SealKey(HOME.below(), Direction.UP));
            var fill = data.get(new GolemSeals.SealKey(HOME.offset(3,-1,0), Direction.UP));
            require(pickup.priority()==-5 && pickup.color()==16 && pickup.locked() && pickup.redstone(), "Pickup controls restored");
            require(fill.priority()==5 && fill.color()==6 && fill.locked() && !fill.redstone(), "Fill controls restored");
        }
        if (BELL_SCENARIO) require(golem.isFollowingOwner(), "Original following FLAGS bit restored");
        if (AREA_SCENARIO) {
            var pickup = GolemSeals.get(level).get(new GolemSeals.SealKey(HOME.below(), Direction.UP));
            require(pickup.area().equals(new BlockPos(2, 3, 4)), "Configured asymmetric area restored from disk");
            var bounds = GolemSeals.bounds(pickup);
            require(bounds.minX == HOME.getX()-1 && bounds.maxX == HOME.getX()+2
                    && bounds.minY == HOME.getY() && bounds.maxY == HOME.getY()+3
                    && bounds.minZ == HOME.getZ()-3 && bounds.maxZ == HOME.getZ()+4, "Restored scan uses configured dimensions");
        }
        if (EMPTY_SCENARIO) {
            var data = GolemSeals.get(level);
            var key = new GolemSeals.SealKey(HOME.offset(-3, -1, 0), Direction.UP);
            var seal = data.get(key);
            require(seal != null && seal.type() == GolemSeals.Type.EMPTY && seal.owner().equals(OWNER_ID)
                    && seal.priority() == 1 && seal.color() == 6 && seal.locked() && !seal.redstone(), "Typed Empty and controls restored");
            var source = dev.ehtid.thaumcraft.golem.inventory.GolemContainers.handler(level, key);
            require(source != null && ItemStack.matches(source.getStackInSlot(0), new ItemStack(Items.EMERALD, 23)), "Source stock restored exactly once");
            require(ItemStack.matches(golem.transferRecovery(), new ItemStack(Items.DIAMOND, 3)), "Actual recovery payload restored beside original cargo37");
            if (LEAVE_SCENARIO) require(seal.leaveOne(), "Original pleave toggle restored from disk");
            if (NBT_SCENARIO) require(!seal.filter().matchNbt() && !seal.filter().blacklist()
                    && seal.filter().accepts(source.getStackInSlot(0))
                    && !ItemStack.isSameItemSameComponents(seal.filter().item(), source.getStackInSlot(0)), "Restored loose pnbt accepts actual source with distinct components");
        }
        int workers = 0;
        for (var entity : level.getAllEntities()) if (entity instanceof ThaumcraftGolem) workers++;
        require(workers == 1, "Exactly one restored worker");
        require(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                golem.getBoundingBox().inflate(8)).isEmpty(), "Cargo not duplicated on ground");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        if (active == event.getServer()) active = null;
    }
}
