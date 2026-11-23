#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
src=src/main/java/dev/ehtid/thaumcraft/port/core
testsrc=src/test/java/dev/ehtid/thaumcraft/port/core
javac --release 21 -d "$tmp" \
  "$src/SilverwoodLegacyFootprint.java" \
  "$src/SilverwoodLegacyLogs.java" \
  "$src/SilverwoodLegacyTreePhases.java" \
  "$testsrc/SilverwoodLegacyTreePhasesTest.java"
java -cp "$tmp" dev.ehtid.thaumcraft.port.core.SilverwoodLegacyTreePhasesTest
