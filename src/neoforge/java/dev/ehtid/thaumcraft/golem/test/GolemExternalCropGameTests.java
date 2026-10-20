package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Optional real external-binary tests. Missing content fails; no substitute blocks or compile dependency. */
@GameTestHolder("thaumcraft_golem_crops") @PrefixGameTestTemplate(false)
public final class GolemExternalCropGameTests {
    private record Fixture(GolemSeals data,GolemSeals.SealKey key,BlockPos pos,ThaumcraftGolem worker,
                           CropBlock crop,ItemStack seed,GolemHarvestRegistry.Registration crops,
                           GolemHarvestRegistry.SeedRegistration seeds) {
        void close(){crops.close();if(seeds!=null)seeds.close();data.remove(key.pos(),key.face(),worker.ownerId());worker.discard();}
    }
    private GolemExternalCropGameTests() {}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("farmersdelight",path);}
    private static Item item(String path){
        var result=BuiltInRegistries.ITEM.get(id(path));
        if(result==Items.AIR)throw new AssertionError("Real Farmer's Delight item missing: "+path);
        return result;
    }
    private static Fixture fixture(GameTestHelper h,String block,String seed,boolean clickable,boolean ai){
        var real=BuiltInRegistries.BLOCK.get(id(block));
        if(!(real instanceof CropBlock crop))throw new AssertionError("Real Farmer's Delight crop missing: "+block);
        h.getLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,h.getLevel().getServer());
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var support=h.absolutePos(new BlockPos(5,0,5));var pos=support.above();
        h.getLevel().setBlockAndUpdate(support,Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
        BlockState ripe=crop.getStateForAge(crop.getMaxAge());
        h.getLevel().setBlockAndUpdate(pos,ripe);
        var template=new ItemStack(item(seed));template.set(DataComponents.CUSTOM_NAME,Component.literal("M48 actual foreign paid seed"));
        var crops=clickable?GolemHarvestRegistry.registerClickable(ripe):GolemHarvestRegistry.registerStandard(ripe);
        var seeds=clickable?null:GolemHarvestRegistry.registerSeed(crop,template);
        var owner=UUID.randomUUID();var data=GolemSeals.get(h.getLevel());var key=new GolemSeals.SealKey(support,Direction.UP);
        h.assertTrue(data.place(support,Direction.UP,owner,GolemSeals.Type.HARVEST),"Native Harvest seal");
        data.configureControls(data.get(key),owner,0,0,true,false);
        data.configureArea(data.get(key),owner,new BlockPos(1,1,1));data.configureHarvest(data.get(key),owner,new HarvestSettings(true,false));
        var g=GolemModule.GOLEM.get().create(h.getLevel());g.moveTo(Vec3.atBottomCenterOf(pos.east()));
        g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),owner,pos,0);g.setNoAi(!ai);
        g.setItemSlot(EquipmentSlot.MAINHAND,clickable?new ItemStack(Items.IRON_INGOT,13):template.copyWithCount(2));
        h.assertTrue(h.getLevel().addFreshEntity(g),"Actual native worker");
        return new Fixture(data,key,pos,g,crop,template,crops,seeds);
    }
    private static GolemSeals.BlockAssignment claim(Fixture f){return f.data.claimBlock(f.worker,p->true).orElseThrow();}
    private static ListTag pending(GameTestHelper h,Fixture f){
        for(var tag:f.data.save(new CompoundTag(),h.getLevel().registryAccess()).getList("seals",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)tag;if(row.getLong("pos")==f.key.pos().asLong())return row.getList("ReplantTasks",Tag.TAG_COMPOUND);
        }
        throw new AssertionError("Original seal absent");
    }
    private static int ground(GameTestHelper h,Item item,boolean named){
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds(),e->e.getItem().is(item)&&e.getItem().has(DataComponents.CUSTOM_NAME)==named)
            .stream().mapToInt(e->e.getItem().getCount()).sum();
    }
    @GameTest(template="golem_external_crop",batch="external_cabbage",timeoutTicks=80)
    public static void realCabbageDropsAndExactPaidSeedSurviveIntentDecode(GameTestHelper h){
        var f=fixture(h,"cabbages","cabbage_seeds",false,false);
        h.runAfterDelay(8,()->{
            h.assertTrue(f.data.completeBlock(h.getLevel(),f.worker,claim(f))&&h.getLevel().isEmptyBlock(f.pos),"Real external protected harvest");
            h.assertTrue(ground(h,item("cabbage"),false)==1&&ItemStack.matches(f.worker.getMainHandItem(),f.seed.copyWithCount(2)),"Actual external loot table, no seed debit on harvest");
            var copy=GolemSeals.load(f.data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(copy.save(new CompoundTag(),h.getLevel().registryAccess()).getList("seals",Tag.TAG_COMPOUND).stream()
                .map(t->(CompoundTag)t).anyMatch(r->r.getLong("pos")==f.key.pos().asLong()&&r.getList("ReplantTasks",Tag.TAG_COMPOUND).size()==1),"Foreign item components and native block ID round-trip");
            h.runAfterDelay(8,()->{try{
                h.assertTrue(f.data.completeBlock(h.getLevel(),f.worker,claim(f))&&h.getLevel().getBlockState(f.pos).is(f.crop)
                    &&f.crop.getAge(h.getLevel().getBlockState(f.pos))==0&&ItemStack.matches(f.worker.getMainHandItem(),f.seed)
                    &&pending(h,f).isEmpty()&&ground(h,item("cabbage_seeds"),true)==0&&f.worker.rankXp()==2,"Foreign BlockItem really plants, exactly one named seed consumed");
                LogUtils.getLogger().info("GOLEM_EXTERNAL_CABBAGE_ASSERTIONS_OK fruit=1 namedSeed=1 plant=1 xp=2");
            }finally{f.close();}h.succeed();});
        });
    }
    @GameTest(template="golem_external_crop",batch="external_onion_ai",timeoutTicks=340)
    public static void actualAiHarvestsAndReplantsRealOnionWithoutDuplicatingNamedSeeds(GameTestHelper h){
        var f=fixture(h,"onions","onion",false,true);var settled=new AtomicInteger();
        h.onEachTick(()->{
            int named=f.worker.getMainHandItem().has(DataComponents.CUSTOM_NAME)?f.worker.getMainHandItem().getCount():0;
            named+=f.worker.transferRecoveryItems().stream().filter(s->ItemStack.isSameItemSameComponents(s,f.seed)).mapToInt(ItemStack::getCount).sum();
            boolean planted=f.worker.rankXp()==2&&h.getLevel().getBlockState(f.pos).is(f.crop);
            h.assertTrue(named+ground(h,item("onion"),true)+(planted?1:0)==2,"Each-tick real external seed conservation");
        });
        h.succeedWhen(()->{
            h.assertTrue(f.worker.rankXp()==2&&h.getLevel().getBlockState(f.pos).is(f.crop)&&f.crop.getAge(h.getLevel().getBlockState(f.pos))==0
                &&ItemStack.matches(f.worker.getMainHandItem(),f.seed)&&pending(h,f).isEmpty()&&ground(h,item("onion"),false)>0,"Enabled autonomous AI completed actual external break and paid replant");
            h.assertTrue(settled.incrementAndGet()>=40,"Observe no replay for40ticks");
            LogUtils.getLogger().info("GOLEM_EXTERNAL_ONION_AI_ASSERTIONS_OK ordinaryOnion={} namedSeed=1 plant=1 xp=2",ground(h,item("onion"),false));f.close();
        });
    }
    @GameTest(template="golem_external_crop",batch="external_tomato_click",timeoutTicks=60)
    public static void realTomatoEmptyHandCallbackProducesFruitAndRetainsTheCrop(GameTestHelper h){
        var f=fixture(h,"tomatoes","tomato_seeds",true,false);
        h.runAfterDelay(8,()->{try{
            h.assertTrue(f.data.completeBlock(h.getLevel(),f.worker,claim(f))&&h.getLevel().getBlockState(f.pos).is(f.crop)
                &&f.crop.getAge(h.getLevel().getBlockState(f.pos))==0,"Real foreign right-click callback, no simulated fruit or destroyed plant");
            int n=ground(h,item("tomato"),false);h.assertTrue(n>=1&&n<=2&&f.worker.getMainHandItem().is(Items.IRON_INGOT)
                &&f.worker.getMainHandItem().getCount()==13&&f.worker.rankXp()==1&&pending(h,f).isEmpty(),"Foreign callback output, empty fake hand and unchanged real cargo");
            LogUtils.getLogger().info("GOLEM_EXTERNAL_TOMATO_ASSERTIONS_OK tomato={} age=0 iron=13 xp=1 noReplant=true",n);
        }finally{f.close();}h.succeed();});
    }
    @GameTest(template="golem_external_crop",batch="external_protection",timeoutTicks=60)
    public static void actualProtectionEventCancelsForeignHarvestWithoutSpendingItsSeed(GameTestHelper h){
        var f=fixture(h,"cabbages","cabbage_seeds",false,false);
        h.runAfterDelay(8,()->{var calls=new AtomicInteger();Consumer<BlockEvent.BreakEvent> deny=e->{
            if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.pos)){
                h.assertTrue(e.getPlayer().getUUID().equals(f.worker.ownerId()),"Actual owner fake-player identity");calls.incrementAndGet();e.setCanceled(true);
            }
        };NeoForge.EVENT_BUS.addListener(deny);try{
            h.assertTrue(!f.data.completeBlock(h.getLevel(),f.worker,claim(f))&&calls.get()==1&&f.crop.isMaxAge(h.getLevel().getBlockState(f.pos))
                &&ItemStack.matches(f.worker.getMainHandItem(),f.seed.copyWithCount(2))&&pending(h,f).isEmpty()
                &&f.worker.rankXp()==0&&ground(h,item("cabbage"),false)==0,"Real protected foreign block is retained, no ghost drops/seed debit/XP");
            LogUtils.getLogger().info("GOLEM_EXTERNAL_PROTECTION_ASSERTIONS_OK cancelled=1 paidSeeds=2 fruit=0 xp=0");
        }finally{NeoForge.EVENT_BUS.unregister(deny);f.close();}h.succeed();});
    }
}
