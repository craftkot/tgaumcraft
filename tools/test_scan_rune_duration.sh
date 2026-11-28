#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
core="src/main/java/dev/ehtid/thaumcraft/port/core"
javac --release 21 -d "$tmp" \
  "$core/LegacyScanRuneDuration.java" \
  "src/test/java/dev/ehtid/thaumcraft/port/core/LegacyScanRuneDurationTest.java"
java -cp "$tmp" dev.ehtid.thaumcraft.port.core.LegacyScanRuneDurationTest
