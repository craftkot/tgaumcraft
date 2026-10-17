package dev.ehtid.thaumcraft.port.core;

import java.util.List;

/**
 * Core-only conservation regression for the BETA26 processing chain:
 * source item -> basic smelter -> alembic -> tube -> warded jar.
 * This is NOT a Minecraft gameplay test; no world/loader classes participate.
 */
public final class AlchemyTransportCycleParityTest {
    private static int checks;
    private static void eq(Object expected,Object actual) {
        checks++;
        if(!java.util.Objects.equals(expected,actual))
            throw new AssertionError("wanted "+expected+", got "+actual);
    }
    public static void main(String[] args) {
        // The smelter preserves three units and loses two to flux.
        var cooked=LegacySmelterLogic.processUnits(
                LegacySmelterLogic.Tier.BASIC,false,
                new float[]{.0f,.5f,.99f,.2f,.95f});
        eq(3,cooked.stored());
        eq(2,cooked.polluted());
        var held=LegacyAlembicLogic.State.empty();
        // Three ticks of the original two-pass stack allocation.
        int furnacePool=cooked.stored();
        for(int i=0;i<3;i++) {
            var transfer=LegacyAlembicLogic.depositOne(List.of(held),"aer");
            eq(0,transfer.acceptedAt());
            held=transfer.column().get(0);
            furnacePool--;
        }
        eq(0,furnacePool);
        eq(3,held.amount());
        var downstreamJar=LegacyEssentiaJarLogic.State.empty();
        var tube=LegacyEssentiaTubeLogic.Tube.empty();
        for(int i=0;i<3;i++) {
            // DOWN side: next warded jar has 32 suction, tube gets 31.
            var jarNeighbor=new LegacyEssentiaTubeLogic.Neighbour(
                    0,LegacyEssentiaJarLogic.suction(downstreamJar),
                    LegacyEssentiaJarLogic.suctionAspect(downstreamJar),
                    downstreamJar.aspect(),downstreamJar.amount(),
                    LegacyEssentiaJarLogic.minimumSuction(downstreamJar),true,true);
            tube=LegacyEssentiaTubeLogic.calculateSuction(tube,List.of(jarNeighbor));
            eq(31,tube.suction());
            // An alembic supplies one real unit from a different side.
            var source=new LegacyEssentiaTubeLogic.Neighbour(
                    3,0,null,held.aspect(),held.amount(),0,true,true);
            eq(true,LegacyEssentiaTubeLogic.canPull(tube,source));
            var drained=LegacyAlembicLogic.take(held,"aer",1);
            eq(1,drained.transferred());
            held=drained.state();
            tube=LegacyEssentiaTubeLogic.acceptOne(tube,"aer",3);
            eq(1,tube.amount());
            var received=LegacyEssentiaJarLogic.add(downstreamJar,tube.aspect(),1);
            eq(1,received.transferred());
            downstreamJar=received.state();
            tube=LegacyEssentiaTubeLogic.takeOne(tube,"aer",0);
            eq(0,tube.amount());
            eq(3,downstreamJar.amount()+held.amount());
        }
        eq(0,held.amount());
        eq(3,downstreamJar.amount());
        eq("aer",downstreamJar.aspect());
        // A mislabeled warded jar prevents deposit, so the server must NOT
        // debit a source. Void jars accept same-aspect overflow instead.
        var wrongLabel=new LegacyEssentiaJarLogic.State(null,"ignis",0);
        eq(false,LegacyEssentiaJarLogic.accepts(wrongLabel,"aer"));
        var full=new LegacyEssentiaJarLogic.State("aer",null,250);
        var overflow=LegacyVoidJarLogic.add(full,"aer",1);
        eq(1,overflow.consumed());
        eq(1,overflow.destroyed());
        eq(250,overflow.state().amount());
        System.out.println("BETA26 smelter-alembic-tube-jar conservation: OK ("+checks+" assertions)");
    }
}
