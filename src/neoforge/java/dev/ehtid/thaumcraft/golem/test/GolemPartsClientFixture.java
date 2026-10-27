package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.item.ThaumcraftIngotItem;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Immutable fixture descriptions shared by isolated real TCP processes. */
public final class GolemPartsClientFixture {
    public static final BlockPos MARKER=new BlockPos(9,119,12);
    public static final BlockPos TABLE=new BlockPos(9,120,14);
    public static final BlockPos SOURCE=new BlockPos(3,119,9);
    public static final BlockPos SINK=new BlockPos(14,120,9);
    public static final String[] RECIPES={"brassplate","thaumiumplate","voidplate"};
    public static final int[] METALS={2,0,1},PLATES={0,2,3};
    private GolemPartsClientFixture() {}
    public static UUID gallery(int index){return UUID.fromString("0b983689-c119-40d3-91ed-00000000000"+index);}
    public static UUID worker(){return UUID.fromString("0b983689-c119-40d3-91ed-000000000010");}
    public static GolemDesign design(int index){return GolemDesign.fromIds(index,index%5,index%5,index%4,index%4,index);}
    public static GolemDesign workerDesign(){return GolemDesign.fromIds(3,1,1,1,3,0);}
    public static ItemStack ingot(int metadata){return ThaumcraftIngotItem.create(metadata);}
}
