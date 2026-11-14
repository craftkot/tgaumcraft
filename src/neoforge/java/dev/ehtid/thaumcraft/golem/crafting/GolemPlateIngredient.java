package dev.ehtid.thaumcraft.golem.crafting;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.item.ThaumcraftIngotItem;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/** BETA26 plate recipes use ingotBrass/ingotThaumium/ingotVoid, ignoring unrelated stack NBT. */
public record GolemPlateIngredient(Metal metal) implements ICustomIngredient {
    public static final MapCodec<GolemPlateIngredient> CODEC = StringRepresentable.fromEnum(Metal::values)
            .fieldOf("metal").xmap(GolemPlateIngredient::new, GolemPlateIngredient::metal);

    public enum Metal implements StringRepresentable {
        BRASS("brass", 2), THAUMIUM("thaumium", 0), VOID("void", 1);

        private final String name;
        private final int metadata;
        private final TagKey<Item> commonTag;
        private final TagKey<Item> forgeTag;

        Metal(String name, int metadata) {
            this.name = name;
            this.metadata = metadata;
            commonTag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots/" + name));
            forgeTag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "ingots/" + name));
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @Override
    public boolean test(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // One registered native item holds all three metals. Tags must never erase that distinction.
        if (stack.is(ThaumcraftItems.INGOT.get()))
            return ThaumcraftIngotItem.legacyMetadata(stack) == metal.metadata;
        return stack.is(metal.commonTag) || stack.is(metal.forgeTag);
    }

    @Override
    public Stream<ItemStack> getItems() {
        Stream<ItemStack> tagged = Stream.concat(
                        StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(metal.commonTag).spliterator(), false),
                        StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(metal.forgeTag).spliterator(), false))
                .map(holder -> holder.value()).filter(item -> item != ThaumcraftItems.INGOT.get())
                .distinct().map(ItemStack::new);
        return Stream.concat(Stream.of(ThaumcraftIngotItem.create(metal.metadata)), tagged);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return GolemModule.PLATE_INGOT.get();
    }
}
