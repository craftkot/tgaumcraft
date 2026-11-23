#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac --release 21 -d "$out" \
  src/main/java/dev/ehtid/thaumcraft/port/core/GreatwoodTrunkVolume.java \
  src/main/java/dev/ehtid/thaumcraft/port/core/GreatwoodTrunkPlan.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/GreatwoodTrunkPlanTest.java
java -cp "$out" dev.ehtid.thaumcraft.port.core.GreatwoodTrunkPlanTest
