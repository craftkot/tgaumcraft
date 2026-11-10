#!/usr/bin/env python3
"""Compile the real Salis sparkle caller with API doubles and exercise shape fallback."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/item/SalisMundusSparkles.java"
CORE = ROOT / "src/main/java/dev/ehtid/thaumcraft/port/core/visual"

source_text = SOURCE.read_text(encoding="utf-8")
assert "shape.clip(eye, eye.add(forward.scale(6.0D)), pos)" in source_text
assert "new AABB(pos).clip" not in source_text
assert source_text.count("getBlockState(pos)") == 1

STUBS = {
    "net/minecraft/core/Direction.java": r"""
package net.minecraft.core;
public enum Direction {
    DOWN(0,-1,0), UP(0,1,0), NORTH(0,0,-1), SOUTH(0,0,1), WEST(-1,0,0), EAST(1,0,0);
    private final int x,y,z;
    Direction(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
    public int getStepX(){return x;} public int getStepY(){return y;} public int getStepZ(){return z;}
    public Direction getOpposite(){return switch(this){
        case DOWN -> UP; case UP -> DOWN; case NORTH -> SOUTH;
        case SOUTH -> NORTH; case WEST -> EAST; case EAST -> WEST;
    };}
}
""",
    "net/minecraft/core/BlockPos.java": r"""
package net.minecraft.core;
public record BlockPos(int x,int y,int z) {
    public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;}
    public BlockPos relative(Direction d){return new BlockPos(x+d.getStepX(),y+d.getStepY(),z+d.getStepZ());}
}
""",
    "net/minecraft/world/phys/Vec3.java": r"""
package net.minecraft.world.phys;
import net.minecraft.core.BlockPos;
public final class Vec3 {
    public final double x,y,z;
    public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    public static Vec3 atCenterOf(BlockPos p){return new Vec3(p.x()+.5,p.y()+.5,p.z()+.5);}
    public Vec3 subtract(Vec3 v){return new Vec3(x-v.x,y-v.y,z-v.z);}
    public Vec3 add(Vec3 v){return new Vec3(x+v.x,y+v.y,z+v.z);}
    public Vec3 scale(double s){return new Vec3(x*s,y*s,z*s);}
    public double distanceTo(Vec3 v){double dx=x-v.x,dy=y-v.y,dz=z-v.z;return Math.sqrt(dx*dx+dy*dy+dz*dz);}
}
""",
    "net/minecraft/world/phys/AABB.java": r"""
package net.minecraft.world.phys;
import java.util.Optional;
import net.minecraft.core.BlockPos;
public final class AABB {
    public final double minX,minY,minZ,maxX,maxY,maxZ;
    public AABB(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        this.minX=minX;this.minY=minY;this.minZ=minZ;this.maxX=maxX;this.maxY=maxY;this.maxZ=maxZ;
    }
    public AABB(BlockPos p){this(p.x(),p.y(),p.z(),p.x()+1,p.y()+1,p.z()+1);}
    public double getXsize(){return maxX-minX;} public double getYsize(){return maxY-minY;} public double getZsize(){return maxZ-minZ;}
    // Legacy full-block fallback: a downward ray hits y+1, unlike a half slab.
    public Optional<Vec3> clip(Vec3 start,Vec3 end){
        if(end.y==start.y)return Optional.empty();
        double t=(maxY-start.y)/(end.y-start.y);
        if(t<0||t>1)return Optional.empty();
        return Optional.of(new Vec3(start.x+(end.x-start.x)*t,maxY,start.z+(end.z-start.z)*t));
    }
}
""",
    "net/minecraft/world/phys/HitResult.java": r"""
package net.minecraft.world.phys;
public class HitResult {}
""",
    "net/minecraft/world/phys/BlockHitResult.java": r"""
package net.minecraft.world.phys;
import net.minecraft.core.BlockPos;
public final class BlockHitResult extends HitResult {
    private final BlockPos pos; private final Vec3 location;
    public BlockHitResult(BlockPos pos,Vec3 location){this.pos=pos;this.location=location;}
    public BlockPos getBlockPos(){return pos;} public Vec3 getLocation(){return location;}
}
""",
    "net/minecraft/world/phys/shapes/VoxelShape.java": r"""
package net.minecraft.world.phys.shapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
public final class VoxelShape {
    private final AABB bounds;
    public VoxelShape(AABB bounds){this.bounds=bounds;}
    public boolean isEmpty(){return false;}
    public AABB bounds(){return bounds;}
    // Exact top-face hit for the half-height slab fixture.
    public BlockHitResult clip(Vec3 start,Vec3 end,BlockPos pos){
        double y=pos.y()+bounds.maxY;
        double t=(y-start.y)/(end.y-start.y);
        if(t<0||t>1)return null;
        return new BlockHitResult(pos,new Vec3(start.x+(end.x-start.x)*t,y,start.z+(end.z-start.z)*t));
    }
}
""",
    "net/minecraft/util/RandomSource.java": r"""
package net.minecraft.util;
public final class RandomSource {
    public double nextGaussian(){return 0.0D;}
    public int nextInt(int bound){return 0;}
}
""",
    "net/minecraft/world/InteractionHand.java": r"""
package net.minecraft.world;
public enum InteractionHand { MAIN_HAND, OFF_HAND }
""",
    "net/minecraft/core/particles/SimpleParticleType.java": r"""
