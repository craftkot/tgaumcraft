#!/usr/bin/env bash
set -euo pipefail

timeout_seconds="${TC_GAMETEST_TIMEOUT_SECONDS:-300}"
kill_after_seconds="${TC_GAMETEST_KILL_AFTER_SECONDS:-10}"
log_file="${TC_GAMETEST_LOG_FILE:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/aura-and-crystal-gametest.log}"
marker_file="${TC_AURA_COLD_RESTART_MARKER:-$PWD/run/closure-gametest/aura-cold-restart.marker}"
write_log="${log_file%.log}-write.log"
read_log="${log_file%.log}-read.log"
evidence_dir="$PWD/run/closure-gametest/logs"

for value in "$timeout_seconds" "$kill_after_seconds"; do
    if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
        echo "GameTest timeout values must be positive integers" >&2
        exit 2
    fi
done

mkdir -p "$(dirname "$log_file")" "$(dirname "$marker_file")"
rm -rf "$PWD/run/closure-gametest/world"
rm -f "$marker_file"
: > "$write_log"
: > "$read_log"

require_marker() {
    local file="$1"
    local marker="$2"
    if ! grep -Fq -- "$marker" "$file"; then
        echo "Missing required GameTest evidence in $file: $marker" >&2
        return 1
    fi
}

server_process_running() {
    local pid="$1"
    if ! kill -0 "$pid" 2>/dev/null; then
        return 1
    fi
    local state
    state="$(ps -o stat= -p "$pid" 2>/dev/null | awk 'NR == 1 { print $1 }')"
    [[ -n "$state" && "$state" != Z* ]]
}

