#!/usr/bin/env bash
# M07 category visibility / honest read-only browser core checks.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
OUT=build/research-browser-classes
mkdir -p "$OUT"
mapfile -t CORE_SOURCES < <(find src/main/java/dev/ehtid/thaumcraft/port/core -name '*.java' -print | sort)
javac --release 21 -d "$OUT" "${CORE_SOURCES[@]}" \
  src/test/java/dev/ehtid/thaumcraft/port/core/ResearchBrowserModelTest.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/ResearchBrowserPaginationTest.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/LegacyResearchLayoutTest.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/ResearchBrowserPersistenceParityTest.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/ResearchPageVisibilityTest.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/ResearchAddendumVisibilityTest.java
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.ResearchBrowserModelTest
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.ResearchBrowserPaginationTest
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.LegacyResearchLayoutTest
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.ResearchBrowserPersistenceParityTest
python3 tools/test_research_layout_manifest.py
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.ResearchPageVisibilityTest
python3 tools/test_research_page_manifest.py
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.ResearchAddendumVisibilityTest
