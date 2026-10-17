package dev.ehtid.thaumcraft.port.core;

import java.util.List;

/** Independent source tests for BETA26 tube subtype pressure and direction. */
public final class LegacyTubeVariantParityTest {
    private static int assertions;
    private static void eq(Object expect,Object got){
        assertions++;
        if(!java.util.Objects.equals(expect,got))
            throw new AssertionError("expected "+expect+", received "+got);
    }
    private static LegacyEssentiaTubeLogic.Neighbour port(int side,int suction,String suctionType,
                                                           String aspect,int amount){
        return new LegacyEssentiaTubeLogic.Neighbour(
                side,suction,suctionType,aspect,amount,0,true,true);
    }
    public static void main(String[] args){
        var src=LegacyEssentiaTubeLogic.Tube.empty();
        var links=List.of(port(2,64,"aer",null,0),port(3,32,"ignis",null,0));
        eq(63,LegacyEssentiaTubeLogic.calculateVariant(src,links,
                LegacyEssentiaTubeLogic.Variant.NORMAL,null,2).suction());
        eq(32,LegacyEssentiaTubeLogic.calculateVariant(src,links,
                LegacyEssentiaTubeLogic.Variant.RESTRICT,null,2).suction());
        eq("aer",LegacyEssentiaTubeLogic.calculateVariant(src,links,
                LegacyEssentiaTubeLogic.Variant.FILTER,"aer",2).suctionAspect());
        eq(63,LegacyEssentiaTubeLogic.calculateVariant(src,links,
                LegacyEssentiaTubeLogic.Variant.FILTER,"aer",2).suction());
        eq(31,LegacyEssentiaTubeLogic.calculateVariant(src,links,
                LegacyEssentiaTubeLogic.Variant.FILTER,"ignis",2).suction());
        // Facing north => suction only from SOUTH, and no input from SOUTH.
        eq(31,LegacyEssentiaTubeLogic.calculateVariant(src,links,
                LegacyEssentiaTubeLogic.Variant.ONEWAY,null,2).suction());
        var charged=LegacyEssentiaTubeLogic.calculateVariant(src,links,
                LegacyEssentiaTubeLogic.Variant.NORMAL,null,2);
        eq(false,LegacyEssentiaTubeLogic.canPullVariant(charged,
                port(3,0,null,"aer",1),
                LegacyEssentiaTubeLogic.Variant.ONEWAY,null,2));
        eq(true,LegacyEssentiaTubeLogic.canPullVariant(charged,
                port(4,0,null,"aer",1),
                LegacyEssentiaTubeLogic.Variant.ONEWAY,null,2));
        eq(false,LegacyEssentiaTubeLogic.canPullVariant(charged,
                port(4,0,null,"ignis",1),
                LegacyEssentiaTubeLogic.Variant.FILTER,"aer",2));
        System.out.println("BETA26 filter valve restrict oneway suction and direction: OK ("+
                assertions+" assertions)");
    }
}
