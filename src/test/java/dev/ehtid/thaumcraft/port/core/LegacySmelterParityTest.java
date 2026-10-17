package dev.ehtid.thaumcraft.port.core;
public final class LegacySmelterParityTest {
    static int n;
    static void same(Object a,Object b) {
        n++;
        if (!java.util.Objects.equals(a,b))
            throw new AssertionError("expected "+a+" actual "+b);
    }
    static void near(float a,float b) {
        n++;
        if(Math.abs(a-b)>0.00001f)throw new AssertionError(a+" != "+b);
    }
    public static void main(String[] args) {
        same(256,LegacySmelterLogic.ESSENTIA_CAPACITY);
        near(.8f,LegacySmelterLogic.efficiency(LegacySmelterLogic.Tier.BASIC));
        near(.9f,LegacySmelterLogic.efficiency(LegacySmelterLogic.Tier.THAUMIUM));
        near(.95f,LegacySmelterLogic.efficiency(LegacySmelterLogic.Tier.VOID));
        same(15,LegacySmelterLogic.outputInterval(LegacySmelterLogic.Tier.BASIC));
        same(10,LegacySmelterLogic.outputInterval(LegacySmelterLogic.Tier.THAUMIUM));
        same(15,LegacySmelterLogic.outputInterval(LegacySmelterLogic.Tier.VOID));
        same(20,LegacySmelterLogic.smeltTime(10,0));
        same(15,LegacySmelterLogic.smeltTime(10,2));
        same(true,LegacySmelterLogic.canHold(250,6));
        same(false,LegacySmelterLogic.canHold(250,7));
        var basic=LegacySmelterLogic.processUnits(LegacySmelterLogic.Tier.BASIC,false,
                new float[]{0f,.4f,.8f,.81f,.99f});
        same(3,basic.stored());
        same(2,basic.polluted());
        var flux=LegacySmelterLogic.processUnits(LegacySmelterLogic.Tier.BASIC,true,
                new float[]{.50f,.53f,.8f});
        same(1,flux.stored());
        same(2,flux.polluted());
        same(0,LegacySmelterLogic.processUnits(LegacySmelterLogic.Tier.VOID,false,
                new float[]{}).stored());
        System.out.println("BETA26 smelter retention, speed and capacity: OK ("+n+" assertions)");
    }
}
