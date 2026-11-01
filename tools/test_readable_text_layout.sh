#!/usr/bin/env bash
# Standalone regression: no Minecraft/Gradle downloads required.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac --release 21 -d "$OUT" \
  src/main/java/dev/ehtid/thaumcraft/port/core/ReadableTextLayout.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/ReadableTextLayoutTest.java
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.ReadableTextLayoutTest
