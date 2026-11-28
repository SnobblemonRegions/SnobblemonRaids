"""Windows-only, fresh-world native rehearsal. Never targets an existing server.

prepare copies runtime files, not worlds/configuration; launch records the owned PID.
command verifies both loopback listeners belong to that exact process before RCON.
Credentials stay in the private server.properties and are never printed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import time

from rcon import Rcon
from worldboss_process import WindowsProcess, previous_exited, validate_identity


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines()
                if line and not line.startswith("#") and "=" in line)


def reject_links(path):
    for entry in [path, *path.rglob("*")] if path.is_dir() else [path]:
        if entry.is_symlink() or (getattr(entry.stat(), "st_file_attributes", 0) & 0x400):
            raise ValueError("Runtime source contains a link/reparse point: " + str(entry))


def ports_free(ports):
    if len(set(ports)) != 2 or any(not 1024 <= p <= 65535 for p in ports):
        raise ValueError("Two distinct nonprivileged ports are required")
    sockets = []
    try:
        for port in ports:
            sock = socket.socket()
            sockets.append(sock)
            if os.name == "nt":
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
            sock.bind(("127.0.0.1", port))
    finally:
        for sock in sockets:
            sock.close()


def prepare(args):
    source, jar, root = args.runtime.resolve(), args.jar.resolve(), args.root.resolve()
    if not jar.is_file() or jar.suffix != ".jar" or "sources" in jar.name:
        raise ValueError("A built runtime JAR is required")
    if properties(source / "eula.txt").get("eula") != "true":
        raise ValueError("Runtime must already have an accepted eula.txt")
    ports_free([args.game_port, args.rcon_port])
    runtime = [source / name for name in ("libraries", "versions", "fabric-server-launch.jar", "eula.txt")]
    mods = sorted(p for p in (source / "mods").glob("*.jar") if not p.name.lower().startswith("cobbleraids-"))
    if args.ascensionlib_jar:
        integration = args.ascensionlib_jar.resolve(strict=True)
        if not integration.name.startswith("AscensionLib-") or integration.suffix != ".jar" or "sources" in integration.name:
            raise ValueError("A built AscensionLib runtime JAR is required")
        mods = [p for p in mods if not p.name.lower().startswith("ascensionlib-")] + [integration]
    if args.probe_jar:
        probe = args.probe_jar.resolve(strict=True)
        if not probe.name.startswith("worldboss-perf-probe-") or probe.suffix != ".jar" or "sources" in probe.name:
            raise ValueError("A built rehearsal probe JAR is required")
        mods = [p for p in mods if not p.name.startswith("worldboss-perf-probe-")] + [probe]
    if not mods:
        raise ValueError("No runtime dependencies found")
    for path in [*runtime, *mods, jar]:
        if not path.exists():
            raise ValueError("Missing runtime file: " + str(path))
        reject_links(path)
    root.mkdir(parents=True, exist_ok=True)
    if root.is_relative_to(source) or source.is_relative_to(root):
        raise ValueError("Rehearsal root must be separate from runtime source")
    rig = root / ("worldboss-rehearsal-" + secrets.token_hex(8))
    rig.mkdir()  # Never overwrite an existing installation.
    for path in runtime:
        if path.is_dir():
            shutil.copytree(path, rig / path.name)
        else:
            shutil.copy2(path, rig / path.name)
    (rig / "mods").mkdir()
    for path in [*mods, jar]:
        shutil.copy2(path, rig / "mods" / path.name)
    config = rig / "config" / "cobbleraids"
    config.mkdir(parents=True)
    write_json(config / "world_boss.json", {
        "enabled": True, "raid_definitions": ["cobbleraids:giratina"],
        "pool": {"damage_per_player_minute": 1000},
        "spawn": {"auto_start": False, "join_countdown_seconds": 45},
        "arena": {"enabled": True, "radius": 96},
        "visual": {"boss_scale": 4, "duel_boss_scale": 1}})
    write_json(config / "server.json", {"natural_spawning": {"enabled": False}})
    settings = {
        "server-ip": "127.0.0.1", "server-port": args.game_port,
        "enable-rcon": "true", "rcon.port": args.rcon_port,
        "rcon.password": secrets.token_urlsafe(32), "broadcast-rcon-to-ops": "false",
        "online-mode": "false", "enable-query": "false", "max-players": 80,
        "level-name": "rehearsal-world", "level-type": "minecraft:flat",
        "generator-settings": json.dumps({"biome": "minecraft:plains", "layers": [
            {"block": "minecraft:bedrock", "height": 1},
            {"block": "minecraft:dirt", "height": 62},
            {"block": "minecraft:grass_block", "height": 1}]}, separators=(",", ":")),
        "view-distance": 4, "simulation-distance": 4, "spawn-protection": 0,
        "difficulty": "peaceful", "gamemode": "survival", "allow-flight": "true",
        "motd": "DISPOSABLE World Boss rehearsal", "sync-chunk-writes": "true"}
    (rig / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in settings.items()), encoding="utf-8")
    write_json(rig / "rehearsal.json", {"format": 1, "throwaway": True,
        "nonce": secrets.token_hex(16), "game_port": args.game_port, "rcon_port": args.rcon_port,
        "jar": jar.name, "jar_sha256": digest(jar),
        "mods": [{"name": p.name, "sha256": digest(p)} for p in [*mods, jar]],
        "runtime_source": str(source), "created_unix": time.time(), "pid": None,
        "fixture": "UNVALIDATED technical fixture; no live season or production calibration",
        "recovery_only": getattr(args, "recovery_only", False)})
    print(str(rig))


def load(rig):
    for path in (rig, *rig.parents, rig / "rehearsal-world"):
        if path.exists() and (path.is_symlink() or getattr(path.stat(), "st_file_attributes", 0) & 0x400):
            raise ValueError("Rehearsal path contains a link/reparse point")
    rig = rig.resolve(strict=True)
    reject_links(rig / "rehearsal.json")
    manifest = json.loads((rig / "rehearsal.json").read_text(encoding="utf-8"))
    props = properties(rig / "server.properties")
    if (manifest.get("format") != 1 or manifest.get("throwaway") is not True
            or not rig.name.startswith("worldboss-rehearsal-")
            or props.get("server-ip") != "127.0.0.1"
            or props.get("level-name") != "rehearsal-world"
            or props.get("enable-rcon") != "true"
            or props.get("server-port") != str(manifest["game_port"])
            or props.get("rcon.port") != str(manifest["rcon_port"])):
        raise ValueError("Rehearsal identity/configuration mismatch")
    return rig, manifest, props


def launch(args):
    rig, manifest, props = load(args.rig)
    restarting = getattr(args, "restart", False)
    if restarting and (manifest["pid"] is None or not previous_exited(manifest)):
        raise ValueError("Restart requires the recorded previous process to have exited")
    if manifest["pid"] is not None and not restarting:
        raise ValueError("Installation has already been launched; make a fresh rig")
    jar = rig / "mods" / manifest["jar"]
    if jar.parent != rig / "mods" or digest(jar) != manifest["jar_sha256"]:
        raise ValueError("Prepared runtime JAR was changed")
    for dependency in manifest.get("mods", []):
        path = rig / "mods" / dependency["name"]
        if path.parent != rig / "mods" or digest(path) != dependency["sha256"]:
            raise ValueError("Prepared dependency JAR was changed")
    ports_free([manifest["game_port"], manifest["rcon_port"]])
    java = args.java.resolve(strict=True)
    if java.name.lower() != "java.exe" or os.name != "nt":
        raise ValueError("This launcher requires Windows java.exe")
    launcher = rig / "fabric-server-launch.jar"
    output = rig
    if restarting:
        output = rig / "logs" / ("rehearsal-restart-" + str(time.time_ns()))
        output.mkdir()
        if (rig / "logs/latest.log").exists():
            shutil.copy2(rig / "logs/latest.log", output / "previous-latest.log")
    with (output / "console.out.log").open("xb" if restarting else "wb") as out, (output / "console.err.log").open("xb" if restarting else "wb") as err:
        process = subprocess.Popen([str(java), "-Xms1G", "-Xmx5G", "-jar", str(launcher), "nogui"],
            cwd=rig, stdin=subprocess.DEVNULL, stdout=out, stderr=err,
            creationflags=subprocess.CREATE_NO_WINDOW)
    if restarting:
        manifest.setdefault("launch_history", []).append({key: manifest.get(key) for key in (
            "pid", "java", "launcher", "launched_unix", "process_creation_filetime")})
    manifest.update(pid=process.pid, java=str(java), launcher=str(launcher), launched_unix=time.time())
    write_json(rig / "rehearsal.json", manifest)
    with WindowsProcess(process.pid) as owned:
        identity = owned.identity()
        if os.path.normcase(identity["exe"]) != os.path.normcase(str(java)):
            raise ValueError("Launched executable did not match the owned Java runtime")
        manifest["process_creation_filetime"] = identity["creation_filetime"]
    write_json(rig / "rehearsal.json", manifest)
    print(json.dumps({"pid": process.pid, "rig": str(rig)}))


def crash(args):
    rig, manifest, props = load(args.rig)
    # The held OS handle prevents a PID-reuse race between validation and termination.
    with WindowsProcess(manifest["pid"], terminate=True) as process:
        identity = process.identity()
        validate_identity(manifest, identity)
        verify_owner(manifest)
        evidence = {"scope": "Owned disposable JVM process termination; not power-loss evidence",
                    "pid": manifest["pid"], **identity, "started_unix": time.time(), "exited": False}
        path = rig / ("process-crash-" + str(time.time_ns()) + ".json")
        write_json(path, evidence)
        process.crash()
        evidence.update(exited=True, ended_unix=time.time())
        write_json(path, evidence)
    print(json.dumps(evidence))


def verify_owner(manifest):
    pid = manifest.get("pid")
    if type(pid) is not int or pid <= 0:
        raise ValueError("No owned process recorded")
    # Numeric PID only enters PowerShell code. No credentials or command text enter it.
    script = (f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId={pid}'; "
              "$listeners=@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | "
              f"Where-Object {{$_.OwningProcess -eq {pid}}} | Select-Object LocalAddress,LocalPort); "
              "@{exe=$p.ExecutablePath;command=$p.CommandLine;listeners=$listeners}|ConvertTo-Json -Depth 4 -Compress")
    result = subprocess.run(["powershell.exe", "-NoProfile", "-Command", script],
                            capture_output=True, text=True, check=True, timeout=20)
    owned = json.loads(result.stdout)
    if ((owned.get("exe") or "").lower() != manifest["java"].lower()
            or manifest["launcher"].lower() not in (owned.get("command") or "").lower()):
        raise ValueError("Recorded PID no longer belongs to this rehearsal")
    listeners = owned.get("listeners") or []
    for port in (manifest["game_port"], manifest["rcon_port"]):
        matches = [p for p in listeners if p["LocalPort"] == port]
        if not matches or any(p["LocalAddress"] != "127.0.0.1" for p in matches):
            raise ValueError("Private listeners are not ready or are not owned by this rehearsal")


def command(args):
    rig, manifest, props = load(args.rig)
    verify_owner(manifest)
    with Rcon("127.0.0.1", manifest["rcon_port"], props["rcon.password"]) as client:
        response = client.command(args.text)
    with (rig / "commands.jsonl").open("a", encoding="utf-8") as evidence:
        evidence.write(json.dumps({"unix": time.time(), "command": args.text, "response": response}) + "\n")
    print(response)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="action", required=True)
    prep = subs.add_parser("prepare")
    for name in ("runtime", "jar", "root"):
        prep.add_argument("--" + name, type=Path, required=True)
    prep.add_argument("--game-port", type=int, required=True)
    prep.add_argument("--rcon-port", type=int, required=True)
    prep.add_argument("--ascensionlib-jar", type=Path, help="Use a built compatible integration in the fresh rig only")
    prep.add_argument("--probe-jar", type=Path, help="Optional rehearsal-only full-tick recorder; never deploy this probe")
    prep.add_argument("--recovery-only", action="store_true", help="Exclude this process-crash fixture from capacity recording")
    start = subs.add_parser("launch")
    start.add_argument("--rig", type=Path, required=True)
    start.add_argument("--java", type=Path, required=True)
    resume = subs.add_parser("restart", help="Restart an exited, process-identified disposable recovery set")
    resume.add_argument("--rig", type=Path, required=True)
    resume.add_argument("--java", type=Path, required=True)
    resume.set_defaults(restart=True)
    killed = subs.add_parser("crash", help="Force-exit only the handle-verified disposable process")
    killed.add_argument("--rig", type=Path, required=True)
    cmd = subs.add_parser("command")
    cmd.add_argument("--rig", type=Path, required=True)
    cmd.add_argument("text")
    args = parser.parse_args()
    {"prepare": prepare, "launch": launch, "restart": launch, "crash": crash, "command": command}[args.action](args)


if __name__ == "__main__":
    main()
