#!/usr/bin/env python3
"""Exercise the generated six-crystal shell retry with mocked IO, not fake game evidence.

A real placement and PCM attribution are still required in Minecraft CI.
This checks that the shell retries a *new* capture exactly once, preserves
failure when both original-audio matches fail, and cannot reuse old markers.
"""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

generator = subprocess.run(
    [sys.executable, "tools/client_crystal_variants_place_smoke.py"],
    text=True, capture_output=True, check=True,
)
script = generator.stdout
subprocess.run(["bash", "-n"], input=script, text=True, check=True)
header = "  for variant in aer ignis terra ordo perditio vitium; do"
tail = "  if grep -Eiq 'Unknown sound event"
assert script.count(header) == 1 and script.count(tail) == 1
body = script.split(header, 1)[1].split(tail, 1)[0]
body = "  for variant in aer; do" + body
assert "for attempt in 1 2; do" in body
assert "CRYSTAL_TARGET_EMPTY_${variant}_${attempt}" in body
assert "CRYSTAL_REAL_PLACED_${variant}_${attempt}" in body
assert "client-smoke/crystal-$variant-$attempt-event.pcm" in body
assert "if python3 tools/match_crystal_audio_pcm.py" in body

MOCK_HEADER = r"""
set -Eeuo pipefail
mkdir -p client-smoke/screenshots
: > client-smoke/client.log
: > client-smoke-status.txt
echo 0 > match_calls
send_chat() {
  case "$1" in
    *"run say CRYSTAL_TARGET_EMPTY_"*)
      echo "${1##*say }" >> client-smoke/client.log ;;
    *"run say CRYSTAL_REAL_PLACED_"*)
      if [ "${MOCK_NO_PLACEMENT:-0}" != 1 ]; then
        echo "${1##*say }" >> client-smoke/client.log
      fi ;;
  esac
}
xdotool() { :; }
import() { :; }
sleep() { :; }
timeout() { :; }
python3() {
  test "$1" = tools/match_crystal_audio_pcm.py
  local n outcome
  n=$(cat match_calls)
  n=$((n + 1))
  echo "$n" > match_calls
  outcome="${MOCK_MATCH_OUTCOMES:$((n - 1)):1}"
  if [ "$outcome" = S ]; then
    echo "CRYSTAL_SOURCE_MATCH=PASS correlation=0.35"
    return 0
  fi
  echo "Game PCM not attributable to original crystal.ogg" >&2
  return 1
}
"""


def check_case(outcomes: str, expected_code: int, expected_attempts: int,
               no_placement: bool = False) -> None:
    with tempfile.TemporaryDirectory(prefix="tc-crystal-retry-") as temp:
        env = dict(os.environ, MOCK_MATCH_OUTCOMES=outcomes,
                   MOCK_NO_PLACEMENT="1" if no_placement else "0")
        result = subprocess.run(
            ["bash", "-c", MOCK_HEADER + body], cwd=temp, env=env,
            text=True, capture_output=True,
        )
        status = (Path(temp) / "client-smoke-status.txt").read_text()
        count = int((Path(temp) / "match_calls").read_text())
        assert (result.returncode == 0) == (expected_code == 0), (
            outcomes, result.returncode, result.stderr, status
        )
        assert status.count("CRYSTAL_VARIANT_ATTEMPT=") == expected_attempts, status
        assert count == (0 if no_placement else expected_attempts), (count, status)
        if expected_code == 0:
            assert status.count("CRYSTAL_REAL_PLACE_ORIGINAL_PCM_aer=PASS") == 1, status
            assert "CRYSTAL_REAL_PLACE_ORIGINAL_PCM_aer=FAIL" not in status, status
        elif no_placement:
            assert "CRYSTAL_REAL_PLACE_FAILED=aer:1" in status, status
            assert "CRYSTAL_REAL_PLACE_ORIGINAL_PCM_aer=PASS" not in status, status
        else:
            assert "CRYSTAL_REAL_PLACE_ORIGINAL_PCM_aer=FAIL" in status, status
            assert "CRYSTAL_REAL_PLACE_ORIGINAL_PCM_aer=PASS" not in status, status
        if expected_attempts == 2:
            assert "CRYSTAL_TARGET_EMPTY_aer_2" in (
                Path(temp) / "client-smoke/client.log"
            ).read_text(), status


check_case("S", 0, 1)
check_case("FS", 0, 2)
check_case("FF", 1, 2)
check_case("S", 1, 1, no_placement=True)
print("Six-crystal generated retry: 4 positive/negative shell paths PASS")
