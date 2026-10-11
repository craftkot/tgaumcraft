package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Original BETA26 ItemTCBase("nugget") has eleven metadata variants.
 * The vis crystal catalyst "nuggetQuartz" is exactly metadata 9.
 */
public final class ThaumcraftNuggetItem extends Item {
    private static final String META = "LegacyNuggetMeta";
    private static final String[] NAMES = {
            "iron", "copper", "tin", "silver", "lead", "quicksilver",
            "thaumium", "void", "brass", "quartz", "rareearth"
    };

    public ThaumcraftNuggetItem(Properties properties) { super(properties); }

    public static ItemStack create(int metadata) {
        if (metadata < 0 || metadata >= NAMES.length)
            throw new IllegalArgumentException("BETA26 nugget variant " + metadata);
        ItemStack result = new ItemStack(ThaumcraftItems.NUGGET.get());
        CompoundTag tag = new CompoundTag();
        tag.putInt(META, metadata);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        result.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(metadata));
        return result;
    }

    public static int legacyMetadata(ItemStack stack) {
        if (stack == null || !stack.is(ThaumcraftItems.NUGGET.get())) return -1;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag nbt = data.copyTag();
        if (!nbt.contains(META, Tag.TAG_INT)) return -1;
        int value = nbt.getInt(META);
        return value >= 0 && value < NAMES.length ? value : -1;
    }

    @Override public Component getName(ItemStack stack) {
        int variant = legacyMetadata(stack);
        return Component.translatable("item.nugget."
                + NAMES[variant < 0 ? 0 : variant] + ".name");
    }
}
