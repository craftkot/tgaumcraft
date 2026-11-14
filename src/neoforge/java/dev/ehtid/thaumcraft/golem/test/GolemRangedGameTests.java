package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.*;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.ai.GolemRangedGoal;
import dev.ehtid.thaumcraft.golem.press.*;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.item.GolemPlacerItem;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.*;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemRangedGameTests {
    private GolemRangedGameTests() {}
    private static GolemDesign design() { return GolemDesign.fromIds(3,1,4,0,0,0); }
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
    private static ThaumcraftGolem worker(GameTestHelper h,GolemDesign design,UUID owner,int x,int y,int z,boolean ai) {
        var g=GolemModule.GOLEM.get().create(h.getLevel()); var p=h.absolutePos(new BlockPos(x,y,z)); g.moveTo(Vec3.atBottomCenterOf(p)); g.initialize(design,owner,p,0); g.setNoAi(!ai); h.getLevel().addFreshEntity(g); return g;
    }
    private static net.minecraft.world.entity.monster.Husk target(GameTestHelper h,int x,int y,int z,float hp) {
        var e=EntityType.HUSK.create(h.getLevel()); e.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x,y,z)))); e.setNoAi(true); e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); e.setHealth(hp); h.getLevel().addFreshEntity(e); return e;
    }
    private static List<GolemDart> darts(GameTestHelper h,ThaumcraftGolem g) { return h.getLevel().getEntitiesOfClass(GolemDart.class,h.getBounds().inflate(4),d->d.getOwner()==g); }
    private static void discardDarts(GameTestHelper h,ThaumcraftGolem g) { darts(h,g).forEach(Entity::discard); }
    private static void guard(GameTestHelper h,boolean flying) {
        floor(h); var owner=UUID.randomUUID(); var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(9,flying?6:0,9)),Direction.UP); h.getLevel().setBlockAndUpdate(key.pos(),Blocks.STONE.defaultBlockState()); var data=GolemSeals.get(h.getLevel()); data.place(key.pos(),key.face(),owner,GolemSeals.Type.GUARD); data.configureControls(data.get(key),owner,0,0,true,false);
        var victim=target(h,9,flying?7:1,9,4); var g=worker(h,GolemDesign.fromIds(3,1,4,flying?3:0,0,0),owner,1,1,1,true); g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.GOLD_INGOT,7)); var beforeKill=new AtomicInteger(-1); var actualDartKill=new AtomicBoolean();
        Consumer<LivingDeathEvent> death=e->{ if(e.getEntity()==victim&&e.getSource().getEntity()==g&&e.getSource().getDirectEntity() instanceof GolemDart) { beforeKill.set(g.rankXp()); actualDartKill.set(true); } }; NeoForge.EVENT_BUS.addListener(death); h.runAfterDelay(349,()->NeoForge.EVENT_BUS.unregister(death));
        h.onEachTick(()->h.assertTrue(g.getMainHandItem().getCount()==7,"Source automatic darts do not debit or convert actual cargo7"));
        h.succeedWhen(()->{ h.assertTrue(actualDartKill.get()&&!victim.isAlive()&&beforeKill.get()>=1&&g.rankXp()==beforeKill.get(),"Actual projectile killed original hostile, source ranged kill does not add meleeXP8; before="+beforeKill.get()+", after="+g.rankXp()); if(flying) h.assertTrue(g.isNoGravity(),"FLYER and RANGED functions coexist"); NeoForge.EVENT_BUS.unregister(death); data.remove(key.pos(),key.face(),owner); discardDarts(h,g); victim.discard(); g.discard(); });
    }
    @GameTest(template="golem_climb_test",batch="ranged_guard",timeoutTicks=350)
    public static void actualGroundGuardKillsWithRealDartAndNoMeleeKillXpOrAmmoDebit(GameTestHelper h) { guard(h,false); }
    @GameTest(template="golem_climb_test",batch="ranged_air",timeoutTicks=350)
    public static void actualFlyingGuardUsesRealRangedProjectileAgainstFloatingHostile(GameTestHelper h) { guard(h,true); }
    @GameTest(template="golem_climb_test",batch="ranged_clock",timeoutTicks=90)
    public static void realServerTicksRespectSourceShotClockAtTwentyTwoTickIntervals(GameTestHelper h) {
        floor(h); var g=worker(h,design(),UUID.randomUUID(),3,1,5,false); var victim=target(h,10,1,5,100); g.setTarget(victim); var goal=new GolemRangedGoal(g); var tick=new AtomicInteger(); var shots=new ArrayList<Integer>();
        Consumer<EntityJoinLevelEvent> spawned=e->{ if(e.getEntity() instanceof GolemDart dart&&dart.getOwner()==g) shots.add(tick.get()); }; NeoForge.EVENT_BUS.addListener(spawned); h.runAfterDelay(89,()->NeoForge.EVENT_BUS.unregister(spawned));
        h.onEachTick(()->{ tick.incrementAndGet(); g.getSensing().tick(); goal.tick(); });
        h.runAfterDelay(60,()->{ try { h.assertTrue(shots.equals(List.of(23,45)),"Source first initialization then actual22tick shots; observed="+shots); h.assertTrue(g.rankXp()==0,"Manual source ranged behavior gives no fabricated task XP"); h.succeed(); } finally { NeoForge.EVENT_BUS.unregister(spawned); goal.stop(); discardDarts(h,g); victim.discard(); g.discard(); } });
    }
    @GameTest(template="golem_climb_test",batch="ranged_los",timeoutTicks=150)
    public static void hiddenOutOfRangeAndDeadTargetsNeverSpawnDarts(GameTestHelper h) {
        floor(h); var g=worker(h,design(),UUID.randomUUID(),1,1,1,false); var victim=target(h,10,1,1,100); g.setTarget(victim); var goal=new GolemRangedGoal(g);
        for(int y=1;y<=4;y++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(6,y,z),Blocks.STONE);
        h.onEachTick(()->{ g.getSensing().tick(); goal.tick(); });
        h.runAfterDelay(60,()->{ h.assertTrue(darts(h,g).isEmpty(),"Actual opaque wall/line-of-sight gate prevents all shots"); for(int y=1;y<=4;y++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(6,y,z),Blocks.AIR); victim.setNoGravity(true); victim.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,9,11)))); goal.stop();
            h.runAfterDelay(60,()->{ try { h.assertTrue(g.distanceToSqr(victim)>256&&darts(h,g).isEmpty(),"Visible target outside original16block radius cannot be shot"); victim.discard(); h.assertTrue(!goal.canUse()&&!goal.canContinueToUse(),"Removed target stops source adapter instead of orphan projectile loop"); h.succeed(); } finally { goal.stop(); discardDarts(h,g); victim.discard(); g.discard(); } });
        });
    }
    @GameTest(template="golem_climb_test",batch="ranged_ballistics",timeoutTicks=40)
    public static void actualDartOwnerSizePickupAndLegacyGaussianZeroSpreadVector(GameTestHelper h) {
        floor(h); var g=worker(h,design(),UUID.randomUUID(),3,1,5,false); var dart=new GolemDart(h.getLevel(),g); dart.setBaseDamage(2.75); dart.shootLegacy(3,4,0,1.6F,0);
        h.assertTrue(dart.getOwner()==g&&Math.abs(dart.getBbWidth()-.2)<1e-6&&Math.abs(dart.getBbHeight()-.2)<1e-6&&dart.pickup==AbstractArrow.Pickup.DISALLOWED,"Source NPC dart dimensions .2/owner/no arrow pickup");
        h.assertTrue(dart.getDeltaMovement().distanceToSqr(new Vec3(.96,1.28,0))<1e-12&&dart.getBaseDamage()==2.75,"Actual native dart uses original float-normalized launch and independent base damage"); g.discard(); h.succeed();
    }
    @GameTest(template="golem_climb_test",batch="ranged_nbt",timeoutTicks=40)
    public static void realDartNativeNbtPreservesOwnerDamageMotionAndNoPickup(GameTestHelper h) {
        floor(h); var g=worker(h,design(),UUID.randomUUID(),3,1,5,false); var dart=new GolemDart(h.getLevel(),g); dart.setBaseDamage(2.75); dart.shootLegacy(3,4,0,1.6F,0); var tag=dart.saveWithoutId(new CompoundTag()); var loaded=GolemModule.GOLEM_DART.get().create(h.getLevel()); loaded.load(tag);
        h.assertTrue(loaded.getUUID().equals(dart.getUUID())&&loaded.getOwner()==g&&loaded.getBaseDamage()==2.75&&loaded.getDeltaMovement().distanceToSqr(dart.getDeltaMovement())<1e-12&&loaded.pickup==AbstractArrow.Pickup.DISALLOWED,"Real inherited projectile NBT preserves actual state, owner UUID and no free-arrow status"); g.discard(); h.succeed();
    }
    @GameTest(template="golem_climb_test",batch="ranged_pickup",timeoutTicks=65)
    public static void embeddedNpcDartCannotBePickedUpWhileOrdinaryArrowControlCan(GameTestHelper h) {
        floor(h); var g=worker(h,design(),UUID.randomUUID(),2,4,5,false); var dart=new GolemDart(h.getLevel(),g); dart.shootLegacy(1,-.6,0,1.6F,0); h.getLevel().addFreshEntity(dart);
        var ordinary=EntityType.ARROW.create(h.getLevel()); ordinary.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,4,7)))); ordinary.pickup=AbstractArrow.Pickup.ALLOWED; ordinary.shoot(1,-.6,0,1.6F,0); h.getLevel().addFreshEntity(ordinary);
        h.runAfterDelay(35,()->{ var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"DartPickup")); p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent();
            try { h.assertTrue(dart.saveWithoutId(new CompoundTag()).getBoolean("inGround")&&ordinary.saveWithoutId(new CompoundTag()).getBoolean("inGround"),"Both arrows really struck the ground before pickup test"); p.moveTo(dart.position()); dart.playerTouch(p); h.assertTrue(p.getInventory().isEmpty()&&!dart.isRemoved(),"Actual NPC dart cannot create a free inventory arrow"); p.moveTo(ordinary.position()); ordinary.playerTouch(p); h.assertTrue(ordinary.isRemoved()&&p.getInventory().countItem(Items.ARROW)==1,"Same real player pickup path works for ALLOWED vanilla positive control"); h.succeed(); }
            finally { p.getInventory().clearContent(); dart.discard(); ordinary.discard(); g.discard(); }
        });
    }
    @GameTest(template="golem_climb_test",batch="ranged_paid",timeoutTicks=350)
    public static void pressActuallyDebitsThirtyTwoArrowsAndFiftyFourMachinaThenSpawnsWorkingDarts(GameTestHelper h) {
        floor(h); var d=GolemDesign.fromIds(0,1,4,0,0,0); var owner=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"DartBuilder")); owner.setGameMode(GameType.SURVIVAL); owner.getInventory().clearContent(); owner.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6,1,5)))); GolemRuntimeRules.research(d).forEach(owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge()::addResearch);
        h.setBlock(new BlockPos(5,1,5),GolemModule.PRESS.get()); var press=(GolemPressBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,1,5))); h.assertTrue(press.select(owner,1,1)&&press.select(owner,2,1)&&press.select(owner,2,1)&&press.selection().equals(d),"Existing editor selects researched SMART darts through legal parts"); int slot=0; for(var part:GolemAssembly.components(d)) owner.getInventory().setItem(slot++,GolemComponents.stack(part)); h.assertTrue(owner.getInventory().countItem(Items.ARROW)==32,"Actual source assembly contains32 real arrows");
        h.setBlock(new BlockPos(5,0,5),ThaumcraftBlocks.JAR_NORMAL.get()); var jar=(dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,0,5))); for(int n=0;n<54;n++) h.assertTrue(jar.addFromTransport("machina",1,Direction.UP)==1,"Actual54Machina supply"); h.assertTrue(press.start(owner)&&press.job().maximum()==54&&owner.getInventory().isEmpty(),"Source42actualcomponents plus6traits×2, no fictitious ammo"); var result=new AtomicReference<ThaumcraftGolem>(); var victim=target(h,3,1,8,100);
        h.runAfterDelay(290,()->{ h.assertTrue(!press.job().active()&&jar.snapshot().amount()==0,"Actual paid dart construction completed"); owner.setItemInHand(InteractionHand.MAIN_HAND,press.removeItem(0,1)); var pos=h.absolutePos(new BlockPos(9,0,8)); var ctx=new UseOnContext(owner,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false)); h.assertTrue(GolemModule.GOLEM_PLACER.get().useOn(ctx).consumesAction()&&owner.getMainHandItem().isEmpty(),"One actual configured placer consumed"); var entities=h.getLevel().getEntitiesOfClass(ThaumcraftGolem.class,h.getBounds()); h.assertTrue(entities.size()==1&&entities.getFirst().ranged()&&!entities.getFirst().isNoAi(),"Exactly one active source dart construction"); result.set(entities.getFirst()); result.get().setTarget(victim); });
        h.succeedWhen(()->{ var g=result.get(); h.assertTrue(g!=null&&!darts(h,g).isEmpty(),"Paid source construction actually fires native projectile"); discardDarts(h,g); victim.discard(); g.discard(); h.succeed(); });
    }
    @GameTest(template="golem_climb_test",batch="ranged_trait",timeoutTicks=40)
    public static void ordinaryAndClawConstructionsCannotInvokeUnownedDartFunction(GameTestHelper h) {
        floor(h); var owner=UUID.randomUUID(); var ordinary=worker(h,GolemDesign.defaultWood(),owner,2,1,3,false); var claws=worker(h,GolemDesign.fromIds(0,1,2,0,0,0),owner,2,1,7,false); var victim=target(h,8,1,5,100); ordinary.performRangedAttack(victim,.5F); claws.performRangedAttack(victim,.5F);
        h.assertTrue(darts(h,ordinary).isEmpty()&&darts(h,claws).isEmpty()&&!new GolemRangedGoal(ordinary).canUse()&&!new GolemRangedGoal(claws).canUse(),"Only actual source RANGED trait exposes dart behavior"); victim.discard(); ordinary.discard(); claws.discard(); h.succeed();
    }
    @GameTest(template="golem_climb_test",batch="ranged_owner",timeoutTicks=40)
    public static void loadedOwnerAlliancePreventsDirectAndGoalRangedAttack(GameTestHelper h) {
        floor(h); var owner=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"DartOwner")); h.getLevel().players().add(owner); var g=worker(h,design(),owner.getUUID(),3,1,5,false); owner.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,1,5))));
        try { g.setTarget(owner); var goal=new GolemRangedGoal(g); h.assertTrue(!goal.canUse(),"Actual loaded owner cannot be selected for ranged attack"); g.performRangedAttack(owner,.5F); h.assertTrue(darts(h,g).isEmpty(),"Direct ranged callback also respects source owner/team alliance"); h.succeed(); }
        finally { h.getLevel().players().remove(owner); owner.discard(); g.discard(); }
    }
}
