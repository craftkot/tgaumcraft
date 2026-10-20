package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.ai.*;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.press.*;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.item.GolemPlacerItem;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.UUID;
import java.util.concurrent.atomic.*;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemFlightGameTests {
    private GolemFlightGameTests() {}
    private static GolemDesign design() { return GolemDesign.fromIds(0,0,0,3,0,0); }
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
    private static ThaumcraftGolem worker(GameTestHelper h,GolemDesign design,UUID owner,int x,int y,int z) {
        var g=GolemModule.GOLEM.get().create(h.getLevel()); var pos=h.absolutePos(new BlockPos(x,y,z)); g.moveTo(Vec3.atBottomCenterOf(pos)); g.initialize(design,owner,pos,0); h.getLevel().addFreshEntity(g); return g;
    }
    private static ItemEntity item(GameTestHelper h,BlockPos relative,int count) {
        var pos=Vec3.atBottomCenterOf(h.absolutePos(relative)); var item=new ItemEntity(h.getLevel(),pos.x,pos.y,pos.z,new ItemStack(Items.IRON_INGOT,count)); item.setDeltaMovement(Vec3.ZERO); item.setNoPickUpDelay(); h.getLevel().addFreshEntity(item); return item;
    }
    @GameTest(template="golem_climb_test",batch="flight_motion",timeoutTicks=280)
    public static void actualAirNavigationCrossesGapAndDetoursAroundOpaqueWallWithoutNoclip(GameTestHelper h) {
        floor(h); for(int x=0;x<12;x++) if(x<=2||x>=9) for(int z=0;z<12;z++) for(int y=1;y<=3;y++) h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        for(int y=1;y<=6;y++) for(int z=0;z<=8;z++) h.setBlock(new BlockPos(6,y,z),Blocks.STONE);
        var owner=UUID.randomUUID(); var g=worker(h,design(),owner,1,4,5); var walker=worker(h,GolemDesign.defaultWood(),owner,1,4,3); var target=item(h,new BlockPos(10,4,5),1); g.setFollowingOwner(true); walker.setFollowingOwner(true); var detour=new AtomicBoolean(); double baseY=h.absolutePos(BlockPos.ZERO).getY(); double baseZ=h.absolutePos(BlockPos.ZERO).getZ();
        h.onEachTick(()->{ if(g.getY()-baseY>6.5||g.getZ()-baseZ>8.8) detour.set(true); h.assertTrue(!g.noPhysics&&g.isNoGravity(),"Flight keeps real collision and actual gravity exemption"); });
        h.runAfterDelay(5,()->{ h.assertTrue(g.getNavigation().moveTo(target,g.design().stats().moveSpeedMultiplier()),"Actual source six-neighbor air path exists around wall"); walker.getNavigation().moveTo(target,walker.design().stats().moveSpeedMultiplier()); });
        h.succeedWhen(()->{ h.assertTrue(g.distanceToSqr(target)<3.5&&detour.get(),"Actual flyer crosses gap via clear detour, not straight through stone; worker="+g.position()); h.assertTrue(!walker.isNoGravity()&&walker.distanceToSqr(target)>9,"Ground walker cannot follow same aerial path"); g.discard(); walker.discard(); target.discard(); });
    }
    @GameTest(template="golem_climb_test",batch="flight_control",timeoutTicks=40)
    public static void nativeFlightControllerUsesSourceAccelerationAndArrivalBrake(GameTestHelper h) {
        floor(h); var g=worker(h,design(),UUID.randomUUID(),3,4,5); g.setNoAi(true); h.assertTrue(g.getMoveControl() instanceof GolemFlightMoveControl&&g.getNavigation() instanceof GolemFlightNavigation,"Actual initialized controller/navigation are source air adapters");
        g.setDeltaMovement(.2,-.1,.3); g.getMoveControl().setWantedPosition(g.getX(),g.getY(),g.getZ(),1); g.getMoveControl().tick(); h.assertTrue(g.getDeltaMovement().distanceToSqr(new Vec3(.1,-.05,.15))<1e-12,"Source arrival halves all three actual velocity components");
        g.setDeltaMovement(Vec3.ZERO); g.getMoveControl().setWantedPosition(g.getX(),g.getY()+10,g.getZ(),1); g.getMoveControl().tick(); h.assertTrue(g.getDeltaMovement().distanceToSqr(new Vec3(0,.0125,0))<1e-12,"Source vertical acceleration .0125, not ground jump or teleport");
        float health=g.getHealth(); h.assertTrue(!g.causeFallDamage(20,1,h.getLevel().damageSources().fall())&&g.getHealth()==health,"Native FLYER source fall immunity"); g.discard(); h.succeed();
    }
    @GameTest(template="golem_climb_test",batch="flight_nodes",timeoutTicks=40)
    public static void sourceFlightEvaluatorHasSixAxisNeighborsAndRejectsSolidCell(GameTestHelper h) {
        floor(h); var g=worker(h,design(),UUID.randomUUID(),5,5,5); g.setNoAi(true); var evaluator=g.getNavigation().getNodeEvaluator(); evaluator.prepare(new PathNavigationRegion(h.getLevel(),h.absolutePos(BlockPos.ZERO),h.absolutePos(new BlockPos(11,9,11))),g);
        try { var start=evaluator.getStart(); var output=new Node[32]; int count=evaluator.getNeighbors(output,start); h.assertTrue(count==6,"Source air evaluator exposes exactly six axis neighbors, no invented diagonals");
            for(int n=0;n<count;n++) h.assertTrue(Math.abs(output[n].x-start.x)+Math.abs(output[n].y-start.y)+Math.abs(output[n].z-start.z)==1,"Every actual neighbor is one axis step");
            h.getLevel().setBlockAndUpdate(new BlockPos(start.x+1,start.y,start.z),Blocks.STONE.defaultBlockState()); h.assertTrue(evaluator.getNeighbors(output,start)==5,"Real opaque voxel removes exactly one air neighbor"); h.succeed();
        } finally { evaluator.done(); g.discard(); }
    }
    @GameTest(template="golem_climb_test",batch="flight_nbt",timeoutTicks=40)
    public static void actualFlyerNbtRebuildsControllerNavigationAndGravityThenWalkerResetsThem(GameTestHelper h) {
        floor(h); var g=worker(h,design(),UUID.randomUUID(),3,5,5); g.setNoAi(true); var tag=new CompoundTag(); g.addAdditionalSaveData(tag); var loaded=GolemModule.GOLEM.get().create(h.getLevel()); loaded.readAdditionalSaveData(tag);
        h.assertTrue(loaded.design().equals(design())&&loaded.ownerId().equals(g.ownerId())&&loaded.getNavigation() instanceof GolemFlightNavigation&&loaded.getMoveControl() instanceof GolemFlightMoveControl&&loaded.isNoGravity(),"Real native NBT rebuilds actual flight adapters from packed leg3");
        loaded.initialize(GolemDesign.defaultWood(),g.ownerId(),g.home(),0); h.assertTrue(!(loaded.getNavigation() instanceof GolemFlightNavigation)&&!(loaded.getMoveControl() instanceof GolemFlightMoveControl)&&!loaded.isNoGravity(),"Explicit walker reinitialization clears old source flight functions"); g.discard(); h.succeed();
    }
    @GameTest(template="golem_climb_test",batch="flight_pickup",timeoutTicks=450)
    public static void actualFlyerPickupOnFloatingPlatformReturnsElevenItemsToGroundContainer(GameTestHelper h) {
        floor(h); var owner=UUID.randomUUID(); var source=new GolemSeals.SealKey(h.absolutePos(new BlockPos(7,6,5)),Direction.UP); var sink=new GolemSeals.SealKey(h.absolutePos(new BlockPos(2,1,5)),Direction.UP); var data=GolemSeals.get(h.getLevel()); h.getLevel().setBlockAndUpdate(source.pos(),Blocks.STONE.defaultBlockState()); h.getLevel().setBlockAndUpdate(sink.pos(),Blocks.BARREL.defaultBlockState());
        data.place(source.pos(),source.face(),owner,GolemSeals.Type.PICKUP); data.place(sink.pos(),sink.face(),owner,GolemSeals.Type.FILL); for(var key:new GolemSeals.SealKey[]{source,sink}) data.configureControls(data.get(key),owner,0,0,true,false); var inv=(IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),sink); var paid=item(h,new BlockPos(7,7,5),11); var g=worker(h,design(),owner,3,1,5); var elevated=new AtomicBoolean(); double startY=g.getY();
        h.onEachTick(()->{ if(g.getY()-startY>3) elevated.set(true); int stored=0; for(int i=0;i<inv.getSlots();i++) stored+=inv.getStackInSlot(i).getCount(); h.assertTrue(stored+g.getMainHandItem().getCount()+(paid.isRemoved()?0:paid.getItem().getCount())==11,"Every source aerial pickup/deposit tick conserves actual11"); });
        h.succeedWhen(()->{ h.assertTrue(elevated.get()&&inv.getStackInSlot(0).is(Items.IRON_INGOT)&&inv.getStackInSlot(0).getCount()==11&&g.getMainHandItem().isEmpty()&&paid.isRemoved(),"Real flyer autonomously reaches unsupported high platform and deposits actual11 at ground"); data.remove(source.pos(),source.face(),owner); data.remove(sink.pos(),sink.face(),owner); g.discard(); });
    }
    @GameTest(template="golem_climb_test",batch="flight_guard",timeoutTicks=300)
    public static void actualFlyerGuardNavigatesToFloatingHostileAndPerformsNativeMeleeKill(GameTestHelper h) {
        floor(h); var owner=UUID.randomUUID(); var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(7,6,5)),Direction.UP); var data=GolemSeals.get(h.getLevel()); h.getLevel().setBlockAndUpdate(key.pos(),Blocks.STONE.defaultBlockState()); data.place(key.pos(),key.face(),owner,GolemSeals.Type.GUARD); data.configureControls(data.get(key),owner,0,0,true,false);
        var victim=EntityType.HUSK.create(h.getLevel()); victim.moveTo(Vec3.atBottomCenterOf(key.pos().above())); victim.setNoAi(true); victim.setHealth(4); h.getLevel().addFreshEntity(victim); var g=worker(h,GolemDesign.fromIds(3,1,2,3,0,0),owner,3,1,5); var beforeKill=new AtomicInteger(-1); var elevated=new AtomicBoolean(); double startY=g.getY();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> death=e->{ if(e.getEntity()==victim&&e.getSource().getEntity()==g) beforeKill.set(g.rankXp()); }; NeoForge.EVENT_BUS.addListener(death); h.runAfterDelay(299,()->NeoForge.EVENT_BUS.unregister(death)); h.onEachTick(()->{ if(g.getY()-startY>3) elevated.set(true); });
        h.succeedWhen(()->{ h.assertTrue(elevated.get()&&!victim.isAlive()&&beforeKill.get()>=1&&g.rankXp()==beforeKill.get()+8,"Actual aerial Guard assignment/navigation/native kill8; before="+beforeKill.get()+", after="+g.rankXp()); NeoForge.EVENT_BUS.unregister(death); data.remove(key.pos(),key.face(),owner); victim.discard(); g.discard(); });
    }
    @GameTest(template="golem_climb_test",batch="flight_breaker",timeoutTicks=350)
    public static void actualFlyerBreakerUsesThreeDimensionalPathToHarvestFloatingOreOnce(GameTestHelper h) {
        floor(h); var owner=UUID.randomUUID(); var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(7,5,5)),Direction.UP); var data=GolemSeals.get(h.getLevel()); h.getLevel().setBlockAndUpdate(key.pos(),Blocks.STONE.defaultBlockState()); h.getLevel().setBlockAndUpdate(key.pos().above(),Blocks.DIAMOND_ORE.defaultBlockState()); data.place(key.pos(),key.face(),owner,GolemSeals.Type.BREAKER); data.configureControls(data.get(key),owner,0,0,true,false); data.configureArea(data.get(key),owner,new BlockPos(1,1,1)); var g=worker(h,GolemDesign.fromIds(0,1,3,3,0,0),owner,3,1,5);
        h.succeedWhen(()->{ var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds(),e->e.getItem().is(Items.DIAMOND)); h.assertTrue(h.getLevel().isEmptyBlock(key.pos().above())&&g.rankXp()==1&&drops.stream().mapToInt(e->e.getItem().getCount()).sum()==1,"Actual air path meets original block XYZ reach and native harvest yields diamond1; xp="+g.rankXp()+", worker="+g.position()); data.remove(key.pos(),key.face(),owner); drops.forEach(ItemEntity::discard); g.discard(); });
    }
    @GameTest(template="golem_climb_test",batch="flight_acquisition",timeoutTicks=80)
    public static void missingOriginalLevitatorCannotBecomeFreePressProductButActualTestPlacerChargesOnce(GameTestHelper h) {
        floor(h); var owner=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"FlyerBuilder")); owner.setGameMode(GameType.SURVIVAL); owner.getInventory().clearContent(); owner.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6,1,5)))); var k=owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(); GolemRuntimeRules.research(design()).forEach(k::addResearch); h.setBlock(new BlockPos(5,1,5),GolemModule.PRESS.get()); var press=(GolemPressBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,1,5)));
        h.assertTrue(press.select(owner,3,1)&&press.select(owner,3,1)&&press.selection().equals(design()),"Known wheels then source flyer selected; unresearched climber skipped");
        int slot=0,missing=0; for(var component:GolemAssembly.components(design())) { var stack=GolemComponents.stack(component); if(stack.isEmpty()) { h.assertTrue(component.legacyItemId().equals("thaumcraft:levitator"),"Only source unported levitator dependency is missing"); missing++; } else owner.getInventory().setItem(slot++,stack); }
        int before=owner.getInventory().items.stream().mapToInt(ItemStack::getCount).sum(); h.assertTrue(missing==1&&!press.start(owner)&&!press.job().active()&&press.isEmpty()&&owner.getInventory().items.stream().mapToInt(ItemStack::getCount).sum()==before,"No substitute levitator/free product or partial input debit");
        owner.getInventory().clearContent(); owner.setItemInHand(InteractionHand.MAIN_HAND,GolemPlacerItem.stack(design(),0)); var pos=h.absolutePos(new BlockPos(9,0,8)); var ctx=new UseOnContext(owner,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false)); h.assertTrue(GolemModule.GOLEM_PLACER.get().useOn(ctx).consumesAction()&&owner.getMainHandItem().isEmpty(),"Actual separately supplied test placer consumed once, not a survival acquisition claim"); var entities=h.getLevel().getEntitiesOfClass(ThaumcraftGolem.class,h.getBounds()); h.assertTrue(entities.size()==1&&entities.getFirst().isNoGravity()&&entities.getFirst().getNavigation() instanceof GolemFlightNavigation&&!entities.getFirst().isNoAi(),"One actual enabled flying entity from supported placer"); entities.getFirst().discard(); h.succeed();
    }
}
