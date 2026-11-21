#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
javac --release 21 -d "$tmp" \
  src/main/java/dev/ehtid/thaumcraft/port/core/SilverwoodTrunkRollback.java \
  src/main/java/dev/ehtid/thaumcraft/port/core/SilverwoodCanopyRollback.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/SilverwoodRollbackFailureInjectionTest.java
# Fail closed if the regression silently loses assertions or never reaches its summary.
output="$(java -cp "$tmp" dev.ehtid.thaumcraft.port.core.SilverwoodRollbackFailureInjectionTest)"
printf '%s\n' "$output"
expected='M12 Silverwood rollback failure injection: OK (43 assertions)'
if [[ "$output" != "$expected" ]]; then
  printf 'Unexpected Silverwood rollback regression summary (expected 43 assertions)\n' >&2
  exit 1
fi
