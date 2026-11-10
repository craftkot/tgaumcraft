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
import org.joml.Vector3f;
public class Camera {
  public final float yaw;
  private final Quaternionf rotation;
  public Camera(float yaw) { this.yaw=yaw; this.rotation=Quaternionf.camera(yaw); }
  public Quaternionf rotation() { return rotation; }
  public Vector3f getLookVector() {
    return new Vector3f((float)Math.sin(yaw),0.0F,(float)Math.cos(yaw));
  }
  public float getYRot() { return (float)Math.toDegrees(yaw); }
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
  public Vector3f transform(Vector3f vector) {
    return vector.rotateZ(tip).rotateY(yaw);
  }
}
""",
    "org/joml/Vector3f.java": """
package org.joml;
public class Vector3f {
  public float x, y, z;
  public Vector3f(float x,float y,float z) {this.x=x;this.y=y;this.z=z;}
  public Vector3f rotateZ(float angle) {
    float c=(float)Math.cos(angle), s=(float)Math.sin(angle);
    float nx=c*x-s*y; y=s*x+c*y; x=nx; return this;
  }
  public Vector3f rotateY(float angle) {
    float c=(float)Math.cos(angle), s=(float)Math.sin(angle);
    float nx=c*x+s*z, nz=-s*x+c*z; x=nx; z=nz; return this;
  }
  public float dot(Vector3f other) {return x*other.x+y*other.y+z*other.z;}
}
""",
    "com/mojang/blaze3d/vertex/VertexConsumer.java": """
package com.mojang.blaze3d.vertex;
public interface VertexConsumer {}
""",
    "net/minecraft/client/multiplayer/ClientLevel.java": """
package net.minecraft.client.multiplayer;
public class ClientLevel {}
""",
    "net/minecraft/client/particle/Particle.java": """
