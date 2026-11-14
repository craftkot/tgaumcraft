package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.menu.GolemLogisticsMenu;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.item.PlateItem;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.*;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemMatchGameTests {
    private static final SealFilterRules.Options STRICT=SealFilterRules.Options.strict();
    private GolemMatchGameTests() {}
    private static ItemStack named(Item item,int count,String name) { var s=new ItemStack(item,count); s.set(DataComponents.CUSTOM_NAME,Component.literal(name)); return s; }
    private static SealFilter filter(ItemStack ghost,SealFilterRules.Options options) { return new SealFilter(ghost,false,0,options); }
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
    private static GolemSeals.SealKey barrel(GameTestHelper h,BlockPos relative) { h.setBlock(relative,Blocks.BARREL); return new GolemSeals.SealKey(h.absolutePos(relative),Direction.UP); }
    private static IItemHandlerModifiable inv(GameTestHelper h,GolemSeals.SealKey key) { return (IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),key); }
    private static int stock(IItemHandlerModifiable handler) { int n=0; for(int i=0;i<handler.getSlots();i++) n+=handler.getStackInSlot(i).getCount(); return n; }
    private static ThaumcraftGolem worker(GameTestHelper h,UUID owner,GolemSeals.SealKey key,boolean ai) {
        var g=GolemModule.GOLEM.get().create(h.getLevel()); g.moveTo(Vec3.atBottomCenterOf(key.pos().north()));
        g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),owner,key.pos(),0); g.setNoAi(!ai); h.getLevel().addFreshEntity(g); return g;
    }
    private static UUID empty(GameTestHelper h,GolemSeals.SealKey key,ItemStack ghost,SealFilterRules.Options options,boolean leave) {
        var owner=UUID.randomUUID(); var data=GolemSeals.get(h.getLevel()); data.place(key.pos(),key.face(),owner,GolemSeals.Type.EMPTY_ADVANCED);
        var s=data.get(key); data.configureLogistics(s,owner,s.logistics().withSlot(0,filter(ghost,options)).withMode(false,options.nbt(),false).withOptions(options));
        if(leave) data.configureLeaveOne(data.get(key),owner,true); return owner;
    }

    @GameTest(template="golem_test",batch="match_metadata",timeoutTicks=40)
    public static void metadataAndNbtAreIndependentForDamageFlatteningAndPlates(GameTestHelper h) {
        var a=named(Items.IRON_AXE,1,"Same tool"); var b=a.copy(); a.setDamageValue(2); b.setDamageValue(19);
        h.assertTrue(!filter(a,STRICT).accepts(b) && !filter(a,STRICT.withNbt(false)).accepts(b)
                && filter(a,STRICT.withMetadata(false)).accepts(b),"pmeta alone controls real damage; pnbt cannot bypass metadata");
        b.set(DataComponents.CUSTOM_NAME,Component.literal("Different tool"));
        h.assertTrue(!filter(a,STRICT.withMetadata(false)).accepts(b) && a.getDamageValue()==2 && b.getDamageValue()==19,"Ignoring metadata retains name check and never mutates actual tools");
        h.assertTrue(!filter(new ItemStack(Items.OAK_PLANKS),STRICT).accepts(new ItemStack(Items.SPRUCE_PLANKS))
                && filter(new ItemStack(Items.OAK_PLANKS),STRICT.withMetadata(false)).accepts(new ItemStack(Items.SPRUCE_PLANKS))
                && !filter(new ItemStack(Items.OAK_PLANKS),STRICT.withMetadata(false)).accepts(new ItemStack(Items.CHERRY_PLANKS)),"Audited six legacy planks share metadata family; future-only wood is a separate item");
        h.assertTrue(filter(PlateItem.create(0),STRICT.withMetadata(false)).accepts(PlateItem.create(1))
                && !filter(PlateItem.create(0),STRICT).accepts(PlateItem.create(1)),"Synthetic plate metadata/model is separated from legacy NBT");
        var plate=PlateItem.create(1); plate.set(DataComponents.CUSTOM_NAME,Component.literal("Actual iron plate"));
        h.assertTrue(!filter(PlateItem.create(0),STRICT.withMetadata(false)).accepts(plate) && PlateItem.legacyMetadata(plate)==1,"Real plate name survives metadata normalization"); h.succeed();
    }

    @GameTest(template="golem_test",batch="match_ore_direction",timeoutTicks=40)
    public static void oreShortcutChecksCanonicalRightNbtAndKeepsItsSourceDirection(GameTestHelper h) {
        var oak=new ItemStack(Items.OAK_LOG); var acacia=new ItemStack(Items.ACACIA_LOG); var namedOak=named(Items.OAK_LOG,1,"Ghost"); var namedAcacia=named(Items.ACACIA_LOG,1,"Actual");
        h.assertTrue(filter(oak,STRICT.withOre(true)).accepts(acacia) && filter(namedOak,STRICT.withOre(true)).accepts(acacia),"Shared logWood matches canonical actual stack even with a named ghost");
        h.assertTrue(!filter(oak,STRICT.withOre(true)).accepts(namedAcacia) && !filter(oak,STRICT.withOre(true).withNbt(false)).accepts(namedAcacia),"Source canonical right NBT is checked by ore shortcut even with pnbt off");
        h.assertTrue(filter(new ItemStack(Items.RED_WOOL),STRICT.withOre(true)).accepts(new ItemStack(Items.BLUE_WOOL)),"Source wildcard wool ore group spans color metadata");
        h.assertTrue(!filter(new ItemStack(Items.IRON_INGOT),STRICT.withOre(true)).accepts(new ItemStack(Items.GOLD_INGOT)),"Aggregate ingots category never equates iron and gold"); h.succeed();
    }

    @GameTest(template="golem_test",batch="match_ore_registration",timeoutTicks=40)
    public static void customOreRegistrationsSnapshotCanonicalNbtAndModOptionHasPrecedence(GameTestHelper h) {
        var canonical=named(Items.GOLD_INGOT,1,"Canonical"); var actual=canonical.copy();
        var removeIron=GolemStackMatcher.registerOre("testPaidSource",new ItemStack(Items.IRON_INGOT));
        var removeGold=GolemStackMatcher.registerOre("testPaidSource",canonical);
        canonical.set(DataComponents.CUSTOM_NAME,Component.literal("Changed caller"));
        try {
            h.assertTrue(filter(new ItemStack(Items.IRON_INGOT),STRICT.withOre(true)).accepts(actual)
                    && !filter(new ItemStack(Items.IRON_INGOT),STRICT.withOre(true)).accepts(new ItemStack(Items.GOLD_INGOT)),"Custom canonical NBT is a defensive registration snapshot");
        } finally { removeIron.run(); removeGold.run(); }
        h.assertTrue(!filter(new ItemStack(Items.IRON_INGOT),STRICT.withOre(true)).accepts(actual),"Removed custom registrations do not leak into later work");
        var removeA=GolemStackMatcher.registerOre("testCrossMod",PlateItem.create(1)); var removeB=GolemStackMatcher.registerOre("testCrossMod",new ItemStack(Items.IRON_INGOT));
        try { h.assertTrue(filter(PlateItem.create(1),STRICT.withOre(true)).accepts(new ItemStack(Items.IRON_INGOT))
                && !filter(PlateItem.create(1),STRICT.withOre(true).withMod(true)).accepts(new ItemStack(Items.IRON_INGOT)),"pmod returns false on different namespace before an ore match"); }
        finally { removeA.run(); removeB.run(); }
        h.assertTrue(filter(new ItemStack(Items.IRON_AXE),STRICT.withMod(true)).accepts(named(Items.CARROT,4,"Other item")),"pmod ignores item identity/metadata/NBT within the source namespace"); h.succeed();
    }
    private static void twoWorkers(GameTestHelper h,boolean mod) {
        floor(h); var key=barrel(h,new BlockPos(5,1,5)); var first=named(mod?Items.IRON_INGOT:Items.OAK_PLANKS,3,"Actual first");
        var second=named(mod?Items.CARROT:Items.SPRUCE_PLANKS,4,mod?"Actual second":"Actual first");
        var options=mod?STRICT.withMod(true):STRICT.withMetadata(false); var owner=empty(h,key,first,options,false); var inventory=inv(h,key);
        inventory.setStackInSlot(0,first.copy()); inventory.setStackInSlot(1,second.copy());
        var a=worker(h,owner,key,false); var b=worker(h,owner,key,false); var data=GolemSeals.get(h.getLevel());
        h.runAfterDelay(45,()->{
            var x=data.claimBlock(a,p->true).orElseThrow(); var y=data.claimBlock(b,p->true).orElseThrow();
            h.assertTrue(data.completeBlock(h.getLevel(),a,x) && data.completeBlock(h.getLevel(),b,y),"Two actual leases extract using source comparison options");
            h.assertTrue(ItemStack.matches(a.getMainHandItem(),first) && ItemStack.matches(b.getMainHandItem(),second.copyWithCount(3)) && stock(inventory)==1,"Fuzzy match never fabricates the cached item: actual second variant/components reach second hand");
            h.assertTrue(a.getMainHandItem().getCount()+b.getMainHandItem().getCount()+stock(inventory)==7 && !data.completeBlock(h.getLevel(),b,y),"Seven actual items conserved; late callback cannot repeat extraction");
            data.remove(key.pos(),key.face(),owner); a.discard(); b.discard(); h.succeed();
        });
    }
    @GameTest(template="golem_test",batch="match_real_metadata",timeoutTicks=90)
    public static void cachedPlankTaskTransfersActualOtherMetadataAfterAnotherWorker(GameTestHelper h) { twoWorkers(h,false); }
    @GameTest(template="golem_test",batch="match_real_mod",timeoutTicks=90)
    public static void cachedIronTaskTransfersActualNamedCarrotUnderModPolicy(GameTestHelper h) { twoWorkers(h,true); }

    @GameTest(template="golem_test",batch="match_ore_reserve",timeoutTicks=70)
    public static void leaveOneCountsOreInSourceDirectionAndRetainsTheActualProtectedStack(GameTestHelper h) {
        floor(h); var key=barrel(h,new BlockPos(5,1,5)); var owner=empty(h,key,new ItemStack(Items.OAK_LOG),STRICT.withOre(true),true);
        var inventory=inv(h,key); inventory.setStackInSlot(0,new ItemStack(Items.OAK_LOG,3)); inventory.setStackInSlot(1,named(Items.ACACIA_LOG,3,"Protected actual"));
        var g=worker(h,owner,key,false); var data=GolemSeals.get(h.getLevel());
        h.runAfterDelay(25,()->{
            var lease=data.claimBlock(g,p->true).orElseThrow(); h.assertTrue(data.completeBlock(h.getLevel(),g,lease) && g.getMainHandItem().getCount()==2
                    && inventory.getStackInSlot(0).getCount()==1 && inventory.getStackInSlot(1).getCount()==3,"Directional canonical ore count retains plain oak1; named nonmatching acacia never funds reserve");
            data.remove(key.pos(),key.face(),owner); g.discard(); h.succeed();
        });
    }

    @GameTest(template="golem_test",batch="match_menu",timeoutTicks=45)
    public static void originalOptionActionsAreOwnerGuardedAndSurviveGhostEditsAndOldNbt(GameTestHelper h) {
        floor(h); var key=barrel(h,new BlockPos(5,1,5)); var owner=empty(h,key,new ItemStack(Items.OAK_LOG),STRICT,false); var data=GolemSeals.get(h.getLevel());
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(owner,"MatchOwner")); player.moveTo(Vec3.atCenterOf(key.pos()));
        var menu=new GolemLogisticsMenu(107,player.getInventory(),h.getLevel(),data.get(key));
        h.assertTrue(menu.metadata() && !menu.ore() && !menu.mod(),"Source defaults pmeta=true/pore=pmod=false");
        h.assertTrue(menu.clickMenuButton(player,60) && menu.clickMenuButton(player,32) && menu.clickMenuButton(player,33) && menu.clickMenuButton(player,61),"Source actions set three independent comparison flags");
        menu.setCarried(named(Items.RED_WOOL,13,"Actual cursor")); menu.clicked(8,0,ClickType.PICKUP,player); menu.clickMenuButton(player,34);
        h.assertTrue(!menu.metadata() && menu.ore() && menu.mod() && !menu.matchNbt() && menu.cycle() && menu.getCarried().getCount()==13,"Ghost/cycle updates preserve all match options and real cursor");
        var tag=data.save(new CompoundTag(),h.getLevel().registryAccess()); var restored=GolemSeals.load(tag,h.getLevel().registryAccess());
        h.assertTrue(restored.get(key).logistics().options().equals(new SealFilterRules.Options(false,false,true,true)) && restored.get(key).logistics().slot(8).item().getCount()==1,"Full typed match policy roundtrips with ninth ghost");
        var rows=tag.getList("seals",Tag.TAG_COMPOUND); for(int i=0;i<rows.size();i++) {
            var row=rows.getCompound(i); for(var prop:List.of("pmeta","pore","pmod")) row.remove(prop);
            var slots=row.getList("logistics",Tag.TAG_COMPOUND); for(int j=0;j<slots.size();j++) for(var prop:List.of("pmeta","pore","pmod")) slots.getCompound(j).remove(prop);
        }
        h.assertTrue(GolemSeals.load(tag,h.getLevel().registryAccess()).get(key).logistics().options().equals(new SealFilterRules.Options(true,false,false,false)),"Older saved pnbt=false stays false while missing options use source defaults");
        var other=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"MatchStranger")); other.moveTo(player.position());
        h.assertTrue(!menu.clickMenuButton(other,30),"Another viewer cannot change source matching");
        menu.setCarried(ItemStack.EMPTY); data.remove(key.pos(),key.face(),owner); h.assertTrue(!menu.clickMenuButton(player,30),"Removed seal invalidates menu edits"); h.succeed();
    }

    @GameTest(template="golem_test",batch="match_stock",timeoutTicks=350)
    public static void modStockCountsActualAlternateItemsButProviderStillPaysStrictRequestedPayload(GameTestHelper h) {
        floor(h); var source=barrel(h,new BlockPos(2,1,4)); var target=barrel(h,new BlockPos(9,1,4)); var owner=UUID.randomUUID(); var data=GolemSeals.get(h.getLevel());
        data.place(source.pos(),source.face(),owner,GolemSeals.Type.PROVIDE); data.place(target.pos(),target.face(),owner,GolemSeals.Type.STOCK);
        var stock=data.get(target); data.configureLogistics(stock,owner,stock.logistics().withSlot(0,new SealFilter(new ItemStack(Items.IRON_INGOT),false,10)).withOptions(STRICT.withMod(true)));
        inv(h,source).setStackInSlot(0,new ItemStack(Items.IRON_INGOT,2)); inv(h,target).setStackInSlot(0,named(Items.GOLD_INGOT,9,"Actual alternate stock"));
        var g=worker(h,owner,source,true);
        h.onEachTick(()->h.assertTrue(stock(inv(h,source))+stock(inv(h,target))+g.getMainHandItem().getCount()==11,"Per-tick conservation of11 actual items"));
        h.succeedWhen(()->{
            h.assertTrue(stock(inv(h,target))==10 && stock(inv(h,source))==1 && g.getMainHandItem().isEmpty(),"Only deficit1 paid by strict actual provider iron after counting alternate gold9 under pmod");
            h.assertTrue(inv(h,target).getStackInSlot(0).is(Items.GOLD_INGOT) && inv(h,target).getStackInSlot(0).getCount()==9,"Fuzzy stock counting never converts original gold");
            data.remove(source.pos(),source.face(),owner); data.remove(target.pos(),target.face(),owner); g.discard();
        });
    }
}
