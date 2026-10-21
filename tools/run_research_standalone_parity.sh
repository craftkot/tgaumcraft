#!/usr/bin/env bash
# M07: real Java 21 execution of dependency-free research components.
# Unlike broad core suite this requires neither Gradle nor NeoForge.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
OUT="build/research-standalone-classes"
mkdir -p "$OUT"
javac --release 21 -d "$OUT" \
  src/main/java/dev/ehtid/thaumcraft/port/core/ResearchBrowserPagination.java \
  src/main/java/dev/ehtid/thaumcraft/port/core/LegacyTheorycraftDrawEngine.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/ResearchBrowserPaginationTest.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/ResearchAidDrawStandaloneTest.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/ResearchCardDrawRejectionParityTest.java
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.ResearchBrowserPaginationTest
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.ResearchAidDrawStandaloneTest
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.ResearchCardDrawRejectionParityTest

# Real client Screen hierarchy unavailable in standalone Java; source-based routing regression.
python3 tools/test_research_book_click_routing.py
