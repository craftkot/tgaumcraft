package dev.ehtid.thaumcraft.golem.press;

import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.item.PlateItem;
import dev.ehtid.thaumcraft.port.core.golem.GolemAssembly;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;

/** Exact source component mapping; unknown/unavailable content cannot become a free ingredient. */
public final class GolemComponents {
    private GolemComponents() {}
    public static ItemStack stack(GolemAssembly.Component component) {
        ItemStack result=switch(component.legacyItemId()) {
            case "thaumcraft:plank_greatwood"->new ItemStack(ThaumcraftItems.PLANK_GREATWOOD.get());
            case "thaumcraft:mechanism_simple"->new ItemStack(GolemModule.SIMPLE_MECHANISM.get());
            case "thaumcraft:mind"->new ItemStack(component.metadata()==0?GolemModule.CLOCKWORK_MIND.get():GolemModule.BIOTHAUMIC_MIND.get());
            case "thaumcraft:modules"->new ItemStack(component.metadata()==0?GolemModule.VISION_MODULE.get():GolemModule.AGGRESSION_MODULE.get());
            case "thaumcraft:plate"->PlateItem.create(component.metadata());
            case "minecraft:hardened_clay"->new ItemStack(Items.TERRACOTTA);
            case "minecraft:wool"->new ItemStack(Items.WHITE_WOOL);
            default->BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(component.legacyItemId()))
                    .filter(item->item!=Items.AIR).map(ItemStack::new).orElse(ItemStack.EMPTY);
        };
        if(!result.isEmpty()) result.setCount(component.count()); return result;
    }
    public static boolean matches(ItemStack required,ItemStack actual) {
        if(!required.is(ThaumcraftItems.PLATE.get())) return ItemStack.isSameItemSameComponents(required,actual);
        int meta=PlateItem.legacyMetadata(required);
        if(PlateItem.legacyMetadata(actual)!=meta) return false;
        var normalized=actual.copy();
        var model=normalized.get(DataComponents.CUSTOM_MODEL_DATA);
        if(model!=null && model.value()!=meta) return false;
        normalized.remove(DataComponents.CUSTOM_MODEL_DATA);
        var custom=normalized.get(DataComponents.CUSTOM_DATA);
        if(custom!=null) {
            var tag=custom.copyTag(); tag.remove("LegacyPlateMeta");
            if(tag.isEmpty()) normalized.remove(DataComponents.CUSTOM_DATA);
            else normalized.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        }
        return ItemStack.isSameItemSameComponents(normalized,new ItemStack(ThaumcraftItems.PLATE.get()));
    }
}
