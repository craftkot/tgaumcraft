package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Places the first pickup seal; settings and bell interactions are separate work. */
public final class GolemSealItem extends Item {
    private final GolemSeals.Type type;
    public GolemSealItem(Properties properties) {
        this(properties, GolemSeals.Type.PICKUP);
    }

    public GolemSealItem(Properties properties, GolemSeals.Type type) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos support = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos adjacent = support.relative(face);
        ItemStack held = context.getItemInHand();
        if (!context.getLevel().hasChunkAt(support)
                || !context.getLevel().hasChunkAt(adjacent)
                || !player.mayInteract(context.getLevel(), support)
                || !player.mayUseItemAt(support, face, held)
                || !player.mayUseItemAt(adjacent, face, held)
                || context.getLevel().getBlockState(support).isAir()) {
            return InteractionResult.FAIL;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        GolemSeals seals = GolemSeals.get(level);
        if (player.isShiftKeyDown()) {
            if (!seals.remove(support, face, player.getUUID(), type)) return InteractionResult.FAIL;
            ItemStack returned = new ItemStack(this);
            if (!player.addItem(returned)) player.drop(returned, false);
            player.displayClientMessage(Component.translatable(
                    message("removed")), true);
        } else {
            if (!seals.place(support, face, player.getUUID(), type)) return InteractionResult.FAIL;
            if (!player.getAbilities().instabuild) held.shrink(1);
            player.displayClientMessage(Component.translatable(
                    message("placed"),
                    adjacent.getX(), adjacent.getY(), adjacent.getZ()), true);
        }
        player.getInventory().setChanged();
        return InteractionResult.CONSUME;
    }

    private String message(String action) {
        return "message.thaumcraft.golem.seal." + (type == GolemSeals.Type.FILL ? "fill." : "") + action;
    }
}
