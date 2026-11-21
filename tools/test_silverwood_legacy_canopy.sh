#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
base=src/main/java/dev/ehtid/thaumcraft/port/core
javac --release 21 -d "$tmp" "$base/WorldgenChunkGate.java" "$base/WorldgenHeightGate.java" "$base/WorldgenPlacementGate.java" "$base/SilverwoodLegacyFootprint.java" "$base/SilverwoodLegacyCanopy.java" src/test/java/dev/ehtid/thaumcraft/port/core/SilverwoodLegacyCanopyTest.java
java -cp "$tmp" dev.ehtid.thaumcraft.port.core.SilverwoodLegacyCanopyTest
