package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Optional measured stress matrix; normal245 namespace is unchanged, no production navigation override. */
@GameTestHolder("thaumcraft_golem_stress") @PrefixGameTestTemplate(false)
public final class GolemFlightStressGameTests {
    private GolemFlightStressGameTests() {}
    private static void run(GameTestHelper h,int seed,boolean barrier) {
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        if(barrier)for(int y=1;y<=4;y++)for(int z=0;z<12;z++)h.setBlock(new BlockPos(5,y,z),Blocks.STONE);
        var owner=UUID.randomUUID();var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(7,5,5)),Direction.UP);var data=GolemSeals.get(h.getLevel());
        h.getLevel().setBlockAndUpdate(key.pos(),Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(key.pos().above(),Blocks.DIAMOND_ORE.defaultBlockState());
        h.assertTrue(data.place(key.pos(),key.face(),owner,GolemSeals.Type.BREAKER)&&data.configureControls(data.get(key),owner,0,0,true,false)&&data.configureArea(data.get(key),owner,new BlockPos(1,1,1)),"Actual owner-locked Breaker, no cross-fixture task");
        var g=GolemModule.GOLEM.get().create(h.getLevel());var start=h.absolutePos(new BlockPos(3,1,5));g.moveTo(Vec3.atBottomCenterOf(start));g.initialize(GolemDesign.fromIds(0,1,3,3,0,0),owner,start,0);g.getRandom().setSeed(seed);h.assertTrue(h.getLevel().addFreshEntity(g),"Actual enabled source flyer/Breaker");
        var ticks=new AtomicInteger();h.onEachTick(()->{int t=ticks.incrementAndGet();h.assertTrue(!g.noPhysics&&g.isNoGravity(),"Native collision/gravity rules stay active");if(t==350||t==700)LogUtils.getLogger().info("GOLEM_FLIGHT_MATRIX_PENDING seed={} barrier={} ticks={} position={} pathDone={} xp={}",seed,barrier,t,g.position(),g.getNavigation().isDone(),g.rankXp());});
        h.succeedWhen(()->{
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds(),e->e.getItem().is(Items.DIAMOND));
            h.assertTrue(h.getLevel().isEmptyBlock(key.pos().above())&&g.rankXp()==1&&drops.stream().mapToInt(e->e.getItem().getCount()).sum()==1,"Actual source air path/XYZ reach/native harvest barrier="+barrier+" seed="+seed+" ticks="+ticks.get()+" position="+g.position()+" pathDone="+g.getNavigation().isDone()+" motion="+g.getDeltaMovement());
            LogUtils.getLogger().info("GOLEM_FLIGHT_MATRIX_DONE seed={} barrier={} actualTicks={} diamond=1 xp=1",seed,barrier,ticks.get());data.remove(key.pos(),key.face(),owner);drops.forEach(ItemEntity::discard);g.discard();
        });
    }
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed0(GameTestHelper h){run(h,0,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed1(GameTestHelper h){run(h,1,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed2(GameTestHelper h){run(h,2,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed3(GameTestHelper h){run(h,3,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed4(GameTestHelper h){run(h,4,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed5(GameTestHelper h){run(h,5,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed6(GameTestHelper h){run(h,6,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed7(GameTestHelper h){run(h,7,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed8(GameTestHelper h){run(h,8,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed9(GameTestHelper h){run(h,9,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed10(GameTestHelper h){run(h,10,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed11(GameTestHelper h){run(h,11,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed12(GameTestHelper h){run(h,12,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed13(GameTestHelper h){run(h,13,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed14(GameTestHelper h){run(h,14,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void seed15(GameTestHelper h){run(h,15,false);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed0(GameTestHelper h){run(h,0,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed1(GameTestHelper h){run(h,1,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed2(GameTestHelper h){run(h,2,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed3(GameTestHelper h){run(h,3,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed4(GameTestHelper h){run(h,4,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed5(GameTestHelper h){run(h,5,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed6(GameTestHelper h){run(h,6,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed7(GameTestHelper h){run(h,7,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed8(GameTestHelper h){run(h,8,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed9(GameTestHelper h){run(h,9,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed10(GameTestHelper h){run(h,10,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed11(GameTestHelper h){run(h,11,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed12(GameTestHelper h){run(h,12,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed13(GameTestHelper h){run(h,13,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed14(GameTestHelper h){run(h,14,true);}
    @GameTest(template="golem_flight_stress",batch="flight_seed_matrix",timeoutTicks=1100)
    public static void wallSeed15(GameTestHelper h){run(h,15,true);}

}
