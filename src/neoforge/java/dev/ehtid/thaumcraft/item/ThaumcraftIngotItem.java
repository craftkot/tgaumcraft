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
 * Original BETA26 ItemsTC.ingots: ItemTCBase("ingot",
 * {"thaumium", "void", "brass"}).
 *
 * Preserve the 1.12 Damage metadata via immutable 1.21 item components,
 * not a set of incompatible replacement registry identifiers.
 */
public final class ThaumcraftIngotItem extends Item {
    private static final String META = "LegacyIngotMeta";
    private static final String[] SUBTYPES = {"thaumium", "void", "brass"};

    public ThaumcraftIngotItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(int metadata) {
        if (metadata < 0 || metadata >= SUBTYPES.length)
            throw new IllegalArgumentException("Unknown BETA26 ingot metadata " + metadata);
        ItemStack result = new ItemStack(ThaumcraftItems.INGOT.get());
        CompoundTag nbt = new CompoundTag();
        nbt.putInt(META, metadata);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        result.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(metadata));
        return result;
    }

    public static int legacyMetadata(ItemStack stack) {
        if (!stack.is(ThaumcraftItems.INGOT.get())) return -1;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag nbt = data.copyTag();
        if (!nbt.contains(META, Tag.TAG_INT)) return -1;
        int metadata = nbt.getInt(META);
        return metadata >= 0 && metadata < SUBTYPES.length ? metadata : -1;
    }

    @Override
    public Component getName(ItemStack stack) {
        int metadata = legacyMetadata(stack);
        return Component.translatable("item.ingot."
                + (metadata < 0 ? "thaumium" : SUBTYPES[metadata]) + ".name");
    }
}
