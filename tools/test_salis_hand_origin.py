#!/usr/bin/env python3
"""Compile/run the actual pure Java BETA26 EntityUtils.posToHand geometry helper."""
from pathlib import Path
import subprocess
import tempfile

source = Path("src/main/java/dev/ehtid/thaumcraft/port/core/visual/SalisSparkleHandOrigin.java")
harness = r'''
import dev.ehtid.thaumcraft.port.core.visual.SalisSparkleHandOrigin;

public class SalisSparkleHandOriginTest {
    private static int checks;
    private static void near(double expected, double actual) {
        checks++;
        if (Math.abs(expected - actual) > 1.0E-9) {
            throw new AssertionError("expected " + expected + ", actual " + actual);
        }
    }
    private static void point(double[] p, double x, double y, double z) {
        near(x, p[0]); near(y, p[1]); near(z, p[2]);
    }
    public static void main(String[] args) {
        point(SalisSparkleHandOrigin.position(0, 64, 1.8, 0, 0, 0, 0, 0, false), -.3, 65.15, 0);
        point(SalisSparkleHandOrigin.position(0, 64, 1.8, 0, 0, 0, 0, 0, true), .3, 65.15, 0);
        point(SalisSparkleHandOrigin.position(10, 0, 1.8, -4, 90, 0, 0, 0, false), 10, 1.15, -4.3);
        point(SalisSparkleHandOrigin.position(10, 0, 1.8, -4, 90, 0, 0, 0, true), 10, 1.15, -3.7);
        point(SalisSparkleHandOrigin.position(3, -2, .6, 9, -90, .5, -.5, -.25, false), 3.15, -1.6, 9.225);
        point(SalisSparkleHandOrigin.position(3, -2, .6, 9, -90, .5, -.5, -.25, true), 3.15, -1.6, 8.625);
        System.out.println("SALIS_BETA26_HAND_ANCHOR=PASS assertions=" + checks);
    }
}
'''
with tempfile.TemporaryDirectory(prefix="salis-hand-") as tmp:
    test = Path(tmp) / "SalisSparkleHandOriginTest.java"
    test.write_text(harness)
    subprocess.run(["javac", "--release", "21", "-d", tmp, str(source), str(test)], check=True)
    subprocess.run(["java", "-cp", tmp, "SalisSparkleHandOriginTest"], check=True)
