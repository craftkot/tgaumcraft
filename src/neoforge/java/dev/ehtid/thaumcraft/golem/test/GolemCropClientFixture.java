package dev.ehtid.thaumcraft.golem.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.UUID;

/** Immutable IDs and real item template shared by the two isolated TCP probe processes. */
public final class GolemCropClientFixture {
    public static final BlockPos CROP=new BlockPos(6,120,6);
    public static final UUID WORKER=UUID.fromString("b60f5d09-ce81-4e34-8f2f-d84bf5b777f3");
    private GolemCropClientFixture() {}
    public static ItemStack seed(int count){
        var item=BuiltInRegistries.ITEM.get(GolemPlantAdapterItems.id("no_debit"));
        if(item==Items.AIR||item instanceof BlockItem)throw new AssertionError("Actual opt-in non-BlockItem callback seed must be registered on this process");
        var stack=new ItemStack(item,count);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("M52 graphical paid seed"));
        return stack;
    }
}
