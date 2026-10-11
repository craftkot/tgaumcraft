package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftOptionPools;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftVanillaOptionIds;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Maps selected 1.12 CardMindOverMatter / CardTinker / CardInfuse
 * STATIC option array entries to their real 1.21 ItemStacks.
 *
 * It NEVER retries a different option when a selected original Thaumcraft
 * content item is not yet ported. Retrying would change BETA26 seed behavior
 * and bias the rarity of options.
 *
 * This is only a material mapping bridge, NOT a card initialize/activate
 * factory; full 33-card gameplay still requires prerequisites and subtype NBT.
 */
public final class NeoForgeTheorycraftOriginalOptionResolver {
    private NeoForgeTheorycraftOriginalOptionResolver() {}

    public static ItemStack resolve(LegacyTheorycraftOptionPools.Option option) {
        if (option == null) return ItemStack.EMPTY;
        if (option.kind() == LegacyTheorycraftOptionPools.SourceKind.TC_ITEM) {
            return switch (option.historicalField()) {
                case "ItemsTC.thaumometer" ->
                        new ItemStack(ThaumcraftItems.THAUMOMETER.get());
                case "ItemsTC.salisMundus" ->
                        new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get());
                case "ItemsTC.visResonator" ->
                        new ItemStack(ThaumcraftItems.VIS_RESONATOR.get());
                case "ItemsTC.amber" ->
                        new ItemStack(ThaumcraftItems.AMBER.get());
                case "ItemsTC.quicksilver" ->
                        new ItemStack(ThaumcraftItems.QUICKSILVER.get());
                case "ItemsTC.fabric" ->
                        new ItemStack(ThaumcraftItems.FABRIC.get());
                // Other original Thaumcraft objects do not exist in the
                // current registry. Don't disguise them as a vanilla item.
                default -> ItemStack.EMPTY;
            };
        }
        if (option.kind() == LegacyTheorycraftOptionPools.SourceKind.TC_COLORED_BLOCK)
            return ItemStack.EMPTY; // Yellow Nitor block not yet registered.

        String targetId = LegacyTheorycraftVanillaOptionIds.modernId(option);
        if (targetId == null || option.metadata() != -1) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(targetId);
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    /** Original 18-entry seeded choice, including unported options. */
    public static ItemStack artifice(long seed) {
        return resolve(LegacyTheorycraftOptionPools.artifice(seed));
    }

    public record InfuseSelection(String originalAspectTag, ItemStack required) {}

    /**
     * Original nextInt(compound aspects) precedes nextInt(31 options).
     * No substitute if an original Thaumcraft item isn't ported.
     */
    public static InfuseSelection infuse(AspectRegistry aspects, long seed) {
        var option = LegacyTheorycraftOptionPools.infusion(aspects, seed);
        if (option == null) return null;
        return new InfuseSelection(option.compoundAspect().tag(), resolve(option.requiredItem()));
    }
}
