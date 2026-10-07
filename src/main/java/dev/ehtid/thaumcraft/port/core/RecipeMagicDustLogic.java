package dev.ehtid.thaumcraft.port.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exact loader-neutral shape-independent BETA26 RecipeMagicDust matching rules. */
public final class RecipeMagicDustLogic {
    private RecipeMagicDustLogic() {}
    public enum Kind { EMPTY, BOWL, FLINT, REDSTONE, CRYSTAL_ESSENCE, OTHER }
    public record Ingredient(Kind kind, String crystalAspect) {}
    public static boolean canFit(int width, int height) { return width * height >= 6; }
    public static boolean matches(List<Ingredient> grid) {
        int bowl=0, flint=0, redstone=0; Set<String> aspects=new HashSet<>(); int crystals=0;
        for (Ingredient i:grid) {
            if (i==null || i.kind()==Kind.EMPTY) continue;
            switch(i.kind()) {
                case BOWL -> { if (++bowl>1) return false; }
                case FLINT -> { if (++flint>1) return false; }
                case REDSTONE -> { if (++redstone>1) return false; }
                case CRYSTAL_ESSENCE -> { crystals++; if (i.crystalAspect()==null || !aspects.add(i.crystalAspect()) || crystals>3) return false; }
                default -> { return false; }
            }
        }
        return bowl==1 && flint==1 && redstone==1 && crystals==3;
    }
    public static boolean returnsContainer(Kind kind) { return kind==Kind.BOWL || kind==Kind.FLINT; }
}
