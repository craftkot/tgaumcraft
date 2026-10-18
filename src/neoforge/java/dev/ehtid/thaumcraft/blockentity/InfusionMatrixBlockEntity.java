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
    /** Unique physical payment identity, saved with the matrix ledger. */
    private UUID activeInfusionRitual;
    /** Original cost multiplier frozen when the player starts this ritual. */
    private float ritualStartCostMultiplier = 1.0F;
    /** Source five-cycle per ingredient, persisted separately from paid essentia. */
    private LegacyCrystalFarmerIngredientProgress itemProgress;
    /** Exact real pedestal positions verified at start; never a newly found replacement. */
    private BlockPos seedsPedestal;
    private BlockPos salisPedestal;
    /** Original actual center ItemStack, including every data component. */
    private ItemStack originalCentralInput = ItemStack.EMPTY;
    /** Two original one-item side stacks, preserving data components and order. */
    private ItemStack originalSeedInput = ItemStack.EMPTY;
    private ItemStack originalSalisInput = ItemStack.EMPTY;
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
        // RecipeMatcher assigns occupied pedestal index -> recipe ingredient
        // index. Preserve that actual physical identity through NBT reload:
        // scanning for ANY replacement input later would permit free output.
        List<BlockPos> occupiedPositions = new ArrayList<>();
        for (BlockPos pos : nearbyPedestals()) {
            if (level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal
                    && pedestal.isOccupied())
                occupiedPositions.add(pos.immutable());
        }
        int[] assignment = preview.matching().ingredientIndices();
        if (occupiedPositions.size() != 2 || assignment.length != 2) return false;
        BlockPos matchedSeeds = null, matchedSalis = null;
        for (int i = 0; i < assignment.length; i++) {
            if (assignment[i] == 0) matchedSeeds = occupiedPositions.get(i);
            else if (assignment[i] == 1) matchedSalis = occupiedPositions.get(i);
            else return false;
        }
        if (matchedSeeds == null || matchedSalis == null
                || matchedSeeds.equals(matchedSalis)) return false;
        if (!(level.getBlockEntity(worldPosition.below(2))
                instanceof PedestalBlockEntity originalCenter)
                || !(level.getBlockEntity(matchedSeeds) instanceof PedestalBlockEntity originalSeeds)
                || !(level.getBlockEntity(matchedSalis) instanceof PedestalBlockEntity originalSalis))
            return false;
        ItemStack capturedCenter = originalCenter.displayedItem().copy();
        ItemStack capturedSeeds = originalSeeds.displayedItem().copy();
        ItemStack capturedSalis = originalSalis.displayedItem().copy();
        if (capturedCenter.getCount() != 1
                || !capturedCenter.is(ThaumcraftItems.CRYSTAL_ESSENCE.get())
                || !preview.recipe().aspectTag().equals(
                        CrystalEssenceItem.legacyAspect(capturedCenter))
                || capturedSeeds.getCount() != 1 || !capturedSeeds.is(Items.WHEAT_SEEDS)
                || capturedSalis.getCount() != 1
                || !capturedSalis.is(ThaumcraftItems.SALIS_MUNDUS.get()))
            return false;
        seedsPedestal = matchedSeeds;
        salisPedestal = matchedSalis;
        originalCentralInput = capturedCenter;
        originalSeedInput = capturedSeeds;
        originalSalisInput = capturedSalis;
        ritualLedger = LegacyInfusionRitualLedger.begin(
                preview.recipe().legacyId(), preview.matching().cost());
        activeInfusionRitual = UUID.randomUUID();
        ritualStartCostMultiplier = preview.matching().effectiveMultiplier();
        itemProgress = LegacyCrystalFarmerIngredientProgress.initial();
        finishCheckpoint = LegacyCrystalFarmerFinishCheckpoint.initial();
        recipeInstability = preview.matching().instability();
        crafting = true;
        changedAndSync(); // checkpoint BEFORE any real jar is debited
        ThaumcraftSmokeAudit.mark("INFUSION_CRYSTAL_RESEARCH_GATED_START");
        return true;
    }

    private boolean trackedPedestalsValid() {
        if (seedsPedestal == null || salisPedestal == null
                || seedsPedestal.equals(salisPedestal)) return false;
        return isTrackedSidePosition(seedsPedestal)
                && isTrackedSidePosition(salisPedestal);
    }

    private boolean isTrackedSidePosition(BlockPos where) {
        return LegacyInfusionMatrixLayout.withinScanVolume(
                new LegacyInfusionMatrixLayout.Offset(
                        where.getX() - worldPosition.getX(),
                        where.getY() - worldPosition.getY(),
                        where.getZ() - worldPosition.getZ()));
    }

    /**
     * A pedestal can save its EMPTY slot and exact receipt before the matrix
     * saves the resulting ingredient mask. Restore precisely this one missed
     * transition instead of losing already-consumed materials.
     *
     * Only the originally matched side position, a matching ritual UUID,
     * verified full essentia payment and the expected opposite pedestal
     * permit acknowledgement. The matrix may have saved any earlier 0..5
     * countdown; the receipt proves removal regardless of that stale timer.
     * No new item or additional debit is performed.
     */
    private void recoverSavedSideItemDebit() {
        if (!(level instanceof ServerLevel) || !active || !crafting
                || !finishCheckpoint.canReserve() || activeInfusionRitual == null
                || ritualLedger == null || !ritualLedger.readyForIngredients()
                || itemProgress == null || itemProgress.readyToFinish()
                || !validLocation() || !trackedPedestalsValid()
                || originalCentralInput.isEmpty())
            return;
        if (!(level.getBlockEntity(worldPosition.below(2)) instanceof PedestalBlockEntity center)
                || !ItemStack.isSameItemSameComponents(
                        center.displayedItem(), originalCentralInput))
            return;
        if (!level.hasChunkAt(seedsPedestal) || !level.hasChunkAt(salisPedestal)
                || !(level.getBlockEntity(seedsPedestal) instanceof PedestalBlockEntity seeds)
                || !(level.getBlockEntity(salisPedestal) instanceof PedestalBlockEntity salis))
            return;
        boolean seedReceipt = seeds.hasInfusionRemovalReceipt(activeInfusionRitual,
                LegacyCrystalFarmerIngredientProgress.SEEDS_BIT);
        boolean salisReceipt = salis.hasInfusionRemovalReceipt(activeInfusionRitual,
                LegacyCrystalFarmerIngredientProgress.SALIS_BIT);
        boolean untouchedSalis = ItemStack.isSameItemSameComponents(
                salis.displayedItem(), originalSalisInput);
        int earlierMask = itemProgress.consumedMask();
        // Only the original UUID receipts may advance persisted progress.
        // A matrix that saved BEFORE both pedestal debits may skip from
        // mask=0 straight to mask=3, but NEVER consume either input twice.
        if ((earlierMask == 0 && (!seedReceipt
                || (salisReceipt == untouchedSalis)))
                || (earlierMask == LegacyCrystalFarmerIngredientProgress.SEEDS_BIT
                && (!seedReceipt || !salisReceipt || untouchedSalis)))
            return;
        itemProgress = itemProgress.afterSavedWorldReceipts(
                seedReceipt, salisReceipt, untouchedSalis);
        changedAndSync();
        if ((earlierMask & LegacyCrystalFarmerIngredientProgress.SEEDS_BIT) == 0)
            ThaumcraftSmokeAudit.mark("INFUSION_CRYSTAL_SEEDS_DEBIT_RECOVERED");
        if ((earlierMask & LegacyCrystalFarmerIngredientProgress.SALIS_BIT) == 0
                && (itemProgress.consumedMask()
                    & LegacyCrystalFarmerIngredientProgress.SALIS_BIT) != 0)
            ThaumcraftSmokeAudit.mark("INFUSION_CRYSTAL_SALIS_DEBIT_RECOVERED");
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
                || !spec.aspectTag().equals(CrystalEssenceItem.legacyAspect(center.displayedItem()))
                || originalCentralInput.isEmpty()
                || !ItemStack.isSameItemSameComponents(
                        originalCentralInput, center.displayedItem()))
            return false;
        // Do not force-load a vanished side chunk. A consumed input MUST
        // still be absent at its originally assigned pedestal after reload.
        if (itemProgress == null || !trackedPedestalsValid()
                || !level.hasChunkAt(seedsPedestal) || !level.hasChunkAt(salisPedestal)
                || !(level.getBlockEntity(seedsPedestal) instanceof PedestalBlockEntity seedTile)
                || !(level.getBlockEntity(salisPedestal) instanceof PedestalBlockEntity salisTile))
            return false;
        ItemStack seedStack = seedTile.displayedItem();
        ItemStack salisStack = salisTile.displayedItem();
        if ((!seedStack.isEmpty() && (seedStack.getCount() != 1 || !seedStack.is(Items.WHEAT_SEEDS)))
                || (!salisStack.isEmpty() && (salisStack.getCount() != 1
                        || !salisStack.is(ThaumcraftItems.SALIS_MUNDUS.get()))))
            return false;
        return activeInfusionRitual != null
                && itemProgress.matchesLivePedestals(!seedStack.isEmpty(), !salisStack.isEmpty())
                && itemProgress.matchesSavedReceipts(
                        seedTile.hasInfusionRemovalReceipt(activeInfusionRitual,
                                LegacyCrystalFarmerIngredientProgress.SEEDS_BIT),
                        salisTile.hasInfusionRemovalReceipt(activeInfusionRitual,
                                LegacyCrystalFarmerIngredientProgress.SALIS_BIT))
                && originalSeedInput.getCount() == 1
                && originalSalisInput.getCount() == 1
                && (!seedStack.isEmpty()
                        ? ItemStack.isSameItemSameComponents(seedStack, originalSeedInput)
                        : (itemProgress.consumedMask()
                                & LegacyCrystalFarmerIngredientProgress.SEEDS_BIT) != 0)
                && (!salisStack.isEmpty()
                        ? ItemStack.isSameItemSameComponents(salisStack, originalSalisInput)
                        : (itemProgress.consumedMask()
                                & LegacyCrystalFarmerIngredientProgress.SALIS_BIT) != 0);
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
                || !finishCheckpoint.canReserve()
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
                || !finishCheckpoint.canReserve()
                || ritualLedger == null || !ritualLedger.readyForIngredients()
                || itemProgress == null || itemProgress.readyToFinish()
                || !realInputsRemain()) return;
        int nextBit = itemProgress.nextIngredientBit();
        BlockPos assigned = nextBit == LegacyCrystalFarmerIngredientProgress.SEEDS_BIT
                ? seedsPedestal : salisPedestal;
        if (assigned == null || !level.hasChunkAt(assigned)
                || !(level.getBlockEntity(assigned) instanceof PedestalBlockEntity chosen))
            return;
        ItemStack captured = chosen.displayedItem().copy();
        if (captured.getCount() != 1 || !(nextBit == LegacyCrystalFarmerIngredientProgress.SEEDS_BIT
                ? captured.is(Items.WHEAT_SEEDS)
                : captured.is(ThaumcraftItems.SALIS_MUNDUS.get()))) return;
        switch (itemProgress.nextAction()) {
            case SOURCE_EFFECT, COUNTDOWN -> {
                itemProgress = itemProgress.afterValidatedCountdown();
                changedAndSync();
            }
            case CONSUME -> {
                // Both original recipe inputs have no crafting remainder.
                // Never consume without checking the original data components.
                if (chosen.consumeInfusionIngredientWithReceipt(
                        captured, activeInfusionRitual, nextBit)) {
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


    /** Exact catalogued Crystal Farmer BlockItem, never a saved-NBT output. */
    private static net.minecraft.world.item.Item crystalFarmerOutput(String aspect) {
        return switch (aspect) {
            case "aer" -> ThaumcraftItems.CRYSTAL_AER.get();
            case "ignis" -> ThaumcraftItems.CRYSTAL_IGNIS.get();
            case "aqua" -> ThaumcraftItems.CRYSTAL_AQUA.get();
            case "terra" -> ThaumcraftItems.CRYSTAL_TERRA.get();
            case "ordo" -> ThaumcraftItems.CRYSTAL_ORDO.get();
            case "perditio" -> ThaumcraftItems.CRYSTAL_PERDITIO.get();
            case "vitium" -> ThaumcraftItems.CRYSTAL_VITIUM.get();
            default -> null;
        };
    }

    /** Clears only accounting after the result is already on the pedestal. */
    private void clearCompletedCrystalFarmer() {
        crafting = false;
        recipeInstability = 0;
        ritualLedger = null;
        activeInfusionRitual = null;
        itemProgress = null;
        seedsPedestal = null;
        salisPedestal = null;
        originalCentralInput = ItemStack.EMPTY;
        originalSeedInput = ItemStack.EMPTY;
        originalSalisInput = ItemStack.EMPTY;
        ritualStartCostMultiplier = 1.0F;
        originalCentralInput = ItemStack.EMPTY;
        originalSeedInput = ItemStack.EMPTY;
        originalSalisInput = ItemStack.EMPTY;
        finishCheckpoint = LegacyCrystalFarmerFinishCheckpoint.initial();
        changedAndSync();
    }

    /**
     * Crash window: matrix saved RESERVED but central pedestal already saved
     * its genuine BlockItem output. Restoring RESERVED yields STALLED, so
     * NEVER call craftingFinish again. We can ACK the existing output only
     * after checking the paid ledger, consumed mask, fixed side positions and
     * the exact center result in the real loaded world.
     *
     * Otherwise leave STALLED untouched for manual recovery. No items,
     * essentia, or world chunks are generated/loaded by this method.
     */
    private void acknowledgeRecoveredCrystalFarmerOutput() {
        if (!(level instanceof ServerLevel) || !active || !crafting
                || ritualLedger == null || !ritualLedger.readyForIngredients()
                || itemProgress == null || !itemProgress.readyToFinish()
                || finishCheckpoint.phase() != LegacyCrystalFarmerFinishCheckpoint.Phase.STALLED
                || !validLocation() || !trackedPedestalsValid()
                || !level.hasChunkAt(seedsPedestal) || !level.hasChunkAt(salisPedestal)
                || !(level.getBlockEntity(seedsPedestal) instanceof PedestalBlockEntity seeds)
                || !(level.getBlockEntity(salisPedestal) instanceof PedestalBlockEntity salis)
                || !seeds.displayedItem().isEmpty()
                || !salis.displayedItem().isEmpty()
                || activeInfusionRitual == null
                || !seeds.hasInfusionRemovalReceipt(activeInfusionRitual,
                        LegacyCrystalFarmerIngredientProgress.SEEDS_BIT)
                || !salis.hasInfusionRemovalReceipt(activeInfusionRitual,
                        LegacyCrystalFarmerIngredientProgress.SALIS_BIT)
                || !(level.getBlockEntity(worldPosition.below(2)) instanceof PedestalBlockEntity center))
            return;
        var spec = LegacyInfusionCrystalFarmerCatalog.all().stream()
                .filter(r -> r.legacyId().equals(ritualLedger.recipeId()))
                .findFirst().orElse(null);
        if (spec == null) return;
        net.minecraft.world.item.Item expected = crystalFarmerOutput(spec.aspectTag());
        ItemStack present = center.displayedItem();
        if (expected == null || present.getCount() != 1 || !present.is(expected))
            return;
        var ack = finishCheckpoint.acknowledgeAlreadyPublished(
                ritualLedger.readyForIngredients(),
                itemProgress.readyToFinish(), true);
        if (!ack.finished()) return;
        finishCheckpoint = ack;
        clearCompletedCrystalFarmer();
        ThaumcraftSmokeAudit.mark("INFUSION_CRYSTAL_SAVED_OUTPUT_ACKNOWLEDGED");
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
        net.minecraft.world.item.Item outputItem = crystalFarmerOutput(recipe.aspectTag());
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
        clearCompletedCrystalFarmer();
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
        if (tile.crafting && tile.itemProgress != null
                && tile.ritualLedger != null
                && tile.ritualLedger.readyForIngredients())
            tile.recoverSavedSideItemDebit();
        // Restored RESERVED is STALLED. Acknowledge ONLY a pre-existing,
        // fully paid output; never call the original finish a second time.
        if (tile.crafting && tile.finishCheckpoint.phase()
                == LegacyCrystalFarmerFinishCheckpoint.Phase.STALLED)
            tile.acknowledgeRecoveredCrystalFarmerOutput();
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
            pending.putString("FinishPhase", finishCheckpoint.phase().name());
            if (!originalCentralInput.isEmpty()) {
                var slot = NonNullList.withSize(1, ItemStack.EMPTY);
                slot.set(0, originalCentralInput.copy());
                CompoundTag savedInput = new CompoundTag();
                ContainerHelper.saveAllItems(savedInput, slot, lookup);
                pending.put("OriginalCenterInput", savedInput);
            }
            if (!originalSeedInput.isEmpty() && !originalSalisInput.isEmpty()) {
                var sideSlots = NonNullList.withSize(2, ItemStack.EMPTY);
                sideSlots.set(0, originalSeedInput.copy());
                sideSlots.set(1, originalSalisInput.copy());
                CompoundTag savedSides = new CompoundTag();
                ContainerHelper.saveAllItems(savedSides, sideSlots, lookup);
                pending.put("OriginalSideInputs", savedSides);
            }
            if (trackedPedestalsValid()) {
                pending.putLong("SeedsPedestal", seedsPedestal.asLong());
                pending.putLong("SalisPedestal", salisPedestal.asLong());
            }
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
        activeInfusionRitual = null;
        itemProgress = null;
        seedsPedestal = null;
        salisPedestal = null;
        originalCentralInput = ItemStack.EMPTY;
        originalSeedInput = ItemStack.EMPTY;
        originalSalisInput = ItemStack.EMPTY;
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
                        || !pending.contains("RitualId", Tag.TAG_INT_ARRAY)
                        || !pending.contains("Paid", Tag.TAG_INT)
                        || !pending.contains("ConsumedItems", Tag.TAG_INT)
                        || !pending.contains("ItemCountdown", Tag.TAG_INT)
                        || !pending.contains("FinishPhase", Tag.TAG_STRING)
                        || !pending.contains("OriginalCenterInput", Tag.TAG_COMPOUND)
                        || !pending.contains("OriginalSideInputs", Tag.TAG_COMPOUND)
                        || !pending.contains("SeedsPedestal", Tag.TAG_LONG)
                        || !pending.contains("SalisPedestal", Tag.TAG_LONG))
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
                activeInfusionRitual = pending.getUUID("RitualId");
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
                if (!finishCheckpoint.consistentWithPaidProgress(
                        ritualLedger.readyForIngredients(), itemProgress.readyToFinish()))
                    throw new IllegalArgumentException("Reserved result with unpaid inputs");
                seedsPedestal = BlockPos.of(pending.getLong("SeedsPedestal"));
                salisPedestal = BlockPos.of(pending.getLong("SalisPedestal"));
                if (!trackedPedestalsValid())
                    throw new IllegalArgumentException("Invalid persisted side pedestal positions");
                var centerSlot = NonNullList.withSize(1, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(
                        pending.getCompound("OriginalCenterInput"), centerSlot, lookup);
                originalCentralInput = centerSlot.get(0).copy();
                var sideSlots = NonNullList.withSize(2, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(
                        pending.getCompound("OriginalSideInputs"), sideSlots, lookup);
                originalSeedInput = sideSlots.get(0).copy();
                originalSalisInput = sideSlots.get(1).copy();
                if (originalSeedInput.getCount() != 1
                        || !originalSeedInput.is(Items.WHEAT_SEEDS)
                        || originalSalisInput.getCount() != 1
                        || !originalSalisInput.is(ThaumcraftItems.SALIS_MUNDUS.get()))
                    throw new IllegalArgumentException("Invalid original side ingredient snapshots");
                var spec = LegacyInfusionCrystalFarmerCatalog.all().stream()
                        .filter(r -> r.legacyId().equals(ritualLedger.recipeId()))
                        .findFirst().orElse(null);
                if (spec == null || originalCentralInput.getCount() != 1
                        || !originalCentralInput.is(ThaumcraftItems.CRYSTAL_ESSENCE.get())
                        || !spec.aspectTag().equals(
                                CrystalEssenceItem.legacyAspect(originalCentralInput)))
                    throw new IllegalArgumentException("Original central crystal snapshot differs from recipe");
                if (recipeInstability != spec.instability())
                    throw new IllegalArgumentException("Saved infusion instability differs from original recipe");
            } catch (IllegalArgumentException malformed) {
                // Corrupt/newer NBT must never make a free-running ritual.
                crafting = false;
                ritualLedger = null;
                activeInfusionRitual = null;
                itemProgress = null;
                seedsPedestal = null;
                salisPedestal = null;
                originalCentralInput = ItemStack.EMPTY;
                originalSeedInput = ItemStack.EMPTY;
                originalSalisInput = ItemStack.EMPTY;
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
