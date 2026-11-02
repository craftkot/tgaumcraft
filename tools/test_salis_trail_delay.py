#!/usr/bin/env python3
"""Compile actual Java 21 delay rules; guard actor-only tick dispatch source."""
from pathlib import Path
import subprocess
import tempfile

rules = Path("src/main/java/dev/ehtid/thaumcraft/port/core/visual/SalisSparkleDelayRules.java")
queue = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisSparkleTrailQueue.java").read_text()
fx = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisMundusSparkles.java").read_text()
assert "@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID)" in queue
assert "ServerTickEvent.Post" in queue and "ServerStoppedEvent" in queue
assert "PENDING.clear()" in queue
assert "player.level() != level" in queue
assert "server.getPlayerList().getPlayer(pending.playerId())" in queue
assert "level.sendParticles(player, color, false" in queue
assert "level.sendParticles(color" not in queue
assert "SalisSparkleDelayRules.dueTick(" in queue
assert "SALIS_TRAIL_DELAY_QUEUE_DRAINED" in queue
assert "SalisSparkleTrailQueue.emitOrQueue(" in fx
assert "level.random.nextInt(5)" in fx
assert "TRAIL_COUNT = 50" in fx

harness = r'''
import dev.ehtid.thaumcraft.port.core.visual.SalisSparkleDelayRules;
public final class SalisSparkleDelayRulesTest {
    static int assertions = 0;
    static void eq(long expected, long actual) {
        assertions++;
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
    static void invalid(int delay) {
        assertions++;
        try {
            SalisSparkleDelayRules.dueTick(100, delay);
            throw new AssertionError("accepted invalid delay " + delay);
        } catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        for (int delay = 0; delay < 5; delay++) {
            eq(100 + delay, SalisSparkleDelayRules.dueTick(100, delay));
        }
        eq(0, SalisSparkleDelayRules.dueTick(0, 0));
        eq(-96, SalisSparkleDelayRules.dueTick(-100, 4));
        invalid(-1);
        invalid(5);
        assertions++;
        try {
            SalisSparkleDelayRules.dueTick(Long.MAX_VALUE, 1);
            throw new AssertionError("overflow accepted");
        } catch (ArithmeticException expected) { }
        System.out.println("SALIS_BETA26_TRAIL_DELAY=PASS assertions=" + assertions);
    }
}
'''
with tempfile.TemporaryDirectory(prefix="salis-delay-") as tmp:
    test_file = Path(tmp) / "SalisSparkleDelayRulesTest.java"
    test_file.write_text(harness)
    subprocess.run(["javac", "--release", "21", "-d", tmp, str(rules), str(test_file)], check=True)
    subprocess.run(["java", "-cp", tmp, "SalisSparkleDelayRulesTest"], check=True)
