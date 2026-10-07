package dev.ehtid.thaumcraft.port.core;

/** Exact durability/name/crafting-remainder thresholds from ItemPrimordialPearl BETA26. */
public final class PrimordialPearlLogic {
    public static final int MAX_DAMAGE = 8;

    private PrimordialPearlLogic() {}

    public enum Form {
        PEARL,
        NODULE,
        MOTE
    }

    public static Form formForDamage(int damage) {
        if (damage < 3) return Form.PEARL;
        if (damage < 6) return Form.NODULE;
        return Form.MOTE;
    }

    public static boolean hasCraftingRemainder(int damage) {
        return damage < 7;
    }

    /** Returns next damage, or -1 when the crafting ingredient is consumed completely. */
    public static int craftingRemainderDamage(int damage) {
        return hasCraftingRemainder(damage) ? damage + 1 : -1;
    }
}
