package dev.ehtid.thaumcraft.port.core;
import java.util.List;
public final class LegacyAlembicParityTest {
    static int n;
    static void same(Object a,Object b) {
        n++;
        if (!java.util.Objects.equals(a,b))
            throw new AssertionError("expected "+a+" actual "+b);
    }
    static LegacyAlembicLogic.State jar(String aspect,int amount,String filter) {
        return new LegacyAlembicLogic.State(aspect,amount,filter,0);
    }
    public static void main(String[] args) {
        var empty=LegacyAlembicLogic.State.empty();
        same(128,LegacyAlembicLogic.CAPACITY);
        same(0,LegacyAlembicLogic.comparator(empty));
        same(false,LegacyAlembicLogic.canOutput(empty,0));
        same(true,LegacyAlembicLogic.canOutput(empty,1));
        var labeled=new LegacyAlembicLogic.State(null,0,"aer",2);
        same(false,LegacyAlembicLogic.canOutput(labeled,2));
        var filled=LegacyAlembicLogic.add(labeled,"aer",200);
        same(128,filled.transferred());
        same(128,filled.state().amount());
        same(15,LegacyAlembicLogic.comparator(filled.state()));
        same(0,LegacyAlembicLogic.add(labeled,"ignis",10).transferred());
        same(0,LegacyAlembicLogic.add(filled.state(),"aer",1).transferred());
        same(0,LegacyAlembicLogic.take(filled.state(),"ignis",1).transferred());
        same(0,LegacyAlembicLogic.take(filled.state(),"aer",129).transferred());
        same(128,LegacyAlembicLogic.take(filled.state(),"aer",128).transferred());
        same(null,LegacyAlembicLogic.take(filled.state(),"aer",128).state().aspect());
        var column=List.of(jar("aer",100,null),empty,jar("ignis",100,null));
        var first=LegacyAlembicLogic.depositOne(column,"aer");
        same(0,first.acceptedAt());
        same(101,first.column().get(0).amount());
        var column2=List.of(jar("aer",128,null),empty,jar("ignis",10,null));
        var second=LegacyAlembicLogic.depositOne(column2,"aer");
        same(1,second.acceptedAt());
        same(1,second.column().get(1).amount());
        var column3=List.of(jar("ignis",25,"ignis"),jar("ordo",128,null));
        same(-1,LegacyAlembicLogic.depositOne(column3,"aer").acceptedAt());
        same(-1,LegacyAlembicLogic.depositOne(List.of(),"aer").acceptedAt());
        same(-1,LegacyAlembicLogic.depositOne(column3,null).acceptedAt());
        same(1,LegacyAlembicLogic.depositOne(
                List.of(jar("ignis",10,"ignis"),labeled),"aer").acceptedAt());
        System.out.println("BETA26 alembic two-pass column and extraction: OK ("+n+" assertions)");
    }
}
