#!/usr/bin/env python3
"""Source-level regression for unloaded-neighbor Salis face FX.

This checks the actual production source's control flow, not a Minecraft
chunk-ticket measurement or a native gameplay test.
"""
from pathlib import Path
import re

source = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisMundusSparkles.java").read_text()
# The clicked block must be loaded before any trail is queued or shape read.
method = source[source.index("public static void sendToActor("):]
entry = re.search(
    r"public static void sendToActor\([^)]*\)\s*\{\s*"
    r"(?://[^\n]*\n\s*)*"
    r"if \(!level\.hasChunkAt\(pos\)\) \{\s*return;\s*\}",
    method,
)
assert entry, "Clicked chunk must be checked before Salis FX enqueue/shape access"
assert method.index("if (!level.hasChunkAt(pos))") < method.index("SalisSparkleTrailQueue.emitOrQueue(")
assert method.index("if (!level.hasChunkAt(pos))") < method.index("level.getBlockState(pos)")

start = source.index("for (Direction face : Direction.values())")
end = source.index("for (int i = 0; i < perFace; i++)", start)
body = source[start:end]
expected = re.compile(
    r"BlockPos adjacent = pos\.relative\(face\);\s*"
    r"(?:\/\/[^\n]*\n\s*)*"
    r"if \(level\.hasChunkAt\(adjacent\)\) \{\s*"
    r"var neighbor = level\.getBlockState\(adjacent\);\s*"
    r"if \(neighbor\.isSolidRender\(level, adjacent\)\s*"
    r"\|\| neighbor\.isFaceSturdy\(level, adjacent, face\.getOpposite\(\)\)\) \{\s*"
    r"continue;\s*\}\s*\}",
    re.MULTILINE,
)
assert expected.search(body), "Neighbor state must be read only when its chunk is loaded"
assert body.count("level.getBlockState(adjacent)") == 1, "Unexpected unguarded neighbor read"
assert "level.getBlockState(pos)" in source, "Clicked block shape source unexpectedly removed"

# Smoke evidence must be emitted by the real server-side particle-send path,
# never merely when an effect is admitted to the queue.
queue = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisSparkleTrailQueue.java").read_text()
marker = 'ThaumcraftSmokeAudit.mark("SALIS_PARTICLE_SEND_INVOKED");'
assert marker in queue[queue.index("private static void emit("):], "Missing dispatch evidence"
assert marker not in queue[:queue.index("private static void emit(")], "Dispatch marker emitted before send"
assert queue.index("level.sendParticles(") < queue.index(marker), "Dispatch marker must follow sendParticles"
assert "SALIS_SPARKLES_PACKETS_SENT" not in source, "Misleading queue-admission marker reintroduced"
smoke = Path("tools/client_dust_sound_smoke.py").read_text()
assert "SALIS_PARTICLE_SEND_INVOKED" in smoke, "Client smoke must check dispatch marker"

print("SALIS_UNLOADED_NEIGHBOR_SOURCE_GUARD=PASS (source contract only)")
