"""Exports item textures for the web shop.

    python export_icons.py <out icons folder> <jar> [<jar> ...] -- <item id> [<item id> ...]
    python export_icons.py <out icons folder> <jar> [...] --shop <shop.json>        (every product icon / item reward in a shop file)

Writes <out>/<namespace>/<path>.png - exactly where the server looks (config/snobblegemcurrency/icons/). Each item id is resolved through its
model (assets/<ns>/models/item/<path>.json -> layer0 / parent chain) and falls back to textures/item/<path>.png and textures/block/<path>.png.
Textures come from the jars you pass (the Minecraft client jar for vanilla items, a mod jar for mod items); nothing is downloaded.
Those textures belong to Mojang / the mod authors: this folder is for your own server's page, not for redistribution.
"""
import json
import os
import sys
import zipfile

args = sys.argv[1:]
out = args[0]
rest = args[1:]
jars, ids, shop = [], [], None
mode = 'jars'
for a in rest:
    if a == '--':
        mode = 'ids'
    elif a == '--shop':
        mode = 'shop'
    elif mode == 'jars':
        jars.append(a)
    elif mode == 'ids':
        ids.append(a)
    elif mode == 'shop':
        shop = a

zips = [zipfile.ZipFile(j) for j in jars]


def read(name):
    for z in zips:
        try:
            return z.read(name)
        except KeyError:
            pass
    return None


def split(i):
    return i.split(':', 1) if ':' in i else ('minecraft', i)


def tex_from_model(ns, path, depth=0):
    if depth > 4:
        return None
    raw = read('assets/%s/models/item/%s.json' % (ns, path))
    if raw is None:
        return None
    m = json.loads(raw)
    t = m.get('textures', {})
    for k in ('layer0', 'all', 'particle'):
        if k in t and not t[k].startswith('#'):
            return t[k]
    parent = m.get('parent')
    if parent and not parent.startswith('builtin') and parent not in ('minecraft:item/generated', 'item/generated', 'minecraft:item/handheld', 'item/handheld'):
        pns, ppath = split(parent)
        ppath = ppath[len('item/'):] if ppath.startswith('item/') else ppath
        return tex_from_model(pns, ppath, depth + 1)
    return None


def find_png(item_id):
    ns, path = split(item_id)
    cands = []
    t = tex_from_model(ns, path)
    if t:
        tns, tpath = split(t)
        cands.append('assets/%s/textures/%s.png' % (tns, tpath))
    cands += ['assets/%s/textures/item/%s.png' % (ns, path), 'assets/%s/textures/block/%s.png' % (ns, path)]
    for c in cands:
        b = read(c)
        if b:
            return b, c
    return None, None


if shop:
    data = json.load(open(shop, encoding='utf-8'))
    for p in data.get('products', []):
        for i in (p.get('icon'), (p.get('reward') or {}).get('item')):
            if i and not i.startswith('snobblegems:') and i not in ids:
                ids.append(i)

done, missing = 0, []
for i in ids:
    b, src = find_png(i)
    ns, path = split(i)
    if not b:
        missing.append(i)
        continue
    dst = os.path.join(out, ns, *path.split('/')) + '.png'
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    open(dst, 'wb').write(b)
    done += 1
    print('  %-34s <- %s' % (i, src))
print('exported %d, missing %d %s' % (done, len(missing), missing))
