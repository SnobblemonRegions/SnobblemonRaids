"""Native CobbleTowers page (cobbletowers.json): a copy of the original cobbletowers/app.js "fd" screens (flat dark UI).

All sizes are the original's CSS at 1280x720 (--u = 12.8px) in page pixels; "zoomfit" [720, 1, ...] makes 720 page px = the window height, like the
original's vh/vw units. Views (vm.view): inter, lobby, draft, ... one root per view; modals (reward, vendor) sit over any view on a scrim.
Fonts: Outfit (OFL) as outfit (500), outfit_6, outfit_b (700), outfit_xb (800), outfit_bl (900). Art: tex:cobbletowers/* (server pack).
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
U = 12.8
INK, MUT, SOFT, FAINT, LINE = '#e9eefc', '#93a2c9', '#b3c0e0', '#4d5e90', '#2b3b68'
PRI, ACC, GO, BTN, BTNE = '#3f6fe6', '#7ea2ff', '#16a34a', '#15803d', '#0b5a2a'
WARN, BAD, SURF, SURF2, TRACK = '#d98a06', '#d6264e', '#16213f', '#1d2a50', '#2a3a66'
HOV, GLOW, DIS, DISE, AFG, ABG = '#3f5aa0', '#355190', '#2a3355', '#1e2644', '#f2c75c', '#3a2d0f'
GOLD2 = '#ddab3c'
EDGE = '#00000066'
F, F6, FB, FXB, FBL = 'outfit', 'outfit_6', 'outfit_b', 'outfit_xb', 'outfit_bl'
TEX = 'tex:cobbletowers/'


def u(k):
    return round(U * k, 2)


def T(text, size, color=INK, font=F, **kw):
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


def sp(**kw):
    d = {'type': 'spacer', 'w': 'fill'}
    d.update(kw)
    return d


def tmsg(kind, **kw):
    """a message on the cobbletowers channel: the backing mod's own action record (kind + fields)"""
    d = {'kind': kind}
    d.update(kw)
    return d


def vmsg(action, **kw):
    """a click the page's own server view answers (selection, paging, closing a modal)"""
    d = {'channel': 'snobble_view', 'action': action}
    d.update(kw)
    return d


# ---------------------------------------------------------------------------------------------------------- shell
def logo(big=False):
    ic = 41.2 if big else 37.1     # the rental page draws the sprite's visible 9 cells at the full 2.9u
    return row([box([], w=ic, h=ic, bg=TEX + 'logo_neutral.png'),
                row([T('COBBLE ', u(1.8), INK, FXB, lh=u(1.8), ls=0.46), T('TOWERS', u(1.8), ACC, FBL, lh=u(1.8), ls=0.46)], cross='center')],
               gap=u(.8), cross='center', shrink=True)


def top(crumb_b, crumb_t, right):
    """.dtop: logo + crumb + spacer + right-hand actions"""
    return row([logo(),
                row([T(crumb_b, u(1.3), INK, FXB, lh=u(1.3)), T(crumb_t, u(1.3), MUT, F6, lh=u(1.3))], cross='center', shrink=True)] + [sp()] + right,
               w='fill', gap=u(2), cross='center', pad=[u(1), u(2.4), u(1), u(2.4)], bg=SURF, bsides='b', border=LINE, bw=2, **{'min_h': 63})


def tl(text, id_, msg=None, **kw):
    d = {'type': 'button', 'id': id_, 'text': text, 'font': F6, 'size': u(1.15), 'lh': u(1.15), 'color': MUT, 'hover_color': INK, 'shadow': False,
         'pad': [u(.3), u(.6), u(.3), u(.6)], 'shrink': True, 'args': {}}
    if msg:
        d['msg'] = msg
    d.update(kw)
    return d


def xbtn(id_='close'):
    return {'type': 'button', 'id': id_, 'text': '×', 'font': F, 'size': u(1.5), 'color': MUT, 'hover_color': INK, 'shadow': False, 'w': u(2.8), 'h': u(2.8),
            'text_align': 'center', 'bg': SURF2, 'hover': SURF2, 'border': LINE, 'hover_border': HOV, 'bw': 2, 'radius': 18, 'close': True, 'args': {}, 'msg': tmsg('bye')}


def dbtn(text, id_, kind='pri', msg=None, dis=None, **kw):
    """.dbtn (green, "gh" = ghost, "pri" = blue) 19.2px / 800, 14px radius"""
    green = kind == 'green'
    ghost = kind == 'gh'
    d = {'type': 'button', 'id': id_, 'text': text, 'font': FXB, 'size': u(1.5), 'lh': u(1.5), 'shadow': False, 'shrink': True,
         'color': '#ffffff' if not ghost else INK, 'hover_color': '#ffffff' if not ghost else INK,
         'bg': BTN if green else SURF2, 'hover': '#1a9748' if green else '#26366a',
         'radius': u(1.1),
         'pad': [u(.8), u(1.8), u(.8), u(1.8)], 'args': {}}
    d['disabled'] = DIS
    if green:
        d.update({'ring': '4,#22c55e80', 'ring_pulse': 2400, 'ring_loops': 3})
    if not green:
        d.update({'border': LINE, 'hover_border': LINE, 'bw': 2})
    if msg:
        d['msg'] = msg
    if dis:
        d['enabled'] = '!(' + dis + ')' if False else dis
    d.update(kw)
    return d


def card(children, **kw):
    d = box(children, bg=SURF, border=LINE, bw=2, radius=17.92)
    d.update(kw)
    return d


def ch(title, label, **kw):
    return row([T(title, u(1.6), INK, FXB, lh=u(1.6)), sp(), T(label, u(1.05), MUT, FB, lh=u(1.05), ls=1.54, upper=True)] if label else [T(title, u(1.6), INK, FXB, lh=u(1.6))],
               w='fill', cross='center', pad=[u(1.2), u(1.4), u(.6), u(1.4)], **kw)


# ---------------------------------------------------------------------------------------------------------- intermission
def team_card():
    mon = row([box([], w=u(5), h=u(5) * 2 / 3, bg='{m.icon}'),
               box([row([T('{m.name}', u(1.2), INK, FB, lh=u(1.2)), sp(), T('Lv {m.level}', u(1), MUT, F6, lh=u(1))], w='fill', pad=[0, 0, u(.25), 0]),
                    {'type': 'bar', 'w': 'fill', 'h': u(.7), 'value': 'm.hp', 'max': '100', 'fill': '{m.hpc}', 'fill_bg': TRACK, 'radius': 9}], w='fill')],
              w='fill', gap=u(.7), cross='center', pad=[0, u(1.2), u(.7), u(1.2)], opacity='m.op', anim='drise', anim_delay='m.dl', anim_stable=True, repeat={'for': 'vm.party', 'as': 'm', 'limit': 6})
    head = row([T('Your team', u(1.6), INK, FXB, lh=u(1.6), w=82, wrap=True), sp(), T('After this floor', u(1.05), MUT, FB, lh=u(1.05), ls=1.54, upper=True, w=125.4, wrap=True)],
               w='fill', cross='center', pad=[u(1.2), u(1.4), u(.6), u(1.4)])
    return card([head,
                 T('No team to show.', 16, MUT, F, w='fill', text_align='center', pad=[u(2), u(2), u(2), u(2)], **{'if': '!vm.party'}),
                 mon], w=245, h='fill', **{'hover_set': {'hov': '-1'}})


def gems(g, on_expr):
    """the three risk diamonds"""
    return row([box([], w=14.7, h=14.7, bg=TEX + 'gem_%s.png' % g) for _ in range(1)], gap=0)


def fact(var):
    """.dpl: round sign badge + text"""
    return row([box([T('{%s.sign}' % var, u(1.2), '#ffffff', FXB, lh=u(1.2), w='fill', text_align='center')], w=u(1.8), h=u(1.8), radius=12, bg='{%s.bg}' % var, cross='center', align='center'),
                T('{%s.text}' % var, u(1.2), INK, F, lh=u(1.56), w='fill', wrap=True)],
               w='fill', gap=u(.5), cross='start')


def risk_row(var, wrap_expr=''):
    diamonds = row([box([], w=14.7, h=14.7, bg='{%s.g%d}' % (var, i)) for i in range(3)], gap=u(.3), shrink=True, **{'if': '%s.risk >= 0' % var})
    pill = T('{%s.pill}' % var, u(1), '{%s.pillFg}' % var, FB, lh=u(1), upper=True, shrink=True, bg='{%s.pillBg}' % var,
             radius=u(.5), pad=[u(.32), u(.7), u(.32), u(.7)], ls=0.38)
    dia2 = row([box([], w=14.7, h=14.7, bg='{%s.g%d}' % (var, i)) for i in range(3)], gap=u(.3), shrink=True, **{'if': '%s.risk >= 0' % var})
    pill2 = T('{%s.pill}' % var, u(1), '{%s.pillFg}' % var, FB, lh=u(1), upper=True, shrink=True, bg='{%s.pillBg}' % var,
              radius=u(.5), pad=[u(.32), u(.7), u(.32), u(.7)], ls=0.38)
    return box([row([diamonds, pill], gap=u(.6), cross='center', w='fill', **{'if': '!%s.stack' % var}),
                box([dia2, pill2], gap=u(.6), w='fill', **{'if': '%s.stack' % var})], w='fill')


def mod_card():
    c = {'type': 'button', 'id': 'card', 'w': 'fill', 'h': 'fill', 'bg': SURF, 'hover': SURF, 'border': '{c.edge}', 'hover_border': HOV, 'bw': 2, 'radius': 17.92, 'clip': True,
         'enabled': 'vm.canPick', 'args': {'i': 'int:0:9'}, 'argv': {'i': 'c.i'}, 'msg': vmsg('pick', i='{i}'),
         'hover_set': {'hov': 'c.i'}, 'repeat': {'for': 'vm.cards', 'as': 'c', 'limit': 3}, 'pad': [0, 0, 0, 0], 'children': [box([
             box([box([], w='fill', h='fill', bg='{c.art}'),
                  ], w='fill', h=101, clip=True),
             box([T('{c.name}', u(2), INK, FXB, lh=u(2.1), w='fill', wrap=True),
                  risk_row('c'),
                  box([fact('f')], w='fill', gap=u(.7), repeat={'for': 'c.facts', 'as': 'f', 'limit': 3}),
                  sp(w='fill', h='fill'),
                  row([T('{c.status}', u(1.15), MUT, F, lh=u(1.15)), sp(), T('{c.payout}', u(1.8), AFG, FXB, lh=u(1.8)), T(' payout', u(1), MUT, F6, lh=u(1), **{'if': 'c.payout'})],
                      w='fill', cross='end', bsides='t', border=LINE, bw=2, pad=[u(.8), 0, 0, 0], min_h=u(3))],
                 w='fill', h='fill', gap=u(.7), pad=[u(1), u(1.2), u(1.1), u(1.2)])], w='fill', h='fill')]}
    return c


def side_panel():
    """the selected / hovered card's detail (original: .dside)"""
    show = "c.i == $hov || ($hov < 0 && c.i == vm.cur)"
    body = box([
        box([], w='fill', h=104.4, bg='{c.wide}'),
        row([T('{c.title}', u(1.6), INK, FXB, lh=u(1.6)), sp(), T('{c.pill}', u(1), '{c.pillFg}', FB, lh=u(1), upper=True, ls=0.38, bg='{c.pillBg}', radius=u(.5), pad=[u(.32), u(.7), u(.32), u(.7)], shrink=True)],
            w='fill', cross='center', pad=[u(1.2), u(1.4), u(.6), u(1.4)]),
        box([dict(fact('l'), repeat={'for': 'c.lines', 'as': 'l', 'limit': 8})], w='fill', gap=u(.7), pad=[u(.6), u(1.4), u(1), u(1.4)], h='fill')],
        w='fill', h='fill', **{'if': show, 'repeat': {'for': 'vm.cards', 'as': 'c', 'limit': 3}})
    empty = T('Nothing to show.', 16, MUT, F, w='fill', text_align='center', pad=[u(2), u(2), u(2), u(2)], **{'if': '!vm.cards'})
    members = box([T('Party', u(1.05), MUT, FB, lh=u(1.05), ls=1.54, upper=True),
                   row([box([], w=u(1), h=u(1), radius=u(.5), bg='{m.dot}'), T('{m.name}', u(1.2), INK, F6, lh=u(1.2)), sp(), T('{m.state}', u(1), MUT, F6, lh=u(1))],
                       w='fill', gap=u(.6), cross='center', repeat={'for': 'vm.members', 'as': 'm', 'limit': 8})],
                  w='fill', gap=u(.5), pad=[u(1), u(1.4), u(1), u(1.4)], bsides='t', border=LINE, bw=2)
    return card([body, empty, members], w=315, h='fill', clip=True, **{'hover_set': {'hov': '-1'}})