verify_write_phase() {
    local file="$1"
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_SPREAD_CASE=seagrass result=SPREAD vis=90.0' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_SPREAD_CASE=water result=REJECTED vis=100.0' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_SPREAD_CASE=lava result=REJECTED vis=100.0' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_SPREAD_ACCEPTED=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_WORLDGEN_BORDER_SUPPORT=PASS no_force_load=YES deferred=1 resumed=1 crystal_placed=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_WORKBENCH_HELD_ITEM_OPEN=PASS item_preserved=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_LEGACY_ROCK_VARIANTS=PASS count=27 faces=164' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_STARVATION_AER_MISSING_AURA=PASS size=2' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_STARVATION_VITIUM_MISSING_AURA=PASS size=2' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_STARVATION_ZERO_ISOLATED=PASS' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_STARVATION_ZERO_TOUCHING_SAME=PASS' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_STARVATION_ZERO_TOUCHING_OTHER_VARIANT=PASS' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_STARVATION_UNLOADED_NEIGHBOR=PASS no_force_load=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_SUPPORT_UNLOADED_DEFER=PASS no_force_load=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_SUPPORT_LOADED_RETAIN=PASS' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_SUPPORT_LOADED_REMOVE=PASS drops=1' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_STARVATION_NO_DROPS=PASS' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_STARVATION_NO_AURA_CREATED=PASS' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_STARVATION_ACCEPTED=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_SAMPLE=center biome=minecraft:plains y=50' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_SAMPLE=north biome=minecraft:desert y=50' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_SAMPLE=south biome=minecraft:forest y=50' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_SAMPLE=west biome=minecraft:ocean y=50' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_SAMPLE=east biome=minecraft:swamp y=50' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_GENERATION base=181 vis=181.0 flux=0.0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_REENTRY_PRESERVED=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_SAVE_LOAD base=181 vis=73.0 flux=4.5' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DISK_IO_FLUSH=COMPLETE' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DISK_SAVE file=thaumcraft_aura.dat' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DISK_RELOAD base=181 vis=73.0 flux=4.5' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DISK_REENTRY_PRESERVED=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_NO_FORCE_LOAD=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_GENERATION_ACCEPTED=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_CHUNK_QUEUE_DEFERRED=PASS no_force_load=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_CHUNK_QUEUE_INITIALIZED=PASS' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_CHUNK_QUEUE_ACCEPTED=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_SIMULATE=PASS vis=4.0 flux=2.0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_PARTIAL=PASS vis=6.0 flux=1.0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_CAPPED=PASS vis=0.0 flux=0.0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_NEGATIVE_BETA26=PASS vis=2.0 flux=2.0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_NEGATIVE_CAP_SIMULATE=PASS vis=2.0 flux=2.0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_NEGATIVE_CAP=PASS vis=32766.0 flux=32766.0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_NONFINITE_GUARD=PASS' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_MISSING=PASS' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_ACCEPTED=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_SET_VIS_CLAMP=PASS existing=32766.0 migration=32766.0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_WORLD_TIME_GATE=PASS frozen=0 advanced=1 reverse=1' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_MOON_PHASE_REGEN=PASS phases=8 loaded=1 unloaded=0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_VIS_NEIGHBOR=PASS lowest=2.0 transfer=1.0 conserved=YES threshold=0.75 ceiling=115 unloaded=skipped loaded_source_unloaded_target=skipped no_force_load=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_FLUX_NEIGHBOR=PASS lowest=1.0 transfer=1.0 conserved=YES sourceThreshold=10 ratio=1.75 no_phase_ceiling=YES unloaded=skipped loaded_source_unloaded_target=skipped no_force_load=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_VIS_FLUX_CONVERSION=PASS highVis=conserved lowVis=8phases strictProbability=0.1 rngOrder=shuffleThenConversionThenRift recoveryPriority=YES riftRoll=postConversion unloaded=skipped no_force_load=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_POLLUTE_FLUX_CAPPED=PASS flux=32766.0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_POLLUTE_FLUX_GUARDS=PASS' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_POLLUTE_FLUX_MISSING=PASS' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_POLLUTE_FLUX_ACCEPTED=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_RIFT_TRIGGER=PASS threshold=75.0 chance=0.016 position=chunk_origin transient=YES loaded_only=YES unloaded_skipped=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_SPAWN=PASS size=54 flux=1000_to_945.23 geometry=38' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_THRESHOLD=PASS exact_size=5 no_spawn=YES no_debit=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_NEARBY_GUARD=PASS radius=32 no_spawn=YES no_debit=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_SPAWN_ACCEPTED=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_SPAWN=PASS size=54 flux=1000_to_945.23 geometry=38' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_THRESHOLD=PASS exact_size=5 no_spawn=YES no_debit=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_NEARBY_GUARD=PASS radius=32 no_spawn=YES no_debit=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_SPAWN_ACCEPTED=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_GEOMETRY_BOUNDS=PASS size=YES seed=YES position=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_IMMOBILE=PASS move=ignored position=stable' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_SEGMENT_ENTITY_EFFECTS=PASS damaged=YES items=REMOVED' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_BLOCK_RAYTRACE=PASS breakable=REMOVED unbreakable=RETAINED fluid=IGNORED' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_STABILITY_DECAY=PASS interval=120 delta=-0.2' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_TEST_TICKET_RELEASE=PASS bounded=YES forceTicks=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_COLD_RESTART_WRITE pid=' || return 1
    require_marker "$file" 'All 10 required tests passed' || return 1
    require_marker "$file" 'Stopping server' || return 1
    require_marker "$file" 'Saving worlds' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_COLD_RESTART_PROCESS_EXITED=YES phase=write' || return 1
}

verify_read_phase() {
    local file="$1"
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_SPREAD_SKIPPED_PHASE=read' || return 1
    require_marker "$file" 'TC_GAMETEST_WORKBENCH_HELD_ITEM_OPEN=PASS item_preserved=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_LEGACY_ROCK_VARIANTS=PASS count=27 faces=164' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_STARVATION_SKIPPED_PHASE=read' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_GENERATION_SKIPPED_PHASE=read' || return 1
    require_marker "$file" 'TC_GAMETEST_FLUX_RIFT_SPAWN_SKIPPED_PHASE=read' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_CHUNK_QUEUE_SKIPPED_PHASE=read' || return 1
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_WORLDGEN_BORDER_SUPPORT=PASS no_force_load=YES deferred=1 resumed=1 crystal_placed=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_COLD_RESTART_RESTORED pid=' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_COLD_RESTART_QUEUE_SKIP=PASS existing_record_preserved=YES gaussian=0 local_target_pending=NO' || return 1
    require_marker "$file" 'All 10 required tests passed' || return 1
    require_marker "$file" 'Stopping server' || return 1
    require_marker "$file" 'Saving worlds' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_COLD_RESTART_PROCESS_EXITED=YES phase=read' || return 1
}

