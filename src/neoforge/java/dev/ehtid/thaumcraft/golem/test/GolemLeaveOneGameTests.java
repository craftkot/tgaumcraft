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
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemLeaveOneGameTests {
    private GolemLeaveOneGameTests() {}

    @GameTest(template="golem_test", batch="leave_menu", timeoutTicks=40)
    public static void originalToggleActionsAreOwnedTypedPersistedAndInstanceBound(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var data=GolemSeals.get(level); var owner=player(h,"LeaveOwner");
        var key=seal(h,owner,new BlockPos(6,1,6),GolemSeals.Type.EMPTY); owner.moveTo(Vec3.atBottomCenterOf(key.pos().above()));
        var menu=new GolemSealMenu(25,owner.getInventory(),level,data.get(key));
        h.assertTrue(menu.hasLeaveOne() && !menu.leaveOne(),"Source default pleave=false, only Empty exposes control");
        var other=player(h,"LeaveOther"); other.moveTo(owner.position());
        h.assertTrue(!menu.clickMenuButton(other,35) && !data.configureLeaveOne(data.get(key),other.getUUID(),true),"Menu and service reject another owner");
        h.assertTrue(menu.clickMenuButton(owner,35) && menu.leaveOne() && data.get(key).leaveOne(),"Source action35 enables toggle index5");
        data.configure(data.get(key),owner.getUUID(),new SealFilter(cargo(1),false,0));
        data.configureControls(data.get(key),owner.getUUID(),5,6,true,true);
        h.assertTrue(data.get(key).leaveOne(),"Filter/control changes preserve pleave");
        CompoundTag saved=data.save(new CompoundTag(),level.registryAccess());
        var restored=GolemSeals.load(saved,level.registryAccess()).get(key);
        h.assertTrue(restored.leaveOne() && restored.priority()==5 && restored.color()==6 && restored.redstone()
                && !restored.filter().blacklist(),"Typed NBT roundtrip retains toggle and other policy");
        var row=saved.getList("seals",Tag.TAG_COMPOUND).stream().map(t->(CompoundTag)t)
                .filter(t->t.getLong("pos")==key.pos().asLong() && t.getByte("face")==Direction.UP.ordinal()).findFirst().orElseThrow();
        row.remove("pleave"); h.assertTrue(!GolemSeals.load(saved,level.registryAccess()).get(key).leaveOne(),"Legacy missing flag defaults false");
        h.assertTrue(menu.clickMenuButton(owner,65) && !menu.leaveOne(),"Source action65 disables toggle index5");
        var fill=seal(h,owner,new BlockPos(8,1,6),GolemSeals.Type.FILL); var fillMenu=new GolemSealMenu(26,owner.getInventory(),level,data.get(fill));
        h.assertTrue(!fillMenu.hasLeaveOne() && !fillMenu.clickMenuButton(owner,35)
                && !data.configureLeaveOne(data.get(fill),owner.getUUID(),true),"Fill cannot receive Empty toggle");
        owner.moveTo(owner.position().add(20,0,0)); h.assertTrue(!menu.clickMenuButton(owner,35),"Remote button packet rejected");
        owner.moveTo(Vec3.atBottomCenterOf(key.pos().above())); var old=data.get(key); data.remove(key.pos(),key.face(),owner.getUUID()); data.place(key.pos(),key.face(),owner.getUUID(),GolemSeals.Type.EMPTY);
        h.assertTrue(!menu.clickMenuButton(owner,35) && !data.configureLeaveOne(old,owner.getUUID(),true),"Stale instance cannot edit replacement");
        data.remove(key.pos(),key.face(),owner.getUUID()); data.remove(fill.pos(),fill.face(),owner.getUUID()); h.succeed();
    }

    @GameTest(template="golem_test", batch="leave_workers", timeoutTicks=100)
    public static void twoReservedTasksRecountAndCannotTakeLastOneUntilToggleIsDisabled(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var data=GolemSeals.get(level); var owner=player(h,"LeavePair");
        var key=seal(h,owner,new BlockPos(6,1,6),GolemSeals.Type.EMPTY); handler(h,key).setStackInSlot(0,cargo(7)); data.configureLeaveOne(data.get(key),owner.getUUID(),true);
        var a=worker(h,owner,new BlockPos(5,1,6),false); var b=worker(h,owner,new BlockPos(6,1,5),false);
        h.runAfterDelay(45,()->{
            var first=data.claimEmpty(a,ignored->true).orElseThrow(); var second=data.claimEmpty(b,ignored->true).orElseThrow();
            h.assertTrue(data.empty(level,a,first) && ItemStack.matches(cargo(6),a.getMainHandItem()),"First snapshot takes six, not seven");
            h.assertTrue(!data.empty(level,b,second) && stock(h,key)==1 && held(b)==0,"Second reserved snapshot rechecks fresh stock and leaves last one");
            h.assertTrue(GolemContainers.emptyCandidate(level,key,data.get(key).filter(),true).isEmpty(),"Producer does not issue work for last item");
            data.configureLeaveOne(data.get(key),owner.getUUID(),false);
            h.assertTrue(!data.empty(level,b,second),"Changing toggle cannot revive an old lease");
        });
        h.runAfterDelay(70,()->{
            var fresh=data.claimEmpty(b,ignored->true).orElseThrow();
            h.assertTrue(data.empty(level,b,fresh) && stock(h,key)==0 && ItemStack.matches(cargo(1),b.getMainHandItem())
                    && held(a)+held(b)==7,"Fresh task after disable takes last one exactly once");
            data.remove(key.pos(),key.face(),owner.getUUID()); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="leave_cycle", timeoutTicks=400)
    public static void autonomousWorkersDeliverSixtyNineAndKeepOneAcrossSourceSlots(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"LeaveRoute"); var data=GolemSeals.get(level);
        var source=seal(h,owner,new BlockPos(4,1,6),GolemSeals.Type.EMPTY); handler(h,source).setStackInSlot(0,cargo(64)); handler(h,source).setStackInSlot(1,cargo(6));
        data.configureLeaveOne(data.get(source),owner.getUUID(),true); var target=seal(h,owner,new BlockPos(10,1,6),GolemSeals.Type.FILL);
        var a=worker(h,owner,new BlockPos(2,1,5),true); var b=worker(h,owner,new BlockPos(2,1,7),true);
        h.onEachTick(()->h.assertTrue(stock(h,source)+stock(h,target)+held(a)+held(b)==70,"Every tick conserves all70 across slots, both workers and destination"));
        h.startSequence().thenIdle(300).thenExecute(()->{
            h.assertTrue(stock(h,source)==1 && stock(h,target)==69 && held(a)==0 && held(b)==0,"Actual navigation/Empty/Fill leaves one and delivers69");
            h.assertTrue(ItemStack.matches(handler(h,source).getStackInSlot(1),cargo(1)),"Last item retains exact components");
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,h.getBounds()).isEmpty(),"No ground duplicate");
            data.remove(source.pos(),source.face(),owner.getUUID()); data.remove(target.pos(),target.face(),owner.getUUID());
        }).thenSucceed();
    }

    private static ItemStack cargo(int n) { var s=new ItemStack(Items.EMERALD,n); s.set(DataComponents.CUSTOM_NAME,Component.literal("M10 leave one")); return s; }
    private static FakePlayer player(GameTestHelper h,String name) { return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),name)); }
    private static ThaumcraftGolem worker(GameTestHelper h,FakePlayer owner,BlockPos p,boolean ai) {
        var e=GolemModule.GOLEM.get().create(h.getLevel()); var pos=h.absolutePos(p); e.initialize(GolemDesign.defaultWood(),owner.getUUID(),pos,0); e.moveTo(Vec3.atBottomCenterOf(pos));
        e.setOnGround(true); e.setNoAi(!ai); h.assertTrue(h.getLevel().addFreshEntity(e),"Real worker"); return e;
    }
    private static GolemSeals.SealKey seal(GameTestHelper h,FakePlayer owner,BlockPos p,GolemSeals.Type type) {
        h.setBlock(p,Blocks.BARREL); var key=new GolemSeals.SealKey(h.absolutePos(p),Direction.UP); var data=GolemSeals.get(h.getLevel());
        h.assertTrue(data.place(key.pos(),key.face(),owner.getUUID(),type) && data.configureControls(data.get(key),owner.getUUID(),0,0,true,false),"Isolated typed seal"); return key;
    }
    private static net.neoforged.neoforge.items.IItemHandlerModifiable handler(GameTestHelper h,GolemSeals.SealKey key) { return (net.neoforged.neoforge.items.IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),key); }
    private static int stock(GameTestHelper h,GolemSeals.SealKey key) { var inv=handler(h,key); int n=0; for(int i=0;i<inv.getSlots();i++) n+=inv.getStackInSlot(i).getCount(); return n; }
    private static int held(ThaumcraftGolem e) { return e.getMainHandItem().getCount()+e.getOffhandItem().getCount()+e.transferRecovery().getCount(); }
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
}
