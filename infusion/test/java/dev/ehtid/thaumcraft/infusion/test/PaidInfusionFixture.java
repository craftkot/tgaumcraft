package dev.ehtid.thaumcraft.infusion.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.blockentity.InfusionMatrixBlockEntity;
import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.List;
import java.util.UUID;

/** Test-only physical fixture. Research is explicitly seeded, never claimed as earned progression. */
final class PaidInfusionFixture {
    final ServerLevel level;
    final BlockPos matrixPos;
    final String aspect;
    final List<BlockPos> jars;
    final List<BlockPos> pedestals;
    ServerPlayer actor;
    UUID ritual;
    int lastPaid;
    int completionTick = -1;

    PaidInfusionFixture(ServerLevel level, BlockPos matrixPos, String aspect) {
        this.level = level; this.matrixPos = matrixPos; this.aspect = aspect;
        pedestals = List.of(matrixPos.below(2), matrixPos.offset(-3,-2,0), matrixPos.offset(3,-2,0));
        jars = List.of(matrixPos.offset(-4,-2,-2), matrixPos.offset(0,-2,-4), matrixPos.offset(4,-2,-2));
    }
    InfusionMatrixBlockEntity matrix() { return (InfusionMatrixBlockEntity) level.getBlockEntity(matrixPos); }
    PedestalBlockEntity pedestal(int i) { return (PedestalBlockEntity) level.getBlockEntity(pedestals.get(i)); }
    EssentiaJarBlockEntity jar(int i) { return (EssentiaJarBlockEntity) level.getBlockEntity(jars.get(i)); }
    CompoundTag tag(net.minecraft.world.level.block.entity.BlockEntity tile) { return tile.saveWithFullMetadata(level.registryAccess()); }
    CompoundTag ledger() { return tag(matrix()).getCompound("PortInfusionLedger"); }

