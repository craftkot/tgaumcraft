package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.block.InfusionPillarBlock;
import dev.ehtid.thaumcraft.block.PedestalBlock;
import dev.ehtid.thaumcraft.block.TallowCandleBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionMatrixLayout;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionMatrixIdle;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionStability;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionStartPlanner;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionEssentiaCycle;
import dev.ehtid.thaumcraft.port.core.AspectListData;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionRitualLedger;
import dev.ehtid.thaumcraft.port.core.LegacyCrystalFarmerIngredientProgress;
import dev.ehtid.thaumcraft.port.core.LegacyCrystalFarmerFinishCheckpoint;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionCrystalFarmerCatalog;
import dev.ehtid.thaumcraft.crafting.CrystalFarmerInfusionAdapter;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import dev.ehtid.thaumcraft.crafting.InfusionJarSourceBridge;
import dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData;
import net.minecraft.world.item.ItemStack;
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
 * - BASIC caster can now activate the actual structure and request only
 *   verified Crystal Farmer recipes; other caster foci are not yet ported.
 *
 * Activation is intentionally not exposed as an ordinary empty-hand click;
 * BETA26 invokes it from ItemCaster via IInteractWithCaster.
 */
public final class InfusionMatrixBlockEntity extends BlockEntity {
    /** Cached jar sources belong to THIS matrix, never shared across dimensions. */
    private final InfusionJarSourceBridge jarSources = new InfusionJarSourceBridge();
    private static final AspectRegistry SOURCE_ASPECTS = new AspectRegistry();
    /** Null unless a source-era, research-gated player recipe was verified. */
    private LegacyInfusionRitualLedger ritualLedger;
    /** Original cost multiplier frozen when the player starts this ritual. */
    private float ritualStartCostMultiplier = 1.0F;
    /** Source five-cycle per ingredient, persisted separately from paid essentia. */
    private LegacyCrystalFarmerIngredientProgress itemProgress;
    private LegacyCrystalFarmerFinishCheckpoint.State finishCheckpoint =
            LegacyCrystalFarmerFinishCheckpoint.initial();
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
            // Matrix ticks must never synchronously load a missing chunk.
            if (!level.hasChunkAt(where)) return LegacyInfusionMatrixLayout.Part.OTHER;
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
        return LegacyInfusionMatrixLayout.scanPedestals(at -> {
            BlockPos where = worldPosition.offset(at.x(), at.y(), at.z());
            if (!level.hasChunkAt(where)) return LegacyInfusionMatrixLayout.Part.OTHER;
            return level.getBlockState(where).getBlock() instanceof PedestalBlock
                    ? LegacyInfusionMatrixLayout.Part.PEDESTAL
                    : LegacyInfusionMatrixLayout.Part.OTHER;
        }).stream().map(at -> worldPosition.offset(at.x(), at.y(), at.z()))
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
        for (var offset : LegacyInfusionMatrixLayout.scanPedestals(where -> {
            BlockPos at = worldPosition.offset(where.x(),where.y(),where.z());
            if (!level.hasChunkAt(at)) return LegacyInfusionMatrixLayout.Part.OTHER;
            return level.getBlockState(at).getBlock() instanceof PedestalBlock
                    ? LegacyInfusionMatrixLayout.Part.PEDESTAL
                    : LegacyInfusionMatrixLayout.Part.OTHER;
        })) {
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
        // BETA26 getSurroundings scans X/Z -8..8, matrix Y-7..Y+3.
        // Original BETA26 also counts vanilla Blocks.SKULL as a stabilizer
        // (strength 0.1); in 1.21 skull types are separate registered blocks,
        // but source SKULL had one shared block id across all six metadata
        // variants. Treat all modern skull/head variants as the same source
        // stabilizer key, including wall skulls, regardless of rotation.
        // Existing balancer applies opposite symmetry and diminishing returns.
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            if (dx == 0 && dz == 0) continue;
            for (int dy = -7; dy <= 3; dy++) {
                var at = worldPosition.offset(dx, dy, dz);
                if (!level.hasChunkAt(at)) continue;
                var block = level.getBlockState(at).getBlock();
                if (block instanceof TallowCandleBlock candle) {
                    ring.put(new LegacyInfusionMatrixLayout.Offset(dx, dy, dz),
                            new LegacyInfusionStability.Stabilizer(
                                    BuiltInRegistries.BLOCK.getKey(block).toString(),
                                    candle.getStabilizationAmount(), false, 0.0F));
                } else if (block instanceof AbstractSkullBlock) {
                    ring.put(new LegacyInfusionMatrixLayout.Offset(dx, dy, dz),
                            new LegacyInfusionStability.Stabilizer(
                                    "minecraft:skull",
                                    LegacyInfusionStability.DEFAULT_UNMATCHED_STRENGTH,
                                    false, 0.0F));
                }
            }
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
     * Read-only BETA26 craftingStart preflight over REAL world pedestals.
     * No item/essentia is consumed and crafting stays false. Recipe MUST
     * come from a verified original InfusionRecipe catalog; no synthetic
     * placeholder recipe is registered or made player-accessible here.
     *
     * The central pedestal is at matrix Y-2; side components exclude empty
     * pedestals and follow BETA26 [-8,8] scan order. Snapshots ensure the
     * caller cannot alter actual pedestal stacks by modifying returned plan.
     */
    public LegacyInfusionStartPlanner.Prepared<ItemStack> prepareInfusionStartFromWorld(
            PlayerKnowledgeData knowledge,
            LegacyInfusionStartPlanner.Recipe<ItemStack> verifiedRecipe) {
        if (level == null || level.isClientSide || !active || crafting
                || !validLocation() || knowledge == null || verifiedRecipe == null)
            return null;
        if (!(level.getBlockEntity(worldPosition.below(2))
                instanceof PedestalBlockEntity central))
            return null;
        ItemStack center = central.displayedItem();
        if (center.isEmpty()) return null;
        List<ItemStack> occupied = new ArrayList<>();
        for (BlockPos where : nearbyPedestals()) {
            if (level.getBlockEntity(where) instanceof PedestalBlockEntity tile) {
                ItemStack item = tile.displayedItem();
                if (!item.isEmpty()) occupied.add(item.copy());
            }
        }
        return LegacyInfusionStartPlanner.prepare(true, true, center.copy(),
                occupied, knowledge, verifiedRecipe, essentiaCostMult);
    }

    /**
     * Called only by original-style BasicCasterItem, never a generic GUI
     * button/empty-hand click. On BETA26 the activation click costs no Vis.
     */
    public boolean tryActivateFromCaster() {
        if (level == null || level.isClientSide || active || !validLocation()) return false;
        refreshWorldStability();
        active = true;
        changedAndSync();
        return true;
    }


    /**
     * Only the source-era BasicCasterItem can trigger this research-gated
     * recipe start. It is never invoked by an empty hand or a generic GUI.
     *
     * The recipe has to match real pedestals, source aspect components and
     * player research at this very moment. Nothing is spent on start.
     */
    public boolean beginVerifiedCrystalFarmerFromFutureCaster(ServerPlayer player) {
        if (player == null || level != player.serverLevel() || !active
                || crafting || ritualLedger != null || !validLocation()) return false;
        PlayerKnowledgeData knowledge =
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        var preview = CrystalFarmerInfusionAdapter.prepare(this, knowledge);
        if (preview == null) return false;
        ritualLedger = LegacyInfusionRitualLedger.begin(
                preview.recipe().legacyId(), preview.matching().cost());
        ritualStartCostMultiplier = preview.matching().effectiveMultiplier();
        itemProgress = LegacyCrystalFarmerIngredientProgress.initial();
        finishCheckpoint = LegacyCrystalFarmerFinishCheckpoint.initial();
        recipeInstability = preview.matching().instability();
        crafting = true;
        changedAndSync(); // checkpoint BEFORE any real jar is debited
        ThaumcraftSmokeAudit.mark("INFUSION_CRYSTAL_RESEARCH_GATED_START");
        return true;
    }

    /** A moved/removed center or missing side item must pause debit, never grant output. */
    private boolean realInputsRemain() {
        if (level == null || ritualLedger == null || !validLocation()) return false;
        var spec = LegacyInfusionCrystalFarmerCatalog.all().stream()
                .filter(r -> r.legacyId().equals(ritualLedger.recipeId()))
                .findFirst().orElse(null);
        if (spec == null) return false;
        if (!(level.getBlockEntity(worldPosition.below(2)) instanceof PedestalBlockEntity center)
                || center.displayedItem().getCount() != 1
                || !center.displayedItem().is(ThaumcraftItems.CRYSTAL_ESSENCE.get())
                || !spec.aspectTag().equals(CrystalEssenceItem.legacyAspect(center.displayedItem())))
            return false;
        boolean seeds = false, salis = false;
        for (BlockPos pos : nearbyPedestals()) {
            if (!(level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal)) continue;
            ItemStack stack = pedestal.displayedItem();
            if (stack.getCount() != 1) continue;
            if (stack.is(Items.WHEAT_SEEDS)) seeds = true;
            if (stack.is(ThaumcraftItems.SALIS_MUNDUS.get())) salis = true;
        }
        // Once a real ingredient has been consumed, that same input must
        // NOT be required again (or the ritual would be stuck forever).
        // No progress state => legacy/corrupt crafting NBT is not eligible.
        if (itemProgress == null) return seeds && salis;
        return ((itemProgress.consumedMask()
                & LegacyCrystalFarmerIngredientProgress.SEEDS_BIT) != 0 || seeds)
                && ((itemProgress.consumedMask()
                & LegacyCrystalFarmerIngredientProgress.SALIS_BIT) != 0 || salis);
    }

    /**
     * Consume and checkpoint one REAL jar essentia unit. This version does
     * not accept a caller-provided cost snapshot (which could be replayed).
     * The outstanding costs come only from the persisted, verified ledger.
     *
     * This ends at the ingredient stage; output and pedestal consumption
     * remain gated until the original finish transaction is ported.
     */
    public LegacyInfusionEssentiaCycle.Result drainVerifiedInfusionEssentia() {
        if (!(level instanceof ServerLevel server) || !active || !crafting
                || ritualLedger == null || !realInputsRemain()
                || ritualLedger.readyForIngredients()) return null;
        AspectListData remaining = new AspectListData();
        for (var entry : ritualLedger.remaining().entrySet()) {
            var aspect = SOURCE_ASPECTS.get(entry.getKey());
            if (aspect == null) return null;
            remaining.add(aspect, entry.getValue());
        }
        var result = LegacyInfusionEssentiaCycle.cycle(remaining, stability,
                countDelay,
                (aspect, range, delay) -> jarSources.drainOne(
                        server, worldPosition, aspect.tag(), range, delay));
        if (result.action() == LegacyInfusionEssentiaCycle.Action.DRAINED_ONE) {
            // Only a successful REAL jar debit may advance the checkpoint.
            ritualLedger = ritualLedger.afterWorldDebit(result.consumed().tag());
            if (ritualLedger.readyForIngredients())
                ThaumcraftSmokeAudit.mark("INFUSION_CRYSTAL_ESSENTIA_FULLY_PAID");
        }
        if (result.action() == LegacyInfusionEssentiaCycle.Action.FAILED_ALL)
            surroundingsInitialized = false;
        if (result.action() != LegacyInfusionEssentiaCycle.Action.READY_FOR_INGREDIENTS) {
            stability = result.stability();
            changedAndSync();
        }
        return result;
    }


    /**
     * Real original five-cycle ingestion of the two verified BETA26
     * Crystal Farmer ingredients. No recipe output is granted yet.
     *
     * A countdown only progresses while a matching actual ItemStack still
     * sits on a loaded side pedestal. On the CONSUME cycle the pedestal is
     * modified FIRST and the restart-safe mask is committed immediately
     * afterwards. A crash between these steps can stall the ritual, but
     * cannot grant an output or duplicate the consumed input.
     */
    private void advanceVerifiedCrystalFarmerIngredient() {
        if (!(level instanceof ServerLevel) || !active || !crafting
                || ritualLedger == null || !ritualLedger.readyForIngredients()
                || itemProgress == null || itemProgress.readyToFinish()
                || !realInputsRemain()) return;
        int nextBit = itemProgress.nextIngredientBit();
        PedestalBlockEntity chosen = null;
        ItemStack captured = ItemStack.EMPTY;
        for (BlockPos pos : nearbyPedestals()) {
            if (!(level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal))
                continue;
            ItemStack presented = pedestal.displayedItem();
            if (presented.getCount() != 1) continue;
            boolean matched = nextBit == LegacyCrystalFarmerIngredientProgress.SEEDS_BIT
                    ? presented.is(Items.WHEAT_SEEDS)
                    : presented.is(ThaumcraftItems.SALIS_MUNDUS.get());
            if (matched) {
                chosen = pedestal;
                captured = presented.copy();
                break;
            }
        }
        if (chosen == null) return;
        switch (itemProgress.nextAction()) {
            case SOURCE_EFFECT, COUNTDOWN -> {
                itemProgress = itemProgress.afterValidatedCountdown();
                changedAndSync();
            }
            case CONSUME -> {
                // Both original recipe inputs have no crafting remainder.
                // Never consume without checking the original data components.
                if (chosen.replaceInfusionIngredientIfUnchanged(
                        captured, ItemStack.EMPTY)) {
                    itemProgress = itemProgress.afterActualPedestalRemoval(nextBit);
                    changedAndSync();
                    ThaumcraftSmokeAudit.mark(nextBit == LegacyCrystalFarmerIngredientProgress.SEEDS_BIT
                            ? "INFUSION_CRYSTAL_SEEDS_REMOVED"
                            : "INFUSION_CRYSTAL_SALIS_REMOVED");
                }
            }
            case READY -> { /* Remaining finish/output work is deliberately gated. */ }
        }
    }


    /**
     * BETA26 finish for the ONLY catalogued original infusion outputs:
     * seven aspect-specific CrystalCluster BlockItems, one each. Source
     * recipe matching, real jar payment and real pedestal removals must all
     * have completed before this method can reserve an output.
     *
     * The matrix writes a one-use reservation BEFORE mutating the center.
     * This forbids duplicate output on repeated ticks and on NBT reload.
     * A crash between separate block-entity saves can lose the output;
     * RESERVED deliberately never retries after reload (fail closed).
     */
    private void finishVerifiedCrystalFarmer() {
        if (!(level instanceof ServerLevel) || !active || !crafting
                || ritualLedger == null || !ritualLedger.readyForIngredients()
                || itemProgress == null || !itemProgress.readyToFinish()
                || !finishCheckpoint.canReserve() || !realInputsRemain())
            return;
        var recipe = LegacyInfusionCrystalFarmerCatalog.all().stream()
                .filter(r -> r.legacyId().equals(ritualLedger.recipeId()))
                .findFirst().orElse(null);
        if (recipe == null) return;
        // Only source-era crystal BlockItems, never an output parsed from
        // user-controlled NBT or a new invented recipe.
        net.minecraft.world.item.Item outputItem = switch (recipe.aspectTag()) {
            case "aer" -> ThaumcraftItems.CRYSTAL_AER.get();
            case "ignis" -> ThaumcraftItems.CRYSTAL_IGNIS.get();
            case "aqua" -> ThaumcraftItems.CRYSTAL_AQUA.get();
            case "terra" -> ThaumcraftItems.CRYSTAL_TERRA.get();
            case "ordo" -> ThaumcraftItems.CRYSTAL_ORDO.get();
            case "perditio" -> ThaumcraftItems.CRYSTAL_PERDITIO.get();
            case "vitium" -> ThaumcraftItems.CRYSTAL_VITIUM.get();
            default -> null;
        };
        if (outputItem == null) return;
        if (!(level.getBlockEntity(worldPosition.below(2)) instanceof PedestalBlockEntity center))
            return;
        ItemStack expected = center.displayedItem().copy();
        if (expected.getCount() != 1
                || !expected.is(ThaumcraftItems.CRYSTAL_ESSENCE.get())
                || !recipe.aspectTag().equals(CrystalEssenceItem.legacyAspect(expected)))
            return;

        // Reservation is checkpointed before the real central inventory
        // write. Reloaded RESERVED may not publish anything automatically.
        finishCheckpoint = finishCheckpoint.reserve(true,true,true);
        changedAndSync();
        if (!center.replaceInfusionIngredientIfUnchanged(
                expected, new ItemStack(outputItem))) return;

        finishCheckpoint = finishCheckpoint.finishAfterActualCenterReplacement(true);
        crafting = false;
        recipeInstability = 0;
        ritualLedger = null;
        itemProgress = null;
        finishCheckpoint = LegacyCrystalFarmerFinishCheckpoint.initial();
        changedAndSync();
        ThaumcraftSmokeAudit.mark("INFUSION_CRYSTAL_REAL_OUTPUT_COMMITTED");
    }

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  InfusionMatrixBlockEntity tile) {
        tile.ticks++;
        // Source stage: one REAL unit at most per original matrix craft cycle.
        // A legacy 'crafting:1' tag without our verified checkpoint never
        // drains resources. After essence reaches zero, wait for the future
        // original pedestal/finish implementation instead of granting items.
        if (tile.crafting && tile.ritualLedger != null
                && !tile.ritualLedger.readyForIngredients()
                && tile.ticks % Math.max(1, tile.cycleTime) == 0)
            tile.drainVerifiedInfusionEssentia();
        if (tile.crafting && tile.ritualLedger != null
                && tile.ritualLedger.readyForIngredients()
                && tile.ticks % Math.max(1, tile.cycleTime) == 0)
            tile.advanceVerifiedCrystalFarmerIngredient();
        if (tile.crafting && tile.ritualLedger != null
                && tile.ritualLedger.readyForIngredients()
                && tile.itemProgress != null && tile.itemProgress.readyToFinish()
                && tile.ticks % Math.max(1, tile.cycleTime) == 0)
            tile.finishVerifiedCrystalFarmer();
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
        if (crafting && ritualLedger != null) {
            CompoundTag pending = new CompoundTag();
            pending.putString("Recipe", ritualLedger.recipeId());
            pending.putInt("Paid", ritualLedger.paid());
            pending.putFloat("RecipeCostMult", ritualStartCostMultiplier);
            CompoundTag initial = new CompoundTag();
            CompoundTag remaining = new CompoundTag();
            ritualLedger.requested().forEach(initial::putInt);
            ritualLedger.remaining().forEach(remaining::putInt);
            pending.put("Initial", initial);
            pending.put("Remaining", remaining);
            pending.putString("FinishPhase", finishCheckpoint.phase().name());
            if (itemProgress != null) {
                pending.putInt("ConsumedItems", itemProgress.consumedMask());
                pending.putInt("ItemCountdown", itemProgress.countdown());
            }
            tag.put("PortInfusionLedger", pending);
        }
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
        ritualLedger = null;
        itemProgress = null;
        ritualStartCostMultiplier = 1.0F;
        finishCheckpoint = LegacyCrystalFarmerFinishCheckpoint.initial();
        // An old/corrupt 'crafting:1' without a verified ledger cannot craft.
        if (crafting && !tag.contains("PortInfusionLedger", Tag.TAG_COMPOUND))
            crafting = false;
        if (crafting && tag.contains("PortInfusionLedger", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag pending = tag.getCompound("PortInfusionLedger");
                if (!pending.contains("Initial", Tag.TAG_COMPOUND)
                        || !pending.contains("Remaining", Tag.TAG_COMPOUND)
                        || !pending.contains("Recipe", Tag.TAG_STRING)
                        || !pending.contains("Paid", Tag.TAG_INT)
                        || !pending.contains("ConsumedItems", Tag.TAG_INT)
                        || !pending.contains("ItemCountdown", Tag.TAG_INT)
                        || !pending.contains("FinishPhase", Tag.TAG_STRING))
                    throw new IllegalArgumentException("Incomplete original infusion checkpoint");
                // Legacy checkpoints did not contain a frozen multiplier; use
                // their saved costMult only for migration, and still require
                // exact equality to the source recipe's calculated costs.
                if (pending.contains("RecipeCostMult")
                        && !pending.contains("RecipeCostMult", Tag.TAG_FLOAT))
                    throw new IllegalArgumentException("Invalid saved recipe multiplier");
                ritualStartCostMultiplier = pending.contains("RecipeCostMult")
                        ? pending.getFloat("RecipeCostMult") : essentiaCostMult;
                CompoundTag original = pending.getCompound("Initial");
                CompoundTag remaining = pending.getCompound("Remaining");
                var initialCosts = new java.util.LinkedHashMap<String,Integer>();
                var leftCosts = new java.util.LinkedHashMap<String,Integer>();
                for (String name : original.getAllKeys()) {
                    if (!original.contains(name, Tag.TAG_INT)) throw new IllegalArgumentException("Invalid cost");
                    initialCosts.put(name, original.getInt(name));
                }
                for (String name : remaining.getAllKeys()) {
                    if (!remaining.contains(name, Tag.TAG_INT)) throw new IllegalArgumentException("Invalid remaining");
                    leftCosts.put(name, remaining.getInt(name));
                }
                String id = pending.getString("Recipe");
                ritualLedger = LegacyInfusionRitualLedger.restoreVerifiedCrystalFarmer(
                        id, initialCosts, leftCosts, pending.getInt("Paid"),
                        ritualStartCostMultiplier);
                finishCheckpoint = LegacyCrystalFarmerFinishCheckpoint.restore(
                        pending.getString("FinishPhase"));
                if (finishCheckpoint.finished())
                    throw new IllegalArgumentException("Completed ritual is not crafting");

                itemProgress = new LegacyCrystalFarmerIngredientProgress(
                        pending.getInt("ConsumedItems"),
                        pending.getInt("ItemCountdown"));
                if (itemProgress.consumedMask() != 0
                        && !ritualLedger.readyForIngredients())
                    throw new IllegalArgumentException("Items consumed before full essence payment");
            } catch (IllegalArgumentException malformed) {
                // Corrupt/newer NBT must never make a free-running ritual.
                crafting = false;
                ritualLedger = null;
                itemProgress = null;
                finishCheckpoint = LegacyCrystalFarmerFinishCheckpoint.initial();
            }
        }
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
