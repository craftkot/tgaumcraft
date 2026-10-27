package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.api.GolemHarvestRegistry;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.golem.seal.HarvestSettings;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Native six-way BlockDirectional support; vertical faces must be demonstrated, not inferred from the API. */
@GameTestHolder("thaumcraft_golem_plants")
@PrefixGameTestTemplate(false)
public final class GolemSixWayPlantGameTests {
    private GolemSixWayPlantGameTests() {}
    private record Fixture(GolemSeals data,GolemSeals.SealKey key,BlockPos pos,ThaumcraftGolem worker,
                           ItemStack seed,Direction support,Block crop,GolemHarvestRegistry.Registration crops,
                           GolemHarvestRegistry.SeedRegistration seeds) {
        void close(){crops.close();seeds.close();data.remove(key.pos(),key.face(),worker.ownerId());worker.discard();}
    }
    private static Block crop(){return BuiltInRegistries.BLOCK.get(GolemPlantAdapterItems.id("six_way"));}
    private static ItemStack seed(int count){
        var item=BuiltInRegistries.ITEM.get(GolemPlantAdapterItems.id("six_way"));
        if(item==Items.AIR||item instanceof BlockItem)throw new AssertionError("Real gated non-BlockItem six-way seed required");
        var seed=new ItemStack(item,count);seed.set(DataComponents.CUSTOM_NAME,Component.literal("M55 actual six-way seed"));return seed;
    }
    private static Fixture fixture(GameTestHelper h,Direction support){
        h.assertTrue(crop() instanceof GolemPlantAdapterItems.SixWayPlant,"Actual gated six-way crop required");
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var under=h.absolutePos(new BlockPos(5,0,5));var pos=under.above();var block=crop();
        var mature=block.defaultBlockState().setValue(DirectionalBlock.FACING,support).setValue(GolemPlantAdapterItems.SixWayPlant.AGE,1);
        h.getLevel().setBlockAndUpdate(pos.relative(support),Blocks.JUNGLE_LOG.defaultBlockState());h.getLevel().setBlockAndUpdate(pos,mature);
        var crops=GolemHarvestRegistry.registerStandard(mature);
        var seeds=GolemHarvestRegistry.registerDirectionalSeed(block,seed(1),block,DirectionalBlock.FACING);
        var owner=UUID.randomUUID();var data=GolemSeals.get(h.getLevel());var key=new GolemSeals.SealKey(under,Direction.UP);
        h.assertTrue(data.place(under,Direction.UP,owner,GolemSeals.Type.HARVEST),"One actual owner Harvest seal");
        data.configureControls(data.get(key),owner,0,0,true,false);data.configureArea(data.get(key),owner,new BlockPos(1,1,1));
        data.configureHarvest(data.get(key),owner,new HarvestSettings(true,false));
        var worker=GolemModule.GOLEM.get().create(h.getLevel());
        // A vertical support's opposite could be inside the floor or ceiling; stand beside the crop.
        worker.moveTo(Vec3.atBottomCenterOf(pos.east()));worker.initialize(GolemDesign.fromIds(0,1,1,0,0,0),owner,pos,0);
        worker.setNoAi(true);worker.setItemSlot(EquipmentSlot.MAINHAND,seed(2));
        h.assertTrue(h.getLevel().addFreshEntity(worker),"Actual worker with two funded exact-component seeds");
        return new Fixture(data,key,pos,worker,seed(1),support,block,crops,seeds);
    }
    private static ListTag pending(GameTestHelper h,GolemSeals data,GolemSeals.SealKey key){
        for(var tag:data.save(new CompoundTag(),h.getLevel().registryAccess()).getList("seals",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)tag;
            if(row.getLong("pos")==key.pos().asLong())return row.getList("ReplantTasks",Tag.TAG_COMPOUND);
        }
        throw new AssertionError("Original six-way Harvest seal absent");
    }
    private static void completeCycle(GameTestHelper h,Direction support){
        var f=fixture(h,support);
        h.runAfterDelay(8,()->{
            var harvest=f.data.claimBlock(f.worker,p->true).orElseThrow();
            h.assertTrue(f.data.completeBlock(h.getLevel(),f.worker,harvest)&&h.getLevel().isEmptyBlock(f.pos)
                    &&ItemStack.matches(f.worker.getMainHandItem(),f.seed.copyWithCount(2))&&f.worker.rankXp()==1,
                    "Real mature six-way crop harvested without spending planting funds");
            var stored=pending(h,f.data,f.key);
            h.assertTrue(stored.size()==1&&stored.getCompound(0).getByte("support")==support.ordinal(),"Original vertical support persisted exactly");
            var decoded=GolemSeals.load(f.data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            var restored=pending(h,decoded,f.key);
            h.assertTrue(restored.size()==1&&restored.getCompound(0).getByte("support")==support.ordinal()
                    &&ItemStack.matches(ItemStack.parseOptional(h.getLevel().registryAccess(),restored.getCompound(0).getCompound("seed")),f.seed),
                    "Actual saved intent accepts legitimate UP or DOWN support and exact named seed on decode");
            h.runAfterDelay(11,()->{
                try{
                    var replant=f.data.claimBlock(f.worker,p->true).orElseThrow();
                    h.assertTrue(f.data.completeBlock(h.getLevel(),f.worker,replant),"Actual native callback clicks the saved vertical support face");
                    var state=h.getLevel().getBlockState(f.pos);
                    h.assertTrue(state.is(f.crop)&&state.getValue(DirectionalBlock.FACING)==support&&state.getValue(GolemPlantAdapterItems.SixWayPlant.AGE)==0
                            &&h.getLevel().getBlockState(f.pos.relative(support)).is(Blocks.JUNGLE_LOG),"Genuine young plant retains its actual ceiling or floor log");
                    h.assertTrue(ItemStack.matches(f.worker.getMainHandItem(),f.seed)&&f.worker.getOffhandItem().isEmpty()
                            &&!f.worker.hasTransferRecovery()&&f.worker.rankXp()==2&&pending(h,f.data,f.key).isEmpty(),
                            "No-debit callback changes actual reserved components; source still pays exactly one, leaves one original seed and XP2");
                    h.assertTrue(!f.data.completeBlock(h.getLevel(),f.worker,replant)&&ItemStack.matches(f.worker.getMainHandItem(),f.seed)
                            &&f.worker.rankXp()==2,"Completed vertical callback cannot replay its old lease");
                    h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds(),e->e.getItem().is(f.seed.getItem())).isEmpty(),"No duplicate six-way seed on the ground");
                }finally{f.close();}
                h.succeed();
            });
        });
    }
    @GameTest(template="golem_plant_adapter",batch="six_way_up",timeoutTicks=80)
    public static void nativeCeilingSupportedCropKeepsUpFaceAndPaysOne(GameTestHelper h){completeCycle(h,Direction.UP);}
    @GameTest(template="golem_plant_adapter",batch="six_way_down",timeoutTicks=80)
    public static void nativeFloorSupportedCropKeepsDownFaceAndPaysOne(GameTestHelper h){completeCycle(h,Direction.DOWN);}
    @GameTest(template="golem_plant_adapter",batch="six_way_registry",timeoutTicks=40)
    public static void actualSixWayPropertyAcceptsVerticalFacesAndRefusesSameNameHorizontalProperty(GameTestHelper h){
        h.assertTrue(crop() instanceof GolemPlantAdapterItems.SixWayPlant&&GolemHarvestRegistry.seed(crop()).isEmpty(),"Real gated block has no automatic seed mapping");
        try{
            GolemHarvestRegistry.registerDirectionalSeed(crop(),seed(1),crop(),HorizontalDirectionalBlock.FACING);
            throw new AssertionError("Different horizontal property with the same name accepted");
        }catch(IllegalArgumentException expected){}
        var original=crop().defaultBlockState().setValue(GolemPlantAdapterItems.SixWayPlant.AGE,1);
        try(var registration=GolemHarvestRegistry.registerDirectionalSeed(crop(),seed(1),crop(),DirectionalBlock.FACING)){
            for(var face:Direction.values())h.assertTrue(GolemHarvestRegistry.replantSupport(original.setValue(DirectionalBlock.FACING,face),seed(1))==face
                    &&GolemHarvestRegistry.validReplantSupport(crop(),seed(1),face),"Explicit original six-way property preserves "+face);
            h.assertTrue(!GolemHarvestRegistry.isMature(original,false),"A seed registration does not independently enable mature crop harvesting");
            var different=seed(1);different.remove(DataComponents.CUSTOM_NAME);
            h.assertTrue(GolemHarvestRegistry.replantSupport(original,different)==null
                    &&!GolemHarvestRegistry.validReplantSupport(crop(),different,Direction.UP),"Same item with different components cannot use explicit six-way adapter");
        }
        h.assertTrue(GolemHarvestRegistry.seed(crop()).isEmpty(),"Closing scoped registration leaves ordinary content unchanged");h.succeed();
    }
}
