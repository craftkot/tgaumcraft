#!/usr/bin/env python3
"""Source-faithful BETA26 Salis hand-trail and exposed-face FXGeneric sprite guards."""
from pathlib import Path
import hashlib
import json
import struct

root = Path("src/main/resources/assets/thaumcraft")
atlas = root / "textures/particle/particles.png"
data = atlas.read_bytes()
assert data.startswith(bytes.fromhex("89504e470d0a1a0a")), atlas
assert struct.unpack(">II", data[16:24]) == (1024, 1024), "BETA26 particles atlas must remain 1024x1024"
git_blob_sha = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
assert git_blob_sha == "f958edbcbe774c924690562a1436d12fea5846b8", "particles.png must be byte-identical to BETA26"
assert json.loads(atlas.with_name("particles.png.mcmeta").read_text()) == {"texture": {"blur": True}}
assert json.loads((root / "particles/salis_sparkle.json").read_text()) == {"textures": ["thaumcraft:particles"]}

item = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisMundusItem.java").read_text()
fx = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisMundusSparkles.java").read_text()
queue = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisSparkleTrailQueue.java").read_text()
particle = Path("src/neoforge/java/dev/ehtid/thaumcraft/client/SalisSparkleParticle.java").read_text()
registration = Path("src/neoforge/java/dev/ehtid/thaumcraft/registry/SalisSparkleParticleRegistration.java").read_text()
client_events = Path("src/neoforge/java/dev/ehtid/thaumcraft/client/SalisSparkleParticleClientEvents.java").read_text()

gate = item.index("DustTarget.NONE")
sound = item.index("new ClientboundSoundPacket(")
particles = item.index("SalisMundusSparkles.sendToActor(")
consume = item.index("stack.shrink(1)")
assert gate < sound < particles < consume, "particles must follow authoritative valid-target gate"
assert "SalisMundusSparkles.sendToActor(level, serverPlayer, pos, context.getHand())" in item

assert "TRAIL_COUNT = 50" in fx
assert "SalisSparkleParticleRegistration.TYPE" in fx
assert "velocity.z + (floaty ? 1000.0D : 0.0D)" in fx
assert "FACE_PARTICLE_SENTINEL = 2000.0D" in fx
assert "new Vec3(FACE_PARTICLE_SENTINEL, 0.0025D, 0.0D)" in fx
assert "DustParticleOptions" not in fx
assert "SalisSparkleHandOrigin.position(" in fx
assert "player.getBoundingBox().minY" in fx
assert "player.getBbHeight()" in fx
assert "player.getYRot()" in fx
assert "player.pick(6.0D, 1.0F, false)" in fx
assert "picked instanceof BlockHitResult blockHit" in fx
assert "blockHit.getBlockPos().equals(pos)" in fx
assert "faceStart = blockHit.getLocation()" in fx
assert 'ThaumcraftSmokeAudit.mark("SALIS_FACE_HITPOINT_PICKED")' in fx
assert 'ThaumcraftSmokeAudit.mark("SALIS_FACE_HITPOINT_PICK_FALLBACK")' in fx
assert "hand == InteractionHand.OFF_HAND" in fx
assert "HumanoidArm" not in fx
assert "Vec3 start = new Vec3(origin[0], origin[1], origin[2]);" in fx
assert "Vec3 eye = player.getEyePosition();" in fx
assert "for (Direction face : Direction.values())" in fx
assert "SalisSparkleFaceLayout.particlesPerFace(" in fx
assert "neighbor.isSolidRender(level, adjacent)" in fx
assert "neighbor.isFaceSturdy(level, adjacent, face.getOpposite())" in fx
assert fx.count("SalisSparkleFaceLayout.coordinate(") == 3
layout = Path("src/main/java/dev/ehtid/thaumcraft/port/core/visual/SalisSparkleFaceLayout.java").read_text()
assert "FACE_OFFSET = 0.51D" in layout and "SPREAD = 0.6D" in layout
assert "SalisSparkleFaceDelayRules.delayTicks(" in fx
assert "SalisSparkleTrailQueue.emitOrQueue(" in fx
assert "SalisSparkleTrailQueue.emitFaceOrQueue(" in fx
assert "level.random.nextInt(5)" in fx

assert "ParticleOptions" in queue
assert "DustParticleOptions" not in queue
assert queue.count("level.sendParticles(player,") == 1
assert "SALIS_PARTICLE_SEND_INVOKED" in queue
assert "SALIS_TRAIL_DELAY_QUEUE_DRAINED" in queue
assert "SALIS_FACE_DELAY_QUEUE_DRAINED" in queue

assert "GRID_SIZE = 64" in particle
assert "FRAME_COUNT = 16" in particle
assert "320 : 512" in particle
assert "64 + level.random.nextInt(16)" in particle
assert "this.face = xSpeed >= FACE_SENTINEL * 0.5D" in particle
assert "this.gravity = this.face ? 0.01F : (floaty ? 0.2F : 0.5F)" in particle
assert "this.slowDown = this.face ? 1.0D" in particle
assert "SalisSparkleScaleRules.blockFace(level.random.nextGaussian())" in particle
assert "this.face ? scaleStart * 2.0F : 1.0F" in particle
assert particle.count("this.level.random.nextGaussian()") == 3
assert "this.random.nextGaussian()" not in particle
assert "this.yd -= 0.04D * this.gravity" in particle
assert "TC_SMOKE_SALIS_SPARKLE_CREATED=YES" in particle
assert "TC_SMOKE_SALIS_SPARKLE_RENDERED=YES" in particle
assert "TC_SMOKE_SALIS_SPARKLE_EXPIRED=YES" in particle
assert "TC_SMOKE_SALIS_FACE_SPARKLE_CREATED=YES" in particle
assert "TC_SMOKE_SALIS_FACE_SPARKLE_RENDERED=YES" in particle
assert "TC_SMOKE_SALIS_FACE_SPARKLE_EXPIRED=YES" in particle
assert "this.atlas = sprites.get(0, 1)" in particle
assert "this.setSprite(this.atlas)" in particle
assert "RegisterEvent event" in registration
assert "Registries.PARTICLE_TYPE" in registration
assert "helper.register(ID, TYPE)" in registration
assert "registerSpriteSet(type, SalisSparkleParticle.Provider::new)" in client_events
print("BETA26 Salis hand trail + exposed-face burst: original 320/512 FXGeneric sprites, 64-79 tick motion and source-specific face physics: PASS")
