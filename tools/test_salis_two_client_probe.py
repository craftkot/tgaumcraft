#!/usr/bin/env python3
"""Static contract for the independent two-client Salis acceptance harness."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVER = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/item/SalisTwoClientServerProbe.java").read_text()
CLIENT = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/client/SalisTwoClientPacketProbe.java").read_text()
SMOKE = (ROOT / "tools/client_salis_two_client_smoke.py").read_text()
WORKFLOW = (ROOT / ".github/workflows/closure-salis-two-client.yml").read_text()

for required in (
    'ACTOR_NAME = "TC_SalisActor"',
    'OBSERVER_NAME = "TC_SalisObserver"',
    "actorIndex < observerIndex",
    'knowledge().addResearch("!gotdream")',
    "new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get(), 2)",
    "actor.teleportTo(level, 0.5D, 100.0D, 4.0D",
    "observer.teleportTo(level, 5.5D, 100.0D, 4.0D",
    "level.sendParticles(player, SalisSparkleParticleRegistration.TYPE",
    "new ClientboundSoundPacket(",
    'mark("SALIS_TWO_CLIENT_FIXTURE_READY")',
    'mark("SALIS_TWO_CLIENT_ITEM_USE_CONFIRMED")',
    'mark("SALIS_TWO_CLIENT_SERVER_ACTION_CONFIRMED")',
):
    if required not in SERVER:
        raise SystemExit(f"SALIS_TWO_CLIENT_SERVER_FIXTURE=FAIL missing {required!r}")

for required in (
    "import net.neoforged.neoforge.client.event.ClientTickEvent;",
    "ClientTickEvent.Post",
    "Minecraft.getInstance().getConnection()",
    "listener.getConnection()",
    'pipeline.addBefore("packet_handler"',
    "ChannelInboundHandlerAdapter",
    "ClientboundLevelParticlesPacket",
    "ClientboundSoundPacket",
    "packet.getParticle() == SalisSparkleParticleRegistration.TYPE",
    "packet.getSound().value() == ThaumcraftSounds.DUST.get()",
    "context.fireChannelRead(message)",
    "SALIS_CLIENT_PACKET_PROBE_INSTALLED",
    "SALIS_CLIENT_CONTROL_PARTICLE",
    "SALIS_CLIENT_CONTROL_SOUND",
    "SALIS_CLIENT_TARGET_PARTICLE",
    "SALIS_CLIENT_TARGET_SOUND",
    "SALIS_CLIENT_CHUNK_PARTICLE",
):
    if required not in CLIENT:
        raise SystemExit(f"SALIS_TWO_CLIENT_CLIENT_PROBE=FAIL missing {required!r}")

for required in (
    '["gradle", "runServer", "--no-daemon", "--console=plain"]',
    '["gradle", "runClient", "--no-daemon", "--console=plain"]',
    '"forgeclient" in joined',
    "offline_uuid(username)",
    '"--quickPlayMultiplayer"',
    '":99"',
    '":100"',
    "TC_SalisActor",
    "TC_SalisObserver",
    "TC_SMOKE_SALIS_TWO_CLIENT_FIXTURE_READY=YES",
    "TC_SMOKE_SALIS_CLIENT_PACKET_PROBE_INSTALLED=YES",
    "SALIS_TWO_CLIENT_ACTOR_ONLY_PARTICLES=PASS",
    "SALIS_TWO_CLIENT_ACTOR_ONLY_SOUND=PASS",
    "SALIS_TWO_CLIENT_CHUNK_UNLOAD_RELOAD=PASS",
    "SALIS_NATIVE_CHUNK_UNLOAD_ACCEPTED",
):
    if required not in SMOKE:
        raise SystemExit(f"SALIS_TWO_CLIENT_REAL_CLIENT_DRIVER=FAIL missing {required!r}")

if "net.neoforged.neoforge.event.tick.ClientTickEvent" in CLIENT:
    raise SystemExit("SALIS_TWO_CLIENT_CLIENT_PROBE=FAIL ClientTickEvent must use the client.event package")

if "EmbeddedChannel" in SERVER or "EmbeddedChannel" in CLIENT:
    raise SystemExit("SALIS_TWO_CLIENT_REAL_PROCESSES=FAIL a real-client probe must not use EmbeddedChannel")

for required in (
    "timeout-minutes: 90",
    "flite1-dev",
    'ctypes.CDLL("libflite.so")',
    "MINECRAFT_NARRATOR_FLITE=PASS",
    "workflow_dispatch",
    "xvfb",
    "xdotool",
    "gradle/actions/setup-gradle@v4",
    "python3 tools/test_salis_two_client_probe.py",
    "python3 tools/client_salis_two_client_smoke.py",
    "if: always()",
    "client-salis-two-client/",
):
    if required not in WORKFLOW:
        raise SystemExit(f"SALIS_TWO_CLIENT_WORKFLOW=FAIL missing {required!r}")

print("SALIS_TWO_CLIENT_SERVER_FIXTURE=PASS")
print("SALIS_TWO_CLIENT_INBOUND_PACKET_PROBE=PASS")
print("SALIS_TWO_CLIENT_SEPARATE_JVM_DRIVER=PASS")
print("SALIS_TWO_CLIENT_ISOLATED_WORKFLOW=PASS")
