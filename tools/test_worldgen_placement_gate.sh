#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE="$ROOT/src/main/java/dev/ehtid/thaumcraft/port/core"
TEST="$ROOT/src/test/java/dev/ehtid/thaumcraft/port/core"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac --release 21 -d "$TMP" \
  "$CORE/WorldgenChunkGate.java" \
  "$CORE/WorldgenHeightGate.java" \
  "$CORE/WorldgenPlacementGate.java" \
  "$TEST/WorldgenPlacementGateTest.java"
java -cp "$TMP" dev.ehtid.thaumcraft.port.core.WorldgenPlacementGateTest
