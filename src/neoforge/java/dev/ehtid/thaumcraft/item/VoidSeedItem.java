package dev.ehtid.thaumcraft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** BETA26 ItemTCBase("void_seed"): a stackable, non-damageable void-native crafting item. */
public final class VoidSeedItem extends Item {
    public VoidSeedItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        // The global EN/RU locale files are owned by concurrent integration PRs.
        // Keep a readable fallback until their coordinated localization update.
        return Component.translatableWithFallback("item.thaumcraft.void_seed", "Void Seed");
    }
}
