"""Inspect the real artifacts: server-only base, optional client, no shipped test probe/shader overrides."""
from pathlib import Path
import json,re,struct,zipfile
ROOT=Path(__file__).resolve().parents[2]
server_version=re.search(r"version = '([^']+)'",(ROOT/'build.gradle').read_text(encoding='utf-8')).group(1)
client_version=re.search(r"version = '([^']+)'",(ROOT/'radar-client/build.gradle').read_text(encoding='utf-8')).group(1)
server=ROOT/'build/libs'/f'CobbleRaids-{server_version}.jar'
client=ROOT/'radar-client/build/libs'/f'CobbleRaids-Radar-Client-{client_version}.jar'
with zipfile.ZipFile(server) as z:
    names=z.namelist();manifest=json.loads(z.read('fabric.mod.json'))
    assert 'client' not in manifest['entrypoints']
    assert 'cobbleraids_radar' not in manifest['depends']
    assert not any(n.startswith(('com/cobbleraids/radarclient/', 'com/cobbleraids/veilclient/')) for n in names)
    assert 'com/cobbleraids/visual/protocol/VeilPayload.class' in names
    assert 'com/cobbleraids/visual/protocol/BossHudPayload.class' in names
    assert 'com/cobbleraids/radar/protocol/RadarHologramPayload.class' in names
with zipfile.ZipFile(client) as z:
    names=z.namelist();manifest=json.loads(z.read('fabric.mod.json'))
    assert manifest['environment']=='client'
    assert 'main' not in manifest['entrypoints']
    assert 'cobbleraids' not in manifest['depends']
    assert not any(n.startswith('com/cobbleraids/radarprobe/') for n in names)
    assert not any(n.startswith('com/cobbleraids/raid/') for n in names)
    assert not any(n.startswith('assets/minecraft/shaders/') for n in names)
    assert not any(n.startswith('assets/minecraft/font/') for n in names), 'HUD typography must not replace other interfaces'
    assert not any(n.startswith('assets/cobbleraids_radar/font/') and not n.endswith('/') for n in names), 'HUD uses the native game font'
    assert 'assets/cobbleraids_radar/textures/white.png' in names
    assert 'com.cobbleraids.veilclient.DistortionVeilClient' in manifest['entrypoints']['client']
    for texture in ['veil_silk', 'umbra_wisp', 'veil_mote']:
        assert f'assets/cobbleraids_radar/textures/worldboss/{texture}.png' in names
        assert json.loads(z.read(f'assets/cobbleraids_radar/textures/worldboss/{texture}.png.mcmeta'))['texture']['blur'] is True
    config=json.loads(z.read('cobbleraids-radar.mixins.json'))
    assert 'WorldBossHudMixin' in config['client']
    for texture in ['rift_crown','rift_health','rift_wounded','rift_critical','rift_crown_flair']:
        assert f'assets/cobbleraids_radar/textures/worldboss/{texture}.png' in names
        assert json.loads(z.read(f'assets/cobbleraids_radar/textures/worldboss/{texture}.png.mcmeta'))['texture']['blur'] is False
        data=z.read(f'assets/cobbleraids_radar/textures/worldboss/{texture}.png')
        expected=(360,1536) if texture=='rift_crown_flair' else (2172,724) if texture=='rift_crown' else (189,6)
        assert data[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',data[16:24])==expected, 'HUD draw coordinates must match authored sprite dimensions'
    for mixin in config['client']:
        assert 'com/cobbleraids/radarclient/mixin/'+mixin+'.class' in names
print('Hologram artifact boundary: PASS -- separate optional client, common protocol, no core shader overrides or test probe')
