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
  LegacyVoidJarParityTest
  LegacyWardedJarRecipeParityTest
  InfusionSkullStabilityParityTest
  LegacyEssentiaTubeParityTest
  LegacyEssentiaTubeRecipeTest
  LegacyEssentiaTubeUpgradeRecipesTest
  LegacyBellowsArcaneRecipeTest
  LegacyTubeBufferArcaneRecipeTest
  LegacyAlembicArcaneRecipeTest
  LegacyAlchemyFilterArcaneRecipeTest
  LegacyTubeFilterArcaneRecipeTest
  LegacyTubeVariantParityTest
  LegacyTubeBufferParityTest
  LegacyAlembicParityTest
  LegacySmelterParityTest
  AlchemyTransportCycleParityTest
  LegacySmelterArcaneRecipeTest
  PhialJarExchangeTest
  EssentiaSourceOrderParityTest
  InfusionRitualLedgerParityTest
  CrystalFarmerIngredientProgressTest
  CrystalFarmerFinishCheckpointTest
  LegacyTheorycraftSourceVisParityTest
  LegacyTheorycraftSeedDataIntegrityParityTest
  LegacyTheorycraftSessionFieldIntegrityParityTest
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
LegacyVoidJarParityTest|BETA26 void jar parity: OK
LegacyWardedJarRecipeParityTest|BETA26 warded/void jar arcane recipes: OK
InfusionSkullStabilityParityTest|BETA26 skull infusion stabilizers: OK
LegacyEssentiaTubeParityTest|BETA26 essentia tube pressure and one-unit transfer: OK
LegacyEssentiaTubeRecipeTest|BETA26 eight-tube arcane crafting: OK
LegacyEssentiaTubeUpgradeRecipesTest|BETA26 tube upgrades: OK
LegacyBellowsArcaneRecipeTest|BETA26 bellows arcane crafting: OK
LegacyTubeBufferArcaneRecipeTest|BETA26 essentia buffer arcane crafting: OK
LegacyAlembicArcaneRecipeTest|BETA26 alembic arcane crafting: OK
LegacyAlchemyFilterArcaneRecipeTest|BETA26 alchemy filter arcane crafting: OK
LegacyTubeFilterArcaneRecipeTest|BETA26 filtered tube arcane crafting: OK
LegacyTubeVariantParityTest|BETA26 filter valve restrict oneway suction and direction: OK
LegacyTubeBufferParityTest|BETA26 mixed essentia buffer, choke, and priority: OK
LegacyAlembicParityTest|BETA26 alembic two-pass column and extraction: OK
LegacySmelterParityTest|BETA26 smelter retention, speed and capacity: OK
AlchemyTransportCycleParityTest|BETA26 smelter-alembic-tube-jar conservation: OK
LegacySmelterArcaneRecipeTest|BETA26 basic smelter arcane crafting: OK
PhialJarExchangeTest|BETA26 phial/jar conservation: OK
EssentiaSourceOrderParityTest|BETA26 essentia source search parity: OK
InfusionRitualLedgerParityTest|BETA26 infusion ritual resource ledger: OK
CrystalFarmerIngredientProgressTest|BETA26 Crystal Farmer ingredient countdown: OK
CrystalFarmerFinishCheckpointTest|BETA26 Crystal Farmer finish reservation: OK
LegacyTheorycraftSourceVisParityTest|Original BETA26 CardInfuse explicit ore-tag Vis: OK
LegacyTheorycraftSeedDataIntegrityParityTest|BETA26 seeded alchemy and celestial card integrity: OK
LegacyTheorycraftSessionFieldIntegrityParityTest|BETA26 session-dependent theory card integrity: OK
CHECKS
echo CIRCLECI_THAUMCRAFT_CORE_PARITY_OK
