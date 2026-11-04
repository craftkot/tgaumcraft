#!/usr/bin/env python3
"""Execute the real NeoForge Salis queue against minimal API doubles.

This is a JVM unit regression, NOT a Minecraft GameTest or client smoke.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
QUEUE = ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/item/SalisSparkleTrailQueue.java"
STUBS = {
"dev/ehtid/thaumcraft/ThaumcraftPort.java": "package dev.ehtid.thaumcraft; public class ThaumcraftPort { public static final String MOD_ID=\"thaumcraft\"; }",
"dev/ehtid/thaumcraft/port/core/visual/SalisSparkleDelayRules.java": "package dev.ehtid.thaumcraft.port.core.visual; public class SalisSparkleDelayRules { public static long dueTick(long t,int d){return t+d;} }",
"dev/ehtid/thaumcraft/port/core/visual/SalisSparkleFaceDelayRules.java": "package dev.ehtid.thaumcraft.port.core.visual; public class SalisSparkleFaceDelayRules { public static long dueTick(long t,int d){return t+d;} }",
"dev/ehtid/thaumcraft/util/ThaumcraftSmokeAudit.java": "package dev.ehtid.thaumcraft.util; public class ThaumcraftSmokeAudit { public static int cancelled; public static void mark(String s){if(s.equals(\"SALIS_DELAY_UNLOADED_CANCELLED\"))cancelled++;} }",
"net/minecraft/core/BlockPos.java": "package net.minecraft.core; import net.minecraft.world.phys.Vec3; public record BlockPos(int x,int y,int z) {public static BlockPos containing(Vec3 v){return new BlockPos((int)Math.floor(v.x),(int)Math.floor(v.y),(int)Math.floor(v.z));}}",
"net/minecraft/core/particles/DustParticleOptions.java": "package net.minecraft.core.particles; public class DustParticleOptions {}",
"net/minecraft/resources/ResourceKey.java": "package net.minecraft.resources; public class ResourceKey<T> {}",
"net/minecraft/world/level/Level.java": "package net.minecraft.world.level; public class Level {}",
"net/minecraft/world/level/ChunkPos.java": "package net.minecraft.world.level; import net.minecraft.core.BlockPos; public record ChunkPos(int x,int z) {public ChunkPos(BlockPos p){this(Math.floorDiv(p.x(),16),Math.floorDiv(p.z(),16));}}",
"net/minecraft/world/phys/Vec3.java": "package net.minecraft.world.phys; public class Vec3 {public final double x,y,z; public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}}",
"net/minecraft/server/level/ServerLevel.java": """package net.minecraft.server.level;
import net.minecraft.world.level.Level; import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions; import net.minecraft.resources.ResourceKey;
public class ServerLevel extends Level {
 public long time; public boolean loaded=true; public int sends, loadRequests;
 public ResourceKey<Level> key=new ResourceKey<>();
 public long getGameTime(){return time;} public ResourceKey<Level> dimension(){return key;}
 public boolean hasChunkAt(BlockPos p){return loaded;}
 public void sendParticles(ServerPlayer p,DustParticleOptions d,boolean force,double x,double y,double z,int count,double vx,double vy,double vz,double speed){sends++;}
}""",
"net/minecraft/server/level/ServerPlayer.java": """package net.minecraft.server.level;
import java.util.UUID; public class ServerPlayer {
 public final UUID id=UUID.randomUUID(); public ServerLevel world;
 public ServerPlayer(ServerLevel w){world=w;} public UUID getUUID(){return id;}
 public ServerLevel level(){return world;}
}""",
"net/minecraft/server/MinecraftServer.java": """package net.minecraft.server;
import java.util.*; import net.minecraft.resources.ResourceKey; import net.minecraft.world.level.Level;
import net.minecraft.server.level.*;
public class MinecraftServer {
 public final Map<ResourceKey<Level>,ServerLevel> worlds=new HashMap<>();
 public final Map<UUID,ServerPlayer> players=new HashMap<>();
 public ServerLevel getLevel(ResourceKey<Level> key){return worlds.get(key);}
 public PlayerList getPlayerList(){return new PlayerList();}
 public class PlayerList {public ServerPlayer getPlayer(UUID id){return players.get(id);}}
}""",
"net/neoforged/bus/api/SubscribeEvent.java": "package net.neoforged.bus.api; public @interface SubscribeEvent {}",
"net/neoforged/fml/common/EventBusSubscriber.java": "package net.neoforged.fml.common; public @interface EventBusSubscriber {String modid();}",
"net/neoforged/neoforge/event/server/ServerStoppedEvent.java": "package net.neoforged.neoforge.event.server; public class ServerStoppedEvent {}",
"net/neoforged/neoforge/event/tick/ServerTickEvent.java": "package net.neoforged.neoforge.event.tick; import net.minecraft.server.MinecraftServer; public class ServerTickEvent {public record Post(MinecraftServer server){public MinecraftServer getServer(){return server;}}}",
"net/neoforged/neoforge/event/level/ChunkEvent.java": """package net.neoforged.neoforge.event.level;
import net.minecraft.world.level.*; import net.minecraft.server.level.ServerLevel;
public class ChunkEvent {
 public record Chunk(ChunkPos pos){public ChunkPos getPos(){return pos;}}
 public record Unload(ServerLevel level,Chunk chunk) {
 public ServerLevel getLevel(){return level;} public Chunk getChunk(){return chunk;}
 }
}""",
"test/SalisQueueTest.java": """package test;
import dev.ehtid.thaumcraft.item.SalisSparkleTrailQueue;
import dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit;
import net.minecraft.server.*; import net.minecraft.server.level.*;
import net.minecraft.world.level.*; import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.DustParticleOptions;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
public class SalisQueueTest {
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 static void tick(MinecraftServer s){SalisSparkleTrailQueue.onServerTick(new ServerTickEvent.Post(s));}
 public static void main(String[] args){
  var s=new MinecraftServer(); var a=new ServerLevel(); var b=new ServerLevel();
  s.worlds.put(a.key,a);s.worlds.put(b.key,b);
  var p=new ServerPlayer(a);s.players.put(p.id,p);
  var dust=new DustParticleOptions();var origin=new Vec3(17,64,17);var v=new Vec3(0,0,0);
  // Between-ticks unload/reload: only the event can see this transition.
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,5);
  SalisSparkleTrailQueue.onChunkUnload(new ChunkEvent.Unload(a,new ChunkEvent.Chunk(new ChunkPos(1,1))));
  a.time=5;tick(s);check(a.sends==0,"stale effect emitted after reload");
  check(ThaumcraftSmokeAudit.cancelled==1,"unload event did not cancel");
  // Different dimension and different chunk must not cancel this entry.
  SalisSparkleTrailQueue.emitFaceOrQueue(a,p,dust,origin,v,3);
  SalisSparkleTrailQueue.onChunkUnload(new ChunkEvent.Unload(b,new ChunkEvent.Chunk(new ChunkPos(1,1))));
  SalisSparkleTrailQueue.onChunkUnload(new ChunkEvent.Unload(a,new ChunkEvent.Chunk(new ChunkPos(2,1))));
  a.time=8;tick(s);check(a.sends==1,"unrelated unload cancelled effect");
  // Unloaded at a tick before due: remove, never resurrect on reload.
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,4);
  a.loaded=false;a.time=9;tick(s);a.loaded=true;a.time=12;tick(s);
  check(a.sends==1,"tick cancellation resurrected particle");
  // Already unloaded: both immediate and delayed requests are discarded.
  a.loaded=false;SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,0);
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,3);
  a.loaded=true;a.time=20;tick(s);check(a.sends==1,"unloaded origin admitted");
  check(a.loadRequests==0 && b.loadRequests==0,"chunk was force loaded");
  SalisSparkleTrailQueue.onServerStopped(new ServerStoppedEvent());
  System.out.println("SALIS_QUEUE_UNLOAD_JVM=PASS");
 }
}"""
}
with tempfile.TemporaryDirectory(prefix="salis-queue-jvm-") as d:
    root = Path(d)
    for path, code in STUBS.items():
        target = root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(code, encoding="utf-8")
    classes = root / "classes"
    classes.mkdir()
    java_files = [str(p) for p in root.rglob("*.java")] + [str(QUEUE)]
    subprocess.run(["javac", "--release", "21", "-d", str(classes), *java_files], check=True)
    subprocess.run(["java", "-cp", str(classes), "test.SalisQueueTest"], check=True)
