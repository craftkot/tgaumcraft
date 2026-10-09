package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Single BETA26 celestial_notes item with thirteen legacy metadata variants.
 * Modern data components retain its original 0 sun, 1..4 stars, 5..12 moons.
 */
public final class CelestialNotesItem extends Item {
    private static final String LEGACY_VARIANT_KEY = "LegacyCelestialMeta";
    private static final String[] VARIANTS = {
            "sun", "stars_1", "stars_2", "stars_3", "stars_4",
            "moon_1", "moon_2", "moon_3", "moon_4", "moon_5",
            "moon_6", "moon_7", "moon_8"
    };

    public CelestialNotesItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(int legacyMetadata) {
        if (legacyMetadata < 0 || legacyMetadata >= VARIANTS.length)
            throw new IllegalArgumentException("legacy celestial metadata " + legacyMetadata);
        ItemStack stack = new ItemStack(ThaumcraftItems.CELESTIAL_NOTES.get());
        CompoundTag nbt = new CompoundTag();
        nbt.putInt(LEGACY_VARIANT_KEY, legacyMetadata);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(legacyMetadata));
        return stack;
    }

    public static int legacyMetadata(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return 0;
        CompoundTag tag = data.copyTag();
        int meta = tag.getInt(LEGACY_VARIANT_KEY);
        return meta < 0 || meta >= VARIANTS.length ? 0 : meta;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.thaumcraft.celestial_notes");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        // Original ItemCelestialNotes has a fixed item display name and the
        // metadata-dependent explanation in aqua text on the tooltip.
        tooltip.add(Component.translatable(
                "item.celestial_notes." + VARIANTS[legacyMetadata(stack)] + ".text")
                .withStyle(ChatFormatting.AQUA));
    }
}
