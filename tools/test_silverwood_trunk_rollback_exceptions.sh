#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
javac --release 21 -d "$tmp" \
  src/main/java/dev/ehtid/thaumcraft/port/core/SilverwoodTrunkRollback.java \
  src/test/java/dev/ehtid/thaumcraft/port/core/SilverwoodTrunkRollbackExceptionTest.java
output="$(java -cp "$tmp" dev.ehtid.thaumcraft.port.core.SilverwoodTrunkRollbackExceptionTest)"
printf '%s\n' "$output"
[[ "$output" == 'SilverwoodTrunkRollbackExceptionTest: OK (11 assertions)' ]] || {
  echo 'Unexpected trunk rollback regression summary' >&2
  exit 1
}
