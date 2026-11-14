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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.UUID;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemWheelGameTests {
    private GolemWheelGameTests() {}
    private static GolemDesign design() { return GolemDesign.fromIds(0,0,0,1,0,0); }
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
    private static ThaumcraftGolem worker(GameTestHelper h,GolemDesign design,UUID owner,int x,int z) {
        var g=GolemModule.GOLEM.get().create(h.getLevel()); var pos=h.absolutePos(new BlockPos(x,1,z)); g.moveTo(Vec3.atBottomCenterOf(pos)); g.initialize(design,owner,pos,0); h.getLevel().addFreshEntity(g); return g;
    }
    @GameTest(template="golem_test",batch="wheel_motion",timeoutTicks=80)
    public static void actualGroundNavigationRollsFasterWithSourceMultiplierAndHalfBlockStep(GameTestHelper h) {
        floor(h); var owner=UUID.randomUUID(); var walking=worker(h,GolemDesign.defaultWood(),owner,1,3); var rolling=worker(h,design(),owner,1,7);
        walking.setFollowingOwner(true); rolling.setFollowingOwner(true); // No online owner: exclude unrelated seal goals while real navigation ticks.
        var a=h.absolutePos(new BlockPos(10,1,3)); var b=h.absolutePos(new BlockPos(10,1,7));
        var frames=new java.util.concurrent.atomic.AtomicInteger(); var walkingAt=new java.util.concurrent.atomic.AtomicInteger(-1); var rollingAt=new java.util.concurrent.atomic.AtomicInteger(-1); var started=new java.util.concurrent.atomic.AtomicBoolean(); double origin=walking.getX();
        h.runAfterDelay(5,()->{
            try { h.assertTrue(walking.getNavigation().moveTo(a.getX()+.5,a.getY(),a.getZ()+.5,walking.design().stats().moveSpeedMultiplier())&&rolling.getNavigation().moveTo(b.getX()+.5,b.getY(),b.getZ()+.5,rolling.design().stats().moveSpeedMultiplier()),"Both actual grounded navigation paths exist"); started.set(true); }
            catch(RuntimeException|AssertionError failure) { walking.discard(); rolling.discard(); throw failure; }
        });
        h.onEachTick(()->{ if(!started.get()) return; int tick=frames.incrementAndGet(); if(walking.getX()-origin>=4) walkingAt.compareAndSet(-1,tick); if(rolling.getX()-origin>=4) rollingAt.compareAndSet(-1,tick); });
        h.succeedWhen(()->{ h.assertTrue(rollingAt.get()>0&&walkingAt.get()>0,"Both native paths traverse four actual ground blocks"); h.assertTrue(rollingAt.get()<walkingAt.get(),"Source wheel1.45 reaches four blocks before lightwalker1.2: wheel="+rollingAt.get()+", walker="+walkingAt.get()); h.assertTrue(rolling.getAttributeValue(Attributes.STEP_HEIGHT)==.5&&walking.getAttributeValue(Attributes.STEP_HEIGHT)>.59&&!rolling.isNoAi(),"Source wheel step0.5 versuswalker0.6, real enabled mob"); walking.discard(); rolling.discard(); h.succeed(); });
    }

    @GameTest(template="golem_test",batch="wheel_automation",timeoutTicks=350)
    public static void wheeledGolemActuallyNavigatesPickupToContainerWithoutDuplicatingCargo(GameTestHelper h) {
        floor(h); var owner=UUID.randomUUID(); var data=GolemSeals.get(h.getLevel()); var source=new GolemSeals.SealKey(h.absolutePos(new BlockPos(2,0,5)),Direction.UP); var sink=new GolemSeals.SealKey(h.absolutePos(new BlockPos(9,1,5)),Direction.UP); h.getLevel().setBlockAndUpdate(sink.pos(),Blocks.BARREL.defaultBlockState());
        data.place(source.pos(),source.face(),owner,GolemSeals.Type.PICKUP); data.place(sink.pos(),sink.face(),owner,GolemSeals.Type.FILL); for(var key:new GolemSeals.SealKey[]{source,sink}) data.configureControls(data.get(key),owner,0,0,true,false);
        var inv=(IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),sink); var p=h.absolutePos(new BlockPos(3,1,5)); var item=new ItemEntity(h.getLevel(),p.getX()+.5,p.getY(),p.getZ()+.5,new ItemStack(Items.IRON_INGOT,11)); item.setDeltaMovement(Vec3.ZERO); item.setNoPickUpDelay(); h.getLevel().addFreshEntity(item); var g=worker(h,design(),owner,6,5);
        h.onEachTick(()->{ int stored=0; for(int i=0;i<inv.getSlots();i++) stored+=inv.getStackInSlot(i).getCount(); h.assertTrue(stored+g.getMainHandItem().getCount()+(item.isRemoved()?0:item.getItem().getCount())==11,"Every actual rolling pickup/deposit tick conserves11"); });
        h.succeedWhen(()->{ h.assertTrue(inv.getStackInSlot(0).is(Items.IRON_INGOT)&&inv.getStackInSlot(0).getCount()==11&&g.getMainHandItem().isEmpty()&&item.isRemoved(),"WHEELED worker uses real source seal AI/navigation/capability transfers"); data.remove(source.pos(),source.face(),owner); data.remove(sink.pos(),sink.face(),owner); g.discard(); });
    }
    @GameTest(template="golem_test",batch="wheel_paid",timeoutTicks=150)
    public static void pressPaysExactWheelPartsAndTwelveMachinaThenActualPlacerSpawnsPersistentWorker(GameTestHelper h) {
        floor(h); var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"WheelBuilder")); p.setGameMode(GameType.SURVIVAL); p.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6,1,5)))); p.getInventory().clearContent(); var k=p.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(); GolemRuntimeRules.research(design()).forEach(k::addResearch);
        h.setBlock(new BlockPos(5,1,5),GolemModule.PRESS.get()); var press=(GolemPressBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,1,5))); h.assertTrue(press.select(p,3,1)&&press.selection().equals(design()),"Existing editor selects researched supported wheels"); int slot=0; for(var component:GolemAssembly.components(design())) p.getInventory().setItem(slot++,GolemComponents.stack(component));
        h.setBlock(new BlockPos(5,0,5),ThaumcraftBlocks.JAR_NORMAL.get()); var jar=(dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,0,5))); for(int n=0;n<12;n++) h.assertTrue(jar.addFromTransport("machina",1,Direction.UP)==1,"Real source Machina supply");
        h.assertTrue(press.start(p)&&press.job().maximum()==12&&p.getInventory().isEmpty(),"Exactly2wood+2mechanisms+2bowls+leather1+mind1, sourcecost12");
        h.runAfterDelay(70,()->{ h.assertTrue(!press.job().active()&&jar.snapshot().amount()==0,"Paid wheel job consumes exactly12 actual units"); var actual=press.removeItem(0,1); h.assertTrue(actual.is(GolemModule.GOLEM_PLACER.get()),"One actual configured placer"); p.setItemInHand(InteractionHand.MAIN_HAND,actual); var support=h.absolutePos(new BlockPos(9,0,8)); var ctx=new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false));
            h.assertTrue(GolemModule.GOLEM_PLACER.get().useOn(ctx).consumesAction()&&p.getMainHandItem().isEmpty(),"Actual wheel placer consumes once to spawn"); var entities=h.getLevel().getEntitiesOfClass(ThaumcraftGolem.class,h.getBounds()); h.assertTrue(entities.size()==1&&entities.getFirst().design().equals(design())&&!entities.getFirst().isNoAi(),"One real working wheeled construction"); var g=entities.getFirst(); var tag=new CompoundTag(); g.addAdditionalSaveData(tag); var restored=GolemModule.GOLEM.get().create(h.getLevel()); restored.readAdditionalSaveData(tag); h.assertTrue(restored.design().equals(design())&&!restored.isNoAi()&&restored.getAttributeValue(Attributes.STEP_HEIGHT)==.5&&restored.ownerId().equals(p.getUUID()),"Typed wheel props/owner/source step restored through actual entity NBT"); g.discard(); p.getInventory().clearContent(); h.succeed();
        });
    }
}