run_phase() {
    local phase="$1"
    local phase_log="$2"
    : > "$phase_log"

    set +e
    TC_AURA_COLD_RESTART_PHASE="$phase" \
    TC_AURA_COLD_RESTART_MARKER="$marker_file" \
        timeout --signal=TERM --kill-after="${kill_after_seconds}s" "${timeout_seconds}s" \
        gradle runGameTestServer --no-daemon --console=plain >"$phase_log" 2>&1 &
    local timeout_pid=$!
    set -e

    local shutdown_seen=0
    while kill -0 "$timeout_pid" 2>/dev/null; do
        if grep -Fq 'All 10 required tests passed' "$phase_log" \
                && grep -Fq 'Saving worlds' "$phase_log"; then
            shutdown_seen=1
            # The aura file was explicitly flushed by the GameTest before it
            # succeeds. Give Minecraft's stop path a short grace period, then
            # terminate the still-hanging ModDevGradle wrapper process.
            sleep 3
            kill -TERM "$timeout_pid" 2>/dev/null || true
            break
        fi
        sleep 1
    done

    set +e
    wait "$timeout_pid"
    local gradle_status=$?
    set -e

    if grep -Fq 'All 10 required tests passed' "$phase_log" \
            && grep -Fq 'Saving worlds' "$phase_log"; then
        shutdown_seen=1
    fi
    if [[ "$shutdown_seen" -ne 1 ]]; then
        cat "$phase_log"
        echo "Phase $phase did not reach the GameTest pass summary and world shutdown" >&2
        return 1
    fi

    local server_pid
    if [[ "$phase" == "write" ]]; then
        server_pid="$(sed -n '1p' "$marker_file" 2>/dev/null || true)"
    else
        server_pid="$(sed -n 's/.*TC_GAMETEST_AURA_COLD_RESTART_RESTORED pid=\([0-9][0-9]*\).*/\1/p' "$phase_log" | tail -n 1)"
    fi
    if [[ ! "$server_pid" =~ ^[1-9][0-9]*$ ]]; then
        cat "$phase_log"
        echo "Phase $phase did not expose the Minecraft JVM PID" >&2
        return 1
    fi
    for _ in $(seq 1 10); do
        if ! server_process_running "$server_pid"; then
            break
        fi
        sleep 1
    done
    if server_process_running "$server_pid"; then
        cat "$phase_log"
        echo "Minecraft JVM $server_pid remained alive after world shutdown; cold restart is not accepted" >&2
        return 1
    fi
    echo "TC_GAMETEST_AURA_COLD_RESTART_PROCESS_EXITED=YES phase=$phase pid=$server_pid" >> "$phase_log"

    case "$gradle_status" in
        0)
            echo "TC_GAMETEST_CRYSTAL_SPREAD_RUNNER_EXIT=clean phase=$phase" >> "$phase_log"
            ;;
        124|137|143)
            echo "TC_GAMETEST_CRYSTAL_SPREAD_RUNNER_EXIT=bounded_after_server_shutdown phase=$phase" >> "$phase_log"
            ;;
        *)
            cat "$phase_log"
            echo "Gradle failed after the GameTest server shutdown (exit: $gradle_status)" >&2
            return "$gradle_status"
            ;;
    esac

    cat "$phase_log"
    if [[ "$phase" == "write" ]]; then
        verify_write_phase "$phase_log"
    else
        verify_read_phase "$phase_log"
    fi
}

run_phase write "$write_log"
run_phase read "$read_log"

mkdir -p "$evidence_dir"
cat "$write_log" "$read_log" > "$evidence_dir/aura-cold-restart.log"
cp "$marker_file" "$evidence_dir/aura-cold-restart.marker"
echo "Aura persistence passed a two-process GameTest server restart."
