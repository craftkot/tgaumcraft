package dev.ehtid.thaumcraft.port.core;
import java.util.*;
/** BETA26 CrystalModel: each supported face shuffles eight OBJ parts
 * using a newly seeded java.util.Random, then selects growth+1 parts.
 * Actual six-face baked model wiring remains TODO.
 */
public final class LegacyCrystalMeshSelection {
    private LegacyCrystalMeshSelection() {}
    private static final List<String> PARTS=List.of("s014","s015","s016","s017","s018","s019","s024","s026");
    public static List<String> originalGroups() { return PARTS; }
    public static List<String> select(int growth,long seed) {
        if(growth<0||growth>3) throw new IllegalArgumentException("growth");
        ArrayList<Integer> shuffled=new ArrayList<>();
        for(int i=0;i<8;i++) shuffled.add(i);
        Collections.shuffle(shuffled,new Random(seed));
        ArrayList<String> output=new ArrayList<>();
        for(int i=0;i<=growth;i++) output.add(PARTS.get(shuffled.get(i)));
        return List.copyOf(output);
    }
}
