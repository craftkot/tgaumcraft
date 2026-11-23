#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
core=src/main/java/dev/ehtid/thaumcraft/port/core
test=src/test/java/dev/ehtid/thaumcraft/port/core
javac --release 21 -d "$tmp" \
  "$core/SilverwoodLegacyFootprint.java" \
  "$core/WorldgenHeightGate.java" \
  "$core/SilverwoodLegacyCanopy.java" \
  "$core/SilverwoodLegacyLogs.java" \
  "$core/SilverwoodLegacyTreePhases.java" \
  "$core/SilverwoodLegacyTree.java" \
  "$test/SilverwoodLegacyTreeTest.java"
java -cp "$tmp" dev.ehtid.thaumcraft.port.core.SilverwoodLegacyTreeTest
