#!/usr/bin/env bash
# M07 category visibility / honest read-only browser core checks.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
OUT=build/research-browser-classes
mkdir -p "$OUT"
mapfile -t CORE_SOURCES < <(find src/main/java/dev/ehtid/thaumcraft/port/core -name '*.java' -print | sort)
javac --release 21 -d "$OUT" "${CORE_SOURCES[@]}" \
  src/test/java/dev/ehtid/thaumcraft/port/core/ResearchBrowserModelTest.java
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.ResearchBrowserModelTest
