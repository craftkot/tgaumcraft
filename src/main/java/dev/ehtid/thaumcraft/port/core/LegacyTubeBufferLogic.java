package dev.ehtid.thaumcraft.port.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Original BETA26 TileTubeBuffer semantics:
 * 10 total essentia, multiple aspects, six independently choked ports
 * (0 normal, 1 weak, 2 zero), suction 1 without bellows or 32 per bellows.
 */
public final class LegacyTubeBufferLogic {
    private LegacyTubeBufferLogic(){}
    public static final int CAPACITY=10;

    public record State(Map<String,Integer> stored){
        public State {
            Objects.requireNonNull(stored);
            Map<String,Integer> normalized=new LinkedHashMap<>();
            int total=0;
            for(var entry:stored.entrySet()){
                if(entry.getKey()==null||entry.getKey().isBlank()
                        ||entry.getValue()==null||entry.getValue()<=0)
                    throw new IllegalArgumentException("invalid essentia");
                total+=entry.getValue();
                normalized.put(entry.getKey(),entry.getValue());
            }
            if(total>CAPACITY)throw new IllegalArgumentException("over capacity");
            stored=Collections.unmodifiableMap(normalized);
        }
        public static State empty(){return new State(Map.of());}
        public int amount(){return stored.values().stream().mapToInt(Integer::intValue).sum();}
        public int of(String aspect){return stored.getOrDefault(aspect,0);}
    }
    public record Transfer(State state,int accepted) {}

    public static Transfer add(State state,String aspect,int amount){
        if(state==null||aspect==null||aspect.isBlank()||amount!=1
                ||state.amount()>=CAPACITY)return new Transfer(state,0);
        var map=new LinkedHashMap<>(state.stored());
        map.merge(aspect,1,Integer::sum);
        return new Transfer(new State(map),1);
    }
    public static Transfer take(State state,String aspect,int amount){
        if(state==null||aspect==null||amount<=0||state.of(aspect)<amount)
            return new Transfer(state,0);
        var map=new LinkedHashMap<>(state.stored());
        int remaining=map.get(aspect)-amount;
        if(remaining<=0)map.remove(aspect);
        else map.put(aspect,remaining);
        return new Transfer(new State(map),amount);
    }
    public static int suction(int choke,int bellows){
        if(choke<0||choke>2||bellows<0)throw new IllegalArgumentException();
        if(choke==2)return 0;
        return bellows<=0||choke==1?1:bellows*32;
    }
    /** Source TileTubeBuffer.takeEssentia defers to any stronger compatible drain. */
    public static boolean mayOutputTo(int requestedSuction,int ownSuction,
            String wantedAspect,Map<Integer,Integer> competingSuction,
            Map<Integer,String> competingType,int requesterFace){
        for(var e:competingSuction.entrySet()){
            int face=e.getKey();
            if(face==requesterFace)continue;
            int other=e.getValue();
            String type=competingType.get(face);
            if((type==null||type.equals(wantedAspect))
                    &&requestedSuction<other &&ownSuction<other)
                return false;
        }
        return true;
    }
}
