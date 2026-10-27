package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemColorRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.UUID;

/** Shared constants only; the client never obtains server entities or SavedData. */
public final class GolemOwnerClientFixture {
    public static final BlockPos MARKER=new BlockPos(7,120,10);
    private GolemOwnerClientFixture() {}
    public static String playerName(int role){return role==0?"M53Alice":"M53Bob";}
    public static UUID worker(int role){return UUID.fromString(role==0?"b60f5d09-ce81-4e34-8f2f-d84bf5b777a0":"b60f5d09-ce81-4e34-8f2f-d84bf5b777a1");}
    public static GolemSeals.SealKey key(int role){return new GolemSeals.SealKey(new BlockPos(role==0?6:8,119,6),Direction.UP);}
    public static byte dyeColor(){return GolemColorRules.fromModernDyeId(DyeColor.RED.getId());}
    public static ItemStack cargo(int role){return cargo(role,role==0?13:17);}
    public static ItemStack cargo(int role,int count){
        var result=new ItemStack(role==0?Items.IRON_INGOT:Items.GOLD_INGOT,count);
        result.set(DataComponents.CUSTOM_NAME,Component.literal(role==0?"M53 Alice actual iron":"M53 Bob actual gold"));
        return result;
    }
}
