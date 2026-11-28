#!/usr/bin/env python3
"""Guard the opt-in native Salis chunk probe against leaking its test-only forced ticket."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/item/SalisSparkleChunkUnloadProbe.java"
SMOKE = ROOT / "tools/client_dust_sound_smoke.py"
QUEUE = ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/item/SalisSparkleTrailQueue.java"

probe = PROBE.read_text(encoding="utf-8")
smoke = SMOKE.read_text(encoding="utf-8")
queue = QUEUE.read_text(encoding="utf-8")


def section(source: str, start: str, end: str) -> str:
    if source.count(start) != 1 or end not in source.split(start, 1)[1]:
        raise SystemExit(f"fixture changed; expected one {start!r} and a following {end!r}")
    return source.split(start, 1)[1].split(end, 1)[0]


if probe.count("fixtureTicketHeld = true;") != 2:
    raise SystemExit("SALIS_NATIVE_TICKET_TRACKING=FAIL expected initial and reload acquisitions")
phase0 = section(probe, "if (phase == 0)", "if (phase == 4)")
phase4 = section(probe, "if (phase == 4)", "if (phase == 1)")
phase2 = section(probe, "if (phase == 2)", "if (phase == 3)")
phase3 = section(probe, "if (phase == 3)", "} catch (RuntimeException | AssertionError ex)")
phase5 = section(probe, "if (phase == 5)", "} catch (RuntimeException | AssertionError ex)")
if "require(releaseFixtureTicket()" not in phase4:
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
if "restoreActorFromPacketOrigin()" not in catch:
    raise SystemExit("SALIS_NATIVE_ACTOR_FAILURE_RESTORE=FAIL")
if not (catch.index("restoreActorFromPacketOrigin()") < catch.index("removeOutboundParticleRecorder()")):
    raise SystemExit("SALIS_NATIVE_ACTOR_FAILURE_RESTORE_ORDER=FAIL")
if "removeOutboundParticleRecorder();" not in catch:
    raise SystemExit("SALIS_NATIVE_PACKET_RECORDER_FAILURE_CLEANUP=FAIL")
stopped = section(probe, "public static void onServerStopped", "@SubscribeEvent")
if "cleanupFixtureTicketOnStop();" not in stopped:
    raise SystemExit("SALIS_NATIVE_SERVER_STOP_CLEANUP=FAIL")
if "removeOutboundParticleRecorder();" not in stopped:
    raise SystemExit("SALIS_NATIVE_PACKET_RECORDER_STOP_CLEANUP=FAIL")
release = section(probe, "private static boolean releaseFixtureTicket()", "private static void cleanupFixtureTicketAfterFailure()")
if "targetLevel.setChunkForced(CHUNK_X, CHUNK_Z, false)" not in release or "fixtureTicketHeld = false;" not in release:
    raise SystemExit("SALIS_NATIVE_RELEASE_HELPER=FAIL")
required_runtime_markers = (
    "SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_INSTALLED",
    "SALIS_NATIVE_ORIGIN_AT_CHUNK_EDGE",
    "SALIS_NATIVE_ACTOR_MOVED_TO_PACKET_ORIGIN",
    "SALIS_NATIVE_OUTBOUND_PACKET_POSITIVE_CONTROL",
    "SALIS_NATIVE_ACTOR_RETURNED_FROM_PACKET_ORIGIN",
    "SALIS_NATIVE_CHUNK_TICKETS_RELEASED",
    "SALIS_NATIVE_CHUNK_UNLOADED",
    "SALIS_NATIVE_CHUNK_CANCELLED",
    "SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_UNLOAD",
    "SALIS_NATIVE_CHUNK_RELOADED",
    "SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_RELOAD",
    "SALIS_NATIVE_POST_RELOAD_FRESH_PACKET",
    "SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_REMOVED",
    "SALIS_NATIVE_CHUNK_TICKET_CLEANED",
    "SALIS_NATIVE_CHUNK_RELOADED_NO_REPLAY",
    "SALIS_NATIVE_CHUNK_UNLOAD_ACCEPTED",
)
for marker in required_runtime_markers:
    if marker not in smoke:
        raise SystemExit(f"SALIS_NATIVE_RUNTIME_MARKER_NOT_REQUIRED=FAIL missing {marker!r}")
if 'grep -Fc "TC_SMOKE_${marker}=YES" client-smoke/client.log' not in smoke:
    raise SystemExit("SALIS_NATIVE_RUNTIME_MARKER_EXACTLY_ONCE_GATE=FAIL")
if "SALIS_NATIVE_LIFECYCLE_ORDER=PASS" not in smoke or "positions != sorted(set(positions))" not in smoke:
    raise SystemExit("SALIS_NATIVE_RUNTIME_MARKER_ORDER_GATE=FAIL")
if (
    "grep -Fc 'TC_SMOKE_SALIS_OUTBOUND_PACKET=YES'" not in smoke
    or "count=1 " not in smoke
    or "count=2 " not in smoke
    or 'packet_count" -ne 2' not in smoke
):
    raise SystemExit("SALIS_NATIVE_PACKET_SEQUENCE_GATE=FAIL")
if "expected_count=2" not in smoke or "SALIS_NATIVE_POST_RELOAD_PACKET_ORDER=PASS count=2 after reload" not in smoke:
    raise SystemExit("SALIS_NATIVE_POST_RELOAD_PACKET_GATE=FAIL")
if "SALIS_NATIVE_PACKET_POSITIVE_CONTROL_ORDER=PASS" not in smoke:
    raise SystemExit("SALIS_NATIVE_PACKET_POSITIVE_CONTROL_ORDER_GATE=FAIL")

print("SALIS_NATIVE_CHUNK_PROBE_TICKET_CLEANUP=PASS")
print("SALIS_NATIVE_OUTBOUND_PACKET_RUNTIME_MARKERS_REQUIRED=PASS")

for required in (
    "ClientboundLevelParticlesPacket",
    "ChannelOutboundHandlerAdapter",
    "actor.connection.getConnection()",
    'pipeline.addBefore("packet_handler"',
    "packet.getParticle() == SalisSparkleParticleRegistration.TYPE",
    "actorReturnPosition = actor.position();",
    "actor.teleportTo(level,",
    "actor.teleportTo(targetLevel, returnPosition.x, returnPosition.y, returnPosition.z,",
    "positive-control actor is outside vanilla 32-block particle delivery radius",
    "packet.getCount() == 0",
    "packet.getX()",
    "OUTBOUND_PROBE_PACKET_COUNT.incrementAndGet()",
    "context.write(message, promise)",
    "SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_INSTALLED",
    "SALIS_NATIVE_ORIGIN_AT_CHUNK_EDGE",
    "SALIS_NATIVE_ACTOR_MOVED_TO_PACKET_ORIGIN",
    "SALIS_NATIVE_OUTBOUND_PACKET_POSITIVE_CONTROL",
    "SALIS_NATIVE_ACTOR_RETURNED_FROM_PACKET_ORIGIN",
    "SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_UNLOAD",
    "SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_RELOAD",
    "SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_REMOVED",
):
    if required not in probe:
        raise SystemExit(f"SALIS_OUTBOUND_PACKET_CAPTURE_SOURCE=FAIL missing {required!r}")

phase0 = section(probe, "if (phase == 0)", "if (phase == 4)")
phase4 = section(probe, "if (phase == 4)", "if (phase == 1)")
phase2 = section(probe, "if (phase == 2)", "if (phase == 3)")
phase3 = section(probe, "if (phase == 3)", "} catch (RuntimeException | AssertionError ex)")
if not (
    phase0.index("installOutboundParticleRecorder(actor)")
    < phase0.index("OUTBOUND_PROBE_PACKET_COUNT.get()")
    < phase0.index('mark("SALIS_NATIVE_ORIGIN_AT_CHUNK_EDGE")')
    < phase0.index("moveActorToPacketOrigin(level, actor)")
    < phase0.index("SalisSparkleTrailQueue.emitFaceOrQueue")
):
    raise SystemExit("SALIS_OUTBOUND_PACKET_INSTALL_ORDER=FAIL")
if not (
    phase4.index("observedPackets != outboundPacketsBefore + 1")
    < phase4.index('mark("SALIS_NATIVE_OUTBOUND_PACKET_POSITIVE_CONTROL")')
    < phase4.index("outboundPacketsBefore = observedPackets")
    < phase4.index("restoreActorFromPacketOrigin()")
    < phase4.index("SalisSparkleTrailQueue.emitFaceOrQueue")
    < phase4.index("releaseFixtureTicket()")
):
    raise SystemExit("SALIS_OUTBOUND_PACKET_POSITIVE_CONTROL_ORDER=FAIL")
if "PACKET_TIMEOUT_TICKS" not in probe or "probeActor" not in probe:
    raise SystemExit("SALIS_OUTBOUND_PACKET_POSITIVE_CONTROL_TIMEOUT_OR_ACTOR_GUARD=FAIL")
if "((ORIGIN.getX() + 1) >> 4) == CHUNK_X + 1" not in probe:
    raise SystemExit("SALIS_NATIVE_ORIGIN_CHUNK_EDGE_GUARD=FAIL")
if "OUTBOUND_PROBE_PACKET_COUNT.get() == outboundPacketsBefore" not in phase2:
    raise SystemExit("SALIS_OUTBOUND_PACKET_UNLOAD_ASSERTION=FAIL")
if "OUTBOUND_PROBE_PACKET_COUNT.get() == outboundPacketsBefore" not in phase3:
    raise SystemExit("SALIS_OUTBOUND_PACKET_RELOAD_ASSERTION=FAIL")
if not (
    phase3.index('mark("SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_RELOAD")')
    < phase3.index("moveActorToPacketOrigin(level, probeActor)")
    < phase3.index("SalisSparkleTrailQueue.emitFaceOrQueue")
    < phase3.index("phase = 5;")
):
    raise SystemExit("SALIS_POST_RELOAD_TRIGGER_ORDER=FAIL")
if not (
    "postReloadPackets <= outboundPacketsBefore + 1" in phase5
    and "if (postReloadPackets != outboundPacketsBefore + 1) return;" in phase5
    and 'mark("SALIS_NATIVE_POST_RELOAD_FRESH_PACKET")' in phase5
    and phase5.index('mark("SALIS_NATIVE_POST_RELOAD_FRESH_PACKET")')
        < phase5.index("restoreActorFromPacketOrigin()")
        < phase5.index("removeOutboundParticleRecorder()")
        < phase5.index("releaseFixtureTicket()")
):
    raise SystemExit("SALIS_POST_RELOAD_PACKET_AND_CLEANUP=FAIL")

print("SALIS_OUTBOUND_PACKET_CAPTURE_SOURCE=PASS")
print("SALIS_OUTBOUND_PACKET_POSITIVE_CONTROL=PASS")
print("SALIS_NATIVE_ORIGIN_CHUNK_EDGE=PASS")


logout = section(queue, "public static void onPlayerLoggedOut", "@SubscribeEvent")
if (
    "pending.actor() != player" not in logout
    or "pending.playerId().equals(player.getUUID())" not in logout
    or 'mark("SALIS_DELAY_ACTOR_DISCONNECTED_CANCELLED")' not in logout
):
    raise SystemExit("SALIS_ACTOR_LOGOUT_SESSION_CLEANUP=FAIL")
if "static int pendingCountForActor(ServerPlayer actor)" not in queue:
    raise SystemExit("SALIS_ACTOR_PENDING_COUNT=FAIL")
if "level.sendParticles(player, color, false" not in queue:
    raise SystemExit("SALIS_ACTOR_ONLY_RECIPIENT=FAIL")

if (
    "PlayerEvent.PlayerLoggedOutEvent" not in probe
    or "actor.connection.disconnect(Component.literal(" not in probe
    or 'mark("SALIS_NATIVE_ACTOR_DISCONNECT_PENDING")' not in probe
    or 'mark("SALIS_NATIVE_ACTOR_DISCONNECT_CLEANED")' not in probe
):
    raise SystemExit("SALIS_NATIVE_ACTOR_DISCONNECT_PROBE=FAIL")
phase5 = section(probe, "if (phase == 5)", "if (phase == 6)")
phase6 = section(probe, "if (phase == 6)", "} catch (RuntimeException | AssertionError ex)")
if not (
    phase5.index("restoreActorFromPacketOrigin()")
    < phase5.index("removeOutboundParticleRecorder()")
    < phase5.index("releaseFixtureTicket()")
    < phase5.index("SalisSparkleTrailQueue.emitOrQueue")
    < phase5.index("phase = 6;")
    < phase5.index("actor.connection.disconnect")
):
    raise SystemExit("SALIS_NATIVE_ACTOR_DISCONNECT_CONTROL_ORDER=FAIL")
if not (
    "actorDisconnectEventSeen" in phase6
    and "pendingCountForActor(actor) == 0" in phase6
    and 'mark("SALIS_NATIVE_ACTOR_DISCONNECT_CLEANED")' in phase6
    and 'mark("SALIS_NATIVE_CHUNK_UNLOAD_ACCEPTED")' in phase6
):
    raise SystemExit("SALIS_NATIVE_ACTOR_DISCONNECT_ACCEPTANCE=FAIL")
for marker in (
    "SALIS_NATIVE_ACTOR_DISCONNECT_PENDING",
    "SALIS_DELAY_ACTOR_DISCONNECTED_CANCELLED",
    "SALIS_NATIVE_ACTOR_DISCONNECT_CLEANED",
):
    if marker not in smoke:
        raise SystemExit(f"SALIS_NATIVE_ACTOR_DISCONNECT_MARKER_NOT_REQUIRED=FAIL {marker}")
if not (
    "actor_disconnect_pending < actor_disconnect_cancelled < actor_disconnect_cleaned < unload_accepted" in smoke
):
    raise SystemExit("SALIS_NATIVE_ACTOR_DISCONNECT_LOG_ORDER_GATE=FAIL")

print("SALIS_ACTOR_LOGOUT_SESSION_CLEANUP=PASS")
print("SALIS_ACTOR_ONLY_RECIPIENT=PASS")
print("SALIS_NATIVE_ACTOR_DISCONNECT_PROBE=PASS")
