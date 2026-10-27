package dev.ehtid.thaumcraft.golem.levitator;

import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.port.core.golem.GolemLevitatorRules;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Source TileLevitator range probe, shared aura payment and per-entity directional force. */
public final class GolemLevitatorBlockEntity extends BlockEntity {
    private int rangeIndex = GolemLevitatorRules.DEFAULT_RANGE_INDEX;
    private int credits, actualRange, counter;
    public GolemLevitatorBlockEntity(BlockPos pos, BlockState state) { super(GolemModule.LEVITATOR_ENTITY.get(), pos, state); }
    public int range() { return GolemLevitatorRules.range(rangeIndex); }
    public int cost() { return GolemLevitatorRules.cost(rangeIndex); }
    public int credits() { return credits; }
    public int actualRange() { return actualRange; }
    public void cycleRange() { rangeIndex = (rangeIndex + 1) % 4; actualRange = 0; setChanged(); }

    public static void tick(ServerLevel level, BlockPos pos, BlockState state, GolemLevitatorBlockEntity device) {
        Direction facing = state.getValue(GolemLevitatorBlock.FACING);
        BlockPos probe = pos.relative(facing, GolemLevitatorRules.nextProbe(device.rangeIndex, device.counter));
        // A probe at an absent chunk must never manufacture a chunk ticket.
        boolean opaque = !level.hasChunkAt(probe) || level.getBlockState(probe).isSolidRender(level, probe);
        var scan = GolemLevitatorRules.scan(device.rangeIndex, device.actualRange, device.counter, opaque);
        device.actualRange = scan.actualRange(); device.counter = scan.counter();
        int before = device.credits;
        if (device.credits < 10) device.credits = GolemLevitatorRules.refill(device.credits,
                ThaumcraftAuraSavedData.get(level).drainVis(pos, 1F, false));
        // Source redstone disables force; the range scan and aura refill above still occur.
        if (device.actualRange > 0 && device.credits > 0 && !level.hasNeighborSignal(pos)) {
            AABB beam = new AABB(pos).expandTowards(facing.getStepX() * device.actualRange,
                    facing.getStepY() * device.actualRange, facing.getStepZ() * device.actualRange);
            for (Entity entity : level.getEntitiesOfClass(Entity.class, beam,
                    e -> GolemLevitatorRules.eligible(e instanceof ItemEntity, e.isPushable(), e instanceof Horse))) {
                var movement = entity.getDeltaMovement();
                var motion = GolemLevitatorRules.motion(movement.x, movement.y, movement.z,
                        GolemLevitatorRules.Direction.valueOf(facing.name()), entity.isShiftKeyDown(), entity.onGround());
                entity.setDeltaMovement(motion.x(), motion.y(), motion.z());
                entity.fallDistance = 0; entity.hurtMarked = true;
                device.credits = GolemLevitatorRules.afterEntity(device.credits, device.rangeIndex);
                if (device.credits <= 0) break;
            }
        }
        if (before != device.credits) device.setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByte("range", (byte) rangeIndex); tag.putInt("vis", credits);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int selected = tag.contains("range") ? tag.getByte("range") : GolemLevitatorRules.DEFAULT_RANGE_INDEX;
        rangeIndex = selected >= 0 && selected < 4 ? selected : GolemLevitatorRules.DEFAULT_RANGE_INDEX;
        credits = tag.getInt("vis"); actualRange = 0; counter = 0;
    }
}
