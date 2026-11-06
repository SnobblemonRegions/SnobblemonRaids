"""Crops the CobbleTowers sprite sheets into one PNG per icon, in the SERVER pack (textures/gui/pages/cobbletowers/):
   item_<ns>_<path>.png  from assets/items.png     (map: Ce in app.js, entries [x, y, w, h, ...])
   ico_<name>.png        from assets/gui/icons.png (16px strip, names: nt in app.js)
   sp_<species>.png      from assets/partners.webp (map: pe in app.js; trimmed to the sprite box)
   Writes icons.json (lists) next to them."""
import json, os, re
from PIL import Image

WEB = r'G:\Claude\SnobbleGUIOverhaul\webgui_pages_src\extracted\webgui\web\cobbletowers'
OUT = r'G:\Claude\SnobbleGUIOverhaul\Two-Mod Rework\snobblemon-server\src\main\resources\assets\snobblemon_regions\textures\gui\pages\cobbletowers'


def obj_after(s, start_pat):
    """the {...} literal that follows start_pat (brace matching, strings respected)"""
    i = s.find(start_pat)
    j = s.find('{', i)
    d, k, q = 0, j, None
    while True:
        c = s[k]
        if q:
            if c == '\\':
                k += 1
            elif c == q:
                q = None
        elif c in '"\'`':
            q = c
        elif c == '{':
            d += 1
        elif c == '}':
            d -= 1
            if d == 0:
                return s[j:k + 1]
        k += 1


def js_obj(txt):
    # keys are quoted or bare identifiers; values numbers / arrays / strings
    t = re.sub(r'([{,])\s*([A-Za-z_][A-Za-z0-9_]*)\s*:', r'\1"\2":', txt)
    return json.loads(t)


def main():
    os.makedirs(OUT, exist_ok=True)
    s = open(os.path.join(WEB, 'app.js'), encoding='utf8').read()
    # items
    ce = js_obj(obj_after(s, 'Ce={width:256'))
    sheet = Image.open(os.path.join(WEB, 'assets', 'items.png')).convert('RGBA')
    n = 0
    for k, v in ce['map'].items():
        x, y, w, h = v[:4]
        sheet.crop((x, y, x + w, y + h)).save(os.path.join(OUT, 'item_%s.png' % k.replace(':', '_')))
        n += 1
    print('items', n)
    # ui icons
    m = re.search(r'\bnt=\[((?:"[a-z_0-9]+",?)+)\]', s)
    names = re.findall(r'"([a-z_0-9]+)"', m.group(1))
    strip = Image.open(os.path.join(WEB, 'assets', 'gui', 'icons.png')).convert('RGBA')
    w = strip.width // len(names)
    for i, nm in enumerate(names):
        strip.crop((i * w, 0, (i + 1) * w, strip.height)).save(os.path.join(OUT, 'ico_%s.png' % nm))
    print('icons', len(names), 'cell', w, strip.height)
    # partner sprites
    pe = js_obj(obj_after(s, 'pe={'))
    print('pe keys', list(pe.keys())[:6])
    mp = pe.get('map') or {}
    sp = Image.open(os.path.join(WEB, 'assets', 'partners.webp')).convert('RGBA')
    c = 0
    for k, v in mp.items():
        x, y, w2, h2 = v[:4]
        sp.crop((x, y, x + w2, y + h2)).save(os.path.join(OUT, 'sp_%s.png' % k))
        c += 1
    print('sprites', c)
    # pixel glyph grids (bn) with the page's palette (Ms) and outline rule (Tl): glyph_<name>.png, one pixel per cell
    ms = js_obj(obj_after(s, 'Ms={'))
    bn = js_obj(obj_after(s, 'bn={'))
    def col(h):
        return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)
    for nm, rows in bn.items():
        w0 = max(len(r) for r in rows)
        g = [r.ljust(w0, '.') for r in rows]
        W, H = w0 + 2, len(g) + 2
        im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        def at(x, y):
            return g[y][x] if 0 <= y < len(g) and 0 <= x < w0 else '.'
        for y in range(-1, len(g) + 1):
            for x in range(-1, w0 + 1):
                c = at(x, y)
                if c != '.':
                    if c in ms:
                        im.putpixel((x + 1, y + 1), col(ms[c]))
                    continue
                if any(at(a, b) not in ('.', 'K') for a, b in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))):
                    im.putpixel((x + 1, y + 1), col(ms['K']))
        im.save(os.path.join(OUT, 'glyph_%s.png' % nm))
    print('glyphs', len(bn))
    json.dump({'items': sorted(k.replace(':', '_') for k in ce['map']), 'icons': names, 'sprites': sorted(mp)}, open(os.path.join(OUT, 'icons.json'), 'w'))
    import shutil
    shutil.copy(os.path.join(WEB, 'assets', 'gui', 'raid_points.png'), os.path.join(OUT, 'raid_points.png'))
    res = r'G:\Claude\SnobbleGUIOverhaul\Two-Mod Rework\snobblemon-server\src\main\resources\data\snobblemon_server'
    json.dump({'items': sorted(k.replace(':', '_') for k in ce['map']), 'icons': names, 'sprites': sorted(mp)}, open(os.path.join(res, 'tower_icons.json'), 'w'))



main()
