package dev.ehtid.thaumcraft.port.core;

import dev.ehtid.thaumcraft.port.core.LegacySmelterAttachmentArcaneRecipe.Kind;

public final class LegacySmelterAttachmentArcaneRecipeTest {
    private static int assertions;
    private static void ok(boolean valid, String detail) {
        assertions++;
        if (!valid) throw new AssertionError(detail);
    }

    private static boolean matches(Kind kind, boolean[][] ingredients) {
        return LegacySmelterAttachmentArcaneRecipe.matches(kind,
                ingredients[0], ingredients[1], ingredients[2], ingredients[3],
                ingredients[4], ingredients[5], ingredients[6]);
    }

    private static boolean[][] fixture(Kind kind) {
        boolean[][] f = new boolean[7][9];
        String[] pattern = kind == Kind.AUX
                ? new String[]{"WTW","RGR","IBI"} : new String[]{"IBI","MGF","IBI"};
        for(int slot = 0; slot < 9; slot++) {
            char symbol = pattern[slot/3].charAt(slot%3);
            int index = switch(symbol) {
                case 'W' -> 0;
                case 'T' -> 1;
                case 'R' -> 2;
                case 'I' -> 3;
                case 'G' -> 4;
                case 'B' -> kind == Kind.AUX ? 5 : 2;
                case 'M', 'F' -> 6;
                default -> throw new IllegalArgumentException("bad fixture");
            };
            f[index][slot] = true;
        }
        return f;
    }

    public static void main(String[] args) {
        ok(Kind.AUX.research.equals("IMPROVEDSMELTING"),"Aux research");
        ok(Kind.VENT.research.equals("IMPROVEDSMELTING2"),"Vent research");
        ok(Kind.AUX.vis == 100 && Kind.VENT.vis == 150,"vis");
        ok(Kind.AUX.crystalMask == 0b001001 && Kind.VENT.crystalMask == 0b000001,"crystals");
        ok(LegacySmelterAttachmentArcaneRecipe.AER_SLOT==9, "Aer slot");
        ok(LegacySmelterAttachmentArcaneRecipe.TERRA_SLOT==12, "Terra slot");

        for(Kind kind : Kind.values()) {
            boolean[][] f = fixture(kind);
            ok(matches(kind,f),"correct recipe "+kind);
            for(int ingredient=0; ingredient<f.length; ingredient++) {
                for(int slot=0; slot<9; slot++) {
                    if(!f[ingredient][slot])continue;
                    f[ingredient][slot]=false;
                    ok(!matches(kind,f),"missing ingredient "+kind+" "+slot);
                    f[ingredient][slot]=true;
                }
            }
            ok(!matches(kind,new boolean[7][8]),"invalid array "+kind);
            ok(!LegacySmelterAttachmentArcaneRecipe.matches(kind,
                    null,f[1],f[2],f[3],f[4],f[5],f[6]),"null grid "+kind);
        }

        boolean[][] aux=fixture(Kind.AUX);
        aux[5][7]=false;aux[2][7]=true;
        ok(!matches(Kind.AUX,aux),"must use bellows, not brass, in AUX B slot");
        boolean[][] vent=fixture(Kind.VENT);
        vent[2][1]=false;vent[5][1]=true;
        ok(!matches(Kind.VENT,vent),"must use brass, not bellows, in VENT B slot");
        ok(LegacySmelterAttachmentArcaneRecipe.discountedVis(Kind.AUX,0.0F)==100,"aux cost");
        ok(LegacySmelterAttachmentArcaneRecipe.discountedVis(Kind.VENT,0.0F)==150,"vent cost");
        ok(LegacySmelterAttachmentArcaneRecipe.discountedVis(Kind.AUX,0.25F)==75,"discount");
        ok(LegacySmelterAttachmentArcaneRecipe.discountedVis(Kind.VENT,1.0F)==0,"zero");
        System.out.println("BETA26 smelter attachment arcane crafting: OK ("+assertions+" assertions)");
    }
}
