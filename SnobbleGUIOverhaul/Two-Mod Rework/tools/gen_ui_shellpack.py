"""Native Shellpack screen (shellpack.json), a faithful copy of the original shellpack/index.html + style.css + app.js.

Stage 1600x900 scaled to fit (min(w/1600, h/900)) -> "fit": [1600, 900]. The page was see-through over the world ("nodim").
Colours per shell tier (PAL in app.js: pb / pd / acc, pl = pb mixed 70% to white, sl = pd mixed 10% to black) come in vm.pal.
Panels and slots are the CSS bevels (border-color: light top/left, dark bottom/right). The snail is drawSnail() redrawn to
tex:shellpack/snail_<tier>_<frame>.png; the tab icons are tabIcon()'s 12x12 pixel drawings, sent as a pixfont.
The previous native version is gen_ui_shellpack_v1.py.
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
N, NB, O = 'nunito', 'nunito_b', 'oswald'
TXT, EDGE_SH = '#3a2a18', '#1c1209'
_N = [0]


def nid(p='n'):
    _N[0] += 1
    return '%s%d' % (p, _N[0])


def T(text, size=16, color=TXT, bold=True, font=None, **kw):
    d = {'type': 'label', 'text': text, 'font': font or (NB if bold else N), 'size': size, 'color': color, 'shadow': False}
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


BEVEL = ['{vm.pal.pl}', '{vm.pal.pd}', '{vm.pal.pd}', '{vm.pal.pl}']


def panel(children, **kw):
    """.panel { background: var(--pb); border: 4px solid; border-color: pl pd pd pl; box-shadow: 0 0 0 2px #1c1209 }"""
    d = box(children, shrink=True, bg='{vm.pal.pb}', bcolors=BEVEL, bw=4, ring='2,' + EDGE_SH)
    d.update(kw)
    return d


def tbtn(text, msg, **kw):
    """.tbtn 32x32; margin-left 6px; bg pb; border 3px pl pd pd pl; 15px; hover brightness(1.12) + translateY(-1px)"""
    d = {'type': 'button', 'text': text, 'font': NB, 'size': 15, 'color': TXT, 'shadow': False, 'w': 32, 'h': 32, 'bg': '{vm.pal.pb}', 'hover': '{vm.pal.pbH}',
         'bcolors': BEVEL, 'bw': 3, 'hover_dy': -1, 'text_align': 'center', 'id': nid('t')}
    d.update(msg)
    d.update(kw)
    return d


def slot(src, var='c', limit=12):
    """.slot 46x46; bg sl; border 2px (#0006 #fff4 #fff4 #0006); hover brightness 1.3; .sel 3px white outline; .over red; .bad icon .35; pop-in on first draw"""
    c = {'type': 'button', 'w': 46, 'h': 46, 'bg': '{%s.bg}' % var, 'hover': '{%s.hover}' % var, 'bcolors': ['#00000066', '#ffffff44', '#ffffff44', '#00000066'], 'bw': 2,
         'id': nid('slot'), 'repeat': {'for': src, 'as': var, 'limit': limit},
         'anim': 'grow', 'anim_delay': '%s.delay' % var, 'anim_key': 'vm.popKey',
         'args': {'pane': 'enum:s|p', 'idx': 'int:0:215', 'btn': 'int:0:1', 'shift': 'int:0:1'},
         'argv': {'pane': '%s.pane' % var, 'idx': '%s.idx' % var, 'btn': '0', 'shift': '0'},
         'msg': vmsg('slot', pane='{pane}', idx='{idx}', btn='{btn}', shift='{shift}'), 'children': [
             box([{'type': 'item', 'item': '{%s.item}' % var, 'w': 34, 'h': 34, 'if': '!%s.shell' % var},
                  {'type': 'image', 'pokemon': '{%s.art}' % var, 'w': 34, 'h': 34, 'if': '%s.shell' % var}], x=4, y=4, w=34, h=34, opacity='%s.iop' % var,
                 **{'if': '%s.has' % var}),
             box([], x=0, y=0, w=46, h=46, bg='#b36cff33', **{'if': '%s.foil' % var}),
             T('{%s.n}' % var, 13, '#ffffff', right=1, bottom=-1, tshadow='1,1,#000000', **{'if': '%s.n' % var}),
             box([box([], w='{%s.dpct}' % var if False else 'fill', h=3, bg='{%s.dcol}' % var)], x=4, right=4, bottom=3, h=3, bg='#000000aa', **{'if': '%s.dmg' % var}),
             box([], x=0, y=0, w=46, h=46, border='#ffffff', bw=3, **{'if': '%s.sel' % var}),
             tipbox(var)]}
    # the durability fill is a bar so its width can follow the value
    c['children'][3] = {'type': 'bar', 'x': 4, 'right': 4, 'bottom': 3, 'h': 3, 'value': '%s.dpct' % var, 'max': '100', 'fill': '{%s.dcol}' % var, 'fill_bg': '#000000aa',
                        'if': '%s.dmg' % var}
    return c


def tipbox(var):
    """#tip { max-width 340px; padding 6px 10px; bg #1d0f2e; border 2px #5a2d82; 700 14px Nunito }  b 15px; span #bbb 500; .warn #ff8a80"""
    return box([T('{%s.name}' % var, 15, '#ffffff', w='content'),
                T('{l.text}' % (), 14, '#bbbbbb', bold=False, repeat={'for': '%s.tl' % var, 'as': 'l', 'limit': 8}),
                T('{%s.warn}' % var, 14, '#ff8a80', **{'if': '%s.warn' % var})],
               tipbox=True, x=0, y=0, w='content', pad=[8, 12, 8, 12], bg='#1d0f2e', border='#5a2d82', bw=2, radius=3, **{'if': '%s.has' % var})


def grid(rows_src, cells_var, limit_rows):
    """.grid { gap 2px; background pd; padding 2px; border 2px (pd pl pl pd) }"""
    r = row([slot('r.cells')], gap=2, repeat={'for': rows_src, 'as': 'r', 'limit': limit_rows})
    return box([r], w='content', bg='{vm.pal.pd}', bcolors=['{vm.pal.pd}', '{vm.pal.pl}', '{vm.pal.pl}', '{vm.pal.pd}'], bw=2, pad=[4, 4, 4, 4], gap=2)


# tabIcon(kind): 12x12 drawings with fillRect, as a pixel font ('i' info, 'o' options, 'u' upgrade)
def icon_rows(rects):
    g = [[0] * 12 for _ in range(12)]
    for x, y, w, h, v in rects:
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                g[yy][xx] = v
    return [''.join(str(c) for c in r) for r in g]


ARROWS = {  # the title / inventory button symbols (the fonts carry no arrows; Chrome drew them from a system font)
    'S': ['0100010', '1110010', '0100010', '0100010', '0100111', '0100010', '0000000'][:0] or
         ['00100000', '01110000', '11111000', '00100100', '00100100', '00011111', '00001110', '00000100'],
    'U': ['0001000', '0010100', '0100010', '1110111', '0010100', '0010100', '0011100'],
    'D': ['0011100', '0010100', '0010100', '1110111', '0100010', '0010100', '0001000']}
ICONS = {'i': icon_rows([(5, 1, 2, 2, 1), (5, 4, 2, 6, 1), (4, 10, 4, 1, 1)]),
         'o': icon_rows([(5, 1, 2, 10, 1), (1, 5, 10, 2, 1), (3, 3, 6, 6, 1), (5, 5, 2, 2, 0)]),
         'u': icon_rows([(5, 1, 2, 2, 1), (3, 3, 6, 2, 1), (1, 5, 10, 2, 1), (5, 7, 2, 4, 1)])}


def tabs():
    """#tabs { left -64px; top 52px; gap 6px }  .tab 56x56; bg pb; border 3px bevel; radius 4px 0 0 4px; .on { margin-left -8px; width 64px }"""
    def tab(k, letter):
        b = {'type': 'button', 'w': '{vm.tabs.%s.w}' % k if False else 56, 'h': 56, 'bg': '{vm.pal.pb}', 'hover': '{vm.pal.pbH}', 'bcolors': BEVEL, 'bw': 3,
             'radii': [4, 0, 0, 4], 'ring': '2,' + EDGE_SH, 'x': 'vm.tabX.%s' % k, 'y': 'vm.tabY.%s' % k, 'ease_ms': 250, 'align': 'center', 'cross': 'center',
             'id': nid('tab'), 'args': {'k': 'str:8'}, 'argv': {'k': "'%s'" % {'i': 'info', 'o': 'opt', 'u': 'up'}[letter]}, 'msg': vmsg('tab', k='{k}'),
             'children': [{'type': 'pixtext', 'text': letter, 'size': 2.5, 'color': TXT}]}
        return b
    return [tab('info', 'i'), tab('opt', 'o'), tab('up', 'u')]


def tabpanel():
    """#tabpanel { left -330px; top 52px; width 256px; padding 12px 14px }  h5 Oswald 19px  p 13.5px/1.4 500  .opt buttons  .row  .cost  .go"""
    opt = {'type': 'button', 'text': '{o.label}', 'font': NB, 'size': 13, 'shadow': False, 'pad': [4, 10, 4, 10], 'color': '{o.color}', 'bg': '{o.bg}', 'hover': '{o.bg}',
           'border': '{o.border}', 'bw': 2, 'radius': 2, 'id': nid('opt'), 'repeat': {'for': 'vm.rowsT', 'as': 'o', 'limit': 3},
           'args': {'k': 'int:0:2'}, 'argv': {'k': 'o.k'}, 'msg': vmsg('mode', k='{k}')}
    trow = row([T('{t.name}', 13.5, '{t.color}', bold=False, w='fill'), T('{t.slots}', 13.5, '{t.color}', bold=False)], w='fill', pad=[3, 0, 3, 0],
               border='#00000033', bw=1, dash=True, bsides='b', repeat={'for': 'vm.rowsT', 'as': 't', 'limit': 6})
    cost = row([box([{'type': 'item', 'item': '{k.item}', 'w': 24, 'h': 24, 'x': 2, 'y': 2}], w=32, h=32, bg='{vm.pal.sl}',
                    bcolors=['#00000066', '#ffffff44', '#ffffff44', '#00000066'], bw=2),
                T('{k.text}', 13.5, '{k.color}', bold=False, wrap=True, w='fill')], w='fill', gap=8, cross='center', pad=[4, 0, 4, 0],
               repeat={'for': 'vm.cost', 'as': 'k', 'limit': 6})
    go = {'type': 'button', 'text': '{vm.upText}', 'font': NB, 'size': 15, 'shadow': False, 'w': 'fill', 'pad': [8, 8, 8, 8], 'color': '#ffffff', 'text_align': 'center',
          'bg': '{vm.pal.acc}', 'hover': '{vm.pal.accH}', 'disabled': '#8a8070', 'bcolors': ['#ffffff66', '#00000055', '#00000055', '#ffffff66'], 'bw': 3, 'radius': 2,
          'enabled': 'vm.canUp', 'id': 'upgrade', 'msg': vmsg('upgrade'), 'args': {}, 'if': 'vm.pUp && vm.upText'}
    body = box([
        T('{vm.pTitle}', 19, font=O, ls=0.95, pad=[0, 0, 8, 0]),
        T('{l.text}', 13.5, bold=False, lh=18.9, wrap=True, w='fill', pad=[6, 0, 6, 0], repeat={'for': 'vm.lines', 'as': 'l', 'limit': 8}),
        row([opt], gap=4, wrap=True, w='fill', pad=[8, 0, 8, 0], **{'if': 'vm.pOpt'}),
        box([trow], w='fill', **{'if': 'vm.pUp'}),
        box([cost], w='fill', **{'if': 'vm.pUp'}),
        box([go], w='fill', pad=[10, 0, 0, 0], **{'if': 'vm.pUp && vm.upText'})], w='fill', gap=0)
    return panel([body], x=-330, y=52, w=256, pad=[16, 18, 16, 18], anim='slidein', anim_key='vm.tab', **{'if': 'vm.panel'})


def page():
    title = row([{'type': 'image', 'pokemon': '{vm.icon}', 'w': 26, 'h': 26}, box([], w=10),
                 T('{vm.title}', 22, font=O, ls=0.88), box([], w='fill'),
                 tbtn('', {'msg': vmsg('sort'), 'args': {}}, align='center', cross='center', children=[{'type': 'pixtext', 'text': 'S', 'size': 1.75, 'color': TXT}]), box([], w=6),
                 tbtn('{vm.modeLetter}', {'msg': vmsg('cyclemode'), 'args': {}}), box([], w=6),
                 tbtn('✕', {'close': True})], w='fill', h=34, cross='center', pad=[0, 0, 6, 0])
    meter = row([
        box([box([], x=0, bottom=6, w='fill' if False else None, h=8, bg='{vm.pal.pd}', radius=4, right=0),
             {'type': 'bar', 'x': 0, 'right': 0, 'bottom': 6, 'h': 8, 'value': 'vm.pct', 'max': '100', 'fill': 'grad:{vm.pal.trail0},#ffffff', 'fill_bg': '#00000000', 'radius': 4,
              'ease_ms': 800},
             {'type': 'image', 'texture': '{vm.snail}', 'xp': 'vm.pct', 'bottom': 10, 'w': 48, 'h': 36, 'ease_ms': 800}],
            w='fill', h=44),
        T('{vm.meter}', 15, font=O, ls=0.9, pad=[0, 0, 2, 0])], w='fill', h=44, gap=12, cross='end', pad=[6, 0, 0, 0])
    main = panel([title, grid('vm.shellRows', 'c', 12), meter], pad=[12, 16, 14, 16], gap=0)
    inv_head = row([T('Inventory', 20, font=O, ls=0.8), box([], w='fill'),
                    tbtn('', {'msg': vmsg('stow'), 'args': {}}, align='center', cross='center', children=[{'type': 'pixtext', 'text': 'U', 'size': 1.75, 'color': TXT}]), box([], w=6),
                    tbtn('', {'msg': vmsg('withdraw'), 'args': {}}, align='center', cross='center', children=[{'type': 'pixtext', 'text': 'D', 'size': 1.75, 'color': TXT}])],
                   w='fill', h=34, cross='center', pad=[0, 0, 4, 0])
    inv_rows = box([row([slot('r.cells', 'c', 9)], gap=2, pad=['{r.gap}' if False else 0, 0, 0, 0], repeat={'for': 'vm.invRows', 'as': 'r', 'limit': 4})], gap=2)
    inv_grid = box([inv_rows], w='content', bg='{vm.pal.pd}', bcolors=['{vm.pal.pd}', '{vm.pal.pl}', '{vm.pal.pl}', '{vm.pal.pd}'], bw=2, pad=[4, 4, 4, 4])
    inv = panel([inv_head, inv_grid], pad=[10, 16, 16, 16], gap=0)
    held = {'type': 'column', 'cursor': True, 'x': -19, 'y': -19, 'w': 38, 'h': 38, 'if': 'vm.hasHeld', 'children': [
        {'type': 'item', 'item': '{vm.heldItem}', 'w': 38, 'h': 38, 'if': '!vm.heldShell'},
        {'type': 'image', 'pokemon': '{vm.heldArt}', 'w': 38, 'h': 38, 'if': 'vm.heldShell'},
        T('{vm.heldN}', 13, '#ffffff', right=-2, bottom=-2, tshadow='1,1,#000000', **{'if': 'vm.heldN'})]}
    gui = box([main, inv] + tabs() + [tabpanel()], shrink=True, cross='center', gap=-4)
    ping = {'type': 'spacer', 'id': 'ping', 'x': 0, 'y': 0, 'w': 0, 'h': 0, 'every_ms': 3000, 'args': {}, 'msg': {'channel': 'shellpack', 'action': 'ping'}}
    root = {'type': 'column', 'w': 1600, 'h': 900, 'align': 'center', 'cross': 'center', 'children': [gui, held, ping]}
    font = dict(ICONS)
    font.update(ARROWS)
    return {'v': 2, 'id': 'shellpack', 'channel': 'shellpack', 'hello': 'ready', 'bye': True, 'bye_action': 'close', 'title': 'Shellpack', 'width': 1600, 'height': 900,
            'fit': [1600, 900], 'nodim': True, 'pixfont': font,
            'toast': {'bg': '#2a1d10', 'border': '#8fd05a', 'border_bad': '#d96a58', 'color': '#ffffff', 'color_bad': '#ffffff', 'font': NB, 'size': 18, 'y': 816,
                      'from': 0, 'radius': 0, 'pad_x': 22, 'pad_y': 10, 'bw': 3},
            'root': root}


def strip(o):
    if isinstance(o, dict):
        return {k: strip(v) for k, v in o.items() if v is not None}
    if isinstance(o, list):
        return [strip(v) for v in o]
    return o


d = strip(page())
with open(os.path.join(OUT, 'shellpack.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, separators=(',', ':'))
print('wrote shellpack (%d bytes)' % os.path.getsize(os.path.join(OUT, 'shellpack.json')))
