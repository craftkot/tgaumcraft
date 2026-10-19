package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import dev.ehtid.thaumcraft.golem.press.*;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.*;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemBreakerGameTests {
    private GolemBreakerGameTests() {}
    private static GolemDesign design() { return GolemDesign.fromIds(0,1,3,0,0,0); }
    private record Fixture(GolemSeals data,GolemSeals.SealKey key,BlockPos target,ThaumcraftGolem golem) implements AutoCloseable {
        public void close() { data.remove(key.pos(),key.face(),golem.ownerId()); golem.discard(); }
    }
    private static Fixture fixture(GameTestHelper h,Block block,boolean ai) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var target=h.absolutePos(new BlockPos(5,1,5)); h.getLevel().setBlockAndUpdate(target,block.defaultBlockState());
        var key=new GolemSeals.SealKey(target.below(),Direction.UP); var owner=UUID.randomUUID(); var d=GolemSeals.get(h.getLevel()); d.place(key.pos(),key.face(),owner,GolemSeals.Type.BREAKER); d.configureArea(d.get(key),owner,new BlockPos(1,1,1)); d.configureControls(d.get(key),owner,0,0,true,false);
        var g=GolemModule.GOLEM.get().create(h.getLevel()); g.moveTo(Vec3.atBottomCenterOf(target.north())); g.initialize(design(),owner,key.pos(),0); g.setNoAi(!ai); h.getLevel().addFreshEntity(g); return new Fixture(d,key,target,g);
    }
    private static GolemSeals.BlockAssignment claim(Fixture f) { return f.data.claimBlock(f.golem,p->true).orElseThrow(); }
    private static boolean complete(GameTestHelper h,Fixture f,GolemSeals.BlockAssignment task) { return f.data.completeBlock(h.getLevel(),f.golem,task); }
    private static int drops(GameTestHelper h,Item item) { return h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum(); }
    @GameTest(template="golem_test",batch="breaker_ai",timeoutTicks=250)
    public static void nativeClumsyBreakerNavigatesAndDropsActualCobblestoneOnce(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,true); f.golem.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(9,1,5))));
        h.succeedWhen(()->{ h.assertTrue(h.getLevel().isEmptyBlock(f.target)&&drops(h,Items.COBBLESTONE)==1 && f.golem.rankXp()==1,"Native BREAKER with CLUMSY mines stone into one actual cobblestone and XP1"); h.assertTrue(f.golem.getMainHandItem().isEmpty(),"Built-in tool never becomes carried reward"); f.close(); });
    }
    @GameTest(template="golem_test",batch="breaker_work",timeoutTicks=360)
    public static void obsidianNeedsTwentyFourSpacedCallbacksAndRejectsSameTickAcceleration(GameTestHelper h) {
        var f=fixture(h,Blocks.OBSIDIAN,false);
        var current=new java.util.concurrent.atomic.AtomicReference<GolemSeals.BlockAssignment>(); var started=new java.util.concurrent.atomic.AtomicLong();
        h.runAfterDelay(10,()->{
            var task=claim(f); current.set(task); started.set(h.getLevel().getGameTime());
            for(int n=0;n<50;n++) h.assertTrue(!complete(h,f,task),"Same tick cannot finish hardness500 or repeat mining step");
            h.assertTrue(h.getLevel().getBlockState(f.target).is(Blocks.OBSIDIAN) && f.data.blockWorkOngoing(h.getLevel(),f.golem,task),"Pending is work, not failed goal");
        });
        h.onEachTick(()->{
            var task=current.get(); if(task==null) return;
            boolean done=complete(h,f,task); long elapsed=h.getLevel().getGameTime()-started.get();
            if(elapsed<23L*11) h.assertTrue(!done && h.getLevel().getBlockState(f.target).is(Blocks.OBSIDIAN),"23 reductions cannot complete before callback24 at253ticks");
            if(done) { h.assertTrue(elapsed==253 && drops(h,Items.OBSIDIAN)==1 && f.golem.rankXp()==1 && !complete(h,f,task),"Exact source work and no stale replay"); f.close(); h.succeed(); }
        });
    }

    @GameTest(template="golem_test",batch="breaker_drops",timeoutTicks=65)
    public static void builtinForceHarvestPaysRealDiamondLootAndPreservesCarriedItems(GameTestHelper h) {
        var f=fixture(h,Blocks.DIAMOND_ORE,false); var carried=new ItemStack(Items.CARROT,3); carried.set(DataComponents.CUSTOM_NAME,Component.literal("Actual carried cargo")); f.golem.setItemSlot(EquipmentSlot.MAINHAND,carried.copy());
        h.runAfterDelay(10,()->{ var task=claim(f); h.assertTrue(!complete(h,f,task),"Hardness3 starts30 work, first callback leaves9"); h.runAfterDelay(11,()->{
            h.assertTrue(complete(h,f,task) && drops(h,Items.DIAMOND)==1 && drops(h,Items.DIAMOND_ORE)==0 && ItemStack.matches(f.golem.getMainHandItem(),carried),"Built-in force harvest drops actual diamond without silk/fortune and leaves real cargo3");
            h.assertTrue(drops(h,Items.STICK)==0 && f.golem.rankXp()==1 && !complete(h,f,task),"No placeholder reward or repeated loot"); f.close(); h.succeed();
        }); });
    }
    @GameTest(template="golem_test",batch="breaker_protection",timeoutTicks=90)
    public static void protectedBreakCancelsWithoutXpOrLootThenFreshTaskRetries(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,false); var ownerSeen=new AtomicBoolean();
        h.runAfterDelay(10,()->{
            var old=claim(f); java.util.function.Consumer<BlockEvent.BreakEvent> deny=e->{ if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.target)) { ownerSeen.set(e.getPlayer().getUUID().equals(f.golem.ownerId())); e.setCanceled(true); } };
            NeoForge.EVENT_BUS.addListener(deny);
            try { h.assertTrue(!complete(h,f,old) && ownerSeen.get() && f.golem.rankXp()==0 && drops(h,Items.COBBLESTONE)==0 && h.getLevel().getBlockState(f.target).is(Blocks.STONE),"Owner protection blocks real mining with no payment/XP/loot"); }
            finally { NeoForge.EVENT_BUS.unregister(deny); }
            h.assertTrue(!complete(h,f,old),"Canceled lease cannot replay");
            h.runAfterDelay(5,()->{ h.assertTrue(complete(h,f,claim(f)) && drops(h,Items.COBBLESTONE)==1,"Fresh task retries after protection allows"); f.close(); h.succeed(); });
        });
    }
    @GameTest(template="golem_test",batch="breaker_replace",timeoutTicks=70)
    public static void replacementBlockNeverInheritsOldProgress(GameTestHelper h) {
        var f=fixture(h,Blocks.OBSIDIAN,false);
        h.runAfterDelay(10,()->{ var old=claim(f); complete(h,f,old); h.getLevel().setBlockAndUpdate(f.target,Blocks.STONE.defaultBlockState());
            h.assertTrue(!complete(h,f,old) && drops(h,Items.COBBLESTONE)==0,"Replacement invalidates accumulated hardness work before mutation");
            h.runAfterDelay(5,()->{ h.assertTrue(complete(h,f,claim(f)) && drops(h,Items.COBBLESTONE)==1 && drops(h,Items.OBSIDIAN)==0,"Only actual replacement pays its own loot"); f.close(); h.succeed(); });
        });
    }
    @GameTest(template="golem_test",batch="breaker_traits",timeoutTicks=65)
    public static void ordinaryWorkerCannotMineAndBreakerCannotTakeClumsyForbiddenPickup(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,false); var basic=GolemModule.GOLEM.get().create(h.getLevel()); basic.moveTo(f.golem.position()); basic.initialize(GolemDesign.defaultWood(),f.golem.ownerId(),f.key.pos(),0); basic.setNoAi(true); h.getLevel().addFreshEntity(basic);
        var pickup=new GolemSeals.SealKey(f.key.pos().east(3),Direction.UP); f.data.place(pickup.pos(),pickup.face(),f.golem.ownerId(),GolemSeals.Type.PICKUP); var item=new ItemEntity(h.getLevel(),pickup.pos().getX()+.5,pickup.pos().getY()+1,pickup.pos().getZ()+.5,new ItemStack(Items.IRON_INGOT)); item.setNoPickUpDelay(); h.getLevel().addFreshEntity(item);
        h.runAfterDelay(25,()->{ h.assertTrue(f.data.claimBlock(basic,p->true).isEmpty() && f.data.claim(f.golem,p->true).isEmpty(),"Required BREAKER and per-seal CLUMSY prohibitions remain separate"); h.assertTrue(complete(h,f,claim(f)) && drops(h,Items.COBBLESTONE)==1,"CLUMSY permitted on Breaker"); f.data.remove(pickup.pos(),pickup.face(),f.golem.ownerId()); basic.discard(); item.discard(); f.close(); h.succeed(); });
    }
    @GameTest(template="golem_test",batch="breaker_filter",timeoutTicks=85)
    public static void metadataFilterIgnoresNbtAndUnbreakableBlocksProduceNoTasks(GameTestHelper h) {
        var f=fixture(h,Blocks.BEDROCK,false);
        h.runAfterDelay(8,()->{ h.assertTrue(f.data.claimBlock(f.golem,p->true).isEmpty(),"Bedrock hardness-1 cannot enqueue mining"); h.getLevel().setBlockAndUpdate(f.target,Blocks.SPRUCE_LOG.defaultBlockState());
            var ghost=new ItemStack(Items.OAK_LOG); ghost.set(DataComponents.CUSTOM_NAME,Component.literal("Ghost name never constrains mining")); f.data.configure(f.data.get(f.key),f.golem.ownerId(),new SealFilter(ghost,false,0));
            h.runAfterDelay(5,()->{ h.assertTrue(f.data.claimBlock(f.golem,p->true).isEmpty(),"Source log metadata separates oak and spruce"); var seal=f.data.get(f.key); f.data.configure(seal,f.golem.ownerId(),seal.filter().withOptions(seal.filter().options().withMetadata(false)));
                h.runAfterDelay(5,()->{ h.assertTrue(complete(h,f,claim(f)) && drops(h,Items.SPRUCE_LOG)==1 && drops(h,Items.OAK_LOG)==0,"pmeta false permits same legacy family but pays actual spruce, ghost NBT ignored"); f.close(); h.succeed(); });
            });
        });
    }
    @GameTest(template="golem_test",batch="breaker_menu",timeoutTicks=45)
    public static void ownerMenuSavesBreakerAreaAndMetadataWithoutNbtToggleOrGhostRewards(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,false); var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(f.golem.ownerId(),"BreakerOwner")); p.moveTo(f.golem.position()); p.getInventory().clearContent();
        try { var menu=new GolemSealMenu(71,p.getInventory(),h.getLevel(),f.data.get(f.key)); h.assertTrue(menu.breaker()&&menu.hasArea()&&!menu.hasLeaveOne(),"Typed Breaker menu offers source controls"); h.assertTrue(menu.clickMenuButton(p,60)&&!menu.metadata()&&menu.clickMenuButton(p,93)&&menu.areaX()==2&&!menu.clickMenuButton(p,61),"pmeta and X area accepted; unrelated NBT action rejected");
            var nbt=f.data.save(new CompoundTag(),h.getLevel().registryAccess()); var restored=GolemSeals.load(nbt,h.getLevel().registryAccess()).get(f.key); h.assertTrue(restored.type()==GolemSeals.Type.BREAKER&&restored.area().getX()==2&&!restored.filter().options().metadata(),"Exact type/area/pmeta roundtrip"); h.succeed();
        } finally { p.getInventory().clearContent(); f.close(); }
    }
    @GameTest(template="golem_test",batch="breaker_paid",timeoutTicks=180)
    public static void pressPaysActualBreakerArmsPartsAndPreservesDesignThroughEntityNbt(GameTestHelper h) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"BreakerBuilder")); p.setGameMode(GameType.SURVIVAL); p.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6,1,5)))); p.getInventory().clearContent();
        h.setBlock(new BlockPos(5,1,5),GolemModule.PRESS.get()); var press=(GolemPressBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,1,5)));
        var knowledge=p.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(); GolemRuntimeRules.research(design()).stream().filter(k->!k.equals("GOLEMBREAKER")).forEach(knowledge::addResearch);
        h.assertTrue(!press.select(p,2,1),"Missing GOLEMBREAKER prevents selecting arms"); knowledge.addResearch("GOLEMBREAKER"); h.assertTrue(press.select(p,2,1) && press.select(p,1,1) && press.selection().equals(design()),"Researched functional breakers and SMART head now selectable");
        int slot=0; for(var c:GolemAssembly.components(design())) p.getInventory().setItem(slot++,GolemComponents.stack(c));
        h.setBlock(new BlockPos(5,0,5),ThaumcraftBlocks.JAR_NORMAL.get()); var jar=(dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,0,5))); for(int n=0;n<GolemPress.cost(design());n++) h.assertTrue(jar.addFromTransport("machina",1,Direction.UP)==1,"Actual Machina supply");
        h.assertTrue(press.start(p)&&p.getInventory().isEmpty(),"Actual wood/mechanisms/mind/diamond2/piston2/aggression module paid once");
        h.succeedWhen(()->{ h.assertTrue(!press.getItem(0).isEmpty() && !press.job().active(),"Paid press completes source construction job"); var product=press.getItem(0); h.assertTrue(GolemDesign.fromLong(product.get(DataComponents.CUSTOM_DATA).copyTag().getLong("props")).equals(design())&&jar.snapshot().amount()==0,"Exact breaker props output after actual Machina debit"); var g=GolemModule.GOLEM.get().create(h.getLevel()); g.initialize(design(),p.getUUID(),h.absolutePos(new BlockPos(6,1,5)),0); var nbt=new CompoundTag(); g.addAdditionalSaveData(nbt); var restored=GolemModule.GOLEM.get().create(h.getLevel()); restored.readAdditionalSaveData(nbt); h.assertTrue(restored.design().equals(design())&&!restored.isNoAi()&&restored.design().hasTrait(GolemTrait.BREAKER),"Functional arms survive entity NBT and remain enabled"); press.removeItem(0,1); p.getInventory().clearContent(); h.succeed(); });
    }
}
