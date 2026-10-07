#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="build/core-test-classes"
rm -rf "$OUT"
mkdir -p "$OUT"
mapfile -t CORE_SOURCES < <(find src/main/java/dev/ehtid/thaumcraft/port/core -name '*.java' -print | sort)
javac --release 21 -d "$OUT" "${CORE_SOURCES[@]}" src/test/java/dev/ehtid/thaumcraft/port/core/CoreParityTest.java
java -cp "$OUT" dev.ehtid.thaumcraft.port.core.CoreParityTest
python3 tools/audit_stage10.py
