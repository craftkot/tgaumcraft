#!/usr/bin/env python3
"""Compile and execute the actual pure Java BETA26 sparkle face-layout helper."""
from pathlib import Path
import subprocess
import tempfile

source = Path("src/main/java/dev/ehtid/thaumcraft/port/core/visual/SalisSparkleFaceLayout.java")
harness = r'''
import dev.ehtid.thaumcraft.port.core.visual.SalisSparkleFaceLayout;
public class SalisSparkleFaceLayoutTest {
    static int checks;
    static void eq(double a, double b) {
        checks++;
        if (Math.abs(a - b) > 1e-12) throw new AssertionError(a + " != " + b);
    }
    public static void main(String[] args) {
        eq(40, SalisSparkleFaceLayout.particlesPerFace(1,1,1));
        eq(32, SalisSparkleFaceLayout.particlesPerFace(1,.5,1));
        eq(0, SalisSparkleFaceLayout.particlesPerFace(.01,.01,.01));
        eq(0, SalisSparkleFaceLayout.coordinate(0,1,-1,0));
        eq(1, SalisSparkleFaceLayout.coordinate(0,1,1,0));
        eq(.5, SalisSparkleFaceLayout.coordinate(0,1,0,0));
        eq(.75, SalisSparkleFaceLayout.coordinate(.25,.75,1,0));
        eq(.25, SalisSparkleFaceLayout.coordinate(.25,.75,-1,0));
        eq(.5, SalisSparkleFaceLayout.coordinate(0,.5,0,0));
        eq(.5, SalisSparkleFaceLayout.coordinate(.5,1,0,0));
        eq(0, SalisSparkleFaceLayout.coordinate(0,1,0,-4));
        eq(1, SalisSparkleFaceLayout.coordinate(0,1,0,4));
        eq(.62, SalisSparkleFaceLayout.coordinate(0,1,0,.2));
        try {
            SalisSparkleFaceLayout.coordinate(0,1,2,0);
            throw new AssertionError("invalid direction accepted");
        } catch (IllegalArgumentException expected) { checks++; }
        System.out.println("SALIS_BETA26_FACE_GEOMETRY=PASS assertions=" + checks);
    }
}
'''
with tempfile.TemporaryDirectory(prefix="salis-face-") as tmp:
    test = Path(tmp) / "SalisSparkleFaceLayoutTest.java"
    test.write_text(harness)
    subprocess.run(["javac", "--release", "21", "-d", tmp, str(source), str(test)], check=True)
    subprocess.run(["java", "-cp", tmp, "SalisSparkleFaceLayoutTest"], check=True)
