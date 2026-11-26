"""Renowned raid boss skins: an obsidian-and-gold recolour of every raid species' own textures.

A renowned boss carries the aspect `cobbleraids_renowned` (RenownLook.java). This script writes, for every species a raid
definition names, one resolver file whose variations match that aspect and swap only the texture, so the model, poser and
layers (Charizard's flame, alpha eyes) stay Cobblemon's own. Textures are recoloured from Cobblemon's, never hand-painted:

  - each pixel keeps its lightness, so the artist's shading survives;
  - the texture's main colour (the most common saturated hue) becomes obsidian, lit with bronze;
  - every other colour (wings, markings) becomes gold, so a second colour still reads;
  - near-white and near-grey pixels (eyes, teeth, claws) become a pale gold-white.

Usage: python tools/renown_skins.py <Cobblemon jar> [--preview out.png species...]
Writes src/main/resources/assets/cobbleraids/textures/pokemon/renowned/ and
src/main/resources/assets/cobblemon/bedrock/pokemon/resolvers/zz_cobbleraids_renowned/, plus the list the server adds to the
Polymer resource pack (assets/cobbleraids/renowned_skins.txt). Species with no resolver in the jar (Cobblemon ships no model
for most legendaries; a server's model pack does) are listed and skipped.
"""
import colorsys
import glob
import io
import json
import os
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
TEX_DIR = 'assets/cobbleraids/textures/pokemon/renowned'
RESOLVER_DIR = 'assets/cobblemon/bedrock/pokemon/resolvers/zz_cobbleraids_renowned'
INDEX = 'assets/cobbleraids/renowned_skins.txt'
ASPECT = 'cobbleraids_renowned'
ORDER = 1000  # after Cobblemon's own variations (order 0), so ours is applied last and wins the texture

# (position 0..1 along lightness, RGB)
BODY = [(0.0, (14, 11, 18)), (0.30, (38, 31, 44)), (0.55, (70, 57, 70)), (0.78, (134, 98, 52)), (1.0, (222, 178, 98))]
ACCENT = [(0.0, (34, 20, 6)), (0.30, (110, 72, 18)), (0.55, (186, 132, 34)), (0.78, (236, 192, 74)), (1.0, (255, 238, 168))]
NEUTRAL = [(0.0, (16, 14, 18)), (0.40, (70, 62, 58)), (0.75, (196, 180, 150)), (1.0, (255, 246, 214))]
HUE_BAND = 0.09   # within this much hue (0..1) of the main colour counts as the body
GREY_SAT = 0.16   # below this saturation a pixel is neutral


def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        if t <= b:
            u = (t - a) / (b - a) if b > a else 0.0
            return tuple(round(ca[i] + (cb[i] - ca[i]) * u) for i in range(3))
    return stops[-1][1]


def hue_distance(a, b):
    d = abs(a - b) % 1.0
    return min(d, 1.0 - d)


def main_hue(img):
    """The most common hue among saturated, visible pixels, weighted by saturation."""
    bins = [0.0] * 36
    for r, g, b, a in (img.get_flattened_data() if hasattr(img, 'get_flattened_data') else img.getdata()):
        if a < 128:
            continue
        h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        if s < GREY_SAT or l < 0.08 or l > 0.95:
            continue
        bins[int(h * 36) % 36] += s
    if not any(bins):
        return None
    best = max(range(36), key=lambda i: bins[i - 1] + bins[i] + bins[(i + 1) % 36])
    return (best + 0.5) / 36


def recolour(img):
    img = img.convert('RGBA')
    body = main_hue(img)
    out = Image.new('RGBA', img.size)
    src, dst = img.load(), out.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            hue, light, sat = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
            if sat < GREY_SAT:
                rgb = ramp(NEUTRAL, lum)
            elif body is None or hue_distance(hue, body) <= HUE_BAND:
                rgb = ramp(BODY, lum * 1.1)
            else:
                rgb = ramp(ACCENT, lum * 1.05)
            dst[x, y] = rgb + (a,)
    return out