package net.minecraft.core.particles;
public final class SimpleParticleType {}
""",
    "net/minecraft/server/level/ServerLevel.java": r"""
package net.minecraft.server.level;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
public final class ServerLevel {
    public final RandomSource random=new RandomSource();
    private final BlockState state;
    public ServerLevel(BlockState state){this.state=state;}
    public boolean hasChunkAt(BlockPos pos){return true;}
    public BlockState getBlockState(BlockPos pos){return state;}
}
""",
    "net/minecraft/server/level/ServerPlayer.java": r"""
package net.minecraft.server.level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
public final class ServerPlayer {
    public double getX(){return 10.5D;} public double getZ(){return 30.5D;}
    public float getYRot(){return 0.0F;} public double getBbHeight(){return 1.8D;}
    public AABB getBoundingBox(){return new AABB(10,20,30,11,21.8,31);}
    public Vec3 getLookAngle(){return new Vec3(0,-1,0);}
    public Vec3 getEyePosition(){return new Vec3(10.5,22,30.5);}
    public HitResult pick(double reach,float partial,boolean fluids){
        return new BlockHitResult(new BlockPos(99,99,99),new Vec3(99,99,99));
    }
}
""",
    "net/minecraft/world/level/block/state/BlockState.java": r"""
package net.minecraft.world.level.block.state;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.VoxelShape;
public final class BlockState {
    private final VoxelShape shape;
    public BlockState(VoxelShape shape){this.shape=shape;}
    public VoxelShape getShape(ServerLevel level,BlockPos pos){return shape;}
    public boolean isSolidRender(ServerLevel level,BlockPos pos){return false;}
    public boolean isFaceSturdy(ServerLevel level,BlockPos pos,Direction face){return false;}
}
""",
    "dev/ehtid/thaumcraft/registry/SalisSparkleParticleRegistration.java": r"""
package dev.ehtid.thaumcraft.registry;
import net.minecraft.core.particles.SimpleParticleType;
public final class SalisSparkleParticleRegistration {
    public static final SimpleParticleType TYPE=new SimpleParticleType();
}
""",
    "dev/ehtid/thaumcraft/util/ThaumcraftSmokeAudit.java": r"""
package dev.ehtid.thaumcraft.util;
public final class ThaumcraftSmokeAudit {public static void mark(String marker){}}
""",
    "dev/ehtid/thaumcraft/item/SalisSparkleTrailQueue.java": r"""
package dev.ehtid.thaumcraft.item;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
public final class SalisSparkleTrailQueue {
    public static final List<Integer> faceDelays=new ArrayList<>();
    public static void emitOrQueue(ServerLevel l,ServerPlayer p,SimpleParticleType t,Vec3 o,Vec3 v,int d){}
    public static void emitFaceOrQueue(ServerLevel l,ServerPlayer p,SimpleParticleType t,Vec3 o,Vec3 v,int d){faceDelays.add(d);}
}
""",
    "test/SalisSparkleHitpointFallbackTest.java": r"""
package test;
import dev.ehtid.thaumcraft.item.SalisMundusSparkles;
import dev.ehtid.thaumcraft.item.SalisSparkleTrailQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
public final class SalisSparkleHitpointFallbackTest {
    public static void main(String[] args){
        BlockPos pos=new BlockPos(10,20,30);
        // A slab occupies y=0..0.5. The player ray deliberately misses the
        // clicked block, so production must fall back to VoxelShape.clip.
        VoxelShape slab=new VoxelShape(new AABB(0,0,0,1,.5,1));
        ServerLevel level=new ServerLevel(new BlockState(slab));
        ServerPlayer player=new ServerPlayer();
        SalisMundusSparkles.sendToActor(level,player,pos,InteractionHand.MAIN_HAND);
        if(SalisSparkleTrailQueue.faceDelays.size()!=192)
            throw new AssertionError("expected 6 * 32 exposed-face particles, got "+SalisSparkleTrailQueue.faceDelays.size());
        // Direction order is DOWN then UP; the first UP particle is exactly at
        // the shape-clipped hitpoint (10.5,20.5,30.5), so its delay is zero.
        int upDelay=SalisSparkleTrailQueue.faceDelays.get(32);
        if(upDelay!=0) throw new AssertionError("half-slab top hitpoint was not used; UP delay="+upDelay);
        System.out.println("SALIS_SHAPE_AWARE_HITPOINT_FALLBACK=PASS upDelay="+upDelay+" faceParticles=192");
    }
}
"""
}

with tempfile.TemporaryDirectory(prefix="salis-hitpoint-fallback-") as tmp:
    root = Path(tmp)
    for rel, code in STUBS.items():
        target = root / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(code, encoding="utf-8")
    classes = root / "classes"
    classes.mkdir()
    production = [
        SOURCE,
        CORE / "SalisSparkleFaceLayout.java",
        CORE / "SalisSparkleHandOrigin.java",
        CORE / "SalisSparkleFaceDelayRules.java",
    ]
    java_files = [str(p) for p in root.rglob("*.java")] + [str(p) for p in production]
    subprocess.run(["javac", "--release", "21", "-d", str(classes), *java_files], check=True)
    subprocess.run(["java", "-cp", str(classes), "test.SalisSparkleHitpointFallbackTest"], check=True)
