#!/usr/bin/env bash
# Isolated Java 21 M12 height bounds regression; not a NeoForge/game test.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac --release 21 -d "$TMP" \
  "$ROOT/src/main/java/dev/ehtid/thaumcraft/port/core/WorldgenHeightGate.java" \
  "$ROOT/src/test/java/dev/ehtid/thaumcraft/port/core/WorldgenHeightGateTest.java"
java -cp "$TMP" dev.ehtid.thaumcraft.port.core.WorldgenHeightGateTest
