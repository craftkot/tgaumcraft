package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.press.*;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.item.*;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.*;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemConstructionGameTests {
    private static final GolemDesign FINE=GolemDesign.fromIds(0,1,1,0,0,0);
    private GolemConstructionGameTests() {}
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
    private static FakePlayer player(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"GolemDesigner")); p.setGameMode(GameType.SURVIVAL);
        p.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6,1,5)))); p.getInventory().clearContent(); return p;
    }
    private static void know(FakePlayer p,GolemDesign design) {
        var k=p.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(); GolemRuntimeRules.research(design).forEach(k::addResearch);
    }
    private static GolemPressBlockEntity press(GameTestHelper h) {
        h.setBlock(new BlockPos(5,1,5),GolemModule.PRESS.get()); return (GolemPressBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,1,5)));
    }
    private static void ingredients(FakePlayer p,GolemDesign design) {
        int slot=0; for(var c:GolemAssembly.components(design)) p.getInventory().setItem(slot++,GolemComponents.stack(c));
    }
    private static ThaumcraftGolem worker(GameTestHelper h,GolemDesign design,int x,int z) {
        var g=GolemModule.GOLEM.get().create(h.getLevel()); var pos=h.absolutePos(new BlockPos(x,1,z));
        g.moveTo(Vec3.atBottomCenterOf(pos)); g.initialize(design,UUID.randomUUID(),pos,0); g.setNoAi(true);
        h.assertTrue(h.getLevel().addFreshEntity(g),"Actual construction entity"); return g;
    }

    @GameTest(template="golem_test",batch="construction_paid",timeoutTicks=140)
    public static void selectedSmartFineWorkerPaysEightComponentsAndSixteenMachinaThenSpawns(GameTestHelper h) {
        floor(h); var p=player(h); var machine=press(h); know(p,FINE);
        h.assertTrue(machine.select(p,1,1) && machine.select(p,2,1) && machine.selection().equals(FINE),"Server selects researched SMART/FINE parts");
        ingredients(p,FINE);
        h.setBlock(new BlockPos(5,0,5),ThaumcraftBlocks.JAR_NORMAL.get());
        var jar=(dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,0,5)));
        for(int i=0;i<16;i++) h.assertTrue(jar.addFromTransport("machina",1,Direction.UP)==1,"Actual Machina input");
        h.assertTrue(machine.start(p) && p.getInventory().isEmpty() && machine.job().maximum()==16,"4wood+3mechanisms+biothaumic mind paid, source cost16");
        h.assertTrue(!machine.select(p,1,-1) && !machine.start(p),"Active job design/payment cannot be changed");
        var saved=machine.saveWithFullMetadata(h.getLevel().registryAccess());
        var copy=new GolemPressBlockEntity(machine.getBlockPos(),machine.getBlockState()); copy.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(copy.selection().equals(FINE) && copy.job().props()==FINE.toLong() && copy.job().remaining()==16,"Selected and paid design persist independently");
        h.runAfterDelay(85,()->{
            h.assertTrue(!machine.job().active() && jar.snapshot().amount()==0,"Exactly16 actual units processed");
            var output=machine.removeItem(0,1); h.assertTrue(output.is(GolemModule.GOLEM_PLACER.get()),"One actual configured product");
            var spawn=h.absolutePos(new BlockPos(9,0,8)); p.setItemInHand(InteractionHand.MAIN_HAND,output);
            var context=new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(spawn).add(0,.5,0),Direction.UP,spawn,false));
            h.assertTrue(GolemModule.GOLEM_PLACER.get().useOn(context).consumesAction() && p.getMainHandItem().isEmpty(),"Real selected placer consumed once");
            var entities=h.getLevel().getEntitiesOfClass(ThaumcraftGolem.class,h.getBounds()); h.assertTrue(entities.size()==1,"One configured worker");
            var g=entities.getFirst(); h.assertTrue(g.design().equals(FINE) && g.getMaxHealth()==12 && g.getAttributeValue(Attributes.ARMOR)==1,"Source SMART/DEFT/FRAGILE stats apply");
            g.addRankXp(1000); h.assertTrue(g.design().rank()==1 && g.rankXp()==0 && g.getMaxHealth()==13,"Real SMART work experience increases rank with source threshold");
            var state=new CompoundTag(); g.saveWithoutId(state); var restored=GolemModule.GOLEM.get().create(h.getLevel()); restored.readAdditionalSaveData(state);
            h.assertTrue(restored.design().equals(FINE.withRank(1)) && restored.getMaxHealth()==13 && restored.ownerId().equals(p.getUUID()),"Ranked supported design restores without becoming a default wooden worker");
            g.discard(); p.getInventory().clearContent(); h.succeed();
        });
    }

    @GameTest(template="golem_test",batch="construction_gates",timeoutTicks=40)
    public static void editorRechecksKnowledgeViewerPlateMetadataAndActualPayment(GameTestHelper h) {
        floor(h); var p=player(h); var machine=press(h); ingredients(p,GolemDesign.defaultWood());
        h.assertTrue(!machine.start(p) && p.getInventory().getItem(0).getCount()==3,"Unknown construction knowledge cannot debit ingredients");
        know(p,GolemDesign.defaultWood());
        h.assertTrue(!machine.select(p,1,1),"Unknown SMART/SCOUT research cannot select hidden heads");
        var iron=GolemDesign.fromIds(1,0,0,0,0,0); know(p,iron);
        var menu=new GolemPressMenu(61,p.getInventory(),machine);
        h.assertTrue(menu.clickMenuButton(p,10) && machine.selection().equals(iron) && menu.cost()==12,"Server chooses iron and computes12, never accepts client cost");
        h.assertTrue(menu.getSlot(37).getItem().getCount()==3 && menu.getSlot(38).getItem().getCount()==2,"Ghost preview shows actual selected component quantities");
        menu.clicked(37,0,net.minecraft.world.inventory.ClickType.THROW,p);
        menu.clicked(37,0,net.minecraft.world.inventory.ClickType.CLONE,p);
        h.assertTrue(menu.quickMoveStack(p,37).isEmpty() && menu.getCarried().isEmpty() && menu.getSlot(37).getItem().getCount()==3,"Recipe ghost cannot become free real items through menu actions");
        var other=player(h); h.assertTrue(!menu.clickMenuButton(other,0) && !menu.clickMenuButton(other,10),"Another viewer cannot send edits through this menu");
        ingredients(p,iron); p.getInventory().setItem(0,PlateItem.create(0).copyWithCount(3));
        h.assertTrue(!machine.start(p) && p.getInventory().getItem(0).getCount()==3,"Brass metadata cannot pay iron material");
        p.getInventory().setItem(0,PlateItem.create(1).copyWithCount(3));
        h.assertTrue(machine.start(p) && p.getInventory().isEmpty(),"Exactly3 actual iron plates+2mechanisms+mind paid");
        h.assertTrue(!menu.clickMenuButton(p,10) && machine.job().props()==iron.toLong(),"Busy paid iron job retains its design");
        var bare=new ItemStack(dev.ehtid.thaumcraft.registry.ThaumcraftItems.PLATE.get());
        h.assertTrue(GolemComponents.matches(PlateItem.create(0),bare),"Source bare plate metadata0 equals explicit modern brass variant");
        bare.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("different"));
        h.assertTrue(!GolemComponents.matches(PlateItem.create(0),bare),"Payment does not erase unrelated source components");
        p.getInventory().clearContent(); other.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="golem_test",batch="construction_damage",timeoutTicks=40)
    public static void materialsApplySourceFireCactusAndCappedBlastRules(GameTestHelper h) {
        floor(h); var iron=worker(h,GolemDesign.fromIds(1,0,0,0,0,0),2,2); iron.getAttribute(Attributes.ARMOR).setBaseValue(0);
        iron.hurt(h.getLevel().damageSources().inFire(),10); h.assertTrue(iron.getHealth()==30,"Iron FIREPROOF prevents actual fire health loss");
        var blast=worker(h,GolemDesign.fromIds(1,0,0,0,0,0),4,2); blast.getAttribute(Attributes.ARMOR).setBaseValue(0);
        blast.hurt(h.getLevel().damageSources().explosion(null,null),100); h.assertTrue(blast.getHealth()==15,"BLASTPROOF scales0.3 and caps at half max health before armor");
        var wood=worker(h,GolemDesign.defaultWood(),6,2); wood.getAttribute(Attributes.ARMOR).setBaseValue(0);
        wood.hurt(h.getLevel().damageSources().cactus(),10); h.assertTrue(wood.getHealth()==16,"Source cactus immunity also applies to wood");
        var burn=worker(h,GolemDesign.defaultWood(),8,2); burn.getAttribute(Attributes.ARMOR).setBaseValue(0);
        burn.hurt(h.getLevel().damageSources().inFire(),5); h.assertTrue(burn.getHealth()==11,"Wood is not granted fire immunity");
        for(var g:new ThaumcraftGolem[]{iron,blast,wood,burn}) g.discard(); h.succeed();
    }

    @GameTest(template="golem_test",batch="construction_repair",timeoutTicks=65)
    public static void allSixMaterialsAndVoidRepairHaveRealServerAttributes(GameTestHelper h) {
        floor(h); var all=new java.util.ArrayList<ThaumcraftGolem>();
        for(int m=0;m<6;m++) {
            var d=GolemDesign.fromIds(m,0,0,0,0,0); var g=worker(h,d,1+m,5); all.add(g);
            h.assertTrue(g.getMaxHealth()==d.stats().maxHealth() && g.getAttributeValue(Attributes.ARMOR)==d.stats().armor(),"Material attributes applied for "+d.material()); g.setHealth(g.getMaxHealth()-2);
        }
        h.runAfterDelay(45,()->{
            for(var g:all) { h.assertTrue(g.getHealth()==g.getMaxHealth()-(g.design().material()==GolemMaterial.VOID?1:2),"Only source REPAIR material heals at40ticks"); g.discard(); }
            h.succeed();
        });
    }

    @GameTest(template="golem_test",batch="construction_hauler",timeoutTicks=40)
    public static void haulerCarriesTwoActualStacksAndAdvancedEmptyRequiresSmart(GameTestHelper h) {
        floor(h); var d=GolemDesign.fromIds(0,0,0,0,3,0); var g=worker(h,d,3,3);
        var remainder=g.holdItem(new ItemStack(Items.IRON_INGOT,70));
        h.assertTrue(remainder.isEmpty() && g.getMainHandItem().getCount()==64 && g.getOffhandItem().getCount()==6,"Real HAULER uses two cargo slots");
        var tag=new CompoundTag(); g.saveWithoutId(tag); var restored=GolemModule.GOLEM.get().create(h.getLevel()); restored.readAdditionalSaveData(tag);
        h.assertTrue(restored.design().equals(d) && restored.getMainHandItem().getCount()==64 && restored.getOffhandItem().getCount()==6,"Both actual cargo stacks and configuration round trip");
        h.setBlock(new BlockPos(6,1,6),Blocks.BARREL); var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(6,1,6)),Direction.UP);
        var data=GolemSeals.get(h.getLevel()); var owner=UUID.randomUUID(); data.place(key.pos(),key.face(),owner,GolemSeals.Type.EMPTY_ADVANCED);
        var seal=data.get(key); data.configureLogistics(seal,owner,seal.logistics().withMode(false,true,false).withSlot(8,new SealFilter(new ItemStack(Items.EMERALD),false,0)));
        var inv=(net.neoforged.neoforge.items.IItemHandlerModifiable)dev.ehtid.thaumcraft.golem.inventory.GolemContainers.handler(h.getLevel(),key);
        inv.setStackInSlot(0,new ItemStack(Items.EMERALD,7));
        var basic=worker(h,GolemDesign.defaultWood(),6,5); var smart=worker(h,GolemDesign.fromIds(0,1,0,0,0,0),7,6);
        h.runAfterDelay(25,()->{
            h.assertTrue(data.claimBlock(basic,p->true).isEmpty(),"Basic head cannot use SMART-only advanced Empty");
            var assignment=data.claimBlock(smart,p->true).orElseThrow();
            h.assertTrue(data.completeBlock(h.getLevel(),smart,assignment) && smart.getMainHandItem().getCount()==7 && inv.getStackInSlot(0).isEmpty(),"Actual SMART worker uses ninth extraction filter");
            data.remove(key.pos(),key.face(),owner); g.discard(); basic.discard(); smart.discard(); h.succeed();
        });
    }

    @GameTest(template="golem_test",batch="construction_unported",timeoutTicks=40)
    public static void unportedFunctionsStayGatedAndSavedPayloadIsRetained(GameTestHelper h) {
        floor(h); var d=GolemDesign.fromIds(0,0,4,0,0,0);
        h.assertTrue(!GolemModule.supportsRuntime(d),"Unported ranged function stays gated until real projectiles");
        var g=GolemModule.GOLEM.get().create(h.getLevel()); var tag=new CompoundTag(); tag.putLong("props",d.toLong());
        g.readAdditionalSaveData(tag); var saved=new CompoundTag(); g.saveWithoutId(saved);
        h.assertTrue(g.isNoAi() && saved.getLong("props")==d.toLong(),"Unsupported valid configuration remains inert and preserved for future migration");
        h.succeed();
    }
}
