"""Native Sneelz' Cosmetics Admin Hub (admin.json): the layout of the cosmetics mod's web/admin/index.html (1600x900 stage, dark panel + green accent),
driven by AdminViews.java. Every change is still made and permission-checked by the cosmetics mod (channel snobblecosmeticsadmin)."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
M, MB = 'mono', 'mono_b'
BG, PANEL, PANEL2, LINE, ACC, TXT, DIM, BAD, WARN, GOLD = '#101418', '#171c22', '#1e252d', '#2d3844', '#6ee08a', '#e6edf3', '#8a99a8', '#ff7b7b', '#ffd166', '#ffd166'
INPUT = '#0d1115'
_N = [0]


def T(text, size=15, color=TXT, bold=False, **kw):
    d = {'type': 'label', 'text': text, 'font': MB if bold else M, 'size': size, 'color': color, 'shadow': False}
    d.update(kw)
    return d


def box(children=None, **kw):
    d = {'type': 'column', 'children': children or []}
    d.update(kw)
    return d


def row(children, **kw):
    d = {'type': 'row', 'children': children}
    d.update(kw)
    return d


def vmsg(action, **extra):
    d = {'channel': 'snobble_view', 'action': action}
    d.update(extra)
    return d


def btn(text, action, bg='#26313c', bd='#3b4a59', hover='#2f3c49', w=None, h=36, op=None, k=None, ident=None, **kw):
    d = {'type': 'button', 'text': text, 'font': MB, 'size': 14, 'color': '#ffffff', 'shadow': False, 'text_align': 'center', 'h': h, 'bg': bg, 'hover': hover,
         'border': bd, 'bw': 2, 'pad': [0, 14, 0, 14], 'id': ident or ('b%d_%s' % (_N.__setitem__(0, _N[0] + 1) or _N[0], action)), 'msg': vmsg(action, **({'k': k} if k else {}))}
    if w:
        d['w'] = w
    if op:
        d['opacity'] = op
    d.update(kw)
    return d


def GO(text, action, **kw): return btn(text, action, bg='#1f6b3a', bd=ACC, hover='#27824a', **kw)
def NO(text, action, **kw): return btn(text, action, bg='#6b2328', bd=BAD, hover='#85292f', **kw)
def WN(text, action, **kw): return btn(text, action, bg='#6b5520', bd=WARN, hover='#7d6628', **kw)


def inp(ident, bind, key, hint, w='fill', h=34, maxlen=40, **kw):
    d = {'type': 'input', 'id': ident, 'bind': bind, 'hint': hint, 'w': w, 'h': h, 'max_len': maxlen, 'bg': INPUT, 'border': LINE, 'bw': 2, 'color': TXT, 'font': M, 'size': 15,
         'hint_color': DIM, 'pad': [0, 8, 0, 8], 'debounce_ms': 350, 'args': {'v': 'str:500'}, 'argv': {'v': '$' + bind}, 'msg': vmsg('in', k=key, v='{v}')}
    d.update(kw)
    return d


def lab(text):
    return T(text, 12, DIM, ls=1, lh=15, pad=[0, 0, 3, 0], w='fill')


def toggle(label, on, key, lock=None):
    sw = box([box([], x=3, y=3, w=14, h=14, bg='#ffffff', **{'if': '!' + on}), box([], x=25, y=3, w=14, h=14, bg='#ffffff', **{'if': on})], w=42, h=20, bg='#37424e', radius=10)
    swon = box([], x=0, y=0, w=42, h=20, bg=ACC, radius=10, **{'if': on})
    sw['children'].insert(0, swon)
    d = {'type': 'button', 'w': 'fill', 'h': 36, 'bg': INPUT, 'hover': '#141a20', 'border': LINE, 'bw': 2, 'id': 'tog_' + key, 'msg': vmsg('tog', k=key),
         'children': [row([T(label, 14, TXT, w='fill', lh=18), sw], w='fill', h='fill', pad=[0, 10, 0, 10], cross='center')]}
    if lock:
        d['opacity'] = 0.5
    return d


def cell():
    thumb = box([{'type': 'image', 'pokemon': '{c.thumb}', 'x': 0, 'y': 0, 'w': 56, 'h': 56, 'if': 'c.thumb'}], x=8, y=8, w=60, h=60, bg=INPUT, border=LINE, bw=2)
    badge = T('{c.state}', 11, '#000000', True, right=8, y=8, lh=13, pad=[1, 8, 1, 8], bg='{c.sc}', h=17)
    return {'type': 'button', 'w': 'fill', 'w_expr': 'vm.cellW', 'h': 76, 'bg': '{c.bg}', 'hover': '#223329', 'border': '{c.bd}', 'bw': 2, 'id': 'item', 'args': {'k': 'str:48'}, 'argv': {'k': 'c.id'},
            'msg': vmsg('sel', k='{k}'), 'repeat': {'for': 'r.cells', 'as': 'c', 'limit': 2}, 'children': [
                thumb, T('{c.name}', 16, '{c.nameColor}', True, x=78, y=10, w=190, lh=19, ellipsis=True),
                T('{c.sub}', 12, DIM, x=78, y=34, w=190, lh=14, ellipsis=True),
                badge, T('{c.price}', 14, GOLD, True, right=8, y=44, lh=17, text_align='right')]}


def card(title, rows_src, limit, extra=None, **kw):
    kvrow = row([T('{p.k}', 14, TXT, w='fill', lh=18), T('{p.v}', 14, DIM, lh=18)], w='fill', h=30, pad=[0, 8, 0, 8], bg=INPUT, border=LINE, bw=2, cross='center',
                repeat={'for': rows_src, 'as': 'p', 'limit': limit})
    return box([T(title, 17, ACC, True, lh=22, pad=[0, 0, 8, 0]), kvrow] + (extra or []), w='fill', gap=5, pad=[12, 12, 12, 12], bg=PANEL2, border=LINE, bw=2, **kw)


def listbox():
    tools = row([inp('search', 'q', 'q', 'Search by name or id...', h=36, msg=vmsg('search', v='{v}')), btn('Reload definitions', 'reload', h=36, w=170, op='vm.reloadOp'), btn('Refresh', 'refresh', h=36, w=100)],
                x=10, y=10, w=1288, w_expr='vm.toolW', h=36, gap=8, **{'if': 'vm.isList'})
    rows = box([row([cell()], gap=8, h=76, repeat={'for': 'vm.rows', 'as': 'r', 'limit': 6})], x=10, y=56, gap=8, **{'if': 'vm.isList'})
    empty = T('Nothing here.', 15, DIM, x=10, y=90, w=400, lh=18, **{'if': 'vm.empty'})
    prev = btn('< PREV', 'page', h=34, w=100, op='vm.prevOp', ident='prev', key='left', msg=vmsg('page', d='-1'))
    nxt = btn('NEXT >', 'page', h=34, w=100, op='vm.nextOp', ident='next', key='right', msg=vmsg('page', d='1'))
    pager = row([prev, T('{vm.pageLabel}', 14, DIM, w=90, lh=34, text_align='center'), nxt], x=10, y=724, gap=8, h=34, **{'if': 'vm.pager'})
    note = [T('{vm.packNote}', 12, DIM, lh=15, w='fill', wrap=True, pad=[6, 0, 0, 0])]
    await_card = box([T('Needs a pack rebuild (no restart)', 17, ACC, True, lh=22, pad=[0, 0, 8, 0]), T('{vm.awaitIds}', 14, TXT, lh=18, w='fill', wrap=True),
                      T('These were added after the pack was built. Press the button below: the pack is rebuilt in a few seconds, then you can preview and publish them. Players download the new pack when they reconnect.',
                        12, DIM, lh=15, w='fill', wrap=True, pad=[6, 0, 0, 0])], w='fill', gap=5, pad=[12, 12, 12, 12], bg=PANEL2, border=LINE, bw=2, **{'if': 'vm.hasAwait'})
    pack = box([card('Resource pack', 'vm.packKv', 6, note), await_card,
                row([btn('Reload definitions', 'reload', h=36, w=180, op='vm.reloadOp'), GO('Rebuild resource pack now', 'ask', h=36, w=260, op='vm.packOp', k='rebuild')], gap=8, h=36)],
               x=10, y=10, w=1288, w_expr='vm.toolW', gap=10, **{'if': 'vm.isPack'})
    settings = box([card('Settings (read only)', 'vm.setKv', 8, [T('Edit these in config/snobblemon_cosmetics/config.json and restart. Prices, names and release state are edited on each cosmetic and kept in overrides.json.',
                                                                      12, DIM, lh=15, w='fill', wrap=True, pad=[6, 0, 0, 0])]), card('Your permissions', 'vm.permKv', 6)],
                   x=10, y=10, w=1288, w_expr='vm.toolW', gap=10, **{'if': 'vm.isSettings'})
    owners = box([box([T('Ownership tools', 17, ACC, True, lh=22, pad=[0, 0, 8, 0]),
                       row([inp('player', 'pl', 'pl', 'Player name', w=240, maxlen=32, msg=vmsg('pl', v='{v}')), inp('gid', 'gid', 'gid', 'Cosmetic id (e.g. pikachu_ears)', maxlen=48, msg=vmsg('gid', v='{v}'))], w='fill', gap=8, h=34),
                       row([btn('Show what they own', 'owned', h=36, op='vm.grantOp'), GO('Grant', 'ask', h=36, op='vm.grantOp', k='grant'), NO('Revoke', 'ask', h=36, op='vm.grantOp', k='revoke')], gap=8, h=36, pad=[8, 0, 0, 0]),
                       T('{vm.ownedText}', 12, DIM, lh=15, w='fill', wrap=True, pad=[8, 0, 0, 0]),
                       T('You need snobblemon.cosmetics.admin.grant.', 12, DIM, lh=15, w='fill', **{'if': 'vm.noGrant'})],
                      w='fill', gap=6, pad=[12, 12, 12, 12], bg=PANEL2, border=LINE, bw=2)], x=10, y=10, w=1288, w_expr='vm.toolW', **{'if': 'vm.isOwners'})
    return box([tools, rows, empty, pager, pack, settings, owners], x=268, y=96, w=1308, w_expr='vm.listW', h=780, bg=PANEL, border=LINE, bw=2, clip=True)


def editor():
    d = lambda k: '{vm.d.%s}' % k
    chk = row([T('{k.lv}', 13, '{k.color}', True, w=70, lh=17), T('{k.text}', 13, TXT, w='fill', lh=17, ellipsis=True)], w='fill', h=18, repeat={'for': 'vm.d.checks', 'as': 'k', 'limit': 8})
    chip = lambda label, key, w: {'type': 'button', 'text': label, 'font': M, 'size': 13, 'color': TXT, 'shadow': False, 'text_align': 'center', 'w': w, 'h': 26, 'bg': d('bg_' + key), 'hover': '#1d262e',
                                  'border': d('bd_' + key), 'bw': 2, 'id': 'chip_' + key, 'msg': vmsg('mode', k=key)}
    modes = row([chip('Keep', 'keep', 70), chip('Plain', 'none', 70), chip('Solid', 'solid', 70), chip('Gradient', 'gradient', 94),
                 {'type': 'button', 'text': 'Bold', 'font': M, 'size': 13, 'color': TXT, 'shadow': False, 'text_align': 'center', 'w': 60, 'h': 26, 'bg': d('boldBg'), 'hover': '#1d262e', 'border': d('boldBd'), 'bw': 2,
                  'id': 'bold', 'msg': vmsg('bold')}], gap=6, h=26)
    cols = row([inp('c1', 'c1', 'c1', '#rrggbb', w=110, h=28, maxlen=7, **{'if': 'vm.d.showC1'}), box([], w=8, h=28, **{'if': 'vm.d.showC1'}),
                inp('c2', 'c2', 'c2', '#rrggbb', w=110, h=28, maxlen=7, **{'if': 'vm.d.showC2'})], gap=0, h=28, pad=[4, 0, 0, 0])
    sec = row([{'type': 'button', 'text': '{s.label}', 'font': M, 'size': 13, 'color': TXT, 'shadow': False, 'text_align': 'center', 'h': 26, 'pad': [0, 8, 0, 8], 'bg': '{s.bg}', 'hover': '#1d262e', 'border': '{s.bd}', 'bw': 2,
                'id': 'sec', 'args': {'k': 'str:24'}, 'argv': {'k': 's.k'}, 'msg': vmsg('section', k='{k}'), 'repeat': {'for': 'vm.d.secs', 'as': 's', 'limit': 8}}], gap=5, h=26)
    body = box([
        row([T('{vm.d.name}', 22, '{vm.d.nameColor}', True, lh=26, w='fill', ellipsis=True)], w='fill', h=28),
        row([T('{vm.d.state}', 11, '#000000', True, lh=13, pad=[1, 8, 1, 8], bg='{vm.d.sc}', h=17), T('{vm.d.meta}', 12, DIM, lh=17)], w='fill', h=18, cross='center'),
        box([{'type': 'image', 'pokemon': '{vm.d.thumb}', 'x': 255, 'y': 8, 'w': 120, 'h': 120},
             T('Preview picture - what players will see in the wardrobe and shop', 11, DIM, x=8, bottom=5, lh=13)], w='fill', h=136, bg='#171d24', border=LINE, bw=2),
        box([lab('DISPLAY NAME'), inp('nm', 'nm', 'nm', 'Display name', maxlen=40), box([lab('NAME STYLE'), modes, cols], w='fill', gap=4, pad=[6, 8, 8, 8], bg=INPUT, border=LINE, bw=2)], w='fill', gap=0),
        box([lab('SHOP SECTION'), sec], w='fill'),
        row([box([lab('PRICE ({vm.d.currency})'), inp('pr', 'pr', 'pr', 'price', maxlen=8)], w=200), box([], w=10), box([lab('DESCRIPTION (use | between lines)'), inp('ds', 'ds', 'ds', 'Description', maxlen=300)], w='fill')], w='fill', h=58),
        toggle('Purchasable (sold when published)', 'vm.d.purchOn', 'purchasable', 'vm.d.purchLock'),
        toggle('Hide it in the world while disabled', 'vm.d.suppOn', 'suppress'),
        box([T('VALIDATION AND RESOURCE PACK', 12, DIM, ls=1, lh=15), T('Loading...', 12, DIM, lh=15, **{'if': 'vm.d.loading'}), chk], w='fill', gap=2, pad=[8, 8, 8, 8], bg=INPUT, border=LINE, bw=2),
        T('{vm.d.meta2}', 12, DIM, lh=15, w='fill'),
        row([btn('Preview on me', 'preview', h=34), btn('Validate', 'validate', h=34), GO('Save Changes', 'save', h=34, op='vm.d.saveOp'),
             GO('{vm.d.pubText}', 'ask', h=34, op='vm.d.pubOp', k='publish')], w='fill', gap=6, h=34),
        row([WN('Hide Cosmetic', 'ask', h=34, op='vm.d.hideOp', k='hide'), NO('Disable Cosmetic', 'ask', h=34, op='vm.d.pubRightsOp', k='disable', **{'if': 'vm.d.equipOn'}),
             GO('Re-enable Cosmetic', 'enable', h=34, op='vm.d.pubRightsOp', **{'if': '!vm.d.equipOn'})], w='fill', gap=6, h=34),
        T('You can look but not edit: you need snobblemon.cosmetics.admin.edit.', 12, DIM, lh=15, w='fill', **{'if': 'vm.d.noEdit'})], w='fill', gap=6)
    return box([{'type': 'scroll', 'id': 'edscroll', 'x': 0, 'y': 0, 'w': 'fill', 'h': 'fill', 'pad': [12, 12, 12, 12], 'children': [body]}],
               x='vm.edX', y=96, w=660, h=780, bg=PANEL, border=LINE, bw=2, clip=True, **{'if': 'vm.ed'})


def dialog():
    yes = btn('{vm.dlgLabel}', 'yes', h=36, ident='yes')
    yes['bg'] = '{vm.dlgBg}'
    yes['border'] = '{vm.dlgBd}'
    return box([
        {'type': 'button', 'x': 0, 'y': 0, 'w': 1600, 'h': 900, 'id': 'dlgshut', 'msg': vmsg('no')},
        box([T('{vm.dlgTitle}', 20, '#ffffff', True, lh=24, w='fill'), box([], h=8), T('{vm.dlgText}', 15, '#cfd8e0', lh=20, w='fill', wrap=True), box([], h=14),
             row([box([], w='fill'), btn('Cancel', 'no', h=36), box([], w=8), yes], w='fill', h=36)],
            x=540, y=330, w=520, pad=[18, 18, 18, 18], bg=PANEL, border=ACC, bw=3)],
        x=0, y=0, w=1600, h=900, z=200, bg='#00000099', **{'if': 'vm.dlg'})


def page():
    header = [T("Sneelz' Cosmetics - Admin Hub", 30, ACC, True, x=24, y=14, lh=34, tshadow='3,3,#000000'), T('{vm.sub}', 13, DIM, x=24, y=52, lh=16)]
    stats = row([box([T('{s.n}', 26, '{s.color}', True, lh=30, w='fill', text_align='center'), T('{s.label}', 12, DIM, lh=15, w='fill', text_align='center')], w=92, h=58, bg=PANEL, border=LINE, bw=2, pad=[6, 4, 4, 4],
                     repeat={'for': 'vm.stats', 'as': 's', 'limit': 7})], x=884, y=14, gap=8, h=58)
    nav = box([box([T('{n.label}', 12, DIM, ls=2, lh=15, pad=[10, 6, 6, 6], **{'if': 'n.hdr'}),
                    {'type': 'button', 'w': 'fill', 'h': 38, 'bg': '{n.bg}', 'hover': '#1e252d', 'border': '{n.bd}', 'bw': 2, 'id': 'nav', 'args': {'k': 'str:24'}, 'argv': {'k': 'n.k'}, 'msg': vmsg('nav', k='{k}'),
                     'if': '!n.hdr', 'children': [row([T('{n.label}', 15, TXT, w='fill', lh=18), T('{n.count}', 12, DIM, lh=15)], w='fill', h='fill', pad=[0, 10, 0, 10], cross='center')]}],
                   w='fill', repeat={'for': 'vm.nav', 'as': 'n', 'limit': 20})], x=24, y=96, w=230, h=780, gap=4, pad=[10, 10, 10, 10], bg=PANEL, border=LINE, bw=2, clip=True)
    root = {'type': 'column', 'w': 1600, 'h': 900, 'children': [
        box([], x=0, y=0, w=1600, h=900, bg=BG), box([], x=0, y=0, w=1600, h=900, border='#000000', bw=4),
        box([], x=4, y=4, w=1592, h=892, border=ACC, bw=3)] + header + [stats, nav, listbox(), editor(), dialog(),
        {'type': 'button', 'x': 0, 'y': 0, 'w': 0, 'h': 0, 'id': 'esc', 'key': 'esc', 'msg': vmsg('close')},
        {'type': 'spacer', 'id': 'refresh', 'w': 0, 'h': 0, 'every_ms': 30000, 'args': {}, 'msg': {'channel': 'snobblecosmeticsadmin', 'action': 'refresh'}}]}
    return {'v': 2, 'id': 'admin', 'channel': 'snobblecosmeticsadmin', 'hello': 'ready', 'bye': True, 'title': "Sneelz' Cosmetics - Admin Hub", 'width': 1600, 'height': 900, 'fit': [1600, 900],
            'toast': {'bg': '#16351f', 'border': ACC, 'border_bad': BAD, 'color': '#d7ffe2', 'color_bad': '#ffdede', 'bw': 2, 'radius': 4, 'pad_x': 18, 'pad_y': 10, 'y': 820, 'from': 20, 'font': MB, 'size': 15},
            'root': root}


def strip(o):
    if isinstance(o, dict):
        return {k: strip(v) for k, v in o.items() if v is not None}
    if isinstance(o, list):
        return [strip(v) for v in o]
    return o


d = strip(page())
with open(os.path.join(OUT, 'admin.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, separators=(',', ':'))
print('wrote admin (%d bytes)' % os.path.getsize(os.path.join(OUT, 'admin.json')))
