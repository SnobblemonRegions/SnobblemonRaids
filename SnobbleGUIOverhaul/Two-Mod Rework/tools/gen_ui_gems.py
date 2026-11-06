"""Native Gem Shop (snobblegems_shop.json), a faithful copy of the original snobblegems/shop.html.

The original stage is 2000x1125, scaled to fit the window (min(w/2000, h/1125)) - here: "fit": [2000, 1125] in the same units.
Positions/sizes from its CSS; the cut-corner panels, cards and buttons are the page's own CSS rendered to textures (tex:snobblegems/*).
Headings, numbers and button labels use the page's own 5x7 pixel font (pxt/pxnum), sent with the layout as "pixfont".
The previous native version is gen_ui_gems_v1.py.
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
TXT, DIM, WHITE, ACC, ACC_L, GOLD = '#d4c8ee', '#8f82b8', '#fbf6ff', '#e08cf5', '#f6d2ff', '#ffd24a'
PRISM = 'grad:#ff9af0,#d65aea,#8a44f5,#4c7cff,#3ddcff,#b4f7ff'
F, FB = 'plex', 'plex_b'
_N = [0]

# the page's pixel font (PF letters + PD digits/symbols, PF wins), from shop.html
PF = {"A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"], "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
      "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"], "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
      "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"], "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
      "G": ["01110", "10001", "10000", "10111", "10001", "10001", "01111"], "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
      "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"], "J": ["00111", "00010", "00010", "00010", "00010", "10010", "01100"],
      "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"], "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
      "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"], "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
      "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"], "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
      "Q": ["01110", "10001", "10001", "10001", "10101", "10010", "01101"], "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
      "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"], "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
      "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"], "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
      "W": ["10001", "10001", "10001", "10101", "10101", "11011", "10001"], "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
      "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"], "Z": ["11111", "00001", "00010", "00100", "01000", "10000", "11111"],
      "0": ["01110", "10001", "10011", "10101", "11001", "10001", "01110"], "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
      "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"], "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
      "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"], "5": ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
      "6": ["00110", "01000", "10000", "11110", "10001", "10001", "01110"], "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
      "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"], "9": ["01110", "10001", "10001", "01111", "00001", "00010", "01100"],
      ",": ["00000", "00000", "00000", "00000", "00000", "01100", "01000"]}
PD_EXTRA = {".": ["0", "0", "0", "0", "0", "0", "1"], "+": ["00000", "00100", "00100", "11111", "00100", "00100", "00000"],
            "x": ["00000", "10001", "01010", "00100", "01010", "10001", "00000"], "-": ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
            "/": ["00001", "00010", "00010", "00100", "01000", "01000", "10000"]}


def nid(p='n'):
    _N[0] += 1
    return '%s%d' % (p, _N[0])


def T(text, size=16, color=TXT, bold=False, **kw):
    d = {'type': 'label', 'text': text, 'font': FB if bold else F, 'size': size, 'color': color, 'shadow': False}
    d.update(kw)
    return d


def P(text, px, color, gap=1, bold=False, upper=True, **kw):
    """pxt(text, px, color, gap, bold) / pxnum(text, px, color) (pxnum: gap 1, not uppercased)."""
    d = {'type': 'pixtext', 'text': text, 'size': px, 'color': color, 'ls': gap, 'bold': bold, 'upper': upper}
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
    d = {'type': 'column', 'x': x, 'y': y, 'w': w, 'h': h, 'bg': 'tex:snobblegems/%s.png' % name, 'children': children or []}
    d.update(kw)
    return d


def gem(size, **kw):
    d = {'type': 'image', 'pokemon': 'a:gems_gem', 'w': size, 'h': size}
    d.update(kw)
    return d


def price(val, px, color, gsize, gap):
    """priceHtml(): gem picture + the number in pixel digits."""
    return row([gem(gsize), P(val, px, color, upper=False)], gap=gap, cross='center')


def icon(var, x, y, size):
    return [{'type': 'item', 'if': '!%s.isArt' % var, 'item': '{%s.item}' % var, 'x': x, 'y': y, 'w': size, 'h': size},
            {'type': 'image', 'if': '%s.isArt' % var, 'pokemon': '{%s.art}' % var, 'x': x, 'y': y, 'w': size, 'h': size}]


def left():
    """#left (118, 219, 450x827): rows at top 84 + i*58 (.row 383x56, dashed bottom, 23px); cats at 372 + i*52 (.cat 402x48)."""
    def r(label, value_nodes, i):
        return row([T(label, 23, TXT), box([], w='fill'), row(value_nodes, gap=8, cross='center')],
                   x=34, y=84 + i * 58, w=383, h=56, cross='center', border='#3a2380', bw=1, dash=True, bsides='b')
    cat = {'type': 'button', 'x': 24, 'y': 'k.y', 'w': 402, 'h': 48, 'bg': '{k.bg}', 'hover': '{k.hover}', 'border': '{k.border}', 'bw': 1, 'pad': [0, 12, 0, 14],
           'repeat': {'for': 'vm.cats', 'as': 'k', 'limit': 9}, 'id': nid('cat'), 'args': {'k': 'str:48'}, 'argv': {'k': 'k.id'}, 'msg': vmsg('cat', k='{k}'),
           'children': [row([box([], w=9, h=9, bg='{k.dot}'), box([], w=12, h=1), T('{k.name}', 23, '{k.color}'), box([], w='fill'), T('{k.count}', 20, '{k.ncolor}')],
                            w='fill', h='fill', cross='center')]}
    return tex('left', 118, 219, 450, 827, [
        P('{vm.yourCur}', 3, PRISM, 2, True, x=34, y=38),
        r('Balance', [gem(26), P('{vm.balance}', 3, WHITE, upper=False)], 0),
        r('Earned today', [P('{vm.earned}', 3, WHITE, upper=False)], 1),
        r('Items for sale', [P('{vm.forSale}', 3, WHITE, upper=False)], 2),
        P('Categories', 3, PRISM, 2, True, x=34, y=318),
        cat,
        T('{vm.tip}', 17, DIM, lh=24, wrap=True, x=34, y=748, w=380)], anim='rise', anim_dy=18, anim_ms=550)


