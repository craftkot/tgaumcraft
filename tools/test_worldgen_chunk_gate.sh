#!/usr/bin/env bash
# Isolated M12 pure-Java regression. Does not require Gradle or Minecraft.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac --release 21 -d "$TMP" \
  "$ROOT/src/main/java/dev/ehtid/thaumcraft/port/core/WorldgenChunkGate.java" \
  "$ROOT/src/test/java/dev/ehtid/thaumcraft/port/core/WorldgenChunkGateTest.java"
java -cp "$TMP" dev.ehtid.thaumcraft.port.core.WorldgenChunkGateTest
