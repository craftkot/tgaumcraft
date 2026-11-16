#!/usr/bin/env python3
"""Generate a real-client FIRSTSTEPS Salis dust audio smoke without unrelated late tests."""
from pathlib import Path
import sys
source = Path("tools/client_smoke.sh").read_text()
anchor = "  capture 08k-firststeps-real-bookshelf-before-salis\n"
end = "  # A /give or /item replace does not invoke ItemEntityPickupEvent.Post"
if source.count(anchor) != 1 or source.count(end) != 1 or source.count("export ALSOFT_DRIVERS=null") != 1:
    raise SystemExit("Real-client fixture changed; refuse unsafe injection")
replacement = """  # Observe Nitor from the open approach, not from 0.5 blocks beside the bookshelf.
  # The Salis audio probe switches back to its close microphone position below.
  send_chat '/tp @p 2.5 150 8.5 -14 0'
  xdotool key --clearmodifiers F1
  sleep 0.4
  capture 08m-nitor-particles-before
  # BETA26 TileNitor.update is a real client ticker, not a random display tick.
  send_chat '/setblock 3 151 12 thaumcraft:nitor_yellow'
  send_chat '/execute if block 3 151 12 thaumcraft:nitor_yellow run say NITOR_CLIENT_BLOCK_READY'
  grep -Fq 'NITOR_CLIENT_BLOCK_READY' client-smoke/client.log
  for _ in $(seq 1 50); do
    if grep -Fq 'TC_SMOKE_NITOR_CLIENT_TICK=YES ticks=20 flames=20 cores=2' client-smoke/client.log; then
      break
    fi
    sleep 0.1
  done
  sleep 0.25
  capture 08n-nitor-flame-core-active
  python3 tools/verify_nitor_client_tick.py client-smoke/client.log
  echo 'NITOR_CLIENT_TICK=PASS' | tee -a client-smoke-status.txt
  send_chat '/setblock 3 151 12 minecraft:air'
  # Restore the centered bookshelf view; keep the HUD hidden for both captures.
  send_chat '/tp @p 2.5 150 11.5 0 0'
  sleep 0.5
  capture 08k-firststeps-real-bookshelf-before-salis
  pactl list short sinks | grep -Fq 'tc_dust_sink'
  timeout --signal=TERM 2s parec --device=tc_dust_sink.monitor \\
    --format=s16le --rate=48000 --channels=2 --raw \\
    > client-smoke/dust-baseline.pcm || test "$?" -eq 124
  python3 tools/check_dust_capture_noise.py client-smoke/dust-baseline.pcm
  timeout --signal=TERM 5s parec --device=tc_dust_sink.monitor \\
    --format=s16le --rate=48000 --channels=2 --raw \\
    > client-smoke/dust-event.pcm &
  dust_capture_pid=$!
  sleep 0.6
  xdotool click 3
  # BETA26 face particles are staggered by distance*16 + 0..4 ticks.
  # Capture several early frames while the short-lived fallback is visible;
  # do not change pixel thresholds or accept persistent scenery as FX.
  # Real client #41525689264 rendered 92 transient pixels at the first
  # post-delay capture and zero at the second. Sample the shorter-lived
  # interval instead of lowering the unchanged >=100-pixel acceptance gate.
  # Log actual wall-clock capture times; import duration is variable.
  sleep 0.30
  echo "SALIS_FRAME_EARLY_CAPTURE_MS=$(date +%s%3N)"
  import -display :99 -window root -silent client-smoke/screenshots/08k-salis-sparkles-early.png
  test -s client-smoke/screenshots/08k-salis-sparkles-early.png
  sleep 0.18
  echo "SALIS_FRAME_MID_CAPTURE_MS=$(date +%s%3N)"
  import -display :99 -window root -silent client-smoke/screenshots/08k-salis-sparkles-mid.png
  test -s client-smoke/screenshots/08k-salis-sparkles-mid.png
  sleep 0.18
  echo "SALIS_FRAME_PEAK_CAPTURE_MS=$(date +%s%3N)"
  import -display :99 -window root -silent client-smoke/screenshots/08k-salis-sparkles-peak.png
  test -s client-smoke/screenshots/08k-salis-sparkles-peak.png
  sleep 0.18
  echo "SALIS_FRAME_TAIL_CAPTURE_MS=$(date +%s%3N)"
  import -display :99 -window root -silent client-smoke/screenshots/08k-salis-sparkles-tail.png
  test -s client-smoke/screenshots/08k-salis-sparkles-tail.png
  sleep 0.18
  echo "SALIS_FRAME_FINAL_CAPTURE_MS=$(date +%s%3N)"
  import -display :99 -window root -silent client-smoke/screenshots/08k-salis-sparkles-final.png
  test -s client-smoke/screenshots/08k-salis-sparkles-final.png
  # Defer ImageMagick thumbnail processing and base64 output until AFTER
  # the PCM monitor stops, avoiding interference with quiet 0.33-volume dust.
  sleep 2.65
  wait "$dust_capture_pid" || test "$?" -eq 124
  send_chat '/execute if block 2 151 12 minecraft:air run say FIRSTSTEPS_REAL_DREAM_SALIS_BOOKSHELF_OK'
  grep -Fq 'FIRSTSTEPS_REAL_DREAM_SALIS_BOOKSHELF_OK' client-smoke/client.log
  capture 08l-firststeps-real-bookshelf-after-salis
  assert_smoke_event BOOKSHELF_SALIS_USED
  assert_smoke_event BOOKSHELF_TRANSFORMED
  # Salis CONSUMES the bookshelf: without restoring the test fixture, the
  # late screenshot shows distant sky/terrain, not the original scene.
  # Rebuild only the disposable bookshelf after the real use/audio capture.
  # Keep the player and camera unchanged; this is a visual negative control,
  # not a second gameplay acceptance or evidence of BETA26 sprite parity.
  xdotool key --clearmodifiers F1
  send_chat '/setblock 2 151 12 minecraft:bookshelf'
  send_chat '/execute if block 2 151 12 minecraft:bookshelf run say SALIS_TEMPORAL_CONTROL_RESTORED'
  grep -Fq 'SALIS_TEMPORAL_CONTROL_RESTORED' client-smoke/client.log
  xdotool key --clearmodifiers F1
  sleep 1
  # At >4s after use, with the same shelf restored, FX must have expired.
  import -display :99 -window root -silent client-smoke/screenshots/08k-salis-sparkles-late.png
  test -s client-smoke/screenshots/08k-salis-sparkles-late.png
  identify client-smoke/screenshots/08k-salis-sparkles-early.png
  convert client-smoke/screenshots/08k-salis-sparkles-early.png -resize 512x320 -colors 64 \\
    PNG8:client-smoke/screenshots/08k-salis-sparkles-early-thumb.png
  echo -n 'REAL_CLIENT_SCREEN_08k-salis-sparkles-early='
  base64 -w0 client-smoke/screenshots/08k-salis-sparkles-early-thumb.png
  echo
"""
# Isolate the quiet actor-only dust sound from randomly starting world music.
# The real Minecraft options are written before gradle runClient starts.
# Keep PLAYERS/master audible: those categories carry thaumcraft:dust.
options = "printf 'guiScale:2\\n' > run/options.txt"
if source.count(options) != 1:
    raise SystemExit("Minecraft options fixture changed; refuse unsafe audio injection")