def raid_species():
    names = set()
    for path in glob.glob(os.path.join(RES, 'data', 'cobbleraids', 'raids', '*.json')):
        with open(path, encoding='utf-8') as f:
            names.add(json.load(f)['species'].split(':')[-1])
    return sorted(names)


def resolvers(jar):
    by_species = {}
    for name in jar.namelist():
        if name.startswith('assets/cobblemon/bedrock/pokemon/resolvers/') and name.endswith('.json'):
            data = json.loads(jar.read(name))
            by_species.setdefault(data['species'].split(':')[-1], []).append(data)
    return by_species


def texture_path(location):
    ns, path = location.split(':', 1)
    return f'assets/{ns}/{path}'


def build(jar_path, preview=None, preview_species=()):
    jar = zipfile.ZipFile(jar_path)
    found = resolvers(jar)
    written, skipped, index = 0, [], []
    previews = []
    for species in raid_species():
        if species not in found:
            skipped.append(species)
            continue
        variations, done = [], {}
        for data in sorted(found[species], key=lambda d: d.get('order', 0)):
            for v in data['variations']:
                tex = v.get('texture')
                aspects = v.get('aspects', [])
                if not isinstance(tex, str) or 'shiny' in aspects:
                    continue  # animated textures and shiny swaps: the base recolour covers them
                if tex not in done:
                    recoloured = recolour(Image.open(io.BytesIO(jar.read(texture_path(tex)))))
                    file = os.path.basename(tex.split(':', 1)[1]).replace('.png', '_renowned.png')
                    rel = f'{TEX_DIR}/{species}/{file}'
                    if preview is None:
                        os.makedirs(os.path.join(RES, os.path.dirname(rel)), exist_ok=True)
                        recoloured.save(os.path.join(RES, rel), optimize=True)
                        index.append(rel)
                    elif species in preview_species:
                        previews.append((Image.open(io.BytesIO(jar.read(texture_path(tex)))).convert('RGBA'), recoloured))
                    done[tex] = 'cobbleraids:' + rel.split('/', 2)[2]
                variations.append({'aspects': aspects + [ASPECT], 'texture': done[tex]})
        if not variations:
            skipped.append(species)
            continue
        if preview is None:
            rel = f'{RESOLVER_DIR}/{species}_renowned.json'
            os.makedirs(os.path.join(RES, RESOLVER_DIR), exist_ok=True)
            with open(os.path.join(RES, rel), 'w', encoding='utf-8', newline='\n') as f:
                json.dump({'species': f'cobblemon:{species}', 'order': ORDER, 'variations': variations}, f, indent=1)
                f.write('\n')
            index.append(rel)
        written += 1
    if preview is not None:
        scale, gap = 3, 8
        width = max(a.width + b.width for a, b in previews) * scale + gap * 2
        height = sum(max(a.height, b.height) * scale + gap for a, b in previews)
        sheet = Image.new('RGBA', (width, height), (52, 56, 64, 255))
        y = 0
        for a, b in previews:
            sheet.alpha_composite(a.resize((a.width * scale, a.height * scale), Image.NEAREST), (0, y))
            sheet.alpha_composite(b.resize((b.width * scale, b.height * scale), Image.NEAREST), (a.width * scale + gap, y))
            y += max(a.height, b.height) * scale + gap
        sheet.save(preview)
        print('preview', preview)
        return
    with open(os.path.join(RES, INDEX), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(sorted(index)) + '\n')
    print(f'{written} species skinned, {len(index)} files; skipped (no resolver in this jar): {len(skipped)}')
    print(' ', ' '.join(skipped))


if __name__ == '__main__':
    args = sys.argv[1:]
    if not args:
        sys.exit(__doc__)
    if '--preview' in args:
        i = args.index('--preview')
        build(args[0], args[i + 1], set(args[i + 2:]))
    else:
        build(args[0])
