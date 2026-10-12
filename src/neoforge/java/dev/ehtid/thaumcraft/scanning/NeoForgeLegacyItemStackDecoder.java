package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.item.CurioItem;
import dev.ehtid.thaumcraft.item.PlateItem;
import dev.ehtid.thaumcraft.item.ThaumcraftIngotItem;
import dev.ehtid.thaumcraft.item.ThaumcraftNuggetItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Explicit loss-avoiding translation of a SMALL, safe subset of original
 * Minecraft 1.12 ItemStack.serializeNBT() into a 1.21 ItemStack.
 *
 * Legacy tags: {id: STRING, Count: BYTE, Damage: SHORT, tag?: COMPOUND}.
 * Arbitrary legacy "tag", metadata, enchantments or item variants are NOT
 * converted using a blind modern ItemStack.parse(), which has different
 * schemas and could accidentally substitute the wrong variant or item.
 *
 * This supports a true unchanged direct-registry ItemStack identity, count
 * 1, damage 0, and no legacy NBT, plus exact Curio variants 0..6.
 * Everything else fails closed; the original
 * compound remains in saved CardChoice.cardNBT for future migration.
 */
public final class NeoForgeLegacyItemStackDecoder {
    private NeoForgeLegacyItemStackDecoder() {}

    public static ItemStack safeDecode(CompoundTag originalStack) {
        if (originalStack == null
                || !originalStack.contains("id", Tag.TAG_STRING)
                || !originalStack.contains("Count", Tag.TAG_BYTE)
                || !originalStack.contains("Damage", Tag.TAG_SHORT)
                || originalStack.contains("tag")
                || originalStack.getByte("Count") != 1)
            return ItemStack.EMPTY;

        String originalId = originalStack.getString("id");
        if (originalId.equals("thaumcraft:curio")) {
            int variant = originalStack.getShort("Damage");
            // BETA26 ItemCurio has seven *item variants*, not durability.
            // Legacy metadata 1..6 must survive the 1.21 component transfer.
            return variant >= 0 && variant < 7
                    ? CurioItem.create(variant) : ItemStack.EMPTY;
        }
        if (originalId.equals("thaumcraft:nugget")) {
            int metadata = originalStack.getShort("Damage");
            return metadata >= 0 && metadata < 11
                    ? ThaumcraftNuggetItem.create(metadata) : ItemStack.EMPTY;
        }
        if (originalId.equals("thaumcraft:ingot")) {
            int metadata = originalStack.getShort("Damage");
            return metadata >= 0 && metadata < 3
                    ? ThaumcraftIngotItem.create(metadata) : ItemStack.EMPTY;
        }
        if (originalId.equals("thaumcraft:plate")) {
            int variant = originalStack.getShort("Damage");
            return variant >= 0 && variant < 4
                    ? PlateItem.create(variant) : ItemStack.EMPTY;
        }
        if (originalStack.getShort("Damage") != 0) return ItemStack.EMPTY;
        ResourceLocation parsed = ResourceLocation.tryParse(originalId);
        if (parsed == null || !parsed.toString().equals(originalId))
            return ItemStack.EMPTY;
        Item item;
        // The 1.12 vanilla wool block had 16 metadata variants. ONLY
        // damage=0 maps to 1.21's white_wool, without guessing colors.
        if (originalId.equals("minecraft:wool")) {
            item = Items.WHITE_WOOL;
        } else if (originalId.equals("thaumcraft:thaumometer")) {
            item = ThaumcraftItems.THAUMOMETER.get();
        } else if (originalId.equals("thaumcraft:salis_mundus")) {
            item = ThaumcraftItems.SALIS_MUNDUS.get();
        } else if (originalId.equals("thaumcraft:vis_resonator")) {
            item = ThaumcraftItems.VIS_RESONATOR.get();
        } else if (originalId.equals("thaumcraft:amber")) {
            item = ThaumcraftItems.AMBER.get();
        } else if (originalId.equals("thaumcraft:quicksilver")) {
            item = ThaumcraftItems.QUICKSILVER.get();
        } else if (originalId.startsWith("thaumcraft:nitor_")
                && dev.ehtid.thaumcraft.port.core.LegacyNitorColors.get(
                        originalId.substring("thaumcraft:nitor_".length())) != null) {
            var variant = dev.ehtid.thaumcraft.port.core.LegacyNitorColors.get(
                    originalId.substring("thaumcraft:nitor_".length()));
            var dye = net.minecraft.world.item.DyeColor.valueOf(
                    variant.modernDye().toUpperCase(java.util.Locale.ROOT));
            item = ThaumcraftItems.NITOR.get(dye).get();
        } else if (originalId.equals("thaumcraft:stone_arcane")) {
            item = ThaumcraftItems.STONE_ARCANE.get();
        } else if (originalId.equals("thaumcraft:stone_ancient")) {
            item = ThaumcraftItems.STONE_ANCIENT.get();
        } else if (originalId.equals("thaumcraft:stone_eldritch_tile")) {
            item = ThaumcraftItems.STONE_ELDRITCH_TILE.get();
        } else if (originalId.equals("thaumcraft:alumentum")) {
            item = ThaumcraftItems.ALUMENTUM.get();
        } else if (originalId.equals("thaumcraft:tallow")) {
            item = ThaumcraftItems.TALLOW.get();
        } else if (originalId.equals("thaumcraft:fabric")) {
            item = ThaumcraftItems.FABRIC.get();
        } else if (parsed.getNamespace().equals("minecraft")) {
            // The same concrete vanilla item ID must exist in 1.21.1.
            // No renaming, item->block or metadata fallbacks.
            item = BuiltInRegistries.ITEM.getOptional(parsed).orElse(Items.AIR);
        } else {
            // Other TC6 or addon items need individually audited mappings.
            return ItemStack.EMPTY;
        }
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
