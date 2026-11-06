"""Snobble Hub (hub.json): tab strip + Inventory / Cosmetics / Warps pages, 1280x720. Colours, tabs and entries come from the vm (HubViews.java, config/snobblemon/hub/*.json)."""
import json, os
OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
M, MB = 'mono', 'mono_b'
TXT, DIM, LINE = '#e6edf3', '#8a99a8', '#2d3b4b'


def T(text, size=14, color=TXT, bold=False, **kw):
    d = {'type': 'label', 'text': text, 'font': MB if bold else M, 'size': size, 'color': color, 'shadow': False}
    d.update(kw)
    return d


def box(children=None, **kw):
    d = {'type': 'column', 'children': children or []}
    d.update(kw)
    return d


def vmsg(action, **extra):
    d = {'channel': 'snobble_view', 'action': action}
    d.update(extra)
    return d


def pager(txt, d, x, ident, action):
    return {'type': 'button', 'text': txt, 'font': MB, 'size': 14, 'color': TXT, 'shadow': False, 'text_align': 'center', 'x': x, 'y': 548, 'w': 110, 'h': 36,
            'bg': '#1d2a38', 'hover': '#26384b', 'border': LINE, 'bw': 2, 'id': ident, 'msg': vmsg(action, d=d)}


def page():
    close = {'type': 'button', 'text': 'CLOSE', 'font': MB, 'size': 14, 'color': '#ffffff', 'shadow': False, 'text_align': 'center', 'right': 24, 'y': 20, 'w': 96, 'h': 36,
             'bg': '#3a1f26', 'hover': '#55272f', 'border': '#ff7b7b', 'bw': 2, 'id': 'close', 'msg': vmsg('close')}
    header = [T('{vm.title}', 28, '{vm.accent}', True, x=28, y=14, lh=34), T('{vm.sub}', 13, DIM, x=30, y=52, lh=16), close]
    tab = {'type': 'button', 'w': 230, 'h': 54, 'bg': '{t.bg}', 'hover': '{t.hv}', 'border': '{t.bd}', 'bw': 2, 'id': 'tab', 'args': {'k': 'str:24'}, 'argv': {'k': 't.id'},
           'msg': vmsg('tab', k='{k}'), 'repeat': {'for': 'vm.tabs', 'as': 't', 'limit': 8}, 'children': [
               {'type': 'item', 'item': '{t.icon}', 'x': 14, 'y': 11, 'w': 32, 'h': 32}, T('{t.label}', 16, '{t.col}', True, x=58, y=16, w=164, lh=20, ellipsis=True)]}
    nav = box([tab], x=20, y=92, w=230, h=600, gap=8)
    # ---------------------------------------------------------------- inventory
    slot = {'type': 'button', 'x': 's.x', 'y': 's.y', 'w': 60, 'h': 60, 'bg': '#0c131b', 'hover': '#16212d', 'border': '{s.bd}', 'bw': 2, 'id': 'slot', 'msg': vmsg('noop'),
            'repeat': {'for': 'vm.slots', 'as': 's', 'limit': 41}, 'tooltip': ['{s.name}', '{s.l1}', '{s.l2}'], 'children': [
                {'type': 'item', 'item': '{s.item}', 'x': 6, 'y': 6, 'w': 48, 'h': 48, 'if': 's.has'},
                T('{s.cnt}', 13, '#ffffff', True, right=4, y=42, lh=15, **{'if': 's.many'})]}
    kv = {'type': 'row', 'children': [T('{p.k}', 14, DIM, w=110, lh=18), T('{p.v}', 14, TXT, True, lh=18)], 'w': 'fill', 'h': 26, 'repeat': {'for': 'vm.stats', 'as': 'p', 'limit': 8}}
    inv = box([T('EQUIPMENT', 13, '{vm.accent}', True, x=24, y=14, lh=16),
               T('{vm.eqNames}', 11, DIM, x=24, y=110, lh=13, w=420),
               T('BACKPACK', 13, '{vm.accent}', True, x=24, y=150, lh=16),
               T('HOTBAR', 13, '{vm.accent}', True, x=24, y=382, lh=16),
               box([slot], x=24, y=0, w=640, h=480),
               box([T('INFO', 13, '{vm.accent}', True, lh=16, pad=[0, 0, 6, 0]), kv], x=690, y=14, w=280, gap=4)],
              x=262, y=92, w=998, h=610, bg='{vm.panel}', border=LINE, bw=2, **{'if': 'vm.isInv'})
    # ---------------------------------------------------------------- warps
    wc = {'type': 'button', 'x': 'w.x', 'y': 'w.y', 'w': 300, 'h': 118, 'bg': '{w.bg}', 'hover': '{w.hv}', 'border': '{w.bd}', 'bw': 2, 'id': 'warp', 'args': {'k': 'str:48'},
          'argv': {'k': 'w.id'}, 'msg': vmsg('warp', k='{k}'), 'repeat': {'for': 'vm.warps', 'as': 'w', 'limit': 9}, 'tooltip': ['{w.label}', '{w.desc}'], 'children': [
              {'type': 'item', 'item': '{w.icon}', 'x': 16, 'y': 16, 'w': 40, 'h': 40},
              T('{w.label}', 18, '{w.col}', True, x=70, y=14, w=216, lh=22, ellipsis=True),
              T('{w.desc}', 12, DIM, x=70, y=40, w=216, lh=15),
              T('{w.status}', 12, '{w.sc}', True, x=70, y=88, w=216, lh=14)]}
    warps = box([T('WARPS', 13, '{vm.accent}', True, x=24, y=14, lh=16), T('{vm.warpHint}', 12, DIM, x=24, y=34, lh=15),
                 box([wc], x=24, y=64, w=960, h=480),
                 pager('< PREV', -1, 24, 'wp_prev', 'wpage'), pager('NEXT >', 1, 146, 'wp_next', 'wpage'), T('{vm.warpPage}', 13, DIM, x=272, y=558, lh=16)],
                x=262, y=92, w=998, h=610, bg='{vm.panel}', border=LINE, bw=2, **{'if': 'vm.isWarps'})
    # ---------------------------------------------------------------- cosmetics
    cc = {'type': 'button', 'x': 'c.x', 'y': 'c.y', 'w': 176, 'h': 196, 'bg': '{c.bg}', 'hover': '{c.hv}', 'border': '{c.bd}', 'bw': 2, 'id': 'cos', 'args': {'k': 'str:48'},
          'argv': {'k': 'c.id'}, 'msg': vmsg('cos', k='{k}'), 'repeat': {'for': 'vm.cosCards', 'as': 'c', 'limit': 10}, 'tooltip': ['{c.name}', '{c.sub}'], 'children': [
              {'type': 'image', 'pokemon': '{c.thumb}', 'x': 28, 'y': 8, 'w': 120, 'h': 120},
              T('{c.name}', 14, '{c.nc}', True, x=8, y=130, w=160, lh=17, ellipsis=True, text_align='center'),
              T('{c.state}', 11, '{c.sc}', True, x=8, y=152, w=160, lh=13, text_align='center'),
              T('{c.go}', 12, '#ffffff', True, x=8, y=170, w=160, lh=14, text_align='center')]}
    chip = {'type': 'button', 'w': 110, 'h': 30, 'bg': '{k.bg}', 'hover': '#26384b', 'border': '{k.bd}', 'bw': 2, 'id': 'ccat', 'args': {'k': 'str:24'}, 'argv': {'k': 'k.id'},
            'text': '{k.label}', 'font': MB, 'size': 12, 'color': TXT, 'shadow': False, 'text_align': 'center', 'msg': vmsg('ccat', k='{k}'),
            'repeat': {'for': 'vm.cosCats', 'as': 'k', 'limit': 8}}
    full = {'type': 'button', 'text': 'OPEN FULL WARDROBE', 'font': MB, 'size': 13, 'color': '#ffffff', 'shadow': False, 'text_align': 'center', 'right': 24, 'y': 14, 'w': 210, 'h': 34,
            'bg': '#1f6b3a', 'hover': '#27824a', 'border': '#6ee08a', 'bw': 2, 'id': 'wardrobe', 'msg': vmsg('wardrobe')}
    cos = box([T('COSMETICS', 13, '{vm.accent}', True, x=24, y=14, lh=16), T('{vm.cosWorn}', 12, DIM, x=24, y=34, lh=15, w=620), full,
               box([chip], x=24, y=62, w=960, h=34, type='row', gap=6),
               box([cc], x=24, y=108, w=960, h=420),
               T('{vm.cosMsg}', 16, DIM, x=24, y=200, w=940, lh=20, **{'if': 'vm.cosMsg'}),
               pager('< PREV', -1, 24, 'cp_prev', 'cpage'), pager('NEXT >', 1, 146, 'cp_next', 'cpage'), T('{vm.cosPage}', 13, DIM, x=272, y=558, lh=16)],
              x=262, y=92, w=998, h=610, bg='{vm.panel}', border=LINE, bw=2, **{'if': 'vm.isCos'})
    refresh = {'type': 'spacer', 'id': 'refresh', 'w': 0, 'h': 0, 'every_ms': 4000, 'args': {}, 'msg': vmsg('refresh')}
    root = box([box([], x=0, y=0, w=1280, h=720, bg='{vm.bg}', border='{vm.accent}', bw=3)] + header + [nav, inv, warps, cos, refresh], w=1280, h=720)
    return {'v': 2, 'id': 'hub', 'channel': 'snobblecosmetics', 'hello': 'ready', 'bye': True, 'title': 'Hub', 'width': 1280, 'height': 720, 'fit': [1280, 720],
            'toast': {'bg': '#101820', 'border': '#6ee08a', 'border_bad': '#ff7b7b', 'color': '#6ee08a', 'color_bad': '#ff7b7b', 'bw': 2, 'radius': 0, 'pad_x': 22, 'pad_y': 12, 'y': 40,
                      'from': -22, 'font': M, 'size': 16},
            'root': root}


def strip(o):
    if isinstance(o, dict):
        return {k: strip(v) for k, v in o.items() if v is not None}
    if isinstance(o, list):
        return [strip(v) for v in o]
    return o


d = strip(page())
with open(os.path.join(OUT, 'hub.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, separators=(',', ':'))
print('wrote hub (%d bytes)' % os.path.getsize(os.path.join(OUT, 'hub.json')))