if source.index(anchor) >= source.index(end):
    raise SystemExit("Real-client fixture end marker precedes the injection anchor")
head = source.split(anchor, 1)[0] + replacement
send_chat_block = """  send_chat() {
    xdotool key --clearmodifiers t
    sleep 1
    xdotool type --clearmodifiers --delay 24 "$1"
    xdotool key --clearmodifiers Return
    sleep 4
  }"""
send_chat_with_ack = """  activate_minecraft_client() {
    # World creation can leave keyboard focus on a stale/root X11 window.
    # Activate AND explicitly focus the actual LWJGL window before typing.
    xdotool windowactivate --sync "$WIN"
    xdotool windowraise "$WIN"
    xdotool windowfocus --sync "$WIN"
    xdotool mousemove --window "$WIN" 512 384
    sleep 0.25
    local active focused game_pid active_pid focused_pid
    active="$(xdotool getactivewindow 2>/dev/null || true)"
    focused="$(xdotool getwindowfocus 2>/dev/null || true)"
    game_pid="$(xdotool getwindowpid "$WIN" 2>/dev/null || true)"
    active_pid="$(xdotool getwindowpid "$active" 2>/dev/null || true)"
    focused_pid="$(xdotool getwindowpid "$focused" 2>/dev/null || true)"
    echo "SALIS_INPUT_FOCUS=window:$WIN active:${active:-none} focus:${focused:-none} game_pid:${game_pid:-none} active_pid:${active_pid:-none} focus_pid:${focused_pid:-none}" \
      | tee -a client-smoke-status.txt
    if [ -z "$game_pid" ] || [ "$active_pid" != "$game_pid" ] || [ "$focused_pid" != "$game_pid" ]; then
      echo 'SALIS_INPUT_FOCUS=FAIL' | tee -a client-smoke-status.txt
      capture 05e-command-focus-not-minecraft
      exit 1
    fi
    echo 'SALIS_INPUT_FOCUS=PASS' | tee -a client-smoke-status.txt
  }

  ensure_minecraft_command_channel() {
    if [ "${TC_SMOKE_COMMAND_CHANNEL_READY:-0}" = "1" ]; then
      return 0
    fi
    # Match the established shared client driver: T opens ordinary chat,
    # then the complete slash command is typed as text. Direct slash-key
    # injection opened the field but did not deliver the command to the server.
    xdotool key --clearmodifiers t
    sleep 0.35
    import -display :99 -window root -silent client-smoke/screenshots/05g-command-chat-open.png
    test -s client-smoke/screenshots/05g-command-chat-open.png
    # The screenshot command is read-only, but reassert the real game window
    # focus before typing so the command cannot land in another X11 client.
    activate_minecraft_client
    xdotool type --clearmodifiers --delay 24 '/say TC_SMOKE_COMMAND_CHANNEL_READY'
    sleep 0.15
    import -display :99 -window root -silent client-smoke/screenshots/05h-command-text-before-submit.png
    test -s client-smoke/screenshots/05h-command-text-before-submit.png
    activate_minecraft_client
    xdotool key --clearmodifiers Return
    local ready=0
    for _ in $(seq 1 40); do
      if grep -Fq 'TC_SMOKE_COMMAND_CHANNEL_READY' run/logs/latest.log; then
        ready=1
        break
      fi
      sleep 0.25
    done
    if [ "$ready" -ne 1 ]; then
      echo 'SALIS_COMMAND_CHANNEL=FAIL' | tee -a client-smoke-status.txt
      capture 05f-command-channel-not-ready
      tail -80 run/logs/latest.log >&2 || true
      exit 1
    fi
    export TC_SMOKE_COMMAND_CHANNEL_READY=1
    echo 'SALIS_COMMAND_CHANNEL=PASS' | tee -a client-smoke-status.txt
  }

  send_chat() {
    activate_minecraft_client
    ensure_minecraft_command_channel
    xdotool key --clearmodifiers t
    sleep 1
    xdotool type --clearmodifiers --delay 24 "$1"
    xdotool key --clearmodifiers Return
    sleep 4
  }"""
