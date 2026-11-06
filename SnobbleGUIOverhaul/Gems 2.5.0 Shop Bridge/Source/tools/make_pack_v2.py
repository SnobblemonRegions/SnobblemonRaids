"""Version 2 resource pack + web art, built from the user's gem reference.

    python make_pack_v2.py <gem_icon.png> <out pack folder> <out web assets folder>

Pack (namespace snobblegemsv2, nothing of Version 1 is reused):
  * item models: breeze_rod (and echo_shard for v1 tickets) custom_model_data 7420001 = Gem, 7420002 = Shiny Boost Ticket
  * font snobblegemsv2:icons  U+E300 = Gem glyph sized like the user's other sidebar glyphs (height 8, ascent 7)
Web assets (for the WebGUI page): gem_64/32/16.png, ticket_64.png
"""
import json
import os
import shutil
import sys
import zipfile

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gem_art as G

REF, PACK, WEB = sys.argv[1], sys.argv[2], sys.argv[3]
TICKET_ART = sys.argv[4] if len(sys.argv) > 4 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'art', 'ticket_user_64.png')
hx = G.hx
INK, WHITE = G.INK, G.WHITE


def ticket_base16():
    """Shiny Boost Ticket: lavender ticket with notches, perforation and the new gem as its emblem."""
    PAPER, PAPER_D, EDGE, EDGE_D = hx('#efe6ff'), hx('#b7a6e0'), hx('#8b5cf6'), hx('#4b25b8')
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(3, 13):
        for x in range(0, 16):
            im.putpixel((x, y), PAPER)
    for x in range(0, 16):
        im.putpixel((x, 3), EDGE)
        im.putpixel((x, 12), EDGE_D)
        im.putpixel((x, 11), PAPER_D)
    for y in (7, 8):
        im.putpixel((0, y), (0, 0, 0, 0))
        im.putpixel((15, y), (0, 0, 0, 0))
    for y in (4, 6, 8, 10):
        im.putpixel((3, y), PAPER_D)
    # outline
    src = im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] == 0:
                if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and src.getpixel((x + dx, y + dy))[3] > 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    im.putpixel((x, y), INK)
    # sparkle
    for x, y in ((14, 1), (13, 2), (15, 2), (14, 3)):
        if im.getpixel((x, y))[3] == 0:
            im.putpixel((x, y), hx('#ff7ad9'))
    im.putpixel((14, 2), WHITE)
    return im


def ticket32(master):
    """32x32 ticket: the 16px ticket body doubled, with the sampled gem as its emblem."""
    base = ticket_base16().resize((32, 32), Image.NEAREST)
    emblem = G.sample_small(master, 16, 1.2)
    base.alpha_composite(emblem, (9, 8))
    return base


def mini_gem(size=8):
    """A tiny 8x8 gem for the ticket emblem (hand-placed, same palette)."""
    rows = [
        "..oaabbo",
        ".oaabbbbo"[:8],
        "oppAccyo",
        "ommWWyyo",
        "ommWWyyo",
        "ouuWWbbo",
        ".ouWWbo.",
        "..oddo..",
    ]
    pal = {'o': G.INK, 'a': G.PINK_L, 'b': G.CY_L, 'p': G.PINK, 'A': G.PINK_L, 'c': G.CY, 'y': G.CY, 'm': G.MAG, 'W': G.WHITE, 'u': G.PURP, 'd': G.DEEP}
    im = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r[:8]):
            if ch != '.':
                im.putpixel((x, y), pal[ch])
    return im


def glyph_cell(master):
    """66x66 high-resolution cell like the user's own sidebar glyph textures; gem occupies x 3..62 (width 60) so its advance is about 8.5 px at height 8."""
    cell = Image.new('RGBA', (66, 66), (0, 0, 0, 0))
    g = master.resize((60, 60), Image.NEAREST)
    cell.alpha_composite(g, (3, 3))
    return cell


def skin_art():
    """Prismatic Poke Ball, 16x16 pixel art (shown x4 on the page) - the picture of the Pokemon Skins section."""
    import math
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    stops = [hx('#ff9af0'), hx('#d65aea'), hx('#8a44f5'), hx('#4c7cff'), hx('#3dddff'), hx('#b4f7ff')]
    def prism(t):
        t = max(0.0, min(1.0, t)) * (len(stops) - 1)
        i = min(int(t), len(stops) - 2)
        f = t - i
        a, b = stops[i], stops[i + 1]
        return tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3)) + (255,)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d > 7.4:
                continue
            if y <= 6:
                c = prism((x + (6 - y) * 0.6) / 15.0)
                if y == 1 or (y == 2 and x < 6):
                    c = tuple(min(255, v + 40) for v in c[:3]) + (255,)
            elif y >= 9:
                c = hx('#f2fbff') if (x + y) < 21 else hx('#b9cfdc')
            else:
                c = INK
            im.putpixel((x, y), c)
    src = im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and src.getpixel((x + dx, y + dy))[3] > 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                im.putpixel((x, y), INK)
    for y in range(16):
        for x in range(16):
            if math.hypot(x - cx, y - cy) <= 2.6:
                im.putpixel((x, y), INK)
            if math.hypot(x - cx, y - cy) <= 1.4:
                im.putpixel((x, y), WHITE)
    for x, y in ((13, 1), (12, 2), (14, 2), (13, 3)):
        im.putpixel((x, y), hx('#ff7ad9'))
    im.putpixel((13, 2), WHITE)
    return im.resize((64, 64), Image.NEAREST)


