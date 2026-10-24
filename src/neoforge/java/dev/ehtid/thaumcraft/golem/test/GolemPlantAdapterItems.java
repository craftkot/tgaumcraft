package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Functional native Item.useOn specimens, registered only by the explicit adapter test run. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class GolemPlantAdapterItems {
    private GolemPlantAdapterItems() {}
    public static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID,"adapter_probe_"+name);}
    @SubscribeEvent public static void register(RegisterEvent event){
        if(!Boolean.getBoolean("thaumcraft.golemPlantAdapterProbe"))return;
        event.register(Registries.ITEM,helper->{
            helper.register(id("wheat"),new Item(new Item.Properties()){
                @Override public InteractionResult useOn(UseOnContext c){return Items.WHEAT_SEEDS.useOn(c);}
            });
            helper.register(id("cocoa"),new Item(new Item.Properties()){
                @Override public InteractionResult useOn(UseOnContext c){return Items.COCOA_BEANS.useOn(c);}
            });
            helper.register(id("no_debit"),new CallbackSeed(0));
            helper.register(id("fault"),new CallbackSeed(1));
            helper.register(id("pass"),new CallbackSeed(2));
            helper.register(id("multi_fault"),new CallbackSeed(3));
            helper.register(id("cursor_fault"),new CallbackSeed(4));
        });
    }
    private static final class CallbackSeed extends Item {
        private final int mode;
        CallbackSeed(int mode){super(new Item.Properties());this.mode=mode;}
        @Override public InteractionResult useOn(UseOnContext context){
            var placement=new BlockPlaceContext(context);var pos=placement.getClickedPos();
            var state=Blocks.WHEAT.getStateForPlacement(placement);
            if(!placement.canPlace()||state==null||!state.canSurvive(context.getLevel(),pos))return InteractionResult.FAIL;
            if(!context.getLevel().setBlock(pos,state,3))return InteractionResult.FAIL;
            if(mode==3){
                context.getLevel().setBlock(pos.east(),Blocks.GOLD_BLOCK.defaultBlockState(),3);
                throw new IllegalStateException("Intentional real multi-block Item.useOn fault");
            }
            if(mode==4){
                var player=context.getPlayer();var actual=player.getItemInHand(context.getHand());
                player.setItemInHand(context.getHand(),ItemStack.EMPTY);player.containerMenu.setCarried(actual);
                throw new IllegalStateException("Intentional actual cursor ownership transfer then Item.useOn fault");
            }
            if(mode==1)throw new IllegalStateException("Intentional real placement callback failure");
            if(mode==2)return InteractionResult.PASS;
            // Some callbacks leave payment to the caller; source Harvest still charges its reserved seed.
            context.getItemInHand().set(DataComponents.CUSTOM_NAME,Component.literal("Callback changed the reserved seed components"));
            return InteractionResult.SUCCESS;
        }
    }
}
