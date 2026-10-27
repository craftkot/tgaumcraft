package dev.ehtid.thaumcraft.infusion.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.blockentity.InfusionMatrixBlockEntity;
import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Combined golem/infusion acceptance only; research is explicitly seeded test setup. */
@GameTestHolder("thaumcraft_golem_infusion")
@PrefixGameTestTemplate(false)
public final class GolemInfusionGameTests {
    private GolemInfusionGameTests() {}

    // Independent BETA26 expectations, never derived from the module descriptor being tested.
    private record Cost(String aspect, int amount) {}
    private record RecipeCase(String legacyId, String research, String center, List<String> components,
                              String output, List<Cost> costs, int totalCost, int instability) {}

    @GameTest(template="golem_infusion_blank", batch="golem_infusion_paid", timeoutTicks=1400)
    public static void sealHarvestPaysOriginalRecipeThroughActualCaster(GameTestHelper h) {
        paid(h, new RecipeCase("thaumcraft:SealHarvest", "SEALHARVEST", "thaumcraft:seal_blank",
                List.of("minecraft:wheat_seeds", "minecraft:pumpkin_seeds", "minecraft:melon_seeds",
                        "minecraft:beetroot_seeds", "minecraft:sugar_cane", "minecraft:cactus"),
                "thaumcraft:seal_harvest", List.of(new Cost("herba",10), new Cost("sensus",10),
                        new Cost("humanus",10)), 30, 0));
    }

    @GameTest(template="golem_infusion_blank", batch="golem_infusion_paid", timeoutTicks=1400)
    public static void sealButcherPaysOriginalRecipeThroughActualCaster(GameTestHelper h) {
        paid(h, new RecipeCase("thaumcraft:SealButcher", "SEALBUTCHER", "thaumcraft:seal_guard",
                List.of("minecraft:leather", "minecraft:white_wool", "minecraft:rabbit_hide",
                        "minecraft:porkchop", "minecraft:mutton", "minecraft:beef"),
                "thaumcraft:seal_butcher", List.of(new Cost("bestia",10), new Cost("sensus",10),
                        new Cost("humanus",10)), 30, 0));
    }

    @GameTest(template="golem_infusion_blank", batch="golem_infusion_paid", timeoutTicks=1400)
    public static void sealBreakPaysOriginalRecipeThroughActualCaster(GameTestHelper h) {
        paid(h, new RecipeCase("thaumcraft:SealBreak", "SEALBREAK", "thaumcraft:seal_blank",
                List.of("minecraft:golden_axe", "minecraft:golden_pickaxe", "minecraft:golden_shovel"),
                "thaumcraft:seal_breaker", List.of(new Cost("instrumentum",10), new Cost("perditio",10),
                        new Cost("humanus",10)), 30, 1));
    }

    @GameTest(template="golem_infusion_blank", batch="golem_infusion_paid", timeoutTicks=1400)
    public static void mindBiothaumicPaysOriginalRecipeThroughActualCaster(GameTestHelper h) {
        paid(h, new RecipeCase("thaumcraft:MindBiothaumic", "MINDBIOTHAUMIC", "thaumcraft:mind",
                List.of("thaumcraft:brain", "thaumcraft:mechanism_complex"),
                "thaumcraft:mind_biothaumic", List.of(new Cost("cognitio",50), new Cost("machina",25)),
                75, 4));
    }

    private static void paid(GameTestHelper h, RecipeCase recipe) {
        var f = new Fixture(h.getLevel(), h.absolutePos(new BlockPos(12,8,12)), recipe);
        f.constructAndStart();
        int[] tick = {0};
        int[] completionTick = {-1};
        ItemStack[] published = {ItemStack.EMPTY};
        // Observe normal world BlockEntity ticks; never call matrix/server tick or debit methods here.
        h.onEachTick(() -> {
            tick[0]++;
            f.checkLivePayment();
            if (f.matrix().crafting()) return;
            f.verifyFinished();
            if (completionTick[0] < 0) {
                completionTick[0] = tick[0];
                published[0] = f.center().displayedItem().copy();
                f.snapshot("FINISHED");
                f.casterClick();
                require(!f.matrix().crafting(), "Repeated actual caster use cannot replay consumed recipe");
            }
            require(ItemStack.matches(published[0], f.center().displayedItem()),
                    "Exactly the published output remains through native replay ticks");
            if (tick[0] - completionTick[0] >= 120) {
                f.snapshot("NO_REPLAY_120_TICKS");
                f.actor.getInventory().clearContent();
                LogUtils.getLogger().info("GOLEM_INFUSION_NATIVE_ASSERTIONS_OK recipe={} paid={} output=1 replayTicks=120 research=explicit_fixture",
                        recipe.legacyId(), recipe.totalCost());
                h.succeed();
            }
        });
    }

