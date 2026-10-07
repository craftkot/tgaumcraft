package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Crystal Essence keeps the exact TC6-era {@code Aspects:[{key,amount}]} payload in CUSTOM_DATA.
 * BETA26 assigns a random aspect from the full registered Aspect map, not just the six primals,
 * the first time an aspectless stack ticks server-side or when it is crafted.
 */
public final class CrystalEssenceItem extends Item {
    private static final List<String> BUILTIN_ASPECT_TAGS = new AspectRegistry().values().stream()
            .map(a -> a.tag()).toList();

    public CrystalEssenceItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        assignRandomAspectIfMissing(stack, level);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        assignRandomAspectIfMissing(stack, level);
    }

    public static String legacyAspect(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return null;
        CompoundTag root = data.copyTag();
        if (!root.contains("Aspects", Tag.TAG_LIST)) return null;
        ListTag aspects = root.getList("Aspects", Tag.TAG_COMPOUND);
        if (aspects.isEmpty()) return null;
        CompoundTag row = aspects.getCompound(0);
        if (!row.contains("key", Tag.TAG_STRING)) return null;
        return row.getString("key");
    }

    private static void assignRandomAspectIfMissing(ItemStack stack, Level level) {
        if (level.isClientSide || legacyAspect(stack) != null || BUILTIN_ASPECT_TAGS.isEmpty()) return;
        String aspect = BUILTIN_ASPECT_TAGS.get(level.random.nextInt(BUILTIN_ASPECT_TAGS.size()));
        CompoundTag row = new CompoundTag();
        row.putString("key", aspect);
        row.putInt("amount", 1);
        ListTag list = new ListTag();
        list.add(row);
        CompoundTag root = new CompoundTag();
        root.put("Aspects", list);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }
}
