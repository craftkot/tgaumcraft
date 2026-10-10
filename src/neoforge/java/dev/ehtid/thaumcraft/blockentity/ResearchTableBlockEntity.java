package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.LegacyResearchTableData;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftInspiration;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.scanning.LegacyResearchTableNbt;
import dev.ehtid.thaumcraft.scanning.LegacyTheorycraftChoiceNbt;
import dev.ehtid.thaumcraft.scanning.NeoForgeTheorycraftAidBridge;
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

    public java.util.Optional<LegacyTheorycraftChoiceNbt.Choice> lastDrawChoice() {
        return session == null ? java.util.Optional.empty()
                : LegacyTheorycraftChoiceNbt.readLastDraw(originalSessionNbt);
    }

    /** Server-side state writer. Call only after full card factory validation. */
    public boolean storeCardChoices(java.util.List<LegacyTheorycraftChoiceNbt.Choice> choices) {
        if (level == null || level.isClientSide || session == null) return false;
        java.util.Objects.requireNonNull(choices);
        for (var choice : choices) {
            if (!dev.ehtid.thaumcraft.port.core.LegacyTheorycraftManifest
                    .allCardKeys().contains(choice.key())) return false;
        }
        LegacyTheorycraftChoiceNbt.writeChoices(originalSessionNbt, choices);
        setChanged();
        return true;
    }

    /** Exact original lastDraw shape; null removes the compound entirely. */
    public boolean storeLastDrawChoice(LegacyTheorycraftChoiceNbt.Choice choice) {
        if (level == null || level.isClientSide || session == null) return false;
        if (choice != null && !dev.ehtid.thaumcraft.port.core.LegacyTheorycraftManifest
                .allCardKeys().contains(choice.key())) return false;
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
