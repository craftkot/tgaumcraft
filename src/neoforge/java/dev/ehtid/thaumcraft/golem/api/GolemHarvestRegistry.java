package dev.ehtid.thaumcraft.golem.api;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Modern exact-state projection of BETA26 CropUtils.addClickableCrop; no crops enrolled by default. */
public final class GolemHarvestRegistry {
    private static final CopyOnWriteArrayList<Registration> CLICKABLE=new CopyOnWriteArrayList<>();
    private GolemHarvestRegistry() {}
    public static Registration registerClickable(BlockState... matureStates) {
        var states=Set.copyOf(Arrays.asList(matureStates.clone()));
        if(states.isEmpty()||states.stream().anyMatch(BlockState::isAir))throw new IllegalArgumentException("Clickable crops require non-air mature states");
        var registration=new Registration(states);CLICKABLE.add(registration);return registration;
    }
    /** Explicit wildcard enrollment, corresponding to source metadata32767 across the modern state set. */
    public static Registration registerClickable(Block block) {
        return registerClickable(block.getStateDefinition().getPossibleStates().toArray(BlockState[]::new));
    }
    public static boolean isClickable(BlockState state) {return CLICKABLE.stream().anyMatch(r->r.states.contains(state));}
    /** Scope removes only this registration; duplicate adapters and repeated close do not unregister others. */
    public static final class Registration implements AutoCloseable {
        private final Set<BlockState> states;
        private Registration(Set<BlockState> states){this.states=states;}
        @Override public void close(){CLICKABLE.remove(this);}
    }
}
