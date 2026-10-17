package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.LegacyResearchTableData;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftInspiration;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.scanning.LegacyResearchTableNbt;
import dev.ehtid.thaumcraft.scanning.LegacyTheorycraftChoiceNbt;
import dev.ehtid.thaumcraft.scanning.LegacyTheorycraftCardTagValidator;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftChoiceFlow;
import dev.ehtid.thaumcraft.scanning.NeoForgeTheorycraftAidBridge;
import dev.ehtid.thaumcraft.scanning.NeoForgeTheorycraftDrawBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;

import java.util.Objects;
import java.util.Set;

/**
 * Original BETA26 TileResearchTable inventory: exactly two item slots.
 * Slot 0 accepts IScribeTools; slot 1 accepts vanilla paper.
 *
 * The `note` NBT compound is the original research-table session field.
 * The original savedCards, aidCards and blocked-category lists are decoded.
 * Unknown cardChoices and lastDraw retain their nested original NBT intact
 * until their card subclasses are ported; don't erase them during autosave.
 */
public final class ResearchTableBlockEntity extends BlockEntity implements Container {
    public static final int SCRIBING_SLOT = 0;
    public static final int PAPER_SLOT = 1;
    private final NonNullList<ItemStack> items =
            NonNullList.withSize(2, ItemStack.EMPTY);
    private LegacyResearchTableData session;
    /** Lossless NBT passthrough for original, not-yet-ported card subclasses. */
    private CompoundTag originalSessionNbt = new CompoundTag();
    private long lastNearbyAidsCheck = Long.MIN_VALUE;
    private Set<String> cachedNearbyAids = Set.of();

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) {
        super(ThaumcraftBlockEntities.RESEARCH_TABLE.get(), pos, state);
    }

    public LegacyResearchTableData session() { return session; }
    /**
     * BETA26 CardChoice values are live, subclass-specific NBT records. These
     * accessors permit a future complete draw/activate bridge to preserve all
     * 33 original subclasses without overwriting unknown saved fields.
     */
    public java.util.List<LegacyTheorycraftChoiceNbt.Choice> pendingChoices() {
        return session == null ? java.util.List.of()
                : LegacyTheorycraftChoiceNbt.readChoices(originalSessionNbt);
    }

    /**
     * Do not trust partially decoded old-world card choice lists: readChoices
     * deliberately skips malformed rows to preserve legacy save passthrough.
     * Gameplay must reject those sessions rather than showing an activatable
     * subset that can never be committed.
     */
    public boolean hasSavedCardChoicesTag() {
        return session != null && originalSessionNbt.contains("cardChoices");
    }

    public boolean hasStructurallyValidPendingChoices() {
        if (session == null || !originalSessionNbt.contains("cardChoices",
                net.minecraft.nbt.Tag.TAG_LIST)) return false;
        var rawTag = originalSessionNbt.get("cardChoices");
        if (!(rawTag instanceof net.minecraft.nbt.ListTag rows)
                || rows.size() > 3
                || (!rows.isEmpty() && rows.getElementType() != net.minecraft.nbt.Tag.TAG_COMPOUND))
            return false;
        var decoded = pendingChoices();
        if (decoded.size() != rows.size()) return false;
        int selected = 0;
        for (var choice : decoded) {
            if (!LegacyTheorycraftCardTagValidator.structurallyValid(
                    choice.key(), choice.cardNBT())) return false;
            if (choice.selected() && ++selected > 1) return false;
        }
        return true;
    }

    /**
     * A corrupt lastDraw must not be replaced by a subsequent keep operation.
     * Preserve its original subclass NBT until an explicit migration.
     */
    public boolean hasValidLastDrawHistory() {
        if (session == null) return false;
        if (!originalSessionNbt.contains("lastDraw")) return true;
        if (!originalSessionNbt.contains("lastDraw", net.minecraft.nbt.Tag.TAG_COMPOUND))
            return false;
        var last = lastDrawChoice();
        return last.isPresent() && LegacyTheorycraftCardTagValidator
                .structurallyValid(last.get().key(), last.get().cardNBT());
    }

    public java.util.Optional<LegacyTheorycraftChoiceNbt.Choice> lastDrawChoice() {
        return session == null ? java.util.Optional.empty()
                : LegacyTheorycraftChoiceNbt.readLastDraw(originalSessionNbt);
    }

    /** Server-side state writer. Call only after full card factory validation. */
    public boolean storeCardChoices(java.util.List<LegacyTheorycraftChoiceNbt.Choice> choices) {
        if (level == null || level.isClientSide || session == null) return false;
        java.util.Objects.requireNonNull(choices);
        // The BETA26 GUI has exactly three offer slots. Reject malformed
        // cross-integration writes before changing any persistent NBT.
        if (choices.size() > 3) return false;
        int selectedCount = 0;
        for (var choice : choices) {
            if (choice == null || !LegacyTheorycraftCardTagValidator
                    .structurallyValid(choice.key(), choice.cardNBT())) return false;
            if (choice.selected() && ++selectedCount > 1) return false;
        }
        LegacyTheorycraftChoiceNbt.writeChoices(originalSessionNbt, choices);
        setChanged();
        return true;
    }

    /**
     * Original ContainerResearchTable button 1 during an existing theory:
     * keep the selected card as lastDraw, append the PREVIOUS lastDraw seed
     * to savedCards, then clear pending choices. No materials are charged.
     *
     * Unlike BETA26, a missing selection or malformed legacy NBT is rejected
     * rather than saving duplicate seeds or discarding uninterpretable rows.
     * This is safe for old theories loaded before the client card UI exists.
     */
    public boolean keepSelectedTheoryCard(ServerPlayer player) {
        if (level == null || level.isClientSide || session == null || !stillValid(player))
            return false;
        // Preserve corrupt historical offers intact: the permissive decoder
        // can skip broken rows, so checking only the decoded count is unsafe.
        if (!hasStructurallyValidPendingChoices()
                || !hasValidLastDrawHistory()) return false;
        var choices = pendingChoices();
        if (choices.isEmpty()) return false;

        LegacyTheorycraftChoiceNbt.Choice chosen = null;
        for (var choice : choices) {
            if (!dev.ehtid.thaumcraft.port.core.LegacyTheorycraftManifest
                    .allCardKeys().contains(choice.key())) return false;
            if (choice.selected()) {
                // Reject corrupt legacy saves with more than one selected row.
                if (chosen != null) return false;
                chosen = choice;
            }
        }
        if (chosen == null || !chosen.cardNBT().contains("seed", net.minecraft.nbt.Tag.TAG_LONG))
            return false;
        // A legacy lastDraw with wrong tag type, unknown subclass or
        // incomplete NBT must never be appended to savedCards as a seed.
        if (originalSessionNbt.contains("lastDraw")
                && !originalSessionNbt.contains("lastDraw", net.minecraft.nbt.Tag.TAG_COMPOUND))
            return false;
        var previous = lastDrawChoice().orElse(null);
        if (originalSessionNbt.contains("lastDraw") && previous == null) return false;
        if (previous != null && !LegacyTheorycraftCardTagValidator.structurallyValid(
                previous.key(), previous.cardNBT())) return false;

        var plain = choices.stream().map(c -> new LegacyTheorycraftChoiceFlow.Choice(
                c.key(), c.seed(), c.fromAid(), c.selected())).toList();
        var previousPlain = previous == null ? null
                : new LegacyTheorycraftChoiceFlow.Choice(
                    previous.key(), previous.seed(), previous.fromAid(), previous.selected());
        var transition = LegacyTheorycraftChoiceFlow.keepSelection(
                plain, previousPlain, session.savedCards());
        session.replaceSavedCards(transition.savedCardSeeds());
        // Preserve the selected card's entire original subclass NBT.
        LegacyTheorycraftChoiceNbt.writeLastDraw(originalSessionNbt, chosen);
        LegacyTheorycraftChoiceNbt.writeChoices(originalSessionNbt, java.util.List.of());
        setChanged();
        dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("THEORY_CARD_KEPT");
        return true;
    }

    /** Exact original lastDraw shape; null removes the compound entirely. */
    public boolean storeLastDrawChoice(LegacyTheorycraftChoiceNbt.Choice choice) {
        if (level == null || level.isClientSide || session == null) return false;
        if (choice != null && !LegacyTheorycraftCardTagValidator
                .structurallyValid(choice.key(), choice.cardNBT())) return false;
        LegacyTheorycraftChoiceNbt.writeLastDraw(originalSessionNbt, choice);
        setChanged();
        return true;
    }

    /**
     * The original 9x9x3 surroundings check is costly. Scan once per second
     * for menu display, but ALWAYS rescan at theory creation to make removed
     * or changed aid blocks impossible to exploit between synchronization.
     */
    public Set<String> nearbyAids() {
        if (level == null || level.isClientSide) return Set.of();
        long tick = level.getGameTime();
        if (lastNearbyAidsCheck == Long.MIN_VALUE || tick < lastNearbyAidsCheck
                || tick - lastNearbyAidsCheck >= 20) {
            return refreshNearbyAids();
        }
        return cachedNearbyAids;
    }

    private Set<String> refreshNearbyAids() {
        if (level == null || level.isClientSide) return Set.of();
        cachedNearbyAids = NeoForgeTheorycraftAidBridge.discover(level, worldPosition);
        lastNearbyAidsCheck = level.getGameTime();
        return cachedNearbyAids;
    }


    /**
     * The BETA26 GuiResearchTable create button becomes enabled only when
     * paper is available and the IScribeTools item has ink remaining.
     * Starting a theory itself consumes neither of those table slots.
     */
    public boolean canStartNewTheory() {
        ItemStack tools = items.get(SCRIBING_SLOT);
        ItemStack paper = items.get(PAPER_SLOT);
        return session == null
                && tools.is(ThaumcraftItems.SCRIBING_TOOLS.get())
                && tools.isDamageableItem()
                && tools.getDamageValue() < tools.getMaxDamage()
                && paper.is(Items.PAPER) && !paper.isEmpty();
    }

    /**
     * ResearchTableData.initialize's current verified slice. The complete
     * card-selection UI will call this only after the player has chosen aides.
     */
    public boolean startNewTheory(ServerPlayer player, Set<String> aids) {
        Objects.requireNonNull(aids, "aids");
        if (level == null || level.isClientSide || !canStartNewTheory()
                || !stillValid(player) || !refreshNearbyAids().containsAll(aids))
            return false;
        session = LegacyTheorycraftInspiration.start(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),
                BuiltInResearchCatalog.create(), aids);
        session.setPlayer(player.getGameProfile().getName());
        originalSessionNbt = new CompoundTag();
        LegacyTheorycraftChoiceNbt.writeChoices(originalSessionNbt, java.util.List.of());
        setChanged();
        dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("THEORY_CREATED");
        return true;
    }

    /**
     * First genuine SERVER draw path, with all 33 BETA26 card subclasses
     * initialized, their historical NBT preserved, and original aid/normal
     * pools sampled without replacing missing source materials.
     *
     * This method is intentionally NOT reachable from ResearchTableMenu yet:
     * activation and its 33 real card effects must be fully wired before
     * exposing a paper-consuming draw to players.
     */
    public boolean drawOriginalTheoryCards(ServerPlayer player, int requestedCards) {
        if (level == null || level.isClientSide || session == null
                || session.isComplete() || !stillValid(player)
                || (requestedCards != 2 && requestedCards != 3)
                || (requestedCards == 3 && session.bonusDraws() <= 0))
            return false;
        // Never reroll and replace still-unactivated offers: the original
        // GUI only draws again after accepting one of the current cards.
        // Also preserve malformed legacy rows rather than overwriting them.
        if (originalSessionNbt.contains("cardChoices")
                && !originalSessionNbt.contains("cardChoices", net.minecraft.nbt.Tag.TAG_LIST))
            return false;
        // Never overwrite malformed or selected legacy offers. Even if a
        // future loader decodes only a subset, the raw NBT must be intact.
        if (originalSessionNbt.contains("cardChoices")
                && !hasStructurallyValidPendingChoices()) return false;
        if (!hasValidLastDrawHistory()) return false;
        if (!pendingChoices().isEmpty()) return false;
        ItemStack paper = items.get(PAPER_SLOT);
        if (paper.isEmpty() || !paper.is(Items.PAPER)) return false;

        var rng = player.getRandom();
        var prepared = NeoForgeTheorycraftDrawBridge.prepare(
                player, session, requestedCards,
                new dev.ehtid.thaumcraft.port.core.LegacyTheorycraftDrawEngine.RandomSource() {
                    @Override public float nextFloat() { return rng.nextFloat(); }
                    @Override public int nextInt(int bound) { return rng.nextInt(bound); }
                    @Override public long nextLong() { return rng.nextLong(); }
                });
        if (prepared.choices().isEmpty() || prepared.choices().size() > 3) {
            // The original can consume paper and return zero cards if no
            // registered candidate survives. Never charge the player for
            // a non-playable empty set during staged migration.
            return false;
        }
        var chosenKeys = new java.util.HashSet<String>();
        for (var choice : prepared.choices()) {
            if (!chosenKeys.add(choice.key())
                    || !LegacyTheorycraftCardTagValidator.structurallyValid(
                            choice.key(), choice.cardNBT()))
                return false;
        }
        // Verify helper pool is a MULTISET subset, keeping original
        // BETA26 aid-card weights (not only presence) intact.
        var oldAids = new java.util.ArrayList<>(session.aidCards());
        for (String used : prepared.remainingAidCards()) {
            if (!oldAids.remove(used)) return false;
        }
        if (!consumePaperFromTable()) return false;

        LegacyTheorycraftChoiceNbt.writeChoices(originalSessionNbt, prepared.choices());
        session.replaceAidCards(prepared.remainingAidCards());
        session.addBonusDraws(prepared.remainingBonusDraws() - session.bonusDraws());
        session.setPlayer(player.getGameProfile().getName());
        setChanged();
        return true;
    }

    /**
     * Server-authoritative BETA26 button 4..6 activation, with one synchronous
     * transaction across the player's 36 inventory slots, table ink/paper,
     * experience, original theory NBT, player knowledge and warp.
     *
     * Card effects are FIRST staged against copies. Every external resource is
     * then rechecked BEFORE the first shrink/durability mutation. The normal
     * server main thread performs the remainder without asynchronous calls;
     * client-supplied NBT, card IDs, costs and rewards are never trusted.
     *
     * This does not imply the draw/select GUI is complete. Call this from
     * the menu only when the complete research-card interaction is exposed.
     */
    public boolean activateOriginalTheoryCard(ServerPlayer player, int index) {
        if (level == null || level.isClientSide || session == null
                || !stillValid(player) || session.isComplete()
                || index < 0 || index >= 3) return false;
        // A partially corrupt legacy cardChoices list must not be rewritten
        // or allowed to award anything: preserve it for a future migration.
        if (!originalSessionNbt.contains("cardChoices", net.minecraft.nbt.Tag.TAG_LIST))
            return false;
        var raw = originalSessionNbt.getList("cardChoices", net.minecraft.nbt.Tag.TAG_COMPOUND);
        var choices = pendingChoices();
        if (choices.size() != raw.size() || index >= choices.size()) return false;
        for (var c : choices) {
            if (!LegacyTheorycraftCardTagValidator.structurallyValid(c.key(), c.cardNBT()))
                return false;
        }

        var preflight = dev.ehtid.thaumcraft.scanning.NeoForgeTheorycraftActivationPreflight
                .check(player, this, index);
        if (preflight == null || !preflight.canActivate()) return false;

        // Staging may consume the actual player RNG but cannot change live
        // inventory, table, research, warp or XP on a failed card effect.
        var result = dev.ehtid.thaumcraft.scanning.NeoForgeTheorycraftActivationStager
                .stageAfterValidatedCosts(player, this, index, preflight);
        if (result == null) return false;

        ItemStack reward = ItemStack.EMPTY;
        if (result.spawnedCrystalAspect() != null) {
            reward = dev.ehtid.thaumcraft.scanning.NeoForgeTheorycraftMaterialResolver
                    .crystal(result.spawnedCrystalAspect());
            if (reward.isEmpty()) return false;
        }

        // Do not let the stager's saved-card / source-NBT view go stale even
        // if a future integration introduces nested callbacks.
        var checked = dev.ehtid.thaumcraft.scanning.NeoForgeTheorycraftActivationPreflight
                .check(player, this, index);
        if (checked == null || !checked.canActivate()
                || !checked.originalCard().equals(preflight.originalCard())
                || !canSpendTableSupplies(checked.costs().inkUnits(),
                        checked.costs().tablePaperUnits())) return false;
        var required = dev.ehtid.thaumcraft.scanning.NeoForgeTheorycraftMaterialResolver
                .resolve(checked.originalCard().key(), checked.originalCard().cardNBT());
        if (required == null) return false;

        // All failure-prone computations have finished before any debit.
        // The item planner checks duplicate requirements and retained
        // catalysts together, then rechecks the entire inventory on commit.
        if (!dev.ehtid.thaumcraft.scanning.NeoForgeTheorycraftItemTransaction
                .commit(player, required.required(), required.consumed())) return false;

        if (!spendTableSupplies(checked.costs().inkUnits(),
                checked.costs().tablePaperUnits()))
            throw new IllegalStateException("research table supplies changed during atomic commit");
        if (checked.costs().experienceLevelsDebited() > 0)
            player.giveExperienceLevels(-checked.costs().experienceLevelsDebited());

        var data = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA);
        var knowledge = data.knowledge();
        // getResearchList() is a LIVE view; copy it before loadSnapshot.clear().
        knowledge.loadSnapshot(new java.util.HashSet<>(knowledge.getResearchList()), knowledge.copyStages(),
                knowledge.copyFlags(), result.playerKnowledgeRaw());
        data.warp().loadLegacyWarpArray(result.warpArray());
        data.warp().setCounter(result.warpCounter());

        session = LegacyResearchTableData.fromSnapshot(result.theory());
        var selected = new java.util.ArrayList<LegacyTheorycraftChoiceNbt.Choice>(choices);
        var choice = selected.get(index);
        selected.set(index, new LegacyTheorycraftChoiceNbt.Choice(
                choice.key(), choice.fromAid(), true, choice.cardNBT(), choice.originalRow()));
        LegacyTheorycraftChoiceNbt.writeChoices(originalSessionNbt, selected);
        setChanged();
        if (!reward.isEmpty() && !player.getInventory().add(reward))
            player.drop(reward, false);
        ThaumcraftNetworking.sync(player);
        return true;
    }

    /**
     * Original card 4..6 consumes one ink unit; Scripting consumes an extra
     * ink unit AND paper. Both budgets must pass before either is touched.
     */
    public boolean canSpendTableSupplies(int inkUnits, int paperUnits) {
        if (level == null || level.isClientSide || session == null
                || inkUnits < 0 || paperUnits < 0) return false;
        ItemStack ink = items.get(SCRIBING_SLOT);
        ItemStack paper = items.get(PAPER_SLOT);
        return ink.is(ThaumcraftItems.SCRIBING_TOOLS.get())
                && ink.isDamageableItem()
                && ink.getMaxDamage() - ink.getDamageValue() >= inkUnits
                && (paperUnits == 0 || paper.is(Items.PAPER)
                        && paper.getCount() >= paperUnits);
    }

    public boolean spendTableSupplies(int inkUnits, int paperUnits) {
        if (!canSpendTableSupplies(inkUnits, paperUnits)) return false;
        if (paperUnits > 0) items.get(PAPER_SLOT).shrink(paperUnits);
        if (inkUnits > 0) {
            ItemStack ink = items.get(SCRIBING_SLOT);
            ink.setDamageValue(ink.getDamageValue() + inkUnits);
        }
        if (inkUnits > 0 || paperUnits > 0) setChanged();
        return true;
    }

    /** Grant theory once; a second finish cannot duplicate the reward. */
    public boolean finishTheory(ServerPlayer player) {
        // Safety improvement: completing a saved theory requires the
        // ORIGINAL live table and a nearby player, even when invoked by
        // another server-side integration rather than the vanilla menu.
        if (level == null || level.isClientSide || session == null
                || !session.isComplete() || !stillValid(player)) return false;
        session.grantFinishedTheory(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge());
        session = null;
        originalSessionNbt = new CompoundTag();
        setChanged();
        ThaumcraftNetworking.sync(player);
        return true;
    }

    /**
     * BETA26 ContainerResearchTable button 9: abandon an INCOMPLETE theory.
     * Neither the table's two item slots nor the player's inventory is
     * discarded. A completed theory must instead use button 7.
     */
    public boolean abandonTheory(ServerPlayer player) {
        if (level == null || level.isClientSide || session == null
                || session.isComplete() || !stillValid(player)) return false;
        session = null;
        originalSessionNbt = new CompoundTag();
        setChanged();
        return true;
    }

    /**
     * Bugfix for CardScripting and future theory redraws: the original code
     * checks ItemStack != null, consumes ink and paper separately while
     * discarding BOTH boolean results, and can award free theory when a
     * player's paper slot is empty. Minecraft 1.21 additionally represents
     * empty slots by nonnull ItemStack.EMPTY.
     *
     * The port checks BOTH resources before mutating EITHER resource, so a
     * failed attempt costs no ink and no paper. One successful call consumes
     * exactly one sheet and one unit of the 100-use ink supply.
     */
    public boolean consumePaperAndInkFromTable() {
        if (level == null || level.isClientSide || session == null) return false;
        ItemStack ink = items.get(SCRIBING_SLOT);
        ItemStack paper = items.get(PAPER_SLOT);
        if (!ink.is(ThaumcraftItems.SCRIBING_TOOLS.get())
                || !ink.isDamageableItem()
                || ink.getDamageValue() >= ink.getMaxDamage()
                || paper.isEmpty() || !paper.is(Items.PAPER)) return false;

        paper.shrink(1);
        ink.setDamageValue(ink.getDamageValue() + 1);
        setChanged();
        return true;
    }

    /** BETA26 consumeInkFromTable, incrementing damage rather than consuming item. */
    public boolean consumeInkFromTable() {
        ItemStack scribing = items.get(SCRIBING_SLOT);
        if (!scribing.is(ThaumcraftItems.SCRIBING_TOOLS.get())
                || !scribing.isDamageableItem()
                || scribing.getDamageValue() >= scribing.getMaxDamage()) return false;
        scribing.setDamageValue(scribing.getDamageValue() + 1);
        setChanged();
        return true;
    }

    /** BETA26 consumepaperFromTable consumes exactly one paper from slot 1. */
    public boolean consumePaperFromTable() {
        ItemStack paper = items.get(PAPER_SLOT);
        if (!paper.is(Items.PAPER) || paper.isEmpty()) return false;
        paper.shrink(1);
        setChanged();
        return true;
    }

    @Override public int getContainerSize() { return 2; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) {
        return slot >= 0 && slot < 2 ? items.get(slot) : ItemStack.EMPTY;
    }
    @Override public ItemStack removeItem(int slot, int count) {
        if (slot < 0 || slot >= 2 || count <= 0) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= 2) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= 2) return;
        if (!stack.isEmpty() && !canPlaceItem(slot, stack)) return;
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
        if (slot == 0 && stack.is(ThaumcraftItems.SCRIBING_TOOLS.get())) {
            dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("TABLE_SCRIBING_TOOLS_INSERTED");
        } else if (slot == 1 && stack.is(Items.PAPER)) {
            dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("TABLE_PAPER_INSERTED");
        }
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SCRIBING_SLOT -> stack.is(ThaumcraftItems.SCRIBING_TOOLS.get());
            case PAPER_SLOT -> stack.is(Items.PAPER);
            default -> false;
        };
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }
    @Override public void clearContent() {
        for (int i = 0; i < 2; i++) items.set(i, ItemStack.EMPTY);
        setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (session == null) tag.remove("note");
        else tag.put("note", LegacyResearchTableNbt.write(session, originalSessionNbt));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.set(0, ItemStack.EMPTY);
        items.set(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        originalSessionNbt = tag.contains("note", net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? tag.getCompound("note").copy() : new CompoundTag();
        session = tag.contains("note", net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? LegacyResearchTableNbt.read(originalSessionNbt) : null;
    }
}
