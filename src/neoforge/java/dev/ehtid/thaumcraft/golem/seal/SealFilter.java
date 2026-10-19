package dev.ehtid.thaumcraft.golem.seal;

import dev.ehtid.thaumcraft.port.core.golem.SealFilterRules;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** One ghost slot, as in basic Pickup/Fill. Strict default pmeta/pnbt mapped to components. */
public final class SealFilter {
    private final ItemStack item;
    private final boolean blacklist;
    private final int limit;
    private final boolean matchNbt;

    public SealFilter(ItemStack item, boolean blacklist, int limit) {
        this(item, blacklist, limit, true);
    }

    public SealFilter(ItemStack item, boolean blacklist, int limit, boolean matchNbt) {
        this.item = item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
        this.blacklist = blacklist;
        this.limit = Math.max(0, limit);
        this.matchNbt = matchNbt;
    }

    public static SealFilter defaults() { return new SealFilter(ItemStack.EMPTY, true, 0); }
    public ItemStack item() { return item.copy(); }
    public boolean blacklist() { return blacklist; }
    public int limit() { return limit; }
    public boolean matchNbt() { return matchNbt; }
    public SealFilter withItem(ItemStack next) { return new SealFilter(next, blacklist, limit, matchNbt); }
    public SealFilter withBlacklist(boolean next) { return new SealFilter(item, next, limit, matchNbt); }
    public SealFilter withLimit(int next) { return new SealFilter(item, blacklist, next, matchNbt); }
    public SealFilter withMatchNbt(boolean next) { return new SealFilter(item, blacklist, limit, next); }
    public boolean matches(ItemStack left, ItemStack right) {
        return !left.isEmpty() && !right.isEmpty() && SealFilterRules.matches(left.is(right.getItem()),
                left.getDamageValue() == right.getDamageValue(), ItemStack.isSameItemSameComponents(left, right), matchNbt);
    }
    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && SealFilterRules.accepts(blacklist, !item.isEmpty(),
                matches(item, stack));
    }
    public int transferable(ItemStack carried, long existing) {
        return accepts(carried) ? SealFilterRules.transferable(carried.getCount(), blacklist, limit, existing) : 0;
    }

    public void save(CompoundTag row, HolderLookup.Provider registries) {
        row.putBoolean("bl", blacklist);
        row.putBoolean("pnbt", matchNbt);
        ListTag items = new ListTag();
        if (!item.isEmpty()) {
            CompoundTag entry = (CompoundTag) item.save(registries);
            entry.putByte("Slot", (byte) 0);
            items.add(entry);
        }
        row.put("Items", items);
        ListTag sizes = new ListTag();
        CompoundTag size = new CompoundTag();
        size.putByte("Slot", (byte) 0);
        size.putInt("Size", limit);
        sizes.add(size);
        row.put("Sizes", sizes);
    }

    public static SealFilter load(CompoundTag row, HolderLookup.Provider registries) {
        ItemStack item = ItemStack.EMPTY;
        ListTag items = row.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            if (entry.getByte("Slot") == 0) { item = ItemStack.parseOptional(registries, entry); break; }
        }
        int limit = 0;
        ListTag sizes = row.getList("Sizes", Tag.TAG_COMPOUND);
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.getCompound(i).getByte("Slot") == 0) { limit = sizes.getCompound(i).getInt("Size"); break; }
        }
        return new SealFilter(item, !row.contains("bl") || row.getBoolean("bl"), limit,
                !row.contains("pnbt") || row.getBoolean("pnbt"));
    }
}
