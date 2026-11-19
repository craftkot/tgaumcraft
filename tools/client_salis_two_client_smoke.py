#!/usr/bin/env python3
"""Launch one dedicated NeoForge server and two independent real Minecraft clients.

The first client is the Salis actor; the second is a nearby observer. The
server-side fixture prepares a real bookshelf and the client-side packet
probes observe decoded packets in each client's own Netty connection.
"""
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import signal
import subprocess
import sys
import time
import uuid

ROOT = Path.cwd()
OUT = ROOT / "client-salis-two-client"
SERVER_DIR = ROOT / "run" / "server"
SERVER_GRADLE_LOG = OUT / "server-gradle.log"
SERVER_GAME_LOG = SERVER_DIR / "logs" / "latest.log"
ACTOR_NAME = "TC_SalisActor"
OBSERVER_NAME = "TC_SalisObserver"
ACTOR_DIR = ROOT / "run" / "salis-two-client-actor"
OBSERVER_DIR = ROOT / "run" / "salis-two-client-observer"

PROBE_PROPERTIES = (
    "-Dthaumcraft.salisChunkUnloadScenario=true "
    "-Dthaumcraft.salisTwoClientScenario=true"
)


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def wait_until(predicate, timeout: int, label: str, detail=lambda: "") -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            print(f"{label}=READY", flush=True)
            return
        time.sleep(1)
    extra = detail()
    raise RuntimeError(f"{label}=TIMEOUT after {timeout}s\n{extra}")


def has_option(args: list[str], option: str) -> bool:
    return any(arg == option or arg.startswith(option + "=") for arg in args)


def set_option(args: list[str], option: str, value: str) -> None:
    for index, arg in enumerate(args):
        if arg == option:
            if index + 1 >= len(args):
                args.append(value)
            else:
                args[index + 1] = value
            return
        if arg.startswith(option + "="):
            args[index] = option + "=" + value
            return
    args.extend([option, value])


def remove_option(args: list[str], option: str) -> None:
    index = 0
    while index < len(args):
        if args[index] == option:
            del args[index:index + 2]
        elif args[index].startswith(option + "="):
            del args[index]
        else:
            index += 1


def offline_uuid(username: str) -> str:
    # Match Java UUID.nameUUIDFromBytes("OfflinePlayer:" + username).
    digest = bytearray(hashlib.md5(("OfflinePlayer:" + username).encode("utf-8")).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(digest)))


def find_client_process(timeout: int, gradle_log: Path) -> tuple[int, list[str]]:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        matches: list[tuple[int, list[str]]] = []
        for entry in os.listdir("/proc"):
            if not entry.isdigit():
                continue
            pid = int(entry)
            try:
                raw = Path(f"/proc/{pid}/cmdline").read_bytes()
                args = [part.decode("utf-8", "replace") for part in raw.split(b"\0") if part]
                exe = os.readlink(f"/proc/{pid}/exe").lower()
            except (OSError, ProcessLookupError):
                continue
            joined = " ".join(args)
            is_java = "java" in exe
            is_client = (
                "forgeclient" in joined
                or "net.minecraft.client.main.Main" in joined
                or "net.neoforged.devlaunch.Main" in joined
            )
            if is_java and is_client and has_option(args, "--gameDir") and has_option(args, "--assetsDir"):
                matches.append((pid, args))
        if matches:
            return sorted(matches, key=lambda item: item[0])[-1]
        time.sleep(1)
    raise RuntimeError(
        "RUNCLIENT_TEMPLATE_PROCESS_NOT_FOUND\n"
        + read_text(gradle_log)[-12000:]
    )


def client_args(template: list[str], username: str, game_dir: Path) -> list[str]:
    args = list(template)
    for option in ("--quickPlayPath", "--quickPlayMultiplayer",
                   "--quickPlaySingleplayer", "--quickPlayRealms"):
        remove_option(args, option)
    set_option(args, "--username", username)
    set_option(args, "--uuid", offline_uuid(username))
    set_option(args, "--gameDir", str(game_dir.resolve()))
    set_option(args, "--server", "127.0.0.1")
    set_option(args, "--port", "25565")
    set_option(args, "--width", "1024")
    set_option(args, "--height", "768")
    if not has_option(args, "--username") or not has_option(args, "--gameDir"):
        raise RuntimeError("RUNCLIENT_ARGUMENT_CLONE_INCOMPLETE")
    return args