# ---------------------------------------------------------------------------------------------------------- intermission route map (li)
def route_view():
    """the lane map: one column per floor (3 lanes), curved edges between the stops, the choosable stops on the first column"""
    now = {'type': 'button', 'id': 'rt_now', 'w': 217.6, 'bg': '{n.bg}', 'hover': '{n.bg}', 'border': '{n.k}', 'hover_border': '{n.k}', 'bw': 3, 'radius': 15.4, 'ring': '{n.ring}',
           'enabled': 'n.on', 'pad': [0, 0, 0, 0], 'args': {'i': 'int:0:9'}, 'argv': {'i': 'n.card'}, 'msg': vmsg('pick', i='{i}'), 'if': 'n.isNow', 'children': [
               row([box([box([], w=34, h=34, bg='{n.glyph}')], w=53.8, h=53.8, radius=12.8, bg='#ffffff22', align='center', cross='center'),
                    box([T('{n.name}', 16.64, '{n.fg}', FXB, lh=19, w='fill'), T('{n.sub}', 12.8, '{n.fg2}', F6, lh=16, w='fill')], w='fill', gap=2.6)],
                   w='fill', gap=10.2, cross='center', pad=[9, 9, 9, 9])]}
    ahead = box([box([box([], w=34, h=34, bg='{n.glyph}')], w=46, h=46, radius=12.8, bg='#16213f', border='{n.k}', bw=2.5, align='center', cross='center'),
                 T('{n.label}', 11.5, MUT, FB, lh=14, w=80, text_align='center')], w=80, gap=4, cross='center', **{'if': 'n.isAhead'})
    blocked = box([T('-', 14, FAINT, FB, lh=18, w=69, text_align='center')], w=69, h=69, radius=12.8, border=FAINT, bw=2.5, align='center', cross='center', **{'if': 'n.isBlocked'})
    lane = box([now, ahead, blocked], w='fill', h='fill', align='center', cross='center', repeat={'for': 'c.slots', 'as': 'n', 'limit': 3})
    col = box([T('{c.head}', 12.2, MUT, FB, lh=16, ls=0.9, upper=True, w='fill', text_align='center', pad=[10, 0, 0, 0]), box([lane], w='fill', h='fill')], w=80, w_expr='c.pw', h='fill',
              repeat={'for': 'vm.route.cols', 'as': 'c', 'limit': 8})
    edge = {'type': 'curve', 'x': 0, 'y': 0, 'w': 'fill', 'h': 'fill', 'text': '{e.t}', 'fill': '{e.col}', 'bw': 3, 'repeat': {'for': 'vm.route.edges', 'as': 'e', 'limit': 60}}
    inner = box([edge, row([col], w='fill', h='fill')], w=512, h='fill', pad=[0, 0, 0, 0])
    return box([box([inner], w='fill', h='fill', pad=[0, 8, 0, 90])], w='fill', h='fill', bg=SURF, border=LINE, bw=2, radius=17.92, **{'if': 'vm.route'})



def atlas_view():
    """yi(): the atlas map - a left list of the choosable rooms, the tower drawn as a shaft of rooms (3 lanes, rows bottom-up) with pipes between them"""
    opt = {'type': 'button', 'id': 'at_opt', 'w': 'fill', 'bg': '{n.bg}', 'hover': '{n.bg}', 'border': '{n.k}', 'hover_border': '{n.k}', 'bw': 3, 'radius': 15.4, 'ring': '{n.ring}',
           'enabled': 'n.on', 'pad': [0, 0, 0, 0], 'args': {'i': 'int:0:9'}, 'argv': {'i': 'n.card'}, 'msg': vmsg('pick', i='{i}'), 'repeat': {'for': 'vm.atlas.opts', 'as': 'n', 'limit': 3}, 'children': [
               row([box([box([], w=34, h=34, bg='{n.glyph}')], w=53.8, h=53.8, radius=12.8, bg='#ffffff22', align='center', cross='center'),
                    box([T('{n.name}', 16.64, '{n.fg}', FXB, lh=19, w='fill'), T('{n.sub}', 12.8, '{n.fg2}', F6, lh=16, w='fill')], w='fill', gap=2.6)],
                   w='fill', gap=10.2, cross='center', pad=[9, 9, 9, 9])]}
    leg = row([box([], w=14, h=14, bg='{l.glyph}'), T('{l.name}', 11, SOFT, F6, lh=14)], gap=5, cross='center', repeat={'for': 'vm.atlas.legend', 'as': 'l', 'limit': 14})
    left = box([opt, box([leg], w='fill', gap=3, pad=[8, 0, 0, 4])], w=232, h='fill', gap=12.8, align='center', pad=[0, 0, 0, 10])

    def layer(cond, col, lw):
        return {'type': 'curve', 'x': 0, 'y': 0, 'w': 'fill', 'h': 'fill', 'text': '{e.t}', 'fill': col, 'bw': lw, 'repeat': {'for': 'vm.atlas.edges', 'as': 'e', 'limit': 80}, 'if': cond}
    edges = [layer('true', '#0c0b10', 8)] + [layer("e.st == 'future'", '#5a4a3a', 3), layer("e.st == 'dead'", '#3a2e24', 3), layer("e.st == 'open'", '#b87333', 5), layer("e.st == 'taken'", '#ffb866', 5)]
    win = {'type': 'button', 'id': 'at_win', 'x': 'n.x', 'y': 'n.y', 'w': 34, 'h': 36, 'bg': '{n.bg}', 'hover': '{n.bg}', 'border': '{n.bd}', 'hover_border': '#ffffff', 'bw': 2.5,
           'radius': 17, 'ring': '{n.ring}', 'opacity': 'n.op', 'enabled': 'n.on', 'pad': [0, 0, 0, 0], 'args': {'i': 'int:0:9'}, 'argv': {'i': 'n.card'}, 'msg': vmsg('pick', i='{i}'),
           'repeat': {'for': 'vm.atlas.nodes', 'as': 'n', 'limit': 40}, 'children': [box([box([], w=20, h=20, bg='{n.glyph}')], w='fill', h='fill', align='center', cross='center')]}
    glow = box([], x='n.x', y='n.y', w=34, h=36, radius=17, bg='{n.kg}', anim='glow', anim_ms=2400, anim_stable=True, repeat={'for': 'vm.atlas.nodes', 'as': 'n', 'limit': 40}, **{'if': 'n.reach'})
    mark = box([], x='n.bx', y='n.by', w=14, h=14, bg=TEX + 'rd_check.png', repeat={'for': 'vm.atlas.nodes', 'as': 'n', 'limit': 40}, **{'if': 'n.done'})
    nm = T('{n.name}', 10.5, INK, FB, lh=13, x='n.lx', y='n.ly', w=80, text_align='center', repeat={'for': 'vm.atlas.nodes', 'as': 'n', 'limit': 40}, **{'if': 'n.cur'})
    shaft = box(edges + [glow, win, mark, nm], w=360, h=470, bg='#0d0c12', border='#2b2833', bw=3, radius=15.4)
    return box([row([left, box([shaft], w='fill', h='fill', align='center', cross='center')], w='fill', h='fill')], w='fill', h='fill', bg=SURF, border=LINE, bw=2, radius=17.92, **{'if': 'vm.atlas'})



def inter():
    hint = row([T('{vm.hint}', u(1.4), MUT, F6, lh=u(1.4)), T('{vm.until|countdownclock}', u(1.4), MUT, F6, lh=u(1.4), **{'if': 'vm.until'})], w='fill', pad=[0, u(.4), 0, u(.4)])
    middle = box([hint,
                  T('No modifier is on offer this floor. You can still visit the vendor, ready up or cash out.', 16, MUT, F, w='fill', h='fill', text_align='center', wrap=True,
                    bg=SURF, border=LINE, bw=2, radius=17.92, pad=[u(2), u(2), u(2), u(2)], **{'if': '!vm.cards'}),
                  route_view(),
                  atlas_view(),
                  row([mod_card()], w='fill', h='fill', gap=u(1.4), **{'if': 'vm.cards && !vm.route && !vm.atlas'})],
                 w='fill', h='fill', gap=u(1))
    body = row([team_card(), middle, side_panel()], w='fill', h='fill', gap=u(2), pad=[u(1.8), u(2.4), u(1.6), u(2.4)], bg=TEX + 'pattern.png', anim='dpop', **{'hover_set': {'hov': '-1'}})
    foot = row([dbtn('{vm.pickText}', 'confirm', 'green', msg=vmsg('confirmVote'), enabled='vm.canConfirm'),
                dbtn('Vendor', 'vendor', 'gh', msg=tmsg('IntermissionAction', action='VENDOR', argument='{n}'), args={'n': 'int:0:0'}, argv={'n': '0'}),
                dbtn('{vm.readyText}', 'ready', 'gh', msg=tmsg('IntermissionAction', action='{a}', argument='{n}'), enabled='vm.canReady',
                     args={'a': 'enum:READY|UNREADY', 'n': 'int:0:0'}, argv={'a': 'vm.readyAct', 'n': '0'}),
                dbtn('{vm.cashText}', 'cash', 'gh', msg=tmsg('IntermissionAction', action='{a}', argument='{n}'),
                     args={'a': 'enum:CASH_OUT|STAY', 'n': 'int:0:0'}, argv={'a': 'vm.cashAct', 'n': '0'})],
               w='fill', gap=u(1), cross='center', pad=[u(1.1), u(2.4), u(1.1), u(2.4)], bg=SURF, bsides='t', border=LINE, bw=2)
    return box([top('{vm.crumbB}', '{vm.crumbT}', [tl('Current run', 'currun', vmsg('currun')), xbtn()]), body, foot], w='fill', h='fill', **{'if': "vm.view == 'inter'"})



# ---------------------------------------------------------------------------------------------------------- lobby
def play(action, arg=''):
    return tmsg('PlayAction', action=action, argument=arg)


def dsm(text, id_, msg, kind='gh', **kw):
    """.dbtn.dsm: full-width 16px button"""
    d = dbtn(text, id_, kind, msg=msg, size=16, lh=20, text_align='center', w='fill', pad=[u(.63), u(1.4), u(.63), u(1.4)])
    d.pop('shrink', None)
    d.update(kw)
    return d


def tower_row():
    pbox = box([box([], w='fill', h='fill', bg='{t.pano}'), box([], x=0, y=0, w='fill', h='fill', bg='{t.dim}', radius=u(.7))],
               w=u(11), h=u(4.2), radius=u(.7), clip=True)
    return {'type': 'button', 'id': 'l_trow', 'w': 'fill', 'min_h': 68, 'bg': '{t.bg}', 'hover': '{t.bg}', 'border': '{t.bd}', 'hover_border': '{t.hbd}', 'bw': 2, 'radius': 17.92,
            'enabled': 'vm.lob.canChoose', 'opacity': 't.op', 'args': {'id': 'str:64'}, 'argv': {'id': 't.id'}, 'msg': play('SELECT_TOWER', '{id}'),
            'repeat': {'for': 'vm.lob.towers', 'as': 't', 'limit': 12}, 'pad': [0, 0, 0, 0], 'children': [box([
                row([pbox, box([T('{t.name}', u(1.5), INK, FXB, lh=u(1.5), w='fill', wrap=True),
                                T('{t.sub}', 14.72, MUT, F, lh=u(1.15), w='fill', pad=[u(.1), 0, 0, 0])], w='fill')],
                    w='fill', gap=u(1), cross='center')], w='fill', pad=[u(.4), u(.4), u(.4), u(.4)])]}


def seat():
    av = box([T('{s.av}', u(1.5), '#ffffff', FXB, lh=u(1.5))], w=43.5, h=43.5, radius=22, bg='#4aa3d6', align='center', cross='center')
    head = row([av, box([T('{s.name}', u(1.5), INK, FXB, lh=u(1.65), w='fill'), T('{s.sub}', 14.08, MUT, F, lh=u(1.1) * 1.3, w='fill')], w='fill'),
                T('{s.pill}', 14.08, '{s.pillFg}', FB, lh=14.08, bg='{s.pillBg}', radius=25.6, pad=[u(.3), u(.9), u(.3), u(.9)], shrink=True, **{'if': 's.pill'})],
               w='fill', gap=u(.9), cross='center')
    party = row([box([], w=u(4.6), h=u(4.6) * 2 / 3, bg='{p.icon}', opacity='p.op', repeat={'for': 's.party', 'as': 'p', 'limit': 6})],
                w='fill', gap=u(.2), **{'if': 's.party'})
    note = T('{s.note}', 14.08, MUT, F, lh=u(1.1) * 1.3, w='fill', wrap=True, **{'if': 's.note'})
    inv = row([{'type': 'input', 'id': 'l_inv', 'bind': 'inv', 'hint': 'Player name', 'hint_color': '#757575', 'w': 'fill', 'h': 34.1, 'max_len': 16, 'font': FB if False else F6,
                'size': 15.36, 'bg': SURF, 'border': LINE, 'bw': 2, 'radius': 11.52, 'color': INK, 'pad': [0, u(1), 0, u(1)]},
               dbtn('Invite', 'l_invite', 'green', msg=play('INVITE', '{q}'), size=16, lh=16, args={'q': 'str:16'}, argv={'q': '$inv'}, text_align='center',
                    pad=[u(.78), u(1.4), u(.78), u(1.4)], radius=14.08)],
              w='fill', gap=u(.6), cross='center', pad=[u(.5), 0, 0, 0], **{'if': 's.invite'})
    full = box([head, party, note], w='fill', gap=u(.7), **{'if': 's.kind == 1'})
    open_ = box([T('Open seat', 15.36, MUT, F6, lh=20, w='fill', text_align='center'), inv], w='fill', gap=u(.7), cross='center', **{'if': 's.kind == 0'})
    return box([full, open_], w='fill', bg='{s.bg}', border='{s.bd}', bw=2, radius=15.36, pad=[u(.9), u(1.1), u(.9), u(1.1)], min_h=u(6),
               anim='dslide', repeat={'for': 'vm.lob.seats', 'as': 's', 'limit': 4})


def mode_btn():
    return {'type': 'button', 'id': 'l_mode', 'text': '{b.name}', 'font': F6, 'size': 15.36, 'lh': 19.2, 'color': '{b.fg}', 'hover_color': '{b.fg}', 'shadow': False, 'shrink': True,
            'bg': '{b.bg}', 'hover': '{b.bg}', 'radius': 12.8, 'pad': [u(.55), u(1.1), u(.55), u(1.1)], 'enabled': 'vm.lob.modeOn',
            'args': {'id': 'str:32'}, 'argv': {'id': 'b.id'}, 'msg': play('SET_PLAYLIST', '{id}'), 'repeat': {'for': 'vm.lob.modes', 'as': 'b', 'limit': 12}}


