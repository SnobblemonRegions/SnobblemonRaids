"""Native SnobbleCosmetics Wardrobe (wardrobe.json), a faithful copy of the original wardrobe/index.html + style.css.

The original stage is 1600x900, scaled to fit the window (min(w/1600, h/900)) - here "fit": [1600, 900] in the same units.
Positions and sizes are the original's measured CSS boxes (Chrome, 1600x900). Panels, cards (per rarity / worn / hover), glows,
tabs and buttons are the page's own CSS rendered to textures (tex:wardrobe/*, sprite wardrobe/*). Fonts: Consolas -> "mono"
(Inconsolata), the page's Pixel font (Silkscreen Bold, OFL) -> "silk_b". Pictures: the page's thumbnails with its CSS filters
(drop shadow, locked grayscale/brightness) applied by the server ("w:" keys). The previous native version is gen_ui_wardrobe_v1.py.
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
EDGE, ACC, SHADOW, INPUT, BTNFG, FILL2 = '#8465a8', '#e6cffa', '#4b3563', '#0e0914', '#1a1026', '#36254f'
TXT, DIM, GOLD, GREEN, RED = '#efe7f7', '#a898bd', '#ffd24a', '#4dffb0', '#ff6b7a'
M, MB, PX = 'mono', 'mono_b', 'silk_b'


def T(text, size=16, color=TXT, font=M, **kw):
    d = {'type': 'label', 'text': text, 'font': font, 'size': size, 'color': color, 'shadow': False}
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


def tex(name, x, y, w, h, children=None, **kw):
    d = {'type': 'column', 'x': x, 'y': y, 'w': w, 'h': h, 'bg': 'tex:wardrobe/%s.png' % name, 'children': children or []}
    d.update(kw)
    return d


def H(text, **kw):
    """.h: Pixel 14px, letter-spacing 1px, accent."""
    return T(text, 14, ACC, PX, ls=1, lh=18, **kw)


def tag(text, color, border, **kw):
    """.tag: 12px bold, padding 2px 6px, rgba(0,0,0,.35), 1px border, letter-spacing .5px."""
    return T(text, 12, color, MB, ls=0.5, lh=14, pad=[2, 6, 2, 6], bg='#00000059', border=border, bw=1, h=20, **kw)


def pxbtn(text, sprite, x, y, w, h, size, color, **kw):
    d = {'type': 'button', 'text': text, 'font': PX, 'size': size, 'color': color, 'shadow': False, 'ls': 1, 'text_align': 'center',
         'x': x, 'y': y, 'w': w, 'h': h, 'bg': 'sprite:wardrobe/' + sprite, 'hover': 'sprite:wardrobe/' + sprite + '_hover'}
    d.update(kw)
    return d


# ------------------------------------------------------------------ header
def header():
    title = T('WARDROBE', 34, ACC, PX, ls=1, tshadow='3,3,' + SHADOW, x=70, y=58, h=44, lh=44, anim='wipe', anim_steps=9, anim_ms=550, anim_delay='100')
    tab = {'type': 'button', 'w': 60, 'w_expr': 't.w', 'h': 44, 'bg': '{t.bg}', 'hover': '{t.hv}', 'hover_dy': -2, 'id': 'tab',
           'args': {'k': 'str:16'}, 'argv': {'k': 't.k'}, 'msg': vmsg('cat', k='{k}'), 'repeat': {'for': 'vm.tabs', 'as': 't', 'limit': 7},
           'children': [T('{t.label}', 12, '{t.color}', PX, ls=1, x=0, y=0, w=60, w_expr='t.w', h=44, lh=44, text_align='center'),
                        box([], x=0, y=41, w=60, w_expr='t.w', h=3, bg=GOLD, anim='wipe', anim_ms=250, anim_key='t.k', **{'if': 't.on'})]}
    tabs = row([tab], x=400, y=70, gap=5, h=44, anim='slideu', anim_delay='300')

    def meter(x, label, val, col):
        return tex('meter', x, 54, 150, 56, [T(label, 12, DIM, x=15, y=9, lh=14), T(val, 22, col, MB, x=15, y=23, lh=26)])
    meters = box([meter(0, 'Owned', '{vm.owned}', TXT), meter(164, 'Worn', '{vm.worn}', GOLD)], x=1216, y=0, w=314, h=110, anim='slider', anim_delay='200')
    return [title, tabs, meters]


# ------------------------------------------------------------------ left rail
def rail():
    bar = box([box([], x=1, y=1, w=0, w_expr='vm.collW', h=8, bg='grad:%s,%s' % (ACC, GOLD), ease_ms=500)],
              x=25, y=83, w=250, h=10, bg=INPUT, border=EDGE, bw=1)

    def chk(y, label, on, k):
        return {'type': 'button', 'x': 25, 'y': y, 'w': 250, 'h': 28, 'id': 'f_' + k, 'msg': vmsg('filter', k=k), 'children': [
            box([box([], x=3, y=3, w=12, h=12, bg=ACC, anim='popin', anim_ms=160, anim_key=on, **{'if': on})], x=0, y=4, w=20, h=20, border=EDGE, bw=1),
            T(label, 16, TXT, x=32, y=5, lh=19)]}
    pill = {'type': 'button', 'text': '{s.label}', 'font': M, 'size': 14, 'color': '{s.color}', 'shadow': False, 'text_align': 'center',
            'w': 50, 'w_expr': 's.w', 'h': 27, 'bg': '{s.bg}', 'hover': '{s.hv}', 'border': '{s.border}', 'bw': 1, 'id': 'sort',
            'args': {'k': 'str:12'}, 'argv': {'k': 's.k'}, 'msg': vmsg('sort', k='{k}'), 'repeat': {'for': 'vm.sorts', 'as': 's', 'limit': 3}}
    off = pxbtn('TAKE EVERYTHING OFF', 'danger', 21, 601, 258, 38, 12, RED, id='offall', msg=vmsg('offall'))
    return tex('rail', 70, 150, 300, 660, [
        H('COLLECTION', x=25, y=21), T('{vm.collName}', 22, TXT, MB, x=25, y=49, lh=26), bar, T('{vm.collText}', 16, DIM, x=25, y=101, lh=19),
        H('FILTER', x=25, y=136), chk(164, 'Owned only', 'vm.fOwned', 'owned'), chk(192, 'Worn only', 'vm.fWorn', 'worn'),
        H('SORT', x=25, y=236), row([pill], x=25, y=264, gap=6, h=27), off], anim='slidel', anim_delay='100')


# ------------------------------------------------------------------ cards
def cards():
    go = {'type': 'button', 'text': '{c.go}', 'font': PX, 'size': 12, 'color': '{c.goColor}', 'hover_color': '{c.goHvColor}', 'shadow': False, 'ls': 1,
          'text_align': 'center', 'right': 19, 'y': 278, 'w': 60, 'w_expr': 'c.goW', 'h': 30, 'bg': '{c.goBg}', 'hover': '{c.goHv}', 'opacity': 'c.goOp',
          'id': 'quick', 'args': {'k': 'str:48'}, 'argv': {'k': 'c.id'}, 'msg': vmsg('quick', k='{k}')}
    card = {'type': 'button', 'x': 0, 'y': 0, 'w': 270, 'h': 320, 'bg': '{c.bg}', 'hover': '{c.hv}', 'hover_dy': -6, 'clip': True,
            'repeat': {'for': 'vm.cards', 'as': 'c', 'limit': 8}, 'id': 'open', 'args': {'k': 'str:48'}, 'argv': {'k': 'c.id'}, 'msg': vmsg('open', k='{k}'),
            'anim': 'rise', 'anim_dy': 26, 'anim_ms': 420, 'anim_delay': 'c.delay', 'anim_key': 'vm.play', 'children': [
                tex('pulse_r4', 0, 0, 270, 320, bg='{c.pulse}', anim='glow', anim_ms=3200, **{'if': 'c.epic'}),
                {'type': 'image', 'pokemon': '{c.thumb}', 'x': 51, 'y': 12, 'w': 168, 'h': 168, 'anim': 'floaty', 'anim_delay': 'c.f'},
                tag('{c.slot}', DIM, EDGE, x=17, y=15),
                tag('{c.state}', '{c.stateColor}', '{c.stateBorder}', right=17, y=13),
                T('{c.name}', 21, '{c.nameColor}', MB, x=1, y=191, w=268, h=26, lh=26.25, pad=[0, 18, 0, 18], ellipsis=True, opacity='c.nameOp', size_expr=None),
                T('{c.sub}', 14, DIM, x=19, y=220, w=232, lh=17, ellipsis=True),
                tag('{c.rarity}', '{c.rc}', '{c.rc}', x=19, y=283),
                go,
                tex('shine', -57, 0, 160, 320, anim='sweep', anim_dy=297, anim_delay='c.shineDelay', **{'if': 'c.shine'})]}
    # the name's font size follows its length (21/19/17/15/14px): one label per size
    name = card['children'][4]
    del name['size_expr']
    names = []
    for sz in (21, 19, 17, 15, 14):
        n = dict(name)
        n['size'] = sz
        n['lh'] = round(sz * 1.25, 2)
        n['y'] = 191 + (26.25 - sz * 1.25) / 2 if False else 191
        n['if'] = 'c.nsize == %d' % sz
        names.append(n)
    card['children'][4:5] = names
    grid = box([dict(card, x='c.x', y='c.y')], x=400, y=150, w=1140, h=660)
    empty = box([T('No matches', 22, ACC, PX, lh=26, **{'if': 'vm.emptyTitle'}), T('{vm.emptyText}', 18, '{vm.emptyColor}', lh=21)],
                x=400, y=180, w=1140, gap=10, **{'if': 'vm.empty'})
    return [grid, empty]


# ------------------------------------------------------------------ bottom
def bottom():
    def pg(text, x, d, op, hv):
        return pxbtn(text, 'ghost', x, 6, 111.8, 38, 13, TXT, id='prev' if d < 0 else 'next', key='left' if d < 0 else 'right', opacity=op, hover=hv,
                     args={'d': 'int:-1:1'}, argv={'d': str(d)}, msg=vmsg('page', d='{d}'))
    return box([pg('< PREV', 300, -1, 'vm.prevOp', '{vm.prevHv}'),
                T('{vm.pageLabel}', 15, DIM, ls=1, x=439.8, y=16, w=100, lh=18, text_align='center'),
                pg('NEXT >', 567.8, 1, 'vm.nextOp', '{vm.nextHv}'),
                pxbtn('CLOSE', 'solid', 970, 2, 160, 46, 16, BTNFG, id='close', close=True)],
               x=400, y=822, w=1130, h=50, anim='slideu', anim_delay='400')


# ------------------------------------------------------------------ detail window
def detail():
    D = 'vm.d'
    art = box([tex('glow_r0', 5, 40, 320, 320, bg='{vm.d.glow}'),
               tex('ring_r0', 5, 40, 320, 320, bg='{vm.d.ring}', anim='ring'),
               {'type': 'image', 'pokemon': '{vm.d.thumb}', 'x': 10, 'y': 45, 'w': 310, 'h': 310, 'anim': 'floaty'}],
              x=41, y=41, w=330, h=400, clip=True)
    tags = row([tag('{t.text}', '{t.color}', '{t.border}', repeat={'for': 'vm.d.tags', 'as': 't', 'limit': 3})], gap=6, h=20)
    desc = T('{l}', 17, '#c3d0d8', w=560, lh=26.35, wrap=True, repeat={'for': 'vm.d.desc', 'as': 'l', 'limit': 12})
    extra = T('{l}', 15, DIM, w=560, lh=24, rich=True, rich_plain=True, bcolor=TXT, repeat={'for': 'vm.d.extra', 'as': 'l', 'limit': 6})
    sp = {'type': 'button', 'w': 34, 'h': 34,
          'bg': '{m.bg}', 'border': '{m.border}', 'bw': 1, 'ring': '{m.ring}', 'id': 'mate', 'args': {'k': 'str:48'}, 'argv': {'k': 'm.id'}, 'msg': vmsg('open', k='{k}'),
          'tip': '{m.name}', 'repeat': {'for': 'vm.d.set', 'as': 'm', 'limit': 6},
          'children': [T('{m.abbr}', 11, '{m.color}', PX, x=0, y=0, w=34, h=34, lh=34, text_align='center'),
                       box([], right=-3, y=-3, w=8, h=8, bg=GREEN, **{'if': 'm.worn'})]}
    body = box([box([desc], w=560),
                box([extra], w=560, pad=[12, 0, 0, 0], **{'if': 'vm.d.hasExtra'}),
                box([H('{vm.d.setLabel}'), box([], h=10), row([sp], gap=8, h=34)], w=560, pad=[16, 0, 0, 0], **{'if': 'vm.d.hasSet'})],
               x=401, y=139, w=560, anim='slideu', anim_ms=300, anim_delay='120', anim_key='vm.d.id', **{'if': '!vm.partyMode'})
    prow = {'type': 'button', 'w': 560, 'h': 42, 'border': '{p.border}', 'bw': 1, 'hover': '{p.hv}', 'opacity': 'p.op', 'id': 'skin',
            'args': {'slot': 'int:0:5'}, 'argv': {'slot': 'p.slot'}, 'msg': vmsg('skin', slot='{slot}'), 'repeat': {'for': 'vm.d.members', 'as': 'p', 'limit': 6},
            'children': [T('{p.name}', 18, TXT, MB, x=15, y=10, w=312, lh=22, ellipsis=True, **{'if': '!p.empty'}),
                         T('{p.level}', 14, DIM, x=341, y=12, w=70, lh=17, **{'if': '!p.empty'}),
                         T('{p.status}', 13, '{p.scolor}', ls=1, x=425, y=13, w=120, lh=15, text_align='right', **{'if': '!p.empty'}),
                         T('{p.name}', 16, DIM, x=15, y=11, lh=19, **{'if': 'p.empty'})]}
    party = box([H('CHOOSE A POKEMON'), box([], h=10), T('Loading your party...', 16, DIM, **{'if': 'vm.d.partyLoading'}), box([prow], gap=5)],
                x=401, y=137, w=560, anim='slideu', anim_ms=300, **{'if': 'vm.partyMode'})
    info = [T('{vm.d.slot}', 13, DIM, ls=1, upper=True, x=401, y=35, w=560, lh=15, anim='slideu', anim_ms=300, anim_key='vm.d.id'),
            T('{vm.d.name}', 32, '{vm.d.nameColor}', MB, x=401, y=54, w=560, lh=36.8, ellipsis=True, anim='slideu', anim_ms=300, anim_delay='40', anim_key='vm.d.id'),
            box([tags], x=401, y=101, w=560, anim='slideu', anim_ms=300, anim_delay='80', anim_key='vm.d.id'), body, party]
    status = box([T('{vm.d.status}', 22, '{vm.d.statusColor}', MB, lh=30.8, w='fill'),
                  T('{vm.d.note}', 18, DIM, lh=25.2, wrap=True, w='fill')], x=60, y=479, w=0, w_expr='vm.d.statusW', h=110, align='center')
    btns = row([pxbtn('{vm.d.tryText}', 'ghost', 0, 0, 100, 38, 13, TXT, w_expr='vm.d.tryW', id='try', msg=vmsg('try'), **{'if': 'vm.d.canTry'}),
                pxbtn('{vm.d.mainText}', '{vm.d.mainSprite}', 0, 0, 250, 56, 18, '{vm.d.mainColor}', w_expr='vm.d.mainW', id='main',
                      bg='{vm.d.mainBg}', hover='{vm.d.mainHv}', opacity='vm.d.mainOp', msg=vmsg('main'), **{'if': '!vm.partyMode'}),
                pxbtn('{vm.d.backText}', 'ghost', 0, 0, 94, 38, 13, TXT, w_expr='vm.d.backW', id='back', msg=vmsg('back'))],
               right=60, y=506, h=56, gap=14, cross='center', shrink=True)
    for b in btns['children']:
        b.pop('x', None)
        b.pop('y', None)
    panel = tex('detail', 300, 140, 1000, 620, [art] + info + [tex('dbar', 35, 479, 930, 110), status, btns], anim='popin', anim_ms=240, anim_key='vm.openN')
    # clicking the dark area around the window closes it (#overlay click)
    outside = [{'type': 'button', 'x': x, 'y': y, 'w': w, 'h': h, 'id': 'shut%d' % (n + 1), 'msg': vmsg('close')}
               for n, (x, y, w, h) in enumerate( ((0, 0, 1600, 140), (0, 760, 1600, 140), (0, 140, 300, 620), (1300, 140, 300, 620)))]
    outside[0]['key'] = 'esc'
    return box(outside + [panel], x=0, y=0, w=1600, h=900, z=150, bg='#08050cd6', anim='fade', anim_ms=200, anim_key='vm.openN', **{'if': 'vm.detail'})


def page():
    bg = tex('bg', 0, 0, 1600, 900)
    grid = box([tex('grid', -40, -40, 1680, 980, anim='drift', anim_dy=40)], x=0, y=0, w=1600, h=900, clip=True)
    frame = box([], x=24, y=24, w=1552, h=852, border=EDGE, bw=2, anim='reveal', anim_ms=700)
    refresh = {'type': 'spacer', 'id': 'refresh', 'w': 0, 'h': 0, 'every_ms': 20000, 'args': {}, 'msg': {'channel': 'snobblecosmetics', 'action': 'refresh'}}
    root = box([bg, grid, frame] + header() + [rail()] + cards() + [bottom(), detail(), refresh], w=1600, h=900)
    return {'v': 2, 'id': 'wardrobe', 'channel': 'snobblecosmetics', 'hello': 'ready', 'bye': True, 'title': 'Wardrobe', 'width': 1600, 'height': 900,
            'fit': [1600, 900],
            'toast': {'bg': '#1a1325', 'border': GREEN, 'border_bad': RED, 'color': GREEN, 'color_bad': RED, 'bw': 2, 'radius': 0, 'pad_x': 22, 'pad_y': 12,
                      'y': 52, 'from': -22, 'font': M, 'size': 17},
            'root': root}


def clean(o):
    """drop None values left by the helpers"""
    if isinstance(o, dict):
        return {k: clean(v) for k, v in o.items() if v is not None}
    if isinstance(o, list):
        return [clean(v) for v in o]
    return o


d = clean(page())
with open(os.path.join(OUT, 'wardrobe.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, separators=(',', ':'))
print('wrote wardrobe (%d bytes)' % os.path.getsize(os.path.join(OUT, 'wardrobe.json')))
