#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

javac --release 21 -d "$TMP" \
  src/main/java/dev/ehtid/thaumcraft/port/core/visual/LegacyCrucibleBubbleRules.java \
  src/main/java/dev/ehtid/thaumcraft/port/core/visual/LegacyCrucibleFrothRules.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/LegacyCrucibleFrothRulesTest.java
java -cp "$TMP" dev.ehtid.thaumcraft.port.core.LegacyCrucibleFrothRulesTest