package net.minecraft.client.particle;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Quaternionf;
public class Particle {
  protected static final Random RANDOM = new Random(1234567L);
  public static double lastRenderX,lastRenderY,lastRenderZ,lastRenderXO,lastRenderYO,lastRenderZO;
  public static Quaternionf lastRotation;
  public Random random = RANDOM;
  public double x, y, z, xo, yo, zo, xd, yd, zd;
  public float gravity, alpha, roll, oRoll;
  public int age, lifetime;
  public boolean hasPhysics;
  public boolean removed;
  public Particle(ClientLevel level, double x, double y, double z) {
    this.x=x; this.y=y; this.z=z; this.xo=x; this.yo=y; this.zo=z;
    // Deliberately model Minecraft's nonzero randomized velocity from the
    // (level,x,y,z,dx,dy,dz) constructor, even when dx/dy/dz are zero.
    this.xd=0.07; this.yd=0.03; this.zd=-0.05;
  }
  public double getX(){return x;}
  public double getY(){return y;}
  public double getZ(){return z;}
  public double getXO(){return xo;}
  public double getYO(){return yo;}
  public double getZO(){return zo;}
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
import com.mojang.blaze3d.vertex.VertexConsumer;
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
  public void render(VertexConsumer buffer, Camera camera, float partialTick) {
    lastRenderX=x; lastRenderY=y; lastRenderZ=z;
    lastRenderXO=xo; lastRenderYO=yo; lastRenderZO=zo;
    Quaternionf rotation=new Quaternionf();
    getFacingCameraMode().setRotation(rotation,camera,partialTick);
    lastRotation=rotation;
  }
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
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
public final class ScanRuneOrientationTest {
  private static int assertions = 0;
  private static void check(boolean value, String message) {
    assertions++;
    if (!value) throw new AssertionError(message);
  }
  private static Vector3f expectedOffset(ScanRuneParticle particle, Camera camera) {
    Vector3f offset = new Vector3f(
        particle.localOffsetX, particle.localOffsetY, ScanRuneParticle.LOCAL_OFFSET_Z);
    offset.rotateZ(Mth.HALF_PI).rotateY(particle.legacyCameraYaw);
    return camera.rotation().transform(offset);
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
      check(particle.localOffsetX>=0.0F && particle.localOffsetX<0.2F,
          "original ofx remains in [0,.2)");
      check(particle.localOffsetY>=-0.3F && particle.localOffsetY<0.3F,
          "original ofy remains in [-.3,.3)");
      check(ScanRuneParticle.LOCAL_OFFSET_Z==-0.51F,
          "original local camera-space depth remains -0.51");
      check(particle.getX()==0.0 && particle.getY()==64.0 && particle.getZ()==0.0,
          "spawn keeps the unshifted BETA26 block anchor");

      // BETA26 applies its local translation after the active view matrix.
      // The world offset must therefore be camera.rotation * Ry * Rz * local,
      // recomputed on every render instead of being frozen in world axes.
      Vector3f expectedFirst = expectedOffset(particle,camera);
      particle.render(null,camera,0.0F);
      check(Math.abs(Particle.lastRenderX-expectedFirst.x)<0.0001,
          "rendered X uses the current camera-space offset");
      check(Math.abs(Particle.lastRenderY-(64.0+expectedFirst.y))<0.0001,
          "rendered Y uses the current camera-space offset");
      check(Math.abs(Particle.lastRenderZ-expectedFirst.z)<0.0001,
          "rendered Z uses the current camera-space offset");
      check(Particle.lastRenderXO==Particle.lastRenderX
              && Particle.lastRenderYO==Particle.lastRenderY
              && Particle.lastRenderZO==Particle.lastRenderZ,
          "current and previous positions receive the same render offset");
      check(Particle.lastRotation.cameraSeed && Particle.lastRotation.setCalls==1,
          "real render path still seeds orientation from the active camera");
      check(particle.getX()==0.0 && particle.getY()==64.0 && particle.getZ()==0.0
              && particle.getXO()==0.0 && particle.getYO()==64.0 && particle.getZO()==0.0,
          "temporary camera offset is restored after render");

      Camera camera2 = new Camera(camera.yaw + (float)Math.PI);
      Vector3f expectedSecond = expectedOffset(particle,camera2);
      particle.render(null,camera2,0.9F);
      check(Math.abs(Particle.lastRenderX-expectedSecond.x)<0.0001,
          "opposite camera yaw recomputes rendered X");
      check(Math.abs(Particle.lastRenderY-(64.0+expectedSecond.y))<0.0001,
          "opposite camera yaw recomputes rendered Y");
      check(Math.abs(Particle.lastRenderZ-expectedSecond.z)<0.0001,
          "opposite camera yaw recomputes rendered Z");
      check(Math.abs(expectedFirst.x+expectedSecond.x)<0.0001
              && Math.abs(expectedFirst.z+expectedSecond.z)<0.0001
              && Math.abs(expectedFirst.y-expectedSecond.y)<0.0001,
          "180-degree camera turn moves the local offset to the opposite face");
      double separation=Math.sqrt(
          Math.pow(expectedFirst.x-expectedSecond.x,2)
          +Math.pow(expectedFirst.y-expectedSecond.y,2)
          +Math.pow(expectedFirst.z-expectedSecond.z,2));
      check(separation>1.0,
          "opposite views must differ by the two-sided 0.51-block camera offset");
      check(particle.getX()==0.0 && particle.getY()==64.0 && particle.getZ()==0.0,
          "second render also restores the world anchor");

      particle.tick();
      check(particle.getX()==0.0 && particle.getZ()==0.0,
          "rune must not drift horizontally after a real tick");
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
particle_source = PARTICLE.read_text(encoding="utf-8")
parser_source = (ROOT / "tools/scan_rune_log_parser.py").read_text(encoding="utf-8")
for marker in (
    "camera.rotation().transform(offset)",
    "float frontDot = -offset.dot(look)",
    "this.x += dx",
    "this.xo += dx",
    "finally {",
    "this.x -= dx",
    "this.xo -= dx",
):
    if marker not in particle_source:
        raise AssertionError(f"camera-relative particle transform contract missing: {marker}")

for marker in (
    "crop_x=$((X + WIDTH * 424 / 1000))",
    "crop_y=$((Y + HEIGHT * 396 / 1000))",
    "crop_width=$((WIDTH * 152 / 1000))",
    "crop_height=$((HEIGHT * 203 / 1000))",
    "RUNE_VISUAL_CROP=block-face",
    "RUNE_CAMERA_RELATIVE_OFFSET=PASS",
):
    if marker not in smoke_source:
        raise AssertionError(f"real-client visual crop contract missing: {marker}")
for marker in ("frontDot=(", "cameraYaw=("):
    if marker not in parser_source:
        raise AssertionError(f"scan-rune log parser contract missing: {marker}")
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
