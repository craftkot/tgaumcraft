package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.api.GolemHarvestRegistry;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit source BlockDirectional support through real non-BlockItem callbacks, never synthetic placement. */
@GameTestHolder("thaumcraft_golem_plants") @PrefixGameTestTemplate(false)
public final class GolemDirectionalPlantGameTests {
    private record Fixture(GolemSeals data,GolemSeals.SealKey key,BlockPos pos,ThaumcraftGolem worker,ItemStack seed,
                           Direction support,Block crop,GolemHarvestRegistry.Registration crops,GolemHarvestRegistry.SeedRegistration seeds){
        void close(){crops.close();seeds.close();data.remove(key.pos(),key.face(),worker.ownerId());worker.discard();}
    }
    private GolemDirectionalPlantGameTests() {}
    private static Block crop(){return BuiltInRegistries.BLOCK.get(GolemPlantAdapterItems.id("directional"));}
    private static ItemStack seed(boolean fault,int count){
        var item=BuiltInRegistries.ITEM.get(GolemPlantAdapterItems.id(fault?"directional_fault":"directional"));
        if(item==Items.AIR||item instanceof BlockItem)throw new AssertionError("Real opt-in non-BlockItem directional callback required");
        var result=new ItemStack(item,count);result.set(DataComponents.CUSTOM_NAME,Component.literal("M54 actual directional seed"));return result;
    }
    private static Fixture fixture(GameTestHelper h,Direction support,boolean fault,boolean ai){
        h.assertTrue(crop() instanceof GolemPlantAdapterItems.DirectionalPlant,"Actual registered directional crop required");
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var under=h.absolutePos(new BlockPos(5,0,5));var pos=under.above();var block=crop();
        var mature=block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,support).setValue(GolemPlantAdapterItems.DirectionalPlant.AGE,1);
        h.getLevel().setBlockAndUpdate(pos.relative(support),Blocks.JUNGLE_LOG.defaultBlockState());h.getLevel().setBlockAndUpdate(pos,mature);
        var crops=GolemHarvestRegistry.registerStandard(mature);var seeds=GolemHarvestRegistry.registerDirectionalSeed(block,seed(fault,1),block,HorizontalDirectionalBlock.FACING);
        var owner=UUID.randomUUID();var data=GolemSeals.get(h.getLevel());var key=new GolemSeals.SealKey(under,Direction.UP);
        h.assertTrue(data.place(under,Direction.UP,owner,GolemSeals.Type.HARVEST),"Actual owner Harvest seal");
        data.configureControls(data.get(key),owner,0,0,true,false);data.configureArea(data.get(key),owner,new BlockPos(1,1,1));data.configureHarvest(data.get(key),owner,new HarvestSettings(true,false));
        var worker=GolemModule.GOLEM.get().create(h.getLevel());worker.moveTo(Vec3.atBottomCenterOf(pos.relative(support.getOpposite())));
        worker.initialize(GolemDesign.fromIds(0,1,1,0,0,0),owner,pos,0);worker.setNoAi(!ai);worker.setItemSlot(EquipmentSlot.MAINHAND,seed(fault,2));h.assertTrue(h.getLevel().addFreshEntity(worker),"Actual worker and funded callback seeds");
        return new Fixture(data,key,pos,worker,seed(fault,1),support,block,crops,seeds);
    }
    private static GolemSeals.BlockAssignment claim(Fixture f){return f.data.claimBlock(f.worker,p->true).orElseThrow();}
    private static boolean complete(GameTestHelper h,Fixture f,GolemSeals.BlockAssignment assignment){return f.data.completeBlock(h.getLevel(),f.worker,assignment);}
    private static ListTag pending(GameTestHelper h,GolemSeals data,GolemSeals.SealKey key){
        for(var tag:data.save(new CompoundTag(),h.getLevel().registryAccess()).getList("seals",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)tag;if(row.getLong("pos")==key.pos().asLong())return row.getList("ReplantTasks",Tag.TAG_COMPOUND);
        }throw new AssertionError("Original seal absent");
    }
    private static void harvested(GameTestHelper h,Fixture f){
        h.assertTrue(complete(h,f,claim(f))&&h.getLevel().isEmptyBlock(f.pos)&&ItemStack.matches(f.worker.getMainHandItem(),f.seed.copyWithCount(2))&&f.worker.rankXp()==1,"Actual protected mature directional harvest preserves both funded seeds");
        var records=pending(h,f.data,f.key);h.assertTrue(records.size()==1&&records.getCompound(0).getByte("support")==f.support.ordinal(),"Original directional support is persisted independently of floor geometry");
    }
    private static void planted(GameTestHelper h,Fixture f){
        var state=h.getLevel().getBlockState(f.pos);
        h.assertTrue(state.is(f.crop)&&state.getValue(HorizontalDirectionalBlock.FACING)==f.support&&state.getValue(GolemPlantAdapterItems.DirectionalPlant.AGE)==0
            &&ItemStack.matches(f.worker.getMainHandItem(),f.seed)&&f.worker.getOffhandItem().isEmpty()&&!f.worker.hasTransferRecovery()&&f.worker.rankXp()==2&&pending(h,f.data,f.key).isEmpty(),"Real horizontal callback retains support and pays exactly one despite changing reserved seed components");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds(),e->e.getItem().is(f.seed.getItem())).isEmpty(),"No duplicate callback seed entity");
    }
    private static void cycle(GameTestHelper h,Direction support){
        var f=fixture(h,support,false,false);h.runAfterDelay(8,()->{harvested(h,f);
            var decoded=GolemSeals.load(f.data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            var saved=pending(h,decoded,f.key);h.assertTrue(saved.size()==1&&saved.getCompound(0).getByte("support")==support.ordinal()&&ItemStack.matches(ItemStack.parseOptional(h.getLevel().registryAccess(),saved.getCompound(0).getCompound("seed")),f.seed),"Exact directional intent and named non-BlockItem seed survive decode");
            h.runAfterDelay(11,()->{try{h.assertTrue(complete(h,f,claim(f)),"Actual native directional useOn callback succeeds");planted(h,f);}finally{f.close();}h.succeed();});
        });
    }
    @GameTest(template="golem_plant_adapter",batch="directional_north",timeoutTicks=80)
    public static void nativeNorthFacingCropRetainsOriginalSideAndPaysOne(GameTestHelper h){cycle(h,Direction.NORTH);}
    @GameTest(template="golem_plant_adapter",batch="directional_south",timeoutTicks=80)
    public static void nativeSouthFacingCropRetainsOriginalSideAndPaysOne(GameTestHelper h){cycle(h,Direction.SOUTH);}
    @GameTest(template="golem_plant_adapter",batch="directional_west",timeoutTicks=80)
    public static void nativeWestFacingCropRetainsOriginalSideAndPaysOne(GameTestHelper h){cycle(h,Direction.WEST);}
    @GameTest(template="golem_plant_adapter",batch="directional_east",timeoutTicks=80)
    public static void nativeEastFacingCropRetainsOriginalSideAndPaysOne(GameTestHelper h){cycle(h,Direction.EAST);}
    @GameTest(template="golem_plant_adapter",batch="directional_fault",timeoutTicks=80)
    public static void throwingDirectionalCallbackRollsBackTargetAndRecoversActualCursorSeedOnce(GameTestHelper h){
        var f=fixture(h,Direction.EAST,true,false);h.runAfterDelay(8,()->{harvested(h,f);h.runAfterDelay(11,()->{try{
            h.assertTrue(!complete(h,f,claim(f))&&h.getLevel().isEmptyBlock(f.pos)&&ItemStack.matches(f.worker.getMainHandItem(),f.seed.copyWithCount(2))&&!f.worker.hasTransferRecovery()&&f.worker.getOffhandItem().isEmpty()&&f.worker.rankXp()==1&&pending(h,f.data,f.key).size()==1,"Real side placement then actual cursor ownership transfer throws: original air and two real seeds recovered once, no free plant/XP");
            h.assertTrue(!h.getLevel().captureBlockSnapshots&&!h.getLevel().restoringBlockSnapshots&&h.getLevel().capturedBlockSnapshots.isEmpty(),"Native capture globals repaired after actual directional callback fault");
        }finally{f.close();}h.succeed();});});
    }
    @GameTest(template="golem_plant_adapter",batch="directional_registry",timeoutTicks=40)
    public static void directionalPropertyIsExplicitValidatedAndScopedWithExactSeedComponents(GameTestHelper h){
        try{GolemHarvestRegistry.registerDirectionalSeed(Blocks.WHEAT,seed(false,1),crop(),HorizontalDirectionalBlock.FACING);throw new AssertionError("Absent support property accepted");}catch(IllegalArgumentException expected){}
        try{GolemHarvestRegistry.registerDirectionalSeed(crop(),seed(false,1),crop(),BlockStateProperties.FACING);throw new AssertionError("Different six-way property with same name accepted");}catch(IllegalArgumentException expected){}
        var original=crop().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,Direction.EAST);
        try(var directional=GolemHarvestRegistry.registerDirectionalSeed(crop(),seed(false,1),crop(),HorizontalDirectionalBlock.FACING)){
            h.assertTrue(GolemHarvestRegistry.replantSupport(original,seed(false,1))==Direction.EAST&&!GolemHarvestRegistry.validReplantSupport(crop(),seed(false,1),Direction.UP),"Exact explicit mapping supports stored horizontal direction only");
            var different=seed(false,1);different.remove(DataComponents.CUSTOM_NAME);h.assertTrue(GolemHarvestRegistry.replantSupport(original,different)==null&&!GolemHarvestRegistry.validReplantSupport(crop(),different,Direction.EAST),"Different components cannot impersonate a directional seed mapping");
            try(var floor=GolemHarvestRegistry.registerSeed(crop(),seed(false,1),crop())){h.assertTrue(GolemHarvestRegistry.replantSupport(original,seed(false,1))==Direction.DOWN&&!GolemHarvestRegistry.validReplantSupport(crop(),seed(false,1),Direction.EAST),"Most recent ordinary mapping explicitly requests floor support");}
            h.assertTrue(GolemHarvestRegistry.replantSupport(original,seed(false,1))==Direction.EAST,"Closing override restores prior explicit directional adapter");
        }
        h.assertTrue(GolemHarvestRegistry.seed(crop()).isEmpty(),"Scoped fixture leaves no default content adapter");h.succeed();
    }
    @GameTest(template="golem_plant_adapter",batch="directional_stale",timeoutTicks=100)
    public static void forgedVerticalIntentAndLiveFloorOverrideCannotSpendDirectionalSeed(GameTestHelper h){
        var f=fixture(h,Direction.SOUTH,false,false);h.runAfterDelay(8,()->{harvested(h,f);var tag=f.data.save(new CompoundTag(),h.getLevel().registryAccess());
            for(var t:tag.getList("seals",Tag.TAG_COMPOUND))if(((CompoundTag)t).getLong("pos")==f.key.pos().asLong())((CompoundTag)t).getList("ReplantTasks",Tag.TAG_COMPOUND).getCompound(0).putByte("support",(byte)Direction.UP.ordinal());
            h.assertTrue(pending(h,GolemSeals.load(tag,h.getLevel().registryAccess()),f.key).isEmpty(),"Forged persisted vertical support rejected by original horizontal property");
            h.runAfterDelay(11,()->{var stale=claim(f);try(var floor=GolemHarvestRegistry.registerSeed(f.crop,f.seed,f.crop)){
                h.assertTrue(!complete(h,f,stale)&&h.getLevel().isEmptyBlock(f.pos)&&ItemStack.matches(f.worker.getMainHandItem(),f.seed.copyWithCount(2))&&f.worker.rankXp()==1,"Live mapping change invalidates an existing side-support lease before moving any seed");
            }try{h.assertTrue(complete(h,f,stale),"Restoring original directional mapping permits same still-valid task");planted(h,f);}finally{f.close();}h.succeed();});
        });
    }
    @GameTest(template="golem_plant_adapter",batch="directional_ai",timeoutTicks=300)
    public static void autonomousWorkerHarvestsAndReplantsActualDirectionalCropWithoutReplay(GameTestHelper h){
        var f=fixture(h,Direction.WEST,false,true);var settled=new AtomicInteger();
        h.onEachTick(()->{int n=f.worker.getMainHandItem().is(f.seed.getItem())?f.worker.getMainHandItem().getCount():0;n+=f.worker.transferRecoveryItems().stream().filter(s->s.is(f.seed.getItem())).mapToInt(ItemStack::getCount).sum();boolean grown=f.worker.rankXp()==2&&h.getLevel().getBlockState(f.pos).is(f.crop);h.assertTrue(n+(grown?1:0)==2,"Every tick actual directional seed plus paid plant conservation");});
        h.succeedWhen(()->{planted(h,f);h.assertTrue(settled.incrementAndGet()>=30,"Observe stable actual directional automation for30ticks");f.close();});
    }
}
