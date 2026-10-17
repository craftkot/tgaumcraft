package dev.ehtid.thaumcraft.port.core;

public final class LegacyEssentiaMirrorLogicTest {
    private static int assertions;
    private static void eq(Object expected, Object actual) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("expected: " + expected + ", got: " + actual);
    }

    public static void main(String[] args) {
        var a = new LegacyEssentiaMirrorLogic.Endpoint("minecraft:overworld", 1, 64, 2);
        var b = new LegacyEssentiaMirrorLogic.Endpoint("minecraft:the_nether", -50, 90, 7);
        var c = new LegacyEssentiaMirrorLogic.Endpoint("minecraft:overworld", 5, 64, 3);
        var aToB = new LegacyEssentiaMirrorLogic.Link(b, true);
        var bToA = new LegacyEssentiaMirrorLogic.Link(a, true);
        eq(true, LegacyEssentiaMirrorLogic.reciprocal(a,aToB,b,bToA,true));
        eq(false, LegacyEssentiaMirrorLogic.reciprocal(a,aToB,b,bToA,false));
        eq(false, LegacyEssentiaMirrorLogic.reciprocal(a,new LegacyEssentiaMirrorLogic.Link(b,false),b,bToA,true));
        eq(false, LegacyEssentiaMirrorLogic.reciprocal(a,aToB,b,new LegacyEssentiaMirrorLogic.Link(c,true),true));
        eq(false, LegacyEssentiaMirrorLogic.reciprocal(a,aToB,c,bToA,true));
        eq(false, LegacyEssentiaMirrorLogic.reciprocal(a,new LegacyEssentiaMirrorLogic.Link(a,true),
                a,new LegacyEssentiaMirrorLogic.Link(a,true),true));
        eq(false, LegacyEssentiaMirrorLogic.reciprocal(a,null,b,bToA,true));
        eq(true, LegacyEssentiaMirrorLogic.canRememberDestination(a,b,true));
        eq(false, LegacyEssentiaMirrorLogic.canRememberDestination(a,a,true));
        eq(false, LegacyEssentiaMirrorLogic.canRememberDestination(a,b,false));
        eq(false, LegacyEssentiaMirrorLogic.canRememberDestination(null,b,true));
        eq(false, LegacyEssentiaMirrorLogic.canRememberDestination(a,null,true));
        eq(true, LegacyEssentiaMirrorLogic.canRestore(a,b,true,true,false));
        eq(false, LegacyEssentiaMirrorLogic.canRestore(a,a,true,true,false));
        eq(false, LegacyEssentiaMirrorLogic.canRestore(a,b,false,true,false));
        eq(false, LegacyEssentiaMirrorLogic.canRestore(a,b,true,false,false));
        eq(false, LegacyEssentiaMirrorLogic.canRestore(a,b,true,true,true));

        eq(true,LegacyEssentiaMirrorLogic.mayRelayDebit(1,true,true,true,"aqua",64,"aqua"));
        eq(true,LegacyEssentiaMirrorLogic.mayRelayDebit(1,true,true,true,"aqua",1,null));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayDebit(1,true,true,true,"aqua",1,"ignis"));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayDebit(2,true,true,true,"aqua",2,"aqua"));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayDebit(1,true,false,true,"aqua",1,"aqua"));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayDebit(1,true,true,false,"aqua",1,"aqua"));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayDebit(1,false,true,true,"aqua",1,"aqua"));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayDebit(1,true,true,true,null,1,"aqua"));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayDebit(1,true,true,true,"aqua",0,"aqua"));
                eq(true,LegacyEssentiaMirrorLogic.suppressReturnOutput(true,true,32,64));
        eq(false,LegacyEssentiaMirrorLogic.suppressReturnOutput(true,true,64,64));
        eq(false,LegacyEssentiaMirrorLogic.suppressReturnOutput(false,true,32,64));
        eq(false,LegacyEssentiaMirrorLogic.suppressReturnOutput(true,false,32,64));
        eq(false,LegacyEssentiaMirrorLogic.suppressReturnOutput(true,true,64,32));
        // BETA26 low-level add() deliberately ignores a jar label. The
        // mirror world adapter must validate accepts() separately.
        var aerLabel = new LegacyEssentiaJarLogic.State(null,"aer",0);
        eq(true,LegacyEssentiaJarLogic.accepts(aerLabel,"aer"));
        eq(false,LegacyEssentiaJarLogic.accepts(aerLabel,"ignis"));
        eq(1,LegacyEssentiaJarLogic.add(aerLabel,"ignis",1).transferred());
        eq(false,LegacyEssentiaMirrorLogic.mayRelayCredit(1,true,true,true,
                LegacyEssentiaJarLogic.accepts(aerLabel,"ignis"),"ignis",1,32,64));
                eq(true,LegacyEssentiaMirrorLogic.mayRelayCredit(1,true,true,true,true,"aer",12,32,64));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayCredit(1,true,true,true,true,"aer",12,64,64));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayCredit(1,true,true,true,true,"aer",0,0,64));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayCredit(1,true,true,true,false,"aer",1,0,64));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayCredit(1,true,true,false,true,"aer",1,0,64));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayCredit(1,true,false,true,true,"aer",1,0,64));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayCredit(2,true,true,true,true,"aer",2,0,64));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayCredit(1,true,true,true,true,null,1,0,64));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayCredit(1,false,true,true,true,"aer",1,0,64));
        eq(1,LegacyEssentiaMirrorLogic.TRANSFER_MAX);
        eq(8,LegacyEssentiaMirrorLogic.SEARCH_RANGE);
        eq(true,LegacyEssentiaMirrorLogic.canTransfer(1,true,true,true));
        for(int count:new int[]{0,2,10})
            eq(false,LegacyEssentiaMirrorLogic.canTransfer(count,true,true,true));
        eq(false,LegacyEssentiaMirrorLogic.canTransfer(1,false,true,true));
        eq(false,LegacyEssentiaMirrorLogic.canTransfer(1,true,false,true));
        eq(false,LegacyEssentiaMirrorLogic.canTransfer(1,true,true,false));

        eq(60,LegacyEssentiaMirrorLogic.nextLinkCheckInterval(40,false));
        eq(600,LegacyEssentiaMirrorLogic.nextLinkCheckInterval(600,false));
        eq(600,LegacyEssentiaMirrorLogic.nextLinkCheckInterval(590,false));
        eq(40,LegacyEssentiaMirrorLogic.nextLinkCheckInterval(600,true));
        eq(60,LegacyEssentiaMirrorLogic.nextLinkCheckInterval(0,false));

        eq(0,LegacyEssentiaMirrorLogic.afterTransfer(0,1,false));
        eq(1,LegacyEssentiaMirrorLogic.afterTransfer(0,1,true));
        eq(64,LegacyEssentiaMirrorLogic.afterTransfer(64,0,true));
        eq(65,LegacyEssentiaMirrorLogic.afterTransfer(64,1,true));
        eq(new LegacyEssentiaMirrorLogic.Instability(64,0),
                LegacyEssentiaMirrorLogic.checkInstability(64,1));
        eq(new LegacyEssentiaMirrorLogic.Instability(1,1),
                LegacyEssentiaMirrorLogic.checkInstability(65,1));
        eq(new LegacyEssentiaMirrorLogic.Instability(0,1),
                LegacyEssentiaMirrorLogic.checkInstability(65,100));
        eq(new LegacyEssentiaMirrorLogic.Instability(64,1),
                LegacyEssentiaMirrorLogic.checkInstability(129,100));
        eq(new LegacyEssentiaMirrorLogic.Instability(4,0),
                LegacyEssentiaMirrorLogic.checkInstability(5,100));
        eq(new LegacyEssentiaMirrorLogic.Instability(5,0),
                LegacyEssentiaMirrorLogic.checkInstability(5,99));
        eq(new LegacyEssentiaMirrorLogic.Instability(0,0),
                LegacyEssentiaMirrorLogic.checkInstability(0,100));
        eq(new LegacyEssentiaMirrorLogic.Instability(0,0),
                LegacyEssentiaMirrorLogic.checkInstability(0,0));
        boolean rejected=false;
        try{LegacyEssentiaMirrorLogic.afterTransfer(0,2,true);}
        catch(IllegalArgumentException e){rejected=true;}
        eq(true,rejected);
        rejected=false;
        try{LegacyEssentiaMirrorLogic.checkInstability(-1,1);}
        catch(IllegalArgumentException e){rejected=true;}
        eq(true,rejected);

        System.out.println("BETA26 essentia mirror link and instability parity: OK ("+
                assertions+" assertions)");
    }
}
