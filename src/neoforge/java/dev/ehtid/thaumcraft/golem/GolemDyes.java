package dev.ehtid.thaumcraft.golem;

import dev.ehtid.thaumcraft.port.core.golem.GolemColorRules;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/** Standard c:dyes/color tags replace BETA26's color-specific OreDictionary entries. */
public final class GolemDyes {
    private GolemDyes() {}

    public static byte color(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        // First match follows ConfigAspects.dyes order if a data pack tags one item with several colors.
        // Original OreDictionary registration order is unavailable in the modern tag system.
        for (int id = 15; id >= 0; id--) {
            if (stack.is(DyeColor.byId(id).getTag())) return GolemColorRules.fromModernDyeId(id);
        }
        return 0;
    }
}
