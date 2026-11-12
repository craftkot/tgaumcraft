#!/usr/bin/env python3
"""Guard the opt-in native Salis chunk probe against leaking its test-only forced ticket."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/item/SalisSparkleChunkUnloadProbe.java"
SMOKE = ROOT / "tools/client_dust_sound_smoke.py"

probe = PROBE.read_text(encoding="utf-8")
smoke = SMOKE.read_text(encoding="utf-8")


def section(source: str, start: str, end: str) -> str:
    if source.count(start) != 1 or end not in source.split(start, 1)[1]:
        raise SystemExit(f"fixture changed; expected one {start!r} and a following {end!r}")
    return source.split(start, 1)[1].split(end, 1)[0]


if probe.count("fixtureTicketHeld = true;") != 2:
    raise SystemExit("SALIS_NATIVE_TICKET_TRACKING=FAIL expected initial and reload acquisitions")
phase0 = section(probe, "if (phase == 0)", "if (phase == 1)")
phase2 = section(probe, "if (phase == 2)", "if (phase == 3)")
phase3 = section(probe, "if (phase == 3)", "} catch (RuntimeException | AssertionError ex)")
if "require(releaseFixtureTicket()" not in phase0:
    raise SystemExit("SALIS_NATIVE_INITIAL_TICKET_RELEASE=FAIL")
if "fixtureTicketHeld = true;" not in phase2:
    raise SystemExit("SALIS_NATIVE_RELOAD_TICKET_TRACKING=FAIL")
if "require(releaseFixtureTicket()" not in phase3:
    raise SystemExit("SALIS_NATIVE_FINAL_TICKET_RELEASE=FAIL")
if not (
    phase3.index("require(releaseFixtureTicket()")
    < phase3.index('mark("SALIS_NATIVE_CHUNK_TICKET_CLEANED")')
    < phase3.index("active.halt(false)")
):
    raise SystemExit("SALIS_NATIVE_FINAL_TICKET_RELEASE_ORDER=FAIL")
catch = section(probe, "} catch (RuntimeException | AssertionError ex) {", "private static boolean releaseFixtureTicket()")
if "cleanupFixtureTicketAfterFailure();" not in catch:
    raise SystemExit("SALIS_NATIVE_FAILURE_CLEANUP=FAIL")
stopped = section(probe, "public static void onServerStopped", "@SubscribeEvent")
if "cleanupFixtureTicketOnStop();" not in stopped:
    raise SystemExit("SALIS_NATIVE_SERVER_STOP_CLEANUP=FAIL")
release = section(probe, "private static boolean releaseFixtureTicket()", "private static void cleanupFixtureTicketAfterFailure()")
if "targetLevel.setChunkForced(CHUNK_X, CHUNK_Z, false)" not in release or "fixtureTicketHeld = false;" not in release:
    raise SystemExit("SALIS_NATIVE_RELEASE_HELPER=FAIL")
if "TC_SMOKE_SALIS_NATIVE_CHUNK_TICKET_CLEANED=YES" not in smoke:
    raise SystemExit("SALIS_NATIVE_CLEANUP_MARKER_NOT_REQUIRED=FAIL")

print("SALIS_NATIVE_CHUNK_PROBE_TICKET_CLEANUP=PASS")

for required in (
    "ClientboundLevelParticlesPacket",
    "ChannelOutboundHandlerAdapter",
    "actor.connection.getConnection()",
    'pipeline.addBefore("packet_handler"',
    "packet.getParticle() == SalisSparkleParticleRegistration.TYPE",
    "packet.getCount() == 0",
    "packet.getX()",
    "OUTBOUND_PROBE_PACKET_COUNT.incrementAndGet()",
    "context.write(message, promise)",
    "SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_INSTALLED",
    "SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_UNLOAD",
    "SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_RELOAD",
    "SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_REMOVED",
):
    if required not in probe:
        raise SystemExit(f"SALIS_OUTBOUND_PACKET_CAPTURE_SOURCE=FAIL missing {required!r}")

phase0 = section(probe, "if (phase == 0)", "if (phase == 1)")
phase2 = section(probe, "if (phase == 2)", "if (phase == 3)")
phase3 = section(probe, "if (phase == 3)", "} catch (RuntimeException | AssertionError ex)")
if not (
    phase0.index("installOutboundParticleRecorder(actor)")
    < phase0.index("OUTBOUND_PROBE_PACKET_COUNT.get()")
    < phase0.index("SalisSparkleTrailQueue.emitFaceOrQueue")
):
    raise SystemExit("SALIS_OUTBOUND_PACKET_INSTALL_ORDER=FAIL")
if "OUTBOUND_PROBE_PACKET_COUNT.get() == outboundPacketsBefore" not in phase2:
    raise SystemExit("SALIS_OUTBOUND_PACKET_UNLOAD_ASSERTION=FAIL")
if "OUTBOUND_PROBE_PACKET_COUNT.get() == outboundPacketsBefore" not in phase3:
    raise SystemExit("SALIS_OUTBOUND_PACKET_RELOAD_ASSERTION=FAIL")
