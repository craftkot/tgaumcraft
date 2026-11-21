#!/usr/bin/env python3
"""Static contract for the independent two-client Salis acceptance harness."""
from pathlib import Path
import importlib.util
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SERVER = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/item/SalisTwoClientServerProbe.java").read_text()
CLIENT = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/client/SalisTwoClientPacketProbe.java").read_text()
CHUNK_PROBE = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/item/SalisSparkleChunkUnloadProbe.java").read_text()
QUEUE = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/item/SalisSparkleTrailQueue.java").read_text()
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
    "private static final int DELAY_TICKS = 1200;",
    "private static final BlockPos ORIGIN = new BlockPos(CHUNK_X * 16 + 15, 100, CHUNK_Z * 16 + 8);",
    "&& ((ORIGIN.getX() + 1) >> 4) == CHUNK_X + 1",
    "ChannelOutboundHandlerAdapter",
    "ClientboundLevelParticlesPacket",
    "OUTBOUND_PROBE_PACKET_COUNT.incrementAndGet()",
    "level.setChunkForced(CHUNK_X, CHUNK_Z, true)",
    "private static boolean releaseFixtureTicket()",
    "targetLevel.setChunkForced(CHUNK_X, CHUNK_Z, false)",
    "fixtureTicketHeld = false;",
    "SALIS_NATIVE_ORIGIN_AT_CHUNK_EDGE",
    "SALIS_NATIVE_OUTBOUND_PACKET_POSITIVE_CONTROL",
    "SALIS_NATIVE_CHUNK_TICKETS_RELEASED",
    "SALIS_NATIVE_CHUNK_UNLOADED",
    "SALIS_NATIVE_CHUNK_CANCELLED",
    "SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_UNLOAD",
    "SALIS_NATIVE_CHUNK_RELOADED",
    "SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_RELOAD",
    "SALIS_NATIVE_POST_RELOAD_FRESH_PACKET",
    "SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_REMOVED",
    "SALIS_NATIVE_CHUNK_TICKET_CLEANED",
    "SALIS_NATIVE_ACTOR_DISCONNECT_CLEANED",
    "SALIS_NATIVE_CHUNK_UNLOAD_ACCEPTED",
):
    if required not in CHUNK_PROBE:
        raise SystemExit(f"SALIS_NATIVE_CHUNK_PROBE=FAIL missing {required!r}")

if CHUNK_PROBE.count("require(releaseFixtureTicket(),") != 2:
    raise SystemExit("SALIS_NATIVE_CHUNK_PROBE=FAIL both unload and final-cleanup phases must release the fixture ticket through the shared helper")

for required in (
    "level.getServer().getLevel(level.dimension()) != level",
    "if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(origin))) return;",
    "if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(pending.origin())))",
    "pending.sourceLevel() != level",
    "player != pending.actor()",
    "public static void onChunkUnload(ChunkEvent.Unload event)",
    "pending.actor() != player",
    "level.sendParticles(player, color, false",
):
    if required not in QUEUE:
        raise SystemExit(f"SALIS_NATIVE_QUEUE_GUARD=FAIL missing {required!r}")

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
    "def expand_java_argfiles(",
    "def is_client_process(",
    "is_client_process(expanded_args)",
    "RUNCLIENT_JAVA_PROCESS_CANDIDATE",
    "RUNCLIENT_TEMPLATE_GRADLE_EXITED",
    "offline_uuid(username)",
    '"--quickPlayMultiplayer"',
    '":99"',
    '":100"',
    "TC_SalisActor",
    "TC_SalisObserver",
    "TC_SMOKE_SALIS_TWO_CLIENT_FIXTURE_READY=YES",
    'ACTOR_PROCESS_LOG = OUT / "actor-process.log"',
    'OBSERVER_PROCESS_LOG = OUT / "observer-process.log"',
    "actor_client_log = ACTOR_PROCESS_LOG",
    "observer_client_log = OBSERVER_PROCESS_LOG",
    "TC_SMOKE_SALIS_CLIENT_PACKET_PROBE_INSTALLED=YES",
    "SALIS_TWO_CLIENT_ACTOR_ONLY_PARTICLES=PASS",
    "SALIS_TWO_CLIENT_ACTOR_ONLY_SOUND=PASS",
    "SALIS_TWO_CLIENT_CHUNK_UNLOAD_RELOAD=PASS",
    "SALIS_NATIVE_OUTBOUND_PACKET_POSITIVE_CONTROL",
    "SALIS_NATIVE_CHUNK_TICKETS_RELEASED",
    "SALIS_NATIVE_CHUNK_UNLOADED",
    "SALIS_NATIVE_CHUNK_CANCELLED",
    "SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_UNLOAD",
    "SALIS_NATIVE_CHUNK_RELOADED",
    "SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_RELOAD",
    "SALIS_NATIVE_POST_RELOAD_FRESH_PACKET",
    "SALIS_NATIVE_CHUNK_TICKET_CLEANED",
    "SALIS_NATIVE_ACTOR_DISCONNECT_CLEANED",
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

