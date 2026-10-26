package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemCombatProducerGameTests {
    private GolemCombatProducerGameTests() {}

    @GameTest(template="golem_test", batch="combat_producer_pause", timeoutTicks=480)
    public static void butcherCounterContinuesWhileReservedAndPausesForPoweredTicks(GameTestHelper h) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var level=h.getLevel(); var data=GolemSeals.get(level); var owner=UUID.randomUUID();
        var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(6,0,5)),Direction.UP);
        h.assertTrue(data.place(key.pos(),key.face(),owner,GolemSeals.Type.BUTCHER),"Place actual Butcher");
        h.assertTrue(data.configureControls(data.get(key),owner,0,0,true,true),"Owner locks the redstone-sensitive seal");
        ThaumcraftGolem golem=GolemModule.GOLEM.get().create(level);
        golem.initialize(GolemDesign.fromIds(3,1,2,0,0,0),owner,key.pos(),0);
        golem.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,1,5)))); golem.setNoAi(true);
        h.assertTrue(level.addFreshEntity(golem),"Actual SMART FIGHTER receives tasks without killing the population");
        Cow[] cows=new Cow[3];
        for(int i=0;i<cows.length;i++) {
            cows[i]=EntityType.COW.create(level); cows[i].setNoAi(true);
            cows[i].moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5+i,1,4+i))));
            h.assertTrue(level.addFreshEntity(cows[i]),"Actual eligible adult cow");
        }
        var first=new AtomicReference<GolemSeals.Assignment>(); var firstTick=new AtomicLong(-1);
        Runnable cleanup=()->{
            level.setBlockAndUpdate(key.pos().east(),Blocks.AIR.defaultBlockState());
            for(var cow:cows) cow.discard();
            data.remove(key.pos(),key.face(),owner); golem.discard();
        };
        h.onEachTick(()->{
            if(first.get()==null) {
                var available=data.claimEntity(golem,e->true);
                if(available.isEmpty()) return;
                first.set(available.orElseThrow()); firstTick.set(level.getGameTime());
                return;
            }
            long elapsed=level.getGameTime()-firstTick.get();
            if(elapsed==30) {
                h.assertTrue(data.startCombat(level,golem,first.get()) && golem.rankXp()==1,
                        "Reserved work starts once after thirty active ticks and grants one task XP");
                level.setBlockAndUpdate(key.pos().east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
                h.assertTrue(GolemSeals.stopped(level,data.get(key)),"Actual redstone pauses this seal");
            }
            if(elapsed==70) {
                level.setBlockAndUpdate(key.pos().east(),Blocks.AIR.defaultBlockState());
                h.assertTrue(!GolemSeals.stopped(level,data.get(key)),"Forty powered ticks end without changing the seal");
            }
            if(elapsed<70) return;
            var available=data.claimEntity(golem,e->true);
            if(available.isEmpty()) {
                if(elapsed>240) {
                    cleanup.run();
                    h.assertTrue(false,"Butcher must scan after exactly two hundred active ticks; outstanding work must not freeze its counter");
                }
                return;
            }
            try {
                h.assertTrue(elapsed==240,"Second Butcher scan must count200 active ticks plus40 powered ticks; actual elapsed="+elapsed);
                h.assertTrue(data.startCombat(level,golem,available.orElseThrow()) && golem.rankXp()==2,
                        "The resumed real task grants exactly one further assignment XP");
                for(var cow:cows) h.assertTrue(cow.isAlive(),"Both assignments preserve all three eligible adults until actual combat");
                h.succeed();
            } finally { cleanup.run(); }
        });
    }
}
