package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.entity.AlumentumProjectile;
import dev.ehtid.thaumcraft.port.core.LegacyAlumentumCrucibleRecipe;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** BETA26 right-click: consume 1, throw actual projectile, original sound and ballistics. */
public final class AlumentumItem extends Item {
    public AlumentumItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.3F,
                0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            AlumentumProjectile projectile = new AlumentumProjectile(level, player);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(),
                    LegacyAlumentumCrucibleRecipe.THROW_PITCH_OFFSET,
                    LegacyAlumentumCrucibleRecipe.THROW_SPEED,
                    LegacyAlumentumCrucibleRecipe.THROW_INACCURACY);
            level.addFreshEntity(projectile);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
