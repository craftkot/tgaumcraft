package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.scanning.ThaumometerScanDispatcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Server-authoritative FIRSTSTEPS Thaumometer interaction.
 * Target selection and scan rewards remain in the BETA26 parity dispatcher.
 */
public final class ThaumometerItem extends Item {
    public ThaumometerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.pass(stack);
        boolean scanned = player instanceof ServerPlayer serverPlayer && ThaumometerScanDispatcher.scan(serverPlayer);
        return scanned ? InteractionResultHolder.success(stack) : InteractionResultHolder.pass(stack);
    }
}
