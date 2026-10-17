package dev.ehtid.thaumcraft.port.core;

import java.util.Map;
public final class LegacyTubeBufferParityTest {
    private static int n;
    static void eq(Object exp,Object act){
        n++;
        if(!java.util.Objects.equals(exp,act))
            throw new AssertionError("expected "+exp+", got "+act);
    }
    public static void main(String[] args){
        var empty=LegacyTubeBufferLogic.State.empty();
        eq(0,empty.amount());
        eq(1,LegacyTubeBufferLogic.suction(0,0));
        eq(32,LegacyTubeBufferLogic.suction(0,1));
        eq(64,LegacyTubeBufferLogic.suction(0,2));
        eq(1,LegacyTubeBufferLogic.suction(1,5));
        eq(0,LegacyTubeBufferLogic.suction(2,5));
        var state=empty;
        for(int i=0;i<7;i++){
            var next=LegacyTubeBufferLogic.add(state,"aer",1);
            eq(1,next.accepted());state=next.state();
        }
        for(int i=0;i<3;i++){
            var next=LegacyTubeBufferLogic.add(state,"ignis",1);
            eq(1,next.accepted());state=next.state();
        }
        eq(10,state.amount());
        eq(7,state.of("aer"));
        eq(3,state.of("ignis"));
        eq(0,LegacyTubeBufferLogic.add(state,"aer",1).accepted());
        eq(0,LegacyTubeBufferLogic.add(state,"aer",2).accepted());
        var after=LegacyTubeBufferLogic.take(state,"aer",3);
        eq(3,after.accepted());eq(7,after.state().amount());
        eq(0,LegacyTubeBufferLogic.take(after.state(),"aer",5).accepted());
        eq(4,LegacyTubeBufferLogic.take(after.state(),"aer",4).accepted());
        eq(0,LegacyTubeBufferLogic.take(after.state(),"aer",4).state().of("aer"));
        eq(false,LegacyTubeBufferLogic.mayOutputTo(32,1,"aer",Map.of(1,64),
                Map.of(),2));
        eq(true,LegacyTubeBufferLogic.mayOutputTo(64,1,"aer",Map.of(1,32),
                Map.of(),2));
        eq(true,LegacyTubeBufferLogic.mayOutputTo(32,1,"aer",Map.of(1,64),
                Map.of(1,"ignis"),2));
        System.out.println("BETA26 mixed essentia buffer, choke, and priority: OK ("+n+" assertions)");
    }
}
