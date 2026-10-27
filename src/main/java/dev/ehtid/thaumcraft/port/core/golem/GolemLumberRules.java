package dev.ehtid.thaumcraft.port.core.golem;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** BETA26 BlockUtils.findBlocks: ordered greedy outward search, not an invented whole-tree flood fill. */
public final class GolemLumberRules {
    private GolemLumberRules() {}
    public static final int RADIUS=2, HORIZONTAL_LIMIT=24, VERTICAL_LIMIT=48;
    public record Pos(int x,int y,int z) { public Pos offset(int x,int y,int z) { return new Pos(this.x+x,this.y+y,this.z+z); } }
    public enum Cell { UNKNOWN, OTHER, MATCH }
    public static boolean eligible(Set<GolemTrait> traits) { return traits.contains(GolemTrait.BREAKER)&&traits.contains(GolemTrait.SMART); }
    /** Unknown cells pause without loading chunks. Source's early boundary return and dx/dy/dz order are retained. */
    public static Optional<Pos> furthest(Pos origin,Function<Pos,Cell> blocks) {
        Pos current=origin; long distance=0;
        search: while(true) {
            for(int x=-RADIUS;x<=RADIUS;x++) for(int y=RADIUS;y>=-RADIUS;y--) for(int z=-RADIUS;z<=RADIUS;z++) {
                Pos next=current.offset(x,y,z); long dx=(long)next.x-origin.x,dy=(long)next.y-origin.y,dz=(long)next.z-origin.z;
                if(Math.abs(dx)>HORIZONTAL_LIMIT || Math.abs(dy)>VERTICAL_LIMIT || Math.abs(dz)>HORIZONTAL_LIMIT) return Optional.of(current);
                Cell cell=blocks.apply(next); if(cell==Cell.UNKNOWN) return Optional.empty();
                long candidate=dx*dx+dy*dy+dz*dz;
                if(cell==Cell.MATCH && candidate>distance) { current=next; distance=candidate; continue search; }
            }
            return Optional.of(current);
        }
    }
}
