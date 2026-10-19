package dev.ehtid.thaumcraft.port.core.golem;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** BETA26 GolemProperties material/part costs, merged in original arms/legs/head/addon order. */
public final class GolemAssembly {
    private GolemAssembly() {}

    public record Component(String legacyItemId, int metadata, int count) {}

    private record Key(String id,int meta) {}
    private static Component item(String id,int meta,int n) { return new Component(id,meta,n); }
    private static Component item(String id,int n) { return item(id,0,n); }
    private static Component base(GolemDesign d,int n) {
        return switch(d.material()) {
            case WOOD->item("thaumcraft:plank_greatwood",n);
            case CLAY->item("minecraft:hardened_clay",n);
            case BRASS->item("thaumcraft:plate",0,n);
            case IRON->item("thaumcraft:plate",1,n);
            case THAUMIUM->item("thaumcraft:plate",2,n);
            case VOID->item("thaumcraft:plate",3,n);
        };
    }
    private static Component mech(int n) { return item("thaumcraft:mechanism_simple",n); }
    private static Component module(int meta) { return item("thaumcraft:modules",meta,1); }
    private static void add(Map<Key,Component> rows,Component... components) {
        for(var c:components) rows.merge(new Key(c.legacyItemId(),c.metadata()),c,
                (a,b)->item(a.legacyItemId(),a.metadata(),Math.addExact(a.count(),b.count())));
    }

    /** Rank does not change ingredient payment; vanilla names are the verified 1.12 IDs. */
    public static List<Component> components(GolemDesign design) {
        var rows=new LinkedHashMap<Key,Component>(); add(rows,base(design,2),mech(1));
        switch(design.arms().id()) {
            case 0->{} case 1->add(rows,mech(1),base(design,1));
            case 2->add(rows,module(1),item("minecraft:shears",2),base(design,1));
            case 3->add(rows,item("minecraft:diamond",2),base(design,1),item("minecraft:piston",2));
            case 4->add(rows,module(1),item("minecraft:dispenser",2),item("minecraft:arrow",32),mech(1));
        }
        switch(design.legs().id()) {
            case 0->add(rows,base(design,1),mech(1));
            case 1->add(rows,item("minecraft:bowl",2),item("minecraft:leather",1),mech(1));
            case 2->add(rows,item("minecraft:flint",4),base(design,1),mech(2));
            case 3->add(rows,item("thaumcraft:levitator",1),item("thaumcraft:plate",4),item("minecraft:slime_ball",1),mech(1));
        }
        switch(design.head().id()) {
            case 0->add(rows,item("thaumcraft:mind",0,1));
            case 1->add(rows,item("thaumcraft:mind",1,1));
            case 2->add(rows,item("thaumcraft:mind",1,1),item("thaumcraft:plate",1),base(design,1),item("minecraft:wool",1));
            case 3->add(rows,item("thaumcraft:mind",0,1),module(0));
            case 4->add(rows,item("thaumcraft:mind",1,1),module(0));
        }
        switch(design.addon().id()) {
            case 0->{} case 1->add(rows,base(design,4));
            case 2->add(rows,module(1),mech(1));
            case 3->add(rows,item("minecraft:leather",1),item("minecraft:chest",1));
        }
        return List.copyOf(rows.values());
    }
}