def card():
    """.card 198x198 at (off + col*220, row*220) inside #grid (91, 108); states from the CSS (card / hover / sel), .no = opacity .5."""
    c = {'type': 'button', 'x': 'c.x', 'y': 'c.y', 'w': 198, 'h': 198, 'bg': '{c.tex}', 'hover': '{c.htex}', 'opacity': 'c.op',
         'repeat': {'for': 'vm.cards', 'as': 'c', 'limit': 10}, 'id': nid('card'), 'args': {'k': 'str:48'}, 'argv': {'k': 'c.id'}, 'msg': vmsg('sel', k='{k}'),
         'children': [
             box([T('{c.timer}', 14, '#1a0a40', True, ls=0.5)], x=8, y=8, h=22, bg='{c.timerBg}', pad=[2, 8, 0, 8], **{'if': 'c.timer'}),
             T('★', 20, GOLD, x=14, y=40, **{'if': 'c.star'}),
             P('{c.qty}', 3, WHITE, upper=False, right=14, y=10, **{'if': 'c.qty'}),
         ] + icon('c', 67, 36, 64) + [
             T('{c.name}', 17, '{c.color}', lh=21, wrap=True, lines=2, text_align='center', x=8, y=112, w=182, h=46),
             row([gem(22), P('{c.price}', 3, '{c.priceColor}', upper=False)], x=0, y=160, w=198, h=26, gap=8, align='center', cross='center')]}
    return c


