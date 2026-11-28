"""Normal production JAR graphical probe, isolated from the user's actual modpack configuration."""
from pathlib import Path
import argparse,json,os,re,shutil,subprocess,sys,time
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'validation/smoke'))
from smoke_test import Server,read_password,rcon_port,server_port,CONTAINED_FAULT,MIXIN_TROUBLE,SUSPECT_EXCEPTION
from rcon import Rcon
from make_pack import build_pack
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread

def main():
    p=argparse.ArgumentParser();p.add_argument('--server-dir',type=Path,required=True);p.add_argument('--output-dir',type=Path,required=True)
    p.add_argument('--java',type=Path,default=Path(r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.1+1\bin\java.exe'))
    p.add_argument('--instances-dir',type=Path,default=Path(r'L:\curseforge modpacks\Instances'))
    p.add_argument('--install-dir',type=Path,default=Path(r'L:\curseforge modpacks\Install'))

    p.add_argument("--integrated",action="store_true",help="Test the production radar with a server-delivered Polymer pack")
    args=p.parse_args();args.output_dir.mkdir(parents=True,exist_ok=True)
    run=args.output_dir/'client'
    for folder in ['mods','shaderpacks','config','resourcepacks','screenshots']:
        (run/folder).mkdir(parents=True,exist_ok=True)
    (args.output_dir/'screenshots').mkdir(exist_ok=True)
    for name in ('stage','integrated-ready','follow-turn','follow-verified','menu-verified','integrated-result.txt','failure.txt','integrated-projector.png','follow-projector.png'):
        path=args.output_dir/('screenshots' if name.endswith('.png') else '')/name
        path.unlink(missing_ok=True)
    for stage in range(4):
        (args.output_dir/'screenshots'/f'stage-{stage}.png').unlink(missing_ok=True)
    for pattern in ['iris-fabric-1.8.8+mc1.21.1.jar','sodium-fabric-0.6.13+mc1.21.1.jar']:
        source=args.instances_dir/'Cobblemon SMP/mods'/pattern
        shutil.copy2(source,run/'mods'/source.name)
    for pattern in ['Cobblemon*.jar','fabric-api*.jar']:
        source=next((args.server_dir/'mods').glob(pattern))
        shutil.copy2(source,run/'mods'/source.name)
    experiment=ROOT/'build/smoke-libs/hologram-experiment.jar'
    shutil.copy2(experiment,run/'mods'/experiment.name)
    pack=run/'resourcepacks/radar-prototype.zip'
    if not args.integrated:
        build_pack(pack)
        shutil.copy2(pack,args.output_dir/pack.name)
    shader=next(args.instances_dir.glob('*/shaderpacks/ComplementaryUnbound_r5.1.1.zip'))
    shutil.copy2(shader,run/'shaderpacks'/shader.name)
    (run/'config/iris.properties').write_text('enableShaders=true\nshaderPack='+shader.name+'\n',encoding='utf-8')
    options={'renderDistance':10,'simulationDistance':4,'maxFps':260,'enableVsync':'false',
        'guiScale':2,'pauseOnLostFocus':'false','fov':0.0,'onboardAccessibility':'false',
        'tutorialStep':'none','resourcePacks':'["vanilla","fabric"]' if args.integrated else '["vanilla","fabric","file/radar-prototype.zip"]'}
    (run/'options.txt').write_text(''.join(f'{key}:{value}\n' for key,value in options.items()),encoding='utf-8')
    metadata=json.loads((args.install_dir/'versions/1.21.1/1.21.1.json').read_text(encoding='utf-8'))
    libraries=[]
    for lib in metadata['libraries']:
        allowed=not lib.get('rules')
        for rule in lib.get('rules',[]):
            os_rule=rule.get('os',{})
            if os_rule.get('name','windows')=='windows' and os_rule.get('arch','x86_64') not in ('x86','arm64'):
                allowed=rule['action']=='allow'
        artifact=lib.get('downloads',{}).get('artifact')
        if allowed and artifact:
            jar=args.install_dir/'libraries'/artifact['path']
            if not jar.exists():
                group,module,version=lib['name'].split(':')[:3]
                cached=list((Path.home()/'.gradle/caches/modules-2/files-2.1'/group/module/version).glob('*/'+Path(artifact['path']).name))
                assert cached,jar;jar=cached[0]
            libraries.append(jar)
    for folder in ['net/fabricmc','org/ow2/asm']:
        libraries.extend((args.server_dir/'libraries'/folder).rglob('*.jar'))
    libraries.append(args.install_dir/'versions/1.21.1/1.21.1.jar')
    classpath=';'.join(str(x) for x in libraries)
    assert len(classpath)<28000,'Use an argument file for a longer classpath'
    server=Server(args.server_dir,args.java)
    client=None
    properties=args.server_dir/'server.properties'
    original_properties=properties.read_bytes()
    raid_config=args.server_dir/'config/cobbleraids/server.json'
    original_raid_config=raid_config.read_bytes() if args.integrated and raid_config.exists() else None
    installed=args.server_dir/'mods'/experiment.name
    assert not installed.exists(), f'Test mod already installed at {installed}'
    shutil.copy2(experiment,installed)
    name='RadarExperiment'
    server_jar=None
    original_jar=None
    web=None
    if args.integrated:
        server_jar=next((args.server_dir/'mods').glob('CobbleRaids-*.jar'))
        original_jar=server_jar.read_bytes()
        version=re.search(r"^version = '([^']+)'", (ROOT/'build.gradle').read_text(), re.MULTILINE).group(1)
        shutil.copy2(ROOT/f'build/libs/CobbleRaids-{version}.jar',server_jar)
    def pack_ready():
        with rcon() as connection:return 'accepted=true' in connection.command(f'execute as {name} run radarpack status')
    def rcon():
        return Rcon('127.0.0.1',rcon_port(args.server_dir),read_password(args.server_dir))
    def wait_for(predicate,seconds):
        deadline=time.monotonic()+seconds
        while time.monotonic()<deadline:
            failure=args.output_dir/'failure.txt'
            if failure.exists():raise RuntimeError(failure.read_text())
            if predicate():return
            if client.poll() is not None:
                raise RuntimeError('Client exited. See client.log')
            time.sleep(.5)
        raise TimeoutError('Client did not reach capture. See client.log')
    def png_written(path):
        if not path.exists() or path.stat().st_size<12:return False
        with path.open('rb') as image:
            image.seek(-8,2)
            return image.read(4)==b'IEND'
    def online():
        with rcon() as connection:return name in connection.command('list')
    try:
        if args.integrated:
            raid_settings=json.loads(original_raid_config) if original_raid_config is not None else {}
            raid_settings.setdefault('natural_spawning',{})['enabled']=False
            raid_config.parent.mkdir(parents=True,exist_ok=True)
            raid_config.write_text(json.dumps(raid_settings),encoding='utf-8')
        settings=original_properties.decode('utf-8')
        for key,value in {'level-name':'radar-natural-biome-world','level-type':'minecraft:normal',
                'level-seed':'20261030','generator-settings':'{}','view-distance':'10'}.items():
            settings=re.sub(r'^'+re.escape(key)+r'=.*$',lambda _:key+'='+value,settings,flags=re.MULTILINE)
        properties.write_text(settings,encoding='utf-8')
        server.start()
        server.wait_until_ready()
        if args.integrated:
            with rcon() as connection:
                response=connection.command('radarpack build')
                assert 'pack=true' in response,response
            hosting=args.output_dir/'host'
            hosting.mkdir(exist_ok=True)
            shutil.copy2(args.server_dir/'radar-projector-smoke.zip', hosting/'radar-projector-smoke.zip')
            web=ThreadingHTTPServer(('127.0.0.1',0),partial(SimpleHTTPRequestHandler,directory=str(hosting.resolve())))
            Thread(target=web.serve_forever,daemon=True).start()
        command=[str(args.java),'-Xmx4G','-Dradar.integrated='+str(args.integrated).lower(),'-Dradar.prototype.output='+str(args.output_dir.resolve()),
            '-Dfabric.gameVersion=1.21.1','-cp',classpath,'net.fabricmc.loader.impl.launch.knot.KnotClient',
            '--username',name,'--accessToken','0','--version','1.21.1','--gameDir',str(run.resolve()),
            '--assetsDir',str(Path.home()/'.gradle/caches/fabric-loom/assets'),'--assetIndex','1.21.1-17',
            '--width','1280','--height','720','--quickPlayMultiplayer','127.0.0.1:'+str(server_port(args.server_dir))]
        with (args.output_dir/'client.log').open('w',encoding='utf-8') as log:
            client=subprocess.Popen(command,cwd=run,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
            wait_for(online,180)
            assert 'Caught error loading resourcepacks' not in (args.output_dir/'client.log').read_text(encoding='utf-8',errors='replace')
            if args.integrated:
                with rcon() as connection:
                    connection.command(f'execute as {name} run radarpack offer http://127.0.0.1:{web.server_port}/radar-projector-smoke.zip')
                wait_for(pack_ready,120)
                with rcon() as connection:
                    for cmd in [f'gamemode creative {name}',f'clear {name}',f'tp {name} -104.5 118 -124.5 180 14',
                            'time set 6000','weather clear','cobbleraids despawn all',
                            'cobbleraids spawn charizard 35 118 -158',
                            f'item replace entity {name} hotbar.0 with cobbleraids:raid_radar']:
                        print(connection.command(cmd),flush=True)
                (args.output_dir/'integrated-ready').touch()
                wait_for(lambda:png_written(args.output_dir/'screenshots/integrated-projector.png'),90)
                print((args.output_dir/'projector-data.txt').read_text(),flush=True)
                wait_for(lambda:(args.output_dir/'menu-verified').exists(),15)
                with rcon() as connection:
                    print(connection.command(f'tp {name} -110.5 121 -124.5 -90 30'),flush=True)
                (args.output_dir/'follow-turn').touch()
                wait_for(lambda:(args.output_dir/'follow-verified').exists(),30)
                print((args.output_dir/'follow-verified').read_text(),flush=True)
                wait_for(lambda:(args.output_dir/'integrated-result.txt').exists(),60)
                print((args.output_dir/'integrated-result.txt').read_text(),flush=True)
            else:
                with rcon() as connection:
                    for cmd in [f'gamemode spectator {name}',f'tp {name} -104.5 118 -124.5 180 14','time set 6000','weather clear']:
                        print(connection.command(cmd),flush=True)
                time.sleep(12)
                for stage in (0,1,2,3):
                    with rcon() as connection:
                        reply=connection.command(f'execute as {name} run radarprototype {stage}')
                    print(reply,flush=True)
                    assert reply.startswith(('Projector removed','stage=')),reply
                    (args.output_dir/'stage').write_text(str(stage),encoding='utf-8')
                    wait_for(lambda:png_written(args.output_dir/'screenshots'/f'stage-{stage}.png'),60)
                    print((args.output_dir/f'frame-{stage}.txt').read_text(),flush=True)
                with rcon() as connection:
                    print(connection.command(f'execute as {name} run radarprototype 0'),flush=True)
    finally:
        if client and client.poll() is None:
            subprocess.run(['taskkill','/PID',str(client.pid),'/T','/F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        try:server.stop()
        finally:
            properties.write_bytes(original_properties)
            installed.unlink(missing_ok=True)
            if web is not None:web.shutdown();web.server_close()
            if original_jar is not None:server_jar.write_bytes(original_jar)
            if args.integrated:
                if original_raid_config is None:raid_config.unlink(missing_ok=True)
                else:raid_config.write_bytes(original_raid_config)
    print('Captures saved in '+str(args.output_dir),flush=True)

if __name__=='__main__':main()
