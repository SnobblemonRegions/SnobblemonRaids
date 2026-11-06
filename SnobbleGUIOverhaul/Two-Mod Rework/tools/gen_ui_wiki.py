"""Native Snobblemon Wiki (wiki.json), a faithful copy of the original wiki/index.html + style.css + app.js.

The original fills the window: zoom = max(.4, height / 720 * zoom%) and the stage is laid out at window / zoom CSS pixels
("zoomfit": [720, 1, .4, 99] plus the page's own -/+ zoom, local "zoompct"). Header 76px, body grid 270px | 1fr | 310px
(270px | 1fr for an article or search), footer 58px. Fonts: Oswald (headings, buttons), Nunito (text), IBM Plex Mono (code).
Icons: the page's own 16x16 pixel icons (icons.js), exported to tex:wiki/icon_<name>.png. All text and navigation come from
WikiViews (server); inline **bold**, `code` and [[links]] arrive as rich-text markers U+E001/E002/E003.
The previous native version is gen_ui_wiki_v1.py.
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
INK, DIM, FAINT, GOLD = '#d9f1ee', '#79b8b4', '#5f9a98', '#f2c230'
PANE, PANE2, PANE3, LINE, EDGE, ACCENT = '#0a2a30', '#0d343a', '#12414a', '#18474e', '#04171b', '#ff6b7a'
H, N, NB, MONO = 'oswald', 'nunito', 'nunito_b', 'plex'
R = {'route': 'str:80'}
B1, B2, B3 = '', '', ''


def T(text, size=15, color=INK, font=N, **kw):
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


def go(route_expr, **kw):
    """a button that opens a wiki route"""
    d = {'type': 'button', 'args': R, 'argv': {'route': route_expr}, 'msg': {'action': 'go', 'route': '{route}'}}
    d.update(kw)
    return d


def tile(icon, c, size, art, **kw):
    """.tile: coloured square, 2px rgba(255,255,255,.28) border, the 16px icon drawn at `art` px in the middle"""
    off = (size - art) // 2
    d = box([box([], x=off, y=off, w=art, h=art, bg='tex:wiki/icon_{%s}.png' % icon, **{'if': icon})],
            w=size, h=size, bg='{%s}' % c, border='#ffffff47', bw=2)
    d.update(kw)
    return d


def scroll(id_, children, key, pad, **kw):
    d = {'type': 'scroll', 'id': id_, 'w': 'fill', 'h': 'fill', 'pad': pad, 'value': key, 'track': '#06222a', 'fill': '#1d6b73',
         'children': [box(children, w='fill')]}
    d.update(kw)
    return d


def lbl(text, icon=None, **kw):
    """.lbl: Oswald 13px, letter-spacing 3px, uppercase, faint (optional 16px group icon)"""
    kids = []
    if icon:
        kids.append(box([], w=16, h=16, bg='tex:wiki/icon_{%s}.png' % icon, **{'if': icon}))
        kids.append(box([], w=8, h=1, **{'if': icon}))
    kids.append(T(text, 13, FAINT, H, ls=3, upper=True, lh=20))
    kw.setdefault('h', 20)
    return row(kids, cross='center', **kw)


# ------------------------------------------------------------------ header
def header():
    dots = row([box([], w=12, h=12, radius=6, bg=c, border='#222222', bw=2) for c in ('#e0364a', '#f2c230', '#2fd36b')], gap=6)
    srch = row([{'type': 'input', 'id': 'search', 'bind': 'q', 'hint': 'Search pages, items, commands...', 'w': 'fill', 'h': 24, 'max_len': 60,
                 'font': N, 'size': 16, 'color': INK, 'hint_color': FAINT, 'debounce_ms': 300, 'args': {'q': 'str:64'}, 'argv': {'q': '$q'},
                 'msg': {'action': 'search', 'q': '{q}'}},
                T('/', 13.5, '#99cccc', MONO, pad=[0, 10, 0, 10], border='#1d6b73', bw=2, h=22, lh=18)],
               w='fill', h=42, bg='#06222a', border=EDGE, bw=2, pad=[0, 14, 0, 14], cross='center', gap=6)
    who = box([T('{vm.whoName}', 14, GOLD, lh=17.5), T('{vm.whoLine}', 14, '#bfe0dc', lh=17.5)], bg=PANE, border=EDGE, bw=2, pad=[6, 14, 6, 14],
              **{'if': 'vm.whoName'})
    close = {'type': 'button', 'id': 'close', 'close': True, 'h': 46, 'w': 102, 'bg': '#e9eef0', 'hover': '#ffffff', 'border': '#ffffff', 'bw': 2,
             'children': [box([], x=2, bottom=2, w=98, h=4, bg='#aaaabb'), T('X Close', 16, '#111111', H, ls=1.5, upper=True, x=0, y=11, w=102, lh=24, text_align='center')]}
    return row([box([], w=40, h=40, bg='tex:wiki/orb.png'),
                dots, T('SNOBBLEMON WIKI', 28, '#ffffff', H, ls=5, lh=41), box([srch], w='fill', pad=[0, 12, 0, 12]), who, close],
               w='fill', h=76, pad=[0, 22, 0, 22], gap=14, cross='center')


# ------------------------------------------------------------------ side nav
def side():
    hdr = lbl('{s.name}', icon='s.gicon', pad=[0, 0, 0, 6], **{'if': 's.hdr'})
    gap = box([], h=12, **{'if': 's.hdr && !s.first'})
    gap0 = box([], h=2, **{'if': 's.hdr && s.first'})
    nav = go('s.route', id='nav', w='fill', h=44, bg='{s.bg}', hover='{s.hv}', border='{s.border}', bw=2, **{'if': 's.nav'},
             children=[tile('s.icon', 's.c', 30, 16, x=10, y=7),
                       T('{s.name}', 16, INK, NB, x=50, y=11, w=166, lh=22, ellipsis=True),
                       T('{s.n}', 14, GOLD, H, right=10, y=11, lh=21)])
    sub = go('s.route', id='sub', w='fill', h=26, bg='{s.bg}', **{'if': 's.sub'},
             children=[box([], x=0, y=0, w=4, h=26, bg='{s.bar}'), T('{s.name}', 15, '{s.color}', N, x=50, y=3, w=186, lh=20, ellipsis=True)])
    items = box([gap, gap0, hdr, box([], h=4, **{'if': 's.hdr'}), nav, sub], w='fill', repeat={'for': 'vm.side', 'as': 's', 'limit': 120})
    return box([scroll('side', [items], 'vm.sideKey', [10, 13, 14, 13])], w=270, h='fill', bg=PANE, border=EDGE, bw=3, radius=6)


# ------------------------------------------------------------------ cards (home / mod)
PILLS = {'g': ('#12b45a', '#04210f'), 'b': ('#2ea8e8', '#04202e'), 'y': ('#f2c230', '#2a2000'), 'r': ('#e0364a', '#ffffff'), 'p': ('#9b6bd6', '#12062a'), '': ('#2a4146', '#bbccdd')}


def card(listexpr, prefix):
    def one(sel):
        bw, pad = (4, [12, 14, 36, 14]) if sel else (2, [12, 14, 36, 14])
        return {'type': 'button', 'id': prefix + ('_sel' if sel else '_card'), 'w': 'fill', 'min_h': 112, 'bg': PANE2, 'border': '#ffffff' if sel else LINE, 'bw': bw,
                'hover_border': '#ffffff' if sel else '#3fb0ba', 'hover_dy': -3, 'pad': pad,
                'args': {'k': 'str:80'}, 'argv': {'k': 'cd.sel'}, 'msg': {'action': 'sel', 'k': '{k}'},
                'repeat': {'for': listexpr, 'as': 'cd', 'limit': 80, 'if': 'cd.on' if sel else '!cd.on'},
                'children': [box([
                    row([tile('cd.icon', 'cd.c', 40, 32), T('{cd.title}', 18, INK, H, ls=0.5, w='fill', ellipsis=True, lh=26)], gap=10, w='fill', cross='center'),
                    T('{cd.text}', 14, '#8fb9b6', N, w='fill', wrap=True, lines=2, lh=18.9, pad=[7, 0, 0, 0])], w='fill'),
                    T('{cd.pill}', 12, '{cd.pillFg}', H, ls=1, upper=True, x=12, bottom=9, pad=[1, 8, 1, 8], bg='{cd.pillBg}', radius=4, lh=17, **{'if': 'cd.pill'})]}
    return [one(True), one(False)]


def main_home():
    groups = box([
        box([lbl('{g.name}', icon='g.gicon', pad=[14, 0, 5, 0], h=39, w='fill', bsides='b', border=LINE, bw=2), box([], h=8),
             {'type': 'grid', 'w': 'fill', 'min_col': 230, 'gap': 12, 'children': card('g.cards', 'h')}], w='fill',
            repeat={'for': 'vm.groups', 'as': 'g', 'limit': 20})], w='fill')
    return box([scroll('smain', [T('{vm.crumbs}', 13, FAINT, H, ls=1.5, upper=True, lh=20),
                                T('{vm.title}', 34, '#e8fffb', H, ls=1.5, upper=True, lh=51, pad=[4, 0, 2, 0]),
                                T('{vm.desc}', 15, DIM, N, w='fill', wrap=True, lh=21.75, rich=True, bcolor='#ffffff', ccolor='#7fe3f2', cfont=MONO, lcolor='#7fd7f0', pad=[2, 0, 12, 0]),
                                groups,
                                box([{'type': 'grid', 'w': 'fill', 'min_col': 230, 'gap': 12, 'children': card('vm.cards', 'm')}], w='fill', **{'if': 'vm.isMod'}),
                                T('No pages yet.', 15, DIM, pad=[20, 4, 20, 4], **{'if': 'vm.noPages'})],
                       'vm.scrollKey', [17, 21, 17, 21], anim='rise', anim_ms=160, anim_dy=6, anim_key='vm.scrollKey')],
               w='fill', h='fill', bg=PANE, border=EDGE, bw=3, radius=6, **{'if': 'vm.isHome || vm.isMod'})


# ------------------------------------------------------------------ quick look (detail pane)
def chip(listexpr, id_):
    return go('r.route', id=id_, text='{r.text}', font=H, size=14, color=INK, ls=0.3, shadow=False, h=29, pad=[2, 11, 2, 11], bg=PANE3, border='#1d6b73', bw=2,
              hover_border='#7fd7f0', hover_color='#ffffff', repeat={'for': listexpr, 'as': 'r', 'limit': 8})


def detail():
    d = 'vm.d'
    rows = row([T('{r.k}', 14, DIM, N, w='fill', lh=19), T('{r.v}', 14, INK, N, lh=19)], w='fill', bg=PANE2, pad=[5, 10, 5, 10], gap=8,
               repeat={'for': 'vm.d.rows', 'as': 'r', 'limit': 12})
    body = [
        row([tile('vm.d.icon', 'vm.d.c', 56, 48), box([T('{vm.d.title}', 24, INK, H, ls=1, upper=True, lh=27.6, w='fill', wrap=True), T('{vm.d.red}', 13, ACCENT, N, lh=18)], w='fill')],
            gap=12, cross='center', w='fill'),
        lbl('Overview', pad=[14, 0, 5, 0], h=39),
        T('{vm.d.overview}', 15, INK, N, w='fill', wrap=True, lh=21.75, rich=True, bcolor='#ffffff', ccolor='#7fe3f2', cfont=MONO, lcolor='#7fd7f0'),
        lbl('{vm.d.rowsLabel}', pad=[14, 0, 5, 0], h=39, **{'if': 'vm.d.rowsLabel'}),
        box([], h=5, **{'if': 'vm.d.contents'}),
        box([rows], w='fill', gap=5),
        lbl('Related', pad=[14, 0, 5, 0], h=39, **{'if': 'vm.d.hasRelated'}),
        row([chip('vm.d.related', 'chip')], w='fill', wrap=True, gap=6),
        box([], h=22),
        go('vm.d.open', id='open', text='{vm.d.openText}', font=H, size=18, color='#ffffff', ls=1.5, upper=True, shadow=False, h=42, pad=[5, 25, 5, 25],
           bg='#b5122a', hover='#d2162f', border=ACCENT, bw=3, shrink=True)]
    return box([scroll('sdetail', [box(body, w='fill')], 'vm.detailKey', [17, 19, 17, 19], anim='rise', anim_ms=160, anim_dy=6, anim_key='vm.scrollKey')],
               w=310, h='fill', bg=PANE, border=EDGE, bw=3, radius=6, **{'if': 'vm.isHome || vm.isMod'})


# ------------------------------------------------------------------ article
RICH = dict(rich=True, bcolor='#ffffff', ccolor='#7fe3f2', cfont=MONO, lcolor='#7fd7f0')
PCTS = list(range(5, 100, 5))


def table_cells(cellexpr, size, color_k, color_v, font, upper=False, ls=0.0):
    """one cell per column; the server gives each column a width in 5% steps (cl.p)"""
    out = []
    for p in PCTS:
        out.append(T('{cl.x}', size, '{cl.color}', font, w='%d%%' % p, wrap=True, lh=round(size * 1.45, 2), pad=[5, 8, 5, 8], upper=upper, ls=ls,
                     repeat={'for': cellexpr, 'as': 'cl', 'limit': 8, 'if': 'cl.p == %d' % p}, **RICH))
    return out


def blocks():
    b = 'b'
    tfilter = {'type': 'input', 'id': 'tfilter', 'bind': 'tf', 'hint': '{b.hint}', 'w': 320, 'h': 32, 'max_len': 40, 'font': N, 'size': 16, 'color': INK,
               'hint_color': FAINT, 'bg': '#06222a', 'border': EDGE, 'bw': 2, 'pad': [0, 12, 0, 12], 'debounce_ms': 250,
               'args': {'q': 'str:40'}, 'argv': {'q': '$tf'}, 'msg': {'action': 'tfilter', 'q': '{q}'}}
    kids = [
        T('{b.x}', 15.5, '#d2ecea', N, w='fill', wrap=True, lh=24.8, pad=[0, 0, 9, 0], **RICH, **{'if': "b.t == 'p' && !b.route"}),
        go('b.route', id='plink', w='fill', children=[T('{b.x}', 15.5, '#d2ecea', N, w='fill', wrap=True, lh=24.8, pad=[0, 0, 9, 0], **RICH)],
           **{'if': "b.t == 'p' && b.route"}),
        box([T('{b.x}', 24, GOLD, H, ls=1, upper=True, w='fill', lh=34, pad=[0, 0, 3, 0], bsides='b', border=LINE, bw=2)], w='fill', pad=[18, 0, 8, 0],
            **{'if': "b.t == 'h'"}),
        T('{b.x}', 18, '#e8fffb', H, ls=0.5, w='fill', lh=26, pad=[12, 0, 4, 0], **{'if': "b.t == 'h3'"}),
        row([T('{b.mark}', 15.5, '#d2ecea', N, w=22, lh=24.8, text_align='right', pad=[0, 8, 0, 0]),
             T('{b.x}', 15.5, '#d2ecea', N, w='fill', wrap=True, lh=24.8, **RICH)], w='fill', pad=[0, 0, 3, 0], **{'if': "b.t == 'li'"}),
        box([], h=7, **{'if': "b.t == 'listend'"}),
        box([tfilter], pad=[0, 0, 6, 0], **{'if': "b.t == 'filter'"}),
        row(table_cells('b.cells', 13, None, None, H, upper=True, ls=1.5), w='fill', bsides='b', border=LINE, bw=2, **{'if': "b.t == 'th'"}),
        row(table_cells('b.cells', 14.5, None, None, N), w='fill', bg='{b.bg}', bsides='b', border='#123c43', bw=1, **{'if': "b.t == 'tr'"}),
        box([], h=12, **{'if': "b.t == 'tableend'"}),
        row([box([], w=5, h='fill', bg='{b.bar}'),
             T('{b.x}', 15, '#d2ecea', N, w='fill', wrap=True, lh=22.5, pad=[8, 12, 8, 12], rich=True, bcolor='{b.key}', ccolor='#7fe3f2', cfont=MONO, lcolor='#7fd7f0')],
            w='fill', bg='{b.bg}', **{'if': "b.t == 'call'"}),
        box([], h=10, **{'if': "b.t == 'call'"}),
        T('{b.x}', 13.5, '#7fe3f2', MONO, w='fill', wrap=True, lh=18.9, pad=[9, 11, 9, 11], bg='#06222a', border='#1d6b73', bw=1, **{'if': "b.t == 'code'"}),
        box([], h=10, **{'if': "b.t == 'code'"}),
        {'type': 'grid', 'w': 'fill', 'min_col': 200, 'gap': 10, 'if': "b.t == 'cards'", 'children': [
            go('m.route', id='mini', w='fill', bg=PANE2, border=LINE, bw=2, hover_border='#3a8a92', pad=[10, 12, 10, 12], gap=10,
               repeat={'for': 'b.items', 'as': 'm', 'limit': 24},
               children=[tile('m.icon', 'm.c', 36, 32), box([T('{m.title}', 16, INK, H, ls=0.3, w='fill', lh=24, pad=[0, 0, 2, 0]),
                                                           T('{m.x}', 13.5, '#7fa9a8', N, w='fill', wrap=True, lh=17.55, **RICH)], w='fill')])]},
        box([], h=12, **{'if': "b.t == 'cards'"}),
    ]
    return box(kids, w='fill', repeat={'for': 'vm.blocks', 'as': 'b', 'limit': 500})


def article():
    crumbs = row([go('c.route', id='crumb', text='{c.text}', font=H, size=13, color='{c.color}', hover_color='{c.hv}', ls=1.5, upper=True, shadow=False, h=20,
                     repeat={'for': 'vm.crumbList', 'as': 'c', 'limit': 6})], gap=0, h=20)
    read = scroll('read', [crumbs,
                           T('{vm.title}', 40, INK, H, ls=1, upper=True, lh=44, w='fill', wrap=True, pad=[4, 0, 4, 0]),
                           T('{vm.lede}', 16, '#a8cfcb', N, w='fill', wrap=True, lh=24, pad=[0, 0, 10, 0], **RICH, **{'if': 'vm.lede'}),
                           blocks()],
                  'vm.scrollKey', [17, 25, 29, 25])
    info = row([T('{i.k}', 14, FAINT, N, w='fill', lh=19), T('{i.v}', 14, '#e8fffb', N, lh=19, text_align='right')], w='fill', pad=[3, 0, 3, 0],
               bsides='b', border='#123c43', bw=1, repeat={'for': 'vm.info', 'as': 'i', 'limit': 10})
    ibox = box([T('{vm.modName}', 15, INK, H, ls=2, upper=True, w='fill', text_align='center', bg=PANE3, pad=[5, 8, 5, 8], lh=22),
                box([tile('vm.modIcon', 'vm.modC', 64, 48)], w='fill', h=84, bg=PANE, align='center', cross='center'),
                box([info], w='fill', pad=[4, 10, 6, 10])], w='fill', bg=PANE2, border='#1d6b73', bw=2, pad=[2, 2, 2, 2])
    rlink = go('r.route', id='rlink', text='{r.text}', font=N, size=15, color='#7fd7f0', hover_color='#ffffff', shadow=False, w='fill', h=24,
               text_align='left', repeat={'for': 'vm.related', 'as': 'r', 'limit': 8})
    jump = T('{h}', 15, '#7fd7f0', N, w='fill', lh=24, repeat={'for': 'vm.headings', 'as': 'h', 'limit': 30})
    rail = box([scroll('rail', [ibox, lbl('On this page', pad=[14, 0, 5, 0], h=39, **{'if': 'vm.headings'}), jump,
                                lbl('Related', pad=[14, 0, 5, 0], h=39, **{'if': 'vm.related'}), rlink], 'vm.scrollKey', [14, 14, 14, 17])],
               w=270, h='fill', bg='#082228', bsides='l', border=EDGE, bw=3)
    return box([row([read, rail], w='fill', h='fill', anim='rise', anim_ms=160, anim_dy=6, anim_key='vm.scrollKey')],
               w='fill', h='fill', bg=PANE, border=EDGE, bw=3, radius=6, pad=[3, 3, 3, 3], **{'if': 'vm.isPage'})


def search():
    res = go('r.route', id='res', w='fill', bg=PANE2, border=LINE, bw=2, hover_border='#3a8a92', pad=[10, 14, 10, 14], gap=12,
             repeat={'for': 'vm.results', 'as': 'r', 'limit': 40},
             children=[tile('r.icon', 'r.c', 36, 32), box([T('{r.title}', 18, INK, H, ls=0.3, lh=26, w='fill'), T('{r.trail}', 13, FAINT, N, ls=1, lh=18),
                                                         T('{r.snip}', 14, '#8fb9b6', N, w='fill', wrap=True, lh=18.9, pad=[3, 0, 0, 0], rich=True, bcolor=GOLD,
                                                           rich_plain=True)], w='fill')])
    return box([scroll('sres', [T('{vm.crumbs}', 13, FAINT, H, ls=1.5, upper=True, lh=20),
                                  T('Search', 34, '#e8fffb', H, ls=1.5, upper=True, lh=51, pad=[4, 0, 2, 0]),
                                  T('{vm.desc}', 15, DIM, N, w='fill', wrap=True, lh=21.75, pad=[2, 0, 12, 0]),
                                  box([res], w='fill', gap=8)], 'vm.scrollKey', [17, 21, 17, 21], anim='rise', anim_ms=160, anim_dy=6, anim_key='vm.scrollKey')],
               w='fill', h='fill', bg=PANE, border=EDGE, bw=3, radius=6, **{'if': 'vm.isSearch'})


# ------------------------------------------------------------------ footer
def btn(text, id_, msg, **kw):
    d = {'type': 'button', 'id': id_, 'text': text, 'font': H, 'size': 16, 'color': '#cfeaf2', 'hover_color': '#ffffff', 'ls': 1.5, 'upper': True, 'shadow': False,
         'w': 96, 'h': 40, 'bg': PANE, 'hover': PANE3, 'border': EDGE, 'bw': 2, 'text_align': 'center', 'msg': msg}
    d.update(kw)
    return d


def footer():
    zb = lambda t, id_, d: {'type': 'button', 'id': id_, 'text': t, 'font': H, 'size': 20, 'color': '#7fe3f2', 'shadow': False, 'w': 36, 'h': 40, 'bg': PANE,
                            'hover': PANE3, 'border': EDGE, 'bw': 2, 'text_align': 'center', 'set': {'zoompct': '$zoompct %s 10' % d}}
    return row([btn('< Back', 'back', {'action': 'back'}, opacity='vm.backOp'),
                btn('Home', 'home', {'action': 'home'}),
                T('{vm.stat}', 14, '#99cccc', N, w='fill', h=40, lh=36, pad=[0, 16, 0, 16], bg=PANE, border=EDGE, bw=2, ellipsis=True),
                btn('< Prev', 'prev', {'action': 'step', 'dir': 'prev'}, opacity='vm.prevOp', **{'if': 'vm.isPage'}),
                btn('Next >', 'next', {'action': 'step', 'dir': 'next'}, opacity='vm.nextOp', **{'if': 'vm.isPage'}),
                row([zb('-', 'zminus', '-'), T('{$zoompct}%', 15, '#ffffff', H, ls=1, lh=40), zb('+', 'zplus', '+')], gap=8, cross='center')],
               w='fill', h=58, pad=[0, 14, 12, 14], gap=10, cross='center')


def page():
    body = row([side(), main_home(), detail(), article(), search()], w='fill', h='fill', pad=[0, 14, 12, 14], gap=14)
    root = box([header(), body, footer()], w='fill', h='fill', bg='gradv:#c8202f,#8f1424')
    return {'v': 2, 'id': 'wiki', 'channel': 'snobblewiki', 'hello': 'hello', 'bye': False, 'title': 'Snobblemon Wiki', 'width': 1280, 'height': 720,
            'zoomfit': [720, 1, 0.4, 99], 'state': {'q': '', 'tf': '', 'zoompct': 100}, 'root': root}


def clean(o):
    if isinstance(o, dict):
        return {k: clean(v) for k, v in o.items() if v is not None}
    if isinstance(o, list):
        return [clean(v) for v in o]
    return o


def check(d):
    """the server's rules: unique ids outside repeats, repeat limit 1..500"""
    seen, bad = set(), []
    def walk(n, rep):
        r = n.get('repeat')
        if r and not 1 <= r.get('limit', 1) <= 500:
            bad.append('limit %s' % r.get('limit'))
        rep = rep or bool(r)
        if n.get('id') and not rep:
            if n['id'] in seen:
                bad.append('duplicate id ' + n['id'])
            seen.add(n['id'])
        for k in n.get('children', []):
            walk(k, rep)
    walk(d['root'], False)
    if bad:
        raise SystemExit('INVALID: ' + ', '.join(bad))


if __name__ == '__main__':
    d = clean(page())
    check(d)
    with open(os.path.join(OUT, 'wiki.json'), 'w', encoding='utf8') as f:
        json.dump(d, f, separators=(',', ':'))
    print('wrote wiki (%d bytes)' % os.path.getsize(os.path.join(OUT, 'wiki.json')))
