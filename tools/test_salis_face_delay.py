#!/usr/bin/env python3
"""Compile the actual BETA26 face-delay helper; assert server scheduling and game marker."""
from pathlib import Path
import subprocess
import tempfile

rules = Path("src/main/java/dev/ehtid/thaumcraft/port/core/visual/SalisSparkleFaceDelayRules.java")
fx = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisMundusSparkles.java").read_text()
queue = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisSparkleTrailQueue.java").read_text()
smoke = Path("tools/client_dust_sound_smoke.py").read_text()
assert "new AABB(pos).clip(eye, eye.add(forward.scale(6.0D)))" in fx
assert "SalisSparkleFaceDelayRules.delayTicks(" in fx
assert "spawn.distanceTo(faceStart)" in fx
assert "SalisSparkleTrailQueue.emitFaceOrQueue(" in fx
assert "SalisSparkleFaceDelayRules.dueTick(" in queue
assert "pending.face()" in queue
assert "SALIS_FACE_DELAY_QUEUE_DRAINED" in queue
assert 'ThaumcraftSmokeAudit.mark("SALIS_DELAY_UNLOADED_CANCELLED");' in queue
assert "grep -Fq 'SALIS_FACE_DELAY_QUEUE_DRAINED' client-smoke/client.log" in smoke
assert "level.sendParticles(player, color, false" in queue
assert "level.sendParticles(player," not in fx
assert "PENDING.clear()" in queue
assert "player.level() != level" in queue
assert "level.hasChunkAt(net.minecraft.core.BlockPos.containing(pending.origin()))" in queue
# A chunk unloaded before the due tick must cancel the effect immediately;
# otherwise unload/reload within the delay window resurrects stale particles.
tick_body = queue.split("public static void onServerTick(", 1)[1].split("public static void onServerStopped(", 1)[0]
assert tick_body.index("if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(pending.origin())))") < tick_body.index("if (level.getGameTime() < pending.dueTick()) continue;")
assert tick_body.index("iterator.remove();", tick_body.index("if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(pending.origin())))")) < tick_body.index("if (level.getGameTime() < pending.dueTick()) continue;")
assert tick_body.index('ThaumcraftSmokeAudit.mark("SALIS_DELAY_UNLOADED_CANCELLED");') < tick_body.index("if (level.getGameTime() < pending.dueTick()) continue;")
assert "if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(origin))) return;" in queue
assert queue.index("if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(origin))) return;") < queue.index("emit(level, player, color, origin, velocity);")
assert queue.index("level.hasChunkAt(") < queue.index("emit(level, player, pending.color()")
assert queue.count("if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(origin))) return;") == 2
# Both zero-delay and delayed paths must reject unloaded origins before dispatch.
# Inspect the enqueue method rather than a fixed 250-character window:
# adding unrelated session guards must not break this source contract.
enqueue_body = queue.split("private static void enqueueOrEmit(", 1)[1].split("@SubscribeEvent", 1)[0]
pending_at = enqueue_body.index("PENDING.add(")
loaded_guard = "if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(origin))) return;"
assert enqueue_body[:pending_at].count(loaded_guard) == 2
assert enqueue_body.index(loaded_guard) < enqueue_body.index("emit(level, player, color, origin, velocity);")
assert enqueue_body.rfind(loaded_guard, 0, pending_at) > enqueue_body.index("emit(level, player, color, origin, velocity);")

# ChunkEvent.Unload closes the unload/reload-between-ticks gap: cancel by
# dimension and chunk position immediately, without requesting chunk loads.
unload_body = queue.split("public static void onChunkUnload(", 1)[1].split("public static void onServerStopped(", 1)[0]
assert "ChunkEvent.Unload" in queue
assert "event.getLevel() instanceof ServerLevel level" in unload_body
assert "event.getChunk().getPos()" in unload_body
assert "pending.dimension() != level.dimension()" in unload_body
assert "BlockPos.containing(pending.origin())" in unload_body
assert "iterator.remove();" in unload_body
assert 'ThaumcraftSmokeAudit.mark("SALIS_DELAY_UNLOADED_CANCELLED");' in unload_body
# event.getChunk() only reads the chunk supplied by the unload event;
# forbid level/server chunk retrieval, which could trigger a load.
assert "event.getChunk().getPos()" in unload_body
assert "level.getChunk(" not in unload_body
assert "server.getChunk(" not in unload_body
assert "setChunkForced" not in unload_body

harness = r'''
import dev.ehtid.thaumcraft.port.core.visual.SalisSparkleFaceDelayRules;
public final class FaceDelayTest {
 static int assertions;
 static void eq(long expected,long actual){assertions++;if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
 static void invalid(int jitter,double distance){assertions++;try{SalisSparkleFaceDelayRules.delayTicks(jitter,distance);throw new AssertionError("accepted invalid");}catch(IllegalArgumentException expected){}}
 public static void main(String[] args){
  eq(0,SalisSparkleFaceDelayRules.delayTicks(0,0));
  eq(4,SalisSparkleFaceDelayRules.delayTicks(4,0));
  eq(16,SalisSparkleFaceDelayRules.delayTicks(0,1));
  eq(20,SalisSparkleFaceDelayRules.delayTicks(4,1));
  eq(11,SalisSparkleFaceDelayRules.delayTicks(3,0.5));
  eq(4,SalisSparkleFaceDelayRules.delayTicks(4,0.0624));
  eq(5,SalisSparkleFaceDelayRules.delayTicks(4,0.0625));
  eq(31,SalisSparkleFaceDelayRules.delayTicks(4,Math.sqrt(3)));
  eq(101,SalisSparkleFaceDelayRules.dueTick(70,31));
  invalid(-1,0);invalid(5,0);invalid(0,-1);invalid(0,Double.NaN);invalid(0,Double.POSITIVE_INFINITY);invalid(0,Double.MAX_VALUE);
  assertions++;try{SalisSparkleFaceDelayRules.dueTick(Long.MAX_VALUE,1);throw new AssertionError("overflow accepted");}catch(ArithmeticException expected){}
  System.out.println("SALIS_BETA26_FACE_DELAY=PASS assertions="+assertions);
 }
}
'''
with tempfile.TemporaryDirectory(prefix="salis-face-delay-") as tmp:
    test = Path(tmp) / "FaceDelayTest.java"
    test.write_text(harness)
    subprocess.run(["javac", "--release", "21", "-d", tmp, str(rules), str(test)], check=True)
    subprocess.run(["java", "-cp", tmp, "FaceDelayTest"], check=True)
