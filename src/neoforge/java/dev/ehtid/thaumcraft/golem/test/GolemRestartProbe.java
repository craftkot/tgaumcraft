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
    private static final UUID GOLEM_ID = UUID.fromString("671c5d1c-8533-490b-9ac2-e06c973d0d10");
    private static final UUID OWNER_ID = UUID.fromString("712b8f88-92c3-4bee-a3d8-53e8248ac89d");
    private static final BlockPos HOME = new BlockPos(8, 120, 8);
    private static MinecraftServer active;
    private static int ticks;
    private static boolean checked;

    private GolemRestartProbe() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!PHASE.equals("write") && !PHASE.equals("read")) return;
        active = event.getServer();
        ticks = 0;
        checked = false;
        active.overworld().setChunkForced(0, 0, true); // Test fixture only; production seals never force-load.
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (active != event.getServer() || checked || ++ticks < 60) return;
        checked = true;
        try {
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
        require(GolemSeals.get(level).pendingTasks() == 0 && !golem.hasWorkTask(), "No stale work replayed");
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
