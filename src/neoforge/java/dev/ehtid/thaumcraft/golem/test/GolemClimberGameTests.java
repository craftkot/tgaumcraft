package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
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
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.UUID;
import java.util.concurrent.atomic.*;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemClimberGameTests {
    private GolemClimberGameTests() {}
    private static GolemDesign design() { return GolemDesign.fromIds(0,0,0,2,0,0); }
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
    private static void wall(GameTestHelper h) { floor(h); for(int x=6;x<=10;x++) for(int z=0;z<12;z++) for(int y=1;y<=5;y++) h.setBlock(new BlockPos(x,y,z),Blocks.STONE); }
    private static ThaumcraftGolem worker(GameTestHelper h,GolemDesign design,UUID owner,int x,int z) {
        var g=GolemModule.GOLEM.get().create(h.getLevel()); var pos=h.absolutePos(new BlockPos(x,1,z)); g.moveTo(Vec3.atBottomCenterOf(pos)); g.initialize(design,owner,pos,0); h.getLevel().addFreshEntity(g); return g;
    }
    private static ItemEntity item(GameTestHelper h,int count) {
        var pos=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6,6,5))); var item=new ItemEntity(h.getLevel(),pos.x,pos.y,pos.z,new ItemStack(Items.IRON_INGOT,count)); item.setDeltaMovement(Vec3.ZERO); item.setNoPickUpDelay(); h.getLevel().addFreshEntity(item); return item;
    }
    @GameTest(template="golem_climb_test",batch="climber_motion",timeoutTicks=200)
    public static void realWallNavigationAndCollisionFlagClimbFiveBlocksWhileWalkerCannot(GameTestHelper h) {
        wall(h); var owner=UUID.randomUUID(); var g=worker(h,design(),owner,2,5); var walker=worker(h,GolemDesign.defaultWood(),owner,2,3); var target=item(h,1); g.setFollowingOwner(true); walker.setFollowingOwner(true);
        double floor=h.absolutePos(BlockPos.ZERO).getY(); var seen=new AtomicBoolean(); h.onEachTick(()->{ if(g.onClimbable()) seen.set(true); });
        h.runAfterDelay(5,()->{ h.assertTrue(g.getNavigation().moveTo(target,g.design().stats().moveSpeedMultiplier()),"Source WallClimber navigator accepts actual elevated entity target"); walker.getNavigation().moveTo(target,walker.design().stats().moveSpeedMultiplier()); });
        h.succeedWhen(()->{ h.assertTrue(g.getY()-floor>=4.5&&seen.get(),"Real CLIMBER ascends wall with synced horizontal-collision flag; y="+(g.getY()-floor)); h.assertTrue(walker.getY()-floor<2&&!walker.onClimbable(),"Walker cannot climb same five-block vertical barrier"); h.assertTrue(g.getNavigation() instanceof WallClimberNavigation&&!g.isNoAi(),"Correct native navigator with enabled server AI"); g.discard(); walker.discard(); target.discard(); });
    }
    @GameTest(template="golem_climb_test",batch="climber_pickup",timeoutTicks=500)
    public static void actualClimberPickupAboveWallReturnsElevenItemsToGroundContainer(GameTestHelper h) {
        wall(h); var owner=UUID.randomUUID(); var data=GolemSeals.get(h.getLevel()); var source=new GolemSeals.SealKey(h.absolutePos(new BlockPos(6,5,5)),Direction.UP); var sink=new GolemSeals.SealKey(h.absolutePos(new BlockPos(2,1,5)),Direction.UP); h.getLevel().setBlockAndUpdate(sink.pos(),Blocks.BARREL.defaultBlockState());
        data.place(source.pos(),source.face(),owner,GolemSeals.Type.PICKUP); data.place(sink.pos(),sink.face(),owner,GolemSeals.Type.FILL); for(var key:new GolemSeals.SealKey[]{source,sink}) data.configureControls(data.get(key),owner,0,0,true,false);
        var inv=(IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),sink); var paid=item(h,11); var g=worker(h,design(),owner,3,5); var seen=new AtomicBoolean();
        h.onEachTick(()->{ if(g.onClimbable()) seen.set(true); int stored=0; for(int i=0;i<inv.getSlots();i++) stored+=inv.getStackInSlot(i).getCount(); h.assertTrue(stored+g.getMainHandItem().getCount()+(paid.isRemoved()?0:paid.getItem().getCount())==11,"Every vertical pickup/return/deposit tick conserves actual11"); });
        h.succeedWhen(()->{ h.assertTrue(seen.get()&&inv.getStackInSlot(0).is(Items.IRON_INGOT)&&inv.getStackInSlot(0).getCount()==11&&g.getMainHandItem().isEmpty()&&paid.isRemoved(),"Actual CLIMBER seal AI climbs, picks real cargo and descends to deposit11"); data.remove(source.pos(),source.face(),owner); data.remove(sink.pos(),sink.face(),owner); g.discard(); });
    }
    @GameTest(template="golem_climb_test",batch="climber_nbt",timeoutTicks=40)
    public static void packedClimberRebuildsNavigationAfterInitializationAndNativeNbt(GameTestHelper h) {
        floor(h); var g=GolemModule.GOLEM.get().create(h.getLevel()); h.assertTrue(!(g.getNavigation() instanceof WallClimberNavigation),"Default constructor walker is not secretly a climber"); var owner=UUID.randomUUID(); var home=h.absolutePos(new BlockPos(5,1,5)); g.initialize(design(),owner,home,7);
        h.assertTrue(g.getNavigation() instanceof WallClimberNavigation,"Real initialization replaces default walker navigation"); var tag=new CompoundTag(); g.addAdditionalSaveData(tag); var loaded=GolemModule.GOLEM.get().create(h.getLevel()); loaded.readAdditionalSaveData(tag);
        h.assertTrue(loaded.design().equals(design())&&loaded.ownerId().equals(owner)&&loaded.home().equals(home)&&loaded.rankXp()==7&&loaded.getNavigation() instanceof WallClimberNavigation&&!loaded.onClimbable(),"Packed leg2 survives actual NBT while transient climbing collision flag resets");
        loaded.initialize(GolemDesign.defaultWood(),owner,home,0); h.assertTrue(!(loaded.getNavigation() instanceof WallClimberNavigation)&&!loaded.onClimbable(),"Changing to walker clears old navigation and climbing state"); h.succeed();
    }
    @GameTest(template="golem_climb_test",batch="climber_guard",timeoutTicks=260)
    public static void guardClimberActuallyReachesAndKillsHostileAboveVerticalBarrier(GameTestHelper h) {
        wall(h); var owner=UUID.randomUUID(); var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(6,5,5)),Direction.UP); var data=GolemSeals.get(h.getLevel()); data.place(key.pos(),key.face(),owner,GolemSeals.Type.GUARD); data.configureControls(data.get(key),owner,0,0,true,false);
        var victim=EntityType.HUSK.create(h.getLevel()); victim.moveTo(Vec3.atBottomCenterOf(key.pos().above())); victim.setNoAi(true); victim.setHealth(4); h.getLevel().addFreshEntity(victim); var g=worker(h,GolemDesign.fromIds(3,1,2,2,0,0),owner,2,5); var seen=new AtomicBoolean(); h.onEachTick(()->{ if(g.onClimbable()) seen.set(true); });
        var beforeKill=new AtomicInteger(-1);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> death=e->{ if(e.getEntity()==victim&&e.getSource().getEntity()==g) beforeKill.set(g.rankXp()); };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(death); h.runAfterDelay(259,()->net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(death));
        h.succeedWhen(()->{ h.assertTrue(seen.get()&&!victim.isAlive()&&beforeKill.get()>=1&&g.rankXp()==beforeKill.get()+8,"Climbing Guard native kill8 follows actual assignments: before="+beforeKill.get()+", after="+g.rankXp()); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(death); data.remove(key.pos(),key.face(),owner); victim.discard(); g.discard(); });
    }
    @GameTest(template="golem_climb_test",batch="climber_breaker",timeoutTicks=280)
    public static void sourceBlockReachRejectsDistantVerticalTaskThenMinesAfterActualClimb(GameTestHelper h) {
        wall(h); var owner=UUID.randomUUID(); var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(6,5,5)),Direction.UP); var data=GolemSeals.get(h.getLevel()); data.place(key.pos(),key.face(),owner,GolemSeals.Type.BREAKER); data.configureControls(data.get(key),owner,0,0,true,false); data.configureArea(data.get(key),owner,new BlockPos(1,1,1)); h.getLevel().setBlockAndUpdate(key.pos().above(),Blocks.DIAMOND_ORE.defaultBlockState());
        var g=worker(h,GolemDesign.fromIds(0,1,3,2,0,0),owner,2,5); var seen=new AtomicBoolean(); var stage=new AtomicInteger(); double originY=g.getY();
        var pos=Vec3.atBottomCenterOf(key.pos().above(2)); var destination=new ItemEntity(h.getLevel(),pos.x,pos.y,pos.z,new ItemStack(Items.IRON_INGOT)); destination.setDeltaMovement(Vec3.ZERO); destination.setNoPickUpDelay(); h.getLevel().addFreshEntity(destination);
        h.onEachTick(()->{ if(g.onClimbable()) seen.set(true); if(stage.get()==1&&g.getY()-originY>=4) { stage.set(2); g.setFollowingOwner(false); } });
        h.runAfterDelay(40,()->{ h.assertTrue(g.rankXp()==0&&!g.hasWorkTask()&&g.getY()-originY<1&&h.getLevel().getBlockState(key.pos().above()).is(Blocks.DIAMOND_ORE),"Source AIGotoBlock 3D endpoint gate rejects partial path five blocks below; CLIMBER does not bypass it"); g.setFollowingOwner(true); stage.set(1); h.assertTrue(g.getNavigation().moveTo(destination,g.design().stats().moveSpeedMultiplier()),"Real wall climb to an entity position, separate from rejected block task"); });
        h.succeedWhen(()->{ var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds(),e->e.getItem().is(Items.DIAMOND)); h.assertTrue(stage.get()==2&&seen.get()&&h.getLevel().isEmptyBlock(key.pos().above())&&g.rankXp()==1&&drops.stream().mapToInt(e->e.getItem().getCount()).sum()==1,"After actual climb puts block within original 3D reach, native Breaker yields real diamond1; stage="+stage.get()+", xp="+g.rankXp()+", y="+(g.getY()-originY)); data.remove(key.pos(),key.face(),owner); destination.discard(); drops.forEach(ItemEntity::discard); g.discard(); });
    }
    @GameTest(template="golem_climb_test",batch="climber_paid",timeoutTicks=160)
    public static void researchedPressPaysActualClimberComponentsAndFifteenMachinaThenOnePlacer(GameTestHelper h) {
        floor(h); var owner=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"ClimberBuilder")); owner.setGameMode(GameType.SURVIVAL); owner.getInventory().clearContent(); owner.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6,1,5)))); var k=owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(); GolemRuntimeRules.research(design()).forEach(k::addResearch);
        h.setBlock(new BlockPos(5,1,5),GolemModule.PRESS.get()); var press=(GolemPressBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,1,5))); h.assertTrue(press.select(owner,3,1)&&press.select(owner,3,1)&&press.selection().equals(design()),"Existing editor selects researched source climber through wheel option"); int slot=0; for(var c:GolemAssembly.components(design())) owner.getInventory().setItem(slot++,GolemComponents.stack(c));
        h.setBlock(new BlockPos(5,0,5),ThaumcraftBlocks.JAR_NORMAL.get()); var jar=(dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,0,5))); for(int n=0;n<15;n++) h.assertTrue(jar.addFromTransport("machina",1,Direction.UP)==1,"Real15Machina supply"); h.assertTrue(press.start(owner)&&press.job().maximum()==15&&owner.getInventory().isEmpty(),"Source11actualcomponents+2traits×2 require15Machina");
        h.runAfterDelay(95,()->{ h.assertTrue(!press.job().active()&&jar.snapshot().amount()==0,"Actual researched job paid exactly15units"); owner.setItemInHand(InteractionHand.MAIN_HAND,press.removeItem(0,1)); var pos=h.absolutePos(new BlockPos(9,0,8)); var ctx=new UseOnContext(owner,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false));
            h.assertTrue(GolemModule.GOLEM_PLACER.get().useOn(ctx).consumesAction()&&owner.getMainHandItem().isEmpty(),"One actual climber placer consumed"); var entities=h.getLevel().getEntitiesOfClass(ThaumcraftGolem.class,h.getBounds()); h.assertTrue(entities.size()==1&&entities.getFirst().design().equals(design())&&entities.getFirst().getNavigation() instanceof WallClimberNavigation&&!entities.getFirst().isNoAi(),"One real enabled climber with correct initialization navigation"); entities.getFirst().discard(); h.succeed(); });
    }
    @GameTest(template="golem_climb_test",batch="climber_fall",timeoutTicks=40)
    public static void sourceClimberFallImmunityPreventsActualDamageWhileWalkerStillFalls(GameTestHelper h) {
        floor(h); var owner=UUID.randomUUID(); var climber=worker(h,design(),owner,2,5); var walker=worker(h,GolemDesign.defaultWood(),owner,2,3); climber.setNoAi(true); walker.setNoAi(true); float health=climber.getHealth(); float walkingHealth=walker.getHealth(); var damage=h.getLevel().damageSources().fall();
        h.assertTrue(!climber.causeFallDamage(10,1,damage)&&climber.getHealth()==health,"Source CLIMBER rejects actual fall damage");
        h.assertTrue(walker.causeFallDamage(10,1,damage)&&walker.getHealth()<walkingHealth,"Ordinary walker retains real native fall damage"); climber.discard(); walker.discard(); h.succeed();
    }

}
