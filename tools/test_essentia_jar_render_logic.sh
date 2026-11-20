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
  src/main/java/dev/ehtid/thaumcraft/port/core/LegacyEssentiaJarMetalPartsLogic.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/LegacyEssentiaJarRenderLogicTest.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/LegacyEssentiaJarMetalPartsParityTest.java

java -ea -cp "$TMP" dev.ehtid.thaumcraft.port.core.LegacyEssentiaJarRenderLogicTest
java -ea -cp "$TMP" dev.ehtid.thaumcraft.port.core.LegacyEssentiaJarMetalPartsParityTest

python3 tools/assert_client_jar_liquid_smoke.py --self-test
python3 tools/assert_client_jar_metal_parts.py --self-test
python3 tools/test_client_jar_liquid_probe.py
python3 tools/test_client_jar_metal_parts.py
python3 tools/verify_jar_brine_source_art.py
