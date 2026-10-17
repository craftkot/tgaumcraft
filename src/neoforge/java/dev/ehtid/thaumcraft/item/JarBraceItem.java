package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Original ItemsTC.jarBrace and BlockJar#onBlockActivated: the iron brace is
 * consumed only after a real server jar accepts it. Filled jar content,
 * aspect, filter, orientation and comparator values are left untouched.
 */
public final class JarBraceItem extends Item {
    public JarBraceItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof EssentiaJarBlockEntity jar)) return InteractionResult.PASS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.mayUseItemAt(context.getClickedPos(),
                context.getClickedFace(), context.getItemInHand()))
            return InteractionResult.FAIL;
        if (jar.blocked()) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!jar.applyBrace()) return InteractionResult.FAIL;
        context.getItemInHand().shrink(1);
        player.getInventory().setChanged();
        return InteractionResult.SUCCESS;
    }
}
