#!/usr/bin/env python3
"""Compile and execute the actual pure Java BETA26 sparkle face-layout helper."""
from pathlib import Path
import subprocess
import tempfile

source = Path("src/main/java/dev/ehtid/thaumcraft/port/core/visual/SalisSparkleFaceLayout.java")
scale_source = Path("src/main/java/dev/ehtid/thaumcraft/port/core/visual/SalisSparkleScaleRules.java")
harness = r'''
import dev.ehtid.thaumcraft.port.core.visual.SalisSparkleFaceLayout;
import dev.ehtid.thaumcraft.port.core.visual.SalisSparkleScaleRules;
public class SalisSparkleFaceLayoutTest {
    static int checks;
    static void eq(double a, double b) {
        checks++;
        if (Math.abs(a - b) > 1e-12) throw new AssertionError(a + " != " + b);
    }
    static void feq(float expected, float actual) {
        checks++;
        if (Float.compare(expected, actual) != 0) throw new AssertionError(expected + " != " + actual);
    }
    static void invalid(double sample) {
        checks++;
        try {
            SalisSparkleScaleRules.blockFace(sample);
            throw new AssertionError("accepted nonfinite sample " + sample);
        } catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        // BETA26's discarded AxisAlignedBB.grow() return leaves original bounds.
        eq(40, SalisSparkleFaceLayout.particlesPerFace(1,1,1));
        eq(0, SalisSparkleFaceLayout.coordinate(0,1,-1,0));
        eq(1, SalisSparkleFaceLayout.coordinate(0,1,1,0));
        // A partial shape still clamps to its own original bounds.
        eq(.15, SalisSparkleFaceLayout.coordinate(.15,.85,-1,0));
        eq(.85, SalisSparkleFaceLayout.coordinate(.15,.85,1,0));
        eq(32, SalisSparkleFaceLayout.particlesPerFace(1,.5,1));
        eq(0, SalisSparkleFaceLayout.particlesPerFace(.01,.01,.01));
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
        // BETA26 drawBlockSparkles: size = 0.4f + (float)gaussian * 0.1f.
        // DustParticleOptions cannot represent negative or oversized scales.
        feq(0.4F, SalisSparkleScaleRules.blockFace(0));
        feq(0.5F, SalisSparkleScaleRules.blockFace(1));
        feq(0.3F, SalisSparkleScaleRules.blockFace(-1));
        feq(0.8F, SalisSparkleScaleRules.blockFace(4));
        feq(0.01F, SalisSparkleScaleRules.blockFace(-4));
        feq(4.0F, SalisSparkleScaleRules.blockFace(100));
        feq(0.01F, SalisSparkleScaleRules.blockFace(-100));
        invalid(Double.NaN);
        invalid(Double.POSITIVE_INFINITY);
        invalid(Double.NEGATIVE_INFINITY);
        System.out.println("SALIS_BETA26_FACE_GEOMETRY_AND_SCALE=PASS assertions=" + checks);
    }
}
'''
with tempfile.TemporaryDirectory(prefix="salis-face-") as tmp:
    test = Path(tmp) / "SalisSparkleFaceLayoutTest.java"
    test.write_text(harness)
    subprocess.run(["javac", "--release", "21", "-d", tmp, str(source), str(scale_source), str(test)], check=True)
    subprocess.run(["java", "-cp", tmp, "SalisSparkleFaceLayoutTest"], check=True)
