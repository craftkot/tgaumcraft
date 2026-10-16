package dev.ehtid.thaumcraft.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.DistExecutor;

/**
 * Source-era Thaumonomicon item. The playable initial chapter is rendered
 * client-side, but the actual stage transition is validated by the server's
 * FIRSTSTEPS packet handler. It never mutates knowledge from the client.
 */
public final class ThaumonomiconItem extends Item {
    public ThaumonomiconItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> dev.ehtid.thaumcraft.client.FirstStepsBookScreen::open);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
