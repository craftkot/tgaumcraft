#!/usr/bin/env python3
"""Narrow source-contract regression for BETA26 Salis sparkle fallback.

Does not claim pixel parity: BETA26 uses FXDispatcher custom sprites/lifetimes,
whereas this port currently sends actor-only vanilla colored dust particles.
"""
from pathlib import Path
import re

item = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisMundusItem.java").read_text()
fx = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisMundusSparkles.java").read_text()

gate = item.index("DustTarget.NONE")
packet = item.index("new ClientboundSoundPacket(")
particles = item.index("SalisMundusSparkles.sendToActor(")
consume = item.index("stack.shrink(1)")
assert gate < packet < particles < consume, "particles must follow authoritative valid-target gate"
assert "if (context.getLevel() instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer)" in item
assert "SalisMundusSparkles.sendToActor(level, serverPlayer, pos, context.getHand())" in item

assert "TRAIL_COUNT = 50" in fx
assert "LEGACY_BLOCK_SPARKLES_PER_EDGE = 20" in fx
assert "for (Direction face : Direction.values())" in fx
assert "2 * Math.max(1, (int) ((bounds.getXsize()" in fx
assert "neighbor.isSolidRender(level, adjacent)" in fx
assert "neighbor.isFaceSturdy(level, adjacent, face.getOpposite())" in fx
assert "if (face.getStepX() == 0)" in fx
assert "if (face.getStepY() == 0)" in fx
assert "if (face.getStepZ() == 0)" in fx
assert fx.count("Mth.clamp(") == 3
assert "pos.getX() + level.random.nextDouble()" not in fx
assert "pos.getY() + level.random.nextDouble()" not in fx
assert "pos.getZ() + level.random.nextDouble()" not in fx
assert "i < TRAIL_COUNT / 3" in fx
assert "travel.x / 6.0D" in fx and "travel.y / 6.0D" in fx and "travel.z / 6.0D" in fx
assert "(floaty ? 0.05D : 0.15D)" in fx
assert "level.random.nextGaussian() * 0.05D" in fx
assert "(189 + level.random.nextInt(67)) / 255.0F" in fx
assert "(64 + level.random.nextInt(192)) / 255.0F" in fx
assert re.search(r"new DustParticleOptions\(new Vector3f\(1\.0F,", fx)
assert fx.count("level.sendParticles(player,") == 2
assert "level.sendParticles(warmDust" not in fx
assert "SALIS_SPARKLES_PACKETS_SENT" in fx
print("BETA26 Salis 50-trail + exposed-face block sparkle geometry/color, actor-only gate: PASS")
