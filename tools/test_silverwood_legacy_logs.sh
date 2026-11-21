#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
javac --release 21 -d "$tmp" \
  src/main/java/dev/ehtid/thaumcraft/port/core/SilverwoodLegacyFootprint.java \
  src/main/java/dev/ehtid/thaumcraft/port/core/SilverwoodLegacyLogs.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/SilverwoodLegacyLogsTest.java
java -cp "$tmp" dev.ehtid.thaumcraft.port.core.SilverwoodLegacyLogsTest
