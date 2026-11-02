#!/usr/bin/env python3
"""Generate a real-client FIRSTSTEPS Salis dust audio smoke without unrelated late tests."""
from pathlib import Path
import sys
source = Path("tools/client_smoke.sh").read_text()
anchor = "  capture 08k-firststeps-real-bookshelf-before-salis\n  xdotool click 3\n  sleep 4\n"
end = "  # A /give or /item replace does not invoke ItemEntityPickupEvent.Post"
if source.count(anchor) != 1 or source.count(end) != 1 or source.count("export ALSOFT_DRIVERS=null") != 1:
    raise SystemExit("Real-client fixture changed; refuse unsafe injection")
replacement = """  capture 08k-firststeps-real-bookshelf-before-salis
  pactl list short sinks | grep -Fq 'tc_dust_sink'
  timeout --signal=TERM 2s parec --device=tc_dust_sink.monitor \\
    --format=s16le --rate=48000 --channels=2 --raw \\
    > client-smoke/dust-baseline.pcm || test "$?" -eq 124
  timeout --signal=TERM 5s parec --device=tc_dust_sink.monitor \\
    --format=s16le --rate=48000 --channels=2 --raw \\
    > client-smoke/dust-event.pcm &
  dust_capture_pid=$!
  sleep 0.6
  xdotool click 3
  sleep 4
  wait "$dust_capture_pid" || test "$?" -eq 124
"""
head = source.split(end, 1)[0].replace(anchor, replacement)
head = head.replace("export ALSOFT_DRIVERS=null", "export ALSOFT_DRIVERS=pulse")
tail = """  python3 tools/match_dust_audio_pcm.py \\
    client-smoke/dust-baseline.pcm client-smoke/dust-event.pcm \\
    src/main/resources/assets/thaumcraft/sounds/dust.ogg \\
    | tee client-smoke/dust-source-match.txt
  if grep -Eiq 'Unknown sound event.*thaumcraft:dust|Unable to load sound.*thaumcraft:dust|Mod loading has failed|Exception in thread "Render thread"' client-smoke/client.log; then
    echo 'DUST_CLIENT_SOUND_LOAD_ERROR=YES' | tee -a client-smoke-status.txt
    exit 1
  fi
  echo 'DUST_REAL_SALIS_BOOKSHELF_AND_ORIGINAL_PCM=PASS' | tee -a client-smoke-status.txt
  exit 0
fi
"""
sys.stdout.write(head + tail)