def right():
    pgb = lambda text, d, en: {'type': 'button', 'text': text, 'font': F, 'size': 24, 'color': ACC, 'shadow': False, 'w': 44, 'h': 40, 'border': '#6a3cc4', 'bw': 1,
                               'bg': '#e08cf50a', 'hover': '#e08cf524', 'disabled': '#e08cf50a', 'enabled': en, 'text_align': 'center', 'id': nid('pg'),
                               'args': {'d': 'int:-1:1'}, 'argv': {'d': d}, 'msg': vmsg('page', d='{d}')}
    detail = box([
        P('Select an item', 3, DIM, 2, x=0, y=36, **{'if': '!vm.hasSel'}),
        T('{vm.sel.name}', 28, '{vm.sel.color}', True, ls=1, x=0, y=6, **{'if': 'vm.hasSel'}),
        T('{vm.sel.desc}', 18, TXT, lh=24, wrap=True, x=0, y=52, w=800, **{'if': 'vm.hasSel'}),
        row([gem(34), P('{vm.sel.price}', 4, '{vm.sel.priceColor}', upper=False)], right=280, y=14, gap=12, cross='center', **{'if': 'vm.hasSel'}),
        T('{vm.sel.limit}', 17, '{vm.sel.limitColor}', text_align='right', right=280, y=62, **{'if': 'vm.hasSel'}),
        T('{vm.sel.ends}', 16, '{vm.sel.endsColor}', True, text_align='right', right=280, y=86, **{'if': 'vm.hasSel && vm.sel.ends'}),
        {'type': 'button', 'right': 0, 'y': 14, 'w': 250, 'h': 72, 'bg': '{vm.sel.buyTex}', 'hover': '{vm.sel.buyTex}', 'enabled': 'vm.sel.ok', 'id': 'buy',
         'msg': vmsg('quote'), 'args': {}, 'if': 'vm.hasSel', 'align': 'center', 'cross': 'center', 'children': [
             P('Buy', 4, '{vm.sel.buyColor}', 4, True)]}], x=36, y=544, w=1188, h=100)
    return tex('right', 625, 219, 1260, 666, [
        P('/ {vm.catName}', 3, PRISM, 2, True, x=36, y=26),
        T('{vm.catDesc}', 18, DIM, x=36, y=64),
        row([pgb('◀', '-1', 'vm.canPrev'), box([P('{vm.pageText}', 3, TXT, 1, upper=False)], min_w=80, cross='center'), pgb('▶', '1', 'vm.canNext')],
            right=32, y=24, gap=14, cross='center', **{'if': 'vm.paged'}),
        box([card()], x=91, y=108, w=1078, h=418, anim='rise', anim_dy=18, anim_ms=400, anim_key='vm.popKey'),
        T('{vm.emptyText}', 22, DIM, ls=4, text_align='center', x=0, y=170, w=1260, **{'if': 'vm.empty'}),
        detail], anim='rise', anim_dy=18, anim_ms=550, anim_delay=90)


def modal():
    def mrow(label, val, col, i):
        return row([T(label, 22, TXT), box([], w='fill'), row([gem(40), P(val, 4, col, upper=False)], gap=8, cross='center')],
                   x=40, y=236 + i * 56, w=680, h=52, cross='center', border='#3a2380', bw=1, dash=True, bsides='b')
    ok = {'type': 'button', 'x': 40, 'y': 462, 'w': 320, 'h': 70, 'bg': 'tex:snobblegems/mok.png', 'hover': 'tex:snobblegems/mok.png', 'id': 'confirm',
          'msg': vmsg('confirm'), 'args': {}, 'align': 'center', 'cross': 'center', 'children': [P('Confirm', 4, '#1a0a40', 3, True)]}
    cancel = {'type': 'button', 'x': 400, 'y': 462, 'w': 320, 'h': 70, 'bg': 'tex:snobblegems/mcancel.png', 'hover': 'tex:snobblegems/mcancel.png', 'id': 'cancel',
              'msg': vmsg('cancel'), 'args': {}, 'align': 'center', 'cross': 'center', 'children': [P('Cancel', 4, '#f6e9ff', 3, True)]}
    m = tex('modal', 620, 250, 760, 560, [
        P('Confirm purchase', 4, PRISM, 3, True, x=40, y=34),
        box(icon('vm.q', 14, 14, 80), x=48, y=100, w=110, h=110, border='#6a3cc4', bw=1, bg='#190c46'),
        T('{vm.q.name}', 28, '{vm.q.color}', True, ls=1, x=184, y=112, w=540, ellipsis=True),
        T('{vm.q.desc}', 17, DIM, lh=22, wrap=True, clip=True, x=184, y=156, w=540, h=70),
        mrow('Price', '{vm.q.price}', PRISM, 0), mrow('Your balance', '{vm.q.balance}', WHITE, 1), mrow('Balance after', '{vm.q.after}', PRISM, 2),
        T('{vm.q.held}', 15, DIM, x=40, y=408), ok, cancel], anim='popin', anim_ms=260)
    return box([m], x=0, y=0, w=2000, h=1125, bg='#08031ad6', z=600, **{'if': 'vm.modal'})


