package dev.ehtid.thaumcraft.golem.api;

import net.minecraft.world.item.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Modern exact-state projection of BETA26 CropUtils and ThaumcraftApi.registerSeed. */
public final class GolemHarvestRegistry {
    private enum Kind { STANDARD,CLICKABLE,STACKED }
    private static final CopyOnWriteArrayList<Registration> CROPS=new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<SeedRegistration> SEEDS=new CopyOnWriteArrayList<>();
    private GolemHarvestRegistry() {}
    private static Registration register(Kind kind,BlockState... input) {
        var states=Set.copyOf(Arrays.asList(input.clone()));
        if(states.isEmpty()||states.stream().anyMatch(BlockState::isAir))throw new IllegalArgumentException("Crops require non-air mature states");
        var r=new Registration(kind,states);CROPS.add(r);return r;
    }
    public static Registration registerClickable(BlockState... states){return register(Kind.CLICKABLE,states);}
    public static Registration registerStandard(BlockState... states){return register(Kind.STANDARD,states);}
    public static Registration registerStacked(BlockState... states){return register(Kind.STACKED,states);}
    /** Explicit source metadata32767 maps to the complete modern state set. */
    public static Registration registerClickable(Block block){return registerClickable(states(block));}
    public static Registration registerStandard(Block block){return registerStandard(states(block));}
    public static Registration registerStacked(Block block){return registerStacked(states(block));}
    private static BlockState[] states(Block block){return block.getStateDefinition().getPossibleStates().toArray(BlockState[]::new);}
    public static boolean isClickable(BlockState state){return CROPS.stream().anyMatch(r->r.kind==Kind.CLICKABLE&&r.states.contains(state));}
    public static boolean isMature(BlockState state,boolean sameBlockBelow){return CROPS.stream().anyMatch(r->r.states.contains(state)&&(r.kind!=Kind.STACKED||sameBlockBelow));}
    public static boolean hasBreakableCrop(Block block){return CROPS.stream().anyMatch(r->r.kind!=Kind.CLICKABLE&&r.states.stream().anyMatch(s->s.is(block)));}
    /** The seed may place a different native BlockItem block (e.g. pumpkin seeds -> stem). */
    public static SeedRegistration registerSeed(Block block,ItemStack seed) {
        if(seed.isEmpty()||!(seed.getItem() instanceof BlockItem))throw new IllegalArgumentException("Seed must provide native BlockItem planting; non-BlockItem callbacks need an adapter");
        return registerSeed(block,seed,((BlockItem)seed.getItem()).getBlock());
    }
    /** Explicit replacement for legacy IPlantable: the item's real useOn must plant this block. */
    public static SeedRegistration registerSeed(Block crop,ItemStack seed,Block plantedBlock) {
        return registerSeed(crop,seed,plantedBlock,null);
    }
    /**
     * BETA26's non-IPlantable BlockDirectional path reads the support from the original mature
     * crop's FACING. Modern blocks opt into that contract explicitly; a FACING property alone
     * must never turn an arbitrary block into a supported crop or seed.
     */
    public static SeedRegistration registerDirectionalSeed(Block crop,ItemStack seed,Block plantedBlock,EnumProperty<Direction> supportProperty) {
        Objects.requireNonNull(crop,"crop");Objects.requireNonNull(supportProperty,"support property");
        if(!crop.defaultBlockState().hasProperty(supportProperty))throw new IllegalArgumentException("Support property must belong to the original crop");
        return registerSeed(crop,seed,plantedBlock,supportProperty);
    }
    private static SeedRegistration registerSeed(Block crop,ItemStack seed,Block plantedBlock,EnumProperty<Direction> supportProperty) {
        Objects.requireNonNull(crop,"crop");Objects.requireNonNull(plantedBlock,"plant");
        if(seed.isEmpty()||plantedBlock.defaultBlockState().isAir())throw new IllegalArgumentException("A real seed and non-air native plant are required");
        var r=new SeedRegistration(crop,seed.copyWithCount(1),plantedBlock,supportProperty);SEEDS.add(r);return r;
    }
    /** Pure original-state lookup; ordinary seed registrations retain their floor support. */
    public static Direction replantSupport(BlockState originalCrop,ItemStack actualSeed) {
        var r=latestSeed(originalCrop.getBlock());
        if(r==null)return Direction.DOWN;
        if(!ItemStack.isSameItemSameComponents(r.seed,actualSeed))return null;
        return r.supportProperty==null?Direction.DOWN:originalCrop.getValue(r.supportProperty);
    }
    /** Stored support faces remain valid only under the current exact-seed registration. */
    public static boolean validReplantSupport(Block crop,ItemStack actualSeed,Direction support) {
        if(support==null)return false;
        var r=latestSeed(crop);
        if(r==null)return support==Direction.DOWN;
        if(!ItemStack.isSameItemSameComponents(r.seed,actualSeed))return false;
        return r.supportProperty==null?support==Direction.DOWN:r.supportProperty.getPossibleValues().contains(support);
    }
    private static SeedRegistration latestSeed(Block crop) {
        var snapshot=SEEDS.toArray(SeedRegistration[]::new);
        for(int i=snapshot.length-1;i>=0;i--)if(snapshot[i].block==crop)return snapshot[i];
        return null;
    }
    public static Block plantedBlock(Block crop,ItemStack actualSeed) {
        var snapshot=SEEDS.toArray(SeedRegistration[]::new);
        for(int i=snapshot.length-1;i>=0;i--){var r=snapshot[i];if(r.block==crop)return ItemStack.isSameItemSameComponents(r.seed,actualSeed)?r.plant:null;}
        return actualSeed.getItem() instanceof BlockItem item?item.getBlock():null;
    }
    public static ItemStack seed(Block block) {
        var snapshot=SEEDS.toArray(SeedRegistration[]::new);
        for(int i=snapshot.length-1;i>=0;i--){var r=snapshot[i];if(r.block==block)return r.seed.copy();}
        return ItemStack.EMPTY;
    }
    public static final class Registration implements AutoCloseable {
        private final Kind kind;private final Set<BlockState> states;
        private Registration(Kind kind,Set<BlockState> states){this.kind=kind;this.states=states;}
        @Override public void close(){CROPS.remove(this);}
    }
    public static final class SeedRegistration implements AutoCloseable {
        private final Block block;private final ItemStack seed;private final Block plant;
        private final EnumProperty<Direction> supportProperty;
        private SeedRegistration(Block block,ItemStack seed,Block plant,EnumProperty<Direction> supportProperty){this.block=block;this.seed=seed;this.plant=plant;this.supportProperty=supportProperty;}
        @Override public void close(){SEEDS.remove(this);}
    }
}