if "LOGIN_TIMEOUT_TICKS = 3600L" not in SERVER:
    raise SystemExit("SALIS_TWO_CLIENT_SERVER_FIXTURE=FAIL login deadline below real-client startup budget")
if 'SERVER_DIR = ROOT / "run"' not in SMOKE:
    raise SystemExit("SALIS_TWO_CLIENT_REAL_CLIENT_DRIVER=FAIL wrong ModDev server run directory")
if "DEDICATED_SERVER_PROPERTIES=PASS game_dir=run" not in SMOKE:
    raise SystemExit("SALIS_TWO_CLIENT_REAL_CLIENT_DRIVER=FAIL missing server.properties load assertion")

spec = importlib.util.spec_from_file_location(
    "salis_two_client_driver", ROOT / "tools/client_salis_two_client_smoke.py"
)
if spec is None or spec.loader is None:
    raise SystemExit("SALIS_TWO_CLIENT_ARGFILE_TEST=FAIL cannot import driver")
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)

with tempfile.TemporaryDirectory() as temporary:
    cwd = Path(temporary)
    nested = cwd / "client args.txt"
    nested.write_text(
        "--launchTarget forgeclientdev --version 21.1.250 --gameDir .",
        encoding="utf-8",
    )
    outer = cwd / "gradle args.txt"
    outer.write_text(f'-Xmx2G @"{nested}"', encoding="utf-8")
    expanded = driver.expand_java_argfiles(["/usr/bin/java", f"@{outer}"], cwd)
    if not driver.is_client_process(expanded):
        raise SystemExit("SALIS_TWO_CLIENT_ARGFILE_TEST=FAIL client launch target not expanded")
    if not driver.has_option(expanded, "--gameDir"):
        raise SystemExit("SALIS_TWO_CLIENT_ARGFILE_TEST=FAIL gameDir not expanded")
    if driver.is_client_process(
        ["/usr/bin/java", "--launchTarget", "forgeserverdev", "--gameDir", "."]
    ):
        raise SystemExit("SALIS_TWO_CLIENT_ARGFILE_TEST=FAIL server target matched as client")

if 'ACTOR_DIR / "logs" / "latest.log"' in SMOKE or 'OBSERVER_DIR / "logs" / "latest.log"' in SMOKE:
    raise SystemExit("SALIS_TWO_CLIENT_REAL_CLIENT_DRIVER=FAIL client markers must be read from redirected process logs")

print("SALIS_TWO_CLIENT_ARGFILE_EXPANSION=PASS")

print("SALIS_TWO_CLIENT_SERVER_FIXTURE=PASS")
print("SALIS_TWO_CLIENT_INBOUND_PACKET_PROBE=PASS")
print("SALIS_TWO_CLIENT_SEPARATE_JVM_DRIVER=PASS")
print("SALIS_TWO_CLIENT_ISOLATED_WORKFLOW=PASS")
