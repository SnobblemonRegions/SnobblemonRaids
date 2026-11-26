"""Renowned raid boss look: a runtime colour tint over every raid species' own texture.

A renowned boss carries the aspect `cobbleraids_renowned` (RenownLook.java). For every species a raid definition names, this
script writes one resolver file whose variations match that aspect and add one layer, `cobbleraids_renowned`: the species'
own texture, drawn again with a dark bronze tint. Nothing is copied or edited: the layer points at the texture path the
player's own packs already provide (Cobblemon's, or the Complete Cobblemon Collection's for the legendaries), and the game
multiplies it by the tint while drawing. That keeps us clear of the CCC licence (no modified assets, no bundling), and gives
every species the same treatment.

Draw order: Cobblemon gathers every matching variation's layers into one map, in resolver order, and draws them in that
order. Our file has a negative order, so its layer goes in first and a species' own layers (Charizard's flame, alpha eyes)
still draw on top of it.

Usage: python tools/renown_skins.py <Cobblemon jar> [<model pack zip> ...]
Later sources win where they define the same variation (a model pack replacing Cobblemon's Xerneas, say). Reads paths only.
Writes src/main/resources/assets/cobblemon/bedrock/pokemon/resolvers/zz_cobbleraids_renowned/ and the list the server adds to
the Polymer resource pack (assets/cobbleraids/renowned_skins.txt).
"""
import glob
import json
import os
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
RESOLVER_DIR = 'assets/cobblemon/bedrock/pokemon/resolvers/zz_cobbleraids_renowned'
OLD_TEXTURES = 'assets/cobbleraids/textures/pokemon/renowned'
INDEX = 'assets/cobbleraids/renowned_skins.txt'
ASPECT = 'cobbleraids_renowned'
LAYER = 'cobbleraids_renowned'
ORDER = -1000
# Multiplied into the texture: darkens every colour toward a warm obsidian while keeping its own hue and shading.
TINT = [0.46, 0.38, 0.30, 1.0]


def raid_species():
    names = set()
    for path in glob.glob(os.path.join(RES, 'data', 'cobbleraids', 'raids', '*.json')):
        with open(path, encoding='utf-8') as f:
            names.add(json.load(f)['species'].split(':')[-1])
    return sorted(names)


def resolvers(sources):
    """species -> [(order, source index, resolver)], every source's resolvers for it."""
    found = {}
    for index, path in enumerate(sources):
        archive = zipfile.ZipFile(path)
        for name in archive.namelist():
            if '/bedrock/pokemon/resolvers/' not in name or not name.endswith('.json'):
                continue
            try:
                data = json.loads(archive.read(name))
                species = data['species'].split(':')[-1]
            except (ValueError, KeyError):
                continue
            found.setdefault(species, []).append((data.get('order', 0), index, data))
    return found


def textures(entries):
    """aspects -> the texture a player sees for them, the last definition winning (as Cobblemon applies them). Shiny skipped."""
    chosen = {}
    for _, _, data in sorted(entries, key=lambda e: (e[1], e[0])):
        for variation in data.get('variations', []):
            texture = variation.get('texture')
            aspects = variation.get('aspects', [])
            if texture is None or 'shiny' in aspects:
                continue
            chosen[tuple(aspects)] = texture
    return chosen


def build(sources):
    found = resolvers(sources)
    out_dir = os.path.join(RES, RESOLVER_DIR)
    shutil.rmtree(out_dir, ignore_errors=True)
    shutil.rmtree(os.path.join(RES, OLD_TEXTURES), ignore_errors=True)
    os.makedirs(out_dir)
    index, skipped = [], []
    for species in raid_species():
        chosen = textures(found.get(species, []))
        if not chosen:
            skipped.append(species)
            continue
        variations = []
        for aspects in sorted(chosen, key=len):   # the plain form first, so a form's own layer replaces it
            variations.append({
                'aspects': list(aspects) + [ASPECT],
                'layers': [{'name': LAYER, 'texture': chosen[aspects], 'tint': TINT}],
            })
        rel = f'{RESOLVER_DIR}/{species}_renowned.json'
        with open(os.path.join(RES, rel), 'w', encoding='utf-8', newline='\n') as f:
            json.dump({'species': f'cobblemon:{species}', 'order': ORDER, 'variations': variations}, f, indent=1)
            f.write('\n')
        index.append(rel)
    with open(os.path.join(RES, INDEX), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(sorted(index)) + '\n')
    print(f'{len(index)} species tinted; no texture found for {len(skipped)}: {" ".join(skipped)}')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    build(sys.argv[1:])