    void constructAndStart() {
        require(level.getBlockEntity(matrixPos) == null, "Fresh isolated test fixture");
        level.setBlockAndUpdate(matrixPos, ThaumcraftBlocks.INFUSION_MATRIX.get().defaultBlockState());
        for (BlockPos p : pedestals) level.setBlockAndUpdate(p, ThaumcraftBlocks.PEDESTAL_ARCANE.get().defaultBlockState());
        for (int x : new int[]{-1,1}) for (int z : new int[]{-1,1})
            level.setBlockAndUpdate(matrixPos.offset(x,-2,z), ThaumcraftBlocks.PILLAR_ARCANE.get().defaultBlockState());
        // Physical source stabilizers keep paid-path tests deterministic with real instability enabled.
        // Six opposite skull pairs approach .329 refill/cycle; vitium starts at 0, so let real idle
        // ticks below precharge the matrix before the researched caster click.
        for (int x = 2; x <= 7; x++) {
            level.setBlockAndUpdate(matrixPos.offset(x,-3,3), net.minecraft.world.level.block.Blocks.SKELETON_SKULL.defaultBlockState());
            level.setBlockAndUpdate(matrixPos.offset(-x,-3,-3), net.minecraft.world.level.block.Blocks.SKELETON_SKULL.defaultBlockState());
        }
        pedestal(0).insertFromPlayer(CrystalEssenceItem.ofAspect(aspect));
        pedestal(1).insertFromPlayer(new ItemStack(Items.WHEAT_SEEDS));
        pedestal(2).insertFromPlayer(new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get()));
        String[] tags = {aspect,"vitreus","vinculum"}; int[] quantities = {10,10,5};
        for (int i=0;i<3;i++) {
            level.setBlockAndUpdate(jars.get(i), ThaumcraftBlocks.JAR_NORMAL.get().defaultBlockState());
            require(jar(i).addFromTransport(tags[i],quantities[i],Direction.UP)==quantities[i], "Actual jar fixture receives exact source essentia");
        }
        actor = FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"InfusionPaidTest"));
        actor.setGameMode(GameType.SURVIVAL); actor.getInventory().clearContent();
        actor.moveTo(Vec3.atBottomCenterOf(matrixPos.north()));
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ThaumcraftItems.CASTER_BASIC.get()));
        casterClick();
        require(matrix().active() && !matrix().crafting(), "First actual Basic Caster click activates only");
        casterClick();
        require(!matrix().crafting() && totalEssentia()==25, "Missing CRYSTALFARMER blocks actual caster start and debits nothing");
        var knowledge = actor.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        knowledge.addResearch("CRYSTALFARMER"); // Explicit prerequisite fixture; not a research-progression acceptance claim.
        LogUtils.getLogger().info("INFUSION_TEST_RESEARCH_FIXTURE aspect={} key=CRYSTALFARMER seeded=true",aspect);
        // Drive actual idle server logic, with no seeded stability NBT or disabled hazards.
        for (int idle = 0; idle < 400; idle++)
            InfusionMatrixBlockEntity.serverTick(level, matrixPos, matrix().getBlockState(), matrix());
        snapshot("BEFORE_START"); casterClick();
        require(matrix().crafting(), "Researched actual Basic Caster click starts physical recipe");
        CompoundTag start = ledger(); ritual = start.getUUID("RitualId");
        CompoundTag initial = start.getCompound("Initial");
        require(initial.getInt(aspect)==10 && initial.getInt("vitreus")==10 && initial.getInt("vinculum")==5
                && initial.getAllKeys().size()==3 && start.getInt("Paid")==0
                && Float.compare(start.getFloat("RecipeCostMult"),1.0F)==0, "Exact source cost25 frozen at multiplier1");
        require(tag(matrix()).getInt("recipeinst")== (aspect.equals("vitium")?4:0), "Original recipe instability retained");
        snapshot("STARTED");
    }
    void casterClick() {
        require(actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(matrixPos),Direction.NORTH,matrixPos,false)).consumesAction(),
                "Actual server useItemOn routes through BasicCasterItem.onItemUseFirst");
    }
    int totalEssentia() { return jars.stream().mapToInt(p -> ((EssentiaJarBlockEntity)level.getBlockEntity(p)).snapshot().amount()).sum(); }

    void checkLivePayment() {
        if (!matrix().crafting()) return;
        CompoundTag pending = ledger(); int paid = pending.getInt("Paid");
        require(ritual.equals(pending.getUUID("RitualId")), "Same physical ritual throughout");
        require(paid>=lastPaid && paid-lastPaid<=1, "Native server tick pays at most one actual unit");
        require(paid + totalEssentia()==25, "Every live ledger debit equals actual jar decrease");
        CompoundTag remaining = pending.getCompound("Remaining");
        String[] tags = {aspect,"vitreus","vinculum"};
        for(int i=0;i<3;i++) require(remaining.getInt(tags[i])==jar(i).snapshot().amount(), "Per-aspect physical jar balance equals remaining source cost");
        lastPaid=paid;
    }
    void verifyFinished() {
        require(!matrix().crafting() && totalEssentia()==0, "Native ritual finished after exactly25 actual jar units");
        var output = pedestal(0).displayedItem();
        require(output.getCount()==1 && output.is(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("thaumcraft","crystal_"+aspect))), "Exactly one registered original crystal output");
        require(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(matrixPos).inflate(8)).stream()
                .noneMatch(e -> e.getItem().is(output.getItem())), "No additional world output copies");
        require(actor.getInventory().items.stream().noneMatch(s -> s.is(output.getItem())), "No additional output copied to caster inventory");
        require(ritual.equals(tag(pedestal(0)).getUUID("PortInfusionOutputRitual")), "Center output carries original ritual UUID");
        for(int i=1;i<=2;i++) {
            CompoundTag receipt=tag(pedestal(i));
            require(!pedestal(i).isOccupied() && ritual.equals(receipt.getUUID("PortInfusionRemovalRitual"))
                    && receipt.getInt("PortInfusionRemovalBit")==1<<(i-1), "Both exact side stacks removed once with original UUID receipts");
        }
        require(!tag(matrix()).contains("PortInfusionLedger"), "Completed ledger cleared");
    }
    void snapshot(String phase) {
        LogUtils.getLogger().info("INFUSION_TEST_SNAPSHOT aspect={} phase={} matrix={}",aspect,phase,tag(matrix()));
        for(int i=0;i<3;i++) {
            LogUtils.getLogger().info("INFUSION_TEST_SNAPSHOT aspect={} phase={} pedestal{}={}",aspect,phase,i,tag(pedestal(i)));
            LogUtils.getLogger().info("INFUSION_TEST_SNAPSHOT aspect={} phase={} jar{}={}",aspect,phase,i,tag(jar(i)));
        }
    }
    static void require(boolean ok,String message) { if(!ok) throw new IllegalStateException(message); }
}
