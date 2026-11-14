package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemTaskContinuityGameTests {
    private GolemTaskContinuityGameTests() {}

    @GameTest(template="golem_test", batch="task_continuity", timeoutTicks=300)
    public static void arrivingPickupWaitsForCurrentFillThenAutomationContinues(GameTestHelper h) {
        for (int x=0;x<12;x++) for (int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var level=h.getLevel(); var data=GolemSeals.get(level); var owner=UUID.randomUUID();
        var pickup=new GolemSeals.SealKey(h.absolutePos(new BlockPos(3,0,6)),Direction.UP);
        var fill=new GolemSeals.SealKey(h.absolutePos(new BlockPos(10,1,6)),Direction.WEST);
        h.setBlock(new BlockPos(10,1,6),Blocks.BARREL);
        h.assertTrue(data.place(pickup.pos(),pickup.face(),owner),"Place source pickup");
        h.assertTrue(data.place(fill.pos(),fill.face(),owner,GolemSeals.Type.FILL),"Place source fill");
        ThaumcraftGolem golem=GolemModule.GOLEM.get().create(level);
        var start=h.absolutePos(new BlockPos(2,1,6));
        golem.initialize(GolemDesign.defaultWood(),owner,start,0);
        golem.moveTo(Vec3.atBottomCenterOf(start)); golem.setOnGround(true);
        golem.holdItem(new ItemStack(Items.EMERALD));
        h.assertTrue(level.addFreshEntity(golem),"Actual worker starts with one carried item");
        var container=GolemContainers.handler(level,fill);
        var ground=new AtomicReference<ItemEntity>(); var firstDelivery=new AtomicBoolean();
        h.onEachTick(()->{
            int stored=container.getStackInSlot(0).getCount();
            if(stored>0) firstDelivery.set(true);
            if(ground.get()==null && golem.hasWorkTask()) {
                h.assertTrue(stored==0 && golem.distanceToSqr(Vec3.atCenterOf(fill.pos()))>16,
                        "Fill has started while its real destination remains distant");
                var pos=golem.position();
                var item=new ItemEntity(level,pos.x,pos.y,pos.z,new ItemStack(Items.EMERALD));
                item.setNoPickUpDelay(); item.setDeltaMovement(Vec3.ZERO); item.setOnGround(true);
                h.assertTrue(level.addFreshEntity(item),"New pickup arrives after block work starts");
                ground.set(item);
            }
            var item=ground.get(); if(item==null) return;
            h.assertTrue(firstDelivery.get() || item.isAlive(),
                    "New pickup must wait until the current Fill task delivers its carried item");
            h.assertTrue(stored+golem.getMainHandItem().getCount()+(item.isAlive()?item.getItem().getCount():0)==2,
                    "Both actual items remain conserved across the two task types");
        });
        h.succeedWhen(()->{
            h.assertTrue(firstDelivery.get() && ground.get()!=null && !ground.get().isAlive()
                            && container.getStackInSlot(0).getCount()==2 && golem.getMainHandItem().isEmpty(),
                    "Finishing the existing task permits fresh pickup and a second actual delivery");
            data.remove(pickup.pos(),pickup.face(),owner); data.remove(fill.pos(),fill.face(),owner); golem.discard();
        });
    }
}
