package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.press.*;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.GolemPressBlueprint;
import dev.ehtid.thaumcraft.port.core.golem.GolemPressBlueprint.Part;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemFormationGameTests {
    private GolemFormationGameTests() {}
    @GameTest(template="golem_test", batch="formation_rotations", timeoutTicks=100)
    public static void allFourBlueprintRotationsPayDustOnceAndWaitFiftyTicks(GameTestHelper h) {
        var owner = player(h); var origins = new BlockPos[]{new BlockPos(2,1,2),new BlockPos(6,1,2),new BlockPos(2,1,6),new BlockPos(6,1,6)};
        var knowledge=owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        knowledge.addResearch("UNLOCKALCHEMY");knowledge.setResearchStage("UNLOCKALCHEMY",1);
        var researchBefore = Set.copyOf(owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().getResearchList());
        var before = new ArrayList<BlockPos>();
        for (int rot = 0; rot < 4; rot++) {
            BlockPos origin = h.absolutePos(origins[rot]); placeSource(h.getLevel(), origin, rot);
            BlockPos controller = pos(origin,rot,Part.CONTROLLER); before.add(controller);
            owner.moveTo(Vec3.atBottomCenterOf(controller.east()));
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get(),2));
            h.assertTrue(use(owner,controller,Direction.UP).consumesAction() && owner.getMainHandItem().getCount()==1,"First dust paid");
            h.assertTrue(use(owner,controller,Direction.UP).consumesAction() && owner.getMainHandItem().getCount()==1,"Pending replay spends no second dust");
            h.assertTrue(use(owner,pos(origin,rot,Part.CAULDRON),Direction.UP).consumesAction() && owner.getMainHandItem().getCount()==1,
                    "Reserved cauldron cannot spend another dust through the otherwise valid simple trigger");
        }
        h.startSequence().thenIdle(40).thenExecute(() -> {
            for (var p:before) h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.PISTON),"No early swap before50 ticks");
        }).thenIdle(16).thenExecute(() -> {
            for(int rot=0;rot<4;rot++) checkFormed(h,h.absolutePos(origins[rot]),rot);
            h.assertTrue(researchBefore.equals(owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().getResearchList()),"Formation does not award unrelated research");
            owner.getInventory().clearContent();
        }).thenSucceed();
    }

    @GameTest(template="golem_test", batch="formation_inputs", timeoutTicks=100)
    public static void researchAndExactPistonRejectWithoutPaymentAndLegacyWaterCauldronIsAccepted(GameTestHelper h) {
        var owner=player(h); var knowledge=owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        knowledge.removeResearch("MINDCLOCKWORK");
        BlockPos origin=h.absolutePos(new BlockPos(4,1,4));placeSource(h.getLevel(),origin,0);BlockPos controller=pos(origin,0,Part.CONTROLLER);
        owner.moveTo(Vec3.atBottomCenterOf(controller.east()));owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get(),2));
        h.assertTrue(!use(owner,controller,Direction.EAST).consumesAction()&&owner.getMainHandItem().getCount()==2,"Unknown research cannot activate or pay");
        knowledge.addResearch("MINDCLOCKWORK"); // Basic known research, @2 is not required by source formation.
        h.getLevel().setBlock(controller,Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.UP),18);
        h.assertTrue(!use(owner,controller,Direction.EAST).consumesAction(),"Sticky piston is not source block");
        h.getLevel().setBlock(controller,Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.UP).setValue(PistonBaseBlock.EXTENDED,true),18);
        h.assertTrue(!use(owner,controller,Direction.EAST).consumesAction(),"Extended piston not exact source state");
        h.getLevel().setBlock(controller,Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.UP),18);
        h.getLevel().setBlockAndUpdate(pos(origin,0,Part.CAULDRON),Blocks.LAVA_CAULDRON.defaultBlockState());
        h.assertTrue(!use(owner,controller,Direction.EAST).consumesAction(),"New lava cauldron has no BETA26 counterpart");
        // Do not reuse a piston with a queued vanilla retraction event for the positive fixture.
        BlockPos positive=origin.offset(3,0,3);placeSource(h.getLevel(),positive,0);BlockPos positiveController=pos(positive,0,Part.CONTROLLER);
        owner.moveTo(Vec3.atBottomCenterOf(positiveController.east()));
        h.getLevel().setBlockAndUpdate(pos(positive,0,Part.CAULDRON),Blocks.WATER_CAULDRON.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos(positive,0,Part.ANVIL),Blocks.DAMAGED_ANVIL.defaultBlockState());
        h.assertTrue(use(owner,positiveController,Direction.EAST).consumesAction()&&owner.getMainHandItem().getCount()==1,"Old cauldron levels/anvil damage map to source block variants");
        h.startSequence().thenIdle(55).thenExecute(() -> {
            checkFormed(h,positive,0);h.assertTrue(h.getLevel().getBlockState(positiveController).getValue(GolemPressBlock.FACING)==Direction.EAST,"Source horizontal click facing");
            owner.getInventory().clearContent();
        }).thenSucceed();
    }

    @GameTest(template="golem_test", batch="formation_stale", timeoutTicks=100)
    public static void changedCapturedCellAbortsWholeSwapWithoutOverwritingReplacement(GameTestHelper h) {
        var owner=player(h);BlockPos origin=h.absolutePos(new BlockPos(4,1,4));placeSource(h.getLevel(),origin,0);
        BlockPos controller=pos(origin,0,Part.CONTROLLER);owner.moveTo(Vec3.atBottomCenterOf(controller.east()));
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get()));use(owner,controller,Direction.UP);
        h.getLevel().setBlockAndUpdate(pos(origin,0,Part.ANVIL),Blocks.DIAMOND_BLOCK.defaultBlockState());
        h.startSequence().thenIdle(55).thenExecute(() -> {
            h.assertTrue(h.getLevel().getBlockState(controller).is(Blocks.PISTON)&&h.getLevel().getBlockState(pos(origin,0,Part.ANVIL)).is(Blocks.DIAMOND_BLOCK),"No partial swap or replacement overwrite");
            h.assertTrue(GolemPressStructures.get(h.getLevel()).controllerFor(controller)==null&&owner.getMainHandItem().isEmpty(),"Pending reservation released; source dust remains paid");
        }).thenSucceed();
    }

    @GameTest(template="golem_test", batch="formation_canceled", timeoutTicks=100)
    public static void canceledMultiPlaceRollsBackAllCapturedSourceBlocks(GameTestHelper h) {
        var owner=player(h);BlockPos origin=h.absolutePos(new BlockPos(4,1,4));placeSource(h.getLevel(),origin,1);
        BlockPos controller=pos(origin,1,Part.CONTROLLER);owner.moveTo(Vec3.atBottomCenterOf(controller.east()));
        var captured=GolemPressBlueprint.cells(1).stream().map(c->h.getLevel().getBlockState(origin.offset(c.x(),c.y(),c.z()))).toList();
        int[] events={0};Consumer<BlockEvent.EntityMultiPlaceEvent> listener=event->{if(event.getEntity()==owner){events[0]++;event.setCanceled(true);}};
        NeoForge.EVENT_BUS.addListener(listener);owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get()));use(owner,controller,Direction.UP);
        h.startSequence().thenIdle(55).thenExecute(()->{
            try {
                h.assertTrue(events[0]==1,"Real NeoForge multi-place protection event dispatched once");
                for(var c:GolemPressBlueprint.cells(1)) h.assertTrue(h.getLevel().getBlockState(origin.offset(c.x(),c.y(),c.z()))==captured.get(c.part().ordinal()),"All5 snapshots restored on cancel");
                h.assertTrue(GolemPressStructures.get(h.getLevel()).controllerFor(controller)==null,"Canceled structure cannot operate");
            }finally{NeoForge.EVENT_BUS.unregister(listener);}
        }).thenSucceed();
    }

    @GameTest(template="golem_test", batch="formation_break", timeoutTicks=100)
    public static void breakingEachOfFivePartsRestoresOthersAndDropsActualOutputOnce(GameTestHelper h) {
        var owner=player(h);var origins=List.of(new BlockPos(2,1,2),new BlockPos(6,1,2),new BlockPos(2,1,6),new BlockPos(6,1,6),new BlockPos(9,1,9));
        for(var local:origins){var origin=h.absolutePos(local);placeSource(h.getLevel(),origin,0);var controller=pos(origin,0,Part.CONTROLLER);
            owner.moveTo(Vec3.atBottomCenterOf(controller.east()));owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get()));use(owner,controller,Direction.UP);}
        h.startSequence().thenIdle(55).thenExecute(()->{
            for(int i=0;i<5;i++){
                var origin=h.absolutePos(origins.get(i));checkFormed(h,origin,0);
                var press=(GolemPressBlockEntity)h.getLevel().getBlockEntity(pos(origin,0,Part.CONTROLLER));press.setItem(0,new ItemStack(Items.IRON_INGOT,7));
                Part broken=Part.values()[i];h.getLevel().destroyBlock(pos(origin,0,broken),true,owner);
                for(var c:GolemPressBlueprint.cells(0)) h.assertTrue(h.getLevel().getBlockState(pos(origin,0,c.part())).is(c.part()==broken?Blocks.AIR:source(c.part())),"Restore all unbroken constituents: "+broken+"/"+c.part());
                h.assertTrue(GolemPressStructures.get(h.getLevel()).controllerFor(pos(origin,0,Part.CONTROLLER))==null,"Restored structure detached");
            }
            h.assertTrue(dropped(h,Items.IRON_INGOT)==35,"Actual7 output dropped once for each of5 teardown paths");
            for(Part p:Part.values())h.assertTrue(dropped(h,source(p).asItem())==1,"Exactly one broken constituent item, no refunds for restored neighbors: "+p);
        }).thenSucceed();
    }

    @GameTest(template="golem_test", batch="formation_nbt_menu", timeoutTicks=100)
    public static void savedFootprintRebuildsLinksAndPlaceholderOpensGuardedProcessorMenu(GameTestHelper h) {
        var owner=new MenuPlayer(h.getLevel());owner.setGameMode(GameType.SURVIVAL);owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch("MINDCLOCKWORK");
        BlockPos origin=h.absolutePos(new BlockPos(4,1,4));placeSource(h.getLevel(),origin,2);BlockPos controller=pos(origin,2,Part.CONTROLLER);
        owner.moveTo(Vec3.atBottomCenterOf(controller.east()));owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get()));use(owner,controller,Direction.UP);
        h.startSequence().thenIdle(55).thenExecute(()->{
            var saved=GolemPressStructures.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());
            var loaded=GolemPressStructures.load(saved,h.getLevel().registryAccess());
            h.getLevel().getDataStorage().set("thaumcraft_golem_press_structures",loaded);
            for(var c:GolemPressBlueprint.cells(2))h.assertTrue(controller.equals(loaded.controllerFor(pos(origin,2,c.part()))),"Persisted exact membership rebuilt for all5 parts");
            var bars=pos(origin,2,Part.BARS);h.getLevel().getBlockState(bars).useWithoutItem(h.getLevel(),owner,new BlockHitResult(Vec3.atCenterOf(bars),Direction.UP,bars,false));
            h.assertTrue(owner.opened!=null&&owner.opened.stillValid(owner),"Placeholder routes to actual server press menu");
            owner.moveTo(owner.position().add(20,0,0));h.assertTrue(!owner.opened.stillValid(owner),"Proxy does not bypass controller distance");
            h.getLevel().destroyBlock(pos(origin,2,Part.TABLE),true);
            h.assertTrue(h.getLevel().getBlockState(controller).is(Blocks.PISTON),"Loaded SavedData drives real teardown");
        }).thenSucceed();
    }

    @GameTest(template="golem_test", batch="formation_precedence", timeoutTicks=40)
    public static void existingSimpleCauldronTriggerKeepsPrecedenceOverPressBlueprint(GameTestHelper h) {
        var owner=player(h);var knowledge=owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();knowledge.addResearch("UNLOCKALCHEMY");knowledge.setResearchStage("UNLOCKALCHEMY",1);
        BlockPos origin=h.absolutePos(new BlockPos(4,1,4));placeSource(h.getLevel(),origin,0);var cauldron=pos(origin,0,Part.CAULDRON);owner.moveTo(Vec3.atBottomCenterOf(cauldron.west()));
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get(),2));
        int before=GolemPressStructures.get(h.getLevel()).size();
        h.assertTrue(use(owner,cauldron,Direction.UP).consumesAction()&&owner.getMainHandItem().getCount()==1,"Existing simple trigger pays one dust");
        h.assertTrue(GolemPressStructures.get(h.getLevel()).size()==before,"Press hook does not intercept an existing simple dust match");
        owner.getInventory().clearContent();h.succeed();
    }

    @GameTest(template="golem_test", batch="formation_recovery", timeoutTicks=120)
    public static void persistedRestorationPausesPaidProcessingAndRestoresWithoutNewMachinaDebit(GameTestHelper h) {
        var owner=player(h);BlockPos origin=h.absolutePos(new BlockPos(4,2,4));placeSource(h.getLevel(),origin,0);
        BlockPos controller=pos(origin,0,Part.CONTROLLER);owner.moveTo(Vec3.atBottomCenterOf(controller.east()));
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get()));use(owner,controller,Direction.UP);
        h.startSequence().thenIdle(55).thenExecute(()->{
            var level=h.getLevel();GolemPressGameTests.ingredients(owner.getInventory(),1);
            level.setBlockAndUpdate(controller.below(),dev.ehtid.thaumcraft.registry.ThaumcraftBlocks.JAR_NORMAL.get().defaultBlockState());
            var jar=(dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity)level.getBlockEntity(controller.below());
            for(int i=0;i<8;i++)h.assertTrue(jar.addFromTransport("machina",1,Direction.UP)==1,"Fixture Machina stock");
            var press=(GolemPressBlockEntity)level.getBlockEntity(controller);h.assertTrue(press.start(owner),"Real paid job before restoration");
            var saved=GolemPressStructures.get(level).save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
            var rows=saved.getList("Structures",net.minecraft.nbt.Tag.TAG_COMPOUND);
            for(int i=0;i<rows.size();i++)if(rows.getCompound(i).getLong("Origin")==origin.asLong()){
                rows.getCompound(i).putString("Phase","RESTORING");rows.getCompound(i).putInt("RestoreMask",15);
            }
            // Inject a legitimate persisted deferred-teardown fixture; this does not claim an actual chunk unload.
            var loaded=GolemPressStructures.load(saved,level.registryAccess());level.getDataStorage().set("thaumcraft_golem_press_structures",loaded);
            level.setBlock(pos(origin,0,Part.BARS),Blocks.AIR.defaultBlockState(),18);
            h.assertTrue(!loaded.canOperate(controller),"Persisted recovery forbids machine operation");
            for(int i=0;i<5;i++)GolemPressBlockEntity.tick(level,controller,press.getBlockState(),press);
            h.assertTrue(press.job().remaining()==8&&jar.snapshot().amount()==8,"Recovery cannot consume Machina or progress an incomplete machine");
        }).thenIdle(25).thenExecute(()->{
            var jar=(dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity)h.getLevel().getBlockEntity(controller.below());
            h.assertTrue(h.getLevel().getBlockState(controller).is(Blocks.PISTON)&&jar.snapshot().amount()==8,"Loaded region restores on bounded periodic retry without another debit");
        }).thenSucceed();
    }

    public static void placeSource(ServerLevel level,BlockPos origin,int rotation){
        for(var c:GolemPressBlueprint.cells(rotation)){
            BlockPos pos=origin.offset(c.x(),c.y(),c.z());if(c.y()==0)level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
            var state=source(c.part()).defaultBlockState();if(c.part()==Part.CONTROLLER)state=state.setValue(PistonBaseBlock.FACING,Direction.UP);
            level.setBlock(pos,state,18);
        }
    }
    public static BlockPos pos(BlockPos origin,int rotation,Part part){var c=GolemPressBlueprint.cells(rotation).get(part.ordinal());return origin.offset(c.x(),c.y(),c.z());}
    public static net.minecraft.world.InteractionResult use(FakePlayer owner,BlockPos pos,Direction face){return ThaumcraftItems.SALIS_MUNDUS.get().onItemUseFirst(owner.getMainHandItem(),new UseOnContext(owner,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),face,pos,false)));}
    private static FakePlayer player(GameTestHelper h){var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"PressAssembler"));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch("MINDCLOCKWORK");return p;}
    private static void checkFormed(GameTestHelper h,BlockPos origin,int rotation){
        for(var c:GolemPressBlueprint.cells(rotation)){var state=h.getLevel().getBlockState(origin.offset(c.x(),c.y(),c.z()));h.assertTrue(c.part()==Part.CONTROLLER?state.is(GolemModule.PRESS.get()):state.getBlock() instanceof GolemPressPlaceholderBlock p&&p.part()==c.part(),"Exact formed5-cell footprint");}
    }
    private static Block source(Part part){return switch(part){case CAULDRON->Blocks.CAULDRON;case ANVIL->Blocks.ANVIL;case CONTROLLER->Blocks.PISTON;case TABLE->GolemModule.STONE_TABLE.get();case BARS->Blocks.IRON_BARS;};}
    private static int dropped(GameTestHelper h,Item item){return h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum();}
    private static final class MenuPlayer extends FakePlayer{
        GolemPressMenu opened;MenuPlayer(ServerLevel level){super(level,new GameProfile(UUID.randomUUID(),"PressProxyViewer"));}
        @Override public OptionalInt openMenu(MenuProvider provider,Consumer<RegistryFriendlyByteBuf> extra){opened=(GolemPressMenu)provider.createMenu(15,getInventory(),this);containerMenu=opened;return OptionalInt.of(15);}
    }
}
