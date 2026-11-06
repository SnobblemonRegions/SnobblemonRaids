"""Native Auction House (auction.json), a faithful copy of the original auction/index.html + style.css + app.js.

Window 1200x720, scaled like the original fit(): min(w*.74/1200, h*.74/720) * zoom -> "fit": [1622, 973] and the local "zoompct".
Colours come from the player's theme (gray/violet/emerald/crimson/ocean/amber), kept per player by SnobbleAuctions (prefs), as vm.p.*.
Fonts: Nunito (body), Oswald (headings, buttons). The card spotlight is the page's CSS rendered per theme (tex:auction/art_<theme>.png).
The previous native version is gen_ui_auction_v1.py.
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
N, NB, O = 'nunito', 'nunito_b', 'oswald'
GOLD, GREEN, MAG, RED, BLUE, PURPLE = '#ffd24a', '#4dffb0', '#ff4fe0', '#ff6b7a', '#7cc0ff', '#b9a0ff'
_N = [0]


def nid(p='n'):
    _N[0] += 1
    return '%s%d' % (p, _N[0])


def P(k):
    return '{vm.p.%s}' % k


def T(text, size=16, color=None, bold=False, **kw):
    d = {'type': 'label', 'text': text, 'font': NB if bold else N, 'size': size, 'color': color or P('txt'), 'shadow': False}
    d.update(kw)
    return d


def HD(text, size=14, color=None, ls=3, **kw):
    """Oswald 600 uppercase (the --head font)."""
    d = {'type': 'label', 'text': text, 'font': O, 'size': size, 'color': color or P('acc'), 'ls': ls, 'upper': True, 'shadow': False}
    d.update(kw)
    return d


def H(text, **kw):
    """.h { Oswald 600 14px; letter-spacing 3px; colour acc; margin-bottom 10px; uppercase }"""
    kw.setdefault('pad', [0, 0, 10, 0])
    return HD(text, 14, **kw)


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


def msg(action, args=None, argv=None, **fixed):
    """id + args/argv + a message to the view."""
    m = vmsg(action, **{k: '{%s}' % k for k in (args or {})})
    m.update(fixed)
    return {'id': nid('b'), 'args': args or {}, 'argv': argv or {}, 'msg': m}


def btn(text, kind, action, args=None, argv=None, **kw):
    """.btn { Oswald 600 16px; ls 3px; height 42px; padding 0 20px } .solid { bg acc; colour btnfg; hover #fff } .ghost { bg btnbg; colour txt; inset 1.5px edge; 34px; 13px }"""
    solid = kind == 'solid'
    d = {'type': 'button', 'text': text, 'font': O, 'size': 16 if solid else 13, 'ls': 3, 'upper': True, 'shadow': False, 'h': 42 if solid else 34,
         'pad': [0, 20, 0, 20], 'text_align': 'center', 'color': P('btnfg') if solid else P('txt'), 'bg': P('acc') if solid else P('btnbg'),
         'hover': '#ffffff' if solid else P('on'), 'disabled': P('acc') + '59' if False else (P('acc') if solid else P('btnbg'))}
    if not solid:
        d.update({'border': P('edge'), 'bw': 1})
    d.update(msg(action, args, argv))
    d.update(kw)
    return d


def tbtn(text, action=None, **kw):
    """.tbtn 30x30; 18px bold; bg btnbg; inset 1.5px edge; hover on / #fff"""
    d = {'type': 'button', 'text': text, 'font': NB, 'size': 18, 'color': P('txt'), 'shadow': False, 'w': 30, 'h': 30, 'bg': P('btnbg'), 'hover': P('on'),
         'border': P('edge'), 'bw': 1, 'text_align': 'center'}
    if action:
        d.update(action)
    d.update(kw)
    return d


def tag(text, color, **kw):
    """.tag { 12px bold; padding 2px 5px; bg rgba(0,0,0,.35); border 1px; ls .5px }"""
    return T(text, 12, color, True, ls=0.5, bg='#00000059', border=color, bw=1, pad=[2, 5, 2, 5], **kw)


def tags(src, var, **kw):
    return row([tag('{%s.text}' % var, '{%s.color}' % var, repeat={'for': src, 'as': var, 'limit': 6})], gap=4, wrap=True, **kw)


def sprite(var_sprite, var_item, is_item, size, **kw):
    return box([{'type': 'image', 'pokemon': var_sprite, 'w': size, 'h': size, 'if': '!' + is_item},
                {'type': 'item', 'item': var_item, 'w': size, 'h': size, 'if': is_item}], w=size, h=size, **kw)


# ---------------------------------------------------------------- title bar, tabs, rail
def titlebar():
    meter = lambda lab, val, col: row([T(lab, 13, P('dim')), T(val, 18, col, True)], gap=8, cross='end')
    return row([
        HD('{vm.title}', 20, ls=3, upper=False),
        row([T('{vm.crumb}', 14, P('dim'), ls=2), box([box([], w='fill', h=1, bg=P('edge'), opacity=0.5)], w='fill', pad=[8, 0, 0, 0])], w='fill', gap=12, cross='center'),
        row([meter('Slots', '{vm.slots}', '{vm.slotsColor}'), meter('Balance', '{vm.balance}', GOLD)], gap=22),
        tbtn('⚙', msg('settings', {'k': 'str:8'}, {'k': "'open'"})),
        row([tbtn('−', msg('pref', {'k': 'str:8', 'v': 'str:16'}, {'k': "'zoom'", 'v': "'-10'"})),
             T('{$zoompct}%', 13, P('dim'), min_w=48, text_align='center'),
             tbtn('+', msg('pref', {'k': 'str:8', 'v': 'str:16'}, {'k': "'zoom'", 'v': "'10'"}))], gap=4, cross='center'),
        tbtn('×', {'id': 'close', 'close': True})],
        x=0, y=0, w=1200, h=46, gap=14, cross='center', pad=[0, 12, 0, 18], bg=P('panel'), border=P('edge'), bw=1, bsides='b')


def toptabs():
    def t(label, on, action, k):
        return {'type': 'button', 'text': label, 'font': O, 'size': 16, 'ls': 3, 'upper': True, 'shadow': False, 'w': 'fill', 'h': 38, 'text_align': 'center',
                'bg': '{vm.%s.bg}' % on, 'hover': '{vm.%s.hover}' % on, 'border': '{vm.%s.border}' % on, 'bw': 2, 'color': '{vm.%s.color}' % on,
                **msg(action, {'k': 'str:8'}, {'k': "'%s'" % k})}
    return row([t('Browse', 'tBrowse', 'nav', 'browse'), t('Sell', 'tSell', 'nav', 'sell'), t('My listings', 'tMine', 'nav', 'mine'),
                box([box([], w=2, h=28, bg=P('edge'), opacity=0.6)], pad=[5, 4, 5, 4]),
                t('Pokémon', 'tMon', 'tab', 'mon'), {**t('Items', 'tItem', 'tab', 'item'), 'if': 'vm.allowItems'}], x=14, y=56, w=1172, h=38, gap=8)


def rail():
    search = {'type': 'input', 'id': 'search', 'bind': 'q', 'hint': '{vm.searchHint}', 'w': 'fill', 'h': 36, 'max_len': 40, 'bg': P('input'), 'border': P('edge'), 'bw': 1,
              'color': P('txt'), 'font': N, 'size': 14, 'pad': [0, 10, 0, 10], 'debounce_ms': 450, 'args': {'q': 'str:40'}, 'argv': {'q': '$q'}, 'msg': vmsg('search', q='{q}')}
    chk = {'type': 'button', 'w': 'fill', 'h': 28, 'bg': '#00000000', 'hover': '#00000000', 'repeat': {'for': 'vm.filters', 'as': 'f', 'limit': 6},
           **msg('flt', {'k': 'str:16'}, {'k': 'f.k'}), 'children': [row([
               box([box([], x=3, y=3, w=12, h=12, bg=P('acc'), **{'if': 'f.on'})], w=18, h=18, border=P('edge'), bw=1),
               T('{f.label}', 15)], gap=10, cross='center', h='fill')]}
    sortb = {'type': 'button', 'text': '{s.label}', 'font': N, 'size': 14, 'shadow': False, 'w': 'fill', 'pad': [1, 10, 1, 10], 'color': '{s.color}',
             'bg': '{s.bg}', 'hover': '{s.hover}', 'repeat': {'for': 'vm.sorts', 'as': 's', 'limit': 6}, **msg('sort', {'k': 'str:16'}, {'k': 's.k'})}
    catb = {'type': 'button', 'w': 'fill', 'h': 30, 'pad': [0, 8, 0, 8], 'bg': '{k.bg}', 'hover': P('on'), 'border': '{k.border}', 'bw': '{k.bw}' if False else 2,
            'repeat': {'for': 'vm.cats', 'as': 'k', 'limit': 8}, **msg('cat', {'k': 'str:16'}, {'k': 'k.k'}), 'children': [row([
                {'type': 'item', 'item': '{k.icon}', 'w': 24, 'h': 24}, HD('{k.label}', 14, '{k.color}', 2)], gap=10, cross='center', h='fill')]}
    modb = {'type': 'button', 'text': '{vm.modLabel}', 'font': N, 'size': 14, 'shadow': False, 'w': 'fill', 'h': 32, 'pad': [0, 8, 0, 8], 'color': P('txt'),
            'bg': P('input'), 'hover': P('hov'), 'border': P('edge'), 'bw': 1, **msg('mod')}
    browse = box([H('Search'), search,
                  box([H('Category', pad=[10, 0, 10, 0]), box([catb], w='fill', gap=4), H('Mod', pad=[10, 0, 10, 0]), modb], w='fill', **{'if': 'vm.isItemTab'}),
                  box([H('Filter', pad=[10, 0, 10, 0]), box([chk], w='fill')], w='fill', **{'if': 'vm.isMonTab'}),
                  H('Sort', pad=[10, 0, 10, 0]), box([sortb], w='fill', pad=[0, -6, 0, -6] if False else [0, 0, 0, 0])], w='fill', **{'if': 'vm.isBrowse'})
    other = box([H('Auction House'), T('{vm.hint}', 14, P('dim'), lh=21, wrap=True, w='fill')], w='fill', **{'if': '!vm.isBrowse'})
    return box([browse, other], x=14, y=104, w=214, h=548, pad=[16, 16, 16, 16], bg=P('panel'), border=P('edge'), bw=1, clip=True)


# ---------------------------------------------------------------- browse grid + hover card
def tipbox():
    """#tt: 310px; padding 11px 13px; bg input; border 2px edge + 2px black; 14px / 1.5"""
    line = lambda kind, node: {**node, 'if': 'l.kind == \'%s\'' % kind}
    rows = box([
        line('t', T('{l.text}', 18, '{l.color}', True)),
        line('mod', T('{l.text}', 12, P('dim'), ls=1, upper=True, wrap=True, w='fill')),
        line('hr', box([box([], w='fill', h=1, bg=P('edge'), opacity=0.6)], w='fill', pad=[7, 0, 7, 0])),
        line('kv', T('{l.text}', 14, P('txt'), lh=21, wrap=True, w='fill', rich=True, bcolor='{l.color}')),
        line('line', T('{l.text}', 14, '{l.color}', lh=21, italic=True, wrap=True, w='fill')),
        line('iv', row([T('{l.a}', 14, P('dim'), lh=21, rich=True, bcolor='{l.ac}', w='fill'), T('{l.b}', 14, P('dim'), lh=21, rich=True, bcolor='{l.bc}', w='fill'),
                        T('{l.c}', 14, P('dim'), lh=21, rich=True, bcolor='{l.cc}', w='fill')], w='fill', gap=8)),
        line('hint', HD('{l.text}', 12, GREEN, 2, pad=[5, 0, 0, 0]))], w='fill', gap=0, repeat={'for': 'c.tip', 'as': 'l', 'limit': 24})
    return box([rows], tipbox=True, x=0, y=0, w=310, pad=[13, 15, 13, 15], bg=P('input'), border=P('edge'), bw=2, ring='2,#000000', bshadow='0,12,#000000b3',
               **{'if': 'vm.tips'})


def card():
    """.card 226x267 (4 columns, gap 14): border 2px rarity colour; padding 0 9px 9px; hover lifts 3px, bg hov."""
    c = {'type': 'button', 'w': 'fill', 'h': 267, 'bg': P('panel'), 'hover': P('hov'), 'hover_dy': -3, 'border': '{c.rc}', 'bw': 2, 'ring': '{c.glow}',
         'pad': [0, 11, 11, 11], 'gap': 0, 'anim': 'rise', 'anim_dy': 12, 'anim_ms': 240, 'anim_delay': 'c.delay', 'anim_key': 'vm.sig',
         'repeat': {'for': 'vm.cards', 'as': 'c', 'limit': 8}, **msg('detail', {'k': 'str:64'}, {'k': 'c.id'}), 'children': [box([
             HD('{c.label}', 13, '{c.rc}', 4, w='fill', h=26, text_align='center', pad=[5, 0, 0, 0]),
             box([sprite('{c.sprite}', '{c.item}', 'c.isItem', 128, x=38, y=-1), T('{c.cnt}', 17, '#ffffff', True, tshadow='2,2,#000000', right=8, bottom=4, **{'if': 'c.cnt'})],
                 w=204, h=126, bg='{vm.artTex}'),
             T('{c.name}', 18, '{c.rc}', True, w='fill', text_align='center', ellipsis=True, pad=[5, 0, 0, 0]),
             T('{c.sub}', 13, P('dim'), w='fill', text_align='center', ellipsis=True, pad=[1, 0, 0, 0]),
             box([tags('c.tags', 't', w='content')], w='fill', cross='center', pad=[5, 0, 0, 0], **{'if': '!c.isItem'}),
             box([], h='fill'),
             row([T('{c.time}', 13, P('dim')), box([], w='fill'), T('{c.price}', 18, GOLD, True)], w='fill', h=32, pad=[0, 9, 0, 9], cross='center', bg=P('input'),
                 border=P('edge') + '8c' if False else P('edge'), bw=1)], w='fill', h='fill', gap=0),
             tipbox()]}
    return c


def browse():
    return box([{'type': 'grid', 'w': 946, 'min_col': 200, 'gap': 14, 'children': [card()]},
                T('No listings match. Try clearing a filter.', 18, P('dim'), x=0, y=30, **{'if': 'vm.noneText'})],
               x=0, y=0, w=946, h=548, anim='fade', anim_ms=200, anim_key='vm.viewKey', **{'if': 'vm.isBrowse'})


# ---------------------------------------------------------------- sell / sell items / mine
def price_box(bind, fid, action):
    inp = {'type': 'input', 'id': fid, 'bind': bind, 'hint': '', 'w': 'fill', 'h': 56, 'max_len': 12, 'bg': '#00000000', 'color': GOLD, 'font': NB, 'size': 32,
           'debounce_ms': 250, 'args': {'v': 'str:16'}, 'argv': {'v': '$' + bind}, 'msg': vmsg(action, v='{v}')}
    return row([T('$', 32, GOLD, True), inp], w='fill', h=58, gap=12, cross='center', pad=[0, 16, 0, 16], bg=P('input'), border=P('acc'), bw=1)


def calc(a, b, bcol=None):
    return row([T(a, 17, rich=True, bcolor=P('txt')), box([], w='fill'), T(b, 17, bcol or P('txt'), True)], w='fill', pad=[4, 0, 4, 0])


def sell_mon():
    prow = {'type': 'button', 'w': 'fill', 'h': 68, 'pad': [0, 10, 0, 10], 'bg': '{r.bg}', 'hover': '{r.hover}', 'border': '{r.border}', 'bw': 1, 'opacity': 'r.op',
            'enabled': 'r.ok', 'repeat': {'for': 'vm.party', 'as': 'r', 'limit': 6}, **msg('sellsel', {'n': 'int:0:5'}, {'n': 'r.n'}), 'children': [row([
                sprite('{r.sprite}', 'minecraft:air', 'r.isEmpty', 64), box([T('{r.name}', 19, '{r.color}', True), T('{r.meta}', 13, P('dim'), pad=[2, 0, 0, 0])], gap=0)],
                gap=12, cross='center', h='fill')]}
    party = box([H('Your party'), box([prow], w='fill', gap=6)], x=0, y=0, w=440, h=548, pad=[18, 18, 18, 18], bg=P('panel'), border=P('edge'), bw=1, clip=True)
    body = box([
        row([sprite('{vm.sm.sprite}', 'minecraft:air', 'vm.no', 128),
             box([T('{vm.sm.name}', 26, bold=True), T('{vm.sm.meta}', 16, P('dim'), wrap=True, w='fill'), tags('vm.sm.tags', 't')], w='fill', gap=2)], w='fill', gap=18, cross='center', pad=[2, 0, 14, 0]),
        price_box('price', 'price', 'price'),
        T('{vm.sm.help}', 13, '{vm.sm.helpColor}', pad=[6, 0, 10, 0], min_w=1),
        calc('{vm.sm.tax}', '{vm.sm.receive}', GOLD), calc('Duration', '72 hours'),
        btn('List for sale', 'solid', 'list', w='fill', h=60, size=20, enabled='vm.sm.ok', pad=[0, 0, 0, 0])], w='fill', gap=0, **{'if': 'vm.sellPick'})
    form = box([H('Set your price'), T('Pick a Pokemon from your party.', 17, P('dim'), pad=[16, 0, 0, 0], **{'if': '!vm.sellPick'}), body],
               x=458, y=0, w=488, h=548, pad=[18, 22, 18, 22], bg=P('panel'), border=P('edge'), bw=1, clip=True)
    return box([party, form], x=0, y=0, w=946, h=548, **{'if': 'vm.isSellMon'})


def sell_item():
    slot = {'type': 'button', 'w': 'fill', 'h': 49, 'bg': '{s.bg}', 'hover': P('hov'), 'border': '{s.border}', 'bw': 1, 'opacity': 's.op', 'enabled': 's.has',
            'repeat': {'for': 'r.cells', 'as': 's', 'limit': 9}, 'tooltip': ['{s.name}'], **msg('isel', {'n': 'int:0:40'}, {'n': 's.n'}), 'children': [
                {'type': 'item', 'item': '{s.item}', 'w': 32, 'h': 32, 'x': 8, 'y': 8, 'if': 's.has'},
                T('{s.cnt}', 13, '#ffffff', True, tshadow='2,2,#000000', right=3, bottom=1, **{'if': 's.cnt'})]}
    grid = box([row([slot], w='fill', gap=5, pad=['{r.gap}' if False else 0, 0, 0, 0], repeat={'for': 'vm.invRows', 'as': 'r', 'limit': 4})], w='fill', gap=5, pad=[12, 0, 0, 0])
    inv = box([H('Your inventory'), grid, T('Dimmed items cannot be sold on this server.', 13, P('dim'), pad=[12, 0, 0, 0])],
              x=0, y=0, w=520, h=548, pad=[18, 18, 18, 18], bg=P('panel'), border=P('edge'), bw=1, clip=True)
    qb = lambda text, k, d, w=42: {'type': 'button', 'text': text, 'font': O, 'size': 22 if w == 42 else 13, 'ls': 0 if w == 42 else 2, 'shadow': False, 'w': w, 'h': 42,
                                   'text_align': 'center', 'color': P('txt'), 'bg': P('btnbg'), 'hover': P('on'), 'border': P('edge'), 'bw': 1,
                                   **msg('qty', {'k': 'str:4', 'd': 'int:-1:1'}, {'k': "'%s'" % k, 'd': d})}
    seg = lambda text, k: {'type': 'button', 'text': text, 'font': O, 'size': 13, 'ls': 2, 'shadow': False, 'w': 'fill', 'pad': [8, 0, 8, 0], 'text_align': 'center',
                           'color': '{vm.si.%sColor}' % k, 'bg': '{vm.si.%sBg}' % k, 'hover': '{vm.si.%sBg}' % k, 'border': P('edge'), 'bw': 1,
                           **msg('imode', {'k': 'str:4'}, {'k': "'%s'" % k})}
    body = box([
        row([box([{'type': 'item', 'item': '{vm.si.item}', 'w': 96, 'h': 96, 'x': 16, 'y': 16}], w=128, h=128),
             box([T('{vm.si.name}', 26, bold=True, wrap=True, w='fill'), T('{vm.si.mod}', 13, P('dim'), ls=1, upper=True), T('{vm.si.have}', 16, P('dim'))], w='fill', gap=2)],
            w='fill', gap=18, cross='center', pad=[2, 0, 0, 0]),
        row([T('QUANTITY', 13, P('dim'), w=78), qb('-', 'step', '-1'),
             box([T('{vm.si.qty}', 24, bold=True, w='fill', text_align='center')], w=84, h=42, bg=P('input'), border=P('acc'), bw=1, pad=[6, 0, 0, 0]),
             qb('+', 'step', '1'), qb('ALL', 'all', '0', 62)], gap=8, cross='center', pad=[14, 0, 0, 0]),
        row([seg('PRICE FOR ALL', 'all'), seg('PRICE EACH', 'each')], w='fill', pad=[12, 0, 10, 0]),
        price_box('iprice', 'iprice', 'iprice'),
        T('{vm.si.help}', 13, '{vm.si.helpColor}', pad=[6, 0, 6, 0], min_w=1),
        calc('Total price', '{vm.si.totalVal}', GOLD), calc('{vm.si.tax}', '{vm.si.receive}', GOLD), calc('Duration', '72 hours'),
        btn('List for sale', 'solid', 'ilist', w='fill', h=54, size=20, enabled='vm.si.ok', pad=[0, 0, 0, 0])], w='fill', gap=0, **{'if': 'vm.itemPick'})
    form = box([H('Set your price'), T('Pick an item from your inventory.', 17, P('dim'), pad=[16, 0, 0, 0], **{'if': '!vm.itemPick'}), body],
               x=538, y=0, w=408, h=548, pad=[18, 22, 18, 22], bg=P('panel'), border=P('edge'), bw=1, clip=True)
    return box([inv, form], x=0, y=0, w=946, h=548, **{'if': 'vm.isSellItem'})


def mine():
    lrow = row([sprite('{m.sprite}', '{m.item}', 'm.isItem', 64),
                box([T('{m.name}', 20, bold=True, ellipsis=True, w='fill'), T('{m.price}', 18, GOLD, True, pad=[2, 0, 0, 0]), T('{m.status}', 13, '{m.statusColor}')], w='fill', gap=0),
                {**btn('{m.actText}', 'ghost', '@', h=38, size=13, pad=[0, 14, 0, 14], enabled='m.canAct'),
                 **msg('{m.act}' if False else 'mineact', {'k': 'str:64', 'a': 'str:12'}, {'k': 'm.id', 'a': 'm.act'})}],
               w='fill', h=92, gap=14, cross='center', pad=[0, 12, 0, 12], border=P('edge'), bw=1, repeat={'for': 'vm.mine', 'as': 'm', 'limit': 12})
    lst = box([H('My listings'), box([lrow], w='fill', gap=10), T('You have nothing listed.', 15, P('dim'), pad=[8, 0, 8, 0], **{'if': 'vm.noMine'})],
              x=0, y=0, w=610, h=548, pad=[18, 20, 18, 20], bg=P('panel'), border=P('edge'), bw=1, clip=True)
    srow = box([T('{s.text}', 14, rich=True, bcolor=GOLD, wrap=True, w='fill')], w='fill', pad=[9, 0, 9, 0], border=P('edge'), bw=1, bsides='b',
               repeat={'for': 'vm.sales', 'as': 's', 'limit': 12})
    sales = box([H('Recent sales'), box([srow], w='fill'), T('No sales yet.', 15, P('dim'), pad=[8, 0, 8, 0], **{'if': 'vm.noSales'})],
                x=628, y=0, w=318, h=548, pad=[18, 18, 18, 18], bg=P('panel'), border=P('edge'), bw=1, clip=True)
    return box([lst, sales], x=0, y=0, w=946, h=548, **{'if': 'vm.isMine'})


# ---------------------------------------------------------------- overlays
def detail():
    ivrow = row([T('{i.stat}', 14, P('dim'), w=36), box([{'type': 'bar', 'w': 260, 'h': 12, 'value': 'i.v', 'max': '31', 'fill': '{i.color}', 'fill_bg': P('input')}], w=260),
                 T('{i.v}', 14, w=24, text_align='right')], h=22, gap=10, cross='center', repeat={'for': 'vm.d.ivs', 'as': 'i', 'limit': 6})
    moves = row([box([T('{l.text}', 14)], w=256, pad=[6, 10, 6, 10], border=P('edge'), bw=1, repeat={'for': 'vm.d.lines', 'as': 'l', 'limit': 4})], w=520, gap=8, wrap=True)
    lines = box([T('{l.text}', 14, '{l.color}', lh=21, bold=False, wrap=True, w='fill', repeat={'for': 'vm.d.lines', 'as': 'l', 'limit': 12})],
                w=580, pad=[9, 13, 9, 13], border=P('edge'), bw=1, bg='#00000059')
    info = box([T('{vm.d.name}', 34, bold=True, wrap=True, w=580), T('{vm.d.meta}', 15, P('dim'), wrap=True, w=580, pad=[4, 0, 8, 0]), tags('vm.d.tags', 't'),
                box([H('Stats (IV)', pad=[16, 0, 8, 0]), box([ivrow]), H('Moves', pad=[16, 0, 8, 0]), moves], **{'if': 'vm.d.isMon'}),
                box([H('Item details', pad=[16, 0, 8, 0]), lines], **{'if': '!vm.d.isMon'}),
                T('{vm.d.extra}', 13, P('dim'), pad=[8, 0, 0, 0])], x=330, y=24, w=580, gap=0)
    buy = row([box([T('PRICE', 16, P('dim')), T('{vm.d.price}', 38, GOLD, True, lh=41.8), T('{vm.d.time}', 16, P('dim'))], gap=0), box([], w='fill'),
               row([btn('{vm.d.buyText}', 'solid', 'buy', w=280, h=58, size=21, enabled='vm.d.buyOk'), btn('< Back', 'ghost', 'back')], gap=14, cross='center')],
              x=32, y=590 - 26 - 108, w=876, h=108, pad=[14, 22, 14, 22], cross='center', bg=P('panel'), border=P('edge'), bw=1)
    return box([box([sprite('{vm.d.sprite}', '{vm.d.item}', 'vm.d.isItem', 256)], x=44, y=56, w=256, h=256), info, buy],
               w=940, h=590, bg=P('panel'), anim='pop', anim_ms=180, **{'if': 'vm.showDetail'})


def confirm():
    return box([H('{vm.cf.title}', pad=[0, 0, 16, 0]), T('{vm.cf.text}', 23, lh=31, wrap=True, w='fill', pad=[0, 0, 10, 0]),
                box([T('{l.text}', 14, P('dim'), lh=22.4, wrap=True, w='fill', repeat={'for': 'vm.cf.math', 'as': 'l', 'limit': 6})], w='fill', pad=[0, 0, 22, 0]),
                row([btn('{vm.cf.yes}', 'solid', 'yes'), btn('{vm.cf.no}', 'ghost', 'no')], gap=14, cross='center')],
               w=540, pad=[26, 28, 26, 28], bg=P('panel'), anim='pop', anim_ms=180, **{'if': 'vm.showConfirm'})


def settings():
    theme = {'type': 'button', 'w': 'fill', 'h': 58, 'pad': [7, 9, 7, 9], 'bg': P('btnbg'), 'hover': P('on'), 'border': '{t.border}', 'bw': '{t.bw}' if False else 2,
             'repeat': {'for': 'vm.themes', 'as': 't', 'limit': 6}, **msg('pref', {'k': 'str:8', 'v': 'str:16'}, {'k': "'theme'", 'v': 't.k'}), 'children': [box([
                 HD('{t.name}', 14, P('txt'), 2, upper=True), box([], h='fill'),
                 row([box([], w=22, h=12, bg='{t.c0}', border='#00000080', bw=1), box([], w=22, h=12, bg='{t.c1}', border='#00000080', bw=1),
                      box([], w=22, h=12, bg='{t.c2}', border='#00000080', bw=1)], gap=4)], w='fill', h='fill', gap=0)]}
    sw = lambda k, on: {'type': 'button', 'w': 56, 'h': 28, 'bg': P('input'), 'hover': P('input'), 'border': '{vm.%sBorder}' % k, 'bw': 1,
                        **msg('pref', {'k': 'str:8', 'v': 'str:16'}, {'k': "'%s'" % k, 'v': "'toggle'"}), 'children': [
                            box([], x='vm.%sX' % k, y=4, w=20, h=20, bg='{vm.%sKnob}' % k)]}
    setrow = lambda title, small, ctl: row([box([T(title, 16), T(small, 12, P('dim'))], w='fill', gap=0), ctl], w='fill', pad=[10, 0, 10, 0], cross='center',
                                         border=P('edge'), bw=1, bsides='b')
    zoom = row([tbtn('−', msg('pref', {'k': 'str:8', 'v': 'str:16'}, {'k': "'zoom'", 'v': "'-10'"})), T('{$zoompct}%', 16, min_w=52, text_align='center'),
                tbtn('+', msg('pref', {'k': 'str:8', 'v': 'str:16'}, {'k': "'zoom'", 'v': "'10'"}))], gap=6, cross='center')
    panel = box([H('Settings'), H('Colour theme', pad=[6, 0, 10, 0]),
                 {'type': 'grid', 'w': 'fill', 'min_col': 140, 'gap': 10, 'pad': [4, 0, 12, 0], 'children': [theme]},
                 setrow('Window size', 'Also the − / + buttons in the title bar', zoom),
                 setrow('Rarity glow', 'A soft glow around uncommon and better cards', sw('glow', 'g')),
                 setrow('Hover tooltips', 'Details when you point at a card', sw('tips', 't')),
                 row([box([], w='fill'), btn('Done', 'solid', 'settings', {'k': 'str:8'}, {'k': "'close'"})], w='fill', pad=[18, 0, 0, 0])],
                w=560, pad=[24, 28, 24, 28], bg=P('panel'), border=P('edge'), bw=1, anim='pop', anim_ms=180)
    return box([panel], x=0, y=0, w=1200, h=720, bg='#040608c7', align='center', cross='center', z=700, anim='fade', anim_ms=150, **{'if': 'vm.settings'})


def page():
    grid_lines = [box([], x='g.x', y=0, w=1, h=720, bg=P('grid'), repeat={'for': 'vm.gx', 'as': 'g', 'limit': 31}),
                  box([], x=0, y='g.y', w=1200, h=1, bg=P('grid'), repeat={'for': 'vm.gy', 'as': 'g', 'limit': 19})]
    main = box([browse(), sell_mon(), sell_item(), mine()], x=240, y=104, w=946, h=548)
    pager = row([btn('< Prev', 'ghost', 'page', {'d': 'int:-1:1'}, {'d': '-1'}, enabled='vm.canPrev'), T('{vm.pageLabel}', 14, P('dim'), ls=2),
                 btn('Next >', 'ghost', 'page', {'d': 'int:-1:1'}, {'d': '1'}, enabled='vm.canNext')], gap=28, cross='center')
    bottom = box([pager], x=240, y=662, w=946, h=44, align='center', cross='center', **{'if': 'vm.isBrowse'})
    sweepfx = {'type': 'goldsweep', 'x': 0, 'y': 0, 'w': 1200, 'h': 720, 'fill': '{vm.sweepKey}'}
    overlay = box([detail(), confirm(), sweepfx], x=0, y=0, w=1200, h=720, bg='#040608c7', align='center', cross='center', z=600, anim='fade', anim_ms=150, **{'if': 'vm.overlay'})
    sparksfx = {'type': 'sparks', 'x': 0, 'y': 0, 'w': 1200, 'h': 720, 'text': '600,360', 'fill': '{vm.sparkKey}'}
    tick = {'type': 'spacer', 'id': 'tick', 'x': 0, 'y': 0, 'w': 0, 'h': 0, 'every_ms': 1000, 'args': {}, 'msg': vmsg('tick')}
    root = {'type': 'column', 'w': 1200, 'h': 720, 'bg': 'gradv:{vm.p.bg2},{vm.p.bg1}', 'border': P('edge'), 'bw': 2, 'clip': True,
            'children': grid_lines + [titlebar(), toptabs(), rail(), main, bottom, overlay, sparksfx, settings(), tick]}
    return {'v': 2, 'id': 'auction', 'channel': 'snobbleauctions', 'hello': 'ready', 'bye': True, 'title': 'Auction House', 'width': 1200, 'height': 720,
            'fit': [1622, 973], 'state': {'zoompct': 100}, 'nodim': True,
            'toast': {'bg': '#171d23', 'border': GREEN, 'border_bad': RED, 'color': GREEN, 'color_bad': RED, 'font': N, 'size': 16, 'y': 56, 'from': -22,
                      'radius': 0, 'pad_x': 20, 'pad_y': 10, 'bw': 1},
            'root': root}


d = page()
with open(os.path.join(OUT, 'auction.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, separators=(',', ':'))
print('wrote auction (%d bytes)' % os.path.getsize(os.path.join(OUT, 'auction.json')))
