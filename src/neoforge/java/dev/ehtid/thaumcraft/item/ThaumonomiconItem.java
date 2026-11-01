package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.registry.ThaumcraftSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

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
            // BETA26 ItemThaumonomicon#onItemRightClick: play the original
            // randomized page sound locally at the player's position, then open.
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    ThaumcraftSounds.PAGE.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
            ClientOnly.open();
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
    /** This nested class is not loaded by the dedicated server JVM. */
    private static final class ClientOnly {
        static void open() {
            dev.ehtid.thaumcraft.client.FirstStepsBookScreen.open();
        }
    }

}
