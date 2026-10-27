package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.blockentity.CrucibleBlockEntity;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.menu.GolemLogisticsMenu;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.*;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemAdvancedBreakerGameTests {
    private GolemAdvancedBreakerGameTests() {}
    private record Fixture(GolemSeals data,GolemSeals.SealKey key,BlockPos target,ThaumcraftGolem golem) implements AutoCloseable {
        public void close() { data.remove(key.pos(),key.face(),golem.ownerId()); golem.discard(); }
    }
    private static Fixture fixture(GameTestHelper h,net.minecraft.world.level.block.Block block,boolean ai) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var target=h.absolutePos(new BlockPos(5,1,5)); h.getLevel().setBlockAndUpdate(target,block.defaultBlockState()); var key=new GolemSeals.SealKey(target.below(),Direction.UP); var owner=UUID.randomUUID(); var d=GolemSeals.get(h.getLevel()); d.place(key.pos(),key.face(),owner,GolemSeals.Type.BREAKER_ADVANCED); d.configureArea(d.get(key),owner,new BlockPos(1,1,1)); d.configureControls(d.get(key),owner,0,0,true,false);
        var g=GolemModule.GOLEM.get().create(h.getLevel()); g.moveTo(Vec3.atBottomCenterOf(target.north())); g.initialize(GolemDesign.fromIds(0,1,3,0,0,0),owner,key.pos(),0); g.setNoAi(!ai); h.getLevel().addFreshEntity(g); return new Fixture(d,key,target,g);
    }
    private static void policy(Fixture f,LogisticsPolicy p) { f.data.configureLogistics(f.data.get(f.key),f.golem.ownerId(),p); }
    private static GolemSeals.BlockAssignment claim(Fixture f) { return f.data.claimBlock(f.golem,p->true).orElseThrow(); }
    private static boolean complete(GameTestHelper h,Fixture f,GolemSeals.BlockAssignment a) { return f.data.completeBlock(h.getLevel(),f.golem,a); }
    private static int drops(GameTestHelper h,Item item) { return h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum(); }
    @GameTest(template="golem_test",batch="advanced_breaker_work",timeoutTicks=85)
    public static void silkStoneNeedsThreeSourceCallbacksAndDropsActualStoneOnce(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,false); policy(f,f.data.get(f.key).logistics().withSilk(true));
        h.runAfterDelay(10,()->{ var task=claim(f); h.assertTrue(!complete(h,f,task)&&drops(h,Items.STONE)==0,"Silk step7:15→8");
            h.runAfterDelay(11,()->{ h.assertTrue(!complete(h,f,task)&&h.getLevel().getBlockState(f.target).is(Blocks.STONE),"Silk second callback8→1 leaves real stone");
                h.runAfterDelay(11,()->{ h.assertTrue(complete(h,f,task)&&drops(h,Items.STONE)==1&&drops(h,Items.COBBLESTONE)==0&&f.golem.rankXp()==1&&!complete(h,f,task),"Third source callback at22ticks pays actual silk stone once/XP1"); h.assertTrue(f.golem.getMainHandItem().isEmpty()&&drops(h,Items.STICK)==0,"No enchanted placeholder reward"); f.close(); h.succeed(); });
            });
        });
    }
    @GameTest(template="golem_test",batch="advanced_breaker_ai",timeoutTicks=250)
    public static void nativeAiUsesNinthFilterAndActualSilkDiamondOreLoot(GameTestHelper h) {
        var f=fixture(h,Blocks.DIAMOND_ORE,true); policy(f,f.data.get(f.key).logistics().withSlot(8,new SealFilter(new ItemStack(Items.DIAMOND_ORE),false,0)).withMode(false,true,false).withSilk(true)); f.golem.moveTo(Vec3.atBottomCenterOf(f.target.east(4)));
        h.onEachTick(()->h.assertTrue((h.getLevel().isEmptyBlock(f.target)?0:1)+drops(h,Items.DIAMOND_ORE)==1,"Per tick one actual source block/ore conserved"));
        h.succeedWhen(()->{ h.assertTrue(h.getLevel().isEmptyBlock(f.target)&&drops(h,Items.DIAMOND_ORE)==1&&drops(h,Items.DIAMOND)==0&&f.golem.rankXp()==1,"Native SMART+BREAKER/CLUMSY navigates and mines actual silk ore through ninth ghost"); f.close(); });
    }
    @GameTest(template="golem_test",batch="advanced_breaker_all",timeoutTicks=80)
    public static void sourceWhitelistRequiresEveryNonemptyGhostAndPmetaCanUnifyOldLogSpecies(GameTestHelper h) {
        var f=fixture(h,Blocks.SPRUCE_LOG,false); var p=f.data.get(f.key).logistics().withSlot(0,new SealFilter(new ItemStack(Items.SPRUCE_LOG),false,0)).withSlot(8,new SealFilter(new ItemStack(Items.OAK_LOG),false,0)).withMode(false,true,false); policy(f,p);
        h.runAfterDelay(10,()->{ h.assertTrue(f.data.claimBlock(f.golem,x->true).isEmpty(),"Source whitelist is ALL nonempty slots: ninth oak rejects spruce despite first spruce match"); policy(f,f.data.get(f.key).logistics().withOptions(SealFilterRules.Options.strict().withMetadata(false)));
            h.runAfterDelay(5,()->{ var ghost=new ItemStack(Items.OAK_LOG); ghost.set(DataComponents.CUSTOM_NAME,Component.literal("NBT ignored")); policy(f,f.data.get(f.key).logistics().withSlot(8,new SealFilter(ghost,false,0)));
                h.runAfterDelay(5,()->{ h.assertTrue(complete(h,f,claim(f))&&drops(h,Items.SPRUCE_LOG)==1&&drops(h,Items.OAK_LOG)==0,"pmeta false allows both old-family ghosts, ignores NBT, keeps actual spruce payload"); f.close(); h.succeed(); });
            });
        });
    }
    @GameTest(template="golem_test",batch="advanced_breaker_black",timeoutTicks=80)
    public static void ninthBlacklistBlocksAndEmptyWhitelistStillAcceptsEveryBreakableBlock(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,false); policy(f,f.data.get(f.key).logistics().withSlot(8,new SealFilter(new ItemStack(Items.STONE),true,0)));
        h.runAfterDelay(10,()->{ h.assertTrue(f.data.claimBlock(f.golem,x->true).isEmpty(),"Ninth blacklist match rejects actual stone"); policy(f,f.data.get(f.key).logistics().withSlot(8,SealFilter.defaults()).withMode(false,true,false));
            h.runAfterDelay(5,()->{ h.assertTrue(complete(h,f,claim(f))&&drops(h,Items.COBBLESTONE)==1,"Original empty whitelist accepts stone, unlike ordinary inventory whitelists"); f.close(); h.succeed(); });
        });
    }
    @GameTest(template="golem_test",batch="advanced_breaker_smart",timeoutTicks=65)
    public static void nonSmartBreakerCannotReserveAdvancedDespiteBasicMiningAbility(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,false); var basic=GolemModule.GOLEM.get().create(h.getLevel()); basic.moveTo(f.golem.position()); basic.initialize(GolemDesign.fromIds(0,0,3,0,0,0),f.golem.ownerId(),f.key.pos(),0); basic.setNoAi(true); h.getLevel().addFreshEntity(basic);
        h.runAfterDelay(10,()->{ try { h.assertTrue(f.data.claimBlock(basic,x->true).isEmpty()&&complete(h,f,claim(f)),"Advanced requires SMART+BREAKER; CLUMSY remains allowed"); h.succeed(); } finally { basic.discard(); f.close(); } });
    }
    @GameTest(template="golem_test",batch="advanced_breaker_protect",timeoutTicks=80)
    public static void protectedSilkHarvestCannotPayXpLootOrEnchantedTool(GameTestHelper h) {
        var f=fixture(h,Blocks.GLASS,false); policy(f,f.data.get(f.key).logistics().withSilk(true));
        h.runAfterDelay(10,()->{ var task=claim(f); java.util.function.Consumer<BlockEvent.BreakEvent> deny=e->{ if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.target)) e.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(deny);
            try { h.assertTrue(!complete(h,f,task)&&drops(h,Items.GLASS)==0&&drops(h,Items.STICK)==0&&f.golem.rankXp()==0&&h.getLevel().getBlockState(f.target).is(Blocks.GLASS),"Canceled true silk callback preserves block/no reward"); } finally { NeoForge.EVENT_BUS.unregister(deny); }
            h.runAfterDelay(5,()->{ h.assertTrue(complete(h,f,claim(f))&&drops(h,Items.GLASS)==1&&drops(h,Items.STICK)==0,"Fresh protected callback succeeds once, actual glass"); f.close(); h.succeed(); });
        });
    }
    @GameTest(template="golem_test",batch="advanced_breaker_menu",timeoutTicks=45)
    public static void sourceSilkMetadataAreaAndNinthGhostPersistAcrossMenuEditsAndNbt(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,false); var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(f.golem.ownerId(),"AdvancedMiner")); actor.moveTo(f.golem.position()); actor.getInventory().clearContent();
        try { var menu=new GolemLogisticsMenu(91,actor.getInventory(),h.getLevel(),f.data.get(f.key)); h.assertTrue(menu.advancedBreaker()&&menu.hasArea()&&!menu.hasLimits()&&!menu.canLeaveOne(),"Typed nine-slot Breaker controls");
            h.assertTrue(menu.clickMenuButton(actor,31)&&menu.silk()&&menu.clickMenuButton(actor,60)&&!menu.metadata()&&menu.clickMenuButton(actor,93)&&menu.areaX()==2,"Source psilk index1/pmeta index0/area actions"); h.assertTrue(!menu.clickMenuButton(actor,32)&&!menu.clickMenuButton(actor,33)&&!menu.clickMenuButton(actor,34),"No invented ore/mod/cycle toggles");
            menu.setCarried(new ItemStack(Items.IRON_ORE,4)); menu.clicked(8,0,ClickType.PICKUP,actor); h.assertTrue(menu.getCarried().getCount()==4&&menu.getSlot(8).getItem().getCount()==1&&menu.silk(),"Ghost copy preserves actual4 and independent psilk");
            var tag=f.data.save(new CompoundTag(),h.getLevel().registryAccess()); var loaded=GolemSeals.load(tag,h.getLevel().registryAccess()).get(f.key); h.assertTrue(loaded.type()==GolemSeals.Type.BREAKER_ADVANCED&&loaded.logistics().silk()&&!loaded.logistics().options().metadata()&&loaded.area().getX()==2&&loaded.logistics().slot(8).item().is(Items.IRON_ORE),"Exact typed psilk/pmeta/area/ninth ghost save"); h.succeed();
        } finally { actor.getInventory().clearContent(); f.close(); }
    }
    @GameTest(template="golem_test",batch="advanced_breaker_recipe",timeoutTicks=45)
    public static void sourceUpgradePaysActualBreakerAndTenTenTwentyAspectsWithAndResearch(GameTestHelper h) {
        h.setBlock(new BlockPos(5,1,5),ThaumcraftBlocks.CRUCIBLE.get()); var c=(CrucibleBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,1,5))); var tag=new CompoundTag(); tag.putInt("Water",1000); tag.putShort("Heat",(short)200); c.loadWithComponents(tag,h.getLevel().registryAccess()); var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"AdvancedMinerCrafter"));
        for(String aspect:new String[]{"sensus","cognitio","instrumentum"}) { int amount=aspect.equals("instrumentum")?20:10; var stack=CrystalEssenceItem.ofAspect(aspect); stack.setCount(amount); int paid=c.dissolve(stack,amount); stack.shrink(paid); h.assertTrue(paid==amount&&stack.isEmpty(),"Actual source aspect cost for "+aspect); }
        var k=actor.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(); k.addResearch("SEALBREAK"); var catalyst=new ItemStack(GolemModule.BREAKER_SEAL.get()); h.assertTrue(c.attemptSmelt(catalyst,1,actor)==0&&c.waterMb()==1000,"Missing MINDBIOTHAUMIC cannot debit"); k.addResearch("MINDBIOTHAUMIC"); int paid=c.attemptSmelt(catalyst,1,actor); catalyst.shrink(paid); var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()); h.assertTrue(paid==1&&catalyst.isEmpty()&&c.waterMb()==950&&c.getAspects().visSize()==0&&drops.size()==1&&drops.getFirst().getItem().is(GolemModule.BREAKER_ADVANCED_SEAL.get()),"Actual Breaker1/10Sensus10Cognitio20Instrumentum/water50→one advanced seal"); drops.forEach(ItemEntity::discard); h.succeed();
    }
}