def ticket_model():
    """Item model of the Shiny Boost Ticket. The artwork is a full-width banner, so the model draws it smaller in the hands, on the ground and in
    item frames than a vanilla item (the vanilla generated-item sizes made it huge)."""
    def d(rot, tr, sc):
        return {'rotation': rot, 'translation': tr, 'scale': [sc, sc, sc]}
    return {
        'parent': 'minecraft:item/generated',
        'textures': {'layer0': 'snobblegemsv2:item/ticket'},
        'display': {
            'gui': d([0, 0, 0], [0, 0, 0], 0.88),
            'ground': d([0, 0, 0], [0, 1.2, 0], 0.22),
            'head': d([0, 180, 0], [0, 13, 7], 0.4),
            'fixed': d([0, 180, 0], [0, 0, 0], 0.45),
            'thirdperson_righthand': d([0, 0, 0], [0, 3, 1], 0.2),
            'thirdperson_lefthand': d([0, 0, 0], [0, 3, 1], 0.2),
            'firstperson_righthand': d([0, -90, 25], [1.13, 3.2, 1.13], 0.24),
            'firstperson_lefthand': d([0, 90, -25], [1.13, 3.2, 1.13], 0.24),
        },
    }


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)


def main():
    work = os.path.join(os.path.dirname(os.path.abspath(PACK)), '_gemwork')
    ref = G.load(REF)
    master = G.pixelate(ref, 64, 40)
    g32 = G.sample_small(master, 32, 1.15)
    g16 = G.sample_small(master, 16)
    t32 = ticket32(master)
    tix = Image.open(TICKET_ART).convert('RGBA')   # the user's own ticket picture, used as it is

    if os.path.isdir(PACK):
        shutil.rmtree(PACK)
    ns = os.path.join(PACK, 'assets', 'snobblegemsv2')
    mc = os.path.join(PACK, 'assets', 'minecraft')
    for d in (os.path.join(ns, 'textures', 'item'), os.path.join(ns, 'textures', 'font'), os.path.join(ns, 'models', 'item'), os.path.join(ns, 'font'), os.path.join(mc, 'models', 'item')):
        os.makedirs(d, exist_ok=True)
    write_json(os.path.join(PACK, 'pack.mcmeta'), {'pack': {'pack_format': 34, 'description': '§dSnobbleGemCurrency §7v2 - Gem art'}})

    g32.save(os.path.join(ns, 'textures', 'item', 'gem.png'))
    tix.save(os.path.join(ns, 'textures', 'item', 'ticket.png'))
    write_json(os.path.join(ns, 'models', 'item', 'gem.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'snobblegemsv2:item/gem'}})
    write_json(os.path.join(ns, 'models', 'item', 'ticket.json'), ticket_model())
    overrides = [
        {'predicate': {'custom_model_data': 7420001}, 'model': 'snobblegemsv2:item/gem'},
        {'predicate': {'custom_model_data': 7420002}, 'model': 'snobblegemsv2:item/ticket'},
    ]
    # tickets issued by 2.0.0 are breeze rods: keep dressing them
    write_json(os.path.join(mc, 'models', 'item', 'breeze_rod.json'), {
        'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'minecraft:item/breeze_rod'}, 'overrides': overrides})
    # new tickets / chest-menu icons use the trial key (no recipe uses it: no "New Recipes Unlocked" toast; breeze rods unlock the wind charge recipe)
    write_json(os.path.join(mc, 'models', 'item', 'trial_key.json'), {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': 'minecraft:item/trial_key'}, 'overrides': overrides})
    # tickets issued by version 1 are echo shards: keep dressing them
    write_json(os.path.join(mc, 'models', 'item', 'echo_shard.json'), {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': 'minecraft:item/echo_shard'}, 'overrides': overrides})

    glyph_cell(master).save(os.path.join(ns, 'textures', 'font', 'gem_glyph.png'))
    # the glyph is also offered 16px-tall for chat/placeholder use
    write_json(os.path.join(ns, 'font', 'icons.json'), {'providers': [
        {'type': 'bitmap', 'file': 'snobblegemsv2:font/gem_glyph.png', 'ascent': 7, 'height': 8, 'chars': ['']},
        {'type': 'bitmap', 'file': 'snobblegemsv2:font/gem_glyph.png', 'ascent': 8, 'height': 9, 'chars': ['']},
    ]})
    with open(os.path.join(PACK, 'README.txt'), 'w', encoding='utf-8') as f:
        f.write('SnobbleGemCurrency v2 resource pack (replaces the Version 1 pack: remove that zip from Polymer include_zips).\n'
                'Adds: minecraft:item/breeze_rod and echo_shard overrides (custom_model_data 7420001 gem, 7420002 Shiny Boost Ticket; echo_shard only for v1 tickets) and font snobblegemsv2:icons (U+E300 gem glyph, 8px).\n'
                'Everything else is in the namespace snobblegemsv2. The WebGUI shop does not need this pack (the page carries its own art).\n')

    os.makedirs(WEB, exist_ok=True)
    master.save(os.path.join(WEB, 'gem_64.png'))
    g32.save(os.path.join(WEB, 'gem_32.png'))
    g16.save(os.path.join(WEB, 'gem_16.png'))
    tix.save(os.path.join(WEB, 'ticket_64.png'))
    skin_art().save(os.path.join(WEB, 'skin_64.png'))

    zpath = PACK.rstrip('/\\') + '.zip'
    if os.path.exists(zpath):
        os.remove(zpath)
    with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(PACK):
            for fn in sorted(files):
                full = os.path.join(root, fn)
                z.write(full, os.path.relpath(full, PACK).replace('\\', '/'))
    print('wrote', zpath)


if __name__ == '__main__':
    main()
