package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftOptionPools;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftVanillaOptionIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Writes the exact simple BETA26 ItemStack.serializeNBT() shape for a seeded
 * card option AFTER confirming that its equivalent has been ported.
 *
 * Original CardMindOverMatter, CardTinker, CardInfuse choose their option
 * BEFORE serialization. Any missing original mod item is a failed init,
 * not permission to roll a different item or replace it by a random one.
 */
public final class NeoForgeTheorycraftLegacyOptionNbt {
    private NeoForgeTheorycraftLegacyOptionNbt() {}

    public static CompoundTag serialize(LegacyTheorycraftOptionPools.Option option) {
        if (option == null || !safeMetadata(option)) return null;
        ItemStack live = NeoForgeTheorycraftOriginalOptionResolver.resolve(option);
        if (live.isEmpty()) return null;

        String legacyId = switch (option.kind()) {
            case VANILLA_BLOCK, VANILLA_ITEM ->
                    LegacyTheorycraftVanillaOptionIds.modernId(option);
            case TC_ITEM -> switch (option.historicalField()) {
                case "ItemsTC.alumentum" -> "thaumcraft:alumentum";
                // Original CardInfuse chooses thaumium (meta 0) or brass
                // (meta 2). Both are variants of ONE 1.12 registry item.
                case "ItemsTC.ingots" -> "thaumcraft:ingot";
                case "ItemsTC.thaumometer" -> "thaumcraft:thaumometer";
                case "ItemsTC.salisMundus" -> "thaumcraft:salis_mundus";
                case "ItemsTC.visResonator" -> "thaumcraft:vis_resonator";
                case "ItemsTC.amber" -> "thaumcraft:amber";
                case "ItemsTC.quicksilver" -> "thaumcraft:quicksilver";
                case "ItemsTC.fabric" -> "thaumcraft:fabric";
                default -> null;
            };
            case TC_COLORED_BLOCK ->
                    "BlocksTC.nitor".equals(option.historicalField())
                            && "YELLOW".equals(option.variant())
                            && option.metadata() == -1
                            ? "thaumcraft:nitor_yellow" : null;
        };
        if (legacyId == null) return null;
        if (legacyId.equals("minecraft:white_wool")) legacyId = "minecraft:wool";

        CompoundTag original = new CompoundTag();
        original.putString("id", legacyId);
        original.putByte("Count", (byte) 1);
        // Damage remains the original 1.12 variant, never replaced by
        // modern durability or stripped when reconstructing NBT.
        original.putShort("Damage", (short) (option.metadata() == -1
                ? 0 : option.metadata()));
        // Round-trip through our strict loader instead of silently writing
        // a modern stack that cannot later be activated/paid from inventory.
        ItemStack decoded = NeoForgeLegacyItemStackDecoder.safeDecode(original);
        if (decoded.isEmpty() || !ItemStack.isSameItemSameComponents(decoded, live))
            return null;
        return original;
    }

    private static boolean safeMetadata(LegacyTheorycraftOptionPools.Option option) {
        // Only the original ItemsTC.ingots[2] actually uses metadata 2;
        // all other options must retain their original nonvariant identity.
        return option.metadata() == -1
                || option.metadata() == 0
                || option.kind() == LegacyTheorycraftOptionPools.SourceKind.TC_ITEM
                && "ItemsTC.ingots".equals(option.historicalField())
                && option.metadata() == 2;
    }
}
