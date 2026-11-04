"""Graphical probe of the SERVER-SIDE radar hologram: a real Minecraft client with NO CobbleRaids code joins an isolated test server, opens the radar, and
captures the status menu and the hologram. Isolated from the user's modpack: it writes only under radar-client/run-server-hologram-smoke.

    python validation/radar/server_hologram_smoke.py --server-dir <isolatedRig> --output-dir <results> [--shaders]

Never point --server-dir at a production world: it boots the rig, changes its world and stops it.
"""
from pathlib import Path
import argparse, functools, hashlib, http.server, json, re, shutil, subprocess, sys, threading, time
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'validation/smoke'))
from smoke_test import Server, read_password, rcon_port, server_port, CONTAINED_FAULT, MIXIN_TROUBLE, SUSPECT_EXCEPTION
from rcon import Rcon


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--server-dir', type=Path, required=True)
    p.add_argument('--output-dir', type=Path, required=True)
    p.add_argument('--java', type=Path, default=Path(r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.1+1\bin\java.exe'))
    p.add_argument('--instances-dir', type=Path, default=Path(r'L:\curseforge modpacks\Instances'))
    p.add_argument('--install-dir', type=Path, default=Path(r'L:\curseforge modpacks\Install'))
    p.add_argument('--shaders', action='store_true', help='Also run Iris + Sodium + Complementary, as players with shader packs do')
    args = p.parse_args()
    # The client runs from another directory, so every path it is handed must be absolute.
    args.server_dir, args.output_dir = args.server_dir.resolve(), args.output_dir.resolve()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    run = ROOT / 'radar-client/run-server-hologram-smoke'
    shutil.rmtree(run / 'mods', ignore_errors=True)
    for folder in ['mods', 'shaderpacks', 'config']:
        (run / folder).mkdir(parents=True, exist_ok=True)
    for pattern in ['Cobblemon*.jar', 'fabric-api*.jar']:
        source = next((args.server_dir / 'mods').glob(pattern))
        shutil.copy2(source, run / 'mods' / source.name)
    shutil.copy2(ROOT / 'radar-client/build/smoke-libs/CobbleRaids-Server-Hologram-Probe.jar', run / 'mods/CobbleRaids-Server-Hologram-Probe.jar')
    # Deliberately NOT installed: any CobbleRaids jar. The companion would tell the server to remove its own hologram.
    if args.shaders:
        for pattern in ['iris-fabric-1.8.8+mc1.21.1.jar', 'sodium-fabric-0.6.13+mc1.21.1.jar']:
            source = args.instances_dir / 'Cobblemon SMP/mods' / pattern
            shutil.copy2(source, run / 'mods' / source.name)
        pack = next(args.instances_dir.glob('*/shaderpacks/ComplementaryUnbound_r5.1.1.zip'))
        shutil.copy2(pack, run / 'shaderpacks' / pack.name)
        (run / 'config/iris.properties').write_text('enableShaders=true\nshaderPack=ComplementaryUnbound_r5.1.1.zip\n', encoding='utf-8')
    else:
        (run / 'config/iris.properties').unlink(missing_ok=True)
    (run / 'options.txt').write_text('renderDistance:8\nsimulationDistance:6\nmaxFps:60\nenableVsync:false\nguiScale:2\npauseOnLostFocus:false\n'
                                     'onboardAccessibility:false\ntutorialStep:none\n', encoding='utf-8')
    for name in ['graphical-result.json', 'graphical-failure.txt', 'fixture-ready', 'server-hologram-counts.json']:
        (args.output_dir / name).unlink(missing_ok=True)
    shutil.rmtree(args.output_dir / 'screenshots', ignore_errors=True)

    metadata = json.loads((args.install_dir / 'versions/1.21.1/1.21.1.json').read_text(encoding='utf-8'))
    libraries = []
    for lib in metadata['libraries']:
        allowed = not lib.get('rules')
        for rule in lib.get('rules', []):
            os_rule = rule.get('os', {})
            if os_rule.get('name', 'windows') == 'windows' and os_rule.get('arch', 'x86_64') not in ('x86', 'arm64'):
                allowed = rule['action'] == 'allow'
        artifact = lib.get('downloads', {}).get('artifact')
        if allowed and artifact:
            jar = args.install_dir / 'libraries' / artifact['path']
            if not jar.exists():
                group, module, version = lib['name'].split(':')[:3]
                cached = list((Path.home() / '.gradle/caches/modules-2/files-2.1' / group / module / version).glob('*/' + Path(artifact['path']).name))
                assert cached, jar
                jar = cached[0]
            libraries.append(jar)
    for folder in ['net/fabricmc', 'org/ow2/asm']:
        libraries.extend((args.server_dir / 'libraries' / folder).rglob('*.jar'))
    libraries.append(args.install_dir / 'versions/1.21.1/1.21.1.jar')
    classpath = ';'.join(str(x) for x in libraries)
    assert len(classpath) < 28000, 'Use an argument file for a longer classpath'

    server = Server(args.server_dir, args.java)
    client = None
    properties = args.server_dir / 'server.properties'
    original_properties = properties.read_bytes()
    host = None
    try:
        server.start()
        server.wait_until_ready()
        # Deliver Polymer's resource pack the way a real server does: build it, host it, and point server.properties at it with its SHA-1 and Polymer's own
        # pack id. Polymer builds the pack only when asked, and a pack left from an earlier build would hide this build's models behind the old ones: the client
        # sees a hash it already has and never fetches the new pack. The client accepts the prompt, so Polymer really sees the pack loaded, and nothing is slipped
        # into the client. server.properties is read only at start, so the server is restarted, and the original file is put back afterwards.
        pack = args.server_dir / 'polymer/resource_pack.zip'
        before = pack.stat().st_mtime if pack.exists() else 0
        with Rcon('127.0.0.1', rcon_port(args.server_dir), read_password(args.server_dir)) as rcon:
            print('polymer generate-pack:', ' '.join(rcon.command('polymer generate-pack').split())[:200], flush=True)
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline and not (pack.exists() and pack.stat().st_mtime > before):
            time.sleep(1)
        assert pack.exists() and pack.stat().st_mtime > before, 'Polymer did not write a fresh resource pack'
        time.sleep(2)
        server.stop()
        sha1 = hashlib.sha1(pack.read_bytes()).hexdigest()
        handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(pack.parent))
        host = http.server.ThreadingHTTPServer(('127.0.0.1', 0), handler)
        threading.Thread(target=host.serve_forever, daemon=True).start()
        pack_id = json.loads((args.server_dir / 'config/polymer/resource-pack.json').read_text(encoding='utf-8'))['main_uuid']
        settings = original_properties.decode('utf-8')
        for key, value in {'resource-pack': f'http://127.0.0.1:{host.server_address[1]}/{pack.name}', 'resource-pack-sha1': sha1,
                           'resource-pack-id': pack_id, 'require-resource-pack': 'false'}.items():
            settings = re.sub(r'^' + re.escape(key) + r'=.*$', lambda _: key + '=' + value, settings, flags=re.MULTILINE)
        properties.write_text(settings, encoding='utf-8')
        server = Server(args.server_dir, args.java)
        server.start()
        server.wait_until_ready()
        command = [str(args.java), '-Xmx4G', '-Dcobbleraids.radar.smoke.output=' + str(args.output_dir), '-Dfabric.gameVersion=1.21.1', '-cp', classpath,
                   'net.fabricmc.loader.impl.launch.knot.KnotClient', '--username', 'HologramProbe', '--accessToken', '0', '--version', '1.21.1',
                   '--gameDir', str(run), '--assetsDir', str(Path.home() / '.gradle/caches/fabric-loom/assets'), '--assetIndex', '1.21.1-17',
                   '--width', '1280', '--height', '720', '--quickPlayMultiplayer', '127.0.0.1:' + str(server_port(args.server_dir))]
        with (args.output_dir / 'graphical-client.log').open('w', encoding='utf-8') as log:
            client = subprocess.Popen(command, cwd=run, stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
            ready = False
            deadline = time.monotonic() + 420
            while client.poll() is None and time.monotonic() < deadline:
                with Rcon('127.0.0.1', rcon_port(args.server_dir), read_password(args.server_dir)) as rcon:
                    if not ready and 'HologramProbe' in rcon.command('list'):
                        for cmd in ['fill -16 3 -16 16 3 16 minecraft:stone', 'tp HologramProbe 0.5 4 0.5', 'gamemode creative HologramProbe',
                                    'clear HologramProbe', 'time set day', 'weather clear', 'forceload add 32 -160 160 -32',
                                    'gamerule commandModificationBlockLimit 1000000', 'fill 32 4 -160 160 11 -32 minecraft:stone',
                                    'fill 48 12 -144 144 19 -48 minecraft:stone', 'fill 64 20 -128 128 27 -64 minecraft:stone',
                                    'fill 80 28 -112 112 35 -80 minecraft:stone', 'cobbleraids spawn charizard 96 48 -96',
                                    'item replace entity HologramProbe hotbar.0 with cobbleraids:raid_radar']:
                            response = rcon.command(cmd)
                            print(response[:200], flush=True)
                            assert 'position is not loaded' not in response.lower(), response
                            if cmd.startswith('forceload add'):
                                time.sleep(3)
                        (args.output_dir / 'fixture-ready').write_text('ready', encoding='utf-8')
                        ready = True
                if (args.output_dir / 'graphical-result.json').exists():
                    break
                if (args.output_dir / 'graphical-failure.txt').exists():
                    raise AssertionError((args.output_dir / 'graphical-failure.txt').read_text())
                time.sleep(2)
            assert (args.output_dir / 'graphical-result.json').exists(), 'Graphical probe did not finish; inspect graphical-client.log'
            client.wait(timeout=40)
    finally:
        try:
            if client and client.poll() is None:
                subprocess.run(['taskkill', '/PID', str(client.pid), '/T', '/F'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if server.process and server.process.poll() is None:
                with Rcon('127.0.0.1', rcon_port(args.server_dir), read_password(args.server_dir)) as rcon:
                    rcon.command('forceload remove 32 -160 160 -32')
        finally:
            server.stop()
            properties.write_bytes(original_properties)
            if host:
                host.shutdown()
    for regex in (CONTAINED_FAULT, MIXIN_TROUBLE, SUSPECT_EXCEPTION):
        assert not regex.search(server.read_log()), regex.pattern
    print('SERVER HOLOGRAM (real client, no mod): PASS', flush=True)


if __name__ == '__main__':
    main()
