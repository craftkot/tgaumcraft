#!/usr/bin/env bash
set -euo pipefail

timeout_seconds="${TC_GAMETEST_TIMEOUT_SECONDS:-300}"
kill_after_seconds="${TC_GAMETEST_KILL_AFTER_SECONDS:-10}"
log_file="${TC_GAMETEST_LOG_FILE:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/aura-and-crystal-gametest.log}"

for value in "$timeout_seconds" "$kill_after_seconds"; do
    if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
        echo "GameTest timeout values must be positive integers" >&2
        exit 2
    fi
done

mkdir -p "$(dirname "$log_file")"
: > "$log_file"

set +e
timeout --signal=TERM --kill-after="${kill_after_seconds}s" "${timeout_seconds}s" \
    gradle runGameTestServer --no-daemon --console=plain >"$log_file" 2>&1
gradle_status=$?
set -e

cat "$log_file"

require_marker() {
    if ! grep -Fq -- "$1" "$log_file"; then
        echo "Missing required GameTest evidence: $1" >&2
        return 1
    fi
}

verify_gameplay_pass() {
    require_marker 'TC_GAMETEST_CRYSTAL_SPREAD_CASE=seagrass result=SPREAD vis=90.0' || return 1
    require_marker 'TC_GAMETEST_CRYSTAL_SPREAD_CASE=water result=REJECTED vis=100.0' || return 1
    require_marker 'TC_GAMETEST_CRYSTAL_SPREAD_CASE=lava result=REJECTED vis=100.0' || return 1
    require_marker 'TC_GAMETEST_CRYSTAL_SPREAD_ACCEPTED=YES' || return 1
    require_marker 'TC_GAMETEST_AURA_SAMPLE=center biome=minecraft:plains y=50' || return 1
    require_marker 'TC_GAMETEST_AURA_SAMPLE=north biome=minecraft:desert y=50' || return 1
    require_marker 'TC_GAMETEST_AURA_SAMPLE=south biome=minecraft:forest y=50' || return 1
    require_marker 'TC_GAMETEST_AURA_SAMPLE=west biome=minecraft:ocean y=50' || return 1
    require_marker 'TC_GAMETEST_AURA_SAMPLE=east biome=minecraft:swamp y=50' || return 1
    require_marker 'TC_GAMETEST_AURA_SEALEVEL=center biome=minecraft:desert' || return 1
    require_marker 'TC_GAMETEST_AURA_GENERATION base=181 vis=181.0 flux=0.0' || return 1
    require_marker 'TC_GAMETEST_AURA_REENTRY_PRESERVED=YES' || return 1
    require_marker 'TC_GAMETEST_AURA_SAVE_LOAD base=181 vis=73.0 flux=4.5' || return 1
    require_marker 'TC_GAMETEST_AURA_DISK_SAVE file=thaumcraft_aura.dat' || return 1
    require_marker 'TC_GAMETEST_AURA_DISK_RELOAD base=181 vis=73.0 flux=4.5' || return 1
    require_marker 'TC_GAMETEST_AURA_DISK_REENTRY_PRESERVED=YES' || return 1
    require_marker 'TC_GAMETEST_AURA_NO_FORCE_LOAD=YES' || return 1
    require_marker 'TC_GAMETEST_AURA_GENERATION_ACCEPTED=YES' || return 1
    require_marker 'All 2 required tests passed' || return 1
}

if ! verify_gameplay_pass; then
    echo "Aura/crystal GameTests did not complete successfully (Gradle exit: ${gradle_status})" >&2
    exit 1
fi

case "$gradle_status" in
    0)
        echo 'TC_GAMETEST_CRYSTAL_SPREAD_RUNNER_EXIT=clean'
        ;;
    124|137)
        echo 'TC_GAMETEST_CRYSTAL_SPREAD_RUNNER_EXIT=watchdog_after_all_tests_passed'
        echo 'All required GameTest assertions and the server summary passed; the watchdog only bounded the non-terminating Gradle/GameTest process.'
        ;;
    *)
        echo "Gradle failed after emitting the GameTest success summary (exit: ${gradle_status})" >&2
        exit "$gradle_status"
        ;;
esac
