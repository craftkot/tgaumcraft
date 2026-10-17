package dev.ehtid.thaumcraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * BETA26 jar item carries its BlockEntity data when mined; show its
 * physical contents and aspect filter instead of hiding them behind a
 * visually indistinguishable empty jar. This is a display-only adapter:
 * no state changes, no essentia creation, no client authority.
 */
public final class EssentiaJarItem extends BlockItem {
    public EssentiaJarItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CustomData payload = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (payload == null || payload.isEmpty()) return;
        CompoundTag data = payload.copyTag();
        String aspect = data.contains("Aspect", Tag.TAG_STRING)
                ? data.getString("Aspect") : "";
        int amount = data.contains("Amount", Tag.TAG_SHORT)
                ? data.getShort("Amount") : data.getInt("Amount");
        if (!aspect.isBlank() && amount > 0 && amount <= 250)
            tooltip.add(Component.translatable("item.thaumcraft.jar.contents",
                            Component.translatable("tc.aspect." + aspect), amount)
                    .withStyle(ChatFormatting.AQUA));
        String filter = data.contains("AspectFilter", Tag.TAG_STRING)
                ? data.getString("AspectFilter") : "";
        if (!filter.isBlank())
            tooltip.add(Component.translatable("item.thaumcraft.jar.filter",
                            Component.translatable("tc.aspect." + filter))
                    .withStyle(ChatFormatting.GRAY));
    }
}
