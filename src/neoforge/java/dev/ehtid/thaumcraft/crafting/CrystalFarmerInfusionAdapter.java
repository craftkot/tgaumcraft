package dev.ehtid.thaumcraft.crafting;

import dev.ehtid.thaumcraft.blockentity.InfusionMatrixBlockEntity;
import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionCrystalFarmerCatalog;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionStartPlanner;
import dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Actual 1.21.1 ItemStack adapter to the seven BETA26 Crystal Farmer recipes.
 * Reads REAL pedestal inventory and source-era Crystal Essence CUSTOM_DATA.
 *
 * Preview only: the seven BlockCrystal outputs and networked essentia jars
 * are not yet ported. Do NOT grant items or set crafting from this method.
 */
public final class CrystalFarmerInfusionAdapter {
    private CrystalFarmerInfusionAdapter() {}

    private static final AspectRegistry ASPECTS = new AspectRegistry();

    public record Preview(LegacyInfusionCrystalFarmerCatalog.Recipe recipe,
                          LegacyInfusionStartPlanner.Prepared<ItemStack> matching) {
        public Preview {
            if (recipe == null || matching == null)
                throw new IllegalArgumentException("Missing original recipe");
        }
    }

    public static Preview prepare(InfusionMatrixBlockEntity matrix,
                                  PlayerKnowledgeData knowledge) {
        if (matrix == null || knowledge == null || matrix.getLevel() == null
                || matrix.getLevel().isClientSide || !matrix.active()
                || matrix.crafting() || !matrix.validLocation())
            return null;
        var entity = matrix.getLevel().getBlockEntity(matrix.getBlockPos().below(2));
        if (!(entity instanceof PedestalBlockEntity center)) return null;
        ItemStack centerItem = center.displayedItem();
        if (!centerItem.is(ThaumcraftItems.CRYSTAL_ESSENCE.get())) return null;
        String originalAspect = CrystalEssenceItem.legacyAspect(centerItem);
        var spec = LegacyInfusionCrystalFarmerCatalog.forAspect(originalAspect);
        if (spec == null || !CrystalEssenceItem.isOriginalInfusionCrystal(
                centerItem, spec.aspectTag())) return null;
        // The original ItemStack-containing-NBT becomes IngredientNBTTC.
        // Never match another aspect just because Item IDs are identical.
        var original = LegacyInfusionCrystalFarmerCatalog.matcher(
                spec, ASPECTS,
                (ItemStack item) ->
                        CrystalEssenceItem.isOriginalInfusionCrystal(item, spec.aspectTag()),
                (ItemStack item) -> item.is(Items.WHEAT_SEEDS),
                (ItemStack item) -> item.is(ThaumcraftItems.SALIS_MUNDUS.get()));
        var prepared = matrix.prepareInfusionStartFromWorld(knowledge, original);
        return prepared == null ? null : new Preview(spec, prepared);
    }
}
