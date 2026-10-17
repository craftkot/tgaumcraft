package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/**
 * The original BETA26 ItemLabel has two metadata states:
 * blank = 0, one-aspect imprint = 1. Labels don't add/remove essentia.
 */
public final class JarLabelItem extends Item {
    private static final AspectRegistry ASPECTS = new AspectRegistry();

    public JarLabelItem(Properties properties) { super(properties); }

    public static ItemStack filled(String tag) {
        if (tag == null || ASPECTS.get(tag) == null)
            throw new IllegalArgumentException("Invalid original label aspect: " + tag);
        ItemStack stack = new ItemStack(ThaumcraftItems.JAR_LABEL.get());
        CompoundTag entry = new CompoundTag();
        entry.putString("key", tag);
        entry.putInt("amount", 1);
        ListTag list = new ListTag();
        list.add(entry);
        CompoundTag nbt = new CompoundTag();
        nbt.put("Aspects", list);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
        return stack;
    }

    public static String selectedAspect(ItemStack stack) {
        if (stack == null || !stack.is(ThaumcraftItems.JAR_LABEL.get())) return null;
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || custom.isEmpty()) return null;
        CompoundTag data = custom.copyTag();
        if (!data.contains("Aspects", Tag.TAG_LIST)) return null;
        ListTag list = data.getList("Aspects", Tag.TAG_COMPOUND);
        if (list.size() != 1) return null;
        CompoundTag element = list.getCompound(0);
        String aspect = element.getString("key");
        return element.getInt("amount") == 1 && ASPECTS.get(aspect) != null ? aspect : null;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof EssentiaJarBlockEntity jar)) return InteractionResult.PASS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.mayUseItemAt(context.getClickedPos(),
                context.getClickedFace(), context.getItemInHand()))
            return InteractionResult.FAIL;
        CustomData data = context.getItemInHand().get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().contains("Aspects")
                && selectedAspect(context.getItemInHand()) == null)
            return InteractionResult.PASS;
        String requested = selectedAspect(context.getItemInHand());
        if (jar.snapshot().filter() != null
                || (jar.snapshot().amount() == 0 && requested == null))
            return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!jar.applyLabel(requested, player.getDirection().getOpposite()))
            return InteractionResult.FAIL;
        context.getItemInHand().shrink(1);
        player.getInventory().setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override public Component getName(ItemStack stack) {
        String aspect = selectedAspect(stack);
        return aspect == null
                ? Component.translatable("item.thaumcraft.label")
                : Component.translatable("item.thaumcraft.label.filled",
                        Component.translatable("tc.aspect." + aspect));
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                           List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String aspect = selectedAspect(stack);
        if (aspect != null) tooltip.add(Component.translatable("tc.aspect." + aspect)
                .withStyle(ChatFormatting.AQUA));
    }
}
