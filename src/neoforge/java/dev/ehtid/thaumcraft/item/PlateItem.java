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
 * BETA26 ConfigItems registers a single thaumcraft:plate ItemTCBase with
 * metadata variants 0=brass, 1=iron, 2=thaumium, 3=void.
 * The original 1.12 Damage value becomes 1.21 components, not item damage.
 */
public final class PlateItem extends Item {
    private static final String LEGACY_META = "LegacyPlateMeta";
    private static final String[] NAMES = {"brass", "iron", "thaumium", "void"};

    public PlateItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(int variant) {
        if (variant < 0 || variant >= NAMES.length)
            throw new IllegalArgumentException("unknown BETA26 plate metadata " + variant);
        ItemStack result = new ItemStack(ThaumcraftItems.PLATE.get());
        CompoundTag custom = new CompoundTag();
        custom.putInt(LEGACY_META, variant);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        result.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(variant));
        return result;
    }

    /** Original bare plate ItemStack has Damage=0 (brass). */
    public static int legacyMetadata(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ThaumcraftItems.PLATE.get())) return -1;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(LEGACY_META, Tag.TAG_INT)) return -1;
        int variant = tag.getInt(LEGACY_META);
        return variant >= 0 && variant < NAMES.length ? variant : -1;
    }

    @Override
    public Component getName(ItemStack stack) {
        int variant = legacyMetadata(stack);
        return Component.translatable("item.plate."
                + (variant < 0 ? "brass" : NAMES[variant]) + ".name");
    }
}
