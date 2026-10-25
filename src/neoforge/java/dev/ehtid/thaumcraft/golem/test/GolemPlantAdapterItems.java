package dev.ehtid.thaumcraft.golem.test;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
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
        event.register(Registries.BLOCK,helper->{
            helper.register(id("directional"),new DirectionalPlant(BlockBehaviour.Properties.of().noCollission().noOcclusion().instabreak().noLootTable()));
            helper.register(id("six_way"),new SixWayPlant(BlockBehaviour.Properties.of().noCollission().noOcclusion().instabreak().noLootTable()));
        });
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
            helper.register(id("directional"),new DirectionalSeed(false));
            helper.register(id("directional_fault"),new DirectionalSeed(true));
            helper.register(id("six_way"),new SixWaySeed());
        });
    }
    /** An actual modern directional block with no IPlantable implementation or BlockItem. */
    public static final class DirectionalPlant extends HorizontalDirectionalBlock {
        private static final MapCodec<DirectionalPlant> CODEC=simpleCodec(DirectionalPlant::new);
        public static final IntegerProperty AGE=IntegerProperty.create("age",0,1);
        public DirectionalPlant(BlockBehaviour.Properties properties){
            super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(AGE,0));
        }
        @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,AGE);}
        @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){
            return level.getBlockState(pos.relative(state.getValue(FACING))).is(Blocks.JUNGLE_LOG);
        }
    }
    private static final class DirectionalSeed extends Item {
        private final boolean fault;
        DirectionalSeed(boolean fault){super(new Item.Properties());this.fault=fault;}
        @Override public InteractionResult useOn(UseOnContext context){
            if(context.getClickedFace().getAxis().isVertical())return InteractionResult.FAIL;
            var placement=new BlockPlaceContext(context);var pos=placement.getClickedPos();
            var plant=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id("directional"));
            var state=plant.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,context.getClickedFace().getOpposite());
            if(!placement.canPlace()||!state.canSurvive(context.getLevel(),pos)||!context.getLevel().setBlock(pos,state,3))return InteractionResult.FAIL;
            if(fault){
                var actor=context.getPlayer();var actual=actor.getItemInHand(context.getHand());
                actor.setItemInHand(context.getHand(),ItemStack.EMPTY);actor.containerMenu.setCarried(actual);
                throw new IllegalStateException("Intentional actual directional placement and cursor transfer fault");
            }
            // The native callback does not pay itself and changes its actual reserved stack.
            context.getItemInHand().set(DataComponents.CUSTOM_NAME,Component.literal("Directional callback changed reserved components"));
            return InteractionResult.SUCCESS;
        }
    }
    /** Source BlockDirectional permits all six faces; vertical specimens remain test-run only. */
    public static final class SixWayPlant extends DirectionalBlock {
        private static final MapCodec<SixWayPlant> CODEC=simpleCodec(SixWayPlant::new);
        public static final IntegerProperty AGE=IntegerProperty.create("age",0,1);
        public SixWayPlant(BlockBehaviour.Properties properties){
            super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.DOWN).setValue(AGE,0));
        }
        @Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,AGE);}
        @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){
            return level.getBlockState(pos.relative(state.getValue(FACING))).is(Blocks.JUNGLE_LOG);
        }
    }
    private static final class SixWaySeed extends Item {
        SixWaySeed(){super(new Item.Properties());}
        @Override public InteractionResult useOn(UseOnContext context){
            var placement=new BlockPlaceContext(context);var pos=placement.getClickedPos();
            var plant=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id("six_way"));
            var state=plant.defaultBlockState().setValue(DirectionalBlock.FACING,context.getClickedFace().getOpposite());
            if(!placement.canPlace()||!state.canSurvive(context.getLevel(),pos)||!context.getLevel().setBlock(pos,state,3))return InteractionResult.FAIL;
            // As with the horizontal specimen, source Harvest must debit the actual reservation.
            context.getItemInHand().set(DataComponents.CUSTOM_NAME,Component.literal("Six-way callback changed reserved components"));
            return InteractionResult.SUCCESS;
        }
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
