#!/usr/bin/env python3
"""Generate a real Minecraft client smoke for BETA26 jar liquid and lid parts."""
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
    "PASS: real Minecraft client world, BETA26 jar liquid, metal parts and aspect label smoke",
)

body = r'''
  echo 'JAR_LIQUID_REAL_WORLD=YES' | tee -a client-smoke-status.txt
  mkdir -p client-smoke/screenshots

  send_chat '/time set noon'
  send_chat '/weather clear'
  send_chat '/forceload add 250 250'
  # Keep the camera player on the same platform as the jars during the render poll.
  send_chat '/fill 248 149 248 252 149 252 minecraft:stone'
  send_chat '/fill 248 150 250 252 153 252 minecraft:air'
  send_chat '/setblock 249 150 251 thaumcraft:jar_normal{Aspect:"aer",Amount:125s,facing:2b}'
  send_chat '/setblock 250 150 251 thaumcraft:jar_normal{Aspect:"ignis",Amount:250s,facing:2b}'
  send_chat '/setblock 251 150 251 thaumcraft:jar_normal{Amount:0s,facing:2b}'
  send_chat '/execute if data block 249 150 251 {Aspect:"aer",Amount:125s} run say JAR_LIQUID_AER_FIXTURE_READY'
  send_chat '/execute if data block 250 150 251 {Aspect:"ignis",Amount:250s} run say JAR_LIQUID_IGNIS_FIXTURE_READY'
  send_chat '/execute if data block 251 150 251 {Amount:0s} run say JAR_LIQUID_EMPTY_FIXTURE_READY'

  # Force a client chunk reload so the actual update tag, not only server NBT, is tested.
  send_chat '/tp @p 1000 150 1000 0 0'
  sleep 6
  send_chat '/tp @p 250.5 150 248.5 0 16'
  sleep 6
  capture 96-jar-liquid-fixture-camera

  for marker in     'TC_SMOKE_JAR_METAL_PARTS_LAYER_REGISTERED=YES'     'TC_SMOKE_JAR_LIQUID_CLIENT_STATE=READY'; do
    found=0
    for _ in $(seq 1 90); do
      if grep -Fq "$marker" client-smoke/client.log; then found=1; break; fi
      sleep 1
    done
    if [ "$found" -ne 1 ]; then
      echo "JAR_LIQUID_REQUIRED_CLIENT_MARKER_MISSING=$marker" | tee -a client-smoke-status.txt
      tail -120 client-smoke/client.log >&2
      exit 1
    fi
  done

  for marker in     'TC_SMOKE_JAR_LIQUID_RENDERED=YES pos=249,150,251 amount=125 aspect=aer rgb=FFFF7E topY=0.3750 texture=thaumcraft:block/animatedglow'     'TC_SMOKE_JAR_LIQUID_RENDERED=YES pos=250,150,251 amount=250 aspect=ignis rgb=FF5A01 topY=0.6875 texture=thaumcraft:block/animatedglow'     'TC_SMOKE_JAR_LIQUID_EMPTY_SKIPPED=YES pos=251,150,251 amount=0'; do
    found=0
    for _ in $(seq 1 90); do
      if grep -Fq "$marker" client-smoke/client.log; then found=1; break; fi
      sleep 1
    done
    if [ "$found" -ne 1 ]; then
      echo "JAR_LIQUID_REQUIRED_RENDER_MARKER_MISSING=$marker" | tee -a client-smoke-status.txt
      for stage in TC_SMOKE_JAR_LIQUID_RENDERER_REGISTERED TC_SMOKE_JAR_LIQUID_RENDER_ENTRY TC_SMOKE_JAR_LIQUID_RENDERED; do
        if grep -Fq "$stage" client-smoke/client.log; then
          echo "JAR_LIQUID_STAGE_PRESENT=$stage" | tee -a client-smoke-status.txt
          grep -F "$stage" client-smoke/client.log | tail -12 | tee -a client-smoke-status.txt
        else
          echo "JAR_LIQUID_STAGE_ABSENT=$stage" | tee -a client-smoke-status.txt
        fi
      done
      grep -F "TC_SMOKE_JAR_LIQUID_CLIENT_STATE=" client-smoke/client.log | tail -8 | tee -a client-smoke-status.txt || true
      tail -120 client-smoke/client.log >&2
      exit 1
    fi
  done

  capture 97-jar-liquid-filled
  python3 tools/assert_client_jar_liquid_smoke.py     client-smoke/client.log client-smoke/screenshots/97-jar-liquid-filled.png

  # BETA26 blocked jar: the renderer must add the original gold lid brace even when empty.
  send_chat '/fill 248 150 250 252 153 252 minecraft:air'
  send_chat '/setblock 250 150 251 thaumcraft:jar_normal{blocked:1b,facing:2b}'
  send_chat '/execute if data block 250 150 251 {blocked:1b,facing:2b} run say JAR_BRACE_FIXTURE_READY'
  send_chat '/tp @p 1000 150 1000 0 0'
  sleep 3
  send_chat '/tp @p 250.5 150 248.5 0 16'
  sleep 4
  brace_found=0
  for _ in $(seq 1 45); do
    if grep -Fq 'TC_SMOKE_JAR_BRACE_RENDERED=YES pos=250,150,251' client-smoke/client.log; then
      brace_found=1
      break
    fi
    sleep 1
  done
  if [ "$brace_found" -ne 1 ]; then
    echo 'JAR_BRACE_RENDER_MARKER_MISSING=YES' | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    exit 1
  fi
  capture 98-jar-brace

  # BETA26 getConnectableTile(pos, UP): an open DOWN face on the adjacent tube adds the lid extension.
  # Place the tube first, then the jar, so the real neighbor update opens the tube's DOWN blockstate.
  send_chat '/fill 248 150 250 252 153 252 minecraft:air'
  send_chat '/setblock 250 151 251 thaumcraft:tube'
  send_chat '/setblock 250 150 251 thaumcraft:jar_normal{facing:2b}'
  send_chat '/execute if block 250 151 251 thaumcraft:tube[down=true] run say JAR_EXTENSION_TUBE_FIXTURE_READY'
  send_chat '/tp @p 1000 150 1000 0 0'
  sleep 3
  send_chat '/tp @p 250.5 150 248.5 0 16'
  sleep 4
  extension_found=0
  for _ in $(seq 1 45); do
    if grep -Fq 'TC_SMOKE_JAR_LID_EXTENSION_RENDERED=YES pos=250,150,251 above=250,151,251' client-smoke/client.log; then
      extension_found=1
      break
    fi
    sleep 1
  done
  if [ "$extension_found" -ne 1 ]; then
    echo 'JAR_LID_EXTENSION_RENDER_MARKER_MISSING=YES' | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    exit 1
  fi
  if ! grep -Fq 'JAR_EXTENSION_TUBE_FIXTURE_READY' client-smoke/client.log; then
    echo 'JAR_EXTENSION_TUBE_NOT_CONNECTED=YES' | tee -a client-smoke-status.txt
    tail -120 client-smoke/client.log >&2
    exit 1
  fi
  capture 99-jar-lid-extension
  python3 tools/assert_client_jar_metal_parts.py     client-smoke/client.log     client-smoke/screenshots/98-jar-brace.png     client-smoke/screenshots/99-jar-lid-extension.png
  # BETA26 TileJarRenderer draws the filter label and glyph in an empty jar; crooked=true rotates both paper and icon quads.
  send_chat '/fill 248 150 250 252 153 252 minecraft:air'
  send_chat '/setblock 250 150 251 thaumcraft:jar_normal{AspectFilter:"terra",facing:2b}'
  send_chat '/execute if data block 250 150 251 {AspectFilter:"terra",facing:2b} run say JAR_ASPECT_LABEL_FIXTURE_READY'
  send_chat '/tp @p 1000 150 1000 0 0'
  sleep 3
  send_chat '/tp @p 250.5 150 248.5 0 16'
  sleep 4
  label_found=0
  for _ in $(seq 1 45); do
    if grep -Fq 'TC_SMOKE_JAR_LABEL_RENDERED=YES pos=250,150,251 aspect=terra facing=2 crooked=-2' client-smoke/client.log \
       && grep -Fq 'TC_SMOKE_JAR_LABEL_ICON_RENDERED=YES pos=250,150,251 aspect=terra crooked=-2 texture=thaumcraft:textures/aspects/terra.png' client-smoke/client.log; then
      label_found=1
      break
    fi
    sleep 1
  done
  if [ "$label_found" -ne 1 ]; then
    echo 'JAR_ASPECT_LABEL_RENDER_MARKER_MISSING=YES' | tee -a client-smoke-status.txt
    grep -F 'TC_SMOKE_JAR_LABEL_' client-smoke/client.log | tail -20 | tee -a client-smoke-status.txt || true
    tail -120 client-smoke/client.log >&2
    exit 1
  fi
  capture 100-jar-aspect-label
  python3 tools/assert_client_jar_label.py \
    client-smoke/client.log client-smoke/screenshots/100-jar-aspect-label.png
  echo 'JAR_ASPECT_LABEL_REAL_CLIENT_AND_SCREENSHOT=PASS' | tee -a client-smoke-status.txt client-smoke/client.log

  echo 'JAR_METAL_PARTS_REAL_CLIENT_AND_SCREENSHOT=PASS' | tee -a client-smoke-status.txt client-smoke/client.log
'''
sys.stdout.write(head + body + tail)
