#!/usr/bin/env python3
"""Source-level regression for unloaded-neighbor Salis face FX.

This checks the actual production source's control flow, not a Minecraft
chunk-ticket measurement or a native gameplay test.
"""
from pathlib import Path
import re

source = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisMundusSparkles.java").read_text()
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
print("SALIS_UNLOADED_NEIGHBOR_SOURCE_GUARD=PASS (source contract only)")