    private static final class Fixture {
        private static final int UNRELATED_AER = 7;
        final ServerLevel level;
        final BlockPos matrixPos;
        final RecipeCase recipe;
        final List<BlockPos> sides;
        final List<BlockPos> jars;
        ServerPlayer actor;
        UUID ritual;
        int lastPaid;

        Fixture(ServerLevel level, BlockPos matrixPos, RecipeCase recipe) {
            this.level = level;
            this.matrixPos = matrixPos;
            this.recipe = recipe;
            // Deliberately differs from source X/Z pedestal scan order.
            sides = List.of(matrixPos.offset(3,-2,0), matrixPos.offset(-3,-2,0),
                    matrixPos.offset(0,-2,3), matrixPos.offset(0,-2,-3),
                    matrixPos.offset(3,-2,3), matrixPos.offset(-3,-2,-3))
                    .subList(0, recipe.components().size());
            jars = List.of(matrixPos.offset(-4,-2,-2), matrixPos.offset(0,-2,-4),
                    matrixPos.offset(4,-2,-2), matrixPos.offset(4,-2,2))
                    .subList(0, recipe.costs().size()+1);
        }

        InfusionMatrixBlockEntity matrix() { return (InfusionMatrixBlockEntity)level.getBlockEntity(matrixPos); }
        PedestalBlockEntity center() { return (PedestalBlockEntity)level.getBlockEntity(matrixPos.below(2)); }
        PedestalBlockEntity side(int i) { return (PedestalBlockEntity)level.getBlockEntity(sides.get(i)); }
        EssentiaJarBlockEntity jar(int i) { return (EssentiaJarBlockEntity)level.getBlockEntity(jars.get(i)); }
        CompoundTag tag(BlockEntity tile) { return tile.saveWithFullMetadata(level.registryAccess()); }
        CompoundTag ledger() { return tag(matrix()).getCompound("PortInfusionLedger"); }

