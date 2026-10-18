package dev.ehtid.thaumcraft.port.core.golem;

import java.util.List;

/** Confirmed initial construction cost, not a free recipe or a running Golem Press. */
public final class GolemAssembly {
    private GolemAssembly() {}

    public record Component(String legacyItemId, int metadata, int count) {}

    /**
     * GolemProperties.generateComponents: base body 2*base+mech,
     * WALKER adds base+mech, BASIC head adds mind@0; BASIC arms/NONE add nothing.
     * Rank is not an ingredient. Other part costs require a separate verified catalog.
     */
    public static List<Component> components(GolemDesign design) {
        if (design.material() != GolemMaterial.WOOD || design.head().id() != 0
                || design.arms().id() != 0 || design.legs().id() != 0 || design.addon().id() != 0)
            throw new UnsupportedOperationException("Assembly components not yet ported for this construction");
        return List.of(new Component("thaumcraft:plank_greatwood", 0, 3),
                new Component("thaumcraft:mechanism_simple", 0, 2),
                new Component("thaumcraft:mind", 0, 1));
    }
}