def lobby():
    towers = card([ch('Tower', '{vm.lob.chooseLabel}'),
                   {'type': 'scroll', 'id': 'l_towers', 'w': 'fill', 'h': 'fill', 'pad': [u(.4), u(.6), u(.6), u(.2)], 'children': [box([tower_row()], w='fill', gap=u(.9))]}], w=315.2, h='fill')
    party = card([ch('Party', '{vm.lob.partyLabel}'),
                  {'type': 'scroll', 'id': 'l_seats', 'w': 'fill', 'h': 'fill', 'pad': [u(.4), u(1.2), u(1), u(1.2)], 'children': [box([seat()], w='fill', gap=u(.9))]},
                  box([T('{vm.lob.wait}', u(1.2), '#8fb2ff', F6, lh=u(1.5), w='fill', wrap=True, bg='#1c2d57', radius=u(1),
                         pad=[u(.8), u(1.1), u(.8), u(1.1)])], w='fill', pad=[0, u(1.2), u(1.2), u(1.2)], **{'if': 'vm.lob.wait'})], w=502, h='fill')
    lbl = T('Mode', u(1.05), MUT, FB, lh=u(1.05), ls=1.54, upper=True, pad=[u(.4), 0, 0, 0], **{'if': 'vm.lob.modes'})
    ctl = box([lbl,
               row([mode_btn()], w='fill', gap=6.4, wrap=True, **{'if': 'vm.lob.modes'}),
               dsm('Start at: {vm.lob.depth.label}', 'l_depth', play('SET_ASCENSION', '{n}'), enabled='vm.lob.depth.on', args={'n': 'int:0:999'}, argv={'n': 'vm.lob.depth.next'},
                   **{'if': 'vm.lob.depth'}),
               dsm('Confirm mode', 'l_confirm', play('CONFIRM_MODE'), 'green', **{'if': 'vm.lob.confirm'}),
               dsm('Draft your team', 'l_draft', tmsg('RentalDraftAction', action='OPEN', a=0, b=0), **{'if': 'vm.lob.draft'}),
               dsm('{vm.lob.readyText}', 'l_ready', play('{a}'), 'gh', bg='{vm.lob.readyBg}', hover='{vm.lob.readyBg}', color='{vm.lob.readyFg}', enabled='vm.lob.readyOn', args={'a': 'enum:READY|UNREADY'}, argv={'a': 'vm.lob.readyAct'},
                   **{'if': 'vm.lob.draft'}),
               row([dsm('Accept', 'l_accept', play('ACCEPT', '{h}'), 'green', args={'h': 'str:32'}, argv={'h': 'vm.lob.host0'}),
                    dsm('Decline', 'l_decline', play('DECLINE', '{h}'), 'gh', args={'h': 'str:32'}, argv={'h': 'vm.lob.host0'})], w='fill', gap=u(.8), **{'if': 'vm.lob.answer'}),
               dsm('Choose party', 'l_chooser', play('OPEN_CHOOSER'), **{'if': 'vm.lob.chooser'})],
              w='fill', h='fill', gap=u(.8), pad=[u(.4), u(1.4), u(1), u(1.4)])
    start = box([dbtn('{vm.lob.start.label}', 'l_start', 'green', msg=play('START'), enabled='vm.lob.start.on', w='fill', lh=24, text_align='center', pad=[u(.8), u(1.8), u(.8), u(1.8)], **{'if': 'vm.lob.start'}),
                 T('{vm.lob.start.text}', 14.08, MUT, F, lh=u(1.1) * 1.3, w='fill', **{'if': 'vm.lob.start'}),
                 dsm('{vm.lob.leave}', 'l_leave', play('LEAVE'), **{'if': 'vm.lob.leave'}),
                 dsm('Close', 'l_lclose', tmsg('bye'), close=True, **{'if': '!vm.lob.leave'})],
                w='fill', gap=u(.7), pad=[u(1.1), u(1.4), u(1.3), u(1.4)], bsides='t', border=LINE, bw=2)
    side = card([ch('Settings', '{vm.lob.modeName}'), ctl, start], w=350.2, h='fill')
    body = row([towers, party, side], w='fill', h='fill', gap=u(2), pad=[u(1.8), u(2.4), u(1.6), u(2.4)], bg=TEX + 'pattern.png', anim='dpop')
    return box([top('Lobby', '{vm.lob.host}', [tl('Back', 'l_back', vmsg('back')), xbtn('l_close')]), body], w='fill', h='fill', **{'if': "vm.view == 'lobby'"})



# ---------------------------------------------------------------------------------------------------------- hall
def hall_top():
    """.dtop with the Play/Trials/... pills (Back shown when the hall was entered from another screen)"""
    tab = {'type': 'button', 'id': 'h_tab', 'text': '{k.name}', 'font': F6, 'size': 17.28, 'lh': 21.6, 'color': '{k.fg}', 'hover_color': INK, 'shadow': False, 'shrink': True,
           'bg': '{k.bg}', 'hover': '{k.hov}', 'radius': 25.6, 'pad': [u(.5), u(1.5), u(.5), u(1.5)], 'args': {'t': 'str:16'}, 'argv': {'t': 'k.id'}, 'msg': vmsg('tab', t='{t}'),
           'repeat': {'for': 'vm.tabs', 'as': 'k', 'limit': 8}}
    pills = row([tab], bg='#080e1d', radius=25.6, pad=[4.5, 4.5, 4.5, 4.5], gap=u(.3), shrink=True)
    return row([logo(), pills, sp(),
                tl('Refresh', 'h_refresh', tmsg('TowerHallAction', action='refresh', argument='', revision='')),
                tl('Guide', 'h_guide', vmsg('go', v='guide')), tl('Settings', 'h_settings', vmsg('go', v='settings')), xbtn('h_close')],
               w='fill', gap=u(2), cross='center', pad=[u(1), u(2.4), u(1), u(2.4)], bg=SURF, bsides='b', border=LINE, bw=2, min_h=69.3)


def hall_row():
    pano = box([box([], w='fill', h='fill', bg='{t.pano}'), box([], x=0, y=0, w='fill', h='fill', bg='{t.dim}')], w=332.8, h=83.2, radius=12.8, clip=True)
    medals = row([T('{m.text}', 14.08, '{m.fg}', FB, lh=14.08, bg='{m.bg}', radius=25.6, pad=[u(.4), u(1.1), u(.4), u(1.1)], shrink=True,
                    repeat={'for': 't.medals', 'as': 'm', 'limit': 4})], gap=u(.4), wrap=True, w='fill')
    res = box([box([], x=46.1 * i, y=0, w=56.3, h=37.5, bg='{t.r%d}' % i) for i in range(3)], w=148.5, h=37.5)
    info = box([row([box([], w=33.3, h=33.3, bg='{t.medal}'), T('{t.name}', u(1.9), INK, FXB, lh=u(1.9), w='fill', wrap=True)], gap=u(.5), cross='center', w='fill'),
                T('{t.sub}', 14.72, MUT, F, lh=18.4, w='fill', wrap=True, pad=[u(.15), 0, u(.55), 0]), medals], w='fill', gap=0)
    return {'type': 'button', 'id': 'h_trow', 'w': 'fill', 'min_h': 106.2, 'bg': '{t.bg}', 'hover': '{t.bg}', 'border': '{t.bd}', 'hover_border': '{t.hbd}', 'bw': 2, 'radius': 17.92,
            'args': {'id': 'str:64'}, 'argv': {'id': 't.id'}, 'msg': vmsg('hsel', id='{id}'),
            'repeat': {'for': 'vm.hall.towers', 'as': 't', 'limit': 24}, 'pad': [0, 0, 0, 0], 'children': [box([
                row([pano, info, res], w='fill', gap=u(1.4), cross='center')], w='fill', pad=[9.5, 17.9, 9.5, 7.7])]}


def hall_team():
    mon = box([row([box([], w=u(6.4), h=u(6.4) * 2 / 3, bg='{m.icon}'),
                    box([T('{m.name}', u(1.3), INK, FB, lh=u(1.43), w='fill'), T('{m.sub}', u(1), MUT, F, lh=u(1), w='fill')], w='fill')], gap=u(.5), cross='center', w='fill')],
              w='fill', bg='gradv:#1d2a50,#16213f', border=LINE, bw=2, radius=u(1.1), pad=[u(.5), u(.7), u(.5), u(.7)], opacity='m.op',
              repeat={'for': 'vm.hall.party', 'as': 'm', 'limit': 6})
    return card([ch('Your team', '{vm.hall.teamLabel}'),
                 T('Put Pok\u00e9mon in your party to climb.', 16, MUT, F, w='fill', text_align='center', pad=[u(3), u(2), u(2), u(2)], **{'if': '!vm.hall.party'}),
                 box([mon], w='fill', gap=u(.8), pad=[u(.4), u(1.2), u(1.2), u(1.2)], **{'if': 'vm.hall.party'})], w=405.6, h='fill')


def hall_play():
    run = row([box([], w=u(4.6), h=u(4.6), bg='{vm.hall.runIcon}'),
               box([T('{vm.hall.runSmall}', u(1.1), '#cdd9ff', F, lh=u(1.1), w='fill'), T('{vm.hall.runTitle}', u(1.6), '#ffffff', FXB, lh=u(1.6), w='fill')], w='fill'),
               dsm('{vm.hall.runBtn}', 'h_run', tmsg('TowerHallAction', action='{a}', argument='', revision=''), 'green', enabled='vm.hall.runOn', args={'a': 'enum:resume|lobby'},
                   argv={'a': 'vm.hall.runAct'}, w=None)],
              w='fill', gap=u(1.2), cross='center', bg='grad:#17307c,#1e3fa0', border=GOLD2, bw=4, radius=u(1.4), pad=[u(.9), u(1.4), u(.9), u(1.4)], **{'if': 'vm.hall.runSmall'})
    run['children'][2].pop('w', None)
    bar = row([dsm('{vm.hall.dailyText}', 'h_daily', vmsg('go', v='trial', k='daily'), enabled='vm.hall.hasDaily', w=None),
               dsm('{vm.hall.weeklyText}', 'h_weekly', vmsg('go', v='trial', k='weekly'), enabled='vm.hall.hasWeekly', w=None),
               dsm('Lobby', 'h_lobby', tmsg('TowerHallAction', action='lobby', argument='', revision=''), w=None),
               sp(),
               dbtn('{vm.hall.climbText}', 'h_climb', 'green', msg=vmsg('go', v='tower', id='{id}'), args={'id': 'str:64'}, argv={'id': 'vm.hall.selId'}, enabled='vm.hall.canClimb', text_align='center', lh=24, pad=[u(.8), u(1.8), u(.8), u(1.8)])],
              w='fill', gap=u(1), cross='center', min_h=44.5, wrap=True)      # the original bar is flex-wrap:wrap: with long trial names the Climb button drops to a second line instead of running off the screen
    for c in bar['children'][:3]:
        c.pop('w', None)
        c['shrink'] = True
    rows = {'type': 'scroll', 'id': 'h_rows', 'w': 'fill', 'h': 'fill', 'children': [box([hall_row()], w='fill', gap=u(.9), pad=[u(.4), u(.2), u(.6), u(.2)])]}
    right = box([run, rows, bar], w='fill', h='fill', gap=u(.9))
    body = row([hall_team(), right], w='fill', h='fill', gap=u(2), pad=[u(1.8), u(2.4), u(1.6), u(2.4)], bg=TEX + 'pattern.png', anim='dpop')
    foot = row([T('{vm.hall.status}', 15.36, MUT, F, lh=19.2)], w='fill', pad=[u(1.1), u(2.4), u(1.1), u(2.4)], bg=SURF, bsides='t', border=LINE, bw=2, min_h=48.3)
    return box([body, foot], w='fill', h='fill', **{'if': "vm.tab == 'towers'"})



# ---------------------------------------------------------------------------------------------------------- trials / social tabs
def stat(icon, w, h, big, sub):
    return box([row([box([], w=46.1, h=h, bg=TEX + icon),
                     box([T(big, 25.6, INK, FXB, lh=26.9, w=95, wrap=True), T(sub, 14.72, MUT, F6, lh=19, w=95, wrap=True)], w=95)],
                    gap=12.8, cross='center')],
               w='fill', min_h=103.3, bg=SURF2, border=LINE, bw=4, radius=14.08, pad=[14.3, 15.4, 14.3, 5.4])


def trial_card():
    banner = box([box([], x=0, y=0, w=594.5, h=204.8, bg='{k.banner}'),
                  box([row([T('{k.name}', 30.72, INK, FXB, lh=30.72), sp(), T('{k.label}', 13.44, MUT, FB, lh=13.44, ls=1.54, upper=True, pad=[8, 0, 0, 0])],
                           w='fill', cross='center', pad=[0, 17.9, 0, 17.9])], x=0, y=153.6, w=594.5, h=51.2, bg='#080c1ccc')],
                 w=594.5, h=204.8, radius=16.64, clip=True)
    stats = row([stat('st_floors.png', 46.1, 26.9, '{k.floors}', '{k.floorsSub}'), stat('st_mode.png', 46.1, 38.4, '{k.mode}', 'mode'),
                 stat('st_enemy.png', 46.1, 41.9, '{k.enemy}', 'enemies')], w='fill', gap=11.5)
    for c in stats['children']:
        c['w'] = 178.5
    mods = box([T('Starting modifiers', 13.44, MUT, FB, lh=13.44, ls=1.54, upper=True),
                row([row([box([], w=21.8, h=18.1, bg=TEX + 'mod_gem.png'), T('{c}', 15.36, '#8fb2ff', FB, lh=15.36)], gap=7.7, cross='center', bg='#1c2d57', radius=25.6,
                         pad=[5.6, 11.5, 5.6, 11.5], shrink=True, repeat={'for': 'k.mods', 'as': 'c', 'limit': 6})], w='fill', gap=7.7, wrap=True)],
               w='fill', gap=7.7, **{'if': 'k.hasMods'})
    note = row([box([], w=30.7, h=24.1, bg=TEX + 'note_icon.png'), T('{k.note}', 15.36, AFG, F6, lh=19.2, w='fill', wrap=True)], w='fill', gap=12.8, cross='center',
               bg=ABG, radius=14.08, pad=[12.8, 15, 12.8, 15])
    btn = dbtn('Open briefing', 't_open', 'green', msg=vmsg('go', v='trial', k='{k}'), args={'k': 'str:16'}, argv={'k': 'k.kind'})
    body = box([stats, mods, note, sp(h='fill'), row([btn], w='fill')], w='fill', h='fill', gap=8.7, pad=[8.5, 17.9, 17.9, 17.9])
    return card([banner, body], w='fill', h='fill', repeat={'for': 'vm.hall.trials', 'as': 'k', 'limit': 2})


def trials_tab():
    cards = row([trial_card()], w='fill', h='fill', gap=25.6, pad=[u(1.8), u(2.4), 20.5, u(2.4)], bg=TEX + 'pattern.png', anim='dpop')
    return box([cards], w='fill', h='fill', **{'if': "vm.tab == 'trials'"})


