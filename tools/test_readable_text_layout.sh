#!/usr/bin/env bash
# Standalone regression: no Minecraft/Gradle downloads required.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac --release 21 -d "$OUT" \
  src/main/java/dev/ehtid/thaumcraft/port/core/ReadableTextLayout.java \
  src/main/java/dev/ehtid/thaumcraft/port/core/visual/WorkbenchVisPanelLayout.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/ReadableTextLayoutTest.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/visual/WorkbenchVisPanelLayoutTest.java
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.ReadableTextLayoutTest
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.visual.WorkbenchVisPanelLayoutTest
