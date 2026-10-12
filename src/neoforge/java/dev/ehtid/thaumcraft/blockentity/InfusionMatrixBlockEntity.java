package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.block.InfusionPillarBlock;
import dev.ehtid.thaumcraft.block.PedestalBlock;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionMatrixLayout;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionMatrixIdle;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionStability;
import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Foundation of the original BETA26 TileInfusionMatrix:
 * - the REAL world BlockPedestal/BlockPillar layout controls active status;
 * - invalid structures deactivate after the original 100-tick interval;
 * - original active/crafting/stability/recipeinst NBT keys survive saves;
 * - missing recipe, vis, instability and caster mechanics are NOT simulated.
 *
 * Activation is intentionally not exposed as an ordinary empty-hand click,
 * since BETA26 invokes this through IInteractWithCaster.
 */
public final class InfusionMatrixBlockEntity extends BlockEntity {
    private boolean active;
    private boolean crafting;
    private float stability;
    private int recipeInstability;
    private int ticks;
    private boolean surroundingsInitialized;
    private float stabilityReplenish;
    private float essentiaCostMult = 1.0F;
    private int cycleTime = 10;
    private int countDelay = 5;
    /** Retain old source recipe NBT until its subtype can be safely decoded. */
    private CompoundTag originalRecipeData = new CompoundTag();

    public InfusionMatrixBlockEntity(BlockPos pos, BlockState state) {
        super(ThaumcraftBlockEntities.INFUSION_MATRIX.get(), pos, state);
    }

    public boolean active() { return active; }
    public boolean crafting() { return crafting; }
    public float stability() { return stability; }
    public float stabilityReplenish() { return stabilityReplenish; }
    public float essentiaCostMult() { return essentiaCostMult; }
    public int cycleTime() { return cycleTime; }
    public int countDelay() { return countDelay; }


    public boolean validLocation() {
        if (level == null) return false;
        return LegacyInfusionMatrixLayout.validLocation(at -> {
            BlockPos where = worldPosition.offset(at.x(), at.y(), at.z());
            var block = level.getBlockState(where).getBlock();
            if (block instanceof PedestalBlock)
                return LegacyInfusionMatrixLayout.Part.PEDESTAL;
            if (block instanceof InfusionPillarBlock)
                return LegacyInfusionMatrixLayout.Part.PILLAR;
            return LegacyInfusionMatrixLayout.Part.OTHER;
        });
    }

    /** Surroundings scan preserves original [-8,8] x/z and [-7,3] y limits. */
    public List<BlockPos> nearbyPedestals() {
        if (level == null) return List.of();
        return LegacyInfusionMatrixLayout.scanPedestals(at ->
                level.getBlockState(worldPosition.offset(at.x(), at.y(), at.z()))
                        .getBlock() instanceof PedestalBlock
                        ? LegacyInfusionMatrixLayout.Part.PEDESTAL
                        : LegacyInfusionMatrixLayout.Part.OTHER)
                .stream().map(at -> worldPosition.offset(at.x(), at.y(), at.z()))
                .toList();
    }

    /**
     * BETA26 getSurroundings: count every nearby pedestal, match pairs
     * at (x,y,z) and (-x,y,-z), and apply corner pillar bonuses.
     * This covers the registered pedestal variants; other stabilisers,
     * skulls and matrix-speed/cost glyphs are still pending.
     */
    private void refreshWorldStability() {
        if (level == null || level.isClientSide || !validLocation()) return;
        var corners = new ArrayList<LegacyInfusionStability.Pillar>();
        for (var offset : LegacyInfusionMatrixLayout.requiredPillarOffsets()) {
            var block = level.getBlockState(worldPosition.offset(
                    offset.x(), offset.y(), offset.z())).getBlock();
            var type = ((InfusionPillarBlock) block).variant();
            corners.add(switch (type) {
                case ARCANE -> LegacyInfusionStability.Pillar.ARCANE;
                case ANCIENT -> LegacyInfusionStability.Pillar.ANCIENT;
                case ELDRITCH -> LegacyInfusionStability.Pillar.ELDRITCH;
            });
        }
        var mods = LegacyInfusionStability.cornerModifiers(corners);
        int ancients = 0;
        int eldritch = 0;
        Map<LegacyInfusionMatrixLayout.Offset, LegacyInfusionStability.Stabilizer>
                ring = new HashMap<>();
        for (var offset : LegacyInfusionMatrixLayout.scanPedestals(where ->
                level.getBlockState(worldPosition.offset(where.x(),where.y(),where.z()))
                        .getBlock() instanceof PedestalBlock
                        ? LegacyInfusionMatrixLayout.Part.PEDESTAL
                        : LegacyInfusionMatrixLayout.Part.OTHER)) {
            var worldAt = worldPosition.offset(offset.x(),offset.y(),offset.z());
            var block = level.getBlockState(worldAt).getBlock();
            if (!(block instanceof PedestalBlock pedestal)) continue;
            if (block == ThaumcraftBlocks.PEDESTAL_ANCIENT.get()) ancients++;
            if (block == ThaumcraftBlocks.PEDESTAL_ELDRITCH.get()) eldritch++;
            boolean occupied = level.getBlockEntity(worldAt) instanceof PedestalBlockEntity tile
                    && tile.isOccupied();
            ring.put(offset, new LegacyInfusionStability.Stabilizer(
                    BuiltInRegistries.BLOCK.getKey(block).toString(),
                    pedestal.getStabilizationAmount(), occupied,
                    pedestal.getSymmetryPenalty()));
        }
        mods = LegacyInfusionStability.withPedestals(mods,ancients,eldritch);
        var balance = LegacyInfusionStability.pedestalBalance(ring);
        cycleTime = mods.cycleTime();
        countDelay = mods.countDelay();
        essentiaCostMult = mods.essentiaCostMult();
        stabilityReplenish = mods.replenish() + balance.replenishment();
        surroundingsInitialized = true;
        changedAndSync();
    }

