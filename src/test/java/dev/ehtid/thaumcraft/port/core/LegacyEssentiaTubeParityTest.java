package dev.ehtid.thaumcraft.port.core;

import java.util.List;

public final class LegacyEssentiaTubeParityTest {
    private static int count;
    private static void eq(Object a, Object b) {
        count++;
        if (!java.util.Objects.equals(a, b)) throw new AssertionError("expected " + a + " but got " + b);
    }
    private static LegacyEssentiaTubeLogic.Neighbour neighbor(int side,int suction,String suctionType,
                                                               String aspect,int stored,int min,boolean out) {
        return new LegacyEssentiaTubeLogic.Neighbour(side,suction,suctionType,aspect,stored,min,out,true);
    }
    public static void main(String[] args) {
        var empty = LegacyEssentiaTubeLogic.Tube.empty();
        eq(0, empty.amount()); eq(63, empty.openMask()); eq(0, empty.suction());
        for (int s=0;s<6;s++) eq(s^1, LegacyEssentiaTubeLogic.opposite(s));
        var jarBelow=neighbor(0,64,"aer",null,0,64,false);
        var calc=LegacyEssentiaTubeLogic.calculateSuction(empty,List.of(jarBelow));
        eq(63,calc.suction()); eq("aer",calc.suctionAspect());
        var stronger=neighbor(3,32,null,"aer",250,32,true);
        var withStrong=LegacyEssentiaTubeLogic.calculateSuction(empty,List.of(stronger,jarBelow));
        eq(63,withStrong.suction()); eq("aer",withStrong.suctionAspect());
        eq(true,LegacyEssentiaTubeLogic.canPull(calc,stronger));
        // One free unit of compatible essentia from a transport device.
        var supply=neighbor(3,16,"aer","aer",1,0,true);
        eq(true,LegacyEssentiaTubeLogic.canPull(calc,supply));
        var filled=LegacyEssentiaTubeLogic.acceptOne(calc,"aer",3);
        eq(1,filled.amount()); eq("aer",filled.aspect());
        eq(false,LegacyEssentiaTubeLogic.canPull(filled,supply));
        eq(filled,LegacyEssentiaTubeLogic.acceptOne(filled,"aer",3));
        eq(filled,LegacyEssentiaTubeLogic.takeOne(filled,"ignis",3));
        var discharged=LegacyEssentiaTubeLogic.takeOne(filled,"aer",3);
        eq(0,discharged.amount()); eq(null,discharged.aspect());
        var shut=empty.toggle(3);
        eq(false,shut.open(3));
        eq(false,LegacyEssentiaTubeLogic.canPull(shut,supply));
        eq(shut,LegacyEssentiaTubeLogic.acceptOne(shut,"aer",3));
        eq(empty,shut.toggle(3));
        var noFlux=neighbor(3,63,"ignis",null,0,0,true);
        var vent=LegacyEssentiaTubeLogic.checkVenting(calc,List.of(noFlux));
        eq(40,vent.venting());
        eq(39,LegacyEssentiaTubeLogic.tickVenting(vent).venting());
        var same=neighbor(3,63,"aer",null,0,0,true);
        eq(0,LegacyEssentiaTubeLogic.checkVenting(calc,List.of(same)).venting());
        eq(0,LegacyEssentiaTubeLogic.tickVenting(calc).venting());
        eq(false,LegacyEssentiaTubeLogic.canPull(calc,
                neighbor(3,64,"aer","aer",1,0,true)));
        eq(false,LegacyEssentiaTubeLogic.canPull(calc,
                neighbor(3,16,"aer","ignis",1,0,true)));
        System.out.println("BETA26 essentia tube pressure and one-unit transfer: OK ("+count+" assertions)");
    }
}