def soc_card(i, title, sub, msg):
    inner = box([box([], x=0, y=0, w=594.5, h=184.7, bg=TEX + 'soc_art_%d.png' % i),
                 box([], x=31.7 - 1, y=183.3 - 92.3 - 1, w=140.8, h=140.8, bg=TEX + 'soc_ring_%d.png' % i),
                 T(title, 33.28, INK, FXB, lh=36.6, x=197.1, y=202.7, w=366),
                 T(sub, 19.2, MUT, F, lh=24, x=197.1, y=244.4, w=366)], w=594.5, h=288.8)
    return {'type': 'button', 'id': 'soc_%d' % i, 'w': 'fill', 'h': 'fill', 'bg': SURF, 'hover': SURF, 'border': LINE, 'hover_border': HOV, 'bw': 2, 'radius': 17.92,
            'pad': [0, 0, 0, 0], 'args': {}, 'msg': msg, 'children': [inner]}


def social_tab():
    a = row([soc_card(0, 'Party & invites', 'Gather your team', tmsg('TowerHallAction', action='lobby', argument='', revision='')),
             soc_card(1, 'Club lodge', 'Your community', vmsg('go', v='club'))], w='fill', h='fill', gap=25.6)
    b = row([soc_card(2, 'Spectate', 'Watch challengers', vmsg('go', v='spectate')),
             soc_card(3, 'Run codes', 'Scout an expedition', vmsg('go', v='scout'))], w='fill', h='fill', gap=25.6)
    return box([box([a, b], w='fill', h='fill', gap=25.6, pad=[u(1.8), u(2.4), 20.5, u(2.4)], bg=TEX + 'pattern.png', anim='dpop')], w='fill', h='fill', **{'if': "vm.tab == 'social'"})



# ---------------------------------------------------------------------------------------------------------- progress / ranks tabs
def prog_row():
    ic = box([box([], w=46.1, h=46.1, bg='{r.icon}', **{'if': '!r.crown'}), box([], w=43.5, h=28, bg=TEX + 'ico_crown.png', **{'if': 'r.crown'})], w=46.1, h=46.1, cross='center', align='center')
    body = box([T('{r.name}', 19.2, INK, FXB, lh=19.2, w='fill'), T('{r.sub}', 14.72, MUT, F, lh=18.4, w='fill')], w='fill')
    medal = box([box([], w=35.8, h=35.8, bg='{r.rank}')], w=35.8, h=35.8, **{'if': 'r.hasRank'})
    inner = row([ic, body, medal], w='fill', gap=11.5, cross='center', pad=[0, 7.1, 0, 7.1], min_h=56.3)

    def mk(id_, msg):
        return {'type': 'button', 'id': id_, 'w': 'fill', 'min_h': 60.3, 'bg': '{r.bg}', 'hover': '{r.bg}', 'border': '{r.bd}', 'hover_border': '{r.hbd}', 'bw': 2, 'radius': 17.92,
                'pad': [0, 0, 0, 0], 'args': {'id': 'str:64', 'k': 'str:8'}, 'argv': {'id': 'r.id', 'k': 'r.kind'}, 'msg': msg,
                'repeat': {'for': 'vm.prog.rows', 'as': 'r', 'limit': 8}, 'children': [inner]}
    return [mk('p_row', vmsg('rowpick', id='{id}', k='{k}'))]


def prog_tile():
    chip = T('{t.n}', 12.8, '{t.nfg}', FB, lh=12.8, bg='{t.nbg}', radius=6.4, pad=[3.2, 6.4, 3.2, 6.4], x=6.4, y=5.7, shrink=True)
    ready = T('Ready', 12.8, '#2a1a00', FXB, lh=12.8, bg='#f2c75c', radius=12.8, pad=[3.2, 7.7, 3.2, 7.7], x=96, y=6, shrink=True, **{'if': 't.ready'})
    pic = box([box([], w=48.6, h=48.6, bg='{t.icon}', **{'if': 't.item'}), box([], w=56.3, h=56.3, bg='{t.icon}', **{'if': 't.med'}), box([], w=11.5, h=11.5, bg=TEX + 'dgap.png', **{'if': 't.gap'})], x=0, y=36.1, w=153.4, h=56.3, cross='center', align='center')
    lab = T('{t.label}', 12.8, '{t.lfg}', F6, lh=14.72, x=0, y=99, w=153.4, text_align='center')
    tick = box([], x=126.5, y=118, w=19.2, h=19.2, bg=TEX + 'ico_check.png', **{'if': 't.done'})
    inner = box([chip, ready, pic, lab, tick], w=153.4, h=149.6)
    return {'type': 'button', 'id': 'p_tile', 'w': 157.4, 'h': 153.6, 'bg': '{t.bg}', 'hover': '{t.bg}', 'border': '{t.bd}', 'hover_border': '{t.hbd}', 'bw': 4, 'radius': 15.36,
            'pad': [0, 0, 0, 0], 'args': {'i': 'int:0:999'}, 'argv': {'i': 't.i'}, 'msg': vmsg('tsel', i='{i}'),
            'repeat': {'for': 'vm.prog.tiles', 'as': 't', 'limit': 120}, 'children': [inner]}


def progress_tab():
    cont = row([box([T('Ready to claim', 14.08, '{vm.prog.contFg}', F, lh=18, w='fill'), T('{vm.prog.ready}', 20.48, '#ffffff', FXB, lh=25, w='fill')], w='fill'),
                dsm('{vm.prog.laneBtn}', 'p_lane', vmsg('lane', v='{k}'), args={'k': 'str:8'}, argv={'k': 'vm.prog.laneTo'}, w=None, **{'if': 'vm.prog.laneBtn'})],
               w='fill', gap=15.4, cross='center', bg='{vm.prog.contBg}', border='{vm.prog.contBd}', bw=4, radius=17.92, pad=[11.5, 17.9, 11.5, 17.9], min_h=75.6)
    cont['children'][1].pop('w', None)
    cont['children'][1]['shrink'] = True
    more = box([T('More', 13.44, MUT, FB, lh=13.44, ls=1.54, upper=True, pad=[0, 0, 0, 2.5]),
                row([{k: v for k, v in dsm(t, 'p_more%d' % i, vmsg('go', v='more%d' % i), shrink=True).items() if k != 'w'} for i, t in enumerate(
                    ['Season & rewards', 'Cosmetics & titles', 'Contracts', 'Hall of Fame', 'Last run report', 'Mastery & boards'])], w='fill', gap=8, wrap=True)],
               w='fill', gap=7.7, pad=[10.2, 2.5, 0, 2.5])
    rows = box(prog_row() + [more], w='fill', gap=11.5, pad=[5.1, 2.6, 7.7, 2.6])
    left = box([cont, rows], w=322.1, h='fill', gap=11.5)
    hdr = row([box([], w=66.5, h=66.5, bg='{vm.prog.icon}', **{'if': '!vm.prog.crown'}), box([], w=66.5, h=43, bg=TEX + 'ico_crown.png', **{'if': 'vm.prog.crown'}),
               box([T('{vm.prog.title}', 28.16, INK, FXB, lh=28.16, w='fill'), T('{vm.prog.sub}', 15.36, MUT, F6, lh=21.5, w='fill')], w='fill'),
               T('{vm.prog.timer}', 14.08, AFG, FB, lh=14.08, bg=ABG, radius=20, pad=[3.8, 11.5, 3.8, 11.5], shrink=True, **{'if': 'vm.prog.timer'}),
               dsm('Claim all', 'p_claimall', tmsg('TrackAction', action='claim_all', lane='{l}', tower='{tw}', number=0), enabled='vm.prog.claimAll', w=None, shrink=True,
                   args={'l': 'str:8', 'tw': 'str:64'}, argv={'l': 'vm.prog.lane', 'tw': 'vm.prog.tower'})],
              w='fill', gap=15.4, cross='center', pad=[15.4, 17.9, 7.7, 17.9])
    hdr['children'][4].pop('w', None)
    prog = row([{'type': 'bar', 'w': 'fill', 'h': 15.4, 'value': 'vm.prog.pct', 'max': '100', 'fill': '#d9a63a', 'fill_bg': '#2a3a66', 'radius': 12},
                T('{vm.prog.progText}', 15.36, SOFT, FB, lh=19, shrink=True)], w='fill', gap=11.5, cross='center', pad=[0, 17.9, 0, 17.9], **{'if': 'vm.prog.hasProg'})
    tiles = {'type': 'scroll', 'id': 'p_tiles', 'w': 'fill', 'h': 'fill', 'children': [box([row([prog_tile()], w='fill', gap=11.5, wrap=True)], w='fill', pad=[5.1, 17.9, 12.8, 17.9])]}
    detail = row([box([T('{vm.prog.dTitle}', 17.5, INK, FB, lh=22, w='fill', **{'if': 'vm.prog.dTitle'}), T('{vm.prog.dBody}', 15.36, '{vm.prog.dFg}', F6, lh=20, w='fill', wrap=True),
                       T('{vm.prog.dNote}', 15.36, MUT, F6, lh=20, w='fill', wrap=True, **{'if': 'vm.prog.dNote'})], w='fill'),
                  dbtn('Claim', 'p_claim', 'gh', msg=tmsg('TrackAction', action='claim', lane='{l}', tower='{tw}', number='{n}'), enabled='vm.prog.claimOn',
                       args={'l': 'str:8', 'tw': 'str:64', 'n': 'int:0:9999'}, argv={'l': 'vm.prog.lane', 'tw': 'vm.prog.tower', 'n': 'vm.prog.dNum'})],
               w='fill', gap=15.4, cross='center', bg=SURF, bsides='t', border=LINE, bw=2, pad=[17.9, 17.9, 17.9, 17.9], min_h=84)
    right = card([hdr, prog, tiles, detail], w='fill', h='fill')
    body = row([left, right], w='fill', h='fill', gap=25.6, pad=[u(1.8), u(2.4), 20.5, u(2.4)], bg=TEX + 'pattern.png', anim='dpop')
    load = box([T('Loading your progress...', 16, MUT, FB)], w='fill', h='fill', cross='center', align='center', **{'if': '!vm.prog'})
    return box([box([body], w='fill', h='fill', **{'if': 'vm.prog'}), box([card([load], w='fill', h='fill')], w='fill', h='fill', pad=[u(1.8), u(2.4), 20.5, u(2.4)], bg=TEX + 'pattern.png', anim='dpop', **{'if': '!vm.prog'})],
               w='fill', h='fill', **{'if': "vm.tab == 'progress'"})


def rk_pick(side, id_):
    inner = box([box([], x=0, y=0, w=6.4, h=64.3, bg='{p.r}', **{'if': 'p.on'}),
                 T('{p.name}', 19.2, '{p.r}', FBL, lh=24, x=15.4, y=10.2, w=150),
                 T('{p.sub}', 13.44, MUT, F6, lh=16, x=15.4, y=38, w=160)], w=175.4, h=60.3)
    return {'type': 'button', 'id': id_, 'w': 179.4, 'h': 64.3, 'bg': '{p.rd}', 'hover': '{p.rd}', 'border': '{p.r}', 'hover_border': '{p.r}', 'bw': 2, 'radius': 10.24, 'opacity': 'p.op',
            'pad': [0, 0, 0, 0], 'args': {'l': 'str:64', 'r': 'str:64'}, 'argv': {'l': 'p.nl', 'r': 'p.nr'}, 'msg': tmsg('RanksRequest', left='{l}', right='{r}'),
            'repeat': {'for': 'vm.rk.' + side, 'as': 'p', 'limit': 4}, 'children': [inner]}


def rk_row():
    lv = row([sp(), T('{w.lt}', 24.32, '{w.lfg}', FXB, lh=30)], w='fill')
    lcol = box([lv, {'type': 'bar', 'w': 'fill', 'h': 9, 'value': 'w.lbar', 'max': '100', 'fill': '#00000000', 'fill_bg': '{w.lr}', 'radius': 4}], w='fill', gap=5)
    rcol = box([T('{w.rt}', 24.32, '{w.rfg}', FXB, lh=30), {'type': 'bar', 'w': 'fill', 'h': 9, 'value': 'w.rbar', 'max': '100', 'fill': '{w.rr}', 'fill_bg': '#00000000', 'radius': 4}], w='fill', gap=5)
    mid = T('{w.label}', 13.44, MUT, FB, lh=16, ls=1.34, upper=True, w=179.2, text_align='center')
    return box([row([lcol, mid, rcol], w='fill', gap=17.9, cross='center', pad=[7.7, 0, 7.7, 0], min_h=60.4)], w='fill',
               bsides='b', border='{w.bd}', bw=2, repeat={'for': 'vm.rk.rows', 'as': 'w', 'limit': 12})


def rk_champ(side):
    li = row([T('{c.name}', 16.64, INK, F, lh=20.8, w='fill'), T('{c.points}', 16.64, INK, FB, lh=20.8, shrink=True)], w='fill', pad=[6.4, 0, 6.4, 0], bsides='b', border=LINE, bw=1,
             repeat={'for': 'vm.rk.%sChamps' % side, 'as': 'c', 'limit': 6})
    return box([T('{vm.rk.%sName} champions' % side, 13.44, '{vm.rk.%sR}' % side, FB, lh=16, ls=1.54, upper=True, pad=[0, 0, 9, 0]),
                li, T('No clears yet.', 16, MUT, F, lh=20, pad=[25.6, 0, 0, 0], **{'if': 'vm.rk.%sNone' % side})], w='fill')