    /**
     * The future ORIGINAL caster gauntlet bridge should call this method,
     * never a generic GUI button/empty-hand click. Recipe start needs the
     * full original matching/material/aura transaction before enabling.
     */
    public boolean tryActivateFromCaster() {
        if (level == null || level.isClientSide || active || !validLocation()) return false;
        refreshWorldStability();
        active = true;
        changedAndSync();
        return true;
    }

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  InfusionMatrixBlockEntity tile) {
        tile.ticks++;
        // BETA26 initializes surrounding stabilizers once on world load.
        if (!tile.surroundingsInitialized && tile.validLocation())
            tile.refreshWorldStability();
        // Original idle stability replenishes every max(5,countDelay) ticks;
        // do not advance unfinished crafting/essentia consumption.
        float next = LegacyInfusionStability.idleRecharge(tile.stability, tile.ticks,
                tile.active, tile.crafting, tile.countDelay, tile.stabilityReplenish);
        if (Float.compare(next,tile.stability)!=0) {
            tile.stability = next;
            tile.changedAndSync();
        }
        if (!LegacyInfusionMatrixIdle.checkNow(tile.ticks, tile.crafting)) return;
        if (LegacyInfusionMatrixIdle.deactivate(tile.active, tile.validLocation())) {
            tile.active = false;
            tile.surroundingsInitialized = false;
            tile.changedAndSync();
        } else if (tile.active && !tile.crafting) {
            // Recheck neighboring pedestals periodically until block-event
            // notifications for all supported stabilizers are restored.
            tile.refreshWorldStability();
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putBoolean("active", active);
        tag.putBoolean("crafting", crafting);
        tag.putFloat("stability", stability);
        tag.putInt("recipeinst", recipeInstability);
        tag.putFloat("stabilityReplenish", stabilityReplenish);
        tag.putFloat("costMult", essentiaCostMult);
        tag.putInt("cycleTime", cycleTime);
        tag.putInt("countDelay", countDelay);
        if (!originalRecipeData.isEmpty())
            tag.put("OriginalInfusionRecipe", originalRecipeData.copy());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        active = tag.getBoolean("active");
        crafting = tag.getBoolean("crafting");
        stability = tag.getFloat("stability");
        recipeInstability = tag.getInt("recipeinst");
        stabilityReplenish = tag.getFloat("stabilityReplenish");
        essentiaCostMult = tag.contains("costMult") ? tag.getFloat("costMult") : 1.0F;
        cycleTime = tag.contains("cycleTime") ? tag.getInt("cycleTime") : 10;
        countDelay = tag.contains("countDelay") ? tag.getInt("countDelay") : 5;
        surroundingsInitialized = false;
        if (tag.contains("OriginalInfusionRecipe", Tag.TAG_COMPOUND)) {
            originalRecipeData = tag.getCompound("OriginalInfusionRecipe").copy();
        } else {
            // Preserve old BETA26 serialized recipe data rather than
            // overwriting it when this incomplete runtime saves a world.
            originalRecipeData = new CompoundTag();
            for (String key : List.of("recipein", "rotype", "recipeout",
                    "recipeinput", "recipetype", "recipexp", "recipexp",
                    "recipeplayer", "Aspects")) {
                if (tag.contains(key))
                    originalRecipeData.put(key, tag.get(key).copy());
            }
        }
    }
}
