package dev.ehtid.thaumcraft.golem.api;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
        Objects.requireNonNull(block,"crop");
        if(seed.isEmpty()||!(seed.getItem() instanceof BlockItem))throw new IllegalArgumentException("Seed must provide native BlockItem planting; non-BlockItem callbacks need an adapter");
        var r=new SeedRegistration(block,seed.copyWithCount(1));SEEDS.add(r);return r;
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
        private final Block block;private final ItemStack seed;
        private SeedRegistration(Block block,ItemStack seed){this.block=block;this.seed=seed;}
        @Override public void close(){SEEDS.remove(this);}
    }
}
