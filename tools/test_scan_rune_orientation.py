#!/usr/bin/env python3
"""Compile the actual M16 scan-rune particle with minimal API stubs.

This verifies the original world-space rotation contract independently of
Minecraft startup. It does NOT prove rendered pixels or game-client parity.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
PARTICLE = ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/client/ScanRuneParticle.java"
DURATION = ROOT / "src/main/java/dev/ehtid/thaumcraft/port/core/LegacyScanRuneDuration.java"

STUBS = {
    "net/minecraft/client/Camera.java": """
package net.minecraft.client;
public class Camera {}
""",
    "org/joml/Quaternionf.java": """
package org.joml;
public class Quaternionf {
  public float yaw, tip;
  public int yawCalls, tipCalls;
  public Quaternionf rotationY(float value) { yaw = value; yawCalls++; return this; }
  public Quaternionf rotateZ(float value) { tip = value; tipCalls++; return this; }
}
""",
    "net/minecraft/client/multiplayer/ClientLevel.java": """
package net.minecraft.client.multiplayer;
public class ClientLevel {}
""",
    "net/minecraft/client/particle/Particle.java": """
package net.minecraft.client.particle;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
public class Particle {
  protected static final Random RANDOM = new Random(1234567L);
  public Random random = RANDOM;
  public double x, y, z, xo, yo, zo, xd, yd, zd;
  public float gravity, alpha, roll, oRoll;
  public int age, lifetime;
  public boolean hasPhysics;
  public boolean removed;
  public Particle(ClientLevel level, double x, double y, double z) {
    this.x=x; this.y=y; this.z=z;
  }
  public void setColor(float r,float g,float b) {}
  public void setPos(double x,double y,double z) {this.x=x;this.y=y;this.z=z;}
  public void move(double dx,double dy,double dz) {x+=dx;y+=dy;z+=dz;}
  public void remove() {removed=true;}
  public void tick() {}
  public int getLightColor(float partialTick) {return 0;}
  public ParticleRenderType getRenderType() {return null;}
}
""",
    "net/minecraft/client/particle/SingleQuadParticle.java": """
package net.minecraft.client.particle;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Quaternionf;
public class SingleQuadParticle extends Particle {
  public interface FacingCameraMode {
    void setRotation(Quaternionf rotation, Camera camera, float partialTick);
  }
  public static final FacingCameraMode LOOKAT_XYZ = (q,c,p) -> {};
  public SingleQuadParticle(ClientLevel level,double x,double y,double z) {
    super(level,x,y,z);
  }
  public FacingCameraMode getFacingCameraMode() {return LOOKAT_XYZ;}
}
""",
    "net/minecraft/client/particle/TextureSheetParticle.java": """
package net.minecraft.client.particle;
import net.minecraft.client.multiplayer.ClientLevel;
public class TextureSheetParticle extends SingleQuadParticle {
  protected float quadSize;
  public TextureSheetParticle(ClientLevel level,double x,double y,double z,
                              double dx,double dy,double dz) {
    super(level,x,y,z);this.xd=dx;this.yd=dy;this.zd=dz;
  }
  public void pickSprite(SpriteSet sprites) {}
}
""",
    "net/minecraft/client/particle/ParticleRenderType.java": """
package net.minecraft.client.particle;
public class ParticleRenderType {
  public static final ParticleRenderType PARTICLE_SHEET_TRANSLUCENT =
      new ParticleRenderType();
}
""",
    "net/minecraft/client/particle/SpriteSet.java": """
package net.minecraft.client.particle;
public interface SpriteSet {}
""",
    "net/minecraft/client/particle/ParticleProvider.java": """
package net.minecraft.client.particle;
import net.minecraft.client.multiplayer.ClientLevel;
public interface ParticleProvider<T> {
  Particle createParticle(T type,ClientLevel level,double x,double y,double z,
                          double dx,double dy,double dz);
}
""",
    "net/minecraft/core/particles/SimpleParticleType.java": """
package net.minecraft.core.particles;
public class SimpleParticleType {}
""",
    "net/minecraft/util/Mth.java": """
package net.minecraft.util;
public final class Mth {
  public static final float HALF_PI = (float)(Math.PI / 2.0);
}
""",
    "org/jetbrains/annotations/Nullable.java": """
package org.jetbrains.annotations;
public @interface Nullable {}
""",
    "dev/ehtid/thaumcraft/client/ScanRuneOrientationTest.java": """
package dev.ehtid.thaumcraft.client;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
public final class ScanRuneOrientationTest {
  private static int assertions = 0;
  private static void check(boolean value, String message) {
    assertions++;
    if (!value) throw new AssertionError(message);
  }
  public static void main(String[] args) {
    ScanRuneParticle.Provider provider = new ScanRuneParticle.Provider(new SpriteSet(){});
    Set<Integer> yawSteps = new HashSet<>();
    for (int i=0; i<128; i++) {
      ScanRuneParticle particle = (ScanRuneParticle) provider.createParticle(
          null,new ClientLevel(),0,64,0,0.8,15,0.9);
      Quaternionf first = new Quaternionf();
      particle.getFacingCameraMode().setRotation(first,new Camera(),0.0F);
      int quarter = Math.round(first.yaw / Mth.HALF_PI);
      yawSteps.add(quarter);
      check(quarter>=0 && quarter<4, "yaw must be a world-Y quarter turn");
      check(Math.abs(first.yaw-quarter*Mth.HALF_PI)<0.0001F,
          "yaw must be exact quarter turn");
      check(first.yawCalls==1 && first.tipCalls==1,
          "rotation must use Y then Z once");
      check(Math.abs(first.tip-Mth.HALF_PI)<0.0001F,
          "original rune plane must tip 90 degrees around Z");
      check(particle.roll==0.0F && particle.oRoll==0.0F,
          "billboard roll must not rotate world-space runes");
      check(particle.lifetime==45, "block scan lifetime must stay 45 ticks");
      Quaternionf second = new Quaternionf();
      particle.getFacingCameraMode().setRotation(second,new Camera(),0.9F);
      check(first.yaw==second.yaw && first.tip==second.tip,
          "camera or partial ticks must not change world orientation");
    }
    check(yawSteps.size()==4, "all four original quarter-turns must occur");
    System.out.println("ScanRuneOrientationTest PASS: "+assertions+" assertions");
  }
}
""",
}

with tempfile.TemporaryDirectory(prefix="scan-rune-orientation-") as tmp:
    work = Path(tmp)
    sources = []
    for relative, text in STUBS.items():
        target = work / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")
        sources.append(str(target))
    out = work / "classes"
    out.mkdir()
    subprocess.run(
        ["javac", "--release", "21", "-d", str(out),
         str(DURATION), str(PARTICLE), *sources],
        cwd=ROOT, check=True,
    )
    subprocess.run(
        ["java", "-cp", str(out),
         "dev.ehtid.thaumcraft.client.ScanRuneOrientationTest"],
        cwd=ROOT, check=True,
    )
