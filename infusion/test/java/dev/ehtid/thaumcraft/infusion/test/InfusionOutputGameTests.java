package dev.ehtid.thaumcraft.infusion.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.blockentity.*;
import dev.ehtid.thaumcraft.crafting.InfusionRecipeAdapter;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionRecipeCatalog;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

/** Actual component-bearing outputs, plus original paid dynamic recipes on normal server ticks. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class InfusionOutputGameTests {
    private InfusionOutputGameTests() {}
    private static LegacyInfusionRecipeCatalog.Recipe definition(String id, String output, int count,
                                                                LegacyInfusionRecipeCatalog.Output payload) {
        return new LegacyInfusionRecipeCatalog.Recipe("thaumcraft:" + id,"INFUSION",
                LegacyInfusionRecipeCatalog.Ingredient.anyStack(),
                List.of(LegacyInfusionRecipeCatalog.Ingredient.item("minecraft:diamond")),
                output,count,Map.of("ordo",1),0,payload);
    }
    @GameTest(template="infusion_paid_blank",batch="infusion_output",timeoutTicks=20)
    public static void fixedOutputRetainsCountComponentsAndSourceDamageFraction(GameTestHelper h) {
        var center = new ItemStack(Items.IRON_PICKAXE); center.setDamageValue(125);
        var fixed = definition("OutputFixtureCount","minecraft:arrow",4,
                new LegacyInfusionRecipeCatalog.Output(LegacyInfusionRecipeCatalog.OutputMode.STACK,
                        "{\"minecraft:custom_name\":\"\\\"Source output\\\"\"}","{source:1b}",null,null,null));
        ItemStack output = InfusionRecipeAdapter.output(fixed,center,h.getLevel().registryAccess());
        h.assertTrue(output.getCount()==4 && output.getHoverName().getString().equals("Source output")
                && output.get(DataComponents.CUSTOM_DATA).copyTag().getByte("source")==1,
                "Real stack count, modern components and original typed NBT survive output evaluation");
        var upgraded = definition("OutputFixtureDamage","minecraft:diamond_pickaxe",1,LegacyInfusionRecipeCatalog.Output.stack());
        ItemStack diamond = InfusionRecipeAdapter.output(upgraded,center,h.getLevel().registryAccess());
        h.assertTrue(diamond.getDamageValue()==780,"BETA26 transfers125/250 damage to1561 durability by float truncation");
        h.succeed();
    }
    @GameTest(template="infusion_paid_blank",batch="infusion_output",timeoutTicks=20)
    public static void centralTagMutationPreservesAllComponentsAndRejectsTamperedFrozenOutput(GameTestHelper h) {
        var center = new ItemStack(Items.IRON_HELMET); center.setDamageValue(17);
        center.set(DataComponents.CUSTOM_NAME,Component.literal("Original helmet"));
        var data = new CompoundTag(); data.putInt("retained",42); center.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        var recipe = definition("OutputFixtureTag","minecraft:iron_helmet",1,
                new LegacyInfusionRecipeCatalog.Output(LegacyInfusionRecipeCatalog.OutputMode.SET_TAG,null,null,"goggles","1b",null));
        ItemStack result = InfusionRecipeAdapter.output(recipe,center,h.getLevel().registryAccess());
        h.assertTrue(result.getDamageValue()==17 && result.getHoverName().getString().equals("Original helmet")
                && result.get(DataComponents.CUSTOM_DATA).copyTag().getInt("retained")==42
                && result.get(DataComponents.CUSTOM_DATA).copyTag().getByte("goggles")==1,
                "Source Object[]{label,NBTBase} changes one central tag and preserves physical stack components");
        h.assertTrue(InfusionRecipeAdapter.isValidOutput(recipe,center,result,h.getLevel().registryAccess()),"Legal frozen output validates");
        result.setDamageValue(18);
        h.assertTrue(!InfusionRecipeAdapter.isValidOutput(recipe,center,result,h.getLevel().registryAccess()),"Changed frozen durability fails proof");
        h.assertTrue(!center.get(DataComponents.CUSTOM_DATA).copyTag().contains("goggles"),"Preview does not mutate live central input");
        h.succeed();
    }
    @GameTest(template="infusion_paid_blank",batch="infusion_output",timeoutTicks=20)
    public static void ingredientMetadataAndRelaxedTopLevelNbtMatchSource(GameTestHelper h) {
        var ingredient = new LegacyInfusionRecipeCatalog.Ingredient(List.of(),List.of(),null,
                List.of(new LegacyInfusionRecipeCatalog.StackAlternative("minecraft:iron_pickaxe",7,"{key:{one:1}}")),null,false);
        var stack = new ItemStack(Items.IRON_PICKAXE); stack.setDamageValue(7);
        var nested = new CompoundTag(); nested.putInt("one",1); var tag = new CompoundTag(); tag.put("key",nested);
        tag.putString("extra","allowed"); stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        h.assertTrue(InfusionRecipeAdapter.matches(ingredient,stack),"Extra top-level NBT is allowed with exact source damage");
        stack.setDamageValue(8);
        h.assertTrue(!InfusionRecipeAdapter.matches(ingredient,stack),"Different source metadata fails exact alternative");
        stack.setDamageValue(7); nested.putInt("two",2); tag.put("key",nested); stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        h.assertTrue(!InfusionRecipeAdapter.matches(ingredient,stack),"Nested values require equality, not invented recursive subset matching");
        h.succeed();
    }
    @GameTest(template="infusion_paid_blank",batch="infusion_output",timeoutTicks=20)
    public static void originalInfusionEnchantmentHasTypeMaxAndWarpOutcomeGuards(GameTestHelper h) {
        var recipe = InfusionRecipeAdapter.recipe("thaumcraft:IESOUNDING");
        h.assertTrue(InfusionRecipeAdapter.effectiveRecipe(recipe,new ItemStack(Items.IRON_SWORD))==null,
                "SOUNDING source requires pickaxe class");
        var pickaxe = enchantedPickaxe(3,4);
        h.assertTrue(InfusionRecipeAdapter.effectiveRecipe(recipe,pickaxe)==null,"Source max SOUNDING4 cannot upgrade");
        pickaxe = enchantedPickaxe(0,1); var tag=pickaxe.get(DataComponents.CUSTOM_DATA).copyTag(); tag.putByte("TC.WARP",(byte)4);
        pickaxe.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        var warped = InfusionRecipeAdapter.output(recipe,pickaxe,h.getLevel().registryAccess(),bound -> 0);
        var safe = InfusionRecipeAdapter.output(recipe,pickaxe,h.getLevel().registryAccess(),bound -> 9);
        h.assertTrue(warped.get(DataComponents.CUSTOM_DATA).copyTag().getByte("TC.WARP")==5
                && safe.get(DataComponents.CUSTOM_DATA).copyTag().getByte("TC.WARP")==4,"Source existing enchant count gives exact warp-roll boundary");
        h.assertTrue(InfusionRecipeAdapter.isValidOutput(recipe,pickaxe,warped,h.getLevel().registryAccess())
                && InfusionRecipeAdapter.isValidOutput(recipe,pickaxe,safe,h.getLevel().registryAccess()),"Both possible frozen source outcomes validate");
        var bad=warped.get(DataComponents.CUSTOM_DATA).copyTag(); bad.putByte("TC.WARP",(byte)6); warped.set(DataComponents.CUSTOM_DATA,CustomData.of(bad));
        h.assertTrue(!InfusionRecipeAdapter.isValidOutput(recipe,pickaxe,warped,h.getLevel().registryAccess()),"Arbitrary saved enchantment warp is rejected");
        h.succeed();
    }
    private static ItemStack enchantedPickaxe(int ordinal,int level) {
        var result=new ItemStack(Items.IRON_PICKAXE); var tag=new CompoundTag(); var entry=new CompoundTag();
        entry.putShort("id",(short)ordinal); entry.putShort("lvl",(short)level); var list=new ListTag(); list.add(entry);
        tag.put("infench",list); result.set(DataComponents.CUSTOM_DATA,CustomData.of(tag)); return result;
    }
    @GameTest(template="infusion_paid_blank",batch="infusion_dynamic_paid",timeoutTicks=3000)
    public static void runicSecondUpgradePaysDynamicPriceAndRestoresPaidCheckpoint(GameTestHelper h) { paid(h,true); }
    @GameTest(template="infusion_paid_blank",batch="infusion_dynamic_paid",timeoutTicks=3000)
    public static void soundingPaysExistingEnchantSurchargeAndRestoresFrozenRandomOutput(GameTestHelper h) { paid(h,false); }
    @GameTest(template="infusion_paid_blank",batch="infusion_dynamic_paid",timeoutTicks=2400)
    public static void mirrorEssentiaPaysOriginalRecipeAndRestoresPaidCheckpoint(GameTestHelper h) { paid(h,false,true); }

    private static void paid(GameTestHelper h,boolean runic) { paid(h,runic,false); }
    private static void paid(GameTestHelper h,boolean runic,boolean mirror) {
        BlockPos matrixPos=h.absolutePos(new BlockPos(12,8,12)); var level=h.getLevel();
        level.setBlockAndUpdate(matrixPos,ThaumcraftBlocks.INFUSION_MATRIX.get().defaultBlockState());
        level.setBlockAndUpdate(matrixPos.below(2),ThaumcraftBlocks.PEDESTAL_ARCANE.get().defaultBlockState());
        for(int x:new int[]{-1,1}) for(int z:new int[]{-1,1})
            level.setBlockAndUpdate(matrixPos.offset(x,-2,z),ThaumcraftBlocks.PILLAR_ARCANE.get().defaultBlockState());
        // Genuine source-supported symmetric decorations; no saved stability is injected.
        for(int index=0;index<16;index++) for(int sign:new int[]{-1,1}) {
            int x=(index%4+2)*sign,z=(index/4+2)*sign;
            var stabilizer=ResourceLocation.parse("thaumcraft:stabilizer");
            h.assertTrue(BuiltInRegistries.BLOCK.containsKey(stabilizer),"Original physical stabilizer is registered");
            level.setBlockAndUpdate(matrixPos.offset(x,-5,z),BuiltInRegistries.BLOCK.get(stabilizer).defaultBlockState());
            BlockPos skull=matrixPos.offset(x,-3,z); level.setBlockAndUpdate(skull.below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(skull,Blocks.SKELETON_SKULL.defaultBlockState());
        }
        var positions=List.of(matrixPos.offset(-3,-2,0),matrixPos.offset(3,-2,0),matrixPos.offset(0,-2,3),matrixPos.offset(0,-2,-3));
        ItemStack central;
        if(mirror) { var mirroredGlass=ResourceLocation.parse("thaumcraft:mirrored_glass");
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(mirroredGlass),"Original mirrored glass input is registered");
            central=new ItemStack(BuiltInRegistries.ITEM.get(mirroredGlass)); }
        else if(runic) { central=new ItemStack(Items.IRON_CHESTPLATE); var data=new CompoundTag(); data.putByte("TC.RUNIC",(byte)1);
            central.set(DataComponents.CUSTOM_DATA,CustomData.of(data)); }
        else central=enchantedPickaxe(0,1);
        central.set(DataComponents.CUSTOM_NAME,Component.literal("Physical original"));
        if(!mirror) central.setDamageValue(12);
        var center=(PedestalBlockEntity)level.getBlockEntity(matrixPos.below(2)); center.insertFromPlayer(central);
        List<ItemStack> sides=mirror ? List.of(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.ENDER_PEARL))
                :runic ? List.of(new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get()),new ItemStack(ThaumcraftItems.AMBER.get()),new ItemStack(ThaumcraftItems.AMBER.get()))
                : List.of(new ItemStack(Items.ENCHANTED_BOOK),new ItemStack(Items.MAP));
        for(int i=0;i<sides.size();i++) { level.setBlockAndUpdate(positions.get(i),ThaumcraftBlocks.PEDESTAL_ARCANE.get().defaultBlockState());
            ((PedestalBlockEntity)level.getBlockEntity(positions.get(i))).insertFromPlayer(sides.get(i)); }
        String[] aspects=mirror ? new String[]{"motus","aqua","permutatio"}:runic ? new String[]{"praemunio","vitreus","potentia"}:new String[]{"sensus","ignis"};
        int[] amounts=mirror ? new int[]{25,25,25}:runic ? new int[]{60,30,30}:new int[]{53,79}; int expected=mirror?75:runic?120:132;
        var jars=List.of(matrixPos.offset(-4,-2,-2),matrixPos.offset(0,-2,-4),matrixPos.offset(4,-2,-2));
        for(int i=0;i<aspects.length;i++) { level.setBlockAndUpdate(jars.get(i),ThaumcraftBlocks.JAR_NORMAL.get().defaultBlockState());
            h.assertTrue(((EssentiaJarBlockEntity)level.getBlockEntity(jars.get(i))).addFromTransport(aspects[i],amounts[i],Direction.UP)==amounts[i],"Exact original dynamic price in physical jars"); }
        ServerPlayer actor=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"DynamicInfusion")); actor.setGameMode(GameType.SURVIVAL);
        actor.getInventory().clearContent(); actor.moveTo(Vec3.atBottomCenterOf(matrixPos.north()));
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ThaumcraftItems.CASTER_BASIC.get()));
        actor.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch(mirror?"MIRRORESSENTIA":runic?"RUNICSHIELDING":"INFUSIONENCHANTMENT");
        Runnable click=()->actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(matrixPos),Direction.NORTH,matrixPos,false));
        click.run(); click.run(); var matrix=(InfusionMatrixBlockEntity)level.getBlockEntity(matrixPos);
        h.assertTrue(matrix.crafting(),"Actual researched caster starts original dynamic infusion");
        var initial=matrix.saveWithFullMetadata(level.registryAccess()).getCompound("PortInfusionLedger").getCompound("Initial");
        for(int i=0;i<aspects.length;i++) h.assertTrue(initial.getInt(aspects[i])==amounts[i],"Independent source dynamic price frozen before payment");
        boolean[] restored={false}; int[] completed={-1},tick={0}; ItemStack[] published={ItemStack.EMPTY};
        h.onEachTick(()->{
            tick[0]++; int left=0;
            for(int i=0;i<aspects.length;i++) left+=((EssentiaJarBlockEntity)level.getBlockEntity(jars.get(i))).snapshot().amount();
            if(matrix.crafting()) {
                var saved=matrix.saveWithFullMetadata(level.registryAccess()); var ledger=saved.getCompound("PortInfusionLedger");
                h.assertTrue(ledger.getInt("Paid")+left==expected,"Every native dynamic debit equals real jar loss");
                if(!restored[0] && ledger.getInt("Paid")>=8) { matrix.loadWithComponents(saved,level.registryAccess());
                    h.assertTrue(matrix.crafting(),"Paid dynamic definition and frozen output restore through actual NBT loader"); restored[0]=true; }
                return;
            }
            h.assertTrue(restored[0] && left==0,"Original dynamic infusion finishes after exact paid source price");
            ItemStack actual=center.displayedItem(); var custom=actual.get(DataComponents.CUSTOM_DATA);
            var data=custom==null?new CompoundTag():custom.copyTag();
            if(mirror) h.assertTrue(actual.getCount()==1 && actual.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:mirror_essentia")))
                    && !actual.has(DataComponents.CUSTOM_NAME),"Paid source fixed result is one original essentia mirror with its own components");
            else h.assertTrue(actual.getDamageValue()==12 && actual.getHoverName().getString().equals("Physical original"),"Actual publication preserves central components");
            if(runic) h.assertTrue(data.getByte("TC.RUNIC")==2,"Paid source runic1 upgrades to2");
            else if(!mirror) { var entries=data.getList("infench",Tag.TAG_COMPOUND); boolean sound=false,collector=false;
                for(int i=0;i<entries.size();i++) { var e=entries.getCompound(i); if(e.getShort("id")==3 && e.getShort("lvl")==1) sound=true;
                    if(e.getShort("id")==0 && e.getShort("lvl")==1) collector=true; }
                h.assertTrue(sound && collector,"Paid source enchant adds SOUNDING1 and retains COLLECTOR1"); }
            for(int i=0;i<sides.size();i++) h.assertTrue(!((PedestalBlockEntity)level.getBlockEntity(positions.get(i))).isOccupied(),"Original side inputs consumed");
            if(completed[0]<0) { completed[0]=tick[0]; published[0]=actual.copy(); click.run(); h.assertTrue(!matrix.crafting(),"Consumed inputs cannot replay source output"); }
            h.assertTrue(ItemStack.matches(published[0],actual),"Frozen output stays exact through normal replay ticks");
            if(tick[0]-completed[0]>=40) { actor.getInventory().clearContent(); h.succeed(); }
        });
    }
}
