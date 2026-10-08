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
        // BETA26 always returned SUCCESS for Thaumometer right-click. The server
        // attempted scanTheThing even when the target was null/already scanned;
        // client-side FX/sound are a separate visual follow-up.
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer)
            ThaumometerScanDispatcher.scan(serverPlayer);
        return InteractionResultHolder.success(stack);
    }
}
