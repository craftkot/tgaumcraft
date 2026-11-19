#!/usr/bin/env python3
"""Generate a real Minecraft client smoke for the BETA26 animated jar liquid renderer."""
from pathlib import Path
import sys

source = Path("tools/client_smoke.sh").read_text(encoding="utf-8")
anchor = "  # The initial world is already creative + peaceful, as established"
gradle_anchor = "gradle runClient --no-daemon --console=plain"
tail_anchor = "else\n  echo 'INTEGRATED_WORLD_START_LOG=NOT_CONFIRMED'"
if (source.count(anchor) != 1 or source.count(gradle_anchor) != 1
        or source.count("export ALSOFT_DRIVERS=null") != 1
        or source.count(tail_anchor) != 1):
    raise SystemExit("Verified client boot anchors changed; refusing blind smoke injection")

head = source.split(anchor, 1)[0]
head = head.replace(
    "export ALSOFT_DRIVERS=null",
    "export ALSOFT_DRIVERS=null\nexport JAVA_TOOL_OPTIONS=-Dthaumcraft.jarLiquidSmoke=true",
)
head = head.replace(
    gradle_anchor,
    "gradle --init-script tools/gradle-plugin-portal-fallback.init.gradle runClient --no-daemon --console=plain",
)
tail = tail_anchor + source.split(tail_anchor, 1)[1]
tail = tail.replace(
    "PASS: real Minecraft client world, overhead crucible PNGs captured",
    "PASS: real Minecraft client world, BETA26 jar liquid renderer smoke",
)

body = r'''
  echo 'JAR_LIQUID_REAL_WORLD=YES' | tee -a client-smoke-status.txt
  mkdir -p client-smoke/screenshots

  send_chat '/time set noon'
  send_chat '/weather clear'
  send_chat '/forceload add 250 250'
  send_chat '/fill 248 149 250 252 149 252 minecraft:stone'
  send_chat '/fill 248 150 250 252 153 252 minecraft:air'
  send_chat '/setblock 249 150 251 thaumcraft:jar_normal{Aspect:"aer",Amount:125s}'
  send_chat '/setblock 250 150 251 thaumcraft:jar_normal{Aspect:"ignis",Amount:250s}'
  send_chat '/setblock 251 150 251 thaumcraft:jar_normal{Amount:0s}'
  send_chat '/execute if data block 249 150 251 {Aspect:"aer",Amount:125s} run say JAR_LIQUID_AER_FIXTURE_READY'
  send_chat '/execute if data block 250 150 251 {Aspect:"ignis",Amount:250s} run say JAR_LIQUID_IGNIS_FIXTURE_READY'
  send_chat '/execute if data block 251 150 251 {Amount:0s} run say JAR_LIQUID_EMPTY_FIXTURE_READY'

  # Force the client to unload and reload the already-saved chunk so the
  # actual block-entity update tag, not merely server-side /data NBT, is tested.
  send_chat '/tp @p 1000 150 1000 0 0'
  sleep 6
  send_chat '/tp @p 250.5 150 248.5 0 16'
  sleep 6

  for marker in \
    'TC_SMOKE_JAR_LIQUID_RENDERED=YES pos=249,150,251 amount=125 aspect=aer rgb=FFFF7E topY=0.3750 texture=thaumcraft:block/animatedglow' \
    'TC_SMOKE_JAR_LIQUID_RENDERED=YES pos=250,150,251 amount=250 aspect=ignis rgb=FF5A01 topY=0.6875 texture=thaumcraft:block/animatedglow' \
    'TC_SMOKE_JAR_LIQUID_EMPTY_SKIPPED=YES pos=251,150,251 amount=0'; do
    found=0
    for _ in $(seq 1 90); do
      if grep -Fq "$marker" client-smoke/client.log; then found=1; break; fi
      sleep 1
    done
    if [ "$found" -ne 1 ]; then
      echo "JAR_LIQUID_REQUIRED_RENDER_MARKER_MISSING=$marker" | tee -a client-smoke-status.txt
      tail -120 client-smoke/client.log >&2
      exit 1
    fi
  done

  capture 97-jar-liquid-filled
  python3 tools/assert_client_jar_liquid_smoke.py \
    client-smoke/client.log client-smoke/screenshots/97-jar-liquid-filled.png
  echo 'JAR_LIQUID_REAL_CLIENT_RENDERER_AND_SCREENSHOT=PASS' | tee -a client-smoke-status.txt client-smoke/client.log
'''
sys.stdout.write(head + body + tail)
