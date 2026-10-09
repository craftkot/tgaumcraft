package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.LegacyResearchTableData;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftInspiration;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.scanning.LegacyResearchTableNbt;
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

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) {
        super(ThaumcraftBlockEntities.RESEARCH_TABLE.get(), pos, state);
    }

    public LegacyResearchTableData session() { return session; }

    /**
     * ResearchTableData.initialize's current verified slice. The complete
     * card-selection UI will call this only after the player has chosen aides.
     */
    public boolean startNewTheory(ServerPlayer player, Set<String> aids) {
        Objects.requireNonNull(aids, "aids");
        if (level == null || level.isClientSide || session != null) return false;
        session = LegacyTheorycraftInspiration.start(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),
                BuiltInResearchCatalog.create(), aids);
        session.setPlayer(player.getGameProfile().getName());
        originalSessionNbt = new CompoundTag();
        setChanged();
        return true;
    }

    /** Grant theory once; a second finish cannot duplicate the reward. */
    public boolean finishTheory(ServerPlayer player) {
        if (level == null || level.isClientSide || session == null || !session.isComplete())
            return false;
        session.grantFinishedTheory(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge());
        session = null;
        originalSessionNbt = new CompoundTag();
        setChanged();
        ThaumcraftNetworking.sync(player);
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