def ranks_tab():
    picker = card([row([row([rk_pick('lpicks', 'rk_lp')], w='fill', gap=7.7), T('VS', 25.6, AFG, FXB, lh=33, shrink=True), row([rk_pick('rpicks', 'rk_rp')], w='fill', gap=7.7)],
                       w='fill', gap=20.5, cross='center')], w='fill', min_h=102.1, pad=[17.9, 17.9, 17.9, 17.9])
    duel = card([box([rk_row()], w='fill')], w='fill', min_h=258, pad=[8.7, 20.5, 7.7, 20.5])
    verdict = row([box([T('Rivalry standing', 13.44, MUT, FB, lh=16, ls=1.54, upper=True), T('{vm.rk.verdict}', 25.6, INK, FXB, lh=33, pad=[5, 0, 0, 0])], w='fill'),
                   T('{vm.rk.score}', 30.72, AFG, FXB, lh=39, shrink=True)], w='fill', cross='center', bg=SURF2, border=LINE, bw=2, radius=17.92, pad=[10.2, 20.5, 10.2, 20.5], min_h=80.6)
    heroes = card([row([rk_champ('l'), rk_champ('r')], w='fill', gap=25.6)], w='fill', pad=[17.9, 20.5, 17.9, 20.5])
    col = box([picker, duel, verdict, heroes], w='fill', gap=25.6, pad=[u(1.8), u(2.4), 20.5, 32.3])
    body = {'type': 'scroll', 'id': 'rk_scroll', 'w': 'fill', 'h': 'fill', 'children': [col]}
    foot = row([T('{vm.rk.foot}', 15.36, MUT, F, lh=19.2)], w='fill', pad=[u(1.1), u(2.4), u(1.1), u(2.4)], bg=SURF, bsides='t', border=LINE, bw=2, min_h=48.3)
    load = T('Loading the standings...', 16, MUT, FB, w='fill', h='fill', text_align='center', pad=[80, 0, 0, 0], **{'if': '!vm.rk'})
    return box([box([body, foot], w='fill', h='fill', bg=TEX + 'pattern.png', anim='dpop', **{'if': 'vm.rk'}), box([load], w='fill', h='fill', bg=TEX + 'pattern.png', anim='dpop', **{'if': '!vm.rk'})],
               w='fill', h='fill', **{'if': "vm.tab == 'ranks'"})



# ---------------------------------------------------------------------------------------------------------- tower detail / trial briefing
def d_top(x):
    back = {'type': 'button', 'id': 'd_back' + x, 'bg': '#00000000', 'hover': '#00000000', 'pad': [3.84, 7.68, 3.84, 7.68], 'args': {}, 'msg': vmsg('up'), 'shrink': True,
            'children': [box([T('\u2190', 11, ACC, FB, lh=13, w=38, text_align='center'), T('Back', 14.72, ACC, FB, lh=18, w=38, text_align='center')], w=38)]}
    logo2 = row([box([], w=37.1, h=37.1, bg=TEX + 'logo_neutral.png'), box([T('COBBLE', 23.04, INK, FXB, lh=23.04, ls=0.46), T('TOWERS', 23.04, ACC, FBL, lh=23.04, ls=0.46)])],
                gap=u(.8), cross='center', shrink=True)
    tab = {'type': 'button', 'id': 'd_tab' + x, 'text': '{k.name}', 'font': F6, 'size': 17.28, 'lh': 21.6, 'color': '{k.fg}', 'hover_color': INK, 'shadow': False, 'shrink': True,
           'bg': '{k.bg}', 'hover': '{k.hov}', 'radius': 25.6, 'pad': [u(.5), u(1.5), u(.5), u(1.5)], 'args': {'t': 'str:16'}, 'argv': {'t': 'k.id'}, 'msg': vmsg('tab', t='{t}'),
           'repeat': {'for': 'vm.tabs', 'as': 'k', 'limit': 8}}
    pills = row([tab], bg='#080e1d', radius=25.6, pad=[4.5, 4.5, 4.5, 4.5], gap=u(.3), shrink=True)
    return row([back, logo2, pills, sp(),
                tl('Refresh', 'd_refresh' + x, tmsg('TowerHallAction', action='refresh', argument='', revision='')),
                tl('Guide', 'd_guide' + x, vmsg('go', v='guide')), tl('Settings', 'd_settings' + x, vmsg('go', v='settings')), xbtn('d_close' + x)],
               w='fill', gap=u(2), cross='center', pad=[u(1), u(2.4), u(1), u(2.4)], bg=SURF, bsides='b', border=LINE, bw=2, min_h=72.7)


def d_banner(expr):
    return box([box([], w='fill', h='fill', bg='{%s}' % expr)], w=737.6, h=153.6, radius=16.64, clip=True)


def d_right(title_expr, sub_expr, btn):
    return card([T(title_expr, 20.48, INK, FXB, lh=20.48, w='fill'), T(sub_expr, 15.36, MUT, F6, lh=21.5, w='fill', wrap=True, pad=[19, 0, 15.3, 0]), btn],
                w='fill', h='fill', pad=[20.5, 20.5, 20.5, 20.5])


def d_frame(left, right, cond, x):
    body = row([left, right], w='fill', h='fill', gap=25.6, pad=[u(1.8), u(2.4), 20.5, u(2.4)], bg=TEX + 'pattern.png', anim='dpop')
    foot = row([T('{vm.hall.status}', 15.36, MUT, F, lh=19.2)], w='fill', pad=[u(1.1), u(2.4), u(1.1), u(2.4)], bg=SURF, bsides='t', border=LINE, bw=2, min_h=48.3)
    return box([d_top(x), body], w='fill', h='fill', **{'if': cond})


def tower_detail():
    ch = row([T('{vm.td.name}', 38.4, INK, FXB, lh=38.4), sp(),
              T('{vm.td.medal}', 14.08, '{vm.td.medalFg}', FB, lh=14.08, bg='{vm.td.medalBg}', radius=25.6, pad=[u(.25), u(.8), u(.25), u(.8)], shrink=True)],
             w='fill', cross='center', pad=[15.4, 17.9, 7.7, 17.9])
    kv = row([T('{f.k}', 17.28, MUT, F, lh=21.6, w=131), T('{f.v}', 17.28, INK, FB, lh=21.6, w='fill')], w='fill', repeat={'for': 'vm.td.facts', 'as': 'f', 'limit': 6})
    facts = box([kv], w='fill', gap=7.7, pad=[7.7, 17.9, 15.4, 17.9])
    foes = box([T('Foes you may meet', 13.44, MUT, FB, lh=16, ls=1.54, upper=True),
                box([box([], x=79.3 * i, y=0, w=89.6, h=59.7, bg='{vm.td.r%d}' % i) for i in range(3)], w=300, h=59.7)], w='fill', gap=9, pad=[0, 17.9, 0, 17.9])
    left = card([d_banner('vm.td.banner'), ch, facts, foes], w=739.6, h='fill', clip=True)
    btn = dbtn('Select tower & open lobby', 'd_select', 'green', msg=tmsg('TowerHallAction', action='tower', argument='{id}', revision=''), enabled='vm.td.canAct', w='fill', text_align='center',
               lh=24, args={'id': 'str:64'}, argv={'id': 'vm.td.id'})
    btn.pop('shrink', None)
    right = d_right('Start an expedition', 'Selecting this tower creates or updates your lobby. Review the mode, your party and invitations there before starting.', btn)
    return d_frame(left, right, "vm.view == 'hall' && vm.sub == 'tower'", 't')


def trial_briefing():
    ch = row([T('{vm.tb.title}', 33.28, INK, FXB, lh=33.28), sp(), T('{vm.tb.kind}', 13.44, MUT, FB, lh=13.44, ls=1.54, upper=True)],
             w='fill', cross='center', pad=[15.4, 17.9, 7.7, 17.9])
    st = row([stat('st_floors.png', 46.1, 26.9, '{vm.tb.floors}', '{vm.tb.floorsSub}'), stat('st_mode.png', 46.1, 38.4, '{vm.tb.mode}', 'mode'),
              stat('st_enemy.png', 46.1, 41.9, '{vm.tb.enemy}', 'enemies')], w='fill', gap=11.5)
    for c in st['children']:
        c['w'] = 226.3
    mods = box([T('Starting modifiers', 13.44, MUT, FB, lh=13.44, ls=1.54, upper=True),
                row([row([box([], w=21.8, h=18.1, bg=TEX + 'mod_gem.png'), T('{c}', 15.36, '#8fb2ff', FB, lh=15.36)], gap=7.7, cross='center', bg='#1c2d57', radius=25.6,
                         pad=[5.6, 11.5, 5.6, 11.5], shrink=True, repeat={'for': 'vm.tb.mods', 'as': 'c', 'limit': 6})], w='fill', gap=7.7, wrap=True)],
               w='fill', gap=7.7, pad=[8, 0, 0, 0], **{'if': 'vm.tb.hasMods'})
    lines = box([st, mods], w='fill', gap=8, pad=[7.7, 17.9, 12.8, 17.9])
    left = card([d_banner('vm.tb.banner'), ch, lines], w=739.6, h='fill', clip=True)
    btn = dbtn('Prepare trial lobby', 'd_prepare', 'green', msg=tmsg('TowerHallAction', action='trial', argument='{k}', revision='{rev}'), enabled='vm.tb.canAct', w='fill', text_align='center',
               lh=24, args={'k': 'str:16', 'rev': 'str:64'}, argv={'k': 'vm.tb.k', 'rev': 'vm.tb.rev'})
    btn.pop('shrink', None)
    right = d_right('Take the trial', '{vm.tb.note}', btn)
    return d_frame(left, right, "vm.view == 'hall' && vm.sub == 'trial'", 'b')




# ---------------------------------------------------------------------------------------------------------- rental draft
JF = 'jersey'
BRONZE, RTEXT, RMUTED = '#e3bd7f', '#f0dfbf', '#c6ac87'
FLS = '3,3,#000000cc'      # .fl text-shadow
TX0, TY0 = 30.7, 86.7      # the table's origin on the page


def rt(text, x, y, w, size, color, h=27, **kw):
    """centred pixel-font line placed on the table (page coordinates)"""
    d = T(text, round(size * 1.11, 2), color, JF, lh=h, x=x - TX0, y=y - TY0, w=w, text_align='center', wrap=True, tshadow=FLS)
    d.update(kw)
    return d


def rbtn(text, id_, x, y, w, h, kind='gh', **kw):
    d = dbtn(text, id_, kind, size=19.2, lh=24, text_align='center', x=x - TX0, y=y - TY0, w=w, h=h, pad=[7.04, 15.36, 7.04, 15.36], **kw)
    d.pop('shrink', None)
    return d


def rd_top():
    dot = box([], w=20.5, h=20.5, radius=10, bg='{d.bg}', border='{d.bd}', bw=3, repeat={'for': 'vm.rd.dots', 'as': 'd', 'limit': 3})
    crumb = row([T('Rental draft', 16.64, INK, FXB, lh=21), T('\u00b7 Keep two cards from each of three packs', 16.64, MUT, F6, lh=21)], gap=4, cross='center', shrink=True)
    return row([logo(True), crumb, row([dot], gap=7.7, pad=[0, 0, 0, u(1)], shrink=True), sp(),
                tl('Start over', 'r_restart', tmsg('RentalDraftAction', action='RESTART', a=0, b=0)), xbtn('r_close')],
               w='fill', gap=u(2), cross='center', pad=[u(1), u(2.4), u(1), u(2.4)], bg=SURF, bsides='b', border=LINE, bw=2, min_h=63.7, ks='c')


RD_HEAD = 16


def rd_tip(kept):
    """the page's hover card (#rc-tip): 350 real px wide, drawn at the pointer (above-right), only over the card's INSPECT plaque.
    Everything is in real pixels (ks 'p'); the zone (card units) is the plaque, with the card lifted 8 px by the hover and 14 px lower unless kept."""
    JT = 'jersey'

    def L(text, size, color, **kw):
        d = T(text, round(size * 1.11, 2), color, JT, lh=round(size * 1.28, 1))
        d.update(kw)
        return d
    DIM, UP, DN = '#aaaaaa', '#55ff55', '#ff5555'
    head = row([L('{c.tn}', 22, '#ffffff', bold=True, lh=27), L('{c.tlv}', 18, DIM, lh=27)], gap=10, cross='end')
    types = row([L('{c.ty1}', 21, '{c.ty1c}', lh=27), L('/', 21, '#7a7a7a', lh=27, **{'if': 'c.two'}), L('{c.ty2}', 21, '{c.ty2c}', lh=27, **{'if': 'c.two'})], gap=7, pad=[0, 0, 8, 0])

    def kv(k, v, cond):
        return row([L(k, 15, DIM, lh=21), L(v, 15, '#ffffff', bold=True, lh=21)], gap=5, **{'if': cond})
    nat = row([L('Nature:', 15, DIM, lh=21), L('{c.nat}', 15, '#ffffff', bold=True, lh=21),
               row([L('(', 15, '#7a7a7a', lh=21), L('{c.up}', 15, UP, bold=True, lh=21), L('/', 15, '#7a7a7a', lh=21), L('{c.dn}', 15, DN, bold=True, lh=21), L(')', 15, '#7a7a7a', lh=21)], gap=4)],
              gap=5, **{'if': 'c.hasNat'})

    def block(label, tot, pct, pcol, items, cond):
        top = row([L(label, 15, DIM, lh=21), L(tot, 15, '#ffffff', bold=True, lh=21),
                   row([L('(', 15, '#7a7a7a', lh=21), L(pct, 15, pcol, bold=True, lh=21), L(')', 15, '#7a7a7a', lh=21)], gap=2)], gap=5, pad=[9, 0, 0, 0])
        chip = box([L('{s.t}', 11.5, '#ffffff', bold=True, lh=18)], bg='{s.c}', radius=2, pad=[0, 6, 0, 6], h=20.5, align='center', cross='center', **{'if': 's.chip'})
        val = L('{s.t}', 17, '{s.c}', bold=True, lh=20.5, pad=[0, 5, 0, 0], **{'if': '!s.chip'})
        rowc = {'type': 'row', 'wrap': True, 'gap': 6, 'w': 'fill', 'pad': [5, 0, 0, 0], 'children': [
            {'type': 'column', 'children': [chip, val], 'repeat': {'for': items, 'as': 's', 'limit': 14}}]}
        return box([top, rowc], w='fill', **{'if': cond})
    moves = box([L('Moves:', 15, DIM, lh=21, pad=[9, 0, 0, 0]),
                 {'type': 'row', 'wrap': True, 'gap': 5, 'w': 'fill', 'pad': [1, 0, 0, 0], 'children': [
                     L('{m.t}', 15, '{m.c}', bold=True, lh=16, repeat={'for': 'c.mv', 'as': 'm', 'limit': 8})]}], w='fill', **{'if': 'c.hasMv'})
    body = box([head, types, kv('Ability:', '{c.ab}', 'c.hasAb'), kv('Item:', '{c.item}', 'c.hasIt'), nat,
                block('IVs:', '{c.ivT}', '{c.ivP}', '#ffc83a', 'c.ivs', 'c.hasIv'), block('EVs:', '{c.evT}', '{c.evP}', '#55ff55', 'c.evs', 'c.hasEv'), moves], w='fill')
    cond = 'c.kept' if kept else '!c.kept'
    # zone: x 10..199 of the card; y: card top (16 headroom + 14 lower unless kept - 8 hover lift) + 236..299
    top = 16 + (0 if kept else 14) - 8
    return box([body], tipbox=True, tip_wait=True, tip_above=True, ks='p', x=0, y=0, w=350, pad=[10, 14, 13, 14], bg='#100010f5', border='{c.tring}', bw=2, ring='2,#100010',
               bshadow='0,10,#000000a6', tipzone=[15, top + 236, 204, top + 299], **{'if': cond})


