package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/** BETA26 props/xp payload in 1.21 data components. No free survival assembly recipe. */
public final class GolemPlacerItem extends Item {
    public GolemPlacerItem(Properties properties) { super(properties); }

    public static ItemStack stack(GolemDesign design, int xp) {
        return stack(design.toLong(), xp);
    }

    public static ItemStack stack(long packedDesign, int xp) {
        ItemStack stack = new ItemStack(GolemModule.GOLEM_PLACER.get());
        CompoundTag data = new CompoundTag();
        data.putLong("props", packedDesign);
        data.putInt("xp", Math.max(0, xp));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;
        BlockPos support = context.getClickedPos();
        BlockPos spawn = support.relative(context.getClickedFace());
        var level = context.getLevel();
        if (!level.hasChunkAt(support) || !level.hasChunkAt(spawn)
                || !level.getBlockState(support).isSolid() || !level.getWorldBorder().isWithinBounds(spawn)
                || !player.mayInteract(level, support)
                || !player.mayUseItemAt(spawn, context.getClickedFace(), context.getItemInHand()))
            return InteractionResult.FAIL;
        CompoundTag data = context.getItemInHand().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        GolemDesign design;
        try { design = GolemDesign.fromLong(data.getLong("props")); }
        catch (IllegalArgumentException e) { return InteractionResult.FAIL; }
        if (!GolemModule.supportsRuntime(design)) return InteractionResult.FAIL;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        var golem = GolemModule.GOLEM.get().create(server);
        if (golem == null) return InteractionResult.FAIL;
        golem.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0);
        golem.initialize(design, player.getUUID(), spawn, data.getInt("xp"));
        if (!server.noCollision(golem) || !server.addFreshEntity(golem)) return InteractionResult.FAIL;
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
