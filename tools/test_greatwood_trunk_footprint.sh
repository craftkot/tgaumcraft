#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac --release 21 -d "$TMP" \
  "$ROOT/src/main/java/dev/ehtid/thaumcraft/port/core/GreatwoodTrunkFootprint.java" \
  "$ROOT/src/test/java/dev/ehtid/thaumcraft/port/core/GreatwoodTrunkFootprintTest.java"
java -cp "$TMP" dev.ehtid.thaumcraft.port.core.GreatwoodTrunkFootprintTest
