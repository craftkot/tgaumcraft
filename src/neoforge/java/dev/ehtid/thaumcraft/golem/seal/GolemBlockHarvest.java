package dev.ehtid.thaumcraft.golem.seal;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;

/** Actual owner-protected platform harvest shared by Breaker and Lumber. No synthetic output stacks. */
final class GolemBlockHarvest {
    private static final Set<UUID> BUSY=new HashSet<>();
    private GolemBlockHarvest() {}
    static boolean harvest(ServerLevel level,ThaumcraftGolem g,BlockPos pos,boolean force) {
        if(!level.hasChunkAt(pos)||!BUSY.add(g.getUUID())) return false;
        var before=level.getBlockState(pos);
        var profile=new GameProfile(g.ownerId()==null?g.getUUID():g.ownerId(),"TCB"+g.getUUID().toString().replace("-","").substring(0,13));
        var actor=FakePlayerFactory.get(level,profile); actor.setGameMode(GameType.SURVIVAL); actor.moveTo(g.position());
        var tool=force?new ItemStack(Items.STICK):ItemStack.EMPTY;
        if(force) tool.set(DataComponents.TOOL,new Tool(List.of(Tool.Rule.minesAndDrops(List.of(before.getBlock()),1)),1,0));
        actor.setItemInHand(InteractionHand.MAIN_HAND,tool);
        try { return actor.gameMode.destroyBlock(pos)&&!level.getBlockState(pos).equals(before); }
        finally {
            actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            for(int i=0;i<actor.getInventory().getContainerSize();i++) { var stack=actor.getInventory().removeItemNoUpdate(i); if(!stack.isEmpty()) g.retainTransfer(stack); }
            BUSY.remove(g.getUUID());
        }
    }
}
