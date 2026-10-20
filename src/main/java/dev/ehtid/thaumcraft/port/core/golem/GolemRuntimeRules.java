package dev.ehtid.thaumcraft.port.core.golem;

import java.util.LinkedHashSet;
import java.util.Set;

/** The verified working family. Unsupported navigation/combat functions stay gated until ported. */
public final class GolemRuntimeRules {
    private GolemRuntimeRules() {}
    public static boolean groundWorker(GolemDesign d) {
        return d.toLong()>>>48==0 && d.arms().id()<=3 && d.legs().id()<=2;
    }
    public static Set<String> research(GolemDesign d) {
        var keys=new LinkedHashSet<String>(); keys.add(d.material().research());
        keys.addAll(d.head().research()); keys.addAll(d.arms().research());
        keys.addAll(d.legs().research()); keys.addAll(d.addon().research()); return Set.copyOf(keys);
    }
}
