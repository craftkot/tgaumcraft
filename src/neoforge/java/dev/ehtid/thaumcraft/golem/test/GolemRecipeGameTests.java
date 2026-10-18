package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import dev.ehtid.thaumcraft.blockentity.CrucibleBlockEntity;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.crafting.GolemSealCrafting;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.menu.ArcaneWorkbenchMenu;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemRecipeGameTests {
    private GolemRecipeGameTests() {}

    @GameTest(template="golem_test", batch="recipe_blank", timeoutTicks=40)
    public static void actualMenuPaysForExactlyThreeBlankSealsAndRejectsMissingInputs(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(5,1,5));
        h.setBlock(new BlockPos(5,1,5),ThaumcraftBlocks.ARCANE_WORKBENCH.get());
        var bench=(ArcaneWorkbenchBlockEntity)level.getBlockEntity(pos);
        h.assertTrue(bench!=null,"Existing workbench entity");
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"SealRecipeOwner"));
        player.setGameMode(GameType.SURVIVAL); player.moveTo(Vec3.atBottomCenterOf(pos.east())); player.getInventory().clearContent();
        var knowledge=player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        fillBench(bench,2); bench.setVis(100);
        var menu=new ArcaneWorkbenchMenu(9,player.getInventory(),bench); menu.broadcastChanges();
        h.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.displayedVisCost()==20 && menu.requiredCrystalMask()==1,
                "Cost visible; result gated by CONTROLSEALS");
        h.assertTrue(!GolemSealCrafting.craftBlank(bench,player,0) && bench.getVis()==100,"Unknown research charges nothing");
        knowledge.addResearch("CONTROLSEALS");
        bench.setItem(9,ItemStack.EMPTY);
        bench.setItem(9,CrystalEssenceItem.ofAspect("ignis"));
        h.assertTrue(!GolemSealCrafting.craftBlank(bench,player,0),"Wrong crystal cannot pay Aer");
        h.assertTrue(bench.getItem(9).isEmpty(),"Slot rejects an incompatible crystal instead of replacing Aer");
        bench.setItem(9,CrystalEssenceItem.ofAspect("aer"));
        bench.getItem(9).set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                CrystalEssenceItem.ofAspect("ignis").get(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
        h.assertTrue(!GolemSealCrafting.craftBlank(bench,player,0),"Changed crystal components revalidated at commit");
        bench.setItem(9,CrystalEssenceItem.ofAspect("aer")); bench.getItem(9).setCount(2);
        bench.setVis(19); h.assertTrue(!GolemSealCrafting.craftBlank(bench,player,0) && bench.getItem(0).getCount()==2,"Insufficient aura changes no ingredients");
        bench.setVis(100); menu.broadcastChanges();
        h.assertTrue(menu.getSlot(0).getItem().is(GolemModule.BLANK_SEAL.get()) && menu.getSlot(0).getItem().getCount()==3,"Actual preview yields three");
        menu.clicked(0,1,ClickType.PICKUP,player);
        h.assertTrue(menu.getCarried().is(GolemModule.BLANK_SEAL.get()) && menu.getCarried().getCount()==3,"Right click takes one complete paid batch");
        h.assertTrue(bench.getVis()==80 && bench.getItem(9).getCount()==1 && bench.getItem(0).getCount()==1
                && bench.getItem(3).getCount()==1 && bench.getItem(5).getCount()==1 && bench.getItem(8).getCount()==1,
                "Exactly20 vis, one Aer and one each ingredient consumed");
        menu.broadcastChanges(); menu.setCarried(new ItemStack(GolemModule.BLANK_SEAL.get(),63));
        menu.clicked(0,0,ClickType.PICKUP,player);
        h.assertTrue(menu.getCarried().getCount()==63 && bench.getVis()==80,"Insufficient cursor room cannot split/debit output");
        menu.setCarried(ItemStack.EMPTY); menu.quickMoveStack(player,0);
        int obtained=0; for(var stack:player.getInventory().items) if(stack.is(GolemModule.BLANK_SEAL.get())) obtained+=stack.getCount();
        h.assertTrue(obtained==3 && bench.getVis()==60 && bench.getItem(9).isEmpty(),"Shift craft commits one exact batch");
        menu.broadcastChanges(); h.assertTrue(menu.getSlot(0).getItem().isEmpty(),"Paid ingredients cannot replay");
        player.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="golem_test", batch="recipe_seals", timeoutTicks=40)
    public static void cruciblePaysCatalystWaterAndOnlyMatchingEssentiaForBothSeals(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(5,1,5));
        h.setBlock(new BlockPos(5,1,5),ThaumcraftBlocks.CRUCIBLE.get());
        var crucible=(CrucibleBlockEntity)level.getBlockEntity(pos);
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"SealCrucibleOwner"));
        var knowledge=player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        var catalyst=new ItemStack(GolemModule.BLANK_SEAL.get(),3);
        loadCrucible(h,crucible,0,0);
        for (String tag : new String[]{"desiderium","aversio"}) {
            var crystals = CrystalEssenceItem.ofAspect(tag); crystals.setCount(10);
            int dissolved = crucible.dissolve(crystals,10); crystals.shrink(dissolved);
            h.assertTrue(dissolved==10 && crystals.isEmpty(),"Ten actual crystals dissolved through existing scanner path");
        }
        h.assertTrue(crucible.attemptSmelt(catalyst,3,player)==0 && crucible.waterMb()==1000,"Unknown research cannot create seals");
        knowledge.addResearch("SEALCOLLECT"); knowledge.addResearch("SEALSTORE");
        int consumed=crucible.attemptSmelt(catalyst,3,player); catalyst.shrink(consumed);
        h.assertTrue(consumed==2 && catalyst.getCount()==1 && crucible.waterMb()==900,"Two catalysts paid,50mB each, third retained");
        var aspects=crucible.getAspects(); var a=new dev.ehtid.thaumcraft.port.core.AspectRegistry();
        h.assertTrue(dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.amount(aspects,"desiderium")==0
                && dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.amount(aspects,"aversio")==0
                && dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.amount(aspects,"aer")==7,"Exact10 of each, unrelated essence retained");
        var drops=level.getEntitiesOfClass(ItemEntity.class,h.getBounds());
        h.assertTrue(drops.size()==2 && drops.stream().filter(e->e.getItem().is(GolemModule.PICKUP_SEAL.get())).count()==1
                && drops.stream().filter(e->e.getItem().is(GolemModule.FILL_SEAL.get())).count()==1,"One real Pickup and one real Fill");
        h.assertTrue(crucible.attemptSmelt(catalyst,1,player)==0,"Cannot replay without essence");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="recipe_abort", timeoutTicks=40)
    public static void rejectedOutputAndWrongCatalystCannotSpendSealResources(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(5,1,5));
        h.setBlock(new BlockPos(5,1,5),ThaumcraftBlocks.CRUCIBLE.get());
        var crucible=(CrucibleBlockEntity)level.getBlockEntity(pos); loadCrucible(h,crucible,10,0);
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"SealRecipeAbort"));
        player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch("SEALCOLLECT");
        var catalyst=new ItemStack(GolemModule.BLANK_SEAL.get());
        h.assertTrue(GolemSealCrafting.conversion(new ItemStack(GolemModule.PICKUP_SEAL.get()),
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),crucible.getAspects(),new dev.ehtid.thaumcraft.port.core.AspectRegistry())==null,"Configured seal is not a blank catalyst");
        Consumer<EntityJoinLevelEvent> reject=event->{ if(event.getLevel()==level && event.getEntity() instanceof ItemEntity item
                && item.getItem().is(GolemModule.PICKUP_SEAL.get())) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(reject);
        int consumed;
        try { consumed=crucible.attemptSmelt(catalyst,1,player); }
        finally { NeoForge.EVENT_BUS.unregister(reject); }
        var a=new dev.ehtid.thaumcraft.port.core.AspectRegistry();
        h.assertTrue(consumed==0 && catalyst.getCount()==1 && crucible.waterMb()==1000
                && dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.amount(crucible.getAspects(),"desiderium")==10,"Rejected spawn rolls back water and essence; caller retains input");
        h.assertTrue(crucible.dissolve(catalyst,1)==0,"No-player dropped catalyst does not bypass research");
        var tag=crucible.saveWithFullMetadata(level.registryAccess());
        tag.putShort("Heat",(short)0); crucible.loadWithComponents(tag,level.registryAccess());
        h.assertTrue(crucible.attemptSmelt(catalyst,1,player)==0,"Cold crucible cannot craft");
        h.succeed();
    }

    private static void fillBench(ArcaneWorkbenchBlockEntity bench,int count) {
        bench.setItem(0,new ItemStack(Items.CLAY_BALL,count));
        bench.setItem(3,new ItemStack(ThaumcraftItems.TALLOW.get(),count));
        bench.setItem(5,new ItemStack(Items.RED_DYE,count));
        bench.setItem(8,new ItemStack(ThaumcraftItems.NITOR_YELLOW.get(),count));
        var aer=CrystalEssenceItem.ofAspect("aer"); aer.setCount(count); bench.setItem(9,aer);
    }
    private static void loadCrucible(GameTestHelper h,CrucibleBlockEntity crucible,int desire,int aversion) {
        var tag=new CompoundTag(); tag.putInt("Water",1000); tag.putShort("Heat",(short)200);
        var list=new ListTag();
        for(var pair:new Object[][]{{"desiderium",desire},{"aversio",aversion},{"aer",7}}) {
            var row=new CompoundTag(); row.putString("key",(String)pair[0]); row.putInt("amount",(Integer)pair[1]); list.add(row);
        }
        tag.put("Aspects",list); crucible.loadWithComponents(tag,h.getLevel().registryAccess());
    }
}