        void constructAndStart() {
            require(level.getBlockEntity(matrixPos) == null, "Fresh isolated physical fixture");
            require(recipe.costs().stream().mapToInt(Cost::amount).sum() == recipe.totalCost(),
                    "Independent expected aspect amounts sum to original total");
            registeredStack(recipe.output()); // Fail clearly if this combined content module is absent.
            level.setBlockAndUpdate(matrixPos, ThaumcraftBlocks.INFUSION_MATRIX.get().defaultBlockState());
            level.setBlockAndUpdate(matrixPos.below(2), ThaumcraftBlocks.PEDESTAL_ARCANE.get().defaultBlockState());
            for (BlockPos p : sides)
                level.setBlockAndUpdate(p, ThaumcraftBlocks.PEDESTAL_ARCANE.get().defaultBlockState());
            for (int x : new int[]{-1,1}) for (int z : new int[]{-1,1})
                level.setBlockAndUpdate(matrixPos.offset(x,-2,z), ThaumcraftBlocks.PILLAR_ARCANE.get().defaultBlockState());
            require(center().insertFromPlayer(registeredStack(recipe.center())), "Real original center inserted");
            for (int i=0; i<sides.size(); i++)
                require(side(i).insertFromPlayer(registeredStack(recipe.components().get(i))), "Real original side component inserted");
            for (int i=0; i<jars.size(); i++) {
                level.setBlockAndUpdate(jars.get(i), ThaumcraftBlocks.JAR_NORMAL.get().defaultBlockState());
                String aspect = i<recipe.costs().size() ? recipe.costs().get(i).aspect() : "aer";
                int amount = i<recipe.costs().size() ? recipe.costs().get(i).amount() : UNRELATED_AER;
                require(jar(i).addFromTransport(aspect,amount,Direction.UP) == amount,
                        "Real jar receives the exact physical essentia fixture");
            }
            actor = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GolemInfusionTest"));
            actor.setGameMode(GameType.SURVIVAL);
            actor.getInventory().clearContent();
            actor.moveTo(Vec3.atBottomCenterOf(matrixPos.north()));
            actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ThaumcraftItems.CASTER_BASIC.get()));
            var knowledge = actor.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
            require(!knowledge.isResearchKnown(recipe.research()), "Fresh actor lacks the recipe research");
            casterClick();
            require(matrix().active() && !matrix().crafting(), "First actual Basic Caster click activates only");
            casterClick();
            require(!matrix().crafting() && !tag(matrix()).contains("PortInfusionLedger")
                            && totalEssentia() == recipe.totalCost()+UNRELATED_AER,
                    "Missing recipe research denies actual caster start without ledger or essentia debit");
            require(ItemStack.matches(center().displayedItem(), registeredStack(recipe.center())),
                    "Denied start retains the exact center");
            for (int i=0; i<sides.size(); i++)
                require(ItemStack.matches(side(i).displayedItem(), registeredStack(recipe.components().get(i))),
                        "Denied start retains every exact side input");
            knowledge.addResearch(recipe.research()); // Explicit prerequisite fixture, never earned progression.
            LogUtils.getLogger().info("GOLEM_INFUSION_TEST_RESEARCH_FIXTURE recipe={} key={} seeded=true",
                    recipe.legacyId(), recipe.research());
            snapshot("BEFORE_START");
            casterClick();
            require(matrix().crafting(), "Researched actual Basic Caster use starts the physical recipe");
            CompoundTag start = ledger();
            require(start.hasUUID("RitualId"), "Started ritual has a persistent physical payment UUID");
            ritual = start.getUUID("RitualId");
            require(start.getInt("Paid") == 0 && start.getInt("ConsumedItems") == 0
                            && Float.compare(start.getFloat("RecipeCostMult"),1.0F) == 0,
                    "Start freezes original multiplier one and spends no resources");
            checkLedger(start);
            require(tag(matrix()).getInt("recipeinst") == recipe.instability(),
                    "Original recipe instability retained (not an instability-effects acceptance claim)");
            checkLivePayment();
            snapshot("STARTED");
        }

        void casterClick() {
            require(actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(matrixPos),Direction.NORTH,matrixPos,false)).consumesAction(),
                    "Actual server useItemOn routes through BasicCasterItem");
        }

        int totalEssentia() { return jars.stream().mapToInt(p -> ((EssentiaJarBlockEntity)level.getBlockEntity(p)).snapshot().amount()).sum(); }

        void checkLedger(CompoundTag pending) {
            require(recipe.legacyId().equals(pending.getString("Recipe"))
                            && ritual.equals(pending.getUUID("RitualId")),
                    "Original recipe identity and ritual UUID remain exact");
            CompoundTag initial = pending.getCompound("Initial");
            CompoundTag remaining = pending.getCompound("Remaining");
            Set<String> expectedKeys = recipe.costs().stream().map(Cost::aspect).collect(Collectors.toSet());
            require(initial.getAllKeys().equals(expectedKeys) && remaining.getAllKeys().equals(expectedKeys),
                    "Ledger contains exactly the independently expected aspect keys");
            int physicalPaid = 0;
            for (int i=0; i<recipe.costs().size(); i++) {
                Cost cost = recipe.costs().get(i);
                var physical = jar(i).snapshot();
                require(initial.getInt(cost.aspect()) == cost.amount(), "Frozen per-aspect original price is exact");
                require(physical.amount() >= 0 && physical.amount() <= cost.amount()
                                && (physical.amount() == 0 || cost.aspect().equals(physical.aspect()))
                                && remaining.getInt(cost.aspect()) == physical.amount(),
                        "Every per-aspect ledger balance equals the real source jar");
                physicalPaid += cost.amount()-physical.amount();
            }
            require(physicalPaid == pending.getInt("Paid")
                            && physicalPaid+totalEssentia() == recipe.totalCost()+UNRELATED_AER,
                    "Exact paid total is conserved against all actual jars");
            long[] tracked = pending.getLongArray("SidePedestals");
            require(tracked.length == sides.size(), "Every physical ingredient pedestal is captured");
            for (int i=0; i<sides.size(); i++)
                require(tracked[i] == sides.get(i).asLong(), "Receipt bits track original recipe component order");
            require(jar(recipe.costs().size()).snapshot().amount() == UNRELATED_AER
                            && "aer".equals(jar(recipe.costs().size()).snapshot().aspect()),
                    "Unrelated actual jar essentia remains untouched");
        }

        void checkLivePayment() {
            if (!matrix().crafting()) return;
            CompoundTag pending = ledger();
            checkLedger(pending);
            int paid = pending.getInt("Paid");
            require(paid >= lastPaid && paid-lastPaid <= 1, "One native server tick debits at most one real unit");
            int consumed = pending.getInt("ConsumedItems");
            require(consumed == 0 || paid == recipe.totalCost(), "No side component removed before complete payment");
            for (int i=0; i<sides.size(); i++) {
                if ((consumed & (1<<i)) != 0) verifySideReceipt(i);
                else require(ItemStack.matches(side(i).displayedItem(), registeredStack(recipe.components().get(i))),
                        "Unconsumed exact source component remains on its original pedestal");
            }
            require(ItemStack.matches(center().displayedItem(), registeredStack(recipe.center())),
                    "Original center remains until completed publication");
            lastPaid = paid;
        }

        void verifySideReceipt(int i) {
            CompoundTag receipt = tag(side(i));
            require(!side(i).isOccupied() && receipt.hasUUID("PortInfusionRemovalRitual")
                            && ritual.equals(receipt.getUUID("PortInfusionRemovalRitual"))
                            && receipt.getInt("PortInfusionRemovalBit") == (1<<i),
                    "Every exact side input is removed once with the original UUID and its own receipt bit");
        }

        void verifyFinished() {
            require(!matrix().crafting() && lastPaid == recipe.totalCost()
                            && totalEssentia() == UNRELATED_AER,
                    "Native ritual completes only after all original real costs were observed paid");
            for (int i=0; i<recipe.costs().size(); i++)
                require(jar(i).snapshot().amount() == 0 && jar(i).snapshot().aspect() == null,
                        "Every required source aspect is exhausted exactly");
            require(jar(recipe.costs().size()).snapshot().amount() == UNRELATED_AER
                            && "aer".equals(jar(recipe.costs().size()).snapshot().aspect()),
                    "Finished ritual preserves unrelated essentia");
            ItemStack output = registeredStack(recipe.output());
            require(ItemStack.matches(center().displayedItem(),output), "Exactly one registered original output appears");
            require(tag(center()).hasUUID("PortInfusionOutputRitual")
                            && ritual.equals(tag(center()).getUUID("PortInfusionOutputRitual")),
                    "Published output retains original ritual UUID");
            for (int i=0; i<sides.size(); i++) verifySideReceipt(i);
            require(!tag(matrix()).contains("PortInfusionLedger"), "Completed paid ledger is cleared");
            require(level.getEntitiesOfClass(ItemEntity.class,new AABB(matrixPos).inflate(8)).stream()
                            .noneMatch(e -> e.getItem().is(output.getItem())), "No extra world output copies");
            require(actor.getInventory().items.stream().noneMatch(s -> s.is(output.getItem())),
                    "No output copied into the caster inventory");
        }

        void snapshot(String phase) {
            LogUtils.getLogger().info("GOLEM_INFUSION_TEST_SNAPSHOT recipe={} phase={} matrix={}",recipe.legacyId(),phase,tag(matrix()));
            LogUtils.getLogger().info("GOLEM_INFUSION_TEST_SNAPSHOT recipe={} phase={} center={}",recipe.legacyId(),phase,tag(center()));
            for (int i=0; i<sides.size(); i++)
                LogUtils.getLogger().info("GOLEM_INFUSION_TEST_SNAPSHOT recipe={} phase={} side{}={}",recipe.legacyId(),phase,i,tag(side(i)));
            for (int i=0; i<jars.size(); i++)
                LogUtils.getLogger().info("GOLEM_INFUSION_TEST_SNAPSHOT recipe={} phase={} jar{}={}",recipe.legacyId(),phase,i,tag(jar(i)));
        }
    }

    private static ItemStack registeredStack(String itemId) {
        ResourceLocation id = ResourceLocation.parse(itemId);
        require(BuiltInRegistries.ITEM.containsKey(id), "Required combined-module item is registered: "+itemId);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        require(!stack.isEmpty(), "Required recipe item must never silently resolve to AIR: "+itemId);
        return stack;
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
}