def rcard(sfx, f, click):
    """one rental card (design 280x400 scaled by f): baked face by type, the page's sprite, native numerals/name, baked medals and gems"""
    W, H = round(280 * f, 2), round(400 * f, 2)

    def fx(v):
        return round(v * f, 2)
    sprites = [box([], x=0, y=fx(36), w=W, h=fx(236), bg='{c.sp%s}' % sfx, **(dict(anim='slam', anim_ms=500, anim_delay='c.dslam', anim_key='vm.rd.akey', anim_stable=True) if click else {}))]
    if click:
        sprites += [box([], x=round((W - 120 * f) / 2, 2), y=round(0.42 * H - 60 * f, 2), w=round(120 * f, 2), h=round(120 * f, 2), radius=round(60 * f, 2), bg='#00000000', border='{c.rc}', bw=3,
                        anim='ringout', anim_ms=1000, anim_delay='c.dr%d' % k, anim_key='vm.rd.akey', anim_stable=True, **{'if': 'c.nr > %d' % k}) for k in range(4)]
    face = box([box([], x=0, y=0, w=W, h=H, bg='{c.base}')] + sprites + [
        box([], x=0, y=0, w=W, h=H, bg='{c.rar}'),
        T('{c.lv}', fx(20.5), '#101018', JF, lh=fx(20), x=fx(14), y=fx(263.5), w=fx(48), text_align='center'),
        T('{c.name}', fx(38.75), '#ffffff', JF, lh=fx(31), x=0, y=fx(259), w=W, text_align='center', tshadow='0,%d,{c.acc2}' % max(1, round(3 * f))),
        box([], x=fx(246.3), y=fx(239.4), w=fx(18.3), h=fx(18.3), bg='{c.gem1}', **{'if': 'c.two'}),
        box([], x=fx(246.3), y=fx(260.3), w=fx(18.3), h=fx(18.3), bg='{c.gem2}'),
        box([], x=fx(29), y=fx(316), w=fx(52.1), h=fx(57.9), bg='{c.med1}'),
        box([], x=fx(196), y=fx(316), w=fx(52.1), h=fx(57.9), bg='{c.med2}'),
        ] + ([box([], x=0, y=0, w=W, h=H, bg='#ffffffe6', anim='flash', anim_ms=450, anim_delay='c.dflash', anim_key='vm.rd.akey', anim_stable=True)] if click else []) + ([box([], x=round(-3 * W, 2), y=0, w=round(3 * W, 2), h=H, bg=TEX + 'rd_prism.png', anim='kf', anim_ms=1200, anim_delay='c.dprism', anim_key='vm.rd.akey', anim_stable=True,
                kf={'ease': [.42, 0, .58, 1], 'keys': [{'t': 0, 'dx': 0, 'a': 0}, {'t': .12, 'a': 1}, {'t': .88, 'a': 1}, {'t': 1, 'dx': round(4 * W, 2), 'a': 0}]})] if click else []) + ([box([], x=round((W - 300 * f) / 2, 2), y=fx(100), w=round(300 * f, 2), h=round(90 * f, 2), bg='{c.stamp}', anim='stamp', anim_delay='c.dstamp', anim_key='vm.rd.akey', anim_stable=True, **{'if': 'c.big'})] if click else []), x=0, y='c.lift%s' % sfx, w=W, h=H, radius=round(12 * f, 2), clip=True, tilt=True, holo='c.hv', **(dict(anim='flipb', anim_delay='c.dflip', anim_key='vm.rd.akey', anim_stable=True) if click else {}))
    glow = box([], x=-5, y='c.lift%s - 5' % sfx, w=W + 10, h=H + 10, bg='#00000000', border='#ddab3c', bw=3, radius=15, **{'if': 'c.kept'})
    kids = [glow, face]
    if click:
        back = box([], x=0, y='c.lift%s' % sfx, w=W, h=H, radius=round(12 * f, 2), bg=TEX + 'rd_back.png', anim='flipa', anim_delay=900, anim_hold='c.dhold', anim_key='vm.rd.akey', anim_stable=True, hint='{c.rc}|{c.chg}')
        kids = [glow, back, face]
    if click:
        # z lifts the tick above the face's own layers (they are drawn at higher depths than a plain sibling, which hid most of the tick behind the card)
        kids.append(box([], x=round(W - 28, 2), y=-7, w=22, h=22, z=60, bg=TEX + 'rd_check.png', **{'if': 'c.kept'}))
    inner = box(kids, w=W, h=H + 14)
    if click:
        # headroom above the card: a kept card sits 14 px higher, with its gold glow (-5) and tick (-7) above that, and the hover lift is 8 more - none of it may be cut off
        inner = box([inner], w=W + 10, h=H + 14 + RD_HEAD, pad=[RD_HEAD, 0, 0, 5])      # the card sits in the middle of its 10 px wider button
        return {'type': 'button', 'id': 'rd_pick', 'w': W + 10, 'h': H + 14 + RD_HEAD, 'bg': '#00000000', 'hover': '#00000000', 'pad': [0, 0, 0, 0], 'hover_dy': -8, 'args': {'i': 'int:0:9'}, 'argv': {'i': 'c.i'},
                'tip_wait': True,
                'msg': vmsg('rdPick', i='{i}'), 'children': [inner, rd_tip(False), rd_tip(True)]}
    return inner


def rd_cards_choose():
    unit = box([rcard('1', 218.4 / 280, True), T('{c.num}', 22, '#f8e7c2', JF, lh=24, x=5, y=round(218.4 / 280 * 400 + 14 + RD_HEAD, 2), w=218.4, text_align='center')], w=228.4, gap=0, cross='center', repeat={'for': 'vm.rd.cards', 'as': 'c', 'limit': 6})
    return unit


def rd_view():
    f1, f2 = 218.4 / 280, 159.4 / 280
    # the table is a 2-unit-wide anchor at the frame's centre (so its content can never stretch the frame and push it off-centre); the content hangs from it, centred on the anchor
    table = box([], w=2, h=612.8, ks='t')            # the table's content, authored at ps 1.95 and scaled by the real window's ps
    ch = table['children']
    # --- warning gate
    ch += [rt('{vm.rd.warnTitle}', 370, 186.6, 540, 36, BRONZE, 27, **{'if': "vm.rd.stage == 'warn'"}),
           rt('{vm.rd.warnText}', 370, 252.6, 540, 30, RTEXT, 27, **{'if': "vm.rd.stage == 'warn'"}),
           rbtn('Turn on calm effects and continue', 'rd_calm', 476.1, 507.6, 327.8, 38.1, 'green', msg=vmsg('rdWarn', k='calm'), **{'if': "vm.rd.stage == 'warn' && !vm.rd.calm"}),
           rbtn('Continue', 'rd_cont', 575, 507.6, 130, 38.1, 'green', msg=vmsg('rdWarn', k='fx'), **{'if': "vm.rd.stage == 'warn' && vm.rd.calm"}),
           rbtn('Continue with effects', 'rd_fx', 478.7, 559.6, 222, 40.1, msg=vmsg('rdWarn', k='fx'), **{'if': "vm.rd.stage == 'warn' && !vm.rd.calm"}),
           rbtn('Leave', 'rd_leave', 714.6, 559.6, 86.7, 40.1, msg=tmsg('bye'), close=True, **{'if': "vm.rd.stage == 'warn' && !vm.rd.calm"})]
    # --- sealed pack
    pack = {'type': 'button', 'id': 'rd_pack', 'x': 530.8 - TX0, 'y': 192.1 - TY0, 'w': 218.4, 'h': 312, 'bg': '#00000000', 'hover': '#00000000', 'pad': [0, 0, 0, 0], 'args': {},
            'msg': vmsg('rdOpen'), 'if': "vm.rd.stage == 'sealed'", 'children': [box([], w=218.4, h=312, bg=TEX + 'rd_pack.png', anim='floaty', anim_dy=9, anim_ms=3600)]}
    ch += [pack, rt('Click the pack to open it', 440, 526.8, 400, 30, BRONZE, 27, **{'if': "vm.rd.stage == 'sealed'"}),
           rt('{vm.rd.packText}', 440, 571.8, 400, 30, RMUTED, 27, **{'if': "vm.rd.stage == 'sealed'"})]
    # --- choosing
    row_c = row([rd_cards_choose()], x=608.3 - 584.4, y=179.6 - TY0 - RD_HEAD, w=1170, align='center', h=360 + RD_HEAD, gap=4, **{'if': "vm.rd.stage == 'choose'"})
    tear_top = box([], x=530.8 - TX0, y=192.1 - TY0, w=218.4, h=30, bg=TEX + 'rd_pack_top.png', anim='tear', anim_key='vm.rd.akey', anim_stable=True, **{'if': "vm.rd.stage == 'choose'"})
    tear_body = box([], x=530.8 - TX0, y=222.1 - TY0, w=218.4, h=282, bg=TEX + 'rd_pack_body.png', anim='fadeout', anim_delay=500, anim_key='vm.rd.akey', anim_stable=True, **{'if': "vm.rd.stage == 'choose'"})
    AN_IN = dict(anim='fade', anim_ms=250, anim_delay='vm.rd.dend', anim_key='vm.rd.akey', anim_stable=True)         # the choose line appears once the reveal is over
    AN_OUT = dict(anim='fadeaway', anim_ms=250, anim_delay='vm.rd.dend', anim_key='vm.rd.akey', anim_stable=True)    # the skip line goes away at the same time
    keep_msg = dict(msg=tmsg('RentalDraftAction', action='PICK', a='{a}', b='{b}'), args={'a': 'int:0:9', 'b': 'int:0:9'}, argv={'a': 'vm.rd.selA', 'b': 'vm.rd.selB'})
    def rtl(text, **kw):          # a pixel-font line of natural width (centred by its row)
        return T(text, round(30 * 1.11, 2), BRONZE, JF, lh=27, tshadow=FLS, **kw)

    def nbtn(text, id_, kind='gh', **kw):   # a button of natural width at the page's own (u) size
        d = dbtn(text, id_, kind, size=19.2, lh=24, text_align='center', pad=[7.04, 15.36, 7.04, 15.36], ks='c', **kw)
        return d
    CY = 462.6                    # the choose / skip line's centre on the table (measured on the reference)
    skip_row = row([rtl('Click to skip the reveal'), nbtn('Skip', 'rd_skip', msg=vmsg('rdSkip'))], x=0, y=round(CY - 23, 2), w=1218.6, h=46, gap=20, align='center', cross='center',
                   **AN_OUT, **{'if': "vm.rd.stage == 'choose'"})
    keep_row = row([rtl('{vm.rd.chooseText}'),
                    nbtn('Keep these two', 'rd_keep', 'gh', enabled='vm.rd.canKeep', **{'if': '!vm.rd.canKeep'}),
                    nbtn('Keep these two', 'rd_keep2', 'green', enabled='vm.rd.canKeep', **keep_msg, **{'if': 'vm.rd.canKeep'})],
                   x=0, y=round(CY - 23, 2), w=1218.6, h=46, gap=20, align='center', cross='center', **AN_IN, **{'if': "vm.rd.stage == 'choose'"})
    ch += [row_c, tear_body, tear_top, skip_row, keep_row,
           rt('A team may keep at most 2 legendary or mythic Pok\u00e9mon.', 240, 589.9, 800, 30, RMUTED, 27, **{'if': "vm.rd.stage == 'choose'"})]
    # --- complete
    team = row([box([rcard('2', f2, False)], w=159.4, h=227.8, repeat={'for': 'vm.rd.cards', 'as': 'c', 'limit': 6})], x=121.4 - TX0 + 15.36, y=245.8 - TY0, w=1010, h=237, gap=10.4, **{'if': "vm.rd.stage == 'done'"})
    ch += [rt('Your rental team', 440, 200.8, 400, 36, BRONZE, 27, **{'if': "vm.rd.stage == 'done'"}), team,
           rt('Your team is drafted.', 440, 514.5, 400, 30, RTEXT, 27, **{'if': "vm.rd.stage == 'done'"}),
           rbtn('Draft again', 'rd_again', 459.2, 547, 135.8, 40, msg=tmsg('RentalDraftAction', action='RESTART', a=0, b=0), **{'if': "vm.rd.stage == 'done'"}),
           rbtn('Lobby', 'rd_lobby', 608, 547, 87, 40, msg=tmsg('RentalDraftAction', action='LOBBY', a=0, b=0), **{'if': "vm.rd.stage == 'done'"}),
           rbtn('Ready up', 'rd_ready', 706, 547, 122, 40, 'green', msg=tmsg('RentalDraftAction', action='READY', a=0, b=0), **{'if': "vm.rd.stage == 'done'"})]
    for c_ in ch:                     # content positions are authored from the table's left edge; hang them from the 2-unit anchor's centre instead
        x_ = c_.get('x', 0)
        c_['x'] = round(x_ - 608.3, 2) if isinstance(x_, (int, float)) else '(%s) - 608.3' % x_
    # the table frame fills the space under the header (CSS .ns.dtable: radius 1.4u, border .3u #a8843a, glow + 32px tile + two inner rings); the content sits centred in it
    B, R = round(U * .3, 2), round(U * 1.4, 2)
    inset = dict(x=B, y=B, right=B, bottom=B, w='fill', h='fill')
    tile = box([], bg=TEX + 'rd_tile.png', tile=32, radius=round(R - B, 2), **inset)
    frame = box([box([], bg=TEX + 'rd_glow.png', **inset), tile,
                 box([], border='#a8843a66', bw=round(U * .5, 2), radius=round(R - B, 2), aa=True, **inset),
                 box([], border='#0a0a148c', bw=round(U * .35, 2), radius=round(R - B, 2), aa=True, **inset),
                 table], id='rd_frame', w='fill', h='fill', radius=R, border='#a8843a', bw=B, bg='#0e1a38', bshadow='0,5,#00000059', align='center', cross='center', ks='c', aa=True)
    body = row([box([], bg=TEX + 'rd_tile_body.png', tile=32, x=0, y=0, w='fill', h='fill'), frame], id='rd_body', w='fill', h='fill', pad=[u(1.8), u(2.4), u(1.6), u(2.4)], anim='dpop', align='center', cross='center', ks='c')
    return box([rd_top(), body], w='fill', h='fill', **{'if': "vm.view == 'draft'"})


