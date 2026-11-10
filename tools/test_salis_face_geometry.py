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
        // BETA26 FXGeneric keeps the raw 0.4 + Gaussian*0.1 scale; no dust clamp.
        feq(0.4F, SalisSparkleScaleRules.blockFace(0));
        feq(0.5F, SalisSparkleScaleRules.blockFace(1));
        feq(0.3F, SalisSparkleScaleRules.blockFace(-1));
        feq(0.8F, SalisSparkleScaleRules.blockFace(4));
        feq(0.0F, SalisSparkleScaleRules.blockFace(-4));
        feq(10.4F, SalisSparkleScaleRules.blockFace(100));
        feq(-9.6F, SalisSparkleScaleRules.blockFace(-100));
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

particle = Path("src/neoforge/java/dev/ehtid/thaumcraft/client/SalisSparkleParticle.java").read_text()
sparkles = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisMundusSparkles.java").read_text()
assert "FACE_SENTINEL = 2000.0D" in particle
assert "xSpeed >= FACE_SENTINEL * 0.5D" in particle
assert "SalisSparkleScaleRules.blockFace(level.random.nextGaussian())" in particle
assert "this.gravity = this.face ? 0.01F" in particle
assert "this.slowDown = this.face ? 1.0D" in particle
assert "this.face ? scaleStart * 2.0F : 1.0F" in particle
assert particle.count("this.level.random.nextGaussian()") == 3
assert "this.random.nextGaussian()" not in particle
assert "FACE_PARTICLE_SENTINEL = 2000.0D" in sparkles
assert "new Vec3(FACE_PARTICLE_SENTINEL, 0.0025D, 0.0D)" in sparkles
assert "DustParticleOptions" not in sparkles

# Compile the actual NeoForge sparkle caller and verify exact partial-shape fallback.
subprocess.run(["python3", "tools/test_salis_hitpoint_fallback.py"], check=True)
