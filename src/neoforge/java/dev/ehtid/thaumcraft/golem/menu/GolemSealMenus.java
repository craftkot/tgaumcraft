package dev.ehtid.thaumcraft.golem.menu;

import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class GolemSealMenus {
    private GolemSealMenus() {}
    private static AbstractContainerMenu create(int id,Player player,ServerLevel level,GolemSeals.Seal seal) {
        return seal.type().logistics()?new GolemLogisticsMenu(id,player.getInventory(),level,seal)
                :new GolemSealMenu(id,player.getInventory(),level,seal);
    }
    public static boolean open(Player player,ServerLevel level,GolemSeals.Seal seal) {
        if(!create(0,player,level,seal).stillValid(player)) return false;
        player.openMenu(new SimpleMenuProvider((id,inv,p)->create(id,p,level,seal),Component.translatable("menu.thaumcraft.golem_seal.title")));
        return true;
    }
}
