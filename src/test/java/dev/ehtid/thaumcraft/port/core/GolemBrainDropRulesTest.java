package dev.ehtid.thaumcraft.port.core;

import dev.ehtid.thaumcraft.port.core.golem.GolemBrainDropRules;

/** EntityEvents.livingDrops bytecode300..415: recent zombie hit, nextInt10-looting<1. */
public final class GolemBrainDropRulesTest {
    public static void main(String[] args) {
        int assertions = 0;
        for (int looting = -1; looting <= 12; looting++) {
            int paidRolls = 0;
            for (int roll = 0; roll < 10; roll++) {
                boolean expected = roll - looting < 1;
                if (GolemBrainDropRules.shouldDrop(true, false, true, looting, roll) != expected)
                    throw new AssertionError("Original chance boundary changed: looting=" + looting + ", roll=" + roll);
                if (expected) paidRolls++;
                if (GolemBrainDropRules.shouldDrop(false, false, true, looting, roll)
                        || GolemBrainDropRules.shouldDrop(true, true, true, looting, roll)
                        || GolemBrainDropRules.shouldDrop(true, false, false, looting, roll))
                    throw new AssertionError("Wrong mob/BrainyZombie/environmental death grants brain");
                assertions += 4;
            }
            if (paidRolls != Math.max(0, Math.min(10, looting + 1))) throw new AssertionError("Wrong complete probability distribution");
            assertions++;
        }
        System.out.println("BETA26 brain acquisition: OK (" + assertions + " assertions)");
    }
}
