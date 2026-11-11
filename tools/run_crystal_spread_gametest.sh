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
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_SIMULATE=PASS vis=4.0 flux=2.0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_PARTIAL=PASS vis=6.0 flux=1.0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_CAPPED=PASS vis=0.0 flux=0.0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_NEGATIVE_BETA26=PASS vis=2.0 flux=2.0' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_NONFINITE_GUARD=PASS' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_MISSING=PASS' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_DRAIN_ACCEPTED=YES' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_COLD_RESTART_WRITE pid=' || return 1
    require_marker "$file" 'All 3 required tests passed' || return 1
    require_marker "$file" 'Stopping server' || return 1
    require_marker "$file" 'Saving worlds' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_COLD_RESTART_PROCESS_EXITED=YES phase=write' || return 1
}

verify_read_phase() {
    local file="$1"
    require_marker "$file" 'TC_GAMETEST_CRYSTAL_SPREAD_SKIPPED_PHASE=read' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_GENERATION_SKIPPED_PHASE=read' || return 1
    require_marker "$file" 'TC_GAMETEST_AURA_COLD_RESTART_RESTORED pid=' || return 1
    require_marker "$file" 'All 3 required tests passed' || return 1
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
        if grep -Fq 'All 3 required tests passed' "$phase_log" \
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

    if grep -Fq 'All 3 required tests passed' "$phase_log" \
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
