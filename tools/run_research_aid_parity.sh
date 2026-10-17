#!/usr/bin/env bash
# Research lane only: compile the real loader-neutral core and aid regression.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
OUT=build/research-aid-parity-classes
mkdir -p "$OUT"
mapfile -t SOURCES < <(find src/main/java/dev/ehtid/thaumcraft/port/core -name '*.java' -print | sort)
javac --release 21 -d "$OUT" "${SOURCES[@]}" \
  src/test/java/dev/ehtid/thaumcraft/port/core/ResearchAidDiscoveryParityTest.java
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.ResearchAidDiscoveryParityTest
