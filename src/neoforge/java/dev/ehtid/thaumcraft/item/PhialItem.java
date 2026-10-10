package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/**
 * Faithful 1.21 ItemStack representation of BETA26 ItemPhial's two metadata
 * variants: 0=empty, 1=filled. ItemPhial.makeFilledPhial(aspect) stores ten
 * points in the exact original AspectList {Aspects:[{key,amount}]} layout.
 *
 * This item currently provides persistent contents and appearance only.
 * The original crucible/jar/phial fill and drain interactions must be ported
 * together with those machines; do not silently permit free conversion.
 */
public final class PhialItem extends Item {
    public static final int CAPACITY = 10;
    private static final AspectRegistry BUILTIN = new AspectRegistry();

    public PhialItem(Properties properties) {
        super(properties);
    }

    public static ItemStack emptyPhial() {
        ItemStack stack = new ItemStack(ThaumcraftItems.PHIAL.get());
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(0));
        return stack;
    }

    /** Original BETA26 ItemPhial.makeFilledPhial(aspect): exactly ten essentia. */
    public static ItemStack filledPhial(String aspectTag) {
        return makePhial(aspectTag, CAPACITY);
    }

    /** Original ItemPhial.makePhial(aspect, amount) for partly filled jars. */
    public static ItemStack makePhial(String aspectTag, int amount) {
        if (aspectTag == null || BUILTIN.get(aspectTag) == null)
            throw new IllegalArgumentException("unregistered BETA26 phial aspect: " + aspectTag);
        if (amount <= 0 || amount > CAPACITY)
            throw new IllegalArgumentException("invalid phial essentia amount: " + amount);
        ItemStack stack = new ItemStack(ThaumcraftItems.PHIAL.get());
        CompoundTag aspect = new CompoundTag();
        aspect.putString("key", aspectTag);
        aspect.putInt("amount", amount);
        ListTag list = new ListTag();
        list.add(aspect);
        CompoundTag content = new CompoundTag();
        content.put("Aspects", list);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(content));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
        return stack;
    }

    /** BETA26 getAspects returns NULL for an empty or malformed phial. */
    public static String containedAspect(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ThaumcraftItems.PHIAL.get())) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return null;
        CompoundTag nbt = data.copyTag();
        if (!nbt.contains("Aspects", Tag.TAG_LIST)) return null;
        ListTag contents = nbt.getList("Aspects", Tag.TAG_COMPOUND);
        if (contents.size() != 1) return null;
        CompoundTag entry = contents.getCompound(0);
        if (!entry.contains("key", Tag.TAG_STRING)
                || !entry.contains("amount", Tag.TAG_INT)
                || entry.getInt("amount") <= 0
                || entry.getInt("amount") > CAPACITY) return null;
        String aspect = entry.getString("key");
        return BUILTIN.get(aspect) == null ? null : aspect;
    }

    /** Exact saved essentia amount (0 for empty or invalid NBT). */
    public static int containedAmount(ItemStack stack) {
        if (containedAspect(stack) == null) return 0;
        return stack.get(DataComponents.CUSTOM_DATA).copyTag()
                .getList("Aspects", Tag.TAG_COMPOUND).getCompound(0).getInt("amount");
    }

    @Override
    public Component getName(ItemStack stack) {
        String aspect = containedAspect(stack);
        return aspect == null
                ? Component.translatable("item.phial.empty.name")
                : Component.translatable("item.phial.filled.name",
                        Component.translatable("tc.aspect." + aspect));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String aspect = containedAspect(stack);
        if (aspect != null) {
            tooltip.add(Component.translatable("tc.aspect." + aspect)
                    .append(" (" + containedAmount(stack) + ")").withStyle(ChatFormatting.AQUA));
        }
    }
}
