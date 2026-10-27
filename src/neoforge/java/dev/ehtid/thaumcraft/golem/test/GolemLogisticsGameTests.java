package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.menu.GolemLogisticsMenu;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.UUID;
import dev.ehtid.thaumcraft.blockentity.CrucibleBlockEntity;
import dev.ehtid.thaumcraft.golem.crafting.GolemSealCrafting;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemLogisticsGameTests {
    private GolemLogisticsGameTests() {}
    private record Pair(GolemSeals data,GolemSeals.SealKey source,GolemSeals.SealKey target,UUID owner) {
        void close() { data.remove(source.pos(),source.face(),owner); data.remove(target.pos(),target.face(),owner); }
    }
    private static ItemStack cargo(int n) {
        var stack=new ItemStack(Items.IRON_INGOT,n); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Logistics cargo")); return stack;
    }
    private static FakePlayer player(GameTestHelper h) {
        return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"LogisticsOwner"));
    }
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
    private static IItemHandlerModifiable inv(GameTestHelper h,GolemSeals.SealKey key) { return (IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),key); }
    private static int count(GameTestHelper h,GolemSeals.SealKey key) {
        int total=0; var inv=inv(h,key); for(int i=0;i<inv.getSlots();i++) total+=inv.getStackInSlot(i).getCount(); return total;
    }
    private static int held(ThaumcraftGolem g) { return g.getMainHandItem().getCount()+g.getOffhandItem().getCount()+g.transferRecovery().getCount(); }
    private static ThaumcraftGolem worker(GameTestHelper h,UUID owner,BlockPos relative,boolean noAi) {
        var g=GolemModule.GOLEM.get().create(h.getLevel()); var pos=h.absolutePos(relative); g.moveTo(Vec3.atBottomCenterOf(pos));
        g.initialize(GolemDesign.defaultWood(),owner,pos,0); g.setNoAi(noAi); h.assertTrue(h.getLevel().addFreshEntity(g),"Actual worker spawned"); return g;
    }
    private static Pair pair(GameTestHelper h,int source,int desired,UUID owner) {
        floor(h); var data=GolemSeals.get(h.getLevel());
        h.setBlock(new BlockPos(2,1,4),Blocks.BARREL); h.setBlock(new BlockPos(9,1,4),Blocks.BARREL);
        var from=new GolemSeals.SealKey(h.absolutePos(new BlockPos(2,1,4)),Direction.UP);
        var to=new GolemSeals.SealKey(h.absolutePos(new BlockPos(9,1,4)),Direction.UP);
        data.place(from.pos(),from.face(),owner,GolemSeals.Type.PROVIDE); data.place(to.pos(),to.face(),owner,GolemSeals.Type.STOCK);
        var stock=data.get(to); data.configureLogistics(stock,owner,stock.logistics().withSlot(0,new SealFilter(cargo(1),false,desired)));
        for(int slot=0,remaining=source;remaining>0;slot++,remaining-=64) inv(h,from).setStackInSlot(slot,cargo(Math.min(64,remaining)));
        return new Pair(data,from,to,owner);
    }
    private static void at(ThaumcraftGolem g,GolemSeals.SealKey key) { g.moveTo(Vec3.atBottomCenterOf(key.pos().relative(Direction.NORTH))); }

    @GameTest(template="golem_test",batch="logistics_cycle",timeoutTicks=600)
    public static void autonomousTwoWorkersMeetStockWithoutDuplication(GameTestHelper h) {
        var p=pair(h,130,70,UUID.randomUUID());
        var a=worker(h,p.owner,new BlockPos(4,1,4),false); var b=worker(h,p.owner,new BlockPos(6,1,4),false);
        h.onEachTick(()->h.assertTrue(count(h,p.source)+count(h,p.target)+held(a)+held(b)==130,"Every tick conserves all130 real items"));
        h.succeedWhen(()->{
            h.assertTrue(count(h,p.target)==70 && count(h,p.source)==60 && held(a)+held(b)==0,"Stock reaches70 with60 remaining at provider");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()).isEmpty(),"No incidental world copies");
            p.close(); a.discard(); b.discard();
        });
    }

    @GameTest(template="golem_test",batch="logistics_fresh",timeoutTicks=90)
    public static void deliveryRechecksFreshStockAndLatePickupLeaseCannotRepeatTransfer(GameTestHelper h) {
        var p=pair(h,10,10,UUID.randomUUID()); var g=worker(h,p.owner,new BlockPos(3,1,4),true); at(g,p.source);
        h.runAfterDelay(25,()->{
            var pickup=p.data.claimBlock(g,x->true).orElseThrow();
            h.assertTrue(p.data.completeBlock(h.getLevel(),g,pickup) && held(g)==10 && count(h,p.source)==0,"Actual pickup charged source once");
            var delivery=p.data.claimBlock(g,x->true).orElseThrow();
            p.data.releaseBlock(h.getLevel(),g,pickup,true);
            h.assertTrue(p.data.reservedTasks()==1,"Late pickup release leaves delivery lease reserved");
            inv(h,p.target).setStackInSlot(0,cargo(9)); at(g,p.target);
            h.assertTrue(p.data.completeBlock(h.getLevel(),g,delivery) && count(h,p.target)==10 && held(g)==9,"Fresh cap inserts1 and keeps9 actual remainder");
            h.assertTrue(!p.data.completeBlock(h.getLevel(),g,delivery) && count(h,p.target)==10 && held(g)==9,"Stale completion cannot repeat insertion");
            p.close(); g.discard(); h.succeed();
        });
    }

    @GameTest(template="golem_test",batch="logistics_single",timeoutTicks=90)
    public static void providerSingleAndLeaveOneUseFreshStrictStock(GameTestHelper h) {
        var p=pair(h,2,4,UUID.randomUUID()); var s=p.data.get(p.source);
        p.data.configureLogistics(s,p.owner,s.logistics().withMode(true,true,true)); p.data.configureLeaveOne(s,p.owner,true);
        var g=worker(h,p.owner,new BlockPos(3,1,4),true); at(g,p.source);
        h.runAfterDelay(25,()->{
            var pickup=p.data.claimBlock(g,x->true).orElseThrow();
            h.assertTrue(p.data.completeBlock(h.getLevel(),g,pickup) && held(g)==1 && count(h,p.source)==1,"psing removes1 and pleave retains1");
            var delivery=p.data.claimBlock(g,x->true).orElseThrow(); at(g,p.target); p.data.completeBlock(h.getLevel(),g,delivery);
            h.assertTrue(held(g)==0 && count(h,p.target)==1,"Only actual carried1 delivered, not request4");
            h.runAfterDelay(25,()->{
                at(g,p.source); h.assertTrue(p.data.claimBlock(g,x->true).isEmpty() && count(h,p.source)==1,"Fresh reserve1 blocks further pickup");
                p.close(); g.discard(); h.succeed();
            });
        });
    }

    @GameTest(template="golem_test",batch="logistics_replace",timeoutTicks=100)
    public static void replacementRecipientCancelsOldRouteAndResumesFromRealCargo(GameTestHelper h) {
        var p=pair(h,10,10,UUID.randomUUID()); var g=worker(h,p.owner,new BlockPos(3,1,4),true); at(g,p.source);
        h.runAfterDelay(25,()->{
            var pickup=p.data.claimBlock(g,x->true).orElseThrow(); p.data.completeBlock(h.getLevel(),g,pickup);
            var old=p.data.claimBlock(g,x->true).orElseThrow();
            p.data.remove(p.target.pos(),p.target.face(),p.owner); p.data.place(p.target.pos(),p.target.face(),p.owner,GolemSeals.Type.STOCK);
            var replacement=p.data.get(p.target); p.data.configureLogistics(replacement,p.owner,replacement.logistics().withSlot(0,new SealFilter(cargo(1),false,5)));
            at(g,p.target);
            h.assertTrue(!p.data.completeBlock(h.getLevel(),g,old) && held(g)==10 && count(h,p.target)==0,"Old route cannot target a replacement at the same face");
            h.runAfterDelay(25,()->{
                var resumed=p.data.claimBlock(g,x->true).orElseThrow();
                h.assertTrue(p.data.blockTarget(h.getLevel(),g,resumed).equals(p.target),"Resume directly targets recipient from saved real hands");
                p.data.completeBlock(h.getLevel(),g,resumed);
                h.assertTrue(count(h,p.source)==0 && count(h,p.target)==5 && held(g)==5,"Recovery makes no new provider debit");
                p.close(); g.discard(); h.succeed();
            });
        });
    }

    @GameTest(template="golem_test",batch="logistics_menu",timeoutTicks=40)
    public static void nineGhostSlotsOwnerAuthorityAndTypedNbtRoundTrip(GameTestHelper h) {
        var owner=player(h); var p=pair(h,0,0,owner.getUUID()); atPlayer(owner,p.target);
        var seal=p.data.get(p.target); var menu=new GolemLogisticsMenu(42,owner.getInventory(),h.getLevel(),seal);
        var carried=cargo(37); menu.setCarried(carried); menu.clicked(8,0,ClickType.PICKUP,owner);
        h.assertTrue(carried.getCount()==37 && menu.selected()==8 && p.data.get(p.target).logistics().slot(8).item().getCount()==1,"Ninth ghost copies1 without consuming37");
        menu.clickMenuButton(owner,4); menu.clickMenuButton(owner,3); menu.clickMenuButton(owner,61);
        h.assertTrue(menu.limit()==11 && !menu.matchNbt() && !menu.clickMenuButton(owner,0),"Per-slot target11, loose NBT and forced whitelist");
        var stranger=player(h); atPlayer(stranger,p.target);
        h.assertTrue(!menu.clickMenuButton(stranger,4) && !menu.stillValid(stranger),"Another player cannot edit owner menu");
        var restored=GolemSeals.load(p.data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        var r=restored.get(p.target);
        h.assertTrue(r.type()==GolemSeals.Type.STOCK && r.logistics().slot(8).limit()==11 && !r.logistics().matchNbt()
                && ItemStack.isSameItemSameComponents(r.logistics().slot(8).item(),cargo(1)) && restored.pendingTasks()==0,"Nine slots/type/components/count controls persist, leases do not");
        p.data.remove(p.target.pos(),p.target.face(),p.owner); p.data.place(p.target.pos(),p.target.face(),p.owner,GolemSeals.Type.STOCK);
        h.assertTrue(!menu.stillValid(owner) && !menu.clickMenuButton(owner,4),"Stale menu cannot edit replacement instance");
        menu.setCarried(ItemStack.EMPTY); p.close(); owner.getInventory().clearContent(); h.succeed();
    }
    private static void atPlayer(FakePlayer p,GolemSeals.SealKey key) { p.moveTo(Vec3.atBottomCenterOf(key.pos().above())); }

    @GameTest(template="golem_test",batch="logistics_entity",timeoutTicks=100)
    public static void apiEntityRequestUsesEntityTaskAndDropsActualPaidCargoOnce(GameTestHelper h) {
        var p=pair(h,4,0,UUID.randomUUID()); var g=worker(h,p.owner,new BlockPos(3,1,4),true); at(g,p.source);
        var recipient=EntityType.ARMOR_STAND.create(h.getLevel()); recipient.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(9,1,7)))); h.getLevel().addFreshEntity(recipient);
        GolemProvisioning.request(h.getLevel(),new ProvisionRequest.EntityTarget(recipient.getUUID()),cargo(4),0);
        h.runAfterDelay(25,()->{
            var pickup=p.data.claimBlock(g,x->true).orElseThrow(); p.data.completeBlock(h.getLevel(),g,pickup);
            var delivery=p.data.claimBlock(g,x->true).orElseThrow(); g.moveTo(recipient.position().add(1,0,0));
            java.util.function.Consumer<EntityJoinLevelEvent> reject=e->{ if(e.getLevel()==h.getLevel() && e.getEntity() instanceof ItemEntity item
                    && ItemStack.isSameItemSameComponents(item.getItem(),cargo(1))) e.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(reject);
            try { h.assertTrue(!p.data.completeBlock(h.getLevel(),g,delivery) && held(g)==4 && count(h,p.source)==0,
                    "Rejected real entity drop retains actual cargo and permits retry of the same lease"); }
            finally { NeoForge.EVENT_BUS.unregister(reject); }
            h.assertTrue(p.data.completeBlock(h.getLevel(),g,delivery) && held(g)==0 && count(h,p.source)==0,"Real entity delivery removes exact paid4 from hands");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds());
            h.assertTrue(drops.size()==1 && ItemStack.matches(drops.getFirst().getItem(),cargo(4)),"One real drop retains source components");
            h.assertTrue(!p.data.completeBlock(h.getLevel(),g,delivery),"Entity delivery completion is exclusive");
            p.close(); g.discard(); recipient.discard(); drops.forEach(ItemEntity::discard); h.succeed();
        });
    }

    @GameTest(template="golem_test",batch="logistics_recipe",timeoutTicks=40)
    public static void originalUpgradeAndStockRecipesPayCorrectCatalystsAndBothAspects(GameTestHelper h) {
        floor(h); var owner=player(h); var knowledge=owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        h.setBlock(new BlockPos(5,1,5),ThaumcraftBlocks.CRUCIBLE.get());
        var crucible=(CrucibleBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,1,5)));
        reset(h,crucible,"sensus","cognitio"); knowledge.addResearch("SEALEMPTY");
        var empty=new ItemStack(GolemModule.EMPTY_SEAL.get());
        h.assertTrue(GolemSealCrafting.conversion(empty,knowledge,crucible.getAspects(),new dev.ehtid.thaumcraft.port.core.AspectRegistry())==null,
                "Advanced upgrade requires MINDBIOTHAUMIC in addition to SEALEMPTY");
        knowledge.addResearch("MINDBIOTHAUMIC");
        var advanced=paid(h,crucible,owner,empty,GolemModule.EMPTY_ADVANCED_SEAL.get());
        reset(h,crucible,"permutatio","desiderium"); knowledge.addResearch("SEALPROVIDE");
        java.util.function.Consumer<EntityJoinLevelEvent> reject=e->{if(e.getLevel()==h.getLevel() && e.getEntity() instanceof ItemEntity item && item.getItem().is(GolemModule.PROVIDE_SEAL.get())) e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(reject);
        try { h.assertTrue(crucible.attemptSmelt(advanced,1,owner)==0 && advanced.getCount()==1 && crucible.waterMb()==1000
                && GolemSealRecipes.amount(crucible.getAspects(),"permutatio")==10
                && GolemSealRecipes.amount(crucible.getAspects(),"desiderium")==10,"Rejected Provide spawn restores both aspects/water and preserves catalyst"); }
        finally { NeoForge.EVENT_BUS.unregister(reject); }
        var provider=paid(h,crucible,owner,advanced,GolemModule.PROVIDE_SEAL.get());
        h.assertTrue(provider.getCount()==1,"Actual advanced Empty was consumed to obtain Provide");
        reset(h,crucible,"cognitio","desiderium"); knowledge.addResearch("SEALSTOCK");
        var stock=paid(h,crucible,owner,new ItemStack(GolemModule.FILL_SEAL.get()),GolemModule.STOCK_SEAL.get());
        h.assertTrue(stock.getCount()==1,"Stock consumes Fill, not a blank seal");
        h.succeed();
    }
    private static void reset(GameTestHelper h,CrucibleBlockEntity crucible,String... aspects) {
        var tag=new CompoundTag(); tag.putInt("Water",1000); tag.putShort("Heat",(short)200);
        crucible.loadWithComponents(tag,h.getLevel().registryAccess());
        for(var aspect:aspects) { var crystals=CrystalEssenceItem.ofAspect(aspect); crystals.setCount(10);
            int debit=crucible.dissolve(crystals,10); crystals.shrink(debit); h.assertTrue(debit==10 && crystals.isEmpty(),"Ten actual crystals dissolved for "+aspect); }
    }
    private static ItemStack paid(GameTestHelper h,CrucibleBlockEntity crucible,FakePlayer owner,ItemStack input,Item product) {
        int debit=crucible.attemptSmelt(input,1,owner); input.shrink(debit);
        h.assertTrue(debit==1 && input.isEmpty() && crucible.waterMb()==950 && crucible.getAspects().visSize()==0,"Actual catalyst1/both aspects10/water50 paid once");
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds());
        h.assertTrue(drops.size()==1 && drops.getFirst().getItem().is(product) && drops.getFirst().getItem().getCount()==1,"Exactly one actual typed output");
        var result=drops.getFirst().getItem().copy(); drops.getFirst().discard(); return result;
    }

    @GameTest(template="golem_test",batch="logistics_policy",timeoutTicks=100)
    public static void recipientLockColorAndProviderPolicyAreCheckedBeforeDebit(GameTestHelper h) {
        var p=pair(h,10,10,UUID.randomUUID()); var g=worker(h,UUID.randomUUID(),new BlockPos(3,1,4),true); at(g,p.source);
        var stock=p.data.get(p.target); p.data.configureControls(stock,p.owner,0,0,true,false);
        h.runAfterDelay(25,()->{
            h.assertTrue(p.data.claimBlock(g,x->true).isEmpty() && count(h,p.source)==10,"Recipient owner lock rejects a foreign worker before extraction");
            p.data.configureControls(stock,p.owner,0,3,false,false); g.setGolemColor((byte)2);
            h.runAfterDelay(25,()->{
                h.assertTrue(p.data.claimBlock(g,x->true).isEmpty() && count(h,p.source)==10,"Recipient color rejects mismatched worker before extraction");
                g.setGolemColor((byte)3);
                var provider=p.data.get(p.source); p.data.configureLogistics(provider,p.owner,provider.logistics().withMode(false,true,false));
                h.runAfterDelay(25,()->{
                    h.assertTrue(p.data.claimBlock(g,x->true).isEmpty() && count(h,p.source)==10,"Empty provider whitelist rejects the request without debit");
                    p.close(); g.discard(); h.succeed();
                });
            });
        });
    }

    @GameTest(template="golem_test",batch="logistics_item",timeoutTicks=40)
    public static void sealItemInterceptsContainerUseAndBellOpensAndRefundsExactType(GameTestHelper h) {
        floor(h); var owner=new MenuPlayer(h.getLevel()); owner.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        h.setBlock(new BlockPos(5,1,5),Blocks.BARREL); var pos=h.absolutePos(new BlockPos(5,1,5));
        owner.moveTo(Vec3.atBottomCenterOf(pos.above())); owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(GolemModule.PROVIDE_SEAL.get(),2));
        var hit=new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false);
        var context=new net.minecraft.world.item.context.UseOnContext(owner,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        h.assertTrue(GolemModule.PROVIDE_SEAL.get().onItemUseFirst(owner.getMainHandItem(),context).consumesAction()
                && owner.getMainHandItem().getCount()==1,"Seal intercepts barrel use before vanilla opens its inventory and spends1");
        var data=GolemSeals.get(h.getLevel()); var key=new GolemSeals.SealKey(pos,Direction.UP);
        h.assertTrue(data.get(key).type()==GolemSeals.Type.PROVIDE,"Actual item places provider type");
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(GolemModule.GOLEM_BELL.get()));
        h.assertTrue(GolemModule.GOLEM_BELL.get().useOn(context).consumesAction() && owner.opened instanceof GolemLogisticsMenu,"Bell dispatches actual nine-slot menu");
        owner.setShiftKeyDown(true);
        h.assertTrue(GolemModule.GOLEM_BELL.get().useOn(context).consumesAction() && data.get(key)==null,"Bell removes exact instance");
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds());
        h.assertTrue(drops.size()==1 && drops.getFirst().getItem().is(GolemModule.PROVIDE_SEAL.get()) && drops.getFirst().getItem().getCount()==1,"One exact provider item refund");
        drops.forEach(ItemEntity::discard); owner.getInventory().clearContent(); owner.setShiftKeyDown(false); h.succeed();
    }
    private static final class MenuPlayer extends FakePlayer {
        net.minecraft.world.inventory.AbstractContainerMenu opened;
        MenuPlayer(net.minecraft.server.level.ServerLevel level) { super(level,new GameProfile(UUID.randomUUID(),"LogisticsMenu")); }
        @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> extra) {
            opened=provider.createMenu(51,getInventory(),this); containerMenu=opened; return java.util.OptionalInt.of(51);
        }
    }
}
