package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.golem.seal.SealFilter;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemAreaGameTests {
    private GolemAreaGameTests() {}

    @GameTest(template="golem_test", batch="area_menu", timeoutTicks=40)
    public static void originalActionsBoundsAuthorityAndBackwardCompatibleNbt(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var data=GolemSeals.get(level); var owner=player(h,"AreaOwner");
        var pos=h.absolutePos(new BlockPos(6,0,6)); owner.moveTo(Vec3.atBottomCenterOf(pos.above()));
        data.place(pos,Direction.UP,owner.getUUID()); var key=new GolemSeals.SealKey(pos,Direction.UP);
        var menu=new GolemSealMenu(15,owner.getInventory(),level,data.get(key));
        h.assertTrue(menu.hasArea() && menu.areaX()==3 && menu.areaY()==1 && menu.areaZ()==3,"Source UP defaults3,1,3");
        var stranger=player(h,"AreaStranger"); stranger.moveTo(owner.position());
        h.assertTrue(!menu.clickMenuButton(stranger,91) && !data.configureArea(data.get(key),stranger.getUUID(),new BlockPos(2,3,4)),"Menu and service enforce ownership");
        int[] down={92,90,94},up={93,91,95};
        for(int axis=0;axis<3;axis++) {
            for(int i=0;i<8;i++) menu.clickMenuButton(owner,down[axis]);
            h.assertTrue(!menu.clickMenuButton(owner,down[axis]),"Cannot shrink below1");
            for(int i=0;i<7;i++) h.assertTrue(menu.clickMenuButton(owner,up[axis]),"Grow axis through original actions");
            h.assertTrue(!menu.clickMenuButton(owner,up[axis]),"Cannot grow beyond8");
        }
        h.assertTrue(menu.areaX()==8 && menu.areaY()==8 && menu.areaZ()==8,"All three DataSlots reflect bounds");
        h.assertTrue(!data.configureArea(data.get(key),owner.getUUID(),new BlockPos(9,1,1))
                && !data.configureArea(data.get(key),owner.getUUID(),BlockPos.ZERO),"Invalid external sizes rejected");
        var size=new BlockPos(2,3,4); data.configureArea(data.get(key),owner.getUUID(),size);
        data.configure(data.get(key),owner.getUUID(),new SealFilter(new ItemStack(Items.EMERALD),false,0));
        data.configureControls(data.get(key),owner.getUUID(),5,15,true,true);
        h.assertTrue(data.get(key).area().equals(size),"Other edits preserve area");
        CompoundTag saved=data.save(new CompoundTag(),level.registryAccess());
        var restored=GolemSeals.load(saved,level.registryAccess()).get(key);
        h.assertTrue(restored.area().equals(size) && restored.priority()==5 && restored.color()==15
                && restored.locked() && restored.redstone() && !restored.filter().blacklist(),"Typed NBT preserves area together with controls/filter");
        var row=saved.getList("seals",Tag.TAG_COMPOUND).stream().map(t->(CompoundTag)t)
                .filter(t->t.getLong("pos")==pos.asLong() && t.getByte("face")==Direction.UP.ordinal()).findFirst().orElseThrow();
        row.remove("area");
        h.assertTrue(GolemSeals.load(saved,level.registryAccess()).get(key).area().equals(new BlockPos(3,1,3)),"Missing legacy area falls back to face defaults");
        row.putLong("area",new BlockPos(100,1,1).asLong());
        h.assertTrue(GolemSeals.load(saved,level.registryAccess()).get(key).area().equals(new BlockPos(3,1,3)),"Malformed oversized area cannot create an unbounded scan");
        h.setBlock(new BlockPos(8,1,6),Blocks.BARREL); var fill=new GolemSeals.SealKey(h.absolutePos(new BlockPos(8,1,6)),Direction.UP);
        data.place(fill.pos(),fill.face(),owner.getUUID(),GolemSeals.Type.FILL);
        var fillMenu=new GolemSealMenu(16,owner.getInventory(),level,data.get(fill));
        h.assertTrue(!fillMenu.hasArea() && !fillMenu.clickMenuButton(owner,91)
                && !data.configureArea(data.get(fill),owner.getUUID(),size),"Fill is not ISealConfigArea");
        owner.moveTo(owner.position().add(20,0,0)); h.assertTrue(!menu.clickMenuButton(owner,91),"Remote menu edit rejected");
        owner.moveTo(Vec3.atBottomCenterOf(pos.above())); var old=data.get(key); data.remove(pos,Direction.UP,owner.getUUID()); data.place(pos,Direction.UP,owner.getUUID());
        h.assertTrue(!menu.clickMenuButton(owner,91) && !data.configureArea(old,owner.getUUID(),size),"Replaced face rejects stale menu and service handle");
        data.remove(pos,Direction.UP,owner.getUUID()); data.remove(fill.pos(),fill.face(),owner.getUUID()); h.succeed();
    }

    @GameTest(template="golem_test", batch="area_faces", timeoutTicks=60)
    public static void configuredOneBlockScansUseAllSixOriginalFaceNormals(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var data=GolemSeals.get(level);
        var support=h.absolutePos(new BlockPos(6,3,6)); level.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());
        var workers=new ArrayList<ThaumcraftGolem>(); var items=new ArrayList<ItemEntity>();
        for(Direction face:Direction.values()) {
            var owner=player(h,"Area"+face); var worker=worker(h,owner,new BlockPos(3,1,3)); workers.add(worker);
            var key=new GolemSeals.SealKey(support,face); data.place(support,face,owner.getUUID());
            data.configureArea(data.get(key),owner.getUUID(),new BlockPos(1,1,1));
            data.configureControls(data.get(key),owner.getUUID(),0,0,true,false);
            var target=support.relative(face); level.setBlockAndUpdate(target.below(),Blocks.STONE.defaultBlockState());
            items.add(drop(h,target));
            var box=GolemSeals.bounds(data.get(key));
            h.assertTrue(box.minX==target.getX() && box.minY==target.getY() && box.minZ==target.getZ()
                    && box.maxX==target.getX()+1 && box.maxY==target.getY()+1 && box.maxZ==target.getZ()+1,"Unit area occupies only outward block: "+face);
        }
        h.runAfterDelay(15,()->{
            for(Direction face:Direction.values()) {
                var worker=workers.get(face.ordinal()); var item=items.get(face.ordinal());
                var assignment=data.claim(worker,ignored->true).orElseThrow();
                h.assertTrue(assignment.seal().face()==face && assignment.targetId().equals(item.getUUID()),"Actual producer selects only this outward item: "+face);
                worker.moveTo(item.position());
                h.assertTrue(data.pickup(level,worker,assignment) && worker.getMainHandItem().getCount()==7 && !item.isAlive(),"Real pickup from correct face");
                data.remove(support,face,worker.ownerId());
            }
            h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="area_cancel", timeoutTicks=80)
    public static void shrinkingCancelsOldTaskAndNeverLoadsRemoteChunks(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var data=GolemSeals.get(level); var owner=player(h,"AreaResize");
        var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(6,0,6)),Direction.UP); data.place(key.pos(),key.face(),owner.getUUID());
        var worker=worker(h,owner,new BlockPos(3,1,6)); var item=drop(h,h.absolutePos(new BlockPos(8,1,6)));
        h.runAfterDelay(10,()->{
            var old=data.claim(worker,ignored->true).orElseThrow();
            h.assertTrue(data.target(level,worker,old)==item,"Old wide area produced work");
            data.configureArea(data.get(key),owner.getUUID(),new BlockPos(1,1,1));
            h.assertTrue(data.target(level,worker,old)==null && !data.pickup(level,worker,old)
                    && worker.getMainHandItem().isEmpty() && item.getItem().getCount()==7,"Shrink cancels old lease before completion without taking cargo");
            var remote=new GolemSeals.SealKey(key.pos().offset(16000,0,16000),Direction.NORTH);
            h.assertTrue(!level.hasChunkAt(remote.pos()),"Remote support initially absent");
            data.place(remote.pos(),remote.face(),owner.getUUID()); data.configureArea(data.get(remote),owner.getUUID(),new BlockPos(8,8,8)); GolemSeals.tick(level);
            h.assertTrue(!level.hasChunkAt(remote.pos()),"Maximum configured area never loads remote support"); data.remove(remote.pos(),remote.face(),owner.getUUID());
        });
        h.runAfterDelay(25,()->{
            h.assertTrue(data.claim(worker,ignored->true).isEmpty() && item.isAlive(),"Shrunk producer cannot regenerate out-of-area work");
            data.configureArea(data.get(key),owner.getUUID(),new BlockPos(3,1,1));
        });
        h.runAfterDelay(40,()->{
            var fresh=data.claim(worker,ignored->true).orElseThrow();
            worker.moveTo(item.position());
            h.assertTrue(data.pickup(level,worker,fresh) && !data.pickup(level,worker,fresh)
                    && worker.getMainHandItem().getCount()==7 && !item.isAlive(),"Expansion creates fresh work; exact cargo once");
            data.remove(key.pos(),key.face(),owner.getUUID()); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="area_cycle", timeoutTicks=300)
    public static void expandingAreaStartsActualPickupNavigationAndFill(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var data=GolemSeals.get(level); var owner=player(h,"AreaRoute");
        var worker=worker(h,owner,new BlockPos(3,1,6)); worker.setNoAi(false);
        var pickup=new GolemSeals.SealKey(h.absolutePos(new BlockPos(6,0,6)),Direction.UP);
        data.place(pickup.pos(),pickup.face(),owner.getUUID()); data.configureArea(data.get(pickup),owner.getUUID(),new BlockPos(1,1,1));
        h.setBlock(new BlockPos(10,1,6),Blocks.BARREL); var fill=new GolemSeals.SealKey(h.absolutePos(new BlockPos(10,1,6)),Direction.UP);
        data.place(fill.pos(),fill.face(),owner.getUUID(),GolemSeals.Type.FILL); var item=drop(h,h.absolutePos(new BlockPos(8,1,6)));
        h.runAfterDelay(25,()->{
            h.assertTrue(item.isAlive() && worker.getMainHandItem().isEmpty(),"Narrow area ignores real distant cargo");
            owner.moveTo(Vec3.atBottomCenterOf(pickup.pos().above()));
            var menu=new GolemSealMenu(17,owner.getInventory(),level,data.get(pickup));
            h.assertTrue(menu.clickMenuButton(owner,93) && menu.clickMenuButton(owner,93),"Real server menu expands X to3");
        });
        h.onEachTick(()->{
            var handler=GolemContainers.handler(level,fill); int stored=0;
            for(int i=0;i<handler.getSlots();i++) stored+=handler.getStackInSlot(i).getCount();
            h.assertTrue((item.isAlive()?item.getItem().getCount():0)+worker.getMainHandItem().getCount()+worker.getOffhandItem().getCount()+stored==7,"Resize conserves all seven items every tick");
        });
        h.startSequence().thenIdle(200).thenExecute(()->{
            h.assertTrue(GolemContainers.handler(level,fill).getStackInSlot(0).getCount()==7
                    && worker.getMainHandItem().isEmpty() && !item.isAlive(),"Expanded producer drives complete actual AI route to barrel");
            data.remove(pickup.pos(),pickup.face(),owner.getUUID()); data.remove(fill.pos(),fill.face(),owner.getUUID());
        }).thenSucceed();
    }

    private static FakePlayer player(GameTestHelper h,String name) { return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),name)); }
    private static ThaumcraftGolem worker(GameTestHelper h,FakePlayer owner,BlockPos relative) {
        var e=GolemModule.GOLEM.get().create(h.getLevel()); var pos=h.absolutePos(relative); e.initialize(GolemDesign.defaultWood(),owner.getUUID(),pos,0);
        e.moveTo(Vec3.atBottomCenterOf(pos)); e.setOnGround(true); e.setNoAi(true); h.assertTrue(h.getLevel().addFreshEntity(e),"Actual worker"); return e;
    }
    private static ItemEntity drop(GameTestHelper h,BlockPos absolute) {
        var p=Vec3.atBottomCenterOf(absolute); var e=new ItemEntity(h.getLevel(),p.x,p.y,p.z,new ItemStack(Items.EMERALD,7));
        e.setNoPickUpDelay(); e.setDeltaMovement(Vec3.ZERO); e.setOnGround(true); h.assertTrue(h.getLevel().addFreshEntity(e),"Actual source"); return e;
    }
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
}
