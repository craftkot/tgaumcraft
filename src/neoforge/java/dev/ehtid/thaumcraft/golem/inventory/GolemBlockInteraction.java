package dev.ehtid.thaumcraft.golem.inventory;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import java.util.*;
import java.util.function.Supplier;

/** Isolate a native click's block capture; item ownership stays with the caller's actual inventory. */
public final class GolemBlockInteraction {
    public record Outcome(boolean attempted,InteractionResult result) {}
    private static final Outcome PAUSED=new Outcome(false,InteractionResult.PASS);
    private static final ThreadLocal<Boolean> ACTIVE=ThreadLocal.withInitial(()->false);
    private GolemBlockInteraction() {}

    /** CommonHooks has one mutable list per level; never consume another callback's capture. */
    public static boolean available(ServerLevel level) {
        return !ACTIVE.get()&&!level.captureBlockSnapshots&&!level.restoringBlockSnapshots&&level.capturedBlockSnapshots.isEmpty();
    }

    public static Outcome invoke(ServerLevel level,String action,Supplier<InteractionResult> callback,BlockPos... placementCells) {
        if(!available(level))return PAUSED;
        var fallback=new LinkedHashMap<BlockPos,BlockSnapshot>();
        for(var cell:placementCells) {
            // Snapshot only loaded, replaceable cells. Never load a neighbour just to prepare rollback.
            if(level.isOutsideBuildHeight(cell)||level.getChunkSource().getChunkNow(cell.getX()>>4,cell.getZ()>>4)==null)continue;
            var state=level.getBlockState(cell);
            // Container inventory and arbitrary direct block-use effects are not an inventory transaction.
            if(!state.hasBlockEntity()&&state.canBeReplaced())fallback.putIfAbsent(cell.immutable(),BlockSnapshot.create(level.dimension(),level,cell));
        }
        ACTIVE.set(true);
        try {
            var result=callback.get();
            if(!result.consumesAction()||level.captureBlockSnapshots||level.restoringBlockSnapshots||!level.capturedBlockSnapshots.isEmpty()) {
                restore(level,fallback.values(),action);
                return new Outcome(true,result.consumesAction()?InteractionResult.FAIL:result);
            }
            return new Outcome(true,result);
        } catch(RuntimeException ex) {
            // CommonHooks has no finally around Item.useOn; event exceptions can occur after its list was moved.
            restore(level,fallback.values(),action);
            LogUtils.getLogger().warn("Golem {} callback failed; captured placement blocks restored, actual inventory retained",action,ex);
            return new Outcome(true,InteractionResult.FAIL);
        } finally {ACTIVE.remove();}
    }

    private static void restore(ServerLevel level,Collection<BlockSnapshot> fallback,String action) {
        var captured=new ArrayList<>(level.capturedBlockSnapshots);
        level.captureBlockSnapshots=false;
        level.restoringBlockSnapshots=true;
        try {
            for(int i=captured.size()-1;i>=0;i--)restore(captured.get(i),action);
            for(var snapshot:fallback)if(!level.getBlockState(snapshot.getPos()).equals(snapshot.getState()))restore(snapshot,action);
        } finally {
            level.captureBlockSnapshots=false;level.restoringBlockSnapshots=false;level.capturedBlockSnapshots.clear();
        }
    }
    private static void restore(BlockSnapshot snapshot,String action) {
        try {snapshot.restore(snapshot.getFlags()|Block.UPDATE_CLIENTS);}
        catch(RuntimeException ex){LogUtils.getLogger().error("Golem {} could not restore placement snapshot at {}",action,snapshot.getPos(),ex);}
    }
}
