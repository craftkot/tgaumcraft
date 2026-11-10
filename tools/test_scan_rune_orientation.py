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
import org.joml.Quaternionf;
public class Camera {
  public final float yaw;
  private final Quaternionf rotation;
  public Camera(float yaw) { this.yaw=yaw; this.rotation=Quaternionf.camera(yaw); }
  public Quaternionf rotation() { return rotation; }
}
""",
    "org/joml/Quaternionf.java": """
package org.joml;
public class Quaternionf {
  public float yaw, tip;
  public int setCalls, yawCalls, tipCalls;
  public boolean cameraSeed;
  public static Quaternionf camera(float yaw) {
    Quaternionf q = new Quaternionf(); q.yaw=yaw; q.cameraSeed=true; return q;
  }
  public Quaternionf set(Quaternionf other) {
    yaw=other.yaw; tip=other.tip; cameraSeed=other.cameraSeed; setCalls++; return this;
  }
  public Quaternionf rotateY(float value) { yaw += value; yawCalls++; return this; }
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
    // Deliberately model Minecraft's nonzero randomized velocity from the
    // (level,x,y,z,dx,dy,dz) constructor, even when dx/dy/dz are zero.
    this.xd=0.07; this.yd=0.03; this.zd=-0.05;
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
      Camera camera = new Camera(0.25F);
      Quaternionf first = new Quaternionf();
      particle.getFacingCameraMode().setRotation(first,camera,0.0F);
      int quarter = Math.round((first.yaw - camera.yaw) / Mth.HALF_PI);
      yawSteps.add(quarter);
      check(quarter>=0 && quarter<4, "camera-relative yaw must be a quarter turn");
      check(first.cameraSeed && first.setCalls==1,
          "rotation must start from the active camera quaternion");
      check(Math.abs((first.yaw-camera.yaw)-quarter*Mth.HALF_PI)<0.0001F,
          "camera-relative yaw must be exact quarter turn");
      check(first.yawCalls==1 && first.tipCalls==1,
          "rotation must use camera, Y then Z once");
      check(Math.abs(first.tip-Mth.HALF_PI)<0.0001F,
          "original rune plane must tip 90 degrees around Z");
      check(particle.roll==0.0F && particle.oRoll==0.0F,
          "legacy rotation is supplied by the camera-relative quaternion");
      check(particle.lifetime==45, "block scan lifetime must stay 45 ticks");
      check(particle.xd==0.0 && particle.yd==0.0 && particle.zd==0.0,
          "BETA26 rune starts stationary despite vanilla random velocity");
      // BETA26: glRotatef(worldYaw,Y); glRotatef(90,Z);
      // glTranslated(ofx, ofy, -0.51). The -0.51 displacement must be
      // rotated onto one of four block faces, not left at the block center.
      double px = particle.x;
      double py = particle.y - 64.0;
      double pz = particle.z;
      check(py>=-0.00001 && py<0.20001,
          "local ofx must become world vertical offset [0,.2)");
      switch (quarter) {
        case 0 -> {
          check(Math.abs(pz+0.51)<0.00001, "north face offset");
          check(Math.abs(px)<0.30001, "north tangential jitter");
        }
        case 1 -> {
          check(Math.abs(px+0.51)<0.00001, "west face offset");
          check(Math.abs(pz)<0.30001, "west tangential jitter");
        }
        case 2 -> {
          check(Math.abs(pz-0.51)<0.00001, "south face offset");
          check(Math.abs(px)<0.30001, "south tangential jitter");
        }
        case 3 -> {
          check(Math.abs(px-0.51)<0.00001, "east face offset");
          check(Math.abs(pz)<0.30001, "east tangential jitter");
        }
        default -> throw new AssertionError("invalid quarter");
      }
      check(particle.xo==particle.x && particle.yo==particle.y &&
            particle.zo==particle.z, "no first-frame interpolation from anchor");
      particle.tick();
      check(particle.x==px && particle.z==pz,
          "rune must not drift horizontally after a real tick");
      Camera camera2 = new Camera(-0.375F);
      Quaternionf second = new Quaternionf();
      particle.getFacingCameraMode().setRotation(second,camera2,0.9F);
      check(second.cameraSeed && second.setCalls==1,
          "each render must seed from its active camera");
      check(Math.abs((first.yaw-camera.yaw)-(second.yaw-camera2.yaw))<0.0001F
              && first.tip==second.tip,
          "camera yaw changes must preserve the same legacy local quarter-turn");
    }
    check(yawSteps.size()==4, "all four original quarter-turns must occur");
    System.out.println("ScanRuneOrientationTest PASS: "+assertions+" assertions");
  }
}
""",
}

smoke_source = (ROOT / "tools/client_scan_rune_smoke.py").read_text(encoding="utf-8")
for marker in (
    "crop_x=$((X + WIDTH * 424 / 1000))",
    "crop_y=$((Y + HEIGHT * 396 / 1000))",
    "crop_width=$((WIDTH * 152 / 1000))",
    "crop_height=$((HEIGHT * 203 / 1000))",
    "RUNE_VISUAL_CROP=block-face",
):
    if marker not in smoke_source:
        raise AssertionError(f"real-client visual crop contract missing: {marker}")
if "WIDTH / 2 - 150" in smoke_source or '300x300+$crop_x+$crop_y' in smoke_source:
    raise AssertionError("scan-rune framebuffer check must not include moving sky/clouds")

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
