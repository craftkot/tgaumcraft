package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemControlGameTests {
    private GolemControlGameTests() {}

    @GameTest(template="golem_test", batch="control_menu", timeoutTicks=40)
    public static void menuEnforcesOriginalBoundsAndPersistsControls(GameTestHelper h) {
        floor(h);
        var level=h.getLevel(); var data=GolemSeals.get(level);
        var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(6,0,6)),Direction.UP);
        var owner=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"SealControlOwner"));
        var other=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"SealControlOther"));
        owner.moveTo(Vec3.atBottomCenterOf(key.pos().above())); other.moveTo(owner.position());
        h.assertTrue(data.place(key.pos(),key.face(),owner.getUUID()),"Place owned seal");
        var menu=new GolemSealMenu(4,owner.getInventory(),level,data.get(key));
        h.assertTrue(!menu.clickMenuButton(other,25),"Stranger cannot lock it");
        for(int i=0;i<5;i++) h.assertTrue(menu.clickMenuButton(owner,80),"Priority down");
        h.assertTrue(menu.priority()==-5 && !menu.clickMenuButton(owner,80),"Priority lower bound");
        for(int i=0;i<10;i++) h.assertTrue(menu.clickMenuButton(owner,81),"Priority up");
        h.assertTrue(menu.priority()==5 && !menu.clickMenuButton(owner,81),"Priority upper bound");
        for(int i=0;i<16;i++) h.assertTrue(menu.clickMenuButton(owner,83),"Color up");
        h.assertTrue(menu.color()==16 && !menu.clickMenuButton(owner,83),"Color upper bound");
        h.assertTrue(menu.clickMenuButton(owner,25) && menu.clickMenuButton(owner,27),"Lock and sensitivity via original action IDs");
        var restored=GolemSeals.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess()).get(key);
        h.assertTrue(restored.priority()==5 && restored.color()==16 && restored.locked() && restored.redstone(),"NBT preserves actual control state");
        h.assertTrue(!data.configureControls(data.get(key),owner.getUUID(),6,0,false,false)
                && !data.configureControls(data.get(key),owner.getUUID(),0,17,false,false),"Service rejects invalid settings too");
        for(int i=0;i<16;i++) menu.clickMenuButton(owner,82);
        h.assertTrue(menu.color()==0 && !menu.clickMenuButton(owner,82),"Color zero is bounded wildcard");
        var remote=new GolemSeals.SealKey(key.pos().offset(16000,0,16000),Direction.UP);
        h.assertTrue(!level.hasChunkAt(remote.pos()),"Remote signal area starts unloaded");
        data.place(remote.pos(),remote.face(),owner.getUUID());
        data.configureControls(data.get(remote),owner.getUUID(),0,0,false,true);
        h.assertTrue(GolemSeals.stopped(level,data.get(remote)),"Unknown signal neighborhood conservatively pauses");
        GolemSeals.tick(level);
        h.assertTrue(!level.hasChunkAt(remote.pos()),"Signal guard and producer do not force a chunk");
        data.remove(remote.pos(),remote.face(),owner.getUUID());
        data.remove(key.pos(),key.face(),owner.getUUID()); h.succeed();
    }

    @GameTest(template="golem_test", batch="control_pickup", timeoutTicks=80)
    public static void pickupChecksOwnerColorAndSupportPowerBeforeCompletion(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var data=GolemSeals.get(level); UUID owner=UUID.randomUUID();
        var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(6,0,6)),Direction.UP);
        data.place(key.pos(),key.face(),owner);
        h.assertTrue(data.configureControls(data.get(key),owner,0,4,true,true),"Configure locked colored sensitive seal");
        var worker=worker(h,UUID.randomUUID());
        Vec3 p=Vec3.atBottomCenterOf(key.pos().above());
        var item=new ItemEntity(level,p.x,p.y,p.z,new ItemStack(Items.EMERALD,7));
        item.setNoPickUpDelay(); item.setDeltaMovement(Vec3.ZERO); item.setOnGround(true); level.addFreshEntity(item);
        h.runAfterDelay(10,()->{
            h.assertTrue(data.claim(worker,ignored->true).isEmpty(),"Other owner's worker refused");
            worker.initialize(GolemDesign.defaultWood(),owner,worker.home(),0); worker.setGolemColor((byte)3);
            h.assertTrue(data.claim(worker,ignored->true).isEmpty(),"Wrong color refused");
            worker.setGolemColor((byte)0);
            var lease=data.claim(worker,ignored->true).orElseThrow();
            level.setBlockAndUpdate(key.pos().east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.assertTrue(GolemSeals.stopped(level,data.get(key)) && !data.pickup(level,worker,lease),"Support power blocks callback immediately");
            GolemSeals.tick(level);
            h.assertTrue(data.target(level,worker,lease)==null && item.isAlive() && worker.getMainHandItem().isEmpty(),"Power suspends reservations without taking cargo");
            level.setBlockAndUpdate(key.pos().east(),Blocks.AIR.defaultBlockState());
            h.assertTrue(data.target(level,worker,lease)==null,"Signal off cannot revive old pickup lease");
        });
        h.runAfterDelay(25,()->{
            var fresh=data.claim(worker,ignored->true).orElseThrow();
            h.assertTrue(data.pickup(level,worker,fresh) && worker.getMainHandItem().getCount()==7,"Signal off regenerates actual work");
            data.remove(key.pos(),key.face(),owner); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="control_fill", timeoutTicks=90)
    public static void fillChecksOutwardPowerAndSensitivityDefault(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var data=GolemSeals.get(level); UUID owner=UUID.randomUUID();
        var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(6,1,6)),Direction.WEST);
        h.setBlock(new BlockPos(6,1,6),Blocks.BARREL);
        data.place(key.pos(),key.face(),owner,GolemSeals.Type.FILL);
        var worker=worker(h,owner); worker.holdItem(new ItemStack(Items.EMERALD,9));
        h.runAfterDelay(25,()->{
            level.setBlockAndUpdate(key.pos().west(2),Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.assertTrue(!level.hasNeighborSignal(key.pos()) && level.hasNeighborSignal(key.pos().west()),"Only outward block is powered");
            var old=data.claimFill(worker,ignored->true).orElseThrow();
            h.assertTrue(!data.get(key).redstone() && data.fillTarget(level,worker,old)!=null,"Default ignores redstone");
            data.configureControls(data.get(key),owner,0,0,true,true);
            h.assertTrue(!data.fill(level,worker,old) && data.claimFill(worker,ignored->true).isEmpty(),"Enable sensitivity invalidates old lease and pauses claim");
            level.setBlockAndUpdate(key.pos().west(2),Blocks.AIR.defaultBlockState());
        });
        h.runAfterDelay(50,()->{
            var fresh=data.claimFill(worker,ignored->true).orElseThrow();
            // A signal arriving between claim and completion must cancel this fresh lease too.
            level.setBlockAndUpdate(key.pos().west(2),Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.assertTrue(!data.fill(level,worker,fresh),"Completion checks current outward power");
            GolemSeals.tick(level);
            h.assertTrue(data.fillTarget(level,worker,fresh)==null && worker.getMainHandItem().getCount()==9,"No cargo leaves on cancellation");
            level.setBlockAndUpdate(key.pos().west(2),Blocks.AIR.defaultBlockState());
            h.assertTrue(data.fillTarget(level,worker,fresh)==null,"Signal off cannot revive old fill lease");
        });
        h.runAfterDelay(75,()->{
            var fresh=data.claimFill(worker,ignored->true).orElseThrow();
            h.assertTrue(data.fill(level,worker,fresh),"Fill resumes after signal removal");
            var handler=GolemContainers.handler(level,key);
            h.assertTrue(handler!=null && handler.getStackInSlot(0).getCount()==9 && worker.getMainHandItem().isEmpty(),"Exactly nine paid items in barrel");
            data.remove(key.pos(),key.face(),owner); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="control_priority", timeoutTicks=60)
    public static void configuredPriorityDeterminesRealTaskSelection(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var data=GolemSeals.get(level); UUID owner=UUID.randomUUID();
        var low=new GolemSeals.SealKey(h.absolutePos(new BlockPos(4,1,6)),Direction.UP);
        var high=new GolemSeals.SealKey(h.absolutePos(new BlockPos(8,1,6)),Direction.UP);
        h.setBlock(new BlockPos(4,1,6),Blocks.BARREL); h.setBlock(new BlockPos(8,1,6),Blocks.BARREL);
        data.place(low.pos(),low.face(),owner,GolemSeals.Type.FILL); data.place(high.pos(),high.face(),owner,GolemSeals.Type.FILL);
        data.configureControls(data.get(low),owner,-5,0,false,false); data.configureControls(data.get(high),owner,5,16,false,false);
        var worker=worker(h,owner); worker.holdItem(new ItemStack(Items.EMERALD));
        h.runAfterDelay(25,()->{
            var assignment=data.claimFill(worker,ignored->true).orElseThrow();
            h.assertTrue(assignment.seal().equals(high),"Priority outweighs insertion order and distance; color zero wildcard");
            data.configureControls(data.get(high),owner,-5,16,false,false);
            h.assertTrue(data.fillTarget(level,worker,assignment)==null,"Priority edit cancels existing lease");
            data.remove(low.pos(),low.face(),owner); data.remove(high.pos(),high.face(),owner); h.succeed();
        });
    }

    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
    private static ThaumcraftGolem worker(GameTestHelper h,UUID owner) {
        var e=GolemModule.GOLEM.get().create(h.getLevel()); h.assertTrue(e!=null,"Factory");
        var pos=h.absolutePos(new BlockPos(5,1,6)); e.initialize(GolemDesign.defaultWood(),owner,pos,0);
        e.moveTo(Vec3.atBottomCenterOf(pos)); e.setNoAi(true); h.assertTrue(h.getLevel().addFreshEntity(e),"Real server worker"); return e;
    }
}
