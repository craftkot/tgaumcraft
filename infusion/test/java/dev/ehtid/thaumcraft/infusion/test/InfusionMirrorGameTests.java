package dev.ehtid.thaumcraft.infusion.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.block.EssentiaMirrorBlock;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.blockentity.EssentiaMirrorBlockEntity;
import dev.ehtid.thaumcraft.crafting.InfusionJarSourceBridge;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Actual jars, linked block entities and the production matrix; no fabricated debit callbacks. */
@GameTestHolder("thaumcraft_infusion_mirror")
@PrefixGameTestTemplate(false)
public final class InfusionMirrorGameTests {
    private static final String TEMPLATE = "infusion_mirror_blank";

    @GameTest(template=TEMPLATE, batch="infusion_mirrors", timeoutTicks=40)
    public static void remoteFaceControlsSourceVolumeAndOnlyCallingMirrorAccumulatesInstability(GameTestHelper h) {
        BlockPos origin=h.absolutePos(new BlockPos(12,8,12)), remote=origin.south(24);
        for (Direction facing:Direction.values()) {
            var local=mirror(h,origin.east(),Direction.UP);
            var other=mirror(h,remote,facing);
            h.assertTrue(local.linkTo(h.getLevel().dimension().location().toString(),remote),"Real reciprocal mirror link");
            var behind=jar(h,remote.relative(facing.getOpposite(),2),"aer",9);
            var outside=jar(h,remote.relative(facing,8),"aer",7);
            var wrong=jar(h,remote.relative(facing,2),"ignis",5);
            var blocked=jar(h,remote.relative(facing,3),"aer",4); blocked.applyBrace();
            var eligible=jar(h,remote.relative(facing,7),"aer",2);
            var bridge=new InfusionJarSourceBridge();
            h.assertTrue(bridge.drainOne(h.getLevel(),origin,"aer",8,5),"Wireless matrix source drains through mirror facing "+facing);
            h.assertTrue(eligible.snapshot().amount()==1 && behind.snapshot().amount()==9
                    && outside.snapshot().amount()==7 && wrong.snapshot().amount()==5 && blocked.snapshot().amount()==4,
                    "Only an unblocked matching jar within front depth0..7 may pay");
            h.assertTrue(local.getUpdateTag(h.getLevel().registryAccess()).getInt("instability")==1
                    && other.getUpdateTag(h.getLevel().registryAccess()).getInt("instability")==0,
                    "BETA26 takeFromContainer calls addInstability(null,1) on caller only");
            h.assertTrue(bridge.drainOne(h.getLevel(),origin,"aer",8,5) && eligible.snapshot().amount()==0
                    && eligible.snapshot().aspect()==null,"Cached source clears final actual jar unit");
            h.assertTrue(!bridge.drainOne(h.getLevel(),origin,"aer",8,5)
                    && local.getUpdateTag(h.getLevel().registryAccess()).getInt("instability")==2,
                    "Exhausted source neither invents essence nor adds instability");
            for(var tile:List.of(behind,outside,wrong,blocked,eligible,local,other))
                h.getLevel().setBlockAndUpdate(tile.getBlockPos(),Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch="infusion_mirrors", timeoutTicks=40)
    public static void closerLocalJarPaysBeforeMirrorAndBrokenLinkNeverDebits(GameTestHelper h) {
        BlockPos origin=h.absolutePos(new BlockPos(12,8,12)), remote=origin.south(24);
        var local=mirror(h,origin.east(3),Direction.UP); var other=mirror(h,remote,Direction.UP);
        h.assertTrue(local.linkTo(h.getLevel().dimension().location().toString(),remote),"Actual linked pair");
        var near=jar(h,origin.west(),"aer",1); var far=jar(h,remote.above(2),"aer",3);
        var bridge=new InfusionJarSourceBridge();
        h.assertTrue(bridge.drainOne(h.getLevel(),origin,"aer",8,5) && near.snapshot().amount()==0
                && far.snapshot().amount()==3,"Unified source list retains original squared-distance order");
        h.assertTrue(bridge.drainOne(h.getLevel(),origin,"aer",8,5) && far.snapshot().amount()==2,
                "After local exhaustion cached mirror pays exactly one remote unit");
        h.getLevel().setBlockAndUpdate(other.getBlockPos(),Blocks.AIR.defaultBlockState());
        h.assertTrue(!bridge.drainOne(h.getLevel(),origin,"aer",8,5) && far.snapshot().amount()==2,
                "Cached mirror validates current reciprocal link before physical debit");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch="infusion_mirrors", timeoutTicks=40)
    public static void remoteMirrorCannotRelayToAnotherPair(GameTestHelper h) {
        BlockPos origin=h.absolutePos(new BlockPos(12,8,12)), remote=origin.south(24);
        var local=mirror(h,origin.east(),Direction.UP); mirror(h,remote,Direction.UP);
        h.assertTrue(local.linkTo(h.getLevel().dimension().location().toString(),remote),"First pair links");
        var relay=mirror(h,remote.above(2),Direction.UP); BlockPos third=origin.east(24).south(24);
        mirror(h,third,Direction.UP);
        h.assertTrue(relay.linkTo(h.getLevel().dimension().location().toString(),third),"Second pair links");
        var far=jar(h,third.above(2),"aer",6);
        h.assertTrue(!new InfusionJarSourceBridge().drainOne(h.getLevel(),origin,"aer",8,5)
                && far.snapshot().amount()==6,"Remote scan ignores all mirrors, even valid linked pairs");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch="infusion_mirrors", timeoutTicks=40)
    public static void unloadedPartnerIsNotForceLoaded(GameTestHelper h) {
        BlockPos origin=h.absolutePos(new BlockPos(12,8,12));
        var local=mirror(h,origin.east(),Direction.UP);
        BlockPos unloaded=new BlockPos(20_000_000,80,20_000_000);
        h.assertTrue(!h.getLevel().hasChunkAt(unloaded),"Fixture destination genuinely unloaded");
        h.assertTrue(local.linkTo(h.getLevel().dimension().location().toString(),unloaded),"Pending link is persisted");
        h.assertTrue(!new InfusionJarSourceBridge().drainOne(h.getLevel(),origin,"aer",8,5)
                && !h.getLevel().hasChunkAt(unloaded),"Wireless scan never force-loads a distant partner chunk");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch="infusion_mirrors_paid", timeoutTicks=700)
    public static void realRitualPaysRemoteJarsResumesCheckpointAndPublishesOnlyOnce(GameTestHelper h) {
        var f=new PaidInfusionFixture(h.getLevel(),h.absolutePos(new BlockPos(12,8,12)),"aer");
        f.constructAndStart(); BlockPos remote=f.matrixPos.south(24);
        var local=mirror(h,f.matrixPos.east(2),Direction.UP); var other=mirror(h,remote,Direction.UP);
        h.assertTrue(local.linkTo(h.getLevel().dimension().location().toString(),remote),"Paid ritual linked pair");
        List<EssentiaJarBlockEntity> jars=new ArrayList<>();
        for(int i=0;i<3;i++) {
            var before=f.jar(i).snapshot();
            h.assertTrue(f.jar(i).takeForInfusion(before.aspect(),before.amount()),"Move existing fixture essence without creating it");
            jars.add(jar(h,remote.offset(i-1,2,0),before.aspect(),before.amount()));
        }
        int[] ticks={0},finished={-1}; boolean[] reloaded={false}; ItemStack[] output={ItemStack.EMPTY};
        h.onEachTick(() -> {
            ticks[0]++;
            int actualRemaining=jars.stream().mapToInt(j->j.snapshot().amount()).sum();
            if(f.matrix().crafting()) {
                int paid=f.ledger().getInt("Paid");
                h.assertTrue(paid==25-actualRemaining && f.totalEssentia()==0,"Matrix ledger equals actual remote jar debits only");
                if(!reloaded[0] && paid>=8) {
                    // Real independently saved BE snapshots, not a synthetic progress payload.
                    for(var tile:List.of(local,other)) tile.loadWithComponents(f.tag(tile),h.getLevel().registryAccess());
                    for(var jar:jars) jar.loadWithComponents(f.tag(jar),h.getLevel().registryAccess());
                    f.matrix().loadWithComponents(f.tag(f.matrix()),h.getLevel().registryAccess());
                    h.assertTrue(f.matrix().crafting() && f.ledger().getInt("Paid")==paid,"World checkpoint retains paid remote progress");
                    reloaded[0]=true;
                }
                return;
            }
            h.assertTrue(reloaded[0] && actualRemaining==0,"All25 remote units paid and checkpoint exercised");
            f.verifyFinished();
            if(finished[0]<0) { finished[0]=ticks[0]; output[0]=f.pedestal(0).displayedItem().copy(); f.casterClick(); }
            h.assertTrue(ItemStack.matches(output[0],f.pedestal(0).displayedItem()),"Repeat caster click cannot duplicate output");
            if(ticks[0]-finished[0]>=120) {
                f.actor.getInventory().clearContent();
                LogUtils.getLogger().info("INFUSION_MIRROR_PAID_NATIVE_OK remotePaid=25 output=1 checkpoint=true replayTicks=120 research=explicit_fixture");
                h.succeed();
            }
        });
    }

    private static EssentiaMirrorBlockEntity mirror(GameTestHelper h,BlockPos pos,Direction face) {
        h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos,ThaumcraftBlocks.MIRROR_ESSENTIA.get().defaultBlockState().setValue(EssentiaMirrorBlock.FACING,face));
        return (EssentiaMirrorBlockEntity)h.getLevel().getBlockEntity(pos);
    }
    private static EssentiaJarBlockEntity jar(GameTestHelper h,BlockPos pos,String aspect,int amount) {
        h.getLevel().setBlockAndUpdate(pos,ThaumcraftBlocks.JAR_NORMAL.get().defaultBlockState());
        var jar=(EssentiaJarBlockEntity)h.getLevel().getBlockEntity(pos);
        h.assertTrue(jar.addFromTransport(aspect,amount,Direction.UP)==amount,"Exact physical jar fixture fill");
        return jar;
    }
}