if source.count(send_chat_block) != 1 or head.count(send_chat_block) != 1:
    raise SystemExit("Shared chat driver changed; refuse unverified command-channel injection")
head = head.replace(send_chat_block, send_chat_with_ack)
head = head.replace(options,
    "printf 'guiScale:2\\nsoundCategory_music:0.0\\nsoundCategory_ambient:0.0\\n' > run/options.txt")
# The bookshelf is at (2,151,12); the shared fixture originally records
# from z=8.5, attenuating this intentionally quiet BETA26 0.33-volume sound.
# Move only this isolated sound probe nearer the real interaction target.
# Do not alter the canonical multi-feature client smoke script.
far = "  send_chat '/tp @p 2.5 150 8.5 0 0'\n"
near = "  send_chat '/tp @p 2.5 150 11.5 0 0'\n"
if head.count(far) != 1:
    raise SystemExit("Salis teleport fixture changed; refuse unverified relocation")
head = head.replace(far, near)
head = head.replace("export ALSOFT_DRIVERS=null", "export ALSOFT_DRIVERS=pulse")
tail = """  # Report actual server sendParticles calls, not packet delivery, even if PCM attribution fails.
  grep -Fq 'SALIS_PARTICLE_SEND_INVOKED' client-smoke/client.log
  grep -Fq 'SALIS_TRAIL_DELAY_QUEUE_DRAINED' client-smoke/client.log
  grep -Fq 'SALIS_FACE_DELAY_QUEUE_DRAINED' client-smoke/client.log
  python3 tools/verify_salis_sparkle_particle_log.py client-smoke/client.log
  echo 'SALIS_PARTICLE_SEND_INVOKED=PASS' | tee -a client-smoke-status.txt
  echo 'SALIS_FACE_DELAY_QUEUE_DRAINED=PASS' | tee -a client-smoke-status.txt
  python3 tools/match_dust_audio_pcm.py \\
    client-smoke/dust-baseline.pcm client-smoke/dust-event.pcm \\
    src/main/resources/assets/thaumcraft/sounds/dust.ogg \\
    | tee client-smoke/dust-source-match.txt
  if grep -Eiq 'Unknown sound event.*thaumcraft:dust|Unable to load sound.*thaumcraft:dust|Mod loading has failed|Exception in thread "Render thread"' client-smoke/client.log; then
    echo 'DUST_CLIENT_SOUND_LOAD_ERROR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'DUST_REAL_SALIS_BOOKSHELF_AND_ORIGINAL_PCM=PASS' | tee -a client-smoke-status.txt
  # Wait for the opt-in native chunk unload/reload probe after the real Salis use.
  native_probe_ok=0
  for _ in $(seq 1 180); do
    if grep -Fq 'TC_SMOKE_SALIS_NATIVE_CHUNK_UNLOAD_ACCEPTED=YES' client-smoke/client.log; then
      native_probe_ok=1
      break
    fi
    if grep -Fq 'TC_SMOKE_SALIS_NATIVE_CHUNK_UNLOAD_FAILED=YES' client-smoke/client.log; then
      tail -120 client-smoke/client.log >&2
      exit 1
    fi
    sleep 1
  done
  if [ "$native_probe_ok" -ne 1 ]; then
    echo 'SALIS_NATIVE_CHUNK_UNLOAD_ACCEPTED=NO' | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    exit 1
  fi
  for marker in SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_INSTALLED SALIS_NATIVE_ORIGIN_AT_CHUNK_EDGE SALIS_NATIVE_ACTOR_MOVED_TO_PACKET_ORIGIN SALIS_NATIVE_OUTBOUND_PACKET_POSITIVE_CONTROL SALIS_NATIVE_ACTOR_RETURNED_FROM_PACKET_ORIGIN SALIS_NATIVE_CHUNK_TICKETS_RELEASED SALIS_NATIVE_CHUNK_UNLOADED SALIS_NATIVE_CHUNK_CANCELLED SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_UNLOAD SALIS_NATIVE_CHUNK_RELOADED SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_RELOAD SALIS_NATIVE_POST_RELOAD_FRESH_PACKET SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_REMOVED SALIS_NATIVE_CHUNK_TICKET_CLEANED SALIS_NATIVE_CHUNK_RELOADED_NO_REPLAY SALIS_NATIVE_CHUNK_UNLOAD_ACCEPTED; do
    expected_count=1
    case "$marker" in
      SALIS_NATIVE_ACTOR_MOVED_TO_PACKET_ORIGIN|SALIS_NATIVE_ACTOR_RETURNED_FROM_PACKET_ORIGIN)
        expected_count=2
        ;;
    esac
    actual_count="$(grep -Fc "TC_SMOKE_${marker}=YES" client-smoke/client.log || true)"
    if [ "$actual_count" -ne "$expected_count" ]; then
      echo "SALIS_NATIVE_MARKER_COUNT=FAIL marker=$marker expected=$expected_count actual=$actual_count" >&2
      exit 1
    fi
  done
  # Verify both positive controls and the unload/reload gap in strict order.
  python3 - <<'SALIS_LIFECYCLE_ORDER'
from pathlib import Path

marker_occurrences = (
    ('SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_INSTALLED', 1),
    ('SALIS_NATIVE_ORIGIN_AT_CHUNK_EDGE', 1),
    ('SALIS_NATIVE_ACTOR_MOVED_TO_PACKET_ORIGIN', 1),
    ('SALIS_NATIVE_OUTBOUND_PACKET_POSITIVE_CONTROL', 1),
    ('SALIS_NATIVE_ACTOR_RETURNED_FROM_PACKET_ORIGIN', 1),
    ('SALIS_NATIVE_CHUNK_TICKETS_RELEASED', 1),
    ('SALIS_NATIVE_CHUNK_UNLOADED', 1),
    ('SALIS_NATIVE_CHUNK_CANCELLED', 1),
    ('SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_UNLOAD', 1),
    ('SALIS_NATIVE_CHUNK_RELOADED', 1),
    ('SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_RELOAD', 1),
    ('SALIS_NATIVE_ACTOR_MOVED_TO_PACKET_ORIGIN', 2),
    ('SALIS_NATIVE_POST_RELOAD_FRESH_PACKET', 1),
    ('SALIS_NATIVE_ACTOR_RETURNED_FROM_PACKET_ORIGIN', 2),
    ('SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_REMOVED', 1),
    ('SALIS_NATIVE_CHUNK_TICKET_CLEANED', 1),
    ('SALIS_NATIVE_CHUNK_RELOADED_NO_REPLAY', 1),
    ('SALIS_NATIVE_CHUNK_UNLOAD_ACCEPTED', 1),
)
expected_counts = {}
for marker, occurrence in marker_occurrences:
    expected_counts[marker] = max(expected_counts.get(marker, 0), occurrence)

log = Path('client-smoke/client.log').read_text(errors='replace').splitlines()
matches_by_marker = {}
for marker, expected_count in expected_counts.items():
    matches = [i for i, line in enumerate(log) if f'TC_SMOKE_{marker}=YES' in line]
    if len(matches) != expected_count:
        raise SystemExit(
            f'SALIS_NATIVE_MARKER_COUNT=FAIL {marker}: expected {expected_count}, got {len(matches)}'
        )
    matches_by_marker[marker] = matches

positions = [
    matches_by_marker[marker][occurrence - 1]
    for marker, occurrence in marker_occurrences
]
if positions != sorted(set(positions)):
    raise SystemExit('SALIS_NATIVE_LIFECYCLE_ORDER=FAIL')

packet_positions = [
    i for i, line in enumerate(log)
    if 'TC_SMOKE_SALIS_OUTBOUND_PACKET=YES' in line
]
if (
    len(packet_positions) != 2
    or 'count=1 ' not in log[packet_positions[0]]
    or 'count=2 ' not in log[packet_positions[1]]
):
    raise SystemExit(f'SALIS_NATIVE_PACKET_SEQUENCE=FAIL packets={len(packet_positions)}')

actor_moved = matches_by_marker['SALIS_NATIVE_ACTOR_MOVED_TO_PACKET_ORIGIN']
actor_returned = matches_by_marker['SALIS_NATIVE_ACTOR_RETURNED_FROM_PACKET_ORIGIN']
positive_control = matches_by_marker['SALIS_NATIVE_OUTBOUND_PACKET_POSITIVE_CONTROL'][0]
tickets_released = matches_by_marker['SALIS_NATIVE_CHUNK_TICKETS_RELEASED'][0]
chunk_reloaded = matches_by_marker['SALIS_NATIVE_CHUNK_RELOADED'][0]
no_packet_after_reload = matches_by_marker['SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_RELOAD'][0]
fresh_packet = matches_by_marker['SALIS_NATIVE_POST_RELOAD_FRESH_PACKET'][0]
recorder_removed = matches_by_marker['SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_REMOVED'][0]
ticket_cleaned = matches_by_marker['SALIS_NATIVE_CHUNK_TICKET_CLEANED'][0]
reloaded_no_replay = matches_by_marker['SALIS_NATIVE_CHUNK_RELOADED_NO_REPLAY'][0]
unload_accepted = matches_by_marker['SALIS_NATIVE_CHUNK_UNLOAD_ACCEPTED'][0]

if not (
    actor_moved[0] < packet_positions[0] < positive_control
    < actor_returned[0] < tickets_released
):
    raise SystemExit('SALIS_NATIVE_PACKET_POSITIVE_CONTROL_ORDER=FAIL')
if not (
    chunk_reloaded < no_packet_after_reload < actor_moved[1]
    < packet_positions[1] < fresh_packet < actor_returned[1]
    < recorder_removed < ticket_cleaned < reloaded_no_replay < unload_accepted
):
    raise SystemExit('SALIS_NATIVE_POST_RELOAD_PACKET_ORDER=FAIL')

print('SALIS_NATIVE_LIFECYCLE_ORDER=PASS')
print('SALIS_NATIVE_PACKET_POSITIVE_CONTROL_ORDER=PASS count=1 before unload')
print('SALIS_NATIVE_POST_RELOAD_PACKET_ORDER=PASS count=2 after reload')
SALIS_LIFECYCLE_ORDER
  packet_count=$(grep -Fc 'TC_SMOKE_SALIS_OUTBOUND_PACKET=YES' client-smoke/client.log || true)
  if [ "$packet_count" -ne 2 ] \
      || ! grep -Fq 'TC_SMOKE_SALIS_OUTBOUND_PACKET=YES count=1 ' client-smoke/client.log \
      || ! grep -Fq 'TC_SMOKE_SALIS_OUTBOUND_PACKET=YES count=2 ' client-smoke/client.log; then
    echo "SALIS_NATIVE_OUTBOUND_PACKET_CAPTURE=FAIL expected=2 actual=$packet_count" | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'SALIS_NATIVE_OUTBOUND_PACKET_CAPTURE=PASS positive_control=1 post_reload=1 no_replay=0' | tee -a client-smoke-status.txt
  echo 'SALIS_NATIVE_POST_RELOAD_PACKET_ORDER=PASS count=2 after reload' | tee -a client-smoke-status.txt
  echo 'SALIS_NATIVE_CHUNK_TICKET_CLEANED=PASS' | tee -a client-smoke-status.txt
  echo 'SALIS_NATIVE_CHUNK_UNLOAD_ACCEPTED=PASS' | tee -a client-smoke-status.txt
  exit 0
fi
"""
sys.stdout.write(head + tail)
