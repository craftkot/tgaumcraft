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
        // Only a live one-unit debit AND matching sink credit constitutes
        // authoritative conservation. Reject false positive/no-op/duplication.
        eq(true,LegacyEssentiaMirrorLogic.conservedOneUnit(12,11,0,1));
        eq(true,LegacyEssentiaMirrorLogic.conservedOneUnit(1,0,63,64));
        eq(true,LegacyEssentiaMirrorLogic.conservedOneUnit(12,11,
                LegacyEssentiaJarLogic.CAPACITY-1,LegacyEssentiaJarLogic.CAPACITY));
        eq(false,LegacyEssentiaMirrorLogic.conservedOneUnit(12,12,0,1));
        eq(false,LegacyEssentiaMirrorLogic.conservedOneUnit(12,11,0,2));
        eq(false,LegacyEssentiaMirrorLogic.conservedOneUnit(12,10,0,1));
        eq(false,LegacyEssentiaMirrorLogic.conservedOneUnit(12,11,0,0));
        eq(false,LegacyEssentiaMirrorLogic.conservedOneUnit(0,-1,0,1));
        eq(false,LegacyEssentiaMirrorLogic.conservedOneUnit(12,11,-1,0));
        // Aligned with live negative mirror world scenarios: a mismatched
        // filter or full jar never qualifies for a one-unit credit.
        var wrongAspect = new LegacyEssentiaJarLogic.State(null,"ignis",0);
        var fullAer = new LegacyEssentiaJarLogic.State("aer","aer",LegacyEssentiaJarLogic.CAPACITY);
        eq(false,LegacyEssentiaJarLogic.accepts(wrongAspect,"aer"));
        eq(0,LegacyEssentiaJarLogic.add(fullAer,"aer",1).transferred());
        eq(false,LegacyEssentiaMirrorLogic.mayRelayCredit(1,true,true,true,
                LegacyEssentiaJarLogic.accepts(wrongAspect,"aer"),"aer",12,0,64));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayCredit(1,true,true,true,
                LegacyEssentiaJarLogic.add(fullAer,"aer",1).transferred()==1,
                "aer",12,0,64));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayCredit(1,true,true,true,
                true,"aer",0,0,64));
        eq(false,LegacyEssentiaMirrorLogic.mayRelayCredit(1,false,true,true,
                true,"aer",12,0,64));

        eq(1,LegacyEssentiaMirrorLogic.TRANSFER_MAX);
        eq(8,LegacyEssentiaMirrorLogic.SEARCH_RANGE);
        // Match actual BETA26 nullFacing(range=8) boundaries, including
        // asymmetric Y. Cached remote sources outside the CURRENT partner's
        // search box must never be reused after an NBT/relink.
        eq(true,LegacyEssentiaMirrorLogic.inPartnerSearchBox(-8,-8,-8));
        eq(true,LegacyEssentiaMirrorLogic.inPartnerSearchBox(8,7,8));
        eq(true,LegacyEssentiaMirrorLogic.inPartnerSearchBox(0,0,1));
        eq(false,LegacyEssentiaMirrorLogic.inPartnerSearchBox(0,0,0));
        eq(false,LegacyEssentiaMirrorLogic.inPartnerSearchBox(0,8,0));
        eq(false,LegacyEssentiaMirrorLogic.inPartnerSearchBox(0,-9,0));
        eq(false,LegacyEssentiaMirrorLogic.inPartnerSearchBox(9,0,0));
        eq(false,LegacyEssentiaMirrorLogic.inPartnerSearchBox(-9,0,0));
        eq(false,LegacyEssentiaMirrorLogic.inPartnerSearchBox(0,0,9));
        eq(false,LegacyEssentiaMirrorLogic.inPartnerSearchBox(0,0,-9));
        // Exhaustively compare the optimization against the ORIGINAL
        // nullFacing source enumeration at every in-box and out-of-box
        // coordinate, not just representative boundary examples.
        var originalScanOffsets = new java.util.HashSet<>(
                LegacyEssentiaSourceOrder.nullFacing(
                        LegacyEssentiaMirrorLogic.SEARCH_RANGE));
        for(int dx=-9;dx<=9;dx++)
            for(int dy=-9;dy<=8;dy++)
                for(int dz=-9;dz<=9;dz++)
                    eq(originalScanOffsets.contains(
                            new LegacyEssentiaSourceOrder.Offset(dx,dy,dz)),
                            LegacyEssentiaMirrorLogic.inPartnerSearchBox(dx,dy,dz));
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