def enable_probe_env(env: dict[str, str]) -> dict[str, str]:
    env["THAUMCRAFT_SMOKE"] = "1"
    options = env.get("JAVA_TOOL_OPTIONS", "")
    current = options.split()
    for option in PROBE_PROPERTIES.split():
        if option not in current:
            options = (options + " " + option).strip()
            current = options.split()
    env["JAVA_TOOL_OPTIONS"] = options
    return env


def start_x_server(display: str, log_dir: Path, processes: list[subprocess.Popen]) -> dict[str, str]:
    xvfb_log = (log_dir / f"xvfb-{display[1:]}.log").open("w", encoding="utf-8")
    xvfb = subprocess.Popen(
        ["Xvfb", display, "-screen", "0", "1280x800x24",
         "+extension", "GLX", "+render", "-noreset"],
        cwd=ROOT, stdout=xvfb_log, stderr=subprocess.STDOUT,
    )
    processes.append(xvfb)
    env = os.environ.copy()
    env.update({
        "DISPLAY": display,
        "LIBGL_ALWAYS_SOFTWARE": "1",
        "MESA_LOADER_DRIVER_OVERRIDE": "llvmpipe",
        "ALSOFT_DRIVERS": "null",
        "XDG_RUNTIME_DIR": f"/tmp/tc-salis-runtime-{display[1:]}",
    })
    runtime = Path(env["XDG_RUNTIME_DIR"])
    runtime.mkdir(parents=True, exist_ok=True)
    runtime.chmod(0o700)
    wait_until(
        lambda: subprocess.run(
            ["xdpyinfo", "-display", display],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        ).returncode == 0,
        30, f"XVFB_{display[1:]}",
    )
    wm_log = (log_dir / f"openbox-{display[1:]}.log").open("w", encoding="utf-8")
    wm = subprocess.Popen(["openbox"], cwd=ROOT, env=env,
                          stdout=wm_log, stderr=subprocess.STDOUT)
    processes.append(wm)
    gl = subprocess.run(["glxinfo", "-B"], cwd=ROOT, env=env,
                        capture_output=True, text=True, timeout=30)
    if gl.returncode != 0 or "OpenGL version string" not in gl.stdout:
        raise RuntimeError(f"GLX_{display[1:]}_UNAVAILABLE\n{gl.stdout}\n{gl.stderr}")
    subprocess.run(["xdotool", "mousemove", "512", "384"], cwd=ROOT, env=env, check=True)
    return env


def find_minecraft_window(env: dict[str, str]) -> str | None:
    result = subprocess.run(
        ["xdotool", "search", "--onlyvisible", "--name", "Minecraft"],
        cwd=ROOT, env=env, capture_output=True, text=True,
    )
    windows = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    return windows[-1] if windows else None


def prepare_window(env: dict[str, str], window: str, label: str) -> None:
    subprocess.run(["xdotool", "windowmove", window, "0", "0"],
                   cwd=ROOT, env=env, check=True)
    subprocess.run(["xdotool", "windowsize", window, "1024", "768"],
                   cwd=ROOT, env=env, check=True)
    subprocess.run(["xdotool", "windowactivate", "--sync", window],
                   cwd=ROOT, env=env, check=True)
    print(f"{label}_WINDOW={window}", flush=True)


def marker_count(path: Path, marker: str) -> int:
    return sum(1 for line in read_text(path).splitlines() if marker in line)


def capture(env: dict[str, str], name: str) -> None:
    path = OUT / name
    subprocess.run(
        ["import", "-display", env["DISPLAY"], "-window", "root", "-silent", str(path)],
        cwd=ROOT, env=env, check=True, timeout=30,
    )
    if not path.is_file() or path.stat().st_size == 0:
        raise RuntimeError(f"SCREENSHOT_MISSING={path}")
    subprocess.run(["identify", str(path)], cwd=ROOT, check=True, timeout=30)


