package dev.ehtid.thaumcraft.golem.inventory;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Return actual callback inventory and menu cursor to their golem, including exceptional exits. */
public final class GolemInteractionInventory {
    private GolemInteractionInventory() {}
    public static void recover(FakePlayer actor,ThaumcraftGolem golem) {
        try {
            var cursor=actor.containerMenu.getCarried();actor.containerMenu.setCarried(ItemStack.EMPTY);
            if(!cursor.isEmpty())golem.retainTransfer(cursor);
            actor.closeContainer();
        } finally {
            for(int i=0;i<actor.getInventory().getContainerSize();i++) {
                var actual=actor.getInventory().removeItemNoUpdate(i);
                if(!actual.isEmpty())golem.retainTransfer(actual);
            }
        }
    }
}