# ---------------------------------------------------------------------------------------------------------- guide / settings
GUIDE = [
    ('The tower', ['Pick a tower in the Hall and open a lobby. A tower is a climb of floors; each floor is a battle against wild opponents, then a boss.',
                   'Floors 5 and 10 are milestones with bigger bosses. Some towers keep going into harder cycles (Ascension).']),
    ('Getting in', ["You need a Tower Key (Rental Key for a rental draft; each player's first ordinary run of the day is free) and a registered party of up to six Pokemon. Invite friends in the lobby and everyone readies up.",
                    'In Rental Draft mode you borrow a team instead, and your own Pokemon stay safe.']),
    ('Between floors', ['At the intermission the team drafts a modifier (a harder floor for better rewards), can visit the vendor to heal or buy, and sometimes meets a room: Rest, Shrine, Forge, Library, Vault or Mystic Pool.',
                        'Relics from bosses and rooms help for the rest of the run (up to six). The team votes to keep climbing or cash out.']),
    ('Rewards', ['Rewards are banked at milestone floors, when you cash out and when you finish the tower. Cashing out keeps what is banked and ends the run.']),
    ('Daily and weekly trials', ['Each day and week has its own tower, built from a mix of regions with its own rules. Everyone gets the same one.',
                                 'You get one scored attempt on each trial; later runs are practice. Daily trials build a streak, and weekly trials add a rule for the whole week.']),
    ('Mastery and your name', ['Achievements in a tower raise your mastery level there. The icon and number beside your name show your best tower.',
                               'Pick which tower shows, or hide it, in the Hall settings.']),
    ('Useful commands', ['Talk to the Tower Keeper to open the Hall. /tower help shows this guide in chat. /tower report <text> tells us about a problem or an idea.']),
]


def guide_view():
    kids = [T('How CobbleTowers works', 33.28, INK, FXB, lh=42, w='fill', pad=[0, 0, 18, 0])]
    for h, lines in GUIDE:
        kids.append(T(h, 20.48, ACC, FXB, lh=25, w=962.6, pad=[18, 0, 7, 0]))
        for ln in lines:
            kids.append(T(ln, 17.92, INK, FB, lh=26, w=962.6, wrap=True, pad=[0, 0, 8.7, 0]))
    col = box(kids, w=1024, pad=[20.5, 30.7, 30, 30.7])
    body = {'type': 'scroll', 'id': 'g_scroll', 'w': 'fill', 'h': 'fill', 'children': [col]}
    page = row([card([body], w='fill', h='fill')], w='fill', h='fill', pad=[u(1.8), u(2.4), 20.5, u(2.4)], bg=TEX + 'pattern.png', anim='dpop')
    return box([d_top('g'), page], w='fill', h='fill', **{'if': "vm.view == 'hall' && vm.sub == 'guide'"})


def set_row(title, sub, ctl, last=False):
    kw = {} if last else {'bsides': 'b', 'border': LINE, 'bw': 1}
    return row([box([T(title, 22, INK, FXB, lh=26, w='fill'), T(sub, 15.36, MUT, F6, lh=19, w='fill')], w='fill'), ctl], w='fill', cross='center',
               pad=[13, 0, 13, 0], **kw)


def settings_view():
    anim = dbtn('{vm.set.anim}', 's_anim', 'green', msg=vmsg('anim'), shrink=True, size=16, lh=20, pad=[u(.6), u(1.5), u(.6), u(1.5)], text_align='center', bg='{vm.set.animBg}')
    head = row([T('Settings', 20.48, INK, FXB, lh=25), sp(), T('Kept on this device', 13.44, MUT, FB, lh=16, ls=1.54, upper=True)], w='fill', cross='center', pad=[0, 0, 12, 0])
    note = T("Game volume stays in Minecraft's sound settings; the tower pages make no sounds of their own. Tower keys are set in Minecraft's controls screen, under Miscellaneous.",
             15.36, MUT, F6, lh=19, w='fill', wrap=True, pad=[13, 0, 0, 0])
    c = card([box([head, set_row('Animations', 'Reduced motion keeps every choice available.', anim, True), note], w='fill', pad=[0, 0, 0, 0])], w='fill', h='fill', pad=[17.9, 17.9, 17.9, 17.9])
    page = row([c], w='fill', h='fill', pad=[u(1.8), u(2.4), 20.5, u(2.4)], bg=TEX + 'pattern.png', anim='dpop')
    return box([d_top('s'), page], w='fill', h='fill', **{'if': "vm.view == 'hall' && vm.sub == 'settings'"})


# ---------------------------------------------------------------------------------------------------------- feature sections (Codex etc.)
def f_entry(idp, rep, wide, show):
    """.dentry: a bordered row button (title + one-line summary); wide in the list mode"""
    pad = [14, 17.9, 14, 17.9] if wide else [11.5, 15.4, 11.5, 15.4]
    inner = box([T('{e.label}', 17.92, INK, FB, lh=21.5, w='fill'), T('{e.sub}', 14.08, MUT, F, lh=17, w='fill', **{'if': 'e.sub'})], w='fill', gap=3.8, pad=pad)
    return {'type': 'button', 'id': idp, 'w': 'fill', 'bg': '{e.bg}', 'hover': '{e.bg}', 'border': '{e.bd}', 'hover_border': HOV, 'bw': 2.5, 'radius': 14, 'pad': [0, 0, 0, 0],
            'enabled': 'vm.fe.canAct', 'args': {'i': 'int:0:199'}, 'argv': {'i': 'e.i'}, 'msg': vmsg('fopen', i='{i}'), 'children': [inner],
            'repeat': {'for': rep, 'as': 'e', 'limit': 60}, **({'if': show} if show else {})}


def f_line():
    dot = box([T('{l.sign}', 15.36, '#ffffff', FXB, lh=18, w=23, text_align='center')], w=23, h=23, radius=12, bg='{l.dot}', pad=[2.5, 0, 0, 0])
    return row([dot, T('{l.text}', 15.36, INK, F, lh=20, w='fill', wrap=True)], w='fill', gap=6.4, repeat={'for': 'vm.fe.lines', 'as': 'l', 'limit': 40})


def feature_view():
    note = T('Loading\u2026', 16, MUT, F, lh=20, w='fill', text_align='center', **{'if': 'vm.fe.loading'})
    loading = card([box([note], w='fill', h='fill', align='center', cross='center')], w='fill', h='fill', **{'if': 'vm.fe.loading'})
    # list mode: one wide card
    lst = card([ch('{vm.fe.heading}', '{vm.fe.count}'),
                {'type': 'scroll', 'id': 'f_lrows', 'w': 'fill', 'h': 'fill', 'pad': [5, 15, 15, 15],
                 'children': [box([f_entry('f_lrow', 'vm.fe.items', True, None), T('Nothing here yet.', 16, MUT, F, lh=20, w='fill', text_align='center', pad=[20, 0, 0, 0], **{'if': '!vm.fe.hasItems'})], w='fill', gap=11.5)]}],
               w='fill', h='fill', **{'if': "!vm.fe.loading && vm.fe.mode == 'list'"})
    # entry / review mode: entries left, detail right
    left = card([ch('{vm.fe.title}', None),
                 {'type': 'scroll', 'id': 'f_erows', 'w': 'fill', 'h': 'fill', 'pad': [5, 8, 8, 3], 'children': [box([f_entry('f_erow', 'vm.fe.items', False, None)], w='fill', gap=11.5)]}],
                w='34%', h='fill')
    inp = {'type': 'input', 'id': 'f_in', 'bind': 'ft', 'hint': '{vm.fe.hint}', 'w': 'fill', 'h': 38, 'max_len': 256, 'font': F, 'size': 16, 'bg': '#0b1430', 'border': LINE, 'bw': 2, 'radius': 10,
           'pad': [0, 10, 0, 10], 'color': INK, 'hint_color': MUT, 'args': {}, 'if': 'vm.fe.needsInput'}
    lines = {'type': 'scroll', 'id': 'f_lines', 'w': 'fill', 'h': 'fill', 'pad': [8, 18, 13, 18], 'children': [box([f_line(), inp], w='fill', gap=9)]}
    acts = row([dbtn('{vm.fe.primary}', 'f_prim', 'pri', msg=vmsg('fprim'), enabled='vm.fe.canAct', **{'if': 'vm.fe.hasPrimary'}),
                dbtn('Confirm', 'f_conf', 'pri', msg=vmsg('fconf', t='{t}'), enabled='vm.fe.canAct', args={'t': 'str:256'}, argv={'t': '$ft'}, **{'if': 'vm.fe.review'}),
                dbtn('Cancel', 'f_cancel', 'gh', msg=vmsg('fcancel'), **{'if': 'vm.fe.review'})],
               w='fill', gap=12.8, pad=[15.4, 17.9, 17.9, 17.9], bsides='t', border=LINE, bw=2, **{'if': 'vm.fe.hasPrimary || vm.fe.review'})
    right = card([ch('{vm.fe.heading}', None), lines, acts], w='fill', h='fill')
    ent = row([left, right], w='fill', h='fill', gap=25.6, **{'if': "!vm.fe.loading && vm.fe.mode != 'list'"})
    body = box([loading, lst, ent], w='fill', h='fill', pad=[u(1.8), u(2.4), u(1.6), u(2.4)], bg=TEX + 'pattern.png', anim='dpop')
    sm = dict(size=14.7, lh=18, pad=[8.5, 15.4, 8.5, 15.4])
    foot = row([dbtn('Overview', 'f_over', 'gh', msg=vmsg('fover'), enabled='vm.fe.canAct', **sm), dbtn('Refresh', 'f_ref', 'gh', msg=vmsg('fref'), enabled='vm.fe.canAct', **sm),
                T('{vm.fe.status}', 15.36, MUT, F, lh=19, w='fill')],
               w='fill', gap=12.8, cross='center', pad=[14, 30.7, 14, 30.7], bg=SURF, bsides='t', border=LINE, bw=2, min_h=65)
    return box([d_top('f'), body, foot], w='fill', h='fill', **{'if': "vm.view == 'hall' && vm.sub == 'feature'"})


def hall():
    main = box([hall_top(), hall_play(), trials_tab(), social_tab(), progress_tab(), ranks_tab()], w='fill', h='fill', **{'if': "vm.view == 'hall' && !vm.sub"})
    return box([main, tower_detail(), trial_briefing(), guide_view(), settings_view(), feature_view()], w='fill', h='fill', **{'if': "vm.view == 'hall'"})


# ---------------------------------------------------------------------------------------------------------- modals

# ---------------------------------------------------------------------------------------------------------- party registration / scouting / spectator
def party_view():
    """oa(): "Choose your party" - one tile per Pokemon, numbered in the order chosen"""
    tile = {'type': 'button', 'id': 'pt_mon', 'w': 'fill', 'bg': '{m.bg}', 'hover': '{m.bg}', 'border': '{m.bd}', 'hover_border': HOV, 'bw': 2.5, 'radius': 15.4,
            'ring': '{m.ring}', 'opacity': 'm.op', 'pad': [0, 0, 0, 0], 'args': {'id': 'str:64'}, 'argv': {'id': 'm.id'}, 'msg': play('TOGGLE_POKEMON', '{id}'),
            'repeat': {'for': 'vm.pt.mons', 'as': 'm', 'limit': 60}, 'children': [box([
                box([{'type': 'image', 'pokemon': '{m.pic}', 'x': 0, 'y': 0, 'w': 80, 'h': 80},
                     box([T('{m.order}', 15.36, '#ffffff', FXB, lh=18, w=30.7, text_align='center', pad=[6, 0, 0, 0])], x=-34, y=2, w=30.7, h=30.7, radius=15.4, bg=PRI, **{'if': 'm.on'})],
                    w=80, h=80),
                T('{m.name}', 16.64, INK, FXB, lh=20, w='fill', text_align='center'),
                T('{m.sub}', 12.8, MUT, F, lh=16, w='fill', text_align='center'),
                T('{m.where}', 12.8, MUT, F, lh=16, w='fill', text_align='center')], w='fill', gap=3.8, cross='center', pad=[12.8, 10.2, 12.8, 10.2])]}
    head = row([box([T('Choose your party', 20.48, INK, FXB, lh=24), T('{vm.pt.sub}', 15.36, MUT, F6, lh=19.2, pad=[3.8, 0, 0, 0])], w='fill'),
                row([dbtn('Clear', 'pt_clear', 'gh', msg=play('CLEAR_CHOICE'), size=16, lh=20, pad=[8, 18, 8, 18]),
                     dbtn('Done', 'pt_done', 'pri', msg=vmsg('partyDone'), size=16, lh=20, pad=[8, 18, 8, 18], bg=PRI, hover='#4f7ef0', border=PRI, hover_border=PRI)], gap=10.2, shrink=True)],
               w='fill', cross='center', pad=[15.4, 17.9, 7.7, 17.9])
    grid = {'type': 'scroll', 'id': 'pt_scroll', 'w': 'fill', 'h': 'fill', 'pad': [5, 17.9, 12.8, 17.9], 'children': [
        box([{'type': 'grid', 'w': 'fill', 'min_col': u(17), 'gap': 11.5, 'children': [tile]},
             T('You have no Pok\u00e9mon yet.', 16, MUT, F, lh=20, w='fill', text_align='center', pad=[25, 0, 0, 0], **{'if': 'vm.pt.none'})], w='fill')]}
    body = box([card([head, grid], w='fill', h='fill')], w='fill', h='fill', pad=[u(1.8), u(2.4), 20.5, u(2.4)], bg=TEX + 'pattern.png', anim='dpop')
    return box([top('Lobby', '\u00b7 Party registration', [tl('Back', 'pt_back', vmsg('partyDone')), xbtn('pt_close')]), body], w='fill', h='fill', **{'if': "vm.view == 'party'"})


