#!/usr/bin/env bash
# Standalone zero-Gradle original BETA26 core parity suite.
# Runs on inexpensive CircleCI Docker executors while GitHub-hosted CI is
# unavailable; never relies on external Minecraft/NeoForge binary downloads.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
OUT=build/core-test-classes
rm -rf "$OUT"
mkdir -p "$OUT"
mapfile -t CORE_SOURCES < <(find src/main/java/dev/ehtid/thaumcraft/port/core -name '*.java' -print | sort)
TEST_ROOT=src/test/java/dev/ehtid/thaumcraft/port/core
TESTS=(
  CoreParityTest
  CrystalFarmerParityTest
  CrystalGrowthParityTest
  CrystalMeshParityTest
  EssentiaJarParityTest
  EssentiaSourceOrderParityTest
  InfusionRitualLedgerParityTest
  CrystalFarmerIngredientProgressTest
  CrystalFarmerFinishCheckpointTest
  LegacyTheorycraftSourceVisParityTest
)
TEST_SOURCES=()
for cls in "${TESTS[@]}"; do TEST_SOURCES+=("$TEST_ROOT/$cls.java"); done
javac --release 21 -d "$OUT" "${CORE_SOURCES[@]}" "${TEST_SOURCES[@]}"
while IFS='|' read -r cls expected; do
  echo "== Java parity: $cls"
  output="$(java -cp "$OUT" "dev.ehtid.thaumcraft.port.core.$cls")"
  echo "$output"
  grep -Fq -- "$expected" <<< "$output" || {
    printf >&2 'Parity result missing expected marker: %s (%s)\n' "$expected" "$cls"
    exit 1
  }
done <<'CHECKS'
CoreParityTest|Core parity tests: OK (
CrystalFarmerParityTest|Crystal farmer BETA26 parity: OK
CrystalGrowthParityTest|Crystal growth BETA26 parity: OK
CrystalMeshParityTest|CrystalModel seeded selection parity OK
EssentiaJarParityTest|BETA26 jar parity: OK
EssentiaSourceOrderParityTest|BETA26 essentia source search parity: OK
InfusionRitualLedgerParityTest|BETA26 infusion ritual resource ledger: OK
CrystalFarmerIngredientProgressTest|BETA26 Crystal Farmer ingredient countdown: OK
CrystalFarmerFinishCheckpointTest|BETA26 Crystal Farmer finish reservation: OK
LegacyTheorycraftSourceVisParityTest|Original BETA26 CardInfuse explicit ore-tag Vis: OK
CHECKS
echo CIRCLECI_THAUMCRAFT_CORE_PARITY_OK
