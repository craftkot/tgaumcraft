package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemGuardGameTests {
    private GolemGuardGameTests() {}
    private record Fixture(GolemSeals data,GolemSeals.SealKey key,ThaumcraftGolem golem) implements AutoCloseable {
        public void close() { data.remove(key.pos(),key.face(),golem.ownerId()); golem.discard(); }
    }
    private static Fixture fixture(GameTestHelper h,GolemSeals.Type type,boolean ai) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(6,0,5)),Direction.UP); var data=GolemSeals.get(h.getLevel()); var owner=UUID.randomUUID(); data.place(key.pos(),key.face(),owner,type); data.configureControls(data.get(key),owner,0,0,true,false);
        var g=GolemModule.GOLEM.get().create(h.getLevel()); g.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,1,5)))); g.initialize(GolemDesign.fromIds(3,1,2,0,0,0),owner,key.pos(),0); g.setNoAi(!ai); h.getLevel().addFreshEntity(g); return new Fixture(data,key,g);
    }
    private static <T extends Mob> T target(GameTestHelper h,EntityType<T> type,int x,int z,float hp) {
        T e=type.create(h.getLevel()); e.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x,1,z)))); e.setNoAi(true); e.setHealth(hp); h.getLevel().addFreshEntity(e); return e;
    }
    private static GolemSeals.Assignment claim(Fixture f) { return f.data.claimEntity(f.golem,e->true).orElseThrow(); }
    @GameTest(template="golem_test",batch="guard_ai",timeoutTicks=200)
    public static void guardActuallyAssignsNavigatesAndKillsHostileWithSourceAssignmentAndKillXp(GameTestHelper h) {
        var f=fixture(h,GolemSeals.Type.GUARD,true); var hostile=target(h,EntityType.HUSK,7,5,4); f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.GOLD_INGOT,7));
        var beforeKill=new java.util.concurrent.atomic.AtomicInteger(-1);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> death=e->{ if(e.getEntity()==hostile&&e.getSource().getEntity()==f.golem) beforeKill.set(f.golem.rankXp()); };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(death);
        h.runAfterDelay(199,()->net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(death));
        h.onEachTick(()->h.assertTrue(f.golem.getMainHandItem().getCount()==7,"Guard combat conserves all7actualcargo"));
        h.succeedWhen(()->{ h.assertTrue(!hostile.isAlive()&&beforeKill.get()>=1&&f.golem.rankXp()==beforeKill.get()+8,"Native Guard kill8 follows actual source assignments: before="+beforeKill.get()+", after="+f.golem.rankXp()); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(death); f.close(); });
    }
    @GameTest(template="golem_test",batch="guard_routing",timeoutTicks=60)
    public static void combatTaskNeverRoutesIntoPickupOrContainerAndStartCannotReplay(GameTestHelper h) {
        var f=fixture(h,GolemSeals.Type.GUARD,false); h.getLevel().setBlockAndUpdate(f.key.pos(),Blocks.BARREL.defaultBlockState()); var hostile=target(h,EntityType.HUSK,7,5,20);
        h.runAfterDelay(22,()->{ try {
            h.assertTrue(f.data.claimBlock(f.golem,p->true).isEmpty()&&f.data.claim(f.golem,p->true).isEmpty(),"Living target never falls through item pickup or container extraction");
            var assignment=claim(f); h.assertTrue(f.data.startCombat(h.getLevel(),f.golem,assignment)&&f.golem.getTarget()==hostile&&f.golem.rankXp()==1,"Source onTaskStarted assigns immediately and gives1XP before walking");
            h.assertTrue(!f.data.startCombat(h.getLevel(),f.golem,assignment)&&f.golem.rankXp()==1,"Suspended completed lease cannot replay assignment XP"); h.succeed();
        } finally { hostile.discard(); f.close(); } });
    }
    @GameTest(template="golem_test",batch="guard_priority",timeoutTicks=60)
    public static void pickupAndGuardShareActualSourcePriorityOrder(GameTestHelper h) {
        var f=fixture(h,GolemSeals.Type.GUARD,false); f.golem.initialize(GolemDesign.fromIds(0,1,1,0,2,0),f.golem.ownerId(),f.key.pos(),0);
        f.data.configureControls(f.data.get(f.key),f.golem.ownerId(),-5,0,true,false); var pickup=new GolemSeals.SealKey(h.absolutePos(new BlockPos(4,0,5)),Direction.UP); f.data.place(pickup.pos(),pickup.face(),f.golem.ownerId(),GolemSeals.Type.PICKUP); f.data.configureControls(f.data.get(pickup),f.golem.ownerId(),5,0,true,false);
        var hostile=target(h,EntityType.HUSK,7,5,20); var pos=Vec3.atBottomCenterOf(pickup.pos().above()); var actual=new ItemEntity(h.getLevel(),pos.x,pos.y,pos.z,new ItemStack(Items.IRON_INGOT,3)); actual.setDeltaMovement(Vec3.ZERO); actual.setNoPickUpDelay(); h.getLevel().addFreshEntity(actual);
        h.runAfterDelay(22,()->{ try {
            var first=claim(f); h.assertTrue(first.targetId().equals(actual.getUUID()),"Higher priority Pickup wins shared entity order over combat"); f.golem.moveTo(actual.position());
            h.assertTrue(f.data.pickup(h.getLevel(),f.golem,first)&&f.golem.getMainHandItem().getCount()==3,"Selected pickup transfers actual3 items");
            var second=claim(f); h.assertTrue(second.targetId().equals(hostile.getUUID())&&f.data.startCombat(h.getLevel(),f.golem,second),"Same selector subsequently assigns actual living combat target"); h.succeed();
        } finally { f.data.remove(pickup.pos(),pickup.face(),f.golem.ownerId()); hostile.discard(); actual.discard(); f.close(); } });
    }
    @GameTest(template="golem_test",batch="guard_animals",timeoutTicks=60)
    public static void advancedAnimalToggleRetainsSourceIAnimalsMonsterOverlap(GameTestHelper h) {
        var f=fixture(h,GolemSeals.Type.GUARD_ADVANCED,false); f.data.configureCombat(f.data.get(f.key),f.golem.ownerId(),new GolemCombatRules.Settings(false,true,false)); var hostile=target(h,EntityType.HUSK,7,5,20);
        h.runAfterDelay(22,()->{ try { var a=claim(f); h.assertTrue(a.targetId().equals(hostile.getUUID())&&f.data.startCombat(h.getLevel(),f.golem,a),"Source panimal includes hostile IAnimals even when pmob is false"); h.succeed(); } finally { hostile.discard(); f.close(); } });
    }
    @GameTest(template="golem_test",batch="guard_controls",timeoutTicks=50)
    public static void advancedGuardOwnerMenuGhostAndNativeNbtPreserveThreeSourceFlags(GameTestHelper h) {
        var f=fixture(h,GolemSeals.Type.GUARD_ADVANCED,false); var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(f.golem.ownerId(),"GuardOwner")); p.moveTo(Vec3.atBottomCenterOf(f.key.pos().above())); var menu=new GolemSealMenu(90,p.getInventory(),h.getLevel(),f.data.get(f.key));
        try {
            h.assertTrue(menu.stillValid(p)&&menu.advancedGuard()&&menu.hasArea()&&!menu.getSlot(0).isActive(),"Existing owner menu has source toggles/area and no fictitious filter"); menu.setCarried(new ItemStack(Items.GOLD_INGOT,5)); menu.clicked(0,0,ClickType.PICKUP,p); h.assertTrue(menu.getCarried().getCount()==5,"Hidden ghost cannot consume actual cursor");
            h.assertTrue(menu.clickMenuButton(p,60)&&menu.clickMenuButton(p,31)&&menu.clickMenuButton(p,32)&&menu.clickMenuButton(p,93),"Source0/1/2 on-off IDs and X area setting accepted");
            var settings=new GolemCombatRules.Settings(false,true,true); h.assertTrue(f.data.combatSettings(f.data.get(f.key)).equals(settings)&&f.data.get(f.key).area().getX()==4,"Three actual source flags/area changed");
            var loaded=GolemSeals.load(f.data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()); h.assertTrue(loaded.get(f.key).type()==GolemSeals.Type.GUARD_ADVANCED&&loaded.combatSettings(loaded.get(f.key)).equals(settings)&&loaded.get(f.key).area().getX()==4,"Typed native serializer restores flags/area");
            var wrong=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"NotGuardOwner")); wrong.moveTo(p.position()); h.assertTrue(!menu.clickMenuButton(wrong,30),"Wrong viewer cannot toggle guard"); h.succeed();
        } finally { menu.setCarried(ItemStack.EMPTY); f.close(); }
    }
    @GameTest(template="golem_test",batch="guard_gates",timeoutTicks=60)
    public static void advancedGuardRequiresSmartFighterAndRespectsHomeColorFollow(GameTestHelper h) {
        var f=fixture(h,GolemSeals.Type.GUARD_ADVANCED,false); var hostile=target(h,EntityType.HUSK,7,5,20);
        h.runAfterDelay(22,()->{ try {
            f.golem.initialize(GolemDesign.fromIds(0,0,2,0,0,0),f.golem.ownerId(),f.key.pos(),0); h.assertTrue(f.data.claimEntity(f.golem,e->true).isEmpty(),"Advanced requires SMART in addition to FIGHTER");
            f.golem.initialize(GolemDesign.fromIds(0,1,0,0,0,0),f.golem.ownerId(),f.key.pos(),0); h.assertTrue(f.data.claimEntity(f.golem,e->true).isEmpty(),"SMART worker without FIGHTER cannot guard");
            f.golem.initialize(GolemDesign.fromIds(0,1,2,0,0,0),f.golem.ownerId(),f.key.pos().east(100),0); h.assertTrue(f.data.claimEntity(f.golem,e->true).isEmpty(),"Entity target must remain inside source home radius");
            f.golem.initialize(GolemDesign.fromIds(0,1,2,0,0,0),f.golem.ownerId(),f.key.pos(),0); f.golem.setFollowingOwner(true); h.assertTrue(f.data.claimEntity(f.golem,e->true).isEmpty(),"Following golem does not take seal tasks"); f.golem.setFollowingOwner(false);
            f.golem.setGolemColor((byte)2); f.data.configureControls(f.data.get(f.key),f.golem.ownerId(),0,1,true,false);
            h.runAfterDelay(22,()->{ try { h.assertTrue(f.data.claimEntity(f.golem,e->true).isEmpty(),"Source nonzero color mismatch rejected"); f.golem.setGolemColor((byte)1); h.assertTrue(f.data.startCombat(h.getLevel(),f.golem,claim(f)),"Matching SMART FIGHTER including CLUMSY accepted"); h.succeed(); } finally { hostile.discard(); f.close(); } });
        } catch(RuntimeException|AssertionError e) { hostile.discard(); f.close(); throw e; } });
    }
    @GameTest(template="golem_test",batch="butcher_population",timeoutTicks=450)
    public static void butcherIgnoresPigletAndTamedWolfAndNeedsThreeEligibleAdults(GameTestHelper h) {
        var f=fixture(h,GolemSeals.Type.BUTCHER,false); var a=target(h,EntityType.PIG,5,4,10); var b=target(h,EntityType.PIG,6,4,10); var baby=target(h,EntityType.PIG,7,4,10); baby.setBaby(true); var wolf=target(h,EntityType.WOLF,5,6,8); wolf.setTame(true,false);
        h.runAfterDelay(205,()->{
            h.assertTrue(f.data.claimEntity(f.golem,e->true).isEmpty(),"Two eligible adults plus piglet and tame wolf are not source surplus"); var c=target(h,EntityType.PIG,7,6,10);
            h.succeedWhen(()->{
                var available=f.data.claimEntity(f.golem,e->true);
                h.assertTrue(available.isPresent(),"Butcher emits actual work after the third eligible adult arrives");
                var assignment=available.orElseThrow();
                h.assertTrue(!assignment.targetId().equals(baby.getUUID())&&!assignment.targetId().equals(wolf.getUUID())&&f.data.startCombat(h.getLevel(),f.golem,assignment),"Third actual eligible adult permits one butcher assignment");
                for(var e:new Mob[]{a,b,c,baby,wolf}) e.discard(); f.close();
            });
        });
    }
    @GameTest(template="golem_test",batch="butcher_ai",timeoutTicks=470)
    public static void butcherActuallyKillsOneOfThreeAndKeepsTwoAdultsAcrossNextScan(GameTestHelper h) {
        var f=fixture(h,GolemSeals.Type.BUTCHER,true); Pig[] pigs={target(h,EntityType.PIG,5,4,1),target(h,EntityType.PIG,6,5,1),target(h,EntityType.PIG,7,6,1)};
        h.runAfterDelay(430,()->{ try { int alive=0; for(var pig:pigs) if(pig.isAlive()) alive++; h.assertTrue(alive==2&&f.golem.rankXp()==9,"Actual source butcher assigns/kills once, next200scan preserves two adults"); h.succeed(); } finally { for(var pig:pigs) pig.discard(); f.close(); } });
    }
    @GameTest(template="golem_test",batch="guard_stale",timeoutTicks=60)
    public static void vanishedLivingTargetCannotStartOrGrantExperience(GameTestHelper h) {
        var f=fixture(h,GolemSeals.Type.GUARD,false); var hostile=target(h,EntityType.HUSK,7,5,20);
        h.runAfterDelay(22,()->{ try { var assignment=claim(f); hostile.discard(); h.assertTrue(!f.data.startCombat(h.getLevel(),f.golem,assignment)&&f.golem.rankXp()==0&&f.golem.getTarget()==null,"Actual removed target cannot produce attack target or assignment XP"); h.succeed(); } finally { f.close(); } });
    }
    @GameTest(template="golem_test",batch="guard_basic",timeoutTicks=60)
    public static void basicGuardAcceptsSourceClumsyFighterWithoutSmart(GameTestHelper h) {
        var f=fixture(h,GolemSeals.Type.GUARD,false); f.golem.initialize(GolemDesign.fromIds(0,0,2,0,0,0),f.golem.ownerId(),f.key.pos(),0); var hostile=target(h,EntityType.HUSK,7,5,20);
        h.runAfterDelay(22,()->{ try { h.assertTrue(f.data.startCombat(h.getLevel(),f.golem,claim(f))&&f.golem.getTarget()==hostile&&f.golem.rankXp()==0,"Basic source Guard needs FIGHTER only, CLUMSY allowed, nonSMART gains no rankXP"); h.assertTrue(!f.data.configureCombat(f.data.get(f.key),f.golem.ownerId(),new GolemCombatRules.Settings(false,true,true)),"Basic guard has no invented advanced toggles"); h.succeed(); } finally { hostile.discard(); f.close(); } });
    }
    @GameTest(template="golem_test",batch="butcher_classes",timeoutTicks=240)
    public static void butcherCountsAssignableClassesRatherThanDistinctRegistryTypes(GameTestHelper h) {
        var f=fixture(h,GolemSeals.Type.BUTCHER,false); var cow=target(h,EntityType.COW,5,4,10); var a=target(h,EntityType.MOOSHROOM,6,5,10); var b=target(h,EntityType.MOOSHROOM,7,6,10);
        // Source has a random per-seal phase, independent of the test start tick.
        // Observe real work when it appears, before its ten-second lifespan expires.
        h.succeedWhen(()->{
            var available=f.data.claimEntity(f.golem,e->true);
            h.assertTrue(available.isPresent(),"Butcher must emit actual work for the three assignable adults");
            var assignment=available.orElseThrow();
            h.assertTrue(assignment.targetId().equals(cow.getUUID())&&f.data.startCombat(h.getLevel(),f.golem,assignment),"Cow class counts both MushroomCow subclasses (3), MushroomCow alone counts2; actual source asymmetric query");
            cow.discard(); a.discard(); b.discard(); f.close();
        });
    }
    @GameTest(template="golem_test",batch="guard_recipe",timeoutTicks=50)
    public static void realCruciblePaysGuardAndAdvancedUpgradeWithBothResearchGates(GameTestHelper h) {
        h.setBlock(new BlockPos(5,1,5),dev.ehtid.thaumcraft.registry.ThaumcraftBlocks.CRUCIBLE.get()); var c=(dev.ehtid.thaumcraft.blockentity.CrucibleBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,1,5))); var tag=new CompoundTag(); tag.putInt("Water",1000); tag.putShort("Heat",(short)200); c.loadWithComponents(tag,h.getLevel().registryAccess()); var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"GuardCrafter"));
        var knowledge=p.getData(dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        for(String aspect:new String[]{"aversio","praemunio"}) { var crystals=dev.ehtid.thaumcraft.item.CrystalEssenceItem.ofAspect(aspect); crystals.setCount(20); int debit=c.dissolve(crystals,20); crystals.shrink(debit); h.assertTrue(debit==20&&crystals.isEmpty(),"Actual20 crystals paid "+aspect); }
        var blank=new ItemStack(GolemModule.BLANK_SEAL.get()); h.assertTrue(c.attemptSmelt(blank,1,p)==0,"Unknown SEALGUARD does not debit"); knowledge.addResearch("SEALGUARD"); int used=c.attemptSmelt(blank,1,p); blank.shrink(used); var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds());
        h.assertTrue(used==1&&blank.isEmpty()&&c.waterMb()==950&&c.getAspects().visSize()==0&&drops.size()==1&&drops.getFirst().getItem().is(GolemModule.GUARD_SEAL.get()),"Real blank1/Aversion20/Protect20/water50 yields Guard1"); var guard=drops.getFirst().getItem(); drops.getFirst().discard();
        for(String aspect:new String[]{"sensus","cognitio"}) { var crystals=dev.ehtid.thaumcraft.item.CrystalEssenceItem.ofAspect(aspect); crystals.setCount(20); int debit=c.dissolve(crystals,20); crystals.shrink(debit); h.assertTrue(debit==20&&crystals.isEmpty(),"Actual upgrade20 crystals paid "+aspect); }
        h.assertTrue(c.attemptSmelt(guard,1,p)==0&&guard.getCount()==1,"Advanced upgrade also requires MINDBIOTHAUMIC"); knowledge.addResearch("MINDBIOTHAUMIC"); used=c.attemptSmelt(guard,1,p); guard.shrink(used); drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds());
        h.assertTrue(used==1&&guard.isEmpty()&&c.waterMb()==900&&c.getAspects().visSize()==0&&drops.size()==1&&drops.getFirst().getItem().is(GolemModule.GUARD_ADVANCED_SEAL.get()),"Real Guard1/Sensus20/Cognitio20/water50 yields AdvancedGuard1"); drops.forEach(ItemEntity::discard); h.succeed();
    }

}
