package dev.ehtid.thaumcraft.port.core;
import java.util.*;
public final class CrystalMeshParityTest {
    public static void main(String[] args) {
        var groups=LegacyCrystalMeshSelection.originalGroups();
        if(groups.size()!=8) throw new AssertionError("eight BETA26 shards");
        String[] seed0={"s014","s026","s017","s018"};
        String[] seed42={"s016","s024","s017","s015"};
        for(int g=0;g<4;g++) {
            var a=LegacyCrystalMeshSelection.select(g,0L);
            var b=LegacyCrystalMeshSelection.select(g,42L);
            if(a.size()!=g+1||b.size()!=g+1) throw new AssertionError("growth");
            if(!a.equals(Arrays.asList(seed0).subList(0,g+1))) throw new AssertionError(a);
            if(!b.equals(Arrays.asList(seed42).subList(0,g+1))) throw new AssertionError(b);
            if(!a.equals(LegacyCrystalMeshSelection.select(g,0L))) throw new AssertionError("seed");
        }
        System.out.println("CrystalModel seeded selection parity OK");
    }
}
