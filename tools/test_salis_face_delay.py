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
assert "grep -Fq 'SALIS_FACE_DELAY_QUEUE_DRAINED' client-smoke/client.log" in smoke
assert "level.sendParticles(player, color, false" in queue
assert "level.sendParticles(player," not in fx
assert "PENDING.clear()" in queue
assert "player.level() != level" in queue
assert "level.hasChunkAt(net.minecraft.core.BlockPos.containing(pending.origin()))" in queue
assert queue.index("level.hasChunkAt(") < queue.index("emit(level, player, pending.color()")

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
