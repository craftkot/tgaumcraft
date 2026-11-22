#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

javac --release 21 -encoding UTF-8 -d "$TMP" \
  "$ROOT/src/main/java/dev/ehtid/thaumcraft/port/core/visual/LegacyWispZapGeometry.java" \
  "$ROOT/src/test/java/dev/ehtid/thaumcraft/port/core/visual/LegacyWispZapGeometryTest.java"

java -cp "$TMP" dev.ehtid.thaumcraft.port.core.visual.LegacyWispZapGeometryTest
