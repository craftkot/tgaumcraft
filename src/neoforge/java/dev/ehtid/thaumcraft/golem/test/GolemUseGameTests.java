package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.blockentity.CrucibleBlockEntity;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemUseGameTests {
    private GolemUseGameTests() {}
    private record Fixture(GolemSeals data,GolemSeals.SealKey key,ThaumcraftGolem golem) implements AutoCloseable {
        public void close() { data.remove(key.pos(),key.face(),golem.ownerId()); golem.discard(); }
    }
    private static Fixture fixture(GameTestHelper h,Block block,boolean ai) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var pos=h.absolutePos(new BlockPos(7,1,5)); h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState()); var key=new GolemSeals.SealKey(pos,Direction.UP); var owner=UUID.randomUUID(); var data=GolemSeals.get(h.getLevel()); data.place(pos,Direction.UP,owner,GolemSeals.Type.USE); data.configureControls(data.get(key),owner,0,0,true,false);
        var g=GolemModule.GOLEM.get().create(h.getLevel()); g.moveTo(Vec3.atBottomCenterOf(pos.north())); g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),owner,pos,0); g.setNoAi(!ai); h.getLevel().addFreshEntity(g); return new Fixture(data,key,g);
    }
    private static void settings(Fixture f,GolemUseRules.Settings value) { f.data.configureUse(f.data.get(f.key),f.golem.ownerId(),value); }
    private static GolemSeals.BlockAssignment claim(Fixture f) { return f.data.claimBlock(f.golem,p->true).orElseThrow(); }
    private static boolean complete(GameTestHelper h,Fixture f,GolemSeals.BlockAssignment task) { return f.data.completeBlock(h.getLevel(),f.golem,task); }
    private static int drops(GameTestHelper h,Item item) { return h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum(); }
    private static int placed(GameTestHelper h,Fixture f,Block block) { return h.getLevel().getBlockState(f.key.pos()).is(block)?1:0; }
    @GameTest(template="golem_test",batch="use_ai",timeoutTicks=260)
    public static void nativeAiPlacesOnePaidBlockInAssignedEmptyCellAndKeepsActualRemainder(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,true); settings(f,GolemUseRules.Settings.defaults().withToggle(5,true)); h.getLevel().setBlockAndUpdate(f.key.pos(),Blocks.AIR.defaultBlockState()); f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIRT,3)); f.golem.moveTo(Vec3.atBottomCenterOf(f.key.pos().east(3)));
        h.onEachTick(()->h.assertTrue(placed(h,f,Blocks.DIRT)+f.golem.getMainHandItem().getCount()==3,"Per tick actual input3=placed1+remainder2"));
        h.succeedWhen(()->{ h.assertTrue(placed(h,f,Blocks.DIRT)==1&&f.golem.getMainHandItem().is(Items.DIRT)&&f.golem.getMainHandItem().getCount()==2&&f.golem.rankXp()==1&&f.data.contains(f.key.pos(),f.key.face()),"Native SMART+DEFT navigates, preserves air-capable Use seal, places exact assigned cell using real input"); f.close(); });
    }
    @GameTest(template="golem_test",batch="use_empty_hand",timeoutTicks=60)
    public static void emptyHandClickTogglesActualLeverWithoutDeletingNamedCarriedCargo(GameTestHelper h) {
        var f=fixture(h,Blocks.LEVER,false); var cargo=new ItemStack(Items.GOLD_INGOT,3); cargo.set(DataComponents.CUSTOM_NAME,Component.literal("Paid cargo must survive empty-hand click")); f.golem.setItemSlot(EquipmentSlot.MAINHAND,cargo.copy()); settings(f,GolemUseRules.Settings.defaults().withToggle(6,true));
        h.runAfterDelay(10,()->{ var task=claim(f); h.assertTrue(complete(h,f,task)&&h.getLevel().getBlockState(f.key.pos()).getValue(LeverBlock.POWERED)&&ItemStack.matches(f.golem.getMainHandItem(),cargo)&&f.golem.rankXp()==1,"Actual empty-hand right click toggles lever, source-loss guard keeps all3 named gold"); h.assertTrue(!complete(h,f,task)&&drops(h,Items.GOLD_INGOT)==0,"No stale lever replay or copied cargo"); f.close(); h.succeed(); });
    }
    @GameTest(template="golem_test",batch="use_bucket",timeoutTicks=65)
    public static void realWaterBucketFillsCauldronAndReturnsTheActualEmptyBucket(GameTestHelper h) {
        var f=fixture(h,Blocks.CAULDRON,false); f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.WATER_BUCKET));
        h.runAfterDelay(10,()->{ var task=claim(f); h.assertTrue(complete(h,f,task)&&h.getLevel().getBlockState(f.key.pos()).is(Blocks.WATER_CAULDRON)&&h.getLevel().getBlockState(f.key.pos()).getValue(LayeredCauldronBlock.LEVEL)==3&&f.golem.getMainHandItem().is(Items.BUCKET)&&f.golem.getMainHandItem().getCount()==1,"Actual cauldron activation consumes water and returns one actual bucket, no ghost restoration"); h.assertTrue(!complete(h,f,task)&&drops(h,Items.WATER_BUCKET)==0&&drops(h,Items.BUCKET)==0,"No stale use/duplicated buckets"); f.close(); h.succeed(); });
    }
    @GameTest(template="golem_test",batch="use_cancel",timeoutTicks=65)
    public static void ownerProtectedSneakingRightClickReturnsActualStackWithoutWorldMutation(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,false); f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIRT,4)); settings(f,GolemUseRules.Settings.defaults().withToggle(7,true)); var seen=new AtomicReference<net.minecraft.world.entity.player.Player>();
        h.runAfterDelay(10,()->{ var task=claim(f); java.util.function.Consumer<PlayerInteractEvent.RightClickBlock> deny=e->{ if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.key.pos())) { h.assertTrue(e.getEntity().getUUID().equals(f.golem.ownerId())&&e.getEntity().isShiftKeyDown()&&e.getEntity().getMainHandItem().getCount()==4,"Actual owner/sneaking/paid4 observed at protected callback"); seen.set(e.getEntity()); e.setCanceled(true); } }; NeoForge.EVENT_BUS.addListener(deny);
            try { h.assertTrue(complete(h,f,task)&&seen.get()!=null&&h.getLevel().isEmptyBlock(f.key.pos().above())&&f.golem.getMainHandItem().getCount()==4&&f.golem.rankXp()==1&&!seen.get().isShiftKeyDown(),"Source attempted-clickXP1 preserved; real protection blocks placement and all4 actual items recovered, sneak reset"); }
            finally { NeoForge.EVENT_BUS.unregister(deny); f.close(); } h.succeed();
        });
    }
    @GameTest(template="golem_test",batch="use_left",timeoutTicks=70)
    public static void leftClickUsesRealInstantBreakPathForMatureCropWithoutInventingReplant(GameTestHelper h) {
        var f=fixture(h,Blocks.WHEAT,false); h.getLevel().setBlockAndUpdate(f.key.pos().below(),Blocks.FARMLAND.defaultBlockState()); h.getLevel().setBlockAndUpdate(f.key.pos(),Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7)); f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_HOE)); settings(f,GolemUseRules.Settings.defaults().withToggle(4,true));
        h.runAfterDelay(10,()->{ h.assertTrue(complete(h,f,claim(f))&&h.getLevel().isEmptyBlock(f.key.pos())&&drops(h,Items.WHEAT)==1&&f.golem.getMainHandItem().is(Items.WOODEN_HOE)&&f.golem.rankXp()==1,"Source left-click START uses real instant harvest path/crop loot/tool, no invented charged breaker or seed planting"); f.close(); h.succeed(); });
    }
    private static GolemSeals.SealKey provider(GameTestHelper h,Fixture f,Item item) {
        var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(2,1,5)),Direction.UP); h.getLevel().setBlockAndUpdate(key.pos(),Blocks.BARREL.defaultBlockState()); f.data.place(key.pos(),key.face(),f.golem.ownerId(),GolemSeals.Type.PROVIDE); var s=f.data.get(key); f.data.configureLogistics(s,f.golem.ownerId(),s.logistics().withSlot(8,new SealFilter(new ItemStack(item),false,0)).withMode(false,true,false)); ((IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),key)).setStackInSlot(0,new ItemStack(item,2)); return key;
    }
    private static void provision(GameTestHelper h,boolean metadata) {
        var f=fixture(h,Blocks.STONE,true); settings(f,GolemUseRules.Settings.defaults().withToggle(5,true).withToggle(8,true)); h.getLevel().setBlockAndUpdate(f.key.pos(),Blocks.AIR.defaultBlockState()); var actual=metadata?Items.DIRT:Items.SPRUCE_PLANKS; var block=metadata?Blocks.DIRT:Blocks.SPRUCE_PLANKS; var ghost=metadata?Items.DIRT:Items.OAK_PLANKS;
        f.data.configure(f.data.get(f.key),f.golem.ownerId(),new SealFilter(new ItemStack(ghost),false,0,SealFilterRules.Options.strict().withMetadata(metadata))); var source=provider(h,f,actual); var inv=(IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),source); f.golem.moveTo(Vec3.atBottomCenterOf(source.pos().north()));
        h.onEachTick(()->h.assertTrue(inv.getStackInSlot(0).getCount()+f.golem.getMainHandItem().getCount()+placed(h,f,block)==2,"Provider/source actual2 conserved through request/pickup/click"));
        h.succeedWhen(()->{ h.assertTrue(inv.getStackInSlot(0).getCount()==1&&placed(h,f,block)==1&&f.golem.getMainHandItem().isEmpty()&&f.golem.rankXp()==2,"Use SealTarget request obtains actual1 through existing STRICT provider then native places it; source variant preserved"); f.data.remove(source.pos(),source.face(),f.golem.ownerId()); f.close(); });
    }
    @GameTest(template="golem_test",batch="use_provision",timeoutTicks=350)
    public static void nativeProviderSuppliesOneRealUseItemBeforeActualPlacement(GameTestHelper h) { provision(h,true); }
    @GameTest(template="golem_test",batch="use_wildcard",timeoutTicks=350)
    public static void metadataWildcardProvisionBindsActualSpruceInsteadOfCreatingOakGhostPayload(GameTestHelper h) { provision(h,false); }
    @GameTest(template="golem_test",batch="use_traits",timeoutTicks=60)
    public static void bothSmartAndDeftAreRequiredEvenWithEmptyHandEnabled(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,false); settings(f,GolemUseRules.Settings.defaults().withToggle(6,true)); var others=new java.util.ArrayList<ThaumcraftGolem>();
        for(var design:new GolemDesign[]{GolemDesign.fromIds(0,0,1,0,0,0),GolemDesign.fromIds(0,1,0,0,0,0)}) { var g=GolemModule.GOLEM.get().create(h.getLevel()); g.moveTo(f.golem.position()); g.initialize(design,f.golem.ownerId(),f.key.pos(),0); g.setNoAi(true); h.getLevel().addFreshEntity(g); others.add(g); }
        h.runAfterDelay(10,()->{ try { for(var g:others) h.assertTrue(f.data.claimBlock(g,p->true).isEmpty(),"Empty-hand toggle cannot replace required SMART+DEFT"); var task=claim(f); h.assertTrue(f.data.blockTarget(h.getLevel(),f.golem,task)!=null,"SMART+DEFT reserves real Use work"); f.data.releaseBlock(h.getLevel(),f.golem,task,false); h.succeed(); } finally { others.forEach(ThaumcraftGolem::discard); f.close(); } });
    }
    @GameTest(template="golem_test",batch="use_menu",timeoutTicks=50)
    public static void ownerMenuSavesNineSourceTogglesAndGhostWithoutRewardingActualInput(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,false); var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(f.golem.ownerId(),"UseOwner")); p.moveTo(f.golem.position()); p.getInventory().clearContent();
        try { var menu=new GolemSealMenu(98,p.getInventory(),h.getLevel(),f.data.get(f.key)); h.assertTrue(menu.use()&&!menu.hasArea()&&!menu.hasLimit()&&!menu.hasLeaveOne(),"Single ghost Use menu reuses source authority/inventory guards");
            for(int id:new int[]{60,61,32,33,34,35,36,37,38}) h.assertTrue(menu.clickMenuButton(p,id),"Source Use option action accepted "+id);
            menu.setCarried(new ItemStack(Items.IRON_INGOT,4)); menu.clicked(0,0,ClickType.PICKUP,p); h.assertTrue(menu.getCarried().getCount()==4&&menu.getSlot(0).getItem().getCount()==1,"Ghost copy never owns actual4"); var tag=f.data.save(new CompoundTag(),h.getLevel().registryAccess()); var loaded=GolemSeals.load(tag,h.getLevel().registryAccess()); var s=loaded.get(f.key);
            h.assertTrue(s.type()==GolemSeals.Type.USE&&!s.filter().options().metadata()&&!s.filter().options().nbt()&&s.filter().options().ore()&&s.filter().options().mod(),"Four matching flags survive native serializer"); for(int i=4;i<=8;i++) h.assertTrue(loaded.useSettings(s).toggle(i),"Source action flag persists "+i);
            var stranger=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"WrongUseOwner")); stranger.moveTo(p.position()); h.assertTrue(!menu.clickMenuButton(stranger,64),"Another viewer cannot edit Use flags"); h.succeed();
        } finally { p.getInventory().clearContent(); f.close(); }
    }
    @GameTest(template="golem_test",batch="use_recipe",timeoutTicks=50)
    public static void actualUseRecipePaysBlankAndTwentyFabricoTenSensusTwentyCognitio(GameTestHelper h) {
        h.setBlock(new BlockPos(5,1,5),ThaumcraftBlocks.CRUCIBLE.get()); var c=(CrucibleBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,1,5))); var tag=new CompoundTag(); tag.putInt("Water",1000); tag.putShort("Heat",(short)200); c.loadWithComponents(tag,h.getLevel().registryAccess()); var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"UseCrafter"));
        for(String aspect:new String[]{"fabrico","sensus","cognitio"}) { int count=aspect.equals("sensus")?10:20; var stack=CrystalEssenceItem.ofAspect(aspect); stack.setCount(count); int debit=c.dissolve(stack,count); stack.shrink(debit); h.assertTrue(debit==count&&stack.isEmpty(),"Actual source crystals paid "+aspect); }
        var catalyst=new ItemStack(GolemModule.BLANK_SEAL.get()); h.assertTrue(c.attemptSmelt(catalyst,1,p)==0&&c.waterMb()==1000,"Unknown SEALUSE cannot debit"); p.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch("SEALUSE"); int debit=c.attemptSmelt(catalyst,1,p); catalyst.shrink(debit); var output=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()); h.assertTrue(debit==1&&catalyst.isEmpty()&&c.getAspects().visSize()==0&&c.waterMb()==950&&output.size()==1&&output.getFirst().getItem().is(GolemModule.USE_SEAL.get()),"Actual blank1/Fabrico20/Sensus10/Cognitio20/water50→Use1"); output.forEach(ItemEntity::discard); h.succeed();
    }
    @GameTest(template="golem_test",batch="use_hauler",timeoutTicks=75)
    public static void sourceEmptyGhostCompletionUsesMainOnlyButConfiguredFilterCanUseActualOffhand(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,false); f.golem.initialize(GolemDesign.fromIds(0,1,1,0,3,0),f.golem.ownerId(),f.key.pos(),0); f.golem.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.DIRT,2));
        h.runAfterDelay(10,()->{ h.assertTrue(complete(h,f,claim(f))&&f.golem.rankXp()==0&&h.getLevel().isEmptyBlock(f.key.pos().above())&&f.golem.getOffhandItem().getCount()==2,"Source empty ghost reads empty MAIN: reserves but suspends without click/XP or OFF debit");
            f.data.configure(f.data.get(f.key),f.golem.ownerId(),new SealFilter(new ItemStack(Items.DIRT),false,0));
            h.runAfterDelay(5,()->{ h.assertTrue(complete(h,f,claim(f))&&h.getLevel().getBlockState(f.key.pos().above()).is(Blocks.DIRT)&&f.golem.getMainHandItem().getCount()+f.golem.getOffhandItem().getCount()==1&&f.golem.rankXp()==1,"Configured source filter selects actual OFF, pays one block and returns actual remainder"); f.close(); h.succeed(); });
        });
    }
    @GameTest(template="golem_test",batch="use_exception",timeoutTicks=70)
    public static void exceptionInsideRealInteractionCallbackRecoversActualInventoryAndClosesTask(GameTestHelper h) {
        var f=fixture(h,Blocks.STONE,false); f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIRT,4));
        h.runAfterDelay(10,()->{ var task=claim(f); java.util.function.Consumer<PlayerInteractEvent.RightClickBlock> fault=e->{ if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.key.pos())) throw new IllegalStateException("Intentional GameTest Use callback fault"); }; NeoForge.EVENT_BUS.addListener(fault);
            try { h.assertTrue(complete(h,f,task)&&f.golem.getMainHandItem().is(Items.DIRT)&&f.golem.getMainHandItem().getCount()==4&&h.getLevel().isEmptyBlock(f.key.pos().above())&&f.golem.rankXp()==1&&!complete(h,f,task),"Source catches attempted-click failure, real four items recovered and old task cannot replay"); }
            finally { NeoForge.EVENT_BUS.unregister(fault); f.close(); } h.succeed();
        });
    }

}
