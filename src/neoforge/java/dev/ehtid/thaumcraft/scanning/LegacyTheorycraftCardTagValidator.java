package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftCardFields;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftManifest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates only the original BETA26 NBT structure, not player eligibility
 * or actual item availability. Unknown keys remain untouched in saved NBT.
 * A legacy 1.12 ItemStack compound is accepted RAW (not decoded as 1.21);
 * material identity conversion is a separate required gameplay task.
 */
public final class LegacyTheorycraftCardTagValidator {
    private LegacyTheorycraftCardTagValidator() {}

    public static List<String> invalidFields(String cardKey, CompoundTag data) {
        if (cardKey == null || !LegacyTheorycraftManifest.allCardKeys().contains(cardKey))
            return List.of("unregistered card");
        if (data == null) return List.of("cardNBT");
        ArrayList<String> invalid = new ArrayList<>();
        if (!data.contains("seed", Tag.TAG_LONG)) invalid.add("seed");
        for (LegacyTheorycraftCardFields.Field field
                : LegacyTheorycraftCardFields.additionalFields(cardKey)) {
            int expected = switch (field.type()) {
                case STRING -> Tag.TAG_STRING;
                case INT -> Tag.TAG_INT;
                case LONG -> Tag.TAG_LONG;
                case ITEM_STACK -> Tag.TAG_COMPOUND;
            };
            if (!data.contains(field.key(), expected)) invalid.add(field.key());
        }
        return List.copyOf(invalid);
    }

    /**
     * Generation-time guard: every draw result must have the original seed
     * and each subclass's actual named fields. Do not interpret the result as
     * an item-availability or card-activation authorization.
     */
    public static boolean structurallyValid(String cardKey, CompoundTag data) {
        return invalidFields(cardKey, data).isEmpty();
    }
}