def stop_process(proc: subprocess.Popen | None, label: str) -> None:
    if proc is None or proc.poll() is not None:
        return
    proc.terminate()
    try:
        proc.wait(timeout=15)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait(timeout=10)
    print(f"STOPPED_{label}=YES", flush=True)


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.iterdir():
        if old.is_file():
            old.unlink()

    SERVER_DIR.mkdir(parents=True, exist_ok=True)
    (SERVER_DIR / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (SERVER_DIR / "server.properties").write_text(
        "server-ip=127.0.0.1\n"
        "server-port=25565\n"
        "online-mode=false\n"
        "enforce-secure-profile=false\n"
        "white-list=false\n"
        "spawn-protection=0\n"
        "max-players=4\n"
        "view-distance=2\n"
        "simulation-distance=2\n"
        "gamemode=survival\n"
        "difficulty=peaceful\n"
        "allow-flight=true\n"
        "level-type=minecraft:flat\n"
        "generate-structures=false\n"
        "level-seed=260611\n"
        "motd=Thaumcraft two-client Salis smoke\n",
        encoding="utf-8",
    )

    base_env = enable_probe_env(os.environ.copy())

    processes: list[subprocess.Popen] = []
    files = []
    server = None
    template_gradle = None
    actor = None
    observer = None
    try:
        actor_env = enable_probe_env(start_x_server(":99", OUT, processes))
        observer_env = enable_probe_env(start_x_server(":100", OUT, processes))

        server_log_file = SERVER_GRADLE_LOG.open("w", encoding="utf-8")
        files.append(server_log_file)
        server = subprocess.Popen(
            ["gradle", "runServer", "--no-daemon", "--console=plain"],
            cwd=ROOT, env=base_env, stdout=server_log_file, stderr=subprocess.STDOUT,
        )
        processes.append(server)
        wait_until(
            lambda: "Done (" in read_text(SERVER_GRADLE_LOG)
                    or "Done (" in read_text(SERVER_GAME_LOG),
            900, "DEDICATED_SERVER_READY",
            detail=lambda: (read_text(SERVER_GRADLE_LOG)[-12000:]
                            + "\n" + read_text(SERVER_GAME_LOG)[-12000:]),
        )

        template_log = (OUT / "runclient-template.log").open("w", encoding="utf-8")
        files.append(template_log)
        template_env = dict(actor_env)
        template_env["THAUMCRAFT_SMOKE"] = "1"
        template_gradle = subprocess.Popen(
            ["gradle", "runClient", "--no-daemon", "--console=plain"],
            cwd=ROOT, env=template_env, stdout=template_log, stderr=subprocess.STDOUT,
        )
        processes.append(template_gradle)
        template_pid, template_args = find_client_process(360, OUT / "runclient-template.log")
        print("RUNCLIENT_TEMPLATE_MAIN=FOUND", flush=True)
        print(f"RUNCLIENT_TEMPLATE_ARGS={len(template_args)}", flush=True)
        os.kill(template_pid, signal.SIGTERM)
        try:
            template_gradle.wait(timeout=60)
        except subprocess.TimeoutExpired:
            template_gradle.terminate()
            template_gradle.wait(timeout=15)
        print("RUNCLIENT_TEMPLATE_STOPPED=YES", flush=True)

        for game_dir in (ACTOR_DIR, OBSERVER_DIR):
            game_dir.mkdir(parents=True, exist_ok=True)
            (game_dir / "options.txt").write_text("guiScale:2\n", encoding="utf-8")

        actor_log_file = (OUT / "actor-process.log").open("w", encoding="utf-8")
        observer_log_file = (OUT / "observer-process.log").open("w", encoding="utf-8")
        files.extend([actor_log_file, observer_log_file])

        actor = subprocess.Popen(
            client_args(template_args, ACTOR_NAME, ACTOR_DIR),
            cwd=ROOT, env=actor_env, stdout=actor_log_file, stderr=subprocess.STDOUT,
        )
        processes.append(actor)
        wait_until(lambda: find_minecraft_window(actor_env) is not None,
                   240, "ACTOR_CLIENT_WINDOW",
                   detail=lambda: read_text(OUT / "actor-process.log")[-12000:])
        actor_window = find_minecraft_window(actor_env)
        prepare_window(actor_env, actor_window, "ACTOR")
        wait_until(lambda: f"{ACTOR_NAME} joined the game" in
                   (read_text(SERVER_GRADLE_LOG) + read_text(SERVER_GAME_LOG)),
                   240, "ACTOR_JOINED",
                   detail=lambda: (read_text(SERVER_GRADLE_LOG)[-12000:]
                                   + "\n" + read_text(SERVER_GAME_LOG)[-12000:]))

        observer = subprocess.Popen(
            client_args(template_args, OBSERVER_NAME, OBSERVER_DIR),
            cwd=ROOT, env=observer_env, stdout=observer_log_file, stderr=subprocess.STDOUT,
        )
        processes.append(observer)
        wait_until(lambda: find_minecraft_window(observer_env) is not None,
                   240, "OBSERVER_CLIENT_WINDOW",
                   detail=lambda: read_text(OUT / "observer-process.log")[-12000:])
        observer_window = find_minecraft_window(observer_env)
        prepare_window(observer_env, observer_window, "OBSERVER")
        wait_until(lambda: f"{OBSERVER_NAME} joined the game" in
                   (read_text(SERVER_GRADLE_LOG) + read_text(SERVER_GAME_LOG)),
                   240, "OBSERVER_JOINED",
                   detail=lambda: (read_text(SERVER_GRADLE_LOG)[-12000:]
                                   + "\n" + read_text(SERVER_GAME_LOG)[-12000:]))

        actor_game_log = ACTOR_DIR / "logs" / "latest.log"
        observer_game_log = OBSERVER_DIR / "logs" / "latest.log"
        wait_until(
            lambda: "TC_SMOKE_SALIS_TWO_CLIENT_FIXTURE_READY=YES" in
                    (read_text(SERVER_GRADLE_LOG) + read_text(SERVER_GAME_LOG)),
            180, "TWO_CLIENT_FIXTURE_READY",
            detail=lambda: (read_text(SERVER_GRADLE_LOG)[-12000:]
                            + "\n" + read_text(SERVER_GAME_LOG)[-12000:]),
        )
        for path, label in ((actor_game_log, "ACTOR"), (observer_game_log, "OBSERVER")):
            wait_until(
                lambda p=path: "TC_SMOKE_SALIS_CLIENT_PACKET_PROBE_INSTALLED=YES" in read_text(p),
                120, f"{label}_PACKET_PROBE_INSTALLED",
                detail=lambda p=path: read_text(p)[-8000:],
            )
            wait_until(
                lambda p=path: "TC_SMOKE_SALIS_CLIENT_CONTROL_PARTICLE=YES" in read_text(p)
                               and "TC_SMOKE_SALIS_CLIENT_CONTROL_SOUND=YES" in read_text(p),
                120, f"{label}_POSITIVE_CONTROL",
                detail=lambda p=path: read_text(p)[-8000:],
            )

        capture(actor_env, "actor-before-salis.png")
        capture(observer_env, "observer-before-salis.png")
        subprocess.run(["xdotool", "windowactivate", "--sync", actor_window],
                       cwd=ROOT, env=actor_env, check=True)
        subprocess.run(["xdotool", "key", "1"], cwd=ROOT, env=actor_env, check=True)
        time.sleep(0.5)
        subprocess.run(["xdotool", "click", "3"], cwd=ROOT, env=actor_env, check=True)
        print("ACTOR_REAL_RIGHT_CLICK=ISSUED", flush=True)

        server_text = lambda: read_text(SERVER_GRADLE_LOG) + "\n" + read_text(SERVER_GAME_LOG)
        wait_until(lambda: "TC_SMOKE_BOOKSHELF_SALIS_USED=YES" in server_text(),
                   120, "REAL_SALIS_ITEM_USE",
                   detail=lambda: server_text()[-12000:])
        wait_until(lambda: "TC_SMOKE_BOOKSHELF_TRANSFORMED=YES" in server_text(),
                   180, "REAL_BOOKSHELF_TRANSFORM",
                   detail=lambda: server_text()[-12000:])
        wait_until(lambda: "TC_SMOKE_SALIS_CLIENT_TARGET_SOUND=YES" in read_text(actor_game_log),
                   30, "ACTOR_TARGET_SOUND_PACKET",
                   detail=lambda: read_text(actor_game_log)[-8000:])
        wait_until(lambda: "TC_SMOKE_SALIS_CLIENT_TARGET_PARTICLE=YES" in read_text(actor_game_log),
                   30, "ACTOR_TARGET_PARTICLE_PACKET",
                   detail=lambda: read_text(actor_game_log)[-8000:])
        capture(actor_env, "actor-after-salis.png")
        capture(observer_env, "observer-after-salis.png")

        wait_until(lambda: "TC_SMOKE_SALIS_NATIVE_CHUNK_UNLOAD_ACCEPTED=YES" in server_text(),
                   2400, "NATIVE_CHUNK_UNLOAD_RELOAD_ACCEPTED",
                   detail=lambda: server_text()[-16000:])
        wait_until(lambda: server.poll() is not None, 180,
                   "DEDICATED_SERVER_CLEAN_EXIT",
                   detail=lambda: read_text(SERVER_GRADLE_LOG)[-12000:])

        server_output = server_text()
        required_server_markers = [
            "SALIS_TWO_CLIENT_FIXTURE_READY",
            "SALIS_TWO_CLIENT_ITEM_USE_CONFIRMED",
            "SALIS_TWO_CLIENT_SERVER_ACTION_CONFIRMED",
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
        ]
        positions = [server_output.find("TC_SMOKE_" + marker + "=YES") for marker in required_server_markers]
        if any(position < 0 for position in positions) or positions != sorted(positions):
            raise RuntimeError(f"SALIS_NATIVE_LIFECYCLE_ORDER=FAIL positions={positions}")

        actor_control_particles = marker_count(actor_game_log, "TC_SMOKE_SALIS_CLIENT_CONTROL_PARTICLE=YES")
        actor_control_sounds = marker_count(actor_game_log, "TC_SMOKE_SALIS_CLIENT_CONTROL_SOUND=YES")
        observer_control_particles = marker_count(observer_game_log, "TC_SMOKE_SALIS_CLIENT_CONTROL_PARTICLE=YES")
        observer_control_sounds = marker_count(observer_game_log, "TC_SMOKE_SALIS_CLIENT_CONTROL_SOUND=YES")
        actor_target_particles = marker_count(actor_game_log, "TC_SMOKE_SALIS_CLIENT_TARGET_PARTICLE=YES")
        observer_target_particles = marker_count(observer_game_log, "TC_SMOKE_SALIS_CLIENT_TARGET_PARTICLE=YES")
        actor_target_sounds = marker_count(actor_game_log, "TC_SMOKE_SALIS_CLIENT_TARGET_SOUND=YES")
        observer_target_sounds = marker_count(observer_game_log, "TC_SMOKE_SALIS_CLIENT_TARGET_SOUND=YES")
        actor_chunk_particles = marker_count(actor_game_log, "TC_SMOKE_SALIS_CLIENT_CHUNK_PARTICLE=YES")
        observer_chunk_particles = marker_count(observer_game_log, "TC_SMOKE_SALIS_CLIENT_CHUNK_PARTICLE=YES")

        if (actor_control_particles, actor_control_sounds,
            observer_control_particles, observer_control_sounds) != (1, 1, 1, 1):
            raise RuntimeError(
                "SALIS_TWO_CLIENT_POSITIVE_CONTROL=FAIL "
                f"actor={actor_control_particles}/{actor_control_sounds} "
                f"observer={observer_control_particles}/{observer_control_sounds}"
            )
        if actor_target_particles < 1 or observer_target_particles != 0:
            raise RuntimeError(
                "SALIS_TWO_CLIENT_ACTOR_ONLY_PARTICLES=FAIL "
                f"actor={actor_target_particles} observer={observer_target_particles}"
            )
        if actor_target_sounds != 1 or observer_target_sounds != 0:
            raise RuntimeError(
                "SALIS_TWO_CLIENT_ACTOR_ONLY_SOUND=FAIL "
                f"actor={actor_target_sounds} observer={observer_target_sounds}"
            )
        if actor_chunk_particles != 2 or observer_chunk_particles != 0:
            raise RuntimeError(
                "SALIS_TWO_CLIENT_CHUNK_PACKET_ROUTE=FAIL "
                f"actor={actor_chunk_particles} observer={observer_chunk_particles}"
            )
        if server.returncode != 0:
            raise RuntimeError(f"DEDICATED_SERVER_EXIT={server.returncode}")

        print("SALIS_TWO_CLIENT_REAL_SESSIONS=PASS actor=TC_SalisActor observer=TC_SalisObserver", flush=True)
        print("SALIS_TWO_CLIENT_POSITIVE_CONTROL=PASS particles=1 sounds=1 per_client", flush=True)
        print(f"SALIS_TWO_CLIENT_ACTOR_ONLY_PARTICLES=PASS actor={actor_target_particles} observer=0", flush=True)
        print("SALIS_TWO_CLIENT_ACTOR_ONLY_SOUND=PASS actor=1 observer=0", flush=True)
        print("SALIS_TWO_CLIENT_CHUNK_UNLOAD_RELOAD=PASS actor_packets=2 observer_packets=0", flush=True)
        print("SALIS_TWO_CLIENT_NATIVE_TICKET_AND_NO_REPLAY=PASS", flush=True)
        return 0

    finally:
        stop_process(observer, "OBSERVER_CLIENT")
        stop_process(actor, "ACTOR_CLIENT")
        stop_process(template_gradle, "TEMPLATE_GRADLE")
        stop_process(server, "DEDICATED_SERVER")
        for proc in reversed(processes):
            if proc not in (observer, actor, template_gradle, server):
                stop_process(proc, f"DISPLAY_PROCESS_{proc.pid}")
        for handle in files:
            try:
                handle.close()
            except OSError:
                pass


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"SALIS_TWO_CLIENT_SMOKE=FAIL {error}", file=sys.stderr, flush=True)
        raise