def page():
    chip = tex('chip', 1490, 104, 395, 62, [gem(40, x=22, y=11), P('{vm.balance}', 4, PRISM, upper=False, x=76, y=16), P('{vm.currency}', 2, DIM, 2, right=34, y=24)],
               anim='rise', anim_dy=18, anim_ms=550, anim_delay=50)
    close = {'type': 'button', 'x': 973, 'y': 927, 'w': 564, 'h': 120, 'bg': 'tex:snobblegems/close.png', 'hover': 'tex:snobblegems/close.png', 'id': 'close', 'close': True,
             'align': 'center', 'cross': 'center', 'children': [P('Close', 4, '#1a0a40', 4, True)], 'anim': 'rise', 'anim_dy': 18, 'anim_ms': 550, 'anim_delay': 180}
    banner = tex('banner', 400, 380, 1200, 260, [box([P('Purchased!', 8, PRISM, 4, True), T('{vm.bannerName}', 26, WHITE, True, ls=2, tshadow='0,0,#d65aeae6')],
                                                   w=1200, h=260, align='center', cross='center', gap=14)], anim='pop', anim_ms=420, z=500, **{'if': 'vm.banner'})
    burst = {'type': 'burst', 'x': 0, 'y': 0, 'w': 2000, 'h': 1125, 'text': '500,280', 'fill': '{vm.burstKey}', 'hint': 'tex:snobblegems/gem.png', 'z': 0}
    tick = {'type': 'spacer', 'id': 'tick', 'x': 0, 'y': 0, 'w': 0, 'h': 0, 'every_ms': 1000, 'args': {}, 'msg': vmsg('tick')}
    ping = {'type': 'spacer', 'id': 'ping', 'x': 0, 'y': 0, 'w': 0, 'h': 0, 'every_ms': 4000, 'args': {}, 'msg': {'channel': 'snobblegems', 'kind': 'ping'}}
    root = {'type': 'column', 'w': 2000, 'h': 1125, 'bg': 'tex:snobblegems/bg.png', 'children': [
        P('{vm.title}', 4, PRISM, 3, True, x=120, y=108),
        T('/ SNOBBLEMON REGIONS', 20, '#a698d0', ls=5, x=520, y=122),
        chip, left(), right(), close, box([], x=1884, y=130, w=12, h=12, bg='{vm.liveColor}'), banner, burst, modal(), tick, ping]}
    font = dict(PD_EXTRA)
    font.update(PF)
    return {'v': 2, 'id': 'snobblegems_shop', 'channel': 'snobblegems', 'hello': 'hello', 'bye': True, 'title': 'Gem Shop', 'width': 2000, 'height': 1125,
            'fit': [2000, 1125], 'pixfont': font,
            'toast': {'tex_ok': 'snobblegems/toast_ok.png', 'tex_bad': 'snobblegems/toast_bad.png', 'x': 600, 'y': 52, 'w': 800, 'h': 56, 'font': F, 'size': 21, 'ls': 1,
                      'color_ok': '#b5ffcc', 'color_bad': '#ffc2c9'},
            'root': root}


d = page()
with open(os.path.join(OUT, 'snobblegems_shop.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, separators=(',', ':'))
print('wrote snobblegems_shop (%d bytes)' % os.path.getsize(os.path.join(OUT, 'snobblegems_shop.json')))
