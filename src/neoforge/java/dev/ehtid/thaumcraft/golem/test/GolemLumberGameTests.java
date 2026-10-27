package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.blockentity.CrucibleBlockEntity;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.*;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemLumberGameTests {
    private GolemLumberGameTests() {}
    private static GolemDesign design() { return GolemDesign.fromIds(0,1,3,0,0,0); }
    private record Fixture(GolemSeals data,GolemSeals.SealKey key,BlockPos origin,ThaumcraftGolem golem) implements AutoCloseable {
        public void close() { data.remove(key.pos(),key.face(),golem.ownerId()); golem.discard(); }
    }
    private static Fixture fixture(GameTestHelper h,boolean ai) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var origin=h.absolutePos(new BlockPos(5,1,5)); h.getLevel().setBlockAndUpdate(origin,Blocks.OAK_LOG.defaultBlockState()); var key=new GolemSeals.SealKey(origin.below(),Direction.UP);
        var owner=UUID.randomUUID(); var d=GolemSeals.get(h.getLevel()); d.place(key.pos(),key.face(),owner,GolemSeals.Type.LUMBER); d.configureArea(d.get(key),owner,new BlockPos(1,1,1)); d.configureControls(d.get(key),owner,0,0,true,false);
        var g=GolemModule.GOLEM.get().create(h.getLevel()); g.moveTo(Vec3.atBottomCenterOf(origin.north())); g.initialize(design(),owner,key.pos(),0); g.setNoAi(!ai); h.getLevel().addFreshEntity(g); return new Fixture(d,key,origin,g);
    }
    private static GolemSeals.BlockAssignment claim(Fixture f) { return f.data.claimBlock(f.golem,p->true).orElseThrow(); }
    private static boolean complete(GameTestHelper h,Fixture f,GolemSeals.BlockAssignment task) { return f.data.completeBlock(h.getLevel(),f.golem,task); }
    private static int logs(GameTestHelper h) { return h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()).stream().filter(e->e.getItem().is(Items.OAK_LOG)||e.getItem().is(Items.OAK_WOOD)).mapToInt(e->e.getItem().getCount()).sum(); }
    @GameTest(template="golem_test",batch="lumber_ai",timeoutTicks=260)
    public static void nativeAiCutsThreeActualLogsTopDownWithoutPlantingOrTakingDrops(GameTestHelper h) {
        var f=fixture(h,true); h.getLevel().setBlockAndUpdate(f.origin.above(),Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X)); h.getLevel().setBlockAndUpdate(f.origin.above(2),Blocks.OAK_LOG.defaultBlockState()); f.golem.moveTo(Vec3.atBottomCenterOf(f.origin.east(4)));
        h.onEachTick(()->{ int remaining=0; for(int y=0;y<3;y++) if(!h.getLevel().isEmptyBlock(f.origin.above(y))) remaining++; h.assertTrue(remaining+logs(h)==3,"Per tick three actual logs conserved"); if(h.getLevel().isEmptyBlock(f.origin)) h.assertTrue(h.getLevel().isEmptyBlock(f.origin.above())&&h.getLevel().isEmptyBlock(f.origin.above(2)),"Base removed after upper trunk"); });
        h.succeedWhen(()->{ h.assertTrue(h.getLevel().isEmptyBlock(f.origin)&&logs(h)==3&&f.golem.rankXp()==3&&f.golem.getMainHandItem().isEmpty(),"Native SMART/BREAKER navigation cuts source log family across axes, actual loot3/XP3, no invented replant/pickup"); f.close(); });
    }
    @GameTest(template="golem_test",batch="lumber_order",timeoutTicks=75)
    public static void firstOrderedGreedyBranchWinsAndRepeatedSameTickCallbackCannotCutMore(GameTestHelper h) {
        var f=fixture(h,false); var first=f.origin.offset(-2,2,-2); var other=f.origin.offset(2,2,2); var far=f.origin.offset(2,4,2);
        for(var p:new BlockPos[]{first,other,far}) h.getLevel().setBlockAndUpdate(p,Blocks.OAK_LOG.defaultBlockState());
        h.runAfterDelay(10,()->{ var task=claim(f); h.assertTrue(!complete(h,f,task)&&h.getLevel().isEmptyBlock(first)&&h.getLevel().getBlockState(other).is(Blocks.OAK_LOG)&&h.getLevel().getBlockState(far).is(Blocks.OAK_LOG)&&logs(h)==1,"First dx-/dy+/dz- branch wins, not global farthest BFS");
            for(int n=0;n<30;n++) h.assertTrue(!complete(h,f,task)&&logs(h)==1,"One real log per source callback interval");
            h.runAfterDelay(11,()->{ h.assertTrue(!complete(h,f,task)&&h.getLevel().isEmptyBlock(far)&&logs(h)==2&&f.golem.rankXp()==2,"Next real callback recomputes search from original base"); f.close(); h.succeed(); });
        });
    }
    @GameTest(template="golem_test",batch="lumber_family",timeoutTicks=70)
    public static void barkBelongsToOldLogFamilyAndDifferentSpeciesIsLeftUntouched(GameTestHelper h) {
        var f=fixture(h,false); h.getLevel().setBlockAndUpdate(f.origin.above(),Blocks.OAK_WOOD.defaultBlockState()); h.getLevel().setBlockAndUpdate(f.origin.above(2),Blocks.SPRUCE_LOG.defaultBlockState());
        h.runAfterDelay(10,()->{ var task=claim(f); h.assertTrue(!complete(h,f,task)&&h.getLevel().isEmptyBlock(f.origin.above())&&logs(h)==1,"Source damageDropped strips old bark bits, actual oak wood drops preserved");
            h.runAfterDelay(11,()->{ h.assertTrue(complete(h,f,task)&&h.getLevel().isEmptyBlock(f.origin)&&h.getLevel().getBlockState(f.origin.above(2)).is(Blocks.SPRUCE_LOG)&&logs(h)==2&&!complete(h,f,task),"Species metadata differs, only two actual oak family blocks harvested once"); f.close(); h.succeed(); });
        });
    }
    @GameTest(template="golem_test",batch="lumber_traits",timeoutTicks=60)
    public static void smartAndBreakerAreBothRequiredWhileClumsyIsAllowed(GameTestHelper h) {
        var f=fixture(h,false); var others=new java.util.ArrayList<ThaumcraftGolem>();
        for(var design:new GolemDesign[]{GolemDesign.fromIds(0,0,3,0,0,0),GolemDesign.fromIds(0,1,1,0,0,0)}) { var g=GolemModule.GOLEM.get().create(h.getLevel()); g.moveTo(f.golem.position()); g.initialize(design,f.golem.ownerId(),f.key.pos(),0); g.setNoAi(true); h.getLevel().addFreshEntity(g); others.add(g); }
        h.runAfterDelay(10,()->{ try { for(var g:others) h.assertTrue(f.data.claimBlock(g,p->true).isEmpty(),"Neither BREAKER alone nor SMART alone may reserve Lumber"); h.assertTrue(complete(h,f,claim(f))&&logs(h)==1,"SMART+BREAKER may harvest despite CLUMSY"); h.succeed(); } finally { others.forEach(ThaumcraftGolem::discard); f.close(); } });
    }
    @GameTest(template="golem_test",batch="lumber_protection",timeoutTicks=75)
    public static void protectedTopLogNeverPaysLootOrXpAndFreshTaskRetries(GameTestHelper h) {
        var f=fixture(h,false); var top=f.origin.above(2); h.getLevel().setBlockAndUpdate(top,Blocks.OAK_LOG.defaultBlockState());
        h.runAfterDelay(10,()->{ var old=claim(f); java.util.function.Consumer<BlockEvent.BreakEvent> deny=e->{ if(e.getLevel()==h.getLevel()&&e.getPos().equals(top)) e.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(deny);
            try { h.assertTrue(!complete(h,f,old)&&logs(h)==0&&f.golem.rankXp()==0&&h.getLevel().getBlockState(top).is(Blocks.OAK_LOG),"Protection applies at actual distant target with no fake loot/XP"); } finally { NeoForge.EVENT_BUS.unregister(deny); }
            h.assertTrue(!complete(h,f,old),"Denied stale lease cannot replay"); h.runAfterDelay(5,()->{ h.assertTrue(!complete(h,f,claim(f))&&h.getLevel().isEmptyBlock(top)&&logs(h)==1&&f.golem.rankXp()==1,"Fresh source task retries actual permitted top"); f.close(); h.succeed(); });
        });
    }
    @GameTest(template="golem_test",batch="lumber_menu",timeoutTicks=40)
    public static void areaAndTypedNbtPersistButLumberNeverOffersGhostsOrPlantingToggles(GameTestHelper h) {
        var f=fixture(h,false); var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(f.golem.ownerId(),"LumberOwner")); actor.moveTo(f.golem.position()); actor.getInventory().clearContent();
        try { var menu=new GolemSealMenu(88,actor.getInventory(),h.getLevel(),f.data.get(f.key)); h.assertTrue(menu.lumber()&&menu.hasArea()&&!menu.getSlot(0).isActive()&&!menu.clickMenuButton(actor,30)&&!menu.clickMenuButton(actor,31)&&!menu.clickMenuButton(actor,0),"Lumber has area, no filter/NBT/replant controls"); h.assertTrue(menu.clickMenuButton(actor,93)&&menu.areaX()==2,"Owner source X area control"); var nbt=f.data.save(new CompoundTag(),h.getLevel().registryAccess()); var loaded=GolemSeals.load(nbt,h.getLevel().registryAccess()).get(f.key); h.assertTrue(loaded.type()==GolemSeals.Type.LUMBER&&loaded.area().getX()==2,"Typed seal/area survive real NBT serializer"); h.succeed(); }
        finally { actor.getInventory().clearContent(); f.close(); }
    }
    @GameTest(template="golem_test",batch="lumber_recipe",timeoutTicks=45)
    public static void actualCrucibleUpgradePaysFortyHerbaAndTwentySensusOnlyWithResearch(GameTestHelper h) {
        h.setBlock(new BlockPos(5,1,5),ThaumcraftBlocks.CRUCIBLE.get()); var crucible=(CrucibleBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,1,5))); var tag=new CompoundTag(); tag.putInt("Water",1000); tag.putShort("Heat",(short)200); crucible.loadWithComponents(tag,h.getLevel().registryAccess());
        var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"LumberCrafter")); var knowledge=actor.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        for(String aspect:new String[]{"herba","sensus"}) { int count=aspect.equals("herba")?39:20; var stack=CrystalEssenceItem.ofAspect(aspect); stack.setCount(count); int debit=crucible.dissolve(stack,count); stack.shrink(debit); h.assertTrue(debit==count&&stack.isEmpty(),"Actual initial crystals paid"); }
        var catalyst=new ItemStack(GolemModule.BREAKER_SEAL.get()); knowledge.addResearch("SEALLUMBER"); h.assertTrue(crucible.attemptSmelt(catalyst,1,actor)==0&&crucible.waterMb()==1000,"39Herba cannot pay required40");
        var extra=CrystalEssenceItem.ofAspect("herba"); int debit=crucible.dissolve(extra,1); extra.shrink(debit); h.assertTrue(debit==1&&extra.isEmpty(),"Actual missing Herba1 supplied");
        var unknown=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"UnknownLumber")); h.assertTrue(crucible.attemptSmelt(catalyst,1,unknown)==0&&crucible.waterMb()==1000,"Unknown research cannot craft");
        debit=crucible.attemptSmelt(catalyst,1,actor); catalyst.shrink(debit); h.assertTrue(debit==1&&catalyst.isEmpty()&&crucible.waterMb()==950&&crucible.getAspects().visSize()==0,"Exactly real Breaker1/Herba40/Sensus20/water50 consumed"); var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()); h.assertTrue(drops.size()==1&&drops.getFirst().getItem().is(GolemModule.LUMBER_SEAL.get()),"One actual Lumber output"); drops.forEach(ItemEntity::discard); h.succeed();
    }
}
