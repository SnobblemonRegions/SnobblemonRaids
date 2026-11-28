"""Check the prepared private pack against the actual built prototype assets."""
from pathlib import Path
import json
import sys
import zipfile

workspace = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
rig = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else workspace / 'build/native-rehearsals/worldboss-rehearsal-84f8a7152b739cff'
source = workspace / 'build/latest-mods-2026-11-26/SnobblemonRaids/build/libs/CobbleRaids-0.8.211-worldboss-playtest.jar'
try:
    assert rig.is_relative_to(workspace / 'build/native-rehearsals')
    assert json.loads((rig / 'rehearsal.json').read_text())['throwaway'] is True
    with zipfile.ZipFile(rig / 'polymer/resource_pack.zip') as archive, zipfile.ZipFile(source) as expected:
        resources = {'models/item/worldboss_sovereign_' + name + '.json' for name in ['band','crest','halo','energy','ground']}
        for path in tuple(resources):
            model = json.loads(expected.read('assets/cobbleraids/' + path))
            for texture in model['textures'].values():
                assert texture.startswith('cobbleraids:item/sovereign_'), texture
                resources.add('textures/' + texture.split(':')[1] + '.png')
        for path in resources:
            resource = 'assets/cobbleraids/' + path
            assert archive.read(resource) == expected.read(resource), resource
        paper = json.loads(archive.read('assets/minecraft/models/item/paper.json'))
        owned = [row for row in paper.get('overrides', []) if row.get('model', '').startswith('cobbleraids:item/worldboss_sovereign_')]
        assert len(owned) == 5
        assert len({row['predicate']['custom_model_data'] for row in owned}) == 5
    print('Sovereign Seal prototype pack verified.')
except (OSError, zipfile.BadZipFile, KeyError, AssertionError, json.JSONDecodeError) as error:
    print('Prototype pack is not ready: ' + str(error))
    sys.exit(1)
