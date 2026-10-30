package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.block.InfusionPillarBlock;
import dev.ehtid.thaumcraft.block.PedestalBlock;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionMatrixLayout;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionMatrixIdle;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionStability;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionStartPlanner;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionEssentiaCycle;
import dev.ehtid.thaumcraft.port.core.AspectListData;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionRitualLedger;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionIngredientQueue;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionFinishCheckpoint;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionCrystalFarmerCatalog;
import dev.ehtid.thaumcraft.crafting.InfusionRecipeAdapter;
import dev.ehtid.thaumcraft.crafting.InfusionStabilizerAdapter;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionInstability;
import dev.ehtid.thaumcraft.infusion.InfusionInstabilityEffects;
import dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import net.minecraft.server.level.ServerPlayer;
import dev.ehtid.thaumcraft.crafting.InfusionJarSourceBridge;
import dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData;
import net.minecraft.world.item.ItemStack;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

/**
 * Foundation of the original BETA26 TileInfusionMatrix:
 * - the REAL world BlockPedestal/BlockPillar layout controls active status;
 * - invalid structures deactivate after the original 100-tick interval;
 * - original active/crafting/stability/recipeinst NBT keys survive saves;
 * - BASIC caster can now activate the actual structure and request only
 *   registered research-gated source recipes; other caster foci are not yet ported.
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
    /** Unique physical payment identity, saved with the matrix ledger. */
    private UUID activeInfusionRitual;
    /** Frozen full recipe identity; an upgraded same-ID recipe cannot inherit payment. */
    private String ritualRecipeFingerprint;
    /** Original cost multiplier frozen when the player starts this ritual. */
    private float ritualStartCostMultiplier = 1.0F;
    /** Source five-cycle per ingredient, persisted separately from paid essentia. */
    private LegacyInfusionIngredientQueue itemProgress;
    /** Physical payment positions; unpaid entries may follow a matching moved ingredient. */
    private List<BlockPos> sidePedestals = List.of();
    /** Original actual center ItemStack, including every data component. */
    private ItemStack originalCentralInput = ItemStack.EMPTY;
    /** Source output is evaluated once; saved random enchantment results never reroll on reload. */
    private ItemStack plannedOutput = ItemStack.EMPTY;
    /** Original side stacks in source recipe order, including every data component. */
    private List<ItemStack> originalSideInputs = List.of();
    private LegacyInfusionFinishCheckpoint.State finishCheckpoint =
            LegacyInfusionFinishCheckpoint.initial();
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

    /** Source scan includes provider blocks, skulls, candles, inlay and corner glyphs. */
    private void refreshWorldStability() {
        if (level == null || level.isClientSide || !validLocation()) return;
        var surroundings = InfusionStabilizerAdapter.scan(level, worldPosition);
        var mods = surroundings.modifiers();
        cycleTime = mods.cycleTime();
        countDelay = mods.countDelay();
        essentiaCostMult = mods.essentiaCostMult();
        stabilityReplenish = surroundings.replenishment();
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
    /** Compatibility entry point for the existing Basic Caster. */
    public boolean beginVerifiedCrystalFarmerFromFutureCaster(ServerPlayer player) {
        return beginVerifiedInfusionFromCaster(player);
    }

    public boolean beginVerifiedInfusionFromCaster(ServerPlayer player) {
        if (player == null || level != player.serverLevel() || !active
                || crafting || ritualLedger != null || !validLocation()) return false;
        refreshWorldStability();
        var knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        var preview = InfusionRecipeAdapter.prepare(this, knowledge);
        if (preview == null) return false;
        List<BlockPos> occupied = new ArrayList<>();
        for (BlockPos pos : nearbyPedestals())
            if (level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal && pedestal.isOccupied())
                occupied.add(pos.immutable());
        int[] assignment = preview.matching().ingredientIndices();
        int size = preview.recipe().components().size();
        if (occupied.size() != size || assignment.length != size) return false;
        var positions = new ArrayList<BlockPos>(java.util.Collections.nCopies(size,null));
        var inputs = new ArrayList<ItemStack>(java.util.Collections.nCopies(size,ItemStack.EMPTY));
        for (int i = 0; i < size; i++) {
            int index = assignment[i];
            if (index < 0 || index >= size || positions.get(index) != null
                    || !(level.getBlockEntity(occupied.get(i)) instanceof PedestalBlockEntity pedestal)) return false;
            ItemStack input = pedestal.displayedItem().copy();
            if (!InfusionRecipeAdapter.matches(preview.recipe().components().get(index),input)) return false;
            positions.set(index,occupied.get(i)); inputs.set(index,input);
        }
        if (!(level.getBlockEntity(worldPosition.below(2)) instanceof PedestalBlockEntity center)) return false;
        ItemStack centerInput = center.displayedItem().copy();
        if (!InfusionRecipeAdapter.matches(preview.recipe().center(),centerInput)) return false;
        ItemStack result = InfusionRecipeAdapter.output(preview.recipe(), centerInput, level.registryAccess(), level.random::nextInt);
        if (result.isEmpty()) return false;
        plannedOutput = result.copy();
        sidePedestals = List.copyOf(positions); originalSideInputs = List.copyOf(inputs);
        originalCentralInput = centerInput;
        ritualLedger = LegacyInfusionRitualLedger.begin(preview.recipe().legacyId(),preview.matching().cost());
        activeInfusionRitual = UUID.randomUUID();
        ritualRecipeFingerprint = preview.recipe().checkpointFingerprint();
        ritualStartCostMultiplier = preview.matching().effectiveMultiplier();
        itemProgress = LegacyInfusionIngredientQueue.initial(size);
        finishCheckpoint = LegacyInfusionFinishCheckpoint.initial();
        recipeInstability = preview.matching().instability();
        crafting = true;
        changedAndSync();
        markRitual("RESEARCH_GATED_START");
        return true;
    }

    private void markRitual(String event) {
        boolean crystal = ritualLedger != null && LegacyInfusionCrystalFarmerCatalog.all().stream()
                .anyMatch(r -> r.legacyId().equals(ritualLedger.recipeId()));
        ThaumcraftSmokeAudit.mark((crystal ? "INFUSION_CRYSTAL_" : "INFUSION_RECIPE_") + event);
    }

    private boolean trackedPedestalsValid() {
        if (sidePedestals.isEmpty() || sidePedestals.size() != originalSideInputs.size()
                || new java.util.HashSet<>(sidePedestals).size() != sidePedestals.size()) return false;
        return sidePedestals.stream().allMatch(this::isTrackedSidePosition);
    }

    private boolean isTrackedSidePosition(BlockPos where) {
        return where != null && LegacyInfusionMatrixLayout.withinScanVolume(
                new LegacyInfusionMatrixLayout.Offset(where.getX() - worldPosition.getX(),
                        where.getY() - worldPosition.getY(),where.getZ() - worldPosition.getZ()));
    }

    /** Capture complete physical proof first; never partially acknowledge a damaged set of receipts. */
    private LegacyInfusionIngredientQueue verifiedSideProgress() {
        if (level == null || itemProgress == null || activeInfusionRitual == null || !trackedPedestalsValid()) return null;
        boolean[] receipts = new boolean[sidePedestals.size()];
        boolean[] present = new boolean[sidePedestals.size()];
        for (int i = 0; i < sidePedestals.size(); i++) {
            var pos = sidePedestals.get(i);
            if (!level.hasChunkAt(pos)) return null;
            if (!(level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal)) {
                if ((itemProgress.consumedMask() & (1 << i)) != 0) return null;
                // Source searches other loaded pedestals for an unpaid ingredient after its old tile is removed.
                continue;
            }
            ItemStack item = pedestal.displayedItem();
            receipts[i] = pedestal.hasInfusionRemovalReceipt(activeInfusionRitual,1 << i,
                    InfusionRecipeAdapter.remainder(originalSideInputs.get(i)));
            present[i] = item.getCount() == 1 && ItemStack.isSameItemSameComponents(item,originalSideInputs.get(i));
        }
        try { return itemProgress.recoverSavedReceipts(receipts,present); }
        catch (IllegalArgumentException | IllegalStateException mismatch) { return null; }
    }

    private boolean originalCenterPresent() {
        if (level == null || ritualLedger == null || !validLocation()) return false;
        var recipe = InfusionRecipeAdapter.effectiveRecipe(
                InfusionRecipeAdapter.recipe(ritualLedger.recipeId()), originalCentralInput);
        return recipe != null && level.getBlockEntity(worldPosition.below(2)) instanceof PedestalBlockEntity center
                && InfusionRecipeAdapter.matches(recipe.center(),center.displayedItem())
                && ItemStack.isSameItemSameComponents(originalCentralInput,center.displayedItem());
    }

    private void recoverSavedSideItemDebit() {
        if (!(level instanceof ServerLevel) || !active || !crafting || !finishCheckpoint.canReserve()
                || ritualLedger == null || !ritualLedger.readyForIngredients() || !originalCenterPresent()) return;
        var recovered = verifiedSideProgress();
        if (recovered != null && !recovered.equals(itemProgress)) {
            itemProgress = recovered;
            changedAndSync();
            markRitual("SIDE_DEBIT_RECOVERED");
        }
    }

    private boolean realInputsRemain() {
        return originalCenterPresent() && itemProgress != null && itemProgress.equals(verifiedSideProgress());
    }

    /**
     * Consume and checkpoint one REAL jar essentia unit. This version does
     * not accept a caller-provided cost snapshot (which could be replayed).
     * The outstanding costs come only from the persisted, verified ledger.
     *
     * Every validated debit updates the persisted verified price ledger;
     * later pedestal consumption and the one-time result use separate
     * physical receipts and a reservation checkpoint.
     */
    public LegacyInfusionEssentiaCycle.Result drainVerifiedInfusionEssentia() {
        if (!(level instanceof ServerLevel server) || !active || !crafting
                || !finishCheckpoint.canReserve()
                || ritualLedger == null || !originalCenterPresent()
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
                markRitual("ESSENTIA_FULLY_PAID");
        }
        if (result.action() == LegacyInfusionEssentiaCycle.Action.FAILED_ALL)
            surroundingsInitialized = false;
        if (result.action() != LegacyInfusionEssentiaCycle.Action.READY_FOR_INGREDIENTS) {
            stability = result.stability();
            changedAndSync();
        }
        return result;
    }


    /** Source searches each remaining ingredient, skipping missing inputs while retaining one countdown. */
    private void advanceVerifiedInfusionIngredient() {
        if (!(level instanceof ServerLevel server) || !active || !crafting || !finishCheckpoint.canReserve()
                || ritualLedger == null || !ritualLedger.readyForIngredients() || itemProgress == null
                || itemProgress.readyToFinish() || !realInputsRemain()) return;
        var recipe = InfusionRecipeAdapter.effectiveRecipe(
                InfusionRecipeAdapter.recipe(ritualLedger.recipeId()), originalCentralInput);
        if (recipe == null) return;
        int remainingIndex = 0;
        for (int index = 0; index < itemProgress.ingredientCount(); index++) {
            int bit = 1 << index;
            if ((itemProgress.consumedMask() & bit) != 0) continue;
            BlockPos selected = null;
            for (BlockPos pos : nearbyPedestals()) {
                int tracked = sidePedestals.indexOf(pos);
                // A prior physical debit's receipt must remain available for save recovery.
                if (tracked >= 0 && (itemProgress.consumedMask() & (1 << tracked)) != 0) continue;
                if (level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal
                        && InfusionRecipeAdapter.matches(recipe.components().get(index), pedestal.displayedItem())) {
                    selected = pos; break;
                }
            }
            if (selected == null) {
                if (server.random.nextInt(1 + remainingIndex) == 0 && !ritualLedger.remaining().isEmpty()) {
                    var aspects = new ArrayList<>(ritualLedger.remaining().keySet());
                    ritualLedger = ritualLedger.withPenalty(aspects.get(server.random.nextInt(aspects.size())));
                    stability -= .25F;
                }
                changedAndSync();
                remainingIndex++;
                continue;
            }
            var pedestal = (PedestalBlockEntity) level.getBlockEntity(selected);
            if (!selected.equals(sidePedestals.get(index))) {
                var positions = new ArrayList<>(sidePedestals);
                int other = positions.indexOf(selected);
                if (other >= 0) positions.set(other, positions.get(index));
                positions.set(index, selected.immutable());
                sidePedestals = List.copyOf(positions);
            }
            var inputs = new ArrayList<>(originalSideInputs);
            inputs.set(index, pedestal.displayedItem().copy());
            originalSideInputs = List.copyOf(inputs);
            itemProgress = itemProgress.select(index);
            switch (itemProgress.nextAction()) {
                case SOURCE_EFFECT, COUNTDOWN -> {
                    itemProgress = itemProgress.afterValidatedCountdown(); changedAndSync();
                }
                case CONSUME -> {
                    if (pedestal.consumeInfusionIngredientWithReceipt(originalSideInputs.get(index), activeInfusionRitual, bit)) {
                        itemProgress = itemProgress.afterActualPedestalRemoval(bit); changedAndSync();
                        markRitual("SIDE_ITEM_REMOVED");
                        if (LegacyInfusionCrystalFarmerCatalog.all().stream().anyMatch(r -> r.legacyId().equals(ritualLedger.recipeId())))
                            markRitual(index == 0 ? "SEEDS_REMOVED" : "SALIS_REMOVED");
                    }
                }
                case READY -> { }
            }
            return; // Source handles at most one available side item in this cycle.
        }
    }

    private void clearCompletedInfusion() {
        crafting = false; recipeInstability = 0; ritualLedger = null; activeInfusionRitual = null;
        ritualRecipeFingerprint = null;
        itemProgress = null; sidePedestals = List.of(); originalSideInputs = List.of();
        originalCentralInput = ItemStack.EMPTY; plannedOutput = ItemStack.EMPTY; ritualStartCostMultiplier = 1.0F;
        finishCheckpoint = LegacyInfusionFinishCheckpoint.initial(); changedAndSync();
    }

    /** A reservation never authorizes replay. Only the original physical publication can be acknowledged. */
    private void acknowledgeRecoveredInfusionOutput() {
        if (!(level instanceof ServerLevel) || !active || !crafting || ritualLedger == null
                || !ritualLedger.readyForIngredients() || itemProgress == null || !itemProgress.readyToFinish()
                || finishCheckpoint.phase() != LegacyInfusionFinishCheckpoint.Phase.STALLED
                || !validLocation() || !itemProgress.equals(verifiedSideProgress())
                || !(level.getBlockEntity(worldPosition.below(2)) instanceof PedestalBlockEntity center)) return;
        ItemStack output = plannedOutput.copy();
        if (output.isEmpty() || !center.hasInfusionOutputReceipt(activeInfusionRitual,output)) return;
        finishCheckpoint = finishCheckpoint.acknowledgeAlreadyPublished(true,true,true);
        if (!finishCheckpoint.finished()) return;
        markRitual("SAVED_OUTPUT_ACKNOWLEDGED"); clearCompletedInfusion();
    }

    private void finishVerifiedInfusion() {
        if (!(level instanceof ServerLevel) || !active || !crafting || ritualLedger == null
                || !ritualLedger.readyForIngredients() || itemProgress == null || !itemProgress.readyToFinish()
                || !finishCheckpoint.canReserve() || !realInputsRemain()) return;
        ItemStack output = plannedOutput.copy();
        if (output.isEmpty() || !(level.getBlockEntity(worldPosition.below(2)) instanceof PedestalBlockEntity center)) return;
        finishCheckpoint = finishCheckpoint.reserve(true,true,true); changedAndSync();
        if (!center.publishInfusionResultWithReceipt(originalCentralInput,output,activeInfusionRitual)) return;
        finishCheckpoint = finishCheckpoint.finishAfterActualCenterReplacement(true);
        markRitual("REAL_OUTPUT_COMMITTED"); clearCompletedInfusion();
    }

    /** Source ordering: stability/event, then exactly one resource or ingredient stage. */
    private void craftCycle(ServerLevel server) {
        if (!active || !validLocation()) return;
        var random = new LegacyInfusionInstability.RandomSource() {
            public int nextInt(int bound) { return server.random.nextInt(bound); }
            public float nextFloat() { return server.random.nextFloat(); }
            public boolean nextBoolean() { return server.random.nextBoolean(); }
        };
        var cycle = LegacyInfusionInstability.beginCycle(stability, recipeInstability,
                stabilityReplenish, LegacyInfusionStability.CAP, originalCenterPresent(), random);
        stability = cycle.stability();
        if (cycle.event() != LegacyInfusionInstability.Event.NONE) {
            InfusionInstabilityEffects.perform(server, worldPosition, cycle.event(), nearbyPedestals(), random);
            stability = LegacyInfusionInstability.recoverAfterEvent(stability, random);
        }
        if (cycle.abortRecipe()) {
            // Original fail branch consumes no replacement item and never refunds snapshots.
            clearCompletedInfusion();
            return;
        }
        changedAndSync();
        if (!cycle.continueRecipe()) return;
        countDelay = Math.max(1, cycleTime / 2);
        if (!ritualLedger.readyForIngredients()) {
            drainVerifiedInfusionEssentia();
            return;
        }
        if (itemProgress != null && !itemProgress.readyToFinish()) {
            advanceVerifiedInfusionIngredient();
            return;
        }
        finishVerifiedInfusion();
    }

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  InfusionMatrixBlockEntity tile) {
        tile.ticks++;
        if (!tile.surroundingsInitialized && tile.validLocation()) tile.refreshWorldStability();
        if (LegacyInfusionMatrixIdle.checkNow(tile.ticks, tile.crafting) && !tile.validLocation()) {
            tile.active = false;
            tile.changedAndSync();
            return;
        }
        if (tile.crafting && tile.itemProgress != null
                && tile.ritualLedger != null
                && tile.ritualLedger.readyForIngredients())
            tile.recoverSavedSideItemDebit();
        // Restored RESERVED is STALLED. Acknowledge ONLY a pre-existing,
        // fully paid output; never call the original finish a second time.
        if (tile.crafting && tile.finishCheckpoint.phase()
                == LegacyInfusionFinishCheckpoint.Phase.STALLED)
            tile.acknowledgeRecoveredInfusionOutput();
        if (tile.crafting && tile.ritualLedger != null && tile.finishCheckpoint.canReserve()
                && tile.ticks % Math.max(1, tile.countDelay) == 0)
            tile.craftCycle(level);
        // Original idle stability replenishes every max(5,countDelay) ticks;
        // do not advance unfinished crafting/essentia consumption.
        float next = LegacyInfusionStability.idleRecharge(tile.stability, tile.ticks,
                tile.active, tile.crafting, tile.countDelay, tile.stabilityReplenish);
        if (Float.compare(next,tile.stability)!=0) {
            tile.stability = next;
            tile.changedAndSync();
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
            if (ritualRecipeFingerprint != null)
                pending.putString("RecipeFingerprint",ritualRecipeFingerprint);
            if (activeInfusionRitual != null)
                pending.putUUID("RitualId", activeInfusionRitual);
            pending.putInt("Paid", ritualLedger.paid());
            pending.putFloat("RecipeCostMult", ritualStartCostMultiplier);
            CompoundTag initial = new CompoundTag();
            CompoundTag remaining = new CompoundTag();
            ritualLedger.requested().forEach(initial::putInt);
            ritualLedger.remaining().forEach(remaining::putInt);
            pending.put("Initial", initial);
            pending.put("Remaining", remaining);
            CompoundTag surcharges = new CompoundTag();
            ritualLedger.surchargeCosts().forEach(surcharges::putInt);
            pending.put("Surcharges", surcharges);
            pending.putString("FinishPhase", finishCheckpoint.phase().name());
            if (!originalCentralInput.isEmpty()) {
                var slot = NonNullList.withSize(1, ItemStack.EMPTY);
                slot.set(0, originalCentralInput.copy());
                CompoundTag savedInput = new CompoundTag();
                ContainerHelper.saveAllItems(savedInput, slot, lookup);
                pending.put("OriginalCenterInput", savedInput);
            }
            pending.putInt("Format",3);
            var outputSlot = NonNullList.withSize(1, ItemStack.EMPTY);
            outputSlot.set(0, plannedOutput.copy());
            CompoundTag savedOutput = new CompoundTag();
            ContainerHelper.saveAllItems(savedOutput, outputSlot, lookup);
            pending.put("PlannedOutput", savedOutput);
            var sideSlots = NonNullList.withSize(originalSideInputs.size(),ItemStack.EMPTY);
            for (int i = 0; i < originalSideInputs.size(); i++) sideSlots.set(i,originalSideInputs.get(i).copy());
            CompoundTag savedSides = new CompoundTag();
            ContainerHelper.saveAllItems(savedSides,sideSlots,lookup);
            pending.put("OriginalSideInputs",savedSides);
            pending.putLongArray("SidePedestals",sidePedestals.stream().mapToLong(BlockPos::asLong).toArray());
            if (itemProgress != null) {
                pending.putInt("ConsumedItems", itemProgress.consumedMask());
                pending.putInt("ItemCountdown", itemProgress.countdown());
                pending.putInt("ActiveIngredient", itemProgress.activeIndex());
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
        if (!Float.isFinite(stability) || !Float.isFinite(stabilityReplenish)
                || !Float.isFinite(essentiaCostMult) || cycleTime < 1 || cycleTime > 100
                || countDelay < 1 || countDelay > 100) {
            active = false; crafting = false; stability = 0; stabilityReplenish = 0;
            essentiaCostMult = 1; cycleTime = 10; countDelay = 5;
        }
        surroundingsInitialized = false;
        ritualLedger = null;
        activeInfusionRitual = null;
        ritualRecipeFingerprint = null;
        itemProgress = null;
        sidePedestals = List.of();
        originalCentralInput = ItemStack.EMPTY;
        plannedOutput = ItemStack.EMPTY;
        originalSideInputs = List.of();
        ritualStartCostMultiplier = 1.0F;
        finishCheckpoint = LegacyInfusionFinishCheckpoint.initial();
        // An old/corrupt 'crafting:1' without a verified ledger cannot craft.
        if (crafting && !tag.contains("PortInfusionLedger", Tag.TAG_COMPOUND))
            crafting = false;
        if (crafting && tag.contains("PortInfusionLedger", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag pending = tag.getCompound("PortInfusionLedger");
                if (!pending.contains("Initial", Tag.TAG_COMPOUND)
                        || !pending.contains("Remaining", Tag.TAG_COMPOUND)
                        || !pending.contains("Recipe", Tag.TAG_STRING)
                        || !pending.contains("RitualId", Tag.TAG_INT_ARRAY)
                        || !pending.contains("Paid", Tag.TAG_INT)
                        || !pending.contains("ConsumedItems", Tag.TAG_INT)
                        || !pending.contains("ItemCountdown", Tag.TAG_INT)
                        || !pending.contains("FinishPhase", Tag.TAG_STRING)
                        || !pending.contains("OriginalCenterInput", Tag.TAG_COMPOUND)
                        || !pending.contains("OriginalSideInputs", Tag.TAG_COMPOUND))
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
                var surcharges = new java.util.LinkedHashMap<String,Integer>();
                if (pending.contains("Surcharges")) {
                    if (!pending.contains("Surcharges", Tag.TAG_COMPOUND))
                        throw new IllegalArgumentException("Invalid infusion surcharges");
                    CompoundTag extra = pending.getCompound("Surcharges");
                    for (String key : extra.getAllKeys()) {
                        if (!extra.contains(key, Tag.TAG_INT)) throw new IllegalArgumentException("Invalid surcharge amount");
                        surcharges.put(key, extra.getInt(key));
                    }
                }
                String id = pending.getString("Recipe");
                activeInfusionRitual = pending.getUUID("RitualId");
                var centerSlot = NonNullList.withSize(1,ItemStack.EMPTY);
                ContainerHelper.loadAllItems(pending.getCompound("OriginalCenterInput"),centerSlot,lookup);
                originalCentralInput = centerSlot.get(0).copy();
                var spec = InfusionRecipeAdapter.effectiveRecipe(InfusionRecipeAdapter.recipe(id), originalCentralInput);
                if (spec == null) throw new IllegalArgumentException("Unregistered infusion recipe");
                if (pending.contains("Format") || pending.contains("RecipeFingerprint")) {
                    if (!pending.contains("RecipeFingerprint",Tag.TAG_STRING))
                        throw new IllegalArgumentException("Missing infusion recipe definition fingerprint");
                    spec.verifyCheckpointFingerprint(pending.getString("RecipeFingerprint"));
                }
                ritualRecipeFingerprint = spec.checkpointFingerprint();
                ritualLedger = LegacyInfusionRitualLedger.restoreVerified(
                        spec, initialCosts, leftCosts, pending.getInt("Paid"),
                        ritualStartCostMultiplier, surcharges);
                finishCheckpoint = LegacyInfusionFinishCheckpoint.restore(
                        pending.getString("FinishPhase"));
                if (finishCheckpoint.finished())
                    throw new IllegalArgumentException("Completed ritual is not crafting");

                if (pending.getInt("Format") == 3 && !pending.contains("ActiveIngredient", Tag.TAG_INT))
                    throw new IllegalArgumentException("Missing selected infusion ingredient");
                itemProgress = LegacyInfusionIngredientQueue.restore(spec.components().size(),
                        pending.getInt("ConsumedItems"), pending.getInt("ItemCountdown"),
                        pending.contains("ActiveIngredient", Tag.TAG_INT) ? pending.getInt("ActiveIngredient") : null);
                if (itemProgress.consumedMask() != 0
                        && ritualLedger.paid() < ritualLedger.requested().values().stream().mapToInt(Integer::intValue).sum())
                    throw new IllegalArgumentException("Items consumed before full essence payment");
                if (!finishCheckpoint.consistentWithPaidProgress(
                        ritualLedger.readyForIngredients(), itemProgress.readyToFinish()))
                    throw new IllegalArgumentException("Reserved result with unpaid inputs");
                if (pending.contains("Format")) {
                    if (!pending.contains("Format",Tag.TAG_INT) || (pending.getInt("Format") != 2 && pending.getInt("Format") != 3)
                            || !pending.contains("SidePedestals",Tag.TAG_LONG_ARRAY))
                        throw new IllegalArgumentException("Unsupported infusion checkpoint format");
                    long[] positions = pending.getLongArray("SidePedestals");
                    if (positions.length != spec.components().size()) throw new IllegalArgumentException("Wrong side count");
                    sidePedestals = java.util.Arrays.stream(positions).mapToObj(BlockPos::of).toList();
                } else {
                    // Explicit migration of existing crystal-only NBT and bit identities (1 seeds, 2 Salis).
                    if (LegacyInfusionCrystalFarmerCatalog.all().stream().noneMatch(r -> r.legacyId().equals(id))
                            || !pending.contains("SeedsPedestal",Tag.TAG_LONG) || !pending.contains("SalisPedestal",Tag.TAG_LONG))
                        throw new IllegalArgumentException("Incomplete legacy crystal checkpoint");
                    sidePedestals = List.of(BlockPos.of(pending.getLong("SeedsPedestal")),BlockPos.of(pending.getLong("SalisPedestal")));
                }

                var sideSlots = NonNullList.withSize(spec.components().size(),ItemStack.EMPTY);
                ContainerHelper.loadAllItems(pending.getCompound("OriginalSideInputs"),sideSlots,lookup);
                originalSideInputs = sideSlots.stream().map(ItemStack::copy).toList();
                if (!trackedPedestalsValid()) throw new IllegalArgumentException("Invalid tracked pedestal positions");
                for (int i = 0; i < originalSideInputs.size(); i++)
                    if (!InfusionRecipeAdapter.matches(spec.components().get(i),originalSideInputs.get(i)))
                        throw new IllegalArgumentException("Invalid original ingredient snapshot");
                if (!InfusionRecipeAdapter.matches(spec.center(),originalCentralInput))
                    throw new IllegalArgumentException("Invalid original center snapshot");
                if (recipeInstability != spec.instability())
                    throw new IllegalArgumentException("Saved infusion instability differs from original recipe");
                if (pending.getInt("Format") == 3) {
                    if (!pending.contains("PlannedOutput", Tag.TAG_COMPOUND))
                        throw new IllegalArgumentException("Missing frozen infusion output");
                    var slot = NonNullList.withSize(1, ItemStack.EMPTY);
                    ContainerHelper.loadAllItems(pending.getCompound("PlannedOutput"), slot, lookup);
                    plannedOutput = slot.get(0).copy();
                } else {
                    // Only pre-existing plain fixed results can migrate without a saved output.
                    plannedOutput = InfusionRecipeAdapter.output(spec);
                }
                if (!InfusionRecipeAdapter.isValidOutput(spec, originalCentralInput, plannedOutput, lookup))
                    throw new IllegalArgumentException("Saved infusion output differs from source recipe");
            } catch (IllegalArgumentException malformed) {
                // Corrupt/newer NBT must never make a free-running ritual.
                crafting = false;
                ritualLedger = null;
                activeInfusionRitual = null;
                ritualRecipeFingerprint = null;
                itemProgress = null;
                sidePedestals = List.of();
                originalCentralInput = ItemStack.EMPTY;
                plannedOutput = ItemStack.EMPTY;
                originalSideInputs = List.of();
                finishCheckpoint = LegacyInfusionFinishCheckpoint.initial();
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
