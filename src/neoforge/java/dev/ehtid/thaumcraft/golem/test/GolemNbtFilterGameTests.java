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
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemNbtFilterGameTests {
    private GolemNbtFilterGameTests() {}

    @GameTest(template="golem_test", batch="nbt_menu", timeoutTicks=40)
    public static void sourcePnbtActionsPersistForAllThreeTypesAndGhostEditsPreservePolicy(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var data=GolemSeals.get(level); var owner=player(h,"NbtMenuOwner");
        var other=player(h,"NbtMenuOther");
        for(var type:GolemSeals.Type.values()) {
            var key=seal(h,owner,new BlockPos(4+type.ordinal()*2,1,6),type);
            owner.moveTo(Vec3.atBottomCenterOf(key.pos().above())); other.moveTo(owner.position());
            var menu=new GolemSealMenu(30+type.ordinal(),owner.getInventory(),level,data.get(key));
            h.assertTrue(menu.matchNbt() && !menu.clickMenuButton(other,61),"Strict default and owner guard: "+type);
            h.assertTrue(menu.clickMenuButton(owner,61) && !menu.matchNbt(),"Source action61 disables pnbt index1");
            menu.setCarried(named("ghost",5)); menu.clicked(0,0,ClickType.PICKUP,owner); menu.clickMenuButton(owner,0);
            if(menu.hasLimit()) menu.clickMenuButton(owner,3);
            h.assertTrue(!data.get(key).filter().matchNbt() && data.get(key).filter().item().getCount()==1
                    && !data.get(key).filter().blacklist(),"Ghost/mode/limit updates preserve loose policy");
            menu.clickMenuButton(owner,5); h.assertTrue(!menu.matchNbt() && data.get(key).filter().item().isEmpty(),"Clearing ghost preserves pnbt");
            menu.clicked(0,0,ClickType.PICKUP,owner);
            CompoundTag saved=data.save(new CompoundTag(),level.registryAccess());
            h.assertTrue(!GolemSeals.load(saved,level.registryAccess()).get(key).filter().matchNbt(),"NBT roundtrip preserves false");
            var row=saved.getList("seals",Tag.TAG_COMPOUND).stream().map(t->(CompoundTag)t)
                    .filter(t->t.getLong("pos")==key.pos().asLong() && t.getByte("face")==Direction.UP.ordinal()).findFirst().orElseThrow();
            row.remove("pnbt"); h.assertTrue(GolemSeals.load(saved,level.registryAccess()).get(key).filter().matchNbt(),"Missing legacy flag defaults strict");
            h.assertTrue(menu.clickMenuButton(owner,31) && menu.matchNbt(),"Source action31 enables pnbt");
            owner.moveTo(owner.position().add(20,0,0)); h.assertTrue(!menu.clickMenuButton(owner,61),"Remote button rejected");
            owner.moveTo(Vec3.atBottomCenterOf(key.pos().above())); data.remove(key.pos(),key.face(),owner.getUUID()); data.place(key.pos(),key.face(),owner.getUUID(),type);
            h.assertTrue(!menu.clickMenuButton(owner,61),"Stale instance cannot edit replacement");
            data.remove(key.pos(),key.face(),owner.getUUID()); menu.setCarried(ItemStack.EMPTY);
        }
        h.succeed();
    }

    @GameTest(template="golem_test", batch="nbt_damage", timeoutTicks=40)
    public static void ignoringNbtNeverDisablesDamageOrChangesInputEnchantments(GameTestHelper h) {
        var ghost=tool(h,"ghost",7,false); var variant=tool(h,"actual",7,true);
        var strict=new SealFilter(ghost,false,0); var loose=strict.withMatchNbt(false);
        h.assertTrue(!strict.accepts(variant) && loose.accepts(variant),"Ignore names/enchantments only when pnbt disabled");
        var damaged=variant.copy(); damaged.setDamageValue(8);
        h.assertTrue(!loose.accepts(damaged) && !loose.accepts(new ItemStack(Items.DIAMOND_PICKAXE)),"Damage and item identity remain mandatory");
        h.assertTrue(ItemStack.matches(variant,tool(h,"actual",7,true)) && !variant.get(DataComponents.ENCHANTMENTS).isEmpty(),"Comparison never mutates actual enchantment/name/damage");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="nbt_fill", timeoutTicks=70)
    public static void fillCapCountsDifferentComponentsButDepositsOnlyActualVariant(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"NbtStock"); var data=GolemSeals.get(level);
        var key=seal(h,owner,new BlockPos(6,1,6),GolemSeals.Type.FILL); handler(h,key).setStackInSlot(0,named("A",9));
        data.configure(data.get(key),owner.getUUID(),new SealFilter(named("A",1),false,12,false));
        var a=worker(h,owner,new BlockPos(5,1,6),false); var b=worker(h,owner,new BlockPos(6,1,5),false); a.holdItem(named("B",10)); b.holdItem(named("C",10));
        h.runAfterDelay(25,()->{
            var first=data.claimFill(a,ignored->true).orElseThrow(); var second=data.claimFill(b,ignored->true).orElseThrow();
            h.assertTrue(data.fill(level,a,first) && stock(h,key)==12 && ItemStack.matches(named("B",7),a.getMainHandItem()),"Fresh loose stock cap accepts exactly3 actual B");
            h.assertTrue(!data.fill(level,b,second) && ItemStack.matches(named("C",10),b.getMainHandItem()),"Second reserved callback cannot overfill across names");
            h.assertTrue(ItemStack.matches(named("A",9),handler(h,key).getStackInSlot(0)) && ItemStack.matches(named("B",3),handler(h,key).getStackInSlot(1)),"Different actual components stay in separate slots");
            data.releaseBlock(level,b,second,false); data.remove(key.pos(),key.face(),owner.getUUID()); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="nbt_empty", timeoutTicks=110)
    public static void policyChangeCancelsEmptyLeaseAndNeverSynthesizesGhostName(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"NbtEmpty"); var data=GolemSeals.get(level);
        var key=seal(h,owner,new BlockPos(6,1,6),GolemSeals.Type.EMPTY); handler(h,key).setStackInSlot(0,named("actual",5));
        data.configure(data.get(key),owner.getUUID(),new SealFilter(named("ghost",1),false,0,false)); var worker=worker(h,owner,new BlockPos(5,1,6),false);
        h.runAfterDelay(25,()->{
            var old=data.claimEmpty(worker,ignored->true).orElseThrow(); data.configure(data.get(key),owner.getUUID(),data.get(key).filter().withMatchNbt(true));
            h.assertTrue(!data.empty(level,worker,old) && stock(h,key)==5 && held(worker)==0,"Switch to strict invalidates reserved loose extraction");
        });
        h.runAfterDelay(50,()->{
            h.assertTrue(data.claimEmpty(worker,ignored->true).isEmpty(),"Strict producer rejects different actual name");
            data.configure(data.get(key),owner.getUUID(),data.get(key).filter().withMatchNbt(false));
        });
        h.runAfterDelay(75,()->{
            var fresh=data.claimEmpty(worker,ignored->true).orElseThrow();
            h.assertTrue(data.empty(level,worker,fresh) && stock(h,key)==0 && ItemStack.matches(named("actual",5),worker.getMainHandItem()),"Fresh loose task carries actual name, never ghost template");
            data.remove(key.pos(),key.face(),owner.getUUID()); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="nbt_pickup", timeoutTicks=100)
    public static void pickupPreservesActualToolAndRejectsDifferentDamageAfterPolicyEdits(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"NbtPickup"); var data=GolemSeals.get(level);
        var key=seal(h,owner,new BlockPos(6,1,6),GolemSeals.Type.PICKUP); h.setBlock(new BlockPos(7,1,6),Blocks.STONE);
        var actual=tool(h,"actual",7,true); var wanted=drop(h,new BlockPos(6,2,6),actual); var wrong=drop(h,new BlockPos(7,2,6),tool(h,"wrong damage",8,true));
        data.configure(data.get(key),owner.getUUID(),new SealFilter(tool(h,"ghost",7,false),false,0)); var worker=worker(h,owner,new BlockPos(5,1,6),false);
        h.runAfterDelay(10,()->{
            h.assertTrue(data.claim(worker,ignored->true).isEmpty(),"Strict source rejects different tool data");
            data.configure(data.get(key),owner.getUUID(),data.get(key).filter().withMatchNbt(false));
        });
        h.runAfterDelay(25,()->{
            var old=data.claim(worker,ignored->true).orElseThrow(); h.assertTrue(old.targetId().equals(wanted.getUUID()),"Loose producer chooses matching damage tool");
            data.configure(data.get(key),owner.getUUID(),data.get(key).filter().withMatchNbt(true));
            h.assertTrue(!data.pickup(level,worker,old) && wanted.isAlive(),"Editing policy invalidates pickup lease");
            data.configure(data.get(key),owner.getUUID(),data.get(key).filter().withMatchNbt(false));
        });
        h.runAfterDelay(45,()->{
            var fresh=data.claim(worker,ignored->true).orElseThrow(); worker.moveTo(wanted.position());
            h.assertTrue(data.pickup(level,worker,fresh) && !wanted.isAlive() && ItemStack.matches(actual,worker.getMainHandItem())
                    && wrong.isAlive() && wrong.getItem().getDamageValue()==8,"Real pickup keeps enchantments/name/damage and leaves wrong-damage tool");
            data.remove(key.pos(),key.face(),owner.getUUID()); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="nbt_cycle", timeoutTicks=400)
    public static void looseTwoWorkerRoutePreservesDistinctEnchantedToolsAndLeaveOne(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"NbtRoute"); var data=GolemSeals.get(level);
        var source=seal(h,owner,new BlockPos(4,1,6),GolemSeals.Type.EMPTY); var target=seal(h,owner,new BlockPos(10,1,6),GolemSeals.Type.FILL);
        var aStack=tool(h,"A",7,false); var bStack=tool(h,"B",7,true); var cStack=tool(h,"C",7,true);
        handler(h,source).setStackInSlot(0,aStack.copy()); handler(h,source).setStackInSlot(1,bStack.copy()); handler(h,source).setStackInSlot(2,cStack.copy());
        data.configure(data.get(source),owner.getUUID(),new SealFilter(aStack,false,0,false)); data.configureLeaveOne(data.get(source),owner.getUUID(),true);
        data.configure(data.get(target),owner.getUUID(),new SealFilter(aStack,false,2,false));
        var a=worker(h,owner,new BlockPos(2,1,5),true); var b=worker(h,owner,new BlockPos(2,1,7),true);
        h.onEachTick(()->h.assertTrue(stock(h,source)+stock(h,target)+held(a)+held(b)==3,"Every tick conserves three actual tool instances including recovery"));
        h.startSequence().thenIdle(300).thenExecute(()->{
            h.assertTrue(stock(h,source)==1 && stock(h,target)==2 && held(a)==0 && held(b)==0,"Two actual tools delivered; one left across loose matching variants");
            h.assertTrue(ItemStack.matches(cStack,handler(h,source).getStackInSlot(2)),"Last C tool retains real data");
            boolean gotA=false,gotB=false;
            for(int i=0;i<handler(h,target).getSlots();i++) { var s=handler(h,target).getStackInSlot(i); gotA|=ItemStack.matches(aStack,s); gotB|=ItemStack.matches(bStack,s); }
            h.assertTrue(gotA && gotB && level.getEntitiesOfClass(ItemEntity.class,h.getBounds()).isEmpty(),"Destination receives distinct A and enchanted B, no ghost substitution or ground copies");
            data.remove(source.pos(),source.face(),owner.getUUID()); data.remove(target.pos(),target.face(),owner.getUUID());
        }).thenSucceed();
    }

    private static ItemStack named(String name,int count) { var s=new ItemStack(Items.EMERALD,count); s.set(DataComponents.CUSTOM_NAME,Component.literal(name)); return s; }
    private static ItemStack tool(GameTestHelper h,String name,int damage,boolean enchanted) {
        var s=new ItemStack(Items.IRON_PICKAXE); s.setDamageValue(damage); s.set(DataComponents.CUSTOM_NAME,Component.literal(name));
        if(enchanted) { var e=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY); e.set(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING),3); s.set(DataComponents.ENCHANTMENTS,e.toImmutable()); }
        return s;
    }
    private static FakePlayer player(GameTestHelper h,String name) { return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),name)); }
    private static ThaumcraftGolem worker(GameTestHelper h,FakePlayer owner,BlockPos p,boolean ai) {
        var e=GolemModule.GOLEM.get().create(h.getLevel()); var pos=h.absolutePos(p); e.initialize(GolemDesign.defaultWood(),owner.getUUID(),pos,0); e.moveTo(Vec3.atBottomCenterOf(pos)); e.setOnGround(true); e.setNoAi(!ai); h.assertTrue(h.getLevel().addFreshEntity(e),"Real worker"); return e;
    }
    private static GolemSeals.SealKey seal(GameTestHelper h,FakePlayer owner,BlockPos p,GolemSeals.Type type) {
        h.setBlock(p,Blocks.BARREL); var key=new GolemSeals.SealKey(h.absolutePos(p),Direction.UP); var data=GolemSeals.get(h.getLevel());
        h.assertTrue(data.place(key.pos(),key.face(),owner.getUUID(),type) && data.configureControls(data.get(key),owner.getUUID(),0,0,true,false),"Isolated typed seal"); return key;
    }
    private static net.neoforged.neoforge.items.IItemHandlerModifiable handler(GameTestHelper h,GolemSeals.SealKey key) { return (net.neoforged.neoforge.items.IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),key); }
    private static int stock(GameTestHelper h,GolemSeals.SealKey key) { var inv=handler(h,key); int n=0; for(int i=0;i<inv.getSlots();i++) n+=inv.getStackInSlot(i).getCount(); return n; }
    private static int held(ThaumcraftGolem e) { return e.getMainHandItem().getCount()+e.getOffhandItem().getCount()+e.transferRecovery().getCount(); }
    private static ItemEntity drop(GameTestHelper h,BlockPos p,ItemStack stack) { var v=Vec3.atBottomCenterOf(h.absolutePos(p)); var e=new ItemEntity(h.getLevel(),v.x,v.y,v.z,stack.copy()); e.setNoPickUpDelay(); e.setOnGround(true); e.setDeltaMovement(Vec3.ZERO); h.assertTrue(h.getLevel().addFreshEntity(e),"Real named tool"); return e; }
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
}
