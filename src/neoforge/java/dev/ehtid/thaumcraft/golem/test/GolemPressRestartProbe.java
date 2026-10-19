package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.press.GolemPressBlockEntity;
import dev.ehtid.thaumcraft.item.GolemPlacerItem;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;

/** Opt-in actual disk save and separate-process resume of both paid phases. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID)
public final class GolemPressRestartProbe {
    private static final String PHASE = System.getProperty("thaumcraft.golemRestartPhase", "");
    private static final boolean ENABLED = "press".equals(System.getProperty("thaumcraft.golemRestartScenario", ""));
    private static final BlockPos FIRST = new BlockPos(8, 120, 8), SECOND = new BlockPos(12, 120, 8);
    private static MinecraftServer active;
    private static int ticks;
    private static boolean checked;
    private GolemPressRestartProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!ENABLED || !PHASE.equals("write") && !PHASE.equals("read")) return;
        active = event.getServer(); ticks = 0; checked = false;
        active.overworld().setChunkForced(0, 0, true); // Isolated fixture only.
        active.overworld().getChunk(0, 0);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (active != event.getServer() || checked || ++ticks < 60) return;
        var level = active.overworld();
        boolean ready = level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(0, 0)) && level.isPositionEntityTicking(FIRST);
        if (!ready && ticks < 600) return;
        checked = true;
        try {
            require(ready, "Fixture chunk is ticking");
            if (PHASE.equals("write")) write(); else read();
            LogUtils.getLogger().info("GOLEM_PRESS_RESTART_{}_ASSERTIONS_OK", PHASE.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException | AssertionError error) {
            LogUtils.getLogger().error("GOLEM_PRESS_RESTART_ASSERTIONS_FAILED", error);
        } finally { active.halt(false); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { if (event.getServer() == active) active = null; }

    private static void write() {
        var level = active.overworld();
        require(!level.getBlockState(FIRST).is(GolemModule.PRESS.get()), "Write requires a fresh fixture world");
        for (BlockPos pos : new BlockPos[]{FIRST, SECOND}) {
            level.setBlockAndUpdate(pos, GolemModule.PRESS.get().defaultBlockState());
            level.setBlockAndUpdate(pos.below(), ThaumcraftBlocks.JAR_NORMAL.get().defaultBlockState());
            level.setBlockAndUpdate(pos.north(), Blocks.BARREL.defaultBlockState());
            var ingredients = (Container) level.getBlockEntity(pos.north());
            GolemPressGameTests.ingredients(ingredients, 1);
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PressRestart"));
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.moveTo(Vec3.atBottomCenterOf(pos.east()));
            var press = press(pos); var jar = jar(pos);
            for (int unit = 0; unit < 10; unit++) require(jar.addFromTransport("machina", 1, Direction.UP) == 1, "Fixture source credit");
            require(press.start(player) && ingredients.isEmpty(), "Exactly one paid component batch removed");
            GolemPressGameTests.ingredients(ingredients, 1); // Untouched second batch proves reload cannot pay/start again.
            if (pos.equals(FIRST)) {
                for (int tick = 0; tick < 15; tick++) GolemPressBlockEntity.tick(level, pos, press.getBlockState(), press);
                require(press.job().remaining() == 5 && jar.snapshot().amount() == 7, "First three actual source debits");
                require(jar.takeTransport("machina", 1, Direction.UP) == 1
                        && press.addTransport("machina", 1, Direction.DOWN) == 1, "Persist a fourth paid unit in buffer");
            }
            press.setItem(0, blocker());
            if (pos.equals(SECOND)) {
                for (int tick = 0; tick < 40; tick++) GolemPressBlockEntity.tick(level, pos, press.getBlockState(), press);
                require(press.job().ready() && jar.snapshot().amount() == 2, "Second job fully paid but blocked before save");
            }
        }
        require(press(FIRST).job().buffered() && press(FIRST).job().remaining() == 5 && jar(FIRST).snapshot().amount() == 6,
                "Mid-craft saved state includes one actual paid buffer");
    }
    private static void read() {
        var level = active.overworld();
        for (BlockPos pos : new BlockPos[]{FIRST, SECOND}) {
            var press = press(pos); var jar = jar(pos);
            require(press.job().ready() && press.job().props() == 0 && press.job().maximum() == 8, "Both restored jobs finish or remain paid and blocked");
            require(jar.snapshot().amount() == 2 && "machina".equals(jar.snapshot().aspect()), "Buffer survived restart: exact total eight Machina debited");
            require(ItemStack.isSameItemSameComponents(blocker(), press.getItem(0)) && press.getItem(0).getCount() == 2,
                    "Actual output stack and components survive disk restart");
            var inputs = (Container) level.getBlockEntity(pos.north());
            require(inputs.getItem(0).getCount() == 3 && inputs.getItem(1).getCount() == 2 && inputs.getItem(2).getCount() == 1,
                    "Untouched second batch is not consumed on resume");
            press.removeItem(0, 2);
            for (int tick = 0; tick < 5; tick++) GolemPressBlockEntity.tick(level, pos, press.getBlockState(), press);
            require(!press.job().active() && press.getItem(0).getCount() == 1
                    && ItemStack.isSameItemSameComponents(press.getItem(0), GolemPlacerItem.stack(GolemDesign.defaultWood(), 0)),
                    "Exactly one product per paid restored job");
            for (int tick = 0; tick < 10; tick++) GolemPressBlockEntity.tick(level, pos, press.getBlockState(), press);
            require(press.getItem(0).getCount() == 1 && jar.snapshot().amount() == 2, "No replay or additional debit");
        }
        require(level.getEntitiesOfClass(ItemEntity.class, new AABB(FIRST).inflate(8)).isEmpty(), "No duplicated ground output");
    }
    private static ItemStack blocker() {
        var stack = new ItemStack(Items.GOLD_INGOT, 2); stack.set(DataComponents.CUSTOM_NAME, Component.literal("M10 press restart blocker")); return stack;
    }
    private static GolemPressBlockEntity press(BlockPos pos) { return (GolemPressBlockEntity) active.overworld().getBlockEntity(pos); }
    private static EssentiaJarBlockEntity jar(BlockPos pos) { return (EssentiaJarBlockEntity) active.overworld().getBlockEntity(pos.below()); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
