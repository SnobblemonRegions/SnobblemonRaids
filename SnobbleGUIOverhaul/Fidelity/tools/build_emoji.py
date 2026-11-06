"""Colour emoji for the native pages (the web pages used the system emoji font, e.g. Segoe UI Emoji, which cannot be shipped).

Renders Twemoji SVGs (CC-BY 4.0, Fidelity/emoji_src/twemoji_svg/<codepoint>.svg) into one atlas, 48px cells, and adds a bitmap
provider for it to every page font (snobblemon_regions/font/<name>_<px>.json), sized to that font's em, so an emoji inside a
label draws inline like in the browser. Also writes the codepoint list into snobblemon_fonts.json ("emoji") - the client only
lets those characters through (anything else non-BMP is still removed so it never shows as a box).

  python build_emoji.py
"""
import json, os, sys, time, math
sys.path.insert(0, os.path.dirname(__file__))
import fid
from PIL import Image

ROOT = fid.ROOT
SRC = os.path.join(ROOT, 'emoji_src', 'twemoji_svg')
RES = os.path.abspath(os.path.join(ROOT, '..', 'Two-Mod Rework', 'snobblemon-server', 'src', 'main', 'resources', 'assets', 'snobblemon_regions'))
FONTS = os.path.join(RES, 'font')
CELL = 48


def render():
    from playwright.sync_api import sync_playwright
    cps = sorted(f[:-4] for f in os.listdir(SRC) if f.endswith('.svg'))
    cols = math.ceil(math.sqrt(len(cps)))
    rows = math.ceil(len(cps) / cols)
    atlas = Image.new('RGBA', (cols * CELL, rows * CELL), (0, 0, 0, 0))
    tmp = os.path.join(ROOT, 'emoji_src', '_png')
    os.makedirs(tmp, exist_ok=True)
    with sync_playwright() as p:
        b = p.chromium.launch(executable_path=fid.CHROME, headless=True)
        pg = b.new_page(viewport={'width': CELL, 'height': CELL})
        for cp in cps:
            svg = open(os.path.join(SRC, cp + '.svg'), encoding='utf8').read()
            # 1px inset so neighbouring cells never bleed; the emoji fills the em like the system font's
            pg.set_content('<html><body style="margin:0;background:transparent"><div style="width:%dpx;height:%dpx;padding:1px;box-sizing:border-box">%s</div></body></html>'
                           % (CELL, CELL, svg.replace('<svg ', '<svg width="%d" height="%d" ' % (CELL - 2, CELL - 2), 1)))
            out = os.path.join(tmp, cp + '.png')
            pg.screenshot(path=out, omit_background=True, clip={'x': 0, 'y': 0, 'width': CELL, 'height': CELL})
        b.close()
    grid = []
    for i, cp in enumerate(cps):
        im = Image.open(os.path.join(tmp, cp + '.png')).convert('RGBA')
        atlas.alpha_composite(im, ((i % cols) * CELL, (i // cols) * CELL))
    chars = []
    for r in range(rows):
        row = ''
        for c in range(cols):
            i = r * cols + c
            row += chr(int(cps[i], 16)) if i < len(cps) else '\u0000'
        chars.append(row)
    os.makedirs(os.path.join(RES, 'textures', 'font'), exist_ok=True)
    atlas.save(os.path.join(RES, 'textures', 'font', 'emoji.png'))
    return cps, chars


def patch_fonts(cps, chars):
    n = 0
    for f in os.listdir(FONTS):
        if not f.endswith('.json') or f == 'emoji.json':
            continue
        try:
            px = int(f.rsplit('_', 1)[1][:-5])
        except (IndexError, ValueError):
            continue
        try:
            d = json.load(open(os.path.join(FONTS, f), encoding='utf8'))
        except ValueError:      # a file cut short by an earlier failed run: rebuild the font's base providers
            d = {'providers': [{'type': 'ttf', 'file': 'snobblemon_regions:%s.ttf' % f.rsplit('_', 1)[0], 'size': px, 'oversample': 1.0, 'shift': [0, 0], 'skip': ''},
                               {'type': 'reference', 'id': 'minecraft:default'}]}
        prov = [p for p in d['providers'] if not (p.get('type') == 'bitmap' and p.get('file', '').endswith('font/emoji.png'))]
        # an emoji is about one em tall and sits like a capital letter with a small descent (Segoe UI Emoji: ~0.8 em above the baseline)
        em = {'type': 'bitmap', 'file': 'snobblemon_regions:font/emoji.png', 'height': px, 'ascent': int(round(px * 0.82)), 'chars': chars}
        ref = [i for i, p in enumerate(prov) if p.get('type') == 'reference']
        prov.insert(ref[0] if ref else len(prov), em)
        d['providers'] = prov
        json.dump(d, open(os.path.join(FONTS, f), 'w', encoding='utf8'))
        n += 1
    meta_p = os.path.join(RES, 'snobblemon_fonts.json')
    meta = json.load(open(meta_p))
    meta['emoji'] = [int(c, 16) for c in cps]
    json.dump(meta, open(meta_p, 'w'), indent=1)
    lic = os.path.join(FONTS, 'licenses')
    os.makedirs(lic, exist_ok=True)
    open(os.path.join(lic, 'emoji_twemoji.txt'), 'w', encoding='utf8').write(
        'Emoji graphics: Twemoji, Copyright 2019 Twitter, Inc and other contributors, https://github.com/jdecked/twemoji\n'
        'Licensed under CC-BY 4.0: https://creativecommons.org/licenses/by/4.0/\n'
        'Used unchanged except for rasterising to textures/font/emoji.png.\n')
    print('patched', n, 'font files;', len(cps), 'emoji')


if __name__ == '__main__':
    patch_fonts(*render())
