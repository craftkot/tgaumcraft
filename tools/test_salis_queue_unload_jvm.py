#!/usr/bin/env python3
"""Execute the real NeoForge Salis queue against minimal API doubles.

This is a JVM unit regression, NOT a Minecraft GameTest or client smoke.
It does not instrument native chunk tickets or establish absence of force-loading.
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
"dev/ehtid/thaumcraft/util/ThaumcraftSmokeAudit.java": "package dev.ehtid.thaumcraft.util; public class ThaumcraftSmokeAudit { private static final java.util.Map<String,Integer> COUNTS=new java.util.HashMap<>(); public static int cancelled; public static void mark(String s){COUNTS.merge(s,1,Integer::sum);if(s.equals(\"SALIS_DELAY_UNLOADED_CANCELLED\"))cancelled++;} public static int count(String s){return COUNTS.getOrDefault(s,0);} }",
"net/minecraft/core/BlockPos.java": "package net.minecraft.core; import net.minecraft.world.phys.Vec3; public record BlockPos(int x,int y,int z) {public static BlockPos containing(Vec3 v){return new BlockPos((int)Math.floor(v.x),(int)Math.floor(v.y),(int)Math.floor(v.z));}}",
"net/minecraft/core/particles/ParticleOptions.java": "package net.minecraft.core.particles; public interface ParticleOptions {}",
"net/minecraft/core/particles/DustParticleOptions.java": "package net.minecraft.core.particles; public class DustParticleOptions implements ParticleOptions {}",
"net/minecraft/resources/ResourceKey.java": "package net.minecraft.resources; public class ResourceKey<T> {}",
"net/minecraft/world/level/Level.java": "package net.minecraft.world.level; public class Level {}",
"net/minecraft/world/level/ChunkPos.java": "package net.minecraft.world.level; import net.minecraft.core.BlockPos; public record ChunkPos(int x,int z) {public ChunkPos(BlockPos p){this(Math.floorDiv(p.x(),16),Math.floorDiv(p.z(),16));}}",
"net/minecraft/world/phys/Vec3.java": "package net.minecraft.world.phys; public class Vec3 {public final double x,y,z; public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}}",
"net/minecraft/server/level/ServerLevel.java": """package net.minecraft.server.level;
import net.minecraft.world.level.Level; import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions; import net.minecraft.resources.ResourceKey;
public class ServerLevel extends Level {
 public long time; public boolean loaded=true; public final java.util.Map<net.minecraft.world.level.ChunkPos,Boolean> chunkStates=new java.util.HashMap<>(); public int sends; public ServerPlayer lastRecipient; public boolean lastForce; public int lastCount; public double lastVx,lastVy,lastVz,lastSpeed;
 public net.minecraft.server.MinecraftServer server;
 public net.minecraft.server.MinecraftServer getServer(){return server;}
 public ResourceKey<Level> key=new ResourceKey<>();
 public long getGameTime(){return time;} public ResourceKey<Level> dimension(){return key;}
 public boolean hasChunkAt(BlockPos p){return chunkStates.getOrDefault(new net.minecraft.world.level.ChunkPos(p),loaded);}
 public void sendParticles(ServerPlayer p,net.minecraft.core.particles.ParticleOptions d,boolean force,double x,double y,double z,int count,double vx,double vy,double vz,double speed){sends++;lastRecipient=p;lastForce=force;lastCount=count;lastVx=vx;lastVy=vy;lastVz=vz;lastSpeed=speed;}
}""",
"net/minecraft/server/level/ServerPlayer.java": """package net.minecraft.server.level;
import java.util.UUID; public class ServerPlayer {
 public final UUID id; public ServerLevel world;
 public ServerPlayer(ServerLevel w){this(w,UUID.randomUUID());}
 public ServerPlayer(ServerLevel w,UUID uuid){world=w;id=uuid;} public UUID getUUID(){return id;}
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
"net/neoforged/neoforge/event/server/ServerStoppedEvent.java": "package net.neoforged.neoforge.event.server; import net.minecraft.server.MinecraftServer; public record ServerStoppedEvent(MinecraftServer server) {public MinecraftServer getServer(){return server;}}",
"net/neoforged/neoforge/event/tick/ServerTickEvent.java": "package net.neoforged.neoforge.event.tick; import net.minecraft.server.MinecraftServer; public class ServerTickEvent {public record Post(MinecraftServer server){public MinecraftServer getServer(){return server;}}}",
"net/neoforged/neoforge/event/level/ChunkEvent.java": """package net.neoforged.neoforge.event.level;
import net.minecraft.world.level.*; import net.minecraft.server.level.ServerLevel;
public class ChunkEvent {
 public record Chunk(ChunkPos pos){public ChunkPos getPos(){return pos;}}
 public record Unload(Level level,Chunk chunk) {
 public Level getLevel(){return level;} public Chunk getChunk(){return chunk;}
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
  s.worlds.put(a.key,a);s.worlds.put(b.key,b);a.server=s;b.server=s;
  var p=new ServerPlayer(a);s.players.put(p.id,p);
  var dust=new DustParticleOptions();var origin=new Vec3(17,64,17);var v=new Vec3(0,0,0);
  // Between-ticks unload/reload: only the event can see this transition.
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,5);
  check(SalisSparkleTrailQueue.pendingCountForServer(s)==1,"pending count must include the actual server's queued effect");
  check(ThaumcraftSmokeAudit.count("SALIS_DELAY_ENQUEUED")>=1,"queue admission marker missing");
  SalisSparkleTrailQueue.onChunkUnload(new ChunkEvent.Unload(a,new ChunkEvent.Chunk(new ChunkPos(1,1))));
  check(SalisSparkleTrailQueue.pendingCountForServer(s)==0,"native unload event must remove the server's pending effect");
  check(ThaumcraftSmokeAudit.count("SALIS_DELAY_UNLOADED_CANCELLED")>=1,"unload cancellation marker missing");
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
  // Negative-coordinate chunk regression: floorDiv(-1,16)=-1, not zero.
  // A real unload/reload event must cancel a pending effect at a negative edge.
  var negativeOrigin=new Vec3(-1,64,-17);
  a.time=20;
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,negativeOrigin,v,3);
  SalisSparkleTrailQueue.onChunkUnload(new ChunkEvent.Unload(a,new ChunkEvent.Chunk(new ChunkPos(-1,-2))));
  a.time=23;tick(s);
  check(a.sends==1,"negative chunk unload resurrected stale particle");
  check(ThaumcraftSmokeAudit.cancelled==3,"negative chunk unload not detected: expected earlier event, tick-time cancellation, and negative-chunk event");
  // A neighboring negative chunk must not cancel the correctly indexed effect.
  a.time=23;
  SalisSparkleTrailQueue.emitFaceOrQueue(a,p,dust,negativeOrigin,v,3);
  SalisSparkleTrailQueue.onChunkUnload(new ChunkEvent.Unload(a,new ChunkEvent.Chunk(new ChunkPos(-2,-2))));
  a.time=26;tick(s);
  check(a.sends==2,"neighbor negative chunk incorrectly cancelled effect");
  // Same dimension key on a separate server is NOT the same loaded world.
  // An unload on server A must not cancel server C's pending particle.
  var cServer=new MinecraftServer(); var c=new ServerLevel();
  c.key=a.key; c.server=cServer; cServer.worlds.put(c.key,c);
  var cPlayer=new ServerPlayer(c); cServer.players.put(cPlayer.id,cPlayer);
  c.time=30;
  SalisSparkleTrailQueue.emitOrQueue(c,cPlayer,dust,origin,v,3);
  check(SalisSparkleTrailQueue.pendingCountForServer(cServer)==1,"pending count must be isolated to server C");
  // Another server's tick must not discard this pending particle.
  a.time=31; tick(s);
  SalisSparkleTrailQueue.onChunkUnload(new ChunkEvent.Unload(a,new ChunkEvent.Chunk(new ChunkPos(1,1))));
  c.time=33; tick(cServer);
  check(c.sends==1,"unload in another server cancelled same-dimension pending FX");
  c.time=33; tick(cServer);
  check(SalisSparkleTrailQueue.pendingCountForServer(cServer)==0,"server C queue did not drain on its own tick");
  check(c.sends==1,"unload in another server cancelled same-dimension pending FX");
  // Stopping server A must not erase server C's pending particle.
  c.time=33; SalisSparkleTrailQueue.emitOrQueue(c,cPlayer,dust,origin,v,2);
  SalisSparkleTrailQueue.onServerStopped(new ServerStoppedEvent(s));
  c.time=35; tick(cServer);
  check(c.sends==2,"other server shutdown cleared pending FX");
  // A different ServerLevel with the same dimension key must not inherit
  // old pending work after server world replacement.
  c.time=34;
  SalisSparkleTrailQueue.emitOrQueue(c,cPlayer,dust,origin,v,3);
  var replacementWorld=new ServerLevel();
  replacementWorld.key=c.key; replacementWorld.server=cServer;
  cServer.worlds.put(c.key,replacementWorld);
  c.time=37; tick(cServer);
  check(c.sends==2 && replacementWorld.sends==0,"replacement level inherited old Salis FX");
  // Admission must reject a stale world even for zero-delay FX. A former
  // ServerLevel can remain loaded and keep a valid player reference briefly.
  c.time=38;
  SalisSparkleTrailQueue.emitOrQueue(c,cPlayer,dust,origin,v,0);
  check(c.sends==2 && replacementWorld.sends==0,"stale world immediate effect emitted");
  // A delayed call made against the replaced world must not be retained even
  // if the server later restores the same ServerLevel before the due tick.
  SalisSparkleTrailQueue.emitFaceOrQueue(c,cPlayer,dust,origin,v,3);
  cServer.worlds.put(c.key,c);
  c.time=41; tick(cServer);
  check(c.sends==2,"stale world queued effect resurrected after restoration");
  cServer.worlds.put(c.key,replacementWorld);
  // Restore the baseline count for the remaining independent assertions.
  a.sends=1;
  // Disconnect before due tick: no packet must be sent to a departed player.
  a.loaded=true; a.time=21;
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,3);
  s.players.remove(p.id); a.time=24; tick(s);
  check(a.sends==1,"disconnected player received delayed effect");
  // Reconnect with the same UUID must not inherit an old session's pending effect.
  a.time=24;
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,3);
  var replacement=new ServerPlayer(a,p.id);
  check(replacement.getUUID().equals(p.getUUID()),"reconnect fixture UUID differs");
  s.players.put(p.id,replacement); // actual same-UUID new session
  a.time=27; tick(s);
  check(a.sends==1,"reconnected player inherited old session's delayed effect");
  s.players.put(p.id,p);
  // Dimension switch before due tick: never send a stale effect to another world.
  s.players.put(p.id,p); p.world=a; a.time=25;
  SalisSparkleTrailQueue.emitFaceOrQueue(a,p,dust,origin,v,3);
  p.world=b; a.time=28; tick(s);
  check(a.sends==1 && b.sends==0,"cross-dimension stale effect emitted");
  // Shutdown clears pending work even when a new server reuses the same dimension key.
  p.world=a; a.time=29;
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,3);
  SalisSparkleTrailQueue.onServerStopped(new ServerStoppedEvent(s));
  a.time=33; tick(s);
  check(a.sends==1,"pending effect survived server shutdown");
  SalisSparkleTrailQueue.onServerStopped(new ServerStoppedEvent(s));
  // Zero-delay call from a stale world reference must not address a moved player.
  p.world=b; a.time=34;
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,0);
  check(a.sends==1 && b.sends==0,"immediate cross-dimension stale effect emitted");
  // Stale delayed call must not queue work that could emit if player returns before due.
  p.world=b; a.time=35;
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,3);
  p.world=a; a.time=38; tick(s);
  check(a.sends==1,"stale delayed request emitted after player returned");
  // Immediate calls must reject disconnected and replaced player sessions.
  s.players.remove(p.id); p.world=a;
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,0);
  check(a.sends==1,"disconnected actor received immediate effect");
  s.players.put(p.id,new ServerPlayer(a,p.id));
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,0);
  check(a.sends==1,"replaced session received stale immediate effect");
  s.players.put(p.id,p);
  // A stale delayed caller must be rejected at enqueue, even if the same actor
  // is restored to the player list before the due tick (no stale queue entry).
  s.players.remove(p.id); a.time=39;
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,3);
  s.players.put(p.id,p); a.time=42; tick(s);
  check(a.sends==1,"disconnected actor queued delayed effect that fired after rejoin");
  s.players.put(p.id,new ServerPlayer(a,p.id)); a.time=43;
  SalisSparkleTrailQueue.emitFaceOrQueue(a,p,dust,origin,v,3);
  s.players.put(p.id,p); a.time=46; tick(s);
  check(a.sends==1,"replaced actor queued delayed effect that fired after restoration");
  // A valid zero-delay call still emits immediately in the player's world.
  p.world=a;
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,0);
  check(a.sends==2,"valid immediate effect suppressed");
  check(a.lastRecipient==p,"particle dispatch must target the acting player only");
  check(!a.lastForce && a.lastCount==0,"Salis must send a non-forced single particle, not a broadcast burst");
  check(a.lastVx==0 && a.lastVy==0 && a.lastVz==0 && a.lastSpeed==1.0D,"Salis must preserve exact per-particle velocity and speed");
  // Adjacent chunks at the positive 15/16 block boundary are distinct.
  // An unload of chunk (0,0) must cancel only the effect at x=15,
  // leaving the x=16 effect in chunk (1,0) eligible to dispatch.
  a.time=47;
  var westEdge=new Vec3(15.99,64,0.5);
  var eastEdge=new Vec3(16.0,64,0.5);
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,westEdge,v,2);
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,eastEdge,v,2);
  SalisSparkleTrailQueue.onChunkUnload(new ChunkEvent.Unload(a,new ChunkEvent.Chunk(new ChunkPos(0,0))));
  a.time=49;tick(s);
  check(a.sends==3,"chunk boundary unload cancelled neighbor or retained stale particle");
  check(ThaumcraftSmokeAudit.cancelled==4,"positive boundary unload did not cancel exactly one queued effect");
  // Restore the prior baseline for the following independent reload assertions.
  a.sends=2;
  // A fresh effect after an unload/reload must work, but the pre-unload
  // effect must never replay. This models an unload event between ticks.
  a.time=50;
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,5);
  SalisSparkleTrailQueue.onChunkUnload(new ChunkEvent.Unload(a,new ChunkEvent.Chunk(new ChunkPos(1,1))));
  a.loaded=false;
  a.time=51; tick(s);
  a.loaded=true;
  a.time=52;
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,origin,v,2);
  a.time=54; tick(s);
  check(a.sends==3,"fresh post-reload particle was lost or stale one replayed");
  a.time=55; tick(s);
  check(a.sends==3,"pre-unload particle replayed at original due tick");
  // Native chunk managers track chunk availability independently. Verify that
  // an unloaded neighbor does not suppress a loaded target's delayed FX.
  a.time=60;
  a.chunkStates.put(new ChunkPos(0,0),false);
  a.chunkStates.put(new ChunkPos(1,0),true);
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,new Vec3(15.99,64,0.5),v,2);
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,new Vec3(16.0,64,0.5),v,2);
  a.time=62;tick(s);
  check(a.sends==4,"unloaded neighbor suppressed loaded target or emitted into unloaded chunk");
  a.chunkStates.clear();
  // Per-chunk tick-time cancellation must be permanent even if the chunk
  // reloads before its original due tick; a loaded neighbor still dispatches.
  a.time=70;
  a.chunkStates.put(new ChunkPos(0,0),true);
  a.chunkStates.put(new ChunkPos(1,0),true);
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,new Vec3(15.99,64,0.5),v,4);
  SalisSparkleTrailQueue.emitOrQueue(a,p,dust,new Vec3(16.0,64,0.5),v,4);
  a.chunkStates.put(new ChunkPos(0,0),false);
  a.time=71;tick(s);
  a.chunkStates.put(new ChunkPos(0,0),true);
  a.time=74;tick(s);
  check(a.sends==5,"tick-cancelled chunk resurrected after reload or loaded neighbor was lost");
  check(ThaumcraftSmokeAudit.cancelled==6,"tick-time chunk cancellation did not record exactly one event");
  a.chunkStates.clear();
  SalisSparkleTrailQueue.onServerStopped(new ServerStoppedEvent(s));
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