def scout_view():
    """qs(): what the scouts saw - the list of details and the chosen one in large"""
    pic = box([box([], w=43.5, h=43.5, bg='{k.glyph}')], w=69.1, h=69.1, radius=35, bg='#1b2f63', border=GOLD2, bw=3, align='center', cross='center')
    tile = {'type': 'button', 'id': 'sc_cat', 'w': 'fill', 'bg': '{k.bg}', 'hover': '{k.bg}', 'border': '{k.bd}', 'hover_border': HOV, 'bw': 2.5, 'radius': 15.4, 'ring': '{k.ring}',
            'pad': [0, 0, 0, 0], 'args': {'k': 'int:0:99'}, 'argv': {'k': 'k.i'}, 'msg': vmsg('scoutPick', k='{k}'), 'repeat': {'for': 'vm.sc.cats', 'as': 'k', 'limit': 24},
            'children': [row([pic, box([T('{k.name}', 19.2, INK, FXB, lh=22, w='fill', wrap=True), T('{k.value}', 15.36, SOFT, F, lh=20, w='fill', wrap=True)], w='fill', gap=5)],
                             w='fill', gap=12.8, pad=[14, 14, 14, 14])]}
    left = card([ch('What the scouts saw', '{vm.sc.count}'),
                 {'type': 'scroll', 'id': 'sc_list', 'w': 'fill', 'h': 'fill', 'pad': [5, 17.9, 12.8, 17.9], 'children': [
                     box([{'type': 'grid', 'w': 'fill', 'min_col': u(17), 'gap': 11.5, 'children': [tile]},
                          T('Nothing revealed.', 16, MUT, F, lh=20, w='fill', text_align='center', pad=[25, 0, 0, 0], **{'if': 'vm.sc.none'})], w='fill')]}], w='64%', h='fill')
    chip = row([T('{c.text}', 16.64, '{c.fg}', FXB, lh=20)], bg='{c.bg}', border='{c.bd}', bw=2, radius=12.8, pad=[6, 12.8, 6, 12.8], shrink=True,
               repeat={'for': 'vm.sc.sel.chips', 'as': 'c', 'limit': 12})
    lead = box([{'type': 'image', 'pokemon': '{vm.sc.sel.lead}', 'w': 217, 'h': 217}], w='fill', cross='center', align='center', radius=15.4, bg='#1b2f63', border='#c8a24a', bw=4,
               pad=[18, 18, 18, 18], **{'if': 'vm.sc.sel.hasLead'})
    skull = box([box([], w=102, h=102, bg=TEX + 'glyph_skull.png')], w='fill', cross='center', align='center', radius=15.4, bg='#1b2f63', border='#c8a24a', bw=4,
                pad=[18, 18, 18, 18], **{'if': 'vm.sc.sel.isBoss'})
    detail = {'type': 'scroll', 'id': 'sc_detail', 'w': 'fill', 'h': 'fill', 'pad': [8, 17.9, 12.8, 17.9], 'children': [
        box([lead, skull, row([chip], w='fill', gap=9, wrap=True, **{'if': 'vm.sc.sel.hasChips'}),
             T('{vm.sc.sel.value}', 19.2, SOFT, F, lh=28, w='fill', wrap=True)], w='fill', gap=15.4)]}
    acts = row([dbtn('Close', 'sc_cclose', 'pri', msg=tmsg('bye'), close=True, bg=PRI, hover='#4f7ef0', border=PRI, hover_border=PRI)], w='fill', pad=[12.8, 17.9, 17.9, 17.9], bsides='t', border=LINE, bw=2)
    right = card([ch('{vm.sc.sel.name}', None), detail, acts], w='fill', h='fill', **{'if': 'vm.sc.hasSel'})
    right2 = card([ch('Details', None), T('Nothing revealed.', 16, MUT, F, lh=20, w='fill', pad=[12, 18, 12, 18])], w='fill', h='fill', **{'if': '!vm.sc.hasSel'})
    body = row([left, box([right, right2], w='fill', h='fill')], w='fill', h='fill', gap=25.6, pad=[u(1.8), u(2.4), 20.5, u(2.4)], bg=TEX + 'pattern.png', anim='dpop')
    return box([top('Scouting', '\u00b7 Floor {vm.sc.floor}', [xbtn('sc_close')]), body], w='fill', h='fill', **{'if': "vm.view == 'scout'"})


def spec_box():
    """Us(): the spectator's corner box"""
    return box([T('{vm.spec.title}', 19.2, '#ffffff', FXB, lh=24), T('{vm.spec.line}', 19.2, '#ffffff', F6, lh=24),
                T('{vm.spec.sub}', 15.4, '#ffffffbf', F6, lh=20)], x=20.5, y=20.5, w=330, bg='#16202ee6', radius=15.4, pad=[14, 20.5, 14, 20.5], gap=0, z=0, **{'if': 'vm.spec'})


def reward_modal():
    chip = {'type': 'button', 'id': 'chip', 'w': u(5), 'h': u(5), 'bg': SURF2, 'hover': SURF2, 'border': '{g.edge}', 'hover_border': '{g.edge}', 'bw': 2, 'radius': u(1),
            'args': {'k': 'int:0:99'}, 'argv': {'k': 'g.i'}, 'msg': vmsg('rewardPick', k='{k}'), 'repeat': {'for': 'vm.rw.grants', 'as': 'g', 'limit': 24}, 'children': [
                box([], w=u(3.2), h=u(3.2), bg='{g.icon}', x=u(.7), y=u(.7) if False else 0)]}
    grant = box([box([box([], x=u(-1.6), y=u(-1.6), w=u(15.2), h=u(15.2), bg=TEX + 'rd_rays.png', anim='draysin', anim_key='vm.rw.name'),
                          box([], w=u(12), h=u(12), bg='{vm.rw.plinth}'),
                          box([], x=u(2.5), y=u(2.5), w=u(7), h=u(7), bg='{vm.rw.icon}', anim='dbounce', anim_delay=80, anim_key='vm.rw.name'),
                          box([], x=u(-1.5), y=u(-1.5), w=u(15), h=u(15), bg=TEX + 'rd_ring.png', anim='dburst', anim_delay=100, anim_key='vm.rw.name', **{'if': 'vm.rw.tier'})]
                         + [box([], x=u(12) * px - 7, y=u(12) * py - 7, w=14, h=14, bg=TEX + 'rd_spark.png', anim='dtwinkle', anim_delay=dl, anim_key='vm.rw.name') for px, py, dl in ((.08, .18, 0), (.88, .30, 300), (.14, .78, 600), (.82, .82, 150))], w=u(12), h=u(12)),
                 T('{vm.rw.name}', u(2.2), INK, FXB, lh=u(2.4), w='fill', text_align='center', wrap=True, anim='pop', anim_ms=300, anim_delay=220, anim_key='vm.rw.name'),
                 T('× {vm.rw.amount}', u(1.6), MUT, FB, lh=u(1.8), w='fill', text_align='center'),
                 T('{vm.rw.tier}', u(1.05), '{vm.rw.tierFg}', FXB, lh=u(1.05), ls=1.28, upper=True, shrink=True, bg='{vm.rw.tierBg}', radius=u(1.5), pad=[u(.3), u(.9), u(.3), u(.9)],
                   **{'if': 'vm.rw.tier'})],
                w='fill', cross='center', gap=u(.5), pad=[u(.5), 0, u(.5), 0], **{'if': 'vm.rw.name'})
    pager = row([dbtn('‹', 'rprev', 'gh', msg=vmsg('rewardStep', d='{d}'), args={'d': 'int:-1:-1'}, argv={'d': '-1'}, size=u(1.25), pad=[u(.55), u(1.2), u(.55), u(1.2)]),
                 T('{vm.rw.pos}', u(1.3), MUT, FB, lh=u(1.3)),
                 dbtn('›', 'rnext', 'gh', msg=vmsg('rewardStep', d='{d}'), args={'d': 'int:1:1'}, argv={'d': '1'}, size=u(1.25), pad=[u(.55), u(1.2), u(.55), u(1.2)])],
                gap=u(1.2), cross='center', **{'if': 'vm.rw.many'})
    modal = box([T('Rewards secured', u(1.05), MUT, FB, lh=u(1.05), ls=1.54, upper=True),
                 T('Through floor {vm.rw.floor}', u(2.4), INK, FXB, lh=u(2.5)),
                 grant,
                 row([chip], gap=u(.6), align='center', w='fill', **{'if': 'vm.rw.many'}),
                 pager,
                 dbtn('Continue', 'rcont', 'green', msg=vmsg('rewardClose'))],
                w=u(44), pad=[u(1.6), u(1.6), u(1.6), u(1.6)], gap=u(.7), cross='center', radius=u(1.8), bg='gradv:%s,%s' % (SURF2, SURF), border='{vm.rw.edge}', bw=4, anim='dpop', anim_ms=240)
    return box([modal], x=0, y=0, w='fill', h='fill', z=120, screen_bg='#060a16d6', align='center', cross='center', anim='fade', anim_ms=180, **{'if': 'vm.rw'})


def vendor_modal():
    good = {'type': 'button', 'id': 'good', 'w': u(17), 'h': u(7.4), 'bg': SURF, 'hover': SURF, 'border': '{s.edge}', 'hover_border': HOV, 'bw': 2, 'radius': u(1.1),
            'args': {'k': 'int:0:99'}, 'argv': {'k': 's.i'}, 'msg': vmsg('vendorPick', k='{k}'), 'repeat': {'for': 'vm.ven.services', 'as': 's', 'limit': 24}, 'children': [
                row([box([box([], w=u(3.4), h=u(3.4), bg='{s.glyph}')], w=u(5.4), h=u(5.4), bg=TEX + 'medal.png', align='center', cross='center'),
                     box([T('{s.name}', u(1.4), INK, FXB, lh=u(1.4)),
                          row([box([], w=u(1.7), h=u(1.7), bg=TEX + 'ico_coin.png'), T('{s.price}', u(1.2), AFG, FXB, lh=u(1.2))], gap=u(.4), cross='center'),
                          T('{s.left}', u(1), MUT, F, lh=u(1))], gap=u(.3))], gap=u(1), cross='center', pad=[u(1), u(1), u(1), u(1)])]}
    modal = box([row([box([T('Tower supplies', u(1.05), MUT, FB, lh=u(1.05), ls=1.54, upper=True),
                           row([box([], w=u(2.6), h=u(2.6), bg=TEX + 'ico_coin.png'), T('Balance: {vm.ven.balance}', u(2.4), INK, FXB, lh=u(2.5))], gap=u(.7), cross='center')], gap=0),
                     sp(),
                     dbtn('For: {vm.ven.target}', 'vtarget', 'gh', msg=vmsg('vendorTarget'), size=u(1.25), pad=[u(.55), u(1.2), u(.55), u(1.2)], **{'if': 'vm.ven.target'})],
                    w='fill', cross='center'),
                 T('Nothing is for sale.', 16, MUT, F, w='fill', text_align='center', pad=[u(2), u(2), u(2), u(2)], **{'if': '!vm.ven.services'}),
                 {'type': 'grid', 'w': 'fill', 'min_col': u(17), 'gap': u(.9), 'children': [good]},
                 row([box([T('{vm.ven.pickName}', u(1.4), INK, FXB, lh=u(1.4)), T('{vm.ven.pickSub}', u(1.2), MUT, F6, lh=u(1.7)),
                           T('{vm.ven.message}', u(1.2), MUT, F6, lh=u(1.7), **{'if': 'vm.ven.message'})], w='fill'),
                      dbtn('Purchase', 'buy', 'green', msg=tmsg('VendorPurchase', serviceId='{sid}', targetPlayerId='{tid}'), enabled='vm.ven.canBuy',
                           args={'sid': 'str:64', 'tid': 'str:64'}, argv={'sid': 'vm.ven.pickId', 'tid': 'vm.ven.targetId'}),
                      dbtn('Close', 'vclose', 'gh', msg=vmsg('vendorClose'))],
                     w='fill', gap=u(1.4), cross='center', bsides='t', border=LINE, bw=2, pad=[u(1.2), 0, 0, 0])],
                w=u(80), pad=[u(2.4), u(2.4), u(2.4), u(2.4)], gap=u(1.2), radius=u(1.8), bg=SURF, anim='dpop', anim_ms=240)
    burst = {'type': 'burst', 'x': 0, 'y': 0, 'w': 1280, 'h': 720, 'text': '640,330', 'fill': '{vm.ven.burstKey}'}
    return box([modal, burst], x=0, y=0, w='fill', h='fill', z=120, screen_bg='#060a16d6', align='center', cross='center', anim='fade', anim_ms=180, **{'if': 'vm.ven'})


def page():
    root = box([hall(), rd_view(), inter(), lobby(), party_view(), scout_view(), reward_modal(), vendor_modal(),
                spec_box(),
                {'type': 'spacer', 'id': 'ping', 'every_ms': 1500, 'msg': tmsg('ping')}],
               w='fill', h='fill', bg='gradv:#080e1d,#0f1830', hover_set={'hov': '-1'})
    return {'v': 2, 'id': 'cobbletowers', 'channel': 'cobbletowers', 'title': 'CobbleTowers', 'width': 1280, 'height': 720, 'bye': True, 'hello': 'hello',
            'zoomfit': [720, 1, 0.4, 99], 'nodim': True,
            'toast': {'bg': SURF2, 'border': LINE, 'color': INK, 'font': F6, 'size': 17.92, 'y': 654, 'from': 40, 'bw': 1, 'pad_x': 23, 'pad_y': 11.5, 'radius': 15}, 'state': {'hov': -1}, 'root': root}


if __name__ == '__main__':
    with open(os.path.join(OUT, 'cobbletowers.json'), 'w', encoding='utf8') as f:
        json.dump(page(), f, ensure_ascii=False, separators=(',', ':'))
    print('wrote cobbletowers', os.path.getsize(os.path.join(OUT, 'cobbletowers.json')))
