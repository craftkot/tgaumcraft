#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cd "$ROOT"

javac --release 21 -encoding UTF-8 -d "$TMP" \
  src/main/java/dev/ehtid/thaumcraft/port/core/AspectData.java \
  src/main/java/dev/ehtid/thaumcraft/port/core/AspectRegistry.java \
  src/main/java/dev/ehtid/thaumcraft/port/core/LegacyEssentiaJarLogic.java \
  src/main/java/dev/ehtid/thaumcraft/port/core/LegacyEssentiaJarRenderLogic.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/LegacyEssentiaJarRenderLogicTest.java

java -ea -cp "$TMP" dev.ehtid.thaumcraft.port.core.LegacyEssentiaJarRenderLogicTest

python3 tools/assert_client_jar_liquid_smoke.py --self-test
